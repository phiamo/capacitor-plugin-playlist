package org.dwbn.plugins.playlist.offline

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Where offline data lives. Cache, download index and plugin metadata sit under
 * [Context.getNoBackupFilesDir] so Android backup / device transfer never copies them
 * (Story 59.4). Never `getDatabasePath`, never the cache dir.
 */
@UnstableApi
object OfflineStorage {
    private const val ROOT = "offline"
    private const val INDEX_DB = "offline_index.db"

    private var cache: SimpleCache? = null
    private var databaseProvider: DatabaseProvider? = null

    @JvmStatic
    fun rootDir(context: Context): File = File(context.noBackupFilesDir, ROOT).also { it.mkdirs() }

    @JvmStatic
    fun cacheDir(context: Context): File = File(rootDir(context), "cache")

    @JvmStatic
    fun metaFile(context: Context): File = File(rootDir(context), "meta.json")

    @Synchronized
    @JvmStatic
    fun databaseProvider(context: Context): DatabaseProvider =
        databaseProvider ?: NoBackupDatabaseProvider(context.applicationContext, rootDir(context))
            .also { databaseProvider = it }

    /** Process-wide [SimpleCache]; Media3 allows exactly one instance per folder. */
    @Synchronized
    @JvmStatic
    fun cache(context: Context): Cache =
        cache ?: SimpleCache(
            cacheDir(context).also { it.mkdirs() },
            NoOpCacheEvictor(),
            databaseProvider(context)
        ).also { cache = it }

    /** Cache read-only on top of [upstream]: playback never writes, a miss falls through. */
    @JvmStatic
    fun readOnlyDataSourceFactory(context: Context, upstream: DataSource.Factory): CacheDataSource.Factory =
        readOnlyDataSourceFactory(cache(context), upstream)

    @JvmStatic
    fun readOnlyDataSourceFactory(cache: Cache, upstream: DataSource.Factory): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setCacheKeyFactory(QueryIgnoringCacheKeyFactory)
            .setUpstreamDataSourceFactory(upstream)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** Playback data source: cache first, network only for what the cache does not hold. */
    @JvmStatic
    fun playbackDataSourceFactory(context: Context): DataSource.Factory =
        readOnlyDataSourceFactory(context, DefaultDataSource.Factory(context))

    /** Downloader data source: writes every fetched segment into the cache. */
    @JvmStatic
    fun readWriteDataSourceFactory(context: Context, upstream: DataSource.Factory): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache(context))
            .setCacheKeyFactory(QueryIgnoringCacheKeyFactory)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /**
     * Cache key that drops query and fragment: a resumed or re-signed URL (new `?token=`) reuses the
     * segments cached under the previous signature.
     */
    object QueryIgnoringCacheKeyFactory : CacheKeyFactory {
        override fun buildCacheKey(dataSpec: DataSpec): String =
            dataSpec.key ?: keyFor(dataSpec.uri.toString())

        @JvmStatic
        fun keyFor(uri: String): String {
            val end = uri.indexOfAny(charArrayOf('?', '#')).let { if (it < 0) uri.length else it }
            return uri.substring(0, end)
        }
    }

    /** SQLite index kept in [rootDir]; a [ContextWrapper] redirects the database path. */
    private class NoBackupDatabaseProvider(context: Context, dir: File) :
        SQLiteOpenHelper(NoBackupContext(context, dir), INDEX_DB, null, 1),
        DatabaseProvider {
        override fun onCreate(db: SQLiteDatabase) {}

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    }

    private class NoBackupContext(base: Context, private val dir: File) : ContextWrapper(base) {
        override fun getDatabasePath(name: String): File = File(dir, name)

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?
        ): SQLiteDatabase = SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
            errorHandler: android.database.DatabaseErrorHandler?
        ): SQLiteDatabase = SQLiteDatabase.openDatabase(
            getDatabasePath(name).path,
            factory,
            SQLiteDatabase.CREATE_IF_NECESSARY,
            errorHandler
        )
    }
}
