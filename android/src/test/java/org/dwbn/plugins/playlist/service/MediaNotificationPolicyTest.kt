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

    @Test
    fun prepareForVideoHandoff_pausesRegardlessOfIsPlaying() {
        assertTrue(MediaNotificationPolicy.shouldPauseForVideoHandoff(true))
        assertTrue(MediaNotificationPolicy.shouldPauseForVideoHandoff(false))
    }

    @Test
    fun prepareForVideoHandoff_retainsForeground() {
        assertTrue(MediaNotificationPolicy.shouldRetainForegroundOnPrepare())
    }

    @Test
    fun retain_forcesNotificationForegroundPastMedia3Timeout() {
        assertTrue(MediaNotificationPolicy.shouldForceNotificationForeground(true))
        assertFalse(MediaNotificationPolicy.shouldForceNotificationForeground(false))
        assertTrue(
            MediaNotificationPolicy.startInForegroundRequired(
                videoHandoffForegroundRetain = true,
                media3Requested = false
            )
        )
        assertTrue(
            MediaNotificationPolicy.startInForegroundRequired(
                videoHandoffForegroundRetain = true,
                media3Requested = true
            )
        )
        assertFalse(
            MediaNotificationPolicy.startInForegroundRequired(
                videoHandoffForegroundRetain = false,
                media3Requested = false
            )
        )
        assertTrue(
            MediaNotificationPolicy.startInForegroundRequired(
                videoHandoffForegroundRetain = false,
                media3Requested = true
            )
        )
        assertTrue(
            MediaService::class.java.declaredMethods.any { it.name == "onUpdateNotification" }
        )
    }

    @Test
    fun retain_ignoresSessionPlay() {
        assertTrue(MediaNotificationPolicy.shouldIgnoreSessionPlay(true))
        assertFalse(MediaNotificationPolicy.shouldIgnoreSessionPlay(false))
    }

    @Test
    fun audibleResume_playsThenSeeks() {
        assertTrue(MediaNotificationPolicy.shouldPlayThenSeekOnResume())
    }

    @Test
    fun lastKnownPosition_isHandoffSnapshot() {
        assertEquals(42.5f, MediaNotificationPolicy.lastKnownPositionSec(42.5f), 0f)
        assertEquals(0f, MediaNotificationPolicy.lastKnownPositionSec(0f), 0f)
    }

    @Test
    fun resume_reportsInPlaceOnlyWhenForegroundUp() {
        assertTrue(
            MediaNotificationPolicy.shouldReportInPlaceResumed(
                prewarm = false,
                play = true,
                retain = true,
                serviceInForeground = true
            )
        )
        assertFalse(
            MediaNotificationPolicy.shouldReportInPlaceResumed(
                prewarm = true,
                play = true,
                retain = true,
                serviceInForeground = true
            )
        )
        assertFalse(
            MediaNotificationPolicy.shouldReportInPlaceResumed(
                prewarm = false,
                play = true,
                retain = true,
                serviceInForeground = false
            )
        )
        assertFalse(
            MediaNotificationPolicy.shouldReportInPlaceResumed(
                prewarm = false,
                play = false,
                retain = true,
                serviceInForeground = true
            )
        )
    }
}
