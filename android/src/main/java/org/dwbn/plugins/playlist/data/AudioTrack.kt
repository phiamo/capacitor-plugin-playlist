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
