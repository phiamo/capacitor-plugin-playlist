package org.dwbn.plugins.playlist

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManager
import org.dwbn.plugins.playlist.data.AudioTrack
import org.dwbn.plugins.playlist.manager.PlaylistManager
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.function.Consumer

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

    @Test
    fun addItem_throwingProvider_rejectsAndLeavesQueueUnchanged() {
        AudioDrm.setProvider { _, _ -> throw IllegalStateException("open failed") }
        val manager = PlaylistManager(RuntimeEnvironment.getApplication())
        manager.addItem(plain("a"))

        val failure = manager.addItem(drmTrack("b"))

        assertEquals(AudioDrm.CODE_NO_PROVIDER, failure)
        assertEquals(listOf("a"), manager.getAllItems().map { it.trackId })
    }

    @Test
    fun attachPlayer_startsCurrentSession_pauseDoesNotRelease() {
        val session = RecordingSession()
        AudioDrm.setProvider { _, _ -> session }
        val manager = PlaylistManager(RuntimeEnvironment.getApplication())
        assertNull(manager.addItem(drmTrack("a")))
        val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        try {
            manager.attachPlayer(player)
            assertTrue(session.startCount >= 1)
            assertEquals(0, session.releaseCount)
            val mediaItem = player.currentMediaItem
            assertNotNull(mediaItem)
            assertNotNull(manager.drmSessionManagerFor(mediaItem!!))

            player.pause()
            assertTrue(session.startCount >= 1)
            assertEquals(0, session.releaseCount)
        } finally {
            manager.detachPlayer()
            player.release()
        }
        assertEquals(1, session.releaseCount)
    }

    @Test
    fun clearItems_releasesSession_lookupReturnsNull() {
        val session = RecordingSession()
        AudioDrm.setProvider { _, _ -> session }
        val manager = PlaylistManager(RuntimeEnvironment.getApplication())
        assertNull(manager.addItem(drmTrack("a")))
        val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        try {
            manager.attachPlayer(player)
            val mediaItem = player.currentMediaItem
            assertNotNull(manager.drmSessionManagerFor(mediaItem!!))
            manager.clearItems()
            assertNull(manager.drmSessionManagerFor(mediaItem))
            assertEquals(1, session.releaseCount)
        } finally {
            manager.detachPlayer()
            player.release()
        }
    }

    @Test
    fun skipToNext_releasesPreviousAndStartsNext() {
        val sessions = mutableListOf<RecordingSession>()
        AudioDrm.setProvider { _, _ -> RecordingSession().also { sessions.add(it) } }
        val manager = PlaylistManager(RuntimeEnvironment.getApplication())
        assertNull(manager.addItem(drmTrack("a")))
        assertNull(manager.addItem(drmTrack("b")))
        val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        try {
            manager.attachPlayer(player)
            assertTrue(sessions[0].startCount >= 1)
            assertEquals(0, sessions[0].releaseCount)
            manager.skipToNext()
            assertEquals(1, sessions[0].releaseCount)
            assertTrue(sessions[1].startCount >= 1)
            assertNull(manager.drmSessionManagerFor(AudioMediaItemFactory.fromAudioTrack(drmTrack("a"))))
        } finally {
            manager.detachPlayer()
            player.release()
        }
    }

    @Test
    fun drmSessionManagerProvider_lookupVsMiss() {
        val session = LookupSession()
        AudioDrm.setProvider { _, _ -> session }
        val manager = PlaylistManager(RuntimeEnvironment.getApplication())
        assertNull(manager.addItem(drmTrack("a")))
        val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        try {
            manager.attachPlayer(player)
            val known = player.currentMediaItem
            assertNotNull(known)
            val miss = MediaItem.Builder()
                .setMediaId("missing")
                .setUri("https://example.com/miss.mp3")
                .build()
            val provider = manager.drmSessionManagerProvider()

            assertSame(session.manager, manager.drmSessionManagerFor(known!!))
            assertNull(manager.drmSessionManagerFor(miss))
            assertSame(session.manager, provider.get(known))
            assertSame(DrmSessionManager.DRM_UNSUPPORTED, provider.get(miss))
        } finally {
            manager.detachPlayer()
            player.release()
        }
    }

    @Test
    fun providerOnError_afterSuccessfulOpen_reachesDrmErrorListener() {
        val captured = mutableListOf<Consumer<String>>()
        AudioDrm.setProvider { _, onError ->
            captured.add(onError)
            FakeSession()
        }
        val manager = PlaylistManager(RuntimeEnvironment.getApplication())
        val errors = mutableListOf<Pair<String?, String>>()
        manager.drmErrorListener = { trackId, error -> errors.add(trackId to error) }

        assertNull(manager.addItem(drmTrack("a")))
        assertEquals(1, captured.size)

        captured[0].accept(AudioDrm.ERROR_NOT_ENTITLED)
        captured[0].accept("licenseDenied")

        assertEquals(listOf("a" to AudioDrm.ERROR_NOT_ENTITLED, "a" to AudioDrm.ERROR_UNKNOWN), errors)
    }

    @Test
    fun notEntitled_stopsPlayer_andDoesNotOpenAnotherSession() {
        val captured = mutableListOf<Consumer<String>>()
        val session = RecordingSession()
        AudioDrm.setProvider { _, onError ->
            captured.add(onError)
            session
        }
        val manager = PlaylistManager(RuntimeEnvironment.getApplication())
        assertNull(manager.addItem(drmTrack("a")))
        val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        try {
            manager.attachPlayer(player)
            player.playWhenReady = true
            val startsAfterAttach = session.startCount
            assertTrue(startsAfterAttach >= 1)
            captured[0].accept(AudioDrm.ERROR_NOT_ENTITLED)
            Shadows.shadowOf(player.applicationLooper).idle()
            assertFalse(player.playWhenReady)
            manager.attachPlayer(player)
            assertEquals(startsAfterAttach, session.startCount)
            assertEquals(1, captured.size)
        } finally {
            manager.detachPlayer()
            player.release()
        }
    }

    @Test
    fun attachPlayer_openFailure_notifiesUnknown() {
        AudioDrm.setProvider { _, _ -> RecordingSession() }
        val manager = PlaylistManager(RuntimeEnvironment.getApplication())
        assertNull(manager.addItem(drmTrack("a")))
        val errors = mutableListOf<String>()
        manager.drmErrorListener = { _, error -> errors.add(error) }
        val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        try {
            manager.attachPlayer(player)
            manager.detachPlayer()
            AudioDrm.setProvider(null)
            errors.clear()
            manager.attachPlayer(player)
            assertTrue(errors.contains(AudioDrm.ERROR_UNKNOWN))
            assertTrue(errors.all { it == AudioDrm.ERROR_UNKNOWN })
        } finally {
            manager.detachPlayer()
            player.release()
        }
    }

    @Test
    fun createDrmError_pinsTypedErrorOnPayload() {
        val entitled = OnStatusCallback.createDrmError(AudioDrm.ERROR_NOT_ENTITLED)
        assertEquals(AudioDrm.ERROR_NOT_ENTITLED, entitled.getString("error"))
        val coerced = OnStatusCallback.createDrmError("licenseDenied")
        assertEquals(AudioDrm.ERROR_UNKNOWN, coerced.getString("error"))
    }

    private class LookupSession : AudioDrmSession {
        val manager: DrmSessionManager = object : DrmSessionManager by DrmSessionManager.DRM_UNSUPPORTED {}

        override fun applyDrm(builder: MediaItem.Builder) {
            builder.setDrmConfiguration(MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID).build())
        }

        override fun getDrmSessionManager(): DrmSessionManager = manager

        override fun start() {}

        override fun release() {}
    }

    private class RecordingSession : AudioDrmSession {
        var startCount = 0
        var releaseCount = 0

        override fun applyDrm(builder: MediaItem.Builder) {
            builder.setDrmConfiguration(MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID).build())
        }

        override fun getDrmSessionManager(): DrmSessionManager = DrmSessionManager.DRM_UNSUPPORTED

        override fun start() {
            startCount++
        }

        override fun release() {
            releaseCount++
        }
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
