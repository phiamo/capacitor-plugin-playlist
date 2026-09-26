# History & credits

## Where this plugin comes from

| When | What |
|---|---|
| 2018 | [**Rolamix/cordova-plugin-playlist**](https://github.com/Rolamix/cordova-plugin-playlist) — a Cordova plugin for native audio playlists with background playback and lock-screen controls. Its `RmxAudioPlayer` API lives on here as the Cordova-compatibility wrapper. |
| Nov 2020 | [**phiamo**](https://github.com/phiamo) starts the Capacitor port for the DWBN apps: a Capacitor plugin API (`Playlist`) plus web support, keeping `RmxAudioPlayer` as a drop-in for Cordova users. |
| 2021–2025 | Published on npm as `capacitor-plugin-playlist` and kept current with Capacitor 3 through 7, with many community fixes. |
| 0.12.0 (Sep 2026) | Android moves from ExoMedia/PlaylistCore to **androidx.media3** (`MediaSessionService`). |
| 8.0.0 (Sep 2026) | The version major follows Capacitor's (8.x = Capacitor 8). Error codes that tell network failures apart from decode failures, and stall detection. |
| 2026 | Widevine DRM on Android through host-registered providers ([drm-kit](https://github.com/phiamo/drm-kit)). Published as **`@dwbn/capacitor-plugin-playlist`**. |

Details for each release are in [CHANGELOG.md](../CHANGELOG.md). Notes for old versions are in [legacy.md](./legacy.md).

## Standing on the shoulders of

The original Cordova plugin credited these projects. Several of them shaped this code base:

- [cordova-plugin-playlist](https://github.com/Rolamix/cordova-plugin-playlist) by **Rolamix** — the original plugin and API design (MIT).
- [ExoMedia](https://github.com/brianwernick/ExoMedia) and [PlaylistCore](https://github.com/brianwernick/PlaylistCore) by **Brian Wernick** — the Android playback and notification stack up to 0.11.x.
- [Bi-Directional AVQueuePlayer](https://github.com/jrtaal/AVBidirectionalQueuePlayer) — the basis of `AVBidirectionalQueuePlayer`, still used on iOS.
- [cordova-plugin-media](https://github.com/apache/cordova-plugin-media) — Apache Cordova's media plugin.
- [cordova-music-controls-plugin](https://github.com/homerours/cordova-music-controls-plugin) — lock-screen / notification controls.

## Contributors

Thank you to everyone who sent code:

| | | |
|---|---|---|
| [@phiamo](https://github.com/phiamo) — maintainer | [@emilsaj](https://github.com/emilsaj) | [@mustafa0x](https://github.com/mustafa0x) |
| [@ghenry22](https://github.com/ghenry22) | [@markusfassbender](https://github.com/markusfassbender) | [@asika32764](https://github.com/asika32764) |
| [@dasantonym](https://github.com/dasantonym) | [@timstoute](https://github.com/timstoute) | [@kashz](https://github.com/kashz) |
| [@thoasty-dev](https://github.com/thoasty-dev) | [@RobbieTheWagner](https://github.com/RobbieTheWagner) | [@ronildo](https://github.com/ronildo) |

The full list is at [GitHub contributors](https://github.com/phiamo/capacitor-plugin-playlist/graphs/contributors).
