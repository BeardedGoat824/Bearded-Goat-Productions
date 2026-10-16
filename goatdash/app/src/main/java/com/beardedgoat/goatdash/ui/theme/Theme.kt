package com.beardedgoat.goatdash.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Background = Color(0xFF07090D)
val CardColor = Color(0xFF12151C)
val Border = Color(0xFF222834)
val TextColor = Color(0xFFF1F5F9)
val Muted = Color(0xFF8B95AB)
val GoatOrange = Color(0xFFFF8A1F)
val Good = Color(0xFF22C55E)
val Warn = Color(0xFFF59E0B)
val Danger = Color(0xFFEF4444)

private val Colors = darkColorScheme(
    primary = GoatOrange,
    onPrimary = Color(0xFF1A0E00),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF00131D),
    background = Background,
    onBackground = TextColor,
    surface = CardColor,
    onSurface = TextColor,
    surfaceVariant = Color(0xFF0D1016),
    onSurfaceVariant = Muted,
    surfaceContainer = Color(0xFF0D1016),
    surfaceContainerHigh = Color(0xFF181C25),
    outline = Border,
    outlineVariant = Color(0xFF1A202C),
    error = Danger,
)

@Composable
fun GoatDashTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
