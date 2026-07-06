package com.balltrack.ai.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.balltrack.ai.coach.AiCoach
import com.balltrack.ai.coach.ScoreSoundPlayer
import com.balltrack.ai.coach.SessionStats
import com.balltrack.ai.data.db.SportTrackDatabase
import com.balltrack.ai.game.GameRules
import com.balltrack.ai.game.GameState
import com.balltrack.ai.game.Player
import com.balltrack.ai.game.Possession
import com.balltrack.ai.game.Team
import com.balltrack.ai.game.TeamScoringEngine
import com.balltrack.ai.game.ViolationReplayManager
import com.balltrack.ai.game.ViolationType
import com.balltrack.ai.sport.basketball.BallTracker
import com.balltrack.ai.sport.basketball.DribbleCounter
import com.balltrack.ai.sport.basketball.ShotDetector
import com.balltrack.ai.vision.CourtDetector
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionUiState(
    val sport: String = "basketball",
    val isSessionActive: Boolean = false,
    val gameState: GameState = GameState(GameRules.STREET_BALL),
    val courtDetection: CourtDetector.CourtDetectionResult? = null,
    val dribbleCount: Int = 0,
    val coachMessage: String = "",
    val isCourtDetecting: Boolean = true,
    val selectedRules: GameRules = GameRules.STREET_BALL,
    val shotClockSeconds: Int? = null,
    val elapsedSeconds: Int = 0,
    val ttsEnabled: Boolean = true,
    val isDribbleMode: Boolean = false,
    val ballTrail: List<Pair<Float, Float>> = emptyList() // normalized 0-1 points, oldest first — the ball's recent path
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val db = SportTrackDatabase.getInstance(app)
    val coach = AiCoach(app)
    val soundPlayer = ScoreSoundPlayer(app)
    val courtDetector = CourtDetector()
    val shotDetector = ShotDetector(shootingHandIsRight = true)
    val ballTracker = BallTracker()
    val dribbleCounter = DribbleCounter()
    val violationManager = ViolationReplayManager(app)
    val featureFlags = com.balltrack.ai.utils.FeatureFlagsRepository(app)

    // Team roster for the current match, set via setTeams() from TeamSetupScreen.
    // null = solo session, no auto-attribution needed (score goes to a single player).
    private var scoringEngine: TeamScoringEngine? = null
    private val _teamA = MutableStateFlow<List<Player>>(emptyList())
    private val _teamB = MutableStateFlow<List<Player>>(emptyList())
    val teamA: StateFlow<List<Player>> = _teamA.asStateFlow()
    val teamB: StateFlow<List<Player>> = _teamB.asStateFlow()

    // Path to the currently-recording match video. Set by LiveSessionScreen once
    // MatchVideoRecorder actually starts recording — until then this stays null
    // and flagViolation() truthfully reports no clip is available.
    private var currentMatchVideoPath: String? = null
    private var lastWristPosition: Pair<Float, Float>? = null

    fun setMatchVideoPath(path: String?) { currentMatchVideoPath = path }

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private var frameCount = 0
    private var sessionStartTime = 0L
    private var shotClockJob: kotlinx.coroutines.Job? = null

    fun selectSport(sport: String) = _uiState.update { it.copy(sport = sport) }
    fun selectRules(rules: GameRules) = _uiState.update { it.copy(selectedRules = rules, gameState = GameState(rules)) }
    fun toggleDribbleMode() = _uiState.update { it.copy(isDribbleMode = !it.isDribbleMode) }
    fun toggleTts() = _uiState.update { it.copy(ttsEnabled = !it.ttsEnabled) }

    /** Call from TeamSetupScreen before starting a multi-player match. */
    fun setTeams(teamA: List<Player>, teamB: List<Player>) {
        _teamA.value = teamA
        _teamB.value = teamB
        scoringEngine = if (teamA.isNotEmpty() && teamB.isNotEmpty()) TeamScoringEngine(teamA, teamB) else null
    }

    fun startSession() {
        sessionStartTime = System.currentTimeMillis()
        shotDetector.reset(); ballTracker.reset(); dribbleCounter.reset()
        _uiState.update { it.copy(isSessionActive = true, elapsedSeconds = 0, coachMessage = "Session started. Good luck!", ballTrail = emptyList()) }
        startElapsedTimer()
        startShotClock()
    }

    fun endSession() {
        val state = _uiState.value
        _uiState.update { it.copy(isSessionActive = false) }
        shotClockJob?.cancel()
        soundPlayer.play(ScoreSoundPlayer.SoundEvent.SESSION_END)
        viewModelScope.launch {
            saveSessionToDatabase(state)
            requestCoachTip()
        }
    }

    private suspend fun saveSessionToDatabase(state: SessionUiState) {
        val gs = state.gameState
        val entity = com.balltrack.ai.data.db.SessionEntity(
            sport = state.sport,
            gameMode = state.selectedRules.name,
            startTimestamp = sessionStartTime,
            endTimestamp = System.currentTimeMillis(),
            durationSeconds = state.elapsedSeconds,
            makes = gs.sessionMakes,
            attempts = gs.sessionAttempts,
            homeScore = gs.score.home,
            awayScore = gs.score.away,
            avgReleaseAngle = null, // TODO: wire rolling average once tracked per-shot
            avgShotSpeedSeconds = null,
            peakStreak = gs.currentStreak,
            videoPath = null, // match recording not yet implemented — see SETUP.md
            courtZoneJson = null,
            rulesJson = state.selectedRules.toJson()
        )
        try {
            db.sessionDao().insert(entity)
        } catch (_: Exception) {
            // Saving session history should never crash a session end — if the
            // DB write fails, the player still sees their final score, they just
            // won't have history for this one session.
        }
    }

    /** Called every frame from the camera analyzer */
    fun onPoseResult(result: PoseLandmarkerResult, timestampMs: Long, bitmap: Bitmap) {
        val state = _uiState.value
        if (!state.isSessionActive) return

        // Dribble mode — count dribbles instead of shots
        if (state.isDribbleMode) {
            if (!featureFlags.isEnabled(com.balltrack.ai.utils.FeatureFlag.DRIBBLE_COUNTER)) return
            val count = dribbleCounter.onFrame(result)
            _uiState.update { it.copy(dribbleCount = count) }
            return
        }

        // Shot mode
        val shotEvent = shotDetector.onFrame(result, timestampMs)
        if (shotEvent != null) lastWristPosition = shotEvent.wristX to shotEvent.wristY
        if (shotEvent == null) return

        // Periodic court detection (every 90 frames ~= every 3s at 30fps)
        frameCount++
        if (featureFlags.isEnabled(com.balltrack.ai.utils.FeatureFlag.COURT_AUTO_DETECT) &&
            frameCount % 90 == 0 && state.courtDetection == null) {
            viewModelScope.launch {
                val detection = courtDetector.detect(bitmap)
                _uiState.update { it.copy(courtDetection = detection, isCourtDetecting = false) }
            }
        }

        // Ball tracking for make/miss — fully gated by its own flag, since this
        // is the most failure-prone feature (basic color tracking, not a
        // trained model). With it off, shots are still detected but make/miss
        // isn't scored automatically.
        if (!featureFlags.isEnabled(com.balltrack.ai.utils.FeatureFlag.BALL_TRACKING)) return

        val ball = ballTracker.findBall(bitmap, timestampMs)
        val hoopRegion = state.courtDetection?.hoopRegion
        val isMake: Boolean? = if (hoopRegion != null) ballTracker.isMake(hoopRegion) else null

        // Keep the on-screen ball path in sync with what's actually tracked,
        // regardless of make/miss outcome below — this is a visual trail, not a
        // scoring signal, so it updates every frame the ball is found.
        _uiState.update { it.copy(ballTrail = ballTracker.trailPoints()) }

        // Determine court zone
        val zone = ball?.let { b ->
            state.courtDetection?.courtZones?.let { zones ->
                courtDetector.zoneForPosition(b.x, b.y, zones)
            }
        }

        // Point value based on zone
        val points = when {
            zone?.isThreePoint == true && state.selectedRules.threePointsEnabled -> 3
            else -> if (state.selectedRules.twoPointsEnabled) 2 else 1
        }

        // isMake can be true (make), false (miss), or null (ball tracking
        // lost the ball / inconclusive). null must NOT be scored as a miss —
        // that would silently inflate miss counts every time tracking just
        // failed to follow the ball, which happens often in real conditions.
        if (isMake == null) return

        val newGameState = if (isMake) {
            soundPlayer.play(ScoreSoundPlayer.SoundEvent.MAKE)
            state.gameState.withMake(Possession.HOME, points).also {
                if (it.currentStreak >= 3) soundPlayer.play(ScoreSoundPlayer.SoundEvent.STREAK)
            }
        } else {
            soundPlayer.play(ScoreSoundPlayer.SoundEvent.MISS)
            state.gameState.withMiss(Possession.HOME)
        }

        // Team mode: credit whichever player currently has possession, based on
        // game-flow logic (make-it-take-it / alternating), not facial recognition.
        // This is the auto-attribution approach — see chat history for why full
        // automatic "who took the shot" detection isn't built: it would require
        // multi-person pose tracking accurate enough to not misattribute shots,
        // which isn't reliable on this hardware. Turn-based logic gets the same
        // result correctly as long as players take turns normally; if someone
        // shoots out of turn, correct it manually on the Team screen after.
        scoringEngine?.let { engine ->
            if (!featureFlags.isEnabled(com.balltrack.ai.utils.FeatureFlag.TEAM_AUTO_SCORING)) return@let
            val creditedPlayer = engine.likelyCurrentShooter()
            if (creditedPlayer != null) {
                val (newA, newB) = if (isMake) engine.recordMake(creditedPlayer, points, state.selectedRules)
                                    else engine.recordMiss(creditedPlayer)
                _teamA.value = newA
                _teamB.value = newB
            }
        }

        _uiState.update { it.copy(gameState = newGameState, shotClockSeconds = state.selectedRules.shotClock) }

        // Check game over
        if (newGameState.isGameOver) {
            soundPlayer.play(ScoreSoundPlayer.SoundEvent.SESSION_END)
            endSession()
        }

        // Coach tip every 10 shots
        if (featureFlags.isEnabled(com.balltrack.ai.utils.FeatureFlag.AI_COACH) &&
            newGameState.sessionAttempts % 10 == 0 && newGameState.sessionAttempts > 0) {
            viewModelScope.launch { requestCoachTip() }
        }
    }

    /**
     * Manual ref control for HORSE mode. [to] is the side that missed the shot
     * they needed to match, so they receive the next letter. Not AI-inferred —
     * the camera can't reliably tell whether two different people's shots
     * "matched," so this stays a deliberate human call.
     */
    fun assignHorseLetter(to: Possession) {
        val newState = _uiState.value.gameState.withHorseLetter(to)
        _uiState.update { it.copy(gameState = newState) }
        if (newState.isGameOver) {
            soundPlayer.play(ScoreSoundPlayer.SoundEvent.SESSION_END)
            endSession()
        }
    }

    fun flagViolation(type: ViolationType) {
        if (!featureFlags.isEnabled(com.balltrack.ai.utils.FeatureFlag.VIOLATION_FLAGGING)) return
        val videoPath = currentMatchVideoPath
        if (videoPath == null) {
            // No active recording to clip from yet — surface this honestly
            // rather than pretending a clip exists.
            _uiState.update { it.copy(coachMessage = "No recording active — violation noted but no replay clip available.") }
            return
        }
        viewModelScope.launch {
            violationManager.flagViolation(
                type = type,
                currentMatchVideoPath = videoPath,
                currentTimestampMs = System.currentTimeMillis() - sessionStartTime,
                flaggedByPlayerId = null,
                zoomFocusPoint = lastWristPosition
            )
        }
    }

    private suspend fun requestCoachTip() {
        val state = _uiState.value
        val gs = state.gameState
        val stats = SessionStats(
            sport = state.sport,
            makes = gs.sessionMakes,
            attempts = gs.sessionAttempts,
            avgReleaseAngle = null, // TODO: track rolling average in next iteration
            avgShotSpeed = null,
            currentStreak = gs.currentStreak,
            dominantZone = null,
            sessionDurationMin = state.elapsedSeconds / 60
        )
        val tip = coach.getTip(stats)
        _uiState.update { it.copy(coachMessage = tip) }
        if (state.ttsEnabled && featureFlags.isEnabled(com.balltrack.ai.utils.FeatureFlag.TTS_VOICE_COACH)) {
            coach.speak(tip, getApplication())
        }
    }

    private fun startElapsedTimer() {
        viewModelScope.launch {
            while (_uiState.value.isSessionActive) {
                delay(1000)
                _uiState.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
            }
        }
    }

    private fun startShotClock() {
        val shotClockSecs = _uiState.value.selectedRules.shotClock ?: return
        _uiState.update { it.copy(shotClockSeconds = shotClockSecs) }
        shotClockJob = viewModelScope.launch {
            while (_uiState.value.isSessionActive) {
                delay(1000)
                val current = _uiState.value.shotClockSeconds ?: return@launch
                if (current <= 1) {
                    _uiState.update { it.copy(shotClockSeconds = shotClockSecs, coachMessage = "Shot clock! Turnover.") }
                } else {
                    _uiState.update { it.copy(shotClockSeconds = current - 1) }
                }
            }
        }
    }

    override fun onCleared() {
        coach.shutdown()
        soundPlayer.shutdown()
        super.onCleared()
    }
}
