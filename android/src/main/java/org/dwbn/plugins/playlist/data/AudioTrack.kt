package org.dwbn.plugins.playlist.data

import com.devbrackets.android.playlistcore.annotation.SupportedMediaType
import com.devbrackets.android.playlistcore.api.PlaylistItem
import com.devbrackets.android.playlistcore.manager.BasePlaylistManager
import org.json.JSONException
import org.json.JSONObject

class AudioTrack (private val config: JSONObject) : PlaylistItem {
    var bufferPercentFloat = 0f
        set(buff) {
            // There is a bug in MediaProgress where if bufferPercent == 100 it sets bufferPercentFloat
            // to 100 instead of to 1.
            field = Math.min(Math.max(bufferPercentFloat, buff), 1f)
        }
    var bufferPercent = 0
        set(buff) {
            field = Math.max(bufferPercent, buff)
        }
    var duration: Long = 0
        set(dur) {
            field = Math.max(0, dur)
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
        } catch (e: JSONException) {
            // I can think of no reason this would ever fail
        }
        return info
    }

    override val id: Long
        get() =
            if (trackId == null) {
                0
            } else trackId.hashCode().toLong()

    val isStream: Boolean
        get() = config.optBoolean("isStream", false)

    val trackId: String?
        get() {
            val trackId = config.optString("trackId")
            return if (trackId == "") {
                null
            } else trackId
        }

    // Would really like to set this to true once the cache has it...
    override val downloaded: Boolean
        get() = false // Would really like to set this to true once the cache has it...

    // ... at which point we can return a value here.
    override val downloadedMediaUri: String?
        get() = null // ... at which point we can return a value here.

    @get:SupportedMediaType
    override val mediaType: Int
        get() = BasePlaylistManager.AUDIO

    override val mediaUrl: String
        get() = config.optString("assetUrl", "")

    // we should have a good default here.
    override val thumbnailUrl: String?
        get() {
            val albumArt = config.optString("albumArt")
            return if (albumArt == "") {
                null
            } else albumArt // we should have a good default here.
        }

    override val artworkUrl: String?
        get() = thumbnailUrl

    override val title: String
        get() = config.optString("title")

    override val album: String
        get() = config.optString("album")

    override val artist: String
        get() = config.optString("artist")

}
