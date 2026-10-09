package com.ACMterminal.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ACMterminal.model.Macro
import com.ACMterminal.model.SerialConfig
import com.ACMterminal.model.TerminalLine
import com.ACMterminal.model.TerminalLineType
import com.ACMterminal.usb.UsbConnectionStatus
import com.ACMterminal.usb.UsbEvent
import com.ACMterminal.usb.UsbSerialService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class LineEnding(val label: String, val value: String) {
    NONE("None", ""),
    CR("CR (\\r)", "\r"),
    LF("LF (\\n)", "\n"),
    CRLF("CRLF (\\r\\n)", "\r\n")
}

data class TerminalUiState(
    val lines: List<TerminalLine> = emptyList(),
    val connectionStatus: UsbConnectionStatus = UsbConnectionStatus.Disconnected,
    val dtrAsserted: Boolean = false,
    val rtsAsserted: Boolean = false,
    val baudRate: Int = 115200,
    val lineEnding: LineEnding = LineEnding.CRLF,
    val autoScroll: Boolean = true,
    val timestampEnabled: Boolean = false,
    val macros: List<Macro> = emptyList(),
    val isEditingMacro: Macro? = null,
    val availableBaudRates: List<Int> = listOf(9600, 19200, 38400, 57600, 74880, 115200, 230400, 460800, 921600)
)

class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("acm_terminal_prefs", Context.MODE_PRIVATE)
    private val usbService = UsbSerialService(application, viewModelScope)

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private val lineBuffer = StringBuilder()
    private val maxLines = 1000

    init {
        loadMacros()
        usbService.register()

        viewModelScope.launch {
            usbService.connectionStatus.collect { status ->
                _uiState.value = _uiState.value.copy(connectionStatus = status)
            }
        }

        viewModelScope.launch {
            usbService.dtrState.collect { dtr ->
                _uiState.value = _uiState.value.copy(dtrAsserted = dtr)
            }
        }

        viewModelScope.launch {
            usbService.rtsState.collect { rts ->
                _uiState.value = _uiState.value.copy(rtsAsserted = rts)
            }
        }

        viewModelScope.launch {
            usbService.events.collect { event ->
                handleUsbEvent(event)
            }
        }

        // Initial ready line
        appendLine(
            TerminalLine(
                text = "ACM Terminal Ready. Connect USB device via USB-OTG.",
                type = TerminalLineType.SYSTEM
            )
        )
    }

    private fun handleUsbEvent(event: UsbEvent) {
        when (event) {
            is UsbEvent.DataReceived -> {
                processIncomingAscii(event.text)
            }
            is UsbEvent.DataSent -> {
                appendLine(
                    TerminalLine(
                        text = "TX: ${event.text.trimEnd('\r', '\n')}",
                        type = TerminalLineType.INPUT
                    )
                )
            }
            is UsbEvent.SystemMessage -> {
                appendLine(
                    TerminalLine(
                        text = "[SYS] ${event.message}",
                        type = TerminalLineType.SYSTEM
                    )
                )
            }
            is UsbEvent.ErrorMessage -> {
                appendLine(
                    TerminalLine(
                        text = "[ERR] ${event.message}",
                        type = TerminalLineType.ERROR
                    )
                )
            }
        }
    }

    private fun processIncomingAscii(chunk: String) {
        lineBuffer.append(chunk)
        val content = lineBuffer.toString()
        if (content.contains('\n') || content.contains('\r')) {
            val lines = content.split(Regex("\r\n|\n|\r"))
            val endsWithNewline = content.endsWith("\n") || content.endsWith("\r")
            val completeLines = lines.dropLast(1)
            lineBuffer.clear()
            if (!endsWithNewline && lines.isNotEmpty()) {
                lineBuffer.append(lines.last())
            }

            completeLines.forEach { line ->
                if (line.isNotEmpty()) {
                    appendLine(TerminalLine(text = line, type = TerminalLineType.OUTPUT))
                }
            }
        } else if (lineBuffer.length > 256) {
            appendLine(TerminalLine(text = lineBuffer.toString(), type = TerminalLineType.OUTPUT))
            lineBuffer.clear()
        }
    }

    private fun appendLine(line: TerminalLine) {
        val current = _uiState.value.lines
        val updated = if (current.size >= maxLines) {
            current.drop(current.size - maxLines + 1) + line
        } else {
            current + line
        }
        _uiState.value = _uiState.value.copy(lines = updated)
    }

    fun connect() {
        val config = SerialConfig(baudRate = _uiState.value.baudRate)
        usbService.requestConnect(config = config)
    }

    fun disconnect() {
        usbService.disconnect()
        appendLine(TerminalLine(text = "[SYS] Disconnected from device", type = TerminalLineType.SYSTEM))
    }

    fun sendText(rawCommand: String) {
        if (rawCommand.isEmpty()) return
        val commandWithEnding = rawCommand + _uiState.value.lineEnding.value
        usbService.sendData(commandWithEnding)
    }

    fun executeMacro(macro: Macro) {
        val cmd = if (macro.appendNewline) {
            macro.command + _uiState.value.lineEnding.value
        } else {
            macro.command
        }
        usbService.sendData(cmd)
    }

    fun toggleDtr() {
        val next = !_uiState.value.dtrAsserted
        usbService.setDtr(next)
    }

    fun toggleRts() {
        val next = !_uiState.value.rtsAsserted
        usbService.setRts(next)
    }

    fun pulseReset() {
        usbService.pulseReset()
    }

    fun enterBootloader() {
        usbService.enterBootloader()
    }

    fun setBaudRate(baud: Int) {
        _uiState.value = _uiState.value.copy(baudRate = baud)
        usbService.updateBaudRate(baud)
    }

    fun setLineEnding(ending: LineEnding) {
        _uiState.value = _uiState.value.copy(lineEnding = ending)
    }

    fun toggleAutoScroll() {
        _uiState.value = _uiState.value.copy(autoScroll = !_uiState.value.autoScroll)
    }

    fun toggleTimestamp() {
        _uiState.value = _uiState.value.copy(timestampEnabled = !_uiState.value.timestampEnabled)
    }

    fun clearTerminal() {
        lineBuffer.clear()
        _uiState.value = _uiState.value.copy(lines = emptyList())
    }

    fun openMacroEditor(macro: Macro) {
        _uiState.value = _uiState.value.copy(isEditingMacro = macro)
    }

    fun closeMacroEditor() {
        _uiState.value = _uiState.value.copy(isEditingMacro = null)
    }

    fun saveMacro(id: Int, command: String, appendNewline: Boolean) {
        val currentMacros = _uiState.value.macros.toMutableList()
        val index = currentMacros.indexOfFirst { it.id == id }
        val updatedMacro = Macro(id, command, appendNewline)
        if (index != -1) {
            currentMacros[index] = updatedMacro
        } else {
            currentMacros.add(updatedMacro)
        }
        _uiState.value = _uiState.value.copy(macros = currentMacros, isEditingMacro = null)
        saveMacrosToPrefs(currentMacros)
    }

    private fun loadMacros() {
        val json = prefs.getString("macros_json", null)
        val list = mutableListOf<Macro>()
        if (json != null) {
            try {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val cmd = if (obj.has("command")) obj.getString("command") else obj.optString("label", "")
                    list.add(
                        Macro(
                            id = obj.getInt("id"),
                            command = cmd,
                            appendNewline = obj.optBoolean("appendNewline", true)
                        )
                    )
                }
            } catch (e: Exception) {
                list.clear()
            }
        }

        if (list.isEmpty()) {
            list.addAll(
                listOf(
                    Macro(1, "help"),
                    Macro(2, "info"),
                    Macro(3, "status"),
                    Macro(4, "reset"),
                    Macro(5, "ping"),
                    Macro(6, "AT")
                )
            )
        }

        _uiState.value = _uiState.value.copy(macros = list)
    }

    private fun saveMacrosToPrefs(macros: List<Macro>) {
        val array = JSONArray()
        macros.forEach { m ->
            val obj = JSONObject().apply {
                put("id", m.id)
                put("command", m.command)
                put("appendNewline", m.appendNewline)
            }
            array.put(obj)
        }
        prefs.edit().putString("macros_json", array.toString()).apply()
    }

    override fun onCleared() {
        super.onCleared()
        usbService.unregister()
    }
}
