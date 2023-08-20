package org.dwbn.plugins.playlist.playlist;

import android.app.Service;
import android.content.Context;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.NonNull;
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
import com.devbrackets.android.playlistcore.data.MediaInfo;
import com.devbrackets.android.playlistcore.data.MediaProgress;
import com.devbrackets.android.playlistcore.manager.BasePlaylistManager;
import org.dwbn.plugins.playlist.RmxAudioPlayer;
import org.dwbn.plugins.playlist.data.AudioTrack;
import org.dwbn.plugins.playlist.manager.PlaylistManager;
import org.dwbn.plugins.playlist.notification.PlaylistNotificationProvider;
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

    @Override
    public void onPrepared(@NotNull MediaPlayerApi<I> mediaPlayer) {
        super.onPrepared(mediaPlayer);
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

    /**
     * A workaround to set current position into MediaControls session.
     *
     * @deprecated Can be delete after PlaylistCore updated.
     */
    @Override
    public boolean onProgressUpdated(@NonNull MediaProgress mediaProgress) {
        MediaPlayerApi<I> currentMediaPlayer = getCurrentMediaPlayer();

        long pos = currentMediaPlayer != null ? currentMediaPlayer.getCurrentPosition() : PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN;

        PlaybackStateCompat.Builder playbackStateBuilder = new PlaybackStateCompat.Builder();
        playbackStateBuilder.setState(
                isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_CONNECTING,
                pos,
                1.0f
        );
        getMediaSessionProvider().get().setPlaybackState(playbackStateBuilder.build());

        return super.onProgressUpdated(mediaProgress);
    }

    /**
     * A workaround to set duration into MediaControls metadata.
     *
     * @deprecated Can be delete after PlaylistCore updated.
     */
    @Override
    public void updateMediaControls() {
        super.updateMediaControls();

        MediaPlayerApi<I> currentMediaPlayer = getCurrentMediaPlayer();

        long duration = currentMediaPlayer != null ? currentMediaPlayer.getDuration() : -1;

        // Set duration to Media Controls.
        MediaMetadataCompat.Builder metaDataBuilder = new MediaMetadataCompat.Builder();
        metaDataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);

        getMediaSessionProvider().get().setMetadata(metaDataBuilder.build());
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
