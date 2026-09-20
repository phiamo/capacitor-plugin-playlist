package org.dwbn.plugins.playlist.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Coverage for [AudioTrack.startPositionMs] — the resume-position fix for skipToNext/Previous. */
class AudioTrackTest {

    private fun trackConfig(startPosition: Double? = null): JSONObject {
        val json = JSONObject()
        json.put("assetUrl", "https://example.com/a.mp3")
        json.put("artist", "Artist")
        json.put("album", "Album")
        json.put("title", "Title")
        if (startPosition != null) {
            json.put("startPosition", startPosition)
        }
        return json
    }

    @Test
    fun startPositionMs_parsesSecondsFromJsAsMilliseconds() {
        val track = AudioTrack(trackConfig(startPosition = 58.294))

        assertEquals(58294L, track.startPositionMs)
    }

    @Test
    fun startPositionMs_defaultsToZeroWhenAbsent() {
        val track = AudioTrack(trackConfig(startPosition = null))

        assertEquals(0L, track.startPositionMs)
    }

    @Test
    fun startPositionMs_canBeUpdatedAfterConstruction() {
        val track = AudioTrack(trackConfig(startPosition = 10.0))

        track.startPositionMs = 42_000L

        assertEquals(42_000L, track.startPositionMs)
    }

    @Test
    fun startPositionMs_clampsNegativeToZero() {
        val track = AudioTrack(trackConfig(startPosition = 10.0))

        track.startPositionMs = -5L

        assertEquals(0L, track.startPositionMs)
    }
}
