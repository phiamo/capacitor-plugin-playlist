package org.dwbn.plugins.playlist.offline

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@UnstableApi
class HlsWidevineSessionKeyTest {

    @Test
    fun formatFromPlaylist_readsTest1SessionKey() {
        val playlist = """
            #EXTM3U
            #EXT-X-INDEPENDENT-SEGMENTS
            #EXT-X-SESSION-KEY:METHOD=SAMPLE-AES,URI="data:text/plain;base64,$TEST1_PSSH_B64",KEYID=0x$TEST1_KID_HEX,IV=0x8cc01bfa103c305aca6277566aac0955,KEYFORMATVERSIONS="1",KEYFORMAT="urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"
            #EXT-X-MEDIA:TYPE=AUDIO,URI="audio/stream.m3u8",GROUP-ID="audio",NAME="audio",DEFAULT=NO,AUTOSELECT=YES,CHANNELS="2"
            #EXT-X-STREAM-INF:BANDWIDTH=99508,CODECS="mp4a.40.2",AUDIO="audio"
            audio/stream.m3u8
        """.trimIndent()

        val format = HlsWidevineSessionKey.formatFromPlaylist(playlist)
        assertNotNull(format)
        val init = format!!.drmInitData
        assertNotNull(init)
        assertEquals(1, init!!.schemeDataCount)
        val scheme = init.get(0)
        assertTrue(scheme.matches(C.WIDEVINE_UUID))
        val pssh = scheme.data
        assertNotNull(pssh)
        assertTrue(pssh!!.size > 16)
        val psshText = String(pssh, Charsets.ISO_8859_1)
        assertTrue(psshText.contains("pssh"))
        val kid = hexToBytes(TEST1_KID_HEX)
        assertTrue(indexOf(pssh, kid) >= 0)
        assertArrayEquals(pssh, HlsWidevineSessionKey.firstWidevinePssh(playlist))
    }

    @Test
    fun firstWidevinePssh_skipsFairPlayAndReadsMediaKey() {
        val playlist = """
            #EXT-X-SESSION-KEY:METHOD=SAMPLE-AES,URI="skd://session-kid",KEYFORMAT="com.apple.streamingkeydelivery"
            #EXT-X-KEY:METHOD=SAMPLE-AES,URI="data:text/plain;base64,$TEST1_PSSH_B64",KEYFORMAT="urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"
        """.trimIndent()

        assertNotNull(HlsWidevineSessionKey.firstWidevinePssh(playlist))
    }

    @Test
    fun formatFromPlaylist_plainMaster_isNull() {
        assertNull(
            HlsWidevineSessionKey.formatFromPlaylist(
                "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\naudio/stream.m3u8\n",
            ),
        )
    }

    private fun hexToBytes(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun indexOf(hay: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..hay.size - needle.size) {
            for (j in needle.indices) {
                if (hay[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    companion object {
        const val TEST1_KID_HEX = "4b835f185137cb68df173e05cf758407"
        const val TEST1_PSSH_B64 =
            "AAAAOHBzc2gAAAAA7e+LqXnWSs6jyCfc1R0h7QAAABgSEEuDXxhRN8to3xc+Bc91hAdI88aJmwY="
    }
}
