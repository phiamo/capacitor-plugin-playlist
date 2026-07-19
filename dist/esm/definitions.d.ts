import { PluginListenerHandle } from '@capacitor/core';
import { AudioPlayerOptions, AudioTrack, PlaylistItemOptions, PlaylistStatusChangeCallback } from './interfaces';
export interface PlaylistPlugin {
    /**
     * Subscribe to native playback status events (track changes, position, errors, etc.).
     *
     * @param eventName Must be `'status'`.
     * @param listenerFunc Callback receiving `{ action, status }` where `status.msgType` is a `RmxAudioStatusMessage`.
     */
    addListener(eventName: 'status', listenerFunc: PlaylistStatusChangeCallback): Promise<PluginListenerHandle>;
    /**
     * Configure plugin behaviour (verbose logging, stream pause handling, notification icon).
     * Can be called at any time; not required before playback.
     */
    setOptions(options: AudioPlayerOptions): Promise<void>;
    /**
     * Initialise the native player, register status callbacks, and arm lock-screen / notification controls.
     * Call once before playback (e.g. on app start).
     */
    initialize(): Promise<void>;
    /**
     * Tear down native resources (audio session, media service, observers).
     * Call when the app no longer needs background audio (e.g. on logout).
     */
    release(): Promise<void>;
    /**
     * Replace the entire playlist. Clears all previous items.
     * Use `options.retainPosition` to keep the current track and playback position.
     */
    setPlaylistItems(options: PlaylistOptions): Promise<void>;
    /**
     * Append a single track to the end of the playlist.
     */
    addItem(options: AddItemOptions): Promise<void>;
    /**
     * Append multiple tracks to the end of the playlist.
     * Raises one `RMXSTATUS_ITEM_ADDED` event per track.
     */
    addAllItems(options: AddAllItemOptions): Promise<void>;
    /**
     * Remove a track by index (preferred) or id.
     * If the removed track is currently playing, the next track starts automatically.
     */
    removeItem(options: RemoveItemOptions): Promise<void>;
    /**
     * Remove multiple tracks in a single batch.
     * If the currently playing track is removed, the next available track starts automatically.
     */
    removeItems(options: RemoveItemsOptions): Promise<void>;
    /**
     * Remove all tracks from the playlist. Raises `RMXSTATUS_PLAYLIST_CLEARED` and `RMXSTATUS_STOPPED`.
     */
    clearAllItems(): Promise<void>;
    /**
     * Return a snapshot of the current playlist items.
     */
    getPlaylist(): Promise<GetPlaylistResult>;
    /**
     * Start or resume playback of the current track.
     * No-op if the playlist is empty.
     */
    play(): Promise<void>;
    /**
     * Pause playback of the current track.
     */
    pause(): Promise<void>;
    /**
     * Skip to the next track. At the end of the playlist, wraps to the beginning when loop is enabled.
     */
    skipForward(): Promise<void>;
    /**
     * Skip to the previous track. No-op when already at the first track.
     */
    skipBack(): Promise<void>;
    /**
     * Seek to a position (seconds) in the currently playing track.
     * If the position exceeds track length, playback advances to the next track.
     */
    seekTo(options: SeekToOptions): Promise<void>;
    /**
     * Jump to the track at the given 0-based index and start playback.
     */
    playTrackByIndex(options: PlayByIndexOptions): Promise<void>;
    /**
     * Jump to the track with the given id and start playback.
     */
    playTrackById(options: PlayByIdOptions): Promise<void>;
    /**
     * Select the track at the given index without necessarily starting playback.
     */
    selectTrackByIndex(options: SelectByIndexOptions): Promise<void>;
    /**
     * Select the track with the given id without necessarily starting playback.
     */
    selectTrackById(options: SelectByIdOptions): Promise<void>;
    /**
     * Set media stream volume. Float in range [0, 1].
     * Hardware volume controls still apply on top of this value.
     */
    setPlaybackVolume(options: SetPlaybackVolumeOptions): Promise<void>;
    /**
     * When true, the playlist loops back to the first track after the last track completes.
     */
    setLoop(options: SetLoopOptions): Promise<void>;
    /**
     * Set playback speed. Float value; 0 pauses, 1 is normal speed.
     */
    setPlaybackRate(options: SetPlaybackRateOptions): Promise<void>;
    /**
     * Release native audio session / focus so a video player can own playback.
     *
     * **Android:** pauses current track, abandons audio focus, stores head position. Does not stop the foreground media service.
     * **iOS:** pauses, captures head position, deactivates `AVAudioSession` with `notifyOthersOnDeactivation`.
     * **Web:** pauses HTMLAudioElement and stores `currentTime`.
     *
     * Call immediately before native video starts (e.g. your video plugin's init method).
     */
    prepareForVideoHandoff(): Promise<void>;
    /**
     * Re-arm native audio after video ends or, on Android, prewarm the media service before video starts.
     *
     * **Without `prewarm` (typical exit path):**
     * - Android: when `play` is true (default), re-acquires focus and resumes at `position`. When `resumed` is `true`, JS should skip redundant `seekTo`/`play`. When `play` is false, clears handoff retain and returns `{ resumed: false }` so JS can seek without playing.
     * - iOS: restores pinned track, reactivates `AVAudioSession`, seeks to `position`, and when `play` is true starts playback (seek-then-play). Returns `{ resumed: true }` when native handled the handoff.
     * - Web: stores position only (no native session); returns `{ resumed: false }`.
     *
     * **With `prewarm: true` (Android, before video):** starts `MediaService` in foreground at `position` but stays silent — no audio focus, no audible playback. Always returns `{ resumed: false }`.
     */
    resumeAfterVideoHandoff(options: ResumeAfterVideoHandoffOptions): Promise<ResumeAfterVideoHandoffResult>;
    /**
     * Return the audio head position (seconds) captured during the most recent `prepareForVideoHandoff`
     * or passed to `resumeAfterVideoHandoff`.
     */
    getLastKnownPosition(): Promise<GetLastKnownPositionResult>;
}
export interface ResumeAfterVideoHandoffOptions {
    /** Resume position in seconds (video exit head or saved audio position). */
    position: number;
    /**
     * **Android only.** When `true`, promote `MediaService` to foreground and prepare at `position`
     * without requesting audio focus or playing audio. Use immediately after `prepareForVideoHandoff`
     * and before native video starts, while the app is still foregrounded.
     * Ignored on iOS (no-op). Not applicable on web.
     */
    prewarm?: boolean;
    /**
     * When `true`, native starts audible playback after seeking to `position`.
     * When `false` (paused video exit), native must not start playback.
     * iOS defaults to `false` when omitted; Android defaults to `true` for legacy callers.
     */
    play?: boolean;
}
export interface ResumeAfterVideoHandoffResult {
    /**
     * `true` when native already handled seek (and play when requested) in place.
     * When `true`, JS should skip redundant `seekTo` / `play` to avoid a stutter.
     * `false` on web, prewarm, paused Android handoff, and Android last-resort `beginPlayback`.
     */
    resumed: boolean;
}
export interface GetLastKnownPositionResult {
    position: number;
}
export interface PlaylistOptions {
    items: Array<AudioTrack>;
    options: PlaylistItemOptions;
}
export interface AddItemOptions {
    item: AudioTrack;
}
export interface AddAllItemOptions {
    items: Array<AudioTrack>;
}
export interface RemoveItemOptions {
    id?: string;
    index?: number;
}
export interface RemoveItemsOptions {
    items: Array<RemoveItemOptions>;
}
export interface SeekToOptions {
    position: number;
}
export interface PlayByIndexOptions {
    index: number;
    position?: number;
}
export interface PlayByIdOptions {
    id: string;
    position?: number;
}
export interface SelectByIndexOptions {
    index: number;
    position?: number;
}
export interface SelectByIdOptions {
    id: string;
    position?: number;
}
export interface SetPlaybackVolumeOptions {
    volume: number;
}
export interface SetLoopOptions {
    loop: boolean;
}
export interface SetPlaybackRateOptions {
    rate: number;
}
export interface GetPlaylistResult {
    items: Array<AudioTrack>;
}
