package com.balltrack.ai.ui.screens

import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.balltrack.ai.ui.MainViewModel
import com.balltrack.ai.vision.PoseFrameAnalyzer
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * Fixed cyan for the live pose skeleton — deliberately NOT tied to the
 * customizable accent color. The tracking overlay is "what the AI sees,"
 * a distinct visual layer from app chrome, so it should read consistently
 * no matter what accent the person picks in Settings.
 */
private val TrackingOverlayColor = Color(0xFF00E5FF)

@Composable
fun LiveSessionScreen(viewModel: MainViewModel, onNavigateToSettings: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var poseResult by remember { mutableStateOf<com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var currentCameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA) }

    // isSessionActive is read inside a camera-thread callback that's created once
    // and reused across frames — a plain captured val would go stale the moment
    // the session starts/stops. This ref is mutated from the main thread each time
    // uiState changes, so the analyzer always sees the current value.
    val isSessionActiveRef = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    LaunchedEffect(state.isSessionActive) { isSessionActiveRef.set(state.isSessionActive) }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val videoRecorder = remember { com.balltrack.ai.vision.MatchVideoRecorder(context) }

    // Release the analyzer thread when this screen leaves composition, otherwise
    // the executor thread leaks for the lifetime of the process.
    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            videoRecorder.stopRecording()
        }
    }

    // Start/stop the actual local match recording in lockstep with the session,
    // so flagViolation() has a real file to clip from instead of always
    // reporting "no recording active."
    LaunchedEffect(state.isSessionActive) {
        if (state.isSessionActive && viewModel.featureFlags.isEnabled(com.balltrack.ai.utils.FeatureFlag.MATCH_RECORDING)) {
            val path = videoRecorder.startRecording(lifecycleOwner)
            viewModel.setMatchVideoPath(path)
        } else {
            videoRecorder.stopRecording()
            viewModel.setMatchVideoPath(null)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Camera preview ────────────────────────────────────────────────
        val previewViewRef = remember { mutableStateOf<PreviewView?>(null) }

        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewViewRef.value = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Binding runs exactly once per camera-selector change (back/front toggle),
        // NOT on every recomposition — keyed LaunchedEffect is the correct tool
        // here, unlike the AndroidView update lambda which fires constantly.
        LaunchedEffect(currentCameraSelector, previewViewRef.value) {
            val previewView = previewViewRef.value ?: return@LaunchedEffect
            val provider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val videoCapture = videoRecorder.buildVideoCapture()

            val poseDetector = com.balltrack.ai.vision.PoseDetector(
                context = context,
                onResult = { r, _ -> poseResult = r },
                onError = {}
            )

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    val frameAnalyzer = PoseFrameAnalyzer(poseDetector) { bitmap ->
                        if (isSessionActiveRef.get()) {
                            poseResult?.let { r ->
                                viewModel.onPoseResult(r, System.currentTimeMillis(), bitmap)
                            }
                        }
                    }
                    analysis.setAnalyzer(executor, frameAnalyzer)
                }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, currentCameraSelector, preview, analyzer, videoCapture)
            } catch (_: Exception) {}
        }

        // ── Skeleton overlay ──────────────────────────────────────────────
        poseResult?.let { result ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                val lm = result.landmarks().firstOrNull() ?: return@Canvas
                val connections = listOf(
                    11 to 12, 11 to 13, 13 to 15, 12 to 14, 14 to 16,
                    11 to 23, 12 to 24, 23 to 24, 23 to 25, 25 to 27,
                    24 to 26, 26 to 28
                )
                connections.forEach { (a, b) ->
                    if (a < lm.size && b < lm.size) {
                        drawLine(
                            // Deliberately fixed, not theme-driven: the tracking
                            // overlay should read as "the AI seeing you" — a
                            // distinct layer from the UI chrome — regardless of
                            // whatever accent color is chosen in Settings.
                            color = TrackingOverlayColor,
                            start = Offset(lm[a].x() * size.width, lm[a].y() * size.height),
                            end = Offset(lm[b].x() * size.width, lm[b].y() * size.height),
                            strokeWidth = 4f
                        )
                    }
                }
                lm.take(29).forEach { pt ->
                    drawCircle(TrackingOverlayColor, radius = 8f, center = Offset(pt.x() * size.width, pt.y() * size.height))
                }
            }
        }

        // ── Court detection confidence indicator ──────────────────────────
        state.courtDetection?.let { detection ->
            if (detection.confidence < 0.6f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp)
                        .background(Color(0xBB000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(detection.message, color = Color.Yellow, fontSize = 12.sp)
                }
            }
        }

        // ── AI Coach banner (top of screen) ──────────────────────────────
        if (state.coachMessage.isNotBlank()) {
            CoachBanner(
                message = state.coachMessage,
                ttsEnabled = state.ttsEnabled,
                onToggleTts = { viewModel.toggleTts() },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        // ── HUD overlay (score / angle / speed) ──────────────────────────
        if (state.isSessionActive) {
            SessionHud(
                state = state,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // ── Mini court map (bottom right) ─────────────────────────────────
        state.courtDetection?.let { detection ->
            MiniCourtMap(
                zones = detection.courtZones,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(90.dp)
            )
        }

        // ── Top action bar ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.sport.replaceFirstChar { it.uppercase() },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier
                    .background(Color(0xBB000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
            Row {
                if (state.isSessionActive) {
                    IconButton(onClick = { viewModel.toggleDribbleMode() }) {
                        Icon(
                            if (state.isDribbleMode) Icons.Filled.SportsBasketball else Icons.Filled.FitnessCenter,
                            contentDescription = "Toggle mode",
                            tint = Color.White
                        )
                    }
                }
                com.balltrack.ai.ui.components.CameraSwitchButton(
                    currentSelector = currentCameraSelector,
                    onSwitch = { currentCameraSelector = it }
                )
                IconButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
                }
            }
        }

        // ── AR court lines (for unmarked/concrete courts) ───────────────────
        if (viewModel.featureFlags.isEnabled(com.balltrack.ai.utils.FeatureFlag.AR_COURT_LINES)) {
            state.courtDetection?.hoopRegion?.let { hoop ->
                com.balltrack.ai.ui.components.ArCourtOverlay(
                    hoopRegion = androidx.compose.ui.geometry.Rect(
                        left = hoop.left, top = hoop.top, right = hoop.right, bottom = hoop.bottom
                    ),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // ── Violation flag bar (only during an active session, if enabled) ──
        if (state.isSessionActive && viewModel.featureFlags.isEnabled(com.balltrack.ai.utils.FeatureFlag.VIOLATION_FLAGGING)) {
            ViolationFlagBar(
                onFlag = { type -> viewModel.flagViolation(type) },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
            )
        }

        // ── Start / End session button ────────────────────────────────────
        if (!state.isSessionActive) {
            Button(
                onClick = { viewModel.startSession() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
                    .height(56.dp)
                    .width(200.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("START SESSION", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            }
        } else {
            Button(
                onClick = { viewModel.endSession() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
            ) {
                Text("END SESSION", fontWeight = FontWeight.Bold)
            }
        }

        // ── Violation replay overlay (covers everything when a call is flagged) ──
        val pendingViolation by viewModel.violationManager.pendingViolation.collectAsState()
        pendingViolation?.let { event ->
            ViolationReplayOverlay(
                event = event,
                replayManager = viewModel.violationManager,
                onResolved = {}
            )
        }
    }
}

// ── Coach banner ─────────────────────────────────────────────────────────────

@Composable
fun CoachBanner(message: String, ttsEnabled: Boolean, onToggleTts: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 8.dp, end = 8.dp)
            .background(Color(0xCC1A1A2E), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🏀", fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = message,
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onToggleTts, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = if (ttsEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                contentDescription = "Toggle TTS",
                tint = if (ttsEnabled) MaterialTheme.colorScheme.primary else Color.Gray,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ── Session HUD ──────────────────────────────────────────────────────────────

@Composable
fun SessionHud(state: com.balltrack.ai.ui.SessionUiState, modifier: Modifier = Modifier) {
    val gs = state.gameState
    val pct = if (gs.sessionAttempts > 0) gs.sessionMakes * 100 / gs.sessionAttempts else 0
    val elapsed = "%d:%02d".format(state.elapsedSeconds / 60, state.elapsedSeconds % 60)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(com.balltrack.ai.ui.theme.SportTrackColors.Ink.copy(alpha = 0.78f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Score — the most important number on screen, so it gets the
        // signature corner-bracket frame. Everything else in the HUD stays
        // plain text, per "spend boldness in one place."
        com.balltrack.ai.ui.components.CornerBracketFrame(
            bracketColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = "${gs.score.home} / ${gs.rules.winScore}",
                color = com.balltrack.ai.ui.theme.SportTrackColors.Chalk,
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "$pct% · $elapsed${state.shotClockSeconds?.let { " · ${it}s" } ?: ""}",
            color = com.balltrack.ai.ui.theme.SportTrackColors.ChalkMuted,
            style = MaterialTheme.typography.bodySmall
        )
        if (gs.currentStreak >= 2) {
            Text(
                "${gs.currentStreak} STREAK",
                color = com.balltrack.ai.ui.theme.SportTrackColors.GoldStreak,
                style = MaterialTheme.typography.labelLarge
            )
        }
        if (state.isDribbleMode) {
            Text("Dribbles: ${state.dribbleCount}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun MiniCourtMap(zones: List<com.balltrack.ai.vision.CourtDetector.CourtZone>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        drawRect(Color(0xFF2A5C2A), size = size) // court green background
        zones.forEach { zone ->
            val b = zone.bounds
            drawRect(
                color = if (zone.isThreePoint) Color(0x553F51B5) else Color(0x55FF9800),
                topLeft = Offset(b.left * w, b.top * h),
                size = Size((b.right - b.left) * w, (b.bottom - b.top) * h)
            )
            drawRect(
                color = Color.White.copy(alpha = 0.4f),
                topLeft = Offset(b.left * w, b.top * h),
                size = Size((b.right - b.left) * w, (b.bottom - b.top) * h),
                style = Stroke(1f)
            )
        }
    }
}
