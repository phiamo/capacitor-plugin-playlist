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
     * Rebuffer/position-freeze tolerance (ms) before a track that still claims PLAYING is reported
     * as truly stalled (issue #143: a mid-stream network loss can leave ExoPlayer's own
     * `playbackState` at STATE_READY/isPlaying=true indefinitely, so `currentPosition` failing to
     * advance is the only reliable signal — not a `Player.Listener` state transition). Overridable
     * per-host via `AudioPlayerOptions.stallTimeoutMs` (a reporter's own watchdog used 6s).
     */
    const val STALL_THRESHOLD_MS = 10_000L

    /** True once [msSinceLastProgress] exceeds [thresholdMs] for a normal micro-buffering hiccup. */
    @JvmStatic
    fun hasStalled(msSinceLastProgress: Long, thresholdMs: Long = STALL_THRESHOLD_MS): Boolean =
        msSinceLastProgress >= thresholdMs

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
    fun previousAvailable(index: Int, loop: Boolean, itemCount: Int): Boolean {
        if (itemCount <= 0) {
            return false
        }
        return index > 0 || loop
    }

    /**
     * Window index for [Player.seekTo] when skipping forward, matching [PlaylistManager.skipToNext]:
     * next item, or wrap to 0 at the end of the list when looping.
     */
    @JvmStatic
    fun nextSkipIndex(fromIndex: Int, itemCount: Int, loop: Boolean): Int =
        if (fromIndex + 1 >= itemCount && loop) 0 else fromIndex + 1

    /**
     * Window index for [Player.seekTo] when skipping backward, matching
     * [PlaylistManager.skipToPrevious]: previous item, or wrap to the last item when looping.
     */
    @JvmStatic
    fun previousSkipIndex(fromIndex: Int, itemCount: Int, loop: Boolean): Int =
        if (fromIndex <= 0 && loop) itemCount - 1 else fromIndex - 1

    /** An item played to its end and the player moved on by itself (not a seek or list change). */
    @JvmStatic
    fun isNaturalItemEnd(transitionReason: Int): Boolean =
        transitionReason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
            transitionReason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT

    @JvmStatic
    fun shouldSkipAfterError(sequentialErrors: Int, nextAvailable: Boolean): Boolean =
        nextAvailable && sequentialErrors <= MAX_SEQUENTIAL_ERRORS

    /**
     * Typed DRM failures are terminal. ExoPlayer's default load retry (~1–5s backoff) would
     * re-hit `/drm-token` and flood 403s. Do not skip/prepare/retry the same source.
     */
    @JvmStatic
    fun shouldHaltPlaybackForDrmError(discriminator: String?): Boolean =
        discriminator == "blockedByStreamLimit" ||
            discriminator == "notEntitled" ||
            discriminator == "expired"

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
