package com.lyx.copy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Slate,
    secondary = Sky,
    tertiary = Storm,
    background = Sand,
    surface = Color.White,
    surfaceVariant = Mist,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Ink,
    onSurface = Ink,
    onSurfaceVariant = Storm
)

@Composable
fun CopyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content
    )
}
