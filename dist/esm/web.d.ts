import { WebPlugin } from '@capacitor/core';
import { RmxAudioStatusMessage } from './Constants';
import type { AddAllItemOptions, AddItemOptions, MoveItemOptions, PlayByIdOptions, PlayByIndexOptions, PlaylistOptions, PlaylistPlugin, RemoveItemOptions, RemoveItemsOptions, ReplaceItemOptions, SeekToOptions, SelectByIdOptions, SelectByIndexOptions, SetLoopOptions, SetPlaybackRateOptions, SetPlaybackVolumeOptions } from './definitions';
import type { AudioPlayerOptions, AudioTrack } from './interfaces';
export declare class PlaylistWeb extends WebPlugin implements PlaylistPlugin {
    protected audio: HTMLAudioElement | undefined;
    protected playlistItems: AudioTrack[];
    protected loop: boolean;
    protected options: AudioPlayerOptions;
    protected currentTrack: AudioTrack | null;
    protected lastState: string;
    private isStalled;
    private isSeeking;
    private hasCanPlayed;
    private lastProgressPositionMs;
    private lastProgressObservedAtMs;
    private stallWatchdogId;
    addAllItems(options: AddAllItemOptions): Promise<void>;
    addItem(options: AddItemOptions): Promise<void>;
    moveItem(options: MoveItemOptions): Promise<void>;
    replaceItem(options: ReplaceItemOptions): Promise<void>;
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
    }): Promise<{
        resumed: boolean;
    }>;
    getLastKnownPosition(): Promise<{
        position: number;
    }>;
    setMediaSessionRemoteControlMetadata(): Promise<void>;
    mediaSessionControlsHandler(actionDetails: MediaSessionActionDetails): Promise<void>;
    registerHtmlListeners(position?: number): void;
    private clearStalled;
    private clearStallWatchdog;
    /**
     * Backstop for issue #143 parity: 'waiting'/'stalled' reliably fire for an ordinary data
     * underrun, but polling `currentTime` directly (as Android/iOS do) also catches playback
     * that is genuinely frozen without either event ever firing. Tied to this `audio` element's
     * lifetime — cleared on release()/track change rather than left running for the page's life.
     */
    private startStallWatchdog;
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
    protected loadHlsJs(): Promise<void>;
}
