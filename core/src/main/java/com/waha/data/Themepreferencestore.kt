package com.waha.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

object ThemePreferenceStore {
    private const val PREFS_NAME = "waha_theme_prefs"
    private const val KEY_DARK_MODE = "dark_mode"

    val isDarkMode = mutableStateOf(true)

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val sharedPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sharedPrefs
        isDarkMode.value = sharedPrefs.getBoolean(KEY_DARK_MODE, true)
    }

    fun setDarkMode(value: Boolean) {
        isDarkMode.value = value
        prefs?.edit()?.putBoolean(KEY_DARK_MODE, value)?.apply()
    }
}