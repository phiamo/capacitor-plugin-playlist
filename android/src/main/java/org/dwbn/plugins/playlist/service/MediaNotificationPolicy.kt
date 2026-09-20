package org.dwbn.plugins.playlist.service

import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
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
     * Media3's own stuck-player detector (androidx.media3:media3-exoplayer 1.9.0+) reports a
     * `StuckPlayerException` through the existing `onPlayerError` path we already forward as
     * `RMXSTATUS_ERROR` — no custom polling needed for the "player has definitively given up"
     * case. Its `STATE_READY`-with-no-progress check is opt-in (`ExoPlayer.Builder`'s static
     * `experimentalEnableStuckPlayingDetection`, default false); its `STATE_BUFFERING` check is
     * always on but defaults to a 10-minute timeout, far too slow to be useful — tightened here.
     * [RmxAudioPlayer]'s own position-freeze check (`RMXSTATUS_STALLED`, ~10s) stays as a faster,
     * softer "still trying" signal layered underneath this "give up" backstop.
     */
    const val STUCK_BUFFERING_DETECTION_TIMEOUT_MS = 60_000

    /** FGS start blocked by notification permission or related security policy. */
    @JvmStatic
    fun isForegroundStartSecurityBlocked(error: Throwable): Boolean = error is SecurityException

    @JvmStatic
    fun isForegroundStartNotAllowedFromBackground(error: Throwable): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            error is android.app.ForegroundServiceStartNotAllowedException

    /** Distinct from the video plugin session (`org.dwbn.video`); Media3 forbids two empty IDs. */
    const val MEDIA_SESSION_ID = "org.dwbn.playlist"

    /**
     * [MediaService.promoteToForeground] must not call [android.app.Service.startForeground]
     * (Android 12+ forbids restarting FGS from the background after video).
     * FGS starts at the beginning of [MediaService.onCreate] so the startForegroundService
     * timeout cannot fire while ExoPlayer / MediaSession are built. Media3 then replaces that
     * notification; [MediaService.onStartCommand] must not post the title-only placeholder again.
     */
    const val ALLOW_START_FOREGROUND_ON_PROMOTE = false

    /** Title-only FGS placeholder is only allowed before the session exists (onCreate start). */
    const val ALLOW_TITLE_ONLY_NOTIFICATION_AFTER_SESSION = false

    /** Show Media3's session notification before playback is ready so FGS starts within the timeout. */
    const val SHOW_NOTIFICATION_WHEN_IDLE =
        androidx.media3.session.MediaSessionService.SHOW_NOTIFICATION_FOR_IDLE_PLAYER_ALWAYS

    val mediaNotificationProviderClass: Class<out DefaultMediaNotificationProvider>
        get() = DefaultMediaNotificationProvider::class.java

    @JvmStatic
    fun shouldStartForegroundOnPromote(): Boolean = ALLOW_START_FOREGROUND_ON_PROMOTE

    /** A second [android.content.Context.startForegroundService] while onCreate is still running trips the FGS timeout. */
    @JvmStatic
    fun shouldStartForegroundService(serviceAlreadyCreated: Boolean): Boolean = !serviceAlreadyCreated

    @JvmStatic
    fun shouldRequestLegacyAudioFocus(): Boolean = false

    @JvmStatic
    fun shouldLoadRemoteArtwork(artworkUri: String?): Boolean = !artworkUri.isNullOrEmpty()

    @JvmStatic
    fun hasNoLargeArtwork(artworkData: ByteArray?, artworkUri: String?): Boolean =
        (artworkData == null || artworkData.isEmpty()) && !shouldLoadRemoteArtwork(artworkUri)

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
     * at 10 minutes, so [MediaService.onUpdateNotificationAsync] must force
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

    /** MediaSession / notification play must not start audible audio while retain is set and video is absent. */
    @JvmStatic
    fun shouldIgnoreSessionPlay(videoHandoffForegroundRetain: Boolean): Boolean =
        shouldIgnoreSessionPlay(videoHandoffForegroundRetain, videoHandoffPlayerAttached = false)

    @JvmStatic
    fun shouldIgnoreSessionPlay(
        videoHandoffForegroundRetain: Boolean,
        videoHandoffPlayerAttached: Boolean
    ): Boolean = videoHandoffForegroundRetain && !videoHandoffPlayerAttached

    @JvmStatic
    fun shouldDelegateNotificationToVideo(
        videoHandoffForegroundRetain: Boolean,
        videoHandoffPlayerAttached: Boolean
    ): Boolean = videoHandoffForegroundRetain && videoHandoffPlayerAttached

    @JvmStatic
    fun mediaNotificationSkipPreviousEnabled(
        previousAvailable: Boolean,
        videoHandoffForegroundRetain: Boolean,
        videoHandoffPlayerAttached: Boolean
    ): Boolean {
        if (shouldDelegateNotificationToVideo(videoHandoffForegroundRetain, videoHandoffPlayerAttached)) {
            return false
        }
        return mediaNotificationSkipPreviousEnabled(previousAvailable)
    }

    @JvmStatic
    fun mediaNotificationSkipNextEnabled(
        nextAvailable: Boolean,
        videoHandoffForegroundRetain: Boolean,
        videoHandoffPlayerAttached: Boolean
    ): Boolean {
        if (shouldDelegateNotificationToVideo(videoHandoffForegroundRetain, videoHandoffPlayerAttached)) {
            return false
        }
        return mediaNotificationSkipNextEnabled(nextAvailable)
    }

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

    /**
     * Failed in-place resume must not last-resort [org.dwbn.plugins.playlist.manager.PlaylistManager.beginPlayback]
     * (Android 12+ forbids starting FGS from the background after video). JS may play/seek.
     */
    @JvmStatic
    fun shouldBeginPlaybackWhenInPlaceUnavailable(): Boolean = false

    /** Teaching-sequence retain starts on prepare — [org.dwbn.plugins.playlist.RmxAudioPlayer] must call this. */
    @JvmStatic
    fun applyForegroundRetainOnPrepare(setRetain: Runnable) {
        if (shouldRetainForegroundOnPrepare()) {
            setRetain.run()
        }
    }

    /** Miss-path audible resume must not [org.dwbn.plugins.playlist.manager.PlaylistManager.beginPlayback]. */
    @JvmStatic
    fun applyBeginPlaybackWhenInPlaceUnavailable(beginPlayback: Runnable) {
        if (shouldBeginPlaybackWhenInPlaceUnavailable()) {
            beginPlayback.run()
        }
    }

    /**
     * [org.dwbn.plugins.playlist.manager.PlaylistManager] player listener must call this when
     * [androidx.media3.common.Player.Listener.onPlayWhenReadyChanged] fires.
     */
    @JvmStatic
    fun applyRetainPlayWhenReadyGuard(
        playWhenReady: Boolean,
        videoHandoffForegroundRetain: Boolean,
        videoHandoffPlayerAttached: Boolean,
        pausePlayer: Runnable
    ) {
        if (playWhenReady &&
            shouldIgnoreSessionPlay(videoHandoffForegroundRetain, videoHandoffPlayerAttached)
        ) {
            pausePlayer.run()
        }
    }

    @JvmStatic
    fun sessionLaunchIntentFlags(): Int =
        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP

    @JvmStatic
    fun shouldOverwriteMedia3NotificationOnStartCommand(): Boolean = false

    @JvmStatic
    fun shouldAllowTitleOnlyNotificationAfterSession(): Boolean =
        ALLOW_TITLE_ONLY_NOTIFICATION_AFTER_SESSION

    /** CPU + Wi-Fi lock while playing, as PlaylistCore's WifiLock and ExoMedia's partial wake lock did. */
    const val WAKE_MODE = C.WAKE_MODE_NETWORK

    /** `setOptions({ options: { icon } })` names an app drawable; fall back to the platform play glyph. */
    @JvmStatic
    fun smallIconRes(resolvedIconRes: Int): Int =
        if (resolvedIconRes != 0) resolvedIconRes else android.R.drawable.ic_media_play

    /** Mirrors [org.dwbn.plugins.playlist.manager.PlaylistManager.isPreviousAvailable] for JVM tests. */
    @JvmStatic
    fun mediaNotificationSkipPreviousEnabled(previousAvailable: Boolean): Boolean = previousAvailable

    /** Mirrors [org.dwbn.plugins.playlist.manager.PlaylistManager.isNextAvailable] for JVM tests. */
    @JvmStatic
    fun mediaNotificationSkipNextEnabled(nextAvailable: Boolean): Boolean = nextAvailable

    /**
     * Starts from Media3 default / wrapped player commands so metadata and play-pause stay
     * available. Skip is added or removed from that set — never replace it with a tiny list.
     */
    @JvmStatic
    fun playerCommandsForMediaNotification(
        base: Player.Commands,
        previousAvailable: Boolean,
        nextAvailable: Boolean
    ): Player.Commands {
        val builder = base.buildUpon().add(Player.COMMAND_PLAY_PAUSE)
        if (mediaNotificationSkipPreviousEnabled(previousAvailable)) {
            builder.add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        } else {
            builder.remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            builder.remove(Player.COMMAND_SEEK_TO_PREVIOUS)
        }
        if (mediaNotificationSkipNextEnabled(nextAvailable)) {
            builder.add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        } else {
            builder.remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            builder.remove(Player.COMMAND_SEEK_TO_NEXT)
        }
        return builder.build()
    }
}
