package com.waha.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlin.time.Duration

/**
 * Position of the last row read from the library.
 *
 * `created_at` alone is not unique, so the id travels with it and breaks ties —
 * the same pair the server orders by. This is what turns the library read from
 * an `OFFSET` walk (which skips or repeats rows whenever something is published
 * mid-read) into a keyset walk.
 */
data class LibraryCursor(val createdAt: String, val id: String) {

    companion object {
        /** Null when the row has no timestamp to page from. */
        fun of(row: VideoRow): LibraryCursor? {
            val createdAt = row.createdAt?.takeIf { it.isNotBlank() } ?: return null
            return LibraryCursor(createdAt = createdAt, id = row.id)
        }
    }
}

/** What one walk over the library produced. */
internal data class LibraryWalk(
    /** Every row read, in server order. */
    val rows: List<VideoRow>,
    /**
     * True when the walk reached the end of the library, so [rows] describe the
     * whole thing. A walk that stopped early (known page, page cap, or a row
     * without a timestamp) is not complete and must never prune the cache.
     */
    val complete: Boolean,
    /** Cursor of the last row read; null when nothing was read. */
    val lastCursor: LibraryCursor?
)

/**
 * Reads the library newest-first with keyset paging instead of `OFFSET`.
 *
 * [fetchPage] receives the cursor of the previous page (null for the first) and
 * returns up to `pageSize` rows ordered by `created_at DESC, id DESC`. The walk
 * ends on the first short page, or — when [stopAtKnownPage] is set and
 * [isKnownPage] confirms every row is already cached — on the first page that is
 * entirely local, which is what makes a routine refresh cost one request.
 *
 * [maxPages] is a safety valve so a misbehaving endpoint cannot spin forever.
 */
internal suspend fun collectLibraryKeyset(
    pageSize: Long = VideoRepository.PAGE_SIZE,
    maxPages: Int = VideoRepository.MAX_PAGES,
    stopAtKnownPage: Boolean = false,
    isKnownPage: suspend (List<VideoRow>) -> Boolean = { false },
    fetchPage: suspend (cursor: LibraryCursor?) -> List<VideoRow>
): LibraryWalk {
    val rows = ArrayList<VideoRow>()
    var cursor: LibraryCursor? = null

    repeat(maxPages) {
        val page = fetchPage(cursor)
        rows += page

        // A short page is the end of the library.
        if (page.size.toLong() < pageSize) {
            return LibraryWalk(rows, complete = true, lastCursor = page.lastOrNull()?.let(LibraryCursor::of) ?: cursor)
        }

        // Without a timestamp on the last row there is no cursor to continue from.
        val next = page.lastOrNull()?.let(LibraryCursor::of)
            ?: return LibraryWalk(rows, complete = false, lastCursor = cursor)

        cursor = next

        // Already-synced territory: everything below this page is in the cache.
        if (stopAtKnownPage && isKnownPage(page)) {
            return LibraryWalk(rows, complete = false, lastCursor = next)
        }
    }

    return LibraryWalk(rows, complete = false, lastCursor = cursor)
}

/**
 * Runs [block] once for every caller that asks while it is already running.
 *
 * The work lives in its own [scope], so a screen that goes away mid-refresh
 * (navigation, a cancelled pull-to-refresh) does not cancel the sync the other
 * screens are waiting on. Two browse screens opening at once — or the home
 * screen and the player both asking for the library — share one network walk.
 */
internal class SingleFlight<T>(private val scope: CoroutineScope) {

    private val lock = Any()
    private var inFlight: Deferred<T>? = null

    fun run(block: suspend () -> T): Deferred<T> = synchronized(lock) {
        val running = inFlight?.takeIf { it.isActive }
        if (running != null) {
            running
        } else {
            scope.async { block() }.also { inFlight = it }
        }
    }
}

/**
 * Maps a network row onto its cached form, formatting the duration badge here so
 * no screen ever has to.
 */
internal fun VideoRow.toVideoEntity(generation: Long): VideoEntity {
    val length = duration
    val usable = length != null && length > Duration.ZERO
    return VideoEntity(
        id = id,
        title = title?.takeIf { it.isNotBlank() } ?: "بدون عنوان",
        thumbnail = thumbnail?.takeIf { it.isNotBlank() },
        category = category?.takeIf { it.isNotBlank() },
        durationSeconds = if (usable) length.inWholeSeconds else 0L,
        durationText = if (usable) length.toVideoDurationText() else null,
        createdAt = createdAt?.takeIf { it.isNotBlank() },
        syncGeneration = generation
    )
}
