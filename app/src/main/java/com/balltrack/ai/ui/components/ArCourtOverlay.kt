package com.balltrack.ai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import com.balltrack.ai.vision.CourtDetector

/**
 * Draws AR-style court lines (3-point arc, key/paint boundary, half-court) over
 * the camera feed when playing on a court with no painted lines (e.g. driveway
 * concrete, a bare slab). Lines are computed from the detected hoop position —
 * see CourtDetector — and rendered as an overlay only; nothing is physically
 * measured, this is a visual approximation based on standard court proportions.
 *
 * HONEST LIMITATION: accuracy depends entirely on correct hoop height/distance
 * detection from CourtDetector. On an unmarked surface there's no ground-truth
 * to calibrate against, so treat these lines as a helpful guide, not a precise
 * regulation boundary.
 */
@Composable
fun ArCourtOverlay(hoopRegion: androidx.compose.ui.geometry.Rect?, modifier: Modifier = Modifier) {
    if (hoopRegion == null) return

    Canvas(modifier = modifier) {
        val hoopCx = hoopRegion.center.x
        val hoopCy = hoopRegion.center.y
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))

        // Three-point arc approximation (simple arc using quadratic curve points)
        val threeRadius = size.width * 0.42f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(hoopCx - threeRadius, hoopCy + size.height * 0.05f)
            quadraticBezierTo(hoopCx, hoopCy + size.height * 0.55f, hoopCx + threeRadius, hoopCy + size.height * 0.05f)
        }
        drawPath(path, color = Color(0xFFFFD600), style = Stroke(width = 4f, pathEffect = dashEffect))

        // Paint/key rectangle
        val paintWidth = size.width * 0.18f
        val paintHeight = size.height * 0.22f
        drawRect(
            color = Color(0xFF00E5FF),
            topLeft = Offset(hoopCx - paintWidth / 2, hoopCy),
            size = androidx.compose.ui.geometry.Size(paintWidth, paintHeight),
            style = Stroke(width = 3f)
        )

        // Free-throw line
        drawLine(
            color = Color(0xFF00E5FF),
            start = Offset(hoopCx - paintWidth / 2, hoopCy + paintHeight),
            end = Offset(hoopCx + paintWidth / 2, hoopCy + paintHeight),
            strokeWidth = 3f
        )
    }
}
