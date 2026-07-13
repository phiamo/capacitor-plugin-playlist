/**
 * Enum describing the possible errors that may come from the plugins
 */
export var RmxAudioErrorType;
(function (RmxAudioErrorType) {
    RmxAudioErrorType[RmxAudioErrorType["RMXERR_NONE_ACTIVE"] = 0] = "RMXERR_NONE_ACTIVE";
    RmxAudioErrorType[RmxAudioErrorType["RMXERR_ABORTED"] = 1] = "RMXERR_ABORTED";
    RmxAudioErrorType[RmxAudioErrorType["RMXERR_NETWORK"] = 2] = "RMXERR_NETWORK";
    RmxAudioErrorType[RmxAudioErrorType["RMXERR_DECODE"] = 3] = "RMXERR_DECODE";
    RmxAudioErrorType[RmxAudioErrorType["RMXERR_NONE_SUPPORTED"] = 4] = "RMXERR_NONE_SUPPORTED";
})(RmxAudioErrorType || (RmxAudioErrorType = {}));
;
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
export var RmxAudioStatusMessage;
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
})(RmxAudioStatusMessage || (RmxAudioStatusMessage = {}));
;
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
    115: 'Track Removed',
    120: 'Playlist Cleared',
    200: 'DEBUG_View_Disappeared',
};
//# sourceMappingURL=Constants.js.map