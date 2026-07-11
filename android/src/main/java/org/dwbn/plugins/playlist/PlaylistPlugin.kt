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
public class PlaylistPlugin : Plugin(), OnStatusReportListener {
    var TAG = "PlaylistPlugin"
    private var statusCallback: OnStatusCallback? = null
    private var audioPlayerImpl: RmxAudioPlayer? = null
    private var resetStreamOnPause = true
    private var isWebViewActive = true

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
            // resetStreamOnPause is a top-level option; "options" is reserved for notification options.
            resetStreamOnPause =
                call.getBoolean("resetStreamOnPause", this.resetStreamOnPause) ?: this.resetStreamOnPause
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
        val items: JSArray = call.getArray("items")
        val optionsArgs: JSONObject = call.getObject("options")
        val options = PlaylistItemOptions(optionsArgs)
        Handler(Looper.getMainLooper()).post {

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
        }
        Log.i(TAG, "setPlaylistItems: " + items.toString())
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

            val removals = ArrayList<TrackRemovalItem>()
            for (index in 0 until items.length()) {
                val entry = items.optJSONObject(index) ?: continue
                val trackIndex = entry.optInt("trackIndex", -1)
                val trackId = entry.optString("trackId", "")
                removals.add(TrackRemovalItem(trackIndex, trackId))
            }

            val removedTracks = audioPlayerImpl!!.playlistManager.removeAllItems(removals)
            for (removedItem in removedTracks) {
                onStatus(
                    RmxAudioStatusMessage.RMXSTATUS_ITEM_REMOVED,
                    removedItem.trackId,
                    removedItem.toDict()
                )
            }

            val result = JSObject()
            result.put("removed", removedTracks.size)
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
    fun getPlaylist(call: PluginCall) {
        Handler(Looper.getMainLooper()).post {
            val playlistManager = audioPlayerImpl!!.playlistManager
            val audioTracks = playlistManager.getAllItems()
            val itemsArray = JSONArray()

            for (track in audioTracks) {
                itemsArray.put(track.toDict())
            }

            val result = JSObject()
            result.put("items", itemsArray)
            call.resolve(result)

            Log.i(TAG, "getPlaylist: ${audioTracks.size} items")
        }
    }

    @PluginMethod
    fun play(call: PluginCall) {
        Handler(Looper.getMainLooper()).post {
            val handler = audioPlayerImpl!!.playlistManager.playlistHandler
            if (handler == null || handler.currentMediaPlayer == null) {
                // MediaPlayer was released or MediaService was killed (e.g. after a long video session
                // where audio focus was permanently abandoned). Fall back to beginPlayback which
                // re-starts the service, re-prepares the MediaPlayer, and re-acquires audio focus.
                val posMs = (audioPlayerImpl!!.getLastKnownPositionSec() * 1000f).toLong()
                audioPlayerImpl!!.playlistManager.beginPlayback(posMs, false)
                Log.i(TAG, "play: handler/mediaPlayer was null — re-armed via beginPlayback at ${posMs}ms")
            } else {
                // Handler and MediaPlayer are alive — use the lightweight resume path.
                // Guard against stacking up repeat cycles (playlistcore bug): skip if already playing.
                val isPlaying = handler.currentMediaPlayer?.isPlaying ?: false
                if (!isPlaying) {
                    handler.play()
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
            val seekPosition = (call.getFloat("position", 0f)!! * 1000.0f).toLong()

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
                val seekPosition = (call.getFloat("position", 0f)!! * 1000.0f).toLong()
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

            val seekPosition = (call.getFloat("position", 0f)!! * 1000.0f).toLong()

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

                val seekPosition = (call.getFloat("position", 0f)!! * 1000.0f).toLong()

                audioPlayerImpl!!.playlistManager.beginPlayback(seekPosition, true)
            }
            call.resolve()

            Log.i(TAG, "selectTrackById")
        }
    }

    @PluginMethod
    fun pause(call: PluginCall) {
        Handler(Looper.getMainLooper()).post {
            if (audioPlayerImpl!!.playlistManager.isPlaying) {
                audioPlayerImpl!!.playlistManager.playlistHandler?.pause(false)
            }

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
                (call.getFloat("position", position / 1000.0f)!! * 1000.0f).toLong()

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
    fun setPlaybackVolume(call: PluginCall) {
        Handler(Looper.getMainLooper()).post {
            val volume = call.getFloat("volume", 1.0f)!!
            audioPlayerImpl!!.setVolume(volume)

            call.resolve()

            Log.i(TAG, "setPlaybackVolume: $volume")
        }
    }

    @PluginMethod
    fun prepareForVideoHandoff(call: PluginCall) {
        Handler(Looper.getMainLooper()).post {
            audioPlayerImpl!!.prepareForVideoHandoff()
            call.resolve()
            Log.i(TAG, "prepareForVideoHandoff")
        }
    }

    @PluginMethod
    fun resumeAfterVideoHandoff(call: PluginCall) {
        Handler(Looper.getMainLooper()).post {
            val position = call.getFloat("position", 0f)!!
            audioPlayerImpl!!.resumeAfterVideoHandoff(position)
            call.resolve()
            Log.i(TAG, "resumeAfterVideoHandoff")
        }
    }

    @PluginMethod
    fun getLastKnownPosition(call: PluginCall) {
        Handler(Looper.getMainLooper()).post {
            val position = audioPlayerImpl!!.getLastKnownPositionSec()
            val o = JSObject()
            o.put("position", position.toDouble())
            call.resolve(o)
            Log.i(TAG, "getLastKnownPosition")
        }
    }

    override fun handleOnPause() {
        super.handleOnPause()
        isWebViewActive = false
    }

    override fun handleOnResume() {
        super.handleOnResume()
        isWebViewActive = true
        Handler(Looper.getMainLooper()).post {
            audioPlayerImpl?.emitPlaybackSnapshot()
        }
    }

    override fun handleOnDestroy() {
        Log.d(TAG, "Plugin destroy")
        super.handleOnDestroy()
        destroyResources()
    }

    override fun onError(errorCode: RmxAudioErrorType?, trackId: String?, message: String?) {
        if (statusCallback == null) {
            statusCallback = OnStatusCallback(this)
        }
        val errorObj = OnStatusCallback.createErrorWithCode(errorCode, message)
        onStatus(RmxAudioStatusMessage.RMXSTATUS_ERROR, trackId, errorObj)
    }

    override fun onStatus(what: RmxAudioStatusMessage, trackId: String?, param: JSONObject?) {
        // Defensive: recreate the callback if it was ever cleared (e.g. by an unexpected
        // destroyResources call) so that audio events are never permanently silenced.
        if (statusCallback == null) {
            statusCallback = OnStatusCallback(this)
        }
        statusCallback!!.onStatus(what, trackId, param)
    }

    private fun destroyResources() {
        // Do NOT null statusCallback here — it is bound to the Plugin instance and must remain
        // alive for the entire app lifetime. Nulling it silences all subsequent audio events
        // (PLAYING, PAUSE, PLAYBACK_POSITION, etc.) because onStatus() would early-return.
        // Only clear the playback items so native memory is released.
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

    fun emitStatus(what: RmxAudioStatusMessage, trackId: String?, param: JSONObject?) {
        if (!shouldEmitStatusToBridge(what, isWebViewActive)) {
            return
        }
        val data = JSObject()
        val detail = JSObject()
        detail.put("msgType", what.value)
        detail.put("trackId", trackId)
        detail.put("value", param)
        data.put("action", "status")
        data.put("status", detail)
        Log.v(TAG, "statusChanged:$data")
        notifyListeners("status", data, shouldRetainStatusEvent(what))
    }

    /** @deprecated Use [emitStatus] so retain/gating policy is applied consistently. */
    fun emit(name: String, data: JSObject) {
        this.notifyListeners(name, data, true)
    }

    companion object {
        @JvmStatic
        internal fun shouldEmitStatusToBridge(
            what: RmxAudioStatusMessage,
            isWebViewActive: Boolean
        ): Boolean {
            if (what == RmxAudioStatusMessage.RMXSTATUS_PLAYBACK_POSITION && !isWebViewActive) {
                return false
            }
            return true
        }

        @JvmStatic
        internal fun shouldRetainStatusEvent(what: RmxAudioStatusMessage): Boolean {
            return what != RmxAudioStatusMessage.RMXSTATUS_PLAYBACK_POSITION
        }
    }
}
