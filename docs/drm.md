# Protected playback (DRM)

Items can carry an optional `drm` descriptor. On **Android** the plugin plays them with Widevine through a provider the **host app** registers. **iOS and web** refuse `drm` for now (FairPlay is planned).

The plugin deliberately does **not** depend on a DRM library. The DWBN apps use [**drm-kit**](https://github.com/phiamo/drm-kit), which provides the Widevine session (license, token refresh, heartbeat, stream limits) for both this plugin and [`@dwbn/capacitor-video-player`](https://github.com/phiamo/capacitor-video-player).

## 1. Add drm-kit to the host app

`android/app/build.gradle` (the app module — not this plugin):

```gradle
repositories { maven { url 'https://jitpack.io' } }
dependencies { implementation 'com.github.phiamo:drm-kit:0.3.1' }
```

## 2. Register the provider

Once, from your `Application` class. One session class can serve both plugins:

```java
@UnstableApi
public class App extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    AudioDrm.setProvider(MyWidevineSession::new);   // org.dwbn.plugins.playlist.AudioDrm
    VideoDrm.setProvider(MyWidevineSession::new);   // only if you also use the video player
  }
}
```

`MyWidevineSession` implements `AudioDrmSession` (and `VideoDrmSession`) by wrapping drm-kit's `WidevineSession`. The provider receives the item's `drm` object and an `onError` callback. Token URL, heartbeat URL and the bearer token live in your app, never in the item. Pass the token as a getter (`() -> String`) so refreshed tokens reach long sessions. The [drm-kit README](https://github.com/phiamo/drm-kit#usage) has a complete session class.

## 3. Send `drm` with the item

```typescript
await Playlist.setPlaylistItems({
  items: [{
    trackId: 'talk-42',
    assetUrl: 'https://cdn.example/talk-42/audio.mpd',
    title: 'Talk 42',
    artist: 'Speaker',
    album: 'Talks',
    drm: {
      widevineLicenseUrl: 'https://license.example/widevine',
      playbackSessionId: 'ps_123',
      renewalCredential: '…',
      streamLimit: { mode: 'axinom_csl', renewalIntervalSeconds: 300, heartbeatIntervalSeconds: 60 }, // optional, from your playback API
    },
  }],
  options: {},
});
```

Field names match the playback API and the video plugin. `fairplayLicenseUrl` / `fairplayCertificateUrl` may be present and are ignored on Android.

## Behaviour

| Situation | Result |
|---|---|
| Item has no `drm` | Plain playback, as always |
| `drm` set, no provider registered (Android) | `setPlaylistItems` / `addItem` / `replaceItem` / `addAllItems` reject with code `noProvider`; the previous queue stays unchanged |
| `drm` set on iOS or web | Rejected with code `notSupported`, message `DRM not supported on this platform yet` |
| `addAllItems` with one item that cannot open | The whole call fails; the previous queue stays |

## Errors

Provider errors arrive on the existing `status` listener as `RMXSTATUS_ERROR` with `value.error`:

| `error` | Meaning | Suggested handling |
|---|---|---|
| `blockedByStreamLimit` | Concurrent-stream limit reached | Tell the user; **do not auto-retry** |
| `notEntitled` | No right to play this item | Show entitlement message |
| `expired` | License / session expired | Refresh the session and retry once |
| `network` | License or token server unreachable | Retry with backoff |
| `unknown` | Anything else | Log and show a generic error |

For switching between protected audio and video, see [Handoff with DRM](./video-handoff.md#handoff-with-drm).
