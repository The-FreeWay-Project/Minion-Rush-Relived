package de.freeway.mrr.patcher.ui

import java.util.Locale

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import de.freeway.mrr.patcher.pipeline.PatchState
import de.freeway.mrr.patcher.ui.theme.PatcherColors

private val PIPELINE_STATES = setOf(
    PatchState.DOWNLOADING,
    PatchState.VERIFYING,
    PatchState.READY_TO_PATCH,
    PatchState.PATCHING,
    PatchState.VERIFYING_PATCH,
)

/**
 * Single-screen MRR Patcher UI: dark gaming layout (MRR header, server /
 * version rows, one full-width action button, live pipeline progress and a
 * coloured status block). Responsive - vertically scrollable and capped at
 * 560 dp on wide screens.
 */
@Composable
fun PatcherScreen(
    state: PatcherUiState,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Header()
            Spacer(Modifier.height(28.dp))
            InfoRows(state)
            Spacer(Modifier.height(28.dp))
            PrimaryButton(state = state, onAction = onAction)
            Spacer(Modifier.height(24.dp))
            ProgressBlock(state = state)
            StatusBlock(state = state)
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Patches only touch the MRR patch workspace. " +
                    "Signed APKs are never modified.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Header() {
    Text(
        text = "MRR",
        fontSize = 44.sp,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 6.sp,
    )
    Text(
        text = "MINION RUSH RELIVED",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 4.sp,
    )
}

@Composable
private fun InfoRows(state: PatcherUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InfoLabel("Server:")
            Spacer(Modifier.size(8.dp))
            ServerDot(state.serverOnline)
            Spacer(Modifier.size(6.dp))
            Text(
                text = when (state.serverOnline) {
                    true -> "ONLINE"
                    false -> "OFFLINE"
                    null -> "\u2013"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = when (state.serverOnline) {
                    true -> PatcherColors.Green
                    false -> PatcherColors.Red
                    null -> PatcherColors.Neutral
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            InfoLabel("Installed:")
            Spacer(Modifier.size(8.dp))
            Text(
                text = state.installedVersion,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            InfoLabel("Available:")
            Spacer(Modifier.size(8.dp))
            Text(
                text = state.availableVersion ?: "\u2013",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = when {
                    state.availableVersion != null -> MaterialTheme.colorScheme.primary
                    else -> PatcherColors.Neutral
                },
            )
        }
    }
}

@Composable
private fun InfoLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ServerDot(online: Boolean?) {
    if (online == null) return
    val transition = rememberInfiniteTransition(label = "server-dot")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "server-dot-alpha",
    )
    val color = if (online) PatcherColors.Green else PatcherColors.Red
    Spacer(
        Modifier
            .size(8.dp)
            .background(color.copy(alpha = alpha), CircleShape),
    )
}

@Composable
private fun PrimaryButton(
    state: PatcherUiState,
    onAction: () -> Unit,
) {
    val label: String
    val enabled: Boolean
    when (state.status) {
        PatchState.IDLE -> {
            label = "CHECK"
            enabled = true
        }
        PatchState.CHECKING -> {
            label = "CHECKING\u2026"
            enabled = false
        }
        PatchState.UP_TO_DATE -> {
            label = "PATCH"
            enabled = true
        }
        PatchState.UPDATE_AVAILABLE -> {
            label = "UPDATE"
            enabled = true
        }
        PatchState.SUCCESS -> {
            label = "CHECK"
            enabled = true
        }
        PatchState.FAILED -> {
            label = "RETRY"
            enabled = true
        }
        else -> {
            label = "PATCHING\u2026"
            enabled = false
        }
    }
    Button(
        onClick = onAction,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )
    }
}

@Composable
private fun ProgressBlock(state: PatcherUiState) {
    AnimatedVisibility(visible = state.status in PIPELINE_STATES) {
        val progress = state.progress
        val indeterminate = state.status != PatchState.DOWNLOADING ||
            progress == null ||
            progress.percent < 0
        Column(modifier = Modifier.fillMaxWidth()) {
            if (indeterminate) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { (progress?.percent ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(10.dp))
            if (progress != null && progress.currentFile.isNotEmpty()) {
                Text(
                    text = progress.currentFile,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.status == PatchState.DOWNLOADING && progress != null && progress.percent >= 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Downloading ${progress.percent}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (progress != null && progress.totalBytesTotal > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${humanBytes(progress.totalBytesDone)} / ${humanBytes(progress.totalBytesTotal)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatusBlock(state: PatcherUiState) {
    val color = when (state.status) {
        PatchState.UP_TO_DATE, PatchState.SUCCESS -> PatcherColors.Green
        PatchState.UPDATE_AVAILABLE,
        PatchState.DOWNLOADING,
        PatchState.VERIFYING,
        PatchState.READY_TO_PATCH,
        PatchState.PATCHING,
        PatchState.VERIFYING_PATCH -> MaterialTheme.colorScheme.primary
        PatchState.FAILED -> MaterialTheme.colorScheme.error
        PatchState.IDLE, PatchState.CHECKING -> PatcherColors.Neutral
    }
    val label = when (state.status) {
        PatchState.IDLE -> "Standby"
        PatchState.CHECKING -> "Connecting to server \u2026"
        PatchState.UP_TO_DATE -> "Up to date"
        PatchState.UPDATE_AVAILABLE -> "Update available"
        PatchState.DOWNLOADING -> "Downloading \u2026"
        PatchState.VERIFYING -> "Verifying checksums \u2026"
        PatchState.READY_TO_PATCH -> "Ready to patch"
        PatchState.PATCHING -> "Applying patch \u2026"
        PatchState.VERIFYING_PATCH -> "Verifying installation \u2026"
        PatchState.SUCCESS -> "Done"
        PatchState.FAILED -> "Error"
    }

    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = color,
        textAlign = TextAlign.Center,
    )
    Text(
        text = state.message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

private fun humanBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> String.format(Locale.US, "%.1f kB", bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}
