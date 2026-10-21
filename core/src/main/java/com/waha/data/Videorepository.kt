package com.waha.data

import com.waha.domain.VideoItem
import com.waha.domain.toVideoItem
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The library: Room is the single source of truth and Supabase only feeds it.
 *
 * Screens [observeVideos] the cache, which emits immediately whatever is stored
 * and again after every sync, then call [refresh] to pull the newest rows. That
 * ordering is what makes a warm start instant: the first frame is served from
 * disk while the network walk is still in flight.
 *
 * Open so a test can stand in for the cache and the network: the browse
 * screens' state machine (loading, ready, error) is worth asserting without a
 * live Supabase project.
 */
open class VideoRepository {

    /** Room is opened on first use and only ever queried off the main thread. */
    private val database: WahaDatabase by lazy { WahaDatabase.get() }

    private val remote: LibraryPageSource = SupabaseLibraryPageSource()

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default

    /**
     * Owned by the repository, not by a screen: a repository-level sync outlives
     * the caller that started it, which is what lets two screens share one walk.
     */
    private val syncScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val singleFlight = SingleFlight<Unit>(syncScope)

    /**
     * When this process last finished a sync. Only a success counts: a failed
     * walk must not silence the next refresh (or a retry button) for a minute.
     */
    @Volatile
    private var lastSuccessfulSyncMs = 0L

    /**
     * The cached library, newest first, re-emitted by Room after every write.
     *
     * Mapping runs on [Dispatchers.Default]; the collector only receives the
     * finished list, so a large library never decodes or formats on the main
     * thread.
     */
    open fun observeVideos(): Flow<List<VideoItem>> =
        database.videoDao().observeVideos()
            .map { entities -> entities.map { it.toVideoItem() } }
            .flowOn(defaultDispatcher)

    /**
     * The same cache, pre-shaped for the browse screens: the full list plus the
     * per-category rails. Building the rails is an O(n) pass over the library,
     * so it happens here, on a worker thread, and not while a frame is due.
     */
    open fun observeLibrary(): Flow<VideoLibrary> =
        observeVideos()
            .map { videos ->
                VideoLibrary(
                    videos = videos,
                    byCategory = videos
                        .groupBy { it.categoryKey.orEmpty() }
                        .filterKeys { it.isNotEmpty() }
                )
            }
            .flowOn(defaultDispatcher)

    /**
     * Syncs the library into Room.
     *
     * Concurrent callers share a single walk (see [SingleFlight]), and a routine
     * refresh inside [MIN_SYNC_INTERVAL_MS] is skipped unless [force] is set —
     * pull-to-refresh passes `force = true`.
     */
    open suspend fun refresh(force: Boolean = false) {
        if (!force && !isRefreshDue()) return
        singleFlight.run { syncLibrary(force) }.await()
    }

    private fun isRefreshDue(): Boolean {
        val last = lastSuccessfulSyncMs
        if (last == 0L) return true
        return System.currentTimeMillis() - last >= MIN_SYNC_INTERVAL_MS
    }

    /**
     * Walks the library page by page into Room.
     *
     * Every request is a keyset page (`created_at`, `id`) instead of an
     * `OFFSET` slice, so rows published while the walk is running can neither
     * shift a page out from under it nor be read twice. The whole walk is
     * written at the end: a network failure half-way leaves the previous cache
     * untouched, and the next attempt simply starts over.
     */
    private suspend fun syncLibrary(force: Boolean) = withContext(ioDispatcher) {
        val startedAt = System.currentTimeMillis()

        val dao = database.videoDao()
        val metadataDao = database.syncMetadataDao()
        val metadata = metadataDao.get()

        val cacheWasComplete = metadata?.completeWalk == true
        val lastFullSyncAt = metadata?.lastFullSyncAtMs ?: 0L
        val fullWalk = force || !cacheWasComplete || startedAt - lastFullSyncAt >= FULL_SYNC_INTERVAL_MS

        // Only an incremental walk needs the local ids, and only to decide when
        // it has reached rows the cache already holds.
        val knownIds: Set<String> = if (fullWalk) emptySet() else dao.getIds().toHashSet()

        val walk = collectLibraryKeyset(
            pageSize = PAGE_SIZE,
            maxPages = MAX_PAGES,
            stopAtKnownPage = !fullWalk,
            isKnownPage = { page -> page.all { it.id in knownIds } }
        ) { cursor -> remote.fetchPage(cursor, PAGE_SIZE) }

        // Formatting and mapping happen once, here, on a worker thread.
        val entities = withContext(defaultDispatcher) {
            walk.rows.map { it.toVideoEntity(startedAt) }
        }
        entities.chunked(UPSERT_CHUNK).forEach { dao.upsertAll(it) }

        // Only a walk that reached the end knows the whole library, so only it
        // may drop rows that were unpublished upstream.
        if (walk.complete) {
            dao.deleteNotInGeneration(startedAt)
        }

        val finishedAt = System.currentTimeMillis()
        lastSuccessfulSyncMs = finishedAt
        metadataDao.upsert(
            SyncMetadataEntity(
                lastSyncAtMs = finishedAt,
                lastFullSyncAtMs = if (walk.complete) startedAt else lastFullSyncAt,
                completeWalk = walk.complete || (cacheWasComplete && walk.rows.isNotEmpty()),
                lastCursorCreatedAt = walk.lastCursor?.createdAt,
                lastCursorId = walk.lastCursor?.id,
                cachedVideoCount = dao.count()
            )
        )
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

        /** Rows written per transaction, so a big first sync does not hold the
         *  write lock for one long statement. */
        internal const val UPSERT_CHUNK = 500

        /** A routine refresh is skipped if one started this recently. */
        internal const val MIN_SYNC_INTERVAL_MS = 60_000L

        /**
         * A full walk (which reconciles deletions) runs at most this often;
         * everything in between is a cheap incremental check of the newest rows.
         */
        internal const val FULL_SYNC_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
}

/**
 * The browse screens' data, built once per cache change: the full library plus
 * its category rails.
 */
data class VideoLibrary(
    val videos: List<VideoItem>,
    val byCategory: Map<String, List<VideoItem>>
)

/**
 * One page of the library, ordered by `created_at DESC, id DESC`.
 *
 * Split out from [VideoRepository] so the keyset walk is unit-tested without a
 * network and without Room.
 */
internal interface LibraryPageSource {
    suspend fun fetchPage(cursor: LibraryCursor?, limit: Long): List<VideoRow>
}

/**
 * Reads the published library from Supabase with keyset pagination.
 *
 * The `or`/`and` pair is the tuple comparison
 * `(created_at, id) < (cursor.createdAt, cursor.id)` in descending order, which
 * PostgREST cannot express as a row constructor. Only the columns the app draws
 * are requested, plus `created_at` because it is the ordering key.
 */
internal class SupabaseLibraryPageSource : LibraryPageSource {

    override suspend fun fetchPage(cursor: LibraryCursor?, limit: Long): List<VideoRow> =
        SupabaseClientProvider.client.from("videos")
            .select(columns = LIBRARY_COLUMNS) {
                filter { applyLibraryKeyset(cursor) }
                order(column = "created_at", order = Order.DESCENDING)
                order(column = "id", order = Order.DESCENDING)
                limit(limit)
            }
            .decodeList<VideoRow>()

    companion object {
        /** Only the columns the app actually draws (plus the ordering key). */
        private val LIBRARY_COLUMNS = Columns.list(
            "id", "title", "thumbnail", "category", "duration", "created_at"
        )
    }
}

/**
 * The keyset predicate: `status = 'approved'` plus, after the first page,
 * `(created_at, id) < (cursor.createdAt, cursor.id)` — written as the `or`/`and`
 * pair PostgREST understands because it has no row-constructor comparison.
 *
 * Kept as its own function so the exact query shape is unit-tested instead of
 * being trusted at runtime.
 */
internal fun PostgrestFilterBuilder.applyLibraryKeyset(cursor: LibraryCursor?) {
    eq("status", "approved")
    if (cursor == null) return
    or {
        lt("created_at", cursor.createdAt)
        and {
            eq("created_at", cursor.createdAt)
            lt("id", cursor.id)
        }
    }
}
