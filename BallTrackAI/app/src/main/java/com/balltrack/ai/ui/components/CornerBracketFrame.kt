package com.balltrack.ai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * SIGNATURE MOTIF — used once per screen, not scattered everywhere.
 *
 * Four corner brackets, like a camera viewfinder or HUD reticle, framing a
 * piece of content. This is the one visual idea this app is built around: it
 * watches court geometry and body position live, so its frame should look
 * like something actively targeting/reading what's inside it, rather than a
 * generic rounded card. Reserved for the most important content on a screen
 * — the live score, a key stat block — not used on every card, per the
 * design principle of spending boldness in one place.
 */
@Composable
fun CornerBracketFrame(
    modifier: Modifier = Modifier,
    bracketColor: Color = MaterialTheme.colorScheme.primary,
    bracketLength: Dp = 18.dp,
    strokeWidth: Dp = 2.5.dp,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val len = bracketLength.toPx()
            val sw = strokeWidth.toPx()
            val w = size.width
            val h = size.height

            // Top-left
            drawLine(bracketColor, Offset(0f, sw / 2), Offset(len, sw / 2), sw)
            drawLine(bracketColor, Offset(sw / 2, 0f), Offset(sw / 2, len), sw)
            // Top-right
            drawLine(bracketColor, Offset(w - len, sw / 2), Offset(w, sw / 2), sw)
            drawLine(bracketColor, Offset(w - sw / 2, 0f), Offset(w - sw / 2, len), sw)
            // Bottom-left
            drawLine(bracketColor, Offset(0f, h - sw / 2), Offset(len, h - sw / 2), sw)
            drawLine(bracketColor, Offset(sw / 2, h - len), Offset(sw / 2, h), sw)
            // Bottom-right
            drawLine(bracketColor, Offset(w - len, h - sw / 2), Offset(w, h - sw / 2), sw)
            drawLine(bracketColor, Offset(w - sw / 2, h - len), Offset(w - sw / 2, h), sw)
        }
        content()
    }
}
