package org.dwbn.plugins.playlist

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.devbrackets.android.playlistcore.data.MediaProgress
import com.getcapacitor.*
import com.getcapacitor.annotation.CapacitorPlugin
import org.dwbn.plugins.playlist.data.AudioTrack
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

@CapacitorPlugin(name = "Playlist")
class PlaylistPlugin : Plugin(), OnStatusReportListener {
    var TAG = "PlaylistPlugin"
    private var statusCallback: OnStatusCallback? = null
    private var audioPlayerImpl: RmxAudioPlayer? = null
    private var resetStreamOnPause = true

    override fun load() {
        audioPlayerImpl = RmxAudioPlayer(this, (this.context.applicationContext as App))
    }

    @PluginMethod
    fun initialize(call: PluginCall) {
        Handler(Looper.getMainLooper()).post {
            statusCallback = OnStatusCallback(this)
            onStatus(RmxAudioStatusMessage.RMXSTATUS_REGISTER, "INIT", null)
            Log.i(TAG, "Initialized...")

            audioPlayerImpl!!.resume()
            call.resolve()
        }
    }
    @PluginMethod
    fun setOptions(call: PluginCall) {
        Handler(Looper.getMainLooper()).post {
            val options: JSObject = call.getObject("options") ?: JSObject()
            resetStreamOnPause = options.optBoolean("resetStreamOnPause", this.resetStreamOnPause)
            Log.i("AudioPlayerOptions", options.toString())
            audioPlayerImpl!!.resetStreamOnPause = resetStreamOnPause
            audioPlayerImpl!!.setOptions(options)
            call.resolve()
        }
    }

    @PluginMethod
    fun release(call: PluginCall) {
        Handler(Looper.getMainLooper()).post {
            destroyResources()
            call.resolve()
            Log.i(TAG, "released")
        }
    }

    @PluginMethod
    fun setLoop(call: PluginCall) {
        Handler(Looper.getMainLooper()).post {
            val loop: Boolean = call.getBoolean("loop", audioPlayerImpl!!.playlistManager.loop)!!
            audioPlayerImpl!!.playlistManager.loop = loop
            call.resolve()
            Log.i(TAG, "setLoop: " + (if (loop) "TRUE" else "FALSE"))
        }
    }

    @PluginMethod
    fun setPlaylistItems(call: PluginCall) {
        Handler(Looper.getMainLooper()).post {
            val items: JSArray = call.getArray("items")
            val optionsArgs: JSONObject = call.getObject("options")
            val options = PlaylistItemOptions(optionsArgs)

            val trackItems: ArrayList<AudioTrack> = getTrackItems(items)
            audioPlayerImpl!!.playlistManager.setAllItems(trackItems, options)
            for (playerItem in trackItems) {
                if (playerItem.trackId != null) {
                    onStatus(
                        RmxAudioStatusMessage.RMXSTATUS_ITEM_ADDED,
                        playerItem.trackId,
                        playerItem.toDict()
                    )
                }
            }

            call.resolve()

            Log.i(TAG, "setPlaylistItems" + items.length().toString())
        }
    }

    @PluginMethod
    fun addItem(call: PluginCall) {
        Handler(Looper.getMainLooper()).post {
            val item: JSONObject = call.getObject("item")
            val playerItem: AudioTrack? = getTrackItem(item)
            audioPlayerImpl!!.getPlaylistManager().addItem(playerItem)


            if (playerItem?.trackId != null) {
                onStatus(
                    RmxAudioStatusMessage.RMXSTATUS_ITEM_ADDED,
                    playerItem.trackId,
                    playerItem.toDict()
                )
            }
            call.resolve()
            Log.i(TAG, "addItem")
        }
    }

    @PluginMethod
    fun addAllItems(call: PluginCall) {
        Handler(Looper.getMainLooper()).post {
            val items: JSONArray = call.getArray("items")
            val trackItems = getTrackItems(items)
            audioPlayerImpl!!.playlistManager.addAllItems(trackItems)

            for (playerItem in trackItems) {
                if (playerItem.trackId != null) {
                    onStatus(
                        RmxAudioStatusMessage.RMXSTATUS_ITEM_ADDED,
                        playerItem.trackId,
                        playerItem.toDict()
                    )
                }
            }
            call.resolve()
            Log.i(TAG, "addAllItems")
        }
    }

    @PluginMethod
    fun removeItem(call: PluginCall) {
        Handler(Looper.getMainLooper()).post {
            val trackIndex: Int = call.getInt("index", -1)!!
            val trackId: String = call.getString("id", "")!!
            Log.i(TAG, "removeItem trackIn")
            val item = audioPlayerImpl!!.playlistManager.removeItem(trackIndex, trackId)

            if (item != null) {
                onStatus(RmxAudioStatusMessage.RMXSTATUS_ITEM_REMOVED, item.trackId, item.toDict())
                call.resolve()
            } else {
                call.reject("Could not find item!")
            }
        }
    }

    @PluginMethod
    fun removeItems(call: PluginCall) {
        Handler(Looper.getMainLooper()).post {
            val items: JSONArray = call.getArray("items")
            var removed = 0

            val removals = ArrayList<TrackRemovalItem>()
            for (index in 0 until items.length()) {
                val entry = items.optJSONObject(index) ?: continue
                val trackIndex = entry.optInt("trackIndex", -1)
                val trackId = entry.optString("trackId", "")
                removals.add(TrackRemovalItem(trackIndex, trackId))
                val removedTracks = audioPlayerImpl!!.playlistManager.removeAllItems(removals)
                if (removedTracks.size > 0) {
                    for (removedItem in removedTracks) {
                        onStatus(
                            RmxAudioStatusMessage.RMXSTATUS_ITEM_REMOVED,
                            removedItem.trackId,
                            removedItem.toDict()
                        )
                    }
                    removed = removedTracks.size
                }
            }

            val result = JSObject()
            result.put("removed", removed)
            call.resolve(result)

            Log.i(TAG, "removeItems")
        }
    }

    @PluginMethod
    fun clearAllItems(call: PluginCall) {
        Handler(Looper.getMainLooper()).post {
            audioPlayerImpl!!.playlistManager.clearItems()

            onStatus(RmxAudioStatusMessage.RMXSTATUS_PLAYLIST_CLEARED, "INVALID", null)
            call.resolve()

            Log.i(TAG, "clearAllItems")
        }
    }

    @PluginMethod
    fun play(call: PluginCall) {
        Handler(Looper.getMainLooper()).post {
            if (audioPlayerImpl!!.playlistManager.playlistHandler != null) {
                val isPlaying =
                    (audioPlayerImpl!!.playlistManager.playlistHandler?.currentMediaPlayer != null
                            && audioPlayerImpl!!.playlistManager.playlistHandler?.currentMediaPlayer?.isPlaying!!)
                // There's a bug in the threaded repeater that it stacks up the repeat calls instead of ignoring
                // additional ones or starting a new one. E.g. every time this is called, you'd get a new repeat cycle,
                // meaning you get N updates per second. Ew.
                if (!isPlaying) {
                    audioPlayerImpl!!.playlistManager.playlistHandler?.play()
                    //audioPlayerImpl.getPlaylistManager().playlistHandler.seek(position)
                }
            }

            call.resolve()

            Log.i(TAG, "play")
        }
    }

    @PluginMethod
    fun playTrackByIndex(call: PluginCall) {
        Handler(Looper.getMainLooper()).post {
            val index: Int =
                call.getInt("index", audioPlayerImpl!!.playlistManager.currentPosition)!!
            val seekPosition = (call.getInt("position", 0)!! * 1000.0).toLong()

            audioPlayerImpl!!.playlistManager.currentPosition = index
            audioPlayerImpl!!.playlistManager.beginPlayback(seekPosition, false)

            call.resolve()

            Log.i(TAG, "playTrackByIndex")
        }
    }

    @PluginMethod
    fun playTrackById(call: PluginCall) {
        Handler(Looper.getMainLooper()).post {
            val id: String = call.getString("id")!!
            if ("" != id) {
                // alternatively we could search for the item and set the current index to that item.
                val code = id.hashCode()
                val seekPosition = (call.getInt("position", 0)!! * 1000.0).toLong()
                audioPlayerImpl!!.playlistManager.setCurrentItem(code.toLong())
                audioPlayerImpl!!.playlistManager.beginPlayback(seekPosition, false)
            }

            call.resolve()

            Log.i(TAG, "playTrackById")
        }
    }

    @PluginMethod
    fun selectTrackByIndex(call: PluginCall) {
        Handler(Looper.getMainLooper()).post {
            val index: Int =
                call.getInt("index", audioPlayerImpl!!.playlistManager.currentPosition)!!

            audioPlayerImpl!!.playlistManager.currentPosition = index

            val seekPosition = (call.getInt("position", 0)!! * 1000.0).toLong()

            audioPlayerImpl!!.playlistManager.beginPlayback(seekPosition, true)

            call.resolve()

            Log.i(TAG, "selectTrackByIndex")
        }
    }


    @PluginMethod
    fun selectTrackById(call: PluginCall) {
        Handler(Looper.getMainLooper()).post {
            val id: String = call.getString("id")!!
            if ("" != id) {
                // alternatively we could search for the item and set the current index to that item.
                val code = id.hashCode()
                audioPlayerImpl!!.playlistManager.setCurrentItem(code.toLong())

                val seekPosition = (call.getInt("position", 0)!! * 1000.0).toLong()

                audioPlayerImpl!!.playlistManager.beginPlayback(seekPosition, true)
            }
            call.resolve()

            Log.i(TAG, "selectTrackById")
        }
    }

    @PluginMethod
    fun pause(call: PluginCall) {
        Handler(Looper.getMainLooper()).post {
            audioPlayerImpl!!.playlistManager.invokePausePlay()

            call.resolve()

            Log.i(TAG, "pause")
        }
    }

    @PluginMethod
    fun skipForward(call: PluginCall) {
        Handler(Looper.getMainLooper()).post {
            audioPlayerImpl!!.playlistManager.invokeNext()

            call.resolve()

            Log.i(TAG, "skipForward")
        }
    }

    @PluginMethod
    fun skipBack(call: PluginCall) {
        Handler(Looper.getMainLooper()).post {
            audioPlayerImpl!!.playlistManager.invokePrevious()

            call.resolve()

            Log.i(TAG, "skipBack")
        }
    }

    @PluginMethod
    fun seekTo(call: PluginCall) {
        Handler(Looper.getMainLooper()).post {
            var position: Long = 0
            val progress: MediaProgress? = audioPlayerImpl!!.playlistManager.currentProgress
            if (progress != null) {
                position = progress.position
            }

            val seekPosition =
                (call.getInt("position", (position / 1000.0f).toInt())!! * 1000.0).toLong()

            val isPlaying: Boolean? =
                audioPlayerImpl!!.playlistManager.playlistHandler?.currentMediaPlayer?.isPlaying
            audioPlayerImpl!!.playlistManager.playlistHandler?.seek(seekPosition)
            if (isPlaying === null || !isPlaying) {
                audioPlayerImpl!!.playlistManager.playlistHandler?.pause(false)
            }

            call.resolve()

            Log.i(TAG, "seekTo")
        }
    }

    @PluginMethod
    fun setPlaybackRate(call: PluginCall) {
        Handler(Looper.getMainLooper()).post {
            val speed =
                call.getFloat("rate", audioPlayerImpl!!.playlistManager.getPlaybackSpeed())!!
            audioPlayerImpl!!.playlistManager.setPlaybackSpeed(speed)

            call.resolve()

            Log.i(TAG, "setPlaybackRate")
        }
    }

    @PluginMethod
    fun setVolume(call: PluginCall) {
        Handler(Looper.getMainLooper()).post {
            val volume = call.getFloat("volume", audioPlayerImpl!!.volume)!!
            audioPlayerImpl!!.volume = volume

            call.resolve()

            Log.i(TAG, "addItem")
        }
    }

    override fun handleOnDestroy() {
        Log.d(TAG, "Plugin destroy")
        super.handleOnDestroy()
        destroyResources()
    }

    override fun onError(errorCode: RmxAudioErrorType?, trackId: String?, message: String?) {
        if (statusCallback == null) {
            return
        }
        val errorObj = OnStatusCallback.createErrorWithCode(errorCode, message)
        onStatus(RmxAudioStatusMessage.RMXSTATUS_ERROR, trackId, errorObj)
    }

    override fun onStatus(what: RmxAudioStatusMessage, trackId: String?, param: JSONObject?) {
        if (statusCallback == null) {
            return
        }
        statusCallback!!.onStatus(what, trackId, param)
    }

    private fun destroyResources() {
        statusCallback = null
        audioPlayerImpl!!.playlistManager.clearItems()
    }

    private fun getTrackItem(item: JSONObject?): AudioTrack? {
        if (item != null) {
            val track = AudioTrack(item)
            return if (track.trackId != null) {
                track
            } else null
        }
        return null
    }
    private fun getTrackItems(items: JSONArray?): ArrayList<AudioTrack> {
        val trackItems = ArrayList<AudioTrack>()
        if (items != null && items.length() > 0) {
            for (index in 0 until items.length()) {
                val obj = items.optJSONObject(index)
                val track: AudioTrack = getTrackItem(obj) ?: continue
                trackItems.add(track)
            }
        }
        return trackItems
    }

    fun emit(name: String, data: JSObject) {
        this.notifyListeners(name, data, true)
    }
}
