package org.dwbn.plugins.playlist;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.dwbn.plugins.playlist.data.AudioTrack;
import org.json.JSONObject;
import org.junit.Test;

public class AudioMediaItemFactoryTest {

  @Test
  public void m3u8Url_shouldUseHlsMimeType() {
    assertTrue(AudioMediaItemFactory.shouldUseHlsMimeType("https://example.com/audio.m3u8", false));
  }

  @Test
  public void extensionlessStream_shouldUseHlsMimeType() {
    assertTrue(AudioMediaItemFactory.shouldUseHlsMimeType("https://example.com/stream/audio", true));
  }

  @Test
  public void mp3Url_shouldNotForceHlsMimeType() {
    assertFalse(AudioMediaItemFactory.shouldUseHlsMimeType("https://example.com/lecture.mp3", false));
  }

  @Test
  public void albumArt_mapsToThumbnailUrlForArtworkUri() throws Exception {
    JSONObject json = new JSONObject();
    json.put("assetUrl", "https://example.com/lecture.mp3");
    json.put("albumArt", "https://example.com/art.jpg");
    json.put("title", "Lecture");
    json.put("artist", "Lama Ole");
    json.put("album", "Awareness");
    AudioTrack track = new AudioTrack(json);

    assertEquals("https://example.com/art.jpg", AudioMediaItemFactory.artworkUriString(track));
    assertTrue(
        org.dwbn.plugins.playlist.service.GlideBitmapLoader.shouldLoadRemoteArtwork(
            AudioMediaItemFactory.artworkUriString(track)));
  }

  @Test
  public void missingAlbumArt_doesNotLoadRemoteArtwork() throws Exception {
    JSONObject json = new JSONObject();
    json.put("assetUrl", "https://example.com/lecture.mp3");
    json.put("title", "Lecture");
    AudioTrack track = new AudioTrack(json);

    assertNull(AudioMediaItemFactory.artworkUriString(track));
    assertFalse(
        org.dwbn.plugins.playlist.service.GlideBitmapLoader.shouldLoadRemoteArtwork(
            AudioMediaItemFactory.artworkUriString(track)));
  }

  @Test
  public void emptyAlbumArt_doesNotLoadRemoteArtwork() throws Exception {
    JSONObject json = new JSONObject();
    json.put("assetUrl", "https://example.com/lecture.mp3");
    json.put("albumArt", "");
    json.put("title", "Lecture");
    AudioTrack track = new AudioTrack(json);

    assertNull(AudioMediaItemFactory.artworkUriString(track));
    assertFalse(org.dwbn.plugins.playlist.service.GlideBitmapLoader.shouldLoadRemoteArtwork(""));
    assertFalse(org.dwbn.plugins.playlist.service.GlideBitmapLoader.shouldLoadRemoteArtwork(null));
    assertNull(AudioMediaItemFactory.artworkUriString(null));
  }
}
