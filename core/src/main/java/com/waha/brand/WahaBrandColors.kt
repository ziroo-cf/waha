package com.waha.brand

import androidx.compose.ui.graphics.Color

/**
 * Waha's brand palette: the single source of truth for colour across the phone
 * and TV modules.
 *
 * The literals used to live in `:mobile`'s `Color.kt`, which meant the TV build
 * could only approximate them. They now sit here, so `:mobile` aliases them
 * (keeping its existing names) and `:tv` builds its TV colour scheme from the
 * exact same values.
 *
 * The identity is "Warm Oasis": brand orange for the sun (and every tap/selected
 * state), palm green for growth and progress, sun-gold for stars and badges, all
 * over one warm-brown neutral family (dark) and warm sand (light). It stays
 * quiet on purpose: the video thumbnails are the loudest thing on screen.
 */
object WahaBrandColors {

    // --- Fixed brand colour (same in both schemes) ------------------------
    /** Stars and badges. Sits on dark ink in either scheme. */
    val GoldFixed = Color(0xFFFFCC33)

    /**
     * The brand orange as small TEXT on light surfaces. #FF6600 is a fill colour
     * and too light to read at text sizes on sand, so light-mode text uses this
     * deeper shade; dark mode uses [PrimaryDark] directly.
     */
    val OrangeTextFixed = Color(0xFFB84500)

    // --- Light scheme -----------------------------------------------------
    val PrimaryLight = Color(0xFFFF6600)
    val OnPrimaryLight = Color(0xFF2B1000)
    val PrimaryContainerLight = Color(0xFFFFDBC7)
    val OnPrimaryContainerLight = Color(0xFF3A1400)

    val SecondaryLight = Color(0xFF0E8539)
    val OnSecondaryLight = Color(0xFFFFFFFF)
    val SecondaryContainerLight = Color(0xFFC8F0D4)
    val OnSecondaryContainerLight = Color(0xFF00210C)

    val TertiaryLight = Color(0xFF8A6500)
    val OnTertiaryLight = Color(0xFFFFFFFF)
    val TertiaryContainerLight = Color(0xFFFFE7A3)
    val OnTertiaryContainerLight = Color(0xFF271D00)

    val ErrorLight = Color(0xFFBA1A1A)
    val OnErrorLight = Color(0xFFFFFFFF)
    val ErrorContainerLight = Color(0xFFFFDAD6)
    val OnErrorContainerLight = Color(0xFF410002)

    val BackgroundLight = Color(0xFFFFFBF5)
    val OnBackgroundLight = Color(0xFF211A13)
    val SurfaceLight = Color(0xFFFFFBF5)
    val OnSurfaceLight = Color(0xFF211A13)
    val SurfaceVariantLight = Color(0xFFF5E9DA)
    val OnSurfaceVariantLight = Color(0xFF50453A)
    val OutlineLight = Color(0xFF85766A)
    val OutlineVariantLight = Color(0xFFDCCBBB)

    val InverseSurfaceLight = Color(0xFF362F28)
    val InverseOnSurfaceLight = Color(0xFFFBEEE0)
    val InversePrimaryLight = Color(0xFFFF8A3D)

    val SurfaceBrightLight = Color(0xFFFFFBF5)
    val SurfaceDimLight = Color(0xFFE3D6C5)
    val SurfaceContainerLowestLight = Color(0xFFFFFDF9)
    val SurfaceContainerLowLight = Color(0xFFFBF3E8)
    val SurfaceContainerLight = Color(0xFFF5E9DA)
    val SurfaceContainerHighLight = Color(0xFFEFE1CF)
    val SurfaceContainerHighestLight = Color(0xFFE8D9C5)

    /** Orange is a fill colour; surfaces carry no brand tint of their own. */
    val SurfaceTintLight = Color.Transparent
    val ScrimLight = Color(0xFF000000)

    // --- Dark scheme (the living-room default) ----------------------------
    val PrimaryDark = Color(0xFFFF6600)
    val OnPrimaryDark = Color(0xFF2B1000)
    val PrimaryContainerDark = Color(0xFF6B2A00)
    val OnPrimaryContainerDark = Color(0xFFFFDBC7)

    val SecondaryDark = Color(0xFF4CC77F)
    val OnSecondaryDark = Color(0xFF00210F)
    val SecondaryContainerDark = Color(0xFF0B4A22)
    val OnSecondaryContainerDark = Color(0xFFB7F2C9)

    val TertiaryDark = Color(0xFFFFC93C)
    val OnTertiaryDark = Color(0xFF2B1F00)
    val TertiaryContainerDark = Color(0xFF5A4500)
    val OnTertiaryContainerDark = Color(0xFFFFE08A)

    val ErrorDark = Color(0xFFFFB4AB)
    val OnErrorDark = Color(0xFF690005)
    val ErrorContainerDark = Color(0xFF93000A)
    val OnErrorContainerDark = Color(0xFFFFDAD6)

    val BackgroundDark = Color(0xFF130F0B)
    val OnBackgroundDark = Color(0xFFEDE0D4)
    val SurfaceDark = Color(0xFF1C1612)
    val OnSurfaceDark = Color(0xFFEDE0D4)
    val SurfaceVariantDark = Color(0xFF2B221B)
    val OnSurfaceVariantDark = Color(0xFFC9B8A8)
    val OutlineDark = Color(0xFF9A8878)
    val OutlineVariantDark = Color(0xFF4A3E34)

    val InverseSurfaceDark = Color(0xFFEDE0D4)
    val InverseOnSurfaceDark = Color(0xFF362F28)
    val InversePrimaryDark = Color(0xFFB84500)

    val SurfaceBrightDark = Color(0xFF3D3229)
    val SurfaceDimDark = Color(0xFF130F0B)
    val SurfaceContainerLowestDark = Color(0xFF0F0B08)
    val SurfaceContainerLowDark = Color(0xFF1A1410)
    val SurfaceContainerDark = Color(0xFF211A15)
    val SurfaceContainerHighDark = Color(0xFF2B221B)
    val SurfaceContainerHighestDark = Color(0xFF352B23)

    /** See [SurfaceTintLight]: no brand tint is pushed onto surfaces. */
    val SurfaceTintDark = Color.Transparent
    val ScrimDark = Color(0xFF000000)
}
