package org.dwbn.plugins.playlist.service

import android.content.Intent
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSessionService
import org.dwbn.plugins.playlist.manager.PlaylistManager
import org.dwbn.plugins.playlist.playlist.AudioPlaylistHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaNotificationPolicyTest {

    @Test
    fun backgroundPlay_usesDefaultMediaNotificationProviderOnMediaSessionService() {
        assertEquals(
            DefaultMediaNotificationProvider::class.java,
            MediaNotificationPolicy.mediaNotificationProviderClass
        )
        assertTrue(MediaSessionService::class.java.isAssignableFrom(MediaService::class.java))
        assertFalse(MediaNotificationPolicy.shouldStartForegroundOnPromote())
        assertFalse(MediaNotificationPolicy.ALLOW_START_FOREGROUND_ON_PROMOTE)
        assertEquals(
            MediaSessionService.SHOW_NOTIFICATION_FOR_IDLE_PLAYER_ALWAYS,
            MediaNotificationPolicy.SHOW_NOTIFICATION_WHEN_IDLE
        )
    }

    @Test
    fun artworkPresent_loadsRemoteArtwork() {
        val uri = "https://example.com/art.jpg"
        assertTrue(MediaNotificationPolicy.shouldLoadRemoteArtwork(uri))
        assertFalse(MediaNotificationPolicy.hasNoLargeArtwork(null, uri))
    }

    @Test
    fun artworkMissing_hasNoLargeArt() {
        assertTrue(MediaNotificationPolicy.hasNoLargeArtwork(null, null))
        assertTrue(MediaNotificationPolicy.hasNoLargeArtwork(null, ""))
        assertFalse(MediaNotificationPolicy.shouldLoadRemoteArtwork(null))
        assertFalse(MediaNotificationPolicy.shouldLoadRemoteArtwork(""))
    }

    @Test
    fun focusLoss_media3HandlesFocusWithoutAudioManagerApi() {
        assertTrue(MediaNotificationPolicy.HANDLE_AUDIO_FOCUS)
        assertFalse(MediaNotificationPolicy.shouldRequestLegacyAudioFocus())
        val managerMethods = PlaylistManager::class.java.methods.map { it.name }
        assertFalse(managerMethods.contains("requestAudioFocus"))
        assertFalse(managerMethods.contains("abandonAudioFocus"))
        val handlerMethods = AudioPlaylistHandler::class.java.methods.map { it.name }
        assertFalse(handlerMethods.contains("requestAudioFocus"))
        assertFalse(handlerMethods.contains("abandonAudioFocus"))
    }

    @Test
    fun notificationTap_usesHostLaunchReorderFlags() {
        val flags = MediaNotificationPolicy.sessionLaunchIntentFlags()
        assertEquals(
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            flags
        )
    }

    @Test
    fun handoffRetain_skipsEndForeground() {
        assertTrue(MediaNotificationPolicy.shouldSkipEndForeground(true))
        assertFalse(MediaNotificationPolicy.shouldSkipEndForeground(false))
    }
}
