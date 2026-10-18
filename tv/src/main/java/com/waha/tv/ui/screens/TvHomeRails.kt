package com.waha.tv.ui.screens

import com.waha.domain.VideoItem
import kotlin.random.Random

/** One horizontal row of videos on the TV home screen. */
data class TvRail(
    val key: String,
    val title: String,
    val videos: List<VideoItem>
)

/**
 * Turns the library into the rows the TV home screen shows.
 *
 * Pure and deterministic for a given [Random], so it can be unit tested without a
 * device: the phone build's grid is one flat list, but a TV browses by rows. The
 * first row is always [ALL_RAIL_TITLE] — the whole library — and after it comes
 * one row per category.
 *
 * The categories are not a list written in the app: they are read from the
 * `category` column of the library rows that arrived from Supabase, de-duplicated
 * and sorted, so publishing a video in a new category makes that row appear
 * without a code change.
 *
 * Every row is shuffled **inside its own context** before it is capped at
 * [MAX_RAIL_ITEMS]: a category with hundreds of videos would otherwise always
 * show the same newest twenty, and a shelf that never changes is a shelf nobody
 * walks twice. The "الكل" row draws from the whole library, each category row
 * from that category only. The caller remembers the result, so the order is
 * stable while the viewer is on the screen and only changes with a new fetch.
 */
object TvRails {

    /**
     * How many videos one row holds.
     *
     * Twenty fills a shelf well past the three and a half cards a 720p screen
     * shows at once, so a row always has somewhere to scroll to — and stops a
     * huge library from turning into one endless row.
     */
    const val MAX_RAIL_ITEMS = 20

    /** Title of the first row: the whole library, whatever the category. */
    const val ALL_RAIL_TITLE = "الكل"

    /** Stable key of that row; no category row reuses it. */
    const val ALL_RAIL_KEY = "all"

    /**
     * Rows for [videos]: the "الكل" row first, then one per category, alphabetically.
     *
     * "الكل" holds a random twenty of the library, so a video without a category is
     * still reachable from the home screen — it simply has no row of its own. Blank
     * categories are skipped because they would produce a nameless row.
     */
    fun build(videos: List<VideoItem>, random: Random = Random.Default): List<TvRail> {
        if (videos.isEmpty()) return emptyList()

        val all = TvRail(
            key = ALL_RAIL_KEY,
            title = ALL_RAIL_TITLE,
            videos = videos.shuffled(random).take(MAX_RAIL_ITEMS)
        )

        val categoryRails = videos
            .mapNotNull { it.categoryKey?.trim()?.takeIf { key -> key.isNotEmpty() } }
            .distinct()
            // A category literally named "all" would collide with the first row's
            // list key; its videos are already in "الكل", so it gets no second row.
            .filter { category -> category != ALL_RAIL_KEY }
            .sorted()
            .map { category ->
                TvRail(
                    key = category,
                    title = category,
                    videos = videos
                        .filter { it.categoryKey?.trim() == category }
                        .shuffled(random)
                        .take(MAX_RAIL_ITEMS)
                )
            }

        return listOf(all) + categoryRails
    }
}
