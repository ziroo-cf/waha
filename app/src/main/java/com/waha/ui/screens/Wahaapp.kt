package com.waha.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.waha.data.RecentSearchesStore
import com.waha.data.SavedVideosStore
import com.waha.ui.theme.WahaDarkBg

enum class WahaScreen { Home, Settings, Saved }

@Composable
fun WahaApp(viewModel: WahaHomeViewModel = viewModel()) {
    val context = LocalContext.current
    val density = LocalDensity.current
    LaunchedEffect(Unit) {
        SavedVideosStore.init(context)
        RecentSearchesStore.init(context)
    }

    var currentScreen by remember { mutableStateOf(WahaScreen.Home) }
    var activeVideo by remember { mutableStateOf<VideoItem?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var isChromeVisible by remember { mutableStateOf(true) }
    var selectedCategory by remember { mutableStateOf(ALL_CATEGORY_KEY) }

    var topBarHeight by remember { mutableStateOf(Dp.Hairline) }
    var bottomBarHeight by remember { mutableStateOf(Dp.Hairline) }
    var sideBarWidth by remember { mutableStateOf(Dp.Hairline) }

    val windowInfo = rememberWahaWindowInfo()
    val useTabletLayout = windowInfo.useTabletLayout

    val uiState by viewModel.uiState.collectAsState()
    val successState = uiState as? HomeUiState.Success
    val allVideos = successState?.videos.orEmpty()

    val dynamicCategories = remember(successState?.videosByCategory?.keys) {
        listOf(CategoryFilterTile(ALL_CATEGORY_KEY, "الكل")) +
                successState?.videosByCategory?.keys.orEmpty().sorted().map { key ->
                    CategoryFilterTile(key, categoryLabels[key] ?: key)
                }
    }

    BackHandler(enabled = activeVideo != null) { activeVideo = null }
    BackHandler(enabled = showSearch && activeVideo == null) { showSearch = false }
    BackHandler(enabled = currentScreen != WahaScreen.Home && activeVideo == null && !showSearch) {
        currentScreen = WahaScreen.Home
    }

    fun navigateTo(screen: WahaScreen) {
        currentScreen = screen
        isChromeVisible = true
    }

    Box(modifier = Modifier.fillMaxSize().background(WahaDarkBg)) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentScreen) {
                WahaScreen.Home -> WahaHomeScreen(
                    viewModel = viewModel,
                    topBarHeight = topBarHeight,
                    bottomBarHeight = bottomBarHeight,
                    selectedCategory = selectedCategory,
                    onVideoClick = { activeVideo = it },
                    onChromeVisibilityChange = { isChromeVisible = it },
                    sideBarPadding = if (useTabletLayout) sideBarWidth else 0.dp
                )
                WahaScreen.Settings -> SettingsScreen(
                    topBarHeight = topBarHeight,
                    bottomBarHeight = bottomBarHeight,
                    sideBarPadding = if (useTabletLayout) sideBarWidth else 0.dp
                )
                WahaScreen.Saved -> SavedVideosScreen(
                    allVideos = allVideos,
                    topBarHeight = topBarHeight,
                    bottomBarHeight = bottomBarHeight,
                    onVideoClick = { activeVideo = it },
                    onChromeVisibilityChange = { isChromeVisible = it },
                    sideBarPadding = if (useTabletLayout) sideBarWidth else 0.dp
                )
            }
        }

        AnimatedVisibility(
            visible = isChromeVisible,
            enter = slideInVertically(initialOffsetY = { -it }),
            exit = slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .onSizeChanged {
                    val measured = with(density) { it.height.toDp() }
                    if (measured > Dp.Hairline) topBarHeight = measured
                }
        ) {
            Column {
                WahaTopBar(
                    isSearchActive = showSearch,
                    isSettingsActive = currentScreen == WahaScreen.Settings,
                    onSearchClick = { showSearch = true },
                    onSettingsClick = { navigateTo(WahaScreen.Settings) }
                )
                if (currentScreen == WahaScreen.Home) {
                    CategoryFilterBar(
                        categories = dynamicCategories,
                        selectedCategory = selectedCategory,
                        onCategorySelect = { selectedCategory = it }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isChromeVisible,
            enter = if (useTabletLayout) {
                slideInHorizontally(initialOffsetX = { it })
            } else {
                slideInVertically(initialOffsetY = { it })
            },
            exit = if (useTabletLayout) {
                slideOutHorizontally(targetOffsetX = { it })
            } else {
                slideOutVertically(targetOffsetY = { it })
            },
            modifier = Modifier
                // In RTL the layout "start" edge is the physical right, matching
                // the requested right-hand island for tablets.
                .align(if (useTabletLayout) Alignment.CenterStart else Alignment.BottomCenter)
                .onSizeChanged {
                    val measured = with(density) { it.width.toDp() }
                    val measuredHeight = with(density) { it.height.toDp() }
                    if (useTabletLayout) {
                        if (measured > Dp.Hairline) sideBarWidth = measured
                    } else if (measuredHeight > Dp.Hairline) {
                        bottomBarHeight = measuredHeight
                    }
                }
        ) {
            WahaBottomNavigation(
                selectedScreen = currentScreen,
                onScreenSelect = { navigateTo(it) },
                vertical = useTabletLayout
            )
        }

        if (showSearch) {
            SearchOverlay(
                allVideos = allVideos,
                onVideoClick = {
                    activeVideo = it
                    showSearch = false
                },
                onClose = { showSearch = false }
            )
        }

        activeVideo?.let { video ->
            VideoPlayerModal(
                video = video,
                allVideos = allVideos,
                onVideoSelect = { selected -> activeVideo = selected },
                onClose = { activeVideo = null }
            )
        }
    }
}