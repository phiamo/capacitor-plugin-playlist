package org.dwbn.plugins.playlist.manager

import android.app.Application
import android.util.Log
import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import com.devbrackets.android.exomedia.listener.OnErrorListener
import com.devbrackets.android.playlistcore.api.MediaPlayerApi
import com.devbrackets.android.playlistcore.manager.ListPlaylistManager
import org.dwbn.plugins.playlist.PlaylistItemOptions
import org.dwbn.plugins.playlist.TrackRemovalItem
import org.dwbn.plugins.playlist.data.AudioTrack
import org.dwbn.plugins.playlist.playlist.AudioApi
import org.dwbn.plugins.playlist.service.MediaService
import java.lang.ref.WeakReference
import java.util.*

/**
 * A PlaylistManager that extends the [ListPlaylistManager] for use with the
 * [MediaService] which extends [com.devbrackets.android.playlistcore.service.BasePlaylistService].
 */
class PlaylistManager(application: Application) :
    ListPlaylistManager<AudioTrack>(application, MediaService::class.java), OnErrorListener {
    private val audioTracks: MutableList<AudioTrack> = ArrayList()
    private var volumeLeft = 1.0f
    private var volumeRight = 1.0f
    private var playbackSpeed = 1.0f
    var loop = false
    var isShouldStopPlaylist = false
    var currentErrorTrack: AudioTrack? = null

    // Really need a way to propagate the settings through the app
    var resetStreamOnPause = true
    var options: Options
    private var mediaControlsListener = WeakReference<MediaControlsListener?>(null)
    private var errorListener = WeakReference<OnErrorListener?>(null)
    private var currentMediaPlayer: WeakReference<MediaPlayerApi<AudioTrack>?>? =
        WeakReference(null)

    fun setOnErrorListener(listener: OnErrorListener?) {
        errorListener = WeakReference(listener)
    }

    fun setMediaControlsListener(listener: MediaControlsListener?) {
        mediaControlsListener = WeakReference(listener)
    }

    val isPlaying: Boolean
        get() = playlistHandler != null && playlistHandler!!.currentMediaPlayer != null && playlistHandler!!.currentMediaPlayer!!.isPlaying

    override fun onError(e: Exception?): Boolean {

        if (e != null && errorListener.get() != null) {
            Log.i(TAG, "onError: $e")
            errorListener.get()!!.onError(e)
        }
        return true
    }

    /*
     * isNextAvailable, getCurrentItem, and next() are overridden because there is
     * a glaring bug in playlist core where when an item completes, isNextAvailable and
     * getCurrentItem return wildly contradictory things, resulting in endless repeat
     * of the last item in the playlist.
     */
    override val isNextAvailable: Boolean
        get() {
            if (itemCount <= 1) {
                return false;
            }
            val isAtEnd = currentPosition + 1 >= itemCount
            val isConstrained = currentPosition + 1 in 0 until itemCount
            return if (isAtEnd) {
                loop
            } else isConstrained
        }

    override operator fun next(): AudioTrack? {
        if (isNextAvailable) {
            val isAtEnd = currentPosition + 1 >= itemCount
            currentPosition = if (isAtEnd && loop) {
                0
            } else {
                (currentPosition + 1).coerceAtMost(itemCount)
            }
        } else {
            if (loop) {
                currentPosition = INVALID_POSITION
            } else {
                isShouldStopPlaylist = true
                return null
            }
        }

        return currentItem
    }


    /*
     * List management
     */
    fun setAllItems(items: List<AudioTrack>?, options: PlaylistItemOptions) {
        clearItems()
        addAllItems(items)
        currentPosition = 0
        // If the options said to start from a specific position, do so.
        var seekStart: Long = 0
        if (options.playFromPosition > 0) {
            seekStart = options.playFromPosition
        } else if (options.retainPosition) {
            val progress = currentProgress
            if (progress != null) {
              seekStart = progress.position
            }
        }

        // If the options said to start from a specific id, do so.
        var idStart: String? = null
        if (options.playFromId != null) {
            idStart = options.playFromId
        }
        if (idStart != null && "" != idStart) {
            val code = idStart.hashCode()
            setCurrentItem(code.toLong())
        }

        // We assume that if the playlist is fully loaded in one go,
        // that the next thing to happen will be to play. So let's start
        // paused, which will allow the player to pre-buffer until the
        // user says Go.
        beginPlayback(seekStart, options.startPaused)
    }

    fun addItem(item: AudioTrack?) {
        if (item == null) {
            return
        }
        val countBefore = audioTracks.size;
        audioTracks.add(item)
        items = audioTracks
        if (countBefore == 0) {
            currentPosition = 0
            beginPlayback(1, true)
        }
        if (this.playlistHandler != null) {
            this.playlistHandler!!.updateMediaControls()
        }
    }

    fun addAllItems(its: List<AudioTrack>?) {
        val currentItem = currentItem // may be null
        audioTracks.addAll(its!!)
        items =
            audioTracks // not *strictly* needed since they share the reference, but for good measure..
        currentPosition = audioTracks.indexOf(currentItem)
    }

    fun removeItem(index: Int, itemId: String): AudioTrack? {
        val wasPlaying = isPlaying
        if (playlistHandler != null) {
            playlistHandler!!.pause(true)
        }
        var currentPosition = currentPosition
        var foundItem: AudioTrack? = null
        var removingCurrent = false

        // If isPlaying is true, and currentItem is not null,
        // that implies that currentItem is the currently playing item.
        // If removingCurrent gets set to true, we are removing the currently playing item,
        // and we need to restart playback once we do.
        val resolvedIndex = resolveItemPosition(index, itemId)
        if (resolvedIndex >= 0) {
            foundItem = audioTracks[resolvedIndex]
            if (foundItem == currentItem) {
                removingCurrent = true
            }
            audioTracks.removeAt(resolvedIndex)
        }
        items = audioTracks
        currentPosition = if (removingCurrent) currentPosition else audioTracks.indexOf(currentItem)
        beginPlayback(currentPosition.toLong(), !wasPlaying)
        if (this.playlistHandler != null) {
            this.playlistHandler!!.updateMediaControls()
        }
        return foundItem
    }

    fun removeAllItems(its: ArrayList<TrackRemovalItem>): ArrayList<AudioTrack> {
        val removedTracks = ArrayList<AudioTrack>()
        val wasPlaying = isPlaying
        if (playlistHandler != null) {
            playlistHandler!!.pause(true)
        }
        var currentPosition = currentPosition
        val currentItem = currentItem // may be null
        var removingCurrent = false
        for (item in its) {
            val resolvedIndex = resolveItemPosition(item.trackIndex, item.trackId)
            if (resolvedIndex >= 0) {
                val foundItem = audioTracks[resolvedIndex]
                if (foundItem == currentItem) {
                    removingCurrent = true
                }
                removedTracks.add(foundItem)
                audioTracks.removeAt(resolvedIndex)
            }
        }
        items = audioTracks
        currentPosition = if (removingCurrent) currentPosition else audioTracks.indexOf(currentItem)
        beginPlayback(currentPosition.toLong(), !wasPlaying)
        return removedTracks
    }

    fun clearItems() {
        if (playlistHandler != null) {
            playlistHandler!!.stop()
        }
        audioTracks.clear()
        items = audioTracks
        currentPosition = INVALID_POSITION
    }

    private fun resolveItemPosition(trackIndex: Int, trackId: String): Int {
        var resolvedPosition = -1
        if (trackIndex >= 0 && trackIndex < audioTracks.size) {
            resolvedPosition = trackIndex
        } else if ("" != trackId) {
            val itemPos = getPositionForItem(trackId.hashCode().toLong())
            if (itemPos != INVALID_POSITION) {
                resolvedPosition = itemPos
            }
        }
        return resolvedPosition
    }

    fun getVolumeLeft(): Float {
        return volumeLeft
    }

    fun getVolumeRight(): Float {
        return volumeRight
    }

    fun setVolume(
        @FloatRange(from = 0.0, to = 1.0) left: Float,
        @FloatRange(from = 0.0, to = 1.0) right: Float
    ) {
        volumeLeft = left
        volumeRight = right
        if (currentMediaPlayer != null && currentMediaPlayer!!.get() != null) {
            Log.i("PlaylistManager", "setVolume completing with volume = $left")
            currentMediaPlayer!!.get()!!.setVolume(volumeLeft, volumeRight)
        }
    }

    fun getPlaybackSpeed(): Float {
        return playbackSpeed
    }

    fun setPlaybackSpeed(@FloatRange(from = 0.0, to = 1.0) speed: Float) {
        playbackSpeed = speed
        if (playlistHandler!!.currentMediaPlayer != null &&  playlistHandler!!.currentMediaPlayer!! is AudioApi) {
            Log.i(TAG, "setPlaybackSpeed completing with speed = $speed")
            (playlistHandler!!.currentMediaPlayer as AudioApi?)!!.setPlaybackSpeed(playbackSpeed)
        }
    }

    fun beginPlayback(@IntRange(from = 0) seekPosition: Long, startPaused: Boolean) {
        currentItem ?: return
        super.play(seekPosition, startPaused)
        try {
            setVolume(volumeLeft, volumeRight)
            setPlaybackSpeed(playbackSpeed)
        } catch (e: Exception) {
            Log.w(TAG, "beginPlayback: Error setting volume or playback speed: " + e.message)
        }
    }

    companion object {
        private const val TAG = "PlaylistManager"
    }

    init {
        setParameters(audioTracks, 0)
        options = Options(application.baseContext)
    }

}
