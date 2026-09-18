package org.dwbn.plugins.playlist;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
}
