package com.waha

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import com.waha.data.ThemePreferenceStore
import com.waha.ui.screens.WahaApp
import com.waha.ui.theme.WahaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemePreferenceStore.init(this)
        applySystemBarStyle()
        setContent {
            WahaTheme {
                val darkMode = ThemePreferenceStore.isDarkMode.value
                // Keep the system bar icons readable whenever the theme changes.
                LaunchedEffect(darkMode) { applySystemBarStyle() }
                WahaApp()
            }
        }
    }

    /** Dark is the app default, so the system bars start light-on-dark. */
    private fun applySystemBarStyle() {
        val style = if (ThemePreferenceStore.isDarkMode.value) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
