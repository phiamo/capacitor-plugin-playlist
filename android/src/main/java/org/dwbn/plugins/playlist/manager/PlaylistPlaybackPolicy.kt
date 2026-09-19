package org.dwbn.plugins.playlist.manager

import androidx.media3.common.Player

/**
 * Playback decisions that keep the Media3 playlist behaving like the former PlaylistCore stack.
 * Pure functions so JVM tests pin the parity rules.
 */
object PlaylistPlaybackPolicy {
    /** PlaylistCore skipped a failing item up to this many times in a row before giving up. */
    const val MAX_SEQUENTIAL_ERRORS = 3

    /**
     * PlaylistCore never repeated a single item: with one item `next` was unavailable even when
     * looping, so playback stopped at its end. Media3 `REPEAT_MODE_ALL` would restart it forever.
     */
    @JvmStatic
    fun repeatMode(loop: Boolean, itemCount: Int): Int =
        if (loop && itemCount > 1) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF

    @JvmStatic
    fun nextAvailable(index: Int, itemCount: Int, loop: Boolean): Boolean {
        if (itemCount <= 1) {
            return false
        }
        val isAtEnd = index + 1 >= itemCount
        return if (isAtEnd) loop else index + 1 in 0 until itemCount
    }

    /**
     * Window index for [Player.seekTo] after a failed item, matching [PlaylistManager.skipFailedItem]:
     * next item, or wrap to 0 at the end of the list.
     */
    @JvmStatic
    fun errorSkipWindowIndex(failedIndex: Int, itemCount: Int): Int {
        if (itemCount <= 0) {
            return 0
        }
        return if (failedIndex + 1 >= itemCount) 0 else failedIndex + 1
    }

    @JvmStatic
    fun previousAvailable(index: Int, loop: Boolean): Boolean = index > 0 || loop

    /** An item played to its end and the player moved on by itself (not a seek or list change). */
    @JvmStatic
    fun isNaturalItemEnd(transitionReason: Int): Boolean =
        transitionReason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
            transitionReason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT

    @JvmStatic
    fun shouldSkipAfterError(sequentialErrors: Int, nextAvailable: Boolean): Boolean =
        nextAvailable && sequentialErrors <= MAX_SEQUENTIAL_ERRORS

    /**
     * PlaylistCore assumed the user had pressed play for every item after the first, so it kept
     * playing after skipping a broken item unless the failure hit the auto-buffered first item.
     */
    @JvmStatic
    fun playWhenReadyAfterErrorSkip(playWhenReady: Boolean, failedIndex: Int): Boolean =
        playWhenReady || failedIndex > 0

    /** Media3 ignores play on an idle or ended player; it must be prepared or rewound first. */
    @JvmStatic
    fun needsPrepareBeforePlay(playbackState: Int): Boolean = playbackState == Player.STATE_IDLE

    @JvmStatic
    fun needsRewindBeforePlay(playbackState: Int): Boolean = playbackState == Player.STATE_ENDED

    /**
     * `resetStreamOnPause`: resume a paused live stream at the live edge instead of where it paused.
     * VOD streams (`isStream` HLS lectures) are not live and keep their position.
     */
    @JvmStatic
    fun shouldJumpToLiveEdge(resetStreamOnPause: Boolean, isStream: Boolean, isLive: Boolean): Boolean =
        resetStreamOnPause && isStream && isLive
}
