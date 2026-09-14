package com.waha

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.waha.ui.screens.WahaApp
import com.waha.ui.theme.WahaTheme
import com.waha.data.ThemePreferenceStore
import androidx.activity.enableEdgeToEdge
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemePreferenceStore.init(this)
        setContent {
            WahaTheme {
                WahaApp()
            }
        }
    }

}