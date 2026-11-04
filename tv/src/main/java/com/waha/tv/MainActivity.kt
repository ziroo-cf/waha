package com.waha.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.waha.tv.ui.screens.TvApp
import com.waha.tv.ui.screens.TvHomeViewModel
import com.waha.tv.ui.theme.WahaTvTheme

/**
 * Android TV entry point. Declared with a LEANBACK_LAUNCHER intent filter so it
 * appears on the TV home screen.
 *
 * The TV build is dark-only ([WahaTvTheme]) and does not read the phone's theme
 * preference.
 */
class MainActivity : ComponentActivity() {

    /**
     * Held by the activity, not created inside the composition, so [onStart] can
     * reshuffle the shelf the activity is already showing.
     */
    private val homeViewModel: TvHomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WahaTvTheme {
                TvApp(homeViewModel)
            }
        }
    }

    /**
     * Every time the app is opened — from the launcher, or back from another app while
     * the process was still alive — the shelf takes a new order from the cache it
     * already holds. No network walk: the viewer asked for a different order, not for a
     * sync, and the shelf must be different the moment it appears.
     */
    override fun onStart() {
        super.onStart()
        homeViewModel.reshuffle()
    }
}
