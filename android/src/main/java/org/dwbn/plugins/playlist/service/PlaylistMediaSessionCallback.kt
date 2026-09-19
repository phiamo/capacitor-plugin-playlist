package org.dwbn.plugins.playlist.service

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession

/**
 * Exposes play/pause and playlist skip commands to the system media notification controller
 * without stripping Media3's default metadata commands.
 */
@OptIn(UnstableApi::class)
internal class PlaylistMediaSessionCallback(
    private val skipAvailability: () -> SkipAvailability
) : MediaSession.Callback {

    data class SkipAvailability(
        val previousAvailable: Boolean,
        val nextAvailable: Boolean
    )

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        if (session.isMediaNotificationController(controller)) {
            val availability = skipAvailability()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(
                    MediaNotificationPolicy.playerCommandsForMediaNotification(
                        MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
                        availability.previousAvailable,
                        availability.nextAvailable
                    )
                )
                .build()
        }
        return super.onConnect(session, controller)
    }
}
