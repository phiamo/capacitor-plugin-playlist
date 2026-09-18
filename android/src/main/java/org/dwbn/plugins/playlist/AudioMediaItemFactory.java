package org.dwbn.plugins.playlist;

import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import java.util.Locale;
import org.dwbn.plugins.playlist.data.AudioTrack;

/**
 * Single factory for audio {@link MediaItem} instances. Story 55.4 — no DRM; later epics attach
 * {@link MediaItem.DrmConfiguration} here only.
 */
public final class AudioMediaItemFactory {

  private AudioMediaItemFactory() {}

  public static MediaItem fromAudioTrack(AudioTrack track) {
    return fromUrl(track.getMediaUrl(), track.isStream(), track);
  }

  public static MediaItem fromUrl(String url, boolean isStream) {
    return fromUrl(url, isStream, null);
  }

  private static MediaItem fromUrl(String url, boolean isStream, AudioTrack track) {
    MediaItem.Builder builder = new MediaItem.Builder().setUri(Uri.parse(url));
    if (track != null && track.getTrackId() != null) {
      builder.setMediaId(track.getTrackId());
    }
    if (shouldUseHlsMimeType(url, isStream)) {
      builder.setMimeType(MimeTypes.APPLICATION_M3U8);
    }
    if (track != null) {
      MediaMetadata.Builder metadata = new MediaMetadata.Builder();
      String title = track.getTitle();
      if (title == null || title.isEmpty()) {
        metadata.setTitle("Audio playback");
      } else {
        metadata.setTitle(title);
      }
      String artist = track.getArtist();
      if (artist != null && !artist.isEmpty()) {
        metadata.setArtist(artist);
      }
      String album = track.getAlbum();
      if (album != null && !album.isEmpty()) {
        metadata.setAlbumTitle(album);
      }
      String artwork = artworkUriString(track);
      if (artwork != null) {
        metadata.setArtworkUri(Uri.parse(artwork));
      }
      builder.setMediaMetadata(metadata.build());
    }
    return builder.build();
  }

  /** Artwork URI string passed to {@link MediaMetadata.Builder#setArtworkUri}. */
  static String artworkUriString(AudioTrack track) {
    if (track == null) {
      return null;
    }
    String artwork = track.getThumbnailUrl();
    if (artwork == null || artwork.isEmpty()) {
      return null;
    }
    return artwork;
  }

  public static boolean shouldUseHlsMimeType(String url, boolean isStream) {
    if (url == null || url.isEmpty()) {
      return false;
    }
    String lower = url.toLowerCase(Locale.US);
    if (lower.contains(".m3u8")) {
      return true;
    }
    return isStream && !hasKnownMediaExtension(lower);
  }

  private static boolean hasKnownMediaExtension(String lowerUrl) {
    return lowerUrl.contains(".mp3")
        || lowerUrl.contains(".mp4")
        || lowerUrl.contains(".m4a")
        || lowerUrl.contains(".aac")
        || lowerUrl.contains(".ogg")
        || lowerUrl.contains(".wav")
        || lowerUrl.contains(".flac")
        || lowerUrl.contains(".webm");
  }
}
