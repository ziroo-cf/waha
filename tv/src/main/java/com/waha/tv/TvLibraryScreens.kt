package com.waha.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.waha.data.SavedVideosStore
import com.waha.data.WatchHistoryStore
import com.waha.domain.VideoItem
import com.waha.domain.watchedAgoLabel

/**
 * The phone build's saved list, as a TV grid.
 *
 * It reads the same [SavedVideosStore] the phone writes, so saving a video on
 * the phone shows up on the TV (and the other way round) without syncing code.
 */
@Composable
fun TvSavedScreen(
    allVideos: List<VideoItem>,
    onVideoClick: (VideoItem) -> Unit
) {
    val savedIds = SavedVideosStore.savedIds
    val savedVideos = remember(allVideos, savedIds.size) {
        val ids = savedIds.toList()
        allVideos.filter { it.id in ids }
    }

    TvLibraryScreen(
        title = "المحفوظات",
        count = savedVideos.size,
        emptyMessage = "لم تحفظ أي فيديو بعد — افتح أي فيديو واضغط زر الحفظ",
        videos = savedVideos,
        onVideoClick = onVideoClick
    )
}

/**
 * The phone build's watch history, as a TV grid.
 *
 * The order and the "watched x ago" labels come from [WatchHistoryStore]; the
 * label formatter itself now lives in `:core`, so both screens word it the same.
 */
@Composable
fun TvHistoryScreen(
    allVideos: List<VideoItem>,
    onVideoClick: (VideoItem) -> Unit
) {
    val recentIds = WatchHistoryStore.recentIds
    val watchedAt = WatchHistoryStore.watchedAtMs.value
    val historyVideos = remember(allVideos, recentIds.size) {
        val byId = allVideos.associateBy { it.id }
        recentIds.mapNotNull { byId[it] }
    }

    TvLibraryScreen(
        title = "سجل المشاهدة",
        count = historyVideos.size,
        emptyMessage = "لم تشاهد أي فيديو بعد — شغّل أي فيديو وسيظهر هنا",
        videos = historyVideos,
        onVideoClick = onVideoClick,
        footnote = { video -> watchedAt[video.id]?.let { watchedAgoLabel(it) } },
        action = if (historyVideos.isEmpty()) null else ({
            TvKeyButton(label = "مسح الكل", onClick = { WatchHistoryStore.clear() })
        })
    )
}

/**
 * Shared body of the two library screens: a title row (with an optional action)
 * and either the grid or a centred "nothing here yet" message.
 */
@Composable
private fun TvLibraryScreen(
    title: String,
    count: Int,
    emptyMessage: String,
    videos: List<VideoItem>,
    onVideoClick: (VideoItem) -> Unit,
    footnote: (VideoItem) -> String? = { null },
    action: (@Composable () -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = WahaTvDimens.ScreenPadding,
                    end = WahaTvDimens.ScreenPadding,
                    top = WahaTvDimens.ScreenPadding / 2,
                    bottom = WahaTvDimens.SectionSpacing / 2
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (count > 0) "$title ($count)" else title,
                color = WahaTvColors.TextWarm,
                style = MaterialTheme.typography.headlineSmall
            )
            action?.invoke()
        }

        if (videos.isEmpty()) {
            TvMessage(text = emptyMessage, modifier = Modifier.weight(1f))
        } else {
            TvVideoGrid(
                videos = videos,
                onVideoClick = onVideoClick,
                footnote = footnote,
                contentPadding = PaddingValues(
                    start = WahaTvDimens.ScreenPadding,
                    end = WahaTvDimens.ScreenPadding,
                    top = 0.dp,
                    bottom = WahaTvDimens.ScreenPadding
                )
            )
        }
    }
}
