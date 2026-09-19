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
        assertTrue(MediaNotificationPolicy.hasNoLargeArtwork(ByteArray(0), null))
        assertFalse(MediaNotificationPolicy.hasNoLargeArtwork(ByteArray(0), "https://example.com/art.jpg"))
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
    fun foregroundStartFailure_securityExceptionIsRecoverable() {
        assertTrue(MediaNotificationPolicy.isForegroundStartSecurityBlocked(SecurityException("denied")))
        assertFalse(MediaNotificationPolicy.isForegroundStartSecurityBlocked(IllegalStateException()))
    }

    @Test
    fun prepareForVideoHandoff_retainsForeground() {
        val retainCalls = mutableListOf<Boolean>()
        MediaNotificationPolicy.applyForegroundRetainOnPrepare { retainCalls.add(true) }
        assertEquals(listOf(true), retainCalls)
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
        assertTrue(MediaService.resolveHandoffNotificationForeground(true, false))
        assertFalse(MediaService.resolveHandoffNotificationForeground(false, false))
    }

    @Test
    fun retain_playWhenReadyTrue_forcesPause() {
        val pauses = mutableListOf<Boolean>()
        MediaNotificationPolicy.applyRetainPlayWhenReadyGuard(
            playWhenReady = true,
            videoHandoffForegroundRetain = true,
            videoHandoffPlayerAttached = false
        ) { pauses.add(true) }
        assertEquals(listOf(true), pauses)
    }

    @Test
    fun retain_playWhenReadyFalse_doesNotForcePause() {
        val pauses = mutableListOf<Boolean>()
        MediaNotificationPolicy.applyRetainPlayWhenReadyGuard(
            playWhenReady = false,
            videoHandoffForegroundRetain = true,
            videoHandoffPlayerAttached = false
        ) { pauses.add(true) }
        assertTrue(pauses.isEmpty())
    }

    @Test
    fun retainWithVideoAttached_playWhenReadyTrue_doesNotForcePause() {
        val pauses = mutableListOf<Boolean>()
        MediaNotificationPolicy.applyRetainPlayWhenReadyGuard(
            playWhenReady = true,
            videoHandoffForegroundRetain = true,
            videoHandoffPlayerAttached = true
        ) { pauses.add(true) }
        assertTrue(pauses.isEmpty())
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

    @Test
    fun failedInPlaceResume_doesNotBeginPlayback() {
        val beginPlaybackCalls = mutableListOf<String>()
        MediaNotificationPolicy.applyBeginPlaybackWhenInPlaceUnavailable {
            beginPlaybackCalls.add("beginPlayback")
        }
        assertTrue(beginPlaybackCalls.isEmpty())
        assertFalse(MediaNotificationPolicy.shouldBeginPlaybackWhenInPlaceUnavailable())
    }

    @Test
    fun pausedVideoExit_doesNotClaimInPlaceResume() {
        assertFalse(
            MediaNotificationPolicy.shouldReportInPlaceResumed(
                prewarm = false,
                play = false,
                retain = true,
                serviceInForeground = true
            )
        )
        assertFalse(MediaNotificationPolicy.shouldBeginPlaybackWhenInPlaceUnavailable())
    }

    @Test
    fun retainMustClearBeforeAudiblePlay() {
        assertTrue(MediaNotificationPolicy.shouldIgnoreSessionPlay(true))
        assertFalse(MediaNotificationPolicy.shouldIgnoreSessionPlay(false))
    }

    @Test
    fun mediaSessionId_isDistinctFromEmptyDefault() {
        assertEquals("org.dwbn.playlist", MediaNotificationPolicy.MEDIA_SESSION_ID)
        assertFalse(MediaNotificationPolicy.MEDIA_SESSION_ID.isEmpty())
    }

    @Test
    fun ensureService_skipsWhenAlreadyCreated() {
        assertFalse(MediaNotificationPolicy.shouldStartForegroundService(true))
        assertTrue(MediaNotificationPolicy.shouldStartForegroundService(false))
    }

    @Test
    fun mediaNotificationSkipFlags_followPlaylistAvailability() {
        assertFalse(MediaNotificationPolicy.mediaNotificationSkipPreviousEnabled(false))
        assertTrue(MediaNotificationPolicy.mediaNotificationSkipPreviousEnabled(true))
        assertFalse(MediaNotificationPolicy.mediaNotificationSkipNextEnabled(false))
        assertTrue(MediaNotificationPolicy.mediaNotificationSkipNextEnabled(true))
    }

    @Test
    fun onStartCommand_doesNotOverwriteMedia3Notification() {
        assertFalse(MediaNotificationPolicy.shouldOverwriteMedia3NotificationOnStartCommand())
        assertFalse(MediaNotificationPolicy.shouldAllowTitleOnlyNotificationAfterSession())
        val methodNames = MediaService::class.java.declaredMethods.map { it.name }
        assertTrue(methodNames.contains("startForegroundImmediately"))
        assertTrue(methodNames.contains("startForegroundWithMedia3Notification"))
        assertTrue(methodNames.contains("onUpdateNotificationAsync"))
        assertTrue(methodNames.contains("onStartCommand"))
    }

    @Test
    fun playback_holdsNetworkWakeLockLikePlaylistCore() {
        assertEquals(androidx.media3.common.C.WAKE_MODE_NETWORK, MediaNotificationPolicy.WAKE_MODE)
    }

    @Test
    fun smallIcon_usesAppDrawableFromOptions() {
        assertEquals(0x7f080001, MediaNotificationPolicy.smallIconRes(0x7f080001))
    }

    @Test
    fun smallIcon_missingDrawable_fallsBackToPlatformPlayGlyph() {
        assertEquals(android.R.drawable.ic_media_play, MediaNotificationPolicy.smallIconRes(0))
    }
}
