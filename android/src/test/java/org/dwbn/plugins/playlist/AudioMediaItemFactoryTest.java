package org.dwbn.plugins.playlist;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.MimeTypes;
import org.dwbn.plugins.playlist.data.AudioTrack;
import org.json.JSONObject;
import org.junit.Test;

public class AudioMediaItemFactoryTest {

  @Test
  public void m3u8Path_resolvesToHlsMimeType() {
    assertEquals(
        MimeTypes.APPLICATION_M3U8,
        AudioMediaItemFactory.resolveMimeType("https://example.com/audio.m3u8", null));
    assertEquals(
        MimeTypes.APPLICATION_M3U8,
        AudioMediaItemFactory.resolveMimeType("https://example.com/audio.m3u8?token=abc", null));
  }

  /** Issue #144: an extensionless progressive stream must be sniffed, not parsed as HLS. */
  @Test
  public void extensionlessUrl_resolvesToNoMimeType() {
    assertNull(AudioMediaItemFactory.resolveMimeType("https://example.com/stream/audio", null));
  }

  @Test
  public void progressiveUrls_resolveToNoMimeType() {
    assertNull(AudioMediaItemFactory.resolveMimeType("https://example.com/lecture.mp3", null));
    assertNull(AudioMediaItemFactory.resolveMimeType("https://example.com/lecture.m4a", null));
  }

  /** The old check was a substring match over the whole URL, so a query string could decide. */
  @Test
  public void extensionInQueryString_doesNotDecideMimeType() {
    assertNull(AudioMediaItemFactory.resolveMimeType("https://cdn.example.com/stream?p=.m3u8", null));
    assertNull(AudioMediaItemFactory.resolveMimeType("https://cdn.example.com/stream#.m3u8", null));
  }

  @Test
  public void declaredMimeType_winsOverUrl() {
    assertEquals(
        MimeTypes.APPLICATION_M3U8,
        AudioMediaItemFactory.resolveMimeType("https://example.com/stream/audio", "application/x-mpegURL"));
    assertEquals(
        "audio/mpeg",
        AudioMediaItemFactory.resolveMimeType("https://example.com/audio.m3u8", "audio/mpeg"));
  }

  @Test
  public void blankDeclaredMimeType_isIgnored() {
    assertNull(AudioMediaItemFactory.resolveMimeType("https://example.com/stream/audio", "   "));
    assertEquals(
        MimeTypes.APPLICATION_M3U8,
        AudioMediaItemFactory.resolveMimeType("https://example.com/audio.m3u8", ""));
  }

  @Test
  public void nullOrEmptyUrl_resolvesToNoMimeType() {
    assertNull(AudioMediaItemFactory.resolveMimeType(null, null));
    assertNull(AudioMediaItemFactory.resolveMimeType("", null));
  }

  /** isStream is about pause/resume buffering and must not affect source parsing (issue #144). */
  @Test
  public void isStreamTrue_doesNotAffectMimeResolution() throws Exception {
    JSONObject extensionless = new JSONObject();
    extensionless.put("assetUrl", "https://example.com/stream/audio");
    extensionless.put("isStream", true);
    assertNull(new AudioTrack(extensionless).getMimeType());
    assertNull(
        AudioMediaItemFactory.resolveMimeType(
            new AudioTrack(extensionless).getMediaUrl(), new AudioTrack(extensionless).getMimeType()));

    JSONObject mp3 = new JSONObject();
    mp3.put("assetUrl", "https://example.com/lecture.mp3");
    mp3.put("isStream", true);
    assertNull(
        AudioMediaItemFactory.resolveMimeType(
            new AudioTrack(mp3).getMediaUrl(), new AudioTrack(mp3).getMimeType()));
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
