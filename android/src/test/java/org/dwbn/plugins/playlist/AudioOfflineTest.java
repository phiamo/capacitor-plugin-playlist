package org.dwbn.plugins.playlist;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import com.getcapacitor.JSObject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 24)
@UnstableApi
public class AudioOfflineTest {

  @After
  public void tearDown() {
    AudioOffline.setProvider(null);
  }

  private static final class Stub implements AudioOfflineProvider {

    String state = STATE_ACTIVE;
    boolean throwOnState;
    AudioDrmSession session;

    @Override
    public String acquire(String downloadId, Format format, JSObject drm) {
      return null;
    }

    @Override
    public boolean needsRenewal(String downloadId) {
      return false;
    }

    @Override
    public Long expiresAt(String downloadId) {
      return null;
    }

    @Override
    public String renew(String downloadId, JSObject drm) {
      return null;
    }

    @Override
    public void release(String downloadId) {}

    @Override
    public String state(String downloadId) {
      if (throwOnState) {
        throw new IllegalStateException("state");
      }
      return state;
    }

    @Override
    public AudioDrmSession openOffline(String downloadId) {
      return session;
    }
  }

  @Test
  public void typedError_acceptsOfflineDeviceLimit_andCollapsesTheRest() {
    assertEquals(AudioDrm.ERROR_OFFLINE_DEVICE_LIMIT, AudioDrm.typedError("offlineDeviceLimit"));
    assertEquals(AudioDrm.ERROR_NOT_ENTITLED, AudioOffline.typedError("notEntitled"));
    assertEquals(AudioDrm.ERROR_UNKNOWN, AudioOffline.typedError("licenseDenied"));
    assertEquals(AudioDrm.ERROR_UNKNOWN, AudioOffline.typedError(null));
  }

  @Test
  public void stateOf_normalisesAndSwallowsFailures() {
    Stub stub = new Stub();
    assertEquals(AudioOfflineProvider.STATE_ACTIVE, AudioOffline.stateOf(stub, "d"));
    stub.state = AudioOfflineProvider.STATE_EXPIRED;
    assertEquals(AudioOfflineProvider.STATE_EXPIRED, AudioOffline.stateOf(stub, "d"));
    stub.state = "weird";
    assertEquals(AudioOfflineProvider.STATE_NONE, AudioOffline.stateOf(stub, "d"));
    stub.throwOnState = true;
    assertEquals(AudioOfflineProvider.STATE_NONE, AudioOffline.stateOf(stub, "d"));
  }

  @Test
  public void openOffline_withoutProvider_isNoProvider() {
    AudioDrm.OpenAttempt attempt = AudioOffline.openOffline("d");
    assertEquals(AudioDrm.CODE_NO_PROVIDER, attempt.failureCode);
    assertNull(attempt.session);
  }

  @Test
  public void openOffline_nullSession_isNoProvider() {
    AudioOffline.setProvider(new Stub());
    assertEquals(AudioDrm.CODE_NO_PROVIDER, AudioOffline.openOffline("d").failureCode);
  }

  @Test
  public void openOffline_returnsProviderSession() {
    Stub stub = new Stub();
    stub.session = new AudioDrmTest.FakeAudioDrmSession();
    AudioOffline.setProvider(stub);
    AudioDrm.OpenAttempt attempt = AudioOffline.openOffline("d");
    assertNull(attempt.failureCode);
    assertSame(stub.session, attempt.session);
  }
}
