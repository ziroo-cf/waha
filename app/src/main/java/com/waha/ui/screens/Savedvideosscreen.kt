package com.waha.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
    val savedVideos = allVideos.filter { SavedVideosStore.isSaved(it.id) }
    val listState = rememberLazyListState()

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
        var previousIndex by remember { mutableStateOf(0) }
        var previousOffset by remember { mutableStateOf(0) }
        var accumulatedDelta by remember { mutableStateOf(0) }
        val scrollThresholdPx = 60

        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .collect { (index, offset) ->
                    if (index != previousIndex) {
                        onChromeVisibilityChange(index <= previousIndex)
                        accumulatedDelta = 0
                    } else {
                        val delta = offset - previousOffset
                        accumulatedDelta += delta
                        if (accumulatedDelta > scrollThresholdPx) {
                            onChromeVisibilityChange(false)
                            accumulatedDelta = 0
                        } else if (accumulatedDelta < -scrollThresholdPx) {
                            onChromeVisibilityChange(true)
                            accumulatedDelta = 0
                        }
                    }
                    previousIndex = index
                    previousOffset = offset
                }
        }

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