# @dwbn/capacitor-plugin-playlist

Native audio playlists for Capacitor on **Android**, **iOS** and **Web**. Supports background playback, lock-screen and notification controls, a clean handoff to native video, and Widevine DRM on Android.

[![npm](https://img.shields.io/npm/v/@dwbn/capacitor-plugin-playlist?style=flat-square)](https://www.npmjs.com/package/@dwbn/capacitor-plugin-playlist)
[![CI](https://img.shields.io/github/actions/workflow/status/phiamo/capacitor-plugin-playlist/release-package.yml?branch=main&style=flat-square)](https://github.com/phiamo/capacitor-plugin-playlist/actions/workflows/release-package.yml)
[![license](https://img.shields.io/npm/l/@dwbn/capacitor-plugin-playlist?style=flat-square)](./LICENSE)
[![Capacitor 8](https://img.shields.io/badge/capacitor-8-119EFF?style=flat-square)](https://capacitorjs.com)

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/W8V527Q5YX)

> 📦 **New package name.** This plugin is now published as **`@dwbn/capacitor-plugin-playlist`** in the `@dwbn` npm organization. The unscoped `capacitor-plugin-playlist` is deprecated and receives no further releases. The repository stays here. [How to switch →](./docs/upgrading.md#capacitor-plugin-playlist--dwbncapacitor-plugin-playlist)

## Part of the DWBN media stack

| | Project | What it does |
|---|---|---|
| 🎵 | **capacitor-plugin-playlist** (this repo) · [`@dwbn/capacitor-plugin-playlist`](https://www.npmjs.com/package/@dwbn/capacitor-plugin-playlist) | Background audio playlists, lock screen, audio↔video handoff |
| 🎬 | [**capacitor-video-player**](https://github.com/phiamo/capacitor-video-player) · [`@dwbn/capacitor-video-player`](https://www.npmjs.com/package/@dwbn/capacitor-video-player) | Native fullscreen video with Media3 / AVPlayer, PiP, Chromecast and subtitles |
| 🔐 | [**drm-kit**](https://github.com/phiamo/drm-kit) · Swift Package Manager / JitPack | Widevine session and DRM identifiers that the host app plugs into both plugins |

The plugins work on their own. drm-kit is optional and is added by the **app**, never by a plugin. The [audio ↔ video handoff guide](./docs/video-handoff.md) shows all three working together.

## Platform support

| | Android | iOS | Web |
|---|---|---|---|
| Engine | androidx.media3 1.11.1 (`MediaSessionService`) | `AVBidirectionalQueuePlayer` (AVQueuePlayer) | `HTMLAudioElement` + optional hls.js |
| Background playback | ✅ foreground media service | ✅ `UIBackgroundModes: audio` | — |
| Lock screen / notification | ✅ MediaStyle notification | ✅ Now Playing / Control Center | Browser media controls |
| HLS | ✅ | ✅ | ✅ with hls.js |
| Video handoff | ✅ (with optional prewarm) | ✅ | stub (pause + position) |
| DRM | ✅ Widevine via [drm-kit](./docs/drm.md) | planned (FairPlay) | — |
| Minimum | SDK 24 | iOS 18 | modern browsers |

Requires **Capacitor 8** (`@capacitor/core >= 8.0.0`).

## Install

```bash
npm i @dwbn/capacitor-plugin-playlist
npx cap sync
```

On iOS, add `audio` to `UIBackgroundModes`. On Android 13+, request `POST_NOTIFICATIONS`. The [installation guide](./docs/installation.md) has the full platform setup, including the notification icon, Glide and the Media3 version pin.

## Quick start

```typescript
import { Playlist, RmxAudioStatusMessage, OnStatusCallbackUpdateData } from '@dwbn/capacitor-plugin-playlist';

await Playlist.setOptions({ options: { icon: 'ic_notification' } });
await Playlist.initialize();

await Playlist.addListener('status', ({ status }) => {
  if (status.msgType === RmxAudioStatusMessage.RMXSTATUS_PLAYBACK_POSITION) {
    const { currentPosition, duration } = status.value as OnStatusCallbackUpdateData;
    console.log(`${currentPosition} / ${duration}s`);
  }
});

await Playlist.setPlaylistItems({
  items: [{
    trackId: 'track-1',
    assetUrl: 'https://example.com/audio.mp3',
    title: 'Track title',
    artist: 'Artist',
    album: 'Album',
    albumArt: 'https://example.com/cover.jpg',
  }],
  options: {},
});
await Playlist.play();
```

Coming from `cordova-plugin-playlist`? The `RmxAudioPlayer` wrapper is a drop-in replacement ([usage](./docs/usage.md#migrating-from-cordova-plugin-playlist)).

## Documentation

| Guide | |
|---|---|
| [Installation](./docs/installation.md) | Android manifest, Media3 pin, notification icon, Glide, iOS background mode, web/hls.js |
| [Usage](./docs/usage.md) | Features, playlist management, `RmxAudioPlayer`, Cordova migration |
| [Events](./docs/events.md) | The `status` stream: message types, payloads, track states |
| [Audio ↔ video handoff](./docs/video-handoff.md) | Switch between background audio and native video, with and without DRM |
| [Protected playback (DRM)](./docs/drm.md) | Widevine on Android with drm-kit, error codes |
| [API reference](./docs/API.md) | Every method and type (generated) |
| [Upgrading](./docs/upgrading.md) | Moving to `@dwbn/…`, and from 0.14.x to 8.x |
| [Legacy versions](./docs/legacy.md) | 0.x version notes and old upgrade paths |
| [History & credits](./docs/history.md) | From cordova-plugin-playlist to today |
| [Changelog](./CHANGELOG.md) | Release notes |

## Versioning

The major version follows Capacitor's major: **8.x targets Capacitor 8**, the same as [capacitor-video-player](https://github.com/phiamo/capacitor-video-player). Minor and patch versions are this plugin's own. There was never a 1.x–7.x: 8.0.0 came straight after 0.15.0 ([why](./docs/legacy.md)).

## Not supported

Shuffle, audio ads / IMA ([#71](https://github.com/phiamo/capacitor-plugin-playlist/issues/71)), and mixing with other audio. Lock-screen controls need exclusive audio focus. For low-latency game sounds, use [cordova-plugin-nativeaudio](https://github.com/floatinghotpot/cordova-plugin-nativeaudio).

## History & credits

Started in 2020 as the Capacitor port of [**Rolamix/cordova-plugin-playlist**](https://github.com/Rolamix/cordova-plugin-playlist). It builds on ideas and code from [ExoMedia / PlaylistCore](https://github.com/brianwernick/ExoMedia) (Brian Wernick), the [Bi-Directional AVQueuePlayer](https://github.com/jrtaal/AVBidirectionalQueuePlayer), [cordova-plugin-media](https://github.com/apache/cordova-plugin-media) and [cordova-music-controls-plugin](https://github.com/homerours/cordova-music-controls-plugin). It is maintained by [@phiamo](https://github.com/phiamo) for the DWBN apps, with help from [many contributors](./docs/history.md#contributors). Thank you all 🙏

## License

[MIT](./LICENSE)
