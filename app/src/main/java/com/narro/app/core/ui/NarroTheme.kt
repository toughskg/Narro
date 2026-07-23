package com.narro.app.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF007F7B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF1EB),
    onPrimaryContainer = Color(0xFF00201E),
    secondary = Color(0xFF4A6361),
    background = Color(0xFFFAFDFC),
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF80D5CF),
    onPrimary = Color(0xFF003735),
    primaryContainer = Color(0xFF00504D),
    onPrimaryContainer = Color(0xFF9CF1EB),
    background = Color(0xFF101413),
    onBackground = Color(0xFFE0E4E2),
    surface = Color(0xFF101413),
    onSurface = Color(0xFFE0E4E2),
    surfaceContainerHigh = Color(0xFF26312F),
    outline = Color(0xFF899390),
)

@Composable
fun NarroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
