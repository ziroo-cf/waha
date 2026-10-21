package com.waha.tv.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waha.data.VideoRepository
import com.waha.domain.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/** What the TV browse screen can show. */
sealed interface TvUiState {
    data object Loading : TvUiState
    data class Ready(val videos: List<VideoItem>) : TvUiState
    data class Error(val message: String) : TvUiState
}

/**
 * TV-side state holder. It reuses the exact same repository and cache as the
 * phone app, so both browse screens list identical content.
 *
 * The rail comes from Room, which means zapping back to the home screen shows
 * the library instantly instead of waiting on the network again.
 */
class TvHomeViewModel(
    private val repository: VideoRepository = VideoRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<TvUiState>(TvUiState.Loading)
    val uiState: StateFlow<TvUiState> = _uiState.asStateFlow()

    init {
        observeCache()
        loadVideos()
    }

    private fun observeCache() {
        viewModelScope.launch {
            repository.observeVideos()
                .mapNotNull { videos ->
                    // Nothing cached yet: stay on Loading rather than showing an
                    // empty home while the first sync runs.
                    if (videos.isEmpty()) return@mapNotNull null
                    TvUiState.Ready(videos)
                }
                .collect { _uiState.value = it }
        }
    }

    fun loadVideos() {
        if (_uiState.value !is TvUiState.Ready) {
            _uiState.value = TvUiState.Loading
        }

        viewModelScope.launch {
            try {
                repository.refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (_uiState.value !is TvUiState.Ready) {
                    _uiState.value = TvUiState.Error(e.message ?: "حدث خطأ غير متوقع")
                }
            }
        }
    }
}
