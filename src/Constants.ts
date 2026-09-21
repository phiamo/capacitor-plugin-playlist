/**
 * Enum describing the possible errors that may come from the plugins
 */
export enum RmxAudioErrorType {
    /**
     * No active source to play. You are unlikely to see this.
     */
    RMXERR_NONE_ACTIVE = 0,
    /**
     * Playback was aborted, typically because the source was torn down mid-load.
     * Web only, from `MediaError.MEDIA_ERR_ABORTED`.
     */
    RMXERR_ABORTED = 1,
    /**
     * The source is fine but could not be reached or read - a dropped connection,
     * a timeout, a bad HTTP status. **This is the retry-able error**: the same URL
     * may well succeed once connectivity returns.
     *
     * Android: media3's `ERROR_CODE_IO_*` network codes. iOS: an `NSError` in
     * `NSURLErrorDomain` / `NSPOSIXErrorDomain`. Web: `MediaError.MEDIA_ERR_NETWORK`.
     */
    RMXERR_NETWORK = 2,
    /**
     * The media was reached but could not be decoded. Not retry-able.
     *
     * Android: media3's decoder error codes. iOS: `AVFoundationErrorDomain` decode
     * and parse failures. Web: `MediaError.MEDIA_ERR_DECODE`.
     */
    RMXERR_DECODE = 3,
    /**
     * The source itself is unusable - missing, the wrong container, or a body that
     * does not parse as what it claims to be. Not retry-able; skip the track.
     *
     * Also the fallback when a platform reports a failure that does not classify.
     */
    RMXERR_NONE_SUPPORTED = 4,
};

/**
 * String descriptions corresponding to the RmxAudioErrorType values
 */
export const RmxAudioErrorTypeDescriptions = [
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
export enum RmxAudioStatusMessage {
    /**
     * The starting state of the plugin. You will never see this value;
     * it changes before the callbacks are even registered to report changes to this value.
     */
    RMXSTATUS_NONE = 0,
    /**
     * Raised when the plugin registers the callback handler for onStatus callbacks.
     * You will probably not be able to see this (nor do you need to).
     */
    RMXSTATUS_REGISTER = 1,
    /**
     * Reserved for future use
     */
    RMXSTATUS_INIT = 2,
    /**
     * Indicates an error is reported in the 'value' field.
     */
    RMXSTATUS_ERROR = 5,

    /**
     * The reported track is being loaded by the player
     */
    RMXSTATUS_LOADING = 10,
    /**
     * The reported track is able to begin playback
     */
    RMXSTATUS_CANPLAY = 11,
    /**
     * The reported track has loaded 100% of the file (either from disc or network)
     */
    RMXSTATUS_LOADED = 15,
    /**
     * Playback has stalled - the player is still trying, but position is not advancing.
     * Raised on all three platforms: from each platform's own stall signal where it fires,
     * and otherwise from a position-freeze poll gated by `AudioPlayerOptions.stallTimeoutMs`.
     * Emitted once per stall episode; the track's reported `status` reads `"stalled"` until
     * position advances again.
     */
    RMXSTATUS_STALLED = 20,
    /**
     * Reports an update in the reported track's buffering status
     */
    RMXSTATUS_BUFFERING = 25,
    /**
     * The reported track has started (or resumed) playing
     */
    RMXSTATUS_PLAYING = 30,
    /**
     * The reported track has been paused, either by the user or by the system.
     * (iOS only): This value is raised when MP3's are malformed (but still playable).
     * These require the user to explicitly press play again. This can be worked
     * around and is on the TODO list.
     */
    RMXSTATUS_PAUSE = 35,
    /**
     * Reports a change in the reported track's playback position.
     *
     * Suppressed while the WebView is backgrounded, and (Android/iOS) while the track is
     * stalled - so a frozen position is never reported as if playback were healthy.
     */
    RMXSTATUS_PLAYBACK_POSITION = 40,
    /**
     * The reported track has seeked.
     * On Android, only the plugin consumer can generate this (Notification controls on Android do not include a seek bar).
     * On iOS, the Command Center includes a seek bar so this will be reported when the user has seeked via Command Center.
     */
    RMXSTATUS_SEEK = 45,
    /**
     * The reported track has completed playback.
     */
    RMXSTATUS_COMPLETED = 50,
    /**
     * The reported track's duration has changed. This is raised once, when duration is updated for the first time.
     * For streams, this value is never reported.
     */
    RMXSTATUS_DURATION = 55,
    /**
     * All playback has stopped, probably because the plugin is shutting down.
     */
    RMXSTATUS_STOPPED = 60,

    /**
     * The playlist has skipped forward to the next track.
     * On both Android and iOS, this will be raised if the notification controls/Command Center were used to skip.
     * It is unlikely you need to consume this event: RMXSTATUS_TRACK_CHANGED is also reported when this occurs,
     * so you can generalize your track change handling in one place.
     */
    RMX_STATUS_SKIP_FORWARD = 90,
    /**
     * The playlist has skipped back to the previous track.
     * On both Android and iOS, this will be raised if the notification controls/Command Center were used to skip.
     * It is unlikely you need to consume this event: RMXSTATUS_TRACK_CHANGED is also reported when this occurs,
     * so you can generalize your track change handling in one place.
     */
    RMX_STATUS_SKIP_BACK = 95,
    /**
     * Reported when the current track has changed in the native player. This event contains full data about
     * the new track, including the index and the actual track itself. The type of the 'value' field in this case
     * is OnStatusTrackChangedData.
     */
    RMXSTATUS_TRACK_CHANGED = 100,
    /**
     * The entire playlist has completed playback.
     * After this event has been raised, the current item is set to null and the current index to -1.
     */
    RMXSTATUS_PLAYLIST_COMPLETED = 105,
    /**
     * An item has been added to the playlist. For the setPlaylistItems and addAllItems methods, this status is
     * raised once for every track in the collection.
     */
    RMXSTATUS_ITEM_ADDED = 110,
    /**
     * An item has been removed from the playlist. For the removeItems and clearAllItems methods, this status is
     * raised once for every track that was removed.
     */
    RMXSTATUS_ITEM_REMOVED = 115,
    /**
     * An item has been moved within the playlist.
     */
    RMXSTATUS_ITEM_MOVED = 112,
    /**
     * An item in the playlist has been replaced in place.
     */
    RMXSTATUS_ITEM_REPLACED = 113,
    /**
     * All items have been removed from the playlist
     */
    RMXSTATUS_PLAYLIST_CLEARED = 120,

    /**
     * Just for testing.. you don't need this and in fact can never receive it, the plugin is destroyed before it can be raised.
     */
    RMXSTATUS_VIEWDISAPPEAR = 200,
};

/**
 * String descriptions corresponding to the RmxAudioStatusMessage values
 */
export const RmxAudioStatusMessageDescriptions = {
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
    112: 'Track Moved',
    113: 'Track Replaced',
    115: 'Track Removed',
    120: 'Playlist Cleared',

    200: 'DEBUG_View_Disappeared',
};
