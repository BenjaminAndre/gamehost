package com.brigade.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Dark only, and not as a style preference.
 *
 * The GM surface is used at a table in a dim room, next to a display the players are
 * looking at. A bright tablet is a light source pointed at everyone's night vision, and
 * it wrecks the GM's own adaptation to the player screen.
 */
private val BrigadeColors = darkColorScheme(
    primary = Color(0xFFA8C0E8),
    onPrimary = Color(0xFF0B1220),
    primaryContainer = Color(0xFF2A3A55),
    onPrimaryContainer = Color(0xFFD6E2F7),
    secondary = Color(0xFFB6C4D6),
    onSecondary = Color(0xFF12181F),
    // Faction red in the note bar. Material 3's third accent, used rather than `error`
    // because that red already marks a missing slot in the control bar directly below —
    // two reds side by side, one meaning "something is broken", read as the same signal
    // in dim light. Warmer and more saturated than the error red, deliberately.
    tertiary = Color(0xFFE0736A),
    onTertiary = Color(0xFF2A0D0A),
    background = Color(0xFF12141A),
    onBackground = Color(0xFFE2E5EA),
    surface = Color(0xFF181B22),
    onSurface = Color(0xFFE2E5EA),
    surfaceVariant = Color(0xFF262A33),
    onSurfaceVariant = Color(0xFFB8BEC9),
    outline = Color(0xFF4A515E),
    error = Color(0xFFE79A9A),
    onError = Color(0xFF2A0F0F),
)

@Composable
fun BrigadeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BrigadeColors, content = content)
}
