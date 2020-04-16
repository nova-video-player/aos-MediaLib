package com.archos.medialib;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoListener;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Map;

public class ExoMediaPlayer extends GenericMediaPlayer implements Player.EventListener, VideoListener {
    private SimpleExoPlayer exoPlayer;
    private DataSource.Factory dataSourceFactory;
    private MediaSource videoSource;
    private int startTime = 0;

    ExoMediaPlayer(Context context) {
        Looper looper;
        if ((looper = Looper.myLooper()) != null) {
            mEventHandler = new EventHandler(this, looper);
        } else if ((looper = Looper.getMainLooper()) != null) {
            mEventHandler = new EventHandler(this, looper);
        } else {
            mEventHandler = null;
        }
        exoPlayer = new SimpleExoPlayer.Builder(context).setLooper(looper).build();
        exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC);
        exoPlayer.addListener(this);
        exoPlayer.addVideoListener(this);
        dataSourceFactory = new DefaultDataSourceFactory(context,
                Util.getUserAgent(context, "NovaVideoPlayer"));
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
        return new MediaMetadata();
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
        return false;
    }

    @Override
    public void checkSubtitles() throws IllegalStateException {

    }

    @Override
    public boolean setSubtitleTrack(int stream) throws IllegalStateException {
        return false;
    }

    @Override
    public void setSubtitleDelay(int delay) throws IllegalStateException {

    }

    @Override
    public void setSubtitleRatio(int n, int d) throws IllegalStateException {

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
}
