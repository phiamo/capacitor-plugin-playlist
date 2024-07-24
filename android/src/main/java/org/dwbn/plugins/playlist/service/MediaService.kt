package org.dwbn.plugins.playlist.service

import android.app.Notification
import android.content.pm.ServiceInfo
import android.os.Build
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

    override fun runAsForeground(notificationId: Int, notification: Notification) {
        if (inForeground) {
            return
        }

        inForeground = true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            startForeground(notificationId, notification)
        } else {
            startForeground(notificationId, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
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