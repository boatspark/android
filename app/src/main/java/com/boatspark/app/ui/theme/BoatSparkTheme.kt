package com.boatspark.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Orange = Color(0xFFF04523)
private val Dark = Color(0xFF17161B)
private val Light = Color(0xFFE8E9E4)

private val LightColors = lightColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    secondary = Dark,
    background = Color(0xFFF9F9F6),
    surface = Color.White,
    onSurface = Dark,
)

private val DarkColors = darkColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    secondary = Light,
    background = Dark,
    surface = Color(0xFF252329),
    onSurface = Light,
)

@Composable
fun BoatSparkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

