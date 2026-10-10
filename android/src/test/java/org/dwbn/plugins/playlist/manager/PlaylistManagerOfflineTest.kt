package org.dwbn.plugins.playlist

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManager
import com.getcapacitor.JSObject
import org.dwbn.plugins.playlist.data.AudioTrack
import org.dwbn.plugins.playlist.manager.PlaylistManager
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@UnstableApi
class PlaylistManagerOfflineTest {

    @After
    fun tearDown() {
        AudioOffline.setProvider(null)
        AudioDrm.setProvider(null)
    }

    private fun offlineTrack(id: String, downloadId: String = "d-$id"): AudioTrack {
        val json = JSONObject()
        json.put("trackId", id)
        json.put("assetUrl", "https://example.com/$id.mp3")
        json.put("title", "Title")
        json.put("downloadId", downloadId)
        return AudioTrack(json)
    }

    private fun streamingTrack(id: String): AudioTrack {
        val json = JSONObject()
        json.put("trackId", id)
        json.put("assetUrl", "https://example.com/$id.mp3")
        return AudioTrack(json)
    }

    private fun manager(): PlaylistManager = PlaylistManager(RuntimeEnvironment.getApplication()).also {
        it.offlineUriResolver = { id -> "https://cdn.example/$id/audio.m3u8?sig=1" }
    }

    @Test
    fun addItem_downloadWithoutProvider_rejectsNoProvider() {
        val manager = manager()
        manager.addItem(streamingTrack("a"))

        val failure = manager.addItem(offlineTrack("b"))

        assertEquals(AudioDrm.CODE_NO_PROVIDER, failure)
        assertEquals(listOf("a"), manager.getAllItems().map { it.trackId })
    }

    @Test
    fun addItem_providerReturnsNoSession_rejectsNoProvider() {
        AudioOffline.setProvider(FakeOfflineProvider(session = null))
        assertEquals(AudioDrm.CODE_NO_PROVIDER, manager().addItem(offlineTrack("a")))
    }

    @Test
    fun attachPlayer_offlineItem_usesRequestUriHlsMimeAndOfflineSession() {
        val session = RecordingSession()
        val provider = FakeOfflineProvider(session)
        AudioOffline.setProvider(provider)
        val manager = manager()
        assertNull(manager.addItem(offlineTrack("a")))
        val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        try {
            manager.attachPlayer(player)

            val item = player.currentMediaItem!!
            assertEquals("https://cdn.example/d-a/audio.m3u8?sig=1", item.localConfiguration?.uri.toString())
            assertEquals(MimeTypes.APPLICATION_M3U8, item.localConfiguration?.mimeType)
            assertEquals(listOf("d-a"), provider.opened)
            assertNotNull(manager.drmSessionManagerFor(item))
            assertTrue(session.startCount >= 1)
        } finally {
            manager.detachPlayer()
            player.release()
        }
        assertEquals(1, session.releaseCount)
    }

    @Test
    fun streamingItems_areUnchangedByOfflineSupport() {
        val provider = FakeOfflineProvider(RecordingSession())
        AudioOffline.setProvider(provider)
        val manager = manager()
        assertNull(manager.addItem(streamingTrack("a")))
        val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        try {
            manager.attachPlayer(player)
            assertEquals("https://example.com/a.mp3", player.currentMediaItem?.localConfiguration?.uri.toString())
            assertTrue(provider.opened.isEmpty())
        } finally {
            manager.detachPlayer()
            player.release()
        }
    }

    @Test
    fun expiredLicence_emitsExpiredAndDoesNotPlay() {
        val provider = FakeOfflineProvider(RecordingSession(), AudioOfflineProvider.STATE_EXPIRED)
        AudioOffline.setProvider(provider)
        val manager = manager()
        val errors = mutableListOf<Pair<String?, String>>()
        manager.drmErrorListener = { trackId, error -> errors.add(trackId to error) }
        assertNull(manager.addItem(offlineTrack("a")))
        val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        try {
            manager.attachPlayer(player)
            manager.beginPlayback(0, false)
            Shadows.shadowOf(player.applicationLooper).idle()

            assertTrue(errors.contains("a" to AudioDrm.ERROR_EXPIRED))
            assertTrue(provider.opened.isEmpty())
            assertFalse(player.playWhenReady)
            assertTrue(manager.drmPlaybackHalted)
        } finally {
            manager.detachPlayer()
            player.release()
        }
    }

    @Test
    fun toDict_roundTripsDownloadId_andReplaceKeepsIt() {
        val track = offlineTrack("a", "dl-1")
        assertEquals("dl-1", track.toDict().getString("downloadId"))
        assertTrue(track.downloaded)
        assertFalse(streamingTrack("a").downloaded)
        val nullId = JSONObject().put("trackId", "n").put("assetUrl", "https://example.com/n.mp3")
            .put("downloadId", JSONObject.NULL)
        assertNull(AudioTrack(nullId).downloadId)
        assertFalse(AudioTrack(nullId).downloaded)
        assertNull(streamingTrack("a").toDict().optString("downloadId", "").takeIf { it.isNotEmpty() })

        val merged = PlaylistManager.mergeReplacementTrackId(streamingTrack("a"), track)
        assertEquals("dl-1", merged.downloadId)
        assertTrue(PlaylistManager.rebuildSourceOnReplace(streamingTrack("a"), track))
        assertFalse(PlaylistManager.rebuildSourceOnReplace(streamingTrack("a"), streamingTrack("a")))
    }

    private class FakeOfflineProvider(
        private val session: AudioDrmSession?,
        private val stateValue: String = AudioOfflineProvider.STATE_ACTIVE
    ) : AudioOfflineProvider {
        val opened = mutableListOf<String>()

        override fun acquire(downloadId: String, format: Format, drm: JSObject): String? = null

        override fun needsRenewal(downloadId: String): Boolean = false

        override fun expiresAt(downloadId: String): Long? = null

        override fun renew(downloadId: String, drm: JSObject?): String? = null

        override fun release(downloadId: String) {}

        override fun state(downloadId: String): String = stateValue

        override fun openOffline(downloadId: String): AudioDrmSession? {
            opened.add(downloadId)
            return session
        }
    }

    private class RecordingSession : AudioDrmSession {
        var startCount = 0
        var releaseCount = 0

        override fun applyDrm(builder: MediaItem.Builder) {
            builder.setDrmConfiguration(MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID).build())
        }

        override fun getDrmSessionManager(): DrmSessionManager = object : DrmSessionManager by DrmSessionManager.DRM_UNSUPPORTED {}

        override fun start() {
            startCount++
        }

        override fun release() {
            releaseCount++
        }
    }
}
