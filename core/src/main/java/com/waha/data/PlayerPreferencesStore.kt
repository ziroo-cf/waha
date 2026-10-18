package com.waha.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import com.waha.domain.PlayerQuality

/**
 * Remembers the viewer's player choices between sessions.
 *
 * Both players used to hold the chosen quality in composition state, so it was
 * forgotten the moment the app restarted. The preference belongs to the viewer,
 * not to the screen, so it lives here in the shared layer.
 */
object PlayerPreferencesStore {

    private const val PREFS_NAME = "waha_player_prefs"
    private const val KEY_QUALITY_HEIGHT = "quality_height"

    /** Preferred quality cap; null means "auto" (best the extractor can find). */
    val qualityHeight = mutableStateOf<Int?>(null)

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val sharedPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sharedPrefs
        qualityHeight.value = PlayerQuality.decode(sharedPrefs.getString(KEY_QUALITY_HEIGHT, null))
    }

    fun setQualityHeight(height: Int?) {
        qualityHeight.value = height
        prefs?.edit()?.putString(KEY_QUALITY_HEIGHT, PlayerQuality.encode(height))?.apply()
    }
}
