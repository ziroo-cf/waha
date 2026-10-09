package com.waha.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * Android TV entry point. Declared with a LEANBACK_LAUNCHER intent filter so it
 * appears on the TV home screen.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WahaTvTheme {
                TvApp()
            }
        }
    }
}
