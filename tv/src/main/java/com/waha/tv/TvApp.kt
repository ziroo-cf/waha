package com.waha.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.waha.data.RecentSearchesStore
import com.waha.data.SavedVideosStore
import com.waha.data.WatchHistoryStore
import com.waha.domain.VideoItem

/**
 * TV app root.
 *
 * The phone build navigates with a bottom bar plus overlays; a TV has no thumb
 * and no touch, so the same four destinations live in a sidebar, "back" walks
 * the remote out one level at a time, and the player covers everything else.
 *
 * The stores are the phone build's own ([SavedVideosStore], [RecentSearchesStore],
 * [WatchHistoryStore]), initialised here so saves, searches and history are one
 * shared library across both apps.
 */
@Composable
fun TvApp(viewModel: TvHomeViewModel = viewModel()) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        SavedVideosStore.init(context)
        RecentSearchesStore.init(context)
        WatchHistoryStore.init(context)
    }

    val uiState by viewModel.uiState.collectAsState()
    val allVideos = (uiState as? TvUiState.Ready)?.videos.orEmpty()

    var destination by remember { mutableStateOf(TvDestination.Home) }
    var playingVideo by remember { mutableStateOf<VideoItem?>(null) }
    // Held above the player so a quality choice survives jumping to another video
    // from the "شاهد المزيد" rail, the way a viewer expects a setting to stick.
    var quality by remember { mutableStateOf(TV_QUALITY_OPTIONS.first()) }

    // Back leaves the player first, then the current destination. The player
    // registers its own handler while its control panel is open, and Compose
    // gives the newest enabled handler the key, so the panel closes first.
    BackHandler(enabled = playingVideo != null) { playingVideo = null }
    BackHandler(enabled = playingVideo == null && destination != TvDestination.Home) {
        destination = TvDestination.Home
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WahaTvColors.Background)
    ) {
        TvMainScreen(
            destination = destination,
            onDestinationSelect = { destination = it }
        ) {
            when (destination) {
                TvDestination.Home -> TvHomeScreen(
                    uiState = uiState,
                    onVideoClick = { playingVideo = it },
                    onRetry = viewModel::loadVideos
                )

                TvDestination.Search -> TvSearchScreen(
                    allVideos = allVideos,
                    onVideoClick = { playingVideo = it }
                )

                TvDestination.Saved -> TvSavedScreen(
                    allVideos = allVideos,
                    onVideoClick = { playingVideo = it }
                )

                TvDestination.History -> TvHistoryScreen(
                    allVideos = allVideos,
                    onVideoClick = { playingVideo = it }
                )
            }
        }

        // Keyed by id so a suggestion swap rebuilds the player (and its ExoPlayer)
        // instead of mutating the one already playing.
        playingVideo?.let { video ->
            key(video.id) {
                TvPlayerScreen(
                    video = video,
                    allVideos = allVideos,
                    quality = quality,
                    onQualityChange = { quality = it },
                    onVideoSelect = { playingVideo = it },
                    onBack = { playingVideo = null }
                )
            }
        }
    }
}
