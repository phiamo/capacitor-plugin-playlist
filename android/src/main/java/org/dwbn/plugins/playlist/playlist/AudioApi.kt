
package org.dwbn.plugins.playlist.playlist

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.PowerManager
import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.util.EventLogger
import com.devbrackets.android.exomedia.AudioPlayer
import com.devbrackets.android.exomedia.listener.OnErrorListener
import com.devbrackets.android.playlistcore.manager.BasePlaylistManager
import org.dwbn.plugins.playlist.data.AudioTrack
import java.lang.ref.WeakReference
import java.util.concurrent.locks.ReentrantLock

@OptIn(UnstableApi::class)
class AudioApi(context: Context) : BaseMediaApi() {
    private val audioPlayer: AudioPlayer = AudioPlayer(context.applicationContext)

    private val errorListenersLock = ReentrantLock(true)
    private val errorListeners = ArrayList<WeakReference<OnErrorListener>>()
    
    // Store the current track for excerpt handling
    private var currentTrack: AudioTrack? = null

    override val isPlaying: Boolean
        get() = audioPlayer.isPlaying

    override val handlesOwnAudioFocus: Boolean
        get() = false

    override val currentPosition: Long
        get() = if (prepared) {
            // Convert absolute position to excerpt-relative position
            val absolutePosition = audioPlayer.currentPosition
            val track = currentTrack
            
            if (track != null) {
                val startTimeMs = (track.startTime * 1000).toLong()
                Math.max(0, absolutePosition - startTimeMs)
            } else {
                absolutePosition
            }
        } else 0

    override val duration: Long
        get() = if (prepared) {
            // Return excerpt duration instead of absolute duration
            val absoluteDuration = audioPlayer.duration
            val track = currentTrack
            
            if (track != null) {
                val startTimeMs = (track.startTime * 1000).toLong()
                val endTimeMs = track.endTime?.let { (it * 1000).toLong() } ?: absoluteDuration
                val excerptDuration = endTimeMs - startTimeMs
                Math.max(0, excerptDuration)
            } else {
                absoluteDuration
            }
        } else 0

    override val bufferedPercent: Int
        get() = bufferPercent

    init {
        audioPlayer.setOnErrorListener(this)
        audioPlayer.setOnPreparedListener(this)
        audioPlayer.setOnCompletionListener(this)
        audioPlayer.setOnSeekCompletionListener(this)
        audioPlayer.setOnBufferUpdateListener(this)

        audioPlayer.setWakeLevel(PowerManager.PARTIAL_WAKE_LOCK)
        audioPlayer.setAudioAttributes(getAudioAttributes(C.USAGE_MEDIA, C.AUDIO_CONTENT_TYPE_MUSIC))
        audioPlayer.setAnalyticsListener(EventLogger())
    }

    override fun play() {
        audioPlayer.start()
    }

    override fun pause() {
        audioPlayer.pause()
    }

    override fun stop() {
        audioPlayer.stop()
    }

    override fun reset() {
        audioPlayer.reset()
    }

    override fun release() {
        audioPlayer.release()
    }

    override fun setVolume(@FloatRange(from = 0.0, to = 1.0) left: Float, @FloatRange(from = 0.0, to = 1.0) right: Float) {
        audioPlayer.volume = (left + right) / 2
    }

    override fun seekTo(@IntRange(from = 0L) milliseconds: Long) {
        // The input milliseconds parameter is excerpt-relative position from system player
        // Convert to absolute position for the media player
        val track = currentTrack
        if (track != null) {
            val startTimeMs = (track.startTime * 1000).toLong()
            val absolutePosition = milliseconds + startTimeMs
            audioPlayer.seekTo(absolutePosition)
        } else {
            audioPlayer.seekTo(milliseconds)
        }
    }

    override fun handlesItem(item: AudioTrack): Boolean {
        return item.mediaType == BasePlaylistManager.AUDIO
    }

    override fun playItem(item: AudioTrack) {
        try {
            bufferPercent = 0
            audioPlayer.setMedia(Uri.parse(if (item.downloaded) item.downloadedMediaUri else item.mediaUrl))
            currentTrack = item
        } catch (e: Exception) {
            //Purposefully left blank
        }
    }

    fun setPlaybackSpeed(@FloatRange(from = 0.0, to = 1.0) speed: Float) {
        audioPlayer.setPlaybackSpeed(speed)
    }

    fun addErrorListener(listener: OnErrorListener) {
        errorListenersLock.lock()
        errorListeners.add(WeakReference<OnErrorListener>(listener))
        errorListenersLock.unlock()
    }

    @Suppress("SameParameterValue")
    private fun getAudioAttributes(@C.AudioUsage usage: Int, @C.AudioContentType contentType: Int): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(contentType)
            .build()
    }
}
