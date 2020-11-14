import { WebPlugin } from '@capacitor/core';
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
  SetPlaybackVolumeOptions,
  SetPlaybackRateOptions
} from './definitions';
import {AudioTrack, AudioPlayerOptions} from "./interfaces";
import {validateTrack, validateTracks} from "./utils";
declare var Hls: any;

export class PlaylistWeb extends WebPlugin implements PlaylistPlugin {
  protected audio: HTMLVideoElement | undefined;
  protected playlistItems: AudioTrack[] = [];
  protected loop = false;
  protected options: AudioPlayerOptions = {};
  protected currentTrack: AudioTrack | null = null;
  constructor() {
    super({
      name: 'PlaylistPlugin',
      platforms: ['web'],
    });
  }
  addAllItems(options: AddAllItemOptions): Promise<void> {
    this.playlistItems = this.playlistItems.concat(validateTracks(options.items))
    return Promise.resolve();
  }
  addItem(options: AddItemOptions): Promise<void> {
    const track = validateTrack(options.item);
    if(track) {
      this.playlistItems.push(track)
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
      if(item.trackId === options.id) {
        await this.setCurrent(item);
        return this.play();
      }
    })
    return Promise.reject();
  }

  playTrackByIndex(options: PlayByIndexOptions): Promise<void> {
    this.playlistItems.forEach(async (item, index) => {
      if(index === options.index) {
        await this.setCurrent(item);
        return this.play();
      }
    })
    return Promise.reject();
  }

  async release(): Promise<void> {
    await this.pause();
    this.audio = undefined;
    return Promise.resolve();
  }

  removeItem(options: RemoveItemOptions): Promise<void> {
    this.playlistItems.forEach((item, index) => {
      if(options.item.trackIndex && options.item.trackIndex === index) {
        this.playlistItems.splice(index, 1);
      }
      else if(options.item.trackId && options.item.trackId === item.trackId) {
        this.playlistItems.splice(index, 1);
      }
    })
    return Promise.resolve();
  }

  removeItems(options: RemoveItemsOptions): Promise<void> {
    options.items.forEach((item) => {
      this.removeItem({item});
    })
    return Promise.resolve();
  }

  seekTo(options: SeekToOptions): Promise<void> {
    if(this.audio) {
      this.audio.currentTime = options.position;
      return Promise.resolve();
    }
    return Promise.reject();
  }

  selectTrackById(options: SelectByIdOptions): Promise<void> {
    this.playlistItems.forEach(async (item) => {
      if(item.trackId === options.id) {
        return this.setCurrent(item);
      }
    })
    return Promise.reject();
  }

  selectTrackByIndex(options: SelectByIndexOptions): Promise<void> {
    this.playlistItems.forEach(async (item, index) => {
      if(index === options.index) {
        return this.setCurrent(item);
      }
    })
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
    if(this.audio) {
      this.audio.volume = options.volume;
      return Promise.resolve();
    }
    return Promise.reject();
  }

  setPlaylistItems(options: PlaylistOptions): Promise<void> {
    this.playlistItems = options.items;
    return this.setCurrent(this.playlistItems[0], options.options.playFromPosition);
  }

  skipForward(): Promise<void> {
    let found: number | null = null;
    this.playlistItems.forEach((item, index) => {
      if(!found && this.currentTrack?.trackId === item.trackId) {
        found = index;
      }
    })

    if(found === this.playlistItems.length-1) {
      found = 0;
    }

    this.log("Skipping forward to ", found)
    if(found) {
      return this.setCurrent(this.playlistItems[found + 1]);
    }

    return Promise.reject();
  }

  skipBack(): Promise<void> {
    let found: number | null = null;
    this.playlistItems.forEach((item, index) => {
      if(!found && this.currentTrack?.trackId === item.trackId) {
        found = index;
      }
    })
    if(found === 0) {
      found = this.playlistItems.length-1;
    }

    if(found) {
      this.setCurrent(this.playlistItems[found - 1]);
      return Promise.resolve();
    }

    return Promise.reject();
  }

  setPlaybackRate(options: SetPlaybackRateOptions): Promise<void> {
    if(this.audio) {
      this.audio.playbackRate = options.rate;
      return Promise.resolve();
    }
    return Promise.reject();
  }

  // register events

  private registerHlsListeners(hls: Hls, position?: number) {
    hls.on(Hls.Events.MANIFEST_PARSED, async () => {
      this.notifyListeners('status', {
        action: "status",
        status: {
          msgType: RmxAudioStatusMessage.RMXSTATUS_CANPLAY,
          trackId: this.currentTrack?.trackId
        }
      })
      if(position) {
        await this.seekTo({position});
      }
    });
  }
  registerHtmlListeners(position?: number) {
    if(this.audio) {
      this.audio.addEventListener('canplay', async () => {
        this.log("Event: canplay")
        this.notifyListeners('status', {
          action: "status",
          status: {
            msgType: RmxAudioStatusMessage.RMXSTATUS_CANPLAY,
            trackId: this.currentTrack?.trackId
          }
        })
        if(position) {
          await this.seekTo({position});
        }
      });
      this.audio.addEventListener('playing', () => {
        this.log("Event: playing")
        this.notifyListeners('status', {
          action: "status",
          status: {
            msgType: RmxAudioStatusMessage.RMXSTATUS_PLAYING,
            trackId: this.currentTrack?.trackId
          }
        })
      });

      this.audio.addEventListener('pause', () => {
        this.log("Event: pause")
        this.notifyListeners('status', {
          action: "status",
          status: {
            msgType: RmxAudioStatusMessage.RMXSTATUS_PAUSE,
            trackId: this.currentTrack?.trackId
          }
        })
      });

      this.audio.addEventListener('error', () => {
        this.log("Event: error")
        this.notifyListeners('status', {
          action: "status",
          status: {
            msgType: RmxAudioStatusMessage.RMXSTATUS_ERROR,
            trackId: this.currentTrack?.trackId
          }
        })
      });

      this.audio.addEventListener('ended', () => {
        this.log("Event: ended")
        this.notifyListeners('status', {
          action: "status",
          status: {
            msgType: RmxAudioStatusMessage.RMXSTATUS_STOPPED,
            trackId: this.currentTrack?.trackId
          }
        })
      });

      this.audio.addEventListener('timeupdate', () => {
        this.log("Event: timeupdate", this.audio?.currentTime)
        if(!this.audio?.paused) {
          this.notifyListeners('status', {
            action: "status",
            status: {
              msgType: RmxAudioStatusMessage.RMXSTATUS_PLAYBACK_POSITION,
              trackId: this.currentTrack?.trackId,
              value: {
                currentPosition: this.audio?.currentTime
              }
            }
          })
        }
      });
    }
  }
  // more internal methods
  protected async setCurrent(item: AudioTrack, position?: number) {
    this.audio = document.createElement('video')
    this.log("Setting current Item: ", item)
    this.currentTrack = item;
    if(item.assetUrl.includes('.m3u8')) {
      await this.loadHlsJs();

      const hls = new Hls({
        autoStartLoad: true,
        debug: false,
        enableWorker: true,
      });
      hls.attachMedia(this.audio);

      hls.on(Hls.Events.MEDIA_ATTACHED, () => {
        hls.loadSource("https://vod.dwbn.org/myRecordings/_definst_/mp4:ALTMUHLE-STREAMING-OCTOBER-31-1-2020-10-31/3rd-karmapa-mahamudra-and-questions-and-answers-6_96.mp4/playlist.m3u8?wowzatokenendtime=1605364671&wowzatokenstarttime=1605357799&wowzatokensso_user_id=c9725986d4d34ee58906d1779996518d&wowzatokenhash=-hSX1CilC8Lwj4lQ560xpecuXk-_bYB2WunPjCviT2Y=");
      })

      this.registerHlsListeners(hls, position);
      await this.registerHtmlListeners(position);
    }
    else {
      this.audio.src = item.assetUrl;
      await this.registerHtmlListeners(position);
    }
  }

  protected log(message?: any, ...optionalParams: any[]) {
    if(this.options.verbose) {
      console.log(message, ...optionalParams)
    }
  }

  protected loadHlsJs() {
    return new Promise(
      function(resolve, reject) {
        var script = document.createElement('script');
        script.type = 'text/javascript';
        script.src = 'https://cdn.jsdelivr.net/npm/hls.js@0.9.1';
        document.getElementsByTagName('head')[0].appendChild(script);
        script.onload = function() {
          resolve();
        }
        script.onerror = function() {
          reject();
        }
      });
  }

}

const Playlist = new PlaylistWeb();

export { Playlist };

import { registerWebPlugin } from '@capacitor/core';
import {RmxAudioStatusMessage} from "./Constants";
registerWebPlugin(Playlist);
