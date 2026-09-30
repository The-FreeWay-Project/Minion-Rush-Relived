package de.freeway.mrr.patcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Dark gaming scheme of the MRR Patcher: near-black surfaces with the
 * Minion-yellow accent.
 */
private val PatcherColorScheme = darkColorScheme(
    primary = Color(0xFFF9A825),
    onPrimary = Color(0xFF101216),
    primaryContainer = Color(0xFFB27900),
    onPrimaryContainer = Color(0xFFFFDEA6),
    secondary = Color(0xFF66BB6A),
    onSecondary = Color(0xFF0B2010),
    background = Color(0xFF0B0D12),
    onBackground = Color(0xFFE6E8EF),
    surface = Color(0xFF12151C),
    onSurface = Color(0xFFE6E8EF),
    surfaceVariant = Color(0xFF1B1F29),
    onSurfaceVariant = Color(0xFF9AA1B2),
    error = Color(0xFFEF5350),
    onError = Color(0xFF2A0B0A),
)

/** Status accent colours shared by the screen. */
object PatcherColors {
    val Green = Color(0xFF66BB6A)
    val Yellow = Color(0xFFF9A825)
    val Red = Color(0xFFEF5350)
    val Neutral = Color(0xFF9AA1B2)
}

@Composable
fun PatcherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PatcherColorScheme,
        content = content,
    )
}
