package com.waha.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import com.waha.data.WatchHistoryStore
import com.waha.domain.VideoItem
import com.waha.domain.continueWatching
import com.waha.tv.TvTestTags
import com.waha.tv.ui.theme.WahaTvColors

/** Title of the Continue Watching rail, shown above the category rows. */
const val CONTINUE_WATCHING_RAIL_TITLE = "أكمل المشاهدة"

/** Stable list key for that rail (a real category could never be spelled this way). */
private const val CONTINUE_WATCHING_RAIL_KEY = "continue_watching"

/** Stable list key for the reload button at the end of the shelf. */
private const val RELOAD_ITEM_KEY = "reload_library"

/** What the reload button says, below the shelf and above a failed load. */
const val RELOAD_LABEL = "إعادة تحميل المحتوى"

/**
 * The TV home screen: the phone build's library as a stack of horizontal rows —
 * "الكل" first, then one row per category read from the library itself.
 *
 * There is no banner and no header above them. A living-room viewer already chose
 * what to watch by opening the app, so the screen opens straight onto the shelf —
 * and on a 720p TV, whose whole height is about 540dp, the rows the viewer came
 * for would otherwise start below the fold. There is no category filter bar
 * either: "الكل" already shows the whole library, and every category is its own
 * row, so nothing has to be filtered to be reached.
 *
 * The shelf ends with one reload button — a remote's only way to ask for a fresh
 * library without leaving the app — and the same button is what a failed load
 * offers, so the viewer never has to look for two different ways back.
 */
@Composable
fun TvHomeScreen(
    uiState: TvUiState,
    onVideoClick: (VideoItem) -> Unit,
    onRetry: () -> Unit
) {
    when (uiState) {
        is TvUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WahaTvColors.Accent)
            }
        }

        is TvUiState.Error -> TvErrorState(onRetry = onRetry)

        is TvUiState.Ready -> if (uiState.videos.isEmpty()) {
            TvMessage("لا توجد فيديوهات منشورة بعد")
        } else {
            TvHomeRails(
                videos = uiState.videos,
                onVideoClick = onVideoClick,
                onReload = onRetry
            )
        }
    }
}

@Composable
private fun TvHomeRails(
    videos: List<VideoItem>,
    onVideoClick: (VideoItem) -> Unit,
    onReload: () -> Unit
) {
    val listState = rememberLazyListState()

    val rails = remember(videos) { TvRails.build(videos) }

    // "أكمل المشاهدة": the videos the viewer paused part-way, newest first.
    // The filter itself lives in :core so phone and TV agree on what counts as
    // "unfinished"; here it is just joined back to the library's metadata.
    val continueWatchingEntries = remember(WatchHistoryStore.snapshot) {
        WatchHistoryStore.continueWatching()
    }
    val progressById = remember(continueWatchingEntries) {
        continueWatchingEntries.associate { it.videoId to it.progressFraction }
    }
    val continueWatching = remember(videos, continueWatchingEntries) {
        videos.continueWatching(continueWatchingEntries)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag(TvTestTags.HomeList),
        contentPadding = PaddingValues(
            top = WahaTvDimens.SectionSpacing / 2,
            bottom = WahaTvDimens.ScreenPadding
        )
    ) {
        // The viewer's own progress comes first, above the categories.
        if (continueWatching.isNotEmpty()) {
            item(key = CONTINUE_WATCHING_RAIL_KEY) {
                TvVideoRail(
                    railKey = CONTINUE_WATCHING_RAIL_KEY,
                    title = CONTINUE_WATCHING_RAIL_TITLE,
                    videos = continueWatching,
                    onVideoClick = onVideoClick,
                    progress = { video -> progressById[video.id] }
                )
            }
        }

        items(rails, key = { it.key }) { rail ->
            TvVideoRail(
                railKey = rail.key,
                title = rail.title,
                videos = rail.videos,
                onVideoClick = onVideoClick
            )
        }

        // Below every card: the only way to ask for a fresh library from the shelf.
        item(key = RELOAD_ITEM_KEY) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = WahaTvDimens.SectionSpacing),
                contentAlignment = Alignment.Center
            ) {
                TvReloadButton(onClick = onReload)
            }
        }
    }
}

/**
 * The reload action, in one place: below the shelf and in the error state, so a
 * viewer who has just seen a failure presses the same thing they would press to
 * refresh a working library — activated with the remote's centre key like every
 * other control on this screen.
 */
@Composable
private fun TvReloadButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        scale = ButtonDefaults.scale(focusedScale = WahaTvDimens.FocusScale),
        modifier = modifier.testTag(TvTestTags.ReloadButton)
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = RELOAD_LABEL, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun TvErrorState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "تعذّر تحميل المحتوى",
            color = WahaTvColors.TextWarm,
            style = MaterialTheme.typography.titleLarge
        )
        TvReloadButton(
            onClick = onRetry,
            modifier = Modifier.padding(top = 24.dp)
        )
    }
}
