package com.waha.data

import com.waha.domain.toVideoItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The pieces between the network and Room: the single-flight gate that stops two
 * screens from running the same walk, and the row → cache mapping that formats
 * the duration once instead of on every read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibrarySyncTest {

    /**
     * The repository owns a supervised scope, so a failed sync cannot cancel the
     * app-wide scope. The tests build the same shape.
     */
    private fun TestScope.flightScope() =
        CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

    @Test
    fun `concurrent callers share one sync`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var runs = 0
        val flight = SingleFlight<Int>(flightScope())

        val first = flight.run { runs++; gate.await(); 7 }
        val second = flight.run { runs++; gate.await(); 7 }

        gate.complete(Unit)

        assertEquals(listOf(7, 7), awaitAll(first, second))
        // The second caller joined the first instead of starting another walk.
        assertEquals(1, runs)
        assertTrue(first === second)
    }

    @Test
    fun `a finished sync is not reused`() = runTest {
        var runs = 0
        val flight = SingleFlight<Int>(flightScope())

        assertEquals(1, flight.run { ++runs }.await())
        assertEquals(2, flight.run { ++runs }.await())
        assertEquals(2, runs)
    }

    @Test
    fun `a failing sync does not poison the next one`() = runTest {
        var runs = 0
        val flight = SingleFlight<Int>(flightScope())

        val failure = runCatching { flight.run { runs++; error("لا يوجد اتصال") }.await() }
        assertTrue("the failing sync must surface its error", failure.isFailure)

        // The next caller starts a fresh walk instead of joining the failed one.
        assertEquals(2, flight.run { ++runs }.await())
        assertEquals(2, runs)
    }

    @Test
    fun `a row is formatted into the cache once`() {
        val entity = VideoRow(
            id = "youtube-1",
            title = "  ",
            thumbnail = " ",
            category = "",
            duration = (4 * 60 + 30).seconds,
            createdAt = "2026-01-02T03:04:05Z"
        ).toVideoEntity(generation = 42)

        assertEquals("youtube-1", entity.id)
        assertEquals("بدون عنوان", entity.title)
        assertNull(entity.thumbnail)
        assertNull(entity.category)
        assertEquals(270L, entity.durationSeconds)
        assertEquals("4:30", entity.durationText)
        assertEquals("2026-01-02T03:04:05Z", entity.createdAt)
        assertEquals(42L, entity.syncGeneration)

        // Reading it back is a field copy: no duration parsing, no formatting.
        assertEquals("4:30", entity.toVideoItem().durationText)
    }

    @Test
    fun `a row without a duration keeps its badge hidden`() {
        val entity = VideoRow(id = "v1", title = "فيديو").toVideoEntity(generation = 1)

        assertEquals(0L, entity.durationSeconds)
        assertNull(entity.durationText)
        assertNull(entity.toVideoItem().durationText)
    }

    @Test
    fun `a long video is formatted with hours`() {
        val entity = VideoRow(id = "v1", duration = (2 * 60 + 5).minutes).toVideoEntity(generation = 1)

        assertEquals("2:05:00", entity.durationText)
    }
}
