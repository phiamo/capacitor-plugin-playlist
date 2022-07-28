import {Injectable, NgZone} from '@angular/core';
import {ReplaySubject} from 'rxjs';
import {
  AudioTrack,
  AudioTrackRemoval,
  OnStatusCallbackData,
  OnStatusErrorCallbackData,
  PlaylistItemOptions,
  RmxAudioPlayer,
} from 'capacitor-plugin-playlist';
import 'capacitor-plugin-playlist';
import {environment} from '@env/environment';


/**
 * This is meant as an example for usage in angular 2+
 * Adopt it to your own needs, most probably use another interface than AudioTrack to fulfil contracts with your app,
 * e.g. add a service for offline playback, which can deliver a different url depending if the file is local or not
 */
@Injectable({
  providedIn: 'root'
})
export class AudioProvider {
  status$ = new ReplaySubject<OnStatusCallbackData | OnStatusErrorCallbackData>(1);
  private audioPlayer: RmxAudioPlayer;
  constructor(
    protected zone: NgZone,
  ) {
    this.audioPlayer = new RmxAudioPlayer();
  }
  get isPlaying() {
    return this.audioPlayer.currentState === 'playing';
  }

  async init() {
    // dont forget to init the player to connect the events stream
    await this.audioPlayer.initialize();
    this.audioPlayer.on('status', (data: OnStatusCallbackData | OnStatusErrorCallbackData) => {
      // run in zone, so e.g. change detection can act, since the event is triggered outside
      this.zone.run(() => {
        this.status$.next(data);
      });
    });

    await this.audioPlayer.setOptions({
      verbose: environment.production,
      options: {
        icon: 'icon_bw'
      },
    });
    // set loop true, made android behave correctly
    await this.audioPlayer.setLoop( true);
  }

  async setCurrent(audioTrack: AudioTrack, currentPosition: number) {
    const wasPlaying = this.isPlaying;

    if (wasPlaying) {
      await this.audioPlayer.playTrackById(audioTrack.trackId, currentPosition);
    } else {
      await this.audioPlayer.selectTrackById(audioTrack.trackId, currentPosition);
    }
  }

  async setPlaylistItems(audioTracks: AudioTrack[], currentItem?: AudioTrack, currentPosition= 0) {
    let currentId = null;

    let options: PlaylistItemOptions = {};
    if (currentItem) {
      currentId = currentItem.trackId;
      const startPaused = !this.isPlaying;
      options = {
        playFromPosition: currentPosition,
        startPaused,
        retainPosition: true,
        playFromId: currentId
      };
    }

    return this.audioPlayer.setPlaylistItems(audioTracks, options);
  }

  async addItem(audioTrack: AudioTrack) {
    if (!audioTrack) {
      return;
    }
    await this.audioPlayer.addItem(audioTrack);
  }

  async removeItem(audioTrack: AudioTrack) {
    const removeItem: AudioTrackRemoval = {
      trackId: audioTrack.trackId
    };
    await this.audioPlayer.removeItem(removeItem);
  }

  clearAllItems() {
    return this.audioPlayer.clearAllItems();
  }

  /**
   * Playback management
   */

  play() {
    return this.audioPlayer.play();
  }

  pause() {
    return this.audioPlayer.pause();
  }

  skipForward() {
    return this.audioPlayer.skipForward();
  }

  skipBack() {
    return this.audioPlayer.skipBack();
  }

  async seekTo(position: number) {
    const wasPlaying = this.isPlaying;
    return this.audioPlayer.seekTo(position).then(() => {
      if (wasPlaying) {
        return this.play();
      }
    });
  }

  setPlaybackRate(rate: number) {
    return this.audioPlayer.setPlaybackRate(rate);
  }

}
