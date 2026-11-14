package com.waha.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waha.data.VideoRepository
import com.waha.domain.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(
        val videos: List<VideoItem>,
        val videosByCategory: Map<String, List<VideoItem>>
    ) : HomeUiState

    data class Error(val message: String) : HomeUiState
}

/**
 * Home screen state.
 *
 * The list comes from the local cache through [VideoRepository.observeVideos],
 * so a warm start renders the previous library immediately and the network
 * refresh only replaces it when it lands. Grouping runs on a worker thread.
 *
 * The cache hands the library over newest first; Home draws it in a random order
 * instead, so the grid is a shelf to browse rather than a feed sorted by publish
 * date. The shuffle is seeded by [shuffleSeed] so the cards never move under the
 * viewer's finger when Room re-emits the same library, while a new app process —
 * or a pull-to-refresh — draws a new seed and with it a new order.
 */
class WahaHomeViewModel(
    private val repository: VideoRepository = VideoRepository()
) : ViewModel() {

    /**
     * Seed of the grid's order: written from the main thread on a refresh and
     * read on the worker thread that shuffles, hence volatile.
     */
    @Volatile
    private var shuffleSeed: Long = Random.nextLong()

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        observeCache()
        loadVideos()
    }

    private fun observeCache() {
        viewModelScope.launch {
            repository.observeLibrary()
                .mapNotNull { library ->
                    // An empty cache is "not loaded yet", not "the library is empty":
                    // keep the spinner until either data or an error arrives.
                    if (library.videos.isEmpty()) return@mapNotNull null
                    val videos = library.videos.shuffled(Random(shuffleSeed))
                    HomeUiState.Success(
                        videos = videos,
                        // Re-grouped from the shuffled list instead of reusing
                        // library.byCategory, so a category's cards keep the order
                        // they have on the unfiltered grid.
                        videosByCategory = videos
                            .groupBy { video -> video.categoryKey.orEmpty() }
                            .filterKeys { key -> key.isNotEmpty() }
                    )
                }
                .flowOn(Dispatchers.Default)
                .catch { error ->
                    Log.e("WahaDebug", "فشل قراءة مكتبة الفيديوهات من الكاش", error)
                    if (_uiState.value !is HomeUiState.Success) {
                        _uiState.value = HomeUiState.Error(error.message ?: "حدث خطأ غير متوقع")
                    }
                }
                .collect { _uiState.value = it }
        }
    }

    fun loadVideos(isPullToRefresh: Boolean = false) {
        if (isPullToRefresh) {
            // Refresh is the viewer asking for a fresh shelf, so the grid is drawn
            // in a new order along with the new data.
            shuffleSeed = Random.nextLong()
            _isRefreshing.value = true
        } else if (_uiState.value !is HomeUiState.Success) {
            _uiState.value = HomeUiState.Loading
        }

        viewModelScope.launch {
            try {
                // A pull-to-refresh must hit the network; a routine load joins the
                // repository's single-flight sync and lets Room push the result.
                repository.refresh(force = isPullToRefresh)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e("WahaDebug", "فشل تحميل الفيديوهات من Supabase", e)
                // Cached content is still valid; only surface the error when the
                // screen has nothing to show.
                if (_uiState.value !is HomeUiState.Success) {
                    _uiState.value = HomeUiState.Error(e.message ?: "حدث خطأ غير متوقع")
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
