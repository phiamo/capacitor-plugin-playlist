package org.dwbn.plugins.playlist.service

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class HandoffForwardingPlayerTest {

    @Test
    fun retain_playAndSetPlayWhenReadyAreNoOps() {
        val recording = RecordingPlayer()
        val forwarding = HandoffForwardingPlayer(recording.proxy) { true }

        forwarding.play()
        forwarding.setPlayWhenReady(true)

        assertEquals(0, recording.playCount)
        assertTrue(recording.setPlayWhenReadyCalls.isEmpty())
    }

    private class RecordingPlayer {
        var playCount = 0
        val setPlayWhenReadyCalls = mutableListOf<Boolean>()
        val proxy: Player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java)
        ) { _, method, args ->
            when (method.name) {
                "play" -> playCount++
                "setPlayWhenReady" -> setPlayWhenReadyCalls.add(args[0] as Boolean)
            }
            defaultValue(method.returnType)
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
