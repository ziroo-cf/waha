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
 * The identity: brand orange is the one action colour (taps, focus, selection),
 * palm green marks growth and progress, sun-gold is reserved for stars and
 * badges. Everything neutral is green-black in the dark scheme and clean
 * sage-white in the light one — no brown, beige or sand anywhere. It stays quiet
 * on purpose: the video thumbnails are the loudest thing on screen.
 */
object WahaBrandColors {

    // --- Fixed brand colour (same in both schemes) ------------------------
    /** Stars and badges. Sits on dark ink in either scheme. */
    val GoldFixed = Color(0xFFFFCC33)

    /**
     * The brand orange as small TEXT on light surfaces. #FF6600 is a fill colour
     * and too light to read at text sizes on a pale surface, so light-mode text
     * uses this deeper shade; dark mode uses [PrimaryDark] directly.
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

    val TertiaryLight = Color(0xFFFFCC33)
    val OnTertiaryLight = Color(0xFF2B1F00)
    val TertiaryContainerLight = Color(0xFFFFF0BF)
    val OnTertiaryContainerLight = Color(0xFF261A00)

    val ErrorLight = Color(0xFFBA1A1A)
    val OnErrorLight = Color(0xFFFFFFFF)
    val ErrorContainerLight = Color(0xFFFFDAD6)
    val OnErrorContainerLight = Color(0xFF410002)

    val BackgroundLight = Color(0xFFF8FAF7)
    val OnBackgroundLight = Color(0xFF181C1A)
    val SurfaceLight = Color(0xFFF8FAF7)
    val OnSurfaceLight = Color(0xFF181C1A)
    val SurfaceVariantLight = Color(0xFFE4EBE6)
    val OnSurfaceVariantLight = Color(0xFF404944)
    val OutlineLight = Color(0xFF707974)
    val OutlineVariantLight = Color(0xFFC2CBC5)

    val InverseSurfaceLight = Color(0xFF2D3230)
    val InverseOnSurfaceLight = Color(0xFFEEF2EE)
    val InversePrimaryLight = Color(0xFFFF8A3D)

    val SurfaceBrightLight = Color(0xFFF8FAF7)
    val SurfaceDimLight = Color(0xFFD8DFDA)
    val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
    val SurfaceContainerLowLight = Color(0xFFF2F5F1)
    val SurfaceContainerLight = Color(0xFFECF0EB)
    val SurfaceContainerHighLight = Color(0xFFE6EBE5)
    val SurfaceContainerHighestLight = Color(0xFFE0E5E0)

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

    val TertiaryDark = Color(0xFFFFCC33)
    val OnTertiaryDark = Color(0xFF2B1F00)
    val TertiaryContainerDark = Color(0xFF4D4000)
    val OnTertiaryContainerDark = Color(0xFFFFE585)

    val ErrorDark = Color(0xFFFFB4AB)
    val OnErrorDark = Color(0xFF690005)
    val ErrorContainerDark = Color(0xFF93000A)
    val OnErrorContainerDark = Color(0xFFFFDAD6)

    val BackgroundDark = Color(0xFF0B0F0D)
    val OnBackgroundDark = Color(0xFFE3E8E4)
    val SurfaceDark = Color(0xFF121815)
    val OnSurfaceDark = Color(0xFFE3E8E4)
    val SurfaceVariantDark = Color(0xFF1B2420)
    val OnSurfaceVariantDark = Color(0xFFAEB9B2)
    val OutlineDark = Color(0xFF85908A)
    val OutlineVariantDark = Color(0xFF2F3A35)

    val InverseSurfaceDark = Color(0xFFE3E8E4)
    val InverseOnSurfaceDark = Color(0xFF1B2420)
    val InversePrimaryDark = Color(0xFFB84500)

    val SurfaceBrightDark = Color(0xFF2A3530)
    val SurfaceDimDark = Color(0xFF0B0F0D)
    val SurfaceContainerLowestDark = Color(0xFF080B0A)
    val SurfaceContainerLowDark = Color(0xFF0F1411)
    val SurfaceContainerDark = Color(0xFF151C19)
    val SurfaceContainerHighDark = Color(0xFF1B2420)
    val SurfaceContainerHighestDark = Color(0xFF222C28)

    /** See [SurfaceTintLight]: no brand tint is pushed onto surfaces. */
    val SurfaceTintDark = Color.Transparent
    val ScrimDark = Color(0xFF000000)
}
