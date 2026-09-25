import { WebPlugin } from '@capacitor/core';
import { RmxAudioErrorType, RmxAudioStatusMessage } from './Constants';
import { validateTrack, validateTracks } from './utils';
const HLS_MIME_TYPES = ['application/x-mpegurl', 'application/vnd.apple.mpegurl', 'audio/mpegurl', 'audio/x-mpegurl'];
/**
 * Whether the track should be played through hls.js. An `.m3u8` path is the usual signal;
 * an explicit `mimeType` covers HLS served from a URL with no extension (issue #144).
 */
function isHlsSource(item) {
    var _a;
    const declared = (_a = item.mimeType) === null || _a === void 0 ? void 0 : _a.trim().toLowerCase();
    if (declared) {
        return HLS_MIME_TYPES.includes(declared);
    }
    return pathEndsWith(item.assetUrl, '.m3u8');
}
/** Whether the URL's path ends in `suffix`, ignoring any query string or fragment. */
function pathEndsWith(url, suffix) {
    if (!url) {
        return false;
    }
    return url.toLowerCase().split('#')[0].split('?')[0].endsWith(suffix);
}
/** Map an `HTMLMediaElement.error` onto the RmxAudioErrorType values shared with the native platforms. */
function mediaErrorToRmxErrorType(error) {
    switch (error === null || error === void 0 ? void 0 : error.code) {
        case 1: // MEDIA_ERR_ABORTED
            return RmxAudioErrorType.RMXERR_ABORTED;
        case 2: // MEDIA_ERR_NETWORK
            return RmxAudioErrorType.RMXERR_NETWORK;
        case 3: // MEDIA_ERR_DECODE
            return RmxAudioErrorType.RMXERR_DECODE;
        default: // MEDIA_ERR_SRC_NOT_SUPPORTED, or no MediaError at all
            return RmxAudioErrorType.RMXERR_NONE_SUPPORTED;
    }
}
export class PlaylistWeb extends WebPlugin {
    constructor() {
        super(...arguments);
        this.playlistItems = [];
        this.loop = false;
        this.options = {};
        this.currentTrack = null;
        this.lastState = 'stopped';
        this.isStalled = false;
        this.isSeeking = false;
        this.hasCanPlayed = false;
        // Issue #143 parity backstop: position-freeze polling, in case the browser's own
        // 'waiting'/'stalled' events never fire while playback is genuinely frozen (mirrors
        // Android's ExoPlayer-specific gap). null means "no baseline yet".
        this.lastProgressPositionMs = null;
        this.lastProgressObservedAtMs = null;
        this.lastKnownHandoffPosition = 0;
        this.hlsLoaded = false;
    }
    async addAllItems(options) {
        assertWebDrmNotSupported(options.items);
        this.playlistItems = this.playlistItems.concat(validateTracks(options.items));
    }
    async addItem(options) {
        assertWebDrmNotSupported([options.item]);
        const track = validateTrack(options.item);
        if (track) {
            const insertIndex = options.index !== undefined && options.index !== null
                ? Math.min(Math.max(0, options.index), this.playlistItems.length)
                : this.playlistItems.length;
            this.playlistItems.splice(insertIndex, 0, track);
            // currentTrack is tracked by object reference, so getCurrentIndex() (indexOf)
            // automatically reflects the shift caused by this insertion; no bookkeeping needed here.
            this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_ITEM_ADDED, Object.assign(Object.assign({}, track), { index: insertIndex }), track.trackId);
        }
    }
    moveItem(options) {
        const { from, to } = options;
        if (from < 0 || from >= this.playlistItems.length || to < 0 || to >= this.playlistItems.length) {
            return Promise.reject(new Error('Index out of bounds'));
        }
        if (from === to) {
            return Promise.resolve();
        }
        const [item] = this.playlistItems.splice(from, 1);
        this.playlistItems.splice(to, 0, item);
        this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_ITEM_MOVED, { from, to, currentIndex: this.getCurrentIndex() }, item.trackId);
        return Promise.resolve();
    }
    async replaceItem(options) {
        var _a;
        assertWebDrmNotSupported([options.item]);
        let replaceIndex = -1;
        if (options.index !== undefined && options.index !== null) {
            replaceIndex = options.index;
        }
        else if (options.id) {
            replaceIndex = this.playlistItems.findIndex((t) => t.trackId === options.id);
        }
        if (replaceIndex < 0 || replaceIndex >= this.playlistItems.length) {
            return Promise.reject(new Error('Could not find item to replace'));
        }
        const existing = this.playlistItems[replaceIndex];
        const replacement = validateTrack(Object.assign(Object.assign({}, options.item), { trackId: (_a = options.item.trackId) !== null && _a !== void 0 ? _a : existing.trackId }));
        if (!replacement) {
            return Promise.reject(new Error('Invalid replacement track'));
        }
        const isCurrent = this.currentTrack === existing;
        let savedPosition = 0;
        let wasPlaying = false;
        if (isCurrent && this.audio) {
            savedPosition = this.audio.currentTime;
            wasPlaying = !this.audio.paused;
        }
        this.playlistItems[replaceIndex] = replacement;
        if (isCurrent) {
            await this.setCurrent(replacement, savedPosition);
            if (!wasPlaying) {
                await this.pause();
            }
        }
        this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_ITEM_REPLACED, replacement, replacement.trackId);
        return Promise.resolve();
    }
    async clearAllItems() {
        await this.release();
        this.playlistItems = [];
        this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_PLAYLIST_CLEARED, null, "INVALID");
        return Promise.resolve();
    }
    async getPlaylist() {
        return Promise.resolve({ items: this.playlistItems });
    }
    async initialize() {
        this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_INIT, null, "INVALID");
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
        for (const track of this.playlistItems) {
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
        for (const { index, item } of this.playlistItems.map((item, index) => ({ index, item }))) {
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
        this.clearStallWatchdog();
        this.audio = undefined;
        this.currentTrack = null;
        this.lastState = 'stopped';
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
            this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_ITEM_REMOVED, removedTrack, removedTrack === null || removedTrack === void 0 ? void 0 : removedTrack.trackId);
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
        assertWebDrmNotSupported(options.items);
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
        const currentIndex = this.getCurrentIndex();
        if (currentIndex < 0) {
            return;
        }
        let targetIndex = currentIndex + 1;
        if (targetIndex >= this.playlistItems.length) {
            if (!this.loop) {
                return;
            }
            targetIndex = 0;
        }
        const targetTrack = this.playlistItems[targetIndex];
        await this.setCurrent(targetTrack);
        this.updateStatus(RmxAudioStatusMessage.RMX_STATUS_SKIP_FORWARD, {
            currentIndex: targetIndex,
            currentItem: targetTrack
        }, targetTrack.trackId);
    }
    async skipBack() {
        const currentIndex = this.getCurrentIndex();
        if (currentIndex <= 0) {
            return;
        }
        const targetIndex = currentIndex - 1;
        const targetTrack = this.playlistItems[targetIndex];
        await this.setCurrent(targetTrack);
        this.updateStatus(RmxAudioStatusMessage.RMX_STATUS_SKIP_BACK, {
            currentIndex: targetIndex,
            currentItem: targetTrack
        }, targetTrack.trackId);
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
      private registerHlsListeners(hls: any, position?: number) {
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
        if (this.audio) {
            this.startStallWatchdog(this.audio);
        }
        const canPlayListener = async () => {
            var _a;
            this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_CANPLAY, this.getCurrentTrackStatus('paused'));
            if (position) {
                await this.seekTo({ position });
            }
            (_a = this.audio) === null || _a === void 0 ? void 0 : _a.removeEventListener('canplay', canPlayListener);
        };
        if (this.audio) {
            this.audio.addEventListener('loadstart', () => {
                this.hasCanPlayed = false;
                this.isStalled = false;
                this.isSeeking = false;
                this.setMediaSessionRemoteControlMetadata();
            });
            this.audio.addEventListener('canplay', canPlayListener);
            this.audio.addEventListener('canplay', () => {
                this.hasCanPlayed = true;
                this.clearStalled();
            });
            this.audio.addEventListener('playing', () => {
                this.clearStalled();
                this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_PLAYING, this.getCurrentTrackStatus('playing'));
            });
            this.audio.addEventListener('pause', () => {
                this.clearStalled();
                this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_PAUSE, this.getCurrentTrackStatus('paused'));
            });
            this.audio.addEventListener('error', () => {
                var _a, _b;
                this.clearStalled();
                this.isSeeking = false;
                // Carry a real RmxAudioErrorType so consumers can tell a dropped connection
                // (retry) from an unusable source (skip) - issue #143. The track status fields
                // are kept alongside `code`/`message` so this stays a superset of what web
                // emitted before, while now matching the declared OnStatusErrorCallbackData.
                const mediaError = (_b = (_a = this.audio) === null || _a === void 0 ? void 0 : _a.error) !== null && _b !== void 0 ? _b : null;
                this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_ERROR, Object.assign({ code: mediaErrorToRmxErrorType(mediaError), message: (mediaError === null || mediaError === void 0 ? void 0 : mediaError.message) || 'Playback error' }, this.getCurrentTrackStatus('error')));
            });
            // Parity with Android (position-freeze polling) / iOS (AVPlayerItemPlaybackStalledNotification):
            // the browser's own stall signals for "still trying, not necessarily failed" (issue #143).
            // 'waiting' is the reliable one (temporary data underrun); 'stalled' is best-effort.
            // Suppressed during an in-flight seek and before the first 'canplay' of a source, since both
            // routinely fire a spurious 'waiting' that isn't an actual playback stall. Only emitted once
            // per stall episode; 'playing'/'pause'/'error'/'canplay'/'ended' all clear it again.
            const stalledListener = () => {
                if (this.isSeeking || !this.hasCanPlayed || this.isStalled) {
                    return;
                }
                this.isStalled = true;
                this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_STALLED, this.getCurrentTrackStatus('stalled'));
            };
            this.audio.addEventListener('waiting', stalledListener);
            this.audio.addEventListener('stalled', stalledListener);
            this.audio.addEventListener('ended', () => {
                this.clearStalled();
                this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_COMPLETED, this.getCurrentTrackStatus('stopped'));
                const currentTrackIndex = this.playlistItems.findIndex(i => i.trackId === this.getCurrentTrackId());
                if (currentTrackIndex === this.playlistItems.length - 1) {
                    this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_PLAYLIST_COMPLETED, this.getCurrentTrackStatus('stopped'));
                }
                else {
                    this.setCurrent(this.playlistItems[currentTrackIndex + 1], undefined, true);
                }
            });
            let lastTrackId, lastPosition;
            this.audio.addEventListener('timeupdate', () => {
                const status = this.getCurrentTrackStatus(this.lastState);
                if (lastTrackId !== this.getCurrentTrackId() || lastPosition !== status.currentPosition) {
                    this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_PLAYBACK_POSITION, status);
                    lastTrackId = this.getCurrentTrackId();
                    lastPosition = status.currentPosition;
                }
            });
            this.audio.addEventListener('durationchange', () => {
                this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_DURATION, this.getCurrentTrackStatus(this.lastState));
            });
            this.audio.addEventListener('seeking', () => {
                this.isSeeking = true;
                const status = this.getCurrentTrackStatus(this.lastState);
                this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_SEEK, status);
            });
            this.audio.addEventListener('seeked', () => {
                this.isSeeking = false;
            });
        }
    }
    clearStalled() {
        this.isStalled = false;
    }
    clearStallWatchdog() {
        if (this.stallWatchdogId !== undefined) {
            clearInterval(this.stallWatchdogId);
            this.stallWatchdogId = undefined;
        }
        this.lastProgressPositionMs = null;
        this.lastProgressObservedAtMs = null;
    }
    /**
     * Backstop for issue #143 parity: 'waiting'/'stalled' reliably fire for an ordinary data
     * underrun, but polling `currentTime` directly (as Android/iOS do) also catches playback
     * that is genuinely frozen without either event ever firing. Tied to this `audio` element's
     * lifetime — cleared on release()/track change rather than left running for the page's life.
     */
    startStallWatchdog(audio) {
        this.clearStallWatchdog();
        this.stallWatchdogId = setInterval(() => {
            var _a;
            if (audio.paused || audio.ended || this.isSeeking || !this.hasCanPlayed) {
                this.lastProgressPositionMs = null;
                this.lastProgressObservedAtMs = null;
                return;
            }
            const positionMs = audio.currentTime * 1000;
            const nowMs = Date.now();
            // The first observation after (re)arming only establishes a baseline — it is not
            // evidence of a genuine resume, so it must not clear a stall an already-fired native
            // 'waiting'/'stalled' event just reported.
            if (this.lastProgressPositionMs === null) {
                this.lastProgressPositionMs = positionMs;
                this.lastProgressObservedAtMs = nowMs;
                return;
            }
            if (positionMs !== this.lastProgressPositionMs) {
                this.lastProgressPositionMs = positionMs;
                this.lastProgressObservedAtMs = nowMs;
                if (this.isStalled) {
                    this.clearStalled();
                }
                return;
            }
            const stallTimeoutMs = (_a = this.options.stallTimeoutMs) !== null && _a !== void 0 ? _a : 10000;
            if (!this.isStalled && this.lastProgressObservedAtMs !== null
                && nowMs - this.lastProgressObservedAtMs >= stallTimeoutMs) {
                this.isStalled = true;
                this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_STALLED, this.getCurrentTrackStatus('stalled'));
            }
        }, 1000);
    }
    getCurrentTrackId() {
        if (this.currentTrack) {
            return this.currentTrack.trackId;
        }
        return 'INVALID';
    }
    getCurrentIndex() {
        return this.currentTrack ? this.playlistItems.indexOf(this.currentTrack) : -1;
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
        // Reset directly here rather than relying solely on the new element's 'loadstart' to fire
        // after registerHtmlListeners() attaches below — `.src` is assigned before that, so a race
        // between the load task and listener attachment could otherwise leave stale state (in
        // particular `isSeeking`) stuck across a track change that interrupted an in-flight seek.
        this.hasCanPlayed = false;
        this.isStalled = false;
        this.isSeeking = false;
        this.currentTrack = item;
        if (isHlsSource(item)) {
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
        this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_TRACK_CHANGED, {
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
        if (typeof Hls !== 'undefined' || this.hlsLoaded) {
            return Promise.resolve();
        }
        return new Promise((resolve, reject) => {
            console.log("LOADING HLS FROM CDN");
            const script = document.createElement('script');
            script.type = 'text/javascript';
            script.src = 'https://cdn.jsdelivr.net/npm/hls.js@1.7.3';
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
/** iOS/web refuse `drm` before creating a player (Story 57.5). */
export function assertWebDrmNotSupported(items) {
    if (items.some((item) => item != null && item.drm != null)) {
        const error = new Error('DRM not supported on this platform yet');
        error.code = 'notSupported';
        throw error;
    }
}
//# sourceMappingURL=web.js.map