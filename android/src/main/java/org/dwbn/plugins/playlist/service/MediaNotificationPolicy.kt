package org.dwbn.plugins.playlist.service

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider

/**
 * Pure notification / focus decisions for Story 55.5. [MediaService] and [GlideBitmapLoader]
 * call these so JVM tests can cover the I/O matrix without a device.
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

    @JvmStatic
    fun sessionLaunchIntentFlags(): Int =
        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
}
