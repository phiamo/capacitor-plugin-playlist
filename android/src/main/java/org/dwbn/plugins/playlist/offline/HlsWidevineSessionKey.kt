package org.dwbn.plugins.playlist.offline

import android.util.Base64
import androidx.media3.common.C
import androidx.media3.common.DrmInitData
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

/**
 * Reads a Widevine PSSH from HLS `#EXT-X-SESSION-KEY` / `#EXT-X-KEY` (`data:` URI).
 * Media3 [DownloadHelper] track formats often omit that PSSH on audio-only masters.
 */
@UnstableApi
object HlsWidevineSessionKey {
    private const val WIDEVINE_KEYFORMAT = "urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"

    /** Track format carrying the first Widevine PSSH in [playlist], or null. */
    @JvmStatic
    fun formatFromPlaylist(playlist: String): Format? {
        val pssh = firstWidevinePssh(playlist) ?: return null
        return Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setDrmInitData(
                DrmInitData(DrmInitData.SchemeData(C.WIDEVINE_UUID, MimeTypes.VIDEO_MP4, pssh)),
            )
            .build()
    }

    /** Decoded PSSH bytes from the first Widevine key line, or null. */
    @JvmStatic
    fun firstWidevinePssh(playlist: String): ByteArray? {
        for (raw in playlist.splitToSequence('\n', '\r')) {
            val line = raw.trim()
            if (!line.startsWith("#EXT-X-SESSION-KEY:") && !line.startsWith("#EXT-X-KEY:")) {
                continue
            }
            if (!line.contains(WIDEVINE_KEYFORMAT, ignoreCase = true)) {
                continue
            }
            val uri = attribute(line, "URI") ?: continue
            decodeDataUri(uri)?.let { return it }
        }
        return null
    }

    internal fun attribute(line: String, name: String): String? {
        val prefix = "$name=\""
        val start = line.indexOf(prefix, ignoreCase = true)
        if (start < 0) {
            return null
        }
        val from = start + prefix.length
        val end = line.indexOf('"', from)
        return if (end < 0) null else line.substring(from, end)
    }

    private fun decodeDataUri(uri: String): ByteArray? {
        if (!uri.startsWith("data:", ignoreCase = true)) {
            return null
        }
        val comma = uri.indexOf(',')
        if (comma < 0) {
            return null
        }
        val meta = uri.substring(5, comma)
        if (!meta.contains("base64", ignoreCase = true)) {
            return null
        }
        return try {
            Base64.decode(uri.substring(comma + 1), Base64.DEFAULT)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
