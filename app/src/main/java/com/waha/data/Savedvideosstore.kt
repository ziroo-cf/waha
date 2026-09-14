package com.waha.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.savedVideosDataStore by preferencesDataStore(name = "saved_videos")
private val SAVED_IDS_KEY = stringSetPreferencesKey("saved_video_ids")

object SavedVideosStore {
    val savedIds = mutableStateListOf<String>()

    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        scope.launch {
            val stored = appContext!!.savedVideosDataStore.data.first()[SAVED_IDS_KEY] ?: emptySet()
            withContext(Dispatchers.Main) {
                savedIds.clear()
                savedIds.addAll(stored)
            }
        }
    }

    fun toggle(videoId: String) {
        if (savedIds.contains(videoId)) savedIds.remove(videoId) else savedIds.add(videoId)
        persist()
    }

    fun isSaved(videoId: String): Boolean = savedIds.contains(videoId)

    private fun persist() {
        val context = appContext ?: return
        val snapshot = savedIds.toSet()
        scope.launch {
            context.savedVideosDataStore.edit { it[SAVED_IDS_KEY] = snapshot }
        }
    }
}