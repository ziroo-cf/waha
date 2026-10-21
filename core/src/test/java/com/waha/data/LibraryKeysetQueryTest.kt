package com.waha.data

import io.github.jan.supabase.postgrest.PropertyConversionMethod
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The keyset predicate is the one piece of SQL-ish text the app sends that a
 * compiler cannot check. PostgREST has no row-constructor comparison, so the
 * tuple `(created_at, id) < (…)` has to be written as `or=(a.lt.x,and(a.eq.x,b.lt.y))`;
 * a typo here would silently return the same page forever.
 */
class LibraryKeysetQueryTest {

    private fun builder() = PostgrestFilterBuilder(PropertyConversionMethod.NONE)

    @Test
    fun `the first page only filters by status`() {
        val filter = builder().apply { applyLibraryKeyset(null) }

        assertEquals(listOf("eq.approved"), filter.params["status"])
        assertNull(filter.params["or"])
    }

    @Test
    fun `later pages compare the (created_at, id) tuple`() {
        val cursor = LibraryCursor(createdAt = "2026-01-02T03:04:05Z", id = "abc")

        val filter = builder().apply { applyLibraryKeyset(cursor) }

        assertEquals(
            listOf("(created_at.lt.2026-01-02T03:04:05Z,and(created_at.eq.2026-01-02T03:04:05Z,id.lt.abc))"),
            filter.params["or"]
        )
    }
}
