package com.balltrack.ai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.balltrack.ai.data.db.SessionEntity
import com.balltrack.ai.data.db.SportTrackDatabase
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionHistoryScreen(db: SportTrackDatabase, onBack: () -> Unit) {
    val sessions by db.sessionDao().allSessions().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var sessionToDelete by remember { mutableStateOf<SessionEntity?>(null) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Session History") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } }
        )
    }) { padding ->
        if (sessions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No sessions yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("Finish a session to see it here", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(
                contentPadding = padding,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
            ) {
                item {
                    Spacer(Modifier.height(8.dp))
                    SummaryCard(sessions)
                    Spacer(Modifier.height(16.dp))
                    Text("All Sessions", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                }
                items(sessions) { session ->
                    SessionCard(session = session, onDelete = { sessionToDelete = session })
                    Spacer(Modifier.height(8.dp))
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    sessionToDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("Delete this session?") },
            text = { Text("This removes it from your local history. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { db.sessionDao().deleteById(session.id) }
                    sessionToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { sessionToDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SummaryCard(sessions: List<SessionEntity>) {
    val totalMakes = sessions.sumOf { it.makes }
    val totalAttempts = sessions.sumOf { it.attempts }
    val pct = if (totalAttempts > 0) totalMakes * 100 / totalAttempts else 0
    val bestStreak = sessions.maxOfOrNull { it.peakStreak } ?: 0

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatBlock("${sessions.size}", "Sessions")
            StatBlock("$pct%", "Overall")
            StatBlock("$bestStreak", "Best Streak")
        }
    }
}

@Composable
private fun StatBlock(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SessionCard(session: SessionEntity, onDelete: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()) }
    val pct = if (session.attempts > 0) session.makes * 100 / session.attempts else 0
    val durationMin = session.durationSeconds / 60
    val durationSec = session.durationSeconds % 60

    Card {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${session.sport.replaceFirstChar { it.uppercase() }} · ${session.gameMode}",
                    fontWeight = FontWeight.Bold
                )
                Text(dateFormat.format(session.startTimestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(4.dp))
                Text("${session.makes}/${session.attempts} ($pct%) · ${durationMin}m ${durationSec}s · Best streak ${session.peakStreak}", style = MaterialTheme.typography.bodySmall)
                if (session.homeScore > 0 || session.awayScore > 0) {
                    Text("Final: ${session.homeScore} - ${session.awayScore}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete session", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
