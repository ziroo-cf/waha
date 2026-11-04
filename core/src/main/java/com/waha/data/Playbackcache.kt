package com.waha.data

/**
 * A tiny LRU with a time-to-live per entry, usable from any thread.
 *
 * It exists because resolving a stream URL is the slowest step between tapping a
 * video and the first frame: extraction and the Piped instances are both network
 * bound. A resolved URL stays playable for hours, so reopening the same video (or
 * switching back to a quality already resolved) should not pay for it again.
 *
 * [clock] is injectable so expiry can be tested without waiting.
 */
internal class TtlCache<K : Any, V : Any>(
    private val maxEntries: Int,
    private val ttlMs: Long,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private class Entry<V>(val value: V, val storedAtMs: Long)

    // accessOrder = true makes the eldest entry the least recently used one.
    private val entries = object : LinkedHashMap<K, Entry<V>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>?): Boolean =
            size > maxEntries
    }

    /** The live value for [key], or null when missing or expired. */
    fun get(key: K): V? = synchronized(entries) {
        val entry = entries[key] ?: return null
        if (clock() - entry.storedAtMs >= ttlMs) {
            entries.remove(key)
            return null
        }
        entry.value
    }

    fun put(key: K, value: V) {
        synchronized(entries) { entries[key] = Entry(value, clock()) }
    }

    fun clear() {
        synchronized(entries) { entries.clear() }
    }

    /** Entries that can still be served; the rest are swept as they are read. */
    fun size(): Int = synchronized(entries) { entries.size }
}

/** One resolved stream identity: the video and the quality cap it was resolved for. */
data class PlaybackCacheKey(val youtubeId: String, val maxHeight: Int?)

/**
 * Process-wide cache of resolved playback URLs.
 *
 * Forty-five minutes is well inside the lifetime of both a Piped URL and an
 * extracted one, and long enough that the common case — open a video, leave the
 * player, come back, or switch quality and switch back — is free.
 */
object PlaybackCache {

    internal const val TTL_MS = 45L * 60L * 1_000L
    internal const val MAX_ENTRIES = 32

    private val cache = TtlCache<PlaybackCacheKey, PipedPlaybackSource>(MAX_ENTRIES, TTL_MS)

    fun get(youtubeId: String, maxHeight: Int?): PipedPlaybackSource? =
        cache.get(PlaybackCacheKey(youtubeId, maxHeight))

    fun put(youtubeId: String, maxHeight: Int?, source: PipedPlaybackSource) {
        cache.put(PlaybackCacheKey(youtubeId, maxHeight), source)
    }

    /** Drops every cached URL (tests, and any future "clear data" action). */
    internal fun clear() = cache.clear()
}
