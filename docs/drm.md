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

## Offline downloads (Android and iOS)

Protected HLS audio (`audio.m3u8`) can be downloaded and played offline, in the background and on the lock screen. **Android** uses Widevine via Media3 `DownloadManager` / `DownloadService`; **iOS** uses FairPlay via `AVAssetDownloadURLSession`. The plugin owns the media download and local playback. **Licences and renewal are delegated to a host `AudioOfflineProvider`**; the plugin never imports drm-kit. Web rejects `notSupported`.

### Register the offline provider

**Android** — once, from your `Application` class:

```java
@UnstableApi
public class App extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    AudioOffline.setProvider(new MyOfflineProvider(context));   // org.dwbn.plugins.playlist.AudioOffline
  }
}
```

`AudioOfflineProvider` methods (errors are the five `AudioDrm` discriminators plus `offlineDeviceLimit`; return `null` for success):

| Method | Purpose |
|---|---|
| `String acquire(downloadId, Format, JSObject drm)` | Fetch + persist the offline licence. Runs on a background thread, **before any segment is fetched**. A refusal fails the download without media traffic. |
| `boolean needsRenewal(downloadId)` | Quick, local. Reported in `listDownloads`. |
| `String renew(downloadId, JSObject drm)` | Renew online. |
| `void release(downloadId)` | Drop the stored licence. Failures are ignored. |
| `String state(downloadId)` | `none` \| `active` \| `expired`. Quick, local. |
| `AudioDrmSession openOffline(downloadId)` | Session with the offline `DrmSessionManager` + `DrmConfiguration` (stored `keySetId`). |

Adapter sketch over drm-kit's `OfflineLicenseManager`:

```kotlin
class MyOfflineProvider(private val kit: OfflineLicenseManager, private val endpoints: (JSObject) -> OfflineEndpoints) :
    AudioOfflineProvider {
    override fun acquire(downloadId: String, format: Format, drm: JSObject): String? =
        kit.acquire(downloadId, format, endpoints(drm)).error?.name           // e.g. "notEntitled", "offlineDeviceLimit"
    override fun needsRenewal(downloadId: String) = kit.needsRenewal(downloadId)
    override fun renew(downloadId: String, drm: JSObject?): String? = kit.renew(downloadId).error?.name
    override fun release(downloadId: String) = kit.release(downloadId)
    override fun state(downloadId: String) = kit.state(downloadId).name.lowercase()  // none | active | expired
    override fun openOffline(downloadId: String): AudioDrmSession = MyOfflineSession(kit, downloadId) // wraps createOfflineDrmSessionManager / offlineDrmConfiguration
}
```

**iOS** — once, from `AppDelegate`'s `application(_:didFinishLaunchingWithOptions:)`:

```swift
import PlaylistPlugin

AudioOffline.setProvider(MyFairPlayOfflineProvider())
```

| Method | Purpose |
|---|---|
| `acquire(downloadId, keyIdentifier, drm) -> String?` | Fetch + persist the persistable FairPlay key. Runs off the main thread, **before any media is fetched**. `keyIdentifier` is the playlist's `skd://` URI. |
| `needsRenewal(downloadId)` | Quick, local. |
| `renew(downloadId, drm) -> String?` | Renew online. |
| `release(downloadId)` | Drop the stored licence. Failures are ignored. |
| `state(downloadId)` | `none` \| `active` \| `expired`. Quick, local. |
| `attachOffline(downloadId, asset)` | Answer the local asset's key requests from the stored persistable key. Call before the player item loads. |

Adapter sketch over drm-kit's `FairPlayOfflineLicenseManager` (host wiring is Story 59.6):

```swift
final class MyFairPlayOfflineProvider: AudioOfflineProvider {
    let kit: FairPlayOfflineLicenseManager
    func acquire(downloadId: String, keyIdentifier: String, drm: JSObject) -> String? {
        // kit.acquire(downloadId:keyIdentifier:endpoints:) — map result.error?.rawValue
        nil
    }
    func needsRenewal(downloadId: String) -> Bool { kit.needsRenewal(downloadId: downloadId) }
    func renew(downloadId: String, drm: JSObject?) -> String? { /* kit.renew */ nil }
    func release(downloadId: String) { Task { await kit.release(downloadId: downloadId) } }
    func state(downloadId: String) -> String { kit.state(downloadId: downloadId).rawValue } // none | active | expired
    func attachOffline(downloadId: String, asset: AVURLAsset) {
        kit.addOfflinePlaybackRecipient(asset) // before AVPlayerItem is built
    }
}
```

Map drm-kit's error names to the discriminators if they differ; anything unknown becomes `unknown`.

Also forward the plugin-owned background session (Story 59.6 wires this in the host app):

```swift
func application(_ application: UIApplication,
                 handleEventsForBackgroundURLSession identifier: String,
                 completionHandler: @escaping () -> Void) {
    AudioOffline.handleEventsForBackgroundURLSession(identifier, completionHandler: completionHandler)
}
```

The session identifier is `AudioOffline.backgroundSessionIdentifier` (`org.dwbn.plugins.playlist.offline`).

### API

```typescript
await Playlist.addListener('download', ({ downloadId, state, progress, error }) => { /* queued | downloading | completed | failed | expired */ });

await Playlist.startDownload({ downloadId: 'dl-42', url: signedAudioM3u8Url, drm: { playbackSessionId: 'ps_123' } });
const { downloads } = await Playlist.listDownloads();   // [{ downloadId, state, progress, expiresAt, needsRenewal }]
await Playlist.renewDownload({ downloadId: 'dl-42', drm: { playbackSessionId: 'ps_124' } });
await Playlist.cancelDownload({ downloadId: 'dl-42' });  // non-completed only; no-op once completed
await Playlist.deleteDownload({ downloadId: 'dl-42' });  // any state

// Offline playback: no network needed, assetUrl is not used.
await Playlist.addItem({ item: { trackId: 'talk-42', downloadId: 'dl-42', assetUrl: '', title: 'Talk 42', artist: '', album: '' } });
```

- `startDownload` resolves once the request is accepted; everything else is reported on the `download` listener. It rejects `noProvider` when no provider is registered. Progress ticks are not retained; terminal states (`queued` / `completed` / `failed` / `expired`) are.
- **Android** order: prepare HLS → first track `Format` with `drmInitData` → `provider.acquire` → enqueue segments. The playlist must therefore expose the PSSH to the downloader, via `#EXT-X-SESSION-KEY` in the multivariant playlist (the DWBN backend publishes the Widevine key there for every audio package since Story 59.4a; older packages need the `app:drm:backfill-session-key` backfill); without a format carrying `drmInitData` the download fails with `unknown`.
- **iOS** order: read FairPlay `skd://` from the remote HLS → `provider.acquire(downloadId, keyIdentifier, drm)` off the main thread → only then start `AVAssetDownloadURLSession.makeAssetDownloadTask(downloadConfiguration:)`. No identifier → `unknown`. Prepare I/O → `network`. FairPlay does not run in Simulator.
- A segment `403` (signed URL expired) ends in `failed`. Call `startDownload` again with a fresh URL: **Android** reuses cached segments (the cache key ignores the URL query); **iOS** starts a new task and discards partial media.
- `expiresAt` (epoch ms) is the last successful acquire/renew **+ 27 days**. The exact CDM remaining time is not read.
- A refused `renewDownload` rejects with the typed error and emits `expired` only when the provider's state for that download is actually `expired`.
- Playing an item with `downloadId` whose provider `state` is `expired` emits the existing `expired` status error and does not play. Streaming items are unchanged. Queue mutations of `downloadId` items without a provider reject `noProvider` and leave the queue unchanged.
- The plugin creates no plaintext audio files. `deleteDownload` removes the asset, index entry, metadata and calls `provider.release`. `cancelDownload` does the same for non-completed downloads and is a no-op once completed. A completed `startDownload` is idempotent; a second pending `start` is ignored.
- Mobile-data policy (Wi-Fi only etc.) is the app's concern. Android's download service runs as a `dataSync` foreground service (permission `FOREGROUND_SERVICE_DATA_SYNC` is declared in the plugin manifest; the app should request `POST_NOTIFICATIONS` on Android 13+).

### Backup exclusion

**Android:** cache, download index and plugin metadata live under `Context.getNoBackupFilesDir()/offline/`, which Android excludes from backup and device transfer. Make sure your own rules do not re-include it, and exclude the provider's own licence storage:

```xml
<!-- res/xml/data_extraction_rules.xml (Android 12+) -->
<data-extraction-rules>
  <cloud-backup><exclude domain="no_backup" path="." /><exclude domain="sharedpref" path="my_offline_licences.xml" /></cloud-backup>
  <device-transfer><exclude domain="no_backup" path="." /><exclude domain="sharedpref" path="my_offline_licences.xml" /></device-transfer>
</data-extraction-rules>

<!-- res/xml/backup_rules.xml (Android 11 and below, android:fullBackupContent) -->
<full-backup-content>
  <exclude domain="no_backup" path="." />
  <exclude domain="sharedpref" path="my_offline_licences.xml" />
</full-backup-content>
```

**iOS:** media, index and plugin metadata live under Application Support `org.dwbn.plugins.playlist.offline/` with `isExcludedFromBackup`. drm-kit's own keys live in Application Support `drm-kit/` (also excluded from backup). Do not re-include those directories in a custom backup.

### Manual device checks

On a real device (Pixel 7a / physical iPhone): download a protected lecture, switch to airplane mode, play it, lock the screen and confirm lock-screen controls and background playback. Confirm there is no `.mp3` file. On Android nothing should sit outside `noBackupFilesDir`; on iOS the offline directory should have `isExcludedFromBackup`. Check expiry (`state(...) = expired` refuses playback) and renewal. FairPlay will not run in Simulator.

For switching between protected audio and video, see [Handoff with DRM](./video-handoff.md#handoff-with-drm).
