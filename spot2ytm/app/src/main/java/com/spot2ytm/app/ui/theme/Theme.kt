package com.spot2ytm.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Background = Color(0xFF0B0D12)
val CardColor = Color(0xFF141821)
val Border = Color(0xFF252B3B)
val TextColor = Color(0xFFEEF1F7)
val Muted = Color(0xFF8B95AB)
val SpotifyGreen = Color(0xFF1DB954)
val YouTubeRed = Color(0xFFFF0033)
val Accent = Color(0xFF3B82F6)
val Warn = Color(0xFFF59E0B)
val Danger = Color(0xFFEF4444)

private val Colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = SpotifyGreen,
    onSecondary = Color(0xFF05160B),
    tertiary = YouTubeRed,
    onTertiary = Color.White,
    background = Background,
    onBackground = TextColor,
    surface = CardColor,
    onSurface = TextColor,
    surfaceVariant = Color(0xFF0E1118),
    onSurfaceVariant = Muted,
    surfaceContainerHigh = Color(0xFF1A1F2B),
    outline = Border,
    outlineVariant = Color(0xFF1A202C),
    error = Danger,
)

@Composable
fun Spot2YtmTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
