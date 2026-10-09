package com.ACMterminal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ACMterminal.ui.EditMacroDialog
import com.ACMterminal.ui.TerminalScreen
import com.ACMterminal.ui.theme.ACMTerminalTheme
import com.ACMterminal.viewmodel.TerminalViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: TerminalViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ACMTerminalTheme {
                val uiState by viewModel.uiState.collectAsState()

                TerminalScreen(
                    uiState = uiState,
                    onConnect = { viewModel.connect() },
                    onDisconnect = { viewModel.disconnect() },
                    onSendText = { text -> viewModel.sendText(text) },
                    onExecuteMacro = { macro -> viewModel.executeMacro(macro) },
                    onEditMacro = { macro -> viewModel.openMacroEditor(macro) },
                    onToggleDtr = { viewModel.toggleDtr() },
                    onToggleRts = { viewModel.toggleRts() },
                    onPulseReset = { viewModel.pulseReset() },
                    onEnterBootloader = { viewModel.enterBootloader() },
                    onSetBaudRate = { baud -> viewModel.setBaudRate(baud) },
                    onSetLineEnding = { ending -> viewModel.setLineEnding(ending) },
                    onToggleAutoScroll = { viewModel.toggleAutoScroll() },
                    onToggleTimestamp = { viewModel.toggleTimestamp() },
                    onClearTerminal = { viewModel.clearTerminal() }
                )

                // Dialog to edit macro command
                uiState.isEditingMacro?.let { macro ->
                    EditMacroDialog(
                        macro = macro,
                        onDismiss = { viewModel.closeMacroEditor() },
                        onSave = { id, command, appendNewline ->
                            viewModel.saveMacro(id, command, appendNewline)
                        }
                    )
                }
            }
        }
    }
}
