package com.balltrack.ai.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.balltrack.ai.coach.ScoreSoundPlayer
import com.balltrack.ai.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenFaceData: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenDeveloperMenu: () -> Unit = {},
    accentPrefs: com.balltrack.ai.ui.theme.AccentPreferences? = null,
    onAccentChanged: (androidx.compose.ui.graphics.Color) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    var serverUrl by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Privacy info ──────────────────────────────────────────────
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Security, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Privacy First", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Camera frames and body tracking stay 100% on this device. " +
                            "If you set up an AI coach server, only numeric stats are ever sent — " +
                            "no images, no video, no body data.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // ── AI Coach server ────────────────────────────────────────────
            Text("AI Coach Server (Optional)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Run Ollama + Mistral on your own server (Oracle Cloud free tier recommended). " +
                "Only numeric session stats are ever sent to this address.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Mistral server URL (e.g. http://your-ip:11434)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    if (serverUrl.isNotBlank()) {
                        IconButton(onClick = { viewModel.coach.setServerUrl(serverUrl) }) {
                            Icon(Icons.Filled.Check, "Save server URL")
                        }
                    }
                }
            )

            // ── TTS ────────────────────────────────────────────────────────
            Text("Voice Coach", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Text-to-speech callouts", modifier = Modifier.weight(1f))
                Switch(checked = state.ttsEnabled, onCheckedChange = { viewModel.toggleTts() })
            }

            // ── Sound settings ─────────────────────────────────────────────
            Text("Sounds", style = MaterialTheme.typography.titleMedium)
            ScoreSoundPlayer.SoundEvent.entries.forEach { event ->
                SoundRow(event = event, soundPlayer = viewModel.soundPlayer)
            }

            // ── History ────────────────────────────────────────────────────
            Text("Session History", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.History, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("View Past Sessions")
            }

            // ── Face data ──────────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            Text("Face Recognition Data", style = MaterialTheme.typography.titleMedium)
            Text(
                "Stored locally only, used to auto-score known players. You can review or delete this anytime.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = onOpenFaceData, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Face, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Manage Face Data")
            }

            // ── Appearance ────────────────────────────────────────────────
            if (accentPrefs != null) {
                var selectedAccent by remember { mutableStateOf(accentPrefs.accentColor) }
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Pick the accent color used across buttons, the score frame, and highlights.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    com.balltrack.ai.ui.theme.AccentPreferences.PRESETS.forEach { (name, color) ->
                        val isSelected = selectedAccent == color
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    shape = CircleShape
                                )
                                .clickable {
                                    accentPrefs.accentColor = color
                                    selectedAccent = color
                                    onAccentChanged(color)
                                }
                        )
                    }
                }
            }

            // ── Developer menu ───────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            Text("Advanced", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onOpenDeveloperMenu, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Tune, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Developer Menu — Feature Toggles")
            }

            // ── About ──────────────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            Text("SportTrack AI v1.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Text("All tracking is local. No account required. Free forever.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun SoundRow(event: ScoreSoundPlayer.SoundEvent, soundPlayer: ScoreSoundPlayer) {
    var enabled by remember { mutableStateOf(soundPlayer.isEnabled(event)) }
    var hasCustom by remember { mutableStateOf(soundPlayer.customUri(event) != null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { soundPlayer.setCustomSound(event, it); hasCustom = true }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(event.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() })
            if (hasCustom) Text("Custom sound", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = { soundPlayer.play(event) }) {
            Icon(Icons.Filled.PlayArrow, "Preview")
        }
        IconButton(onClick = { picker.launch(arrayOf("audio/*")) }) {
            Icon(Icons.Filled.AudioFile, "Pick sound")
        }
        if (hasCustom) {
            IconButton(onClick = { soundPlayer.clearCustom(event); hasCustom = false }) {
                Icon(Icons.Filled.RestartAlt, "Reset to default")
            }
        }
        Switch(checked = enabled, onCheckedChange = { soundPlayer.setEnabled(event, it); enabled = it })
    }
}
