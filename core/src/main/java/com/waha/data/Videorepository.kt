package com.waha.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

/**
 * Reads the published library.
 *
 * Open so a test can stand in for the network: the browse screens' state machine
 * (loading, ready, error) is worth asserting without a live Supabase project.
 */
open class VideoRepository {

    /**
     * The whole approved library, newest first.
     *
     * PostgREST does not return an unbounded result: Supabase caps one response
     * at [PAGE_SIZE] rows (the project's `db-max-rows`, 1000 by default). Asking
     * once therefore silently truncated the library to its newest thousand rows —
     * whole categories published earlier never reached the app. This walks the
     * pages with `offset`/`limit` until a short page says the library ended.
     */
    open suspend fun getVideos(): List<VideoRow> =
        collectVideoPages { from, to ->
            SupabaseClientProvider.client.from("videos")
                .select(columns = LIBRARY_COLUMNS) {
                    filter {
                        eq("status", "approved")
                    }
                    // `created_at` is not unique; the id breaks ties so a row can be
                    // neither skipped nor repeated where two share a timestamp.
                    order(column = "created_at", order = Order.DESCENDING)
                    order(column = "id", order = Order.ASCENDING)
                    range(from = from, to = to)
                }
                .decodeList<VideoRow>()
        }

    companion object {

        /** Rows per request: Supabase's default cap for a single response. */
        const val PAGE_SIZE = 1000L

        /**
         * Safety valve: stop after this many pages even if the server keeps
         * answering with full ones, so a misbehaving endpoint cannot spin here
         * forever. Far above any realistic library.
         */
        const val MAX_PAGES = 50

        /**
         * Only the columns the app actually draws (plus the id). `status` stays in
         * the filter but is not needed in the payload; `channel_id` is not used.
         */
        private val LIBRARY_COLUMNS = Columns.list(
            "id", "title", "thumbnail", "category", "duration"
        )
    }
}

/**
 * Paged read of the library: asks [fetchPage] for consecutive ranges until a
 * page comes back shorter than [pageSize], and returns every row in order.
 *
 * Split out from [VideoRepository] so the loop — the part that used to lose
 * categories — is unit-tested without a network.
 */
internal suspend fun collectVideoPages(
    pageSize: Long = VideoRepository.PAGE_SIZE,
    maxPages: Int = VideoRepository.MAX_PAGES,
    fetchPage: suspend (from: Long, to: Long) -> List<VideoRow>
): List<VideoRow> {
    val all = ArrayList<VideoRow>()
    var from = 0L

    repeat(maxPages) {
        val page = fetchPage(from, from + pageSize - 1)
        all += page
        if (page.size.toLong() < pageSize) return all
        from += pageSize
    }

    return all
}
