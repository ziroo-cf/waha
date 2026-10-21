package com.waha.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * One cached row of the published library, as stored locally.
 *
 * [durationText] is formatted once, here at write time, instead of on every
 * read: the badge text used to be rebuilt with `String.format` for every card
 * on every recomposition (see the core performance audit, P2).
 */
@Entity(
    tableName = "videos",
    // The cache is always read newest-first; this index lets SQLite return the
    // list in order instead of sorting the whole table on every emission.
    indices = [Index(value = ["createdAt", "id"])]
)
data class VideoEntity(
    @PrimaryKey val id: String,
    val title: String,
    val thumbnail: String?,
    val category: String?,
    /** Length in seconds; 0 when the row carries no usable duration. */
    val durationSeconds: Long,
    /** Pre-formatted badge text ("4:30"), or null when there is nothing to show. */
    val durationText: String?,
    /** Supabase `created_at`, the first half of the keyset (ordering + paging). */
    val createdAt: String?,
    /**
     * Sync run that last saw this row. A walk that reaches the end of the
     * library deletes every row whose generation is older, which is how rows
     * unpublished upstream disappear locally without a huge `NOT IN (...)` list.
     */
    val syncGeneration: Long
)

/**
 * Bookkeeping for the last library sync, so the app can tell a complete cache
 * from a partial one and skip re-downloading rows it already has.
 */
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    /** When the last successful sync finished, in epoch millis. */
    val lastSyncAtMs: Long,
    /** When the cache was last known to be a complete snapshot of the library. */
    val lastFullSyncAtMs: Long,
    /** True when the local cache covers the whole library, not just a prefix. */
    val completeWalk: Boolean,
    /** Keyset position of the deepest row read by the last sync. */
    val lastCursorCreatedAt: String?,
    val lastCursorId: String?,
    val cachedVideoCount: Int
) {
    companion object {
        /** There is exactly one metadata row. */
        const val SINGLETON_ID = 1
    }
}

@Dao
interface VideoDao {

    /** The library, newest first. Room re-emits on every write. */
    @Query("SELECT * FROM videos ORDER BY createdAt DESC, id DESC")
    fun observeVideos(): Flow<List<VideoEntity>>

    /** Ids only, for the incremental sync's "have I seen this page?" check. */
    @Query("SELECT id FROM videos")
    suspend fun getIds(): List<String>

    @Query("SELECT COUNT(*) FROM videos")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertAll(videos: List<VideoEntity>)

    /** Drops rows a completed sync run did not see (unpublished upstream). */
    @Query("DELETE FROM videos WHERE syncGeneration <> :generation")
    suspend fun deleteNotInGeneration(generation: Long)

    @Query("DELETE FROM videos")
    suspend fun clear()
}

@Dao
interface SyncMetadataDao {

    @Query("SELECT * FROM sync_metadata WHERE id = ${SyncMetadataEntity.SINGLETON_ID}")
    suspend fun get(): SyncMetadataEntity?

    @Upsert
    suspend fun upsert(metadata: SyncMetadataEntity)
}

/**
 * The on-device library cache: the only place the screens read videos from.
 *
 * The database is created lazily and never opened on the caller's thread by
 * this class; Room performs the I/O when a DAO is actually queried, which
 * [VideoRepository] always does on a background dispatcher.
 */
@Database(
    entities = [VideoEntity::class, SyncMetadataEntity::class],
    version = 1,
    exportSchema = true
)
abstract class WahaDatabase : RoomDatabase() {

    abstract fun videoDao(): VideoDao

    abstract fun syncMetadataDao(): SyncMetadataDao

    companion object {

        private const val DATABASE_NAME = "waha-library.db"

        @Volatile
        private var appContext: Context? = null

        @Volatile
        private var instance: WahaDatabase? = null

        /** Called once, before any repository touches the cache. */
        fun install(context: Context) {
            appContext = context.applicationContext
        }

        /**
         * Opens (or reuses) the cache. Building the database object is cheap —
         * the file is only opened on the first query, which the repository
         * always issues off the main thread.
         */
        fun get(): WahaDatabase {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: build().also { instance = it }
            }
        }

        private fun build(): WahaDatabase {
            val context = appContext ?: error(
                "WahaDatabase is not installed: WahaCoreInitializer must run before " +
                    "the library cache is used."
            )
            return Room.databaseBuilder(context, WahaDatabase::class.java, DATABASE_NAME)
                .build()
        }
    }
}

/**
 * Installs the cache at process start.
 *
 * A library module cannot add an `Application` subclass to its hosts, so :core
 * ships this invisible provider instead: the merged manifest instantiates it
 * before the first activity, and by the time a screen builds its repository the
 * database has a context. This replaces nothing at startup — the provider holds
 * no resources and the database opens on first use.
 */
class WahaCoreInitializer : ContentProvider() {

    override fun onCreate(): Boolean {
        context?.let(WahaDatabase::install)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
