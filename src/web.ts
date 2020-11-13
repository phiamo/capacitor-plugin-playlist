import { WebPlugin } from '@capacitor/core';
import {
  AddAllItemOptions,
  AddItemOptions,
  PlayByIdOptions,
  PlayByIndexOptions, PlaylistOptions,
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

export class PlaylistPluginWeb extends WebPlugin implements PlaylistPlugin {
  constructor() {
    super({
      name: 'Playlist',
      platforms: ['web'],
    });
  }
  // @ts-ignore
  addAllItems(options: AddAllItemOptions): Promise<void> {
    return Promise.resolve(undefined);
  }
  // @ts-ignore
  addItem(options: AddItemOptions): Promise<void> {
    return Promise.resolve(undefined);
  }

  clearAllItems(): Promise<void> {
    return Promise.resolve(undefined);
  }

  initialize(): Promise<void> {
    return Promise.resolve(undefined);
  }

  pause(): Promise<void> {
    return Promise.resolve(undefined);
  }

  play(): Promise<void> {
    return Promise.resolve(undefined);
  }

  // @ts-ignore
  playTrackById(options: PlayByIdOptions): Promise<void> {
    return Promise.resolve(undefined);
  }

  // @ts-ignore
  playTrackByIndex(options: PlayByIndexOptions): Promise<void> {
    return Promise.resolve(undefined);
  }

  release(): Promise<void> {
    return Promise.resolve(undefined);
  }

  // @ts-ignore
  removeItem(options: RemoveItemOptions): Promise<void> {
    return Promise.resolve(undefined);
  }

  // @ts-ignore
  removeItems(options: RemoveItemsOptions): Promise<void> {
    return Promise.resolve(undefined);
  }

  // @ts-ignore
  seekTo(options: SeekToOptions): Promise<void> {
    return Promise.resolve(undefined);
  }

  // @ts-ignore
  selectTrackById(options: SelectByIdOptions): Promise<void> {
    return Promise.resolve(undefined);
  }

  // @ts-ignore
  selectTrackByIndex(options: SelectByIndexOptions): Promise<void> {
    return Promise.resolve(undefined);
  }

  // @ts-ignore
  setLoop(options: SetLoopOptions): Promise<void> {
    return Promise.resolve(undefined);
  }

  // @ts-ignore
  setOptions(options: AudioPlayerOptions): Promise<void> {
    return Promise.resolve(undefined);
  }

  // @ts-ignore
  setPlaybackVolume(options: SetPlaybackVolumeOptions): Promise<void> {
    return Promise.resolve(undefined);
  }

  // @ts-ignore
  setPlaylistItems(options: PlaylistOptions): Promise<void> {
    return Promise.resolve(undefined);
  }

  skipBack(): Promise<void> {
    return Promise.resolve(undefined);
  }

  skipForward(): Promise<void> {
    return Promise.resolve(undefined);
  }

  // @ts-ignore
  setPlaybackRate(options: SetPlaybackRateOptions): Promise<void> {
    throw new Error("Method not implemented.");
  }
}

const Playlist = new PlaylistPluginWeb();

export { Playlist };

import { registerWebPlugin } from '@capacitor/core';
import {AudioPlayerOptions} from "./interfaces";
registerWebPlugin(Playlist);
