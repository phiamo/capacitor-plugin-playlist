package org.dwbn.plugins.playlist.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.dwbn.plugins.playlist.PlaylistRuntime
import org.dwbn.plugins.playlist.manager.PlaylistManager

/**
 * Media3 [MediaSessionService] hosting one [ExoPlayer] and one [MediaSession] for audio playlists.
 */
@OptIn(UnstableApi::class)
class MediaService : MediaSessionService() {
    companion object {
        private const val TAG = "MediaService"
        private const val NOTIFICATION_ID = 0x504C4159 // "PLAY"
        private const val CHANNEL_ID = "org.dwbn.plugins.playlist.media"

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
        createNotificationChannel()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        exoPlayer = player

        val session = MediaSession.Builder(this, player).build()
        mediaSession = session

        playlistManager.attachPlayer(player)
        playlistManager.attachService(this)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            promoteToForeground()
            START_STICKY
        } catch (e: android.app.ForegroundServiceStartNotAllowedException) {
            Log.w(TAG, "Cannot start foreground service: app is in background", e)
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        playlistManager.detachPlayer()
        mediaSession?.release()
        mediaSession = null
        exoPlayer?.release()
        exoPlayer = null
        instance = null
        super.onDestroy()
    }

    fun isRunningInForeground(): Boolean = inForeground

    fun promoteToForeground() {
        if (inForeground) {
            playlistManager.mediaServiceInForeground = true
            return
        }
        inForeground = true
        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                startForeground(NOTIFICATION_ID, notification)
            } else {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            }
            playlistManager.mediaServiceInForeground = true
        } catch (e: android.app.ForegroundServiceStartNotAllowedException) {
            Log.w(TAG, "Cannot start foreground service: app is in background", e)
            inForeground = false
            playlistManager.mediaServiceInForeground = false
        }
    }

    fun endForeground(removeNotification: Boolean) {
        if (playlistManager.videoHandoffForegroundRetain) {
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
        if (!inForeground) {
            return
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Audio playback",
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = launchIntent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val track = playlistManager.currentItem
        val title = track?.title?.takeIf { it.isNotEmpty() } ?: "Audio playback"
        val artist = track?.artist?.takeIf { it.isNotEmpty() }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (artist != null) {
            builder.setContentText(artist)
        }
        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }
        return builder.build()
    }
}
