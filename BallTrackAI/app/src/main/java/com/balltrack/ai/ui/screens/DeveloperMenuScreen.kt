package com.balltrack.ai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.balltrack.ai.utils.FeatureFlag
import com.balltrack.ai.utils.FeatureFlagsRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperMenuScreen(flagsRepository: FeatureFlagsRepository, onBack: () -> Unit) {
    val flags by flagsRepository.flags.collectAsState()
    var showResetConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer Menu") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Card(
                modifier = Modifier.padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Text(
                    "Turn individual features on or off. Each one explains what it does and what turning it off changes — nothing here is hidden behavior.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            LazyColumnFlags(flags = flags, flagsRepository = flagsRepository)

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showResetConfirm = true },
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
            ) {
                Icon(Icons.Filled.RestartAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Reset All to Defaults")
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset all features to default?") },
            text = { Text("This turns every feature back to its normal on/off state. It does not delete any data (sessions, face data, etc).") },
            confirmButton = {
                TextButton(onClick = { flagsRepository.resetAllToDefaults(); showResetConfirm = false }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun LazyColumnFlags(flags: Map<FeatureFlag, Boolean>, flagsRepository: FeatureFlagsRepository) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        FeatureFlag.entries.forEach { flag ->
            val enabled = flags[flag] ?: flag.defaultEnabled
            FeatureFlagRow(flag = flag, enabled = enabled, onToggle = { flagsRepository.setEnabled(flag, it) })
            Divider(modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
private fun FeatureFlagRow(flag: FeatureFlag, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(flag.displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(flag.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}
