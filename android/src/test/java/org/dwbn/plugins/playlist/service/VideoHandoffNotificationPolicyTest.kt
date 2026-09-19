package org.dwbn.plugins.playlist.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoHandoffNotificationPolicyTest {

    @Test
    fun retainWithoutVideo_stillIgnoresSessionPlay() {
        assertTrue(MediaNotificationPolicy.shouldIgnoreSessionPlay(true, videoHandoffPlayerAttached = false))
    }

    @Test
    fun retainWithVideo_doesNotIgnoreSessionPlay() {
        assertFalse(MediaNotificationPolicy.shouldIgnoreSessionPlay(true, videoHandoffPlayerAttached = true))
    }

    @Test
    fun videoHandoff_hidesSkipCommandsEvenWhenPlaylistWouldAllow() {
        assertFalse(
            MediaNotificationPolicy.mediaNotificationSkipNextEnabled(
                nextAvailable = true,
                videoHandoffForegroundRetain = true,
                videoHandoffPlayerAttached = true
            )
        )
        assertFalse(
            MediaNotificationPolicy.mediaNotificationSkipPreviousEnabled(
                previousAvailable = true,
                videoHandoffForegroundRetain = true,
                videoHandoffPlayerAttached = true
            )
        )
    }

    @Test
    fun playlistSkipRestoredWhenVideoDetached() {
        assertTrue(
            MediaNotificationPolicy.mediaNotificationSkipNextEnabled(
                nextAvailable = true,
                videoHandoffForegroundRetain = true,
                videoHandoffPlayerAttached = false
            )
        )
    }
}
