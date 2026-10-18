package com.waha.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.waha.data.SavedVideosStore
import com.waha.data.WatchHistoryStore
import com.waha.domain.PlaybackResolver
import com.waha.domain.VideoItem
import com.waha.domain.VideoSuggestionsEngine
import com.waha.domain.WahaPlayer
import com.waha.domain.formatPlaybackTime
import com.waha.domain.toMediaSource
import com.waha.tv.TvCardMeta
import com.waha.tv.TvTestTags
import com.waha.tv.ui.theme.WahaTvColors
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

/** How long a seek hint stays in the middle of the video, icon first. */
private const val FEEDBACK_TIMEOUT_MS = 700L

/**
 * What the middle of the screen is telling the viewer, if anything.
 *
 * The remote has no pointer, so every press that changes playback says so with an
 * icon in the one place the eye already rests — no text, nothing to read across the
 * room.
 */
internal enum class TvPlayerFeedback(val description: String) {
    Play("تشغيل"),
    Pause("إيقاف مؤقت"),
    Rewind("إرجاع ١٠ ثوانٍ"),
    Forward("تقديم ١٠ ثوانٍ");

    val icon: ImageVector
        get() = when (this) {
            Play -> Icons.Filled.PlayArrow
            Pause -> Icons.Filled.Pause
            Rewind -> Icons.Filled.Replay10
            Forward -> Icons.Filled.Forward10
        }
}

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

/**
 * The option matching a persisted quality height (null = auto). An unknown
 * height (an older build's choice, or an edited preferences file) falls back to
 * auto rather than leaving the panel with nothing selected.
 */
internal fun qualityOptionFor(height: Int?): TvQualityOption =
    TV_QUALITY_OPTIONS.firstOrNull { it.maxHeight == height } ?: TV_QUALITY_OPTIONS.first()

/** How many "شاهد المزيد" cards the player panel shows. */
private const val PLAYER_SUGGESTION_COUNT = 12

/**
 * TV player: no touch controls at all.
 *
 * - centre / OK  → play or pause, and the middle of the screen answers with the
 *                  same glyph (a 10-second arrow while seeking)
 * - left / right → seek 10 seconds
 * - down         → open the control panel (transport, quality, save + "شاهد المزيد")
 * - up           → close the panel, or bring the overlay back
 * - back         → close the panel, or leave the player
 *
 * While the panel is open it owns the D-pad: the video's own keys stand down, so
 * the arrows walk the panel's controls and the centre key works whichever control
 * the remote is on.
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
    onBack: () -> Unit,
    /** Where playback should start — the Continue Watching rail passes the saved position. */
    startPositionMs: Long = 0L
) {
    val context = LocalContext.current
    val player = remember(context) { WahaPlayer.create(context) }
    val rootFocusRequester = remember { FocusRequester() }
    val panelFocusRequester = remember { FocusRequester() }

    var playback by remember(video.id) { mutableStateOf<TvPlaybackState>(TvPlaybackState.Loading) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    // The transient icon in the middle (a seek hint), and a tick so pressing the
    // same direction twice restarts its timer.
    var feedback by remember { mutableStateOf<TvPlayerFeedback?>(null) }
    var feedbackTicks by remember { mutableIntStateOf(0) }
    // Bumped by every press that reveals the overlay, so the hide timer restarts
    // even when the overlay was already up.
    var overlayTicks by remember { mutableIntStateOf(0) }
    // Only the first resolve uses [startPositionMs]: after that, switching
    // quality must resume from wherever the viewer actually is.
    var pendingResumeMs by remember(video.id) { mutableLongStateOf(startPositionMs.coerceAtLeast(0L)) }
    var overlayVisible by remember { mutableStateOf(true) }
    var panelVisible by remember { mutableStateOf(false) }

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
        val resumeFrom = pendingResumeMs.takeIf { it > 0L }
            ?: player.currentPosition.coerceAtLeast(0L)
        pendingResumeMs = 0L
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

    // Remember where the viewer stopped, so leaving the player (or the room)
    // does not lose their place: this is what Continue Watching resumes from.
    DisposableEffect(video.id) {
        onDispose { WatchHistoryStore.updateProgress(video.id, positionMs, durationMs) }
    }

    // Hide the seek hint shortly after it appears.
    LaunchedEffect(feedbackTicks) {
        if (feedback != null) {
            delay(FEEDBACK_TIMEOUT_MS)
            feedback = null
        }
    }

    LaunchedEffect(overlayVisible, overlayTicks, isPlaying, playback, panelVisible) {
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
        // The requester only becomes usable once its node has been placed, which is a
        // frame after the panel is first composed. Requesting in the effect itself is
        // early enough to be dropped, leaving the first D-pad press dead; one frame
        // of patience lands the focus before the viewer can press anything.
        withFrameNanos { }
        runCatching {
            if (panelVisible) panelFocusRequester.requestFocus()
            else rootFocusRequester.requestFocus()
        }
    }

    /** Shows the overlay and gives its hide timer a fresh window. */
    fun showOverlay() {
        overlayVisible = true
        overlayTicks++
    }

    LaunchedEffect(Unit) { runCatching { rootFocusRequester.requestFocus() } }

    BackHandler(enabled = panelVisible) { panelVisible = false }

    fun togglePlayback() {
        showOverlay()
        // The middle of the screen shows the state the viewer is now in: the pause
        // glyph while it plays, the play glyph while it is held.
        feedback = null
        feedbackTicks++
        if (isPlaying) {
            player.pause()
            // Pausing is the moment the viewer is most likely to walk away, so
            // the position is saved here as well as when the player closes.
            WatchHistoryStore.updateProgress(video.id, positionMs, durationMs)
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
        showOverlay()
        feedback = if (deltaMs < 0) TvPlayerFeedback.Rewind else TvPlayerFeedback.Forward
        feedbackTicks++
    }

    fun openPanel() {
        showOverlay()
        // [LaunchedEffect] hands the panel focus as soon as it has been laid out.
        panelVisible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            // While the panel is open the video gives up the D-pad entirely: the only
            // focusable things on screen are the panel's own controls, so walking to
            // the end of a row has nowhere to go instead of dropping off the panel.
            .focusable(enabled = !panelVisible)
            // Preview, not onKeyEvent: the D-pad belongs to the video, and its arrows
            // must never wander to another control while a film is playing. The panel,
            // whenever it is open, owns the arrows and the centre key instead — this
            // handler only closes it and lets media keys through.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                if (panelVisible) {
                    return@onPreviewKeyEvent when (event.key) {
                        Key.DirectionUp -> {
                            panelVisible = false
                            true
                        }

                        Key.MediaPlayPause -> {
                            togglePlayback()
                            true
                        }

                        else -> false
                    }
                }

                when (event.key) {
                    Key.DirectionCenter, Key.Enter -> {
                        togglePlayback()
                        true
                    }

                    Key.DirectionLeft -> {
                        seekBy(-SEEK_STEP_MS)
                        true
                    }

                    Key.DirectionRight -> {
                        seekBy(SEEK_STEP_MS)
                        true
                    }

                    Key.DirectionDown -> {
                        openPanel()
                        true
                    }

                    Key.DirectionUp -> {
                        showOverlay()
                        true
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
            TvPlaybackState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = WahaTvColors.Accent)
                }
            }

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
            // The name and its category occupy the top-right corner, the progress bar
            // the bottom edge: the two places a viewer looks without leaving the video.
            PlayerTopOverlay(
                title = video.title,
                category = TvCardMeta.categoryLabel(video),
                qualityLabel = quality.label,
                modifier = Modifier.align(Alignment.TopStart)
            )
            PlayerBottomOverlay(
                positionMs = positionMs,
                durationMs = durationMs,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // The one control a viewer looks for sits dead centre: pause while the
        // video runs, play while it is held, and a 10-second arrow for the length
        // of a seek press. No text — a glyph reads from across the room.
        if (playback == TvPlaybackState.Playing && !panelVisible &&
            (overlayVisible || !isPlaying || feedback != null)
        ) {
            PlayerCenterOverlay(
                isPlaying = isPlaying,
                feedback = feedback,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (panelVisible) {
            PlayerPanel(
                videoId = video.id,
                isPlaying = isPlaying,
                qualityOptions = TV_QUALITY_OPTIONS,
                selectedQuality = quality,
                onQualitySelect = onQualityChange,
                onTogglePlayback = ::togglePlayback,
                onSeekBackward = { seekBy(-SEEK_STEP_MS) },
                onSeekForward = { seekBy(SEEK_STEP_MS) },
                suggestions = suggestions,
                onVideoSelect = { selected ->
                    panelVisible = false
                    onVideoSelect(selected)
                },
                initialFocusRequester = panelFocusRequester,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
    }
}

/**
 * The video's name at the screen's top-right — the corner Arabic reading starts
 * from — with its category directly underneath. The quality label sits in the
 * opposite corner so it never competes with the title.
 */
@Composable
internal fun PlayerTopOverlay(
    title: String,
    category: String,
    qualityLabel: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(1f)
                .background(
                    Brush.verticalGradient(
                        listOf(WahaTvColors.MediaScrim, Color.Transparent)
                    )
                )
                .padding(start = 48.dp, end = 48.dp, top = 26.dp, bottom = 44.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                color = WahaTvColors.OnMedia,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // The category the row was built from, right under the name.
            Text(
                text = category,
                color = WahaTvColors.OnMedia.copy(alpha = 0.78f),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        // The stream's quality sits in the far corner as a quiet pill: useful when
        // the video looks soft, out of the way of the title. End is the left edge
        // on this right-to-left screen.
        Text(
            text = qualityLabel,
            color = WahaTvColors.OnMedia.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 48.dp, top = 26.dp)
                .background(
                    color = WahaTvColors.MediaScrimSoft,
                    shape = RoundedCornerShape(percent = 50)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/**
 * The icon that answers a press, in the middle of the video.
 *
 * What it shows, in order of precedence: the seek hint just pressed, otherwise the
 * playback state (pause while the video runs, play while it is held). Nothing at
 * all while the overlay is tucked away, so a paused film is not permanently
 * stamped with a glyph.
 */
@Composable
internal fun PlayerCenterOverlay(
    isPlaying: Boolean,
    feedback: TvPlayerFeedback?,
    modifier: Modifier = Modifier
) {
    val shown = feedback ?: if (isPlaying) TvPlayerFeedback.Pause else TvPlayerFeedback.Play

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .background(color = WahaTvColors.MediaScrimSoft, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = shown.icon,
                contentDescription = shown.description,
                tint = WahaTvColors.OnMedia,
                modifier = Modifier
                    .size(56.dp)
                    .testTag(TvTestTags.PlayerCenter)
            )
        }
    }
}

/**
 * The progress bar along the bottom edge: times at both ends and the phone's own
 * slider in between — the same Material 3 component, with the same colours, so the
 * two builds cannot drift apart. It is not focusable (a remote has nothing to
 * drag) and the value follows playback; the D-pad seeks in 10-second steps from
 * the video itself.
 *
 * Forced LTR, exactly like the phone's control bar, so the bar fills from left to
 * right on an otherwise right-to-left screen.
 */
@Composable
internal fun PlayerBottomOverlay(
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                    )
                )
                .padding(horizontal = 48.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = formatPlaybackTime(positionMs),
                color = WahaTvColors.OnMedia,
                style = MaterialTheme.typography.labelLarge
            )

            Slider(
                value = progressFraction(positionMs, durationMs),
                onValueChange = {},
                enabled = false,
                colors = SliderDefaults.colors(
                    disabledThumbColor = WahaTvColors.Accent,
                    disabledActiveTrackColor = WahaTvColors.Accent,
                    disabledInactiveTrackColor = WahaTvColors.ProgressTrack
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag(TvTestTags.PlayerProgress)
            )

            Text(
                text = formatPlaybackTime(durationMs),
                color = WahaTvColors.OnMedia,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/**
 * The panel the down key opens, in three tidy bands — transport, quality, then the
 * same "شاهد المزيد" rail the phone shows below the video.
 *
 * The transport row leads with the one control a viewer needs (play/pause), the
 * two 10-second buttons around it, and the save toggle at the far edge. The quality
 * choices sit together on their own row, where a quality is a choice among equals
 * rather than a fifth transport button.
 *
 * Colours are deliberately neutral: translucent white chips on a near-black panel,
 * a white pill for the selected choice and the brand orange only as the focus ring.
 * Nothing here wears a green/turquoise surface, so the panel reads as one control
 * strip instead of a patchwork.
 */
@Composable
internal fun PlayerPanel(
    videoId: String,
    isPlaying: Boolean,
    qualityOptions: List<TvQualityOption>,
    selectedQuality: TvQualityOption,
    onQualitySelect: (TvQualityOption) -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    suggestions: List<VideoItem>,
    onVideoSelect: (VideoItem) -> Unit,
    initialFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(WahaTvColors.PanelBackground)
            .testTag(TvTestTags.PlayerPanel)
    ) {
        // A hairline separates the panel from the video without a heavy edge.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.10f))
        )

        Column(
            modifier = Modifier.padding(
                start = WahaTvDimens.PlayerPanelPadding,
                end = WahaTvDimens.PlayerPanelPadding,
                top = 18.dp,
                bottom = 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PanelActionButton(
                        icon = Icons.Filled.Replay10,
                        description = "إرجاع ١٠ ثوانٍ",
                        onClick = onSeekBackward,
                        modifier = Modifier.testTag(TvTestTags.playerControl("rewind"))
                    )
                    PanelActionButton(
                        icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        description = if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                        onClick = onTogglePlayback,
                        primary = true,
                        focusRequester = initialFocusRequester,
                        modifier = Modifier.testTag(TvTestTags.playerControl("play_pause"))
                    )
                    PanelActionButton(
                        icon = Icons.Filled.Forward10,
                        description = "تقديم ١٠ ثوانٍ",
                        onClick = onSeekForward,
                        modifier = Modifier.testTag(TvTestTags.playerControl("forward"))
                    )
                }

                SavePill(videoId = videoId)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الجودة",
                    color = WahaTvColors.TextMuted,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = 6.dp)
                )

                qualityOptions.forEach { option ->
                    PanelPill(
                        label = option.label,
                        selected = option == selectedQuality,
                        onClick = { onQualitySelect(option) },
                        modifier = Modifier.testTag(TvTestTags.playerQuality(option.label))
                    )
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
                            width = 240.dp
                        )
                    }
                }
            }
        }
    }
}

/** The panel's ink: the near-black that sits on a white selected pill. */
private val PanelInk = Color(0xFF121212)

/** One resolved set of colours for a panel control, so a pill and a button match. */
private data class PanelControlColors(
    val container: Color,
    val content: Color,
    val border: Color
)

/**
 * The panel's one colour rule: neutral fill, white ink when selected, accent ring
 * when focused. No turquoise/green surfaces anywhere.
 */
@Composable
private fun panelControlColors(selected: Boolean, isFocused: Boolean): PanelControlColors = when {
    selected && isFocused -> PanelControlColors(Color.White, PanelInk, WahaTvColors.Accent)
    selected -> PanelControlColors(Color.White, PanelInk, Color.White.copy(alpha = 0.35f))
    isFocused -> PanelControlColors(Color.White.copy(alpha = 0.16f), Color.White, WahaTvColors.Accent)
    else -> PanelControlColors(
        container = Color.White.copy(alpha = 0.06f),
        content = WahaTvColors.TextMuted,
        border = Color.White.copy(alpha = 0.16f)
    )
}

/**
 * A round panel button: an icon, a D-pad target, and nothing else.
 *
 * The primary button (play/pause) is the one solid white shape in the panel, so the
 * remote's first landing place is also the way to start the video.
 */
@Composable
private fun PanelActionButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val colors = panelControlColors(selected = primary, isFocused = isFocused)
    val shape = CircleShape

    Box(
        modifier = modifier
            .size(if (primary) 56.dp else 48.dp)
            .clip(shape)
            .background(colors.container)
            .border(
                border = BorderStroke(
                    width = if (isFocused) WahaTvDimens.FocusBorderWidth else if (primary) 0.dp else 1.dp,
                    color = if (isFocused) WahaTvColors.Accent else if (primary) Color.Transparent else colors.border
                ),
                shape = shape
            )
            .then(if (focusRequester == null) Modifier else Modifier.focusRequester(focusRequester))
            .tvClickOnEnterKey(onClick)
            .focusable(interactionSource = interactionSource)
            // One node per button: the icon and the D-pad semantics belong together,
            // which is also what makes the button addressable by its own label.
            .semantics(mergeDescendants = true) {
                contentDescription = description
                semanticsOnClick(label = description) { onClick(); true }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.content,
            modifier = Modifier.size(if (primary) 28.dp else 24.dp)
        )
    }
}

/**
 * A panel pill: a quality choice, or the save toggle. One shape, one D-pad path.
 */
@Composable
private fun PanelPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val colors = panelControlColors(selected = selected, isFocused = isFocused)
    val shape = CircleShape

    Row(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(colors.container)
            .border(
                border = BorderStroke(
                    width = if (isFocused) WahaTvDimens.FocusBorderWidth else 1.dp,
                    color = colors.border
                ),
                shape = shape
            )
            .tvClickOnEnterKey(onClick)
            .focusable(interactionSource = interactionSource)
            .semantics(mergeDescendants = true) { semanticsOnClick(label = label) { onClick(); true } }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = colors.content,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = label,
            color = colors.content,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/**
 * The save toggle, at the far edge of the transport row.
 *
 * It flips the video in the same [SavedVideosStore] the phone build writes, so a
 * save made here appears in المكتبة on both apps.
 */
@Composable
private fun SavePill(videoId: String) {
    // Reading the snapshot list subscribes the pill to saves made anywhere else.
    val isSaved = SavedVideosStore.savedIds.contains(videoId)

    PanelPill(
        label = if (isSaved) "محفوظ" else "حفظ",
        selected = isSaved,
        onClick = { SavedVideosStore.toggle(videoId) },
        leadingIcon = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
        modifier = Modifier.testTag(TvTestTags.playerControl("save"))
    )
}

private fun progressFraction(positionMs: Long, durationMs: Long): Float =
    if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
