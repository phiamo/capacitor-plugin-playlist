import {WebPlugin} from '@capacitor/core';
import {RmxAudioStatusMessage} from './Constants';
import {
    AddAllItemOptions,
    AddItemOptions,
    PlayByIdOptions,
    PlayByIndexOptions,
    PlaylistOptions,
    PlaylistPlugin,
    RemoveItemOptions,
    RemoveItemsOptions,
    SeekToOptions,
    SelectByIdOptions,
    SelectByIndexOptions,
    SetLoopOptions,
    SetPlaybackRateOptions,
    SetPlaybackVolumeOptions
} from './definitions';
import {AudioPlayerOptions, AudioTrack} from './interfaces';
import {validateTrack, validateTracks} from './utils';

declare var Hls: any;

export class PlaylistWeb extends WebPlugin implements PlaylistPlugin {
    protected audio: HTMLVideoElement | undefined;
    protected playlistItems: AudioTrack[] = [];
    protected loop = false;
    protected options: AudioPlayerOptions = {};
    protected currentTrack: AudioTrack | null = null;
    protected lastState = 'stopped';

    addAllItems(options: AddAllItemOptions): Promise<void> {
        this.playlistItems = this.playlistItems.concat(validateTracks(options.items));
        return Promise.resolve();
    }

    addItem(options: AddItemOptions): Promise<void> {
        const track = validateTrack(options.item);
        if (track) {
            this.playlistItems.push(track);
            this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_ITEM_ADDED, track, track.trackId);
        }
        return Promise.resolve();
    }

    async clearAllItems(): Promise<void> {
        await this.release();
        this.playlistItems = [];
        this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_PLAYLIST_CLEARED, null, "INVALID");
        return Promise.resolve();
    }

    async initialize(): Promise<void> {
        this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_INIT, null, "INVALID");
        return Promise.resolve();
    }

    async pause(): Promise<void> {
        this.audio?.pause();
    }

    async play(): Promise<void> {
        await this.audio?.play();
    }

    async playTrackById(options: PlayByIdOptions): Promise<void> {
        for (let track of this.playlistItems) {
            if (track.trackId === options.id) {
                if (track !== this.currentTrack) {
                    await this.setCurrent(track);
                }
                return this.play();
            }
        }
        return Promise.reject();
    }

    async playTrackByIndex(options: PlayByIndexOptions): Promise<void> {
        for (let {index, item} of this.playlistItems.map((item, index) => ({ index, item }))) {
            if (index === options.index) {
                if (item !== this.currentTrack) {
                    await this.setCurrent(item);
                }
                return this.play();
            }
        }
        return Promise.reject();
    }

    async release(): Promise<void> {
        await this.pause();
        this.audio = undefined;
        return Promise.resolve();
    }

    removeItem(options: RemoveItemOptions): Promise<void> {
        this.playlistItems.forEach((item, index) => {
            if (options.index && options.index === index) {
                const removedTrack = this.playlistItems.splice(index, 1);

                this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_ITEM_REMOVED, removedTrack[0], removedTrack[0].trackId);
            } else if (options.id && options.id === item.trackId) {
                const removedTrack = this.playlistItems.splice(index, 1);
                this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_ITEM_REMOVED, removedTrack[0], removedTrack[0].trackId);
            }
        });
        return Promise.resolve();
    }

    removeItems(options: RemoveItemsOptions): Promise<void> {
        options.items.forEach(async (item) => {
            await this.removeItem(item);
        });
        return Promise.resolve();
    }

    seekTo(options: SeekToOptions): Promise<void> {
        if (this.audio) {
            this.audio.currentTime = options.position;
            return Promise.resolve();
        }
        return Promise.reject();
    }

    selectTrackById(options: SelectByIdOptions): Promise<void> {
        for (const item of this.playlistItems) {
            if (item.trackId === options.id) {
                return this.setCurrent(item);
            }
        }
        return Promise.reject();
    }

    selectTrackByIndex(options: SelectByIndexOptions): Promise<void> {
        let index = 0;
        for (const item of this.playlistItems) {
            if (index === options.index) {
                return this.setCurrent(item);
            }
            index++;
        }
        return Promise.reject();
    }

    setLoop(options: SetLoopOptions): Promise<void> {
        this.loop = options.loop;
        return Promise.resolve();
    }

    setOptions(options: AudioPlayerOptions): Promise<void> {
        this.options = options || {};
        return Promise.resolve();
    }

    setPlaybackVolume(options: SetPlaybackVolumeOptions): Promise<void> {
        if (this.audio) {
            this.audio.volume = options.volume;
            return Promise.resolve();
        }
        return Promise.reject();
    }

    setPlaylistItems(options: PlaylistOptions): Promise<void> {
        this.playlistItems = options.items;
        if (this.playlistItems.length > 0) {
            let currentItem = this.playlistItems.filter(i => i.trackId === options.options?.playFromId)[0];
            if (!currentItem) {
                currentItem = this.playlistItems[0];
            }
            return this.setCurrent(currentItem, options.options?.retainPosition ? options.options?.playFromPosition : 0);
        }
        return Promise.resolve();
    }

    async skipForward(): Promise<void> {
        let found: number | null = null;
        this.playlistItems.forEach((item, index) => {
            if (!found && this.getCurrentTrackId() === item.trackId) {
                found = index;
            }
        });

        if (found === this.playlistItems.length - 1) {
            found = -1;
        }

        if (found !== null) {
            this.updateStatus(RmxAudioStatusMessage.RMX_STATUS_SKIP_BACK, {
                currentIndex: found + 1,
                currentItem: this.playlistItems[found + 1]
            }, this.playlistItems[found + 1].trackId);
            return this.setCurrent(this.playlistItems[found + 1]);
        }

        return Promise.reject();
    }

    async skipBack(): Promise<void> {
        let found: number | null = null;
        this.playlistItems.forEach((item, index) => {
            if (!found && this.getCurrentTrackId() === item.trackId) {
                found = index;
            }
        });
        if (found === 0) {
            found = this.playlistItems.length - 1;
        }

        if (found !== null) {
            this.updateStatus(RmxAudioStatusMessage.RMX_STATUS_SKIP_BACK, {
                currentIndex: found - 1,
                currentItem: this.playlistItems[found -1]
            }, this.playlistItems[found -1].trackId);
            return this.setCurrent(this.playlistItems[found - 1]);
        }

        return Promise.reject();
    }

    setPlaybackRate(options: SetPlaybackRateOptions): Promise<void> {
        if (this.audio) {
            this.audio.playbackRate = options.rate;
            return Promise.resolve();
        }
        return Promise.reject();
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
    registerHtmlListeners(position?: number) {
        const canPlayListener = async () => {
            this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_CANPLAY, this.getCurrentTrackStatus('paused'));
            if (position) {
                await this.seekTo({position});
            }
            this.audio?.removeEventListener('canplay', canPlayListener);
        };
        if (this.audio) {
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
                this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_STOPPED, this.getCurrentTrackStatus('stopped'));
            });

            let lastTrackId: any, lastPosition: any;
            this.audio.addEventListener('timeupdate', () => {
                const status = this.getCurrentTrackStatus(this.lastState);
                if (lastTrackId !== this.getCurrentTrackId() || lastPosition !== status.currentPosition) {
                    this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_PLAYBACK_POSITION, status);
                    lastTrackId = this.getCurrentTrackId();
                    lastPosition = status.currentPosition;
                }
            });
        }
    }

    protected getCurrentTrackId() {
        if (this.currentTrack) {
            return this.currentTrack.trackId;
        }
        return 'INVALID';
    }

    protected getCurrentIndex() {
        if (this.currentTrack) {
            for (let i = 0; i < this.playlistItems.length; i++) {
                if (this.playlistItems[i].trackId === this.currentTrack.trackId) {
                    return i;
                }
            }
        }
        return -1;
    }

    protected getCurrentTrackStatus(currentState: string) {
        this.lastState = currentState;
        return {
            trackId: this.getCurrentTrackId(),
            isStream: !!this.currentTrack?.isStream,
            currentIndex: this.getCurrentIndex(),
            status: currentState,
            currentPosition: this.audio?.currentTime || 0,
        };
    }

    protected async setCurrent(item: AudioTrack, position?: number) {
        let wasPlaying = false;
        if (this.audio) {
            wasPlaying = !this.audio.paused;
            this.audio.pause();
            this.audio.src = '';
            this.audio.removeAttribute('src');
            this.audio.load();
        }
        this.audio = document.createElement('video');
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
        } else {
            this.audio.src = item.assetUrl;
        }

        await this.registerHtmlListeners(position);

        if (wasPlaying) {
            this.audio.addEventListener('canplay', () => {
                this.play();
            });
        }

        this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_TRACK_CHANGED, {
            currentItem: item
        })
    }
    protected updateStatus(msgType: RmxAudioStatusMessage, value: any, trackId?: string) {
        this.notifyListeners('status', {
            action: 'status',
            status: {
                msgType: msgType,
                trackId: trackId ? trackId : this.getCurrentTrackId(),
                value: value
            }
        });
    }

    private hlsLoaded = false;

    protected loadHlsJs() {
        if (this.hlsLoaded) {
            return Promise.resolve();
        }
        return new Promise(
            (resolve, reject) => {
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
