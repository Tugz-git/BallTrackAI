package com.balltrack.ai.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.balltrack.ai.coach.ScoreSoundPlayer

/**
 * Settings section: lets the user toggle each sound event on/off and choose
 * between the bundled default sound or a custom audio file from their own
 * device storage. File picking uses the system document picker — Claude/this
 * app never sees or transmits the audio file anywhere.
 */
@Composable
fun SoundSettingsScreen(soundPlayer: ScoreSoundPlayer) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Sound Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        ScoreSoundPlayer.SoundEvent.entries.forEach { event ->
            SoundEventRow(event = event, soundPlayer = soundPlayer)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun SoundEventRow(event: ScoreSoundPlayer.SoundEvent, soundPlayer: ScoreSoundPlayer) {
    var enabled by remember { mutableStateOf(soundPlayer.isEventEnabled(event)) }
    var customUri by remember { mutableStateOf(soundPlayer.getCustomSoundUri(event)) }

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            soundPlayer.setCustomSound(event, uri)
            customUri = uri.toString()
        }
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = labelFor(event), style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    soundPlayer.setEventEnabled(event, it)
                }
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                text = if (customUri != null) "Custom sound set" else "Using default sound",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )

            TextButton(onClick = { pickFileLauncher.launch(arrayOf("audio/*")) }) {
                Text("Choose file")
            }

            if (customUri != null) {
                TextButton(onClick = {
                    soundPlayer.clearCustomSound(event)
                    customUri = null
                }) {
                    Text("Reset to default")
                }
            }

            IconButton(onClick = { soundPlayer.play(event) }) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.PlayArrow,
                    contentDescription = "Preview sound"
                )
            }
        }
    }
}

private fun labelFor(event: ScoreSoundPlayer.SoundEvent): String = when (event) {
    ScoreSoundPlayer.SoundEvent.MAKE -> "On made shot"
    ScoreSoundPlayer.SoundEvent.MISS -> "On missed shot"
    ScoreSoundPlayer.SoundEvent.STREAK -> "On streak milestone"
    ScoreSoundPlayer.SoundEvent.SESSION_END -> "On session end"
}
