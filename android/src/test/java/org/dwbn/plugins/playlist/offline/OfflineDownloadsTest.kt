package org.dwbn.plugins.playlist.offline

import androidx.media3.common.C
import androidx.media3.common.DrmInitData
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import com.getcapacitor.JSObject
import org.dwbn.plugins.playlist.AudioDrm
import org.dwbn.plugins.playlist.AudioDrmSession
import org.dwbn.plugins.playlist.AudioOffline
import org.dwbn.plugins.playlist.AudioOfflineProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@UnstableApi
class OfflineDownloadsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val calls = mutableListOf<String>()
    private val events = mutableListOf<DownloadEvent>()
    private var now = 1_000_000L
    private lateinit var engine: FakeEngine
    private lateinit var provider: FakeProvider
    private lateinit var meta: OfflineMetaStore
    private lateinit var downloads: OfflineDownloads

    @Before
    fun setUp() {
        engine = FakeEngine(calls)
        provider = FakeProvider(calls)
        meta = OfflineMetaStore(tmp.newFile("meta.json").also { it.delete() })
        downloads = OfflineDownloads(engine, meta, { it.run() }, { now }, { provider })
        downloads.eventSink = OfflineDownloads.EventSink { events.add(it) }
    }

    @After
    fun tearDown() {
        AudioOffline.setProvider(null)
    }

    private fun drm() = JSObject().put("playbackSessionId", "s1")

    @Test
    fun start_acquiresLicenceBeforeEnqueue() {
        assertNull(downloads.start("d1", "https://cdn.example/a.m3u8?t=1", null, drm()))

        assertEquals(listOf("prepare:d1", "acquire:d1", "enqueue:d1"), calls)
        assertEquals(DownloadStates.QUEUED, events.first().state)
    }

    @Test
    fun start_thenProgressAndComplete_listShowsExpiresAt() {
        downloads.start("d1", "https://cdn.example/a.m3u8", null, drm())
        engine.emit("d1", EngineState.DOWNLOADING, 0.5f)
        engine.emit("d1", EngineState.COMPLETED, 1f)

        assertEquals(
            listOf(DownloadStates.QUEUED, DownloadStates.DOWNLOADING, DownloadStates.COMPLETED),
            events.map { it.state }
        )
        assertEquals(0.5f, events[1].progress, 0f)
        val info = downloads.list().single()
        assertEquals(DownloadStates.COMPLETED, info.state)
        assertEquals(now + OfflineExpiry.LICENSE_VALIDITY_MS, info.expiresAt)
    }

    @Test
    fun start_licenceRefused_fetchesNoMediaAndFails() {
        provider.acquireResult = AudioDrm.ERROR_OFFLINE_DEVICE_LIMIT

        downloads.start("d1", "https://cdn.example/a.m3u8", null, drm())

        assertEquals(listOf("prepare:d1", "acquire:d1"), calls)
        val last = events.last()
        assertEquals(DownloadStates.FAILED, last.state)
        assertEquals(AudioDrm.ERROR_OFFLINE_DEVICE_LIMIT, last.error)
        assertNull(meta.get("d1"))
        assertTrue(downloads.list().isEmpty())
    }

    @Test
    fun start_notEntitled_isTyped() {
        provider.acquireResult = AudioDrm.ERROR_NOT_ENTITLED
        downloads.start("d1", "https://cdn.example/a.m3u8", null, drm())
        assertEquals(AudioDrm.ERROR_NOT_ENTITLED, events.last().error)
        assertFalse(calls.contains("enqueue:d1"))
    }

    @Test
    fun start_unknownRefusal_collapsesToUnknown() {
        provider.acquireResult = "licenseDenied"
        downloads.start("d1", "https://cdn.example/a.m3u8", null, drm())
        assertEquals(AudioDrm.ERROR_UNKNOWN, events.last().error)
    }

    @Test
    fun start_throwingProvider_failsUnknown() {
        provider.acquireThrows = true
        downloads.start("d1", "https://cdn.example/a.m3u8", null, drm())
        assertEquals(AudioDrm.ERROR_UNKNOWN, events.last().error)
        assertFalse(calls.contains("enqueue:d1"))
    }

    @Test
    fun start_withoutProvider_returnsNoProvider() {
        val bare = OfflineDownloads(engine, meta, { it.run() }, { now }, { null })

        assertEquals(AudioOffline.CODE_NO_PROVIDER, bare.start("d1", "https://x/a.m3u8", null, drm()))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun start_prepareIoError_failsNetworkWithoutLicence() {
        engine.prepareError = IOException("boom")
        downloads.start("d1", "https://cdn.example/a.m3u8", null, drm())
        assertEquals(AudioDrm.ERROR_NETWORK, events.last().error)
        assertFalse(calls.contains("acquire:d1"))
    }

    @Test
    fun start_noDrmFormat_failsWithoutLicence() {
        engine.format = null
        downloads.start("d1", "https://cdn.example/a.m3u8", null, drm())
        assertEquals(DownloadStates.FAILED, events.last().state)
        assertFalse(calls.contains("acquire:d1"))
    }

    @Test
    fun start_segmentFailure_isFailedThenResumable() {
        downloads.start("d1", "https://cdn.example/a.m3u8?t=1", null, drm())
        engine.emit("d1", EngineState.FAILED, 0.4f)
        assertEquals(DownloadStates.FAILED, events.last().state)
        assertEquals(AudioDrm.ERROR_NETWORK, events.last().error)

        calls.clear()
        downloads.start("d1", "https://cdn.example/a.m3u8?t=2", null, drm())

        assertEquals(listOf("prepare:d1", "acquire:d1", "enqueue:d1"), calls)
        assertEquals(0.4f, events.last { it.state == DownloadStates.QUEUED }.progress, 0f)
    }

    @Test
    fun start_alreadyCompleted_isIdempotent() {
        downloads.start("d1", "https://cdn.example/a.m3u8", null, drm())
        engine.emit("d1", EngineState.COMPLETED, 1f)
        calls.clear()

        assertNull(downloads.start("d1", "https://cdn.example/a.m3u8", null, drm()))

        assertTrue(calls.isEmpty())
        assertEquals(DownloadStates.COMPLETED, events.last().state)
    }

    @Test
    fun cancelDuringLicenceRequest_releasesLicenceAndEnqueuesNothing() {
        provider.onAcquire = { downloads.cancel("d1") }

        downloads.start("d1", "https://cdn.example/a.m3u8", null, drm())

        assertFalse(calls.contains("enqueue:d1"))
        assertTrue(calls.contains("release:d1"))
        assertNull(meta.get("d1"))
    }

    @Test
    fun cancel_nonCompleted_removesEverything() {
        downloads.start("d1", "https://cdn.example/a.m3u8", null, drm())
        engine.emit("d1", EngineState.DOWNLOADING, 0.2f)
        calls.clear()

        downloads.cancel("d1")

        assertEquals(listOf("remove:d1", "release:d1"), calls)
        assertNull(meta.get("d1"))
        assertNull(engine.get("d1"))
    }

    @Test
    fun cancel_completed_isNoOp() {
        downloads.start("d1", "https://cdn.example/a.m3u8", null, drm())
        engine.emit("d1", EngineState.COMPLETED, 1f)
        calls.clear()

        downloads.cancel("d1")

        assertTrue(calls.isEmpty())
        assertTrue(meta.get("d1") != null)
        assertEquals(1, downloads.list().size)
    }

    @Test
    fun delete_completed_removesCacheMetaAndLicence_ignoringReleaseFailure() {
        downloads.start("d1", "https://cdn.example/a.m3u8", null, drm())
        engine.emit("d1", EngineState.COMPLETED, 1f)
        provider.releaseThrows = true
        calls.clear()

        downloads.delete("d1")

        assertEquals(listOf("remove:d1", "release:d1"), calls)
        assertNull(meta.get("d1"))
        assertTrue(downloads.list().isEmpty())
    }

    @Test
    fun renew_success_refreshesExpiresAt() {
        downloads.start("d1", "https://cdn.example/a.m3u8", null, drm())
        engine.emit("d1", EngineState.COMPLETED, 1f)
        now += 20L * 24 * 60 * 60 * 1000
        var result: String? = "pending"

        downloads.renew("d1", null) { result = it }

        assertNull(result)
        assertEquals(now + OfflineExpiry.LICENSE_VALIDITY_MS, downloads.list().single().expiresAt)
        assertEquals(DownloadStates.COMPLETED, events.last().state)
    }

    @Test
    fun renew_refused_movesToExpiredWithTypedError() {
        downloads.start("d1", "https://cdn.example/a.m3u8", null, drm())
        engine.emit("d1", EngineState.COMPLETED, 1f)
        val expiresBefore = meta.get("d1")!!.expiresAt
        provider.renewResult = AudioDrm.ERROR_NOT_ENTITLED
        provider.expireOnRenewRefusal = true
        var result: String? = null

        downloads.renew("d1", null) { result = it }

        assertEquals(AudioDrm.ERROR_NOT_ENTITLED, result)
        assertEquals(DownloadStates.EXPIRED, events.last().state)
        assertEquals(AudioDrm.ERROR_NOT_ENTITLED, events.last().error)
        assertEquals(expiresBefore, meta.get("d1")!!.expiresAt)
    }

    @Test
    fun renew_refusedWhileProviderStateStaysActive_emitsNoExpiredEvent() {
        downloads.start("d1", "https://cdn.example/a.m3u8", null, drm())
        engine.emit("d1", EngineState.COMPLETED, 1f)
        val eventsBefore = events.size
        provider.renewResult = AudioDrm.ERROR_EXPIRED
        var result: String? = null

        downloads.renew("d1", null) { result = it }

        assertEquals(AudioDrm.ERROR_EXPIRED, result)
        assertEquals(eventsBefore, events.size)
        assertEquals(DownloadStates.COMPLETED, downloads.list().single().state)
    }

    @Test
    fun start_whileAlreadyPending_isNoOp() {
        provider.onAcquire = {
            assertNull(downloads.start("d1", "https://cdn.example/a.m3u8", null, drm()))
        }

        downloads.start("d1", "https://cdn.example/a.m3u8", null, drm())

        assertEquals(listOf("prepare:d1", "acquire:d1", "enqueue:d1"), calls)
    }

    @Test
    fun start_enqueueThrows_releasesLicenceCleansMetaAndFails() {
        engine.enqueueError = IllegalStateException("enqueue")

        downloads.start("d1", "https://cdn.example/a.m3u8", null, drm())

        assertTrue(calls.contains("release:d1"))
        assertNull(meta.get("d1"))
        assertEquals(DownloadStates.FAILED, events.last().state)
        assertEquals(AudioDrm.ERROR_UNKNOWN, events.last().error)
    }

    @Test
    fun renew_networkFailure_doesNotExpire() {
        downloads.start("d1", "https://cdn.example/a.m3u8", null, drm())
        engine.emit("d1", EngineState.COMPLETED, 1f)
        val eventsBefore = events.size
        provider.renewResult = AudioDrm.ERROR_NETWORK
        var result: String? = null

        downloads.renew("d1", null) { result = it }

        assertEquals(AudioDrm.ERROR_NETWORK, result)
        assertEquals(eventsBefore, events.size)
    }

    @Test
    fun renew_unknownDownload_andNoProvider() {
        var result: String? = null
        downloads.renew("nope", null) { result = it }
        assertEquals(AudioDrm.ERROR_UNKNOWN, result)

        val bare = OfflineDownloads(engine, meta, { it.run() }, { now }, { null })
        bare.renew("d1", null) { result = it }
        assertEquals(AudioOffline.CODE_NO_PROVIDER, result)
    }

    @Test
    fun list_skipsFailedEngineRows() {
        engine.emit("ghost", EngineState.FAILED, 0.2f)
        assertTrue(downloads.list().isEmpty())
    }

    @Test
    fun init_purgesFailedEngineRows() {
        engine.emit("ghost", EngineState.FAILED, 0.2f)
        val cleaned = OfflineDownloads(engine, meta, { it.run() }, { now }, { provider })
        assertNull(engine.get("ghost"))
        assertTrue(cleaned.list().isEmpty())
    }

    @Test
    fun list_marksCompletedDownloadExpiredWhenProviderSaysSo() {
        downloads.start("d1", "https://cdn.example/a.m3u8", null, drm())
        engine.emit("d1", EngineState.COMPLETED, 1f)
        provider.stateResult = AudioOfflineProvider.STATE_EXPIRED
        provider.needsRenewalResult = true

        val info = downloads.list().single()

        assertEquals(DownloadStates.EXPIRED, info.state)
        assertTrue(info.needsRenewal)
    }

    @Test
    fun expiryMaths_is27Days() {
        assertEquals(27L * 24 * 60 * 60 * 1000, OfflineExpiry.LICENSE_VALIDITY_MS)
        assertEquals(5_000L + OfflineExpiry.LICENSE_VALIDITY_MS, OfflineExpiry.expiresAt(5_000L))
    }

    @Test
    fun cacheKey_ignoresQueryAndFragment() {
        val key = OfflineStorage.QueryIgnoringCacheKeyFactory
        assertEquals(
            "https://cdn.example/a/seg0.ts",
            key.keyFor("https://cdn.example/a/seg0.ts?token=abc&exp=1")
        )
        assertEquals(key.keyFor("https://cdn.example/a/seg0.ts?token=1"), key.keyFor("https://cdn.example/a/seg0.ts?token=2"))
        assertEquals("https://cdn.example/a/seg0.ts", key.keyFor("https://cdn.example/a/seg0.ts#frag"))
        assertEquals("https://cdn.example/a/seg0.ts", key.keyFor("https://cdn.example/a/seg0.ts"))
    }

    @Test
    fun metaStore_persistsAndRemoves() {
        val file = tmp.newFile("persist.json").also { it.delete() }
        OfflineMetaStore(file).put("d1", OfflineMetaStore.Entry(1, 2))

        val reopened = OfflineMetaStore(file)
        assertEquals(OfflineMetaStore.Entry(1, 2), reopened.get("d1"))
        reopened.remove("d1")
        assertNull(OfflineMetaStore(file).get("d1"))
        assertFalse(file.readText().contains("http"))
    }

    @Test
    fun firstWithDrmInitData_prefersSelectedAndSkipsPlain() {
        val plain = Format.Builder().setId("plain").build()
        val drmA = Format.Builder().setId("a").setDrmInitData(drmInit()).build()
        val drmB = Format.Builder().setId("b").setDrmInitData(drmInit()).build()

        assertEquals("b", OfflineFormats.firstWithDrmInitData(listOf(plain to true, drmA to false, drmB to true))?.id)
        assertEquals("a", OfflineFormats.firstWithDrmInitData(listOf(plain to true, drmA to false))?.id)
        assertNull(OfflineFormats.firstWithDrmInitData(listOf(plain to true)))
    }

    private fun drmInit() = DrmInitData(DrmInitData.SchemeData(C.WIDEVINE_UUID, "video/mp4", ByteArray(4)))

    private class FakeEngine(private val calls: MutableList<String>) : OfflineEngine {
        override var listener: OfflineEngine.Listener? = null
        var prepareError: IOException? = null
        var enqueueError: RuntimeException? = null
        var format: Format? = Format.Builder()
            .setDrmInitData(DrmInitData(DrmInitData.SchemeData(C.WIDEVINE_UUID, "video/mp4", ByteArray(4))))
            .build()
        private val store = LinkedHashMap<String, EngineDownload>()

        override fun prepare(downloadId: String, url: String, mimeType: String?): PreparedDownload {
            calls.add("prepare:$downloadId")
            prepareError?.let { throw it }
            return PreparedDownload(downloadId, format, url)
        }

        override fun enqueue(prepared: PreparedDownload) {
            calls.add("enqueue:${prepared.downloadId}")
            enqueueError?.let { throw it }
            val previous = store[prepared.downloadId]
            store[prepared.downloadId] = EngineDownload(
                prepared.downloadId,
                EngineState.QUEUED,
                previous?.progress ?: 0f,
                prepared.handle as String
            )
        }

        override fun remove(downloadId: String) {
            calls.add("remove:$downloadId")
            store.remove(downloadId)
        }

        override fun get(downloadId: String): EngineDownload? = store[downloadId]

        override fun all(): List<EngineDownload> = store.values.toList()

        fun emit(id: String, state: EngineState, progress: Float) {
            val updated = EngineDownload(id, state, progress, store[id]?.requestUri)
            store[id] = updated
            listener?.onChanged(updated)
        }
    }

    private class FakeProvider(private val calls: MutableList<String>) : AudioOfflineProvider {
        var acquireResult: String? = null
        var acquireThrows = false
        var renewResult: String? = null
        var releaseThrows = false
        var stateResult = AudioOfflineProvider.STATE_ACTIVE
        var needsRenewalResult = false
        var onAcquire: (() -> Unit)? = null
        var expireOnRenewRefusal = false

        override fun acquire(downloadId: String, format: Format, drm: JSObject): String? {
            calls.add("acquire:$downloadId")
            if (acquireThrows) throw IllegalStateException("acquire")
            onAcquire?.invoke()
            return acquireResult
        }

        override fun needsRenewal(downloadId: String): Boolean = needsRenewalResult

        override fun renew(downloadId: String, drm: JSObject?): String? {
            if (renewResult != null && expireOnRenewRefusal) {
                stateResult = AudioOfflineProvider.STATE_EXPIRED
            }
            return renewResult
        }

        override fun release(downloadId: String) {
            calls.add("release:$downloadId")
            if (releaseThrows) throw IllegalStateException("release")
        }

        override fun state(downloadId: String): String = stateResult

        override fun openOffline(downloadId: String): AudioDrmSession = throw UnsupportedOperationException()
    }
}
