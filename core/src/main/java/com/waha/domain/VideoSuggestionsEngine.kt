package com.waha.domain

/** Finds an episode number anywhere in a title: "Episode 2", "الحلقة ٣", "Ep. 7". */
private val EPISODE_NUMBER_REGEX = Regex(
    """(?:episode|ep\.?|part|الحلقة|حلقه|حلقة|الجزء)\s*([0-9\u0660-\u0669\u06F0-\u06F9]+)""",
    RegexOption.IGNORE_CASE
)

/** Words that describe format rather than the series itself. */
private val SERIES_KEYWORDS = setOf(
    "episode", "ep", "part", "season", "الحلقة", "حلقه", "حلقة", "الجزء", "جزء", "الموسم", "موسم"
)

/** Filler words that must never link two titles together. */
private val SERIES_STOPWORDS = setOf(
    "the", "a", "an", "of", "in", "on", "and", "to", "for", "with", "at", "by", "from",
    "في", "من", "على", "و", "مع", "إلى"
)

private val NUMBER_TOKEN_REGEX = Regex("^[0-9\u0660-\u0669\u06F0-\u06F9]+$")

/**
 * Weights for the suggestion scorer. Series identity dominates, the immediate
 * next episode tops everything, and shared categories break ties.
 */
private const val NEXT_EPISODE_SCORE = 100
private const val SHARED_SERIES_TOKEN_SCORE = 10
private const val SHARED_CATEGORY_SCORE = 5
private const val SHARED_SERIES_CAP = 3

/** After this many picks from one category, force a pick from another. */
private const val MAX_PER_CATEGORY_RUN = 2

/** Default number of "شاهد المزيد" suggestions kept for one video. */
const val DEFAULT_SUGGESTION_COUNT = 20

/**
 * Ranks the "شاهد المزيد" rail shown under the player.
 *
 * The engine is UI-agnostic: it works purely on [VideoItem] lists, so the phone
 * and TV apps share exactly the same ordering. It prefers the next episode of
 * the current series, then videos sharing series words, then the category of
 * the current video, and it never suggests a video the viewer already watched
 * (which is what used to make A and B suggest each other forever).
 */
object VideoSuggestionsEngine {

    /** Arabic-Indic digits (٠-٩ / ۰-۹) are normalized so episode numbers parse uniformly. */
    private fun normalizeDigits(text: String): String = buildString(text.length) {
        for (ch in text) {
            append(
                when (ch) {
                    in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')
                    in '\u06F0'..'\u06F9' -> '0' + (ch - '\u06F0')
                    else -> ch
                }
            )
        }
    }

    /**
     * The episode number carried by a title, wherever it appears
     * ("Cartoon Blue Episode 2 Season 4 | The Garden" -> 2). Null when the title
     * has no numbered episode marker.
     */
    internal fun episodeNumber(title: String): Int? =
        EPISODE_NUMBER_REGEX.find(title.trim())?.groupValues?.get(1)
            ?.let { normalizeDigits(it).toIntOrNull() }

    /**
     * The series-identity words of a title: its significant tokens after dropping
     * separators, episode/season markers, bare numbers, and filler stopwords. The
     * name may sit anywhere in the title — start, middle, or end — so
     * "Watch Blue Safari | Cartoon Blue Episode 2" still yields {cartoon, blue,
     * safari, watch}. Empty when nothing significant remains.
     */
    internal fun seriesTokens(title: String): Set<String> =
        title.lowercase()
            .split(Regex("""[\s:|\-–—_.,!?()\[\]"]+"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it in SERIES_KEYWORDS }
            .filterNot { it in SERIES_STOPWORDS }
            .filterNot { NUMBER_TOKEN_REGEX.matches(it) }
            .toSet()

    /**
     * Scores one candidate against the current video. Higher is better.
     *
     * - Being the exact next episode of the same series dwarfs everything else.
     * - Each shared series word adds [SHARED_SERIES_TOKEN_SCORE], so "Cartoon Blue
     *   Episode 3..." (2 shared words) outranks a video sharing just "Blue".
     * - The shared category is a small boost, not a tier of its own.
     * - Deterministic given the same inputs: no shuffling, so the ordering is
     *   stable and testable, and the same pair of videos can never circle.
     */
    internal fun suggestionScore(current: VideoItem, candidate: VideoItem): Int {
        var score = 0

        val currentTokens = seriesTokens(current.title)
        val sharedTokens = seriesTokens(candidate.title).count { it in currentTokens }

        val currentEpisode = episodeNumber(current.title)
        val candidateEpisode = episodeNumber(candidate.title)
        if (currentEpisode != null && candidateEpisode == currentEpisode + 1 && sharedTokens > 0) {
            score += NEXT_EPISODE_SCORE
        }

        score += sharedTokens * SHARED_SERIES_TOKEN_SCORE

        if (current.categoryKey != null && current.categoryKey == candidate.categoryKey) {
            score += SHARED_CATEGORY_SCORE
        }

        return score
    }

    /**
     * Reorders [ranked] (best first) so no single category hogs the list: once
     * [MAX_PER_CATEGORY_RUN] consecutive picks share a category, the next slot
     * must go to a different one (or to a category-less video). Videos whose
     * category is unknown never count toward a run.
     */
    internal fun diversifyByCategory(ranked: List<VideoItem>): List<VideoItem> {
        val result = mutableListOf<VideoItem>()
        val remaining = ArrayDeque(ranked)
        while (remaining.isNotEmpty()) {
            val runCategory = result.lastOrNull()?.categoryKey
            var runLength = 0
            for (i in result.indices.reversed()) {
                if (result[i].categoryKey != null && result[i].categoryKey == runCategory) runLength++ else break
            }
            val blocked = runCategory != null && runLength >= MAX_PER_CATEGORY_RUN

            var pickIndex = if (blocked) {
                remaining.indexOfFirst { it.categoryKey != runCategory }
            } else {
                0
            }
            if (pickIndex == -1) pickIndex = 0 // everything left shares the category
            result += remaining.removeAt(pickIndex)
        }
        return result
    }

    /** Suggestion rail for [current], ordered and diversified, capped at [count]. */
    fun build(
        current: VideoItem,
        allVideos: List<VideoItem>,
        watchedIds: Set<String> = emptySet(),
        count: Int = DEFAULT_SUGGESTION_COUNT
    ): List<VideoItem> {
        // Skip the current video and everything already watched, so two videos can
        // never pin each other as suggestions (A -> B -> A -> ... forever).
        val pool = allVideos.filter { it.id != current.id && it.id !in watchedIds }

        // Never leave the suggestions empty when the history plus the current video
        // covers the whole library: fall back to the unfiltered pool.
        val effectivePool = if (pool.isEmpty()) {
            allVideos.filter { it.id != current.id }
        } else {
            pool
        }

        // Score every candidate once, then keep the best. The score already encodes
        // episode affinity, series identity, and category, so no hand-ordered tiers.
        val ranked = effectivePool
            .map { it to suggestionScore(current, it) }
            .sortedWith(compareByDescending<Pair<VideoItem, Int>> { (_, score) -> score }
                .thenBy { (candidate, _) -> candidate.id }) // stable tie-break by id
            .map { (candidate, _) -> candidate }

        // Keep the strongest series matches at the top but cap them so the list
        // still breathes with category and other picks.
        val currentTokens = seriesTokens(current.title)
        var seriesShown = 0
        val limited = mutableListOf<VideoItem>()
        for (candidate in ranked) {
            val sharesSeriesTokens = currentTokens.isNotEmpty() &&
                    seriesTokens(candidate.title).any { it in currentTokens }
            if (sharesSeriesTokens) {
                if (seriesShown >= SHARED_SERIES_CAP) continue
                seriesShown++
            }
            limited += candidate
        }

        // Spread categories so one topic never fills the whole rail.
        return diversifyByCategory(limited).take(count)
    }
}
