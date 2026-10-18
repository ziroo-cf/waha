package com.waha.tv.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.waha.data.SavedVideosStore
import com.waha.data.WatchHistoryStore
import com.waha.domain.VideoItem
import com.waha.domain.watchedAgoLabel
import com.waha.tv.TvTestTags

/** Stable rail keys for the library's two rows. */
internal const val SAVED_RAIL_KEY = "saved"
internal const val HISTORY_RAIL_KEY = "history"

/**
 * The viewer's own library: what they saved and what they watched, as two rows
 * of the same cards the home screen uses.
 *
 * المحفوظات and السجل used to be two destinations in the rail; they are one room
 * now, because both answer the same question ("where is that video I had?") and
 * a remote should not have to choose between them. The rows read the very same
 * stores the phone build writes, so a save or a watch on either device shows up
 * here without any syncing code.
 */
@Composable
fun TvLibraryScreen(
    allVideos: List<VideoItem>,
    onVideoClick: (VideoItem) -> Unit
) {
    // Keyed on the ids themselves (not just their count) so saving or un-saving a
    // video, or reordering the history, redraws the rows immediately.
    val savedIds = SavedVideosStore.savedIds.toList()
    val savedVideos = remember(allVideos, savedIds) {
        allVideos.filter { it.id in savedIds }
    }

    val watchedAt = WatchHistoryStore.watchedAtMs.value
    val historyVideos = remember(allVideos, WatchHistoryStore.recentIds.toList()) {
        val byId = allVideos.associateBy { it.id }
        WatchHistoryStore.recentIds.mapNotNull { byId[it] }
    }

    if (savedVideos.isEmpty() && historyVideos.isEmpty()) {
        TvMessage("لم تحفظ أو تشاهد أي فيديو بعد — افتح أي فيديو واضغط زر الحفظ")
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TvTestTags.LibraryList),
        contentPadding = PaddingValues(bottom = WahaTvDimens.ScreenPadding)
    ) {
        item(key = SAVED_RAIL_KEY) {
            TvVideoRail(
                railKey = SAVED_RAIL_KEY,
                title = "المحفوظات",
                videos = savedVideos,
                onVideoClick = onVideoClick,
                emptyMessage = "لم تحفظ أي فيديو بعد — افتح أي فيديو واضغط زر الحفظ"
            )
        }

        item(key = HISTORY_RAIL_KEY) {
            TvVideoRail(
                railKey = HISTORY_RAIL_KEY,
                title = "السجل",
                videos = historyVideos,
                onVideoClick = onVideoClick,
                footnote = { video -> watchedAt[video.id]?.let { watchedAgoLabel(it) } },
                emptyMessage = "لم تشاهد أي فيديو بعد — شغّل أي فيديو وسيظهر هنا",
                action = if (historyVideos.isEmpty()) null else ({
                    TvKeyButton(
                        label = "مسح الكل",
                        onClick = { WatchHistoryStore.clear() },
                        width = 140.dp
                    )
                })
            )
        }
    }
}
