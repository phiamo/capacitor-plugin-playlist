# Changelog

## Unreleased

## 0.12.0

- Feat (Android): Media3 video handoff — retain the foreground service on `prepareForVideoHandoff` (teaching-sequence path has no prewarm), pause even while buffering so audio focus drops, keep the notification in the foreground past Media3's 10-minute FGS cap, ignore MediaSession play during video, and play-then-seek on audible in-place resume. JS API unchanged (`prepareForVideoHandoff` / `resumeAfterVideoHandoff` / `getLastKnownPosition`).

## 0.11.4

- Fix (Android): apply `kotlin-android` only for standalone/CI builds (AGP < 9); AGP 9+ consuming apps use built-in Kotlin.

## 0.11.3

- Fix (Android): AGP 9 compatibility — use `proguard-android-optimize.txt` and drop redundant `kotlin-android` plugin (built-in Kotlin in AGP 9+).

## 0.11.2

- Fix (Android): Separate playlist identity from track ids so colliding public `trackId` values do not break setup, playback, selection, or removal ([#139](https://github.com/phiamo/capacitor-plugin-playlist/pull/139)).
- Fix (Android): Retain playback position when replacing the playlist; preserve explicit `playFromPosition` (including zero) and otherwise keep current progress ([#140](https://github.com/phiamo/capacitor-plugin-playlist/pull/140)).
- Chore (CI): Upgrade GitHub Actions to v5 and Node.js 24.

## 0.11.1

- Feat: `addItem({ item, index? })` — insert at a 0-based index without interrupting playback ([#80](https://github.com/phiamo/capacitor-plugin-playlist/issues/80)).
- Feat: `moveItem({ from, to })` — reorder playlist in place without restarting the current track ([#81](https://github.com/phiamo/capacitor-plugin-playlist/issues/81)).
- Feat: `replaceItem({ index?, id?, item })` — replace track metadata/URL in place; preserves position when replacing the current track ([#94](https://github.com/phiamo/capacitor-plugin-playlist/issues/94)).
- Feat: New status events `RMXSTATUS_ITEM_MOVED` (112) and `RMXSTATUS_ITEM_REPLACED` (113).
- Fix: `replaceItem` preserves existing track id when `item.trackId` is omitted (TS wrapper, Android bridge, iOS).
- Fix (Android): Correct current-index bookkeeping when moving the playing track.
- Fix (iOS): Avoid `RMXSTATUS_ITEM_ADDED` flood when mutating the queue in place.
- Chore: Add Vitest, JUnit, and XCTest coverage for playlist mutations; gate release publish on CI.

## 0.11.0

- Fix (Android): Share playlist manager through `PlaylistRuntime` using the host application context; host apps no longer need `android:name="org.dwbn.plugins.playlist.App"` (legacy `App` class kept as deprecated shim).
- Fix (Android): Declare `MediaService`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` in the plugin library manifest for automatic manifest merge.

## 0.10.10

- Fix (web): `release()` clears `currentTrack` and resets `lastState` so resume after video (or any `release()`) cannot leave the plugin in a “current track but no `<audio>` element” state where `play()` / `playTrackById` silently no-op.

## 0.10.9

- Chore: Bump version; stop committing built `dist/` (consumers build via `prepublishOnly` / local `npm run build`).

## 0.10.8

- Feat (iOS): `resumeAfterVideoHandoff` seek-then-play (when `play: true`) and returns `{ resumed: true }` so JS can skip redundant `playTrackById` after video exit.
- Feat: `play` option on `resumeAfterVideoHandoff` — paused video exit must pass `play: false` so audio stays paused (Android clears handoff retain and returns `{ resumed: false }`).

## 0.10.7

- Fix (iOS): During video handoff, set `actionAtItemEnd = .none` and pin the current track id so a failed/ended HLS item cannot advance the queue to the next track while native video owns the session. `resumeAfterVideoHandoff` restores `.advance` and re-selects the pinned track if the queue drifted.

## 0.10.6

- Fix (iOS): `playCommand` always re-activates `AVAudioSession` after video handoff (no longer skips when `isOtherAudioPlaying` is briefly true during AVPlayer teardown). `resumeAfterVideoHandoff` always calls `activateAudioSession()` for the same reason.

## 0.10.5

- Fix (Android): `resumePlaybackAfterVideoHandoff` plays before seek so playlistcore `onSeekComplete` does not pause after video→audio handoff (seek-while-paused race left UI “playing” with silent native audio).

## 0.10.4

- Feat: `resumeAfterVideoHandoff` resolves with `{ resumed: boolean }`. Android returns `true` when in-place resume already seeked and started playback so JS can skip redundant `seekTo`/`play` (smoother video→audio handoff). iOS and web always return `{ resumed: false }`.

## 0.10.3

- Fix (Android): During video handoff prewarm, `AudioPlaylistHandler` no longer requests audio focus or plays audio while native video owns focus. Prevents video sound from dropping shortly after start.
- Fix (Android): `onPrepared()` during prewarm prepares at seek position but stays silent (paused, focus abandoned) until `resumeAfterVideoHandoff` clears the prewarm flag.
- Fix (iOS): Register `prepareForVideoHandoff`, `resumeAfterVideoHandoff`, and `getLastKnownPosition` in `pluginMethods` allow-list.
- Feat (iOS): `resumeAfterVideoHandoff` accepts optional `prewarm` option.

## 0.10.2

- Fix (iOS): Swift 6 SPM build with v5 language mode and concurrency globals.

## 0.10.1

- Chore: Include committed `dist/` for git/npm consumers that install without running `prepare`.

## 0.10.0

- Feat (iOS): Swift Package Manager support (`Package.swift`, `CAPBridgedPlugin`).

## 0.9.5

- Fix (npm): Remove `prepare` script to fix consumer CI installs that do not run build on install.

## 0.9.4

- Fix (iOS): Raise minimum deployment target to iOS 18; Swift 6 error handling updates.

## 0.9.3

- Fix (Android): Catch `ForegroundServiceStartNotAllowedException` and background MediaService start failures in `beginPlayback`.
- Merge: Integrate interop branch (0.9.1–0.9.3 background audio and bridge fixes).

## 0.9.2

- Fix (Android/iOS): Application state notifications for background audio status handling.

## 0.9.1

- Fix (Android/iOS): Suppress `RMXSTATUS_PLAYBACK_POSITION` bridge emissions while the WebView is backgrounded; emit one live playback snapshot on foreground resume instead of flushing a backlog of stale position ticks.
- Fix (Android): Do not retain `PLAYBACK_POSITION` events in the Capacitor bridge (`notifyListeners` retain=false for msgType 40); discrete events (PLAYING, PAUSE, TRACK_CHANGED, etc.) remain retained.

## 0.9.0

- Chore: Version bump after merging interop fixes into main for release.

## 0.8.11

- Fix (iOS): `observeValue case "rate"` now uses the new rate value to determine playing/paused state instead of `player?.isPlaying` (which equals `timeControlStatus == .playing`). During the `.waitingToPlayAtSpecifiedRate` transition right after `play()`, `isPlaying` was `false` even though rate had changed to 1, causing a spurious PAUSE event to be sent to JS immediately on resume.
- Fix (iOS): `resumeAfterVideoHandoff` now resets `lastTrackId = nil` so the `timeControlStatus` KVO guard (`lastTrackId != trackId || isAtBeginning`) does not suppress the PLAYING event for same-track non-index-0 resume. Without this, any audio track at playlist index > 0 would never receive a PLAYING status after video handoff.

## 0.8.10

- Fix (Android): `resumeAfterVideoHandoff` now calls `beginPlayback(positionMs, startPaused=true)` to re-arm the MediaService and re-acquire audio focus before JS sends `play()`. Previously it was a pure no-op (stored position only), so if the audio service had stopped itself after audio focus was abandoned (e.g. during a long video session), the subsequent `Playlist.play()` call would silently no-op, leaving audio permanently paused after exiting video.
- Fix (Android): `Playlist.play()` now self-heals when `playlistHandler` or `currentMediaPlayer` is null: falls back to `beginPlayback(lastKnownPositionSec, startPaused=false)` so audio is re-prepared and started instead of silently doing nothing. Removes unsafe `!!` non-null assertion on `isPlaying`.

## 0.8.9

- Fix (Android): `destroyResources()` no longer nulls `statusCallback`; `onStatus` / `onError` lazily recreate it if missing. Prevents all audio events (`PLAYING`, `PAUSE`, `PLAYBACK_POSITION`, etc.) from being permanently silenced after `Playlist.release()` (e.g. video hand-off).
- Fix (iOS): `releaseResources()` now removes KVO observers (`currentItem`, `rate`, `timeControlStatus`) and resets `commandCenterRegistered`; `initialize()` is idempotent; `playbackTimeObserver` is re-armed on `initialize()`, `setPlaylistItems`, and `playCommand` via `installPlaybackTimeObserverIfNeeded()`. Prevents periodic events (`PLAYBACK_POSITION`, periodic `PLAYING`/`PAUSE`) and lock-screen controls from going dead after `Playlist.release()`.

## 0.8.8

- Feat: `prepareForVideoHandoff`, `resumeAfterVideoHandoff`, `getLastKnownPosition` on iOS, Android, and web stub.
