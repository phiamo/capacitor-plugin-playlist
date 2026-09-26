# Upgrading a host app

## `capacitor-plugin-playlist` → `@dwbn/capacitor-plugin-playlist`

The package moved to the `@dwbn` npm organization. The code, the repository, the native plugin name (`Playlist`), the Android package `org.dwbn.plugins.playlist` and the iOS pod `CapacitorPluginPlaylist` are unchanged.

```bash
npm uninstall capacitor-plugin-playlist
npm i @dwbn/capacitor-plugin-playlist
npx cap sync
```

Then replace the import path everywhere:

```diff
- import { Playlist } from 'capacitor-plugin-playlist';
+ import { Playlist } from '@dwbn/capacitor-plugin-playlist';
```

The unscoped `capacitor-plugin-playlist` package is deprecated and receives no further releases.

## From 0.14.x → 8.x

The TypeScript API is unchanged — every `AudioTrack` and `AudioPlayerOptions` field that existed on 0.14.x still exists, `isStream` included. Three **behavior** changes need a look at consuming code.

**1. Extensionless HLS now needs `mimeType`.** Through 0.14.x, `isStream: true` on a URL with no recognised media extension forced the HLS parser on Android. That was undocumented, disagreed with iOS and web, and broke every progressive stream behind an extensionless URL ([#144](https://github.com/phiamo/capacitor-plugin-playlist/issues/144)). HLS is now selected by an `.m3u8` path or by an explicit hint:

```ts
// Only needed when the URL has no usable extension. An `.m3u8` path is detected automatically.
{ trackId: 't1', assetUrl: 'https://cdn.example/stream/audio', mimeType: 'application/x-mpegURL' }
```

Nothing to do if your HLS URLs end in `.m3u8` — that is the common case and it keeps working. `isStream` keeps its documented meaning (pause/resume buffering) and still drives the live-edge jump on Android and iOS.

**2. Error codes now discriminate.** `RMXSTATUS_ERROR` previously reported `RMXERR_DECODE` for *every* failure on iOS, and `RMXERR_NONE_SUPPORTED` for every non-decoder failure on Android — so a dropped connection was indistinguishable from an unplayable file. Now a network failure reports `RMXERR_NETWORK`, which is the retry-able case:

```ts
if (status.msgType === RmxAudioStatusMessage.RMXSTATUS_ERROR) {
  const { code } = status.value as OnStatusErrorCallbackData;
  if (code === RmxAudioErrorType.RMXERR_NETWORK) retry(); else skipTrack();
}
```

If you branch on `code === RMXERR_DECODE` today, that branch used to catch everything on iOS and will now catch almost nothing. Web previously sent no `code` at all and now conforms to `OnStatusErrorCallbackData`; the rest of its old error payload is still there alongside `code`/`message`.

**3. `status: "stalled"` is new, and position events stop during a stall.** A genuine stall no longer streams `RMXSTATUS_PLAYBACK_POSITION` with a frozen position — the events stop and one `RMXSTATUS_STALLED` fires ([#143](https://github.com/phiamo/capacitor-plugin-playlist/issues/143)). Treat `"stalled"` as "still trying", not as an error, and don't assume a progress bar keeps ticking. The threshold is tunable with `Playlist.setOptions({ stallTimeoutMs: 6000 })` (default 10000).

**Android host app:** no Gradle or manifest changes beyond the [Android setup](./installation.md#android-media3-and-notifications).

Older upgrade steps (0.11.x and earlier) live in [legacy.md](./legacy.md).
