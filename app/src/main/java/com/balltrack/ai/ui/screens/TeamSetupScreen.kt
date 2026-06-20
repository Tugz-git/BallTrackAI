package com.balltrack.ai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.balltrack.ai.game.Player
import com.balltrack.ai.game.Roster
import com.balltrack.ai.game.RosterRepository
import com.balltrack.ai.game.Team
import com.balltrack.ai.vision.FaceRecognitionManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamSetupScreen(
    rosterRepository: RosterRepository,
    faceManager: FaceRecognitionManager,
    featureFlags: com.balltrack.ai.utils.FeatureFlagsRepository,
    onStart: (List<Player>, List<Player>) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var savedRosters by remember { mutableStateOf<List<Roster>>(emptyList()) }
    var teamA by remember { mutableStateOf<List<Player>>(emptyList()) }
    var teamB by remember { mutableStateOf<List<Player>>(emptyList()) }
    var newPlayerName by remember { mutableStateOf("") }
    var addingToTeam by remember { mutableStateOf(Team.A) }
    var consentTarget by remember { mutableStateOf<Player?>(null) }

    LaunchedEffect(Unit) { savedRosters = rosterRepository.loadAll() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Team Setup") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } }
        )
    }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {

            if (savedRosters.isNotEmpty()) {
                Text("Saved Matchups", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                    items(savedRosters) { roster ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            onClick = { teamA = roster.teamA; teamB = roster.teamB }
                        ) {
                            Text(roster.format(), modifier = Modifier.padding(12.dp))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Divider()
                Spacer(Modifier.height(16.dp))
            }

            Text("Add Players", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newPlayerName,
                    onValueChange = { newPlayerName = it },
                    label = { Text("Player name") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = addingToTeam == Team.A, onClick = { addingToTeam = Team.A }, label = { Text("Team A") })
                Spacer(Modifier.width(4.dp))
                FilterChip(selected = addingToTeam == Team.B, onClick = { addingToTeam = Team.B }, label = { Text("Team B") })
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (newPlayerName.isNotBlank()) {
                        val player = Player(id = java.util.UUID.randomUUID().toString(), name = newPlayerName.trim())
                        if (addingToTeam == Team.A) teamA = teamA + player else teamB = teamB + player
                        if (featureFlags.isEnabled(com.balltrack.ai.utils.FeatureFlag.FACE_RECOGNITION)) {
                            consentTarget = player
                        }
                        newPlayerName = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add Player")
            }

            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TeamColumn("Team A", teamA, onRemove = { p -> teamA = teamA - p }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                TeamColumn("Team B", teamB, onRemove = { p -> teamB = teamB - p }, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    scope.launch {
                        if (teamA.isNotEmpty() && teamB.isNotEmpty()) {
                            rosterRepository.saveRoster(Roster(name = "${teamA.first().name} vs ${teamB.first().name}", teamA = teamA, teamB = teamB))
                        }
                    }
                    onStart(teamA, teamB)
                },
                enabled = teamA.isNotEmpty() && teamB.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Start Match", fontWeight = FontWeight.Bold)
            }
        }
    }

    consentTarget?.let { player ->
        FaceConsentScreen(
            playerName = player.name,
            onAccept = { consentTarget = null /* actual capture flow triggered from camera screen */ },
            onDecline = { consentTarget = null }
        )
    }
}

@Composable
private fun TeamColumn(label: String, players: List<Player>, onRemove: (Player) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        players.forEach { player ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(player.name, modifier = Modifier.weight(1f))
                IconButton(onClick = { onRemove(player) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
