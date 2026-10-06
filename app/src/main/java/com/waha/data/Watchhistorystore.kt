package com.waha.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.watchHistoryDataStore by preferencesDataStore(name = "watch_history")
private val WATCH_HISTORY_KEY = stringPreferencesKey("watch_history_ids")
private val WATCH_TIMES_KEY = stringPreferencesKey("watch_history_times")

/**
 * Remembers which videos the user has opened, most recent first, so the
 * player's suggestions can skip them instead of circling back (A suggests B,
 * B suggests A, endlessly) — and so the History screen can list them.
 */
object WatchHistoryStore {
    const val MAX_HISTORY = 30
    val recentIds = mutableStateListOf<String>()

    /** Video id -> epoch millis of the last watch, for "watched x ago" labels. */
    val watchedAtMs = mutableStateOf<Map<String, Long>>(emptyMap())

    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        scope.launch {
            val prefs = appContext!!.watchHistoryDataStore.data.first()
            val ids = prefs[WATCH_HISTORY_KEY]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
            val times = prefs[WATCH_TIMES_KEY]?.split('\n')
                ?.mapNotNull { entry ->
                    val id = entry.substringBefore('=')
                    val ms = entry.substringAfter('=', "").toLongOrNull()
                    if (id.isNotBlank() && ms != null) id to ms else null
                }?.toMap() ?: emptyMap()
            withContext(Dispatchers.Main) {
                recentIds.clear()
                recentIds.addAll(ids)
                watchedAtMs.value = times
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
            if (removed != videoId) watchedAtMs.value = watchedAtMs.value - removed
        }
        watchedAtMs.value = watchedAtMs.value + (videoId to System.currentTimeMillis())
        persist()
    }

    /** Forgets one video (History screen swipe/delete). */
    fun remove(videoId: String) {
        recentIds.remove(videoId)
        watchedAtMs.value = watchedAtMs.value - videoId
        persist()
    }

    fun clear() {
        recentIds.clear()
        watchedAtMs.value = emptyMap()
        persist()
    }

    private fun persist() {
        val context = appContext ?: return
        val ids = recentIds.joinToString("\n")
        val times = watchedAtMs.value.entries.joinToString("\n") { "${it.key}=${it.value}" }
        scope.launch {
            context.watchHistoryDataStore.edit {
                it[WATCH_HISTORY_KEY] = ids
                it[WATCH_TIMES_KEY] = times
            }
        }
    }
}
