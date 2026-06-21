package com.balltrack.ai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Must be shown and explicitly accepted before FaceRecognitionManager.registerPlayer()
 * is ever called. This applies to every player being registered — including
 * players who are not the phone's owner.
 */
@Composable
fun FaceConsentScreen(playerName: String, onAccept: () -> Unit, onDecline: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Face, contentDescription = null, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("Face Recognition Setup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "This will let the app recognize $playerName automatically in future sessions, " +
                    "so makes get scored to the right player without manual tapping.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Text("What this means:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                BulletPoint("A numeric face signature is stored ONLY on this phone — never uploaded anywhere, ever.")
                BulletPoint("No photo is saved. The stored data cannot be turned back into a picture of $playerName's face.")
                BulletPoint("$playerName should know this is happening and agree to it before you continue.")
                BulletPoint("You can delete this data anytime in Settings → Face Data → Delete All.")
                BulletPoint("This is optional — you can skip this and just tap to assign scores manually instead.")
            }
        }

        Spacer(Modifier.height(8.dp))
        Row {
            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(6.dp))
            Text(
                "Only proceed if $playerName is present and has agreed to this.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
            Text("$playerName Agrees — Set Up Recognition")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
            Text("Skip — Use Manual Tap Instead")
        }
    }
}

@Composable
private fun BulletPoint(text: String) {
    Row(modifier = Modifier.padding(top = 4.dp)) {
        Text("•  ", style = MaterialTheme.typography.bodySmall)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
