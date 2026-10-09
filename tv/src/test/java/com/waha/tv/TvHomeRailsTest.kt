package com.waha.tv

import com.waha.domain.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvHomeRailsTest {

    private fun video(id: String, category: String? = null, title: String = "فيديو $id") =
        VideoItem(
            id = id,
            youtubeId = id,
            title = title,
            meta = category.orEmpty(),
            categoryKey = category
        )

    private val library = listOf(
        video("a", "أطفال"),
        video("b", "طبخ"),
        video("c", "أطفال"),
        video("d")
    )

    @Test
    fun `empty library has no rails`() {
        assertTrue(TvRails.build(emptyList()).isEmpty())
    }

    @Test
    fun `latest rail comes first and holds the whole library`() {
        val rails = TvRails.build(library)

        assertEquals(TvRails.LATEST_RAIL_KEY, rails.first().key)
        assertEquals(TvRails.LATEST_RAIL_TITLE, rails.first().title)
        assertEquals(listOf("a", "b", "c", "d"), rails.first().videos.map { it.id })
    }

    @Test
    fun `one rail per category, alphabetically, skipping uncategorised videos`() {
        val rails = TvRails.build(library)
        val categoryRails = rails.drop(1)

        // Arabic alphabetical order puts "أطفال" before "طبخ".
        assertEquals(listOf("أطفال", "طبخ"), categoryRails.map { it.key })
        assertEquals(listOf("a", "c"), categoryRails.first().videos.map { it.id })
        assertEquals(listOf("b"), categoryRails.last().videos.map { it.id })
        // The category-less video only appears in the latest rail.
        assertTrue(rails.none { rail -> rail.videos.any { it.id == "d" } && rail.key != TvRails.LATEST_RAIL_KEY })
    }

    @Test
    fun `blank categories do not produce a nameless rail`() {
        val rails = TvRails.build(listOf(video("a", "  "), video("b", "طبخ")))

        assertEquals(listOf(TvRails.LATEST_RAIL_KEY, "طبخ"), rails.map { it.key })
    }

    @Test
    fun `rails are capped so a huge library still browses comfortably`() {
        val many = (1..(TvRails.MAX_RAIL_ITEMS + 5)).map { video("v$it", "قسم") }
        val rails = TvRails.build(many)

        assertEquals(TvRails.MAX_RAIL_ITEMS, rails.first().videos.size)
        assertEquals(TvRails.MAX_RAIL_ITEMS, rails.last().videos.size)
    }

    @Test
    fun `filtering by all keeps every rail`() {
        val rails = TvRails.build(library)
        assertEquals(rails, TvRails.filter(rails, TvRails.ALL_CATEGORY_KEY))
        assertEquals(rails, TvRails.filter(rails, null))
    }

    @Test
    fun `filtering by a category keeps only that category`() {
        val rails = TvRails.build(library)
        val filtered = TvRails.filter(rails, "طبخ")

        // Exactly what the phone's grid does when its chip is tapped: only the
        // chosen category survives, promoted to the top so the hero uses it.
        assertEquals(1, filtered.size)
        assertEquals(TvRails.LATEST_RAIL_KEY, filtered.first().key)
        assertEquals(TvRails.LATEST_RAIL_TITLE, filtered.first().title)
        assertEquals(listOf("b"), filtered.first().videos.map { it.id })
    }

    @Test
    fun `filtering by an unknown category is a no-op`() {
        val rails = TvRails.build(library)
        assertEquals(rails, TvRails.filter(rails, "لا يوجد"))
    }

    @Test
    fun `tiles start with all and list the real categories`() {
        assertEquals(
            listOf(TvRails.ALL_CATEGORY_KEY, "أطفال", "طبخ"),
            TvRails.categoryTiles(library).map { it.key }
        )
        assertEquals("الكل", TvRails.categoryTiles(library).first().label)
    }
}
