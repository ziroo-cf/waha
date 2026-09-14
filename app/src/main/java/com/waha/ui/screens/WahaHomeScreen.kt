package com.waha.ui.screens

import android.util.Log

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.waha.R
import com.waha.ui.theme.*

data class VideoItem(
    val id: String,
    val youtubeId: String,
    val title: String,
    val meta: String,
    val thumbnailUrl: String? = null,
    val categoryKey: String? = null
)

const val ALL_CATEGORY_KEY = "all"

data class CategoryFilterTile(val key: String, val label: String)

val categoryLabels = mapOf(
    "kids" to "للأطفال",
    "islamic" to "إسلاميات",
    "educational" to "تعليمي",
    "dubbed" to "دبلجة رسمية"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WahaHomeScreen(
    viewModel: WahaHomeViewModel,
    topBarHeight: Dp,
    bottomBarHeight: Dp,
    selectedCategory: String,
    onVideoClick: (VideoItem) -> Unit,
    onChromeVisibilityChange: (Boolean) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val listState = rememberLazyListState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.loadVideos(isPullToRefresh = true) },
        modifier = Modifier.fillMaxSize()
    ) {
        when (val state = uiState) {
            is HomeUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = WahaTeal)
                }
            }
            is HomeUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("تعذّر تحميل المحتوى", color = WahaTextWarm)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.loadVideos() }) {
                            Text("إعادة المحاولة", color = WahaTealBright)
                        }
                    }
                }
            }
            is HomeUiState.Success -> {
                val displayedVideos = if (selectedCategory == ALL_CATEGORY_KEY) {
                    state.shuffledVideos
                } else {
                    state.videosByCategory[selectedCategory].orEmpty()
                }

                LaunchedEffect(state.shuffledVideos, selectedCategory) {
                    listState.scrollToItem(0)
                }

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

                if (displayedVideos.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("لا توجد اي فيديوهات في هذه الفئة بعد", color = WahaTextMuted)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = topBarHeight + 12.dp,
                            bottom = bottomBarHeight + 12.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(displayedVideos, key = { it.id }) { video ->
                            VideoCard(video = video, onClick = { onVideoClick(video) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WahaTopBar(
    isSearchActive: Boolean,
    isSettingsActive: Boolean,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(100.dp),
            color = WahaCardBg,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_logo),
                        contentDescription = "شعار واحة",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(100.dp))
                    )
                    Text(
                        text = "واحة",
                        color = WahaTextWarm,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onSearchClick, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Filled.Search else Icons.Outlined.Search,
                            contentDescription = "بحث",
                            tint = if (isSearchActive) WahaTeal else WahaTextWarm
                        )
                    }
                    IconButton(onClick = onSettingsClick, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = if (isSettingsActive) Icons.Filled.Settings else Icons.Outlined.Settings,
                            contentDescription = "الإعدادات",
                            tint = if (isSettingsActive) WahaTeal else WahaTextWarm
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryFilterBar(
    categories: List<CategoryFilterTile>,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories, key = { it.key }) { cat ->
            val isSelected = cat.key == selectedCategory
            Surface(
                onClick = { onCategorySelect(cat.key) },
                shape = RoundedCornerShape(100.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else WahaCardBg,
                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else WahaLine)
            ) {
                Text(
                    text = cat.label,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else WahaTextMuted,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun VideoCard(video: VideoItem, onClick: () -> Unit) {
    val safeThumbnailUrl = video.thumbnailUrl?.takeIf { it.isNotBlank() }
        ?: "https://img.youtube.com/vi/${video.youtubeId}/hqdefault.jpg"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(WahaCardBg2, WahaCardBg)))
        ) {
            AsyncImage(
                model = safeThumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { state ->
                    Log.e("WahaDebug", "فشل تحميل الصورة: $safeThumbnailUrl", state.result.throwable)
                },
                onSuccess = {
                    Log.d("WahaDebug", "نجح تحميل الصورة: $safeThumbnailUrl")
                }
            )

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = video.title,
            color = WahaTextWarm,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 20.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        if (video.meta.isNotBlank()) {
            Text(
                text = video.meta,
                color = WahaTextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun WahaBottomNavigation(selectedScreen: WahaScreen, onScreenSelect: (WahaScreen) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 10.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(100.dp),
            color = WahaCardBg,
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(
                    selected = selectedScreen == WahaScreen.Home,
                    outlinedIcon = Icons.Outlined.Home,
                    filledIcon = Icons.Filled.Home,
                    label = "الرئيسية",
                    onClick = { onScreenSelect(WahaScreen.Home) }
                )
                BottomNavItem(
                    selected = selectedScreen == WahaScreen.Saved,
                    outlinedIcon = Icons.Outlined.BookmarkBorder,
                    filledIcon = Icons.Filled.Bookmark,
                    label = "محفوظاتي",
                    onClick = { onScreenSelect(WahaScreen.Saved) }
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    selected: Boolean,
    outlinedIcon: ImageVector,
    filledIcon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = if (selected) filledIcon else outlinedIcon,
            contentDescription = null,
            tint = if (selected) WahaTealBright else WahaTextMuted
        )
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) WahaTealBright else WahaTextMuted
        )
    }
}