# Usage

## Getting started

See also [`examples/audio-provider.ts`](../examples/audio-provider.ts) for an Angular/Ionic integration.

### Basic flow (`Playlist` API)

```typescript
import { Playlist, AudioTrack, RmxAudioStatusMessage } from '@dwbn/capacitor-plugin-playlist';

await Playlist.setOptions({
  verbose: true,
  resetStreamOnPause: true,
  options: { icon: 'ic_notification' },
});

await Playlist.initialize();

const handle = await Playlist.addListener('status', ({ status }) => {
  if (status.msgType === RmxAudioStatusMessage.RMXSTATUS_PLAYBACK_POSITION) {
    // update UI progress
  }
});

const track: AudioTrack = {
  trackId: 'track-1',
  assetUrl: 'https://example.com/audio.mp3',
  title: 'Track title',
  artist: 'Artist name',
  album: 'Album name',
  albumArt: 'https://example.com/cover.jpg',
  isStream: false,
  // mimeType: 'application/x-mpegURL',  // only for sources whose URL has no usable extension
};

await Playlist.setPlaylistItems({
  items: [track],
  options: { startPaused: false },
});

await Playlist.play();
// later: handle.remove();
```

### `RmxAudioPlayer` wrapper (Cordova migration)

```typescript
import { RmxAudioPlayer, AudioTrack } from '@dwbn/capacitor-plugin-playlist';

const player = new RmxAudioPlayer();
await player.initialize();

player.on('status', (data) => {
  console.log('status', data.msgType, data);
});

await player.setLoop(true);
await player.setPlaylistItems([track], { retainPosition: true, playFromId: track.trackId });
await player.play();
```

## Features

### Playlist management

- `setPlaylistItems` — replace entire playlist (optional position retention)
- `addItem` / `addAllItems` — append tracks; `addItem` accepts optional insert index (“play next”)
- `moveItem` — reorder without disrupting current playback
- `replaceItem` — swap track metadata/URL in place (e.g. stream → offline file)
- `removeItem` / `removeItems` / `clearAllItems` — remove tracks
- `getPlaylist` — snapshot of current items
- `setLoop` — loop entire playlist when the last track completes

### Playback controls

- `play` / `pause`
- `skipForward` / `skipBack`
- `seekTo` (seconds)
- `playTrackByIndex` / `playTrackById` — jump and play
- `selectTrackByIndex` / `selectTrackById` — select without playing
- `setPlaybackVolume` (0–1)
- `setPlaybackRate` (0 pauses, 1 = normal speed)

### Native platform integration

| Platform | Engine | OS controls |
|----------|--------|-------------|
| Android | [androidx.media3](https://developer.android.com/jetpack/androidx/releases/media3) **1.11.1** (`ExoPlayer` + `MediaSessionService` + `DefaultMediaNotificationProvider`) | MediaStyle notification, MediaSession, foreground `mediaPlayback` service |
| iOS | Custom `AVBidirectionalQueuePlayer` | Lock screen + Control Center via `MPNowPlayingInfoCenter` / `MPRemoteCommandCenter` |
| Web | HTMLAudioElement + optional HLS.js | Browser media controls only |

### Background audio

- Android: foreground media service with `WAKE_LOCK`, `FOREGROUND_SERVICE`, and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (Android 14+)
- iOS: `UIBackgroundModes` → `audio`
- Position events throttled while WebView is backgrounded; one live snapshot emitted on foreground resume

### Tracks and sources

- Remote URLs, local files (`file://` or app-resolved paths), and streams
- Set `isStream: true` on streaming URLs so pause/resume buffering behaves correctly. This does not
  affect how the source is parsed — HLS is picked up automatically from an `.m3u8` path, and anything
  else is sniffed
- Set `mimeType` only when the URL has no usable extension and the type can't be inferred, e.g.
  `mimeType: 'application/x-mpegURL'` for an HLS playlist behind an extensionless URL
- `albumArt` shown in notification / lock screen (Glide on Android)
- Protected HLS lectures can be downloaded for offline playback on Android and iOS (`startDownload` / `downloadId` items). See [Protected playback (DRM)](./drm.md#offline-downloads-android-and-ios). Web has no offline playback.

### Status event stream

Single `status` listener with `RmxAudioStatusMessage` events (see [Events](./events.md)).

### Video handoff

When switching from background audio to native fullscreen video:

- `prepareForVideoHandoff()` — release audio focus / session
- `getLastKnownPosition()` — saved head position (seconds)
- `resumeAfterVideoHandoff({ position, prewarm? })` — re-arm audio after video

See the [video handoff guide](./video-handoff.md).

### Cordova-compatible wrapper

`RmxAudioPlayer` (`src/RmxAudioPlayer.ts`) is a drop-in replacement for cordova-plugin-playlist with `on('status')` / `off('status')` and state getters (`isPlaying`, `currentTrack`, etc.).

**Not exposed on `RmxAudioPlayer`:** `getPlaylist`, video handoff methods — call `Playlist` directly for those.

### Not supported

- Shuffle
- Audio ads / IMA integration ([#71](https://github.com/phiamo/capacitor-plugin-playlist/issues/71))
- Mixable / low-latency game audio (use [cordova-plugin-nativeaudio](https://github.com/floatinghotpot/cordova-plugin-nativeaudio) instead)
- Simultaneous audio mixing (lock-screen controls require exclusive audio focus)

## Migrating from cordova-plugin-playlist

Use the shipped `RmxAudioPlayer` class — in the best case you only change your import:

```typescript
// before
import { RmxAudioPlayer } from 'cordova-plugin-playlist';

// after
import { RmxAudioPlayer } from '@dwbn/capacitor-plugin-playlist';
```

For new code or video handoff, prefer the `Playlist` plugin object directly. Coming from Cordova, see the [project history](./history.md).

Full method and type reference: [API.md](./API.md). Events: [events.md](./events.md).
