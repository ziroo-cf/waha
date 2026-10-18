package com.waha.tv

import com.waha.data.WatchHistoryCodec
import com.waha.domain.ContinueWatching
import com.waha.domain.PlayerQuality
import com.waha.tv.ui.screens.TV_QUALITY_OPTIONS
import com.waha.tv.ui.screens.qualityOptionFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the two playback preferences the TV build keeps between sessions:
 * where the viewer stopped, and the quality they chose.
 *
 * The stores themselves need Android (DataStore / SharedPreferences), so what
 * is exercised here is the persisted format plus the TV-side mapping on top of
 * it — the parts that would silently lose a value if they drifted.
 */
class TvPlaybackPreferencesTest {

    private val tenMinutes = 10 * 60_000L

    // --- Preferred quality -------------------------------------------------

    @Test
    fun `every quality option survives being saved and restored`() {
        TV_QUALITY_OPTIONS.forEach { option ->
            val stored = PlayerQuality.encode(option.maxHeight)
            val restored = PlayerQuality.decode(stored)

            assertEquals(option, qualityOptionFor(restored))
        }
    }

    @Test
    fun `auto is the default and the fallback`() {
        assertEquals(TV_QUALITY_OPTIONS.first(), qualityOptionFor(null))
        assertEquals(TV_QUALITY_OPTIONS.first(), qualityOptionFor(PlayerQuality.decode(null)))

        // A height the panel does not offer (an older build's choice) must not
        // leave the panel with no selected chip.
        assertEquals(TV_QUALITY_OPTIONS.first(), qualityOptionFor(480))
    }

    @Test
    fun `option labels come from the shared formatter`() {
        assertNull(TV_QUALITY_OPTIONS.first().maxHeight)
        TV_QUALITY_OPTIONS.forEach { option ->
            assertEquals(PlayerQuality.label(option.maxHeight), option.label)
        }
    }

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
