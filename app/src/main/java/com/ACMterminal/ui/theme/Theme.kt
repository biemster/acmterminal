package com.ACMterminal.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val TerminalDarkColorScheme = darkColorScheme(
    primary = TerminalGreen,
    onPrimary = Color(0xFF003919),
    primaryContainer = Color(0xFF005328),
    onPrimaryContainer = Color(0xFF6CFF9E),
    secondary = TerminalCyan,
    onSecondary = Color(0xFF003258),
    secondaryContainer = Color(0xFF00497D),
    onSecondaryContainer = Color(0xFFD1E4FF),
    background = DarkBackground,
    onBackground = WhiteText,
    surface = DarkSurface,
    onSurface = WhiteText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = GreyText,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    outline = OutlineGrey,
    outlineVariant = Color(0xFF21262D),
    error = TerminalError,
    onError = Color.White
)

private val TerminalLightColorScheme = lightColorScheme(
    primary = Color(0xFF006D3A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF6CFF9E),
    onPrimaryContainer = Color(0xFF00210E),
    secondary = Color(0xFF006399),
    onSecondary = Color.White,
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF1F2328),
    surface = Color.White,
    onSurface = Color(0xFF1F2328)
)

@Composable
fun ACMTerminalTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> TerminalDarkColorScheme
        else -> TerminalLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
