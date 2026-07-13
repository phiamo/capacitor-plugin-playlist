import { WebPlugin } from '@capacitor/core';
import { RmxAudioStatusMessage } from './Constants';
import { AddAllItemOptions, AddItemOptions, PlayByIdOptions, PlayByIndexOptions, PlaylistOptions, PlaylistPlugin, RemoveItemOptions, RemoveItemsOptions, SeekToOptions, SelectByIdOptions, SelectByIndexOptions, SetLoopOptions, SetPlaybackRateOptions, SetPlaybackVolumeOptions } from './definitions';
import { AudioPlayerOptions, AudioTrack } from './interfaces';
export declare class PlaylistWeb extends WebPlugin implements PlaylistPlugin {
    protected audio: HTMLAudioElement | undefined;
    protected playlistItems: AudioTrack[];
    protected loop: boolean;
    protected options: AudioPlayerOptions;
    protected currentTrack: AudioTrack | null;
    protected lastState: string;
    addAllItems(options: AddAllItemOptions): Promise<void>;
    addItem(options: AddItemOptions): Promise<void>;
    clearAllItems(): Promise<void>;
    getPlaylist(): Promise<{
        items: AudioTrack[];
    }>;
    initialize(): Promise<void>;
    pause(): Promise<void>;
    play(): Promise<void>;
    playTrackById(options: PlayByIdOptions): Promise<void>;
    playTrackByIndex(options: PlayByIndexOptions): Promise<void>;
    release(): Promise<void>;
    create(): Promise<void>;
    removeItem(options: RemoveItemOptions): Promise<void>;
    removeItems(options: RemoveItemsOptions): Promise<void>;
    seekTo(options: SeekToOptions): Promise<void>;
    selectTrackById(options: SelectByIdOptions): Promise<void>;
    selectTrackByIndex(options: SelectByIndexOptions): Promise<void>;
    setLoop(options: SetLoopOptions): Promise<void>;
    setOptions(options: AudioPlayerOptions): Promise<void>;
    setPlaybackVolume(options: SetPlaybackVolumeOptions): Promise<void>;
    setPlaylistItems(options: PlaylistOptions): Promise<void>;
    skipForward(): Promise<void>;
    skipBack(): Promise<void>;
    setPlaybackRate(options: SetPlaybackRateOptions): Promise<void>;
    protected lastKnownHandoffPosition: number;
    prepareForVideoHandoff(): Promise<void>;
    resumeAfterVideoHandoff(options: {
        position: number;
    }): Promise<void>;
    getLastKnownPosition(): Promise<{
        position: number;
    }>;
    setMediaSessionRemoteControlMetadata(): Promise<void>;
    mediaSessionControlsHandler(actionDetails: MediaSessionActionDetails): Promise<void>;
    registerHtmlListeners(position?: number): void;
    protected getCurrentTrackId(): string | undefined;
    protected getCurrentIndex(): number;
    protected getCurrentTrackStatus(currentState: string): {
        trackId: string | undefined;
        isStream: boolean;
        currentIndex: number;
        status: string;
        currentPosition: number;
        duration: number;
    };
    protected setCurrent(item: AudioTrack, position?: number, forceAutoplay?: boolean): Promise<void>;
    protected updateStatus(msgType: RmxAudioStatusMessage, value: any, trackId?: string): void;
    private hlsLoaded;
    protected loadHlsJs(): Promise<unknown>;
}
