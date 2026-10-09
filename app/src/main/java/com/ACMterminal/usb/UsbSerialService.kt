package com.ACMterminal.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.ACMterminal.model.SerialConfig
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.nio.charset.StandardCharsets

sealed interface UsbConnectionStatus {
    data object Disconnected : UsbConnectionStatus
    data class Connecting(val deviceName: String) : UsbConnectionStatus
    data class Connected(
        val deviceName: String,
        val baudRate: Int,
        val portNumber: Int = 0
    ) : UsbConnectionStatus
    data class Error(val message: String) : UsbConnectionStatus
}

sealed interface UsbEvent {
    data class DataReceived(val text: String) : UsbEvent
    data class DataSent(val text: String) : UsbEvent
    data class SystemMessage(val message: String) : UsbEvent
    data class ErrorMessage(val message: String) : UsbEvent
}

/**
 * Handles USB Host API discovery, permission requests, and serial I/O via usb-serial-for-android
 * Supports CDC ACM devices, standard MCUs (STM32, RP2040, Arduino, Teensy, etc.) and USB-UART bridges (FTDI, CP210x, CH34x).
 */
class UsbSerialService(
    private val context: Context,
    private val scope: CoroutineScope
) : SerialInputOutputManager.Listener {

    companion object {
        private const val TAG = "UsbSerialService"
        const val ACTION_USB_PERMISSION = "com.ACMterminal.USB_PERMISSION"
        private const val WRITE_TIMEOUT_MS = 2000
    }

    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private var serialPort: UsbSerialPort? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var ioManager: SerialInputOutputManager? = null

    private val _connectionStatus = MutableStateFlow<UsbConnectionStatus>(UsbConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<UsbConnectionStatus> = _connectionStatus.asStateFlow()

    private val _events = MutableSharedFlow<UsbEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<UsbEvent> = _events.asSharedFlow()

    private val _dtrState = MutableStateFlow(false)
    val dtrState: StateFlow<Boolean> = _dtrState.asStateFlow()

    private val _rtsState = MutableStateFlow(false)
    val rtsState: StateFlow<Boolean> = _rtsState.asStateFlow()

    private var currentConfig = SerialConfig()
    private var pendingDevice: UsbDevice? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }

                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        if (granted && device != null) {
                            emitSystem("USB permission granted for ${device.productName ?: device.deviceName}")
                            connectToDevice(device)
                        } else {
                            emitError("USB permission denied")
                            _connectionStatus.value = UsbConnectionStatus.Disconnected
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    emitSystem("USB device detached: ${device?.productName ?: "Device"}")
                    disconnect()
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    emitSystem("USB device attached: ${device?.productName ?: "Device"}")
                    device?.let { requestConnect(it) }
                }
            }
        }
    }

    private var isReceiverRegistered = false

    fun register() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(ACTION_USB_PERMISSION)
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(usbReceiver, filter)
            }
            isReceiverRegistered = true
        }
    }

    fun unregister() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(usbReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister receiver", e)
            }
            isReceiverRegistered = false
        }
        disconnect()
    }

    /**
     * Get a list of currently connected USB serial compatible devices
     */
    fun getAvailableDevices(): List<UsbDevice> {
        return usbManager.deviceList.values.toList()
    }

    /**
     * Custom prober table for standard and custom CDC ACM devices
     */
    private fun getCustomProber(): UsbSerialProber {
        val customTable = ProbeTable()
        // Standard CDC ACM devices & MCU USB serial interfaces
        customTable.addProduct(0x303A, 0x1001, CdcAcmSerialDriver::class.java)
        customTable.addProduct(0x303A, 0x1002, CdcAcmSerialDriver::class.java)
        // STM32 Virtual COM Port (STMicroelectronics - 0x0483:0x5740)
        customTable.addProduct(0x0483, 0x5740, CdcAcmSerialDriver::class.java)
        // Raspberry Pi RP2040 Pico (Raspberry Pi Ltd - 0x2E8A:0x000A)
        customTable.addProduct(0x2E8A, 0x000A, CdcAcmSerialDriver::class.java)
        return UsbSerialProber(customTable)
    }

    /**
     * Initiate connection or permission flow
     */
    fun requestConnect(device: UsbDevice? = null, config: SerialConfig = currentConfig) {
        this.currentConfig = config
        val targetDevice = device ?: findFirstSerialDevice()

        if (targetDevice == null) {
            emitError("No USB devices detected. Connect a USB serial device via OTG.")
            _connectionStatus.value = UsbConnectionStatus.Disconnected
            return
        }

        if (usbManager.hasPermission(targetDevice)) {
            connectToDevice(targetDevice)
        } else {
            pendingDevice = targetDevice
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
                flags
            )
            _connectionStatus.value = UsbConnectionStatus.Connecting(targetDevice.productName ?: targetDevice.deviceName)
            emitSystem("Requesting USB permission for ${targetDevice.productName ?: "Device"}...")
            usbManager.requestPermission(targetDevice, permissionIntent)
        }
    }

    private fun findFirstSerialDevice(): UsbDevice? {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isNotEmpty()) {
            return availableDrivers.first().device
        }

        // Check with custom CDC ACM prober
        val customDrivers = getCustomProber().findAllDrivers(usbManager)
        if (customDrivers.isNotEmpty()) {
            return customDrivers.first().device
        }

        // Fallback to first USB device in list
        return usbManager.deviceList.values.firstOrNull()
    }

    private fun connectToDevice(device: UsbDevice) {
        scope.launch(Dispatchers.IO) {
            try {
                _connectionStatus.value = UsbConnectionStatus.Connecting(device.productName ?: device.deviceName)

                // 1. Probe for driver
                var driver: UsbSerialDriver? = UsbSerialProber.getDefaultProber().probeDevice(device)
                if (driver == null) {
                    driver = getCustomProber().probeDevice(device)
                }
                if (driver == null) {
                    // Fallback to CdcAcmSerialDriver directly
                    driver = CdcAcmSerialDriver(device)
                }

                if (driver.ports.isEmpty()) {
                    throw IOException("No serial ports found on driver")
                }

                val connection = usbManager.openDevice(driver.device)
                    ?: throw IOException("Failed to open USB device connection (openDevice returned null)")

                val port = driver.ports[0]
                port.open(connection)
                port.setParameters(
                    currentConfig.baudRate,
                    currentConfig.dataBits,
                    currentConfig.stopBits,
                    currentConfig.parity
                )

                // Set initial DTR and RTS states (typically true for CDC ACM communication)
                try {
                    port.dtr = true
                    port.rts = true
                    _dtrState.value = true
                    _rtsState.value = true
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to set default DTR/RTS", e)
                }

                usbConnection = connection
                serialPort = port

                // 2. Start I/O Manager in background thread pool
                val manager = SerialInputOutputManager(port, this@UsbSerialService)
                ioManager = manager
                manager.start()

                val devName = device.productName ?: "CDC ACM Device"
                _connectionStatus.value = UsbConnectionStatus.Connected(
                    deviceName = devName,
                    baudRate = currentConfig.baudRate,
                    portNumber = 0
                )
                emitSystem("Connected to $devName @ ${currentConfig.baudRate} baud (8N1)")

            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                disconnect()
                _connectionStatus.value = UsbConnectionStatus.Error(e.localizedMessage ?: "Connection error")
                emitError("Connection failed: ${e.message}")
            }
        }
    }

    /**
     * Send raw string data to the serial device
     */
    fun sendData(data: String) {
        val port = serialPort ?: run {
            emitError("Not connected to USB device")
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val bytes = data.toByteArray(StandardCharsets.US_ASCII)
                port.write(bytes, WRITE_TIMEOUT_MS)
                _events.emit(UsbEvent.DataSent(data))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send data", e)
                emitError("Send error: ${e.message}")
            }
        }
    }

    /**
     * Toggle or set DTR state
     */
    fun setDtr(asserted: Boolean) {
        val port = serialPort ?: return
        scope.launch(Dispatchers.IO) {
            try {
                port.dtr = asserted
                _dtrState.value = asserted
                emitSystem("DTR line ${if (asserted) "ASSERTED (HIGH)" else "DEASSERTED (LOW)"}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set DTR", e)
                emitError("Set DTR failed: ${e.message}")
            }
        }
    }

    /**
     * Toggle or set RTS state
     */
    fun setRts(asserted: Boolean) {
        val port = serialPort ?: return
        scope.launch(Dispatchers.IO) {
            try {
                port.rts = asserted
                _rtsState.value = asserted
                emitSystem("RTS line ${if (asserted) "ASSERTED (HIGH)" else "DEASSERTED (LOW)"}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set RTS", e)
                emitError("Set RTS failed: ${e.message}")
            }
        }
    }

    /**
     * Pulse reset via hardware control lines (RTS/DTR sequence)
     */
    fun pulseReset() {
        val port = serialPort ?: return
        scope.launch(Dispatchers.IO) {
            try {
                emitSystem("Pulse Resetting hardware (RTS/DTR toggle)...")
                port.rts = true
                port.dtr = false
                kotlinx.coroutines.delay(100)
                port.rts = false
                kotlinx.coroutines.delay(50)
                emitSystem("Reset pulse completed")
                _rtsState.value = false
                _dtrState.value = false
            } catch (e: Exception) {
                emitError("Reset failed: ${e.message}")
            }
        }
    }

    /**
     * Trigger bootloader mode via hardware control lines (DTR low while pulsing RTS)
     */
    fun enterBootloader() {
        val port = serialPort ?: return
        scope.launch(Dispatchers.IO) {
            try {
                emitSystem("Triggering hardware bootloader mode...")
                port.dtr = true
                port.rts = true
                kotlinx.coroutines.delay(100)
                port.rts = false
                kotlinx.coroutines.delay(50)
                port.dtr = false
                emitSystem("Bootloader mode signal sent")
            } catch (e: Exception) {
                emitError("Bootloader trigger failed: ${e.message}")
            }
        }
    }

    fun updateBaudRate(baudRate: Int) {
        currentConfig = currentConfig.copy(baudRate = baudRate)
        val port = serialPort ?: return
        scope.launch(Dispatchers.IO) {
            try {
                port.setParameters(
                    baudRate,
                    currentConfig.dataBits,
                    currentConfig.stopBits,
                    currentConfig.parity
                )
                emitSystem("Baud rate changed to $baudRate")
                val cur = _connectionStatus.value
                if (cur is UsbConnectionStatus.Connected) {
                    _connectionStatus.value = cur.copy(baudRate = baudRate)
                }
            } catch (e: Exception) {
                emitError("Failed to update baud rate: ${e.message}")
            }
        }
    }

    fun disconnect() {
        try {
            ioManager?.listener = null
            ioManager?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping ioManager", e)
        }
        ioManager = null

        try {
            serialPort?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing serialPort", e)
        }
        serialPort = null

        try {
            usbConnection?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing usbConnection", e)
        }
        usbConnection = null

        _connectionStatus.value = UsbConnectionStatus.Disconnected
        _dtrState.value = false
        _rtsState.value = false
    }

    // --- SerialInputOutputManager.Listener Callbacks ---

    override fun onNewData(data: ByteArray) {
        val asciiText = String(data, StandardCharsets.US_ASCII)
        scope.launch(Dispatchers.Main) {
            _events.emit(UsbEvent.DataReceived(asciiText))
        }
    }

    override fun onRunError(e: Exception) {
        Log.e(TAG, "Serial I/O error", e)
        scope.launch(Dispatchers.Main) {
            emitError("Serial connection error: ${e.message}")
            disconnect()
        }
    }

    private fun emitSystem(msg: String) {
        scope.launch(Dispatchers.Main) {
            _events.emit(UsbEvent.SystemMessage(msg))
        }
    }

    private fun emitError(msg: String) {
        scope.launch(Dispatchers.Main) {
            _events.emit(UsbEvent.ErrorMessage(msg))
        }
    }
}
