package de.freeway.mrr.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MrrColorScheme = lightColorScheme(
    primary = Color(0xFFF9A825),
    onPrimary = Color(0xFF212121),
    primaryContainer = Color(0xFFFFF59D),
    onPrimaryContainer = Color(0xFF212121),
    secondary = Color(0xFF2E7D32),
    onSecondary = Color.White,
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    error = Color(0xFFB3261E),
)

@Composable
fun MrrTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MrrColorScheme,
        content = content,
    )
}
