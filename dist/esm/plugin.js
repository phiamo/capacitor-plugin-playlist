import { registerPlugin } from '@capacitor/core';
// todo: find out why we get imported twice
let playListWebInstance;
const Playlist = registerPlugin('Playlist', {
    web: () => import('./web').then(m => {
        if (!playListWebInstance) {
            playListWebInstance = new m.PlaylistWeb();
        }
        return playListWebInstance;
    }),
});
export { Playlist };
//# sourceMappingURL=plugin.js.map