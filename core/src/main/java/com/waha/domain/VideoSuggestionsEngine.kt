package com.waha.domain

import java.util.concurrent.ConcurrentHashMap

/** Finds an episode number anywhere in a title: "Episode 2", "الحلقة ٣", "Ep. 7". */
private val EPISODE_NUMBER_REGEX = Regex(
    """(?:episode|ep\.?|part|الحلقة|حلقه|حلقة|الجزء)\s*([0-9\u0660-\u0669\u06F0-\u06F9]+)""",
    RegexOption.IGNORE_CASE
)

/** Separators a title is split on before its words are filtered. */
private val TITLE_SPLIT_REGEX = Regex("""[\s:|\-–—_.,!?()\[\]"]+""")

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
 * The parts of a title the scorer cares about, computed once.
 *
 * Tokenising a title used to happen several times per candidate on every build
 * (inside the scorer, again for the series cap, again in the tie-breakers). A
 * title only ever yields the same words and episode number, so they are derived
 * once and memoised by [VideoSuggestionsEngine.indexOf].
 */
internal class TitleIndex(val tokens: Set<String>, val episodeNumber: Int?)

/**
 * Ranks the "شاهد المزيد" rail shown under the player.
 *
 * The engine is UI-agnostic: it works purely on [VideoItem] lists, so the phone
 * and TV apps share exactly the same ordering. It prefers the next episode of
 * the current series, then videos sharing series words, then the category of
 * the current video, and it never suggests a video the viewer already watched
 * (which is what used to make A and B suggest each other forever).
 *
 * Everything expensive is derived once per title ([TitleIndex]) and the final
 * ordering is cached per (video, library, history) shape, because the player
 * used to rebuild the whole ranking every time a playback position was saved.
 */
object VideoSuggestionsEngine {

    /**
     * Titles are short and finite (one per library row), so the index is a plain
     * bounded memo. It is cleared wholesale at the bound instead of evicting one
     * entry at a time: a title is cheap to re-derive and a real library is far
     * below the bound anyway.
     */
    private const val MAX_INDEX_ENTRIES = 10_000

    /** How many finished rankings are kept: enough for a few open players. */
    private const val MAX_CACHED_RESULTS = 8

    private val titleIndexes = ConcurrentHashMap<String, TitleIndex>()

    private val resultCache = object : LinkedHashMap<CacheKey, List<VideoItem>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, List<VideoItem>>?) =
            size > MAX_CACHED_RESULTS
    }

    /** Identifies one ranking: same video, same library, same history, same cap. */
    private data class CacheKey(
        val videoId: String,
        val title: String,
        val categoryKey: String?,
        val count: Int,
        val watchedIds: Set<String>,
        val libraryIdentity: Int,
        val librarySize: Int
    )

    /** One scored candidate; keeps the shared-word count so the cap is free. */
    private class Candidate(
        val video: VideoItem,
        val score: Int,
        val sharedTokens: Int
    )

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

    private fun tokensOf(title: String): Set<String> =
        title.lowercase()
            .split(TITLE_SPLIT_REGEX)
            .filter { it.isNotEmpty() }
            .filterNot { it in SERIES_KEYWORDS }
            .filterNot { it in SERIES_STOPWORDS }
            .filterNot { NUMBER_TOKEN_REGEX.matches(it) }
            .toSet()

    private fun episodeOf(title: String): Int? =
        EPISODE_NUMBER_REGEX.find(title.trim())?.groupValues?.get(1)
            ?.let { normalizeDigits(it).toIntOrNull() }

    /**
     * The memoised [TitleIndex] of [title]. First access tokenises; every later
     * access (every pause, every rebuild, every candidate) is a hash lookup.
     */
    internal fun indexOf(title: String): TitleIndex = titleIndexes[title] ?: run {
        val computed = TitleIndex(tokensOf(title), episodeOf(title))
        if (titleIndexes.size >= MAX_INDEX_ENTRIES) titleIndexes.clear()
        titleIndexes.putIfAbsent(title, computed) ?: computed
    }

    /** Drops every memoised index and cached ranking (tests, library resets). */
    internal fun clearCaches() {
        titleIndexes.clear()
        synchronized(resultCache) { resultCache.clear() }
    }

    /**
     * The episode number carried by a title, wherever it appears
     * ("Cartoon Blue Episode 2 Season 4 | The Garden" -> 2). Null when the title
     * has no numbered episode marker.
     */
    internal fun episodeNumber(title: String): Int? = indexOf(title).episodeNumber

    /**
     * The series-identity words of a title: its significant tokens after dropping
     * separators, episode/season markers, bare numbers, and filler stopwords. The
     * name may sit anywhere in the title — start, middle, or end — so
     * "Watch Blue Safari | Cartoon Blue Episode 2" still yields {cartoon, blue,
     * safari, watch}. Empty when nothing significant remains.
     */
    internal fun seriesTokens(title: String): Set<String> = indexOf(title).tokens

    /** Words shared by two indexes, walking the smaller set once. */
    private fun sharedTokenCount(first: TitleIndex, second: TitleIndex): Int {
        if (first.tokens.isEmpty() || second.tokens.isEmpty()) return 0
        val (small, large) = if (first.tokens.size <= second.tokens.size) {
            first.tokens to second.tokens
        } else {
            second.tokens to first.tokens
        }
        var shared = 0
        for (token in small) if (token in large) shared++
        return shared
    }

    private fun scoreOf(
        current: TitleIndex,
        candidate: TitleIndex,
        sharedTokens: Int,
        currentCategory: String?,
        candidateCategory: String?
    ): Int {
        var score = 0
        val currentEpisode = current.episodeNumber
        if (currentEpisode != null &&
            candidate.episodeNumber == currentEpisode + 1 &&
            sharedTokens > 0
        ) {
            score += NEXT_EPISODE_SCORE
        }
        score += sharedTokens * SHARED_SERIES_TOKEN_SCORE
        if (currentCategory != null && currentCategory == candidateCategory) {
            score += SHARED_CATEGORY_SCORE
        }
        return score
    }

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
        val currentIndex = indexOf(current.title)
        val candidateIndex = indexOf(candidate.title)
        return scoreOf(
            current = currentIndex,
            candidate = candidateIndex,
            sharedTokens = sharedTokenCount(currentIndex, candidateIndex),
            currentCategory = current.categoryKey,
            candidateCategory = candidate.categoryKey
        )
    }

    /**
     * Reorders [ranked] (best first) so no single category hogs the list: once
     * [MAX_PER_CATEGORY_RUN] consecutive picks share a category, the next slot
     * must go to a different one (or to a category-less video). Videos whose
     * category is unknown never count toward a run.
     *
     * Linear: candidates are bucketed by category once, and the "next item from
     * another category" is the earliest front among those buckets, so no
     * repeated `indexOfFirst` scan over the remaining candidates.
     *
     * [limit] stops the walk once that many picks are made, so the player only
     * pays for the slots it shows — the first picks are identical either way.
     */
    internal fun diversifyByCategory(ranked: List<VideoItem>, limit: Int = ranked.size): List<VideoItem> {
        if (limit <= 0 || ranked.isEmpty()) return emptyList()
        if (ranked.size < 2) return ranked.take(limit)

        val buckets = LinkedHashMap<String?, ArrayDeque<Int>>()
        for (index in ranked.indices) {
            val category = ranked[index].categoryKey
            buckets.getOrPut(category) { ArrayDeque() }.addLast(index)
        }

        val taken = BooleanArray(ranked.size)
        val result = ArrayList<VideoItem>(ranked.size)
        var cursor = 0
        var runCategory: String? = null
        var runLength = 0

        fun frontOf(queue: ArrayDeque<Int>): Int? {
            while (queue.isNotEmpty() && taken[queue.first()]) queue.removeFirst()
            return queue.firstOrNull()
        }

        repeat(minOf(ranked.size, limit)) {
            while (cursor < ranked.size && taken[cursor]) cursor++

            val blocked = runCategory != null && runLength >= MAX_PER_CATEGORY_RUN
            var pick = -1
            if (blocked) {
                var best = Int.MAX_VALUE
                for ((category, queue) in buckets) {
                    if (category == runCategory) continue
                    val front = frontOf(queue) ?: continue
                    if (front < best) best = front
                }
                if (best != Int.MAX_VALUE) pick = best
            }
            if (pick < 0) {
                // Not blocked, or everything left shares the blocked category.
                if (cursor >= ranked.size) return result
                pick = cursor
            }

            taken[pick] = true
            val video = ranked[pick]
            result += video

            if (video.categoryKey != null && video.categoryKey == runCategory) {
                runLength++
            } else {
                runCategory = video.categoryKey
                runLength = if (video.categoryKey == null) 0 else 1
            }
        }
        return result
    }

    /**
     * Suggestion rail for [current], ordered and diversified, capped at [count].
     *
     * The result is cached by the ranking's inputs, so saving a playback position
     * (which does not change the library or the watched set) reuses the previous
     * rail instead of re-scoring thousands of videos.
     */
    fun build(
        current: VideoItem,
        allVideos: List<VideoItem>,
        watchedIds: Set<String> = emptySet(),
        count: Int = DEFAULT_SUGGESTION_COUNT
    ): List<VideoItem> {
        if (count <= 0 || allVideos.isEmpty()) return emptyList()

        // The current video is excluded from the pool anyway, so its own presence
        // in the history must not invalidate the cache: opening the player records
        // it, and that should be a hit, not a rebuild.
        val key = CacheKey(
            videoId = current.id,
            title = current.title,
            categoryKey = current.categoryKey,
            count = count,
            watchedIds = if (current.id in watchedIds) watchedIds - current.id else watchedIds,
            libraryIdentity = System.identityHashCode(allVideos),
            librarySize = allVideos.size
        )

        synchronized(resultCache) { resultCache[key] }?.let { return it }

        val result = buildUncached(current, allVideos, watchedIds, count)
        synchronized(resultCache) { resultCache[key] = result }
        return result
    }

    private fun buildUncached(
        current: VideoItem,
        allVideos: List<VideoItem>,
        watchedIds: Set<String>,
        count: Int
    ): List<VideoItem> {
        // Skip the current video and everything already watched, so two videos can
        // never pin each other as suggestions (A -> B -> A -> ... forever).
        var pool = allVideos.filter { it.id != current.id && it.id !in watchedIds }

        // Never leave the suggestions empty when the history plus the current video
        // covers the whole library: fall back to the unfiltered pool.
        if (pool.isEmpty()) pool = allVideos.filter { it.id != current.id }
        if (pool.isEmpty()) return emptyList()

        val currentIndex = indexOf(current.title)
        val scored = ArrayList<Candidate>(pool.size)
        for (candidate in pool) {
            val candidateIndex = indexOf(candidate.title)
            val shared = sharedTokenCount(currentIndex, candidateIndex)
            scored += Candidate(
                video = candidate,
                score = scoreOf(
                    current = currentIndex,
                    candidate = candidateIndex,
                    sharedTokens = shared,
                    currentCategory = current.categoryKey,
                    candidateCategory = candidate.categoryKey
                ),
                sharedTokens = shared
            )
        }

        // Stable order: best score first, id as the deterministic tie-break.
        scored.sortWith(
            compareByDescending<Candidate> { it.score }.thenBy { it.video.id }
        )

        // Keep the strongest series matches at the top but cap them so the list
        // still breathes with category and other picks. The whole ranking is
        // carried forward (it is one cheap pass over the scored array) because
        // dropping the tail here could drop the only other category from the rail.
        val limited = ArrayList<VideoItem>(scored.size)
        var seriesShown = 0
        for (candidate in scored) {
            if (candidate.sharedTokens > 0) {
                if (seriesShown >= SHARED_SERIES_CAP) continue
                seriesShown++
            }
            limited += candidate.video
        }

        // Spread categories so one topic never fills the whole rail, stopping as
        // soon as the rail is full.
        return diversifyByCategory(limited, count)
    }
}
