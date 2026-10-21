package com.waha.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.waha.data.RecentSearchesStore
import com.waha.domain.VideoItem
import com.waha.tv.ui.screens.TvSearchScreen
import com.waha.tv.ui.theme.WahaTvTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Search on a TV, driven by the remote: the results are one horizontal row of
 * cards beside the on-screen keyboard, and the keyboard stays whole while the
 * viewer walks into them — there is nothing to fold away now that the results no
 * longer fill the screen.
 */
@RunWith(AndroidJUnit4::class)
class TvSearchRowTest {

    @get:Rule
    val rule = createComposeRule()

    private fun video(id: String, title: String) = VideoItem(
        id = id,
        youtubeId = id,
        title = title,
        meta = "أطفال",
        categoryKey = "أطفال"
    )

    private val videos = listOf(
        video("v1", "واحة الأصدقاء"),
        video("v2", "واحة العلوم"),
        video("v3", "طبخ سريع")
    )

    @Before
    fun clearSearches() {
        RecentSearchesStore.clear()
    }

    @After
    fun clearSearchesAgain() {
        RecentSearchesStore.clear()
    }

    private fun showSearch() {
        rule.setContent {
            WahaTvTheme {
                TvSearchScreen(allVideos = videos, onVideoClick = {})
            }
        }
    }

    /** Types one letter by pressing its on-screen key with the remote. */
    private fun typeLetter(letter: String) {
        rule.onAllNodesWithText(letter).onFirst().requestFocus()
        rule.pressDPadKey(Key.DirectionCenter)
        rule.waitForIdle()
    }

    private fun bounds(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    @Test
    fun resultsAreOneRowOfCards() {
        showSearch()
        typeLetter("و")

        rule.onNodeWithTag(TvTestTags.SearchResults).assertIsDisplayed()
        rule.onNodeWithTag(TvTestTags.card("v1")).assertIsDisplayed()
        rule.onNodeWithTag(TvTestTags.card("v2")).assertIsDisplayed()

        assertEquals(
            "the second result must sit beside the first, not below it",
            bounds(TvTestTags.card("v1")).top,
            bounds(TvTestTags.card("v2")).top,
            1f
        )
        // The non-matching video is not in the row.
        rule.onNodeWithTag(TvTestTags.card("v3")).assertDoesNotExist()
    }

    @Test
    fun theKeyboardStaysWholeWhenTheRemoteEntersTheResults() {
        showSearch()
        typeLetter("و")

        rule.onNodeWithTag(TvTestTags.card("v1")).requestFocus()
        rule.waitForIdle()

        // A letter key below the action row is still on screen: the keyboard has
        // not folded to a single row.
        rule.onAllNodesWithText("ب").onFirst().assertIsDisplayed()
        rule.onAllNodesWithText("حذف").onFirst().assertIsDisplayed()
    }
}
