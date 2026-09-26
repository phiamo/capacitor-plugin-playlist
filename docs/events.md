# Events

Subscribe via `Playlist.addListener('status', …)` or `RmxAudioPlayer.on('status', …)`.

Each callback receives `{ action: 'status', status: OnStatusCallbackData }` where:

- `status.trackId` — current track id, `"NONE"` when idle, `"INVALID"` when playlist completed
- `status.msgType` — `RmxAudioStatusMessage` enum value
- `status.value` — payload (shape depends on `msgType`)

| msgType | Name | When | Payload |
|---------|------|------|---------|
| 5 | ERROR | Playback or network failure | `OnStatusErrorCallbackData` (`code`, `message`) — `code` distinguishes network from decode from unsupported |
| 10 | LOADING | Track loading started | `OnStatusCallbackUpdateData` |
| 11 | CANPLAY | Track ready to play | `OnStatusCallbackUpdateData` |
| 15 | LOADED | Track fully loaded | `OnStatusCallbackUpdateData` |
| 20 | STALLED | Position stopped advancing while the player still reports playing. All platforms | `OnStatusCallbackUpdateData` |
| 25 | BUFFERING | Buffer progress update | `OnStatusCallbackUpdateData` |
| 30 | PLAYING | Playback started/resumed | `OnStatusCallbackUpdateData` |
| 35 | PAUSE | Playback paused | `OnStatusCallbackUpdateData` |
| 40 | PLAYBACK_POSITION | Periodic position tick | `OnStatusCallbackUpdateData` (suppressed while the WebView is backgrounded, and — Android/iOS — while stalled) |
| 45 | SEEK | User or app seeked | `OnStatusCallbackUpdateData` |
| 50 | COMPLETED | Current track finished | `OnStatusCallbackUpdateData` |
| 55 | DURATION | Duration first known | `OnStatusCallbackUpdateData` |
| 60 | STOPPED | All playback stopped | `OnStatusCallbackUpdateData` |
| 90 | SKIP_FORWARD | Skipped to next track | `OnStatusCallbackUpdateData` |
| 95 | SKIP_BACK | Skipped to previous track | `OnStatusCallbackUpdateData` |
| 100 | TRACK_CHANGED | Active track changed | `OnStatusTrackChangedData` |
| 105 | PLAYLIST_COMPLETED | Entire playlist finished | `OnStatusCallbackUpdateData` |
| 110 | ITEM_ADDED | Track added | `OnStatusCallbackUpdateData` |
| 115 | ITEM_REMOVED | Track removed | `OnStatusCallbackUpdateData` |
| 120 | PLAYLIST_CLEARED | All tracks removed | `OnStatusCallbackUpdateData` |

For track changes, prefer handling `TRACK_CHANGED` over `SKIP_FORWARD` / `SKIP_BACK`.

## Track `status` values

Most payloads carry a `status` string describing the track's current state. Treat an unrecognised value as non-fatal rather than as an error:

| `status` | Meaning |
|----------|---------|
| `loading` | Source is being prepared; not yet playable |
| `playing` | Playing, position advancing |
| `paused` | Paused by the user or the system |
| `stalled` | Still trying, but position is not advancing — see `RMXSTATUS_STALLED` and `stallTimeoutMs`. |
| `error` | Playback failed; see the `code` on the `RMXSTATUS_ERROR` payload |

## DRM errors

DRM failures arrive on the same `status` listener as `RMXSTATUS_ERROR` with `value.error` set to one of `blockedByStreamLimit` | `notEntitled` | `expired` | `network` | `unknown`. See [drm.md](./drm.md#errors).
