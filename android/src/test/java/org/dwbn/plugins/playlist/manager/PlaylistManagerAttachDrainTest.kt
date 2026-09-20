package org.dwbn.plugins.playlist.manager

import androidx.media3.exoplayer.ExoPlayer
import org.dwbn.plugins.playlist.data.AudioTrack
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Regression coverage for the "attach drain" deferred from the spec-55-4 review: a `beginPlayback`
 * call that arrives before the player is attached (e.g. JS `play()`/`setPlaylistItems` racing
 * `MediaService.onCreate()`) must not be dropped — it should be replayed once `attachPlayer` runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlaylistManagerAttachDrainTest {

    private fun track(id: String): AudioTrack {
        val json = JSONObject()
        json.put("trackId", id)
        json.put("assetUrl", "https://example.com/$id.mp3")
        json.put("artist", "Artist")
        json.put("album", "Album")
        json.put("title", "Title")
        return AudioTrack(json)
    }

    @Test
    fun beginPlaybackAt_beforeAttach_isReplayedOnceThePlayerAttaches() {
        val manager = PlaylistManager(RuntimeEnvironment.getApplication())
        manager.addItem(track("a"))
        manager.addItem(track("b"))

        // No player yet — this must be stashed, not dropped or applied against a null player.
        manager.beginPlaybackAt(5_000L, false)

        val exoPlayer = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        try {
            manager.attachPlayer(exoPlayer)

            assertEquals(5_000L, exoPlayer.currentPosition)
            assertTrue(exoPlayer.playWhenReady)
        } finally {
            exoPlayer.release()
        }
    }

    @Test
    fun attachPlayer_withoutAPendingBegin_doesNotForcePlayback() {
        val manager = PlaylistManager(RuntimeEnvironment.getApplication())
        manager.addItem(track("a"))

        val exoPlayer = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        try {
            // addItem(track("a")) already triggers an initial paused beginPlayback(1, true) while
            // the player is still null (first-item autoplay-armed case) — attaching should drain
            // exactly that, not silently skip it either.
            manager.attachPlayer(exoPlayer)

            assertEquals(1L, exoPlayer.currentPosition)
            assertTrue(!exoPlayer.playWhenReady)
        } finally {
            exoPlayer.release()
        }
    }
}
