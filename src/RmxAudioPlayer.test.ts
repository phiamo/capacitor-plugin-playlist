import { beforeEach, describe, expect, it, vi } from 'vitest';

import { RmxAudioPlayer } from './RmxAudioPlayer';
import type { AudioTrack } from './interfaces';
import { Playlist } from './plugin';

vi.mock('./plugin', () => ({
    Playlist: {
        addItem: vi.fn().mockResolvedValue(undefined),
        moveItem: vi.fn().mockResolvedValue(undefined),
        replaceItem: vi.fn().mockResolvedValue(undefined),
        release: vi.fn().mockResolvedValue(undefined),
        addListener: vi.fn(),
    },
}));

const track = (overrides: Partial<AudioTrack> = {}): AudioTrack => ({
    assetUrl: 'https://example.com/track.mp3',
    artist: 'Artist',
    album: 'Album',
    title: 'Title',
    ...overrides,
});

describe('RmxAudioPlayer#addItem', () => {
    let player: RmxAudioPlayer;

    beforeEach(() => {
        vi.clearAllMocks();
        player = new RmxAudioPlayer();
    });

    it('delegates to Playlist.addItem, generating a trackId when missing', () => {
        player.addItem(track());

        expect(Playlist.addItem).toHaveBeenCalledTimes(1);
        const arg = (Playlist.addItem as any).mock.calls[0][0];
        expect(arg.index).toBeUndefined();
        expect(typeof arg.item.trackId).toBe('string');
        expect(arg.item.trackId.length).toBeGreaterThan(0);
    });

    it('forwards the requested insert index', () => {
        player.addItem(track({ trackId: 'known' }), 2);

        expect(Playlist.addItem).toHaveBeenCalledWith({ item: expect.objectContaining({ trackId: 'known' }), index: 2 });
    });

    it('throws for a null track', () => {
        expect(() => player.addItem(null as any)).toThrow();
    });
});

describe('RmxAudioPlayer#moveItem', () => {
    let player: RmxAudioPlayer;

    beforeEach(() => {
        vi.clearAllMocks();
        player = new RmxAudioPlayer();
    });

    it('delegates to Playlist.moveItem with from/to', () => {
        player.moveItem(0, 3);
        expect(Playlist.moveItem).toHaveBeenCalledWith({ from: 0, to: 3 });
    });
});

describe('RmxAudioPlayer#replaceItem', () => {
    let player: RmxAudioPlayer;

    beforeEach(() => {
        vi.clearAllMocks();
        player = new RmxAudioPlayer();
    });

    it('preserves an omitted trackId (does not auto-generate a new one)', () => {
        const replacement = track();
        expect(replacement.trackId).toBeUndefined();

        player.replaceItem(replacement, { index: 1 });

        expect(Playlist.replaceItem).toHaveBeenCalledTimes(1);
        const arg = (Playlist.replaceItem as any).mock.calls[0][0];
        expect(arg.index).toBe(1);
        expect(arg.id).toBeUndefined();
        expect(arg.item.trackId).toBeUndefined();
        // Regression guard: the item object passed through must be the exact same
        // object reference (untouched by validateTrack), proving no id was injected.
        expect(arg.item).toBe(replacement);
    });

    it('forwards an explicit trackId spec', () => {
        player.replaceItem(track(), { trackId: 'existing-id' });

        expect(Playlist.replaceItem).toHaveBeenCalledWith({
            item: expect.any(Object),
            index: undefined,
            id: 'existing-id',
        });
    });

    it('throws when neither index nor trackId is provided', () => {
        expect(() => player.replaceItem(track(), {})).toThrow();
    });

    it('throws for a null track', () => {
        expect(() => player.replaceItem(null as any, { index: 0 })).toThrow();
    });
});
