package com.waha.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.waha.data.PlayerPreferencesStore
import com.waha.data.ThemePreferenceStore
import com.waha.tv.ui.screens.TvApp
import com.waha.tv.ui.theme.WahaTvTheme

/**
 * Android TV entry point. Declared with a LEANBACK_LAUNCHER intent filter so it
 * appears on the TV home screen.
 *
 * The theme comes from [ThemePreferenceStore], the same preference the phone
 * build's settings screen writes — dark remains the living-room default.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemePreferenceStore.init(this)
        // Loaded before the first composition: the player reads the saved quality
        // synchronously, so it must already be there.
        PlayerPreferencesStore.init(this)
        setContent {
            val darkMode = ThemePreferenceStore.isDarkMode.value
            WahaTvTheme(darkTheme = darkMode) {
                TvApp()
            }
        }
    }
}
