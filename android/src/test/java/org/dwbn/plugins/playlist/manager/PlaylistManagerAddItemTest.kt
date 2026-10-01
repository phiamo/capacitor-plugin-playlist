package org.dwbn.plugins.playlist.manager

import org.dwbn.plugins.playlist.data.AudioTrack
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlaylistManagerAddItemTest {

    private fun track(id: String): AudioTrack {
        val json = JSONObject()
        json.put("trackId", id)
        json.put("assetUrl", "https://example.com/$id.mp3")
        json.put("artist", "Artist")
        json.put("album", "Album")
        json.put("title", "Title")
        return AudioTrack(json)
    }

    @Test
    fun addItem_duplicateTrackId_leavesQueueUnchanged() {
        val manager = PlaylistManager(RuntimeEnvironment.getApplication())
        manager.addItem(track("a"))
        manager.addItem(track("b"))

        manager.addItem(track("a"))

        assertEquals(listOf("a", "b"), manager.getAllItems().map { it.trackId })
    }

    @Test
    fun addAllItems_skipsExistingTrackIds() {
        val manager = PlaylistManager(RuntimeEnvironment.getApplication())
        manager.addItem(track("a"))
        manager.addItem(track("b"))

        manager.addAllItems(listOf(track("b"), track("c")))

        assertEquals(listOf("a", "b", "c"), manager.getAllItems().map { it.trackId })
    }
}
