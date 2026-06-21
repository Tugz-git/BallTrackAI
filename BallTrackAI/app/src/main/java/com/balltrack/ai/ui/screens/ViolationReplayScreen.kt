package com.balltrack.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.balltrack.ai.game.ViolationEvent
import com.balltrack.ai.game.ViolationReplayManager

/**
 * Full-screen overlay shown when a violation is flagged. Plays the clipped
 * moment in slow motion, zoomed to the flagged area. Shows a 30-second
 * countdown — if no one taps Confirm or Dismiss, the clip auto-deletes.
 */
@Composable
fun ViolationReplayOverlay(
    event: ViolationEvent,
    replayManager: ViolationReplayManager,
    onResolved: () -> Unit
) {
    val secondsLeft by replayManager.responseSecondsLeft.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Real slow-mo + zoom playback of the locally-saved clip.
        com.balltrack.ai.ui.components.SlowMoZoomPlayer(
            localClipPath = event.videoClipPath,
            zoomFocusPoint = event.zoomFocusPoint,
            modifier = Modifier.fillMaxSize()
        )

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color(0xCC000000))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("${event.type.emoji} ${event.type.label} flagged", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Slow-motion replay — review the call", color = Color(0xAAFFFFFF), fontSize = 12.sp)
            }
            CountdownRing(secondsLeft = secondsLeft, total = 30)
        }

        // Bottom action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { replayManager.confirmViolation(keepClip = false); onResolved() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242)),
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Dismiss & Delete")
            }
            Button(
                onClick = { replayManager.confirmViolation(keepClip = true); onResolved() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Confirm — Keep Clip")
            }
        }

        Text(
            text = if (secondsLeft > 0) "Auto-deletes in ${secondsLeft}s if no response" else "Deleting…",
            color = Color(0xAAFFFFFF),
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)
        )
    }
}

@Composable
fun CountdownRing(secondsLeft: Int, total: Int) {
    val progress = secondsLeft.toFloat() / total
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (secondsLeft <= 5) Color(0xFFFF5252) else Color(0xFF333333)),
        contentAlignment = Alignment.Center
    ) {
        Text("$secondsLeft", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/** Small floating button row shown during live recording to flag a violation in real time. */
@Composable
fun ViolationFlagBar(onFlag: (com.balltrack.ai.game.ViolationType) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color(0xCC000000), RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        com.balltrack.ai.game.ViolationType.entries.forEach { type ->
            TextButton(onClick = { onFlag(type) }) {
                Text("${type.emoji}", fontSize = 16.sp)
            }
        }
    }
}
