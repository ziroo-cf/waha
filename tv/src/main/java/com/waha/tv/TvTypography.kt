package com.waha.tv

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
 * The phone ramp (headline 28sp / body 16sp) re-scaled for a 10-foot UI: the
 * same hierarchy, roughly 1.5x the size so titles read from across the room.
 */
val WahaTvTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = WahaTvFonts.Tajawal,
        fontWeight = FontWeight.Black,
        fontSize = 48.sp,
        lineHeight = 56.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = WahaTvFonts.Tajawal,
        fontWeight = FontWeight.Black,
        fontSize = 36.sp,
        lineHeight = 44.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = WahaTvFonts.Tajawal,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 38.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = WahaTvFonts.Tajawal,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 34.sp
    ),
    titleLarge = TextStyle(
        fontFamily = WahaTvFonts.Tajawal,
        fontWeight = FontWeight.Bold,
        fontSize = 23.sp,
        lineHeight = 30.sp
    ),
    titleMedium = TextStyle(
        fontFamily = WahaTvFonts.Tajawal,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 27.sp
    ),
    titleSmall = TextStyle(
        fontFamily = WahaTvFonts.Tajawal,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = WahaTvFonts.PlexArabic,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = WahaTvFonts.PlexArabic,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 26.sp
    ),
    bodySmall = TextStyle(
        fontFamily = WahaTvFonts.PlexArabic,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    labelLarge = TextStyle(
        fontFamily = WahaTvFonts.PlexArabic,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    labelMedium = TextStyle(
        fontFamily = WahaTvFonts.PlexArabic,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp
    ),
    labelSmall = TextStyle(
        fontFamily = WahaTvFonts.PlexArabic,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp
    )
)
