package com.waha.tv

import app.cash.turbine.test
import com.waha.data.VideoRepository
import com.waha.domain.VideoItem
import com.waha.tv.ui.screens.TvHomeViewModel
import com.waha.tv.ui.screens.TvUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The browse screen's state machine, asserted through its StateFlow on a virtual
 * clock: a TV has nowhere to show a spinner-and-error-and-retry tangle, so the
 * three states have to arrive in the right order, once each.
 *
 * The ViewModel now reads the library from the (Room-backed) cache stream and
 * asks the repository to sync separately, so the fake stands in for both: a
 * flow of cached videos and a refresh that can fail.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TvHomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /** A library that can be made to fail, so the error path is reachable. */
    private class FakeRepository : VideoRepository() {
        /** Stands in for Room: the ViewModel observes it, never reads it directly. */
        val cached = MutableStateFlow<List<VideoItem>>(emptyList())
        var failure: Exception? = null

        override fun observeVideos(): Flow<List<VideoItem>> = cached

        override suspend fun refresh(force: Boolean) {
            failure?.let { throw it }
        }
    }

    private fun row(id: String) = VideoItem(
        id = id,
        youtubeId = id,
        title = "فيديو $id",
        meta = ""
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the library flows from loading to ready`() = runTest {
        val repository = FakeRepository().apply {
            cached.value = listOf(row("v1"), row("v2"))
        }
        val viewModel = TvHomeViewModel(repository)

        viewModel.uiState.test {
            assertEquals(TvUiState.Loading, awaitItem())

            advanceUntilIdle()

            val ready = awaitItem()
            assertTrue("expected a ready state, got $ready", ready is TvUiState.Ready)
            assertEquals(listOf("v1", "v2"), (ready as TvUiState.Ready).videos.map { it.id })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failing library becomes an error, and retry recovers`() = runTest {
        val repository = FakeRepository().apply { failure = IllegalStateException("لا يوجد اتصال") }
        val viewModel = TvHomeViewModel(repository)

        viewModel.uiState.test {
            assertEquals(TvUiState.Loading, awaitItem())

            advanceUntilIdle()

            val error = awaitItem()
            assertTrue("expected an error state, got $error", error is TvUiState.Error)
            assertEquals("لا يوجد اتصال", (error as TvUiState.Error).message)

            repository.failure = null
            viewModel.loadVideos()

            assertEquals(TvUiState.Loading, awaitItem())

            // The retry lands in the cache; the screen follows the cache.
            repository.cached.value = listOf(row("v9"))
            advanceUntilIdle()
            assertEquals(
                listOf("v9"),
                (awaitItem() as TvUiState.Ready).videos.map { it.id }
            )

            cancelAndIgnoreRemainingEvents()
        }
    }
}
