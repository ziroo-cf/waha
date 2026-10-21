package com.waha.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.savedVideosDataStore by preferencesDataStore(name = "saved_videos")
private val SAVED_IDS_KEY = stringSetPreferencesKey("saved_video_ids")

/**
 * The viewer's saved videos.
 *
 * The ids are held as one immutable [Set] rather than a mutable snapshot list:
 * `contains()` is a hash lookup instead of a linear scan in every card's
 * composition, and one toggle replaces the value atomically. Saves are coalesced
 * so tapping the bookmark on several cards writes the file once.
 */
object SavedVideosStore {

    private const val PERSIST_DEBOUNCE_MS = 400L

    private val _savedIds = mutableStateOf<Set<String>>(emptySet())

    /** Compose-observable snapshot; reading it subscribes the composition. */
    val savedIds: Set<String> get() = _savedIds.value

    /** O(1) check for a card's bookmark badge. */
    fun isSaved(videoId: String): Boolean = _savedIds.value.contains(videoId)

    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var persistJob: Job? = null

    fun init(context: Context) {
        if (appContext != null) return
        val application = context.applicationContext
        appContext = application
        scope.launch {
            val stored = application.savedVideosDataStore.data.first()[SAVED_IDS_KEY].orEmpty()
            withContext(Dispatchers.Main) {
                // Keep anything saved while the file was being read.
                _savedIds.value = _savedIds.value + stored
            }
        }
    }

    fun toggle(videoId: String) {
        if (videoId.isBlank()) return
        val current = _savedIds.value
        _savedIds.value = if (videoId in current) current - videoId else current + videoId
        schedulePersist()
    }

    fun clear() {
        if (_savedIds.value.isEmpty()) return
        _savedIds.value = emptySet()
        schedulePersist()
    }

    /** One write per burst of toggles, performed on IO. */
    private fun schedulePersist() {
        val context = appContext ?: return
        val snapshot = _savedIds.value
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            context.savedVideosDataStore.edit { it[SAVED_IDS_KEY] = snapshot }
        }
    }
}
