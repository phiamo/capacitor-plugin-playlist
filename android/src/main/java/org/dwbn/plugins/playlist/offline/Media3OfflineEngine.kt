package org.dwbn.plugins.playlist.offline

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import java.io.ByteArrayOutputStream
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** [OfflineEngine] on Media3 `DownloadManager` / `DownloadService` with the no-backup cache and index. */
@UnstableApi
class Media3OfflineEngine(context: Context) : OfflineEngine {
    private val appContext: Context = context.applicationContext
    private val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)

    override var listener: OfflineEngine.Listener? = null

    @Volatile
    private var created: DownloadManager? = null

    /**
     * Media3's [DownloadManager] must be created and mutated from one thread: the main looper.
     * Created on first use; off-main callers hop to main and wait. No lock is held while waiting, so
     * the service creating it on main cannot deadlock against a worker. Also used by
     * [OfflineDownloadService].
     */
    val manager: DownloadManager
        get() = created ?: onMain { created ?: createManager().also { created = it } }

    private fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return block()
        }
        val latch = CountDownLatch(1)
        val result = AtomicReference<Result<T>>()
        Handler(Looper.getMainLooper()).post {
            result.set(runCatching(block))
            latch.countDown()
        }
        try {
            if (!latch.await(MAIN_HOP_TIMEOUT_S, TimeUnit.SECONDS)) {
                throw IllegalStateException("main thread did not respond")
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("interrupted waiting for main thread")
        }
        return result.get().getOrThrow()
    }

    private var tickerHandler: Handler? = null
    private var tickerRunning = false

    private fun createManager(): DownloadManager {
        val factory = DefaultDownloaderFactory(
            OfflineStorage.readWriteDataSourceFactory(appContext, http),
            Executors.newFixedThreadPool(DOWNLOAD_THREADS)
        )
        val created = DownloadManager(
            appContext,
            DefaultDownloadIndex(OfflineStorage.databaseProvider(appContext)),
            factory
        )
        created.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                map(download, finalException)?.let { listener?.onChanged(it) }
                if (download.state == Download.STATE_DOWNLOADING) {
                    startTicker(created)
                }
            }

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                listener?.onRemoved(download.request.id)
            }
        })
        tickerHandler = Handler(created.applicationLooper)
        return created
    }

    /** Media3 only reports state changes; poll percent while something downloads. */
    private fun startTicker(downloadManager: DownloadManager) {
        val handler = tickerHandler ?: return
        handler.post {
            if (tickerRunning) {
                return@post
            }
            tickerRunning = true
            val tick = object : Runnable {
                override fun run() {
                    val active = downloadManager.currentDownloads
                        .filter { it.state == Download.STATE_DOWNLOADING }
                    active.forEach { map(it, null)?.let { d -> listener?.onChanged(d) } }
                    if (active.isEmpty()) {
                        tickerRunning = false
                    } else {
                        handler.postDelayed(this, PROGRESS_INTERVAL_MS)
                    }
                }
            }
            handler.postDelayed(tick, PROGRESS_INTERVAL_MS)
        }
    }

    /**
     * Media3 [DownloadHelper] / [DefaultTrackSelector] must be created and read on the main looper.
     * The worker waits on a latch so main is not blocked for the HLS fetch.
     */
    override fun prepare(downloadId: String, url: String, mimeType: String?): PreparedDownload {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "prepare blocks; call from a background thread"
        }
        val item = MediaItem.Builder()
            .setUri(url)
            .setMimeType(mimeType ?: MimeTypes.APPLICATION_M3U8)
            .build()
        val done = CountDownLatch(1)
        val outcome = AtomicReference<Result<PreparedRead>>()
        val helperRef = AtomicReference<DownloadHelper>()
        val main = Handler(Looper.getMainLooper())
        main.post {
            val helper = DownloadHelper.Factory()
                .setDataSourceFactory(http)
                .setRenderersFactory(DefaultRenderersFactory(appContext))
                .create(item)
            helperRef.set(helper)
            helper.prepare(object : DownloadHelper.Callback {
                override fun onPrepared(helper: DownloadHelper, tracksInfoAvailable: Boolean) {
                    outcome.set(runCatching { readPrepared(downloadId, helper, tracksInfoAvailable) })
                    done.countDown()
                }

                override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                    helper.release()
                    outcome.set(Result.failure(e))
                    done.countDown()
                }
            })
        }
        try {
            if (!done.await(PREPARE_TIMEOUT_S, TimeUnit.SECONDS)) {
                main.post { helperRef.get()?.release() }
                throw IOException("prepare timed out")
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            main.post { helperRef.get()?.release() }
            throw IOException("prepare interrupted")
        }
        val result = outcome.get() ?: throw IOException("prepare produced no result")
        val read = result.getOrThrow()
        var format = read.prepared.format
        var sessionKeyFallback = false
        if (format == null) {
            val playlist = try {
                fetchUtf8(url)
            } catch (e: IOException) {
                Log.w(TAG, "session-key fetch failed id=$downloadId ${e.javaClass.simpleName}: ${e.message}")
                null
            }
            format = playlist?.let { HlsWidevineSessionKey.formatFromPlaylist(it) }
            sessionKeyFallback = format != null
        }
        Log.i(
            TAG,
            "prepare id=$downloadId tracksInfoAvailable=${read.tracksInfoAvailable} " +
                "periodCount=${read.periodCount} formats=${read.formatCount} drm=${read.drmCount} " +
                "sessionKeyFallback=$sessionKeyFallback",
        )
        return PreparedDownload(downloadId, format, read.prepared.handle)
    }

    private fun fetchUtf8(url: String): String {
        val source = http.createDataSource()
        source.open(DataSpec(Uri.parse(url)))
        try {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8 * 1024)
            while (true) {
                val n = source.read(buf, 0, buf.size)
                if (n == C.RESULT_END_OF_INPUT) {
                    break
                }
                out.write(buf, 0, n)
            }
            return out.toString(Charsets.UTF_8.name())
        } finally {
            source.close()
        }
    }

    private fun readPrepared(
        downloadId: String,
        helper: DownloadHelper,
        tracksInfoAvailable: Boolean,
    ): PreparedRead {
        try {
            val periodCount = if (tracksInfoAvailable) helper.periodCount else 0
            val formats = if (tracksInfoAvailable && periodCount > 0) {
                helper.getTracks(0).groups.flatMap { group ->
                    (0 until group.length).map { group.getTrackFormat(it) to group.isTrackSelected(it) }
                }
            } else {
                emptyList()
            }
            val request = helper.getDownloadRequest(downloadId, null)
            return PreparedRead(
                PreparedDownload(downloadId, OfflineFormats.firstWithDrmInitData(formats), request),
                tracksInfoAvailable,
                periodCount,
                formats.size,
                formats.count { it.first.drmInitData != null },
            )
        } finally {
            helper.release()
        }
    }

    private data class PreparedRead(
        val prepared: PreparedDownload,
        val tracksInfoAvailable: Boolean,
        val periodCount: Int,
        val formatCount: Int,
        val drmCount: Int,
    )

    override fun enqueue(prepared: PreparedDownload) {
        val request = prepared.handle as DownloadRequest
        try {
            DownloadService.sendAddDownload(appContext, OfflineDownloadService::class.java, request, true)
        } catch (e: RuntimeException) {
            // Foreground start refused (app backgrounded): the manager still downloads in-process.
            Log.w(TAG, "download service start refused: ${e.javaClass.simpleName}")
            onMain {
                manager.addDownload(request)
                manager.resumeDownloads()
            }
        }
    }

    override fun remove(downloadId: String) {
        onMain {
            manager.removeDownload(downloadId)
            manager.resumeDownloads()
        }
    }

    override fun get(downloadId: String): EngineDownload? = try {
        manager.downloadIndex.getDownload(downloadId)?.let { map(it, null) }
    } catch (e: IOException) {
        null
    }

    override fun all(): List<EngineDownload> = try {
        val out = ArrayList<EngineDownload>()
        manager.downloadIndex.getDownloads().use { cursor ->
            while (cursor.moveToNext()) {
                map(cursor.download, null)?.let { out.add(it) }
            }
        }
        out
    } catch (e: IOException) {
        emptyList()
    }

    private fun map(download: Download, finalException: Exception?): EngineDownload? {
        val state = when (download.state) {
            Download.STATE_QUEUED, Download.STATE_STOPPED, Download.STATE_RESTARTING -> EngineState.QUEUED
            Download.STATE_DOWNLOADING -> EngineState.DOWNLOADING
            Download.STATE_COMPLETED -> EngineState.COMPLETED
            Download.STATE_FAILED -> EngineState.FAILED
            else -> return null // REMOVING
        }
        val percent = download.percentDownloaded
        val progress = when {
            state == EngineState.COMPLETED -> 1f
            percent < 0f -> 0f
            else -> (percent / 100f).coerceIn(0f, 1f)
        }
        return EngineDownload(
            download.request.id,
            state,
            progress,
            download.request.uri.toString(),
            finalException == null || finalException is IOException
        )
    }

    private companion object {
        const val TAG = "Media3OfflineEngine"
        const val DOWNLOAD_THREADS = 2
        const val PROGRESS_INTERVAL_MS = 1000L
        const val PREPARE_TIMEOUT_S = 60L
        const val MAIN_HOP_TIMEOUT_S = 30L
    }
}

/** Pure format selection, unit-testable without a network. */
@UnstableApi
object OfflineFormats {
    /** First track format (selected tracks win) that carries `drmInitData`, or null. */
    @JvmStatic
    fun firstWithDrmInitData(formats: List<Pair<Format, Boolean>>): Format? =
        formats.firstOrNull { (format, selected) -> selected && format.drmInitData != null }?.first
            ?: formats.firstOrNull { (format, _) -> format.drmInitData != null }?.first
}
