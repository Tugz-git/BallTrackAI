package com.balltrack.ai.vision

import android.content.Context
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File

/**
 * Records the live match to a LOCAL file only — app-private storage, never
 * shared automatically, never uploaded. This is what gives ViolationReplayManager
 * something real to clip from when a foul/travel is flagged.
 *
 * Quality is capped at 720p by default — the Helio G100 in the Infinix GT 20 Pro
 * is already doing real-time pose tracking + ball tracking on the same camera
 * feed; recording at 1080p/4K on top of that risks dropped frames in the
 * tracking pipeline. 720p keeps replay quality reasonable without competing for
 * the same CPU/GPU budget as the pose detector.
 */
class MatchVideoRecorder(private val context: Context) {

    private var recording: Recording? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var outputFile: File? = null

    fun buildVideoCapture(): VideoCapture<Recorder> {
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD)) // 720p cap, see class doc
            .build()
        return VideoCapture.withOutput(recorder).also { videoCapture = it }
    }

    /** Returns the local file path once recording has started, or null if it couldn't start. */
    fun startRecording(lifecycleOwner: LifecycleOwner): String? {
        val capture = videoCapture ?: return null
        val outDir = File(context.filesDir, "match_recordings").apply { mkdirs() }
        val file = File(outDir, "match_${System.currentTimeMillis()}.mp4")
        outputFile = file

        val outputOptions = FileOutputOptions.Builder(file).build()

        recording = capture.output
            .prepareRecording(context, outputOptions)
            .withAudioEnabled() // optional — lets the violation replay include crowd/player audio
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize && event.hasError()) {
                    // Recording failed partway — the file may be incomplete or
                    // unreadable. Violation clipping will fall back to a copy
                    // attempt and surface "clip unavailable" if that also fails,
                    // rather than crash.
                }
            }
        return file.absolutePath
    }

    fun stopRecording() {
        recording?.stop()
        recording = null
    }

    fun currentFilePath(): String? = outputFile?.absolutePath

    /** Permanently delete the local match recording (e.g. after the session ends and isn't needed). */
    fun deleteRecording() {
        outputFile?.delete()
        outputFile = null
    }
}
