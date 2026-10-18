package com.waha.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the pure "أكمل المشاهدة" rules: only videos paused part-way show up
 * (>5% and <90% watched), newest first, and playback resumes only when the
 * saved position is still worth resuming.
 */
class ContinueWatchingTest {

    private val tenMinutes = 10 * 60_000L

    private fun item(
        id: String,
        fraction: Double,
        watchedAtMs: Long = 1_000L,
        durationMs: Long = tenMinutes
    ) = WatchHistoryItem(
        videoId = id,
        watchedAtMs = watchedAtMs,
        lastPositionMs = (durationMs * fraction).toLong(),
        durationMs = durationMs
    )

    @Test
    fun `half-watched video is kept`() {
        val kept = listOf(item("a", 0.50)).filterContinueWatching()

        assertEquals(listOf("a"), kept.map { it.videoId })
        assertEquals(0.5f, kept.first().progressFraction)
    }

    @Test
    fun `barely started video is dropped`() {
        // 2% is below the 5% floor: the viewer only glanced at it.
        assertTrue(listOf(item("a", 0.02)).filterContinueWatching().isEmpty())
    }

    @Test
    fun `almost finished video is dropped`() {
        // 95% is past the 90% ceiling: it should not be offered again.
        assertTrue(listOf(item("a", 0.95)).filterContinueWatching().isEmpty())
    }

    @Test
    fun `nine in ten watched video is dropped`() {
        // The ceiling itself belongs to "finished", not to "continue".
        assertTrue(listOf(item("a", 0.90)).filterContinueWatching().isEmpty())
    }

    @Test
    fun `the floor itself belongs to the dropped side`() {
        assertTrue(listOf(item("a", 0.05)).filterContinueWatching().isEmpty())
    }

    @Test
    fun `unknown duration is never offered`() {
        // Without a length the fraction cannot be computed, so the entry is not
        // trustworthy enough to promise "10 دقائق متبقية" on a card.
        val unknown = WatchHistoryItem("a", 1_000L, lastPositionMs = 60_000L, durationMs = 0L)

        assertTrue(listOf(unknown).filterContinueWatching().isEmpty())
    }

    @Test
    fun `results are newest first`() {
        val mixed = listOf(
            item("old", 0.40, watchedAtMs = 100L),
            item("newest", 0.30, watchedAtMs = 900L),
            item("middle", 0.60, watchedAtMs = 500L)
        )

        assertEquals(
            listOf("newest", "middle", "old"),
            mixed.filterContinueWatching().map { it.videoId }
        )
    }

    @Test
    fun `both filters apply together`() {
        val mixed = listOf(
            item("kept", 0.50, watchedAtMs = 10L),
            item("tooEarly", 0.01, watchedAtMs = 20L),
            item("tooLate", 0.99, watchedAtMs = 30L)
        )

        assertEquals(listOf("kept"), mixed.filterContinueWatching().map { it.videoId })
    }

    @Test
    fun `the list is capped`() {
        val many = (1..ContinueWatching.MAX_ITEMS + 5).map { item("v$it", 0.5, watchedAtMs = it.toLong()) }

        assertEquals(ContinueWatching.MAX_ITEMS, many.filterContinueWatching().size)
    }

    @Test
    fun `resume keeps an in-progress position`() {
        assertEquals(300_000L, ContinueWatching.resumePositionMs(300_000L, tenMinutes))
    }

    @Test
    fun `resume restarts a finished video`() {
        assertEquals(0L, ContinueWatching.resumePositionMs(9_500_000L, tenMinutes))
    }

    @Test
    fun `resume is a no-op for a positionless or lengthless entry`() {
        assertEquals(0L, ContinueWatching.resumePositionMs(0L, tenMinutes))
        assertEquals(0L, ContinueWatching.resumePositionMs(60_000L, 0L))
        assertEquals(0L, ContinueWatching.resumePositionMs(-5L, tenMinutes))
    }

    @Test
    fun `progress fraction is clamped and zero without a duration`() {
        assertEquals(0f, WatchHistoryItem("a", 0L, 60_000L, 0L).progressFraction)
        // A bogus position past the end is clamped, not reported as 200%.
        assertEquals(1f, WatchHistoryItem("a", 0L, 2 * tenMinutes, tenMinutes).progressFraction)
    }

    @Test
    fun `entries join back onto the library in the rail's order`() {
        val library = listOf(
            VideoItem("old", "old", "قديم", ""),
            VideoItem("newest", "newest", "الأحدث", ""),
            VideoItem("tooLate", "tooLate", "مكتمل", "")
        )
        val entries = listOf(
            item("old", 0.40, watchedAtMs = 100L),
            item("newest", 0.30, watchedAtMs = 900L),
            item("tooLate", 0.99, watchedAtMs = 950L),
            item("missingFromLibrary", 0.50, watchedAtMs = 990L)
        )

        assertEquals(
            listOf("newest", "old"),
            library.continueWatching(entries).map { it.id }
        )
    }

    @Test
    fun `the join handles an empty library or empty history`() {
        assertTrue(emptyList<VideoItem>().continueWatching(listOf(item("a", 0.5))).isEmpty())
        assertTrue(
            listOf(VideoItem("a", "a", "عنوان", "")).continueWatching(emptyList()).isEmpty()
        )
    }

    @Test
    fun `isInProgress matches the filter`() {
        assertTrue(ContinueWatching.isInProgress(item("a", 0.5)))
        assertFalse(ContinueWatching.isInProgress(item("a", 0.95)))
        assertFalse(ContinueWatching.isInProgress(item("a", 0.02)))
    }
}
