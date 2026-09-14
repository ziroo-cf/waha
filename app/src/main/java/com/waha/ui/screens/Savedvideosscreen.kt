package com.waha.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waha.data.SavedVideosStore
import com.waha.ui.theme.WahaTextMuted

@Composable
fun SavedVideosScreen(
    allVideos: List<VideoItem>,
    topBarHeight: Dp,
    bottomBarHeight: Dp,
    onVideoClick: (VideoItem) -> Unit,
    onChromeVisibilityChange: (Boolean) -> Unit = {}
) {
    val savedVideos = SavedVideosStore.savedVideosState(allVideos)
    val listState = rememberLazyListState()

    listState.HideOnScrollEffect(onVisibilityChange = onChromeVisibilityChange)

    if (savedVideos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "لم تحفظ أي فيديو بعد — اضغط على أيقونة الحفظ بأي فيديو",
                color = WahaTextMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 40.dp)
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = topBarHeight + 16.dp,
                bottom = bottomBarHeight + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(savedVideos, key = { it.id }) { video ->
                VideoCard(video = video, onClick = { onVideoClick(video) })
            }
        }
    }
}
