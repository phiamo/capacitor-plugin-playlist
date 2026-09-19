package org.dwbn.plugins.playlist.handoff

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import org.dwbn.plugins.playlist.service.MediaService
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Process-local link from the video plugin ExoPlayer to the playlist [MediaService] session.
 * Video attaches via reflection so the video module does not depend on the playlist module.
 */
@OptIn(UnstableApi::class)
object VideoPlayerBridge {
    @Volatile
    private var activePlayer: Player? = null

    private var playbackListener: Player.Listener? = null

    private val playbackChangedListeners = CopyOnWriteArrayList<() -> Unit>()

    @JvmStatic
    fun attach(player: Player) {
        detach(player)
        activePlayer = player
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_PLAY_WHEN_READY_CHANGED,
                        Player.EVENT_PLAYBACK_STATE_CHANGED,
                        Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_MEDIA_METADATA_CHANGED,
                        Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_POSITION_DISCONTINUITY
                    )
                ) {
                    notifyPlaybackChanged()
                }
            }
        }
        playbackListener = listener
        player.addListener(listener)
        notifyPlaybackChanged()
    }

    @JvmStatic
    fun detach(player: Player?) {
        if (player == null) {
            detachInternal()
            notifyPlaybackChanged()
            return
        }
        if (activePlayer !== player) {
            return
        }
        detachInternal()
        notifyPlaybackChanged()
    }

    @JvmStatic
    fun activePlayer(): Player? = activePlayer

    @JvmStatic
    fun hasActivePlayer(): Boolean = activePlayer != null

    @JvmStatic
    fun addPlaybackChangedListener(listener: () -> Unit) {
        playbackChangedListeners.addIfAbsent(listener)
    }

    @JvmStatic
    fun removePlaybackChangedListener(listener: () -> Unit) {
        playbackChangedListeners.remove(listener)
    }

    @JvmStatic
    fun requestNotificationRefresh() {
        MediaService.instance?.triggerNotificationUpdate()
    }

    /** Test-only reset. */
    @JvmStatic
    internal fun clearForTests() {
        detachInternal()
        playbackChangedListeners.clear()
    }

    private fun notifyPlaybackChanged() {
        playbackChangedListeners.forEach { it() }
        requestNotificationRefresh()
    }

    private fun detachInternal() {
        val player = activePlayer
        val listener = playbackListener
        if (player != null && listener != null) {
            player.removeListener(listener)
        }
        activePlayer = null
        playbackListener = null
    }
}
