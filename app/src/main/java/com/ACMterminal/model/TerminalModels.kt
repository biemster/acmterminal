package com.ACMterminal.model

/**
 * Represents a log line in the terminal
 */
data class TerminalLine(
    val id: Long = System.nanoTime(),
    val text: String,
    val type: TerminalLineType = TerminalLineType.OUTPUT,
    val timestamp: Long = System.currentTimeMillis()
)

enum class TerminalLineType {
    INPUT,     // User transmitted command
    OUTPUT,    // Incoming serial data from USB/UART device
    SYSTEM,    // Status / connection events
    ERROR      // Error messages
}

/**
 * Customizable shortcut macro where label is the command itself being sent
 */
data class Macro(
    val id: Int,
    val command: String,
    val appendNewline: Boolean = true
) {
    // For backwards compatibility or direct display
    val label: String
        get() = command
}

/**
 * Serial connection settings
 */
data class SerialConfig(
    val baudRate: Int = 115200,
    val dataBits: Int = 8,
    val stopBits: Int = 1,
    val parity: Int = 0 // 0: None, 1: Odd, 2: Even, 3: Mark, 4: Space
)
