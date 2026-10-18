package com.waha.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.waha.domain.ContinueWatching
import com.waha.domain.WatchHistoryItem
import com.waha.domain.filterContinueWatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.watchHistoryDataStore by preferencesDataStore(name = "watch_history")
private val WATCH_HISTORY_KEY = stringPreferencesKey("watch_history_ids")
private val WATCH_TIMES_KEY = stringPreferencesKey("watch_history_times")
private val WATCH_POSITIONS_KEY = stringPreferencesKey("watch_history_positions")
private val WATCH_DURATIONS_KEY = stringPreferencesKey("watch_history_durations")

/**
 * Remembers which videos the user has opened, most recent first, so the
 * player's suggestions can skip them instead of circling back (A suggests B,
 * B suggests A, endlessly) — and so the History screen can list them.
 *
 * Each entry also carries how far the viewer got and how long the video is,
 * which is what "أكمل المشاهدة" (Continue Watching) is built from.
 */
object WatchHistoryStore {
    const val MAX_HISTORY = 30

    val recentIds = mutableStateListOf<String>()

    /** Video id -> epoch millis of the last watch, for "watched x ago" labels. */
    val watchedAtMs = mutableStateOf<Map<String, Long>>(emptyMap())

    /** Video id -> where playback stopped, in millis. */
    val lastPositionMs = mutableStateOf<Map<String, Long>>(emptyMap())

    /** Video id -> the video's total length in millis. */
    val durationMs = mutableStateOf<Map<String, Long>>(emptyMap())

    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        scope.launch {
            val prefs = appContext!!.watchHistoryDataStore.data.first()
            val ids = WatchHistoryCodec.decodeIds(prefs[WATCH_HISTORY_KEY])
            val times = WatchHistoryCodec.decodeMillis(prefs[WATCH_TIMES_KEY])
            val positions = WatchHistoryCodec.decodeMillis(prefs[WATCH_POSITIONS_KEY])
            val durations = WatchHistoryCodec.decodeMillis(prefs[WATCH_DURATIONS_KEY])
            withContext(Dispatchers.Main) {
                recentIds.clear()
                recentIds.addAll(ids)
                watchedAtMs.value = times
                lastPositionMs.value = positions
                durationMs.value = durations
            }
        }
    }

    /** Marks [videoId] as just watched: moves it to the front and caps the list. */
    fun record(videoId: String) {
        if (videoId.isBlank()) return
        recentIds.remove(videoId)
        recentIds.add(0, videoId)
        while (recentIds.size > MAX_HISTORY) {
            val removed = recentIds.removeAt(recentIds.lastIndex)
            if (removed != videoId) {
                watchedAtMs.value = watchedAtMs.value - removed
                lastPositionMs.value = lastPositionMs.value - removed
                durationMs.value = durationMs.value - removed
            }
        }
        watchedAtMs.value = watchedAtMs.value + (videoId to System.currentTimeMillis())
        persist()
    }

    /**
     * Remembers where playback stopped — call this when the viewer pauses or
     * closes the player, so the next open (and the Continue Watching rail) can
     * pick the video up from the same spot.
     */
    fun updateProgress(videoId: String, positionMs: Long, totalDurationMs: Long) {
        if (videoId.isBlank()) return
        val position = positionMs.coerceAtLeast(0L)
        val length = totalDurationMs.coerceAtLeast(0L)
        lastPositionMs.value = lastPositionMs.value + (videoId to position)
        durationMs.value = durationMs.value + (videoId to length)
        record(videoId)
    }

    /** Where [videoId] stopped, or 0 when it was never played. */
    fun lastPositionOf(videoId: String): Long = lastPositionMs.value[videoId] ?: 0L

    /** How much of [videoId] is watched (0f..1f), or 0f when the length is unknown. */
    fun progressFractionOf(videoId: String): Float = WatchHistoryItem(
        videoId = videoId,
        watchedAtMs = 0L,
        lastPositionMs = lastPositionOf(videoId),
        durationMs = durationMs.value[videoId] ?: 0L
    ).progressFraction

    /** Where playback of [videoId] should resume; 0 when fresh, finished or unknown. */
    fun resumePositionMs(videoId: String): Long = ContinueWatching.resumePositionMs(
        positionMs = lastPositionOf(videoId),
        durationMs = durationMs.value[videoId] ?: 0L
    )

    /** The history entries in history order (newest first). */
    fun items(): List<WatchHistoryItem> = recentIds.map { id ->
        WatchHistoryItem(
            videoId = id,
            watchedAtMs = watchedAtMs.value[id] ?: 0L,
            lastPositionMs = lastPositionMs.value[id] ?: 0L,
            durationMs = durationMs.value[id] ?: 0L
        )
    }

    /** The partly-watched videos, newest first — the "أكمل المشاهدة" rail. */
    fun continueWatching(): List<WatchHistoryItem> = items().filterContinueWatching()

    /** Forgets one video (History screen swipe/delete). */
    fun remove(videoId: String) {
        recentIds.remove(videoId)
        watchedAtMs.value = watchedAtMs.value - videoId
        lastPositionMs.value = lastPositionMs.value - videoId
        durationMs.value = durationMs.value - videoId
        persist()
    }

    fun clear() {
        recentIds.clear()
        watchedAtMs.value = emptyMap()
        lastPositionMs.value = emptyMap()
        durationMs.value = emptyMap()
        persist()
    }

    private fun persist() {
        val context = appContext ?: return
        val ids = WatchHistoryCodec.encodeIds(recentIds)
        val times = WatchHistoryCodec.encodeMillis(watchedAtMs.value)
        val positions = WatchHistoryCodec.encodeMillis(lastPositionMs.value)
        val durations = WatchHistoryCodec.encodeMillis(durationMs.value)
        scope.launch {
            context.watchHistoryDataStore.edit {
                it[WATCH_HISTORY_KEY] = ids
                it[WATCH_TIMES_KEY] = times
                it[WATCH_POSITIONS_KEY] = positions
                it[WATCH_DURATIONS_KEY] = durations
            }
        }
    }
}
