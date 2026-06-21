package com.balltrack.ai.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.balltrack.ai.game.GameRules
import com.balltrack.ai.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameModeScreen(viewModel: MainViewModel, onStart: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var showCustomBuilder by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Choose Game Mode") })
    }) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Default presets
            items(GameRules.defaults) { rules ->
                GameModeCard(
                    rules = rules,
                    isSelected = state.selectedRules.name == rules.name,
                    onClick = {
                        viewModel.selectRules(rules)
                        viewModel.soundPlayer.play(com.balltrack.ai.coach.ScoreSoundPlayer.SoundEvent.MENU_CLICK)
                    }
                )
            }

            // Custom rule builder button
            item {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCustomBuilder = true }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Custom Rules", fontWeight = FontWeight.Bold)
                            Text("Build your own game mode", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            // Sport selector
            item {
                Text("Sport", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("basketball", "volleyball", "football").forEach { sport ->
                        FilterChip(
                            selected = state.sport == sport,
                            onClick = { viewModel.selectSport(sport) },
                            label = { Text(sport.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }

            // Start button
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("START — ${state.selectedRules.name}", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showCustomBuilder) {
        CustomRulesDialog(
            onDismiss = { showCustomBuilder = false },
            onConfirm = { rules ->
                viewModel.selectRules(rules)
                showCustomBuilder = false
            }
        )
    }
}

@Composable
fun GameModeCard(rules: GameRules, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(rules.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(rules.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RuleChip("First to ${rules.winScore}")
                    if (rules.winByTwo) RuleChip("Win by 2")
                    if (rules.makeItTakeIt) RuleChip("Make it take it")
                    if (rules.threePointsEnabled) RuleChip("3s")
                    rules.shotClock?.let { RuleChip("${it}s clock") }
                }
            }
            if (isSelected) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun RuleChip(label: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(label, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp)
    }
}

@Composable
fun CustomRulesDialog(onDismiss: () -> Unit, onConfirm: (GameRules) -> Unit) {
    var name by remember { mutableStateOf("My Rules") }
    var winScore by remember { mutableStateOf("21") }
    var winByTwo by remember { mutableStateOf(true) }
    var makeItTakeIt by remember { mutableStateOf(true) }
    var threeEnabled by remember { mutableStateOf(true) }
    var twoEnabled by remember { mutableStateOf(true) }
    var foulTracking by remember { mutableStateOf(false) }
    var shotClock by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Rules") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Rule set name") }, singleLine = true)
                OutlinedTextField(
                    value = winScore, onValueChange = { winScore = it },
                    label = { Text("First to (points)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = shotClock, onValueChange = { shotClock = it },
                    label = { Text("Shot clock (seconds, blank = none)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                listOf(
                    "Win by 2" to winByTwo, "Make it take it" to makeItTakeIt,
                    "3-pointers" to threeEnabled, "2-pointers" to twoEnabled,
                    "Foul tracking" to foulTracking
                ).forEachIndexed { i, (label, checked) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checked, onCheckedChange = { v ->
                            when (i) { 0 -> winByTwo = v; 1 -> makeItTakeIt = v; 2 -> threeEnabled = v; 3 -> twoEnabled = v; 4 -> foulTracking = v }
                        })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val rules = GameRules(
                    name = name.ifBlank { "Custom" },
                    winScore = winScore.toIntOrNull() ?: 21,
                    winByTwo = winByTwo,
                    makeItTakeIt = makeItTakeIt,
                    clearBall = false,
                    threePointsEnabled = threeEnabled,
                    twoPointsEnabled = twoEnabled,
                    foulTracking = foulTracking,
                    shotClock = shotClock.toIntOrNull(),
                    maxFouls = null,
                    description = "Custom rules"
                )
                onConfirm(rules)
            }) { Text("Start") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
