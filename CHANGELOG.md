# Changelog

## 0.8.9

- Fix (Android): `destroyResources()` no longer nulls `statusCallback`; `onStatus` / `onError` lazily recreate it if missing. Prevents all audio events (`PLAYING`, `PAUSE`, `PLAYBACK_POSITION`, etc.) from being permanently silenced after `Playlist.release()` (e.g. video hand-off).
- Fix (iOS): `releaseResources()` now removes KVO observers (`currentItem`, `rate`, `timeControlStatus`) and resets `commandCenterRegistered`; `initialize()` is idempotent; `playbackTimeObserver` is re-armed on `initialize()`, `setPlaylistItems`, and `playCommand` via `installPlaybackTimeObserverIfNeeded()`. Prevents periodic events (`PLAYBACK_POSITION`, periodic `PLAYING`/`PAUSE`) and lock-screen controls from going dead after `Playlist.release()`.

## 0.8.8

- Epic 45: `prepareForVideoHandoff`, `resumeAfterVideoHandoff`, `getLastKnownPosition` on iOS, Android, and web stub.
