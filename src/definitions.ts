import { PluginListenerHandle } from '@capacitor/core';
import {
    AudioPlayerOptions, AudioTrack, PlaylistItemOptions, PlaylistStatusChangeCallback
} from './interfaces';



export interface PlaylistPlugin {
    /**
     * Listen for screen reader state change (on/off)
     */
    addListener(eventName: 'status', listenerFunc: PlaylistStatusChangeCallback): Promise<PluginListenerHandle>;

    // Playlist item management
    setOptions(options: AudioPlayerOptions): Promise<void>;

    initialize(): Promise<void>;

    release(): Promise<void>;

    setPlaylistItems(options: PlaylistOptions): Promise<void>;

    addItem(options: AddItemOptions): Promise<void>;

    addAllItems(options: AddAllItemOptions): Promise<void>;

    removeItem(options: RemoveItemOptions): Promise<void>;

    removeItems(options: RemoveItemsOptions): Promise<void>;

    clearAllItems(): Promise<void>;

    getPlaylist(): Promise<GetPlaylistResult>;

    // Playback
    play(): Promise<void>;

    pause(): Promise<void>;

    skipForward(): Promise<void>;

    skipBack(): Promise<void>;

    seekTo(options: SeekToOptions): Promise<void>;

    playTrackByIndex(options: PlayByIndexOptions): Promise<void>;

    playTrackById(options: PlayByIdOptions): Promise<void>;

    selectTrackByIndex(options: SelectByIndexOptions): Promise<void>;

    selectTrackById(options: SelectByIdOptions): Promise<void>;

    setPlaybackVolume(options: SetPlaybackVolumeOptions): Promise<void>;

    setLoop(options: SetLoopOptions): Promise<void>;

    // advanced
    setPlaybackRate(options: SetPlaybackRateOptions): Promise<void>;

    // inserts a track at a position or after an id
    insertItem(options: InsertItemOptions): Promise<void>;

    /**
     * Epic 45 — release native audio session / focus so the video player can own playback.
     * Call immediately before CapacitorVideoPlayer.initPlayer (Critical Rule 2).
     */
    prepareForVideoHandoff(): Promise<void>;

    /**
     * Epic 45 — optional resume hook after video exits (full behaviour in Stories 45.3/45.4).
     */
    resumeAfterVideoHandoff(options: ResumeAfterVideoHandoffOptions): Promise<void>;

    /**
     * Epic 45 — last audio head captured during prepare (seconds).
     */
    getLastKnownPosition(): Promise<GetLastKnownPositionResult>;
}

export interface ResumeAfterVideoHandoffOptions {
    position: number;
}

export interface GetLastKnownPositionResult {
    position: number;
}

export interface PlaylistOptions {
    items: Array<AudioTrack>;
    options: PlaylistItemOptions
}

export interface AddItemOptions {
    item: AudioTrack
}

export interface AddAllItemOptions {
    items: Array<AudioTrack>
}

export interface RemoveItemOptions {
    id?: string;
    index?: number;
}

export interface RemoveItemsOptions {
    items: Array<RemoveItemOptions>
}

export interface SeekToOptions {
    position: number
}

export interface PlayByIndexOptions {
    index: number;
    position?: number;
}

export interface PlayByIdOptions {
    id: string;
    position?: number;
}

export interface SelectByIndexOptions {
    index: number;
    position?: number;
}

export interface SelectByIdOptions {
    id: string;
    position?: number;
}

export interface SetPlaybackVolumeOptions {
    volume: number
}

export interface SetLoopOptions {
    loop: boolean
}

export interface SetPlaybackRateOptions {
    rate: number
}

export interface GetPlaylistResult {
    items: Array<AudioTrack>
}

export interface InsertItemOptions {
    item: AudioTrack;
    index?: number;
    id?: string;
}
