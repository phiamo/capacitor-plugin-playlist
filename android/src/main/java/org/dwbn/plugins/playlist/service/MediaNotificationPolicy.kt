package org.dwbn.plugins.playlist.service

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider

/**
 * Pure notification / focus / video-handoff decisions for Stories 55.5–55.6.
 * [MediaService], [org.dwbn.plugins.playlist.playlist.AudioPlaylistHandler] and
 * [org.dwbn.plugins.playlist.RmxAudioPlayer] call these so JVM tests can cover the I/O matrix
 * without a device.
 */
@OptIn(UnstableApi::class)
object MediaNotificationPolicy {
    const val HANDLE_AUDIO_FOCUS = true

    /**
     * [MediaService.promoteToForeground] must not call [android.app.Service.startForeground]
     * (Android 12+ forbids restarting FGS from the background after video).
     * [MediaService.onStartCommand] still must call [android.app.Service.startForeground]
     * immediately after [android.content.Context.startForegroundService].
     */
    const val ALLOW_START_FOREGROUND_ON_PROMOTE = false

    /** Show Media3's session notification before playback is ready so FGS starts within the timeout. */
    const val SHOW_NOTIFICATION_WHEN_IDLE =
        androidx.media3.session.MediaSessionService.SHOW_NOTIFICATION_FOR_IDLE_PLAYER_ALWAYS

    val mediaNotificationProviderClass: Class<out DefaultMediaNotificationProvider>
        get() = DefaultMediaNotificationProvider::class.java

    @JvmStatic
    fun shouldStartForegroundOnPromote(): Boolean = ALLOW_START_FOREGROUND_ON_PROMOTE

    @JvmStatic
    fun shouldRequestLegacyAudioFocus(): Boolean = false

    @JvmStatic
    fun shouldLoadRemoteArtwork(artworkUri: String?): Boolean = !artworkUri.isNullOrEmpty()

    @JvmStatic
    fun hasNoLargeArtwork(artworkData: ByteArray?, artworkUri: String?): Boolean =
        artworkData == null && !shouldLoadRemoteArtwork(artworkUri)

    @JvmStatic
    fun shouldSkipEndForeground(videoHandoffForegroundRetain: Boolean): Boolean =
        videoHandoffForegroundRetain

    /**
     * Pause even when buffering / [androidx.media3.common.Player.isPlaying] is false so Media3
     * [handleAudioFocus] abandons. Teaching-sequence video may call prepare while not playing.
     */
    @JvmStatic
    fun shouldPauseForVideoHandoff(isPlaying: Boolean): Boolean = true

    /** Teaching-sequence path has no prewarm — retain must start on prepare. */
    @JvmStatic
    fun shouldRetainForegroundOnPrepare(): Boolean = true

    /**
     * Media3 caps [androidx.media3.session.MediaSessionService.setForegroundServiceTimeoutMs]
     * at 10 minutes, so [MediaService.onUpdateNotification] must force
     * `startInForegroundRequired=true` while retain is set.
     */
    @JvmStatic
    fun shouldForceNotificationForeground(videoHandoffForegroundRetain: Boolean): Boolean =
        videoHandoffForegroundRetain

    @JvmStatic
    fun startInForegroundRequired(
        videoHandoffForegroundRetain: Boolean,
        media3Requested: Boolean
    ): Boolean = media3Requested || shouldForceNotificationForeground(videoHandoffForegroundRetain)

    /** MediaSession / notification play must not start audible audio while retain is set. */
    @JvmStatic
    fun shouldIgnoreSessionPlay(videoHandoffForegroundRetain: Boolean): Boolean =
        videoHandoffForegroundRetain

    /** Android audible resume stays play then seek (not seek then play). */
    @JvmStatic
    fun shouldPlayThenSeekOnResume(): Boolean = true

    /**
     * `{ resumed: true }` only for in-place audible resume while FGS is already up.
     * Prewarm, paused exit, and missing FGS all return false so JS may `playTrackById`.
     */
    @JvmStatic
    fun shouldReportInPlaceResumed(
        prewarm: Boolean,
        play: Boolean,
        retain: Boolean,
        serviceInForeground: Boolean
    ): Boolean = !prewarm && play && retain && serviceInForeground

    /** [org.dwbn.plugins.playlist.RmxAudioPlayer.getLastKnownPositionSec] is the handoff snapshot. */
    @JvmStatic
    fun lastKnownPositionSec(handoffPositionSec: Float): Float = handoffPositionSec

    @JvmStatic
    fun sessionLaunchIntentFlags(): Int =
        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
}
