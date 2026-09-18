package org.dwbn.plugins.playlist.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture

/**
 * Glide-backed [BitmapLoader] for Media3 notification / lock-screen artwork.
 * Load failures complete the future exceptionally so Media3 keeps the small icon only.
 */
@OptIn(UnstableApi::class)
class GlideBitmapLoader(context: Context) : BitmapLoader {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun supportsMimeType(mimeType: String): Boolean {
        return isSupportedImageMimeType(mimeType)
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        try {
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            if (bitmap == null) {
                future.setException(IllegalStateException("Failed to decode artwork bytes"))
            } else {
                future.set(bitmap)
            }
        } catch (t: Throwable) {
            if (!future.isDone) {
                future.setException(t)
            }
        }
        return future
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        return loadWithGlide(uri)
    }

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        if (MediaNotificationPolicy.hasNoLargeArtwork(metadata.artworkData, metadata.artworkUri?.toString())) {
            return null
        }
        val data = metadata.artworkData
        if (data != null) {
            return decodeBitmap(data)
        }
        val uri = metadata.artworkUri
        if (uri != null && MediaNotificationPolicy.shouldLoadRemoteArtwork(uri.toString())) {
            return loadBitmap(uri)
        }
        return null
    }

    private fun loadWithGlide(model: Any): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        val load = Runnable {
            try {
                Glide.with(appContext)
                    .asBitmap()
                    .load(model)
                    .into(object : CustomTarget<Bitmap>() {
                        override fun onResourceReady(
                            resource: Bitmap,
                            transition: Transition<in Bitmap>?
                        ) {
                            future.set(resource)
                        }

                        override fun onLoadFailed(errorDrawable: Drawable?) {
                            if (!future.isDone) {
                                future.setException(IllegalStateException("Artwork load failed"))
                            }
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {
                            if (!future.isDone) {
                                future.setException(IllegalStateException("Artwork load cleared"))
                            }
                        }
                    })
            } catch (t: Throwable) {
                if (!future.isDone) {
                    future.setException(t)
                }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            load.run()
        } else if (!mainHandler.post(load)) {
            future.setException(IllegalStateException("Artwork load post failed"))
        }
        return future
    }

    companion object {
        @JvmStatic
        fun isSupportedImageMimeType(mimeType: String): Boolean {
            return mimeType.startsWith("image/", ignoreCase = true)
        }

        @JvmStatic
        fun shouldLoadRemoteArtwork(artworkUri: String?): Boolean {
            return MediaNotificationPolicy.shouldLoadRemoteArtwork(artworkUri)
        }
    }
}
