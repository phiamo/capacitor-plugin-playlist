package org.dwbn.plugins.playlist.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.dwbn.plugins.playlist.PlaylistRuntime
import org.dwbn.plugins.playlist.manager.PlaylistManager

/**
 * Media3 [MediaSessionService] hosting one [ExoPlayer] and one [MediaSession] for audio playlists.
 * Foreground notification is owned by [DefaultMediaNotificationProvider] after the session exists.
 */
@OptIn(UnstableApi::class)
class MediaService : MediaSessionService() {
    companion object {
        private const val TAG = "MediaService"

        @JvmStatic
        @Volatile
        var instance: MediaService? = null
    }

    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var inForeground = false

    private val playlistManager: PlaylistManager
        get() = PlaylistRuntime.getPlaylistManager(applicationContext)

    override fun onCreate() {
        super.onCreate()
        instance = this
        // startForegroundService timeout starts before onCreate; player/session setup can exceed it.
        startForegroundImmediately()
        setListener(foregroundStartListener)
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
        notificationProvider.setSmallIcon(android.R.drawable.ic_media_play)
        setMediaNotificationProvider(notificationProvider)
        setShowNotificationForIdlePlayer(MediaNotificationPolicy.SHOW_NOTIFICATION_WHEN_IDLE)

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                MediaNotificationPolicy.HANDLE_AUDIO_FOCUS
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        exoPlayer = player
        // Load items before the session is built so the notification controller sees a timeline.
        playlistManager.attachPlayer(player)

        val forwardingPlayer = HandoffForwardingPlayer(
            player,
            retain = { playlistManager.videoHandoffForegroundRetain },
            onSkipToNext = { playlistManager.skipToNext() },
            onSkipToPrevious = { playlistManager.skipToPrevious() },
            previousAvailable = { playlistManager.isPreviousAvailable },
            nextAvailable = { playlistManager.isNextAvailable }
        )
        val sessionBuilder = MediaSession.Builder(this, forwardingPlayer)
            .setId(MediaNotificationPolicy.MEDIA_SESSION_ID)
            .setBitmapLoader(GlideBitmapLoader(this))
            .setCallback(
                PlaylistMediaSessionCallback {
                    PlaylistMediaSessionCallback.SkipAvailability(
                        playlistManager.isPreviousAvailable,
                        playlistManager.isNextAvailable
                    )
                }
            )
        sessionActivityPendingIntent()?.let { sessionBuilder.setSessionActivity(it) }
        val session = sessionBuilder.build()
        mediaSession = session
        playlistManager.attachService(this)

        startForegroundWithMedia3Notification(session)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    @SuppressLint("NewApi")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            super.onStartCommand(intent, flags, startId)
        } catch (e: IllegalStateException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is android.app.ForegroundServiceStartNotAllowedException
            ) {
                Log.w(TAG, "Cannot start foreground service: app is in background", e)
                inForeground = false
                playlistManager.mediaServiceInForeground = false
                START_NOT_STICKY
            } else {
                throw e
            }
        }
    }

    override fun onDestroy() {
        clearListener()
        playlistManager.detachPlayer()
        mediaSession?.release()
        mediaSession = null
        exoPlayer?.release()
        exoPlayer = null
        instance = null
        super.onDestroy()
    }

    fun isRunningInForeground(): Boolean = inForeground

    /**
     * Media3 owns FGS via [DefaultMediaNotificationProvider]. Do not call [startForeground] here:
     * Android 12+ forbids restarting a foreground service from the background after video.
     */
    fun promoteToForeground() {
        if (!MediaNotificationPolicy.shouldStartForegroundOnPromote()) {
            return
        }
    }

    /**
     * Media3 1.11.1 posts the MediaStyle notification from [onUpdateNotificationAsync].
     * Overriding the 2-arg [onUpdateNotification] and calling it from onCreate does not.
     */
    override fun onUpdateNotificationAsync(
        session: MediaSession,
        startInForegroundRequired: Boolean
    ): ListenableFuture<Void?> {
        val required = MediaNotificationPolicy.startInForegroundRequired(
            playlistManager.videoHandoffForegroundRetain,
            startInForegroundRequired
        )
        return try {
            val result = super.onUpdateNotificationAsync(session, required)
            inForeground = required
            playlistManager.mediaServiceInForeground = required
            result
        } catch (e: IllegalStateException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is android.app.ForegroundServiceStartNotAllowedException
            ) {
                Log.w(TAG, "Cannot start foreground service: app is in background", e)
                inForeground = false
                playlistManager.mediaServiceInForeground = false
                Futures.immediateFuture(null)
            } else {
                throw e
            }
        }
    }

    fun endForeground(removeNotification: Boolean) {
        if (MediaNotificationPolicy.shouldSkipEndForeground(playlistManager.videoHandoffForegroundRetain)) {
            return
        }
        inForeground = false
        playlistManager.mediaServiceInForeground = false
        if (removeNotification) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(STOP_FOREGROUND_DETACH)
        }
    }

    fun updateForegroundNotification() {
        // Media3 updates the session notification from Player / MediaItem.MediaMetadata.
    }

    /** FGS within startForegroundService timeout; replaced by MediaStyle once the session exists. */
    private fun startForegroundImmediately() {
        startForegroundWith(buildImmediateNotification())
    }

    private fun buildImmediateNotification(): Notification {
        return mediaNotificationBuilder().build()
    }

    /**
     * Attach the Media3 session token so System UI shows play/pause/skip (API 33+ compact slots).
     * Do not call [onUpdateNotification] here: Media3 1.11.1 only posts via [onUpdateNotificationAsync].
     */
    private fun startForegroundWithMedia3Notification(session: MediaSession) {
        val notification = mediaNotificationBuilder()
            .setStyle(MediaStyleNotificationHelper.MediaStyle(session))
            .build()
        startForegroundWith(notification)
    }

    private fun mediaNotificationBuilder(): NotificationCompat.Builder {
        val channelId = DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Audio playback",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val title = playlistManager.currentItem?.title?.takeIf { it.isNotEmpty() } ?: "Audio playback"
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        sessionActivityPendingIntent()?.let { builder.setContentIntent(it) }
        return builder
    }

    private fun startForegroundWith(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID, notification)
        }
        inForeground = true
        playlistManager.mediaServiceInForeground = true
    }

    private fun sessionActivityPendingIntent(): PendingIntent? {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        launchIntent.addFlags(MediaNotificationPolicy.sessionLaunchIntentFlags())
        return PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private val foregroundStartListener = object : Listener {
        @RequiresApi(Build.VERSION_CODES.S)
        override fun onForegroundServiceStartNotAllowedException() {
            Log.w(TAG, "Cannot start foreground service: app is in background")
            inForeground = false
            playlistManager.mediaServiceInForeground = false
        }
    }

}
