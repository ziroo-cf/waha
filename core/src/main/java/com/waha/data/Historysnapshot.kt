package com.waha.data

import com.waha.domain.WatchHistoryItem

/**
 * The watch history as one immutable value.
 *
 * It used to be four mutable maps plus a `mutableStateListOf`: every save copied
 * two or three maps, touched the list (invalidating every composition that read
 * it) and re-ordered the history even when only the playback position changed.
 * One value makes a change atomic, cheap to observe, and — because the reducers
 * below are pure — testable without a device.
 */
data class HistorySnapshot(
    val recentIds: List<String> = emptyList(),
    val watchedAtMs: Map<String, Long> = emptyMap(),
    val lastPositionMs: Map<String, Long> = emptyMap(),
    val durationMs: Map<String, Long> = emptyMap()
) {
    /**
     * Membership set for the suggestion engine and the saved badges. A plain
     * `Set` instead of a `contains()` scan over the ordered list.
     */
    val watchedIds: Set<String> = recentIds.toSet()

    fun item(videoId: String): WatchHistoryItem = WatchHistoryItem(
        videoId = videoId,
        watchedAtMs = watchedAtMs[videoId] ?: 0L,
        lastPositionMs = lastPositionMs[videoId] ?: 0L,
        durationMs = durationMs[videoId] ?: 0L
    )
}

/**
 * Pure state transitions for the history.
 *
 * `updateProgress` deliberately never touches `recentIds` order or `watchedAtMs`:
 * saving a position is not "watching something new", and re-ordering on every
 * pause is what used to invalidate the player's `remember` on the suggestions.
 * [WatchHistoryReducer.updateProgress] returns the very same instance when
 * nothing changed, so callers can skip both recomposition and disk writes.
 */
internal object WatchHistoryReducer {

    const val MAX_HISTORY = 30

    /** Puts [id] at the front, capping the list and dropping the evicted entries. */
    fun record(
        state: HistorySnapshot,
        videoId: String,
        nowMs: Long,
        maxHistory: Int = MAX_HISTORY
    ): HistorySnapshot {
        if (videoId.isBlank()) return state
        val (ids, evicted) = withFront(state.recentIds, videoId, maxHistory)
        return state.copy(
            recentIds = ids,
            watchedAtMs = (state.watchedAtMs - evicted) + (videoId to nowMs),
            lastPositionMs = state.lastPositionMs - evicted,
            durationMs = state.durationMs - evicted
        )
    }

    /**
     * Remembers where playback stopped.
     *
     * [minDeltaMs] suppresses writes for movement smaller than that (the viewer
     * paused, resumed and paused again a second later) while the in-memory position
     * still moves forward on the next save. A zero duration never overwrites a
     * known length, which is what makes the Continue Watching fraction stable.
     */
    fun updateProgress(
        state: HistorySnapshot,
        videoId: String,
        positionMs: Long,
        totalDurationMs: Long,
        minDeltaMs: Long = 0L,
        maxHistory: Int = MAX_HISTORY
    ): HistorySnapshot {
        if (videoId.isBlank()) return state
        val position = positionMs.coerceAtLeast(0L)
        val reportedDuration = totalDurationMs.coerceAtLeast(0L)
        val previousPosition = state.lastPositionMs[videoId] ?: 0L
        val previousDuration = state.durationMs[videoId] ?: 0L
        val length = if (reportedDuration > 0L) reportedDuration else previousDuration
        val known = videoId in state.watchedIds

        if (known && length == previousDuration &&
            kotlin.math.abs(position - previousPosition) < minDeltaMs
        ) {
            return state
        }

        val (ids, evicted) = if (known) {
            state.recentIds to emptySet<String>()
        } else {
            withFront(state.recentIds, videoId, maxHistory)
        }

        return state.copy(
            recentIds = ids,
            lastPositionMs = (state.lastPositionMs - evicted) + (videoId to position),
            durationMs = (state.durationMs - evicted) + (videoId to length)
        )
    }

    fun remove(state: HistorySnapshot, videoId: String): HistorySnapshot {
        if (videoId !in state.watchedIds) return state
        return state.copy(
            recentIds = state.recentIds.filter { it != videoId },
            watchedAtMs = state.watchedAtMs - videoId,
            lastPositionMs = state.lastPositionMs - videoId,
            durationMs = state.durationMs - videoId
        )
    }

    fun clear(): HistorySnapshot = HistorySnapshot()

    /** Front insert with the oldest entries returned as evicted ids. */
    private fun withFront(
        ids: List<String>,
        videoId: String,
        maxHistory: Int
    ): Pair<List<String>, Set<String>> {
        val remaining = ids.filter { it != videoId }
        val kept = remaining.take((maxHistory - 1).coerceAtLeast(0))
        val evicted = remaining.drop(kept.size).toSet()
        return (listOf(videoId) + kept) to evicted
    }
}
