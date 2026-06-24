package com.fluffyspace.tmuxbridge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal700 = Color(0xFF00796B)
private val Teal500 = Color(0xFF009688)
private val Teal200 = Color(0xFF80CBC4)
private val Teal100 = Color(0xFFB2DFDB)

private val LightColors = lightColorScheme(
    primary = Teal700,
    onPrimary = Color.White,
    primaryContainer = Teal100,
    onPrimaryContainer = Color(0xFF00251F),
    secondary = Teal500,
    onSecondary = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Teal200,
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF004D40),
    onPrimaryContainer = Teal100,
    secondary = Teal200,
    onSecondary = Color(0xFF00382F),
)

@Composable
fun TmuxBridgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
