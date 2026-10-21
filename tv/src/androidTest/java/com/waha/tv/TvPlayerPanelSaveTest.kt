package com.waha.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.waha.data.SavedVideosStore
import com.waha.tv.ui.screens.PlayerPanel
import com.waha.tv.ui.screens.TV_QUALITY_OPTIONS
import com.waha.tv.ui.theme.WahaTvTheme
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The player panel's controls, driven with real D-pad presses: the save pill at the
 * far edge of the transport row, and the quality choices on the row below it.
 *
 * The panel is a destination of its own — while it is open the video gives up the
 * D-pad — so these tests also pin that walking to the panel's outer edge never
 * drops focus.
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
                        qualityOptions = TV_QUALITY_OPTIONS,
                        selectedQuality = TV_QUALITY_OPTIONS.first(),
                        onQualitySelect = {},
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
    fun theSavePillSitsBesideTheTransportButtons() {
        showPanel()

        val play = rule.onNodeWithTag(TvTestTags.playerControl("play_pause"))
            .fetchSemanticsNode().boundsInRoot
        val save = rule.onNodeWithText("حفظ").fetchSemanticsNode().boundsInRoot
        val quality = rule.onNodeWithText("تلقائي").fetchSemanticsNode().boundsInRoot

        assertTrue(
            "the save pill must share the transport row",
            save.top < play.bottom && play.top < save.bottom
        )
        // The layout is right-to-left: the transport buttons start at the right edge,
        // so the save pill (the row's far end) is further left.
        assertTrue("save must follow the transport buttons", save.center.x < play.center.x)
        assertTrue("the qualities belong on their own row below", quality.top >= play.bottom)
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
     * The rows are walked with the arrows: down leaves the transport row for the
     * qualities, left walks the qualities, and up comes back.
     */
    @Test
    fun arrowsWalkFromTheTransportRowToTheQualities() {
        showPanel()

        rule.onNodeWithTag(TvTestTags.playerControl("play_pause")).requestFocus()
        rule.pressDPadKey(Key.DirectionDown)
        assertTrue("down must land on a quality, was ${focusedTag()}", focusedTag().startsWith("tv_player_quality_"))

        val firstQuality = focusedTag()
        rule.pressDPadKey(Key.DirectionLeft)
        val secondQuality = focusedTag()
        assertTrue("left must walk the qualities, was $secondQuality", secondQuality.startsWith("tv_player_quality_"))
        assertTrue("left must land on another quality", secondQuality != firstQuality)

        rule.pressDPadKey(Key.DirectionUp)
        assertTrue("up must return to the transport row, was ${focusedTag()}", focusedTag().startsWith("tv_player_control_"))
    }

    /**
     * The outer edges are where the old panel dropped the focus ring off the screen.
     * Walking to either end must leave the remote on a panel control, never nowhere.
     */
    @Test
    fun arrowsNeverDropFocusOutOfThePanel() {
        showPanel()

        rule.onNodeWithTag(TvTestTags.playerQuality("تلقائي")).requestFocus()

        repeat(6) {
            rule.pressDPadKey(Key.DirectionRight)
            assertTrue("right dropped focus out of the panel: ${focusedTag()}", focusedTag().startsWith("tv_player_"))
        }
        repeat(6) {
            rule.pressDPadKey(Key.DirectionLeft)
            assertTrue("left dropped focus out of the panel: ${focusedTag()}", focusedTag().startsWith("tv_player_"))
        }
    }
}
