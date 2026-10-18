package com.waha.tv.ui.screens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing and sizing for a 10-foot UI.
 *
 * The phone build's 16dp gutters and 15sp body text disappear across a living
 * room, so the TV build runs a deliberately larger scale: every measurement
 * here is roughly the phone value doubled.
 */
object WahaTvDimens {

    /** Outer gutter of every screen (phone: 16dp). */
    val ScreenPadding = 48.dp

    /** Vertical rhythm between rails and sections (phone: 20dp). */
    val SectionSpacing = 36.dp

    /** Gap between two cards inside a rail. */
    val CardSpacing = 16.dp

    /**
     * Fixed card width inside a rail. Together with [CardAspectRatio] this gives
     * the wide 16:9 landscape card a TV browses films and episodes with — about
     * three and a half of them fit a 720p screen, which is the point: the row is
     * meant to be walked, not read.
     */
    val CardWidth = 240.dp

    /**
     * The card artwork's shape: 16:9. The height is derived from the width at
     * layout time (never a separate constant) so the ratio cannot drift, and the
     * same card inside the 4-column grids stays 16:9 too.
     */
    const val CardAspectRatio = 16f / 9f

    /**
     * Card title lines under the artwork. Two lines fit a real Arabic title without
     * cutting it mid-phrase, and the card reserves both even for a short one so a
     * shelf of cards stays one height.
     */
    val CardTitleMaxLines = 2

    /**
     * Gutter inside a card's detail block, and the gap between its two lines.
     *
     * Title on top, channel underneath, both flush to the same gutter on the left
     * and right, which is what keeps a shelf of cards even.
     */
    val CardMetaPaddingHorizontal = 14.dp
    val CardMetaPaddingTop = 12.dp
    val CardMetaPaddingBottom = 14.dp
    val CardMetaLineSpacing = 4.dp

    /**
     * The duration badge on the artwork: a small dark pill in the card's bottom-left
     * corner, inset just far enough that it never touches the card's edge.
     */
    val BadgeInset = 10.dp
    val BadgePaddingHorizontal = 8.dp
    val BadgePaddingVertical = 3.dp
    val BadgeCornerRadius = 6.dp

    /**
     * Width of the navigation rail while the remote is elsewhere: one icon, one gutter.
     *
     * The rail is drawn by this module rather than by Compose-for-TV's own drawer, whose
     * items animate their width and slide their names in; the rail has to change state in
     * a single frame, so its geometry is decided here.
     */
    val RailWidthResting = 56.dp

    /** Width of the rail once the remote is on it: an icon and a destination name. */
    val RailWidthFocused = 256.dp

    /** The rail's width for a given state — the single place the rail's geometry is decided. */
    fun railWidth(expanded: Boolean): Dp = if (expanded) RailWidthFocused else RailWidthResting

    /** Height of the rail's brand band, above the destinations. */
    val RailHeaderHeight = 76.dp

    /** The Waha mark in the rail's header (the shared launcher icon, at 10-foot size). */
    val RailLogoSize = 32.dp

    /** Height of one destination in the rail. */
    val RailItemHeight = 48.dp

    /** A destination's icon. */
    val RailIconSize = 24.dp

    /** Gap between a rail item's icon and its name (the brand uses it too). */
    val RailLabelSpacing = 12.dp

    /** Gap between the rail's destinations. */
    val RailItemSpacing = 8.dp

    /**
     * The rail's gutter, used by everything in it — the brand, the divider and the items —
     * so the mark, the icons and the names all start from the same edge. An item is
     * [RailEdgePadding] + an icon + [RailEdgePadding], which is exactly [RailWidthResting]:
     * the folded rail is one icon wide with nothing left over.
     */
    val RailEdgePadding = 16.dp

    /** Breath above the brand and between the rail's bands. */
    val RailTopPadding = 24.dp
    val RailDividerSpacing = 10.dp

    /** Corner radius for cards, badges and panels. */
    val CornerRadius = 12.dp

    /**
     * Focus enlargement: none.
     *
     * D-pad focus is shown by a crisp accent border (and a soft accent glow)
     * rather than by growing the focused item, so moving the remote never shifts
     * the layout under the viewer — the highlight is exactly as wide as the card.
     */
    const val FocusScale = 1f

    /** Focus border thickness in the brand accent — thick enough to read across a room. */
    val FocusBorderWidth = 4.dp

    /** Focus glow radius. */
    val FocusGlowRadius = 16.dp

    /** The player's bottom control panel. */
    val PlayerPanelPadding = 32.dp

    /** Height of a focusable list row (recent searches, and the like). */
    val ListRowHeight = 64.dp

    /** Gap between keyboard keys — tight, so the grid does not eat the results. */
    val KeyboardKeyGap = 4.dp

    /**
     * One on-screen keyboard key. 36dp (not the usual 48dp) keeps the whole
     * keyboard short enough to leave the search results visible on a 720p TV,
     * which only has about 540dp of height to share between the two.
     */
    val KeyboardKeyWidth = 56.dp
    val KeyboardKeyHeight = 36.dp

    /** How tall the progress bar over a Continue Watching card's artwork is. */
    val ProgressBarHeight = 6.dp
}
