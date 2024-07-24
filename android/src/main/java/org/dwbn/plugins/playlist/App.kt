package org.dwbn.plugins.playlist

import android.app.Application
import org.dwbn.plugins.playlist.manager.PlaylistManager

class App : Application() {
    private lateinit var _playlistManager: PlaylistManager;
    val playlistManager get() = _playlistManager

    fun resetPlaylistManager() {
        _playlistManager = PlaylistManager(this)
    }

    override fun onCreate() {
        resetPlaylistManager()
        super.onCreate()

    }
}
