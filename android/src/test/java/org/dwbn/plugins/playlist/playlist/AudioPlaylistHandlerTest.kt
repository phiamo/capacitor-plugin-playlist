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

    private class RecordingPlayer(private val isPlaying: Boolean) {
        val setPlayWhenReadyCalls = mutableListOf<Boolean>()
        val proxy: Player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java)
        ) { _, method, args ->
            when (method.name) {
                "isPlaying" -> isPlaying
                "setPlayWhenReady" -> {
                    setPlayWhenReadyCalls.add(args[0] as Boolean)
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
