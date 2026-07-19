package org.dwbn.plugins.playlist.playlist;

import android.app.Service;
import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.devbrackets.android.playlistcore.api.MediaPlayerApi;
import com.devbrackets.android.playlistcore.api.PlaylistItem;
import com.devbrackets.android.playlistcore.components.audiofocus.AudioFocusProvider;
import com.devbrackets.android.playlistcore.components.audiofocus.DefaultAudioFocusProvider;
import com.devbrackets.android.playlistcore.components.image.ImageProvider;
import com.devbrackets.android.playlistcore.components.mediacontrols.DefaultMediaControlsProvider;
import com.devbrackets.android.playlistcore.components.mediacontrols.MediaControlsProvider;
import com.devbrackets.android.playlistcore.components.mediasession.DefaultMediaSessionProvider;
import com.devbrackets.android.playlistcore.components.mediasession.MediaSessionProvider;
import com.devbrackets.android.playlistcore.components.playlisthandler.DefaultPlaylistHandler;
import com.devbrackets.android.playlistcore.data.PlaybackState;
import com.devbrackets.android.playlistcore.manager.BasePlaylistManager;

import org.dwbn.plugins.playlist.RmxAudioPlayer;
import org.dwbn.plugins.playlist.data.AudioTrack;
import org.dwbn.plugins.playlist.manager.PlaylistManager;
import org.dwbn.plugins.playlist.notification.PlaylistNotificationProvider;
import org.dwbn.plugins.playlist.service.MediaService;
import org.jetbrains.annotations.NotNull;


public class AudioPlaylistHandler<I extends PlaylistItem, M extends BasePlaylistManager<I>>
            extends DefaultPlaylistHandler<I, M> {

    private static final String TAG = "PlaylistAudioPlaylistHandler";

    AudioPlaylistHandler(
            Context context,
            Class<? extends Service> serviceClass,
            M playlistManager,
            ImageProvider<I> imageProvider,
            com.devbrackets.android.playlistcore.components.notification.PlaylistNotificationProvider notificationProvider,
            MediaSessionProvider mediaSessionProvider,
            MediaControlsProvider mediaControlsProvider,
            AudioFocusProvider<I> audioFocusProvider,
            @Nullable Listener<I> listener
    ) {
        super(context, serviceClass, playlistManager, imageProvider, notificationProvider,
                mediaSessionProvider, mediaControlsProvider, audioFocusProvider, listener);
        // Lmao this entire class exists for the sake of this one line
        // The default value is 30fps (e.g 33ms), which would overwhelm the Cordova webview with messages
        // Ideally we could make this configurable.
        getMediaProgressPoll().setProgressPollDelay(1000);
    }

    public void next() {
        if (!getPlaylistManager().isNextAvailable()) {
            return;
        }
        getPlaylistManager().next();
        startItemPlayback(0, !this.isPlaying());
    }

    public void previous() {
        if (!getPlaylistManager().isPreviousAvailable()) {
            return;
        }
        getPlaylistManager().previous();
        startItemPlayback(0, !this.isPlaying());
    }

    /**
     * Request audio focus before starting ExoMedia — DefaultPlaylistHandler.play() starts the
     * MediaPlayer first, which fails silently after native video handoff (focus abandoned in
     * pauseForVideoHandoff while the video ExoPlayer still held the stream).
     * During Epic 45 prewarm (native video active), never request focus or start audible playback.
     */
    @Override
    public void play() {
        if (isVideoHandoffPrewarmActive()) {
            setupForeground();
            return;
        }
        getAudioFocusProvider().requestFocus();
        com.devbrackets.android.playlistcore.api.MediaPlayerApi<I> mediaPlayer = getCurrentMediaPlayer();
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            }
            mediaPlayer.play();
        }
        getMediaProgressPoll().start();
        setPlaybackState(PlaybackState.PLAYING);
        setupForeground();
    }

    /**
     * Resume at {@code positionMs} after native video ends. Re-requests focus and starts playback.
     * <p>
     * Must {@link #play()} before {@link #seek(long)}: playlistcore {@code performSeek} sets
     * {@code playingBeforeSeek = isPlaying()}. After video handoff the player is paused, so
     * seek-then-play races with {@code onSeekComplete}, which calls {@code pause()} when
     * {@code playingBeforeSeek} is false — UI briefly shows playing while native audio stays silent.
     */
    public void resumePlaybackAfterVideoHandoff(long positionMs) {
        ((PlaylistManager) getPlaylistManager()).setVideoHandoffForegroundRetain(false);
        setStartPaused(false);
        getAudioFocusProvider().requestFocus();
        play();
        if (positionMs > 0) {
            seek(positionMs);
        }
    }

    /**
     * Release audio focus for video without tearing down the foreground service (Epic 45 / Android 17).
     */
    public void pauseForVideoHandoff() {
        if (isPlaying()) {
            com.devbrackets.android.playlistcore.api.MediaPlayerApi<I> mediaPlayer = getCurrentMediaPlayer();
            if (mediaPlayer != null) {
                mediaPlayer.pause();
            }
        }
        getMediaProgressPoll().stop();
        setPlaybackState(com.devbrackets.android.playlistcore.data.PlaybackState.PAUSED);
        getAudioFocusProvider().abandonFocus();
    }

    @Override
    public void pause(boolean isTemporary) {
        if (((PlaylistManager) getPlaylistManager()).getVideoHandoffForegroundRetain()) {
            if (isPlaying()) {
                com.devbrackets.android.playlistcore.api.MediaPlayerApi<I> mediaPlayer = getCurrentMediaPlayer();
                if (mediaPlayer != null) {
                    mediaPlayer.pause();
                }
            }
            getMediaProgressPoll().stop();
            setPlaybackState(com.devbrackets.android.playlistcore.data.PlaybackState.PAUSED);
            if (!isTemporary) {
                getAudioFocusProvider().abandonFocus();
            }
            return;
        }
        super.pause(isTemporary);
    }

    @Override
    protected void relaxResources() {
        if (((PlaylistManager) getPlaylistManager()).getVideoHandoffForegroundRetain()) {
            return;
        }
        super.relaxResources();
    }

    /**
     * Android 17 AudioHardening: re-calling startForeground from background revokes WIU and mutes playback.
     * When prewarm already promoted MediaService, only update the notification via super when not foreground.
     */
    @Override
    protected void setupForeground() {
        MediaService service = MediaService.getInstance();
        if (service != null && service.isRunningInForeground()) {
            return;
        }
        super.setupForeground();
    }

    @Override
    public void onPrepared(@NotNull MediaPlayerApi<I> mediaPlayer) {
        if (isVideoHandoffPrewarmActive()) {
            // Prewarm: prepare at seek position but stay silent — native video owns audio focus.
            super.onPrepared(mediaPlayer);
            com.devbrackets.android.playlistcore.api.MediaPlayerApi<I> currentPlayer = getCurrentMediaPlayer();
            if (currentPlayer != null && currentPlayer.isPlaying()) {
                currentPlayer.pause();
            }
            getMediaProgressPoll().stop();
            setPlaybackState(com.devbrackets.android.playlistcore.data.PlaybackState.PAUSED);
            getAudioFocusProvider().abandonFocus();
            return;
        }
        super.onPrepared(mediaPlayer);
    }

    private boolean isVideoHandoffPrewarmActive() {
        return ((PlaylistManager) getPlaylistManager()).getVideoHandoffForegroundRetain();
    }

    @Override
    public boolean onError(@NotNull MediaPlayerApi<I> mediaPlayer) {
        ((PlaylistManager)getPlaylistManager()).setCurrentErrorTrack((AudioTrack) getCurrentPlaylistItem());
        int currentIndex = getPlaylistManager().getCurrentPosition();
        int currentErrorCount = getSequentialErrors();

        super.onError(mediaPlayer);
        // Do not set startPaused to false if we are at the first item.
        // For all other items, the user MUST have triggered playback;
        // for item 0, they will never have done so at this point (since the tracks
        // are auto-buffered when a list is loaded).
        // This is a bit of a guess. What happens of tracks 2,3,4 are also broken?
        // User has no network? The only way to be *certain* is to capture all user interaction
        // input points and create a global flag "somewhere" that says the user has tried to play.
        // Maintaining that would be a nightmare.
        if (currentIndex > 0 && currentErrorCount <= 3) {
            Log.e(TAG, "ListHandler error: setting startPaused to false");
            setStartPaused(false);
        }

        return false;
    }

    @Override
    public void onSeekComplete(MediaPlayerApi<I> mediaPlayer) {
        Log.i(TAG, "onSeekComplete! " + mediaPlayer.getCurrentPosition());
        getCurrentMediaProgress().update(mediaPlayer.getCurrentPosition(), mediaPlayer.getBufferedPercent(), mediaPlayer.getDuration());
        super.onSeekComplete(mediaPlayer);
    }

    @Override
    public void onCompletion(@NotNull MediaPlayerApi<I> mediaPlayer) {
        ((RmxAudioPlayer)super.getPlaylistManager().getPlaybackStatusListener()).onCompletion((AudioTrack) getCurrentPlaylistItem());
        Log.i("AudioPlaylistHandler", "onCompletion");
        // This is called when a single item completes playback.
        // For now, the superclass does the right thing, but we may need to override.
        super.onCompletion(mediaPlayer);
    }


    public static class Builder<I extends PlaylistItem, M extends BasePlaylistManager<I>> {

        Context context;
        Class<? extends Service> serviceClass;
        M playlistManager;
        ImageProvider<I> imageProvider;

        com.devbrackets.android.playlistcore.components.notification.PlaylistNotificationProvider notificationProvider = null;
        MediaSessionProvider mediaSessionProvider = null;
        MediaControlsProvider mediaControlsProvider = null;
        AudioFocusProvider<I> audioFocusProvider = null;
        Listener<I> listener;
        public Builder(Context context, Class<? extends Service> serviceClass,
                       M playlistManager, ImageProvider<I> imageProvider, Listener<I> listener) {
            this.context = context;
            this.serviceClass = serviceClass;
            this.playlistManager = playlistManager;
            this.imageProvider = imageProvider;
            this.listener = listener;
        }

        public AudioPlaylistHandler<I, M> build() {
            return new AudioPlaylistHandler<>(context,
                serviceClass,
                playlistManager,
                imageProvider,
                notificationProvider != null ? notificationProvider : new PlaylistNotificationProvider(context),
                mediaSessionProvider != null ? mediaSessionProvider : new DefaultMediaSessionProvider(context, serviceClass),
                mediaControlsProvider != null ? mediaControlsProvider : new DefaultMediaControlsProvider(context),
                audioFocusProvider != null ? audioFocusProvider : new DefaultAudioFocusProvider<I>(context),
                listener);
        }
    }
}
