package org.dwbn.plugins.playlist.manager

enum class RmxPlaybackState {
    STOPPED,
    RETRIEVING,
    PREPARING,
    SEEKING,
    PLAYING,
    PAUSED,
    /** Claims PLAYING but currentPosition hasn't advanced for [PlaylistPlaybackPolicy.STALL_THRESHOLD_MS]. */
    STALLED,
    ERROR
}
