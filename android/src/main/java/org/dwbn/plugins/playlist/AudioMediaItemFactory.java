package org.dwbn.plugins.playlist;

import android.net.Uri;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import java.util.Locale;
import org.dwbn.plugins.playlist.data.AudioTrack;

/**
 * Single factory for audio {@link MediaItem} instances. Story 55.4 — no DRM; later epics attach
 * {@link MediaItem.DrmConfiguration} here only.
 */
@OptIn(markerClass = UnstableApi.class)
public final class AudioMediaItemFactory {

  private AudioMediaItemFactory() {}

  public static MediaItem fromAudioTrack(AudioTrack track) {
    return fromUrl(track.getMediaUrl(), track.getMimeType(), track);
  }

  public static MediaItem fromUrl(String url, String declaredMimeType) {
    return fromUrl(url, declaredMimeType, null);
  }

  private static MediaItem fromUrl(String url, String declaredMimeType, AudioTrack track) {
    Uri uri = (url == null || url.isEmpty()) ? Uri.EMPTY : Uri.parse(url);
    MediaItem.Builder builder = new MediaItem.Builder().setUri(uri);
    if (track != null && track.getTrackId() != null) {
      builder.setMediaId(track.getTrackId());
    }
    String mimeType = resolveMimeType(url, declaredMimeType);
    if (mimeType != null) {
      builder.setMimeType(mimeType);
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

  /**
   * MIME type to hand ExoPlayer for {@code url}, or {@code null} to let it sniff the content.
   *
   * <p>Only two things select a type: an explicit {@code mimeType} on the track, and an {@code
   * .m3u8} file extension on the URL's path. Everything else is sniffed, which is what progressive
   * sources need. Notably {@code isStream} does <em>not</em> participate — through 0.14.x it forced
   * {@link MimeTypes#APPLICATION_M3U8} on any extensionless stream URL, so a progressive MP3 stream
   * with no extension (Audius, Icecast, signed CDN links) was parsed as an HLS playlist and failed
   * with "Input does not start with the #EXTM3U header" (issue #144). {@code isStream} is documented
   * as being about pause/resume buffering and is used for exactly that elsewhere.
   */
  static String resolveMimeType(String url, String declaredMimeType) {
    if (declaredMimeType != null && !declaredMimeType.trim().isEmpty()) {
      return declaredMimeType.trim();
    }
    if (pathEndsWith(url, ".m3u8")) {
      return MimeTypes.APPLICATION_M3U8;
    }
    return null;
  }

  /**
   * Whether the URL's <em>path</em> ends in {@code lowerSuffix}, ignoring query and fragment. Plain
   * string work rather than {@link Uri} so this stays testable off-device; the old implementation
   * used {@code String.contains} over the whole URL, which matched extensions appearing only in a
   * query string (e.g. {@code /stream?file=a.m3u8}).
   */
  static boolean pathEndsWith(String url, String lowerSuffix) {
    if (url == null || url.isEmpty()) {
      return false;
    }
    String path = url.toLowerCase(Locale.US);
    int fragment = path.indexOf('#');
    if (fragment >= 0) {
      path = path.substring(0, fragment);
    }
    int query = path.indexOf('?');
    if (query >= 0) {
      path = path.substring(0, query);
    }
    return path.endsWith(lowerSuffix);
  }
}
