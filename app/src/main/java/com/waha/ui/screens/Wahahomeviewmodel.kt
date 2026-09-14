package com.waha.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waha.data.VideoRepository
import com.waha.data.VideoRow
import com.waha.data.toVideoDurationText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(
        val videos: List<VideoItem>,
        val videosByCategory: Map<String, List<VideoItem>>
    ) : HomeUiState

    data class Error(val message: String) : HomeUiState
}

class WahaHomeViewModel(
    private val repository: VideoRepository = VideoRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadVideos()
    }

    fun loadVideos(isPullToRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isPullToRefresh) {
                _isRefreshing.value = true
            } else {
                _uiState.value = HomeUiState.Loading
            }
            try {
                val rows = repository.getVideos()
                val videos = rows.map { it.toVideoItem() }.shuffled()
                val grouped = videos
                    .groupBy { it.categoryKey.orEmpty() }
                    .filterKeys { it.isNotEmpty() }

                _uiState.value = HomeUiState.Success(
                    videos = videos,
                    videosByCategory = grouped
                )
            } catch (e: Exception) {
                Log.e("WahaDebug", "فشل تحميل الفيديوهات من Supabase", e)
                _uiState.value = HomeUiState.Error(e.message ?: "حدث خطأ غير متوقع")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun VideoRow.toVideoItem(): VideoItem = VideoItem(
        id = id,
        youtubeId = id,
        title = title ?: "بدون عنوان",
        meta = category?.let { categoryLabels[it] ?: it } ?: "",
        thumbnailUrl = thumbnail?.takeIf { it.isNotBlank() },
        categoryKey = category,
        durationText = duration
            ?.takeIf { it > kotlin.time.Duration.ZERO }
            ?.toVideoDurationText()
    )
}
