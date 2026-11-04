package com.waha.tv.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography
import com.waha.core.R as CoreR

/**
 * The same brand faces as the phone build (Tajawal for headings, IBM Plex Sans
 * Arabic for text), loaded from the shared fonts in `:core`.
 */
object WahaTvFonts {
    val Tajawal = FontFamily(
        Font(CoreR.font.tajawal_regular, FontWeight.Normal),
        Font(CoreR.font.tajawal_medium, FontWeight.Medium),
        Font(CoreR.font.tajawal_bold, FontWeight.Bold),
        Font(CoreR.font.tajawal_black, FontWeight.Black)
    )

    val PlexArabic = FontFamily(
        Font(CoreR.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
        Font(CoreR.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
        Font(CoreR.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold)
    )
}

/**
 * The ramp's scale, in step with [com.waha.tv.ui.screens.WahaTvDimens]: the numbers
 * below are the sizes the screens were first built with, and the ramp multiplies
 * them once here rather than at every call site.
 */
private const val TypeScale = 1.5f

private fun tvStyle(
    fontFamily: FontFamily,
    weight: FontWeight,
    size: Float,
    line: Float
): TextStyle = TextStyle(
    fontFamily = fontFamily,
    fontWeight = weight,
    fontSize = (size * TypeScale).sp,
    lineHeight = (line * TypeScale).sp
)

/**
 * The phone ramp (headline 28sp / body 16sp) re-scaled for a 10-foot UI: the
 * same hierarchy, large enough to read from across the room.
 *
 * The player does not read this ramp for its overlays: it keeps the sizes it was
 * designed with (see `playerSized` in `TvPlayerScreen`), so growing the browse UI
 * leaves the video's own chrome exactly as it was.
 */
val WahaTvTypography = Typography(
    displayLarge = tvStyle(WahaTvFonts.Tajawal, FontWeight.Black, 48f, 56f),
    headlineLarge = tvStyle(WahaTvFonts.Tajawal, FontWeight.Black, 36f, 44f),
    headlineMedium = tvStyle(WahaTvFonts.Tajawal, FontWeight.Bold, 30f, 38f),
    headlineSmall = tvStyle(WahaTvFonts.Tajawal, FontWeight.Bold, 26f, 34f),
    titleLarge = tvStyle(WahaTvFonts.Tajawal, FontWeight.Bold, 23f, 30f),
    titleMedium = tvStyle(WahaTvFonts.Tajawal, FontWeight.Bold, 20f, 27f),
    titleSmall = tvStyle(WahaTvFonts.Tajawal, FontWeight.Medium, 18f, 24f),
    bodyLarge = tvStyle(WahaTvFonts.PlexArabic, FontWeight.Normal, 20f, 28f),
    bodyMedium = tvStyle(WahaTvFonts.PlexArabic, FontWeight.Normal, 18f, 26f),
    bodySmall = tvStyle(WahaTvFonts.PlexArabic, FontWeight.Normal, 16f, 22f),
    labelLarge = tvStyle(WahaTvFonts.PlexArabic, FontWeight.SemiBold, 18f, 24f),
    labelMedium = tvStyle(WahaTvFonts.PlexArabic, FontWeight.SemiBold, 16f, 21f),
    labelSmall = tvStyle(WahaTvFonts.PlexArabic, FontWeight.SemiBold, 14f, 19f)
)
