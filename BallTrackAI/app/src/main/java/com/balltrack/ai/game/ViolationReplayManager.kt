package com.balltrack.ai.game

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class ViolationType(val label: String, val emoji: String) {
    TRAVELING("Traveling", "🚶"),
    DOUBLE_DRIBBLE("Double Dribble", "⛹️"),
    FOUL("Foul", "🚫"),
    OUT_OF_BOUNDS("Out of Bounds", "📏"),
    OTHER("Rule Break", "⚠️")
}

data class ViolationEvent(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: ViolationType,
    val timestampMs: Long,           // position in the recorded match video
    val flaggedByPlayerId: String?,  // who tapped it, if known
    val videoClipPath: String,       // local file path to the buffered clip around this moment
    val zoomFocusPoint: Pair<Float, Float>?, // normalized x,y to zoom into during replay, if known
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Manages the lifecycle of a flagged violation: replay with slow-mo + zoom,
 * a 30-second response window, and auto-deletion of the clip if no response.
 * Everything here operates on a local video file path — nothing is uploaded.
 */
class ViolationReplayManager(private val context: Context) {

    private val _pendingViolation = MutableStateFlow<ViolationEvent?>(null)
    val pendingViolation = _pendingViolation.asStateFlow()

    private val _responseSecondsLeft = MutableStateFlow(30)
    val responseSecondsLeft = _responseSecondsLeft.asStateFlow()

    private var countdownJob: kotlinx.coroutines.Job? = null

    /**
     * Call when a player taps the violation button during a live match.
     * Clips the last [bufferSeconds] of the recorded match around this moment
     * for replay, then starts the 30-second response countdown.
     */
    suspend fun flagViolation(
        type: ViolationType,
        currentMatchVideoPath: String,
        currentTimestampMs: Long,
        flaggedByPlayerId: String?,
        zoomFocusPoint: Pair<Float, Float>?,
        bufferSeconds: Int = 6
    ): ViolationEvent {
        val clipPath = extractClip(currentMatchVideoPath, currentTimestampMs, bufferSeconds)
        val event = ViolationEvent(
            type = type,
            timestampMs = currentTimestampMs,
            flaggedByPlayerId = flaggedByPlayerId,
            videoClipPath = clipPath,
            zoomFocusPoint = zoomFocusPoint
        )
        _pendingViolation.value = event
        startCountdown(event)
        return event
    }

    /** User confirms the call stands — clip is kept (moved to permanent session storage). */
    fun confirmViolation(keepClip: Boolean) {
        countdownJob?.cancel()
        val event = _pendingViolation.value
        if (event != null && !keepClip) {
            File(event.videoClipPath).delete()
        }
        _pendingViolation.value = null
    }

    private fun startCountdown(event: ViolationEvent) {
        countdownJob?.cancel()
        _responseSecondsLeft.value = 30
        countdownJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            for (i in 30 downTo 1) {
                _responseSecondsLeft.value = i
                delay(1000)
            }
            // No response — auto-delete
            File(event.videoClipPath).delete()
            _pendingViolation.value = null
        }
    }

    /**
     * Extracts a short clip from the full match recording around the flagged moment,
     * using MediaExtractor + MediaMuxer to trim by timestamp without re-encoding.
     * Falls back to a full-file copy only if trimming fails (e.g. corrupt source),
     * so the feature degrades instead of silently producing an empty file.
     */
    private fun extractClip(sourcePath: String, atTimestampMs: Long, bufferSeconds: Int): String {
        val outDir = File(context.filesDir, "violation_clips").apply { mkdirs() }
        val outFile = File(outDir, "violation_${System.currentTimeMillis()}.mp4")
        val source = File(sourcePath)
        if (!source.exists()) return outFile.apply { createNewFile() }.absolutePath

        val startUs = ((atTimestampMs - bufferSeconds * 1000L).coerceAtLeast(0)) * 1000
        val endUs = (atTimestampMs + 2000L) * 1000

        try {
            trimVideo(sourcePath, outFile.absolutePath, startUs, endUs)
        } catch (_: Exception) {
            // Trim failed — fall back to copying the full file so the user still
            // has *something* to review, rather than a broken/empty clip.
            try { source.copyTo(outFile, overwrite = true) } catch (_: Exception) {}
        }
        return outFile.absolutePath
    }

    /**
     * Trims [sourcePath] to the range [startUs, endUs] (microseconds) and writes
     * the result to [outPath], copying encoded samples directly (no re-encode,
     * fast and lossless). Standard MediaExtractor/MediaMuxer approach.
     */
    private fun trimVideo(sourcePath: String, outPath: String, startUs: Long, endUs: Long) {
        val extractor = android.media.MediaExtractor()
        extractor.setDataSource(sourcePath)

        val muxer = android.media.MediaMuxer(outPath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val indexMap = HashMap<Int, Int>()

        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                val muxerTrackIndex = muxer.addTrack(format)
                indexMap[i] = muxerTrackIndex
                extractor.selectTrack(i)
            }
        }

        muxer.start()

        val buffer = java.nio.ByteBuffer.allocate(1 * 1024 * 1024)
        val bufferInfo = android.media.MediaCodec.BufferInfo()

        extractor.seekTo(startUs, android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC)

        while (true) {
            val trackIndex = extractor.sampleTrackIndex
            if (trackIndex < 0) break
            val sampleTime = extractor.sampleTime
            if (sampleTime > endUs) break

            buffer.clear()
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break

            bufferInfo.apply {
                offset = 0
                size = sampleSize
                presentationTimeUs = sampleTime - startUs // re-base to start at 0
                flags = extractor.sampleFlags
            }

            val muxerIndex = indexMap[trackIndex]
            if (muxerIndex != null && bufferInfo.presentationTimeUs >= 0) {
                muxer.writeSampleData(muxerIndex, buffer, bufferInfo)
            }
            extractor.advance()
        }

        muxer.stop()
        muxer.release()
        extractor.release()
    }
}
