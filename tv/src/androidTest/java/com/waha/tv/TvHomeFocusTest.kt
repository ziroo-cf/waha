package com.waha.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.waha.domain.VideoItem
import com.waha.tv.ui.screens.TvHomeScreen
import com.waha.tv.ui.screens.TvRails
import com.waha.tv.ui.screens.TvUiState
import com.waha.tv.ui.theme.WahaTvTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Where the remote lands when the shelf appears.
 *
 * The page opens on the first card of "الكل" — not on the first card of whichever row
 * happens to sit on top (Continue Watching, whose content the viewer did not come here
 * to browse) and not on the rail. After a reload reshuffles the shelf, the remote moves
 * to the new first card, because the card it was on may no longer be there.
 *
 * The shelves are built here with the same [TvRails.buildStable] the state holder uses,
 * so the expected first card is the one the screen actually draws.
 */
@RunWith(AndroidJUnit4::class)
class TvHomeFocusTest {

    @get:Rule
    val rule = createComposeRule()

    private fun video(id: String, category: String) = VideoItem(
        id = id,
        youtubeId = id,
        title = "فيديو $id",
        meta = category,
        categoryKey = category
    )

    private val videos = (1..8).map { video("v$it", if (it % 2 == 0) "طبخ" else "أطفال") }

    private fun firstCardOfAll(order: Long): String =
        TvRails.buildStable(videos, order).first().videos.first().id

    private fun focusedTag(): String =
        rule.onNode(isFocused()).fetchSemanticsNode().config[SemanticsProperties.TestTag]

    @Test
    fun theShelfOpensOnTheFirstCardOfTheAllRow() {
        rule.setContent {
            WahaTvTheme {
                TvHomeScreen(
                    uiState = TvUiState.Ready(
                        videos = videos,
                        rails = TvRails.buildStable(videos, 5L),
                        order = 5L
                    ),
                    onVideoClick = {},
                    onRetry = {}
                )
            }
        }

        rule.waitForIdle()

        assertEquals(TvTestTags.card(firstCardOfAll(5L)), focusedTag())
    }

    @Test
    fun aReshufflePutsTheRemoteOnTheNewFirstCard() {
        // The app-open order and the post-reload one, picked so the two first cards
        // really differ: otherwise the test could pass with the focus never moving.
        val firstOrder = 5L
        val secondOrder = generateSequence(6L) { it + 1L }
            .first { firstCardOfAll(it) != firstCardOfAll(firstOrder) }

        var order by mutableStateOf(firstOrder)

        rule.setContent {
            WahaTvTheme {
                TvHomeScreen(
                    uiState = TvUiState.Ready(
                        videos = videos,
                        rails = TvRails.buildStable(videos, order),
                        order = order
                    ),
                    onVideoClick = {},
                    onRetry = {}
                )
            }
        }

        rule.waitForIdle()
        assertEquals(TvTestTags.card(firstCardOfAll(firstOrder)), focusedTag())

        order = secondOrder
        rule.waitForIdle()

        assertEquals(TvTestTags.card(firstCardOfAll(secondOrder)), focusedTag())
    }
}
