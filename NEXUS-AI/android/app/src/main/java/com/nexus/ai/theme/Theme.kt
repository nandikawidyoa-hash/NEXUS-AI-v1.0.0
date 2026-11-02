package com.nexus.ai.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkPalette = darkColorScheme(
    primary = AccentBlueLight,
    onPrimary = Slate950,
    secondary = EmeraldOk,
    background = Slate950,
    surface = Slate900,
    surfaceVariant = Slate800,
    onBackground = Slate50,
    onSurface = Slate100,
    error = CrimsonDanger
)

private val LightPalette = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    secondary = EmeraldOk,
    background = Slate50,
    surface = Color.White,
    surfaceVariant = Slate100,
    onBackground = Slate900,
    onSurface = Slate900,
    error = CrimsonDanger
)

@Composable
fun NexusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) DarkPalette else LightPalette
    MaterialTheme(
        colorScheme = scheme,
        typography = NexusTypography,
        content = content
    )
}
