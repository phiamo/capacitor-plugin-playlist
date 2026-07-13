import { AudioTrack } from './interfaces';
/**
 * Validates the list of AudioTrack items to ensure they are valid.
 * Used internally but you can call this if you need to :)
 *
 * @param items The AudioTrack items to validate
 */
export declare const validateTracks: (items: AudioTrack[]) => AudioTrack[];
/**
 * Validate a single track and ensure it is valid for playback.
 * Used internally but you can call this if you need to :)
 *
 * @param track The AudioTrack to validate
 */
export declare const validateTrack: (track: AudioTrack) => AudioTrack | null;
