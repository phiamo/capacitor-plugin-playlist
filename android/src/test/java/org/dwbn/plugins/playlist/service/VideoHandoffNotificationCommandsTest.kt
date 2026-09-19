package org.dwbn.plugins.playlist.service

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VideoHandoffNotificationCommandsTest {

    @Test
    fun videoHandoff_playerCommandsHideSkipButKeepPlayPause() {
        val commands = MediaNotificationPolicy.playerCommandsForMediaNotification(
            Player.Commands.EMPTY,
            MediaNotificationPolicy.mediaNotificationSkipPreviousEnabled(
                previousAvailable = true,
                videoHandoffForegroundRetain = true,
                videoHandoffPlayerAttached = true
            ),
            MediaNotificationPolicy.mediaNotificationSkipNextEnabled(
                nextAvailable = true,
                videoHandoffForegroundRetain = true,
                videoHandoffPlayerAttached = true
            )
        )
        assertTrue(commands.contains(Player.COMMAND_PLAY_PAUSE))
        assertFalse(commands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertFalse(commands.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
    }

    @Test
    fun retainWithoutVideo_playerCommandsExposeSkipWhenAvailable() {
        val commands = MediaNotificationPolicy.playerCommandsForMediaNotification(
            Player.Commands.EMPTY,
            MediaNotificationPolicy.mediaNotificationSkipPreviousEnabled(
                previousAvailable = true,
                videoHandoffForegroundRetain = true,
                videoHandoffPlayerAttached = false
            ),
            MediaNotificationPolicy.mediaNotificationSkipNextEnabled(
                nextAvailable = true,
                videoHandoffForegroundRetain = true,
                videoHandoffPlayerAttached = false
            )
        )
        assertTrue(commands.contains(Player.COMMAND_PLAY_PAUSE))
        assertTrue(commands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(commands.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
    }
}
