import {AudioTrack} from './interfaces';

/**
 * Validates the list of AudioTrack items to ensure they are valid.
 * Used internally but you can call this if you need to :)
 *
 * @param items The AudioTrack items to validate
 */
export const validateTracks = (items: AudioTrack[]) => {
    if (!items || !Array.isArray(items)) {
        return [];
    }
    return items.map(validateTrack).filter(x => !!x) as AudioTrack[]; // may produce an empty array!
};

/**
 * Validate a single track and ensure it is valid for playback.
 * Used internally but you can call this if you need to :)
 *
 * @param track The AudioTrack to validate
 */
export const validateTrack = (track: AudioTrack) => {
    if (!track) {
        return null;
    }
    // For now we will rely on TS to do the heavy lifting, but we can add a validation here
    // that all the required fields are valid. For now we just take care of the unique ID.
    track.trackId = track.trackId || generateUUID();
    return track;
};

/**
 * Generate a v4 UUID for use as a unique trackId. Used internally, but you can use this to generate track ID's if you want.
 */
const generateUUID = () => { // Doesn't need to be perfect or secure, just good enough to give each item an ID.
    var d = new Date().getTime();
    if (typeof performance !== 'undefined' && typeof performance.now === 'function') {
        d += performance.now(); //use high-precision timer if available
    }
    // There are better ways to do this in ES6, we are intentionally avoiding the import
    // of an ES6 polyfill here.
    const template = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx';
    return (<string[]> [].slice.call(template)).map(function(c) {
        if (c === '-' || c === '4') {
            return c;
        }
        var r = (d + Math.random() * 16) % 16 | 0;
        d = Math.floor(d / 16);
        return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
    }).join('');
};
