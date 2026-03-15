package org.dwbn.plugins.playlist.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import com.devbrackets.android.playlistcore.components.playlisthandler.PlaylistHandler
import com.devbrackets.android.playlistcore.service.BasePlaylistService
import org.dwbn.plugins.playlist.App
import org.dwbn.plugins.playlist.data.AudioTrack
import org.dwbn.plugins.playlist.manager.PlaylistManager
import org.dwbn.plugins.playlist.playlist.AudioApi
import org.dwbn.plugins.playlist.playlist.AudioPlaylistHandler
import org.dwbn.plugins.playlist.service.MediaImageProvider.OnImageUpdatedListener

/**
 * A simple service that extends [BasePlaylistService] in order to provide
 * the application specific information required.
 */
class MediaService : BasePlaylistService<AudioTrack, PlaylistManager>() {
    companion object {
        private const val TAG = "MediaService"
    }

    override fun onCreate() {
        super.onCreate()
        // Adds the audio player implementation, otherwise there's nothing to play media with
        val newAudio = AudioApi(applicationContext)
        newAudio.addErrorListener(playlistManager)
        playlistManager.mediaPlayers.add(newAudio)
    }

    override fun onDestroy() {
        super.onDestroy()

        // Releases and clears all the MediaPlayersMediaImageProvider
        for (player in playlistManager.mediaPlayers) {
            player.release()
        }
        playlistManager.mediaPlayers.clear()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            super.onStartCommand(intent, flags, startId)
        } catch (e: android.app.ForegroundServiceStartNotAllowedException) {
            // Android 12+ blocks foreground service start from background
            // Log the error but don't crash - service can still function without foreground status
            Log.w(TAG, "Cannot start foreground service: app is in background", e)
            // Return START_NOT_STICKY so service won't be restarted if killed
            // The service will continue as a regular service (may be killed by system)
            Service.START_NOT_STICKY
        }
    }

    override fun runAsForeground(notificationId: Int, notification: Notification) {
        if (inForeground) {
            return
        }

        inForeground = true
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                startForeground(notificationId, notification)
            } else {
                startForeground(notificationId, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            }
        } catch (e: android.app.ForegroundServiceStartNotAllowedException) {
            // Android 12+ blocks foreground service start from background
            // Log the error but don't crash - service can still function without foreground status
            Log.w(TAG, "Cannot start foreground service: app is in background", e)
            inForeground = false
            // Service will continue as a regular service (may be killed by system)
        }
    }

    override val playlistManager: PlaylistManager
        get() = (applicationContext as App).playlistManager

    override fun newPlaylistHandler(): PlaylistHandler<AudioTrack> {
        val imageProvider = MediaImageProvider(applicationContext, object : OnImageUpdatedListener {
            override fun onImageUpdated() {
                playlistHandler.updateMediaControls()
            }
        }, playlistManager.options)

        return AudioPlaylistHandler.Builder(
            applicationContext,
            javaClass,
            playlistManager,
            imageProvider,
            null
        ).build()
    }
}