package org.dwbn.plugins.playlist.service

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/** Blocks MediaSession / notification play while video owns focus (Story 55.6). */
@OptIn(UnstableApi::class)
internal class HandoffForwardingPlayer(
    player: Player,
    private val retain: () -> Boolean,
    private val onSkipToNext: (() -> Unit)? = null,
    private val onSkipToPrevious: (() -> Unit)? = null,
    private val previousAvailable: (() -> Boolean)? = null,
    private val nextAvailable: (() -> Boolean)? = null
) : ForwardingPlayer(player) {
    override fun play() {
        if (MediaNotificationPolicy.shouldIgnoreSessionPlay(retain())) {
            return
        }
        super.play()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady && MediaNotificationPolicy.shouldIgnoreSessionPlay(retain())) {
            return
        }
        super.setPlayWhenReady(playWhenReady)
    }

    override fun seekToNextMediaItem() {
        onSkipToNext?.invoke() ?: super.seekToNextMediaItem()
    }

    override fun seekToPreviousMediaItem() {
        onSkipToPrevious?.invoke() ?: super.seekToPreviousMediaItem()
    }

    override fun getAvailableCommands(): Player.Commands {
        val previous = previousAvailable
        val next = nextAvailable
        if (previous == null && next == null) {
            return super.getAvailableCommands()
        }
        return MediaNotificationPolicy.playerCommandsForMediaNotification(
            super.getAvailableCommands(),
            previous?.invoke() ?: false,
            next?.invoke() ?: false
        )
    }
}
