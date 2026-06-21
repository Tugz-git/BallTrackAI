package com.balltrack.ai.coach

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.Locale
import java.util.concurrent.TimeUnit

// ─── Datastore ─────────────────────────────────────────────────────────────

private val Context.coachDataStore by preferencesDataStore("coach_prefs")

// ─── Mistral API interface ─────────────────────────────────────────────────

interface MistralApi {
    @POST("api/generate")
    suspend fun generate(@Body req: MistralRequest): MistralResponse
}

data class MistralRequest(val model: String = "mistral", val prompt: String, val stream: Boolean = false)
data class MistralResponse(val response: String)

// ─── Numeric-only stats payload (never images, never body landmarks) ────────

data class SessionStats(
    val sport: String,
    val makes: Int,
    val attempts: Int,
    val avgReleaseAngle: Float?,
    val avgShotSpeed: Float?,
    val currentStreak: Int,
    val dominantZone: String?,
    val sessionDurationMin: Int
)

// ─── Coach ─────────────────────────────────────────────────────────────────

class AiCoach(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var mistralApi: MistralApi? = null

    private val serverUrlKey = stringPreferencesKey("mistral_server_url")
    private val ttsEnabledKey = booleanPreferencesKey("tts_enabled")

    init {
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            tts?.language = Locale.US
        }
    }

    suspend fun configure() {
        val url = context.coachDataStore.data.map { it[serverUrlKey] }.first()
        if (!url.isNullOrBlank()) buildRetrofit(url)
    }

    fun setServerUrl(url: String) {
        buildRetrofit(url)
    }

    private fun buildRetrofit(baseUrl: String) {
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        mistralApi = Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MistralApi::class.java)
    }

    /**
     * Get a coaching tip for the current session stats.
     * Tries the Mistral server first; falls back to local rule-based tip on failure.
     * ONLY numeric stats are ever sent to the server — no images, no body data.
     */
    suspend fun getTip(stats: SessionStats): String = withContext(Dispatchers.IO) {
        mistralApi?.let { api ->
            try {
                val prompt = buildPrompt(stats)
                api.generate(MistralRequest(prompt = prompt)).response.trim()
            } catch (_: Exception) {
                localTip(stats) // server unreachable → fall back silently
            }
        } ?: localTip(stats)
    }

    private fun buildPrompt(s: SessionStats): String = """
        You are a basketball coach AI. Give ONE short, specific coaching tip (max 2 sentences) 
        based on these session stats. Be encouraging but honest.
        
        Sport: ${s.sport}
        Makes/Attempts: ${s.makes}/${s.attempts} (${if (s.attempts > 0) (s.makes * 100 / s.attempts) else 0}%)
        Avg release angle: ${s.avgReleaseAngle?.let { "%.1f°".format(it) } ?: "N/A"}
        Avg shot speed: ${s.avgShotSpeed?.let { "%.2fs".format(it) } ?: "N/A"}
        Current streak: ${s.currentStreak}
        Dominant zone: ${s.dominantZone ?: "unknown"}
        Session duration: ${s.sessionDurationMin} min
        
        Respond with only the coaching tip, no preamble.
    """.trimIndent()

    fun localTip(stats: SessionStats): String {
        val pct = if (stats.attempts > 0) stats.makes * 100 / stats.attempts else 0
        val angle = stats.avgReleaseAngle
        return when {
            stats.currentStreak >= 5 -> "You're on fire — ${stats.currentStreak} in a row! Keep that rhythm."
            angle != null && angle < 38f -> "Your release is coming out flat at ${angle.toInt()}°. Aim for 45–55° for better arc."
            angle != null && angle > 65f -> "That's a very high arc at ${angle.toInt()}°. Slightly flatter shots will improve range."
            pct < 30 && stats.attempts > 5 -> "Shooting at $pct% — slow down and focus on your follow-through on each rep."
            pct > 70 -> "Great shooting at $pct%! You're locked in."
            stats.sessionDurationMin > 30 && pct < 40 -> "You might be fatiguing — your percentage is dropping. Take a water break."
            stats.currentStreak == 0 && stats.attempts > 3 -> "Reset your routine between shots — consistency starts with a consistent setup."
            else -> "Stay focused. ${stats.makes} makes so far — keep building."
        }
    }

    fun speak(text: String, ctx: Context) {
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "coach_tip")
        }
    }

    fun shutdown() { tts?.stop(); tts?.shutdown() }
}
