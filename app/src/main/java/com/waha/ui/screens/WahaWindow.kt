package com.waha.ui.screens

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Snapshot of the current window shape used to adapt the layout between
 * compact and wide screens.
 *
 * The "tablet" layout (two-column feeds + the right-hand vertical navigation
 * island) is shown whenever the window is wide enough to make use of it:
 * tablets, foldables when unfolded, and phones in landscape.
 */
data class WahaWindowInfo(
    val isTablet: Boolean,
    val isLandscape: Boolean,
    /** Whether the wide ("tablet") layout should be used for the current window. */
    val useTabletLayout: Boolean
) {
    /** Number of video columns the feeds should display. */
    val gridColumns: Int get() = if (useTabletLayout) 2 else 1
}

/** Width in dp at or above which the two-column tablet layout is used. */
private const val WIDE_LAYOUT_BREAKPOINT_DP = 600

@Composable
fun rememberWahaWindowInfo(): WahaWindowInfo {
    val configuration = LocalConfiguration.current
    // 600dp is the standard Material breakpoint between phones and tablets.
    val isTablet = configuration.smallestScreenWidthDp >= 600
    // Orientation-aware: a phone on its side has a wide window even though its
    // smallest width stays phone-sized.
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Foldables report a wide window once unfolded, so a width check covers them,
    // along with tablets and landscape phones. A folded foldable stays narrow and
    // keeps the compact layout, which is the desired behaviour.
    val useTabletLayout = isTablet || isLandscape ||
        configuration.screenWidthDp >= WIDE_LAYOUT_BREAKPOINT_DP

    return WahaWindowInfo(
        isTablet = isTablet,
        isLandscape = isLandscape,
        useTabletLayout = useTabletLayout
    )
}

/** Width reserved on the side of tablet layouts for the vertical navigation island. */
val TabletNavRailWidth: Dp = 84.dp
