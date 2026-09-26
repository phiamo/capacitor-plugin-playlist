# API reference

Generated from the TypeScript definitions with `@capacitor/docgen` (`npm run docgen`). Do not edit between the docgen markers.

Most apps use the `Playlist` object shown here. The Cordova-style `RmxAudioPlayer` wrapper is described in [usage.md](./usage.md#rmxaudioplayer-wrapper-cordova-migration).

<docgen-index>

* [`addListener('status', ...)`](#addlistenerstatus-)
* [`setOptions(...)`](#setoptions)
* [`initialize()`](#initialize)
* [`release()`](#release)
* [`setPlaylistItems(...)`](#setplaylistitems)
* [`addItem(...)`](#additem)
* [`moveItem(...)`](#moveitem)
* [`replaceItem(...)`](#replaceitem)
* [`addAllItems(...)`](#addallitems)
* [`removeItem(...)`](#removeitem)
* [`removeItems(...)`](#removeitems)
* [`clearAllItems()`](#clearallitems)
* [`getPlaylist()`](#getplaylist)
* [`play()`](#play)
* [`pause()`](#pause)
* [`skipForward()`](#skipforward)
* [`skipBack()`](#skipback)
* [`seekTo(...)`](#seekto)
* [`playTrackByIndex(...)`](#playtrackbyindex)
* [`playTrackById(...)`](#playtrackbyid)
* [`selectTrackByIndex(...)`](#selecttrackbyindex)
* [`selectTrackById(...)`](#selecttrackbyid)
* [`setPlaybackVolume(...)`](#setplaybackvolume)
* [`setLoop(...)`](#setloop)
* [`setPlaybackRate(...)`](#setplaybackrate)
* [`prepareForVideoHandoff()`](#prepareforvideohandoff)
* [`resumeAfterVideoHandoff(...)`](#resumeaftervideohandoff)
* [`getLastKnownPosition()`](#getlastknownposition)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)
* [Enums](#enums)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### addListener('status', ...)

```typescript
addListener(eventName: 'status', listenerFunc: PlaylistStatusChangeCallback) => Promise<PluginListenerHandle>
```

Subscribe to native playback status events (track changes, position, errors, etc.).

| Param              | Type                                                                                  | Description                                                                                                                       |
| ------------------ | ------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'status'</code>                                                                 | Must be `'status'`.                                                                                                               |
| **`listenerFunc`** | <code><a href="#playliststatuschangecallback">PlaylistStatusChangeCallback</a></code> | Callback receiving `{ action, status }` where `status.msgType` is a <a href="#rmxaudiostatusmessage">`RmxAudioStatusMessage`</a>. |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### setOptions(...)

```typescript
setOptions(options: AudioPlayerOptions) => Promise<void>
```

Configure plugin behaviour (verbose logging, stream pause handling, notification icon).
Can be called at any time; not required before playback.

| Param         | Type                                                              |
| ------------- | ----------------------------------------------------------------- |
| **`options`** | <code><a href="#audioplayeroptions">AudioPlayerOptions</a></code> |

--------------------


### initialize()

```typescript
initialize() => Promise<void>
```

Initialise the native player, register status callbacks, and arm lock-screen / notification controls.
Call once before playback (e.g. on app start).

--------------------


### release()

```typescript
release() => Promise<void>
```

Tear down native resources (audio session, media service, observers).
Call when the app no longer needs background audio (e.g. on logout).

--------------------


### setPlaylistItems(...)

```typescript
setPlaylistItems(options: PlaylistOptions) => Promise<void>
```

Replace the entire playlist. Clears all previous items.
Use `options.retainPosition` to keep the current track and playback position.

Optional `drm` on items is additive: Android attaches Widevine through a
host-registered provider (`AudioDrm.setProvider`). Without a provider the call
is rejected with code `noProvider` and the previous queue is left unchanged.
iOS and web reject with code `notSupported` (`DRM not supported on this platform yet`)
and do not play. DRM playback errors use the existing `status` listener
(`RMXSTATUS_ERROR`) with `value.error` set to one of
`blockedByStreamLimit` | `notEntitled` | `expired` | `network` | `unknown`.

| Param         | Type                                                        |
| ------------- | ----------------------------------------------------------- |
| **`options`** | <code><a href="#playlistoptions">PlaylistOptions</a></code> |

--------------------


### addItem(...)

```typescript
addItem(options: AddItemOptions) => Promise<void>
```

Append a single track to the end of the playlist, or insert at a 0-based index.
When `index` is omitted the track is appended. Insertion does not interrupt playback
of the current track.

Items with `drm` follow the same provider / `notSupported` rules as `setPlaylistItems`.

| Param         | Type                                                      |
| ------------- | --------------------------------------------------------- |
| **`options`** | <code><a href="#additemoptions">AddItemOptions</a></code> |

--------------------


### moveItem(...)

```typescript
moveItem(options: MoveItemOptions) => Promise<void>
```

Move a track from one index to another without restarting the current track.

| Param         | Type                                                        |
| ------------- | ----------------------------------------------------------- |
| **`options`** | <code><a href="#moveitemoptions">MoveItemOptions</a></code> |

--------------------


### replaceItem(...)

```typescript
replaceItem(options: ReplaceItemOptions) => Promise<void>
```

Replace a track's metadata and source URL in place (e.g. stream URL → local file).
When replacing the currently playing track, playback position and play/pause state are preserved.

Items with `drm` follow the same provider / `notSupported` rules as `setPlaylistItems`.

| Param         | Type                                                              |
| ------------- | ----------------------------------------------------------------- |
| **`options`** | <code><a href="#replaceitemoptions">ReplaceItemOptions</a></code> |

--------------------


### addAllItems(...)

```typescript
addAllItems(options: AddAllItemOptions) => Promise<void>
```

Append multiple tracks to the end of the playlist.
Raises one `RMXSTATUS_ITEM_ADDED` event per track.

If any item has `drm` and cannot open, the whole call fails and the previous
queue is left unchanged (same provider / `notSupported` rules as `setPlaylistItems`).

| Param         | Type                                                            |
| ------------- | --------------------------------------------------------------- |
| **`options`** | <code><a href="#addallitemoptions">AddAllItemOptions</a></code> |

--------------------


### removeItem(...)

```typescript
removeItem(options: RemoveItemOptions) => Promise<void>
```

Remove a track by index (preferred) or id.
If the removed track is currently playing, the next track starts automatically.

| Param         | Type                                                            |
| ------------- | --------------------------------------------------------------- |
| **`options`** | <code><a href="#removeitemoptions">RemoveItemOptions</a></code> |

--------------------


### removeItems(...)

```typescript
removeItems(options: RemoveItemsOptions) => Promise<void>
```

Remove multiple tracks in a single batch.
If the currently playing track is removed, the next available track starts automatically.

| Param         | Type                                                              |
| ------------- | ----------------------------------------------------------------- |
| **`options`** | <code><a href="#removeitemsoptions">RemoveItemsOptions</a></code> |

--------------------


### clearAllItems()

```typescript
clearAllItems() => Promise<void>
```

Remove all tracks from the playlist. Raises `RMXSTATUS_PLAYLIST_CLEARED` and `RMXSTATUS_STOPPED`.

--------------------


### getPlaylist()

```typescript
getPlaylist() => Promise<GetPlaylistResult>
```

Return a snapshot of the current playlist items.

**Returns:** <code>Promise&lt;<a href="#getplaylistresult">GetPlaylistResult</a>&gt;</code>

--------------------


### play()

```typescript
play() => Promise<void>
```

Start or resume playback of the current track.
No-op if the playlist is empty.

--------------------


### pause()

```typescript
pause() => Promise<void>
```

Pause playback of the current track.

--------------------


### skipForward()

```typescript
skipForward() => Promise<void>
```

Skip to the next track. At the end of the playlist, wraps to the beginning when loop is enabled.

--------------------


### skipBack()

```typescript
skipBack() => Promise<void>
```

Skip to the previous track. No-op when already at the first track.

--------------------


### seekTo(...)

```typescript
seekTo(options: SeekToOptions) => Promise<void>
```

Seek to a position (seconds) in the currently playing track.
If the position exceeds track length, playback advances to the next track.

| Param         | Type                                                    |
| ------------- | ------------------------------------------------------- |
| **`options`** | <code><a href="#seektooptions">SeekToOptions</a></code> |

--------------------


### playTrackByIndex(...)

```typescript
playTrackByIndex(options: PlayByIndexOptions) => Promise<void>
```

Jump to the track at the given 0-based index and start playback.

| Param         | Type                                                              |
| ------------- | ----------------------------------------------------------------- |
| **`options`** | <code><a href="#playbyindexoptions">PlayByIndexOptions</a></code> |

--------------------


### playTrackById(...)

```typescript
playTrackById(options: PlayByIdOptions) => Promise<void>
```

Jump to the track with the given id and start playback.

| Param         | Type                                                        |
| ------------- | ----------------------------------------------------------- |
| **`options`** | <code><a href="#playbyidoptions">PlayByIdOptions</a></code> |

--------------------


### selectTrackByIndex(...)

```typescript
selectTrackByIndex(options: SelectByIndexOptions) => Promise<void>
```

Select the track at the given index without necessarily starting playback.

| Param         | Type                                                                  |
| ------------- | --------------------------------------------------------------------- |
| **`options`** | <code><a href="#selectbyindexoptions">SelectByIndexOptions</a></code> |

--------------------


### selectTrackById(...)

```typescript
selectTrackById(options: SelectByIdOptions) => Promise<void>
```

Select the track with the given id without necessarily starting playback.

| Param         | Type                                                            |
| ------------- | --------------------------------------------------------------- |
| **`options`** | <code><a href="#selectbyidoptions">SelectByIdOptions</a></code> |

--------------------


### setPlaybackVolume(...)

```typescript
setPlaybackVolume(options: SetPlaybackVolumeOptions) => Promise<void>
```

Set media stream volume. Float in range [0, 1].
Hardware volume controls still apply on top of this value.

| Param         | Type                                                                          |
| ------------- | ----------------------------------------------------------------------------- |
| **`options`** | <code><a href="#setplaybackvolumeoptions">SetPlaybackVolumeOptions</a></code> |

--------------------


### setLoop(...)

```typescript
setLoop(options: SetLoopOptions) => Promise<void>
```

When true, the playlist loops back to the first track after the last track completes.

| Param         | Type                                                      |
| ------------- | --------------------------------------------------------- |
| **`options`** | <code><a href="#setloopoptions">SetLoopOptions</a></code> |

--------------------


### setPlaybackRate(...)

```typescript
setPlaybackRate(options: SetPlaybackRateOptions) => Promise<void>
```

Set playback speed. Float value; 0 pauses, 1 is normal speed.

| Param         | Type                                                                      |
| ------------- | ------------------------------------------------------------------------- |
| **`options`** | <code><a href="#setplaybackrateoptions">SetPlaybackRateOptions</a></code> |

--------------------


### prepareForVideoHandoff()

```typescript
prepareForVideoHandoff() => Promise<void>
```

Release native audio session / focus so a video player can own playback.

**Android:** pauses current track, abandons audio focus, stores head position. Does not stop the foreground media service.
**iOS:** pauses, captures head position, deactivates `AVAudioSession` with `notifyOthersOnDeactivation`.
**Web:** pauses HTMLAudioElement and stores `currentTime`.

Call immediately before native video starts (e.g. your video plugin's init method).

--------------------


### resumeAfterVideoHandoff(...)

```typescript
resumeAfterVideoHandoff(options: ResumeAfterVideoHandoffOptions) => Promise<ResumeAfterVideoHandoffResult>
```

Re-arm native audio after video ends or, on Android, prewarm the media service before video starts.

**Without `prewarm` (typical exit path):**
- Android: when `play` is true (default), re-acquires focus and resumes at `position`. When `resumed` is `true`, JS should skip redundant `seekTo`/`play`. When `play` is false, clears handoff retain and returns `{ resumed: false }` so JS can seek without playing.
- iOS: restores pinned track, reactivates `AVAudioSession`, seeks to `position`, and when `play` is true starts playback (seek-then-play). Returns `{ resumed: true }` when native handled the handoff.
- Web: stores position only (no native session); returns `{ resumed: false }`.

**With `prewarm: true` (Android, before video):** starts `MediaService` in foreground at `position` but stays silent — no audio focus, no audible playback. Always returns `{ resumed: false }`.

| Param         | Type                                                                                      |
| ------------- | ----------------------------------------------------------------------------------------- |
| **`options`** | <code><a href="#resumeaftervideohandoffoptions">ResumeAfterVideoHandoffOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#resumeaftervideohandoffresult">ResumeAfterVideoHandoffResult</a>&gt;</code>

--------------------


### getLastKnownPosition()

```typescript
getLastKnownPosition() => Promise<GetLastKnownPositionResult>
```

Return the audio head position (seconds) captured during the most recent `prepareForVideoHandoff`
or passed to `resumeAfterVideoHandoff`.

**Returns:** <code>Promise&lt;<a href="#getlastknownpositionresult">GetLastKnownPositionResult</a>&gt;</code>

--------------------


### Interfaces


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


#### PlaylistStatusChangeCallbackArg

| Prop         | Type                                                                  |
| ------------ | --------------------------------------------------------------------- |
| **`action`** | <code>string</code>                                                   |
| **`status`** | <code><a href="#onstatuscallbackdata">OnStatusCallbackData</a></code> |


#### OnStatusCallbackData

Encapsulates the data received by an onStatus callback

| Prop          | Type                                                                                                                                                                                                                        | Description                                                                                                                                                                                                                                                   |
| ------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`trackId`** | <code>string</code>                                                                                                                                                                                                         | The ID of this track. If the track is null or has completed, this value is "NONE" If the playlist is completed, this value is "INVALID"                                                                                                                       |
| **`msgType`** | <code><a href="#rmxaudiostatusmessage">RmxAudioStatusMessage</a></code>                                                                                                                                                     | The type of status update                                                                                                                                                                                                                                     |
| **`value`**   | <code><a href="#onstatuscallbackupdatedata">OnStatusCallbackUpdateData</a> \| <a href="#onstatustrackchangeddata">OnStatusTrackChangedData</a> \| <a href="#onstatuserrorcallbackdata">OnStatusErrorCallbackData</a></code> | The status payload. For all updates except ERROR, the data package is described by <a href="#onstatuscallbackupdatedata">OnStatusCallbackUpdateData</a>. For Errors, the data is shaped as <a href="#onstatuserrorcallbackdata">OnStatusErrorCallbackData</a> |


#### OnStatusCallbackUpdateData

Contains the current track status as of the moment an onStatus update event is emitted.

| Prop                  | Type                                                                               | Description                                                                                                                                                                                                                                                                                                                                               |
| --------------------- | ---------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`trackId`**         | <code>string</code>                                                                | The ID of this track corresponding to this event. If the track is null or has completed, this value is "NONE". This will happen when skipping to the beginning or end of the playlist. If the playlist is completed, this value is "INVALID"                                                                                                              |
| **`isStream`**        | <code>boolean</code>                                                               | Boolean indicating whether this is a streaming track.                                                                                                                                                                                                                                                                                                     |
| **`currentIndex`**    | <code>number</code>                                                                | The current index of the track in the playlist.                                                                                                                                                                                                                                                                                                           |
| **`status`**          | <code>'error' \| 'unknown' \| 'ready' \| 'playing' \| 'loading' \| 'paused'</code> | The current status of the track, as a string. This is used to summarize the various event states that a track can be in; e.g. "playing" is true for any number of track statuses. The Javascript interface takes care of this for you; this field is here only for reference.                                                                             |
| **`currentPosition`** | <code>number</code>                                                                | Current playback position of the reported track.                                                                                                                                                                                                                                                                                                          |
| **`duration`**        | <code>number</code>                                                                | The known duration of the reported track. For streams or malformed MP3's, this value will be 0.                                                                                                                                                                                                                                                           |
| **`playbackPercent`** | <code>number</code>                                                                | Progress of track playback, as a percent, in the range 0 - 100                                                                                                                                                                                                                                                                                            |
| **`bufferPercent`**   | <code>number</code>                                                                | Buffering progress of the track, as a percent, in the range 0 - 100                                                                                                                                                                                                                                                                                       |
| **`bufferStart`**     | <code>number</code>                                                                | The starting position of the buffering progress. For now, this is always reported as 0.                                                                                                                                                                                                                                                                   |
| **`bufferEnd`**       | <code>number</code>                                                                | The maximum position, in seconds, of the track buffer. For now, only the buffer with the maximum playback position is reported, even if there are other segments (due to seeking, for example). Practically speaking you don't need to worry about that, as in both implementations the minor gaps are automatically filled in by the underlying players. |


#### OnStatusTrackChangedData

Reports information about the playlist state when a track changes.
Includes the new track, its index, and the state of the playlist.

| Prop                | Type                                              | Description                                                                                                                |
| ------------------- | ------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| **`currentItem`**   | <code><a href="#audiotrack">AudioTrack</a></code> | The new track that has been selected. May be null if you are at the end of the playlist, or the playlist has been emptied. |
| **`currentIndex`**  | <code>number</code>                               | The 0-based index of the new track. If the playlist has ended or been cleared, this will be -1.                            |
| **`isAtEnd`**       | <code>boolean</code>                              | Indicates whether the playlist is now currently at the last item in the list.                                              |
| **`isAtBeginning`** | <code>boolean</code>                              | Indicates whether the playlist is now at the first item in the list                                                        |
| **`hasNext`**       | <code>boolean</code>                              | Indicates if there are additional playlist items after the current item.                                                   |
| **`hasPrevious`**   | <code>boolean</code>                              | Indicates if there are any items before this one in the playlist.                                                          |


#### AudioTrack

An audio track for playback by the playlist.

| Prop                | Type                                                                  | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| ------------------- | --------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`isStream`**      | <code>boolean</code>                                                  | This item is a streaming asset. Make sure this is set to true for stream URLs, otherwise you will get odd behavior when the asset is paused. This only affects pause/resume buffering behavior. It does not select how the source is parsed - use `mimeType` for that.                                                                                                                                                                                            |
| **`trackId`**       | <code>string</code>                                                   | trackId is optional and if not passed in, an auto-generated UUID will be used.                                                                                                                                                                                                                                                                                                                                                                                    |
| **`assetUrl`**      | <code>string</code>                                                   | URL of the asset; can be local, a URL, or a streaming URL. If the asset is a stream, make sure that isStream is set to true, otherwise the plugin can't properly handle the item's buffer.                                                                                                                                                                                                                                                                        |
| **`mimeType`**      | <code>string</code>                                                   | Optional container hint for sources whose URL carries no usable file extension, e.g. `'application/x-mpegURL'` for an HLS playlist served from an extensionless URL. Leave this unset for ordinary progressive sources - Android sniffs the content and web reads the URL, both of which handle MP3/AAC/OGG streams without a hint. HLS is detected automatically when the URL path ends in `.m3u8`. No-op on iOS, where AVFoundation determines the type itself. |
| **`albumArt`**      | <code>string</code>                                                   | The local or remote URL to an image asset to be shown for this track. If this is null, the plugin's default image is used.                                                                                                                                                                                                                                                                                                                                        |
| **`artist`**        | <code>string</code>                                                   | The track's artist                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| **`album`**         | <code>string</code>                                                   | Album the track belongs to                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| **`title`**         | <code>string</code>                                                   | Title of the track                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| **`startPosition`** | <code>number</code>                                                   | Last known playback position to resume from, in seconds, when this track becomes current via a native skip-to-next/previous (e.g. OS notification, headset button, Android Auto/CarPlay, or the in-app Next/Previous buttons). Optional; defaults to 0.                                                                                                                                                                                                           |
| **`drm`**           | <code><a href="#audiotrackdrmoptions">AudioTrackDrmOptions</a></code> | Optional DRM descriptor fields (API names). Android attaches Widevine through a host-registered provider. Token URL, heartbeat URL, and Bearer live on that provider, not here. FairPlay fields may be present and are ignored on Android. iOS and web refuse items that set this.                                                                                                                                                                                |


#### AudioTrackDrmOptions

| Prop                         | Type                                                                          | Description                                                          |
| ---------------------------- | ----------------------------------------------------------------------------- | -------------------------------------------------------------------- |
| **`widevineLicenseUrl`**     | <code>string</code>                                                           |                                                                      |
| **`playbackSessionId`**      | <code>string</code>                                                           |                                                                      |
| **`renewalCredential`**      | <code>string</code>                                                           |                                                                      |
| **`streamLimit`**            | <code><a href="#audiotrackdrmstreamlimit">AudioTrackDrmStreamLimit</a></code> |                                                                      |
| **`fairplayLicenseUrl`**     | <code>string</code>                                                           | Present on the descriptor; ignored on Android (FairPlay is Epic 58). |
| **`fairplayCertificateUrl`** | <code>string</code>                                                           |                                                                      |


#### AudioTrackDrmStreamLimit

Optional playlist-item DRM fields. Names match the playback API and video plugin.
Token / heartbeat URLs and Bearer are supplied by the host provider (57.6).

| Prop                           | Type                |
| ------------------------------ | ------------------- |
| **`mode`**                     | <code>string</code> |
| **`renewalIntervalSeconds`**   | <code>number</code> |
| **`heartbeatIntervalSeconds`** | <code>number</code> |


#### OnStatusErrorCallbackData

Represents an error reported by the onStatus callback.

| Prop          | Type                                                              | Description                                                                                                                                                                                                        |
| ------------- | ----------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **`code`**    | <code><a href="#rmxaudioerrortype">RmxAudioErrorType</a></code>   | Error code                                                                                                                                                                                                         |
| **`message`** | <code>string</code>                                               | The error, as a message                                                                                                                                                                                            |
| **`error`**   | <code><a href="#audiotrackdrmerror">AudioTrackDrmError</a></code> | Typed DRM failure when the host provider reports one. Exactly one of `blockedByStreamLimit` \| `notEntitled` \| `expired` \| `network` \| `unknown`. Unknown strings are coerced to `unknown`. Not a new listener. |


#### AudioPlayerOptions

Options governing the overall behavior of the audio player plugin

| Prop                     | Type                                                                | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| ------------------------ | ------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **`verbose`**            | <code>boolean</code>                                                | Should the plugin's javascript dump the status message stream to the javascript console?                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| **`resetStreamOnPause`** | <code>boolean</code>                                                | If true, when pausing a live stream, play will continue from the LIVE POSITION (e.g. the stream jumps forward to the current point in time, rather than picking up where it left off when you paused). If false, the stream will continue where you paused. The drawback of doing this is that when the audio buffer fills, it will jump forward to the current point in time, cause a disjoint in playback. Default is true.                                                                                                                                |
| **`stallTimeoutMs`**     | <code>number</code>                                                 | How long, in milliseconds, playback position may fail to advance while the player reports it is playing before the plugin declares the track stalled and emits `RMXSTATUS_STALLED` (issue #143). Each platform's native "playing" signal (Android ExoPlayer's `playbackState`/`isPlaying`, iOS's `AVPlayerItemPlaybackStalledNotification`, the browser's `waiting`/`stalled` events) can fail to fire on a mid-stream network loss, so all three platforms poll actual position as a backstop and use this shared threshold for it. Default is 10000 (10s). |
| **`options`**            | <code><a href="#notificationoptions">NotificationOptions</a></code> | Further options for notifications                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |


#### NotificationOptions

| Prop       | Type                |
| ---------- | ------------------- |
| **`icon`** | <code>string</code> |


#### PlaylistOptions

| Prop          | Type                                                                |
| ------------- | ------------------------------------------------------------------- |
| **`items`**   | <code>AudioTrack[]</code>                                           |
| **`options`** | <code><a href="#playlistitemoptions">PlaylistItemOptions</a></code> |


#### PlaylistItemOptions

Options governing how the items are managed when using setPlaylistItems
to update the playlist. This is typically useful if you are retaining items
that were in the previous list.

| Prop                   | Type                 | Description                                                                                                                                                              |
| ---------------------- | -------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **`retainPosition`**   | <code>boolean</code> | If true, the plugin will continue playback from the current playback position after setting the items to the playlist.                                                   |
| **`playFromPosition`** | <code>number</code>  | If retainPosition is true, this value will tell the plugin the exact time to start from, rather than letting the plugin decide based on current playback.                |
| **`playFromId`**       | <code>string</code>  | If retainPosition is true, this value will tell the plugin the uid of the "current" item to start from, rather than letting the plugin decide based on current playback. |
| **`startPaused`**      | <code>boolean</code> | If playback should immediately begin when calling setPlaylistItems on the plugin. Default is false;                                                                      |


#### AddItemOptions

| Prop        | Type                                              | Description                                 |
| ----------- | ------------------------------------------------- | ------------------------------------------- |
| **`item`**  | <code><a href="#audiotrack">AudioTrack</a></code> |                                             |
| **`index`** | <code>number</code>                               | 0-based index to insert at. Omit to append. |


#### MoveItemOptions

| Prop       | Type                | Description                                |
| ---------- | ------------------- | ------------------------------------------ |
| **`from`** | <code>number</code> | Source index (0-based).                    |
| **`to`**   | <code>number</code> | Destination index (0-based) after removal. |


#### ReplaceItemOptions

| Prop        | Type                                              | Description                                                                |
| ----------- | ------------------------------------------------- | -------------------------------------------------------------------------- |
| **`item`**  | <code><a href="#audiotrack">AudioTrack</a></code> | Replacement track data. When `trackId` is omitted the existing id is kept. |
| **`index`** | <code>number</code>                               | Index of the track to replace (preferred over `id`).                       |
| **`id`**    | <code>string</code>                               | Id of the track to replace.                                                |


#### AddAllItemOptions

| Prop        | Type                      |
| ----------- | ------------------------- |
| **`items`** | <code>AudioTrack[]</code> |


#### RemoveItemOptions

| Prop        | Type                |
| ----------- | ------------------- |
| **`id`**    | <code>string</code> |
| **`index`** | <code>number</code> |


#### RemoveItemsOptions

| Prop        | Type                             |
| ----------- | -------------------------------- |
| **`items`** | <code>RemoveItemOptions[]</code> |


#### GetPlaylistResult

| Prop        | Type                      |
| ----------- | ------------------------- |
| **`items`** | <code>AudioTrack[]</code> |


#### SeekToOptions

| Prop           | Type                |
| -------------- | ------------------- |
| **`position`** | <code>number</code> |


#### PlayByIndexOptions

| Prop           | Type                |
| -------------- | ------------------- |
| **`index`**    | <code>number</code> |
| **`position`** | <code>number</code> |


#### PlayByIdOptions

| Prop           | Type                |
| -------------- | ------------------- |
| **`id`**       | <code>string</code> |
| **`position`** | <code>number</code> |


#### SelectByIndexOptions

| Prop           | Type                |
| -------------- | ------------------- |
| **`index`**    | <code>number</code> |
| **`position`** | <code>number</code> |


#### SelectByIdOptions

| Prop           | Type                |
| -------------- | ------------------- |
| **`id`**       | <code>string</code> |
| **`position`** | <code>number</code> |


#### SetPlaybackVolumeOptions

| Prop         | Type                |
| ------------ | ------------------- |
| **`volume`** | <code>number</code> |


#### SetLoopOptions

| Prop       | Type                 |
| ---------- | -------------------- |
| **`loop`** | <code>boolean</code> |


#### SetPlaybackRateOptions

| Prop       | Type                |
| ---------- | ------------------- |
| **`rate`** | <code>number</code> |


#### ResumeAfterVideoHandoffResult

| Prop          | Type                 | Description                                                                                                                                                                                                                              |
| ------------- | -------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`resumed`** | <code>boolean</code> | `true` when native already handled seek (and play when requested) in place. When `true`, JS should skip redundant `seekTo` / `play` to avoid a stutter. `false` on web, prewarm, and paused Android handoff (native does not auto-play). |


#### ResumeAfterVideoHandoffOptions

| Prop           | Type                 | Description                                                                                                                                                                                                                                                                                                       |
| -------------- | -------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`position`** | <code>number</code>  | Resume position in seconds (video exit head or saved audio position).                                                                                                                                                                                                                                             |
| **`prewarm`**  | <code>boolean</code> | **Android only.** When `true`, promote `MediaService` to foreground and prepare at `position` without requesting audio focus or playing audio. Use immediately after `prepareForVideoHandoff` and before native video starts, while the app is still foregrounded. Ignored on iOS (no-op). Not applicable on web. |
| **`play`**     | <code>boolean</code> | When `true`, native starts audible playback after seeking to `position`. When `false` (paused video exit), native must not start playback. iOS defaults to `false` when omitted; Android defaults to `true` for legacy callers.                                                                                   |


#### GetLastKnownPositionResult

| Prop           | Type                |
| -------------- | ------------------- |
| **`position`** | <code>number</code> |


### Type Aliases


#### PlaylistStatusChangeCallback

<code>(data: <a href="#playliststatuschangecallbackarg">PlaylistStatusChangeCallbackArg</a>): void</code>


#### AudioTrackDrmError

Exactly the five DRM error discriminators. Emitted on the existing `status`
channel as `value.error` (do not overload numeric <a href="#rmxaudioerrortype">`RmxAudioErrorType`</a> 0–4).

<code>'blockedByStreamLimit' | 'notEntitled' | 'expired' | 'network' | 'unknown'</code>


### Enums


#### RmxAudioStatusMessage

| Members                            | Value            | Description                                                                                                                                                                                                                                                                                                                                                                                                     |
| ---------------------------------- | ---------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`RMXSTATUS_NONE`**               | <code>0</code>   | The starting state of the plugin. You will never see this value; it changes before the callbacks are even registered to report changes to this value.                                                                                                                                                                                                                                                           |
| **`RMXSTATUS_REGISTER`**           | <code>1</code>   | Raised when the plugin registers the callback handler for onStatus callbacks. You will probably not be able to see this (nor do you need to).                                                                                                                                                                                                                                                                   |
| **`RMXSTATUS_INIT`**               | <code>2</code>   | Reserved for future use                                                                                                                                                                                                                                                                                                                                                                                         |
| **`RMXSTATUS_ERROR`**              | <code>5</code>   | Indicates an error is reported in the 'value' field.                                                                                                                                                                                                                                                                                                                                                            |
| **`RMXSTATUS_LOADING`**            | <code>10</code>  | The reported track is being loaded by the player                                                                                                                                                                                                                                                                                                                                                                |
| **`RMXSTATUS_CANPLAY`**            | <code>11</code>  | The reported track is able to begin playback                                                                                                                                                                                                                                                                                                                                                                    |
| **`RMXSTATUS_LOADED`**             | <code>15</code>  | The reported track has loaded 100% of the file (either from disc or network)                                                                                                                                                                                                                                                                                                                                    |
| **`RMXSTATUS_STALLED`**            | <code>20</code>  | Playback has stalled - the player is still trying, but position is not advancing. Raised on all three platforms: from each platform's own stall signal where it fires, and otherwise from a position-freeze poll gated by <a href="#audioplayeroptions">`AudioPlayerOptions.stallTimeoutMs`</a>. Emitted once per stall episode; the track's reported `status` reads `"stalled"` until position advances again. |
| **`RMXSTATUS_BUFFERING`**          | <code>25</code>  | Reports an update in the reported track's buffering status                                                                                                                                                                                                                                                                                                                                                      |
| **`RMXSTATUS_PLAYING`**            | <code>30</code>  | The reported track has started (or resumed) playing                                                                                                                                                                                                                                                                                                                                                             |
| **`RMXSTATUS_PAUSE`**              | <code>35</code>  | The reported track has been paused, either by the user or by the system. (iOS only): This value is raised when MP3's are malformed (but still playable). These require the user to explicitly press play again. This can be worked around and is on the TODO list.                                                                                                                                              |
| **`RMXSTATUS_PLAYBACK_POSITION`**  | <code>40</code>  | Reports a change in the reported track's playback position. Suppressed while the WebView is backgrounded, and (Android/iOS) while the track is stalled - so a frozen position is never reported as if playback were healthy.                                                                                                                                                                                    |
| **`RMXSTATUS_SEEK`**               | <code>45</code>  | The reported track has seeked. On Android, only the plugin consumer can generate this (Notification controls on Android do not include a seek bar). On iOS, the Command Center includes a seek bar so this will be reported when the user has seeked via Command Center.                                                                                                                                        |
| **`RMXSTATUS_COMPLETED`**          | <code>50</code>  | The reported track has completed playback.                                                                                                                                                                                                                                                                                                                                                                      |
| **`RMXSTATUS_DURATION`**           | <code>55</code>  | The reported track's duration has changed. This is raised once, when duration is updated for the first time. For streams, this value is never reported.                                                                                                                                                                                                                                                         |
| **`RMXSTATUS_STOPPED`**            | <code>60</code>  | All playback has stopped, probably because the plugin is shutting down.                                                                                                                                                                                                                                                                                                                                         |
| **`RMX_STATUS_SKIP_FORWARD`**      | <code>90</code>  | The playlist has skipped forward to the next track. On both Android and iOS, this will be raised if the notification controls/Command Center were used to skip. It is unlikely you need to consume this event: RMXSTATUS_TRACK_CHANGED is also reported when this occurs, so you can generalize your track change handling in one place.                                                                        |
| **`RMX_STATUS_SKIP_BACK`**         | <code>95</code>  | The playlist has skipped back to the previous track. On both Android and iOS, this will be raised if the notification controls/Command Center were used to skip. It is unlikely you need to consume this event: RMXSTATUS_TRACK_CHANGED is also reported when this occurs, so you can generalize your track change handling in one place.                                                                       |
| **`RMXSTATUS_TRACK_CHANGED`**      | <code>100</code> | Reported when the current track has changed in the native player. This event contains full data about the new track, including the index and the actual track itself. The type of the 'value' field in this case is <a href="#onstatustrackchangeddata">OnStatusTrackChangedData</a>.                                                                                                                           |
| **`RMXSTATUS_PLAYLIST_COMPLETED`** | <code>105</code> | The entire playlist has completed playback. After this event has been raised, the current item is set to null and the current index to -1.                                                                                                                                                                                                                                                                      |
| **`RMXSTATUS_ITEM_ADDED`**         | <code>110</code> | An item has been added to the playlist. For the setPlaylistItems and addAllItems methods, this status is raised once for every track in the collection.                                                                                                                                                                                                                                                         |
| **`RMXSTATUS_ITEM_REMOVED`**       | <code>115</code> | An item has been removed from the playlist. For the removeItems and clearAllItems methods, this status is raised once for every track that was removed.                                                                                                                                                                                                                                                         |
| **`RMXSTATUS_ITEM_MOVED`**         | <code>112</code> | An item has been moved within the playlist.                                                                                                                                                                                                                                                                                                                                                                     |
| **`RMXSTATUS_ITEM_REPLACED`**      | <code>113</code> | An item in the playlist has been replaced in place.                                                                                                                                                                                                                                                                                                                                                             |
| **`RMXSTATUS_PLAYLIST_CLEARED`**   | <code>120</code> | All items have been removed from the playlist                                                                                                                                                                                                                                                                                                                                                                   |
| **`RMXSTATUS_VIEWDISAPPEAR`**      | <code>200</code> | Just for testing.. you don't need this and in fact can never receive it, the plugin is destroyed before it can be raised.                                                                                                                                                                                                                                                                                       |


#### RmxAudioErrorType

| Members                     | Value          | Description                                                                                                                                                                                                                                                                                                                                                  |
| --------------------------- | -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **`RMXERR_NONE_ACTIVE`**    | <code>0</code> | No active source to play. You are unlikely to see this.                                                                                                                                                                                                                                                                                                      |
| **`RMXERR_ABORTED`**        | <code>1</code> | Playback was aborted, typically because the source was torn down mid-load. Web only, from `MediaError.MEDIA_ERR_ABORTED`.                                                                                                                                                                                                                                    |
| **`RMXERR_NETWORK`**        | <code>2</code> | The source is fine but could not be reached or read - a dropped connection, a timeout, a bad HTTP status. **This is the retry-able error**: the same URL may well succeed once connectivity returns. Android: media3's `ERROR_CODE_IO_*` network codes. iOS: an `NSError` in `NSURLErrorDomain` / `NSPOSIXErrorDomain`. Web: `MediaError.MEDIA_ERR_NETWORK`. |
| **`RMXERR_DECODE`**         | <code>3</code> | The media was reached but could not be decoded. Not retry-able. Android: media3's decoder error codes. iOS: `AVFoundationErrorDomain` decode and parse failures. Web: `MediaError.MEDIA_ERR_DECODE`.                                                                                                                                                         |
| **`RMXERR_NONE_SUPPORTED`** | <code>4</code> | The source itself is unusable - missing, the wrong container, or a body that does not parse as what it claims to be. Not retry-able; skip the track. Also the fallback when a platform reports a failure that does not classify.                                                                                                                             |

</docgen-api>
