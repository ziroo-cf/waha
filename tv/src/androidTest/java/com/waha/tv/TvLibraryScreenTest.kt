package com.waha.tv

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.waha.data.SavedVideosStore
import com.waha.data.WatchHistoryStore
import com.waha.domain.VideoItem
import com.waha.tv.ui.screens.HISTORY_RAIL_KEY
import com.waha.tv.ui.screens.SAVED_RAIL_KEY
import com.waha.tv.ui.screens.TvLibraryScreen
import com.waha.tv.ui.theme.WahaTvTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * المكتبة is one room now: المحفوظات sits over السجل, both drawn with the same
 * cards the home screen's rails use. The stores it reads are the phone build's
 * own, so these tests seed them directly.
 */
@RunWith(AndroidJUnit4::class)
class TvLibraryScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun video(id: String, title: String, category: String) = VideoItem(
        id = id,
        youtubeId = id,
        title = title,
        meta = category,
        categoryKey = category
    )

    private val videos = listOf(
        video("v1", "حلقة محفوظة", "أطفال"),
        video("v2", "حلقة مشاهد", "طبخ")
    )

    @Before
    fun seedEmptyStores() {
        SavedVideosStore.clear()
        WatchHistoryStore.clear()
    }

    @After
    fun clearStores() {
        SavedVideosStore.clear()
        WatchHistoryStore.clear()
    }

    private fun showLibrary() {
        rule.setContent {
            WahaTvTheme {
                TvLibraryScreen(allVideos = videos, onVideoClick = {})
            }
        }
    }

    private fun scrollToHistoryRow() {
        rule.onNodeWithTag(TvTestTags.LibraryList)
            .performScrollToNode(hasTestTag(TvTestTags.rail(HISTORY_RAIL_KEY)))
    }

    @Test
    fun savedAndHistoryAreTwoRowsOfCards() {
        SavedVideosStore.toggle("v1")
        WatchHistoryStore.record("v2")
        showLibrary()

        // Row 1: المحفوظات with the saved card.
        rule.onNodeWithText("المحفوظات").assertIsDisplayed()
        rule.onNodeWithTag(TvTestTags.rail(SAVED_RAIL_KEY)).assertIsDisplayed()
        rule.onNodeWithTag(TvTestTags.card("v1")).assertIsDisplayed()

        // Row 2: السجل with the watched card, below the saved one.
        scrollToHistoryRow()
        rule.onNodeWithText("السجل").assertIsDisplayed()
        rule.onNodeWithTag(TvTestTags.card("v2")).assertIsDisplayed()
    }

    @Test
    fun anEmptyRowExplainsItselfInsteadOfVanishing() {
        SavedVideosStore.toggle("v1")
        showLibrary()

        scrollToHistoryRow()

        rule.onNodeWithText("لم تشاهد أي فيديو بعد — شغّل أي فيديو وسيظهر هنا")
            .assertIsDisplayed()
    }

    @Test
    fun anEmptyLibrarySaysSoOnce() {
        showLibrary()

        rule.onNodeWithText("لم تحفظ أو تشاهد أي فيديو بعد — افتح أي فيديو واضغط زر الحفظ")
            .assertIsDisplayed()
        rule.onNodeWithTag(TvTestTags.rail(SAVED_RAIL_KEY)).assertDoesNotExist()
    }
}
