package com.pokp.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Purple = Color(0xFF5B21B6)
private val PurpleLight = Color(0xFF7C3AED)

private val DarkColors = darkColorScheme(
    primary = PurpleLight,
    secondary = Purple,
)

private val LightColors = lightColorScheme(
    primary = Purple,
    secondary = PurpleLight,
)

@Composable
fun PokpTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
