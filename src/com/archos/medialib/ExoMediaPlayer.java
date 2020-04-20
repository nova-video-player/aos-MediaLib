package com.archos.medialib;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.EventLogger;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoListener;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.google.android.exoplayer2.C.TRACK_TYPE_AUDIO;
import static com.google.android.exoplayer2.C.TRACK_TYPE_TEXT;

public class ExoMediaPlayer extends GenericMediaPlayer implements Player.EventListener, VideoListener, TextOutput {
    private static final String TAG = "ExoMediaPlayer";
    private SimpleExoPlayer exoPlayer;
    private DataSource.Factory dataSourceFactory;
    private MediaSource videoSource;
    private int startTime = 0;
    private TrackGroupArray lastSeenTrackGroupArray;
    private DefaultTrackSelector trackSelector;
    private MediaMetadata mediaMetadata;

    ExoMediaPlayer(Context context) {
        Looper looper;
        if ((looper = Looper.myLooper()) != null) {
            mEventHandler = new EventHandler(this, looper);
        } else if ((looper = Looper.getMainLooper()) != null) {
            mEventHandler = new EventHandler(this, looper);
        } else {
            mEventHandler = null;
        }
        trackSelector = new DefaultTrackSelector(context);
        exoPlayer = new SimpleExoPlayer
                .Builder(context)
                .setTrackSelector(trackSelector)
                .setLooper(looper)
                .build();
        exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC);
        exoPlayer.addListener(this);
        exoPlayer.addVideoListener(this);
        exoPlayer.addTextOutput(this);
        dataSourceFactory = new DefaultDataSourceFactory(context,
                Util.getUserAgent(context, "NovaVideoPlayer"));
        exoPlayer.addAnalyticsListener(new EventLogger(trackSelector));
    }

    @Override
    public int getType() {
        return 0;
    }

    @Override
    public void setDisplay(SurfaceHolder sh) {
        exoPlayer.setVideoSurfaceHolder(sh);
    }

    @Override
    public void setSurface(Surface surface) {
        exoPlayer.setVideoSurface(surface);
    }

    @Override
    public void setDataSource2(String path, Map<String, String> headers) throws IOException, IllegalArgumentException, SecurityException, IllegalStateException {
        Log.d(TAG, "setDataSource2 with " + path);
        if (SmbProxy.needToStream(Uri.parse(path).getScheme())){
            mSmbProxy = SmbProxy.setDataSource(Uri.parse(path), this, headers);
            return;
        }

        videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(Uri.parse(path));
    }

    @Override
    public void setDataSource(FileDescriptor fd, long offset, long length) throws IOException, IllegalArgumentException, IllegalStateException {

    }

    @Override
    public void prepare() throws IOException, IllegalStateException {
        exoPlayer.prepare(videoSource);
    }

    @Override
    public void prepareAsync() throws IllegalStateException {
        exoPlayer.prepare(videoSource);
        mEventHandler.post(() -> mOnPreparedListener.onPrepared(this));
    }

    @Override
    public void start() throws IllegalStateException {
        exoPlayer.setPlayWhenReady(true);
    }

    @Override
    public void stop() throws IllegalStateException {
        exoPlayer.stop();
    }

    @Override
    public void pause() throws IllegalStateException {
            exoPlayer.setPlayWhenReady(false);
    }

    @Override
    public boolean isPlaying() {
        return exoPlayer.isPlaying();
    }

    @Override
    public void seekTo(int msec) throws IllegalStateException {
        exoPlayer.seekTo(msec + startTime);
    }

    @Override
    public boolean setStartTime(int msec) {
        startTime = msec;
        exoPlayer.seekTo(startTime);
        return true;
    }

    @Override
    public int getCurrentPosition() {
        return (int) exoPlayer.getCurrentPosition();
    }

    @Override
    public int getDuration() {
        return (int) exoPlayer.getDuration();
    }

    @Override
    public MediaMetadata getMediaMetadata(boolean update_only, boolean apply_filter) {
        return mediaMetadata;
    }

    @Override
    public void release() {
        exoPlayer.removeVideoListener(this);
        exoPlayer.removeListener(this);
        exoPlayer.release();
    }

    @Override
    public void reset() {
        exoPlayer.stop(true);
    }

    @Override
    public void setLooping(boolean looping) {
        if (looping)
            exoPlayer.setRepeatMode(Player.REPEAT_MODE_ALL);
        else
            exoPlayer.setRepeatMode(Player.REPEAT_MODE_OFF);
    }

    @Override
    public boolean isLooping() {
        return exoPlayer.getRepeatMode() == Player.REPEAT_MODE_ALL;
    }

    @Override
    public boolean setAudioTrack(int stream) throws IllegalStateException {
        return setTrack(stream, TRACK_TYPE_AUDIO);
    }

    private boolean setTrack(int stream, int type) throws IllegalStateException {
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo == null)
            return false;
        for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++) {
            if (mappedTrackInfo.getRendererType(i) == type) {
                if (mappedTrackInfo.getTrackSupport(i, stream, 0) == RendererCapabilities.FORMAT_HANDLED) {
                    TrackGroupArray trackGroupArray = mappedTrackInfo.getTrackGroups(i);
                    trackSelector.setParameters(trackSelector
                            .buildUponParameters().setSelectionOverride(i,
                                    trackGroupArray, new DefaultTrackSelector.SelectionOverride(stream, 0)));
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void checkSubtitles() throws IllegalStateException {

    }

    @Override
    public boolean setSubtitleTrack(int stream) throws IllegalStateException {
        return setTrack(stream, TRACK_TYPE_TEXT);
    }

    @Override
    public void setSubtitleDelay(int delay) throws IllegalStateException {

    }

    @Override
    public void setSubtitleRatio(int n, int d) throws IllegalStateException {

    }

    @Override
    public void onCues(List<Cue> cues) {
        mEventHandler.post(() -> {
            if (cues == null || cues.isEmpty())
                mOnSubtitleListener.onSubtitle(this, new Subtitle.TextSubtitle(""));
            else for (Cue cue: cues) {
                if (cue.text != null)
                    mOnSubtitleListener.onSubtitle(this, new Subtitle.TextSubtitle(cue.text.toString()));
            }
        });
    }

    @Override
    public void setAudioFilter(int n, int nightOn) throws IllegalStateException {

    }

    @Override
    public void setAvDelay(int delay) throws IllegalStateException {

    }

    @Override
    public void setNextTrack(String path) throws IllegalStateException {

    }

    @Override
    public int getAudioSessionId() {
        return 0;
    }

    @Override
    public void onSeekProcessed() {
        mEventHandler.post(() -> mOnSeekCompleteListener.onAllSeekComplete(this));
    }

    @Override
    public void onPositionDiscontinuity(@Player.DiscontinuityReason int reason) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT)
            mEventHandler.post(() -> mOnSeekCompleteListener.onSeekComplete(this));
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, @Player.State int playbackState) {
        if (playbackState == Player.STATE_ENDED)
            mEventHandler.post(() -> mOnCompletionListener.onCompletion(this));
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        mEventHandler.post(() -> mOnVideoSizeChangedListener.onVideoSizeChanged(this, width, height));
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        if (trackGroups != lastSeenTrackGroupArray) {
            mediaMetadata = getMetadata();
            mEventHandler.post(() -> mOnInfoListener.onInfo(this, IMediaPlayer.MEDIA_INFO_METADATA_UPDATE, 0));
        }
        lastSeenTrackGroupArray = trackGroups;
    }

    private MetadataDelegate getMetadata() {
        MetadataDelegate metadataDelegate = new MetadataDelegate();
        int videoTracksCount = 0;
        int audioTracksCount = 0;
        int subsTrackCount = 0;

        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo == null)
            return null;
        for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++) {
            TrackGroupArray trackGroupArray = mappedTrackInfo.getTrackGroups(i);
            for (int j = 0; j < trackGroupArray.length; j++) {
                TrackGroup group = trackGroupArray.get(j);
                for (int k = 0; k < group.length; k++) {
                    switch (mappedTrackInfo.getRendererType(i)) {
                        case C.TRACK_TYPE_VIDEO:
                            if (videoTracksCount == 0) {
                                Log.d(TAG, "video track: " +  group.getFormat(k)) ;
                                videoTracksCount++;
                                metadataDelegate.addExoVideo(group.getFormat(k));
                            } else {
                                Log.w(TAG, "Warning: more than 2 video tracks !");
                            }
                            break;
                        case C.TRACK_TYPE_AUDIO:
                            Log.d(TAG, "audio track: " + group.getFormat(k) + "i j k" + i + "," + j + "," + k) ;
                            metadataDelegate.addExoAudio(group.getFormat(k), audioTracksCount);
                            audioTracksCount++;
                            break;
                        case C.TRACK_TYPE_TEXT:
                            Log.d(TAG, "sub track: " + group.getFormat(k) + "i j k" + i + "," + j + "," + k);
                            metadataDelegate.addExoSubs(group.getFormat(k), subsTrackCount);
                            subsTrackCount++;
                            break;
                        default:
                    }
                }
            }
        }
        metadataDelegate.addInt(METADATA_KEY_NB_VIDEO_TRACK, videoTracksCount);
        metadataDelegate.addInt(METADATA_KEY_NB_AUDIO_TRACK, audioTracksCount);
        metadataDelegate.addInt(METADATA_KEY_NB_SUBTITLE_TRACK, subsTrackCount);

        return metadataDelegate;
    }
}
