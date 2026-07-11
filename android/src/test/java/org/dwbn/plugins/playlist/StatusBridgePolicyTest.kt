package org.dwbn.plugins.playlist

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusBridgePolicyTest {

    @Test
    fun playbackPosition_isSuppressedWhenWebViewInactive() {
        assertFalse(
            PlaylistPlugin.shouldEmitStatusToBridge(
                RmxAudioStatusMessage.RMXSTATUS_PLAYBACK_POSITION,
                isWebViewActive = false
            )
        )
    }

    @Test
    fun playbackPosition_isEmittedWhenWebViewActive() {
        assertTrue(
            PlaylistPlugin.shouldEmitStatusToBridge(
                RmxAudioStatusMessage.RMXSTATUS_PLAYBACK_POSITION,
                isWebViewActive = true
            )
        )
    }

    @Test
    fun playing_isEmittedWhenWebViewInactive() {
        assertTrue(
            PlaylistPlugin.shouldEmitStatusToBridge(
                RmxAudioStatusMessage.RMXSTATUS_PLAYING,
                isWebViewActive = false
            )
        )
    }

    @Test
    fun playbackPosition_isNotRetained() {
        assertFalse(
            PlaylistPlugin.shouldRetainStatusEvent(
                RmxAudioStatusMessage.RMXSTATUS_PLAYBACK_POSITION
            )
        )
    }

    @Test
    fun playing_isRetained() {
        assertTrue(
            PlaylistPlugin.shouldRetainStatusEvent(
                RmxAudioStatusMessage.RMXSTATUS_PLAYING
            )
        )
    }
}
