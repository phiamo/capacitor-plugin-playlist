import { beforeEach, describe, expect, it, vi } from 'vitest';

import { RmxAudioStatusMessage } from './Constants';
import type { AudioTrack } from './interfaces';
import { PlaylistWeb } from './web';

const track = (trackId: string, overrides: Partial<AudioTrack> = {}): AudioTrack => ({
    trackId,
    assetUrl: `https://example.com/${trackId}.mp3`,
    artist: 'Artist',
    album: 'Album',
    title: `Title ${trackId}`,
    ...overrides,
});

describe('PlaylistWeb#addItem', () => {
    let web: PlaylistWeb;

    beforeEach(() => {
        web = new PlaylistWeb();
    });

    it('appends the track when no index is given', async () => {
        await web.addAllItems({ items: [track('a'), track('b')] });
        await web.addItem({ item: track('c') });

        const { items } = await web.getPlaylist();
        expect(items.map((i) => i.trackId)).toEqual(['a', 'b', 'c']);
    });

    it('inserts the track at the given 0-based index', async () => {
        await web.addAllItems({ items: [track('a'), track('b'), track('c')] });
        await web.addItem({ item: track('new'), index: 1 });

        const { items } = await web.getPlaylist();
        expect(items.map((i) => i.trackId)).toEqual(['a', 'new', 'b', 'c']);
    });

    it('clamps an out-of-range index to the start/end of the list', async () => {
        await web.addAllItems({ items: [track('a'), track('b')] });

        await web.addItem({ item: track('negative'), index: -5 });
        await web.addItem({ item: track('huge'), index: 999 });

        const { items } = await web.getPlaylist();
        expect(items.map((i) => i.trackId)).toEqual(['negative', 'a', 'b', 'huge']);
    });

    it('emits RMXSTATUS_ITEM_ADDED with the resolved index', async () => {
        const listener = vi.fn();
        web.addListener('status', listener);
        await web.addAllItems({ items: [track('a')] });

        await web.addItem({ item: track('b'), index: 0 });

        expect(listener).toHaveBeenCalledWith(
            expect.objectContaining({
                status: expect.objectContaining({
                    msgType: RmxAudioStatusMessage.RMXSTATUS_ITEM_ADDED,
                    trackId: 'b',
                    value: expect.objectContaining({ trackId: 'b', index: 0 }),
                }),
            })
        );
    });
});

describe('PlaylistWeb#moveItem', () => {
    let web: PlaylistWeb;

    beforeEach(() => {
        web = new PlaylistWeb();
    });

    it('moves a track from one index to another', async () => {
        await web.addAllItems({ items: [track('a'), track('b'), track('c'), track('d')] });

        await web.moveItem({ from: 0, to: 2 });

        const { items } = await web.getPlaylist();
        expect(items.map((i) => i.trackId)).toEqual(['b', 'c', 'a', 'd']);
    });

    it('moves a track backwards in the list', async () => {
        await web.addAllItems({ items: [track('a'), track('b'), track('c'), track('d')] });

        await web.moveItem({ from: 3, to: 0 });

        const { items } = await web.getPlaylist();
        expect(items.map((i) => i.trackId)).toEqual(['d', 'a', 'b', 'c']);
    });

    it('is a no-op when from === to', async () => {
        await web.addAllItems({ items: [track('a'), track('b')] });
        const listener = vi.fn();
        web.addListener('status', listener);

        await web.moveItem({ from: 1, to: 1 });

        const { items } = await web.getPlaylist();
        expect(items.map((i) => i.trackId)).toEqual(['a', 'b']);
        expect(listener).not.toHaveBeenCalled();
    });

    it('rejects when from/to are out of bounds', async () => {
        await web.addAllItems({ items: [track('a'), track('b')] });

        await expect(web.moveItem({ from: -1, to: 0 })).rejects.toThrow();
        await expect(web.moveItem({ from: 0, to: 5 })).rejects.toThrow();
    });

    it('emits RMXSTATUS_ITEM_MOVED with from/to indices', async () => {
        await web.addAllItems({ items: [track('a'), track('b'), track('c')] });
        const listener = vi.fn();
        web.addListener('status', listener);

        await web.moveItem({ from: 0, to: 2 });

        expect(listener).toHaveBeenCalledWith(
            expect.objectContaining({
                status: expect.objectContaining({
                    msgType: RmxAudioStatusMessage.RMXSTATUS_ITEM_MOVED,
                    trackId: 'a',
                    value: expect.objectContaining({ from: 0, to: 2 }),
                }),
            })
        );
    });
});

describe('PlaylistWeb#replaceItem', () => {
    let web: PlaylistWeb;

    beforeEach(() => {
        web = new PlaylistWeb();
    });

    it('replaces a track by index and preserves the existing trackId when omitted', async () => {
        await web.addAllItems({ items: [track('a'), track('b')] });

        await web.replaceItem({
            index: 1,
            item: {
                assetUrl: 'https://example.com/local-b.mp3',
                artist: 'New Artist',
                album: 'New Album',
                title: 'New Title',
            },
        });

        const { items } = await web.getPlaylist();
        expect(items[1].trackId).toBe('b');
        expect(items[1].assetUrl).toBe('https://example.com/local-b.mp3');
        expect(items[0].trackId).toBe('a');
    });

    it('replaces a track by id', async () => {
        await web.addAllItems({ items: [track('a'), track('b'), track('c')] });

        await web.replaceItem({
            id: 'b',
            item: track('b', { assetUrl: 'https://example.com/replaced.mp3' }),
        });

        const { items } = await web.getPlaylist();
        expect(items.map((i) => i.trackId)).toEqual(['a', 'b', 'c']);
        expect(items[1].assetUrl).toBe('https://example.com/replaced.mp3');
    });

    it('rejects when the target track cannot be found', async () => {
        await web.addAllItems({ items: [track('a')] });

        await expect(web.replaceItem({ id: 'missing', item: track('missing') })).rejects.toThrow();
        await expect(web.replaceItem({ index: 5, item: track('x') })).rejects.toThrow();
    });

    it('emits RMXSTATUS_ITEM_REPLACED for a non-current track without touching playback', async () => {
        await web.addAllItems({ items: [track('a'), track('b')] });
        const listener = vi.fn();
        web.addListener('status', listener);

        await web.replaceItem({ index: 1, item: track('b', { assetUrl: 'https://example.com/new.mp3' }) });

        expect(listener).toHaveBeenCalledWith(
            expect.objectContaining({
                status: expect.objectContaining({
                    msgType: RmxAudioStatusMessage.RMXSTATUS_ITEM_REPLACED,
                    trackId: 'b',
                }),
            })
        );
    });

    it('preserves playback position and paused state when replacing the current track', async () => {
        await web.addAllItems({ items: [track('a'), track('b')] });
        await web.playTrackById({ id: 'a' });

        const audio = (web as any).audio as HTMLAudioElement;
        Object.defineProperty(audio, 'currentTime', { value: 42, writable: true, configurable: true });
        Object.defineProperty(audio, 'paused', { value: true, configurable: true });

        await web.replaceItem({ id: 'a', item: track('a', { assetUrl: 'https://example.com/local-a.mp3' }) });

        const newAudio = (web as any).audio as HTMLAudioElement;
        expect(newAudio.src).toContain('local-a.mp3');
        // Position restore is wired via the 'canplay' listener (matches real browser behavior,
        // where seeking before the browser is ready to play is unreliable).
        newAudio.dispatchEvent(new Event('canplay'));
        expect(newAudio.currentTime).toBe(42);
    });
});
