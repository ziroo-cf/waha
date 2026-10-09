package com.waha.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import com.waha.brand.WahaBrandColors

/**
 * Waha's colour tokens for the 10-foot UI, mirroring the phone build.
 *
 * Every theme-aware token reads from [MaterialTheme] roles, exactly like
 * `:mobile`'s `Color.kt`, so the TV scheme is built from the same
 * [WahaBrandColors] literals the phone uses — one brand, two screen sizes.
 */
object WahaTvColors {

    /** The warm-orange brand accent (mobile's primary) used for focus and progress. */
    val Accent: Color
        @Composable get() = MaterialTheme.colorScheme.primary

    val OnAccent: Color
        @Composable get() = MaterialTheme.colorScheme.onPrimary

    val Background: Color
        @Composable get() = MaterialTheme.colorScheme.background

    val Surface: Color
        @Composable get() = MaterialTheme.colorScheme.surface

    val SurfaceVariant: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceVariant

    val TextWarm: Color
        @Composable get() = MaterialTheme.colorScheme.onBackground

    val TextMuted: Color
        @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

    /** tv-material calls the neutral outline role `border`. */
    val Line: Color
        @Composable get() = MaterialTheme.colorScheme.border.copy(alpha = 0.3f)

    /** Green (mobile's secondary) marks saved videos in badges. */
    val SavedBadge: Color
        @Composable get() = MaterialTheme.colorScheme.secondary

    /**
     * Fixed colours that do not change with the scheme: media scrims and the
     * "unfocused" card fill that sits under every thumbnail.
     */
    val MediaScrim = Color(0xCC000000)
    val MediaScrimSoft = Color(0x99000000)
    val ProgressTrack = Color(0x4DFFFFFF)
    val CardPlaceholder = WahaBrandColors.SurfaceVariantDark
}
