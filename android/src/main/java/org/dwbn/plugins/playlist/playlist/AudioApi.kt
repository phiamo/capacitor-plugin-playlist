
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

    override val isPlaying: Boolean
        get() = audioPlayer.isPlaying

    override val handlesOwnAudioFocus: Boolean
        get() = false

    override val currentPosition: Long
        get() = if (prepared) audioPlayer.currentPosition else 0

    override val duration: Long
        get() = if (prepared) audioPlayer.duration else 0

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
        audioPlayer.setAnalyticsListener(EventLogger(null))
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
        audioPlayer.seekTo(milliseconds.toInt().toLong())
    }

    override fun handlesItem(item: AudioTrack): Boolean {
        return item.mediaType == BasePlaylistManager.AUDIO
    }

    override fun playItem(item: AudioTrack) {
        try {
            bufferPercent = 0
            audioPlayer.setMedia(Uri.parse(if (item.downloaded) item.downloadedMediaUri else item.mediaUrl))
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
