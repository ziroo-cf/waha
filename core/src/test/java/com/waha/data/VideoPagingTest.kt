package com.waha.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The library read is paged because a single PostgREST response is capped at
 * 1000 rows. It pages by keyset (`created_at`, `id`) rather than `OFFSET`, so a
 * row published while the walk is running can neither shift a page out from
 * under it nor make it read a row twice. These tests pin that contract without
 * touching the network.
 */
class VideoPagingTest {

    /** A stable fake library ordered exactly like the server orders it. */
    private class FakeLibrary(rows: List<VideoRow>) {

        /** Mutable so a test can publish a row while the walk is running. */
        val rows: MutableList<VideoRow> = rows.toMutableList()

        val cursors = mutableListOf<LibraryCursor?>()

        fun page(cursor: LibraryCursor?, limit: Long): List<VideoRow> {
            cursors += cursor
            val start = if (cursor == null) 0 else {
                val index = rows.indexOfFirst { it.id == cursor.id }
                // A vanished cursor ends the dataset instead of rewinding it.
                if (index < 0) return emptyList()
                index + 1
            }
            return rows.drop(start).take(limit.toInt())
        }
    }

    private fun row(index: Int): VideoRow = VideoRow(
        id = "v%04d".format(index),
        title = "فيديو $index",
        // Newest first: a lower index is a newer publication.
        createdAt = "2026-01-%02dT00:00:00Z".format(28 - (index % 28))
    )

    private fun library(size: Int): List<VideoRow> =
        (0 until size).map(::row).sortedWith(
            compareByDescending<VideoRow> { it.createdAt }.thenByDescending { it.id }
        )

    @Test
    fun `a library of one short page is read with a single request`() = runTest {
        val fake = FakeLibrary(library(40))

        val walk = collectLibraryKeyset(pageSize = 1000) { fake.page(it, 1000) }

        assertEquals(listOf<LibraryCursor?>(null), fake.cursors)
        assertEquals(40, walk.rows.size)
        assertTrue(walk.complete)
        assertEquals(walk.rows.last().id, walk.lastCursor?.id)
        assertEquals(walk.rows.last().createdAt, walk.lastCursor?.createdAt)
    }

    @Test
    fun `a library larger than the cap is walked cursor by cursor`() = runTest {
        val source = library(2500)
        val fake = FakeLibrary(source)

        val walk = collectLibraryKeyset(pageSize = 1000) { fake.page(it, 1000) }

        // Three requests: two full pages and a short one. No offset ever moves.
        assertEquals(3, fake.cursors.size)
        assertNull(fake.cursors.first())
        assertEquals(2500, walk.rows.size)
        assertEquals(source.map { it.id }, walk.rows.map { it.id })
        assertTrue(walk.complete)
    }

    @Test
    fun `a row published mid-walk is neither repeated nor skipped`() = runTest {
        val original = library(2500)
        val fake = FakeLibrary(original.toMutableList())

        var afterFirstPage = false
        val walk = collectLibraryKeyset(pageSize = 1000) { cursor ->
            val page = fake.page(cursor, 1000)
            if (!afterFirstPage) {
                // Something is published while the walk is running.
                afterFirstPage = true
                fake.rows.add(0, VideoRow(id = "new", title = "جديد", createdAt = "2026-02-01T00:00:00Z"))
            }
            page
        }

        // Every original row arrives exactly once: the newcomer is newer than the
        // cursor, so it is left for the next sync instead of skewing this one.
        assertEquals(original.size, walk.rows.size)
        assertEquals(original.size, walk.rows.map { it.id }.toSet().size)
        assertFalse(walk.rows.any { it.id == "new" })
    }

    @Test
    fun `an incremental walk stops at the first fully cached page`() = runTest {
        val source = library(2500)
        val fake = FakeLibrary(source)
        val cached = source.drop(1000).map { it.id }.toSet()

        val walk = collectLibraryKeyset(
            pageSize = 1000,
            stopAtKnownPage = true,
            isKnownPage = { page -> page.all { it.id in cached } }
        ) { fake.page(it, 1000) }

        // Page one held new rows, page two was entirely local: stop there.
        assertEquals(2, fake.cursors.size)
        assertFalse(walk.complete)
        assertEquals(2000, walk.rows.size)
    }

    @Test
    fun `an endless series of full pages is stopped by the safety valve`() = runTest {
        val fake = FakeLibrary(library(10_000))

        val walk = collectLibraryKeyset(pageSize = 2, maxPages = 5) { fake.page(it, 2) }

        assertEquals(5, fake.cursors.size)
        assertEquals(10, walk.rows.size)
        assertFalse(walk.complete)
    }

    @Test
    fun `a full page without a timestamp cannot advance the cursor`() = runTest {
        val fake = FakeLibrary((1..4).map { VideoRow(id = "v$it") })

        val walk = collectLibraryKeyset(pageSize = 2) { fake.page(it, 2) }

        // One page read, then the walk stops instead of looping on page one.
        assertEquals(1, fake.cursors.size)
        assertFalse(walk.complete)
        assertEquals(2, walk.rows.size)
    }

    @Test
    fun `an empty library is one short page and no rows`() = runTest {
        val fake = FakeLibrary(emptyList())

        val walk = collectLibraryKeyset(pageSize = 1000) { fake.page(it, 1000) }

        assertEquals(1, fake.cursors.size)
        assertEquals(0, walk.rows.size)
        assertTrue(walk.complete)
    }
}
