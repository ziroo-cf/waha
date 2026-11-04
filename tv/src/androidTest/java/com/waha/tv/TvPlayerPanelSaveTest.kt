package com.waha.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.waha.data.SavedVideosStore
import com.waha.domain.VideoItem
import com.waha.tv.ui.screens.PlayerPanel
import com.waha.tv.ui.theme.WahaTvTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The player panel's controls, driven with real D-pad presses: the transport row and
 * the save pill at its far edge.
 *
 * The panel is a destination of its own — while it is open the video gives up the
 * D-pad — so these tests also pin that walking to the panel's outer edge never drops
 * focus on the viewer.
 */
@RunWith(AndroidJUnit4::class)
class TvPlayerPanelSaveTest {

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun clearSaves() {
        SavedVideosStore.clear()
    }

    @After
    fun clearSavesAgain() {
        SavedVideosStore.clear()
    }

    /** The node the remote is currently on (merged, so a pill counts as one control). */
    private fun focused(): SemanticsNodeInteraction = rule.onNode(isFocused())

    /** The test tag of the node the remote is on, or what it is when it has none. */
    private fun focusedTag(): String {
        val node = focused().fetchSemanticsNode()
        if (!node.config.contains(SemanticsProperties.TestTag)) {
            val keys = node.config.joinToString { it.key.name }
            return "untagged[$keys] at ${node.boundsInRoot}"
        }
        return node.config[SemanticsProperties.TestTag]
    }

    private fun showPanel() {
        rule.setContent {
            WahaTvTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    PlayerPanel(
                        videoId = "v1",
                        isPlaying = true,
                        onTogglePlayback = {},
                        onSeekBackward = {},
                        onSeekForward = {},
                        suggestions = emptyList(),
                        onVideoSelect = {},
                        initialFocusRequester = remember { FocusRequester() },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }

    @Test
    fun theSavePillSitsAtTheFarEndOfTheTransportRow() {
        showPanel()

        val play = rule.onNodeWithTag(TvTestTags.playerControl("play_pause"))
            .fetchSemanticsNode().boundsInRoot
        val save = rule.onNodeWithText("حفظ").fetchSemanticsNode().boundsInRoot

        assertTrue(
            "the save pill must share the transport row",
            save.top < play.bottom && play.top < save.bottom
        )
        // The layout is right-to-left: the transport buttons start at the right edge,
        // so the save pill (the row's far end) is further left.
        assertTrue("save must follow the transport buttons", save.center.x < play.center.x)
    }

    @Test
    fun pressingTheCentreKeyTogglesTheSave() {
        showPanel()

        rule.onNodeWithText("حفظ").assertIsDisplayed().requestFocus()
        rule.pressDPadKey(Key.DirectionCenter)

        assertTrue("the video must be saved", SavedVideosStore.savedIds.contains("v1"))
        rule.onNodeWithText("محفوظ").assertIsDisplayed()

        rule.pressDPadKey(Key.DirectionCenter)

        assertFalse("a second press must unsave it", SavedVideosStore.savedIds.contains("v1"))
        rule.onNodeWithText("حفظ").assertIsDisplayed()
    }

    /**
     * The panel is one row now that the quality chips are gone: the arrows must walk
     * from the transport buttons to the save pill and back, and must never leave the
     * panel while doing it.
     */
    @Test
    fun arrowsWalkTheTransportRowWithoutDroppingFocus() {
        showPanel()

        rule.onNodeWithTag(TvTestTags.playerControl("play_pause")).requestFocus()

        repeat(4) {
            rule.pressDPadKey(Key.DirectionLeft)
            assertTrue(
                "left dropped focus out of the panel: ${focusedTag()}",
                focusedTag().startsWith("tv_player_")
            )
        }

        repeat(4) {
            rule.pressDPadKey(Key.DirectionRight)
            assertTrue(
                "right dropped focus out of the panel: ${focusedTag()}",
                focusedTag().startsWith("tv_player_")
            )
        }

        // Up and down have nowhere to go in a one-row panel, so they must keep the
        // remote on a control rather than let it escape to the video behind.
        rule.pressDPadKey(Key.DirectionDown)
        assertTrue("down dropped focus: ${focusedTag()}", focusedTag().startsWith("tv_player_"))
        rule.pressDPadKey(Key.DirectionUp)
        assertTrue("up dropped focus: ${focusedTag()}", focusedTag().startsWith("tv_player_"))
    }

    private fun suggestion(id: String) = VideoItem(
        id = id,
        youtubeId = id,
        title = "فيديو $id",
        meta = "أناشيد",
        categoryKey = "أناشيد"
    )

    private fun showPanelWithSuggestions() {
        rule.setContent {
            WahaTvTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    PlayerPanel(
                        videoId = "v1",
                        isPlaying = true,
                        onTogglePlayback = {},
                        onSeekBackward = {},
                        onSeekForward = {},
                        suggestions = listOf(suggestion("s1"), suggestion("s2"), suggestion("s3")),
                        onVideoSelect = {},
                        initialFocusRequester = remember { FocusRequester() },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }

    /**
     * Down leaves the transport row for the suggestions from every control, not just the
     * play button: the focus search used to read the space under the outer buttons as
     * empty and hand the remote to a node the viewer could not see.
     */
    @Test
    fun theDownKeyReachesTheSuggestionsFromEveryTransportControl() {
        showPanelWithSuggestions()

        for (control in listOf("rewind", "play_pause", "forward", "save")) {
            rule.onNodeWithTag(TvTestTags.playerControl(control)).requestFocus()
            rule.pressDPadKey(Key.DirectionDown)

            val landed = focusedTag()
            assertTrue(
                "down from $control must land on a suggestion card, landed on $landed",
                landed.startsWith("tv_card_")
            )
        }
    }

    /**
     * The first card is the row's start edge: a right press there has nowhere to go and
     * must be rejected, leaving the remote on the card instead of pushing it off the
     * panel.
     */
    @Test
    fun theRightKeyAtTheFirstCardLeavesTheRemoteOnIt() {
        showPanelWithSuggestions()

        rule.onNodeWithTag(TvTestTags.card("s1")).requestFocus()
        assertEquals(TvTestTags.card("s1"), focusedTag())

        rule.pressDPadKey(Key.DirectionRight)

        assertEquals("right must be rejected at the row's start", TvTestTags.card("s1"), focusedTag())
    }
}
