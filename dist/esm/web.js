import { WebPlugin } from '@capacitor/core';
import { RmxAudioStatusMessage } from './Constants';
import { validateTrack, validateTracks } from './utils';
export class PlaylistWeb extends WebPlugin {
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
            this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_ITEM_ADDED, track, track.trackId);
        }
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
            this.updateStatus(RmxAudioStatusMessage.RMX_STATUS_SKIP_FORWARD, {
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
            this.updateStatus(RmxAudioStatusMessage.RMX_STATUS_SKIP_BACK, {
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
            this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_CANPLAY, this.getCurrentTrackStatus('paused'));
            if (position) {
                await this.seekTo({ position });
            }
            (_a = this.audio) === null || _a === void 0 ? void 0 : _a.removeEventListener('canplay', canPlayListener);
        };
        if (this.audio) {
            this.audio.addEventListener('loadstart', () => { this.setMediaSessionRemoteControlMetadata(); });
            this.audio.addEventListener('canplay', canPlayListener);
            this.audio.addEventListener('playing', () => {
                this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_PLAYING, this.getCurrentTrackStatus('playing'));
            });
            this.audio.addEventListener('pause', () => {
                this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_PAUSE, this.getCurrentTrackStatus('paused'));
            });
            this.audio.addEventListener('error', () => {
                this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_ERROR, this.getCurrentTrackStatus('error'));
            });
            this.audio.addEventListener('ended', () => {
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
                const status = this.getCurrentTrackStatus(this.lastState);
                this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_SEEK, status);
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
//# sourceMappingURL=web.js.map