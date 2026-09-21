package com.pixelrender.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Mint = Color(0xFF7FD4A2)
private val MintDark = Color(0xFF16311F)
private val Slate = Color(0xFF101418)
private val SlateRaised = Color(0xFF181D23)

private val DarkScheme = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF07130C),
    primaryContainer = MintDark,
    onPrimaryContainer = Mint,
    background = Slate,
    onBackground = Color(0xFFE3E7EA),
    surface = Slate,
    onSurface = Color(0xFFE3E7EA),
    surfaceVariant = SlateRaised,
    onSurfaceVariant = Color(0xFFAEB6BE),
    error = Color(0xFFE2857A)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF186B41),
    background = Color(0xFFF7F9F8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE7ECE9)
)

@Composable
fun PixelRenderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content
    )
}
