package org.dwbn.plugins.playlist

import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import org.dwbn.plugins.playlist.data.AudioTrack
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AudioMediaItemFactoryMimeTest {

    /** Issue #144: isStream must no longer force the HLS parser onto a progressive stream. */
    @Test
    fun extensionlessStream_setsNoMimeOnMediaItem() {
        val item = AudioMediaItemFactory.fromUrl("https://example.com/stream/audio", null)
        assertNull(item.localConfiguration?.mimeType)
    }

    @Test
    fun m3u8Url_setsHlsMimeOnMediaItem() {
        val item = AudioMediaItemFactory.fromUrl("https://example.com/audio.m3u8", null)
        assertEquals(MimeTypes.APPLICATION_M3U8, item.localConfiguration?.mimeType)
    }

    @Test
    fun mp3Url_doesNotForceHlsMimeOnMediaItem() {
        val item = AudioMediaItemFactory.fromUrl("https://example.com/lecture.mp3", null)
        assertNull(item.localConfiguration?.mimeType)
    }

    @Test
    fun fromAudioTrack_extensionlessStream_setsNoMimeOnMediaItem() {
        val json = JSONObject()
        json.put("assetUrl", "https://example.com/stream/audio")
        json.put("isStream", true)
        val item = AudioMediaItemFactory.fromAudioTrack(AudioTrack(json))
        assertNull(item.localConfiguration?.mimeType)
    }

    @Test
    fun fromAudioTrack_declaredMimeType_setsHlsMimeOnExtensionlessUrl() {
        val json = JSONObject()
        json.put("assetUrl", "https://example.com/stream/audio")
        json.put("isStream", true)
        json.put("mimeType", MimeTypes.APPLICATION_M3U8)
        val item = AudioMediaItemFactory.fromAudioTrack(AudioTrack(json))
        assertEquals(MimeTypes.APPLICATION_M3U8, item.localConfiguration?.mimeType)
    }

    @Test
    fun fromAudioTrack_emptyTitle_fallsBackToAudioPlayback() {
        val json = JSONObject()
        json.put("assetUrl", "https://example.com/lecture.mp3")
        val item = AudioMediaItemFactory.fromAudioTrack(AudioTrack(json))
        assertEquals("Audio playback", item.mediaMetadata.title.toString())
    }

    @Test
    fun fromAudioTrack_setsArtworkUriFromAlbumArt() {
        val json = JSONObject()
        json.put("assetUrl", "https://example.com/lecture.mp3")
        json.put("albumArt", "https://example.com/art.jpg")
        json.put("title", "Lecture")
        val item = AudioMediaItemFactory.fromAudioTrack(AudioTrack(json))
        assertEquals("https://example.com/art.jpg", item.mediaMetadata.artworkUri.toString())
        assertEquals("Lecture", item.mediaMetadata.title.toString())
    }

    @Test
    fun nullUrl_doesNotThrow() {
        val item = AudioMediaItemFactory.fromUrl(null, null)
        assertNull(item.localConfiguration?.mimeType)
    }
}

class RmxPlaybackErrorMapperTest {

    @Test
    fun decoderErrors_mapToDecode() {
        assertEquals(
            RmxAudioErrorType.RMXERR_DECODE,
            RmxPlaybackErrorMapper.fromMedia3ErrorCode(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED)
        )
        assertEquals(
            RmxAudioErrorType.RMXERR_DECODE,
            RmxPlaybackErrorMapper.fromMedia3ErrorCode(PlaybackException.ERROR_CODE_DECODING_FAILED)
        )
        assertEquals(
            RmxAudioErrorType.RMXERR_DECODE,
            RmxPlaybackErrorMapper.fromMedia3ErrorCode(PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED)
        )
    }

    /**
     * Issue #143 follow-up: a dropped connection used to be indistinguishable from an
     * unparseable source, so consumers could not choose between retry and skip.
     */
    @Test
    fun networkErrors_mapToNetwork() {
        listOf(
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        ).forEach { code ->
            assertEquals(
                "error code $code should map to RMXERR_NETWORK",
                RmxAudioErrorType.RMXERR_NETWORK,
                RmxPlaybackErrorMapper.fromMedia3ErrorCode(code)
            )
        }
    }

    @Test
    fun unusableSourceErrors_mapToNoneSupported() {
        listOf(
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED
        ).forEach { code ->
            assertEquals(
                "error code $code should map to RMXERR_NONE_SUPPORTED",
                RmxAudioErrorType.RMXERR_NONE_SUPPORTED,
                RmxPlaybackErrorMapper.fromMedia3ErrorCode(code)
            )
        }
    }

    @Test
    fun unknownError_fallsBackToNoneSupported() {
        assertEquals(
            RmxAudioErrorType.RMXERR_NONE_SUPPORTED,
            RmxPlaybackErrorMapper.fromMedia3ErrorCode(PlaybackException.ERROR_CODE_UNSPECIFIED)
        )
    }
}
