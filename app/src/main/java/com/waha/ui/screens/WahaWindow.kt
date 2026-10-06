package com.waha.ui.screens

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Snapshot of the current window shape used to adapt the layout between compact and wide screens. */
data class WahaWindowInfo(
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
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Covers tablets, unfolded foldables and landscape phones; a folded foldable stays narrow.
    val useTabletLayout = isTablet || isLandscape ||
        configuration.screenWidthDp >= WIDE_LAYOUT_BREAKPOINT_DP

    return WahaWindowInfo(useTabletLayout = useTabletLayout)
}

/** Width reserved on the side of tablet layouts for the vertical navigation island. */
val TabletNavRailWidth: Dp = 84.dp
