package org.dwbn.plugins.playlist.service

import androidx.media3.common.Player
import org.dwbn.plugins.playlist.handoff.VideoPlayerBridge
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class HandoffForwardingPlayerTest {

    @After
    fun tearDown() {
        VideoPlayerBridge.clearForTests()
    }

    @Test
    fun retain_playAndSetPlayWhenReadyAreNoOps() {
        val recording = RecordingPlayer()
        val forwarding = HandoffForwardingPlayer(recording.proxy, retain = { true })

        forwarding.play()
        forwarding.setPlayWhenReady(true)

        assertEquals(0, recording.playCount)
        assertTrue(recording.setPlayWhenReadyCalls.isEmpty())
    }

    @Test
    fun afterRetainCleared_playReachesPlayer() {
        var retain = true
        val recording = RecordingPlayer()
        val forwarding = HandoffForwardingPlayer(recording.proxy, retain = { retain })

        forwarding.setPlayWhenReady(true)
        assertTrue(recording.setPlayWhenReadyCalls.isEmpty())

        retain = false
        forwarding.setPlayWhenReady(true)

        assertEquals(listOf(true), recording.setPlayWhenReadyCalls)
    }

    @Test
    fun seekToNextMediaItem_delegatesToCallback() {
        var skipNextCount = 0
        val recording = RecordingPlayer()
        val forwarding = HandoffForwardingPlayer(
            recording.proxy,
            retain = { false },
            onSkipToNext = { skipNextCount++ }
        )

        forwarding.seekToNextMediaItem()

        assertEquals(1, skipNextCount)
        assertEquals(0, recording.seekToNextCount)
    }

    @Test
    fun seekToPreviousMediaItem_delegatesToCallback() {
        var skipPreviousCount = 0
        val recording = RecordingPlayer()
        val forwarding = HandoffForwardingPlayer(
            recording.proxy,
            retain = { false },
            onSkipToPrevious = { skipPreviousCount++ }
        )

        forwarding.seekToPreviousMediaItem()

        assertEquals(1, skipPreviousCount)
        assertEquals(0, recording.seekToPreviousCount)
    }

    @Test
    fun retainWithVideoAttached_playReachesVideoPlayer() {
        val audio = RecordingPlayer()
        val video = RecordingPlayer()
        VideoPlayerBridge.attach(video.proxy)
        val forwarding = HandoffForwardingPlayer(audio.proxy, retain = { true })

        forwarding.play()

        assertEquals(0, audio.playCount)
        assertEquals(1, video.playCount)
    }

    @Test
    fun retainWithVideoAttached_isPlayingReflectsVideo() {
        val audio = RecordingPlayer()
        val video = RecordingPlayer(playWhenReady = true, playbackState = Player.STATE_READY)
        VideoPlayerBridge.attach(video.proxy)
        val forwarding = HandoffForwardingPlayer(audio.proxy, retain = { true })

        assertTrue(forwarding.isPlaying())
    }

    @Test
    fun retainWithVideoAttached_notPlayingWhileBufferingEvenIfPlayWhenReady() {
        val audio = RecordingPlayer()
        val video = RecordingPlayer(playWhenReady = true, playbackState = Player.STATE_BUFFERING)
        VideoPlayerBridge.attach(video.proxy)
        val forwarding = HandoffForwardingPlayer(audio.proxy, retain = { true })

        assertFalse(forwarding.isPlaying())
    }

    @Test
    fun retainWithVideoAttached_pauseAndSeekHitVideo() {
        val audio = RecordingPlayer()
        val video = RecordingPlayer()
        VideoPlayerBridge.attach(video.proxy)
        val forwarding = HandoffForwardingPlayer(audio.proxy, retain = { true })

        forwarding.pause()
        forwarding.setPlayWhenReady(false)
        forwarding.seekTo(99_000L)

        assertEquals(1, video.pauseCount)
        assertEquals(listOf(false), video.setPlayWhenReadyCalls)
        assertEquals(listOf(99_000L), video.seekToPositionMsCalls)
        assertEquals(0, audio.pauseCount)
    }

    @Test
    fun retainWithVideoAttached_positionAndDurationFromVideo() {
        val audio = RecordingPlayer(currentPosition = 1L, duration = 2L, contentPosition = 3L)
        val video = RecordingPlayer(currentPosition = 50L, duration = 100L, contentPosition = 55L)
        VideoPlayerBridge.attach(video.proxy)
        val forwarding = HandoffForwardingPlayer(audio.proxy, retain = { true })

        assertEquals(50L, forwarding.getCurrentPosition())
        assertEquals(100L, forwarding.getDuration())
        assertEquals(55L, forwarding.getContentPosition())
    }

    @Test
    fun retainWithVideoAttached_skipMediaItemsAreNoOps() {
        var skipNextCount = 0
        var skipPreviousCount = 0
        val audio = RecordingPlayer()
        val video = RecordingPlayer()
        VideoPlayerBridge.attach(video.proxy)
        val forwarding = HandoffForwardingPlayer(
            audio.proxy,
            retain = { true },
            onSkipToNext = { skipNextCount++ },
            onSkipToPrevious = { skipPreviousCount++ }
        )

        forwarding.seekToNextMediaItem()
        forwarding.seekToPreviousMediaItem()

        assertEquals(0, skipNextCount)
        assertEquals(0, skipPreviousCount)
        assertEquals(0, video.seekToNextCount)
    }

    @Test
    fun afterVideoDetach_skipCallbacksWorkAgainWhileRetainStillOn() {
        var skipNextCount = 0
        val audio = RecordingPlayer()
        val video = RecordingPlayer()
        VideoPlayerBridge.attach(video.proxy)
        val forwarding = HandoffForwardingPlayer(
            audio.proxy,
            retain = { true },
            onSkipToNext = { skipNextCount++ }
        )

        forwarding.seekToNextMediaItem()
        assertEquals(0, skipNextCount)

        VideoPlayerBridge.detach(video.proxy)
        forwarding.seekToNextMediaItem()
        assertEquals(1, skipNextCount)
    }

    @Test
    fun afterVideoDetach_playStillIgnoredWhileRetainOn() {
        val audio = RecordingPlayer()
        val video = RecordingPlayer()
        VideoPlayerBridge.attach(video.proxy)
        val forwarding = HandoffForwardingPlayer(audio.proxy, retain = { true })

        VideoPlayerBridge.detach(video.proxy)
        forwarding.play()

        assertEquals(0, audio.playCount)
        assertEquals(0, video.playCount)
    }

    @Test
    fun retainWithVideoAttached_notifiesSessionWhenVideoAttaches() {
        val audio = RecordingPlayer()
        val video = RecordingPlayer(playWhenReady = true, playbackState = Player.STATE_READY)
        val forwarding = HandoffForwardingPlayer(audio.proxy, retain = { true })
        var playingUpdates = 0
        forwarding.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    playingUpdates++
                }
            }
        })

        VideoPlayerBridge.attach(video.proxy)

        assertTrue(playingUpdates > 0)
    }

    private class RecordingPlayer(
        private var playWhenReady: Boolean = false,
        private val playbackState: Int = Player.STATE_IDLE,
        private val isPlayingValue: Boolean = false,
        private val currentPosition: Long = 0L,
        private val duration: Long = 0L,
        private val contentPosition: Long = 0L
    ) {
        var playCount = 0
        var pauseCount = 0
        var seekToNextCount = 0
        var seekToPreviousCount = 0
        val seekToPositionMsCalls = mutableListOf<Long>()
        val setPlayWhenReadyCalls = mutableListOf<Boolean>()
        val proxy: Player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java)
        ) { _, method, args ->
            when (method.name) {
                "play" -> {
                    playCount++
                    null
                }
                "pause" -> {
                    pauseCount++
                    null
                }
                "setPlayWhenReady" -> {
                    val ready = args[0] as Boolean
                    playWhenReady = ready
                    setPlayWhenReadyCalls.add(ready)
                    null
                }
                "seekTo" -> when (args?.size) {
                    1 -> {
                        seekToPositionMsCalls.add(args[0] as Long)
                        null
                    }
                    else -> defaultValue(method.returnType)
                }
                "seekToNextMediaItem" -> {
                    seekToNextCount++
                    null
                }
                "seekToPreviousMediaItem" -> {
                    seekToPreviousCount++
                    null
                }
                "isPlaying", "getIsPlaying" -> isPlayingValue
                "isPlayWhenReady", "getPlayWhenReady" -> playWhenReady
                "getPlaybackState" -> playbackState
                "getCurrentPosition" -> currentPosition
                "getDuration" -> duration
                "getContentPosition" -> contentPosition
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
