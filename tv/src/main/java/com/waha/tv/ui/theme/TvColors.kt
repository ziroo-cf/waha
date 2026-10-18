package com.waha.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
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

    /** Text and glyphs drawn straight on media, where the background is a photo. */
    val OnMedia = Color.White

    /**
     * How see-through the navigation rail's glass panel is at its outer edge.
     *
     * Compose cannot blur what another node paints behind it (`Modifier.blur` only
     * affects a node's own content, and needs API 31+), so the rail's "glass" is
     * layering instead: a thinned-out surface colour, opaque enough to keep the
     * icons legible and thin enough that the shelf shows through the edge the
     * content scrolls against.
     */
    const val RailGlassAlpha = 0.92f

    /** The rail's glass, opaque at the outer edge and thinner towards the content. */
    @Composable
    fun railGlass(): Brush = Brush.horizontalGradient(
        listOf(
            Surface.copy(alpha = RailGlassAlpha),
            Surface.copy(alpha = RailGlassAlpha * 0.55f)
        )
    )

    /** Green (mobile's secondary) marks saved videos in badges. */
    val SavedBadge: Color
        @Composable get() = MaterialTheme.colorScheme.secondary

    /**
     * The player's control panel backdrop: a neutral near-black rather than the
     * brand's slightly green [SurfaceVariant], which read as a turquoise tint
     * behind the panel's chips. Controls on top are translucent white, so the
     * panel stays one quiet strip under the video.
     */
    val PanelBackground = Color(0xF2131313)

    /**
     * Fixed colours that do not change with the scheme: media scrims and the
     * "unfocused" card fill that sits under every thumbnail.
     */
    val MediaScrim = Color(0xCC000000)
    val MediaScrimSoft = Color(0x99000000)
    val ProgressTrack = Color(0x4DFFFFFF)
    val CardPlaceholder = WahaBrandColors.SurfaceVariantDark
}
