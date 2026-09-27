package org.dwbn.plugins.playlist.manager

import org.dwbn.plugins.playlist.data.AudioTrack
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for [PlaylistManager.mergeReplacementTrackId], which decides whether a
 * replacement track keeps its own id or inherits the id of the track it is replacing.
 */
class PlaylistManagerReplaceTest {

    private fun track(
        trackId: String? = null,
        assetUrl: String = "https://example.com/a.mp3",
        drm: JSONObject? = null,
    ): AudioTrack {
        val json = JSONObject()
        if (drm != null) {
            json.put("drm", drm)
        }
        if (trackId != null) {
            json.put("trackId", trackId)
        }
        json.put("assetUrl", assetUrl)
        json.put("artist", "Artist")
        json.put("album", "Album")
        json.put("title", "Title")
        return AudioTrack(json)
    }

    @Test
    fun replacementWithoutTrackId_inheritsExistingTrackId() {
        val existing = track(trackId = "existing-id")
        val replacement = track(trackId = null, assetUrl = "https://example.com/local.mp3")

        val result = PlaylistManager.mergeReplacementTrackId(existing, replacement)

        assertEquals("existing-id", result.trackId)
        assertEquals("https://example.com/local.mp3", result.mediaUrl)
    }

    @Test
    fun replacementWithExplicitTrackId_isNotOverridden() {
        val existing = track(trackId = "existing-id")
        val replacement = track(trackId = "new-id")

        val result = PlaylistManager.mergeReplacementTrackId(existing, replacement)

        assertEquals("new-id", result.trackId)
    }

    @Test
    fun replacementWithoutTrackId_andNoExistingTrackId_staysNull() {
        val existing = track(trackId = null)
        val replacement = track(trackId = null)

        val result = PlaylistManager.mergeReplacementTrackId(existing, replacement)

        assertNull(result.trackId)
    }

    @Test
    fun drmReplacement_rebuildsTheMediaSource() {
        // Emulator 2026-09-27: an in-place update kept the released session; next key request -> unknown.
        val drm = JSONObject().put("widevineLicenseUrl", "https://license.example/wv").put("playbackSessionId", "ps")
        val existing = track(trackId = "t", assetUrl = "https://cdn.example/audio.m3u8", drm = drm)
        val replacement = track(trackId = "t", assetUrl = "https://cdn.example/audio.m3u8", drm = drm)

        assertTrue(PlaylistManager.rebuildSourceOnReplace(existing, replacement))
        assertTrue(PlaylistManager.rebuildSourceOnReplace(track(trackId = "t"), replacement))
    }

    @Test
    fun clearReplacement_keepsInPlaceUpdate() {
        assertFalse(PlaylistManager.rebuildSourceOnReplace(track(trackId = "t"), track(trackId = "t")))
    }
}
