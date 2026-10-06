package com.waha.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waha.data.WatchHistoryStore
import com.waha.ui.theme.WahaLine
import com.waha.ui.theme.WahaTealBright
import com.waha.ui.theme.WahaTextMuted
import com.waha.ui.theme.WahaTextWarm

/** Arabic "watched x ago" label for an epoch-millis timestamp. */
internal fun watchedAgoLabel(watchedAtMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val minutes = ((nowMs - watchedAtMs) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "الآن"
        minutes < 60 -> "قبل $minutes دقيقة"
        minutes < 60 * 24 -> {
            val hours = minutes / 60
            "قبل $hours ساعة"
        }
        minutes < 60 * 24 * 30 -> {
            val days = minutes / (60 * 24)
            "قبل $days يوم"
        }
        else -> "قبل فترة طويلة"
    }
}

@Composable
fun WatchHistoryScreen(
    allVideos: List<VideoItem>,
    topBarHeight: Dp,
    bottomBarHeight: Dp,
    onVideoClick: (VideoItem) -> Unit,
    onChromeVisibilityChange: (Boolean) -> Unit = {},
    sideBarPadding: Dp = 0.dp
) {
    val historyVideos = remember(allVideos, WatchHistoryStore.recentIds.size) {
        val byId = allVideos.associateBy { it.id }
        WatchHistoryStore.recentIds.mapNotNull { byId[it] }
    }
    val watchedAt = WatchHistoryStore.watchedAtMs.value
    val listState = rememberLazyGridState()
    val columns = rememberWahaWindowInfo().gridColumns

    listState.HideOnScrollEffect(onVisibilityChange = onChromeVisibilityChange)

    if (historyVideos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "لم تشاهد أي فيديو بعد — شغّل أي فيديو وسيظهر هنا",
                color = WahaTextMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 40.dp)
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = sideBarPadding,
                top = topBarHeight + 16.dp,
                bottom = bottomBarHeight + 16.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(if (columns > 1) 8.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // A header row with count + clear-all, styled like the home sections.
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = WahaTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "سجل المشاهدة (${historyVideos.size})",
                        color = WahaTextWarm,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    TextButton(onClick = { WatchHistoryStore.clear() }) {
                        Text("مسح الكل", color = WahaTealBright, fontSize = 13.sp)
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                HorizontalDivider(color = WahaLine, thickness = 1.dp)
            }
            items(historyVideos, key = { it.id }) { video ->
                Column {
                    VideoCard(video = video, onClick = { onVideoClick(video) })
                    watchedAt[video.id]?.let { ms ->
                        Text(
                            text = watchedAgoLabel(ms),
                            color = WahaTextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
