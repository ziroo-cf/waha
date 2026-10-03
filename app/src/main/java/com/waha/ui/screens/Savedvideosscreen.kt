package com.waha.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
    onChromeVisibilityChange: (Boolean) -> Unit = {},
    sideBarPadding: Dp = 0.dp
) {
    val savedVideos = SavedVideosStore.savedVideosState(allVideos)
    val listState = rememberLazyGridState()
    val columns = rememberWahaWindowInfo().gridColumns

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
            items(savedVideos, key = { it.id }) { video ->
                VideoCard(video = video, onClick = { onVideoClick(video) })
            }
        }
    }
}
