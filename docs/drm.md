# Protected playback (DRM)

Items can carry an optional `drm` descriptor. On **Android** and **iOS**, the plugin plays them (Widevine / FairPlay) through a provider the **host app** registers. **Web** refuses `drm` for now.

The plugin deliberately does **not** depend on a DRM library. The DWBN apps use [**drm-kit**](https://github.com/phiamo/drm-kit), which provides the Widevine session (license, token refresh, heartbeat, stream limits) for this plugin and [`@dwbn/capacitor-video-player`](https://github.com/phiamo/capacitor-video-player) on Android, and the FairPlay session (`FairPlaySession`, `AVContentKeySession`-based) for this plugin on iOS.

## 1. Add drm-kit to the host app

`android/app/build.gradle` (the app module — not this plugin):

```gradle
repositories { maven { url 'https://jitpack.io' } }
dependencies { implementation 'com.github.phiamo:drm-kit:0.3.1' }
```

iOS: add `https://github.com/phiamo/drm-kit` as a Swift Package dependency of the **App target** (not this plugin — the plugin never imports drm-kit), pinned to a released tag.

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

iOS: once, from `AppDelegate`'s `application(_:didFinishLaunchingWithOptions:)`. One session type can serve both this plugin and `@dwbn/capacitor-video-player`:

```swift
import PlaylistPlugin
import CapacitorVideoPlayerPlugin // only if you also use the video player

let provider = MyFairPlaySessionProvider()
AudioDrm.setProvider(provider)   // PlaylistPlugin.AudioDrm
VideoDrm.setProvider(provider)   // CapacitorVideoPlayerPlugin.VideoDrm — only if you also use the video player
```

`MyFairPlaySessionProvider` implements the plugin's `AudioDrmProvider` protocol; its `open(_:onError:)` returns a `MyFairPlaySession` implementing `AudioDrmSession` (`attach(to: AVURLAsset)`, `start()`, `release()`) by wrapping drm-kit's `FairPlaySession`. As on Android, token/heartbeat URLs and the bearer token live in your app, never in the `drm` item. To pass the same `provider` instance to both `setProvider` calls above, `MyFairPlaySessionProvider` itself (not just `MyFairPlaySession`) must conform to both this plugin's `AudioDrmProvider` and `@dwbn/capacitor-video-player`'s `VideoDrmProvider` protocols. If `MyFairPlaySession` also conforms to `VideoDrmSession` (identical `attach`/`start`/`release` shape), one concrete session type can serve both — but `MyFairPlaySessionProvider`'s `open(_:onError:)` needs two overloads, one per return type (`VideoDrmSession` / `AudioDrmSession`), both with an identical one-line body. Swift's protocol witness matching does not accept a single covariant-return method (e.g. one declared to return the concrete `MyFairPlaySession` type) for two unrelated protocol requirements sharing the same name.

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
      fairplayLicenseUrl: 'https://license.example/fairplay',
      fairplayCertificateUrl: 'https://license.example/fairplay/cert',
      playbackSessionId: 'ps_123',
      renewalCredential: '…',
      streamLimit: { mode: 'axinom_csl', renewalIntervalSeconds: 300, heartbeatIntervalSeconds: 60 }, // optional, from your playback API
    },
  }],
  options: {},
});
```

Field names match the playback API and the video plugin. `fairplayLicenseUrl` / `fairplayCertificateUrl` are consumed by the iOS FairPlay provider; still ignored on Android. `widevineLicenseUrl` is ignored on iOS.

## Behaviour

| Situation | Result |
|---|---|
| Item has no `drm` | Plain playback, as always |
| `drm` set, no provider registered (Android or iOS) | `setPlaylistItems` / `addItem` / `replaceItem` / `addAllItems` reject with code `noProvider`; the previous queue stays unchanged |
| `drm` set on web | Rejected with code `notSupported`, message `DRM not supported on this platform yet` |
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
