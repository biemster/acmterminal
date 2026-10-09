package com.ACMterminal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ACMterminal.model.Macro
import com.ACMterminal.model.TerminalLine
import com.ACMterminal.model.TerminalLineType
import com.ACMterminal.usb.UsbConnectionStatus
import com.ACMterminal.viewmodel.LineEnding
import com.ACMterminal.viewmodel.TerminalUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    uiState: TerminalUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSendText: (String) -> Unit,
    onExecuteMacro: (Macro) -> Unit,
    onEditMacro: (Macro) -> Unit,
    onToggleDtr: () -> Unit,
    onToggleRts: () -> Unit,
    onPulseReset: () -> Unit,
    onEnterBootloader: () -> Unit,
    onSetBaudRate: (Int) -> Unit,
    onSetLineEnding: (LineEnding) -> Unit,
    onToggleAutoScroll: () -> Unit,
    onToggleTimestamp: () -> Unit,
    onClearTerminal: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll when lines are updated
    LaunchedEffect(uiState.lines.size, uiState.autoScroll) {
        if (uiState.autoScroll && uiState.lines.isNotEmpty()) {
            listState.animateScrollToItem(uiState.lines.size - 1)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TerminalTopBar(
                connectionStatus = uiState.connectionStatus,
                baudRate = uiState.baudRate,
                availableBaudRates = uiState.availableBaudRates,
                lineEnding = uiState.lineEnding,
                autoScroll = uiState.autoScroll,
                timestampEnabled = uiState.timestampEnabled,
                onConnect = onConnect,
                onDisconnect = onDisconnect,
                onSetBaudRate = onSetBaudRate,
                onSetLineEnding = onSetLineEnding,
                onToggleAutoScroll = onToggleAutoScroll,
                onToggleTimestamp = onToggleTimestamp,
                onClearTerminal = onClearTerminal
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Hardware Pin Control Toolbar (DTR, RTS, RST, BOOT) with no text wrapping
            HardwareControlBar(
                isConnected = uiState.connectionStatus is UsbConnectionStatus.Connected,
                dtrAsserted = uiState.dtrAsserted,
                rtsAsserted = uiState.rtsAsserted,
                onToggleDtr = onToggleDtr,
                onToggleRts = onToggleRts,
                onPulseReset = onPulseReset,
                onEnterBootloader = onEnterBootloader
            )

            // Terminal Log Viewport (Monospace, dark background)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF0D1117)) // Deep terminal dark
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                if (uiState.lines.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No serial logs received yet.\nTap Connect or plug in a USB device.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF6E7681)
                            ),
                            lineHeight = 20.sp
                        )
                    }
                } else {
                    SelectionContainer {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("terminal_log_list")
                        ) {
                            items(
                                items = uiState.lines,
                                key = { it.id }
                            ) { line ->
                                TerminalLineItem(
                                    line = line,
                                    showTimestamp = uiState.timestampEnabled
                                )
                            }
                        }
                    }
                }
            }

            // Macros Row (Short press = Send, Long press = Edit)
            MacroToolbar(
                macros = uiState.macros,
                onMacroClick = onExecuteMacro,
                onMacroLongClick = onEditMacro
            )

            // Command Input Bar
            CommandInputBar(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    if (inputText.isNotEmpty()) {
                        onSendText(inputText)
                        inputText = ""
                    }
                },
                onClear = { inputText = "" }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalTopBar(
    connectionStatus: UsbConnectionStatus,
    baudRate: Int,
    availableBaudRates: List<Int>,
    lineEnding: LineEnding,
    autoScroll: Boolean,
    timestampEnabled: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSetBaudRate: (Int) -> Unit,
    onSetLineEnding: (LineEnding) -> Unit,
    onToggleAutoScroll: () -> Unit,
    onToggleTimestamp: () -> Unit,
    onClearTerminal: () -> Unit
) {
    var showBaudMenu by remember { mutableStateOf(false) }
    var showLineEndingMenu by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }

    val isConnected = connectionStatus is UsbConnectionStatus.Connected
    val isConnecting = connectionStatus is UsbConnectionStatus.Connecting

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = "USB",
                        tint = if (isConnected) Color(0xFF3DDC84) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ACM terminal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                when (connectionStatus) {
                                    is UsbConnectionStatus.Connected -> Color(0xFF3DDC84)
                                    is UsbConnectionStatus.Connecting -> Color(0xFFFFB74D)
                                    is UsbConnectionStatus.Error -> MaterialTheme.colorScheme.error
                                    is UsbConnectionStatus.Disconnected -> Color(0xFF888888)
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (connectionStatus) {
                            is UsbConnectionStatus.Connected -> connectionStatus.deviceName
                            is UsbConnectionStatus.Connecting -> "Connecting..."
                            is UsbConnectionStatus.Error -> "Error: ${connectionStatus.message}"
                            is UsbConnectionStatus.Disconnected -> "Disconnected"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        },
        actions = {
            // Baud Rate Selector Chip
            Box {
                AssistChip(
                    onClick = { showBaudMenu = true },
                    label = { Text("$baudRate") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Select Baud Rate",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("baud_rate_chip")
                )
                DropdownMenu(
                    expanded = showBaudMenu,
                    onDismissRequest = { showBaudMenu = false }
                ) {
                    availableBaudRates.forEach { rate ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "$rate baud",
                                    fontWeight = if (rate == baudRate) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onSetBaudRate(rate)
                                showBaudMenu = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Connect / Disconnect Action Button
            if (isConnected) {
                IconButton(
                    onClick = onDisconnect,
                    modifier = Modifier.testTag("disconnect_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerOff,
                        contentDescription = "Disconnect USB",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                IconButton(
                    onClick = onConnect,
                    enabled = !isConnecting,
                    modifier = Modifier.testTag("connect_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Power,
                        contentDescription = "Connect USB",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // More Settings Menu
            Box {
                IconButton(
                    onClick = { showOptionsMenu = true },
                    modifier = Modifier.testTag("options_menu_button")
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = "Terminal Settings")
                }
                DropdownMenu(
                    expanded = showOptionsMenu,
                    onDismissRequest = { showOptionsMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Line Ending: ${lineEnding.label}") },
                        onClick = {
                            showOptionsMenu = false
                            showLineEndingMenu = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (autoScroll) "✓ Auto-scroll ON" else "Auto-scroll OFF") },
                        onClick = {
                            onToggleAutoScroll()
                            showOptionsMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (timestampEnabled) "✓ Timestamps ON" else "Timestamps OFF") },
                        onClick = {
                            onToggleTimestamp()
                            showOptionsMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Clear Terminal") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null)
                        },
                        onClick = {
                            onClearTerminal()
                            showOptionsMenu = false
                        }
                    )
                }

                // Line Ending Sub-menu
                DropdownMenu(
                    expanded = showLineEndingMenu,
                    onDismissRequest = { showLineEndingMenu = false }
                ) {
                    LineEnding.entries.forEach { ending ->
                        DropdownMenuItem(
                            text = { Text(ending.label) },
                            onClick = {
                                onSetLineEnding(ending)
                                showLineEndingMenu = false
                            }
                        )
                    }
                }
            }
        }
    )
}

/**
 * Dedicated hardware control bar for DTR, RTS toggling and reset/bootloader commands.
 * Horizontally scrollable with softWrap=false so labels never wrap across lines.
 */
@Composable
fun HardwareControlBar(
    isConnected: Boolean,
    dtrAsserted: Boolean,
    rtsAsserted: Boolean,
    onToggleDtr: () -> Unit,
    onToggleRts: () -> Unit,
    onPulseReset: () -> Unit,
    onEnterBootloader: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "PINS:",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )

            // DTR Pin Toggle
            FilterChip(
                selected = dtrAsserted,
                onClick = onToggleDtr,
                label = {
                    Text(
                        text = if (dtrAsserted) "DTR (HIGH)" else "DTR (LOW)",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                enabled = isConnected,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.testTag("dtr_toggle_button")
            )

            // RTS Pin Toggle
            FilterChip(
                selected = rtsAsserted,
                onClick = onToggleRts,
                label = {
                    Text(
                        text = if (rtsAsserted) "RTS (HIGH)" else "RTS (LOW)",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                enabled = isConnected,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.testTag("rts_toggle_button")
            )

            // Pulse Reset Button
            FilledTonalButton(
                onClick = onPulseReset,
                enabled = isConnected,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(32.dp)
                    .testTag("pulse_reset_button")
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "RESET",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Enter Bootloader Button
            OutlinedButton(
                onClick = onEnterBootloader,
                enabled = isConnected,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(32.dp)
                    .testTag("bootloader_button")
            ) {
                Text(
                    text = "BOOT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun TerminalLineItem(
    line: TerminalLine,
    showTimestamp: Boolean
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    val textColor = when (line.type) {
        TerminalLineType.INPUT -> Color(0xFF58A6FF)     // Soft Blue for TX
        TerminalLineType.OUTPUT -> Color(0xFF56D364)    // Monospace Terminal Green for RX
        TerminalLineType.SYSTEM -> Color(0xFF8B949E)    // Muted grey for system
        TerminalLineType.ERROR -> Color(0xFFF85149)     // Red for errors
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        if (showTimestamp) {
            Text(
                text = timeFormatter.format(Date(line.timestamp)) + " ",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFF6E7681)
                ),
                maxLines = 1,
                softWrap = false
            )
        }

        Text(
            text = line.text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = textColor
            )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MacroToolbar(
    macros: List<Macro>,
    onMacroClick: (Macro) -> Unit,
    onMacroLongClick: (Macro) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                text = "MACROS (Long-press to edit)",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(macros, key = { it.id }) { macro ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .combinedClickable(
                                onClick = { onMacroClick(macro) },
                                onLongClick = { onMacroLongClick(macro) }
                            )
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                            .testTag("macro_button_${macro.id}")
                    ) {
                        Text(
                            text = macro.command,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CommandInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = {
                    Text(
                        text = "Send ASCII command...",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                    )
                },
                trailingIcon = {
                    AnimatedVisibility(visible = text.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear Input")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("command_input_field")
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onSend,
                enabled = text.isNotEmpty(),
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (text.isNotEmpty()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .testTag("send_command_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Transmit Command",
                    tint = if (text.isNotEmpty()) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
        }
    }
}
