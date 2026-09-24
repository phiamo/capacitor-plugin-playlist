package org.dwbn.plugins.playlist;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import com.getcapacitor.JSObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 24)
@UnstableApi
public class AudioDrmTest {

  @After
  public void tearDown() {
    AudioDrm.setProvider(null);
  }

  @Test
  public void getProvider_defaultIsNull() {
    assertNull(AudioDrm.getProvider());
  }

  @Test
  public void open_withoutDrm_isPlainPlayback() {
    AudioDrm.OpenAttempt attempt = AudioDrm.open(null, error -> {});
    assertNull(attempt.failureCode);
    assertNull(attempt.session);
  }

  @Test
  public void open_drmWithoutProvider_isNoProvider() {
    JSObject drm = new JSObject();
    drm.put("widevineLicenseUrl", "https://license.example/wv");
    AudioDrm.OpenAttempt attempt = AudioDrm.open(drm, error -> {});
    assertEquals(AudioDrm.CODE_NO_PROVIDER, attempt.failureCode);
    assertNull(attempt.session);
  }

  @Test
  public void open_withProvider_returnsSession() {
    FakeAudioDrmSession session = new FakeAudioDrmSession();
    AudioDrm.setProvider((drm, onError) -> session);
    JSObject drm = new JSObject();
    drm.put("playbackSessionId", "sess-1");
    AudioDrm.OpenAttempt attempt = AudioDrm.open(drm, error -> {});
    assertNull(attempt.failureCode);
    assertSame(session, attempt.session);
  }

  @Test
  public void open_nullSession_isNoProvider() {
    AudioDrm.setProvider((drm, onError) -> null);
    AudioDrm.OpenAttempt attempt = AudioDrm.open(new JSObject(), error -> {});
    assertEquals(AudioDrm.CODE_NO_PROVIDER, attempt.failureCode);
    assertNull(attempt.session);
  }

  @Test
  public void open_providerThrows_onErrorUnknownAndNoSession() {
    List<String> errors = new ArrayList<>();
    AudioDrm.setProvider((drm, onError) -> {
      throw new IllegalStateException("open failed");
    });
    AudioDrm.OpenAttempt attempt = AudioDrm.open(new JSObject(), errors::add);
    assertEquals(AudioDrm.CODE_NO_PROVIDER, attempt.failureCode);
    assertNull(attempt.session);
    assertEquals(1, errors.size());
    assertEquals(AudioDrm.ERROR_UNKNOWN, errors.get(0));
  }

  @Test
  public void typedError_passesKnownDiscriminators() {
    assertEquals(AudioDrm.ERROR_BLOCKED_BY_STREAM_LIMIT, AudioDrm.typedError("blockedByStreamLimit"));
    assertEquals(AudioDrm.ERROR_NOT_ENTITLED, AudioDrm.typedError("notEntitled"));
    assertEquals(AudioDrm.ERROR_EXPIRED, AudioDrm.typedError("expired"));
    assertEquals(AudioDrm.ERROR_NETWORK, AudioDrm.typedError("network"));
    assertEquals(AudioDrm.ERROR_UNKNOWN, AudioDrm.typedError("unknown"));
  }

  @Test
  public void typedError_coercesUnknownDiscriminator() {
    assertEquals(AudioDrm.ERROR_UNKNOWN, AudioDrm.typedError("licenseDenied"));
  }

  @Test
  public void providerOnError_emitsTypedPayload() {
    List<String> errors = new ArrayList<>();
    AudioDrm.setProvider(
      (drm, onError) -> {
        FakeAudioDrmSession session = new FakeAudioDrmSession();
        session.onError = onError;
        return session;
      }
    );
    JSObject drm = new JSObject();
    AudioDrm.OpenAttempt attempt = AudioDrm.open(drm, errors::add);
    FakeAudioDrmSession session = (FakeAudioDrmSession) attempt.session;
    session.onError.accept(AudioDrm.ERROR_NOT_ENTITLED);
    assertEquals(1, errors.size());
    assertEquals("notEntitled", errors.get(0));
  }

  static final class FakeAudioDrmSession implements AudioDrmSession {

    Consumer<String> onError;

    @Override
    public void applyDrm(MediaItem.Builder builder) {
      builder.setDrmConfiguration(new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID).build());
    }

    @Override
    public DrmSessionManager getDrmSessionManager() {
      return DrmSessionManager.DRM_UNSUPPORTED;
    }

    @Override
    public void start() {}

    @Override
    public void release() {}
  }
}
