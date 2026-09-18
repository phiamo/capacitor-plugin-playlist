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
import org.dwbn.plugins.playlist.PlaylistRuntime
import org.dwbn.plugins.playlist.manager.PlaylistManager

/**
 * Media3 [MediaSessionService] hosting one [ExoPlayer] and one [MediaSession] for audio playlists.
 * Foreground notification is owned by [DefaultMediaNotificationProvider].
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

        val sessionBuilder = MediaSession.Builder(this, player)
            .setBitmapLoader(GlideBitmapLoader(this))
        sessionActivityPendingIntent()?.let { sessionBuilder.setSessionActivity(it) }
        val session = sessionBuilder.build()
        mediaSession = session

        playlistManager.attachPlayer(player)
        playlistManager.attachService(this)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    @SuppressLint("NewApi")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            val result = super.onStartCommand(intent, flags, startId)
            // startForegroundService requires startForeground before Android's timeout.
            // Media3 may wait until STATE_READY; HLS buffering can exceed that window.
            startForegroundImmediately()
            result
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
        // Media3 updates the session notification from MediaItem.MediaMetadata.
    }

    private fun startForegroundImmediately() {
        val notification = buildImmediateNotification()
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

    private fun buildImmediateNotification(): Notification {
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
        return builder.build()
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
