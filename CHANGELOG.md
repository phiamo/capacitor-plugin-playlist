# Changelog

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
