package org.dwbn.plugins.playlist.offline

import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import java.io.IOException

enum class EngineState { QUEUED, DOWNLOADING, COMPLETED, FAILED }

/** Engine view of one download. [requestUri] is the delivery URL; never log or forward it. */
data class EngineDownload(
    val id: String,
    val state: EngineState,
    val progress: Float,
    val requestUri: String?,
    val failedByNetwork: Boolean = true
)

/** A prepared HLS download: the DRM [format] to license, and the engine-private [handle] to enqueue. */
@UnstableApi
class PreparedDownload(val downloadId: String, val format: Format?, val handle: Any?)

/**
 * Segment engine behind [OfflineDownloads]. Production is [Media3OfflineEngine]; unit tests use a
 * fake so licence ordering and cleanup can be asserted without a network.
 */
@UnstableApi
interface OfflineEngine {
    interface Listener {
        fun onChanged(download: EngineDownload)

        fun onRemoved(downloadId: String)
    }

    var listener: Listener?

    /** Blocking; never on the main thread. Reads the HLS playlists, fetches no media. */
    @Throws(IOException::class)
    fun prepare(downloadId: String, url: String, mimeType: String?): PreparedDownload

    /** Queues all segments. Only called after the licence was acquired. */
    fun enqueue(prepared: PreparedDownload)

    /** Removes index entry and cached segments. */
    fun remove(downloadId: String)

    fun get(downloadId: String): EngineDownload?

    fun all(): List<EngineDownload>
}
