package com.waha.tv.ui.screens

import android.util.Log
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val TAG = "TvPlayer"

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

/** How long a seek hint stays at the side of the video. */
private const val FEEDBACK_TIMEOUT_MS = 700L

/**
 * What a press is telling the viewer, if anything.
 *
 * The remote has no pointer, so every press that changes playback says so with an
 * icon — no text, nothing to read across the room. Play and pause belong in the
 * middle, where the eye already rests; the two seek hints belong at the sides they
 * seek towards, so a rewind is never mistaken for the pause glyph it used to sit on
 * top of.
 */
internal enum class TvPlayerFeedback(val description: String) {
    Play("تشغيل"),
    Pause("إيقاف مؤقت"),
    Rewind("إرجاع ١٠ ثوانٍ"),
    Forward("تقديم ١٠ ثوانٍ");

    val icon: ImageVector
        get() = when (this) {
            Play -> TvIcons.PlayArrow
            Pause -> TvIcons.Pause
            Rewind -> TvIcons.Replay10
            Forward -> TvIcons.Forward10
        }
}

/**
 * The player's chrome keeps the type sizes it was designed with, while the browse
 * UI grew by half: the video's overlays sit over the picture and are already read
 * from the same sofa, so scaling them would only cover more of the film.
 */
private fun TextStyle.playerSized(size: Float, line: Float): TextStyle =
    copy(fontSize = size.sp, lineHeight = line.sp)

/** How many "شاهد المزيد" cards the player panel shows. */
private const val PLAYER_SUGGESTION_COUNT = 12

/**
 * TV player: no touch controls at all.
 *
 * - centre / OK  → play or pause
 * - left / right → seek 10 seconds, answered by an arrow at that side
 * - down         → open the control panel (transport, save + "شاهد المزيد")
 * - up           → close the panel, or bring the overlay back
 * - back         → close the panel, or leave the player
 *
 * While the panel is open it owns the D-pad: the video's own keys stand down, so
 * the arrows walk the panel's controls and the centre key works whichever control
 * the remote is on.
 *
 * Stream resolution, the player itself and the suggestion ranking all come from
 * :core, exactly like the phone build, so the two stay in sync when extraction
 * or ranking changes. The stream is always the extractor's default pick: the TV
 * build offers no quality menu.
 */
@Composable
fun TvPlayerScreen(
    video: VideoItem,
    allVideos: List<VideoItem>,
    onVideoSelect: (VideoItem) -> Unit,
    onBack: () -> Unit,
    /** Where playback should start — the Continue Watching rail passes the saved position. */
    startPositionMs: Long = 0L
) {
    val context = LocalContext.current
    // The TV player gets the lean buffer profile: a 1GB box cannot hold the phone's
    // 50-second buffer alongside a merged video+audio stream.
    val player = remember(context) { WahaPlayer.createForTv(context) }
    val rootFocusRequester = remember { FocusRequester() }
    val panelFocusRequester = remember { FocusRequester() }

    var playback by remember(video.id) { mutableStateOf<TvPlaybackState>(TvPlaybackState.Loading) }
    var isPlaying by remember { mutableStateOf(false) }
    // True while ExoPlayer is refilling its buffer — after a seek, or when the network
    // dips. Nothing else tells the viewer why the picture stopped.
    var isBuffering by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    // The transient icon at the side (a seek hint), and a tick so pressing the same
    // direction twice restarts its timer.
    var feedback by remember { mutableStateOf<TvPlayerFeedback?>(null) }
    var feedbackTicks by remember { mutableIntStateOf(0) }
    // Bumped by every press that reveals the overlay, so the hide timer restarts
    // even when the overlay was already up.
    var overlayTicks by remember { mutableIntStateOf(0) }
    var overlayVisible by remember { mutableStateOf(true) }
    var panelVisible by remember { mutableStateOf(false) }
    var pendingResumeMs by remember(video.id) { mutableLongStateOf(startPositionMs.coerceAtLeast(0L)) }

    // The history is one immutable snapshot now: its watched set only changes when
    // the set of watched videos does, not when a position is saved, so opening the
    // player or pausing it no longer re-ranks the whole library.
    // derivedStateOf so a saved playback position (which only moves the position
    // maps) does not invalidate this whole player: only a changed watched set does.
    val watchedIds by remember { derivedStateOf { WatchHistoryStore.watchedIds } }
    val suggestions by produceState(emptyList(), video.id, allVideos, watchedIds) {
        value = withContext(Dispatchers.Default) {
            VideoSuggestionsEngine.build(
                current = video,
                allVideos = allVideos,
                watchedIds = watchedIds,
                count = PLAYER_SUGGESTION_COUNT
            )
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
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

    // Resolving runs whenever the video changes, and resumes from where the viewer
    // was so jumping to a suggestion does not restart from the beginning.
    LaunchedEffect(video.id) {
        val resumeFrom = pendingResumeMs.takeIf { it > 0L }
            ?: player.currentPosition.coerceAtLeast(0L)
        pendingResumeMs = 0L
        playback = TvPlaybackState.Loading
        val source = PlaybackResolver.resolve(video)
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
    // hands the D-pad back to the video.
    //
    // A requester whose node is not attached yet throws instead of focusing, and the
    // request can also be dropped while the panel is still being placed — which is why
    // this retries for a few frames instead of trying once and swallowing the failure.
    // The fallback matters more than the retry: while the panel is up the video's own
    // D-pad handler is disabled, so a panel that could not take focus would leave every
    // key dead except back. If focus cannot be placed, the panel closes again.
    LaunchedEffect(panelVisible) {
        val target = if (panelVisible) panelFocusRequester else rootFocusRequester
        if (!target.requestFocusWithRetry()) {
            Log.w(TAG, "focus unavailable for ${if (panelVisible) "the control panel" else "the player"}")
            if (panelVisible) panelVisible = false
        }
    }

    /** Shows the overlay and gives its hide timer a fresh window. */
    fun showOverlay() {
        overlayVisible = true
        overlayTicks++
    }

    LaunchedEffect(Unit) { rootFocusRequester.requestFocusWithRetry() }

    BackHandler(enabled = panelVisible) { panelVisible = false }

    fun togglePlayback() {
        showOverlay()
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
        // The video surface is mounted once and never torn down. Unmounting PlayerView
        // would destroy and recreate the SurfaceView — a black flash plus a fresh
        // MediaCodec/surface setup — on the device that can least afford it. Loading,
        // buffering and error states are drawn over the surface instead.
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

        when (playback) {
            TvPlaybackState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = WahaTvColors.Accent,
                        modifier = Modifier.testTag(TvTestTags.PlayerBuffering)
                    )
                }
            }

            TvPlaybackState.Error -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "تعذّر تشغيل الفيديو",
                    color = WahaTvColors.TextWarm,
                    style = MaterialTheme.typography.titleLarge.playerSized(23f, 30f)
                )
                Text(
                    text = "اضغط رجوع للخروج، أو اختر فيديو اخر",
                    color = WahaTvColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium.playerSized(18f, 26f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            TvPlaybackState.Playing -> Unit
        }

        // The circle the phone player shows while a stream fills: fast-forwarding, or a
        // network that has not caught up, stops the picture without saying why. It takes
        // the middle only while there is nothing else to say there.
        if (playback == TvPlaybackState.Playing && isBuffering) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = WahaTvColors.Accent,
                    modifier = Modifier.testTag(TvTestTags.PlayerBuffering)
                )
            }
        }

        if (playback == TvPlaybackState.Playing && overlayVisible) {
            // The name and its category occupy the top-right corner, the progress bar
            // the bottom edge: the two places a viewer looks without leaving the video.
            PlayerTopOverlay(
                title = video.title,
                category = TvCardMeta.categoryLabel(video),
                modifier = Modifier.align(Alignment.TopStart)
            )
            PlayerBottomOverlay(
                positionMs = { positionMs },
                durationMs = { durationMs },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // The one control a viewer looks for sits dead centre: pause while the video
        // runs, play while it is held. A seek does not replace it — the arrow belongs at
        // the side the seek moves towards, so the two never stack on one another.
        if (playback == TvPlaybackState.Playing && !panelVisible && !isBuffering &&
            feedback == null && (overlayVisible || !isPlaying)
        ) {
            PlayerCenterOverlay(
                isPlaying = isPlaying,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // A 10-second seek is answered at the edge of the frame it moves towards.
        if (playback == TvPlaybackState.Playing && !panelVisible) {
            PlayerSeekOverlay(
                feedback = feedback,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (panelVisible) {
            PlayerPanel(
                videoId = video.id,
                isPlaying = isPlaying,
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
 * from — with its category directly underneath.
 */
@Composable
internal fun PlayerTopOverlay(
    title: String,
    category: String,
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
                style = MaterialTheme.typography.headlineSmall.playerSized(26f, 34f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = category,
                color = WahaTvColors.OnMedia.copy(alpha = 0.78f),
                style = MaterialTheme.typography.labelLarge.playerSized(18f, 24f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/**
 * The play/pause glyph in the middle of the video: pause while it runs, play while
 * it is held. Nothing at all while the overlay is tucked away, so a paused film is
 * not permanently stamped with a glyph.
 */
@Composable
internal fun PlayerCenterOverlay(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val shown = if (isPlaying) TvPlayerFeedback.Pause else TvPlayerFeedback.Play

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
 * A seek's answer, at the side of the picture the seek moves towards: rewind on the
 * left, forward on the right — the same direction the progress bar fills.
 *
 * The pair is laid out under an LTR direction regardless of the screen's RTL, because
 * "back" in a timeline is the left edge; only the two placements are forced, nothing
 * else about the player changes.
 */
@Composable
internal fun PlayerSeekOverlay(
    feedback: TvPlayerFeedback?,
    modifier: Modifier = Modifier
) {
    val shown = when (feedback) {
        TvPlayerFeedback.Rewind -> TvPlayerFeedback.Rewind
        TvPlayerFeedback.Forward -> TvPlayerFeedback.Forward
        else -> return
    }
    val isRewind = shown == TvPlayerFeedback.Rewind

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier = modifier) {
            Box(
                modifier = Modifier
                    .align(if (isRewind) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(
                        start = if (isRewind) 72.dp else 0.dp,
                        end = if (isRewind) 0.dp else 72.dp
                    )
                    .size(88.dp)
                    .background(color = WahaTvColors.MediaScrimSoft, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = shown.icon,
                    contentDescription = shown.description,
                    tint = WahaTvColors.OnMedia,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag(
                            TvTestTags.playerSeek(if (isRewind) "rewind" else "forward")
                        )
                )
            }
        }
    }
}

/**
 * The progress bar along the bottom edge, drawn like the phone's: rounded track,
 * filled played portion, and a dot at the played edge. The TV has no pointer to
 * drag, so it is not focusable and the D-pad seeks in 10-second steps from the
 * video itself.
 *
 * The position arrives as a lambda, not as a value: reading it in the draw phase
 * means a new frame of playback repaints one node instead of re-composing the
 * whole player twice a second. That is also why the whole bar is one node: the
 * thumb is two more draw calls, not a second composable with its own layout.
 *
 * Forced LTR, exactly like the phone's control bar, so the bar fills from left to
 * right on an otherwise right-to-left screen.
 */
@Composable
private fun PlaybackProgressBar(
    positionMs: () -> Long,
    durationMs: () -> Long,
    modifier: Modifier = Modifier
) {
    val trackColor = WahaTvColors.ProgressTrack
    val barColor = WahaTvColors.Accent

    Box(
        modifier = modifier
            // Tall enough to hold the dot without clipping it; the track itself is thin.
            .height(22.dp)
            .drawBehind {
                val trackHeight = 6.dp.toPx()
                val radius = trackHeight / 2f
                val centreY = size.height / 2f
                val trackTop = centreY - radius

                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(0f, trackTop),
                    size = Size(width = size.width, height = trackHeight),
                    cornerRadius = CornerRadius(radius)
                )

                val total = durationMs()
                if (total > 0L) {
                    val fraction = (positionMs().toFloat() / total).coerceIn(0f, 1f)
                    val playedWidth = size.width * fraction

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(0f, trackTop),
                        size = Size(width = playedWidth, height = trackHeight),
                        cornerRadius = CornerRadius(radius)
                    )

                    // The phone bar's thumb, in the same place: a solid dot riding the
                    // played edge, kept inside the bar at both ends.
                    val thumbRadius = 7.dp.toPx()
                    drawCircle(
                        color = barColor,
                        radius = thumbRadius,
                        center = Offset(
                            x = playedWidth.coerceIn(thumbRadius, size.width - thumbRadius),
                            y = centreY
                        )
                    )
                }
            }
    )
}

/**
 * The times at both ends with the bar between them.
 */
@Composable
internal fun PlayerBottomOverlay(
    positionMs: () -> Long,
    durationMs: () -> Long,
    modifier: Modifier = Modifier
) {
    // Each label is recomposed only when its own text changes — once a second at most.
    val positionText by remember { derivedStateOf { formatPlaybackTime(positionMs()) } }
    val durationText by remember { derivedStateOf { formatPlaybackTime(durationMs()) } }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                    )
                )
                .padding(horizontal = 48.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = positionText,
                color = WahaTvColors.OnMedia,
                style = MaterialTheme.typography.labelLarge.playerSized(18f, 24f)
            )

            PlaybackProgressBar(
                positionMs = positionMs,
                durationMs = durationMs,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TvTestTags.PlayerProgress)
            )

            Text(
                text = durationText,
                color = WahaTvColors.OnMedia,
                style = MaterialTheme.typography.labelLarge.playerSized(18f, 24f)
            )
        }
    }
}

/**
 * The panel the down key opens, in two tidy bands — transport, then the same
 * "شاهد المزيد" rail the phone shows below the video.
 *
 * The transport row leads with the one control a viewer needs (play/pause), the
 * two 10-second buttons around it, and the save toggle at the far edge.
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
    onTogglePlayback: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    suggestions: List<VideoItem>,
    onVideoSelect: (VideoItem) -> Unit,
    initialFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    // The first "شاهد المزيد" card: the transport row hands the remote to it on a down
    // press, whichever control the viewer was on.
    val firstSuggestionRequester = remember { FocusRequester() }
    // Which band the remote is in, so a down press walks into the cards and does not
    // bounce back out of them.
    var inSuggestions by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(WahaTvColors.PanelBackground)
            .testTag(TvTestTags.PlayerPanel)
            // The panel owns the D-pad while it is open, so the arrows are consumed here
            // rather than handed to the framework's focus search: that search reads the
            // space under the transport row as empty and moves the remote to a node the
            // viewer cannot see. Down walks into the cards from *any* control, and a
            // sideways press with nowhere to go leaves the remote exactly where it is.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> {
                        if (!inSuggestions && suggestions.isNotEmpty()) {
                            runCatching { firstSuggestionRequester.requestFocus() }
                        }
                        true
                    }

                    Key.DirectionLeft, Key.DirectionRight -> {
                        focusManager.moveFocus(
                            if (event.key == Key.DirectionLeft) {
                                FocusDirection.Left
                            } else {
                                FocusDirection.Right
                            }
                        )
                        // Consumed even when there was nowhere to move: the press must be
                        // rejected, not handed to whatever sits behind the panel.
                        true
                    }

                    else -> false
                }
            }
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
                modifier = Modifier
                    .fillMaxWidth()
                    // The remote is in the transport band until a card takes it.
                    .onFocusChanged { if (it.hasFocus) inSuggestions = false },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PanelActionButton(
                        icon = TvIcons.Replay10,
                        description = "إرجاع ١٠ ثوانٍ",
                        onClick = onSeekBackward,
                        modifier = Modifier.testTag(TvTestTags.playerControl("rewind"))
                    )
                    PanelActionButton(
                        icon = if (isPlaying) TvIcons.Pause else TvIcons.PlayArrow,
                        description = if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                        onClick = onTogglePlayback,
                        primary = true,
                        focusRequester = initialFocusRequester,
                        modifier = Modifier.testTag(TvTestTags.playerControl("play_pause"))
                    )
                    PanelActionButton(
                        icon = TvIcons.Forward10,
                        description = "تقديم ١٠ ثوانٍ",
                        onClick = onSeekForward,
                        modifier = Modifier.testTag(TvTestTags.playerControl("forward"))
                    )
                }

                SavePill(videoId = videoId)
            }

            if (suggestions.isNotEmpty()) {
                Text(
                    text = "شاهد المزيد",
                    color = WahaTvColors.TextWarm,
                    style = MaterialTheme.typography.titleMedium.playerSized(20f, 27f)
                )
                // Read the saved set once for the row, so a save toggles one badge rather
                // than recomposing every suggestion card.
                val savedIds = SavedVideosStore.savedIds
                LazyRow(
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) inSuggestions = true },
                    horizontalArrangement = Arrangement.spacedBy(WahaTvDimens.CardSpacing)
                ) {
                    itemsIndexed(suggestions, key = { _, suggestion -> suggestion.id }) { index, suggestion ->
                        TvVideoCard(
                            video = suggestion,
                            onClick = { onVideoSelect(suggestion) },
                            modifier = if (index == 0) {
                                Modifier.focusRequester(firstSuggestionRequester)
                            } else {
                                Modifier
                            },
                            width = 240.dp,
                            isSaved = suggestion.id in savedIds
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
 * A panel pill: the save toggle. One shape, one D-pad path.
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
            style = MaterialTheme.typography.labelLarge.playerSized(18f, 24f)
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
    val isSaved = SavedVideosStore.isSaved(videoId)

    PanelPill(
        label = if (isSaved) "محفوظ" else "حفظ",
        selected = isSaved,
        onClick = { SavedVideosStore.toggle(videoId) },
        leadingIcon = if (isSaved) TvIcons.Bookmark else TvIcons.BookmarkBorder,
        modifier = Modifier.testTag(TvTestTags.playerControl("save"))
    )
}
