package org.dwbn.plugins.playlist;

import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import com.getcapacitor.JSObject;

/**
 * Host-registered owner of offline licences and renewal (Story 59.4). The host wraps
 * drm-kit {@code OfflineLicenseManager}; this plugin never imports drm-kit.
 *
 * <p>Error results are the five {@link AudioDrm} discriminators plus {@link
 * AudioDrm#ERROR_OFFLINE_DEVICE_LIMIT}; anything else is coerced to {@code unknown}. Methods may
 * block and are never called on the main thread, except {@link #state}, {@link #needsRenewal} and
 * {@link #openOffline}, which must be quick and local.
 */
@UnstableApi
public interface AudioOfflineProvider {
  /** {@link #state} value: no licence is held for the download. */
  String STATE_NONE = "none";
  /** {@link #state} value: a usable licence is held. */
  String STATE_ACTIVE = "active";
  /** {@link #state} value: the licence has expired and must be renewed. */
  String STATE_EXPIRED = "expired";

  /**
   * Acquire and persist an offline licence for {@code downloadId}. Called after HLS preparation
   * and before any segment is fetched. {@code format} is the first track format carrying {@code
   * drmInitData}; {@code drm} is the descriptor passed to {@code startDownload}.
   *
   * @return {@code null} on success, otherwise an error discriminator (download then fails)
   */
  @Nullable
  String acquire(String downloadId, Format format, JSObject drm);

  /** Whether the stored licence should be renewed soon. Quick and local. */
  boolean needsRenewal(String downloadId);

  /**
   * Real licence expiry (epoch ms) from the host/drm-kit. {@code null} when unknown.
   * Called from {@code listDownloads} / acquire / renew off the main thread.
   */
  @Nullable
  Long expiresAt(String downloadId);

  /**
   * Renew the licence of a downloaded item while online.
   *
   * @return {@code null} on success, otherwise an error discriminator
   */
  @Nullable
  String renew(String downloadId, @Nullable JSObject drm);

  /** Drop the stored licence of {@code downloadId}. Failures are ignored. */
  void release(String downloadId);

  /** One of {@link #STATE_NONE}, {@link #STATE_ACTIVE}, {@link #STATE_EXPIRED}. Quick and local. */
  String state(String downloadId);

  /** Session whose {@code DrmSessionManager} replays the stored offline licence without network. */
  AudioDrmSession openOffline(String downloadId);
}
