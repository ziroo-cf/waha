package com.waha.tv.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.waha.tv.TvTestTags
import com.waha.tv.ui.theme.WahaTvColors

/**
 * The TV destinations, in the order the app's pages sit in.
 *
 * Level 1 of the navigation is the rail (see [railOrder] for the order the remote walks
 * it). The first entry is the page the app opens on.
 */
enum class TvDestination(val label: String) {
    Home("الرئيسية"),
    Search("البحث"),
    Library("المكتبة"),
    Settings("الإعدادات");

    /**
     * The destination's icon, resolved on demand rather than in the enum's
     * constructor so the navigation model itself stays plain data a JVM unit
     * test can read without loading Compose artwork.
     *
     * This is the resting glyph: outlined, to sit quietly in the folded rail.
     * The remote landing on the item swaps it for [focusedIcon].
     */
    val icon: ImageVector
        get() = when (this) {
            Home -> Icons.Outlined.Home
            Search -> Icons.Outlined.Search
            Library -> Icons.Outlined.VideoLibrary
            Settings -> Icons.Outlined.Settings
        }

    /**
     * The solid twin of [icon], drawn the moment the remote lands on the item.
     *
     * Outlined and filled are different glyphs rather than one tint, which is how a
     * TV says "the remote is here" at a glance from the sofa: the shape itself
     * fills in, in the same frame the focus arrives.
     */
    val focusedIcon: ImageVector
        get() = when (this) {
            Home -> Icons.Filled.Home
            Search -> Icons.Filled.Search
            Library -> Icons.Filled.VideoLibrary
            Settings -> Icons.Filled.Settings
        }

    companion object {
        /** The three content sections, in the order the rail lists them. */
        val sections: List<TvDestination> = listOf(Home, Library, Settings)

        /**
         * The rail's destinations, top to bottom: البحث — the rail's own top action, which
         * is why it is not one of [sections] — and then the three sections.
         */
        val railOrder: List<TvDestination> = listOf(Search) + sections
    }
}

/**
 * The app shell for the 10-foot UI: a navigation rail down the right edge — the edge an
 * Arabic layout starts from, so the rail mirrors where YouTube for TV puts it — and the
 * current screen beside it.
 *
 * The rail changes state in a single frame: while the remote is elsewhere it is a strip of
 * icons, and the moment it lands on the rail the strip widens and every destination's name
 * is simply there — no width animation, no sliding, no fading, nothing that moves under the
 * viewer's eye. This is why the rail is drawn here rather than with Compose-for-TV's
 * `NavigationDrawer`: that component animates its own width and slides each name in, and
 * offers no way to turn the animation off.
 *
 * Walking the rail is choosing: the moment the remote lands on a destination the page
 * behind it changes, with no centre press to remember. Only one case is exempt — coming
 * back from the content, where the press that left the page is spent snapping the remote
 * onto the destination the viewer is actually on instead of opening whichever icon the
 * focus engine happened to find at that height.
 *
 * There is no bar above the content: the rail names every page, and a 10-foot screen has
 * no room to spend a header on repeating the destination that is already highlighted two
 * inches away. The whole shell runs in Waha's usual right-to-left direction; nothing here
 * flips it.
 */
@Composable
fun TvMainScreen(
    destination: TvDestination,
    onDestinationSelect: (TvDestination) -> Unit,
    content: @Composable () -> Unit
) {
    // Which destination the remote is on, if any: that, and only that, is what opens the
    // rail. It is read from the destinations' own focus rather than from the rail's
    // container, because a container that is not itself focusable is not told when a child
    // takes focus on a real TV.
    var focusedDestination by remember { mutableStateOf<TvDestination?>(null) }

    // Bumped when the remote arrives from a page. The rail answers it by moving the remote
    // to the destination the viewer is actually on (see TvSideRail).
    var refocusRequests by remember { mutableStateOf(0) }
    // True once the viewer has pressed a key on a page. It is set from the key itself rather
    // than from a focus report, because a focus report cannot tell an arrival from the page
    // apart from a step inside the rail (both look like "no destination was focused, now one
    // is"); a key press can, since the rail was demonstrably out of focus when it happened.
    var pressedOnAPage by remember { mutableStateOf(false) }
    // True once the viewer has pressed a key *on the rail*, which is what makes the next
    // destination focus a deliberate choice worth opening. Without it, the focus the layout
    // itself places when a screen appears (the rail's first icon, or the snap back to the
    // working destination) would be read as the viewer choosing it.
    var movedOnTheRail by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(WahaTvColors.Background)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    if (focusedDestination == null) pressedOnAPage = true else movedOnTheRail = true
                }
                false
            }
    ) {
        // The rail is the first column, which in this right-to-left layout is the screen's
        // right edge; the screen fills what is left.
        TvSideRail(
            current = destination,
            expanded = focusedDestination != null,
            refocusRequests = refocusRequests,
            onSelect = onDestinationSelect,
            onItemFocusChanged = { item, itemFocused ->
                if (itemFocused) {
                    // The remote has just walked in from a page: the focus engine picked the
                    // destination nearest the card the viewer left, which is rarely the page
                    // they are on. Coming back to the rail should land on the working icon —
                    // that is where the viewer left off, and the one thing on the rail they
                    // can be sure about.
                    val arrivedFromPage = pressedOnAPage
                    val browsedByTheViewer = !arrivedFromPage && movedOnTheRail
                    pressedOnAPage = false
                    movedOnTheRail = false
                    focusedDestination = item
                    if (arrivedFromPage) {
                        if (item != destination) refocusRequests++
                    } else if (browsedByTheViewer && item != destination) {
                        // Browsing the rail is choosing: the page opens under the remote as
                        // soon as it lands on a destination, with no centre press. Only the
                        // arrival from a page is exempt — that press belongs to the snap
                        // back to the page the viewer is on (above).
                        onDestinationSelect(item)
                    }
                } else if (focusedDestination == item) {
                    // A departure only clears the state if the viewer has not already
                    // landed on another destination, so two reports in one dispatch can
                    // not fold an open rail.
                    focusedDestination = null
                }
            }
        )

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) { content() }
    }
}

/**
 * The rail: the brand, the search action, and then the four sections.
 *
 * Only destinations are focusable, so "down" from the brand walks straight onto البحث and
 * "left" walks into the content — the brand itself is decoration and never eats a press.
 */
@Composable
private fun TvSideRail(
    current: TvDestination,
    expanded: Boolean,
    refocusRequests: Int,
    onSelect: (TvDestination) -> Unit,
    onItemFocusChanged: (TvDestination, Boolean) -> Unit
) {
    val selectedRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .width(WahaTvDimens.railWidth(expanded))
            .fillMaxHeight()
            .background(WahaTvColors.railGlass())
            .testTag(TvTestTags.SideBar),
        verticalArrangement = Arrangement.spacedBy(WahaTvDimens.RailItemSpacing)
    ) {
        Spacer(modifier = Modifier.height(WahaTvDimens.RailTopPadding))

        TvRailBrand(expanded = expanded)

        TvDestination.railOrder.forEachIndexed { index, destination ->
            // A hairline splits the rail into the two groups a viewer thinks in: what you
            // search with, and where you go.
            if (index == 1) {
                TvRailDivider(expanded = expanded)
            }

            TvRailItem(
                destination = destination,
                selected = destination == current,
                expanded = expanded,
                focusRequester = if (destination == current) selectedRequester else null,
                onClick = { onSelect(destination) },
                onFocusChanged = { focused -> onItemFocusChanged(destination, focused) }
            )
        }
    }

    // The remote starts every screen on the destination the viewer just pressed, which also
    // means the rail is open from the moment that screen appears. The same request answers
    // an arrival from the content: focus has just landed on whichever icon the focus engine
    // found, and the viewer expects the one that is running.
    LaunchedEffect(current, refocusRequests) { selectedRequester.requestFocus() }
}

/** The rail's header: the Waha mark always, the wordmark while the rail is open. */
@Composable
private fun TvRailBrand(expanded: Boolean) {
    Row(
        modifier = Modifier
            .height(WahaTvDimens.RailHeaderHeight)
            .fillMaxWidth()
            .padding(horizontal = WahaTvDimens.RailEdgePadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WahaTvDimens.RailLabelSpacing)
    ) {
        TvBrandMark(logoSize = WahaTvDimens.RailLogoSize)

        // Simply absent while the rail is folded — nothing slides, nothing fades.
        if (expanded) {
            Text(
                text = "واحة",
                color = WahaTvColors.TextWarm,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )
        }
    }
}

/** A hairline on the rail's own width, between the search action and the sections. */
@Composable
private fun TvRailDivider(expanded: Boolean) {
    val lineWidth = WahaTvDimens.railWidth(expanded) - WahaTvDimens.RailEdgePadding * 2

    Box(
        modifier = Modifier
            .padding(start = WahaTvDimens.RailEdgePadding)
            .width(lineWidth)
            .height(1.dp)
            .background(WahaTvColors.Line)
    )
}

/**
 * One destination in the rail.
 *
 * Focus is shown the way the rest of the app shows it — an accent fill, a thick accent
 * ring and the destination's outlined icon swapped for its filled twin, all applied in the
 * same frame the remote lands — and the name is drawn only while the rail is open. Nothing
 * here animates: the item is the same size in every state, so walking the rail cannot move
 * anything.
 */
@Composable
private fun TvRailItem(
    destination: TvDestination,
    selected: Boolean,
    expanded: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val contentColor = tvItemContentColor(selected = selected, isFocused = isFocused)

    Row(
        modifier = Modifier
            // Full bleed: the highlight and the focus ring wrap the whole row — the rail's
            // own width — so the icon does not look ringed in a box of its own.
            .fillMaxWidth()
            .height(WahaTvDimens.RailItemHeight)
            .clip(RoundedCornerShape(WahaTvDimens.CornerRadius))
            .background(if (isFocused) WahaTvColors.Accent else Color.Transparent)
            .border(
                border = when {
                    isFocused -> BorderStroke(WahaTvDimens.FocusBorderWidth, WahaTvColors.Accent)
                    // The page the viewer is on keeps a thin accent outline while the remote
                    // is elsewhere, so "where am I" never depends on focus alone.
                    selected -> BorderStroke(1.dp, WahaTvColors.Accent)
                    else -> BorderStroke(0.dp, Color.Transparent)
                },
                shape = RoundedCornerShape(WahaTvDimens.CornerRadius)
            )
            // The centre key comes before the focus target in this chain, so the key the
            // remote sends bubbles up into it. There is no ripple and no press feedback to
            // animate: the rail's highlight is the whole feedback.
            .tvClickOnEnterKey(onClick)
            // Reported synchronously, in the focus dispatch itself, so the shell can open
            // the rail in the same frame the remote lands on it.
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .then(
                if (focusRequester == null) Modifier
                else Modifier.focusRequester(focusRequester)
            )
            .focusable(interactionSource = interactionSource)
            .semantics { semanticsOnClick(label = destination.label) { onClick(); true } }
            .testTag(TvTestTags.nav(destination))
            // The gutter is inside the highlight: 16dp + the icon + 16dp is exactly the
            // folded rail's width, which is what centres the icon while it is folded.
            .padding(horizontal = WahaTvDimens.RailEdgePadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WahaTvDimens.RailLabelSpacing)
    ) {
        Icon(
            imageVector = if (isFocused) destination.focusedIcon else destination.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(WahaTvDimens.RailIconSize)
        )

        if (expanded) {
            Text(
                text = destination.label,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(TvTestTags.navLabel(destination))
            )
        }
    }
}
