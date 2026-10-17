package org.dwbn.plugins.playlist;

import androidx.media3.common.util.UnstableApi;
import com.getcapacitor.JSObject;
import java.util.function.Consumer;

/**
 * Host-registered factory for {@link AudioDrmSession}. Register from the app {@code Application}
 * via {@link AudioDrm#setProvider(AudioDrmProvider)}; do not pin drm-kit in this plugin.
 */
@UnstableApi
public interface AudioDrmProvider {
  /**
   * Open a session for playlist item {@code drm} options. {@code onError} receives exactly one
   * discriminator: {@code blockedByStreamLimit} | {@code notEntitled} | {@code expired} |
   * {@code network} | {@code unknown}.
   */
  AudioDrmSession open(JSObject drm, Consumer<String> onError);
}
