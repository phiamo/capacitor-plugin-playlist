package org.dwbn.plugins.playlist.offline

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.getcapacitor.JSObject
import org.dwbn.plugins.playlist.AudioDrm
import org.dwbn.plugins.playlist.AudioOffline
import org.dwbn.plugins.playlist.AudioOfflineProvider
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** Fallback licence clock when the host provider does not report a real expiry. */
object OfflineExpiry {
    const val LICENSE_VALIDITY_DAYS = 27L
    const val LICENSE_VALIDITY_MS = LICENSE_VALIDITY_DAYS * 24 * 60 * 60 * 1000

    @JvmStatic
    fun expiresAt(acquiredAtMs: Long): Long = acquiredAtMs + LICENSE_VALIDITY_MS
}

/** Wire states of the `download` event / `listDownloads`. */
object DownloadStates {
    const val QUEUED = "queued"
    const val DOWNLOADING = "downloading"
    const val COMPLETED = "completed"
    const val FAILED = "failed"
    const val EXPIRED = "expired"
}

data class DownloadEvent(
    val downloadId: String,
    val state: String,
    val progress: Float,
    val error: String? = null
)

data class DownloadInfo(
    val downloadId: String,
    val state: String,
    val progress: Float,
    val expiresAt: Long?,
    val needsRenewal: Boolean
)

/**
 * Download orchestration (Story 59.4). Order on start: prepare HLS -> first `Format` with
 * `drmInitData` -> `provider.acquire` (background) -> only then enqueue segments, so a refused
 * licence (incl. `offlineDeviceLimit`) fetches no media. Licences and renewal belong to
 * the host [AudioOfflineProvider]; this class never touches drm-kit.
 */
@UnstableApi
class OfflineDownloads(
    internal val engine: OfflineEngine,
    private val meta: OfflineMetaStore,
    private val executor: Executor,
    private val clock: () -> Long = System::currentTimeMillis,
    private val providerSource: () -> AudioOfflineProvider? = AudioOffline::getProvider
) : OfflineEngine.Listener {

    fun interface EventSink {
        fun onDownloadEvent(event: DownloadEvent)
    }

    @Volatile
    var eventSink: EventSink? = null

    /** downloadId -> token of the in-flight prepare/acquire; removal means "cancelled". */
    private val pending = ConcurrentHashMap<String, Any>()

    init {
        engine.listener = this
        purgeFailed()
    }

    /**
     * Starts (or resumes with a fresh URL) a download. Returns a failure code synchronously
     * ([AudioDrm.CODE_NO_PROVIDER]) or null; everything else is reported through `download` events.
     */
    fun start(downloadId: String, url: String, mimeType: String?, drm: JSObject?): String? {
        val provider = providerSource() ?: return AudioOffline.CODE_NO_PROVIDER
        val existing = engine.get(downloadId)
        if (existing?.state == EngineState.COMPLETED && meta.get(downloadId) != null) {
            if (AudioOffline.stateOf(provider, downloadId) == AudioOfflineProvider.STATE_ACTIVE) {
                emit(downloadId, DownloadStates.COMPLETED, 1f, null)
                return null
            }
            // Leftover media with an unusable licence (Story 59.7): drop and re-acquire.
            removeAll(downloadId)
        }
        val token = Any()
        if (pending.putIfAbsent(downloadId, token) != null) {
            return null // a start for this id is already in flight
        }
        emit(downloadId, DownloadStates.QUEUED, existing?.progress ?: 0f, null)
        executor.execute { runStart(provider, downloadId, url, mimeType, drm, token) }
        return null
    }

    private fun runStart(
        provider: AudioOfflineProvider,
        downloadId: String,
        url: String,
        mimeType: String?,
        drm: JSObject?,
        token: Any
    ) {
        try {
            val prepared = try {
                engine.prepare(downloadId, url, mimeType)
            } catch (e: IOException) {
                Log.w(TAG, "prepare IOException id=$downloadId ${e.javaClass.simpleName}: ${e.message}")
                fail(downloadId, AudioDrm.ERROR_NETWORK, token)
                return
            } catch (e: RuntimeException) {
                Log.w(TAG, "prepare RuntimeException id=$downloadId ${e.javaClass.simpleName}: ${e.message}")
                fail(downloadId, AudioDrm.ERROR_UNKNOWN, token)
                return
            }
            val format = prepared.format
            if (format == null) {
                Log.w(TAG, "no DRM init data found for download")
                fail(downloadId, AudioDrm.ERROR_UNKNOWN, token)
                return
            }
            if (pending[downloadId] !== token) {
                return
            }
            val refusal = try {
                provider.acquire(downloadId, format, drm ?: JSObject())
            } catch (e: RuntimeException) {
                Log.w(TAG, "acquire threw ${e.javaClass.simpleName}: ${e.message}")
                AudioDrm.ERROR_UNKNOWN
            }
            if (refusal != null) {
                Log.w(TAG, "acquire refused id=$downloadId error=$refusal")
                fail(downloadId, AudioOffline.typedError(refusal), token)
                return
            }
            if (pending[downloadId] !== token) {
                // Cancelled or deleted while the licence request was in flight.
                releaseQuietly(provider, downloadId)
                return
            }
            try {
                persistLicenceClock(provider, downloadId)
                engine.enqueue(prepared)
            } catch (e: Exception) {
                Log.w(TAG, "enqueue failed: ${e.javaClass.simpleName}")
                releaseQuietly(provider, downloadId)
                meta.remove(downloadId)
                fail(downloadId, AudioDrm.ERROR_UNKNOWN, token)
            }
        } finally {
            pending.remove(downloadId, token)
        }
    }

    private fun fail(downloadId: String, error: String, token: Any) {
        if (pending[downloadId] !== token) {
            return
        }
        emit(downloadId, DownloadStates.FAILED, engine.get(downloadId)?.progress ?: 0f, error)
    }

    /** Cancels a non-completed download; a no-op for completed ones. */
    fun cancel(downloadId: String) {
        val existing = engine.get(downloadId)
        if (existing?.state == EngineState.COMPLETED) {
            return
        }
        removeAll(downloadId)
    }

    /** Removes cache, index entry, metadata and the licence, in any state. */
    fun delete(downloadId: String) {
        removeAll(downloadId)
    }

    private fun removeAll(downloadId: String) {
        pending.remove(downloadId)
        try {
            engine.remove(downloadId)
        } catch (e: RuntimeException) {
            Log.w(TAG, "engine.remove failed: ${e.javaClass.simpleName}")
        }
        meta.remove(downloadId)
        providerSource()?.let { releaseQuietly(it, downloadId) }
    }

    private fun releaseQuietly(provider: AudioOfflineProvider, downloadId: String) {
        try {
            provider.release(downloadId)
        } catch (_: RuntimeException) {
            // release failures are ignored by contract
        }
    }

    /**
     * Renews the licence online. [onResult] gets null on success or the typed error; a refusal also moves the
     * download to `expired` only when the provider's state for it is actually `expired`.
     */
    fun renew(downloadId: String, drm: JSObject?, onResult: (String?) -> Unit) {
        val provider = providerSource()
        if (provider == null) {
            onResult(AudioOffline.CODE_NO_PROVIDER)
            return
        }
        if (engine.get(downloadId) == null && meta.get(downloadId) == null) {
            onResult(AudioDrm.ERROR_UNKNOWN)
            return
        }
        executor.execute {
            val refusal = try {
                provider.renew(downloadId, drm)
            } catch (e: RuntimeException) {
                AudioDrm.ERROR_UNKNOWN
            }
            if (refusal == null) {
                persistLicenceClock(provider, downloadId)
                val current = engine.get(downloadId)
                emit(
                    downloadId,
                    current?.let { stateName(it) } ?: DownloadStates.COMPLETED,
                    current?.progress ?: 1f,
                    null
                )
                onResult(null)
            } else {
                val typed = AudioOffline.typedError(refusal)
                if (AudioOffline.stateOf(provider, downloadId) == AudioOfflineProvider.STATE_EXPIRED) {
                    emit(downloadId, DownloadStates.EXPIRED, engine.get(downloadId)?.progress ?: 1f, typed)
                }
                onResult(typed)
            }
        }
    }

    fun list(): List<DownloadInfo> {
        val provider = providerSource()
        val seen = HashSet<String>()
        val out = ArrayList<DownloadInfo>()
        for (download in engine.all()) {
            if (download.state == EngineState.FAILED) {
                continue
            }
            seen.add(download.id)
            var state = stateName(download)
            if (state == DownloadStates.COMPLETED && provider != null &&
                AudioOffline.stateOf(provider, download.id) == AudioOfflineProvider.STATE_EXPIRED
            ) {
                state = DownloadStates.EXPIRED
            }
            out.add(
                DownloadInfo(
                    download.id,
                    state,
                    download.progress,
                    licenceExpiresAt(provider, download.id),
                    needsRenewal(provider, download.id)
                )
            )
        }
        for (id in pending.keys) {
            if (seen.add(id)) {
                out.add(DownloadInfo(id, DownloadStates.QUEUED, 0f, licenceExpiresAt(provider, id), false))
            }
        }
        return out
    }

    private fun purgeFailed() {
        for (download in engine.all()) {
            if (download.state == EngineState.FAILED) {
                try {
                    engine.remove(download.id)
                } catch (e: RuntimeException) {
                    Log.w(TAG, "purge failed row failed: ${e.javaClass.simpleName}")
                }
                meta.remove(download.id)
            }
        }
    }

    private fun persistLicenceClock(provider: AudioOfflineProvider, downloadId: String) {
        val now = clock()
        val fromProvider = try {
            provider.expiresAt(downloadId)
        } catch (_: RuntimeException) {
            null
        }
        meta.put(downloadId, OfflineMetaStore.Entry(now, fromProvider ?: OfflineExpiry.expiresAt(now)))
    }

    private fun licenceExpiresAt(provider: AudioOfflineProvider?, downloadId: String): Long? {
        if (provider != null) {
            try {
                provider.expiresAt(downloadId)?.let { return it }
            } catch (_: RuntimeException) {
            }
        }
        return meta.get(downloadId)?.expiresAt
    }

    private fun needsRenewal(provider: AudioOfflineProvider?, downloadId: String): Boolean =
        try {
            provider?.needsRenewal(downloadId) == true
        } catch (_: RuntimeException) {
            false
        }

    /** Delivery URL of the download, for offline playback only; never persisted or logged. */
    fun requestUri(downloadId: String): String? = engine.get(downloadId)?.requestUri

    override fun onChanged(download: EngineDownload) {
        emit(
            download.id,
            stateName(download),
            download.progress,
            if (download.state == EngineState.FAILED) {
                if (download.failedByNetwork) AudioDrm.ERROR_NETWORK else AudioDrm.ERROR_UNKNOWN
            } else {
                null
            }
        )
    }

    override fun onRemoved(downloadId: String) {
        // Removal is already reflected by the callers (cancel/delete); nothing to emit.
    }

    private fun emit(downloadId: String, state: String, progress: Float, error: String?) {
        eventSink?.onDownloadEvent(DownloadEvent(downloadId, state, progress.coerceIn(0f, 1f), error))
    }

    private fun stateName(download: EngineDownload): String = when (download.state) {
        EngineState.QUEUED -> DownloadStates.QUEUED
        EngineState.DOWNLOADING -> DownloadStates.DOWNLOADING
        EngineState.COMPLETED -> DownloadStates.COMPLETED
        EngineState.FAILED -> DownloadStates.FAILED
    }

    companion object {
        private const val TAG = "OfflineDownloads"

        @Volatile
        private var instance: OfflineDownloads? = null

        @JvmStatic
        fun get(context: Context): OfflineDownloads =
            instance ?: synchronized(this) {
                instance ?: create(context.applicationContext).also { instance = it }
            }

        /** Test seam: replace (or clear with null) the process singleton. */
        @JvmStatic
        fun setInstanceForTest(next: OfflineDownloads?) {
            synchronized(this) { instance = next }
        }

        private fun create(context: Context): OfflineDownloads = OfflineDownloads(
            Media3OfflineEngine(context),
            OfflineMetaStore(OfflineStorage.metaFile(context)),
            Executors.newCachedThreadPool()
        )
    }
}
