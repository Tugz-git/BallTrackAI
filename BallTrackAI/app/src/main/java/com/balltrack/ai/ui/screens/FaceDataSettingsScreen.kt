package com.balltrack.ai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.balltrack.ai.vision.FaceRecognitionManager
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun FaceDataSettingsScreen(faceManager: FaceRecognitionManager, onBack: () -> Unit) {
    val players by faceManager.registeredPlayers.collectAsState()
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var playerToDelete by remember { mutableStateOf<FaceRecognitionManager.PlayerFaceData?>(null) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Face Data") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } }
        )
    }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(modifier = Modifier.padding(12.dp)) {
                    Icon(Icons.Filled.Security, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Stored entirely on this device. Never uploaded anywhere. " +
                        "Deleting here is immediate and permanent.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (players.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    Text("No face data stored", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                Text("${players.size} player${if (players.size != 1) "s" else ""} registered", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))

                LazyColumnPlayers(players = players, onDelete = { playerToDelete = it })

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { showDeleteAllConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete All Face Data")
                }
            }
        }
    }

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text("Delete all face data?") },
            text = { Text("This permanently removes every stored face signature for all ${players.size} player(s). This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { faceManager.deleteAll(); showDeleteAllConfirm = false }) {
                    Text("Delete All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteAllConfirm = false }) { Text("Cancel") } }
        )
    }

    playerToDelete?.let { player ->
        AlertDialog(
            onDismissRequest = { playerToDelete = null },
            title = { Text("Delete ${player.playerName}'s face data?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { faceManager.deletePlayer(player.playerId); playerToDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { playerToDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun LazyColumnPlayers(players: List<FaceRecognitionManager.PlayerFaceData>, onDelete: (FaceRecognitionManager.PlayerFaceData) -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    Column {
        players.forEach { player ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Face, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(player.playerName, fontWeight = FontWeight.Bold)
                        Text("Registered ${dateFormat.format(player.registeredAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    IconButton(onClick = { onDelete(player) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
