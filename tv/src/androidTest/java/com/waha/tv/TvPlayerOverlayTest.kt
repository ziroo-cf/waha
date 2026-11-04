package com.waha.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.waha.tv.ui.screens.PlayerBottomOverlay
import com.waha.tv.ui.screens.PlayerCenterOverlay
import com.waha.tv.ui.screens.PlayerSeekOverlay
import com.waha.tv.ui.screens.PlayerTopOverlay
import com.waha.tv.ui.screens.TvPlayerFeedback
import com.waha.tv.ui.theme.WahaTvTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The player's overlays, measured on a real TV surface: the name over the video with
 * its category underneath at the top-right, the phone-shaped progress bar along the
 * bottom edge, and the play/pause-or-seek glyph dead centre.
 *
 * The overlays are rendered on their own — the player itself needs a resolved
 * YouTube stream, which has no place in a layout test.
 */
@RunWith(AndroidJUnit4::class)
class TvPlayerOverlayTest {

    @get:Rule
    val rule = createComposeRule()

    private fun showOverlays(
        title: String = "حلقة الأصدقاء",
        category: String = "أناشيد"
    ) {
        rule.setContent {
            WahaTvTheme {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    PlayerTopOverlay(
                        title = title,
                        category = category,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                    PlayerBottomOverlay(
                        positionMs = { 60_000L },
                        durationMs = { 120_000L },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }

    private fun rootWidth(): Float = rule.onRoot().fetchSemanticsNode().size.width.toFloat()

    @Test
    fun theNameSitsAboveItsCategoryInTheTopRightCorner() {
        showOverlays()

        val title = rule.onNodeWithText("حلقة الأصدقاء")
            .fetchSemanticsNode().boundsInRoot
        val category = rule.onNodeWithText("أناشيد")
            .fetchSemanticsNode().boundsInRoot

        assertTrue("the category must be under the name", category.top >= title.bottom)
        // The layout is right-to-left, so the top-right corner is where the
        // overlay's start edge is: both lines sit on the right half of the screen.
        assertTrue(
            "the name must be on the right half, not centred or left",
            title.center.x > rootWidth() / 2f
        )
        assertTrue(category.center.x > rootWidth() / 2f)
    }

    @Test
    fun theProgressBarRunsAlongTheBottomWithBothTimes() {
        showOverlays()

        val bar = rule.onNodeWithTag(TvTestTags.PlayerProgress)
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot

        assertTrue("the bar belongs in the lower half", bar.center.y > root.center.y)
        rule.onNodeWithText("1:00").assertIsDisplayed()
        rule.onNodeWithText("2:00").assertIsDisplayed()
    }

    @Test
    fun theCentreGlyphShowsPlayWhileHeld() {
        rule.setContent {
            WahaTvTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    PlayerCenterOverlay(
                        isPlaying = false,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }

        val glyph = rule.onNodeWithTag(TvTestTags.PlayerCenter)
            .assertIsDisplayed()
            .fetchSemanticsNode()
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot

        assertTrue(
            "the play glyph must sit in the middle of the screen",
            kotlin.math.abs(glyph.boundsInRoot.center.x - root.center.x) < root.width / 6f
        )
        assertTrue(
            kotlin.math.abs(glyph.boundsInRoot.center.y - root.center.y) < root.height / 6f
        )
        rule.onNodeWithContentDescription("تشغيل").assertIsDisplayed()
    }

    @Test
    fun aSeekPressShowsItsArrowAtTheSideItSeeksTowards() {
        rule.setContent {
            WahaTvTheme {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    PlayerCenterOverlay(
                        isPlaying = true,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    PlayerSeekOverlay(
                        feedback = TvPlayerFeedback.Forward,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val arrow = rule.onNodeWithTag(TvTestTags.playerSeek("forward"))
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot

        // Forward belongs at the right edge, clear of the pause glyph in the middle.
        assertTrue("the forward hint must sit at the right side", arrow.center.x > root.width * 0.65f)
        assertTrue(
            "the hint must stay on the vertical centre",
            kotlin.math.abs(arrow.center.y - root.center.y) < root.height / 6f
        )
        rule.onNodeWithContentDescription("تقديم ١٠ ثوانٍ").assertIsDisplayed()
        // The middle still says what playback is doing, not what the seek did.
        rule.onNodeWithTag(TvTestTags.PlayerCenter).assertIsDisplayed()
    }

    @Test
    fun aRewindPressSitsAtTheOppositeSide() {
        rule.setContent {
            WahaTvTheme {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    PlayerSeekOverlay(
                        feedback = TvPlayerFeedback.Rewind,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val arrow = rule.onNodeWithTag(TvTestTags.playerSeek("rewind"))
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot

        assertTrue("the rewind hint must sit at the left side", arrow.center.x < root.width * 0.35f)
        rule.onNodeWithContentDescription("إرجاع ١٠ ثوانٍ").assertIsDisplayed()
    }
}
