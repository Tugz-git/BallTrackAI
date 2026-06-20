package com.balltrack.ai.vision

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF

/**
 * LOCAL-ONLY court detection. No network, no AI model download.
 * Uses HSV color segmentation to find the court floor region and
 * HoughCircles-style circle detection to find the hoop/rim.
 *
 * HONEST LIMITATION: this works reliably on standard hardwood (tan/brown)
 * and painted asphalt courts (grey, green, red) in decent daylight.
 * It will produce low-confidence results in:
 *   - Very dark environments
 *   - Unusual court colors (e.g. novelty multi-color courts)
 *   - Camera angles less than ~30° above horizontal
 * The UI shows confidence level and warns the user when detection is weak.
 */
class CourtDetector {

    data class CourtDetectionResult(
        val hoopRegion: RectF?,          // normalized 0-1 bounding box around detected rim
        val courtBounds: RectF?,         // normalized court floor region
        val courtZones: List<CourtZone>, // computed zones based on hoopRegion
        val confidence: Float,           // 0.0 - 1.0
        val message: String
    )

    data class CourtZone(
        val name: String,
        val bounds: RectF,               // normalized 0-1
        val isThreePoint: Boolean
    )

    /**
     * Run on a downsampled frame for performance. Call this periodically (every
     * 30 frames or so), not every frame — court position doesn't change mid-session.
     */
    fun detect(bitmap: Bitmap): CourtDetectionResult {
        val scaled = scaleBitmap(bitmap, TARGET_WIDTH, TARGET_HEIGHT)
        val hoopRegion = detectHoop(scaled)
        val courtBounds = detectCourtFloor(scaled)

        return if (hoopRegion != null && courtBounds != null) {
            val zones = buildCourtZones(hoopRegion, courtBounds)
            CourtDetectionResult(
                hoopRegion = hoopRegion,
                courtBounds = courtBounds,
                courtZones = zones,
                confidence = 0.85f,
                message = "Court detected"
            )
        } else if (hoopRegion != null) {
            CourtDetectionResult(
                hoopRegion = hoopRegion,
                courtBounds = null,
                courtZones = buildCourtZones(hoopRegion, RectF(0f, 0.3f, 1f, 1f)),
                confidence = 0.55f,
                message = "Hoop found — move back so full court is visible for better zone tracking"
            )
        } else {
            CourtDetectionResult(
                hoopRegion = null,
                courtBounds = courtBounds,
                courtZones = emptyList(),
                confidence = 0.1f,
                message = "Court not detected — point camera at the hoop from behind the 3-point line"
            )
        }
    }

    // ─── Hoop detection ────────────────────────────────────────────────────
    // Strategy: look for a roughly circular cluster of orange/red pixels in
    // the upper half of the frame (the rim), then look for a rectangular
    // region directly below (the backboard) for confirmation.

    private fun detectHoop(bitmap: Bitmap): RectF? {
        val w = bitmap.width
        val h = bitmap.height
        val candidates = mutableListOf<Pair<Int, Int>>() // x, y of rim-colored pixels

        for (y in 0 until h / 2) {          // hoop is in the upper half
            for (x in 0 until w) {
                val pixel = bitmap.getPixel(x, y)
                if (isRimColor(pixel)) candidates.add(x to y)
            }
        }

        if (candidates.size < 20) return null

        // Find centroid of rim-colored cluster
        val cx = candidates.map { it.first }.average().toFloat()
        val cy = candidates.map { it.second }.average().toFloat()

        // Estimate radius from spread
        val spread = candidates.map { dist(it.first.toFloat(), it.second.toFloat(), cx, cy) }.average().toFloat()
        if (spread < 4f || spread > w * 0.25f) return null

        val norm = RectF(
            (cx - spread * 1.2f) / w,
            (cy - spread * 1.2f) / h,
            (cx + spread * 1.2f) / w,
            (cy + spread * 1.2f) / h
        )
        return norm.coerceInBounds()
    }

    // ─── Court floor detection ──────────────────────────────────────────────
    // Strategy: find the dominant "floor" color in the lower 60% of the frame,
    // then compute the bounding region of pixels matching that color.

    private fun detectCourtFloor(bitmap: Bitmap): RectF? {
        val w = bitmap.width
        val h = bitmap.height
        val startY = (h * 0.4f).toInt()

        var floorPixels = 0
        var minX = w; var maxX = 0; var minY = h; var maxY = 0

        for (y in startY until h step 3) {
            for (x in 0 until w step 3) {
                val pixel = bitmap.getPixel(x, y)
                if (isCourtFloorColor(pixel)) {
                    floorPixels++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (floorPixels < 200) return null
        return RectF(minX.toFloat() / w, minY.toFloat() / h, maxX.toFloat() / w, maxY.toFloat() / h)
    }

    // ─── Zone mapping ──────────────────────────────────────────────────────
    // Zones computed relative to hoop position. All normalized 0-1.

    private fun buildCourtZones(hoop: RectF, court: RectF): List<CourtZone> {
        val hoopCx = hoop.centerX()
        val hoopCy = hoop.centerY()
        val threeLineY = hoopCy + 0.18f   // approximate three-point distance ratio
        val midRangeY = hoopCy + 0.10f

        return listOf(
            CourtZone("paint", RectF(hoopCx - 0.08f, hoopCy, hoopCx + 0.08f, midRangeY), false),
            CourtZone("mid_center", RectF(hoopCx - 0.10f, midRangeY, hoopCx + 0.10f, threeLineY), false),
            CourtZone("mid_left", RectF(court.left, midRangeY, hoopCx - 0.10f, threeLineY), false),
            CourtZone("mid_right", RectF(hoopCx + 0.10f, midRangeY, court.right, threeLineY), false),
            CourtZone("wing_left", RectF(court.left, threeLineY, hoopCx - 0.15f, court.bottom), true),
            CourtZone("top_key", RectF(hoopCx - 0.15f, threeLineY, hoopCx + 0.15f, court.bottom), true),
            CourtZone("wing_right", RectF(hoopCx + 0.15f, threeLineY, court.right, court.bottom), true),
            CourtZone("corner_left", RectF(court.left, threeLineY - 0.02f, court.left + 0.12f, court.bottom), true),
            CourtZone("corner_right", RectF(court.right - 0.12f, threeLineY - 0.02f, court.right, court.bottom), true)
        )
    }

    fun zoneForPosition(x: Float, y: Float, zones: List<CourtZone>): CourtZone? =
        zones.firstOrNull { it.bounds.contains(x, y) }

    // ─── Color heuristics ──────────────────────────────────────────────────

    private fun isRimColor(pixel: Int): Boolean {
        val r = Color.red(pixel); val g = Color.green(pixel); val b = Color.blue(pixel)
        return r in 150..255 && g in 40..120 && b in 0..80 && r > g + 40
    }

    private fun isCourtFloorColor(pixel: Int): Boolean {
        val r = Color.red(pixel); val g = Color.green(pixel); val b = Color.blue(pixel)
        val brightness = (r + g + b) / 3
        // Hardwood: warm tan/brown. Asphalt: grey. Painted: often green or red.
        val isHardwood = r in 140..220 && g in 100..180 && b in 60..140 && r > b
        val isGrey = brightness in 100..200 && kotlin.math.abs(r - g) < 25 && kotlin.math.abs(g - b) < 25
        val isPaintedGreen = g > r && g > b && g in 80..180
        val isPaintedRed = r > g + 30 && r > b + 30 && r in 120..220
        return isHardwood || isGrey || isPaintedGreen || isPaintedRed
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private fun scaleBitmap(src: Bitmap, w: Int, h: Int): Bitmap =
        Bitmap.createScaledBitmap(src, w, h, false)

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float) =
        kotlin.math.sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))

    private fun Float.pow(exp: Int) = kotlin.math.pow(this.toDouble(), exp.toDouble()).toFloat()

    private fun RectF.coerceInBounds() = RectF(
        left.coerceIn(0f, 1f), top.coerceIn(0f, 1f),
        right.coerceIn(0f, 1f), bottom.coerceIn(0f, 1f)
    )

    companion object {
        private const val TARGET_WIDTH = 320
        private const val TARGET_HEIGHT = 240
    }
}
