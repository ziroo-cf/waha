package com.waha.tv.ui.screens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing and sizing for a 10-foot UI.
 *
 * The phone build's 16dp gutters and 15sp body text disappear across a living
 * room, so the TV build runs a deliberately larger scale: every measurement
 * here is roughly the phone value doubled.
 *
 * [UIScale] is the one knob for that: the numbers below are the values the
 * screens were originally built with, and multiplying them here grows the whole
 * browse UI without touching a single screen. The player keeps its own numbers
 * (see [PlayerPanelPadding] and the constants in `TvPlayerScreen`), because the
 * video surface is already the largest thing on screen and a bigger control
 * strip would only cover it.
 */
object WahaTvDimens {

    /** How much larger the browse UI is than the values written below. */
    private const val UIScale = 1.5f

    /** Outer gutter of every screen (phone: 16dp). */
    val ScreenPadding = (48f * UIScale).dp

    /** Vertical rhythm between rails and sections (phone: 20dp). */
    val SectionSpacing = (36f * UIScale).dp

    /** Gap between two cards inside a rail. */
    val CardSpacing = (16f * UIScale).dp

    /**
     * Fixed card width inside a rail. Together with [CardAspectRatio] this gives
     * the wide 16:9 landscape card a TV browses films and episodes with — about
     * three of them fit a 720p screen, which is the point: the row is meant to be
     * walked, not read.
     */
    val CardWidth = (240f * UIScale).dp

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
    val CardMetaPaddingHorizontal = (14f * UIScale).dp
    val CardMetaPaddingTop = (12f * UIScale).dp
    val CardMetaPaddingBottom = (14f * UIScale).dp
    val CardMetaLineSpacing = (4f * UIScale).dp

    /**
     * The duration badge on the artwork: a small dark pill in the card's bottom-left
     * corner, inset just far enough that it never touches the card's edge.
     */
    val BadgeInset = (10f * UIScale).dp
    val BadgePaddingHorizontal = (8f * UIScale).dp
    val BadgePaddingVertical = (3f * UIScale).dp
    val BadgeCornerRadius = (6f * UIScale).dp

    /**
     * Width of the navigation rail while the remote is elsewhere: one icon, one gutter.
     *
     * The rail is drawn by this module rather than by Compose-for-TV's own drawer, whose
     * items animate their width and slide their names in; the rail has to change state in
     * a single frame, so its geometry is decided here.
     *
     * The rail is an overlay, not a column in the screen's layout: the content is always
     * inset by exactly this width, so opening the rail to [RailWidthFocused] draws over
     * the shelf instead of re-measuring every card on it.
     */
    val RailWidthResting = (56f * UIScale).dp

    /** Width of the rail once the remote is on it: an icon and a destination name. */
    val RailWidthFocused = (256f * UIScale).dp

    /** The rail's width for a given state — the single place the rail's geometry is decided. */
    fun railWidth(expanded: Boolean): Dp = if (expanded) RailWidthFocused else RailWidthResting

    /** Height of the rail's brand band, above the destinations. */
    val RailHeaderHeight = (76f * UIScale).dp

    /** The Waha mark in the rail's header (the shared launcher icon, at 10-foot size). */
    val RailLogoSize = (32f * UIScale).dp

    /** Height of one destination in the rail. */
    val RailItemHeight = (48f * UIScale).dp

    /** A destination's icon. */
    val RailIconSize = (24f * UIScale).dp

    /** Gap between a rail item's icon and its name (the brand uses it too). */
    val RailLabelSpacing = (12f * UIScale).dp

    /** Gap between the rail's destinations. */
    val RailItemSpacing = (8f * UIScale).dp

    /**
     * The rail's gutter, used by everything in it — the brand, the divider and the items —
     * so the mark, the icons and the names all start from the same edge. An item is
     * [RailEdgePadding] + an icon + [RailEdgePadding], which is exactly [RailWidthResting]:
     * the folded rail is one icon wide with nothing left over.
     */
    val RailEdgePadding = (16f * UIScale).dp

    /** Breath above the brand and between the rail's bands. */
    val RailTopPadding = (24f * UIScale).dp
    val RailDividerSpacing = (10f * UIScale).dp

    /** Corner radius for cards, badges and panels. */
    val CornerRadius = (12f * UIScale).dp

    /**
     * Focus enlargement: none.
     *
     * D-pad focus is shown by a crisp accent border rather than by growing the
     * focused item, so moving the remote never shifts the layout under the viewer —
     * the highlight is exactly as wide as the card.
     */
    const val FocusScale = 1f

    /** Focus border thickness in the brand accent — thick enough to read across a room. */
    val FocusBorderWidth = (4f * UIScale).dp

    /**
     * The player's bottom control panel.
     *
     * Deliberately not scaled: the panel sits over the video, and growing it by half
     * would eat the frame the viewer is watching.
     */
    val PlayerPanelPadding = 32.dp

    /** Height of a focusable list row (recent searches, and the like). */
    val ListRowHeight = (64f * UIScale).dp

    /** Gap between keyboard keys — tight, so the grid does not eat the results. */
    val KeyboardKeyGap = (4f * UIScale).dp

    /**
     * One on-screen keyboard key. Kept in proportion with the rest of the scaled
     * UI: seven keys and their gaps still fit a 720p screen's width, and the whole
     * keyboard stays short enough to leave the search results visible.
     */
    val KeyboardKeyWidth = (56f * UIScale).dp
    val KeyboardKeyHeight = (36f * UIScale).dp

    /** How tall the progress bar over a Continue Watching card's artwork is. */
    val ProgressBarHeight = (6f * UIScale).dp
}
