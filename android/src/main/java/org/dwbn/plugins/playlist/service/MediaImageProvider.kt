package org.dwbn.plugins.playlist.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.devbrackets.android.playlistcore.components.image.ImageProvider
import org.dwbn.plugins.playlist.FakeR
import org.dwbn.plugins.playlist.data.AudioTrack
import org.dwbn.plugins.playlist.manager.Options

class MediaImageProvider(context: Context, val onImageUpdated: OnImageUpdatedListener, options: Options) : ImageProvider<AudioTrack> {

    interface OnImageUpdatedListener {
        fun onImageUpdated()
    }

    private var options: Options? = null
    private val glide: RequestManager
    private val fakeR: FakeR
    private val notificationImageTarget = NotificationImageTarget()
    private val remoteViewImageTarget = RemoteViewImageTarget()
    private val defaultNotificationImage: Bitmap
    private val defaultArtworkImage: Bitmap
    private var notificationImage: Bitmap? = null
    private var artworkImage: Bitmap? = null
    private var notificationIconId = 0
    override val notificationIconRes: Int
        get() = mipmapIcon

    override val remoteViewIconRes: Int
        get() = mipmapIcon

    override val largeNotificationImage: Bitmap?
        get() = if (notificationImage != null) notificationImage else defaultNotificationImage

    override var remoteViewArtwork: Bitmap? = null
        get() = if (artworkImage != null) artworkImage else defaultArtworkImage
        private set

    override fun updateImages(playlistItem: AudioTrack) {
        glide.asBitmap().load(playlistItem.thumbnailUrl).into(notificationImageTarget)
        glide.asBitmap().load(playlistItem.artworkUrl).into(remoteViewImageTarget)
    }

    // return R.mipmap.icon; // this comes from cordova itself.
    private val mipmapIcon: Int
        get() {
            // return R.mipmap.icon; // this comes from cordova itself.
            if (notificationIconId <= 0) {
                notificationIconId = fakeR.getId("drawable", options?.icon)
            }
            return notificationIconId
        }

    /**
     * A class used to listen to the loading of the large notification images and perform
     * the correct functionality to update the notification once it is loaded.
     *
     * **NOTE:** This is a Glide Image loader class
     */
    private inner class NotificationImageTarget : SimpleTarget<Bitmap>() {
        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
            notificationImage = resource
            onImageUpdated
        }
    }

    /**
     * A class used to listen to the loading of the large lock screen images and perform
     * the correct functionality to update the artwork once it is loaded.
     *
     * **NOTE:** This is a Glide Image loader class
     */
    private inner class RemoteViewImageTarget : SimpleTarget<Bitmap>() {
        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
            remoteViewArtwork = resource
            onImageUpdated
        }
    }

    init {
        glide = Glide.with(context.applicationContext)
        fakeR = FakeR(context.applicationContext)
        this.options = options
        // R.drawable.img_playlist_notif_default
        // R.drawable.img_playlist_artwork_default
        //defaultNotificationImage = BitmapFactory.decodeResource(context.resources, fakeR.getId("drawable", "img_playlist_notif_default"))
        //defaultArtworkImage = BitmapFactory.decodeResource(context.resources, fakeR.getId("drawable", "img_playlist_artwork_default"))
        defaultNotificationImage = BitmapFactory.decodeResource(context.resources, fakeR.getId("drawable", "ic_notification_icon"))
        defaultArtworkImage = BitmapFactory.decodeResource(context.resources, fakeR.getId("drawable", "ic_notification_icon"))
    }
}