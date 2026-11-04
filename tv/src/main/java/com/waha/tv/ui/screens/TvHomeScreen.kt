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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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

/** What it says while the reload it started is still running. */
private const val REFRESHING_LABEL = "جارٍ التحديث…"

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
    onRetry: () -> Unit,
    /** Bumped when the viewer opens this page; the remote returns to the shelf. */
    focusAllTicks: Int = 0
) {
    // Where the remote belongs when the page opens, and after every reshuffle: the first
    // card of "الكل", not the first card of whichever row happens to sit on top (the
    // Continue Watching rail, whose content the viewer did not come here to browse).
    val shelf = uiState as? TvUiState.Ready
    val firstAllCardId = shelf?.rails
        ?.firstOrNull { it.key == TvRails.ALL_RAIL_KEY }
        ?.videos
        ?.firstOrNull()
        ?.id
    // Rebuilt with the card's identity: a requester belongs to one node, and after a
    // reshuffle the node it was attached to is no longer the row's first card.
    val firstAllCardRequester = remember(firstAllCardId) { FocusRequester() }

    LaunchedEffect(shelf?.order, focusAllTicks, firstAllCardId) {
        if (firstAllCardId == null) return@LaunchedEffect
        // A frame for the shelf (or the shelf rebuilt by a reshuffle) to be placed. The
        // shell also asks for the remote on start-up, so this is the request that must
        // land last.
        withFrameNanos { }
        runCatching { firstAllCardRequester.requestFocus() }
    }

    when (uiState) {
        is TvUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WahaTvColors.Accent)
            }
        }

        is TvUiState.Error -> TvErrorState(onRetry = onRetry)

        // A finished sync that left the cache empty. It has its own message and its own
        // reload press: the library really is empty, and nothing is going to arrive on
        // its own.
        is TvUiState.Empty -> TvEmptyLibrary(onRetry = onRetry)

        is TvUiState.Ready -> if (uiState.videos.isEmpty()) {
            TvEmptyLibrary(onRetry = onRetry)
        } else {
            TvHomeRails(
                videos = uiState.videos,
                rails = uiState.rails,
                order = uiState.order,
                refreshing = uiState.isRefreshing,
                firstCardRequester = firstAllCardRequester,
                onVideoClick = onVideoClick,
                onReload = onRetry
            )
        }
    }
}

@Composable
private fun TvHomeRails(
    videos: List<VideoItem>,
    /** The rows, already built off the main thread by the ViewModel. */
    rails: List<TvRail>,
    /** Which shuffle [rails] came from; a new one rebuilds the shelf below. */
    order: Long,
    /** True while an explicit reload is still running. */
    refreshing: Boolean,
    /** Placed on the first card of "الكل", the row the page opens on. */
    firstCardRequester: FocusRequester?,
    onVideoClick: (VideoItem) -> Unit,
    onReload: () -> Unit
) {
    // A reshuffle rebuilds the shelf. A list that survived it would carry the old scroll
    // position — and, because lazy lists restore focus to the item that had it, the old
    // focused card — into an order where that card is no longer first. Rebuilding is also
    // what makes the page's focus request below authoritative.
    key(order) {
        val listState = rememberLazyListState()

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
            // This is the one rail whose data changes while the viewer is watching — pausing
            // writes a position — and the shelf stays composed under the player. Reading the
            // history inside this item's own scope keeps that write from recomposing the
            // shelf; an empty history simply draws no rail.
            item(key = CONTINUE_WATCHING_RAIL_KEY) {
                val entries = WatchHistoryStore.continueWatching()
                val progressById = remember(entries) {
                    entries.associate { it.videoId to it.progressFraction }
                }
                val rows = remember(videos, entries) { videos.continueWatching(entries) }

                TvVideoRail(
                    railKey = CONTINUE_WATCHING_RAIL_KEY,
                    title = CONTINUE_WATCHING_RAIL_TITLE,
                    videos = rows,
                    onVideoClick = onVideoClick,
                    progress = { video -> progressById[video.id] }
                )
            }

            items(rails, key = { it.key }) { rail ->
                TvVideoRail(
                    railKey = rail.key,
                    title = rail.title,
                    videos = rail.videos,
                    onVideoClick = onVideoClick,
                    firstCardRequester = if (rail.key == TvRails.ALL_RAIL_KEY) {
                        firstCardRequester
                    } else {
                        null
                    }
                )
            }

            item(key = RELOAD_ITEM_KEY) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = WahaTvDimens.SectionSpacing),
                    contentAlignment = Alignment.Center
                ) {
                    TvReloadButton(onClick = onReload, refreshing = refreshing)
                }
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
private fun TvReloadButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** True while the refetch this button started is still running. */
    refreshing: Boolean = false
) {
    Button(
        onClick = onClick,
        // A second press while the first is still walking the library would only queue
        // another sync behind the same single flight; the button shows what is happening
        // and stops taking presses until it is done.
        enabled = !refreshing,
        scale = ButtonDefaults.scale(focusedScale = WahaTvDimens.FocusScale),
        modifier = modifier.testTag(TvTestTags.ReloadButton)
    ) {
        if (refreshing) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Icon(
                imageVector = TvIcons.Refresh,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = if (refreshing) REFRESHING_LABEL else RELOAD_LABEL,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/**
 * "Nothing published yet" — with the same reload button the shelf ends with, because an
 * empty library is exactly the case where the viewer needs a way to ask again.
 */
@Composable
private fun TvEmptyLibrary(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TvTestTags.EmptyLibrary),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "لا توجد فيديوهات منشورة بعد",
            color = WahaTvColors.TextMuted,
            style = MaterialTheme.typography.bodyLarge
        )
        TvReloadButton(
            onClick = onRetry,
            modifier = Modifier.padding(top = 24.dp)
        )
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
