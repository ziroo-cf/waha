package com.waha.tv

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.waha.domain.VideoItem
import com.waha.tv.ui.screens.TvVideoRail
import com.waha.tv.ui.theme.WahaTvTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What one card actually measures and shows on a real TV surface.
 *
 * The layout promises a 16:9 artwork with the running time riding on it in the bottom-left
 * corner, and the title and channel permanently underneath it — the kind of promise that
 * only a measurement of the laid-out node can confirm.
 */
@RunWith(AndroidJUnit4::class)
class TvCardDetailTest {

    @get:Rule
    val rule = createComposeRule()

    private fun video(category: String? = "أطفال") = VideoItem(
        id = "v1",
        youtubeId = "v1",
        title = "حلقة الأصدقاء",
        meta = category.orEmpty(),
        categoryKey = category,
        durationText = "4:30"
    )

    // The rail's own heading is deliberately not the category, so "أطفال" below the
    // title is the only node with that text and can be measured on its own.
    private fun showRail(video: VideoItem = video(), extra: VideoItem? = null) {
        rule.setContent {
            WahaTvTheme {
                TvVideoRail(
                    railKey = "قسم",
                    title = "قسم",
                    videos = listOfNotNull(video, extra),
                    onVideoClick = {}
                )
            }
        }
    }

    // The card merges its children's semantics, so every measurement below has
    // to read the unmerged tree to see the artwork and the detail lines as the
    // separate boxes they really are.
    private fun bounds(tag: String) = rule
        .onNodeWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot

    private fun textBounds(text: String) = rule
        .onNodeWithText(text, useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot

    @Test
    fun artworkIs16By9() {
        showRail()

        val artwork = rule.onNodeWithTag(TvTestTags.artwork("v1"), useUnmergedTree = true)
            .fetchSemanticsNode().size

        assertEquals(16f / 9f, artwork.width.toFloat() / artwork.height.toFloat(), 0.02f)
    }

    @Test
    fun titleAndCategorySitUnderTheArtworkInsideTheCard() {
        showRail()

        val artwork = bounds(TvTestTags.artwork("v1"))
        val card = bounds(TvTestTags.card("v1"))
        val category = textBounds("أطفال")

        rule.onNodeWithText("حلقة الأصدقاء", useUnmergedTree = true).assertIsDisplayed()

        assertTrue("the category must be below the artwork", category.top >= artwork.bottom)
        assertTrue("the detail must stay inside the card", category.bottom <= card.bottom)
    }

    @Test
    fun theDurationRidesOnTheArtworkInItsBottomLeftCorner() {
        showRail()

        val artwork = bounds(TvTestTags.artwork("v1"))
        val badge = bounds(TvTestTags.durationBadge("v1"))

        assertTrue("the badge belongs on the artwork", badge.top >= artwork.top && badge.bottom <= artwork.bottom)
        assertTrue("the badge must stay inside the artwork", badge.left >= artwork.left && badge.right <= artwork.right)
        assertTrue("the badge belongs in the lower half", badge.center.y > artwork.center.y)
        assertTrue("the badge belongs in the left corner", badge.center.x < artwork.center.x)
    }

    @Test
    fun theDurationIsOnTheArtworkOnlyOnceAndNowhereElse() {
        showRail()

        val artwork = bounds(TvTestTags.artwork("v1"))

        // One card, one place for the running time: nothing repeats it underneath.
        rule.onAllNodesWithText("4:30", useUnmergedTree = true).assertCountEquals(1)
        assertTrue("the time must not be written under the artwork", textBounds("4:30").top < artwork.bottom)
    }

    @Test
    fun shortAndLongTitlesReserveTheSameTwoLines() {
        // Two lines are reserved on every card, even when the title needs one, so a
        // shelf of cards keeps one height instead of stepping up and down.
        val longTitle = "حلقة طويلة جداً بعنوان يمتد على أكثر من سطر واحد في بطاقة التلفاز"
        showRail(
            video = video(),
            extra = video().copy(id = "v2", title = longTitle, meta = "قصص", categoryKey = "قصص")
        )

        val short = textBounds("حلقة الأصدقاء").height
        val long = textBounds(longTitle).height

        assertEquals("a one-line title must reserve the same room as a two-line one", short, long, 2f)
    }

    @Test
    fun videoWithNoCategoryStillLabelsTheLine() {
        showRail(video = video(category = null))

        rule.onNodeWithText(TvCardMeta.NO_CATEGORY, useUnmergedTree = true).assertIsDisplayed()
    }
}
