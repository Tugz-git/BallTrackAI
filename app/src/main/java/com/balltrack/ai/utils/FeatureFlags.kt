package com.balltrack.ai.utils

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Every major feature in the app gets an on/off switch here, with a plain
 * description of what it actually does. The point isn't just "toggle things
 * off" — it's so a feature behaving oddly can be isolated and turned off
 * without guessing, and so you always know what each switch actually controls
 * before flipping it.
 */
enum class FeatureFlag(
    val displayName: String,
    val description: String,
    val defaultEnabled: Boolean
) {
    COURT_AUTO_DETECT(
        "Auto Court Detection",
        "Tries to find the hoop and court automatically using the camera. If this is off, zones and 3-point detection won't work, but shot tracking still does.",
        defaultEnabled = true
    ),
    AR_COURT_LINES(
        "AR Court Lines",
        "Draws 3-point arc and key lines on screen for courts with no painted lines, like a driveway. Only shows up if a hoop is detected.",
        defaultEnabled = true
    ),
    BALL_TRACKING(
        "Ball Tracking (Make/Miss)",
        "Tracks the ball's color on screen to guess make vs miss. This is a basic color-tracking method, not a trained model — it can lose the ball in bad lighting or busy backgrounds. Turning this off disables automatic make/miss detection entirely.",
        defaultEnabled = true
    ),
    SHOT_METRICS(
        "Release Angle & Shot Speed",
        "Measures your arm angle at release and time from catch to release, using body tracking only. Turning this off stops those two numbers from showing, but shot counting still works.",
        defaultEnabled = true
    ),
    DRIBBLE_COUNTER(
        "Dribble Counter",
        "Counts dribbles by tracking your wrist movement up and down. Works best with the front camera facing you.",
        defaultEnabled = true
    ),
    TEAM_AUTO_SCORING(
        "Team Auto-Scoring",
        "When playing with teams, automatically credits makes/misses to whoever's turn it is, based on game rules (make-it-take-it, etc). It cannot see who specifically is holding the ball on a multi-player team, so it guesses by rotation — turn this off to manually assign every shot instead.",
        defaultEnabled = true
    ),
    FACE_RECOGNITION(
        "Face Recognition",
        "Stores a numeric face signature on this phone only (never uploaded) to try to auto-recognize registered players. The matching itself is an early/weak version right now — it may not reliably tell people apart yet. Turning this off hides face setup and disables all matching, but does not delete data already stored (use Face Data settings for that).",
        defaultEnabled = false
    ),
    VIOLATION_FLAGGING(
        "Violation Flagging & Replay",
        "Shows the foul/travel/etc buttons during a session. Tapping one clips the local match recording around that moment for slow-motion review, with a 30-second window to confirm or it auto-deletes. Requires match recording to be on.",
        defaultEnabled = true
    ),
    MATCH_RECORDING(
        "Match Video Recording",
        "Records your session to a local video file on this phone, used only for violation replay clips. Never uploaded anywhere. Turning this off also disables Violation Flagging, since there'd be nothing to clip from. This is the most demanding feature on your phone's hardware — turn it off first if you notice lag.",
        defaultEnabled = true
    ),
    AI_COACH(
        "AI Coach Tips",
        "Shows a coaching tip banner during sessions. Works offline with simple rule-based tips by default. If you've set a server URL in Settings, it sends ONLY numbers (makes, attempts, angle) to get richer tips from your own Mistral server — never images or video.",
        defaultEnabled = true
    ),
    TTS_VOICE_COACH(
        "Voice Coach (Text-to-Speech)",
        "Reads AI Coach tips out loud using your phone's built-in text-to-speech. No audio is recorded or sent anywhere for this — it only speaks text that was already generated.",
        defaultEnabled = true
    ),
    SOUND_EFFECTS(
        "Sound Effects",
        "Plays a sound on makes, misses, streaks, and session end. Each one can be customized or turned off individually further down in Settings.",
        defaultEnabled = true
    ),
    SHOT_CLOCK(
        "Shot Clock",
        "Shows a countdown timer during Pro Ball mode (or any custom mode with a shot clock set). Purely a visual timer, doesn't affect tracking.",
        defaultEnabled = true
    )
}

class FeatureFlagsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("feature_flags", Context.MODE_PRIVATE)

    private val _flags = MutableStateFlow(loadAll())
    val flags = _flags.asStateFlow()

    fun isEnabled(flag: FeatureFlag): Boolean = _flags.value[flag] ?: flag.defaultEnabled

    fun setEnabled(flag: FeatureFlag, enabled: Boolean) {
        prefs.edit { putBoolean(flag.name, enabled) }
        _flags.value = _flags.value.toMutableMap().apply { put(flag, enabled) }
    }

    fun resetAllToDefaults() {
        prefs.edit { clear() }
        _flags.value = FeatureFlag.entries.associateWith { it.defaultEnabled }
    }

    private fun loadAll(): Map<FeatureFlag, Boolean> =
        FeatureFlag.entries.associateWith { prefs.getBoolean(it.name, it.defaultEnabled) }
}
