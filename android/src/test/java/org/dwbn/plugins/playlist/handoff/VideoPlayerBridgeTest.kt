package org.dwbn.plugins.playlist.handoff

import androidx.media3.common.Player
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class VideoPlayerBridgeTest {

    @After
    fun tearDown() {
        VideoPlayerBridge.clearForTests()
    }

    @Test
    fun attachAndDetach_trackActivePlayer() {
        val player = dummyPlayer()
        VideoPlayerBridge.attach(player)
        assertSame(player, VideoPlayerBridge.activePlayer())
        assertTrue(VideoPlayerBridge.hasActivePlayer())

        VideoPlayerBridge.detach(player)
        assertFalse(VideoPlayerBridge.hasActivePlayer())
    }

    @Test
    fun detach_ignoresDifferentPlayerInstance() {
        val attached = dummyPlayer()
        val other = dummyPlayer()
        VideoPlayerBridge.attach(attached)
        VideoPlayerBridge.detach(other)
        assertTrue(VideoPlayerBridge.hasActivePlayer())
        VideoPlayerBridge.detach(attached)
        assertFalse(VideoPlayerBridge.hasActivePlayer())
    }

    @Test
    fun attach_notifiesPlaybackChangedListeners() {
        var notifications = 0
        VideoPlayerBridge.addPlaybackChangedListener { notifications++ }
        VideoPlayerBridge.attach(dummyPlayer())
        assertTrue(notifications > 0)
    }

    private fun dummyPlayer(): Player =
        Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
            when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> Player.STATE_IDLE
                java.lang.Long.TYPE -> 0L
                else -> null
            }
        } as Player
}
