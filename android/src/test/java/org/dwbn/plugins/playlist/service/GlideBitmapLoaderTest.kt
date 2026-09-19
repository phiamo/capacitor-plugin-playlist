package org.dwbn.plugins.playlist.service

import androidx.media3.common.MediaMetadata
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GlideBitmapLoaderTest {

    private val loader: GlideBitmapLoader
        get() = GlideBitmapLoader(RuntimeEnvironment.getApplication())

    @Test
    fun imageJpeg_isSupportedMimeType() {
        assertTrue(GlideBitmapLoader.isSupportedImageMimeType("image/jpeg"))
    }

    @Test
    fun imagePng_isSupportedMimeType() {
        assertTrue(GlideBitmapLoader.isSupportedImageMimeType("IMAGE/PNG"))
    }

    @Test
    fun audioMpeg_isNotSupportedMimeType() {
        assertFalse(GlideBitmapLoader.isSupportedImageMimeType("audio/mpeg"))
    }

    @Test
    fun nullMimeType_isNotSupported() {
        assertFalse(GlideBitmapLoader.isSupportedImageMimeType(null))
    }

    @Test
    fun artworkUri_loadsRemoteArtwork() {
        assertTrue(GlideBitmapLoader.shouldLoadRemoteArtwork("https://example.com/art.jpg"))
    }

    @Test
    fun missingArtwork_doesNotLoadRemote() {
        assertFalse(GlideBitmapLoader.shouldLoadRemoteArtwork(null))
        assertFalse(GlideBitmapLoader.shouldLoadRemoteArtwork(""))
    }

    @Test
    fun loadBitmapFromMetadata_returnsNullWhenNoArtwork() {
        val metadata = MediaMetadata.Builder().setTitle("Lecture").build()
        assertNull(loader.loadBitmapFromMetadata(metadata))
    }

    @Test
    fun loadBitmapFromMetadata_returnsNullForEmptyArtworkBytes() {
        val metadata = MediaMetadata.Builder()
            .setArtworkData(ByteArray(0), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .build()
        assertNull(loader.loadBitmapFromMetadata(metadata))
    }

    @Test
    fun decodeBitmap_nonImageBytes_completesWithoutHanging() {
        val future = loader.decodeBitmap(byteArrayOf(1, 2, 3, 4))
        try {
            future.get(2, TimeUnit.SECONDS)
        } catch (e: ExecutionException) {
            assertTrue(e.cause is IllegalStateException)
        }
    }
}
