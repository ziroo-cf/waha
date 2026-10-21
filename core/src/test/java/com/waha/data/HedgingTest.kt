package com.waha.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Hedging is the core of the player start-up fix: extraction and Piped race, and
 * the first usable answer wins. Virtual time makes the "who wins" cases exact.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HedgingTest {

    private val headStart = 1_500L

    @Test
    fun `a fast primary wins and the backup never starts`() = runTest {
        var backupCalls = 0

        val result = firstNonNull(
            primary = { "extracted" },
            backup = { backupCalls++; "piped" },
            backupDelayMs = headStart
        )

        assertEquals("extracted", result)
        assertEquals("the backup must not cost a request when extraction works", 0, backupCalls)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun `a failing primary starts the backup immediately`() = runTest {
        var backupStartedAt = -1L

        val result = firstNonNull(
            primary = { null },
            backup = { backupStartedAt = testScheduler.currentTime; "piped" },
            backupDelayMs = headStart
        )

        assertEquals("piped", result)
        assertEquals("an early failure must not wait out the head start", 0L, backupStartedAt)
    }

    @Test
    fun `a slow primary loses to the backup after the head start`() = runTest {
        val result = firstNonNull(
            primary = { delay(5_000); "extracted" },
            backup = { "piped" },
            backupDelayMs = headStart
        )

        assertEquals("piped", result)
        assertEquals(headStart, testScheduler.currentTime)
    }

    @Test
    fun `both lanes failing yields null`() = runTest {
        val result = firstNonNull<String>(
            primary = { null },
            backup = { null },
            backupDelayMs = headStart
        )

        assertNull(result)
    }

    @Test
    fun `a throwing lane counts as a failure instead of escaping`() = runTest {
        val result = firstNonNull<String>(
            primary = { error("extraction broke") },
            backup = { "piped" },
            backupDelayMs = headStart
        )

        assertEquals("piped", result)
    }

    @Test
    fun `when the primary throws the backup does not wait`() = runTest {
        var backupStartedAt = -1L

        firstNonNull<String>(
            primary = { error("extraction broke") },
            backup = { backupStartedAt = testScheduler.currentTime; null },
            backupDelayMs = headStart
        )

        assertEquals(0L, backupStartedAt)
    }
}
