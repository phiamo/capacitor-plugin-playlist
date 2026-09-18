package org.dwbn.plugins.playlist.playlist;

import android.content.Context;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.media3.common.Player;
import org.dwbn.plugins.playlist.manager.PlaylistManager;
import org.dwbn.plugins.playlist.service.MediaService;

/**
 * ExoPlayer playback facade for {@link org.dwbn.plugins.playlist.PlaylistPlugin}. Story 55.4
 * replaces PlaylistCore's {@code DefaultPlaylistHandler}; notification/focus polish is Story 55.5,
 * full video handoff behaviour is Story 55.6.
 */
public class AudioPlaylistHandler {

    private static final String TAG = "PlaylistAudioPlaylistHandler";

    private final Context context;
    private final PlaylistManager playlistManager;

    public AudioPlaylistHandler(Context context, PlaylistManager playlistManager) {
        this.context = context.getApplicationContext();
        this.playlistManager = playlistManager;
    }

    public boolean isPlaying() {
        return playlistManager.isPlaying();
    }

    @Nullable
    public Player getCurrentMediaPlayer() {
        return playlistManager.getPlayer();
    }

    public void play() {
        if (playlistManager.isVideoHandoffPrewarmActive()) {
            playlistManager.ensureForeground();
            return;
        }
        playlistManager.ensureServiceStarted();
        playlistManager.ensureForeground();
        playlistManager.requestAudioFocus();
        Player player = playlistManager.getPlayer();
        if (player != null) {
            player.setPlayWhenReady(true);
        }
    }

    public void pause(boolean isTemporary) {
        if (playlistManager.getVideoHandoffForegroundRetain()) {
            Player player = playlistManager.getPlayer();
            if (player != null && player.isPlaying()) {
                player.setPlayWhenReady(false);
            }
            if (!isTemporary) {
                playlistManager.abandonAudioFocus();
            }
            return;
        }
        Player player = playlistManager.getPlayer();
        if (player != null) {
            player.setPlayWhenReady(false);
        }
        if (!isTemporary) {
            playlistManager.abandonAudioFocus();
        }
    }

    public void seek(long positionMs) {
        Player player = playlistManager.getPlayer();
        if (player != null) {
            player.seekTo(positionMs);
        }
    }

    public void startItemPlayback(long positionMs, boolean startPaused) {
        playlistManager.ensureServiceStarted();
        playlistManager.beginPlaybackAt(positionMs, startPaused);
    }

    public void next() {
        if (!playlistManager.isNextAvailable()) {
            return;
        }
        playlistManager.skipToNext();
    }

    public void previous() {
        if (!playlistManager.isPreviousAvailable()) {
            return;
        }
        playlistManager.skipToPrevious();
    }

    /**
     * Resume at {@code positionMs} after native video ends. Re-requests focus and starts playback.
     */
    public void resumePlaybackAfterVideoHandoff(long positionMs) {
        playlistManager.setVideoHandoffForegroundRetain(false);
        playlistManager.requestAudioFocus();
        play();
        if (positionMs > 0) {
            seek(positionMs);
        }
    }

    /** Release audio focus for video without tearing down the foreground service (Epic 45). */
    public void pauseForVideoHandoff() {
        Player player = playlistManager.getPlayer();
        if (player != null && player.isPlaying()) {
            player.setPlayWhenReady(false);
        }
        playlistManager.abandonAudioFocus();
    }

    public void updateMediaControls() {
        playlistManager.updateForegroundNotification();
    }
}
