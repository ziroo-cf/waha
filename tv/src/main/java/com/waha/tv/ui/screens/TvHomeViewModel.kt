package com.waha.tv.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waha.data.VideoRepository
import com.waha.domain.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the TV browse screen can show. */
sealed interface TvUiState {
    data object Loading : TvUiState

    /**
     * The library is genuinely empty — a finished sync left no rows in the cache.
     *
     * Distinct from [Loading], which means "nothing has been read yet". The two used to
     * be one state, so an empty library showed a spinner that never ended and the
     * viewer's "لا توجد فيديوهات منشورة بعد" message could never appear.
     */
    data object Empty : TvUiState

    /**
     * The library and the rows the home screen draws from it.
     *
     * The rows travel with the library rather than being built by the screen: building
     * them is an O(library × categories) pass over every video, and doing that inside a
     * composition puts it on the main thread at the exact moment a frame is due.
     */
    data class Ready(
        val videos: List<VideoItem>,
        val rails: List<TvRail>,
        /** True while an explicit reload is walking the library. */
        val isRefreshing: Boolean = false,
        /**
         * Which shuffle [rails] came from. It changes on every app open and every
         * reload, so the screen can tell "the shelf was reordered" from "the cache
         * re-emitted the same shelf" and put the remote back on the first card.
         */
        val order: Long = 0L
    ) : TvUiState

    data class Error(val message: String) : TvUiState
}

/**
 * TV-side state holder. It reuses the exact same repository and cache as the
 * phone app, so both browse screens list identical content.
 *
 * The rail comes from Room, which means zapping back to the home screen shows
 * the library instantly instead of waiting on the network again.
 *
 * The order of the shelf is a separate decision from the library itself: it comes
 * from [shuffleSeed], which starts fresh in every process, moves on every reload,
 * and moves again whenever the app is opened. A seed (rather than a fresh shuffle
 * per emission) is what keeps the cards still while the viewer is looking at them:
 * Room re-emits the cache after every sync, and the viewer must not see the shelf
 * rearrange itself under their thumb.
 *
 * [defaultDispatcher] is where the home rows are derived. It is a constructor
 * parameter so the unit tests can drive it on their own virtual clock instead of a
 * real thread pool.
 */
class TvHomeViewModel(
    private val repository: VideoRepository = VideoRepository(),
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** The order the first shelf is shuffled with; tests pass a fixed seed. */
    initialSeed: Long = System.nanoTime()
) : ViewModel() {

    private val _uiState = MutableStateFlow<TvUiState>(TvUiState.Loading)
    val uiState: StateFlow<TvUiState> = _uiState.asStateFlow()

    /** Which shuffle the shelf is showing right now. */
    private val shuffleSeed = MutableStateFlow(initialSeed)

    /** True while an explicit reload is walking the library. */
    private val refreshing = MutableStateFlow(false)

    init {
        observeCache()
        loadVideos()
    }

    private fun observeCache() {
        viewModelScope.launch {
            combine(
                repository.observeVideos(),
                shuffleSeed,
                refreshing
            ) { videos, seed, isRefreshing -> ShelfInput(videos, seed, isRefreshing) }
                .map { input ->
                    when {
                        input.videos.isNotEmpty() ->
                            // Rails are derived here, on a worker thread, and handed over
                            // as finished state: one seed always yields the same shelves,
                            // which is what makes a reshuffle an explicit act.
                            withContext(defaultDispatcher) {
                                TvUiState.Ready(
                                    videos = input.videos,
                                    rails = TvRails.buildStable(input.videos, input.seed),
                                    isRefreshing = input.isRefreshing,
                                    order = input.seed
                                )
                            }

                        // Zero rows is an answer only once a sync has finished. Until then
                        // the cache is simply still empty on disk, and the screen stays on
                        // Loading instead of flashing an empty home over the first sync.
                        repository.syncedVideoCount() == 0 -> TvUiState.Empty

                        else -> null
                    }
                }
                .filterNotNull()
                // A cache that cannot be read (corruption, a failed migration, a full
                // disk) must offer the retry screen, not a spinner that never ends.
                // Nothing is caught while a library is already on screen: the viewer
                // keeps the shelf they have. Cancellation is not caught here.
                .catch { error ->
                    if (_uiState.value !is TvUiState.Ready) {
                        _uiState.value = TvUiState.Error(error.message ?: "حدث خطأ غير متوقع")
                    }
                }
                .collect { _uiState.value = it }
        }
    }

    /** The three things a shelf depends on: what is cached, its order, and a running reload. */
    private data class ShelfInput(
        val videos: List<VideoItem>,
        val seed: Long,
        val isRefreshing: Boolean
    )

    /**
     * A new shelf order from the cache the app already holds — no network walk.
     *
     * Called when the app is opened (see `MainActivity.onStart`) and by every reload,
     * because "اعرض لي غيرها" is a request about order, not about the network.
     */
    fun reshuffle() {
        // One prime step: two consecutive reshuffles can never land on the same order.
        shuffleSeed.value = shuffleSeed.value + SHUFFLE_STEP
    }

    fun loadVideos() {
        if (_uiState.value !is TvUiState.Ready) {
            _uiState.value = TvUiState.Loading
        }
        // Mark the reload before changing the order: the two changes then arrive as two
        // emissions that both say "reloading", so the button never flickers back to idle
        // between them.
        refreshing.value = true
        // The order changes before the walk starts, so the press always visibly does
        // something — on a TV that has just synced, the network may have nothing new.
        reshuffle()

        viewModelScope.launch {
            try {
                // Explicit: the routine interval is for automatic syncs, and being
                // skipped by it is exactly what made the reload button look dead.
                repository.refresh(force = true)
                // The sync has finished and the cache is still empty, so no further Room
                // emission is coming: say so now rather than leaving the spinner up.
                if (_uiState.value is TvUiState.Loading && repository.syncedVideoCount() == 0) {
                    _uiState.value = TvUiState.Empty
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                // The viewer keeps the shelf they have; only the retry screen appears
                // when there was nothing to keep.
                if (_uiState.value !is TvUiState.Ready) {
                    _uiState.value = TvUiState.Error(e.message ?: "حدث خطأ غير متوقع")
                }
            } finally {
                refreshing.value = false
            }
        }
    }

    private companion object {
        /** Distance between two shuffles: prime, so consecutive orders never repeat. */
        const val SHUFFLE_STEP = 104_729L
    }
}
