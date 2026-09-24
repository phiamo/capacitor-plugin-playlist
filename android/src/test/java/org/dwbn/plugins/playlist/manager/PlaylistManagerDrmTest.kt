package org.dwbn.plugins.playlist

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.drm.DrmSessionManager
import org.dwbn.plugins.playlist.data.AudioTrack
import org.dwbn.plugins.playlist.manager.PlaylistManager
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@UnstableApi
class PlaylistManagerDrmTest {

    @After
    fun tearDown() {
        AudioDrm.setProvider(null)
    }

    private fun plain(id: String): AudioTrack {
        val json = JSONObject()
        json.put("trackId", id)
        json.put("assetUrl", "https://example.com/$id.mp3")
        json.put("artist", "Artist")
        json.put("album", "Album")
        json.put("title", "Title")
        return AudioTrack(json)
    }

    private fun drmTrack(id: String): AudioTrack {
        val json = JSONObject()
        json.put("trackId", id)
        json.put("assetUrl", "https://example.com/$id.m3u8")
        json.put("artist", "Artist")
        json.put("album", "Album")
        json.put("title", "Title")
        val drm = JSONObject()
        drm.put("widevineLicenseUrl", "https://license.example/wv")
        drm.put("playbackSessionId", "sess-1")
        json.put("drm", drm)
        return AudioTrack(json)
    }

    @Test
    fun addItem_drmWithoutProvider_rejectsAndLeavesQueueUnchanged() {
        val manager = PlaylistManager(RuntimeEnvironment.getApplication())
        manager.addItem(plain("a"))

        val failure = manager.addItem(drmTrack("b"))

        assertEquals(AudioDrm.CODE_NO_PROVIDER, failure)
        assertEquals(listOf("a"), manager.getAllItems().map { it.trackId })
    }

    @Test
    fun setAllItems_drmWithoutProvider_leavesPreviousQueueUnchanged() {
        val manager = PlaylistManager(RuntimeEnvironment.getApplication())
        manager.addItem(plain("a"))

        val failure = manager.setAllItems(
            listOf(plain("b"), drmTrack("c")),
            PlaylistItemOptions(JSONObject())
        )

        assertEquals(AudioDrm.CODE_NO_PROVIDER, failure)
        assertEquals(listOf("a"), manager.getAllItems().map { it.trackId })
    }

    @Test
    fun addAllItems_oneFailedDrmItem_failsTheWholeCall() {
        val manager = PlaylistManager(RuntimeEnvironment.getApplication())
        manager.addItem(plain("a"))

        val failure = manager.addAllItems(listOf(plain("b"), drmTrack("c")))

        assertEquals(AudioDrm.CODE_NO_PROVIDER, failure)
        assertEquals(listOf("a"), manager.getAllItems().map { it.trackId })
    }

    @Test
    fun replaceItem_drmWithoutProvider_leavesExistingTrack() {
        val manager = PlaylistManager(RuntimeEnvironment.getApplication())
        manager.addItem(plain("a"))

        val (replaced, failure) = manager.replaceItem(0, "a", drmTrack("a"))

        assertEquals(AudioDrm.CODE_NO_PROVIDER, failure)
        assertNull(replaced)
        assertEquals("https://example.com/a.mp3", manager.getAllItems()[0].mediaUrl)
        assertNull(manager.getAllItems()[0].drm)
    }

    @Test
    fun addItem_plaintext_stillSucceedsWithoutProvider() {
        val manager = PlaylistManager(RuntimeEnvironment.getApplication())

        val failure = manager.addItem(plain("a"))

        assertNull(failure)
        assertEquals(1, manager.getAllItems().size)
    }

    @Test
    fun addItem_drmWithProvider_isAccepted() {
        AudioDrm.setProvider { _, _ -> FakeSession() }
        val manager = PlaylistManager(RuntimeEnvironment.getApplication())

        val failure = manager.addItem(drmTrack("a"))

        assertNull(failure)
        assertEquals("a", manager.getAllItems()[0].trackId)
        assertEquals("sess-1", manager.getAllItems()[0].drm!!.getString("playbackSessionId"))
    }

    @Test
    fun mixedQueue_plainAndDrm_succeedsWithProvider() {
        AudioDrm.setProvider { _, _ -> FakeSession() }
        val manager = PlaylistManager(RuntimeEnvironment.getApplication())

        assertNull(manager.addItem(plain("a")))
        assertNull(manager.addItem(drmTrack("b")))

        assertEquals(listOf("a", "b"), manager.getAllItems().map { it.trackId })
        assertNull(manager.getAllItems()[0].drm)
        assertEquals("https://license.example/wv", manager.getAllItems()[1].drm!!.getString("widevineLicenseUrl"))
    }

    private class FakeSession : AudioDrmSession {
        override fun applyDrm(builder: MediaItem.Builder) {
            builder.setDrmConfiguration(MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID).build())
        }

        override fun getDrmSessionManager(): DrmSessionManager = DrmSessionManager.DRM_UNSUPPORTED

        override fun start() {}

        override fun release() {}
    }
}
