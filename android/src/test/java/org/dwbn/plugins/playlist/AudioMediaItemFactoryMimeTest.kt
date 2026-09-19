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

    @Test
    fun extensionlessStream_setsHlsMimeOnMediaItem() {
        val item = AudioMediaItemFactory.fromUrl("https://example.com/stream/audio", true)
        assertEquals(MimeTypes.APPLICATION_M3U8, item.localConfiguration?.mimeType)
    }

    @Test
    fun m3u8Url_setsHlsMimeOnMediaItem() {
        val item = AudioMediaItemFactory.fromUrl("https://example.com/audio.m3u8", false)
        assertEquals(MimeTypes.APPLICATION_M3U8, item.localConfiguration?.mimeType)
    }

    @Test
    fun mp3Url_doesNotForceHlsMimeOnMediaItem() {
        val item = AudioMediaItemFactory.fromUrl("https://example.com/lecture.mp3", false)
        assertNull(item.localConfiguration?.mimeType)
    }

    @Test
    fun fromAudioTrack_setsHlsMimeForExtensionlessStream() {
        val json = JSONObject()
        json.put("assetUrl", "https://example.com/stream/audio")
        json.put("isStream", true)
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
        val item = AudioMediaItemFactory.fromUrl(null, true)
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

    @Test
    fun ioErrors_mapToNoneSupported() {
        assertEquals(
            RmxAudioErrorType.RMXERR_NONE_SUPPORTED,
            RmxPlaybackErrorMapper.fromMedia3ErrorCode(
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
            )
        )
    }
}
