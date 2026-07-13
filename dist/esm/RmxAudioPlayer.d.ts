import { RmxAudioStatusMessage } from './Constants';
import { AudioPlayerEventHandler, AudioPlayerEventHandlers, AudioPlayerOptions, AudioTrack, AudioTrackRemoval, OnStatusCallback, OnStatusCallbackUpdateData, OnStatusErrorCallbackData, OnStatusTrackChangedData, PlaylistItemOptions } from './interfaces';
export declare class RmxAudioPlayer {
    handlers: AudioPlayerEventHandlers;
    options: AudioPlayerOptions;
    private readonly _initPromise;
    private _readyResolve;
    private _readyReject;
    private _currentState;
    private _hasError;
    private _hasLoaded;
    private _currentItem;
    /**
     * The current summarized state of the player, as a string. It is preferred that you use the 'isX' accessors,
     * because they properly interpret the range of these values, but this field is exposed if you wish to observe
     * or interrogate it.
     */
    get currentState(): "unknown" | "ready" | "error" | "playing" | "loading" | "paused" | "stopped";
    get currentTrack(): AudioTrack | null;
    /**
     * If the playlist is currently playling a track.
     */
    get isPlaying(): boolean;
    /**
     * True if the playlist is currently paused
     */
    get isPaused(): boolean;
    /**
     * True if the plugin is currently loading its *current* track.
     * On iOS, many tracks are loaded in parallel, so this only reports for the *current item*, e.g.
     * the item that will begin playback if you press pause.
     * If you need track-specific data, it is better to watch the onStatus stream and watch for RMXSTATUS_LOADING,
     * which will be raised independently & simultaneously for every track in the playlist.
     * On Android, tracks are only loaded as they begin playback, so this value and RMXSTATUS_LOADING should always
     * apply to the same track.
     */
    get isLoading(): boolean;
    /**
     * True if the *currently playing track* has been loaded and can be played (this includes if it is *currently playing*).
     */
    get hasLoaded(): boolean;
    /**
     * True if the *current track* has reported an error. In almost all cases,
     * the playlist will automatically skip forward to the next track, in which case you will also receive
     * an RMXSTATUS_TRACK_CHANGED event.
     */
    get hasError(): boolean;
    /**
     * Creates a new RmxAudioPlayer instance.
     */
    constructor();
    /**
     * Player interface
     */
    /**
     * Returns a promise that resolves when the plugin is ready.
     */
    ready: () => Promise<void>;
    initialize: () => Promise<void>;
    /**
     * Sets the player options. This can be called at any time and is not required before playback can be initiated.
     */
    setOptions: (options: AudioPlayerOptions) => Promise<void>;
    /**
     * Playlist item management
     */
    /**
     * Sets the entire list of tracks to be played by the playlist.
     * This will clear all previous items from the playlist.
     * If you pass options.retainPosition = true, the current playback position will be
     * recorded and used when playback restarts. This can be used, for example, to set the
     * playlist to a new set of tracks, but retain the currently-playing item to avoid skipping.
     */
    setPlaylistItems: (items: AudioTrack[], options?: PlaylistItemOptions) => Promise<void>;
    /**
     * Add a single track to the end of the playlist
     */
    addItem: (trackItem: AudioTrack) => Promise<void>;
    /**
     * Adds the list of tracks to the end of the playlist.
     */
    addAllItems: (items: AudioTrack[]) => Promise<void>;
    /**
     * Removes a track from the playlist. If this is the currently playing item, the next item will automatically begin playback.
     */
    removeItem: (removeItem: AudioTrackRemoval) => Promise<void>;
    /**
     * Removes all given tracks from the playlist; these can be specified either by trackId or trackIndex. If the removed items
     * include the currently playing item, the next available item will automatically begin playing.
     */
    removeItems: (items: AudioTrackRemoval[]) => Promise<void>;
    /**
     * Clear the entire playlist. This will result in the STOPPED event being raised.
     */
    clearAllItems: () => Promise<void>;
    /**
     * Playback management
     */
    /**
     * Begin playback. If no tracks have been added, this has no effect.
     */
    play: () => Promise<void>;
    /**
     * Play the track at the given index. If the track does not exist, this has no effect.
     */
    playTrackByIndex: (index: number, position?: number) => Promise<void>;
    /**
     * Play the track matching the given trackId. If the track does not exist, this has no effect.
     */
    playTrackById: (id: string, position?: number) => Promise<void>;
    /**
     * Play the track matching the given trackId. If the track does not exist, this has no effect.
     */
    selectTrackByIndex: (index: number, position?: number) => Promise<void>;
    /**
     * Play the track matching the given trackId. If the track does not exist, this has no effect.
     */
    selectTrackById: (id: string, position?: number) => Promise<void>;
    /**
     * Pause playback
     */
    pause: () => Promise<void>;
    /**
     * Skip to the next track. If you are already at the end, and loop is false, this has no effect.
     * If you are at the end, and loop is true, playback will begin at the beginning of the playlist.
     */
    skipForward: () => Promise<void>;
    /**
     * Skip to the previous track. If you are already at the beginning, this has no effect.
     */
    skipBack: () => Promise<void>;
    /**
     * Seek to the given position in the currently playing track. If the value exceeds the track length,
     * the track will complete and playback of the next track will begin.
     */
    seekTo: (position: number) => Promise<void>;
    /**
     * Set the playback speed; a float value between [-1, 1] inclusive. If set to 0, this pauses playback.
     */
    setPlaybackRate: (rate: number) => Promise<void>;
    /**
     * Set the playback volume. Float value between [0, 1] inclusive.
     * On both Android and iOS, this sets the volume of the media stream, which can be externally
     * controlled by setting the overall hardware volume.
     */
    setVolume: (volume: number) => Promise<void>;
    /**
     * Sets a flag indicating whether the playlist should loop back to the beginning once it reaches the end.
     */
    setLoop: (loop: boolean) => Promise<void>;
    /**
     * Status event handling
     */
    /**
     * @internal
     * Call this function to emit an onStatus event via the on('status') handler.
     * Internal use only, to raise events received from the native interface.
     */
    protected onStatus(trackId: string, type: RmxAudioStatusMessage, value: OnStatusCallbackUpdateData | OnStatusTrackChangedData | OnStatusErrorCallbackData): void;
    /**
     * Subscribe to events raised by the plugin, e.g. on('status', (data) => { ... }),
     * For now, only 'status' is supported.
     *
     * @param eventName Name of event to subscribe to.
     * @param callback The callback function to receive the event data
     */
    on(eventName: 'status', callback: OnStatusCallback): void;
    /**
     * Remove an event handler from the plugin
     * @param eventName The name of the event whose subscription is to be removed
     * @param handle The event handler to destroy. Ensure that this is the SAME INSTANCE as the handler
     * that was passed in to create the subscription!
     */
    off(eventName: string, handle: AudioPlayerEventHandler): void;
    /**
     * @internal
     * Raises an event via the corresponding event handler. Internal use only.
     * @param args Event args to pass through to the handler.
     */
    protected emit(...args: any[]): boolean;
}
