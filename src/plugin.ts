import { registerPlugin } from '@capacitor/core';

import type { PlaylistPlugin } from './definitions';


let playListWebInstance: PlaylistPlugin;
const Playlist = registerPlugin<PlaylistPlugin>('Playlist', {
    web: () => import('./web').then(m => {
        if (!playListWebInstance) {
            playListWebInstance = new m.PlaylistWeb();
        }
        return playListWebInstance;
    }),
});
export { Playlist };
