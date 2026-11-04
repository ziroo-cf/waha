package com.waha.tv

import com.waha.data.WatchHistoryCodec
import com.waha.domain.ContinueWatching
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the playback preference the TV build keeps between sessions: where
 * the viewer stopped.
 *
 * The stores themselves need Android (DataStore / SharedPreferences), so what
 * is exercised here is the persisted format on top of them — the part that would
 * silently lose a position if it drifted.
 */
class TvPlaybackPreferencesTest {

    private val tenMinutes = 10 * 60_000L

    // --- Saved playback position -------------------------------------------

    @Test
    fun `a saved position round trips through the history format`() {
        val saved = mapOf("aaaaaaaaaaa" to (2 * 60_000L))

        val restored = WatchHistoryCodec.decodeMillis(WatchHistoryCodec.encodeMillis(saved))

        assertEquals(2 * 60_000L, restored["aaaaaaaaaaa"])
        // ...and it is still worth resuming from.
        assertEquals(
            2 * 60_000L,
            ContinueWatching.resumePositionMs(restored.getValue("aaaaaaaaaaa"), tenMinutes)
        )
    }

    @Test
    fun `a position past the end is not resumed`() {
        val saved = WatchHistoryCodec.decodeMillis(
            WatchHistoryCodec.encodeMillis(mapOf("aaaaaaaaaaa" to (9 * 60_000L + 30_000L)))
        )

        assertEquals(0L, ContinueWatching.resumePositionMs(saved.getValue("aaaaaaaaaaa"), tenMinutes))
    }

    @Test
    fun `positions survive alongside the other history entries`() {
        // Positions and durations are stored in separate keys; a round trip of
        // both must not lose an entry the Continue Watching rail needs.
        val positions = mapOf("a" to 60_000L, "b" to 300_000L)
        val durations = mapOf("a" to tenMinutes, "b" to tenMinutes)

        val restoredPositions = WatchHistoryCodec.decodeMillis(WatchHistoryCodec.encodeMillis(positions))
        val restoredDurations = WatchHistoryCodec.decodeMillis(WatchHistoryCodec.encodeMillis(durations))

        assertEquals(positions, restoredPositions)
        assertEquals(durations, restoredDurations)
        assertEquals(0.1f, restoredPositions.getValue("a").toFloat() / restoredDurations.getValue("a"))
    }

    @Test
    fun `an entry without a stored duration cannot be resumed`() {
        val positions = WatchHistoryCodec.decodeMillis(WatchHistoryCodec.encodeMillis(mapOf("a" to 90_000L)))
        val durations = WatchHistoryCodec.decodeMillis(WatchHistoryCodec.encodeMillis(emptyMap()))

        assertTrue(durations.isEmpty())
        assertEquals(0L, ContinueWatching.resumePositionMs(positions.getValue("a"), durations["a"] ?: 0L))
    }
}
