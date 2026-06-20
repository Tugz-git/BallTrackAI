package com.balltrack.ai.sport.basketball

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.atan2
import kotlin.math.abs

class ShotDetector(private val shootingHandIsRight: Boolean = true) {

    private val wristHistory = ArrayDeque<Triple<Long, Float, Float>>() // time, x, y (normalized)
    private var catchTimestamp: Long? = null
    private var inReleaseWindow = false
    private var framesSinceRelease = 0

    data class ShotEvent(
        val timestampMs: Long,
        val releaseAngleDegrees: Float?,
        val releaseSpeedSeconds: Float?,
        val wristX: Float,
        val wristY: Float
    )

    fun onFrame(result: PoseLandmarkerResult, timestampMs: Long): ShotEvent? {
        val lm = result.landmarks().firstOrNull() ?: return null
        val sIdx = if (shootingHandIsRight) 12 else 11
        val eIdx = if (shootingHandIsRight) 14 else 13
        val wIdx = if (shootingHandIsRight) 16 else 15
        if (lm.size <= maxOf(sIdx, eIdx, wIdx)) return null

        val shoulder = lm[sIdx]; val elbow = lm[eIdx]; val wrist = lm[wIdx]

        wristHistory.addLast(Triple(timestampMs, wrist.x(), wrist.y()))
        if (wristHistory.size > 20) wristHistory.removeFirst()

        val vy = verticalVelocity()

        // Track "dip" (ball coming down to shooting pocket) as catch moment
        if (vy > 0.5f && catchTimestamp == null) catchTimestamp = timestampMs
        if (vy < -0.8f && !inReleaseWindow) {
            // Wrist shooting upward fast — this is the release
            inReleaseWindow = true
            framesSinceRelease = 0
        }

        if (inReleaseWindow) {
            framesSinceRelease++
            if (framesSinceRelease == 3) { // few frames after release peak
                inReleaseWindow = false
                val angle = releaseAngle(shoulder.x(), shoulder.y(), elbow.x(), elbow.y(), wrist.x(), wrist.y())
                val speed = catchTimestamp?.let { (timestampMs - it) / 1000f }
                catchTimestamp = null
                return ShotEvent(timestampMs, angle, speed, wrist.x(), wrist.y())
            }
        }
        return null
    }

    private fun verticalVelocity(): Float {
        if (wristHistory.size < 2) return 0f
        val (t1, _, y1) = wristHistory[wristHistory.size - 2]
        val (t2, _, y2) = wristHistory.last()
        val dt = (t2 - t1).coerceAtLeast(1)
        return (y2 - y1) / dt * 1000f
    }

    private fun releaseAngle(sx: Float, sy: Float, ex: Float, ey: Float, wx: Float, wy: Float): Float {
        val dx = (wx - ex).toDouble(); val dy = -(wy - ey).toDouble()
        return Math.toDegrees(atan2(dy, dx)).toFloat()
    }

    fun reset() { wristHistory.clear(); catchTimestamp = null; inReleaseWindow = false }
}

// ─── Ball Tracker ──────────────────────────────────────────────────────────

class BallTracker {
    private val history = ArrayDeque<Pair<Long, Pair<Float, Float>>>() // time → normalized x,y

    data class BallPos(val x: Float, val y: Float)

    fun findBall(bitmap: Bitmap, timestampMs: Long): BallPos? {
        val w = bitmap.width; val h = bitmap.height
        var sumX = 0L; var sumY = 0L; var count = 0
        for (y in 0 until h step 4) for (x in 0 until w step 4) {
            if (isBasketball(bitmap.getPixel(x, y))) { sumX += x; sumY += y; count++ }
        }
        if (count < 12) return null
        val cx = sumX / count.toFloat() / w
        val cy = sumY / count.toFloat() / h
        history.addLast(timestampMs to (cx to cy))
        if (history.size > 25) history.removeFirst()
        return BallPos(cx, cy)
    }

    fun isMake(hoopRegion: RectF): Boolean? {
        if (history.size < 4) return null
        val passedHoop = history.any { (_, pos) ->
            hoopRegion.contains(pos.first, pos.second)
        }
        if (!passedHoop) return null
        // Ball must continue downward after hoop = make
        val last3 = history.takeLast(3)
        val goingDown = last3.size == 3 && last3.last().second.second > last3.first().second.second
        return goingDown
    }

    fun reset() = history.clear()

    private fun isBasketball(pixel: Int): Boolean {
        val r = android.graphics.Color.red(pixel)
        val g = android.graphics.Color.green(pixel)
        val b = android.graphics.Color.blue(pixel)
        return r in 140..255 && g in 60..160 && b in 0..100 && r > g && g > b
    }
}

// ─── Dribble Counter ───────────────────────────────────────────────────────

class DribbleCounter {
    private val wristYHistory = ArrayDeque<Float>()
    private var count = 0
    private var lastPeak = false

    fun onFrame(result: PoseLandmarkerResult): Int {
        val lm = result.landmarks().firstOrNull() ?: return count
        if (lm.size <= 16) return count
        val wristY = lm[16].y() // right wrist Y (normalized, 0=top)
        wristYHistory.addLast(wristY)
        if (wristYHistory.size > 8) wristYHistory.removeFirst()
        if (wristYHistory.size < 4) return count
        val avg = wristYHistory.average().toFloat()
        val isHigh = wristY < avg - 0.04f
        if (isHigh && !lastPeak) count++
        lastPeak = isHigh
        return count
    }

    fun reset() { count = 0; lastPeak = false; wristYHistory.clear() }
    fun getCount() = count
}
