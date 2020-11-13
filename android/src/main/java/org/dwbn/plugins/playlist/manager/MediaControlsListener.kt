package org.dwbn.plugins.playlist.manager

import org.dwbn.plugins.playlist.data.AudioTrack

/*
* Interface to enable the PlaylistManager to send these events out.
* We could add more like play/pause/toggle/stop, but right now there
* are other ways to get all the other information.
*/
interface MediaControlsListener {
    fun onNext(currentItem: AudioTrack?, currentIndex: Int)
    fun onPrevious(currentItem: AudioTrack?, currentIndex: Int)
}