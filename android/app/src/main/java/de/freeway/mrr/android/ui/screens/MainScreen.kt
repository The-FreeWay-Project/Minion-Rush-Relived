package de.freeway.mrr.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.freeway.mrr.android.ui.MrrUiState

@Composable
fun MainScreen(
    state: MrrUiState,
    onCreatePlayer: () -> Unit,
    onDeletePlayer: (String) -> Unit,
    onNewPlayerId: (String) -> Unit,
    onNewPlayerName: (String) -> Unit,
    onOpenState: () -> Unit,
    onLogout: () -> Unit,
    onClearMessages: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Profile", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onLogout, enabled = !state.loading) { Text("Sign out") }
        }

        state.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        state.notice?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val profile = state.profile
                if (profile == null) {
                    Text(text = "Loading profile…", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        text = "Signed in as ${profile.username}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Account #${profile.accountId}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    val player = profile.player
                    if (player == null) {
                        Text(text = "No player yet — create one below.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(text = player.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(text = "ID: ${player.playerId}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "Level ${player.level} · ${player.coins} coins",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onOpenState, enabled = !state.loading, modifier = Modifier.weight(1f)) {
                Text("Save state")
            }
        }

        Text(
            text = "Create player",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        OutlinedTextField(
            value = state.newPlayerId,
            onValueChange = onNewPlayerId,
            label = { Text("Player ID") },
            singleLine = true,
            enabled = !state.loading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        OutlinedTextField(
            value = state.newPlayerName,
            onValueChange = onNewPlayerName,
            label = { Text("Display name") },
            singleLine = true,
            enabled = !state.loading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        Button(
            onClick = onCreatePlayer,
            enabled = !state.loading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("Create player")
        }

        Text(
            text = "Players",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        if (state.players.isEmpty()) {
            Text(
                text = "No players yet.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            state.players.forEach { player ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = player.displayName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "${player.playerId} · ${player.createdAt}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(
                        onClick = { onDeletePlayer(player.playerId) },
                        enabled = !state.loading,
                    ) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}
