import {
    RmxAudioStatusMessage,
    RmxAudioStatusMessageDescriptions
} from './Constants';
import {
    RemoveItemOptions
} from './definitions';
import {
    AudioPlayerEventHandler,
    AudioPlayerEventHandlers,
    AudioPlayerOptions,
    AudioTrack,
    AudioTrackRemoval,
    OnStatusCallback,
    OnStatusCallbackData,
    OnStatusCallbackUpdateData,
    OnStatusErrorCallbackData,
    OnStatusTrackChangedData,
    PlaylistItemOptions
} from './interfaces';
import { Playlist } from './plugin';
import { validateTrack, validateTracks } from './utils';


/*!
 * Module dependencies.
 */

const itemStatusChangeTypes = [
    RmxAudioStatusMessage.RMXSTATUS_PLAYBACK_POSITION,
    RmxAudioStatusMessage.RMXSTATUS_DURATION,
    RmxAudioStatusMessage.RMXSTATUS_BUFFERING,
    RmxAudioStatusMessage.RMXSTATUS_CANPLAY,
    RmxAudioStatusMessage.RMXSTATUS_LOADING,
    RmxAudioStatusMessage.RMXSTATUS_LOADED,
    RmxAudioStatusMessage.RMXSTATUS_PAUSE,
    RmxAudioStatusMessage.RMXSTATUS_COMPLETED,
    RmxAudioStatusMessage.RMXSTATUS_ERROR,
];

export class RmxAudioPlayer {
    handlers: AudioPlayerEventHandlers = {};
    options: AudioPlayerOptions = {verbose: false, resetStreamOnPause: true};

    private readonly _initPromise: Promise<void>;
    private _readyResolve: (value?: any | PromiseLike<boolean>) => void = () => {
    };
    private _readyReject: (reason?: any) => void = () => {
    };

    private _currentState: 'unknown' | 'ready' | 'error' | 'playing' | 'loading' | 'paused' | 'stopped' = 'unknown';
    private _hasError: boolean = false;
    private _hasLoaded: boolean = false;
    private _currentItem: AudioTrack | null = null;

    /**
     * The current summarized state of the player, as a string. It is preferred that you use the 'isX' accessors,
     * because they properly interpret the range of these values, but this field is exposed if you wish to observe
     * or interrogate it.
     */
    get currentState() {
        return this._currentState;
    }

    get currentTrack(): AudioTrack | null {
        return this._currentItem;
    }

    /**
     * If the playlist is currently playling a track.
     */
    get isPlaying() {
        return this._currentState === 'playing';
    }

    /**
     * True if the playlist is currently paused
     */
    get isPaused() {
        return this._currentState === 'paused' || this._currentState === 'stopped';
    }

    /**
     * True if the plugin is currently loading its *current* track.
     * On iOS, many tracks are loaded in parallel, so this only reports for the *current item*, e.g.
     * the item that will begin playback if you press pause.
     * If you need track-specific data, it is better to watch the onStatus stream and watch for RMXSTATUS_LOADING,
     * which will be raised independently & simultaneously for every track in the playlist.
     * On Android, tracks are only loaded as they begin playback, so this value and RMXSTATUS_LOADING should always
     * apply to the same track.
     */
    get isLoading() {
        return this._currentState === 'loading';
    }

    /**
     * True if the *currently playing track* has been loaded and can be played (this includes if it is *currently playing*).
     */
    get hasLoaded() {
        return this._hasLoaded;
    }

    /**
     * True if the *current track* has reported an error. In almost all cases,
     * the playlist will automatically skip forward to the next track, in which case you will also receive
     * an RMXSTATUS_TRACK_CHANGED event.
     */
    get hasError() {
        return this._hasError;
    }


    /**
     * Creates a new RmxAudioPlayer instance.
     */
    constructor() {
        this.handlers = {};
        new Promise<void>((resolve) => {
            window.addEventListener('beforeunload', () => resolve(), {once: true});
        }).then(() => Playlist.release());
        this._initPromise = new Promise((resolve, reject) => {
            this._readyResolve = resolve;
            this._readyReject = reject;
        });
    }

    /**
     * Player interface
     */

    /**
     * Returns a promise that resolves when the plugin is ready.
     */
    ready = () => {
        return this._initPromise;
    };


    initialize = async () => {
        Playlist.addListener(
            'status',
            (data: { action: string, status: OnStatusCallbackData }) => {
                if (data.action === 'status') {
                    this.onStatus(data.status.trackId, data.status.msgType, data.status.value);
                } else {
                    console.warn('Unknown audio player onStatus message:', data);
                }
            }
        );

        try {
            await Playlist.initialize();
            this._readyResolve();
        } catch (args) {
            const message = 'Capacitor RMXAUDIOPLAYER: Error initializing:';
            console.warn(message, args);
            this._readyReject();
        }
    };

    /**
     * Sets the player options. This can be called at any time and is not required before playback can be initiated.
     */
    setOptions = (options: AudioPlayerOptions) => {
        this.options = {...this.options, ...options};
        return Playlist.setOptions(this.options);
    };

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
    setPlaylistItems = (items: AudioTrack[], options?: PlaylistItemOptions) => {
        return Playlist.setPlaylistItems({items: validateTracks(items), options: options || {}});
    };

    /**
     * Add a single track to the end of the playlist
     */
    addItem = (trackItem: AudioTrack) => {
        const validTrackItem = validateTrack(trackItem);
        if (!validTrackItem) {
            throw new Error('Provided track is null or not an audio track');
        }
        return Playlist.addItem({item: validTrackItem});
    };

    /**
     * Adds the list of tracks to the end of the playlist.
     */
    addAllItems = (items: AudioTrack[]) => {
        return Playlist.addAllItems({items: validateTracks(items)});
    };

    /**
     * Removes a track from the playlist. If this is the currently playing item, the next item will automatically begin playback.
     */
    removeItem = (removeItem: AudioTrackRemoval) => {
        if (!removeItem) {
            throw new Error('Track removal spec is empty');
        }
        if (!removeItem.trackId && !removeItem.trackIndex) {
            new Error('Track removal spec is invalid');
        }
        return Playlist.removeItem({id: removeItem.trackId!, index: removeItem.trackIndex!});
    };

    /**
     * Removes all given tracks from the playlist; these can be specified either by trackId or trackIndex. If the removed items
     * include the currently playing item, the next available item will automatically begin playing.
     */
    removeItems = (items: AudioTrackRemoval[]) => {
        return Playlist.removeItems({items: items as RemoveItemOptions[]});
    };

    /**
     * Clear the entire playlist. This will result in the STOPPED event being raised.
     */
    clearAllItems = () => {
        return Playlist.clearAllItems();
    };

    /**
     * Playback management
     */

    /**
     * Begin playback. If no tracks have been added, this has no effect.
     */
    play = () => {
        return Playlist.play();
    };

    /**
     * Play the track at the given index. If the track does not exist, this has no effect.
     */

    playTrackByIndex = (index: number, position?: number) => {
        return Playlist.playTrackByIndex({index, position: position || 0});
    };

    /**
     * Play the track matching the given trackId. If the track does not exist, this has no effect.
     */
    playTrackById = (id: string, position?: number) => {
        return Playlist.playTrackById({id, position: position || 0});
    };

    /**
     * Play the track matching the given trackId. If the track does not exist, this has no effect.
     */
    selectTrackByIndex = (index: number, position?: number) => {
        return Playlist.selectTrackByIndex({index, position: position || 0});
    };

    /**
     * Play the track matching the given trackId. If the track does not exist, this has no effect.
     */
    selectTrackById = (id: string, position?: number) => {
        return Playlist.selectTrackById({id, position: position || 0});
    };

    /**
     * Pause playback
     */
    pause = () => {
        return Playlist.pause();
    };

    /**
     * Skip to the next track. If you are already at the end, and loop is false, this has no effect.
     * If you are at the end, and loop is true, playback will begin at the beginning of the playlist.
     */
    skipForward = () => {
        return Playlist.skipForward();
    };

    /**
     * Skip to the previous track. If you are already at the beginning, this has no effect.
     */
    skipBack = () => {
        return Playlist.skipBack();
    };

    /**
     * Seek to the given position in the currently playing track. If the value exceeds the track length,
     * the track will complete and playback of the next track will begin.
     */
    seekTo = (position: number) => {
        return Playlist.seekTo({position});
    };

    /**
     * Set the playback speed; a float value between [-1, 1] inclusive. If set to 0, this pauses playback.
     */
    setPlaybackRate = (rate: number) => {
        return Playlist.setPlaybackRate({rate});
    };

    /**
     * Set the playback volume. Float value between [0, 1] inclusive.
     * On both Android and iOS, this sets the volume of the media stream, which can be externally
     * controlled by setting the overall hardware volume.
     */
    setVolume = (volume: number) => {
        return Playlist.setPlaybackVolume({volume});
    };

    /**
     * Sets a flag indicating whether the playlist should loop back to the beginning once it reaches the end.
     */
    setLoop = (loop: boolean) => {
        return Playlist.setLoop({loop: loop});
    };

    /**
     * Status event handling
     */

    /**
     * @internal
     * Call this function to emit an onStatus event via the on('status') handler.
     * Internal use only, to raise events received from the native interface.
     */
    protected onStatus(trackId: string, type: RmxAudioStatusMessage, value: OnStatusCallbackUpdateData | OnStatusTrackChangedData | OnStatusErrorCallbackData) {
        const status = <OnStatusCallbackData> {msgType: type, trackId: trackId, value: value};
        if (this.options.verbose) {
            console.debug(`RmxAudioPlayer.onStatus: ${RmxAudioStatusMessageDescriptions[type]}(${type}) [${trackId}]: `, value);
        }

        if (status.msgType === RmxAudioStatusMessage.RMXSTATUS_TRACK_CHANGED) {
            this._hasError = false;
            this._hasLoaded = false;
            this._currentState = 'loading';
            this._currentItem = (status.value as OnStatusTrackChangedData)?.currentItem;
        }

        // The plugin's status changes only in response to specific events.
        if (itemStatusChangeTypes.indexOf(status.msgType) >= 0) {
            // Only change the plugin's *current status* if the event being raised is for the current active track.
            if (this._currentItem && this._currentItem.trackId === trackId) {

                if (status.value && (<any> status.value).status) {
                    this._currentState = (<any> status.value).status;
                }

                if (status.msgType === RmxAudioStatusMessage.RMXSTATUS_CANPLAY) {
                    this._hasLoaded = true;
                }

                if (status.msgType === RmxAudioStatusMessage.RMXSTATUS_ERROR) {
                    this._hasError = true;
                }
            }
        }

        this.emit('status', status);
    }

    /**
     * Subscribe to events raised by the plugin, e.g. on('status', (data) => { ... }),
     * For now, only 'status' is supported.
     *
     * @param eventName Name of event to subscribe to.
     * @param callback The callback function to receive the event data
     */
    on(eventName: 'status', callback: OnStatusCallback): void;
    on(eventName: string, callback: AudioPlayerEventHandler) {
        if (!Object.prototype.hasOwnProperty.call(this.handlers, eventName)) {
            this.handlers[eventName] = [];
        }
        this.handlers[eventName].push(callback);
    }

    /**
     * Remove an event handler from the plugin
     * @param eventName The name of the event whose subscription is to be removed
     * @param handle The event handler to destroy. Ensure that this is the SAME INSTANCE as the handler
     * that was passed in to create the subscription!
     */
    off(eventName: string, handle: AudioPlayerEventHandler) {
        if (Object.prototype.hasOwnProperty.call(this.handlers, eventName)) {
            const handleIndex = this.handlers[eventName].indexOf(handle);
            if (handleIndex >= 0) {
                this.handlers[eventName].splice(handleIndex, 1);
            }
        }
    }

    /**
     * @internal
     * Raises an event via the corresponding event handler. Internal use only.
     * @param args Event args to pass through to the handler.
     */
    protected emit(...args: any[]) {
        const eventName: string = args.shift();
        if (!Object.prototype.hasOwnProperty.call(this.handlers, eventName)) {
            return false;
        }

        const handler = this.handlers[eventName];
        for (let i = 0; i < handler.length; i++) {
            const callback = this.handlers[eventName][i];
            if (typeof callback === 'function') {
                callback(...args);
            }
        }

        return true;
    }
}
