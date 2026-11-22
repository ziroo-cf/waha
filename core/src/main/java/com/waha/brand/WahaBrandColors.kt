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
 * The identity is "Oasis": teal for water (and for every tap/selected state),
 * palm green for growth and progress, gold for stars and badges, over
 * teal-tinted night neutrals (dark) and warm sand (light). It stays quiet on
 * purpose: the video thumbnails are the loudest thing on screen.
 */
object WahaBrandColors {

    // --- Fixed brand colour (same in both schemes) ------------------------
    /** Stars and badges. Sits on dark ink in either scheme. */
    val GoldFixed = Color(0xFFFFB300)

    // --- Light scheme -----------------------------------------------------
    val PrimaryLight = Color(0xFF00796B)
    val OnPrimaryLight = Color(0xFFFFFFFF)
    val PrimaryContainerLight = Color(0xFFB2F0E6)
    val OnPrimaryContainerLight = Color(0xFF00201C)

    val SecondaryLight = Color(0xFF1F7A44)
    val OnSecondaryLight = Color(0xFFFFFFFF)
    val SecondaryContainerLight = Color(0xFFC8F0D4)
    val OnSecondaryContainerLight = Color(0xFF00210C)

    val TertiaryLight = Color(0xFF8A5A00)
    val OnTertiaryLight = Color(0xFFFFFFFF)
    val TertiaryContainerLight = Color(0xFFFFE0A3)
    val OnTertiaryContainerLight = Color(0xFF2B1A00)

    val ErrorLight = Color(0xFFBA1A1A)
    val OnErrorLight = Color(0xFFFFFFFF)
    val ErrorContainerLight = Color(0xFFFFDAD6)
    val OnErrorContainerLight = Color(0xFF410002)

    val BackgroundLight = Color(0xFFFBF7EE)
    val OnBackgroundLight = Color(0xFF1C1B16)
    val SurfaceLight = Color(0xFFFBF7EE)
    val OnSurfaceLight = Color(0xFF1C1B16)
    val SurfaceVariantLight = Color(0xFFF1E9D8)
    val OnSurfaceVariantLight = Color(0xFF4B463A)
    val OutlineLight = Color(0xFF7D766A)
    val OutlineVariantLight = Color(0xFFD0C8B8)

    val InverseSurfaceLight = Color(0xFF2A302E)
    val InverseOnSurfaceLight = Color(0xFFEFF1EC)
    val InversePrimaryLight = Color(0xFF4DD0C0)

    val SurfaceBrightLight = Color(0xFFFBF7EE)
    val SurfaceDimLight = Color(0xFFDDD6C6)
    val SurfaceContainerLowestLight = Color(0xFFFFFDF8)
    val SurfaceContainerLowLight = Color(0xFFF6F1E5)
    val SurfaceContainerLight = Color(0xFFF1E9D8)
    val SurfaceContainerHighLight = Color(0xFFEBE2CF)
    val SurfaceContainerHighestLight = Color(0xFFE5DBC7)

    val SurfaceTintLight = Color(0xFF00796B)
    val ScrimLight = Color(0xFF000000)

    // --- Dark scheme (the living-room default) ----------------------------
    val PrimaryDark = Color(0xFF4DD0C0)
    val OnPrimaryDark = Color(0xFF00201C)
    val PrimaryContainerDark = Color(0xFF005047)
    val OnPrimaryContainerDark = Color(0xFFB2F0E6)

    val SecondaryDark = Color(0xFF5FD38D)
    val OnSecondaryDark = Color(0xFF00391A)
    val SecondaryContainerDark = Color(0xFF0B4A22)
    val OnSecondaryContainerDark = Color(0xFFB7F2C9)

    val TertiaryDark = Color(0xFFFFC247)
    val OnTertiaryDark = Color(0xFF2B1A00)
    val TertiaryContainerDark = Color(0xFF5A4300)
    val OnTertiaryContainerDark = Color(0xFFFFE29A)

    val ErrorDark = Color(0xFFFFB4AB)
    val OnErrorDark = Color(0xFF690005)
    val ErrorContainerDark = Color(0xFF93000A)
    val OnErrorContainerDark = Color(0xFFFFDAD6)

    val BackgroundDark = Color(0xFF0A1514)
    val OnBackgroundDark = Color(0xFFDCE5E2)
    val SurfaceDark = Color(0xFF11201E)
    val OnSurfaceDark = Color(0xFFDCE5E2)
    val SurfaceVariantDark = Color(0xFF19302C)
    val OnSurfaceVariantDark = Color(0xFFA9BDB8)
    val OutlineDark = Color(0xFF7F938E)
    val OutlineVariantDark = Color(0xFF2C4540)

    val InverseSurfaceDark = Color(0xFFDCE5E2)
    val InverseOnSurfaceDark = Color(0xFF1C2B28)
    val InversePrimaryDark = Color(0xFF00796B)

    val SurfaceBrightDark = Color(0xFF2A403B)
    val SurfaceDimDark = Color(0xFF0A1514)
    val SurfaceContainerLowestDark = Color(0xFF081211)
    val SurfaceContainerLowDark = Color(0xFF0F1D1B)
    val SurfaceContainerDark = Color(0xFF152623)
    val SurfaceContainerHighDark = Color(0xFF1B2E2B)
    val SurfaceContainerHighestDark = Color(0xFF223733)

    val SurfaceTintDark = Color(0xFF4DD0C0)
    val ScrimDark = Color(0xFF000000)
}
