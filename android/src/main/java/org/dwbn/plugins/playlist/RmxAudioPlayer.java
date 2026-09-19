package org.dwbn.plugins.playlist;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import org.dwbn.plugins.playlist.data.AudioTrack;
import org.dwbn.plugins.playlist.manager.MediaControlsListener;
import org.dwbn.plugins.playlist.manager.Options;
import org.dwbn.plugins.playlist.manager.PlaybackProgress;
import org.dwbn.plugins.playlist.manager.PlaylistManager;
import org.dwbn.plugins.playlist.manager.RmxPlaybackState;
import org.dwbn.plugins.playlist.playlist.AudioPlaylistHandler;
import org.dwbn.plugins.playlist.service.MediaNotificationPolicy;
import org.dwbn.plugins.playlist.service.MediaService;
import org.json.JSONException;
import org.json.JSONObject;

@OptIn(markerClass = UnstableApi.class)
public class RmxAudioPlayer implements MediaControlsListener {

    public static String TAG = "PlaylistRmxAudioPlayer";
    private static final int PLAYLIST_ID = 32;
    private static final long POSITION_TICK_MS = 1000L;

    private final PlaylistManager playlistManager;
    private final OnStatusReportListener statusListener;
    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private int lastBufferPercent = 0;
    private long lastDuration = 0;
    private boolean trackLoaded = false;
    private boolean resetStreamOnPause = true;
    private boolean listenersRegistered = false;
    private ExoPlayer attachedPlayer;

    private final Runnable positionTicker = new Runnable() {
        @Override
        public void run() {
            emitPositionIfNeeded();
            if (listenersRegistered) {
                mainHandler.postDelayed(this, POSITION_TICK_MS);
            }
        }
    };

    public RmxAudioPlayer(@NonNull OnStatusReportListener statusListener, @NonNull Context context) {
        this.statusListener = statusListener;
        this.context = context.getApplicationContext();
        this.playlistManager = PlaylistRuntime.getPlaylistManager(this.context);

        playlistManager.setId(PLAYLIST_ID);
        playlistManager.setPlaybackStatusListener(this);
        playlistManager.setOnErrorListener(this);
        playlistManager.setMediaControlsListener(this);
    }

    public PlaylistManager getPlaylistManager() {
        return playlistManager;
    }

    public boolean getResetStreamOnPause() {
        return resetStreamOnPause;
    }

    public void setResetStreamOnPause(boolean val) {
        resetStreamOnPause = val;
        getPlaylistManager().setResetStreamOnPause(getResetStreamOnPause());
    }

    public void setOptions(JSONObject val) {
        Options options = new Options(context, val);
        getPlaylistManager().setOptions(options);
    }

    public float getVolume() {
        return (getVolumeLeft() + getVolumeRight()) / 2f;
    }

    public float getVolumeLeft() {
        return playlistManager.getVolumeLeft();
    }

    public float getVolumeRight() {
        return playlistManager.getVolumeRight();
    }

    public void setVolume(float both) {
        setVolume(both, both);
    }

    public void setVolume(float left, float right) {
        playlistManager.setVolume(left, right);
    }

    /**
     * A track played to its end. {@code nextAvailable} is judged from the finished track, because
     * Media3 has already moved to the next item when it reports the transition.
     */
    public void onCompletion(AudioTrack item, boolean nextAvailable) {
        if (item != null) {
            String trackId = item.getTrackId();
            JSONObject trackStatus = getPlayerStatus(item);
            onStatus(RmxAudioStatusMessage.RMXSTATUS_COMPLETED, trackId, trackStatus);
        }

        if (!nextAvailable) {
            onStatus(RmxAudioStatusMessage.RMXSTATUS_PLAYLIST_COMPLETED, "INVALID", null);
        }
    }

    @Override
    public void onPrevious(AudioTrack currentItem, int currentIndex) {
        JSONObject param = new JSONObject();
        String trackId = currentItem == null ? "NONE" : currentItem.getTrackId();

        try {
            param.put("currentIndex", currentIndex);
            param.put("currentItem", currentItem != null ? currentItem.toDict() : null);
        } catch (JSONException e) {
            Log.i(TAG, "Error generating onPrevious status message: " + e.toString());
        }

        onStatus(RmxAudioStatusMessage.RMX_STATUS_SKIP_BACK, trackId, param);
    }

    @Override
    public void onNext(AudioTrack currentItem, int currentIndex) {
        JSONObject param = new JSONObject();
        String trackId = currentItem == null ? "NONE" : currentItem.getTrackId();

        try {
            param.put("currentIndex", currentIndex);
            param.put("currentItem", currentItem != null ? currentItem.toDict() : null);
        } catch (JSONException e) {
            Log.i(TAG, "Error generating onNext status message: " + e.toString());
        }
        onStatus(RmxAudioStatusMessage.RMX_STATUS_SKIP_FORWARD, trackId, param);
    }

    @OptIn(markerClass = UnstableApi.class)
    public void onNativePlayerError(PlaybackException e) {
        RmxAudioErrorType errorType = RmxPlaybackErrorMapper.fromMedia3ErrorCode(e.errorCode);
        String errorMsg = RmxPlaybackErrorMapper.isSourceError(e.errorCode)
            ? "PlaybackException.TYPE_SOURCE: " + e.getMessage()
            : "PlaybackException: " + e.getMessage();

        AudioTrack errorItem = playlistManager.getCurrentErrorTrack();
        String trackId = errorItem != null ? errorItem.getTrackId() : "INVALID";

        Log.i(TAG, "Error playing audio track: [" + trackId + "]: " + errorMsg);
        onError(errorType, trackId, errorMsg);
        playlistManager.setCurrentErrorTrack(null);
    }

    public void onNativeItemCompleted(@Nullable AudioTrack item, boolean nextAvailable) {
        onCompletion(item, nextAvailable);
        if (item != null) {
            String trackId = item.getTrackId();
            JSONObject trackStatus = getPlayerStatus(item);
            onStatus(RmxAudioStatusMessage.RMXSTATUS_STOPPED, trackId, trackStatus);
        }
    }

    public void onNativePlaylistCompleted() {
        Log.i(TAG, "onPlaylistEnded");
        playlistManager.setShouldStopPlaylist(false);
    }

    public void onNativeTrackChanged(@Nullable AudioTrack currentItem, boolean hasNext, boolean hasPrevious) {
        JSONObject info = new JSONObject();
        String trackId = currentItem == null ? "NONE" : currentItem.getTrackId();
        try {
            info.put("currentItem", currentItem != null ? currentItem.toDict() : null);
            info.put("currentIndex", playlistManager.getCurrentPosition());
            info.put("isAtEnd", !hasNext);
            info.put("isAtBeginning", !hasPrevious);
            info.put("hasNext", hasNext);
            info.put("hasPrevious", hasPrevious);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating onPlaylistItemChanged message: " + e.toString());
        }

        lastDuration = 0;
        lastBufferPercent = 0;
        trackLoaded = false;

        onStatus(RmxAudioStatusMessage.RMXSTATUS_TRACK_CHANGED, trackId, info);
    }

    public void onNativePlaybackStateChanged(@NonNull RmxPlaybackState playbackState) {
        AudioTrack currentItem = playlistManager.getCurrentItem();
        JSONObject trackStatus = getPlayerStatus(currentItem);
        Log.i("onPlaybackStateChanged", playbackState.toString() + ", " + trackStatus.toString() + ", " + currentItem);

        switch (playbackState) {
            case STOPPED:
                onStatus(RmxAudioStatusMessage.RMXSTATUS_STOPPED, "INVALID", null);
                break;

            case RETRIEVING:
            case PREPARING:
                if (currentItem != null && currentItem.getTrackId() != null) {
                    onStatus(RmxAudioStatusMessage.RMXSTATUS_LOADING, currentItem.getTrackId(), trackStatus);
                }
                break;
            case SEEKING:
                PlaybackProgress progress = playlistManager.getCurrentProgress();
                if (currentItem != null && currentItem.getTrackId() != null && progress != null) {
                    JSONObject info = new JSONObject();
                    try {
                        info.put("position", progress.getPosition() / 1000f);
                        onStatus(RmxAudioStatusMessage.RMXSTATUS_SEEK, currentItem.getTrackId(), info);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error generating seeking status message: " + e.toString());
                    }
                }
                break;
            case PLAYING:
                if (currentItem != null && currentItem.getTrackId() != null) {
                    if (!trackLoaded) {
                        onStatus(RmxAudioStatusMessage.RMXSTATUS_CANPLAY, currentItem.getTrackId(), trackStatus);
                        trackLoaded = true;
                    }
                    onStatus(RmxAudioStatusMessage.RMXSTATUS_PLAYING, currentItem.getTrackId(), trackStatus);
                }
                break;
            case PAUSED:
                if (currentItem != null && currentItem.getTrackId() != null) {
                    onStatus(RmxAudioStatusMessage.RMXSTATUS_PAUSE, currentItem.getTrackId(), trackStatus);
                }
                break;
            case ERROR:
            default:
                break;
        }
    }

    private void emitPositionIfNeeded() {
        AudioTrack currentItem = playlistManager.getCurrentItem();
        RmxPlaybackState playbackState = playlistManager.getCurrentPlaybackState();
        PlaybackProgress progress = playlistManager.getCurrentProgress();

        if (currentItem == null || progress == null) {
            return;
        }

        updateTrackProgress(currentItem, progress);

        if (progress.getBufferPercent() != lastBufferPercent) {
            JSONObject trackStatus = getPlayerStatus(currentItem);
            if (progress.getBufferPercent() >= 100f) {
                onStatus(RmxAudioStatusMessage.RMXSTATUS_LOADED, currentItem.getTrackId(), trackStatus);
            }

            if (!trackLoaded) {
                onStatus(RmxAudioStatusMessage.RMXSTATUS_CANPLAY, currentItem.getTrackId(), trackStatus);
                trackLoaded = true;
            }

            onStatus(RmxAudioStatusMessage.RMXSTATUS_BUFFERING, currentItem.getTrackId(), trackStatus);
            lastBufferPercent = progress.getBufferPercent();
        }

        if (lastDuration != progress.getDuration() && progress.getDuration() > 0) {
            onStatus(RmxAudioStatusMessage.RMXSTATUS_DURATION, currentItem.getTrackId(), getPlayerStatus(currentItem));
            lastDuration = progress.getDuration();
        }

        if (playbackState == RmxPlaybackState.PLAYING || playbackState == RmxPlaybackState.SEEKING
                || (playbackState == RmxPlaybackState.PREPARING && progress.getDuration() == 0)) {
            onStatus(RmxAudioStatusMessage.RMXSTATUS_PLAYBACK_POSITION, currentItem.getTrackId(), getPlayerStatus(currentItem));
        }
    }

    private void updateTrackProgress(AudioTrack currentItem, PlaybackProgress progress) {
        currentItem.setDuration(progress.getDuration());
        currentItem.setBufferPercent(progress.getBufferPercent());
        currentItem.setBufferPercentFloat(progress.getBufferPercentFloat());
    }

    public JSONObject getPlayerStatus(@Nullable AudioTrack statusItem) {
        AudioTrack currentItem = statusItem != null ? statusItem : playlistManager.getCurrentItem();
        RmxPlaybackState playbackState = playlistManager.getCurrentPlaybackState();
        PlaybackProgress progress = playlistManager.getCurrentProgress();

        String status = "unknown";
        switch (playbackState) {
            case STOPPED:
                status = "stopped";
                break;
            case ERROR:
                status = "error";
                break;
            case RETRIEVING:
            case SEEKING:
            case PREPARING:
                status = "loading";
                break;
            case PLAYING:
                status = "playing";
                break;
            case PAUSED:
                status = "paused";
                break;
            default:
                break;
        }

        String trackId = "";
        boolean isStream = false;
        float bufferPercentFloat = 0;
        int bufferPercent = 0;
        long duration = 0;
        long position = 0;

        if (progress != null) {
            position = progress.getPosition();
        }

        if (currentItem != null) {
            isStream = currentItem.isStream();
            trackId = currentItem.getTrackId();
            bufferPercentFloat = currentItem.getBufferPercentFloat();
            bufferPercent = currentItem.getBufferPercent();
            duration = currentItem.getDuration();
        }

        JSONObject trackStatus = new JSONObject();
        try {
            trackStatus.put("trackId", trackId);
            trackStatus.put("isStream", isStream);
            trackStatus.put("currentIndex", playlistManager.getCurrentPosition());
            trackStatus.put("status", status);
            trackStatus.put("currentPosition", position / 1000.0);
            trackStatus.put("duration", duration / 1000.0);
            trackStatus.put("playbackPercent", duration > 0 ? (((double) position / duration) * 100.0) : 0);
            trackStatus.put("bufferPercent", bufferPercent);
            trackStatus.put("bufferStart", 0.0);
            trackStatus.put("bufferEnd", (bufferPercentFloat * duration) / 1000.0);
        } catch (JSONException e) {
            Log.e(TAG, "Error generating player status: " + e.toString());
        }

        return trackStatus;
    }

    public void pause() {
        Log.i(TAG, "Pausing, removing event listeners");
        stopPositionTicker();
    }

    public void resume() {
        Log.i(TAG, "Resumed, wiring up event listeners");
        startPositionTicker();
        updateCurrentPlaybackInformation();
    }

    @OptIn(markerClass = UnstableApi.class)
    public void onPlayerAttached(ExoPlayer player) {
        attachedPlayer = player;
        if (listenersRegistered) {
            startPositionTicker();
        }
    }

    public void onPlayerDetached() {
        attachedPlayer = null;
        stopPositionTicker();
    }

    private void updateCurrentPlaybackInformation() {
        AudioTrack currentItem = playlistManager.getCurrentItem();
        if (currentItem != null) {
            onNativeTrackChanged(
                    currentItem,
                    playlistManager.isNextAvailable(),
                    playlistManager.isPreviousAvailable()
            );
        }

        RmxPlaybackState currentPlaybackState = playlistManager.getCurrentPlaybackState();
        if (currentPlaybackState != RmxPlaybackState.STOPPED) {
            onNativePlaybackStateChanged(currentPlaybackState);
        }

        emitPositionIfNeeded();
    }

    private void startPositionTicker() {
        listenersRegistered = true;
        mainHandler.removeCallbacks(positionTicker);
        mainHandler.post(positionTicker);
    }

    private void stopPositionTicker() {
        listenersRegistered = false;
        mainHandler.removeCallbacks(positionTicker);
    }

    private void onError(RmxAudioErrorType errorCode, String trackId, String message) {
        statusListener.onError(errorCode, trackId, message);
    }

    private void onStatus(RmxAudioStatusMessage what, String trackId, JSONObject param) {
        statusListener.onStatus(what, trackId, param);
    }

    private float lastKnownHandoffPositionSec = 0f;

    public void prepareForVideoHandoff() {
        PlaybackProgress progress = playlistManager.getCurrentProgress();
        if (progress != null) {
            lastKnownHandoffPositionSec = progress.getPosition() / 1000f;
        } else {
            lastKnownHandoffPositionSec = 0f;
        }
        if (MediaNotificationPolicy.shouldRetainForegroundOnPrepare()) {
            playlistManager.setVideoHandoffForegroundRetain(true);
        }
        AudioPlaylistHandler handler = playlistManager.getPlaylistHandler();
        if (handler != null) {
            handler.pauseForVideoHandoff();
        }
    }

    public boolean resumeAfterVideoHandoff(float positionSec) {
        return resumeAfterVideoHandoff(positionSec, false, true);
    }

    public boolean resumeAfterVideoHandoff(float positionSec, boolean prewarm) {
        return resumeAfterVideoHandoff(positionSec, prewarm, true);
    }

    public boolean resumeAfterVideoHandoff(float positionSec, boolean prewarm, boolean play) {
        lastKnownHandoffPositionSec = positionSec;
        long positionMs = (long) (positionSec * 1000f);
        if (prewarm) {
            playlistManager.setVideoHandoffForegroundRetain(true);
            playlistManager.beginPlayback(positionMs, true);
            return false;
        }
        MediaService service = MediaService.getInstance();
        boolean inForeground = service != null && service.isRunningInForeground();
        if (MediaNotificationPolicy.shouldReportInPlaceResumed(
                false,
                play,
                playlistManager.getVideoHandoffForegroundRetain(),
                inForeground
        ) && tryResumeVideoHandoffInPlace(positionMs)) {
            return true;
        }
        playlistManager.setVideoHandoffForegroundRetain(false);
        if (MediaNotificationPolicy.shouldBeginPlaybackWhenInPlaceUnavailable()) {
            playlistManager.beginPlayback(positionMs, true);
        }
        return false;
    }

    public boolean tryResumeVideoHandoffInPlace(long positionMs) {
        if (!playlistManager.getVideoHandoffForegroundRetain()) {
            return false;
        }
        MediaService service = MediaService.getInstance();
        if (service == null || !service.isRunningInForeground()) {
            return false;
        }
        AudioPlaylistHandler audioHandler = playlistManager.getPlaylistHandler();
        if (audioHandler == null) {
            return false;
        }
        Player mediaPlayer = audioHandler.getCurrentMediaPlayer();
        if (mediaPlayer != null) {
            audioHandler.resumePlaybackAfterVideoHandoff(positionMs);
        } else {
            audioHandler.startItemPlayback(positionMs, false);
        }
        playlistManager.setVideoHandoffForegroundRetain(false);
        return true;
    }

    public float getLastKnownPositionSec() {
        return lastKnownHandoffPositionSec;
    }

    public void emitPlaybackSnapshot() {
        AudioTrack currentItem = playlistManager.getCurrentItem();
        if (currentItem == null) {
            return;
        }
        JSONObject trackStatus = getPlayerStatus(currentItem);
        onStatus(RmxAudioStatusMessage.RMXSTATUS_PLAYBACK_POSITION, currentItem.getTrackId(), trackStatus);
    }
}
