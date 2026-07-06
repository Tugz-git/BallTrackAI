package com.balltrack.ai.vision

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * PRIVACY CONTRACT:
 * - Model runs 100% on-device. No frame, landmark, or image data is sent to any
 *   network endpoint from this class.
 * - Bitmaps fed in are consumed synchronously and not retained.
 * - This file has zero networking imports by design.
 */
class PoseDetector(
    context: Context,
    val onResult: (PoseLandmarkerResult, Long) -> Unit,
    val onError: (String) -> Unit
) {
    private val landmarker: PoseLandmarker

    init {
        val base = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_lite.task")
            .setDelegate(Delegate.GPU)
            .build()

        val opts = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setResultListener { r, _ -> onResult(r, System.currentTimeMillis()) }
            .setErrorListener { e -> onError(e.message ?: "Pose error") }
            .build()

        landmarker = PoseLandmarker.createFromOptions(context, opts)
    }

    fun detectAsync(bitmap: Bitmap, timestampMs: Long) {
        landmarker.detectAsync(BitmapImageBuilder(bitmap).build(), timestampMs)
    }

    fun close() = landmarker.close()
}
