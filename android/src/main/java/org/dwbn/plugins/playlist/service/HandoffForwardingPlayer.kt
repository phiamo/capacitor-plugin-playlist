package org.dwbn.plugins.playlist.service

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/** Blocks MediaSession / notification play while video owns focus (Story 55.6). */
@OptIn(UnstableApi::class)
internal class HandoffForwardingPlayer(
    player: Player,
    private val retain: () -> Boolean
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
}
