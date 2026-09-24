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

    @Test
    fun startPositionMs_clampsNegativeJsValueAtConstruction() {
        val track = AudioTrack(trackConfig(startPosition = -12.0))

        assertEquals(0L, track.startPositionMs)
    }

    @Test
    fun toDict_includesStartPositionInSeconds() {
        val track = AudioTrack(trackConfig(startPosition = 58.294))

        assertEquals(58.294, track.toDict().getDouble("startPosition"), 0.001)
    }

    @Test
    fun drm_isNullWhenOmitted() {
        val track = AudioTrack(trackConfig())

        assertEquals(null, track.drm)
        assertEquals(false, track.toDict().has("drm"))
    }

    @Test
    fun drm_isParsedAndEchoedInToDict() {
        val json = trackConfig()
        val drm = JSONObject()
        drm.put("widevineLicenseUrl", "https://license.example/wv")
        drm.put("playbackSessionId", "sess-1")
        drm.put("renewalCredential", "cred")
        val streamLimit = JSONObject()
        streamLimit.put("mode", "axinom_csl")
        streamLimit.put("renewalIntervalSeconds", 300)
        drm.put("streamLimit", streamLimit)
        drm.put("fairplayLicenseUrl", "https://license.example/fp")
        json.put("drm", drm)

        val track = AudioTrack(json)
        val echoed = track.toDict().getJSONObject("drm")

        assertEquals("https://license.example/wv", track.drm!!.getString("widevineLicenseUrl"))
        assertEquals("sess-1", echoed.getString("playbackSessionId"))
        assertEquals("axinom_csl", echoed.getJSONObject("streamLimit").getString("mode"))
        assertEquals("https://license.example/fp", echoed.getString("fairplayLicenseUrl"))
    }
}
