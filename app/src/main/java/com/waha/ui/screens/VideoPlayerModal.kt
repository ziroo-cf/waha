package com.waha.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.waha.data.PipedApi
import com.waha.data.PipedPlaybackSource
import com.waha.data.SavedVideosStore
import com.waha.data.WatchHistoryStore
import com.waha.data.YoutubeExtractor
import com.waha.ui.theme.WahaCardBg
import com.waha.ui.theme.WahaCardBg2
import com.waha.ui.theme.WahaDarkBg
import com.waha.ui.theme.WahaGold
import com.waha.ui.theme.WahaLine
import com.waha.ui.theme.WahaTeal
import com.waha.ui.theme.WahaTealBright
import com.waha.ui.theme.WahaTextMuted
import com.waha.ui.theme.WahaTextWarm
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import androidx.media3.session.MediaSession

/** How long to wait for on-device extraction before trying Piped. */
private const val EXTRACTION_TIMEOUT_MS = 18_000L

/** How long to wait for Piped before falling back to the embedded player. */
private const val PIPED_RESOLVE_TIMEOUT_MS = 6_000L

/** Finds an episode number anywhere in a title: "Episode 2", "الحلقة ٣", "Ep. 7". */
private val EPISODE_NUMBER_REGEX = Regex(
    """(?:episode|ep\.?|part|الحلقة|حلقه|حلقة|الجزء)\s*([0-9\u0660-\u0669\u06F0-\u06F9]+)""",
    RegexOption.IGNORE_CASE
)

/** Words that describe format rather than the series itself. */
private val SERIES_KEYWORDS = setOf(
    "episode", "ep", "part", "season", "الحلقة", "حلقه", "حلقة", "الجزء", "جزء", "الموسم", "موسم"
)

/** Filler words that must never link two titles together. */
private val SERIES_STOPWORDS = setOf(
    "the", "a", "an", "of", "in", "on", "and", "to", "for", "with", "at", "by", "from",
    "في", "من", "على", "و", "مع", "إلى"
)

private val NUMBER_TOKEN_REGEX = Regex("^[0-9\u0660-\u0669\u06F0-\u06F9]+$")

private val YOUTUBE_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")

/** Recovers a YouTube id from a thumbnail URL (`/vi/<id>/...`) or a `v=` query parameter. */
private val YOUTUBE_ID_IN_URL_REGEX = Regex("(?:/vi/|[?&]v=)([A-Za-z0-9_-]{11})")

/** Playback speeds offered in the player's settings sheet. */
private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** Video heights offered in the player's settings sheet; null means "best available". */
private val QUALITY_OPTIONS = listOf<Int?>(null, 360, 480, 720, 1080)

/** Arabic label for a playback-speed option. */
private fun speedLabel(speed: Float): String = when (speed) {
    0.5f -> "0.5×"
    0.75f -> "0.75×"
    1f -> "عادي"
    1.25f -> "1.25×"
    1.5f -> "1.5×"
    2f -> "2×"
    else -> "${speed}×"
}

/** Arabic label for a video-quality option; null means "auto". */
private fun qualityLabel(height: Int?): String = if (height == null) "تلقائي" else "${height}p"

/** Formats milliseconds as `m:ss` (or `h:mm:ss` past an hour). */
private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds / 60) % 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

/** How the current video is being played. */
private sealed interface PlaybackState {
    data object Loading : PlaybackState

    /** A direct stream URL (on-device extraction or Piped), played natively by ExoPlayer. */
    data object Native : PlaybackState

    /** No playable source could be resolved for the video. */
    data class Error(val message: String, val videoId: String?) : PlaybackState
}

/** Rewrites Arabic-Indic digits (٠-٩ / ۰-۹) to ASCII so episode numbers parse uniformly. */
private fun normalizeDigits(text: String): String = buildString(text.length) {
    for (ch in text) {
        append(
            when (ch) {
                in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')
                in '\u06F0'..'\u06F9' -> '0' + (ch - '\u06F0')
                else -> ch
            }
        )
    }
}

/**
 * The episode number carried by a title, wherever it appears
 * ("Cartoon Blue Episode 2 Season 4 | The Garden" -> 2). Null when the title
 * has no numbered episode marker.
 */
internal fun episodeNumber(title: String): Int? =
    EPISODE_NUMBER_REGEX.find(title.trim())?.groupValues?.get(1)
        ?.let { normalizeDigits(it).toIntOrNull() }

/**
 * The series-identity words of a title: its significant tokens after dropping
 * separators, episode/season markers, bare numbers, and filler stopwords. The
 * name may sit anywhere in the title — start, middle, or end — so
 * "Watch Blue Safari | Cartoon Blue Episode 2" still yields {cartoon, blue,
 * safari, watch}. Empty when nothing significant remains.
 */
internal fun seriesTokens(title: String): Set<String> =
    title.lowercase()
        .split(Regex("""[\s:|\-–—_.,!?()\[\]"]+"""))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .filterNot { it in SERIES_KEYWORDS }
        .filterNot { it in SERIES_STOPWORDS }
        .filterNot { NUMBER_TOKEN_REGEX.matches(it) }
        .toSet()

/** Whether [candidateTitle] belongs to the same series as [currentTokens]. */
internal fun sharesSeries(currentTokens: Set<String>, candidateTitle: String): Boolean {
    if (currentTokens.isEmpty()) return false
    val candidateTokens = seriesTokens(candidateTitle)
    return candidateTokens.any { it in currentTokens }
}

/**
 * Weights for the suggestion scorer. Series identity dominates, the immediate
 * next episode tops everything, and shared categories break ties.
 */
private const val NEXT_EPISODE_SCORE = 100
private const val SHARED_SERIES_TOKEN_SCORE = 10
private const val SHARED_CATEGORY_SCORE = 5
private const val SHARED_SERIES_CAP = 3

/** After this many picks from one category, force a pick from another. */
private const val MAX_PER_CATEGORY_RUN = 2

/**
 * Reorders [ranked] (best first) so no single category hogs the list: once
 * [MAX_PER_CATEGORY_RUN] consecutive picks share a category, the next slot
 * must go to a different one (or to a category-less video). Videos whose
 * category is unknown never count toward a run.
 */
internal fun diversifyByCategory(ranked: List<VideoItem>): List<VideoItem> {
    val result = mutableListOf<VideoItem>()
    val remaining = ArrayDeque(ranked)
    while (remaining.isNotEmpty()) {
        val runCategory = result.lastOrNull()?.categoryKey
        var runLength = 0
        for (i in result.indices.reversed()) {
            if (result[i].categoryKey != null && result[i].categoryKey == runCategory) runLength++ else break
        }
        val blocked = runCategory != null && runLength >= MAX_PER_CATEGORY_RUN

        var pickIndex = if (blocked) {
            remaining.indexOfFirst { it.categoryKey != runCategory }
        } else {
            0
        }
        if (pickIndex == -1) pickIndex = 0 // everything left shares the category
        result += remaining.removeAt(pickIndex)
    }
    return result
}

/**
 * Scores one candidate against the current video. Higher is better.
 *
 * - Being the exact next episode of the same series dwarfs everything else.
 * - Each shared series word adds [SHARED_SERIES_TOKEN_SCORE], so "Cartoon Blue
 *   Episode 3..." (2 shared words) outranks a video sharing just "Blue".
 * - The shared category is a small boost, not a tier of its own.
 * - Deterministic given the same inputs: no shuffling, so the ordering is
 *   stable and testable, and the same pair of videos can never circle.
 */
internal fun suggestionScore(current: VideoItem, candidate: VideoItem): Int {
    var score = 0

    val currentTokens = seriesTokens(current.title)
    val sharedTokens = seriesTokens(candidate.title).count { it in currentTokens }

    val currentEpisode = episodeNumber(current.title)
    val candidateEpisode = episodeNumber(candidate.title)
    if (currentEpisode != null && candidateEpisode == currentEpisode + 1 && sharedTokens > 0) {
        score += NEXT_EPISODE_SCORE
    }

    score += sharedTokens * SHARED_SERIES_TOKEN_SCORE

    if (current.categoryKey != null && current.categoryKey == candidate.categoryKey) {
        score += SHARED_CATEGORY_SCORE
    }

    return score
}

internal fun buildSuggestions(
    current: VideoItem,
    allVideos: List<VideoItem>,
    watchedIds: Set<String> = emptySet(),
    count: Int = 20
): List<VideoItem> {
    // Skip the current video and everything already watched, so two videos can
    // never pin each other as suggestions (A -> B -> A -> ... forever).
    val pool = allVideos.filter { it.id != current.id && it.id !in watchedIds }

    // Never leave the suggestions empty when the history plus the current video
    // covers the whole library: fall back to the unfiltered pool.
    val effectivePool = if (pool.isEmpty()) {
        allVideos.filter { it.id != current.id }
    } else {
        pool
    }

    // Score every candidate once, then keep the best. The score already encodes
    // episode affinity, series identity, and category, so no hand-ordered tiers.
    val ranked = effectivePool
        .map { it to suggestionScore(current, it) }
        .sortedWith(compareByDescending<Pair<VideoItem, Int>> { (_, score) -> score }
            .thenBy { (candidate, _) -> candidate.id }) // stable tie-break by id
        .map { (candidate, _) -> candidate }

    // Keep the strongest series matches at the top but cap them so the list
    // still breathes with category and other picks.
    val currentTokens = seriesTokens(current.title)
    var seriesShown = 0
    val limited = mutableListOf<VideoItem>()
    for (candidate in ranked) {
        val sharesSeriesTokens = currentTokens.isNotEmpty() &&
                seriesTokens(candidate.title).any { it in currentTokens }
        if (sharesSeriesTokens) {
            if (seriesShown >= SHARED_SERIES_CAP) continue
            seriesShown++
        }
        limited += candidate
    }

    // Spread categories so one topic never fills the whole rail.
    return diversifyByCategory(limited).take(count)
}

/**
 * Resolves the YouTube id to hand to Piped. Uses the explicit id when it looks
 * like a YouTube id, otherwise recovers it from the thumbnail URL.
 */
private fun extractYouTubeId(video: VideoItem): String? {
    video.youtubeId.takeIf { YOUTUBE_ID_REGEX.matches(it) }?.let { return it }
    return YOUTUBE_ID_IN_URL_REGEX.find(video.thumbnailUrl.orEmpty())?.groupValues?.get(1)
}

private fun Activity.enterImmersiveFullscreen() {
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    WindowCompat.getInsetsController(window, window.decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.systemBars())
    }
}

private fun Activity.exitImmersiveFullscreen() {
    // Restore the system's own orientation decision (auto-rotate) instead of
    // locking to portrait, so leaving the player never strands the app sideways.
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
}

private fun PipedPlaybackSource.toMediaSource(context: Context): MediaSource {
    val dataSourceFactory = DefaultDataSource.Factory(context)
    return when (this) {
        is PipedPlaybackSource.Progressive ->
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(url))

        is PipedPlaybackSource.Merged -> MergingMediaSource(
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(videoUrl)),
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(audioUrl))
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerModal(
    video: VideoItem,
    allVideos: List<VideoItem>,
    onVideoSelect: (VideoItem) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val isSaved by SavedVideosStore.rememberSavedState(video.id)
    val watchedIds = WatchHistoryStore.recentIds.toSet()
    val suggestions = remember(video.id, allVideos, watchedIds) {
        buildSuggestions(video, allVideos, watchedIds)
    }
    val listState = rememberLazyGridState()
    val columns = rememberWahaWindowInfo().gridColumns

    var isFullscreen by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isScrubbing by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var qualityHeight by remember { mutableStateOf<Int?>(null) }
    var playback by remember(video.id) { mutableStateOf<PlaybackState>(PlaybackState.Loading) }
    var retryKey by remember { mutableStateOf(0) }

    // New player states: buffering, ended, double-tap feedback, resume position.
    var isBuffering by remember { mutableStateOf(false) }
    var isEnded by remember { mutableStateOf(false) }
    var seekFeedback by remember { mutableStateOf<Int?>(null) } // -1 = back, +1 = forward
    var resumeFrom by remember { mutableLongStateOf(0L) }

    val player = remember(context) { ExoPlayer.Builder(context).build() }
    val currentVideo by rememberUpdatedState(video)

    DisposableEffect(context, player) {
        val mediaSession = MediaSession.Builder(context, player).build()

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                isEnded = state == Player.STATE_ENDED
            }

            override fun onPlayerError(error: PlaybackException) {
                playback = PlaybackState.Error("تعذّر تشغيل الفيديو", extractYouTubeId(currentVideo))
            }
        }
        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            activity?.exitImmersiveFullscreen()
            mediaSession.release()
            player.stop()
            player.clearMediaItems()
            player.release()
        }
    }

    LaunchedEffect(video.id) {
        resumeFrom = 0L
        listState.scrollToItem(0)
        showSettings = false
        controlsVisible = true
    }

    // Hide the double-tap seek icon shortly after it appears.
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(600)
            seekFeedback = null
        }
    }

    // Resolve a playable stream whenever the video, retry, or quality changes.
    LaunchedEffect(video.id, retryKey, qualityHeight) {
        playback = PlaybackState.Loading
        player.stop()
        player.clearMediaItems()

        val youtubeId = extractYouTubeId(video)
        if (youtubeId == null) {
            playback = PlaybackState.Error("تعذّر تحديد معرّف الفيديو", null)
            return@LaunchedEffect
        }

        val source = withTimeoutOrNull(EXTRACTION_TIMEOUT_MS) {
            YoutubeExtractor.resolvePlaybackSource(youtubeId, maxHeight = qualityHeight)
        } ?: withTimeoutOrNull(PIPED_RESOLVE_TIMEOUT_MS) {
            PipedApi.resolvePlaybackSource(youtubeId)
        }
        if (source != null) {
            player.setMediaSource(source.toMediaSource(context), resumeFrom)
            resumeFrom = 0L
            player.prepare()
            player.playWhenReady = true
            playback = PlaybackState.Native
        } else {
            playback = PlaybackState.Error("تعذّر تشغيل الفيديو", youtubeId)
        }
    }

    // Keep the player at the chosen speed.
    LaunchedEffect(player, playbackSpeed) { player.setPlaybackSpeed(playbackSpeed) }

    LaunchedEffect(isFullscreen) {
        if (isFullscreen) activity?.enterImmersiveFullscreen() else activity?.exitImmersiveFullscreen()
    }

    LaunchedEffect(controlsVisible, isScrubbing, isPlaying) {
        if (controlsVisible && isPlaying && !isScrubbing) {
            delay(4_000)
            controlsVisible = false
        }
    }

    LaunchedEffect(video.id) { WatchHistoryStore.record(video.id) }

    BackHandler(enabled = isFullscreen) { isFullscreen = false }

    Box(modifier = Modifier.fillMaxSize().background(WahaDarkBg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isFullscreen) Modifier else Modifier.statusBarsPadding())
        ) {
            Box(
                modifier = if (isFullscreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                }
            ) {
                if (playback is PlaybackState.Native) {
                    PlayerSurface(
                        player = player,
                        modifier = Modifier.fillMaxSize()
                    )

                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(Modifier.fillMaxSize()) {
                            listOf(-1, 1).forEach { dir ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onTap = { controlsVisible = !controlsVisible },
                                                onDoubleTap = {
                                                    val target = player.currentPosition + dir * 10_000L
                                                    player.seekTo(
                                                        target.coerceIn(0L, player.duration.coerceAtLeast(0L))
                                                    )
                                                    seekFeedback = dir
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = seekFeedback == dir,
                                        enter = fadeIn(),
                                        exit = fadeOut()
                                    ) {
                                        Icon(
                                            imageVector = if (dir < 0) Icons.Filled.Replay10 else Icons.Filled.Forward10,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                                .padding(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    PlayerControls(
                        player = player,
                        isFullscreen = isFullscreen,
                        isPlaying = isPlaying,
                        visible = controlsVisible,
                        onToggleFullscreen = { isFullscreen = !isFullscreen },
                        onOpenSettings = { showSettings = true },
                        onScrubbingChange = { isScrubbing = it },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )

                    if (isBuffering) {
                        CircularProgressIndicator(
                            color = WahaTealBright,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = controlsVisible && !isBuffering,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        IconButton(
                            onClick = {
                                when {
                                    isPlaying -> player.pause()
                                    else -> {
                                        if (isEnded) player.seekTo(0)
                                        player.play()
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        ) {
                            Icon(
                                imageVector = when {
                                    isEnded -> Icons.Filled.Replay
                                    isPlaying -> Icons.Filled.Pause
                                    else -> Icons.Filled.PlayArrow
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    if (isFullscreen) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = controlsVisible,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.align(Alignment.TopStart)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                                        )
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { isFullscreen = false }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "رجوع",
                                        tint = Color.White
                                    )
                                }
                                Text(
                                    video.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                when (val state = playback) {
                    is PlaybackState.Loading -> CircularProgressIndicator(
                        color = WahaTeal,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    is PlaybackState.Error -> Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(state.message, color = WahaTextWarm)
                        Row {
                            TextButton(onClick = { retryKey++ }) {
                                Text("إعادة المحاولة", color = WahaTealBright)
                            }
                        }
                    }

                    else -> Unit
                }
            }

            if (!isFullscreen) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (columns > 1) 8.dp else 0.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = WahaTextWarm)
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = video.title,
                                        color = WahaTextWarm,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (video.meta.isNotBlank()) {
                                        Text(
                                            text = video.meta,
                                            color = WahaTextMuted,
                                            fontSize = 13.sp,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { SavedVideosStore.toggle(video.id) },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = if (isSaved) "إزالة من المحفوظات" else "إضافة للمحفوظات",
                                        tint = if (isSaved) WahaTeal else WahaTextMuted
                                    )
                                }
                            }

                            HorizontalDivider(color = WahaLine, thickness = 1.dp)
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "شاهد المزيد",
                            color = WahaTextWarm,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(suggestions, key = { it.id }) { suggestion ->
                        VideoCard(video = suggestion, onClick = { onVideoSelect(suggestion) })
                    }
                }
            }
        }
    }

    if (showSettings) {
        PlaybackSettingsSheet(
            playbackSpeed = playbackSpeed,
            qualityHeight = qualityHeight,
            onSpeedSelect = { playbackSpeed = it },
            onQualitySelect = {
                if (it != qualityHeight) resumeFrom = player.currentPosition
                qualityHeight = it
                showSettings = false
            },
            onDismiss = { showSettings = false }
        )
    }
}

/** Bottom sheet exposing playback speed and preferred video quality. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackSettingsSheet(
    playbackSpeed: Float,
    qualityHeight: Int?,
    onSpeedSelect: (Float) -> Unit,
    onQualitySelect: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = WahaCardBg,
        contentColor = WahaTextWarm
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "إعدادات التشغيل",
                color = WahaTextWarm,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
            Text("سرعة التشغيل", color = WahaTextMuted, fontSize = 13.sp)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PLAYBACK_SPEEDS.forEach { speed ->
                    FilterChip(
                        selected = playbackSpeed == speed,
                        onClick = { onSpeedSelect(speed) },
                        label = { Text(speedLabel(speed)) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = WahaCardBg2,
                            labelColor = WahaTextWarm,
                            selectedContainerColor = WahaTeal,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
            Text("الجودة", color = WahaTextMuted, fontSize = 13.sp)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QUALITY_OPTIONS.forEach { height ->
                    FilterChip(
                        selected = qualityHeight == height,
                        onClick = { onQualitySelect(height) },
                        label = { Text(qualityLabel(height)) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = WahaCardBg2,
                            labelColor = WahaTextWarm,
                            selectedContainerColor = WahaTeal,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }
    }
}

/** A compact, colourful control bar laid over the video, YouTube-style. */
@Composable
private fun PlayerControls(
    player: ExoPlayer,
    isFullscreen: Boolean,
    isPlaying: Boolean,
    visible: Boolean,
    onToggleFullscreen: () -> Unit,
    onOpenSettings: () -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var scrubPosition by remember { mutableStateOf<Float?>(null) }

    // Poll the player so the seek bar advances without a recomposition storm.
    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            val reported = player.duration
            durationMs = if (reported > 0L) reported else 0L
            delay(250)
        }
    }

    val hasDuration = durationMs > 0L
    val sliderValue = (scrubPosition ?: positionMs.toFloat())
        .coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f))

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                    )
                )
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(formatTime(sliderValue.toLong()), color = Color.White, fontSize = 11.sp)

            Slider(
                value = sliderValue,
                onValueChange = {
                    scrubPosition = it
                    onScrubbingChange(true)
                },
                onValueChangeFinished = {
                    scrubPosition?.let { player.seekTo(it.toLong()) }
                    scrubPosition = null
                    onScrubbingChange(false)
                },
                valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                enabled = hasDuration,
                colors = SliderDefaults.colors(
                    thumbColor = WahaTealBright,
                    activeTrackColor = WahaTealBright,
                    inactiveTrackColor = Color.White.copy(alpha = 0.30f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
            )

            Text(formatTime(durationMs), color = Color.White, fontSize = 11.sp)

            IconButton(onClick = onOpenSettings, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "إعدادات التشغيل",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = if (isFullscreen) "إنهاء ملء الشاشة" else "ملء الشاشة",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }}
    }
}

/** Hosts the native ExoPlayer surface. */
@Composable
private fun PlayerSurface(player: ExoPlayer, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                // The screen draws its own colourful controls over the surface.
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                setBackgroundColor(android.graphics.Color.BLACK)
                setShutterBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { view ->
            view.player = player
            view.keepScreenOn = true
        },
        modifier = modifier.background(Color.Black)
    )
}