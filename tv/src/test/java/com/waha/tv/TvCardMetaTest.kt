package com.waha.tv

import com.waha.domain.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every TV card promises three facts: the title, the category and the time. The
 * title is the model's own, but the category can be missing from a row, and a
 * card that silently drops the line breaks the shelf — so the fallbacks are
 * asserted here.
 */
class TvCardMetaTest {

    private fun video(category: String? = "أطفال", duration: String? = null) = VideoItem(
        id = "v1",
        youtubeId = "v1",
        title = "حلقة الأصدقاء",
        meta = category.orEmpty(),
        categoryKey = category,
        durationText = duration
    )

    @Test
    fun `the card names the video's category, trimmed`() {
        assertEquals("أطفال", TvCardMeta.categoryLabel(video()))
        assertEquals("برامج دينية", TvCardMeta.categoryLabel(video(category = "  برامج دينية  ")))
    }

    @Test
    fun `a video with no category says so instead of naming the brand`() {
        assertEquals(TvCardMeta.NO_CATEGORY, TvCardMeta.categoryLabel(video(category = null)))
        assertEquals(TvCardMeta.NO_CATEGORY, TvCardMeta.categoryLabel(video(category = "   ")))
        assertEquals("بدون تصنيف", TvCardMeta.NO_CATEGORY)
    }

    @Test
    fun `a blank category falls back to the model's meta label`() {
        // Rows mapped from the library carry both; if only `meta` is set, it is
        // still the category and the card shows it rather than a placeholder.
        val item = VideoItem(
            id = "v1",
            youtubeId = "v1",
            title = "حلقة",
            meta = "قصص",
            categoryKey = null
        )

        assertEquals("قصص", TvCardMeta.categoryLabel(item))
    }

    @Test
    fun `the time is the recorded length, and absent when the library has none`() {
        assertEquals("4:30", TvCardMeta.timeLabel(video(duration = "4:30")))
        assertEquals("1:02:03", TvCardMeta.timeLabel(video(duration = " 1:02:03 ")))
        assertNull(TvCardMeta.timeLabel(video()))
        assertNull(TvCardMeta.timeLabel(video(duration = "   ")))
    }
}
