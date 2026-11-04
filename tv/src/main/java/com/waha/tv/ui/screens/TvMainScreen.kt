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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
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
     * The destination's icon — solid whether it is resting, focused or selected —
     * resolved on demand rather than in the enum's constructor so the navigation model
     * itself stays plain data a JVM unit test can read without loading Compose artwork.
     */
    val icon: ImageVector
        get() = when (this) {
            Home -> TvIcons.Home
            Search -> TvIcons.Search
            Library -> TvIcons.VideoLibrary
            Settings -> TvIcons.Settings
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
 * The rail is also an **overlay**, not a column of the layout: the shelf is inset by the
 * folded rail's width once and never resized again, so opening the rail paints over the
 * shelf instead of re-measuring every row on it. A 200dp width change would otherwise
 * relayout the whole visible library in the one frame the viewer is moving the remote.
 *
 * Walking the rail only moves the highlight; the centre key opens the page. That is one
 * press more than "focus is choosing", and it is the right trade on a weak TV: every
 * focus step would otherwise tear down a screen and build the next one — a search page
 * is a keyboard of thirty-five keys — so scrolling the rail quickly would stutter through
 * pages the viewer never meant to open. The press that opens a page also hands the remote to
 * it — focus steps into the content and the rail folds, so the page is never left with its
 * open-rail edge covered. Coming back from the content is still snapped onto the destination
 * the viewer is actually on, rather than whichever icon the focus engine happened to find at
 * that height.
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
    /**
     * Bumped when the player closes. Non-zero values put the remote back on the card the
     * viewer opened, or on the rail when that card has left the screen.
     */
    focusRestoreTicks: Int = 0,
    content: @Composable () -> Unit
) {
    // Which destination the remote is on, if any: that, and only that, is what opens the
    // rail. It is read from the destinations' own focus rather than from the rail's
    // container, because a container that is not itself focusable is not told when a child
    // takes focus on a real TV.
    var focusedDestination by remember { mutableStateOf<TvDestination?>(null) }

    // True for the one destination change the viewer made with the centre key. The page
    // has just opened, so the remote belongs to it: focus steps into the content and the
    // rail folds, instead of being pulled back onto the icon that opened it.
    var enteringPage by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Bumped when the remote arrives from a page. The rail answers it by moving the remote
    // to the destination the viewer is actually on (see TvSideRail).
    var refocusRequests by remember { mutableStateOf(0) }
    // True once the viewer has pressed a key on a page. It is set from the key itself rather
    // than from a focus report, because a focus report cannot tell an arrival from the page
    // apart from a step inside the rail (both look like "no destination was focused, now one
    // is"); a key press can, since the rail was demonstrably out of focus when it happened.
    var pressedOnAPage by remember { mutableStateOf(false) }
    // Hoisted out of [TvSideRail] so the shell can fall back to it when a card that the
    // player covered is no longer on screen.
    val railFocusRequester = remember { FocusRequester() }
    val cardFocusMemory = LocalTvCardFocusMemory.current

    // The player has just closed. Its handler used to hand the remote back to the rail,
    // which on a shelf of six rows means the viewer walks back to their card by hand; the
    // card they opened is still composed underneath, so focus goes there instead. Only a
    // card that has since left the screen falls back to the rail.
    LaunchedEffect(focusRestoreTicks) {
        if (focusRestoreTicks == 0) return@LaunchedEffect
        withFrameNanos { }
        val restored = cardFocusMemory?.restore() == true
        if (!restored) runCatching { railFocusRequester.requestFocus() }
    }
    // Hands the remote to the page the centre key just opened. It runs after the new
    // destination has been composed, because the page's own nodes do not exist yet when
    // the key is handled; one frame gives them a chance to be placed.
    LaunchedEffect(enteringPage, destination) {
        if (!enteringPage) return@LaunchedEffect
        withFrameNanos { }
        focusManager.moveFocus(FocusDirection.Left)
        enteringPage = false
    }

    // One background for the whole shell lives in TvApp, above this: filling the screen
    // here as well would paint the same colour twice every frame.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && focusedDestination == null) {
                    pressedOnAPage = true
                }
                false
            }
    ) {
        // The shelf reserves the folded rail's strip once, and keeps it whatever the rail
        // does: the content's width is constant, so only the rail itself is ever measured
        // again when the remote enters or leaves it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = WahaTvDimens.RailWidthResting)
        ) { content() }

        // The rail sits on the screen's right edge — the edge an Arabic layout starts from
        // — above the shelf, which it covers only while the remote is on it.
        TvSideRail(
            modifier = Modifier.align(Alignment.TopStart),
            current = destination,
            expanded = focusedDestination != null,
            refocusRequests = refocusRequests,
            selectedRequester = railFocusRequester,
            onSelect = { selected ->
                enteringPage = true
                onDestinationSelect(selected)
            },
            skipAutoFocus = enteringPage,
            onItemFocusChanged = { item, itemFocused ->
                if (itemFocused) {
                    // The remote has just walked in from a page: the focus engine picked the
                    // destination nearest the card the viewer left, which is rarely the page
                    // they are on. Coming back to the rail should land on the working icon —
                    // that is where the viewer left off, and the one thing on the rail they
                    // can be sure about.
                    val arrivedFromPage = pressedOnAPage
                    pressedOnAPage = false
                    focusedDestination = item
                    if (arrivedFromPage && item != destination) refocusRequests++
                } else if (focusedDestination == item) {
                    // A departure only clears the state if the viewer has not already
                    // landed on another destination, so two reports in one dispatch can
                    // not fold an open rail.
                    focusedDestination = null
                }
            }
        )
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
    /** The requester on the working destination, owned by the shell. */
    selectedRequester: FocusRequester,
    onSelect: (TvDestination) -> Unit,
    /** True while the page a centre-key press just opened still owns the remote. */
    skipAutoFocus: Boolean,
    onItemFocusChanged: (TvDestination, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // Read once in composition: the hairline is drawn in the draw phase, where the theme
    // cannot be read.
    val lineColor = WahaTvColors.Line

    Column(
        modifier = modifier
            .width(WahaTvDimens.railWidth(expanded))
            .fillMaxHeight()
            // One opaque fill: the rail is open over the shelf while the remote is on it,
            // so translucency here would show cards through the names.
            .background(WahaTvColors.Surface)
            .drawBehind {
                // A 1dp hairline on the edge the shelf runs against — the rail's left edge,
                // which is where the content is in this right-to-left layout.
                drawRect(
                    color = lineColor,
                    topLeft = Offset.Zero,
                    size = Size(1.dp.toPx(), size.height)
                )
            }
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
    LaunchedEffect(current, refocusRequests) {
        // A page the viewer has just opened owns the remote; the rail does not take it
        // back. The arrival from the content still snaps onto the working destination.
        if (skipAutoFocus) return@LaunchedEffect
        selectedRequester.requestFocus()
    }
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
 * Focus is shown the way the rest of the app shows it — an accent fill and a thick accent
 * ring, both applied in the same frame the remote lands — and the name is drawn only while
 * the rail is open. The glyph itself never changes: it is solid whether the remote is on
 * the item or not. Nothing here animates: the item is the same size in every state, so
 * walking the rail cannot move anything.
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
            imageVector = destination.icon,
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
