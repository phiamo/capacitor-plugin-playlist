import { WebPlugin } from '@capacitor/core';
import { RmxAudioStatusMessage } from './Constants';
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
    SetLoopOptions, SetPlaybackRateOptions, SetPlaybackVolumeOptions
} from './definitions';
import { AudioPlayerOptions, AudioTrack } from './interfaces';
import { validateTrack, validateTracks } from './utils';

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
        }
        return Promise.resolve();
    }

    async clearAllItems(): Promise<void> {
        await this.release();
        this.playlistItems = [];
        return Promise.resolve();
    }

    async initialize(): Promise<void> {
        return Promise.resolve();
    }

    async pause(): Promise<void> {
        this.audio?.pause();
    }

    async play(): Promise<void> {
        return this.audio?.play();
    }

    playTrackById(options: PlayByIdOptions): Promise<void> {
        this.playlistItems.forEach(async (item) => {
            if (item.trackId === options.id) {
                await this.setCurrent(item);
                return this.play();
            }
        });
        return Promise.reject();
    }

    playTrackByIndex(options: PlayByIndexOptions): Promise<void> {
        this.playlistItems.forEach(async (item, index) => {
            if (index === options.index) {
                await this.setCurrent(item);
                return this.play();
            }
        });
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
                this.playlistItems.splice(index, 1);
            } else if (options.id && options.id === item.trackId) {
                this.playlistItems.splice(index, 1);
            }
        });
        return Promise.resolve();
    }

    removeItems(options: RemoveItemsOptions): Promise<void> {
        options.items.forEach((item) => {
            this.removeItem(item);
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
            return this.setCurrent(this.playlistItems[0], options.options?.playFromPosition || 0);
        }
        return Promise.resolve();
    }

    skipForward(): Promise<void> {
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
            return this.setCurrent(this.playlistItems[found + 1]);
        }

        return Promise.reject();
    }

    skipBack(): Promise<void> {
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
            this.setCurrent(this.playlistItems[found - 1]);
            return Promise.resolve();
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
            this.notifyListeners('status', {
                action: 'status',
                status: {
                    msgType: RmxAudioStatusMessage.RMXSTATUS_CANPLAY,
                    trackId: this.getCurrentTrackId(),
                    value: this.getCurrentTrackStatus('loading'),
                }
            });
            if (position) {
                await this.seekTo({position});
            }
            this.audio?.removeEventListener('canplay', canPlayListener);
        };
        if (this.audio) {
            this.audio.addEventListener('canplay', canPlayListener);
            this.audio.addEventListener('playing', () => {
                this.notifyListeners('status', {
                    action: 'status',
                    status: {
                        msgType: RmxAudioStatusMessage.RMXSTATUS_PLAYING,
                        trackId: this.getCurrentTrackId(),
                        value: this.getCurrentTrackStatus('playing'),
                    }
                });
            });

            this.audio.addEventListener('pause', () => {
                this.notifyListeners('status', {
                    action: 'status',
                    status: {
                        msgType: RmxAudioStatusMessage.RMXSTATUS_PAUSE,
                        trackId: this.getCurrentTrackId(),
                        value: this.getCurrentTrackStatus('paused'),
                    }
                });
            });

            this.audio.addEventListener('error', () => {
                this.notifyListeners('status', {
                    action: 'status',
                    status: {
                        msgType: RmxAudioStatusMessage.RMXSTATUS_ERROR,
                        trackId: this.getCurrentTrackId(),
                        value: this.getCurrentTrackStatus('error'),
                    }
                });
            });

            this.audio.addEventListener('ended', () => {
                this.notifyListeners('status', {
                    action: 'status',
                    status: {
                        msgType: RmxAudioStatusMessage.RMXSTATUS_STOPPED,
                        trackId: this.getCurrentTrackId(),
                        value: this.getCurrentTrackStatus('stopped'),
                    }
                });
            });

            let lastTrackId: any, lastPosition: any;
            this.audio.addEventListener('timeupdate', () => {
                const status = this.getCurrentTrackStatus(this.lastState);
                if (lastTrackId !== this.getCurrentTrackId() || lastPosition !== status.currentPosition) {
                    this.notifyListeners('status', {
                        action: 'status',
                        status: {
                            msgType: RmxAudioStatusMessage.RMXSTATUS_PLAYBACK_POSITION,
                            trackId: this.getCurrentTrackId(),
                            value: status,
                        }
                    });
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

        this.notifyListeners('status', {
            action: 'status',
            status: {
                msgType: RmxAudioStatusMessage.RMXSTATUS_TRACK_CHANGED,
                trackId: this.getCurrentTrackId(),
                value: {
                    currentItem: item
                }
            }
        });
    }

    protected log(message?: any, ...optionalParams: any[]) {
        if (this.options.verbose) {
            console.log(message, ...optionalParams);
        }
    }

    private hlsLoaded = false;

    protected loadHlsJs() {
        if (this.hlsLoaded) {
            return Promise.resolve();
        }
        return new Promise(
            (resolve, reject) => {
                var script = document.createElement('script');
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
