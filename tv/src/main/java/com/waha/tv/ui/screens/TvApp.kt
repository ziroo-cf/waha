package com.waha.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
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
    }

    val uiState by viewModel.uiState.collectAsState()
    val allVideos = (uiState as? TvUiState.Ready)?.videos.orEmpty()

    var destination by remember { mutableStateOf(TvDestination.Home) }
    var playingVideo by remember { mutableStateOf<VideoItem?>(null) }
    // Bumped every time the viewer opens the shelf, so the remote lands on it rather than
    // staying on the rail icon that opened it.
    var homeFocusTicks by remember { mutableIntStateOf(0) }

    // Which card the remote was last on, so closing the player puts it back there rather
    // than at the top of the rail.
    val cardFocusMemory = remember { TvCardFocusMemory() }
    // Bumped when the player closes; the shell answers it by restoring that focus.
    var focusRestoreTicks by remember { mutableIntStateOf(0) }

    // Each destination keeps its own saved state — above all, where its list was
    // scrolled to. Switching pages tears the screen down, so without this the viewer
    // returns to a searched shelf at the first row, every time.
    val screenStates = rememberSaveableStateHolder()
    // Where the player should pick the video up: the saved position when the
    // viewer paused part-way (the Continue Watching rail's cards), else 0.
    var resumeFromMs by remember { mutableStateOf(0L) }

    fun openVideo(video: VideoItem) {
        resumeFromMs = WatchHistoryStore.resumePositionMs(video.id)
        playingVideo = video
    }

    fun closePlayer() {
        playingVideo = null
        focusRestoreTicks++
    }

    // Back leaves the player first, then the current destination. The player
    // registers its own handler while its control panel is open, and Compose
    // gives the newest enabled handler the key, so the panel closes first.
    BackHandler(enabled = playingVideo != null) { closePlayer() }
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
            onDestinationSelect = { selected ->
                if (selected == TvDestination.Home) homeFocusTicks++
                destination = selected
            },
            focusRestoreTicks = focusRestoreTicks
        ) {
            // The memory lives here, not above the player: a card in the player's own
            // "شاهد المزيد" rail must not become the place the remote comes back to, or
            // closing the player would look for a card that has left the screen.
            CompositionLocalProvider(LocalTvCardFocusMemory provides cardFocusMemory) {
                screenStates.SaveableStateProvider(destination.name) {
                    when (destination) {
                        TvDestination.Home -> TvHomeScreen(
                            uiState = uiState,
                            onVideoClick = ::openVideo,
                            onRetry = viewModel::loadVideos,
                            focusAllTicks = homeFocusTicks
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
            }
        }

        // Keyed by id so a suggestion swap rebuilds the player (and its ExoPlayer)
        // instead of mutating the one already playing.
        playingVideo?.let { video ->
            key(video.id) {
                TvPlayerScreen(
                    video = video,
                    allVideos = allVideos,
                    onVideoSelect = ::openVideo,
                    onBack = ::closePlayer,
                    startPositionMs = resumeFromMs
                )
            }
        }
    }
}
