package com.waha.tv.ui.screens

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
import com.waha.data.PlayerPreferencesStore
import com.waha.data.RecentSearchesStore
import com.waha.data.SavedVideosStore
import com.waha.data.WatchHistoryStore
import com.waha.domain.VideoItem
import com.waha.tv.ui.theme.WahaTvColors

/**
 * TV app root.
 *
 * The phone build navigates with a bottom bar plus overlays; a TV has no thumb
 * and no touch, so the same destinations live in a rail down the screen's right edge
 * (folded to icons until the remote is on it), "back" walks the remote out one level
 * at a time, and the player covers everything else.
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
        PlayerPreferencesStore.init(context)
    }

    val uiState by viewModel.uiState.collectAsState()
    val allVideos = (uiState as? TvUiState.Ready)?.videos.orEmpty()

    var destination by remember { mutableStateOf(TvDestination.Home) }
    var playingVideo by remember { mutableStateOf<VideoItem?>(null) }
    // Where the player should pick the video up: the saved position when the
    // viewer paused part-way (the Continue Watching rail's cards), else 0.
    var resumeFromMs by remember { mutableStateOf(0L) }
    // Held above the player so a quality choice survives jumping to another video
    // from the "شاهد المزيد" rail, and restored from the saved preference so it
    // survives restarting the app too.
    var quality by remember { mutableStateOf(qualityOptionFor(PlayerPreferencesStore.qualityHeight.value)) }

    fun openVideo(video: VideoItem) {
        resumeFromMs = WatchHistoryStore.resumePositionMs(video.id)
        playingVideo = video
    }

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
                    onVideoClick = ::openVideo,
                    onRetry = viewModel::loadVideos
                )

                TvDestination.Search -> TvSearchScreen(
                    allVideos = allVideos,
                    onVideoClick = ::openVideo
                )

                TvDestination.Library -> TvLibraryScreen(
                    allVideos = allVideos,
                    onVideoClick = ::openVideo
                )

                TvDestination.Settings -> TvSettingsScreen()
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
                    onQualityChange = {
                        quality = it
                        PlayerPreferencesStore.setQualityHeight(it.maxHeight)
                    },
                    onVideoSelect = ::openVideo,
                    onBack = { playingVideo = null },
                    startPositionMs = resumeFromMs
                )
            }
        }
    }
}
