package org.dwbn.plugins.playlist.data

import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

class AudioTrack(private val config: JSONObject) {
    companion object {
        private val nextPlaylistId = AtomicLong(1)
    }

    val id: Long = nextPlaylistId.getAndIncrement()

    var bufferPercentFloat = 0f
        set(buff) {
            // There is a bug in MediaProgress where if bufferPercent == 100 it sets bufferPercentFloat
            // to 100 instead of to 1.
            field = minOf(maxOf(bufferPercentFloat, buff), 1f)
        }
    var bufferPercent = 0
        set(buff) {
            field = maxOf(bufferPercent, buff)
        }
    var duration: Long = 0
        set(dur) {
            field = maxOf(0, dur)
        }

    /**
     * Last known playback position for this track, in ms. Seeded from the JS-supplied
     * `startPosition` (seconds) at construction, and kept fresh by [org.dwbn.plugins.playlist.manager.PlaylistManager]
     * whenever the player skips away from this track, so a later skip back within the same
     * session resumes correctly instead of restarting at 0.
     */
    var startPositionMs: Long = maxOf(0L, (config.optDouble("startPosition", 0.0) * 1000.0).toLong())
        set(value) {
            field = maxOf(0, value)
        }

    fun toDict(): JSONObject {
        val info = JSONObject()
        try {
            info.put("trackId", trackId)
            info.put("isStream", isStream)
            info.put("assetUrl", mediaUrl)
            info.put("albumArt", thumbnailUrl)
            info.put("artist", artist)
            info.put("album", album)
            info.put("title", title)
            info.put("startPosition", startPositionMs / 1000.0)
        } catch (_: JSONException) {
            // I can think of no reason this would ever fail
        }
        return info
    }

    val isStream: Boolean
        get() = config.optBoolean("isStream", false)

    val trackId: String?
        get() {
            val trackId = config.optString("trackId")
            return if (trackId == "") {
                null
            } else trackId
        }

    val downloaded: Boolean
        get() = false

    val downloadedMediaUri: String?
        get() = null

    val mediaUrl: String
        get() = config.optString("assetUrl", "")

    /**
     * Optional container hint for sources whose URL carries no usable extension, e.g.
     * "application/x-mpegURL" for an extensionless HLS playlist. Null when unset or blank,
     * in which case ExoPlayer sniffs the content.
     */
    val mimeType: String?
        get() {
            val mimeType = config.optString("mimeType").trim()
            return if (mimeType == "") {
                null
            } else mimeType
        }

    val thumbnailUrl: String?
        get() {
            val albumArt = config.optString("albumArt")
            return if (albumArt == "") {
                null
            } else albumArt
        }

    val artworkUrl: String?
        get() = thumbnailUrl

    val title: String
        get() = config.optString("title")

    val album: String
        get() = config.optString("album")

    val artist: String
        get() = config.optString("artist")
}
