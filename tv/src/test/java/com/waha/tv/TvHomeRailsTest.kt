package com.waha.tv

import com.waha.domain.VideoItem
import com.waha.tv.ui.screens.TvRails
import kotlin.random.Random
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
    fun `empty library has no rows`() {
        assertTrue(TvRails.build(emptyList()).isEmpty())
    }

    @Test
    fun `the all row comes first and holds the whole library`() {
        val rails = TvRails.build(library, Random(7))

        val all = rails.first()
        assertEquals(TvRails.ALL_RAIL_KEY, all.key)
        assertEquals("الكل", all.title)
        // Every video is reachable from "الكل" — including the uncategorised one,
        // which has no category row of its own.
        assertEquals(setOf("a", "b", "c", "d"), all.videos.map { it.id }.toSet())
    }

    @Test
    fun `one row per category from the data, alphabetically, and no latest row`() {
        val rails = TvRails.build(library, Random(7))

        // Arabic alphabetical order puts "أطفال" before "طبخ". These names are not
        // written in the app: they are whatever the library's category column says.
        assertEquals(listOf(TvRails.ALL_RAIL_KEY, "أطفال", "طبخ"), rails.map { it.key })
        assertEquals(listOf("الكل", "أطفال", "طبخ"), rails.map { it.title })
        assertEquals(setOf("a", "c"), rails[1].videos.map { it.id }.toSet())
        assertEquals(listOf("b"), rails[2].videos.map { it.id })
        // There is no "latest additions" shelf: "الكل" replaces it.
        assertTrue(rails.none { it.title == "أحدث الإضافات" })
    }

    @Test
    fun `a category the app has never seen gets its own row`() {
        val rails = TvRails.build(listOf(video("a", "علوم"), video("b", "أطفال")), Random(7))

        assertEquals(listOf(TvRails.ALL_RAIL_KEY, "أطفال", "علوم"), rails.map { it.key })
        assertEquals(listOf("a"), rails.last().videos.map { it.id })
    }

    @Test
    fun `a video without a category has no row of its own but stays in the all row`() {
        val rails = TvRails.build(library, Random(7))

        assertTrue(rails.drop(1).none { rail -> rail.videos.any { it.id == "d" } })
        assertTrue(rails.first().videos.any { it.id == "d" })
    }

    @Test
    fun `blank categories do not produce a nameless row`() {
        val rails = TvRails.build(listOf(video("a", "  "), video("b", "طبخ")), Random(7))

        assertEquals(listOf(TvRails.ALL_RAIL_KEY, "طبخ"), rails.map { it.key })
    }

    @Test
    fun `every row holds twenty videos`() {
        val many = (1..(TvRails.MAX_RAIL_ITEMS + 5)).map { video("v$it", "قسم") }

        assertEquals(20, TvRails.MAX_RAIL_ITEMS)
        TvRails.build(many, Random(7)).forEach { rail ->
            assertEquals(rail.key, TvRails.MAX_RAIL_ITEMS, rail.videos.size)
        }
    }

    @Test
    fun `the cap is per row, so one big category cannot eat another`() {
        val many = (1..(TvRails.MAX_RAIL_ITEMS + 5)).map { video("a$it", "أطفال") } +
                (1..(TvRails.MAX_RAIL_ITEMS + 5)).map { video("b$it", "طبخ") }
        val rails = TvRails.build(many, Random(7))

        assertEquals(3, rails.size)
        rails.forEach { rail -> assertEquals(TvRails.MAX_RAIL_ITEMS, rail.videos.size) }
        // Each category row is drawn from its own category, never from the other one.
        assertTrue(rails[1].videos.all { it.id.startsWith("a") })
        assertTrue(rails[2].videos.all { it.id.startsWith("b") })
    }

    @Test
    fun `a category row is a random sample, not always its newest twenty`() {
        val many = (1..30).map { video("v$it", "رسوم متحركة") }

        val rail = TvRails.build(many, Random(3)).first { it.key == "رسوم متحركة" }

        val newestTwenty = (1..20).map { "v$it" }.toSet()
        assertEquals(TvRails.MAX_RAIL_ITEMS, rail.videos.size)
        assertEquals(rail.videos.size, rail.videos.map { it.id }.toSet().size)
        assertTrue(
            "the row must not be the same newest twenty every time",
            rail.videos.any { it.id !in newestTwenty }
        )
    }

    @Test
    fun `the all row is a random sample of the whole library`() {
        val many = (1..30).map { video("v$it", "رسوم متحركة") }

        val all = TvRails.build(many, Random(3)).first()

        val newestTwenty = (1..20).map { "v$it" }.toSet()
        assertEquals(TvRails.MAX_RAIL_ITEMS, all.videos.size)
        assertTrue(all.videos.any { it.id !in newestTwenty })
    }

    @Test
    fun `the shuffle sits inside each row's own context`() {
        // Two categories of the same size: neither row may borrow the other's videos,
        // and with the same seed two builds produce the same shelves.
        val many = (1..10).map { video("a$it", "أطفال") } + (1..10).map { video("b$it", "طبخ") }

        val first = TvRails.build(many, Random(11))
        val second = TvRails.build(many, Random(11))

        assertEquals(first.map { it.videos.map(VideoItem::id) }, second.map { it.videos.map(VideoItem::id) })
        assertTrue(first[1].videos.all { it.categoryKey == "أطفال" })
        assertTrue(first[2].videos.all { it.categoryKey == "طبخ" })
    }
}
