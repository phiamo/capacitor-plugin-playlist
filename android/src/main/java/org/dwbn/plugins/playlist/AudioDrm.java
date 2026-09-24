package org.dwbn.plugins.playlist;

import androidx.media3.common.util.UnstableApi;
import com.getcapacitor.JSObject;
import java.util.function.Consumer;

/**
 * Plugin-owned DRM provider registry. The host app registers drm-kit in Story 57.6; this module
 * does not depend on drm-kit.
 */
@UnstableApi
public final class AudioDrm {

  public static final String CODE_NO_PROVIDER = "noProvider";
  public static final String CODE_NOT_SUPPORTED = "notSupported";
  public static final String NOT_SUPPORTED_MESSAGE = "DRM not supported on this platform yet";

  public static final String ERROR_BLOCKED_BY_STREAM_LIMIT = "blockedByStreamLimit";
  public static final String ERROR_NOT_ENTITLED = "notEntitled";
  public static final String ERROR_EXPIRED = "expired";
  public static final String ERROR_NETWORK = "network";
  public static final String ERROR_UNKNOWN = "unknown";

  private static volatile AudioDrmProvider provider;

  private AudioDrm() {}

  public static void setProvider(AudioDrmProvider next) {
    provider = next;
  }

  public static AudioDrmProvider getProvider() {
    return provider;
  }

  /**
   * Open a session when {@code drm} is set. Missing {@code drm} is plain playback. Missing provider
   * with {@code drm} set is {@link #CODE_NO_PROVIDER}.
   */
  public static OpenAttempt open(JSObject drm, Consumer<String> onError) {
    if (drm == null) {
      return OpenAttempt.none();
    }
    AudioDrmProvider registered = getProvider();
    if (registered == null) {
      return OpenAttempt.noProvider();
    }
    Consumer<String> typedOnError = error -> {
      if (onError != null) {
        onError.accept(typedError(error));
      }
    };
    try {
      AudioDrmSession session = registered.open(drm, typedOnError);
      if (session == null) {
        return OpenAttempt.noProvider();
      }
      return OpenAttempt.ok(session);
    } catch (RuntimeException e) {
      typedOnError.accept(ERROR_UNKNOWN);
      return OpenAttempt.noProvider();
    }
  }

  public static String typedError(String error) {
    if (
      ERROR_BLOCKED_BY_STREAM_LIMIT.equals(error) ||
      ERROR_NOT_ENTITLED.equals(error) ||
      ERROR_EXPIRED.equals(error) ||
      ERROR_NETWORK.equals(error) ||
      ERROR_UNKNOWN.equals(error)
    ) {
      return error;
    }
    return ERROR_UNKNOWN;
  }

  public static final class OpenAttempt {

    public final AudioDrmSession session;
    public final String failureCode;

    private OpenAttempt(AudioDrmSession session, String failureCode) {
      this.session = session;
      this.failureCode = failureCode;
    }

    public static OpenAttempt none() {
      return new OpenAttempt(null, null);
    }

    public static OpenAttempt noProvider() {
      return new OpenAttempt(null, CODE_NO_PROVIDER);
    }

    public static OpenAttempt ok(AudioDrmSession session) {
      return new OpenAttempt(session, null);
    }
  }
}
