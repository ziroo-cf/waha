package com.waha.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.waha.domain.ContinueWatching
import com.waha.domain.WatchHistoryItem
import com.waha.domain.filterContinueWatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.watchHistoryDataStore by preferencesDataStore(name = "watch_history")

/** The whole history travels as one payload; see [WatchHistoryCodec.encodeState]. */
private val WATCH_HISTORY_KEY = stringPreferencesKey("watch_history_state")

// The four keys the store used before the snapshot format; read once to migrate.
private val LEGACY_IDS_KEY = stringPreferencesKey("watch_history_ids")
private val LEGACY_TIMES_KEY = stringPreferencesKey("watch_history_times")
private val LEGACY_POSITIONS_KEY = stringPreferencesKey("watch_history_positions")
private val LEGACY_DURATIONS_KEY = stringPreferencesKey("watch_history_durations")

/**
 * Remembers which videos the user has opened, most recent first, so the
 * player's suggestions can skip them instead of circling back (A suggests B,
 * B suggests A, endlessly) — and so the History screen can list them.
 *
 * Each entry also carries how far the viewer got and how long the video is,
 * which is what "أكمل المشاهدة" (Continue Watching) is built from.
 *
 * The store holds one immutable [HistorySnapshot] instead of four snapshot maps
 * and a mutable list: a change is atomic, ordered ids are cheap to compare, and
 * `watchedIds` gives the suggestion engine and the cards O(1) membership.
 * Writes are encoded on the IO dispatcher and coalesced, so a pause/resume
 * churn does not rewrite the preference file on every event.
 */
object WatchHistoryStore {

    const val MAX_HISTORY = WatchHistoryReducer.MAX_HISTORY

    /**
     * A position that moved less than this since the last save is not worth a
     * write; the viewer pausing and resuming twice in a second would otherwise
     * rewrite the whole history each time.
     */
    const val MIN_PROGRESS_DELTA_MS = 5_000L

    /** How long a save waits for a follow-up save before it touches the disk. */
    private const val PERSIST_DEBOUNCE_MS = 800L

    private val _snapshot = mutableStateOf(HistorySnapshot())

    /** Compose-observable history; reading it in composition is what subscribes. */
    val snapshot: HistorySnapshot get() = _snapshot.value

    /** Ordered ids, newest first. */
    val recentIds: List<String> get() = _snapshot.value.recentIds

    /** O(1) membership for suggestion filtering and badge checks. */
    val watchedIds: Set<String> get() = _snapshot.value.watchedIds

    val watchedAtMs: Map<String, Long> get() = _snapshot.value.watchedAtMs

    val lastPositionMs: Map<String, Long> get() = _snapshot.value.lastPositionMs

    val durationMs: Map<String, Long> get() = _snapshot.value.durationMs

    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var persistJob: Job? = null

    fun init(context: Context) {
        if (appContext != null) return
        val application = context.applicationContext
        appContext = application
        scope.launch {
            val prefs = application.watchHistoryDataStore.data.first()
            val stored = prefs[WATCH_HISTORY_KEY]
            val loaded = if (stored != null) {
                WatchHistoryCodec.decodeState(stored)
            } else {
                val legacy = HistorySnapshot(
                    recentIds = WatchHistoryCodec.decodeIds(prefs[LEGACY_IDS_KEY]),
                    watchedAtMs = WatchHistoryCodec.decodeMillis(prefs[LEGACY_TIMES_KEY]),
                    lastPositionMs = WatchHistoryCodec.decodeMillis(prefs[LEGACY_POSITIONS_KEY]),
                    durationMs = WatchHistoryCodec.decodeMillis(prefs[LEGACY_DURATIONS_KEY])
                )
                if (legacy.recentIds.isNotEmpty()) {
                    val payload = WatchHistoryCodec.encodeState(legacy)
                    application.watchHistoryDataStore.edit { it[WATCH_HISTORY_KEY] = payload }
                }
                legacy
            }
            withContext(Dispatchers.Main) {
                // A player may already have recorded something while the file was
                // being read; the live ordering wins, stored entries fill the gaps.
                _snapshot.value = if (_snapshot.value.recentIds.isEmpty()) {
                    loaded
                } else {
                    mergeStoredUnderLive(_snapshot.value, loaded)
                }
            }
        }
    }

    /** Marks [videoId] as just watched: moves it to the front and caps the list. */
    fun record(videoId: String) {
        if (videoId.isBlank()) return
        val before = _snapshot.value
        val after = WatchHistoryReducer.record(before, videoId, System.currentTimeMillis())
        if (after === before) return
        _snapshot.value = after
        schedulePersist()
    }

    /**
     * Remembers where playback stopped — call this when the viewer pauses or
     * closes the player, so the next open (and the Continue Watching rail) can
     * pick the video up from the same spot.
     *
     * This never re-orders the history and never refreshes the "last watched"
     * time: watching further is not opening something new.
     */
    fun updateProgress(videoId: String, positionMs: Long, totalDurationMs: Long) {
        if (videoId.isBlank()) return
        val before = _snapshot.value
        val after = WatchHistoryReducer.updateProgress(
            state = before,
            videoId = videoId,
            positionMs = positionMs,
            totalDurationMs = totalDurationMs,
            minDeltaMs = MIN_PROGRESS_DELTA_MS
        )
        if (after === before) return
        _snapshot.value = after
        schedulePersist()
    }

    /** Where [videoId] stopped, or 0 when it was never played. */
    fun lastPositionOf(videoId: String): Long = lastPositionMs[videoId] ?: 0L

    /** How much of [videoId] is watched (0f..1f), or 0f when the length is unknown. */
    fun progressFractionOf(videoId: String): Float =
        _snapshot.value.item(videoId).progressFraction

    /** Where playback of [videoId] should resume; 0 when fresh, finished or unknown. */
    fun resumePositionMs(videoId: String): Long = ContinueWatching.resumePositionMs(
        positionMs = lastPositionOf(videoId),
        durationMs = _snapshot.value.durationMs[videoId] ?: 0L
    )

    /** The history entries in history order (newest first). */
    fun items(): List<WatchHistoryItem> = _snapshot.value.recentIds.map(_snapshot.value::item)

    /** The partly-watched videos, newest first — the "أكمل المشاهدة" rail. */
    fun continueWatching(): List<WatchHistoryItem> = items().filterContinueWatching()

    /** Forgets one video (History screen swipe/delete). */
    fun remove(videoId: String) {
        val before = _snapshot.value
        val after = WatchHistoryReducer.remove(before, videoId)
        if (after === before) return
        _snapshot.value = after
        schedulePersist()
    }

    fun clear() {
        if (_snapshot.value.recentIds.isEmpty()) return
        _snapshot.value = WatchHistoryReducer.clear()
        schedulePersist()
    }

    /**
     * Coalesces a burst of changes into one write. The encoding happens inside
     * the coroutine (IO), not on whichever thread called the store, and the
     * latest snapshot always wins.
     */
    private fun schedulePersist() {
        val context = appContext ?: return
        val payload = _snapshot.value
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            val encoded = WatchHistoryCodec.encodeState(payload)
            context.watchHistoryDataStore.edit { it[WATCH_HISTORY_KEY] = encoded }
        }
    }

    private fun mergeStoredUnderLive(live: HistorySnapshot, stored: HistorySnapshot): HistorySnapshot {
        val ids = (live.recentIds + stored.recentIds).distinct().take(MAX_HISTORY)
        return HistorySnapshot(
            recentIds = ids,
            watchedAtMs = stored.watchedAtMs + live.watchedAtMs,
            lastPositionMs = stored.lastPositionMs + live.lastPositionMs,
            durationMs = stored.durationMs + live.durationMs
        )
    }
}
