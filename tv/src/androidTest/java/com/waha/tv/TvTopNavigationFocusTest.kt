package com.waha.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.waha.domain.VideoItem
import com.waha.tv.ui.screens.TvDestination
import com.waha.tv.ui.screens.TvHomeScreen
import com.waha.tv.ui.screens.TvMainScreen
import com.waha.tv.ui.screens.TvUiState
import com.waha.tv.ui.screens.WahaTvDimens
import com.waha.tv.ui.theme.WahaTvTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The TV shell's navigation, driven the only way a TV can be driven: with D-pad key
 * presses. Nothing here taps.
 *
 * The layout's exact geometry (how wide the rail opens, which card a step lands on) is a
 * design detail that may move; what these tests hold to is the contract a viewer feels:
 * the rail runs down the right edge and opens only while the remote is on it, there is no
 * bar above the shelf, the shelf is already on screen, the centre key plays, walking back
 * returns the viewer to the rail — on the destination they are actually on.
 */
@RunWith(AndroidJUnit4::class)
class TvTopNavigationFocusTest {

    @get:Rule
    val rule = createComposeRule()

    private var played: String? = null
    private var selected: TvDestination? = null

    private val videos = listOf(
        VideoItem(
            id = "v1",
            youtubeId = "v1",
            title = "حلقة الأصدقاء",
            meta = "أطفال",
            categoryKey = "أطفال",
            durationText = "4:30"
        ),
        VideoItem(
            id = "v2",
            youtubeId = "v2",
            title = "سلطة الفواكه",
            meta = "طبخ",
            categoryKey = "طبخ",
            durationText = "6:00"
        )
    )

    /** The node the remote is currently on. */
    private fun focused(): SemanticsNodeInteraction =
        rule.onNode(isFocused(), useUnmergedTree = true)

    /** The test tag of the node the remote is on. */
    private fun focusedTag(): String =
        focused().fetchSemanticsNode().config[SemanticsProperties.TestTag]

    /** The rail's current width — 56dp of icons, or 256dp with the names showing. */
    private fun railWidth(): Float =
        rule.onNodeWithTag(TvTestTags.SideBar).fetchSemanticsNode().boundsInRoot.width

    /** What that width should be, in the same pixels the layout is measured in. */
    private fun railWidthTarget(expanded: Boolean): Float =
        with(rule.density) { WahaTvDimens.railWidth(expanded).toPx() }

    private fun showShell() {
        rule.setContent {
            WahaTvTheme {
                var destination by remember { mutableStateOf(TvDestination.Home) }

                TvMainScreen(
                    destination = destination,
                    onDestinationSelect = {
                        selected = it
                        destination = it
                    }
                ) {
                    TvHomeScreen(
                        uiState = TvUiState.Ready(videos),
                        onVideoClick = { played = it.id },
                        onRetry = {}
                    )
                }
            }
        }
    }

    @Test
    fun landscape16By9Surface() {
        showShell()

        val root = rule.onRoot().fetchSemanticsNode().size

        assertEquals(16f / 9f, root.width.toFloat() / root.height.toFloat(), 0.2f)
    }

    @Test
    fun everyDestinationIsOnTheRailAndTheCurrentOneHasFocus() {
        showShell()

        TvDestination.railOrder.forEach { destination ->
            // Displayed, not merely present: a destination that is squeezed to
            // nothing is exactly the bug this test exists to catch.
            rule.onNodeWithTag(TvTestTags.nav(destination)).assertIsDisplayed()
        }

        rule.onNodeWithTag(TvTestTags.nav(TvDestination.Home)).assertIsFocused()
    }

    @Test
    fun theRailRunsDownTheRightEdgeWithTheShelfBesideIt() {
        showShell()

        val rail = rule.onNodeWithTag(TvTestTags.SideBar).fetchSemanticsNode().boundsInRoot
        val firstCard = rule.onAllNodesWithTag(TvTestTags.card("v1")).onFirst()
            .fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot

        assertTrue("the rail must be pinned to the right edge", root.right - rail.right <= 1f)
        assertTrue("the rail must be a strip of the screen, not half of it", rail.width < root.width / 2)
        // The shelf starts where the rail ends: nothing — no bar, no header — stands between
        // them.
        assertTrue("the content must stop beside the rail", firstCard.right <= rail.left + 1f)
        // Compact rows: the first row is already on screen without a single press.
        assertTrue("the first card must be visible without scrolling", firstCard.bottom <= root.bottom)
        assertTrue("the first row must start near the top", firstCard.top < root.height / 2)
    }

    @Test
    fun theRailChangesStateInASingleFrameWithoutAnimating() {
        showShell()
        rule.onNodeWithTag(TvTestTags.nav(TvDestination.Home)).assertIsFocused()
        assertEquals(railWidthTarget(expanded = true), railWidth(), 1.5f)

        // The clock is frozen, so an animated rail would be caught halfway: one frame after
        // the remote leaves, the rail is already at its resting width and its names are gone.
        rule.mainClock.autoAdvance = false
        rule.pressDPadKey(Key.DirectionLeft)
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.autoAdvance = true

        rule.waitForIdle()
        assertEquals("the rail must fold in a single frame", railWidthTarget(expanded = false), railWidth(), 1.5f)
        rule.onNodeWithTag(TvTestTags.navLabel(TvDestination.Home)).assertDoesNotExist()
    }

    @Test
    fun theRailShowsNamesOnlyWhileTheRemoteIsOnIt() {
        showShell()

        // The screen opens on الرئيسية, so the rail is open and every destination is named.
        TvDestination.railOrder.forEach { destination ->
            rule.onNodeWithTag(TvTestTags.navLabel(destination), useUnmergedTree = true)
                .assertIsDisplayed()
        }
        val openWidth = railWidth()

        walkIntoTheContent()

        // Leaving the rail folds it back to a strip of icons, and the names go with it.
        TvDestination.railOrder.forEach { destination ->
            rule.onNodeWithTag(TvTestTags.navLabel(destination)).assertDoesNotExist()
        }
        val foldedWidth = railWidth()
        assertTrue(
            "the folded rail must be narrower than the open one ($foldedWidth vs $openWidth)",
            foldedWidth < openWidth
        )
        assertEquals("the folded rail is exactly its resting width", railWidthTarget(false), foldedWidth, 1.5f)

        walkBackToTheRail()

        rule.onNodeWithTag(TvTestTags.navLabel(TvDestination.Home), useUnmergedTree = true)
            .assertIsDisplayed()
        assertEquals("the rail must reopen when the remote returns", railWidthTarget(true), railWidth(), 1.5f)
    }

    /**
     * The reason the shell watches which side the remote was on: walking back into the rail
     * must land on the destination the viewer is actually on, not on whichever icon happens
     * to sit at the same height as the card they left.
     */
    @Test
    fun returningToTheRailLandsOnTheDestinationTheViewerIsOn() {
        showShell()

        // Leave الرئيسية for المكتبة the way a viewer does: one step down, centre key.
        rule.pressDPadKey(Key.DirectionDown)
        rule.onNodeWithTag(TvTestTags.nav(TvDestination.Library)).assertIsFocused()
        rule.pressDPadKey(Key.DirectionCenter)
        assertEquals(TvDestination.Library, selected)

        walkIntoTheContent()
        rule.onNodeWithTag(TvTestTags.navLabel(TvDestination.Library)).assertDoesNotExist()

        walkBackToTheRail()

        rule.onNodeWithTag(TvTestTags.nav(TvDestination.Library)).assertIsFocused()
        // Focus coming back also reopens the rail, so the active page is named on screen.
        rule.onNodeWithTag(TvTestTags.navLabel(TvDestination.Library), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun upAndDownWalkTheRailInOrder() {
        showShell()

        rule.onNodeWithTag(TvTestTags.nav(TvDestination.Home)).assertIsFocused()

        // البحث is the rail's top action, above الرئيسية.
        rule.pressDPadKey(Key.DirectionUp)
        rule.onNodeWithTag(TvTestTags.nav(TvDestination.Search)).assertIsFocused()

        rule.pressDPadKey(Key.DirectionDown)
        rule.pressDPadKey(Key.DirectionDown)
        rule.onNodeWithTag(TvTestTags.nav(TvDestination.Library)).assertIsFocused()
    }

    /**
     * Browsing the rail is choosing: one step onto a destination opens its page, with
     * no centre key to remember.
     */
    @Test
    fun browsingTheRailOpensTheDestinationWithoutPressingTheCentreKey() {
        showShell()

        rule.onNodeWithTag(TvTestTags.nav(TvDestination.Home)).assertIsFocused()

        // One step down from الرئيسية is المكتبة (البحث sits above it).
        rule.pressDPadKey(Key.DirectionDown)

        rule.onNodeWithTag(TvTestTags.nav(TvDestination.Library)).assertIsFocused()
        assertEquals(TvDestination.Library, selected)
    }

    @Test
    fun theCentreKeyStillOpensTheDestinationTheRemoteIsOn() {
        showShell()

        rule.pressDPadKey(Key.DirectionDown)
        rule.onNodeWithTag(TvTestTags.nav(TvDestination.Library)).assertIsFocused()

        rule.pressDPadKey(Key.DirectionCenter)

        assertEquals(TvDestination.Library, selected)
    }

    @Test
    fun leftLeavesTheRailForTheContentAndTheCentreKeyPlays() {
        showShell()

        rule.onNodeWithTag(TvTestTags.nav(TvDestination.Home)).assertIsFocused()

        walkIntoTheContent()
        walkToACard()

        // The centre key plays whatever the remote is on, as on real hardware.
        val cardId = focusedTag().removePrefix("tv_card_")
        rule.pressDPadKey(Key.DirectionCenter)
        assertEquals(cardId, played)
    }

    @Test
    fun cardDetailIsReadableWithoutPressingAnything() {
        showShell()

        // The card's title, category and running time are all printed under (or on) the
        // artwork, so the shelf is readable before the remote has been touched.
        rule.onAllNodesWithText("حلقة الأصدقاء").onFirst().assertIsDisplayed()
        // The line under the title names the category, not the brand.
        rule.onAllNodesWithText("أطفال").onFirst().assertIsDisplayed()
        // The running time rides on the artwork.
        rule.onAllNodesWithText("4:30").onFirst().assertIsDisplayed()
    }

    /**
     * Leaves the rail with the left key — the direction the content lies in, since the
     * rail is the screen's rightmost column.
     */
    private fun walkIntoTheContent() {
        var presses = 0
        while (presses < 4 && !focusedTag().startsWith("tv_card_")) {
            rule.pressDPadKey(Key.DirectionLeft)
            presses++
        }

        assertTrue(
            "the left key must lead out of the rail, focus is on ${focusedTag()}",
            focusedTag().startsWith("tv_card_")
        )
    }

    /** Walks down onto a card, if the remote is not already on one. */
    private fun walkToACard() {
        var presses = 0
        while (presses < 4 && !focusedTag().startsWith("tv_card_")) {
            rule.pressDPadKey(Key.DirectionDown)
            presses++
        }

        assertTrue("the down key must reach the shelf, focus is on ${focusedTag()}", focusedTag().startsWith("tv_card_"))
    }

    /** Walks back to the rail, which is the rightmost thing on screen. */
    private fun walkBackToTheRail() {
        var presses = 0
        while (presses < 8 && !focusedTag().startsWith("tv_nav_")) {
            rule.pressDPadKey(Key.DirectionRight)
            presses++
        }

        assertTrue("the right key must lead back to the rail, focus is on ${focusedTag()}", focusedTag().startsWith("tv_nav_"))
    }
}
