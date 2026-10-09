package com.waha.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the suggestion scorer that now lives in :core: the next episode wins
 * the top slot, series affinity ranks ahead of category, watched videos are
 * excluded, and ordering is deterministic (no A/B circling).
 */
class SuggestionsTest {

    private fun video(id: String, title: String, category: String? = null) = VideoItem(
        id = id,
        youtubeId = id.padEnd(11, 'x'),
        title = title,
        meta = category.orEmpty(),
        categoryKey = category
    )

    @Test
    fun `series tokens are extracted wherever the name sits in the title`() {
        assertEquals(
            setOf("tom", "sawyer", "river"),
            VideoSuggestionsEngine.seriesTokens("Tom Sawyer: The River Episode")
        )
        assertEquals(
            setOf("cartoon", "blue", "garden"),
            VideoSuggestionsEngine.seriesTokens("Cartoon Blue Episode 2 Season 4 | The Garden")
        )
        assertEquals(
            setOf("watch", "blue", "safari", "big", "adventure"),
            VideoSuggestionsEngine.seriesTokens("Watch Blue Safari | Episode 3 - A Big Adventure")
        )
        assertTrue(
            VideoSuggestionsEngine.seriesTokens("الحلقة ٥ | مغامرات زهرة").contains("زهرة")
        )
    }

    @Test
    fun `episode number is found even mid title`() {
        assertEquals(2, VideoSuggestionsEngine.episodeNumber("Cartoon Blue Episode 2 Season 4 | The Garden"))
        assertEquals(3, VideoSuggestionsEngine.episodeNumber("Watch Blue | Cartoon Blue Episode 3"))
        assertEquals(null, VideoSuggestionsEngine.episodeNumber("Tom Sawyer: The River Episode"))
        assertEquals(5, VideoSuggestionsEngine.episodeNumber("الحلقة ٥ | زهرة"))
    }

    @Test
    fun `next episode outscores everything else`() {
        val current = video("aaaaaaaaaaa", "Cartoon Blue Episode 2 Season 4 | The Garden")
        val nextEpisode = video("bbbbbbbbbbb", "Cartoon Blue Episode 3 Season 4 | A Beautiful Chapter")
        val sameSeries = video("ccccccccccc", "Cartoon Blue Collection")
        val sameCategory = video("ddddddddddd", "Different Show", category = "kids")

        assertTrue(
            VideoSuggestionsEngine.suggestionScore(current, nextEpisode) >
                VideoSuggestionsEngine.suggestionScore(current, sameSeries)
        )
        assertTrue(
            VideoSuggestionsEngine.suggestionScore(current, sameSeries) >
                VideoSuggestionsEngine.suggestionScore(current, sameCategory)
        )
    }

    @Test
    fun `more shared series words rank higher`() {
        val current = video("aaaaaaaaaaa", "Cartoon Blue Episode 2 Season 4")
        val twoShared = video("bbbbbbbbbbb", "Cartoon Blue Special")
        val oneShared = video("ccccccccccc", "Blue Collection")

        assertTrue(
            VideoSuggestionsEngine.suggestionScore(current, twoShared) >
                VideoSuggestionsEngine.suggestionScore(current, oneShared)
        )
    }

    @Test
    fun `next episode pins across season and subtitle shapes`() {
        val current = video("aaaaaaaaaaa", "Cartoon Blue Episode 2 Season 4 | The Garden")
        val next = video("bbbbbbbbbbb", "Cartoon Blue Episode 3 Season 4 | A Beautiful Chapter")
        val noise = video("ccccccccccc", "Other Show: Pilot")
        val all = listOf(current, noise, next)

        val suggestions = VideoSuggestionsEngine.build(current, all)

        assertEquals("Cartoon Blue Episode 3 Season 4 | A Beautiful Chapter", suggestions.first().title)
    }

    @Test
    fun `first suggestion keeps the same series name when there is no next episode`() {
        val current = video("aaaaaaaaaaa", "Tom Sawyer: The River Episode")
        val sameSeries = video("bbbbbbbbbbb", "Tom Sawyer: The Best Moments")
        val all = listOf(
            current,
            sameSeries,
            video("ccccccccccc", "Other Show: Pilot"),
            video("ddddddddddd", "Another Thing")
        )

        val suggestions = VideoSuggestionsEngine.build(current, all)

        assertEquals("Tom Sawyer: The Best Moments", suggestions.first().title)
    }

    @Test
    fun `numbered next episode takes the first slot ahead of same series`() {
        val current = video("aaaaaaaaaaa", "Tarzan Episode 2")
        val nextEpisode = video("bbbbbbbbbbb", "Tarzan Episode 3")
        val sameSeries = video("ccccccccccc", "Tarzan: The Best Moments")
        val all = listOf(current, sameSeries, nextEpisode)

        val suggestions = VideoSuggestionsEngine.build(current, all)

        assertEquals("Tarzan Episode 3", suggestions.first().title)
    }

    @Test
    fun `suggestions are deterministic across repeated calls`() {
        val current = video("aaaaaaaaaaa", "Cartoon Blue Episode 2")
        val all = listOf(
            current,
            video("bbbbbbbbbbb", "Cartoon Blue Episode 3"),
            video("ccccccccccc", "Cartoon Blue Special"),
            video("ddddddddddd", "Random Video"),
            video("eeeeeeeeeee", "Another Random")
        )

        val first = VideoSuggestionsEngine.build(current, all)
        val second = VideoSuggestionsEngine.build(current, all)

        assertEquals(first.map { it.id }, second.map { it.id })
    }

    @Test
    fun `current video is never suggested`() {
        val current = video("aaaaaaaaaaa", "Tom Sawyer: The River Episode")
        val all = listOf(current, video("bbbbbbbbbbb", "Tom Sawyer: The Best Moments"))

        val suggestions = VideoSuggestionsEngine.build(current, all)

        assertEquals(false, suggestions.any { it.id == current.id })
    }

    @Test
    fun `watched videos are skipped so A and B cannot suggest each other`() {
        val videoA = video("aaaaaaaaaaa", "Tom Sawyer: The River Episode")
        val videoB = video("bbbbbbbbbbb", "Tom Sawyer: The Best Moments")
        val fresh = video("ccccccccccc", "Other Show: Pilot")
        val all = listOf(videoA, videoB, fresh)

        // The user just came from B, so viewing A must not offer B again.
        val fromA = VideoSuggestionsEngine.build(videoA, all, watchedIds = setOf("bbbbbbbbbbb"))
        assertEquals(false, fromA.any { it.id == "bbbbbbbbbbb" })

        // And viewing B must not offer A back, breaking the endless cycle.
        val fromB = VideoSuggestionsEngine.build(videoB, all, watchedIds = setOf("aaaaaaaaaaa"))
        assertEquals(false, fromB.any { it.id == "aaaaaaaaaaa" })
    }

    @Test
    fun `suggestions stay non-empty when the whole library has been watched`() {
        val videoA = video("aaaaaaaaaaa", "Tom Sawyer: The River Episode")
        val videoB = video("bbbbbbbbbbb", "Tom Sawyer: The Best Moments")
        val all = listOf(videoA, videoB)

        val suggestions = VideoSuggestionsEngine.build(videoA, all, watchedIds = setOf("bbbbbbbbbbb"))

        assertEquals(true, suggestions.isNotEmpty())
    }

    @Test
    fun `diversify breaks long runs of one category`() {
        val ranked = listOf(
            video("aaaaaaaaaaa", "Kids One", category = "kids"),
            video("bbbbbbbbbbb", "Kids Two", category = "kids"),
            video("ccccccccccc", "Kids Three", category = "kids"),
            video("ddddddddddd", "Kids Four", category = "kids"),
            video("eeeeeeeeeee", "Songs One", category = "songs")
        )

        val mixed = VideoSuggestionsEngine.diversifyByCategory(ranked)

        // No three consecutive picks may share a category.
        mixed.windowed(3).forEach { triple ->
            val cats = triple.mapNotNull { it.categoryKey }.toSet()
            assertFalse(
                "run of same category: ${triple.map { it.title }}",
                cats.size == 1 && triple.all { it.categoryKey != null }
            )
        }
        // All videos are still present, nothing dropped.
        assertEquals(ranked.map { it.id }.toSet(), mixed.map { it.id }.toSet())
        // Songs, ranked last, must be pulled up out of the kids run.
        assertEquals("songs", mixed[2].categoryKey)
    }

    @Test
    fun `same-category videos rank above unrelated ones`() {
        val current = video("aaaaaaaaaaa", "Some Show", category = "kids")
        val sameCategory = video("bbbbbbbbbbb", "Other Kids Video", category = "kids")
        val unrelated = video("ccccccccccc", "Unrelated Video", category = "songs")

        assertTrue(
            VideoSuggestionsEngine.suggestionScore(current, sameCategory) >
                VideoSuggestionsEngine.suggestionScore(current, unrelated)
        )
    }

    @Test
    fun `suggestions mix categories instead of filling with one`() {
        val current = video("aaaaaaaaaaa", "Tom Sawyer: The River Episode", category = "stories")
        val all = listOf(
            current,
            video("bbbbbbbbbbb", "Tom Sawyer: The Best Moments", category = "stories"),
            video("ccccccccccc", "Stories One", category = "stories"),
            video("ddddddddddd", "Stories Two", category = "stories"),
            video("eeeeeeeeeee", "Stories Three", category = "stories"),
            video("fffffffffff", "Songs One", category = "songs"),
            video("ggggggggggg", "Nursery One", category = "nursery")
        )

        val suggestions = VideoSuggestionsEngine.build(current, all, count = 6)

        // With 4 stories ranked first, diversification must surface other
        // categories within the first three slots.
        val firstThreeCategories = suggestions.take(3).map { it.categoryKey }.toSet()
        assertEquals(true, firstThreeCategories.size >= 2)
        // Every category still appears in the final list.
        assertEquals(
            setOf("stories", "songs", "nursery"),
            suggestions.mapNotNull { it.categoryKey }.toSet()
        )
    }
}
