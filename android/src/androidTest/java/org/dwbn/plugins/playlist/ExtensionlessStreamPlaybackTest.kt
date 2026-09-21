package org.dwbn.plugins.playlist

import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.dwbn.plugins.playlist.data.AudioTrack
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Issue #144, on a device and through the real Media3 pipeline.
 *
 * The unit tests assert which MIME type the factory resolves; these assert what ExoPlayer actually
 * does with the result. A progressive MP3 is served from an **extensionless** local URL — the exact
 * shape that broke: `isStream: true` with nothing in the URL for the old heuristic to recognise.
 */
@RunWith(AndroidJUnit4::class)
class ExtensionlessStreamPlaybackTest {

    private var server: LocalMediaServer? = null

    @Before
    fun startServer() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        fun asset(name: String) = assets.open(name).use { it.readBytes() }

        val mp3 = asset("tone.mp3")
        assertTrue("fixture MP3 did not load from the test APK's assets", mp3.size > 1000)
        server = LocalMediaServer(
            // Any path not listed here returns the MP3 body, so an extensionless URL still works.
            defaultBody = mp3,
            routes = mapOf(
                "/hls/playlist.m3u8" to asset("hls/playlist.m3u8"),
                "/hls/seg0.ts" to asset("hls/seg0.ts"),
                "/hls/seg1.ts" to asset("hls/seg1.ts"),
                "/hls/seg2.ts" to asset("hls/seg2.ts")
            )
        )
    }

    @After
    fun stopServer() {
        server?.close()
    }

    private fun url(path: String): String = server!!.url(path)

    /** The regression: this URL used to be parsed as an HLS playlist and fail to prepare. */
    @Test
    fun extensionlessStream_preparesAndBecomesReady() {
        val track = audioTrack(url("/stream/audio"), isStream = true)
        assertNull("no MIME type should be forced on an extensionless stream", track.mimeType)

        val outcome = prepare(track)

        assertNull("expected no playback error, got: ${outcome.error?.errorCodeName}", outcome.error)
        assertTrue("player never reached STATE_READY", outcome.becameReady)
    }

    /** Same URL, but explicitly declared as HLS — the opt-in must genuinely reach ExoPlayer. */
    @Test
    fun extensionlessStream_declaredAsHls_failsToParse() {
        val track = audioTrack(
            url("/stream/audio"),
            isStream = true,
            mimeType = MimeTypes.APPLICATION_M3U8
        )
        assertEquals(MimeTypes.APPLICATION_M3U8, track.mimeType)

        val outcome = prepare(track)

        assertNotNull("declaring HLS on an MP3 body should fail to parse", outcome.error)
        // This is the error the reporter saw on every track; it is now opt-in only.
        assertEquals(
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            outcome.error!!.errorCode
        )
        assertEquals(
            RmxAudioErrorType.RMXERR_NONE_SUPPORTED,
            RmxPlaybackErrorMapper.fromMedia3ErrorCode(outcome.error!!.errorCode)
        )
    }

    /** A query string must not decide how the body is parsed (the old check was a substring match). */
    @Test
    fun extensionInQueryString_isStillTreatedAsProgressive() {
        val track = audioTrack(url("/stream/audio?playlist=.m3u8"), isStream = true)

        val outcome = prepare(track)

        assertNull("expected no playback error, got: ${outcome.error?.errorCodeName}", outcome.error)
        assertTrue("player never reached STATE_READY", outcome.becameReady)
    }

    /** The other half of #144: real HLS must still be picked up from an `.m3u8` path. */
    @Test
    fun m3u8Playlist_stillPlaysThroughTheHlsParser() {
        val track = audioTrack(url("/hls/playlist.m3u8"), isStream = true)
        assertEquals(MimeTypes.APPLICATION_M3U8, AudioMediaItemFactory.resolveMimeType(track.mediaUrl, track.mimeType))

        val outcome = prepare(track)

        assertNull("expected no playback error, got: ${outcome.error?.errorCodeName}", outcome.error)
        assertTrue("player never reached STATE_READY", outcome.becameReady)
    }

    /** ...including when the playlist URL carries a query string, as signed CDN links do. */
    @Test
    fun m3u8PlaylistWithQueryString_stillPlaysThroughTheHlsParser() {
        val outcome = prepare(audioTrack(url("/hls/playlist.m3u8?token=abc123"), isStream = true))

        assertNull("expected no playback error, got: ${outcome.error?.errorCodeName}", outcome.error)
        assertTrue("player never reached STATE_READY", outcome.becameReady)
    }

    private fun audioTrack(url: String, isStream: Boolean, mimeType: String? = null): AudioTrack {
        val json = JSONObject()
        json.put("trackId", "t1")
        json.put("assetUrl", url)
        json.put("isStream", isStream)
        if (mimeType != null) {
            json.put("mimeType", mimeType)
        }
        return AudioTrack(json)
    }

    private data class Outcome(val becameReady: Boolean, val error: PlaybackException?)

    /** Prepare the track on a real ExoPlayer and wait for it to settle on READY or an error. */
    private fun prepare(track: AudioTrack): Outcome {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val settled = CountDownLatch(1)
        val error = AtomicReference<PlaybackException?>(null)
        val ready = AtomicReference(false)
        val player = AtomicReference<ExoPlayer?>(null)

        instrumentation.runOnMainSync {
            val exoPlayer = ExoPlayer.Builder(instrumentation.targetContext).build()
            player.set(exoPlayer)
            exoPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        ready.set(true)
                        settled.countDown()
                    }
                }

                override fun onPlayerError(e: PlaybackException) {
                    error.set(e)
                    settled.countDown()
                }
            })
            exoPlayer.setMediaItem(AudioMediaItemFactory.fromAudioTrack(track))
            exoPlayer.prepare()
        }

        val settledInTime = settled.await(20, TimeUnit.SECONDS)
        instrumentation.runOnMainSync { player.get()?.release() }
        assertTrue("player neither became ready nor errored within 20s", settledInTime)

        return Outcome(ready.get(), error.get())
    }
}

/**
 * Minimal HTTP/1.1 server for the fixtures. Unmapped paths return [defaultBody], so a request can
 * be made against a URL that carries no file extension at all. Deliberately does not send a
 * Content-Type: the point is that ExoPlayer decides from the URL and the body, not from a header.
 */
private class LocalMediaServer(
    private val defaultBody: ByteArray,
    private val routes: Map<String, ByteArray> = emptyMap()
) : Closeable {
    private val socket = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
    private val thread = Thread { acceptLoop() }.apply { isDaemon = true; start() }

    fun url(path: String): String = "http://127.0.0.1:${socket.localPort}$path"

    private fun acceptLoop() {
        while (!socket.isClosed) {
            try {
                socket.accept().use { serve(it) }
            } catch (_: Exception) {
                return // socket closed, or the client went away
            }
        }
    }

    private fun serve(client: Socket) {
        val input = client.getInputStream().bufferedReader()
        val requestLine = input.readLine().orEmpty()
        var line = input.readLine()
        var rangeStart = 0
        while (!line.isNullOrEmpty()) {
            if (line.startsWith("Range:", ignoreCase = true)) {
                rangeStart = Regex("bytes=(\\d+)-").find(line)?.groupValues?.get(1)?.toInt() ?: 0
            }
            line = input.readLine()
        }

        val path = requestLine.split(" ").getOrNull(1)?.substringBefore('?').orEmpty()
        val body = routes[path] ?: defaultBody
        val slice = body.copyOfRange(rangeStart.coerceAtMost(body.size), body.size)
        val status = if (rangeStart > 0) "206 Partial Content" else "200 OK"
        val headers = buildString {
            append("HTTP/1.1 $status\r\n")
            append("Content-Length: ${slice.size}\r\n")
            append("Accept-Ranges: bytes\r\n")
            if (rangeStart > 0) {
                append("Content-Range: bytes $rangeStart-${body.size - 1}/${body.size}\r\n")
            }
            append("Connection: close\r\n\r\n")
        }
        client.getOutputStream().apply {
            write(headers.toByteArray())
            write(slice)
            flush()
        }
    }

    override fun close() {
        socket.close()
        thread.interrupt()
    }
}
