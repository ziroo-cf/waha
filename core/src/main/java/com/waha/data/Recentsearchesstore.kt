package com.waha.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.recentSearchesDataStore by preferencesDataStore(name = "recent_searches")
private val RECENT_QUERIES_KEY = stringPreferencesKey("recent_search_queries")

object RecentSearchesStore {
    const val MAX_RECENT_SEARCHES = 8
    val recentQueries = mutableStateListOf<String>()

    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        scope.launch {
            val stored = appContext!!.recentSearchesDataStore.data.first()[RECENT_QUERIES_KEY]
            val queries = stored?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
            withContext(Dispatchers.Main) {
                recentQueries.clear()
                recentQueries.addAll(queries)
            }
        }
    }

    fun add(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        recentQueries.remove(trimmed)
        recentQueries.add(0, trimmed)
        while (recentQueries.size > MAX_RECENT_SEARCHES) {
            recentQueries.removeAt(recentQueries.lastIndex)
        }
        persist()
    }

    fun remove(query: String) {
        recentQueries.remove(query)
        persist()
    }

    fun clear() {
        recentQueries.clear()
        persist()
    }

    private fun persist() {
        val context = appContext ?: return
        val snapshot = recentQueries.joinToString("\n")
        scope.launch {
            context.recentSearchesDataStore.edit { it[RECENT_QUERIES_KEY] = snapshot }
        }
    }
}
