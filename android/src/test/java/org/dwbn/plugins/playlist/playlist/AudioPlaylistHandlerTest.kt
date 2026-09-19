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

    private class RecordingPlayer(private val isPlaying: Boolean) {
        val setPlayWhenReadyCalls = mutableListOf<Boolean>()
        val calls = mutableListOf<String>()
        val proxy: Player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java)
        ) { _, method, args ->
            when (method.name) {
                "isPlaying" -> isPlaying
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
