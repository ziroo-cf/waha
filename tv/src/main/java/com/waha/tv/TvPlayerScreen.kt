package com.waha.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Border
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.waha.data.WatchHistoryStore
import com.waha.domain.PlaybackResolver
import com.waha.domain.VideoItem
import com.waha.domain.VideoSuggestionsEngine
import com.waha.domain.WahaPlayer
import com.waha.domain.formatPlaybackTime
import com.waha.domain.toMediaSource
import kotlinx.coroutines.delay

/** How the TV player is doing: resolving the stream, playing it, or stuck. */
private sealed interface TvPlaybackState {
    data object Loading : TvPlaybackState
    data object Playing : TvPlaybackState
    data object Error : TvPlaybackState
}

/** How far the left/right keys jump. */
private const val SEEK_STEP_MS = 10_000L

/** How long the top overlay stays up before hiding itself. */
private const val OVERLAY_TIMEOUT_MS = 4_000L

/**
 * A quality choice. [maxHeight] caps the resolved stream; null means "whatever
 * the extractor finds best", which is what the phone build plays.
 */
data class TvQualityOption(val label: String, val maxHeight: Int?)

/** The choices the player panel offers, best first. */
internal val TV_QUALITY_OPTIONS = listOf(
    TvQualityOption("تلقائي", null),
    TvQualityOption("1080p", 1080),
    TvQualityOption("720p", 720),
    TvQualityOption("360p", 360)
)

/** How many "شاهد المزيد" cards the player panel shows. */
private const val PLAYER_SUGGESTION_COUNT = 12

/**
 * TV player: no touch controls at all.
 *
 * - centre / OK  → play or pause
 * - left / right → seek 10 seconds
 * - down         → open the control panel (quality + "شاهد المزيد")
 * - up           → close the panel
 * - back         → close the panel, or leave the player
 *
 * Stream resolution, the player itself and the suggestion ranking all come from
 * :core, exactly like the phone build, so the two stay in sync when extraction
 * or ranking changes.
 */
@Composable
fun TvPlayerScreen(
    video: VideoItem,
    allVideos: List<VideoItem>,
    quality: TvQualityOption,
    onQualityChange: (TvQualityOption) -> Unit,
    onVideoSelect: (VideoItem) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val player = remember(context) { WahaPlayer.create(context) }
    val rootFocusRequester = remember { FocusRequester() }
    val panelFocusRequester = remember { FocusRequester() }

    var playback by remember(video.id) { mutableStateOf<TvPlaybackState>(TvPlaybackState.Loading) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var seekFeedback by remember { mutableStateOf(0) } // -1 back, +1 forward, 0 none
    var overlayVisible by remember { mutableStateOf(true) }
    var panelVisible by remember { mutableStateOf(false) }
    // True while the video itself (not a panel control) holds the D-pad focus.
    var rootHasFocus by remember { mutableStateOf(true) }

    val suggestions = remember(video.id, allVideos) {
        VideoSuggestionsEngine.build(
            current = video,
            allVideos = allVideos,
            watchedIds = WatchHistoryStore.recentIds.toSet(),
            count = PLAYER_SUGGESTION_COUNT
        )
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                playback = TvPlaybackState.Error
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.stop()
            player.clearMediaItems()
            player.release()
        }
    }

    // Resolving runs again whenever the video or the chosen quality changes, and
    // resumes from where the viewer was so a quality switch does not restart.
    LaunchedEffect(video.id, quality) {
        val resumeFrom = player.currentPosition.coerceAtLeast(0L)
        playback = TvPlaybackState.Loading
        val source = PlaybackResolver.resolve(video, maxHeight = quality.maxHeight)
        if (source == null) {
            playback = TvPlaybackState.Error
        } else {
            player.setMediaSource(source.toMediaSource(context))
            player.prepare()
            if (resumeFrom > 0L) player.seekTo(resumeFrom)
            player.playWhenReady = true
            playback = TvPlaybackState.Playing
            WatchHistoryStore.record(video.id)
        }
    }

    // Poll the player so the progress bar advances without a recomposition storm.
    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    // Hide the seek hint shortly after it appears.
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != 0) {
            delay(600)
            seekFeedback = 0
        }
    }

    LaunchedEffect(overlayVisible, isPlaying, playback, panelVisible) {
        if (overlayVisible && isPlaying && playback == TvPlaybackState.Playing && !panelVisible) {
            delay(OVERLAY_TIMEOUT_MS)
            overlayVisible = false
        }
    }

    // The panel takes focus so the remote drives its chips and cards; closing it
    // hands the D-pad back to the video. A requester whose node is not attached
    // yet throws instead of focusing, and losing the focus move is recoverable
    // (the D-pad simply starts from the default target), so it must not crash.
    LaunchedEffect(panelVisible) {
        runCatching {
            if (panelVisible) panelFocusRequester.requestFocus()
            else rootFocusRequester.requestFocus()
        }
    }

    /**
     * Sends focus into the open panel. Called when the panel opens, and again from
     * the key handler if a key arrives before that first request landed — a slow
     * device can deliver a D-pad press while focus is still on the video, and a
     * press that silently does nothing feels like a broken remote.
     */
    fun focusPanel() {
        runCatching { panelFocusRequester.requestFocus() }
    }

    LaunchedEffect(Unit) { runCatching { rootFocusRequester.requestFocus() } }

    BackHandler(enabled = panelVisible) { panelVisible = false }

    fun togglePlayback() {
        overlayVisible = true
        if (isPlaying) {
            player.pause()
        } else {
            if (durationMs > 0L && positionMs >= durationMs) player.seekTo(0L)
            player.play()
        }
    }

    fun seekBy(deltaMs: Long) {
        val target = (player.currentPosition + deltaMs)
            .coerceIn(0L, player.duration.coerceAtLeast(0L))
        player.seekTo(target)
        positionMs = target
        seekFeedback = if (deltaMs < 0) -1 else 1
    }

    fun openPanel() {
        overlayVisible = true
        panelVisible = true
        focusPanel()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .onFocusChanged { state -> rootHasFocus = state.isFocused }
            .focusable()
            // Preview, not onKeyEvent: the D-pad arrows are ours, they must never
            // move focus to another control while a video is playing. Once the
            // panel is open, left/right/centre belong to its controls instead.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Same race as [focusPanel]: while the panel is open but focus is
                // still on the video, this press enters the panel instead of
                // being dropped, then the panel's own controls take over.
                if (panelVisible && rootHasFocus && event.key != Key.Back) {
                    focusPanel()
                    return@onPreviewKeyEvent true
                }

                when (event.key) {
                    Key.DirectionCenter, Key.Enter -> {
                        if (panelVisible) false else {
                            togglePlayback()
                            true
                        }
                    }

                    Key.DirectionLeft -> {
                        if (panelVisible) false else {
                            seekBy(-SEEK_STEP_MS)
                            true
                        }
                    }

                    Key.DirectionRight -> {
                        if (panelVisible) false else {
                            seekBy(SEEK_STEP_MS)
                            true
                        }
                    }

                    Key.DirectionDown -> {
                        if (panelVisible) false else {
                            openPanel()
                            true
                        }
                    }

                    Key.DirectionUp -> {
                        if (panelVisible) {
                            panelVisible = false
                            true
                        } else {
                            overlayVisible = true
                            true
                        }
                    }

                    Key.MediaPlayPause -> {
                        togglePlayback()
                        true
                    }

                    else -> false
                }
            }
    ) {
        if (playback == TvPlaybackState.Playing) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        // The screen draws its own remote-friendly overlays.
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { view ->
                    view.player = player
                    view.keepScreenOn = true
                },
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }

        when (playback) {
            TvPlaybackState.Loading -> Text(
                text = "جارٍ تجهيز الفيديو…",
                color = WahaTvColors.TextWarm,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center)
            )

            TvPlaybackState.Error -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "تعذّر تشغيل الفيديو",
                    color = WahaTvColors.TextWarm,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "اضغط رجوع للخروج، أو اختر جودة أخرى من اللوحة",
                    color = WahaTvColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            TvPlaybackState.Playing -> Unit
        }

        if (playback == TvPlaybackState.Playing && overlayVisible) {
            PlayerTopOverlay(
                title = video.title,
                qualityLabel = quality.label,
                positionMs = positionMs,
                durationMs = durationMs,
                seekFeedback = seekFeedback,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }

        if (panelVisible) {
            PlayerPanel(
                qualityOptions = TV_QUALITY_OPTIONS,
                selectedQuality = quality,
                onQualitySelect = onQualityChange,
                suggestions = suggestions,
                onVideoSelect = { selected ->
                    panelVisible = false
                    onVideoSelect(selected)
                },
                firstChipFocusRequester = panelFocusRequester,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
    }
}

/** Title, quality, progress and the seek hint that follows a left/right press. */
@Composable
private fun PlayerTopOverlay(
    title: String,
    qualityLabel: String,
    positionMs: Long,
    durationMs: Long,
    seekFeedback: Int,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(WahaTvColors.MediaScrim)
                .padding(horizontal = 36.dp, vertical = 22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    color = WahaTvColors.TextWarm,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "الجودة: $qualityLabel",
                    color = WahaTvColors.TextMuted,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = formatPlaybackTime(positionMs),
                    color = WahaTvColors.TextWarm,
                    style = MaterialTheme.typography.labelLarge
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(WahaTvColors.ProgressTrack)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressFraction(positionMs, durationMs))
                            .background(WahaTvColors.Accent)
                    )
                }
                Text(
                    text = formatPlaybackTime(durationMs),
                    color = WahaTvColors.TextWarm,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        if (seekFeedback != 0) {
            Text(
                text = if (seekFeedback < 0) "⏪ ١٠ ثوانٍ" else "١٠ ثوانٍ ⏩",
                color = WahaTvColors.TextWarm,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

/**
 * The panel the down key opens: the quality choices the phone build offers in
 * its player sheet, plus the same "شاهد المزيد" rail the phone shows below the
 * video — both driven from :core so the ranking matches.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerPanel(
    qualityOptions: List<TvQualityOption>,
    selectedQuality: TvQualityOption,
    onQualitySelect: (TvQualityOption) -> Unit,
    suggestions: List<VideoItem>,
    onVideoSelect: (VideoItem) -> Unit,
    firstChipFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(WahaTvColors.Surface.copy(alpha = 0.94f))
            .padding(
                start = WahaTvDimens.PlayerPanelPadding,
                end = WahaTvDimens.PlayerPanelPadding,
                top = 22.dp,
                bottom = 26.dp
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "الجودة",
            color = WahaTvColors.TextMuted,
            style = MaterialTheme.typography.labelLarge
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            qualityOptions.forEachIndexed { index, option ->
                val selected = option == selectedQuality
                FilterChip(
                    selected = selected,
                    onClick = { onQualitySelect(option) },
                    colors = FilterChipDefaults.colors(
                        containerColor = WahaTvColors.SurfaceVariant,
                        contentColor = WahaTvColors.TextMuted,
                        focusedContainerColor = WahaTvColors.Accent,
                        focusedContentColor = WahaTvColors.OnAccent,
                        selectedContainerColor = WahaTvColors.Accent,
                        selectedContentColor = WahaTvColors.OnAccent
                    ),
                    scale = FilterChipDefaults.scale(focusedScale = 1.12f),
                    border = FilterChipDefaults.border(
                        border = Border(border = BorderStroke(1.dp, WahaTvColors.Line)),
                        selectedBorder = tvFocusBorder(),
                        focusedSelectedBorder = tvFocusBorder()
                    ),
                    // The first chip is where entry focus lands: from there the
                    // arrows walk the options and up closes the panel.
                    modifier = if (index == 0) Modifier.focusRequester(firstChipFocusRequester) else Modifier
                ) {
                    Text(text = option.label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (suggestions.isNotEmpty()) {
            Text(
                text = "شاهد المزيد",
                color = WahaTvColors.TextWarm,
                style = MaterialTheme.typography.titleMedium
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(WahaTvDimens.CardSpacing)) {
                items(suggestions, key = { it.id }) { suggestion ->
                    TvVideoCard(
                        video = suggestion,
                        onClick = { onVideoSelect(suggestion) },
                        width = 260.dp
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

private fun progressFraction(positionMs: Long, durationMs: Long): Float =
    if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
