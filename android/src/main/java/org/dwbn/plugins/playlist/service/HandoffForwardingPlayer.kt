package org.dwbn.plugins.playlist.service

import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import org.dwbn.plugins.playlist.handoff.VideoPlayerBridge
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Routes the playlist MediaSession to the video ExoPlayer while handoff retain is active and video
 * is attached; otherwise blocks play during retain-only prewarm (Story 55.6).
 */
@OptIn(UnstableApi::class)
internal class HandoffForwardingPlayer(
    player: Player,
    private val retain: () -> Boolean,
    private val onSkipToNext: (() -> Unit)? = null,
    private val onSkipToPrevious: (() -> Unit)? = null,
    private val previousAvailable: (() -> Boolean)? = null,
    private val nextAvailable: (() -> Boolean)? = null
) : ForwardingPlayer(player) {

    private val sessionListeners = CopyOnWriteArrayList<Player.Listener>()
    private val playbackRelay = { dispatchVideoPlaybackToSession() }

    init {
        VideoPlayerBridge.addPlaybackChangedListener(playbackRelay)
        if (VideoPlayerBridge.hasActivePlayer()) {
            playbackRelay()
        }
    }

    override fun addListener(listener: Player.Listener) {
        super.addListener(listener)
        sessionListeners.addIfAbsent(listener)
        if (videoPlayer() != null) {
            playbackRelay()
        }
    }

    override fun removeListener(listener: Player.Listener) {
        super.removeListener(listener)
        sessionListeners.remove(listener)
    }

    private fun videoPlayer(): Player? {
        if (!retain() || !VideoPlayerBridge.hasActivePlayer()) {
            return null
        }
        return VideoPlayerBridge.activePlayer()
    }

    override fun play() {
        val video = videoPlayer()
        if (video != null) {
            video.play()
            return
        }
        if (MediaNotificationPolicy.shouldIgnoreSessionPlay(retain(), VideoPlayerBridge.hasActivePlayer())) {
            return
        }
        super.play()
    }

    override fun pause() {
        videoPlayer()?.pause() ?: super.pause()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        val video = videoPlayer()
        if (video != null) {
            video.playWhenReady = playWhenReady
            return
        }
        if (playWhenReady && MediaNotificationPolicy.shouldIgnoreSessionPlay(retain(), VideoPlayerBridge.hasActivePlayer())) {
            return
        }
        super.setPlayWhenReady(playWhenReady)
    }

    override fun getPlayWhenReady(): Boolean = videoPlayer()?.playWhenReady ?: super.getPlayWhenReady()

    override fun isPlaying(): Boolean {
        val video = videoPlayer()
        if (video != null) {
            return video.playWhenReady && video.playbackState == Player.STATE_READY
        }
        return super.isPlaying()
    }

    override fun getPlaybackState(): Int = videoPlayer()?.playbackState ?: super.getPlaybackState()

    override fun getCurrentPosition(): Long = videoPlayer()?.currentPosition ?: super.getCurrentPosition()

    override fun getContentPosition(): Long = videoPlayer()?.contentPosition ?: super.getContentPosition()

    override fun getDuration(): Long = videoPlayer()?.duration ?: super.getDuration()

    override fun getBufferedPosition(): Long = videoPlayer()?.bufferedPosition ?: super.getBufferedPosition()

    override fun seekTo(positionMs: Long) {
        videoPlayer()?.seekTo(positionMs) ?: super.seekTo(positionMs)
    }

    override fun seekTo(windowIndex: Int, positionMs: Long) {
        videoPlayer()?.seekTo(windowIndex, positionMs) ?: super.seekTo(windowIndex, positionMs)
    }

    override fun seekToNextMediaItem() {
        if (videoPlayer() != null) {
            return
        }
        onSkipToNext?.invoke() ?: super.seekToNextMediaItem()
    }

    override fun seekToPreviousMediaItem() {
        if (videoPlayer() != null) {
            return
        }
        onSkipToPrevious?.invoke() ?: super.seekToPreviousMediaItem()
    }

    override fun getAvailableCommands(): Player.Commands {
        val previous = previousAvailable
        val next = nextAvailable
        if (previous == null && next == null) {
            return super.getAvailableCommands()
        }
        val retainActive = retain()
        val videoAttached = VideoPlayerBridge.hasActivePlayer()
        return MediaNotificationPolicy.playerCommandsForMediaNotification(
            super.getAvailableCommands(),
            MediaNotificationPolicy.mediaNotificationSkipPreviousEnabled(
                previous?.invoke() ?: false,
                retainActive,
                videoAttached
            ),
            MediaNotificationPolicy.mediaNotificationSkipNextEnabled(
                next?.invoke() ?: false,
                retainActive,
                videoAttached
            )
        )
    }

    private fun dispatchVideoPlaybackToSession() {
        val looper = applicationLooperOrNull()
        if (looper != null && Looper.myLooper() != looper) {
            Handler(looper).post { dispatchVideoPlaybackToSessionOnLooper() }
            return
        }
        dispatchVideoPlaybackToSessionOnLooper()
    }

    private fun dispatchVideoPlaybackToSessionOnLooper() {
        if (videoPlayer() == null) {
            return
        }
        val ready = playWhenReady
        val state = playbackState
        val playing = isPlaying
        val commands = try {
            availableCommands
        } catch (_: Throwable) {
            null
        }
        for (listener in sessionListeners) {
            listener.onPlayWhenReadyChanged(ready, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            listener.onPlaybackStateChanged(state)
            listener.onIsPlayingChanged(playing)
            if (commands != null) {
                listener.onAvailableCommandsChanged(commands)
            }
        }
    }

    private fun applicationLooperOrNull(): Looper? = try {
        applicationLooper
    } catch (_: Throwable) {
        null
    }
}
