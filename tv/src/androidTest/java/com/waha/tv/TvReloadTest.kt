package com.waha.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.waha.domain.VideoItem
import com.waha.tv.ui.screens.RELOAD_LABEL
import com.waha.tv.ui.screens.TvHomeScreen
import com.waha.tv.ui.screens.TvUiState
import com.waha.tv.ui.theme.WahaTvTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The reload affordance, driven by a remote like everything else on this screen:
 * one press of the centre key must refetch the library, whether the viewer has
 * walked to the bottom of a working shelf or is looking at a failed load.
 */
@RunWith(AndroidJUnit4::class)
class TvReloadTest {

    @get:Rule
    val rule = createComposeRule()

    private var reloads = 0

    private fun video(id: String, category: String) = VideoItem(
        id = id,
        youtubeId = id,
        title = "فيديو $id",
        meta = category,
        categoryKey = category
    )

    private val videos = listOf(video("v1", "أطفال"), video("v2", "طبخ"))

    private fun showShelf() {
        rule.setContent {
            WahaTvTheme {
                TvHomeScreen(
                    uiState = TvUiState.Ready(videos),
                    onVideoClick = {},
                    onRetry = { reloads++ }
                )
            }
        }
    }

    @Test
    fun theShelfEndsWithAReloadButtonThatTheCentreKeyPresses() {
        showShelf()

        // The button is the last item of the shelf's own list, so reaching it is a
        // scroll past every rail — not a control floating over the cards.
        rule.onNodeWithTag(TvTestTags.HomeList)
            .performScrollToNode(hasTestTag(TvTestTags.ReloadButton))

        rule.onNodeWithTag(TvTestTags.ReloadButton)
            .assertIsDisplayed()
            .requestFocus()

        rule.pressDPadKey(Key.DirectionCenter)

        assertEquals(1, reloads)
    }

    @Test
    fun theButtonIsBelowTheCardsAndTheRemoteWalksBackUpIntoThem() {
        showShelf()

        rule.onNodeWithTag(TvTestTags.HomeList)
            .performScrollToNode(hasTestTag(TvTestTags.ReloadButton))
        rule.onNodeWithTag(TvTestTags.ReloadButton).requestFocus()

        // Directly above the button is a card, so it really is the last thing under
        // the shelf rather than a control parked somewhere over the rows.
        rule.pressDPadKey(Key.DirectionUp)

        val tag = rule.onNode(isFocused(), useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.TestTag]
        assertTrue("expected a card above the button, got $tag", tag.startsWith("tv_card_"))
    }

    @Test
    fun aFailedLoadOffersTheSameReloadButton() {
        rule.setContent {
            WahaTvTheme {
                TvHomeScreen(
                    uiState = TvUiState.Error("لا يوجد اتصال"),
                    onVideoClick = {},
                    onRetry = { reloads++ }
                )
            }
        }

        rule.onNodeWithText(RELOAD_LABEL).assertIsDisplayed()
        rule.onNodeWithTag(TvTestTags.ReloadButton).assertIsDisplayed().requestFocus()

        rule.pressDPadKey(Key.DirectionCenter)
        assertEquals(1, reloads)
    }
}
