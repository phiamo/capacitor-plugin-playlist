package org.dwbn.plugins.playlist.manager

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import org.dwbn.plugins.playlist.AudioMediaItemFactory
import org.dwbn.plugins.playlist.PlaylistItemOptions
import org.dwbn.plugins.playlist.RmxAudioPlayer
import org.dwbn.plugins.playlist.TrackRemovalItem
import org.dwbn.plugins.playlist.data.AudioTrack
import org.dwbn.plugins.playlist.playlist.AudioPlaylistHandler
import org.dwbn.plugins.playlist.handoff.VideoPlayerBridge
import org.dwbn.plugins.playlist.service.MediaNotificationPolicy
import org.dwbn.plugins.playlist.service.MediaService
import java.lang.ref.WeakReference
import java.util.ArrayList

@OptIn(UnstableApi::class)
class PlaylistManager(private val application: Application) {
    private val audioTracks: MutableList<AudioTrack> = ArrayList()
    private var volumeLeft = 1.0f
    private var volumeRight = 1.0f
    private var playbackSpeed = 1.0f
    var loop = false
        set(value) {
            field = value
            applyPlayerSettings()
        }
    var isShouldStopPlaylist = false
    var currentErrorTrack: AudioTrack? = null

    /** When true, MediaService stays in foreground during native video (Android 17 AudioHardening). */
    var videoHandoffForegroundRetain = false
    var mediaServiceInForeground = false

    var resetStreamOnPause = true
    var options: Options = Options(application.baseContext)
        set(value) {
            field = value
            mediaServiceRef.get()?.applyNotificationIcon()
        }
    private var mediaControlsListener = WeakReference<MediaControlsListener?>(null)
    private var playbackStatusListener = WeakReference<RmxAudioPlayer?>(null)

    private var player: ExoPlayer? = null
    private var mediaServiceRef = WeakReference<MediaService?>(null)
    private var handlerInstance: AudioPlaylistHandler? = null
    private var seeking = false
    private var sequentialErrors = 0
    private var rmxPlaybackState = RmxPlaybackState.STOPPED
    private var pendingBeginPlayback: PendingBegin? = null

    private data class PendingBegin(val seekPosition: Long, val startPaused: Boolean)

    var currentPosition: Int = INVALID_POSITION

    val currentItem: AudioTrack?
        get() {
            val index = player?.currentMediaItemIndex ?: currentPosition
            return if (index in audioTracks.indices) audioTracks[index] else null
        }

    val isPlaying: Boolean
        get() = player?.isPlaying == true

    val playlistHandler: AudioPlaylistHandler?
        get() {
            if (handlerInstance == null) {
                handlerInstance = AudioPlaylistHandler(application, this)
            }
            return handlerInstance
        }

    fun setOnErrorListener(listener: RmxAudioPlayer?) {
        playbackStatusListener = WeakReference(listener)
    }

    fun setMediaControlsListener(listener: MediaControlsListener?) {
        mediaControlsListener = WeakReference(listener)
    }

    fun setPlaybackStatusListener(listener: RmxAudioPlayer?) {
        playbackStatusListener = WeakReference(listener)
    }

    @Suppress("UNUSED_PARAMETER")
    fun setId(id: Int) {
        // PlaylistCore required a playlist id; Media3 stack does not use it.
    }

    fun attachPlayer(exoPlayer: ExoPlayer) {
        player = exoPlayer
        if (audioTracks.isNotEmpty()) {
            exoPlayer.setMediaItems(audioTracks.map { AudioMediaItemFactory.fromAudioTrack(it) })
            if (currentPosition in audioTracks.indices) {
                exoPlayer.seekTo(currentPosition, 0)
            }
        }
        applyPlayerSettings()
        exoPlayer.addListener(playerListener)
        playbackStatusListener.get()?.onPlayerAttached(exoPlayer)
        pendingBeginPlayback?.let { pending ->
            pendingBeginPlayback = null
            beginPlaybackAt(pending.seekPosition, pending.startPaused)
        }
    }

    fun detachPlayer() {
        player?.removeListener(playerListener)
        playbackStatusListener.get()?.onPlayerDetached()
        player = null
    }

    fun attachService(service: MediaService) {
        mediaServiceRef = WeakReference(service)
    }

    fun getPlayer(): ExoPlayer? = player

    fun isVideoHandoffPrewarmActive(): Boolean = videoHandoffForegroundRetain

    fun getCurrentPlaybackState(): RmxPlaybackState = rmxPlaybackState

    fun getCurrentProgress(): PlaybackProgress? {
        val exoPlayer = player ?: return null
        val duration = exoPlayer.duration.coerceAtLeast(0)
        val position = exoPlayer.currentPosition.coerceAtLeast(0)
        val buffered = if (duration > 0) {
            ((exoPlayer.bufferedPosition.toFloat() / duration) * 100f).toInt().coerceIn(0, 100)
        } else {
            0
        }
        val bufferedFloat = if (duration > 0) {
            (exoPlayer.bufferedPosition.toFloat() / duration).coerceIn(0f, 1f)
        } else {
            0f
        }
        return PlaybackProgress(position, duration, buffered, bufferedFloat)
    }

    val isNextAvailable: Boolean
        get() = PlaylistPlaybackPolicy.nextAvailable(
            player?.currentMediaItemIndex ?: currentPosition,
            audioTracks.size,
            loop
        )

    val isPreviousAvailable: Boolean
        get() = PlaylistPlaybackPolicy.previousAvailable(
            player?.currentMediaItemIndex ?: currentPosition,
            loop,
            audioTracks.size
        )

    fun invokeNext() {
        playlistHandler?.next()
    }

    fun invokePrevious() {
        playlistHandler?.previous()
    }

    fun skipToNext() {
        val exoPlayer = player ?: return
        if (!isNextAvailable) {
            return
        }
        val fromIndex = exoPlayer.currentMediaItemIndex
        // Snapshot the position being left so a later skip back within this session resumes
        // correctly instead of restarting the track from its queue-build-time position.
        audioTracks.getOrNull(fromIndex)?.startPositionMs = exoPlayer.currentPosition
        val targetIndex = PlaylistPlaybackPolicy.nextSkipIndex(fromIndex, audioTracks.size, loop)
        val targetPositionMs = audioTracks.getOrNull(targetIndex)?.startPositionMs ?: 0
        exoPlayer.seekTo(targetIndex, targetPositionMs)
        notifySkipForward(fromIndex)
    }

    fun skipToPrevious() {
        val exoPlayer = player ?: return
        if (!isPreviousAvailable) {
            return
        }
        val fromIndex = exoPlayer.currentMediaItemIndex
        audioTracks.getOrNull(fromIndex)?.startPositionMs = exoPlayer.currentPosition
        val targetIndex = PlaylistPlaybackPolicy.previousSkipIndex(fromIndex, audioTracks.size, loop)
        val targetPositionMs = audioTracks.getOrNull(targetIndex)?.startPositionMs ?: 0
        exoPlayer.seekTo(targetIndex, targetPositionMs)
        notifySkipBack(fromIndex)
    }

    fun setAllItems(items: List<AudioTrack>?, options: PlaylistItemOptions) {
        val seekStart = when {
            options.playFromPosition >= 0 -> options.playFromPosition
            options.retainPosition -> getCurrentProgress()?.position ?: 0
            else -> 0
        }

        clearItems()
        audioTracks.addAll(items.orEmpty())
        player?.setMediaItems(audioTracks.map { AudioMediaItemFactory.fromAudioTrack(it) })
        currentPosition = 0

        options.playFromId?.let { trackId ->
            val position = findTrackPosition(trackId)
            if (position != INVALID_POSITION) {
                currentPosition = position
            }
        }

        beginPlayback(seekStart, options.startPaused)
        mediaServiceRef.get()?.refreshSkipAvailability()
    }

    fun addItem(item: AudioTrack?, index: Int = -1) {
        if (item == null) {
            return
        }
        val countBefore = audioTracks.size
        val insertIndex = if (index >= 0) {
            index.coerceIn(0, audioTracks.size)
        } else {
            audioTracks.size
        }

        if (insertIndex >= audioTracks.size) {
            audioTracks.add(item)
            player?.addMediaItem(AudioMediaItemFactory.fromAudioTrack(item))
        } else {
            audioTracks.add(insertIndex, item)
            player?.addMediaItem(insertIndex, AudioMediaItemFactory.fromAudioTrack(item))
            if (currentPosition >= insertIndex && currentPosition != INVALID_POSITION) {
                currentPosition++
            }
        }

        applyPlayerSettings()
        if (countBefore == 0) {
            currentPosition = 0
            beginPlayback(1, true)
        } else {
            playlistHandler?.updateMediaControls()
        }
    }

    fun moveItem(from: Int, to: Int): Boolean {
        if (from < 0 || from >= audioTracks.size || to < 0 || to >= audioTracks.size) {
            return false
        }
        if (from == to) {
            return true
        }

        val item = audioTracks.removeAt(from)
        audioTracks.add(to, item)
        player?.moveMediaItem(from, to)

        currentPosition = adjustCurrentIndexForMove(currentPosition, from, to, INVALID_POSITION)
        playlistHandler?.updateMediaControls()
        return true
    }

    fun replaceItem(index: Int, itemId: String, replacement: AudioTrack?): AudioTrack? {
        if (replacement == null) {
            return null
        }
        val resolvedIndex = resolveItemPosition(index, itemId)
        if (resolvedIndex < 0 || resolvedIndex >= audioTracks.size) {
            return null
        }

        val existing = audioTracks[resolvedIndex]
        val resolvedReplacement = mergeReplacementTrackId(existing, replacement)

        val isCurrent = existing == currentItem
        val wasPlaying = isPlaying
        val progress = getCurrentProgress()
        val seekPosition: Long = progress?.position ?: 0

        if (isCurrent) {
            player?.pause()
        }

        audioTracks[resolvedIndex] = resolvedReplacement
        player?.replaceMediaItem(resolvedIndex, AudioMediaItemFactory.fromAudioTrack(resolvedReplacement))

        if (isCurrent) {
            beginPlayback(seekPosition, !wasPlaying)
        } else {
            playlistHandler?.updateMediaControls()
        }

        return resolvedReplacement
    }

    fun addAllItems(its: List<AudioTrack>?) {
        val current = currentItem
        its.orEmpty().forEach { track ->
            audioTracks.add(track)
            player?.addMediaItem(AudioMediaItemFactory.fromAudioTrack(track))
        }
        currentPosition = audioTracks.indexOf(current)
        applyPlayerSettings()
    }

    fun removeItem(index: Int, itemId: String): AudioTrack? {
        val wasPlaying = isPlaying
        player?.pause()

        var removingCurrent = false
        val current = currentItem
        val progress = getCurrentProgress()
        val seekPosition: Long = progress?.position ?: 0

        val resolvedIndex = resolveItemPosition(index, itemId)
        var foundItem: AudioTrack? = null
        if (resolvedIndex >= 0) {
            foundItem = audioTracks[resolvedIndex]
            if (foundItem == current) {
                removingCurrent = true
            }
            audioTracks.removeAt(resolvedIndex)
            player?.removeMediaItem(resolvedIndex)
        }

        currentPosition = if (removingCurrent) {
            (player?.currentMediaItemIndex ?: INVALID_POSITION).coerceAtLeast(0)
        } else {
            audioTracks.indexOf(current)
        }

        val seekStart = if (removingCurrent) 0 else seekPosition
        applyPlayerSettings()
        beginPlayback(seekStart, !wasPlaying)
        playlistHandler?.updateMediaControls()
        return foundItem
    }

    fun removeAllItems(its: ArrayList<TrackRemovalItem>): ArrayList<AudioTrack> {
        val removedTracks = ArrayList<AudioTrack>()
        val wasPlaying = isPlaying
        player?.pause()

        var removingCurrent = false
        val current = currentItem
        val progress = getCurrentProgress()
        val seekPosition: Long = progress?.position ?: 0

        val indicesToRemove = its.mapNotNull { item ->
            val resolvedIndex = resolveItemPosition(item.trackIndex, item.trackId)
            if (resolvedIndex >= 0) resolvedIndex else null
        }.distinct().sortedDescending()

        for (resolvedIndex in indicesToRemove) {
            val foundItem = audioTracks[resolvedIndex]
            if (foundItem == current) {
                removingCurrent = true
            }
            removedTracks.add(foundItem)
            audioTracks.removeAt(resolvedIndex)
            player?.removeMediaItem(resolvedIndex)
        }

        currentPosition = if (removingCurrent) {
            (player?.currentMediaItemIndex ?: INVALID_POSITION).coerceAtLeast(0)
        } else {
            audioTracks.indexOf(current)
        }

        val seekStart = if (removingCurrent) 0 else seekPosition
        applyPlayerSettings()
        beginPlayback(seekStart, !wasPlaying)
        return removedTracks
    }

    fun clearItems() {
        player?.stop()
        player?.clearMediaItems()
        audioTracks.clear()
        currentPosition = INVALID_POSITION
        sequentialErrors = 0
        rmxPlaybackState = RmxPlaybackState.STOPPED
    }

    fun getAllItems(): List<AudioTrack> = audioTracks.toList()

    private fun resolveItemPosition(trackIndex: Int, trackId: String): Int {
        return when {
            trackIndex in audioTracks.indices -> trackIndex
            trackId.isNotEmpty() -> findTrackPosition(trackId)
            else -> INVALID_POSITION
        }
    }

    internal fun findTrackPosition(trackId: String): Int =
        audioTracks.indexOfFirst { it.trackId == trackId }

    fun getVolumeLeft(): Float = volumeLeft

    fun getVolumeRight(): Float = volumeRight

    fun setVolume(
        @FloatRange(from = 0.0, to = 1.0) left: Float,
        @FloatRange(from = 0.0, to = 1.0) right: Float
    ) {
        volumeLeft = left
        volumeRight = right
        player?.volume = (left + right) / 2f
    }

    fun getPlaybackSpeed(): Float = playbackSpeed

    fun setPlaybackSpeed(@FloatRange(from = 0.0625, to = 16.0) speed: Float) {
        val validSpeed = speed.coerceIn(0.0625f, 16.0f)
        playbackSpeed = validSpeed
        player?.setPlaybackSpeed(validSpeed)
    }

    fun beginPlayback(@IntRange(from = 0) seekPosition: Long, startPaused: Boolean) {
        if (audioTracks.isEmpty()) {
            return
        }
        beginPlaybackAt(seekPosition, startPaused)
    }

    fun beginPlaybackAt(@IntRange(from = 0) seekPosition: Long, startPaused: Boolean) {
        if (audioTracks.isEmpty()) {
            return
        }
        try {
            ensureServiceStarted()
            val exoPlayer = player
            if (exoPlayer == null) {
                pendingBeginPlayback = PendingBegin(seekPosition, startPaused)
                return
            }
            val index = currentPosition.coerceIn(0, audioTracks.size - 1)
            // Snapshot the position of whatever track is being switched away from — covers the
            // JS "tap a different playlist item" path (playTrackById), not just native skip, so a
            // later native skip back to that track resumes correctly regardless of how it was left.
            val fromIndex = exoPlayer.currentMediaItemIndex
            if (fromIndex != index) {
                audioTracks.getOrNull(fromIndex)?.startPositionMs = exoPlayer.currentPosition
            }
            currentPosition = index
            exoPlayer.repeatMode = PlaylistPlaybackPolicy.repeatMode(loop, audioTracks.size)
            exoPlayer.seekTo(index, seekPosition)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = !startPaused && !videoHandoffForegroundRetain
            applyPlayerSettings()
            if (MediaNotificationPolicy.shouldRequestLegacyAudioFocus()) {
                // Media3 handleAudioFocus owns pause/resume; do not call AudioManager.requestAudioFocus.
            }
            ensureForeground()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "beginPlayback: cannot start MediaService while backgrounded: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "beginPlayback: ${e.message}")
        }
    }

    fun ensureServiceStarted() {
        if (!MediaNotificationPolicy.shouldStartForegroundService(MediaService.instance != null)) {
            return
        }
        val intent = Intent(application, MediaService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                application.startForegroundService(intent)
            } else {
                application.startService(intent)
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "ensureServiceStarted: ${e.message}")
        }
    }

    fun ensureForeground() {
        // Media3 MediaSessionService owns FGS; do not startForeground in parallel.
        mediaServiceRef.get()?.promoteToForeground()
    }

    fun updateForegroundNotification() {
        mediaServiceRef.get()?.updateForegroundNotification()
    }

    private fun applyPlayerSettings() {
        player?.let { exoPlayer ->
            exoPlayer.volume = (volumeLeft + volumeRight) / 2f
            exoPlayer.setPlaybackSpeed(playbackSpeed)
            exoPlayer.repeatMode = PlaylistPlaybackPolicy.repeatMode(loop, audioTracks.size)
        }
    }

    private fun notifySkipForward(fromIndex: Int) {
        val listener = mediaControlsListener.get()
        val item = if (fromIndex in audioTracks.indices) audioTracks[fromIndex] else currentItem
        listener?.onNext(item, fromIndex)
    }

    private fun notifySkipBack(fromIndex: Int) {
        val listener = mediaControlsListener.get()
        val item = if (fromIndex in audioTracks.indices) audioTracks[fromIndex] else currentItem
        listener?.onPrevious(item, fromIndex)
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            rmxPlaybackState = mapPlaybackState(playbackState)
            playbackStatusListener.get()?.onNativePlaybackStateChanged(rmxPlaybackState)
            if (playbackState == Player.STATE_ENDED) {
                handleItemEnded()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            MediaNotificationPolicy.applyRetainPlayWhenReadyGuard(
                playWhenReady,
                videoHandoffForegroundRetain,
                VideoPlayerBridge.hasActivePlayer()
            ) {
                player?.playWhenReady = false
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                sequentialErrors = 0
                rmxPlaybackState = RmxPlaybackState.PLAYING
            } else if (player?.playbackState == Player.STATE_READY) {
                rmxPlaybackState = RmxPlaybackState.PAUSED
            }
            playbackStatusListener.get()?.onNativePlaybackStateChanged(rmxPlaybackState)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val previousIndex = currentPosition
            currentPosition = player?.currentMediaItemIndex ?: currentPosition
            if (PlaylistPlaybackPolicy.isNaturalItemEnd(reason) && previousIndex in audioTracks.indices) {
                notifyItemCompleted(
                    audioTracks[previousIndex],
                    PlaylistPlaybackPolicy.nextAvailable(previousIndex, audioTracks.size, loop)
                )
            }
            playbackStatusListener.get()?.onNativeTrackChanged(
                currentItem,
                isNextAvailable,
                isPreviousAvailable
            )
            mediaServiceRef.get()?.refreshSkipAvailability()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                seeking = true
                rmxPlaybackState = RmxPlaybackState.SEEKING
                playbackStatusListener.get()?.onNativePlaybackStateChanged(rmxPlaybackState)
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            currentErrorTrack = currentItem
            rmxPlaybackState = RmxPlaybackState.ERROR
            playbackStatusListener.get()?.onNativePlayerError(error)
            skipFailedItem()
        }
    }

    /** PlaylistCore moved past a failing item (up to [PlaylistPlaybackPolicy.MAX_SEQUENTIAL_ERRORS] in a row). */
    private fun skipFailedItem() {
        val exoPlayer = player ?: return
        sequentialErrors++
        val failedIndex = exoPlayer.currentMediaItemIndex
        val hasNext = PlaylistPlaybackPolicy.nextAvailable(failedIndex, audioTracks.size, loop)
        if (!PlaylistPlaybackPolicy.shouldSkipAfterError(sequentialErrors, hasNext)) {
            return
        }
        val resume = PlaylistPlaybackPolicy.playWhenReadyAfterErrorSkip(exoPlayer.playWhenReady, failedIndex)
        val targetIndex = PlaylistPlaybackPolicy.errorSkipWindowIndex(failedIndex, audioTracks.size)
        exoPlayer.seekTo(targetIndex, 0)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = resume && !videoHandoffForegroundRetain
    }

    /** Former PlaylistCore order on a natural item end: COMPLETED, PLAYLIST_COMPLETED when last, then STOPPED. */
    private fun notifyItemCompleted(track: AudioTrack, nextAvailable: Boolean) {
        playbackStatusListener.get()?.onNativeItemCompleted(track, nextAvailable)
    }

    private fun mapPlaybackState(playbackState: Int): RmxPlaybackState {
        return when (playbackState) {
            Player.STATE_IDLE -> RmxPlaybackState.STOPPED
            Player.STATE_BUFFERING -> {
                if (player?.isPlaying == true) RmxPlaybackState.PLAYING else RmxPlaybackState.PREPARING
            }
            Player.STATE_READY -> {
                if (seeking) {
                    seeking = false
                    if (player?.isPlaying == true) RmxPlaybackState.PLAYING else RmxPlaybackState.PAUSED
                } else if (player?.isPlaying == true) {
                    RmxPlaybackState.PLAYING
                } else {
                    RmxPlaybackState.PAUSED
                }
            }
            Player.STATE_ENDED -> RmxPlaybackState.STOPPED
            else -> RmxPlaybackState.PREPARING
        }
    }

    private fun handleItemEnded() {
        val track = currentItem
        val nextAvailable = isNextAvailable
        track?.let { notifyItemCompleted(it, nextAvailable) }
        if (!nextAvailable) {
            playbackStatusListener.get()?.onNativePlaylistCompleted()
        }
    }

    companion object {
        const val INVALID_POSITION = -1
        private const val TAG = "PlaylistManager"

        @JvmStatic
        fun adjustCurrentIndexForMove(currentIndex: Int, from: Int, to: Int, invalidPosition: Int): Int {
            if (currentIndex == invalidPosition) {
                return currentIndex
            }
            if (from == currentIndex) {
                return to
            }
            val mid = if (from < currentIndex) currentIndex - 1 else currentIndex
            return if (mid >= to) mid + 1 else mid
        }

        @JvmStatic
        fun mergeReplacementTrackId(existing: AudioTrack, replacement: AudioTrack): AudioTrack {
            val replacementConfig = replacement.toDict()
            if (replacement.trackId.isNullOrEmpty() && existing.trackId != null) {
                replacementConfig.put("trackId", existing.trackId)
            }
            return AudioTrack(replacementConfig)
        }
    }
}
