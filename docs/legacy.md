# Legacy versions

Everything here is kept for apps still migrating from old releases. New integrations should use the current 8.x line and ignore this page.

## Version history before 8.0.0

Until 8.0.0 the version was deliberately independent of Capacitor and stayed on `0.x` while already requiring `@capacitor/core >= 8.0.0`. From 8.0.0 the major tracks the Capacitor major. **There is no 1.x–7.x**: 8.0.0 follows 0.15.0 directly.

| Plugin version | Meaning | Capacitor peer |
|----------------|---------|----------------|
| **0.15.0**, **0.14.5** | **Deprecated — do not use.** Published to npm in error during the 8.0.0 release; 0.14.5 briefly held the `latest` tag despite predating the [#144](https://github.com/phiamo/capacitor-plugin-playlist/issues/144) fix. Go from 0.14.x straight to 8.x. | 8+ |
| **0.12.0–0.14.4** | Media3 Android stack (`MediaSessionService`); Capacitor JS API unchanged from 0.11.x. | 8+ |
| **0.11.x** | Android: ExoMedia + PlaylistCore. | 8+ |
| **0.8.x–0.10.x** | Historical release notes only, see [CHANGELOG.md](../CHANGELOG.md). Not a target to "stay on 0.8 for Capacitor 8". | — |

All of these were published as the unscoped `capacitor-plugin-playlist`. The package is now [`@dwbn/capacitor-plugin-playlist`](./upgrading.md).

## Upgrading

### From 0.11.x → 0.12.0

For apps still on the ExoMedia/PlaylistCore line. **Capacitor / JavaScript:** no changes across this step — `Playlist`, `RmxAudioPlayer`, status events, and video handoff method names behave as before. **Android host app:** update Gradle and manifest as below, then `npx cap sync android`.

**Remove from the host app** (if you added these when following older docs or samples):

- `implementation` of `com.devbrackets.android:playlistcore` and `com.devbrackets.android:exomedia` — 0.12.0 uses Media3 inside the plugin; leftover host deps pull an old Media3 version
- `android:name="org.dwbn.plugins.playlist.App"` on `<application>` — unused since 0.11.0; keep your own `Application` class

**Stop doing** on 0.12.0: subclassing PlaylistCore, calling `startForeground` beside `MediaService`, or adding `com.google.android.exoplayer:exoplayer-*:2.x` in the app module.

**Then apply** the [Android setup](./installation.md#android-media3-and-notifications).

**Video handoff:** from 0.12.0, foreground retain starts on `prepareForVideoHandoff`. Existing JS that uses `prewarm: true` still works; prewarm is optional, not required for every flow.

### Handoff fixes by version

| Symptom on old versions | Fixed in |
|---|---|
| JS stuck in PAUSED after video (iOS, index > 0) | 0.8.11 |
| `PLAYBACK_POSITION` flood after the app returns to the foreground | 0.9.1 (position events are throttled while the WebView is backgrounded) |
| Foreground service lost during long videos | 0.12.0 (retain starts on `prepareForVideoHandoff`) |

See [CHANGELOG.md](../CHANGELOG.md) (0.8.8–0.12.0) for details.
