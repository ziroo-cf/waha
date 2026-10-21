package com.waha.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The history reducers are pure, so the rules that matter for the player —
 * progress never re-orders the list, tiny movements are ignored, a zero
 * duration never erases a known length — are verified without a device.
 */
class HistorySnapshotTest {

    private fun empty() = HistorySnapshot()

    @Test
    fun `record moves the video to the front and keeps the cap`() {
        var state = empty()
        for (i in 1..35) state = WatchHistoryReducer.record(state, "v$i", nowMs = i.toLong())

        assertEquals(WatchHistoryReducer.MAX_HISTORY, state.recentIds.size)
        assertEquals("v35", state.recentIds.first())
        assertEquals(35L, state.watchedAtMs["v35"])
        // The five oldest fell out of both the list and the maps.
        assertTrue("v1" !in state.watchedIds)
        assertTrue("v1" !in state.watchedAtMs)
    }

    @Test
    fun `record on an existing id does not duplicate it`() {
        var state = WatchHistoryReducer.record(empty(), "a", 1L)
        state = WatchHistoryReducer.record(state, "b", 2L)
        state = WatchHistoryReducer.record(state, "a", 3L)

        assertEquals(listOf("a", "b"), state.recentIds)
        assertEquals(3L, state.watchedAtMs["a"])
    }

    @Test
    fun `saving progress keeps the history order and last-watched time`() {
        var state = WatchHistoryReducer.record(empty(), "a", nowMs = 100L)
        state = WatchHistoryReducer.record(state, "b", nowMs = 200L)
        val before = state

        val after = WatchHistoryReducer.updateProgress(state, "a", positionMs = 60_000L, totalDurationMs = 300_000L)

        // "a" was watched earlier; saving its position must not promote it.
        assertEquals(listOf("b", "a"), after.recentIds)
        assertEquals(before.watchedAtMs, after.watchedAtMs)
        assertEquals(60_000L, after.lastPositionMs["a"])
        assertEquals(300_000L, after.durationMs["a"])
    }

    @Test
    fun `tiny movements are ignored`() {
        var state = WatchHistoryReducer.record(empty(), "a", nowMs = 1L)
        state = WatchHistoryReducer.updateProgress(state, "a", 30_000L, 300_000L)

        val unchanged = WatchHistoryReducer.updateProgress(
            state = state,
            videoId = "a",
            positionMs = 33_000L,
            totalDurationMs = 300_000L,
            minDeltaMs = WatchHistoryStore.MIN_PROGRESS_DELTA_MS
        )

        // Same instance: no recomposition and no disk write for a 3s drift.
        assertSame(state, unchanged)
    }

    @Test
    fun `a real jump is saved`() {
        var state = WatchHistoryReducer.record(empty(), "a", nowMs = 1L)
        state = WatchHistoryReducer.updateProgress(state, "a", 30_000L, 300_000L)

        val moved = WatchHistoryReducer.updateProgress(
            state = state,
            videoId = "a",
            positionMs = 45_000L,
            totalDurationMs = 300_000L,
            minDeltaMs = WatchHistoryStore.MIN_PROGRESS_DELTA_MS
        )

        assertEquals(45_000L, moved.lastPositionMs["a"])
    }

    @Test
    fun `a zero duration never erases a known length`() {
        var state = WatchHistoryReducer.record(empty(), "a", nowMs = 1L)
        state = WatchHistoryReducer.updateProgress(state, "a", 90_000L, 300_000L)

        // The player is not ready yet and reports 0; the fraction must survive.
        val saved = WatchHistoryReducer.updateProgress(state, "a", 120_000L, 0L)

        assertEquals(300_000L, saved.durationMs["a"])
        assertEquals(0.4f, saved.item("a").progressFraction, 0.0001f)
    }

    @Test
    fun `progress for an unseen video still lands in the history`() {
        val state = WatchHistoryReducer.updateProgress(empty(), "new", 10_000L, 100_000L)

        assertEquals(listOf("new"), state.recentIds)
        assertEquals(10_000L, state.lastPositionMs["new"])
        assertTrue("new" in state.watchedIds)
    }

    @Test
    fun `remove and clear drop the entry everywhere`() {
        var state = WatchHistoryReducer.record(empty(), "a", 1L)
        state = WatchHistoryReducer.record(state, "b", 2L)
        state = WatchHistoryReducer.updateProgress(state, "a", 5_000L, 50_000L)

        val removed = WatchHistoryReducer.remove(state, "a")
        assertEquals(listOf("b"), removed.recentIds)
        assertTrue("a" !in removed.watchedIds)
        assertTrue("a" !in removed.lastPositionMs)

        val cleared = WatchHistoryReducer.clear()
        assertTrue(cleared.recentIds.isEmpty())
        assertTrue(cleared.watchedIds.isEmpty())
    }

    @Test
    fun `the snapshot round trips through the codec`() {
        var state = WatchHistoryReducer.record(empty(), "a", 111L)
        state = WatchHistoryReducer.record(state, "b", 222L)
        state = WatchHistoryReducer.updateProgress(state, "a", 12_345L, 678_900L)

        val decoded = WatchHistoryCodec.decodeState(WatchHistoryCodec.encodeState(state))

        assertEquals(state, decoded)
    }

    @Test
    fun `the codec ignores entries for ids that are not in the history`() {
        val payload = """
            #ids
            a
            b
            #times
            a=1
            ghost=9
            #positions
            a=100
            #durations
            a=1000
        """.trimIndent()

        val decoded = WatchHistoryCodec.decodeState(payload)

        assertEquals(listOf("a", "b"), decoded.recentIds)
        assertTrue("ghost" !in decoded.watchedAtMs)
    }

    @Test
    fun `an empty or malformed payload decodes to an empty history`() {
        assertTrue(WatchHistoryCodec.decodeState(null).recentIds.isEmpty())
        assertTrue(WatchHistoryCodec.decodeState("").recentIds.isEmpty())
        assertTrue(WatchHistoryCodec.decodeState("#times\nbroken").recentIds.isEmpty())
    }
}
