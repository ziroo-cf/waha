package com.waha.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The id recovery shared by both players: ids arrive either as a proper YouTube
 * id or embedded in a thumbnail URL, and the resolver must not hand a bogus
 * value to the extractor.
 */
class PlaybackResolverTest {

    private fun video(youtubeId: String, thumbnailUrl: String? = null) = VideoItem(
        id = "row-id",
        youtubeId = youtubeId,
        title = "Any video",
        meta = "",
        thumbnailUrl = thumbnailUrl
    )

    @Test
    fun `plain youtube id is used as is`() {
        assertEquals("dQw4w9WgXcQ", PlaybackResolver.extractYouTubeId(video("dQw4w9WgXcQ")))
    }

    @Test
    fun `id is recovered from a thumbnail url`() {
        val fromPath = video(
            youtubeId = "not-an-id",
            thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
        )
        assertEquals("dQw4w9WgXcQ", PlaybackResolver.extractYouTubeId(fromPath))

        val fromQuery = video(
            youtubeId = "",
            thumbnailUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10"
        )
        assertEquals("dQw4w9WgXcQ", PlaybackResolver.extractYouTubeId(fromQuery))
    }

    @Test
    fun `nothing to recover returns null`() {
        assertNull(PlaybackResolver.extractYouTubeId(video("short", thumbnailUrl = null)))
        assertNull(PlaybackResolver.extractYouTubeId(video("", thumbnailUrl = "https://example.com/a.jpg")))
    }
}
