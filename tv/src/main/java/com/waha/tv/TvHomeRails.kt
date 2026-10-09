package com.waha.tv

import com.waha.domain.VideoItem

/** One horizontal row of videos on the TV home screen. */
data class TvRail(
    val key: String,
    val title: String,
    val videos: List<VideoItem>
)

/** A category filter tile (the "الكل" tile plus one per category). */
data class TvCategoryTile(val key: String, val label: String)

/**
 * Turns the library into the rails the TV home screen shows.
 *
 * Pure and deterministic so it can be unit tested without a device: the phone
 * build's grid is one flat list, but a TV browses by rows, so the same videos
 * are grouped into a "latest" row followed by one row per category.
 */
object TvRails {

    /** Key of the "الكل" (all categories) tile. */
    const val ALL_CATEGORY_KEY = "all"

    /** How many videos one rail holds before it stops growing. */
    const val MAX_RAIL_ITEMS = 20

    /** Title of the first rail, which mirrors the order the library returns. */
    const val LATEST_RAIL_TITLE = "أحدث الإضافات"

    /** The first rail's key, kept distinct from real category keys. */
    const val LATEST_RAIL_KEY = "latest"

    /**
     * Rails for [videos]: "أحدث الإضافات" first, then one rail per category in
     * alphabetical order. Blank categories are skipped (they would produce a
     * nameless rail) and so are rails that would come out empty.
     */
    fun build(videos: List<VideoItem>): List<TvRail> {
        if (videos.isEmpty()) return emptyList()

        val rails = mutableListOf(
            TvRail(
                key = LATEST_RAIL_KEY,
                title = LATEST_RAIL_TITLE,
                videos = videos.take(MAX_RAIL_ITEMS)
            )
        )

        videos
            .mapNotNull { it.categoryKey?.trim()?.takeIf { key -> key.isNotEmpty() } }
            .distinct()
            .sorted()
            .forEach { category ->
                val inCategory = videos
                    .filter { it.categoryKey?.trim() == category }
                    .take(MAX_RAIL_ITEMS)
                if (inCategory.isNotEmpty()) {
                    rails += TvRail(key = category, title = category, videos = inCategory)
                }
            }

        return rails
    }

    /**
     * Applies the category filter exactly like the phone build's grid: "الكل"
     * keeps every rail, any other category keeps only its own rail (promoted to
     * the top, so the hero still features something from that category).
     */
    fun filter(rails: List<TvRail>, categoryKey: String?): List<TvRail> {
        if (categoryKey == null || categoryKey == ALL_CATEGORY_KEY) return rails
        val match = rails.firstOrNull { it.key == categoryKey } ?: return rails
        return listOf(match.copy(key = LATEST_RAIL_KEY, title = LATEST_RAIL_TITLE))
    }

    /** Filter tiles: "الكل" first, then the categories present in [videos]. */
    fun categoryTiles(videos: List<VideoItem>): List<TvCategoryTile> {
        val categories = videos
            .mapNotNull { it.categoryKey?.trim()?.takeIf { key -> key.isNotEmpty() } }
            .distinct()
            .sorted()
        return listOf(TvCategoryTile(ALL_CATEGORY_KEY, "الكل")) +
                categories.map { TvCategoryTile(it, it) }
    }
}
