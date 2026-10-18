package com.waha.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The library read is paged because a single PostgREST response is capped at
 * 1000 rows; before this loop existed the app only ever saw the newest thousand
 * and whole categories were missing. These tests pin the loop's contract without
 * touching the network.
 */
class VideoPagingTest {

    private fun row(id: Int) = VideoRow(id = "v$id", title = "فيديو $id")

    @Test
    fun `a library of one short page is read with a single request`() = runTest {
        val calls = mutableListOf<Pair<Long, Long>>()

        val rows = collectVideoPages(pageSize = 1000) { from, to ->
            calls += from to to
            (1..40).map(::row)
        }

        assertEquals(listOf(0L to 999L), calls)
        assertEquals(40, rows.size)
    }

    @Test
    fun `a library larger than the cap is read page by page, in order`() = runTest {
        val calls = mutableListOf<Pair<Long, Long>>()

        val total = 2500
        val rows = collectVideoPages(pageSize = 1000) { from, to ->
            calls += from to to
            (from.toInt() until minOf(from.toInt() + 1000, total)).map(::row)
        }

        // Two full pages and a short third one; the short page ends the loop.
        assertEquals(listOf(0L to 999L, 1000L to 1999L, 2000L to 2999L), calls)
        assertEquals(total, rows.size)
        assertEquals("v0", rows.first().id)
        assertEquals("v2499", rows.last().id)
    }

    @Test
    fun `an exact multiple of the page size asks once more for the empty tail`() = runTest {
        var calls = 0

        val rows = collectVideoPages(pageSize = 1000) { from, _ ->
            calls++
            if (from == 0L) (0 until 1000).map(::row) else emptyList()
        }

        assertEquals(2, calls)
        assertEquals(1000, rows.size)
    }

    @Test
    fun `an endless series of full pages is stopped by the safety valve`() = runTest {
        var calls = 0

        val rows = collectVideoPages(pageSize = 2, maxPages = 5) { from, _ ->
            calls++
            (from.toInt() until (from + 2).toInt()).map(::row)
        }

        assertEquals(5, calls)
        assertEquals(10, rows.size)
    }

    @Test
    fun `an empty library is one short page and no rows`() = runTest {
        var calls = 0

        val rows = collectVideoPages(pageSize = 1000) { _, _ ->
            calls++
            emptyList()
        }

        assertEquals(1, calls)
        assertEquals(0, rows.size)
    }
}
