import { registerPlugin } from '@capacitor/core'
import type { PlaylistPlugin } from './definitions';
const Playlist = registerPlugin<PlaylistPlugin>('PlaylistPlugin', {
  web: () => import('./web').then(m => new m.PlaylistWeb()),
});

export * from './definitions';
export * from './interfaces'
export * from './Constants'

export { Playlist };
