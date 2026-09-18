package org.dwbn.plugins.playlist.manager

data class PlaybackProgress(
    val position: Long,
    val duration: Long,
    val bufferPercent: Int,
    val bufferPercentFloat: Float
)
