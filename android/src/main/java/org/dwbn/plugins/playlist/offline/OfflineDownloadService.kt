package org.dwbn.plugins.playlist.offline

import android.app.Notification
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import org.dwbn.plugins.playlist.R

/**
 * Foreground (`dataSync`) service that keeps offline downloads running beside the playback
 * `MediaService`. The manager, cache and index are the process singletons from [OfflineDownloads].
 */
@UnstableApi
class OfflineDownloadService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.playlist_offline_channel_name,
    0
) {
    override fun getDownloadManager(): DownloadManager =
        (OfflineDownloads.get(this).engine as Media3OfflineEngine).manager

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification = DownloadNotificationHelper(this, CHANNEL_ID).buildProgressNotification(
        this,
        R.drawable.ic_notification_icon,
        null,
        null,
        downloads,
        notMetRequirements
    )

    private companion object {
        const val NOTIFICATION_ID = 0x44574e // "DWN"
        const val CHANNEL_ID = "playlist_offline_downloads"
    }
}
