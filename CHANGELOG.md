# Changelog

## Unreleased

- Feat (Android): `AudioPlayerOptions.stallTimeoutMs` makes the issue #143 position-freeze stall detector's threshold configurable (default unchanged at 10000ms). Requested by a reporter on #143 whose own app-level watchdog used 6s; iOS/web rely on native stall notifications and ignore this option.

## 0.14.4

Test-only, no behavior change.

- Test (Android): added `PlaylistManagerAttachDrainTest` — a long-deferred item from the spec-55-4 review ("constructing `PlaylistManager`/`ExoPlayer` is more than a direct test") turned out to be feasible with Robolectric, which this repo already depends on for other tests. Confirms a `beginPlaybackAt` call that arrives before the player is attached (e.g. JS `play()`/`setPlaylistItems` racing `MediaService.onCreate()`) is replayed against a real `ExoPlayer` once `attachPlayer()` runs, asserting the actual `currentPosition`/`playWhenReady` outcome rather than just that no exception was thrown.

## 0.14.3

Fixes from an automated review of the 0.14.1/0.14.2 diff.

- Fix (Web): `isSeeking` (0.14.2's seek-suppression guard) was only cleared on `'seeked'`, so a seek interrupted by `'error'` instead of completing, or a track change mid-seek (which tears down the `<audio>` element before its `'seeked'` ever fires), left it stuck `true` for the rest of the session — silently disabling `RMXSTATUS_STALLED` for every later track. Now also reset on `'error'` and directly in `setCurrent()` when a new element is created, instead of relying solely on the new element's `'loadstart'` (which races the `.src` assignment that precedes listener registration).
- Fix (iOS): two native (non-JS) playlist-loop restarts — `playerItemDidReachEnd` and the `currentItem`-reaches-nil KVO handler — still called `setCurrentIndex(0)` with 0.14.2's new default (starts at 0), an unintended behavior change; native loop restart isn't an explicit JS track selection, so both now pass `resumeAtSavedPosition: true`, matching `advanceToNextItem`'s wraparound.
- Hardening (Android): the 0.14.2 fix narrowed but couldn't fully close the process-wide race on `experimentalEnableStuckPlayingDetection` — a `synchronized` block around the flag flip further narrows it to other code that also synchronizes on `ExoPlayer.Builder`, though a host app building an `ExoPlayer.Builder` on another thread without doing so can still race it; full elimination isn't possible since Media3 doesn't offer a non-static way to request this per instance.
- Test (iOS): `PositionResumeTests` for `resumeAtSavedPosition` only asserted which track became current, not the actual seek target, since `AudioTrack` is `final` and its `currentTime()` doesn't reflect a seek against an unloaded asset. Added a player-level seek-capturing subclass so the 0 vs. saved-position target is now directly asserted.

## 0.14.2

Code-review follow-up on 0.14.0, continued.

- Fix (iOS): `AVBidirectionalQueuePlayer.setCurrentIndex()` — shared by native skip *and* the explicit JS `playTrack`/`selectTrack` paths — always resumed the new current item at its saved `startPositionSeconds`. Explicit JS selection now defaults to 0 like Android's `beginPlayback`/`selectTrackByIndex`/`playTrackByIndex` (`call.getFloat("position", 0f)`); only native-skip call sites (`playPreviousItem`, `advanceToNextItem`'s wraparound) opt in via a new `resumeAtSavedPosition` parameter. Also added the `position` param to `selectTrackByIndex`/`selectTrackById`, which iOS previously didn't accept at all.
- Fix (Android): `ExoPlayer.Builder.experimentalEnableStuckPlayingDetection` (0.14.0) is a static, process-wide flag read once inside the `Builder` constructor — leaving it set to `true` for the life of the process meant any other `ExoPlayer.Builder` built elsewhere in the host app (e.g. a separate video player plugin) would silently inherit stuck-player detection too. Now flipped only around the single `ExoPlayer.Builder(this)` constructor call in `MediaService.onCreate()`, then restored to its prior value.

## 0.14.1

Code-review follow-up on 0.14.0.

- Fix (Web): the 0.14.0 `RMXSTATUS_STALLED` listeners set `lastState = 'stalled'` with nothing guaranteed to clear it back — `'playing'`/`'pause'`/`'error'`/`'canplay'`/`'ended'` now explicitly clear it, so a subsequent `RMXSTATUS_PLAYBACK_POSITION` tick can't get stuck reporting `status: "stalled"` after playback has actually resumed.
- Fix (Web): the same listeners fired on the routine `'waiting'` a seek or the initial buffering of a new source causes, producing false `RMXSTATUS_STALLED` on ordinary use. Now suppressed while a seek is in flight and before the first `'canplay'` of the current source, and de-duplicated so only one `RMXSTATUS_STALLED` fires per stall episode.
- Fix (Android): `AudioTrack.startPositionMs`'s negative-value clamp lived only in the property's custom setter, which Kotlin does not invoke for the property-initializer assignment — a negative JS-supplied `startPosition` was clamped on every later reassignment but not at construction. Fixed by clamping at the initializer too.
- Fix (Android): `AudioTrack.toDict()` didn't include `startPosition`, unlike iOS's equivalent — JS read back a track's resume position on iOS but not Android. Added for parity.

## 0.14.0

- Feat (Android): enable Media3's own stuck-player detector (`androidx.media3:media3-exoplayer` 1.9.0+, already present at the pinned 1.11.1) — `experimentalEnableStuckPlayingDetection = true` for the fast STATE_READY-no-progress case, and `setStuckBufferingDetectionTimeoutMs()` tightened from the 10-minute default to 60s for the STATE_BUFFERING case. Both report through the existing `onPlayerError` → `RMXSTATUS_ERROR` path as a "give up" backstop underneath 0.13.3's faster `RMXSTATUS_STALLED` signal.
- Feat (Web): emit `RMXSTATUS_STALLED` on the native `'waiting'`/`'stalled'` `HTMLMediaElement` events — parity with Android (0.13.3) and iOS, which already had this via `AVPlayerItemPlaybackStalledNotification`.
- Fix (Android): `pause()` silently dropped the request while a track was still loading/preparing (not yet reporting `isPlaying`), so a later "ready" callback could resume playback against an explicit pause. Adapted from [PR #142](https://github.com/phiamo/capacitor-plugin-playlist/pull/142) by @mustafa0x — its PlaylistCore-specific implementation predates the Media3 migration and couldn't apply directly, but the underlying bug was still present; the fix (call `pause()` unconditionally, already null-safe against no/loading player) ports over directly.
- Fix (Android): `seekTo()` dropped a redundant "snapshot isPlaying, seek, then force-pause if it wasn't already playing" step left over from the same pre-Media3 architecture — `Player.seekTo()` never touches `playWhenReady` on its own, so this was dead weight rather than an active fix under the current handler. Adapted from [PR #141](https://github.com/phiamo/capacitor-plugin-playlist/pull/141) by @mustafa0x, same PlaylistCore-vs-Media3 caveat as above.
- Fix (Android): `release()` never tore down the foreground service/notification — under `SHOW_NOTIFICATION_FOR_IDLE_PLAYER_ALWAYS` (Media3 never auto-hides on idle), the "now playing" notification stuck around indefinitely after the app was done with playback. This was flagged and deferred back in story 55.5/55.6 (`endForeground()` had no caller); it's wired up now, scoped to `release()` specifically so a mid-session `clearAllItems()` (about to load a new queue) isn't affected.

## 0.13.3

- Fix (Android): emit `RMXSTATUS_STALLED` when `currentPosition` fails to advance for 10s while the player claims PLAYING ([#143](https://github.com/phiamo/capacitor-plugin-playlist/issues/143)). On a mid-stream network loss, ExoPlayer's own `playbackState`/`isPlaying` can stay at "playing" indefinitely with no `Player.Listener` transition to react to, so a frozen position is the only reliable signal. Previously this produced an unbroken stream of `RMXSTATUS_PLAYBACK_POSITION` with a frozen position and `status: "playing"`, with no way for JS to tell "playing normally" from "dead stream".

## 0.13.2

- Fix (Android): the system media notification and hardware media buttons invoke `Player.seekToNext()`/`seekToPrevious()` — distinct from `seekToNextMediaItem()`/`seekToPreviousMediaItem()`, which `HandoffForwardingPlayer` already overrode. Without an override, these fell through to the wrapped ExoPlayer's own default ("restart current track if progressed" for previous, "seek to 0" for next), bypassing the playlist's skip logic and the 0.13.0/0.13.1 position-resume fix entirely for anyone using system-level controls.
- Fix (Android): the system notification controller's available skip commands were granted once at `MediaSession` connect time (typically before any track was loaded) and never refreshed, so the notification's next/previous buttons — and the commands they're allowed to send — stayed frozen at that snapshot for the life of the session. `MediaService` now re-grants them via `refreshSkipAvailability()` on every track transition and queue load.

## 0.13.1

- Fix (Android): `startPosition` (0.13.0) was only refreshed when a track was left via native skipToNext/Previous. Switching tracks via `playTrackById` (the JS "tap a playlist item" path) now also snapshots the outgoing track's position, so a later native skip back to it resumes correctly regardless of which mechanism was used to leave it.

## 0.13.0

- Fix (Android/iOS): Skipping to the next/previous track — via the in-app Next/Previous buttons or a native OS media control (notification, headset button, Android Auto/CarPlay, lock screen) — restarted the target track from 0:00 instead of resuming where it was left. Tracks now carry an optional `startPosition` (seconds) set from the host app's last-known playback position, and native skip/advance now resumes there. `AudioTrack.startPosition` is new and optional; existing hosts are unaffected until they start passing it.

## 0.12.0

- Feat (Android): Replace ExoMedia + PlaylistCore with **androidx.media3** 1.11.1 — `MediaService` extends `MediaSessionService`, playlist is ExoPlayer `MediaItem` list, notification via `DefaultMediaNotificationProvider`. Capacitor JS API unchanged.
- Feat (Android): Media3 video handoff — retain the foreground service on `prepareForVideoHandoff` (teaching-sequence path has no prewarm), pause even while buffering so audio focus drops, keep the notification in the foreground past Media3's 10-minute FGS cap, ignore MediaSession play during video, and play-then-seek on audible in-place resume. JS API unchanged (`prepareForVideoHandoff` / `resumeAfterVideoHandoff` / `getLastKnownPosition`).

### Notes for host apps

- Run `npx cap sync android` after bumping to 0.12.0.
- Pin `media3Version = '1.11.1'` and force every `androidx.media3` module in the host Gradle (see README [Upgrading a host app (0.12.0)](./README.md#upgrading-a-host-app-0120)).
- Remove host dependencies on `playlistcore` / `exomedia` if present. Do not add ExoPlayer 2.x artifacts.
- Declare `POST_NOTIFICATIONS` in the host manifest and request at runtime on API 33+.
- Remove `android:name="org.dwbn.plugins.playlist.App"` if still set. Do not call `startForeground` beside `MediaService`.
- If the APK also uses another Media3 library (for example native video), pin one `media3Version` in Gradle (see README). No Capacitor/JS changes required.

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
