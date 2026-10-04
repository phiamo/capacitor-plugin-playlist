package org.dwbn.plugins.playlist.offline

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.DrmInitData
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import java.io.ByteArrayInputStream

/**
 * Reads Widevine PSSH via Media3 [HlsPlaylistParser.sessionKeyDrmInitData].
 * [DownloadHelper.getTracks] still omits that PSSH unless the helper is built with
 * [androidx.media3.exoplayer.hls.HlsMediaSource.Factory.setUseSessionKeys].
 */
@UnstableApi
object HlsWidevineSessionKey {
    private val parser = HlsPlaylistParser()

    /** Track format carrying the first Widevine PSSH in [playlist], or null. */
    @JvmStatic
    fun formatFromPlaylist(playlist: String): Format? {
        val init = firstWidevineInitData(playlist) ?: return null
        return Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setDrmInitData(init)
            .build()
    }

    /** Decoded PSSH bytes from the first Widevine scheme, or null. */
    @JvmStatic
    fun firstWidevinePssh(playlist: String): ByteArray? = widevinePssh(firstWidevineInitData(playlist))

    private fun firstWidevineInitData(playlist: String): DrmInitData? {
        val parsed = try {
            parser.parse(
                Uri.parse("https://local/master.m3u8"),
                ByteArrayInputStream(playlist.toByteArray(Charsets.UTF_8)),
            )
        } catch (_: Exception) {
            return null
        }
        val inits = (parsed as? HlsMultivariantPlaylist)?.sessionKeyDrmInitData ?: return null
        return inits.firstOrNull { widevinePssh(it) != null }
    }

    private fun widevinePssh(init: DrmInitData?): ByteArray? {
        if (init == null) {
            return null
        }
        for (i in 0 until init.schemeDataCount) {
            val scheme = init.get(i)
            if (scheme.matches(C.WIDEVINE_UUID)) {
                return scheme.data
            }
        }
        return null
    }
}
