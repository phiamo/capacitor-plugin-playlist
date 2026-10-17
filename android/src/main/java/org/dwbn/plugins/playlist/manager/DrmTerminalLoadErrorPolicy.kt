package org.dwbn.plugins.playlist.manager

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/**
 * ExoPlayer's default load retries, except for terminal DRM failures: retrying those re-hits
 * `/drm-token` and floods 403s. Plain network errors (a dropped segment) still retry.
 */
@UnstableApi
class DrmTerminalLoadErrorPolicy(
    private val drmPlaybackHalted: () -> Boolean,
) : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val terminal = drmPlaybackHalted() ||
            PlaylistPlaybackPolicy.shouldHaltPlaybackForDrmError(
                PlaylistPlaybackPolicy.drmDiscriminatorFromCause(loadErrorInfo.exception)
            )
        return if (terminal) C.TIME_UNSET else super.getRetryDelayMsFor(loadErrorInfo)
    }
}
