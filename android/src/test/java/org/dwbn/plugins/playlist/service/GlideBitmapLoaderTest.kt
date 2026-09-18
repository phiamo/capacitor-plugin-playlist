package org.dwbn.plugins.playlist.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlideBitmapLoaderTest {

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
    fun artworkUri_loadsRemoteArtwork() {
        assertTrue(GlideBitmapLoader.shouldLoadRemoteArtwork("https://example.com/art.jpg"))
    }

    @Test
    fun missingArtwork_doesNotLoadRemote() {
        assertFalse(GlideBitmapLoader.shouldLoadRemoteArtwork(null))
        assertFalse(GlideBitmapLoader.shouldLoadRemoteArtwork(""))
    }
}
