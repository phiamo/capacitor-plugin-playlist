package org.dwbn.plugins.playlist.offline

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@UnstableApi
class OfflineStorageTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private class ThrowingUpstream : DataSource {
        override fun addTransferListener(transferListener: TransferListener) {}

        override fun open(dataSpec: DataSpec): Long = throw IOException("offline")

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = throw IOException("offline")

        override fun getUri(): Uri? = null

        override fun close() {}
    }

    private fun readAll(source: DataSource, spec: DataSpec): ByteArray {
        val out = ByteArrayOutputStream()
        source.open(spec)
        try {
            val buf = ByteArray(16)
            while (true) {
                val n = source.read(buf, 0, buf.size)
                if (n == C.RESULT_END_OF_INPUT) break
                out.write(buf, 0, n)
            }
        } finally {
            source.close()
        }
        return out.toByteArray()
    }

    @Test
    fun readOnlyFactory_servesCachedSegmentForReSignedUrl_andNeverWritesOnMiss() {
        val cache = SimpleCache(
            tmp.newFolder("cache"),
            NoOpCacheEvictor(),
            StandaloneDatabaseProvider(RuntimeEnvironment.getApplication())
        )
        try {
            val bytes = byteArrayOf(1, 2, 3, 4)
            val key = OfflineStorage.QueryIgnoringCacheKeyFactory.keyFor("https://cdn.example/a/seg0.ts?token=1")
            val hole = cache.startReadWrite(key, 0, bytes.size.toLong())
            val file = cache.startFile(key, 0, bytes.size.toLong())
            file.writeBytes(bytes)
            cache.commitFile(file, bytes.size.toLong())
            cache.releaseHoleSpan(hole)
            cache.applyContentMetadataMutations(
                key,
                ContentMetadataMutations().also { ContentMetadataMutations.setContentLength(it, bytes.size.toLong()) }
            )
            val spaceBefore = cache.cacheSpace

            // Hit: different query, upstream would throw.
            val hit = OfflineStorage.readOnlyDataSourceFactory(cache) { ThrowingUpstream() }.createDataSource()
            val read = readAll(hit, DataSpec(Uri.parse("https://cdn.example/a/seg0.ts?token=2")))
            assertArrayEquals(bytes, read)

            // Miss: upstream serves it, the read-only cache must not store it.
            val missUpstream = DataSource.Factory { ByteArrayDataSource(byteArrayOf(9, 9)) }
            val miss = OfflineStorage.readOnlyDataSourceFactory(cache, missUpstream).createDataSource()
            val missBytes = readAll(miss, DataSpec(Uri.parse("https://cdn.example/b/other.ts?token=1")))
            assertArrayEquals(byteArrayOf(9, 9), missBytes)
            assertFalse(cache.keys.contains("https://cdn.example/b/other.ts"))
            assertEquals(spaceBefore, cache.cacheSpace)

            // Miss with a dead network surfaces the error instead of fabricating data.
            val dead = OfflineStorage.readOnlyDataSourceFactory(cache) { ThrowingUpstream() }.createDataSource()
            assertThrows(IOException::class.java) {
                dead.open(DataSpec(Uri.parse("https://cdn.example/c/none.ts")))
            }
        } finally {
            cache.release()
        }
    }

    @Test
    fun cacheIndexAndMetadata_liveUnderNoBackupFilesDir() {
        val context = RuntimeEnvironment.getApplication()
        val noBackup = context.noBackupFilesDir.canonicalPath

        OfflineStorage.cache(context)
        OfflineStorage.databaseProvider(context).writableDatabase.close()

        assertTrue(OfflineStorage.cacheDir(context).canonicalPath.startsWith(noBackup))
        assertTrue(OfflineStorage.metaFile(context).canonicalPath.startsWith(noBackup))
        assertTrue(OfflineStorage.cacheDir(context).isDirectory)
        assertTrue(java.io.File(OfflineStorage.rootDir(context), "offline_index.db").exists())
        assertTrue(!java.io.File(context.getDatabasePath("offline_index.db").path).exists())
    }
}
