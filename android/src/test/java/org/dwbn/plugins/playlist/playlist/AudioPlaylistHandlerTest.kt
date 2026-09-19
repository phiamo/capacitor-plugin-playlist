package org.dwbn.plugins.playlist.playlist

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.lang.reflect.Proxy

class AudioPlaylistHandlerTest {

    @Test
    fun pauseForVideoHandoff_setsPlayWhenReadyFalseWhenNotPlaying() {
        val recording = RecordingPlayer(isPlaying = false)
        assertFalse(recording.proxy.isPlaying)

        AudioPlaylistHandler.pauseForVideoHandoff(recording.proxy)

        assertEquals(listOf(false), recording.setPlayWhenReadyCalls)
    }

    @Test
    fun audibleResume_playsBeforeSeekSoAudioIsNotSilent() {
        val recording = RecordingPlayer(isPlaying = false)

        AudioPlaylistHandler.applyAudibleResume(recording.proxy, 175_000L)

        assertEquals(listOf("playWhenReady:true", "seek:175000"), recording.calls)
    }

    @Test
    fun audibleResume_atZero_playsWithoutSeek() {
        val recording = RecordingPlayer(isPlaying = false)

        AudioPlaylistHandler.applyAudibleResume(recording.proxy, 0L)

        assertEquals(listOf("playWhenReady:true"), recording.calls)
    }

    @Test
    fun play_onIdlePlayer_preparesBeforePlaying() {
        val recording = RecordingPlayer(isPlaying = false, playbackState = Player.STATE_IDLE)

        AudioPlaylistHandler.startAudible(recording.proxy)

        assertEquals(listOf("prepare", "playWhenReady:true"), recording.calls)
    }

    @Test
    fun play_onEndedPlayer_rewindsCurrentItemBeforePlaying() {
        val recording = RecordingPlayer(isPlaying = false, playbackState = Player.STATE_ENDED, currentIndex = 2)

        AudioPlaylistHandler.startAudible(recording.proxy)

        assertEquals(listOf("seekToDefault:2", "playWhenReady:true"), recording.calls)
    }

    @Test
    fun play_onReadyPlayer_onlyPlays() {
        val recording = RecordingPlayer(isPlaying = false, playbackState = Player.STATE_READY)

        AudioPlaylistHandler.startAudible(recording.proxy)

        assertEquals(listOf("playWhenReady:true"), recording.calls)
    }

    @Test
    fun resetStreamOnPause_liveStream_resumesAtLiveEdge() {
        val recording = RecordingPlayer(isPlaying = false, isLive = true)

        AudioPlaylistHandler.jumpToLiveEdgeIfReset(recording.proxy, true, true)

        assertEquals(listOf("seekToDefault:current"), recording.calls)
    }

    @Test
    fun resetStreamOnPause_vodHlsLecture_keepsPosition() {
        val recording = RecordingPlayer(isPlaying = false, isLive = false)

        AudioPlaylistHandler.jumpToLiveEdgeIfReset(recording.proxy, true, true)

        assertEquals(emptyList<String>(), recording.calls)
    }

    @Test
    fun resetStreamOnPauseOff_liveStream_keepsPosition() {
        val recording = RecordingPlayer(isPlaying = false, isLive = true)

        AudioPlaylistHandler.jumpToLiveEdgeIfReset(recording.proxy, false, true)

        assertEquals(emptyList<String>(), recording.calls)
    }

    private class RecordingPlayer(
        private val isPlaying: Boolean,
        private val playbackState: Int = Player.STATE_READY,
        private val currentIndex: Int = 0,
        private val isLive: Boolean = false
    ) {
        val setPlayWhenReadyCalls = mutableListOf<Boolean>()
        val calls = mutableListOf<String>()
        val proxy: Player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java)
        ) { _, method, args ->
            when (method.name) {
                "isPlaying" -> isPlaying
                "getPlaybackState" -> playbackState
                "getCurrentMediaItemIndex" -> currentIndex
                "isCurrentMediaItemLive" -> isLive
                "prepare" -> {
                    calls.add("prepare")
                    null
                }
                "seekToDefaultPosition" -> {
                    calls.add("seekToDefault:" + (args?.getOrNull(0) ?: "current"))
                    null
                }
                "setPlayWhenReady" -> {
                    val ready = args[0] as Boolean
                    setPlayWhenReadyCalls.add(ready)
                    calls.add("playWhenReady:$ready")
                    null
                }
                "seekTo" -> {
                    val pos = when (args.size) {
                        1 -> args[0] as Long
                        else -> args[1] as Long
                    }
                    calls.add("seek:$pos")
                    null
                }
                else -> defaultValue(method.returnType)
            }
        } as Player

        private fun defaultValue(returnType: Class<*>): Any? = when (returnType) {
            java.lang.Boolean.TYPE, java.lang.Boolean::class.java -> false
            java.lang.Integer.TYPE, java.lang.Integer::class.java -> 0
            java.lang.Long.TYPE, java.lang.Long::class.java -> 0L
            java.lang.Float.TYPE, java.lang.Float::class.java -> 0f
            java.lang.Double.TYPE, java.lang.Double::class.java -> 0.0
            else -> null
        }
    }
}
