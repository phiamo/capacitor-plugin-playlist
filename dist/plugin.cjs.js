'use strict';

var core = require('@capacitor/core');

/**
 * Enum describing the possible errors that may come from the plugins
 */
exports.RmxAudioErrorType = void 0;
(function (RmxAudioErrorType) {
    RmxAudioErrorType[RmxAudioErrorType["RMXERR_NONE_ACTIVE"] = 0] = "RMXERR_NONE_ACTIVE";
    RmxAudioErrorType[RmxAudioErrorType["RMXERR_ABORTED"] = 1] = "RMXERR_ABORTED";
    RmxAudioErrorType[RmxAudioErrorType["RMXERR_NETWORK"] = 2] = "RMXERR_NETWORK";
    RmxAudioErrorType[RmxAudioErrorType["RMXERR_DECODE"] = 3] = "RMXERR_DECODE";
    RmxAudioErrorType[RmxAudioErrorType["RMXERR_NONE_SUPPORTED"] = 4] = "RMXERR_NONE_SUPPORTED";
})(exports.RmxAudioErrorType || (exports.RmxAudioErrorType = {}));
/**
 * String descriptions corresponding to the RmxAudioErrorType values
 */
const RmxAudioErrorTypeDescriptions = [
    'No Active Sources',
    'Aborted',
    'Network',
    'Failed to Decode',
    'No Supported Sources',
];
/**
 * Enumeration of all status messages raised by the plugin.
 * NONE, REGISTER and INIT are structural and probably not useful to you.
 */
exports.RmxAudioStatusMessage = void 0;
(function (RmxAudioStatusMessage) {
    /**
     * The starting state of the plugin. You will never see this value;
     * it changes before the callbacks are even registered to report changes to this value.
     */
    RmxAudioStatusMessage[RmxAudioStatusMessage["RMXSTATUS_NONE"] = 0] = "RMXSTATUS_NONE";
    /**
     * Raised when the plugin registers the callback handler for onStatus callbacks.
     * You will probably not be able to see this (nor do you need to).
     */
    RmxAudioStatusMessage[RmxAudioStatusMessage["RMXSTATUS_REGISTER"] = 1] = "RMXSTATUS_REGISTER";
    /**
     * Reserved for future use
     */
    RmxAudioStatusMessage[RmxAudioStatusMessage["RMXSTATUS_INIT"] = 2] = "RMXSTATUS_INIT";
    /**
     * Indicates an error is reported in the 'value' field.
     */
    RmxAudioStatusMessage[RmxAudioStatusMessage["RMXSTATUS_ERROR"] = 5] = "RMXSTATUS_ERROR";
    /**
     * The reported track is being loaded by the player
     */
    RmxAudioStatusMessage[RmxAudioStatusMessage["RMXSTATUS_LOADING"] = 10] = "RMXSTATUS_LOADING";
    /**
     * The reported track is able to begin playback
     */
    RmxAudioStatusMessage[RmxAudioStatusMessage["RMXSTATUS_CANPLAY"] = 11] = "RMXSTATUS_CANPLAY";
    /**
     * The reported track has loaded 100% of the file (either from disc or network)
     */
    RmxAudioStatusMessage[RmxAudioStatusMessage["RMXSTATUS_LOADED"] = 15] = "RMXSTATUS_LOADED";
    /**
     * (iOS only): Playback has stalled due to insufficient network
     */
    RmxAudioStatusMessage[RmxAudioStatusMessage["RMXSTATUS_STALLED"] = 20] = "RMXSTATUS_STALLED";
    /**
     * Reports an update in the reported track's buffering status
     */
    RmxAudioStatusMessage[RmxAudioStatusMessage["RMXSTATUS_BUFFERING"] = 25] = "RMXSTATUS_BUFFERING";
    /**
     * The reported track has started (or resumed) playing
     */
    RmxAudioStatusMessage[RmxAudioStatusMessage["RMXSTATUS_PLAYING"] = 30] = "RMXSTATUS_PLAYING";
    /**
     * The reported track has been paused, either by the user or by the system.
     * (iOS only): This value is raised when MP3's are malformed (but still playable).
     * These require the user to explicitly press play again. This can be worked
     * around and is on the TODO list.
     */
    RmxAudioStatusMessage[RmxAudioStatusMessage["RMXSTATUS_PAUSE"] = 35] = "RMXSTATUS_PAUSE";
    /**
     * Reports a change in the reported track's playback position.
     */
    RmxAudioStatusMessage[RmxAudioStatusMessage["RMXSTATUS_PLAYBACK_POSITION"] = 40] = "RMXSTATUS_PLAYBACK_POSITION";
    /**
     * The reported track has seeked.
     * On Android, only the plugin consumer can generate this (Notification controls on Android do not include a seek bar).
     * On iOS, the Command Center includes a seek bar so this will be reported when the user has seeked via Command Center.
     */
    RmxAudioStatusMessage[RmxAudioStatusMessage["RMXSTATUS_SEEK"] = 45] = "RMXSTATUS_SEEK";
    /**
     * The reported track has completed playback.
     */
    RmxAudioStatusMessage[RmxAudioStatusMessage["RMXSTATUS_COMPLETED"] = 50] = "RMXSTATUS_COMPLETED";
    /**
     * The reported track's duration has changed. This is raised once, when duration is updated for the first time.
     * For streams, this value is never reported.
     */
    RmxAudioStatusMessage[RmxAudioStatusMessage["RMXSTATUS_DURATION"] = 55] = "RMXSTATUS_DURATION";
    /**
     * All playback has stopped, probably because the plugin is shutting down.
     */
    RmxAudioStatusMessage[RmxAudioStatusMessage["RMXSTATUS_STOPPED"] = 60] = "RMXSTATUS_STOPPED";
    /**
     * The playlist has skipped forward to the next track.
     * On both Android and iOS, this will be raised if the notification controls/Command Center were used to skip.
     * It is unlikely you need to consume this event: RMXSTATUS_TRACK_CHANGED is also reported when this occurs,
     * so you can generalize your track change handling in one place.
     */
    RmxAudioStatusMessage[RmxAudioStatusMessage["RMX_STATUS_SKIP_FORWARD"] = 90] = "RMX_STATUS_SKIP_FORWARD";
    /**
     * The playlist has skipped back to the previous track.
     * On both Android and iOS, this will be raised if the notification controls/Command Center were used to skip.
     * It is unlikely you need to consume this event: RMXSTATUS_TRACK_CHANGED is also reported when this occurs,
     * so you can generalize your track change handling in one place.
     */
    RmxAudioStatusMessage[RmxAudioStatusMessage["RMX_STATUS_SKIP_BACK"] = 95] = "RMX_STATUS_SKIP_BACK";
    /**
     * Reported when the current track has changed in the native player. This event contains full data about
     * the new track, including the index and the actual track itself. The type of the 'value' field in this case
     * is OnStatusTrackChangedData.
     */
    RmxAudioStatusMessage[RmxAudioStatusMessage["RMXSTATUS_TRACK_CHANGED"] = 100] = "RMXSTATUS_TRACK_CHANGED";
    /**
     * The entire playlist has completed playback.
     * After this event has been raised, the current item is set to null and the current index to -1.
     */
    RmxAudioStatusMessage[RmxAudioStatusMessage["RMXSTATUS_PLAYLIST_COMPLETED"] = 105] = "RMXSTATUS_PLAYLIST_COMPLETED";
    /**
     * An item has been added to the playlist. For the setPlaylistItems and addAllItems methods, this status is
     * raised once for every track in the collection.
     */
    RmxAudioStatusMessage[RmxAudioStatusMessage["RMXSTATUS_ITEM_ADDED"] = 110] = "RMXSTATUS_ITEM_ADDED";
    /**
     * An item has been removed from the playlist. For the removeItems and clearAllItems methods, this status is
     * raised once for every track that was removed.
     */
    RmxAudioStatusMessage[RmxAudioStatusMessage["RMXSTATUS_ITEM_REMOVED"] = 115] = "RMXSTATUS_ITEM_REMOVED";
    /**
     * All items have been removed from the playlist
     */
    RmxAudioStatusMessage[RmxAudioStatusMessage["RMXSTATUS_PLAYLIST_CLEARED"] = 120] = "RMXSTATUS_PLAYLIST_CLEARED";
    /**
     * Just for testing.. you don't need this and in fact can never receive it, the plugin is destroyed before it can be raised.
     */
    RmxAudioStatusMessage[RmxAudioStatusMessage["RMXSTATUS_VIEWDISAPPEAR"] = 200] = "RMXSTATUS_VIEWDISAPPEAR";
})(exports.RmxAudioStatusMessage || (exports.RmxAudioStatusMessage = {}));
/**
 * String descriptions corresponding to the RmxAudioStatusMessage values
 */
const RmxAudioStatusMessageDescriptions = {
    0: 'No Status',
    1: 'Plugin Registered',
    2: 'Plugin Initialized',
    5: 'Error',
    10: 'Loading',
    11: 'CanPlay',
    15: 'Loaded',
    20: 'Stalled',
    25: 'Buffering',
    30: 'Playing',
    35: 'Paused',
    40: 'Playback Position Changed',
    45: 'Seeked',
    50: 'Playback Completed',
    55: 'Duration Changed',
    60: 'Stopped',
    90: 'Skip Forward',
    95: 'Skip Backward',
    100: 'Track Changed',
    105: 'Playlist Completed',
    110: 'Track Added',
    115: 'Track Removed',
    120: 'Playlist Cleared',
    200: 'DEBUG_View_Disappeared',
};

// todo: find out why we get imported twice
let playListWebInstance;
const Playlist = core.registerPlugin('Playlist', {
    web: () => Promise.resolve().then(function () { return web; }).then(m => {
        if (!playListWebInstance) {
            playListWebInstance = new m.PlaylistWeb();
        }
        return playListWebInstance;
    }),
});

/**
 * Validates the list of AudioTrack items to ensure they are valid.
 * Used internally but you can call this if you need to :)
 *
 * @param items The AudioTrack items to validate
 */
const validateTracks = (items) => {
    if (!items || !Array.isArray(items)) {
        return [];
    }
    return items.map(validateTrack).filter(x => !!x); // may produce an empty array!
};
/**
 * Validate a single track and ensure it is valid for playback.
 * Used internally but you can call this if you need to :)
 *
 * @param track The AudioTrack to validate
 */
const validateTrack = (track) => {
    if (!track) {
        return null;
    }
    // For now we will rely on TS to do the heavy lifting, but we can add a validation here
    // that all the required fields are valid. For now we just take care of the unique ID.
    track.trackId = track.trackId || generateUUID();
    return track;
};
/**
 * Generate a v4 UUID for use as a unique trackId. Used internally, but you can use this to generate track ID's if you want.
 */
const generateUUID = () => {
    var d = new Date().getTime();
    if (typeof performance !== 'undefined' && typeof performance.now === 'function') {
        d += performance.now(); //use high-precision timer if available
    }
    // There are better ways to do this in ES6, we are intentionally avoiding the import
    // of an ES6 polyfill here.
    const template = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx';
    return [].slice.call(template).map(function (c) {
        if (c === '-' || c === '4') {
            return c;
        }
        var r = (d + Math.random() * 16) % 16 | 0;
        d = Math.floor(d / 16);
        return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
    }).join('');
};

/*!
 * Module dependencies.
 */
const itemStatusChangeTypes = [
    exports.RmxAudioStatusMessage.RMXSTATUS_PLAYBACK_POSITION,
    exports.RmxAudioStatusMessage.RMXSTATUS_DURATION,
    exports.RmxAudioStatusMessage.RMXSTATUS_BUFFERING,
    exports.RmxAudioStatusMessage.RMXSTATUS_CANPLAY,
    exports.RmxAudioStatusMessage.RMXSTATUS_LOADING,
    exports.RmxAudioStatusMessage.RMXSTATUS_LOADED,
    exports.RmxAudioStatusMessage.RMXSTATUS_PAUSE,
    exports.RmxAudioStatusMessage.RMXSTATUS_COMPLETED,
    exports.RmxAudioStatusMessage.RMXSTATUS_ERROR,
];
class RmxAudioPlayer {
    /**
     * The current summarized state of the player, as a string. It is preferred that you use the 'isX' accessors,
     * because they properly interpret the range of these values, but this field is exposed if you wish to observe
     * or interrogate it.
     */
    get currentState() {
        return this._currentState;
    }
    get currentTrack() {
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
        this.options = { verbose: false, resetStreamOnPause: true };
        this._readyResolve = () => {
        };
        this._readyReject = () => {
        };
        this._currentState = 'unknown';
        this._hasError = false;
        this._hasLoaded = false;
        this._currentItem = null;
        /**
         * Player interface
         */
        /**
         * Returns a promise that resolves when the plugin is ready.
         */
        this.ready = () => {
            return this._initPromise;
        };
        this.initialize = async () => {
            Playlist.addListener('status', (data) => {
                if (data.action === 'status') {
                    this.onStatus(data.status.trackId, data.status.msgType, data.status.value);
                }
                else {
                    console.warn('Unknown audio player onStatus message:', data);
                }
            });
            try {
                await Playlist.initialize();
                this._readyResolve();
            }
            catch (args) {
                const message = 'Capacitor RMXAUDIOPLAYER: Error initializing:';
                console.warn(message, args);
                this._readyReject();
            }
        };
        /**
         * Sets the player options. This can be called at any time and is not required before playback can be initiated.
         */
        this.setOptions = (options) => {
            this.options = Object.assign(Object.assign({}, this.options), options);
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
        this.setPlaylistItems = (items, options) => {
            return Playlist.setPlaylistItems({ items: validateTracks(items), options: options || {} });
        };
        /**
         * Add a single track to the end of the playlist
         */
        this.addItem = (trackItem) => {
            const validTrackItem = validateTrack(trackItem);
            if (!validTrackItem) {
                throw new Error('Provided track is null or not an audio track');
            }
            return Playlist.addItem({ item: validTrackItem });
        };
        /**
         * Adds the list of tracks to the end of the playlist.
         */
        this.addAllItems = (items) => {
            return Playlist.addAllItems({ items: validateTracks(items) });
        };
        /**
         * Removes a track from the playlist. If this is the currently playing item, the next item will automatically begin playback.
         */
        this.removeItem = (removeItem) => {
            if (!removeItem) {
                throw new Error('Track removal spec is empty');
            }
            if (!removeItem.trackId && !removeItem.trackIndex) {
                throw new Error('Track removal spec is invalid');
            }
            const opts = {};
            if (removeItem.trackIndex !== undefined && removeItem.trackIndex !== null) {
                opts.index = removeItem.trackIndex;
            }
            if (removeItem.trackId) {
                opts.id = removeItem.trackId;
            }
            return Playlist.removeItem(opts);
        };
        /**
         * Removes all given tracks from the playlist; these can be specified either by trackId or trackIndex. If the removed items
         * include the currently playing item, the next available item will automatically begin playing.
         */
        this.removeItems = (items) => {
            const mapped = (items || []).map((item) => ({
                id: item === null || item === void 0 ? void 0 : item.trackId,
                index: item === null || item === void 0 ? void 0 : item.trackIndex
            }));
            return Playlist.removeItems({ items: mapped });
        };
        /**
         * Clear the entire playlist. This will result in the STOPPED event being raised.
         */
        this.clearAllItems = () => {
            return Playlist.clearAllItems();
        };
        /**
         * Playback management
         */
        /**
         * Begin playback. If no tracks have been added, this has no effect.
         */
        this.play = () => {
            return Playlist.play();
        };
        /**
         * Play the track at the given index. If the track does not exist, this has no effect.
         */
        this.playTrackByIndex = (index, position) => {
            return Playlist.playTrackByIndex({ index, position: position || 0 });
        };
        /**
         * Play the track matching the given trackId. If the track does not exist, this has no effect.
         */
        this.playTrackById = (id, position) => {
            return Playlist.playTrackById({ id, position: position || 0 });
        };
        /**
         * Play the track matching the given trackId. If the track does not exist, this has no effect.
         */
        this.selectTrackByIndex = (index, position) => {
            return Playlist.selectTrackByIndex({ index, position: position || 0 });
        };
        /**
         * Play the track matching the given trackId. If the track does not exist, this has no effect.
         */
        this.selectTrackById = (id, position) => {
            return Playlist.selectTrackById({ id, position: position || 0 });
        };
        /**
         * Pause playback
         */
        this.pause = () => {
            return Playlist.pause();
        };
        /**
         * Skip to the next track. If you are already at the end, and loop is false, this has no effect.
         * If you are at the end, and loop is true, playback will begin at the beginning of the playlist.
         */
        this.skipForward = () => {
            return Playlist.skipForward();
        };
        /**
         * Skip to the previous track. If you are already at the beginning, this has no effect.
         */
        this.skipBack = () => {
            return Playlist.skipBack();
        };
        /**
         * Seek to the given position in the currently playing track. If the value exceeds the track length,
         * the track will complete and playback of the next track will begin.
         */
        this.seekTo = (position) => {
            return Playlist.seekTo({ position });
        };
        /**
         * Set the playback speed; a float value between [-1, 1] inclusive. If set to 0, this pauses playback.
         */
        this.setPlaybackRate = (rate) => {
            return Playlist.setPlaybackRate({ rate });
        };
        /**
         * Set the playback volume. Float value between [0, 1] inclusive.
         * On both Android and iOS, this sets the volume of the media stream, which can be externally
         * controlled by setting the overall hardware volume.
         */
        this.setVolume = (volume) => {
            return Playlist.setPlaybackVolume({ volume });
        };
        /**
         * Sets a flag indicating whether the playlist should loop back to the beginning once it reaches the end.
         */
        this.setLoop = (loop) => {
            return Playlist.setLoop({ loop: loop });
        };
        this.handlers = {};
        new Promise((resolve) => {
            window.addEventListener('beforeunload', () => resolve(), { once: true });
        }).then(() => Playlist.release());
        this._initPromise = new Promise((resolve, reject) => {
            this._readyResolve = resolve;
            this._readyReject = reject;
        });
    }
    /**
     * Status event handling
     */
    /**
     * @internal
     * Call this function to emit an onStatus event via the on('status') handler.
     * Internal use only, to raise events received from the native interface.
     */
    onStatus(trackId, type, value) {
        var _a;
        const status = { msgType: type, trackId: trackId, value: value };
        if (this.options.verbose) {
            console.debug(`RmxAudioPlayer.onStatus: ${RmxAudioStatusMessageDescriptions[type]}(${type}) [${trackId}]: `, value);
        }
        if (status.msgType === exports.RmxAudioStatusMessage.RMXSTATUS_TRACK_CHANGED) {
            this._hasError = false;
            this._hasLoaded = false;
            this._currentState = 'loading';
            this._currentItem = (_a = status.value) === null || _a === void 0 ? void 0 : _a.currentItem;
        }
        // The plugin's status changes only in response to specific events.
        if (itemStatusChangeTypes.indexOf(status.msgType) >= 0) {
            // Only change the plugin's *current status* if the event being raised is for the current active track.
            if (this._currentItem && this._currentItem.trackId === trackId) {
                if (status.value && status.value.status) {
                    this._currentState = status.value.status;
                }
                if (status.msgType === exports.RmxAudioStatusMessage.RMXSTATUS_CANPLAY) {
                    this._hasLoaded = true;
                }
                if (status.msgType === exports.RmxAudioStatusMessage.RMXSTATUS_ERROR) {
                    this._hasError = true;
                }
            }
        }
        this.emit('status', status);
    }
    on(eventName, callback) {
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
    off(eventName, handle) {
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
    emit(...args) {
        const eventName = args.shift();
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

class PlaylistWeb extends core.WebPlugin {
    constructor() {
        super(...arguments);
        this.playlistItems = [];
        this.loop = false;
        this.options = {};
        this.currentTrack = null;
        this.lastState = 'stopped';
        this.lastKnownHandoffPosition = 0;
        this.hlsLoaded = false;
    }
    addAllItems(options) {
        this.playlistItems = this.playlistItems.concat(validateTracks(options.items));
        return Promise.resolve();
    }
    addItem(options) {
        const track = validateTrack(options.item);
        if (track) {
            this.playlistItems.push(track);
            this.updateStatus(exports.RmxAudioStatusMessage.RMXSTATUS_ITEM_ADDED, track, track.trackId);
        }
        return Promise.resolve();
    }
    async clearAllItems() {
        await this.release();
        this.playlistItems = [];
        this.updateStatus(exports.RmxAudioStatusMessage.RMXSTATUS_PLAYLIST_CLEARED, null, "INVALID");
        return Promise.resolve();
    }
    async getPlaylist() {
        return Promise.resolve({ items: this.playlistItems });
    }
    async initialize() {
        this.updateStatus(exports.RmxAudioStatusMessage.RMXSTATUS_INIT, null, "INVALID");
        return Promise.resolve();
    }
    async pause() {
        var _a;
        (_a = this.audio) === null || _a === void 0 ? void 0 : _a.pause();
    }
    async play() {
        var _a;
        await ((_a = this.audio) === null || _a === void 0 ? void 0 : _a.play());
    }
    async playTrackById(options) {
        for (let track of this.playlistItems) {
            if (track.trackId === options.id) {
                if (track !== this.currentTrack) {
                    await this.setCurrent(track);
                    if (this.audio && (options === null || options === void 0 ? void 0 : options.position) && options.position > 0) {
                        this.audio.currentTime = options.position;
                    }
                }
                return this.play();
            }
        }
        return Promise.reject();
    }
    async playTrackByIndex(options) {
        for (let { index, item } of this.playlistItems.map((item, index) => ({ index, item }))) {
            if (index === options.index) {
                if (item !== this.currentTrack) {
                    await this.setCurrent(item);
                    if (this.audio && (options === null || options === void 0 ? void 0 : options.position) && options.position > 0) {
                        this.audio.currentTime = options.position;
                    }
                }
                return this.play();
            }
        }
        return Promise.reject();
    }
    async release() {
        await this.pause();
        this.audio = undefined;
        return Promise.resolve();
    }
    async create() {
        this.audio = document.createElement('audio');
        this.audio.crossOrigin = 'anonymous';
        this.audio.preload = 'metadata';
        this.audio.controls = true;
        this.audio.autoplay = false;
        return Promise.resolve();
    }
    removeItem(options) {
        // options.index can be 0; don't use a truthy check.
        let removeIndex = -1;
        if (options.index !== undefined && options.index !== null) {
            removeIndex = options.index;
        }
        else if (options.id) {
            removeIndex = this.playlistItems.findIndex((t) => t.trackId === options.id);
        }
        if (removeIndex >= 0 && removeIndex < this.playlistItems.length) {
            const removedTrack = this.playlistItems.splice(removeIndex, 1)[0];
            this.updateStatus(exports.RmxAudioStatusMessage.RMXSTATUS_ITEM_REMOVED, removedTrack, removedTrack === null || removedTrack === void 0 ? void 0 : removedTrack.trackId);
        }
        return Promise.resolve();
    }
    removeItems(options) {
        options.items.forEach(async (item) => {
            await this.removeItem(item);
        });
        return Promise.resolve();
    }
    seekTo(options) {
        if (this.audio) {
            this.audio.currentTime = options.position;
            return Promise.resolve();
        }
        return Promise.reject();
    }
    selectTrackById(options) {
        for (const item of this.playlistItems) {
            if (item.trackId === options.id) {
                return this.setCurrent(item);
            }
        }
        return Promise.reject();
    }
    selectTrackByIndex(options) {
        let index = 0;
        for (const item of this.playlistItems) {
            if (index === options.index) {
                return this.setCurrent(item);
            }
            index++;
        }
        return Promise.reject();
    }
    setLoop(options) {
        this.loop = options.loop;
        return Promise.resolve();
    }
    setOptions(options) {
        this.options = options || {};
        return Promise.resolve();
    }
    setPlaybackVolume(options) {
        if (this.audio) {
            this.audio.volume = options.volume;
            return Promise.resolve();
        }
        return Promise.reject();
    }
    async setPlaylistItems(options) {
        var _a, _b, _c;
        this.playlistItems = options.items;
        if (this.playlistItems.length > 0) {
            let currentItem = this.playlistItems.filter(i => { var _a; return i.trackId === ((_a = options.options) === null || _a === void 0 ? void 0 : _a.playFromId); })[0];
            if (!currentItem) {
                currentItem = this.playlistItems[0];
            }
            await this.setCurrent(currentItem, (_b = (_a = options.options) === null || _a === void 0 ? void 0 : _a.playFromPosition) !== null && _b !== void 0 ? _b : 0);
            if (!((_c = options.options) === null || _c === void 0 ? void 0 : _c.startPaused)) {
                await this.play();
            }
        }
        return Promise.resolve();
    }
    async skipForward() {
        let found = null;
        this.playlistItems.forEach((item, index) => {
            if (found === null && this.getCurrentTrackId() === item.trackId) {
                found = index;
            }
        });
        if (found === this.playlistItems.length - 1) {
            found = -1;
        }
        if (found !== null) {
            const targetIndex = found + 1;
            this.updateStatus(exports.RmxAudioStatusMessage.RMX_STATUS_SKIP_FORWARD, {
                currentIndex: targetIndex,
                currentItem: this.playlistItems[targetIndex]
            }, this.playlistItems[targetIndex].trackId);
            return this.setCurrent(this.playlistItems[targetIndex]);
        }
        return Promise.reject();
    }
    async skipBack() {
        let found = null;
        this.playlistItems.forEach((item, index) => {
            if (found === null && this.getCurrentTrackId() === item.trackId) {
                found = index;
            }
        });
        if (found !== null) {
            const targetIndex = found === 0 ? this.playlistItems.length - 1 : found - 1;
            this.updateStatus(exports.RmxAudioStatusMessage.RMX_STATUS_SKIP_BACK, {
                currentIndex: targetIndex,
                currentItem: this.playlistItems[targetIndex]
            }, this.playlistItems[targetIndex].trackId);
            return this.setCurrent(this.playlistItems[targetIndex]);
        }
        return Promise.reject();
    }
    setPlaybackRate(options) {
        if (this.audio) {
            this.audio.playbackRate = options.rate;
            return Promise.resolve();
        }
        return Promise.reject();
    }
    async prepareForVideoHandoff() {
        var _a, _b;
        this.lastKnownHandoffPosition = (_b = (_a = this.audio) === null || _a === void 0 ? void 0 : _a.currentTime) !== null && _b !== void 0 ? _b : 0;
        await this.pause();
        return Promise.resolve();
    }
    async resumeAfterVideoHandoff(options) {
        this.lastKnownHandoffPosition = options.position;
        return Promise.resolve({ resumed: false });
    }
    async getLastKnownPosition() {
        return Promise.resolve({ position: this.lastKnownHandoffPosition });
    }
    async setMediaSessionRemoteControlMetadata() {
        const audioTrack = this.currentTrack;
        if (!navigator.mediaSession) {
            console.warn('Media Session API not available');
            return Promise.reject();
        }
        navigator.mediaSession.metadata = new MediaMetadata({
            title: audioTrack.title,
            artist: audioTrack.artist,
            album: audioTrack.album,
            artwork: [
                { src: audioTrack.albumArt, sizes: '96x96', type: 'image/jpeg' },
                { src: audioTrack.albumArt, sizes: '128x128', type: 'image/jpeg' },
                { src: audioTrack.albumArt, sizes: '192x192', type: 'image/jpeg' },
                { src: audioTrack.albumArt, sizes: '256x256', type: 'image/jpeg' },
                { src: audioTrack.albumArt, sizes: '384x384', type: 'image/jpeg' },
                { src: audioTrack.albumArt, sizes: '512x512', type: 'image/jpeg' },
            ]
        });
        navigator.mediaSession.setActionHandler('play', (details) => { this.mediaSessionControlsHandler(details); });
        navigator.mediaSession.setActionHandler('pause', (details) => { this.mediaSessionControlsHandler(details); });
        navigator.mediaSession.setActionHandler('nexttrack', (details) => { this.mediaSessionControlsHandler(details); });
        navigator.mediaSession.setActionHandler('previoustrack', (details) => { this.mediaSessionControlsHandler(details); });
        return Promise.resolve();
    }
    async mediaSessionControlsHandler(actionDetails) {
        switch (actionDetails.action) {
            case 'play':
                this.play();
                break;
            case 'pause':
                this.pause();
                break;
            case 'nexttrack':
                this.skipForward();
                break;
            case 'previoustrack':
                this.skipBack();
                break;
        }
        return Promise.resolve();
    }
    // register events
    /*
      private registerHlsListeners(hls: Hls, position?: number) {
        hls.on(Hls.Events.MANIFEST_PARSED, async () => {
          this.notifyListeners('status', {
            action: "status",
            status: {
              msgType: RmxAudioStatusMessage.RMXSTATUS_CANPLAY,
              trackId: this.getCurrentTrackId(),
              value: this.getCurrentTrackStatus('loading'),
            }
          })
          if(position) {
            await this.seekTo({position});
          }
        });
      }*/
    registerHtmlListeners(position) {
        const canPlayListener = async () => {
            var _a;
            this.updateStatus(exports.RmxAudioStatusMessage.RMXSTATUS_CANPLAY, this.getCurrentTrackStatus('paused'));
            if (position) {
                await this.seekTo({ position });
            }
            (_a = this.audio) === null || _a === void 0 ? void 0 : _a.removeEventListener('canplay', canPlayListener);
        };
        if (this.audio) {
            this.audio.addEventListener('loadstart', () => { this.setMediaSessionRemoteControlMetadata(); });
            this.audio.addEventListener('canplay', canPlayListener);
            this.audio.addEventListener('playing', () => {
                this.updateStatus(exports.RmxAudioStatusMessage.RMXSTATUS_PLAYING, this.getCurrentTrackStatus('playing'));
            });
            this.audio.addEventListener('pause', () => {
                this.updateStatus(exports.RmxAudioStatusMessage.RMXSTATUS_PAUSE, this.getCurrentTrackStatus('paused'));
            });
            this.audio.addEventListener('error', () => {
                this.updateStatus(exports.RmxAudioStatusMessage.RMXSTATUS_ERROR, this.getCurrentTrackStatus('error'));
            });
            this.audio.addEventListener('ended', () => {
                this.updateStatus(exports.RmxAudioStatusMessage.RMXSTATUS_COMPLETED, this.getCurrentTrackStatus('stopped'));
                const currentTrackIndex = this.playlistItems.findIndex(i => i.trackId === this.getCurrentTrackId());
                if (currentTrackIndex === this.playlistItems.length - 1) {
                    this.updateStatus(exports.RmxAudioStatusMessage.RMXSTATUS_PLAYLIST_COMPLETED, this.getCurrentTrackStatus('stopped'));
                }
                else {
                    this.setCurrent(this.playlistItems[currentTrackIndex + 1], undefined, true);
                }
            });
            let lastTrackId, lastPosition;
            this.audio.addEventListener('timeupdate', () => {
                const status = this.getCurrentTrackStatus(this.lastState);
                if (lastTrackId !== this.getCurrentTrackId() || lastPosition !== status.currentPosition) {
                    this.updateStatus(exports.RmxAudioStatusMessage.RMXSTATUS_PLAYBACK_POSITION, status);
                    lastTrackId = this.getCurrentTrackId();
                    lastPosition = status.currentPosition;
                }
            });
            this.audio.addEventListener('durationchange', () => {
                this.updateStatus(exports.RmxAudioStatusMessage.RMXSTATUS_DURATION, this.getCurrentTrackStatus(this.lastState));
            });
            this.audio.addEventListener('seeking', () => {
                const status = this.getCurrentTrackStatus(this.lastState);
                this.updateStatus(exports.RmxAudioStatusMessage.RMXSTATUS_SEEK, status);
            });
        }
    }
    getCurrentTrackId() {
        if (this.currentTrack) {
            return this.currentTrack.trackId;
        }
        return 'INVALID';
    }
    getCurrentIndex() {
        if (this.currentTrack) {
            for (let i = 0; i < this.playlistItems.length; i++) {
                if (this.playlistItems[i].trackId === this.currentTrack.trackId) {
                    return i;
                }
            }
        }
        return -1;
    }
    getCurrentTrackStatus(currentState) {
        var _a, _b, _c;
        this.lastState = currentState;
        return {
            trackId: this.getCurrentTrackId(),
            isStream: !!((_a = this.currentTrack) === null || _a === void 0 ? void 0 : _a.isStream),
            currentIndex: this.getCurrentIndex(),
            status: currentState,
            currentPosition: ((_b = this.audio) === null || _b === void 0 ? void 0 : _b.currentTime) || 0,
            duration: ((_c = this.audio) === null || _c === void 0 ? void 0 : _c.duration) || 0,
        };
    }
    async setCurrent(item, position, forceAutoplay = false) {
        let wasPlaying = false;
        if (this.audio) {
            wasPlaying = !this.audio.paused;
            await this.release();
        }
        await this.create();
        this.currentTrack = item;
        if (item.assetUrl.includes('.m3u8')) {
            await this.loadHlsJs();
            const hls = new Hls({
                autoStartLoad: true,
                debug: false,
                enableWorker: true,
            });
            hls.attachMedia(this.audio);
            hls.on(Hls.Events.MEDIA_ATTACHED, () => {
                hls.loadSource(item.assetUrl);
            });
            //this.registerHlsListeners(hls, position);
        }
        else {
            this.audio.src = item.assetUrl;
        }
        await this.registerHtmlListeners(position);
        this.updateStatus(exports.RmxAudioStatusMessage.RMXSTATUS_TRACK_CHANGED, {
            currentItem: item
        });
        if (wasPlaying || forceAutoplay) {
            //this.play();
            this.audio.addEventListener('canplay', () => {
                this.play();
            });
        }
    }
    updateStatus(msgType, value, trackId) {
        this.notifyListeners('status', {
            action: 'status',
            status: {
                msgType: msgType,
                trackId: trackId ? trackId : this.getCurrentTrackId(),
                value: value
            }
        });
    }
    loadHlsJs() {
        if (window.Hls !== undefined || this.hlsLoaded) {
            return Promise.resolve();
        }
        return new Promise((resolve, reject) => {
            console.log("LOADING HLS FROM CDN");
            const script = document.createElement('script');
            script.type = 'text/javascript';
            script.src = 'https://cdn.jsdelivr.net/npm/hls.js@1.1.1';
            document.getElementsByTagName('head')[0].appendChild(script);
            script.onload = () => {
                this.hlsLoaded = true;
                resolve(void 0);
            };
            script.onerror = () => {
                reject();
            };
        });
    }
}

var web = /*#__PURE__*/Object.freeze({
    __proto__: null,
    PlaylistWeb: PlaylistWeb
});

exports.Playlist = Playlist;
exports.RmxAudioErrorTypeDescriptions = RmxAudioErrorTypeDescriptions;
exports.RmxAudioPlayer = RmxAudioPlayer;
exports.RmxAudioStatusMessageDescriptions = RmxAudioStatusMessageDescriptions;
//# sourceMappingURL=plugin.cjs.js.map
