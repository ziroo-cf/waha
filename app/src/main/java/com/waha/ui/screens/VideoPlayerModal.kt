package com.waha.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.waha.data.SavedVideosStore
import com.waha.ui.theme.WahaDarkBg
import com.waha.ui.theme.WahaGold
import com.waha.ui.theme.WahaLine
import com.waha.ui.theme.WahaTextMuted
import com.waha.ui.theme.WahaTextWarm

private const val FULLSCREEN_VIEW_TAG = "youtube_fullscreen_view"

/** Matches titles that end with an episode marker, e.g. "Tarzan Episode 2" or "طارزان الحلقة ٣". */
private val EPISODE_TITLE_REGEX = Regex(
    """^(.*?)[\s\-–—_:]*\s*(?:episode|ep\.?|part|الحلقة|حلقه|الجزء)\s*([0-9\u0660-\u0669\u06F0-\u06F9]+)\s*$""",
    RegexOption.IGNORE_CASE
)

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
 * Splits a title into its series base and episode number when it carries an
 * episode marker ("Tarzan Episode 2" -> ("tarzan", 2)). Returns null for
 * titles with no episode number.
 */
private fun parseSeriesEpisode(title: String): Pair<String, Int>? {
    val match = EPISODE_TITLE_REGEX.matchEntire(title.trim()) ?: return null
    val base = match.groupValues[1].trim().trimEnd('-', '–', '—', ':', '_', ' ').lowercase()
    if (base.isEmpty()) return null
    val episode = normalizeDigits(match.groupValues[2]).toIntOrNull() ?: return null
    return base to episode
}

private fun buildSuggestions(
    current: VideoItem,
    allVideos: List<VideoItem>,
    count: Int = 20
): List<VideoItem> {
    val pool = allVideos.filter { it.id != current.id }

    // When the title belongs to a numbered series, pin the immediate next
    // episode (same series base, episode + 1) as the very first suggestion.
    val nextEpisode = parseSeriesEpisode(current.title)?.let { (base, episode) ->
        pool.firstOrNull { candidate ->
            parseSeriesEpisode(candidate.title)
                ?.let { it.first == base && it.second == episode + 1 } == true
        }
    }

    val usedIds = mutableSetOf<String>()
    val ordered = mutableListOf<VideoItem>()

    if (nextEpisode != null) {
        ordered += nextEpisode
        usedIds += nextEpisode.id
    }

    val sameCategoryFirst = pool
        .filter { it.id !in usedIds }
        .filter { it.categoryKey != null && it.categoryKey == current.categoryKey }
        .shuffled()
        .take(2)

    ordered += sameCategoryFirst
    usedIds += sameCategoryFirst.map { it.id }

    val rest = pool
        .filter { it.id !in usedIds }
        .shuffled()
        .take((count - ordered.size).coerceAtLeast(0))

    return ordered + rest
}

private fun Activity.enterImmersiveFullscreen() {
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    WindowCompat.getInsetsController(window, window.decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.systemBars())
    }
}

private fun Activity.exitImmersiveFullscreen() {
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
}

@Composable
fun VideoPlayerModal(
    video: VideoItem,
    allVideos: List<VideoItem>,
    onVideoSelect: (VideoItem) -> Unit,
    onClose: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = LocalContext.current as? Activity

    val isSaved by SavedVideosStore.rememberSavedState(video.id)
    val suggestions = remember(video.id, allVideos) { buildSuggestions(video, allVideos) }
    val listState = rememberLazyGridState()
    val columns = rememberWahaWindowInfo().gridColumns

    LaunchedEffect(video.id) { listState.scrollToItem(0) }

    val playerRef = remember { mutableStateOf<YouTubePlayer?>(null) }
    var lastLoadedId by remember { mutableStateOf<String?>(null) }

    // Load or switch videos in a side effect (never during composition).
    SideEffect {
        val player = playerRef.value ?: return@SideEffect
        if (lastLoadedId != video.youtubeId) {
            player.loadVideo(video.youtubeId, 0f)
            lastLoadedId = video.youtubeId
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(WahaDarkBg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {}
        ) {
            // Full-bleed, edge-to-edge player exactly like the home feed cards.
            AndroidView(
                factory = { ctx ->
                    YouTubePlayerView(ctx).apply {
                        lifecycleOwner.lifecycle.addObserver(this)
                        enableAutomaticInitialization = false

                        val options = IFramePlayerOptions.Builder(ctx)
                            .controls(1)
                            .fullscreen(1)
                            .build()

                        addFullscreenListener(object : FullscreenListener {
                            override fun onEnterFullscreen(fullscreenView: View, exitFullscreen: () -> Unit) {
                                activity?.enterImmersiveFullscreen()
                                fullscreenView.tag = FULLSCREEN_VIEW_TAG
                                (activity?.window?.decorView as? ViewGroup)?.addView(
                                    fullscreenView,
                                    ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                )
                            }

                            override fun onExitFullscreen() {
                                activity?.exitImmersiveFullscreen()
                                (activity?.window?.decorView as? ViewGroup)?.let { root ->
                                    for (i in root.childCount - 1 downTo 0) {
                                        val child = root.getChildAt(i)
                                        if (child.tag == FULLSCREEN_VIEW_TAG) {
                                            root.removeView(child)
                                        }
                                    }
                                }
                            }
                        })

                        initialize(object : AbstractYouTubePlayerListener() {
                            override fun onReady(youTubePlayer: YouTubePlayer) {
                                playerRef.value = youTubePlayer
                                youTubePlayer.loadVideo(video.youtubeId, 0f)
                                lastLoadedId = video.youtubeId
                            }
                        }, options)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )

            // Suggestions reuse the home-screen card so their images span the full width.
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
                // The title/return bar is the first row of the grid, so it scrolls
                // up together with the videos instead of staying pinned.
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
                                    tint = if (isSaved) WahaGold else WahaTextMuted
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
