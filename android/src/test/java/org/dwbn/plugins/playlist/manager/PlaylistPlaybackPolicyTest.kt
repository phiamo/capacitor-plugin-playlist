package org.dwbn.plugins.playlist.manager

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parity with the former PlaylistCore stack (loop, completion, error skip, play recovery). */
class PlaylistPlaybackPolicyTest {

    @Test
    fun repeatMode_loopWithSeveralItems_repeatsAll() {
        assertEquals(Player.REPEAT_MODE_ALL, PlaylistPlaybackPolicy.repeatMode(loop = true, itemCount = 3))
    }

    @Test
    fun repeatMode_loopWithSingleItem_stopsAtEndLikePlaylistCore() {
        assertEquals(Player.REPEAT_MODE_OFF, PlaylistPlaybackPolicy.repeatMode(loop = true, itemCount = 1))
    }

    @Test
    fun repeatMode_noLoop_isOff() {
        assertEquals(Player.REPEAT_MODE_OFF, PlaylistPlaybackPolicy.repeatMode(loop = false, itemCount = 3))
    }

    @Test
    fun nextAvailable_matchesFormerPlaylistManager() {
        assertFalse(PlaylistPlaybackPolicy.nextAvailable(0, 1, loop = true))
        assertTrue(PlaylistPlaybackPolicy.nextAvailable(0, 3, loop = false))
        assertFalse(PlaylistPlaybackPolicy.nextAvailable(2, 3, loop = false))
        assertTrue(PlaylistPlaybackPolicy.nextAvailable(2, 3, loop = true))
    }

    @Test
    fun previousAvailable_atStartOnlyWhenLooping() {
        assertFalse(PlaylistPlaybackPolicy.previousAvailable(0, loop = false, itemCount = 3))
        assertTrue(PlaylistPlaybackPolicy.previousAvailable(0, loop = true, itemCount = 3))
        assertTrue(PlaylistPlaybackPolicy.previousAvailable(1, loop = false, itemCount = 3))
    }

    @Test
    fun previousAvailable_emptyPlaylist_isFalse() {
        assertFalse(PlaylistPlaybackPolicy.previousAvailable(0, loop = true, itemCount = 0))
    }

    @Test
    fun naturalItemEnd_isAutoOrRepeatTransitionOnly() {
        assertTrue(PlaylistPlaybackPolicy.isNaturalItemEnd(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO))
        assertTrue(PlaylistPlaybackPolicy.isNaturalItemEnd(Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT))
        assertFalse(PlaylistPlaybackPolicy.isNaturalItemEnd(Player.MEDIA_ITEM_TRANSITION_REASON_SEEK))
        assertFalse(PlaylistPlaybackPolicy.isNaturalItemEnd(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED))
    }

    @Test
    fun errorSkip_upToThreeSequentialErrorsWhenNextExists() {
        assertTrue(PlaylistPlaybackPolicy.shouldSkipAfterError(1, nextAvailable = true))
        assertTrue(PlaylistPlaybackPolicy.shouldSkipAfterError(3, nextAvailable = true))
        assertFalse(PlaylistPlaybackPolicy.shouldSkipAfterError(4, nextAvailable = true))
        assertFalse(PlaylistPlaybackPolicy.shouldSkipAfterError(1, nextAvailable = false))
    }

    @Test
    fun errorSkip_keepsPlayingUnlessFirstAutoBufferedItemFailed() {
        assertTrue(PlaylistPlaybackPolicy.playWhenReadyAfterErrorSkip(playWhenReady = true, failedIndex = 0))
        assertFalse(PlaylistPlaybackPolicy.playWhenReadyAfterErrorSkip(playWhenReady = false, failedIndex = 0))
        assertTrue(PlaylistPlaybackPolicy.playWhenReadyAfterErrorSkip(playWhenReady = false, failedIndex = 2))
    }

    @Test
    fun playRecovery_preparesIdleAndRewindsEnded() {
        assertTrue(PlaylistPlaybackPolicy.needsPrepareBeforePlay(Player.STATE_IDLE))
        assertFalse(PlaylistPlaybackPolicy.needsPrepareBeforePlay(Player.STATE_READY))
        assertTrue(PlaylistPlaybackPolicy.needsRewindBeforePlay(Player.STATE_ENDED))
        assertFalse(PlaylistPlaybackPolicy.needsRewindBeforePlay(Player.STATE_BUFFERING))
    }

    @Test
    fun liveEdge_onlyForLiveStreamsWithResetEnabled() {
        assertTrue(PlaylistPlaybackPolicy.shouldJumpToLiveEdge(true, isStream = true, isLive = true))
        assertFalse(PlaylistPlaybackPolicy.shouldJumpToLiveEdge(true, isStream = true, isLive = false))
        assertFalse(PlaylistPlaybackPolicy.shouldJumpToLiveEdge(false, isStream = true, isLive = true))
        assertFalse(PlaylistPlaybackPolicy.shouldJumpToLiveEdge(true, isStream = false, isLive = true))
    }

    @Test
    fun errorSkipWindowIndex_midListAndWrapToStart() {
        assertEquals(2, PlaylistPlaybackPolicy.errorSkipWindowIndex(failedIndex = 1, itemCount = 5))
        assertEquals(0, PlaylistPlaybackPolicy.errorSkipWindowIndex(failedIndex = 4, itemCount = 5))
    }

    @Test
    fun errorSkipWindowIndex_emptyListReturnsZero() {
        assertEquals(0, PlaylistPlaybackPolicy.errorSkipWindowIndex(failedIndex = 0, itemCount = 0))
    }

    @Test
    fun nextAvailable_emptyPlaylist_isFalse() {
        assertFalse(PlaylistPlaybackPolicy.nextAvailable(0, itemCount = 0, loop = true))
    }

    @Test
    fun repeatMode_emptyPlaylist_isOff() {
        assertEquals(Player.REPEAT_MODE_OFF, PlaylistPlaybackPolicy.repeatMode(loop = true, itemCount = 0))
    }

    @Test
    fun nextSkipIndex_midListAdvancesByOne() {
        assertEquals(2, PlaylistPlaybackPolicy.nextSkipIndex(fromIndex = 1, itemCount = 5, loop = false))
    }

    @Test
    fun nextSkipIndex_atEnd_wrapsToStartOnlyWhenLooping() {
        assertEquals(3, PlaylistPlaybackPolicy.nextSkipIndex(fromIndex = 2, itemCount = 3, loop = false))
        assertEquals(0, PlaylistPlaybackPolicy.nextSkipIndex(fromIndex = 2, itemCount = 3, loop = true))
    }

    @Test
    fun previousSkipIndex_midListGoesBackByOne() {
        assertEquals(1, PlaylistPlaybackPolicy.previousSkipIndex(fromIndex = 2, itemCount = 5, loop = false))
    }

    @Test
    fun previousSkipIndex_atStart_wrapsToLastOnlyWhenLooping() {
        assertEquals(-1, PlaylistPlaybackPolicy.previousSkipIndex(fromIndex = 0, itemCount = 3, loop = false))
        assertEquals(2, PlaylistPlaybackPolicy.previousSkipIndex(fromIndex = 0, itemCount = 3, loop = true))
    }
}
