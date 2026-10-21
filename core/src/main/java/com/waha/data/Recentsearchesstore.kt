package com.waha.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.recentSearchesDataStore by preferencesDataStore(name = "recent_searches")
private val RECENT_QUERIES_KEY = stringPreferencesKey("recent_search_queries")

/**
 * The last few search queries, newest first.
 *
 * Held as one immutable list instead of a mutable snapshot list, so the search
 * screens observe a single value, and writes are coalesced: typing and
 * submitting several searches in a row touches the file once.
 */
object RecentSearchesStore {

    const val MAX_RECENT_SEARCHES = 8

    private const val PERSIST_DEBOUNCE_MS = 400L

    private val _recentQueries = mutableStateOf<List<String>>(emptyList())

    /** Compose-observable snapshot; reading it subscribes the composition. */
    val recentQueries: List<String> get() = _recentQueries.value

    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var persistJob: Job? = null

    fun init(context: Context) {
        if (appContext != null) return
        val application = context.applicationContext
        appContext = application
        scope.launch {
            val stored = application.recentSearchesDataStore.data.first()[RECENT_QUERIES_KEY]
            val queries = stored?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
            withContext(Dispatchers.Main) {
                if (_recentQueries.value.isEmpty()) {
                    _recentQueries.value = queries
                } else {
                    _recentQueries.value =
                        (_recentQueries.value + queries).distinct().take(MAX_RECENT_SEARCHES)
                }
            }
        }
    }

    fun add(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        _recentQueries.value = (listOf(trimmed) + _recentQueries.value.filter { it != trimmed })
            .take(MAX_RECENT_SEARCHES)
        schedulePersist()
    }

    fun remove(query: String) {
        if (query !in _recentQueries.value) return
        _recentQueries.value = _recentQueries.value.filter { it != query }
        schedulePersist()
    }

    fun clear() {
        if (_recentQueries.value.isEmpty()) return
        _recentQueries.value = emptyList()
        schedulePersist()
    }

    /** One write per burst of edits, performed on IO. */
    private fun schedulePersist() {
        val context = appContext ?: return
        val snapshot = _recentQueries.value
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            val payload = snapshot.joinToString("\n")
            context.recentSearchesDataStore.edit { it[RECENT_QUERIES_KEY] = payload }
        }
    }
}
