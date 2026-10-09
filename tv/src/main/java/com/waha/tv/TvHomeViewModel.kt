package com.waha.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waha.data.VideoRepository
import com.waha.domain.VideoItem
import com.waha.domain.toVideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the TV browse screen can show. */
sealed interface TvUiState {
    data object Loading : TvUiState
    data class Ready(val videos: List<VideoItem>) : TvUiState
    data class Error(val message: String) : TvUiState
}

/**
 * TV-side state holder. It reuses the exact same repository and row→model
 * mapping as the phone app, so both browse screens list identical content.
 */
class TvHomeViewModel(
    private val repository: VideoRepository = VideoRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<TvUiState>(TvUiState.Loading)
    val uiState: StateFlow<TvUiState> = _uiState.asStateFlow()

    init {
        loadVideos()
    }

    fun loadVideos() {
        viewModelScope.launch {
            _uiState.value = TvUiState.Loading
            _uiState.value = try {
                TvUiState.Ready(repository.getVideos().map { it.toVideoItem() })
            } catch (e: Exception) {
                TvUiState.Error(e.message ?: "حدث خطأ غير متوقع")
            }
        }
    }
}
