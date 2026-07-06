package com.balltrack.ai.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit

/**
 * ============================================================================
 * DESIGN SYSTEM — SportTrack AI
 * ============================================================================
 * Design thesis: this app's one genuinely distinctive thing is that it reads
 * court geometry and body position live. The visual language leans into that
 * directly — a HUD/viewfinder feel (corner-bracket framing, scoreboard-style
 * numerals) rather than a generic "sporty neon" skin. Every color below is a
 * deliberate choice tied to that idea, not a default.
 *
 * Ink (#0B0E14)      — near-black, warmer than pure black so it doesn't read
 *                       as "OLED battery saver mode"; the base court-at-night feel.
 * Chalk (#E8EDF2)    — cool off-white, like court floor markings, for text.
 * Court Orange       — the signature accent (#FF6B35). Tied directly to an
 *   (#FF6B35)           actual basketball's color, not a generic brand-blue or
 *                       cyan. This is the one color the person can customize
 *                       (see AccentPreferences below) — everything else holds.
 * Panel (#1C2330)    — slightly lifted surface for cards, distinct from Ink.
 * Make Green         — #3DDC97, used ONLY for makes/success, never reused for
 *                       anything else so it keeps its meaning.
 * Miss Red (#FF4757) — used ONLY for misses/violations, same reasoning.
 */

object SportTrackColors {
    val Ink = Color(0xFF0B0E14)
    val InkElevated = Color(0xFF11151D)
    val Panel = Color(0xFF1C2330)
    val PanelBorder = Color(0xFF2A3344)
    val Chalk = Color(0xFFE8EDF2)
    val ChalkMuted = Color(0xFF8A93A3)
    val MakeGreen = Color(0xFF3DDC97)
    val MissRed = Color(0xFFFF4757)
    val GoldStreak = Color(0xFFFFC23D)

    // Light mode variants — same thesis, daylight court rather than night court.
    val ChalkLight = Color(0xFFF6F7F9)
    val PanelLight = Color.White
    val InkText = Color(0xFF12161F)
}

/** The one customizable token. Defaults to Court Orange but can be changed in Settings. */
class AccentPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("accent_prefs", Context.MODE_PRIVATE)

    companion object {
        val PRESETS = listOf(
            "Court Orange" to Color(0xFFFF6B35),
            "Cyan" to Color(0xFF00E5FF),
            "Crimson" to Color(0xFFFF3B5C),
            "Violet" to Color(0xFF9B5CFF),
            "Lime" to Color(0xFFB6FF3D)
        )
        private const val DEFAULT_ARGB = 0xFFFF6B35.toInt()
    }

    var accentColor: Color
        get() = Color(prefs.getInt("accent_argb", DEFAULT_ARGB))
        set(value) = prefs.edit { putInt("accent_argb", value.toArgb()) }

    private fun Color.toArgb(): Int = android.graphics.Color.argb(
        (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()
    )
}

// ─── Type scale ──────────────────────────────────────────────────────────────
// Display numerals (scores, big stats) use a condensed system weight so they
// read like a scoreboard rather than app body text. No external font file is
// bundled here — Android's built-in condensed system family is used so this
// compiles and runs immediately. To swap in a specific bundled font later,
// add the .ttf to res/font/ and replace FontFamily.SansSerif below with
// FontFamily(Font(R.font.your_font)).
private val DisplayFontFamily = FontFamily.SansSerif
private val BodyFontFamily = FontFamily.Default

val SportTrackTypography = Typography(
    displayLarge = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.Black, fontSize = 57.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.Black, fontSize = 45.sp, letterSpacing = (-0.25).sp),
    headlineLarge = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp),
    headlineMedium = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp),
    titleLarge = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 0.15.sp),
    bodyLarge = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.5.sp)
)

// ─── Color scheme builders ───────────────────────────────────────────────────

private fun buildDarkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = SportTrackColors.Ink,
    primaryContainer = accent.copy(alpha = 0.18f),
    onPrimaryContainer = accent,
    secondary = SportTrackColors.GoldStreak,
    onSecondary = SportTrackColors.Ink,
    secondaryContainer = SportTrackColors.Panel,
    background = SportTrackColors.Ink,
    onBackground = SportTrackColors.Chalk,
    surface = SportTrackColors.Panel,
    onSurface = SportTrackColors.Chalk,
    surfaceVariant = SportTrackColors.InkElevated,
    onSurfaceVariant = SportTrackColors.ChalkMuted,
    outline = SportTrackColors.PanelBorder,
    error = SportTrackColors.MissRed,
    onError = SportTrackColors.Chalk
)

private fun buildLightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = 0.12f),
    onPrimaryContainer = accent,
    secondary = Color(0xFFB8860B),
    background = SportTrackColors.ChalkLight,
    onBackground = SportTrackColors.InkText,
    surface = SportTrackColors.PanelLight,
    onSurface = SportTrackColors.InkText,
    outline = Color(0xFFC7CDD6),
    error = SportTrackColors.MissRed
)

@Composable
fun SportTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColor: Color = Color(0xFFFF6B35), // Court Orange default — see AccentPreferences for the persisted/customizable version
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) buildDarkScheme(accentColor) else buildLightScheme(accentColor),
        typography = SportTrackTypography,
        shapes = SportTrackShapes,
        content = content
    )
}

/** Sharper, more technical corner radii than Material defaults — see the corner-bracket component for the matching signature motif. */
val SportTrackShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

