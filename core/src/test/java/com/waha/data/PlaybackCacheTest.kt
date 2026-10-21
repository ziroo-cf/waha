package com.waha.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The resolved-URL cache is what makes reopening a video instant, so its two
 * rules — nothing expired is served, and the least recently used entry goes
 * first — are verified with a fake clock instead of a sleep.
 */
class PlaybackCacheTest {

    private var now = 1_000L

    private fun cache(capacity: Int = 2, ttlMs: Long = 1_000L) =
        TtlCache<String, String>(maxEntries = capacity, ttlMs = ttlMs, clock = { now })

    @Test
    fun `serves a value inside its ttl`() {
        val cache = cache()
        cache.put("a", "url-a")

        now += 999
        assertEquals("url-a", cache.get("a"))
    }

    @Test
    fun `expires a value once the ttl has passed`() {
        val cache = cache(ttlMs = 1_000L)
        cache.put("a", "url-a")

        now += 1_000
        assertNull("an expired URL must not be played", cache.get("a"))
        assertEquals(0, cache.size())
    }

    @Test
    fun `evicts the least recently used entry`() {
        val cache = cache(capacity = 2)
        cache.put("a", "url-a")
        cache.put("b", "url-b")

        // Reading "a" makes "b" the least recently used one.
        assertEquals("url-a", cache.get("a"))
        cache.put("c", "url-c")

        assertNull(cache.get("b"))
        assertNotNull(cache.get("a"))
        assertNotNull(cache.get("c"))
    }

    @Test
    fun `clear drops everything`() {
        val cache = cache(capacity = 4)
        cache.put("a", "url-a")
        cache.put("b", "url-b")

        cache.clear()

        assertEquals(0, cache.size())
        assertNull(cache.get("a"))
    }

    @Test
    fun `the playback cache keys on the quality cap`() {
        PlaybackCache.clear()
        PlaybackCache.put("abc", 720, PipedPlaybackSource.Progressive("https://cdn/720"))

        assertEquals(
            PipedPlaybackSource.Progressive("https://cdn/720"),
            PlaybackCache.get("abc", 720)
        )
        assertNull("each quality is resolved separately", PlaybackCache.get("abc", 1080))
        assertNull(PlaybackCache.get("other", 720))
    }
}
