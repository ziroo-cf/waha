package com.waha.domain

/**
 * One entry in the watch history: which video, when it was last opened, and how
 * far the viewer got.
 *
 * The history used to be just an id plus a timestamp; a "continue watching"
 * rail needs the playback position and the video's length too, so they travel
 * with the entry.
 */
data class WatchHistoryItem(
    val videoId: String,
    val watchedAtMs: Long,
    val lastPositionMs: Long = 0L,
    val durationMs: Long = 0L
) {
    /** How much of the video is watched, 0f..1f; 0f when the length is unknown. */
    val progressFraction: Float
        get() = if (durationMs <= 0L) 0f
        else (lastPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/**
 * The rules that decide whether a partly-watched video belongs in
 * "أكمل المشاهدة" (Continue Watching).
 *
 * Pure and deterministic so it can be unit tested without a device: a video the
 * viewer barely opened (≤5%) is noise, and one that is essentially finished
 * (≥90%) should not be offered again.
 */
object ContinueWatching {

    /** Below this fraction the video counts as "not really started". */
    const val MIN_PROGRESS_FRACTION = 0.05f

    /** At or above this fraction the video counts as watched to the end. */
    const val MAX_PROGRESS_FRACTION = 0.90f

    /** How many entries the rail shows at most. */
    const val MAX_ITEMS = 20

    /**
     * True when [item] was paused part-way: strictly more than
     * [MIN_PROGRESS_FRACTION] and strictly less than [MAX_PROGRESS_FRACTION] of
     * a known-length video.
     */
    fun isInProgress(item: WatchHistoryItem): Boolean {
        if (item.durationMs <= 0L) return false
        val fraction = item.progressFraction
        return fraction > MIN_PROGRESS_FRACTION && fraction < MAX_PROGRESS_FRACTION
    }

    /**
     * Position playback should start from: the saved position when the video is
     * genuinely in progress, otherwise 0 (fresh start).
     */
    fun resumePositionMs(positionMs: Long, durationMs: Long): Long {
        if (positionMs <= 0L) return 0L
        val unfinished = isInProgress(
            WatchHistoryItem(
                videoId = "",
                watchedAtMs = 0L,
                lastPositionMs = positionMs,
                durationMs = durationMs
            )
        )
        return if (unfinished) positionMs else 0L
    }
}

/**
 * The "أكمل المشاهدة" list: only the videos paused part-way, newest first.
 *
 * An extension function on the plain list so both UI modules (and the tests)
 * can call it without touching the stores.
 */
fun List<WatchHistoryItem>.filterContinueWatching(
    limit: Int = ContinueWatching.MAX_ITEMS
): List<WatchHistoryItem> =
    filter { ContinueWatching.isInProgress(it) }
        .sortedByDescending { it.watchedAtMs }
        .take(limit)

/**
 * Joins the "أكمل المشاهدة" entries back onto the library's metadata.
 *
 * The history only stores ids (a video may not even be in the current library
 * page), so the caller passes the library and gets back the full [VideoItem]s,
 * in the order [filterContinueWatching] decided.
 */
fun List<VideoItem>.continueWatching(
    entries: List<WatchHistoryItem>,
    limit: Int = ContinueWatching.MAX_ITEMS
): List<VideoItem> {
    if (isEmpty() || entries.isEmpty()) return emptyList()
    val byId = associateBy { it.id }
    return entries.filterContinueWatching(limit).mapNotNull { byId[it.videoId] }
}
