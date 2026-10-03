package org.dwbn.plugins.playlist;

import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;

/**
 * Plugin-owned registry for the host's {@link AudioOfflineProvider}. Register it from the app
 * {@code Application} via {@link #setProvider(AudioOfflineProvider)}; this module does not depend
 * on drm-kit.
 */
@UnstableApi
public final class AudioOffline {

  public static final String CODE_NO_PROVIDER = AudioDrm.CODE_NO_PROVIDER;
  public static final String CODE_NOT_SUPPORTED = AudioDrm.CODE_NOT_SUPPORTED;

  private static volatile AudioOfflineProvider provider;

  private AudioOffline() {}

  public static void setProvider(@Nullable AudioOfflineProvider next) {
    provider = next;
  }

  @Nullable
  public static AudioOfflineProvider getProvider() {
    return provider;
  }

  /** Typed error for a provider result: unknown discriminators collapse to {@code unknown}. */
  public static String typedError(@Nullable String error) {
    return AudioDrm.typedError(error);
  }

  /**
   * Provider {@code state} for {@code downloadId}; a throwing provider reads as {@link
   * AudioOfflineProvider#STATE_NONE}.
   */
  public static String stateOf(AudioOfflineProvider registered, String downloadId) {
    try {
      String state = registered.state(downloadId);
      if (
        AudioOfflineProvider.STATE_ACTIVE.equals(state) ||
        AudioOfflineProvider.STATE_EXPIRED.equals(state)
      ) {
        return state;
      }
    } catch (RuntimeException ignored) {
      // fall through
    }
    return AudioOfflineProvider.STATE_NONE;
  }

  /** Open the offline session for a downloaded item, or a failure code. */
  public static AudioDrm.OpenAttempt openOffline(String downloadId) {
    AudioOfflineProvider registered = provider;
    if (registered == null) {
      return AudioDrm.OpenAttempt.noProvider();
    }
    try {
      AudioDrmSession session = registered.openOffline(downloadId);
      if (session == null) {
        return AudioDrm.OpenAttempt.noProvider();
      }
      return AudioDrm.OpenAttempt.ok(session);
    } catch (RuntimeException e) {
      return AudioDrm.OpenAttempt.noProvider();
    }
  }
}
