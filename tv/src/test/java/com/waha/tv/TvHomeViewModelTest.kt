package com.waha.tv

import app.cash.turbine.test
import com.waha.data.VideoRepository
import com.waha.domain.VideoItem
import com.waha.tv.ui.screens.TvHomeViewModel
import com.waha.tv.ui.screens.TvRails
import com.waha.tv.ui.screens.TvUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

        /** Rows the last finished sync left behind; null means none has finished yet. */
        var syncedCount: Int? = null

        override fun observeVideos(): Flow<List<VideoItem>> = cached

        override fun syncedVideoCount(): Int? = syncedCount

        /** The last [refresh] argument, so an explicit reload's force can be asserted. */
        var lastForce: Boolean? = null

        override suspend fun refresh(force: Boolean) {
            lastForce = force
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
        // The rails are derived on the injected dispatcher, so the test's virtual clock
        // runs that work too instead of racing a real thread pool.
        val viewModel = TvHomeViewModel(repository, dispatcher)

        viewModel.uiState.test {
            assertEquals(TvUiState.Loading, awaitItem())

            advanceUntilIdle()

            val ready = awaitItem()
            assertTrue("expected a ready state, got $ready", ready is TvUiState.Ready)
            assertEquals(listOf("v1", "v2"), (ready as TvUiState.Ready).videos.map { it.id })
            // The rows travel with the state, already built: the screen never derives
            // them inside a composition.
            assertEquals(TvRails.ALL_RAIL_KEY, ready.rails.first().key)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a finished sync that leaves the cache empty shows the empty library`() = runTest {
        // Nothing on the server and nothing cached: the sync completes, the cache stays
        // empty, and zero rows is now an answer. The screen must say "no videos yet"
        // rather than keep a spinner up that no later emission will ever end.
        val repository = FakeRepository().apply { syncedCount = 0 }
        val viewModel = TvHomeViewModel(repository, dispatcher)

        viewModel.uiState.test {
            assertEquals(TvUiState.Loading, awaitItem())

            advanceUntilIdle()

            assertEquals(TvUiState.Empty, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an empty cache between syncs keeps the spinner`() = runTest {
        // A previous sync left rows, so this process knows the library is not empty: an
        // empty read here is a cache that has not been filled yet. Showing the empty
        // library now would flash "no videos" over a library about to arrive.
        val repository = FakeRepository().apply { syncedCount = 12 }
        val viewModel = TvHomeViewModel(repository, dispatcher)

        viewModel.uiState.test {
            assertEquals(TvUiState.Loading, awaitItem())

            advanceUntilIdle()

            assertEquals(TvUiState.Loading, viewModel.uiState.value)
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a cache that cannot be read offers the error state`() = runTest {
        // A corrupt cache, a failed migration, a full disk: the read itself fails. The
        // screen must offer retry instead of waiting on a spinner that never ends.
        val repository = object : VideoRepository() {
            override fun observeVideos(): Flow<List<VideoItem>> = flow {
                throw IllegalStateException("كاش تالف")
            }

            override suspend fun refresh(force: Boolean) = Unit
        }
        val viewModel = TvHomeViewModel(repository, dispatcher)

        viewModel.uiState.test {
            assertEquals(TvUiState.Loading, awaitItem())

            advanceUntilIdle()

            val error = awaitItem()
            assertTrue("expected an error state, got $error", error is TvUiState.Error)
            assertEquals("كاش تالف", (error as TvUiState.Error).message)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failing library becomes an error, and retry recovers`() = runTest {
        val repository = FakeRepository().apply { failure = IllegalStateException("لا يوجد اتصال") }
        val viewModel = TvHomeViewModel(repository, dispatcher)

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

    @Test
    fun `a reload forces a sync and says so on the shelf while it runs`() = runTest {
        // The routine interval is for automatic syncs. A viewer pressing reload is asking
        // for a fresh library now, and being skipped by that interval is exactly what made
        // the button look dead.
        val repository = FakeRepository().apply { cached.value = listOf(row("v1")) }
        val viewModel = TvHomeViewModel(repository, dispatcher)
        advanceUntilIdle()

        viewModel.uiState.test {
            // The shelf the initial sync left.
            awaitItem()

            viewModel.loadVideos()

            val refreshing = awaitItem()
            assertTrue("expected the shelf, got $refreshing", refreshing is TvUiState.Ready)
            assertTrue(
                "the shelf must say it is reloading",
                (refreshing as TvUiState.Ready).isRefreshing
            )

            advanceUntilIdle()
            assertFalse(
                "the shelf must settle when the reload is done",
                (awaitItem() as TvUiState.Ready).isRefreshing
            )
            assertEquals(true, repository.lastForce)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `two app opens shuffle the same cache differently`() = runTest {
        // "different every time the app is opened" is a property of the seed the state
        // holder starts with, not of the library: the cache never changed here.
        val cache = (1..12).map { row("v$it") }
        val repository = FakeRepository().apply {
            cached.value = cache
            syncedCount = cache.size
        }

        val first = TvHomeViewModel(repository, dispatcher, initialSeed = 1L)
        advanceUntilIdle()
        val firstOrder = (first.uiState.value as TvUiState.Ready).rails.first().videos.map { it.id }

        val second = TvHomeViewModel(repository, dispatcher, initialSeed = 2L)
        advanceUntilIdle()
        val secondOrder = (second.uiState.value as TvUiState.Ready).rails.first().videos.map { it.id }

        assertEquals(firstOrder.toSet(), secondOrder.toSet())
        assertNotEquals("a new app open must reshuffle the shelf", firstOrder, secondOrder)
    }

    @Test
    fun `a reload reshuffles the cache before the network answers`() = runTest {
        // On a TV that has just synced the network has nothing new to give, so a reload
        // that only waited for the walk would look broken. The order changes from what is
        // already cached, immediately.
        val cache = (1..12).map { row("v$it") }
        val repository = FakeRepository().apply {
            cached.value = cache
            syncedCount = cache.size
        }
        val viewModel = TvHomeViewModel(repository, dispatcher, initialSeed = 1L)
        advanceUntilIdle()

        viewModel.uiState.test {
            val before = (awaitItem() as TvUiState.Ready).rails.first().videos.map { it.id }

            viewModel.loadVideos()

            // Two emissions can describe the press (marking the reload, then the new
            // order); the one that matters is the first with a different order.
            var changed = awaitItem() as TvUiState.Ready
            while (changed.rails.first().videos.map { it.id } == before) {
                changed = awaitItem() as TvUiState.Ready
            }

            assertEquals(before.toSet(), changed.rails.first().videos.map { it.id }.toSet())
            assertTrue("the shelf must say it is reloading", changed.isRefreshing)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
