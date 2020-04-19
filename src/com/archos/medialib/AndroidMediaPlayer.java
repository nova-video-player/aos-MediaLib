// Copyright 2017 Archos SA
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.archos.medialib;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;

import com.archos.mediacenter.utils.MediaUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;

/**
 * Proxy class between android MediaPlayer class and
 * IMediaPlayer interface
 */
public class AndroidMediaPlayer extends MediaPlayer implements IMediaPlayer,
                                    MediaPlayer.OnPreparedListener,
                                    MediaPlayer.OnCompletionListener,
                                    MediaPlayer.OnBufferingUpdateListener,
                                    MediaPlayer.OnSeekCompleteListener,
                                    MediaPlayer.OnVideoSizeChangedListener,
                                    MediaPlayer.OnErrorListener,
                                    MediaPlayer.OnInfoListener,
                                    MediaPlayerProxy.OnTimedTextListener {
    private static final String TAG = "AndroidMediaPlayer";

    private IMediaPlayer.OnPreparedListener mOnPreparedListener = null;
    private IMediaPlayer.OnCompletionListener mOnCompletionListener = null;
    private IMediaPlayer.OnInfoListener mOnInfoListener = null;
    private IMediaPlayer.OnErrorListener mOnErrorListener = null;
    private IMediaPlayer.OnBufferingUpdateListener mOnBufferingUpdateListener = null;
    private IMediaPlayer.OnSeekCompleteListener mOnSeekCompleteListener = null;
    private IMediaPlayer.OnVideoSizeChangedListener mOnVideoSizeChangedListener = null;
    private IMediaPlayer.OnSubtitleListener mOnSubtitleListener = null;

    private final Context mContext;
    private String mVideoPath;

    private SmbProxy mProxy = null;

    private String[] mTimedTextLangs;
    private MediaPlayerProxy.Tracks mTracks = null;
    private MediaPlayerProxy.Track mLastAudioSelected = null;
    private MediaPlayerProxy.Track mLastSubSelected = null;

    public AndroidMediaPlayer(Context context) {
        super();
        mContext = context;
    }

    public int getType() {
        return IMediaPlayer.TYPE_ANDROID;
    }

    private void addAllTimedTextsInDir(File dir, String videoPath, ArrayList<String> langList) {
        if (dir == null || !dir.isDirectory() || !dir.canRead() || !dir.canExecute())
            return;
        int i = videoPath.lastIndexOf('.');
        if (i > 0) {
            final String videoCmp = videoPath.substring(0, i);

            FileFilter fileFilter = new FileFilter() {
                @Override
                public boolean accept(File file) {
                    String path = file.getName();
                    int i = path.lastIndexOf(".srt");
                    if (i > 0) {
                        path = path.substring(0, i);

                        if (videoCmp.equals(path))
                            return true;
                        i = path.lastIndexOf('.');
                        if (i > 0) {
                            path = path.substring(0, i);
                            if (videoCmp.equals(path))
                                return true;
                        }

                        return false;
                    }
                    return false;
                }
            };
            for (File file : dir.listFiles(fileFilter)) {
                MediaPlayerProxy.addTimedTextSource(this, file.getAbsolutePath());
                String path = file.getName();
                String lang = "SRT";
                int endIdx = path.lastIndexOf('.');
                if (endIdx > 0) {
                    int startIdx = path.lastIndexOf('.', endIdx - 1);
                    if (startIdx > 0)
                        lang = path.substring(startIdx + 1, endIdx);
                }
                langList.add(lang);
            }
        }
    }

    private void addAllTimedTexts() {
        File videoFile;
        try {
            videoFile = new File(mVideoPath);
        } catch (NullPointerException e) {
            videoFile = null;
        }
        if (videoFile != null) {
            ArrayList<String> timedTextLangList = new ArrayList<String>();
            String videoPath = videoFile.getName();

            addAllTimedTextsInDir(videoFile.getParentFile(), videoPath, timedTextLangList);
            addAllTimedTextsInDir(MediaUtils.getSubsDir(mContext), videoPath, timedTextLangList);
            mTimedTextLangs = timedTextLangList.toArray(new String[timedTextLangList.size()]);
        }
    }

    public MediaMetadata getMediaMetadata(boolean update_only, boolean apply_filter) {
        Class paramsType[] = new Class[2];
        paramsType[0] = boolean.class;
        paramsType[1] = boolean.class;
        Method getMetadata = null;
        Object mm = null;
        try {
            getMetadata = super.getClass().getMethod("getMetadata", paramsType);
        } catch (NoSuchMethodException e) {
        }
        if (getMetadata != null)
            try {
                mm = getMetadata.invoke(this, update_only, apply_filter);
            } catch (IllegalAccessException e) {
            } catch (InvocationTargetException e) {
            }
        if (mm != null) {
            if (MediaPlayerProxy.isValid())
                mTracks = MediaPlayerProxy.getTracks(this);
            return new MetadataDelegate(mm, getVideoWidth(), getVideoHeight(), mTracks, mTimedTextLangs);
        }
        return null;
    }

    @Override
    public void setDataSource(Context context, Uri uri, Map<String, String> headers)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException {
        String scheme = uri.getScheme();
        if (Proxy.needToStream(scheme)){
            mProxy = SmbProxy.setDataSource(uri, this, headers);
            return;
        }
        super.setDataSource(context, uri, headers);
    }

    @Override
    public void setDataSource(Context context, Uri uri) throws IOException,
            IllegalArgumentException, SecurityException, IllegalStateException {
        String scheme = uri.getScheme();
        if (SmbProxy.needToStream(scheme)){
            mProxy = SmbProxy.setDataSource(uri, this, null);
            return;
        }
        super.setDataSource(context, uri);
    }

    @Override
    public void setDataSource(String path) throws IOException, IllegalArgumentException,
            SecurityException, IllegalStateException {
        if (SmbProxy.needToStream(Uri.parse(path).getScheme())){
            mProxy = SmbProxy.setDataSource(Uri.parse(path), this, null);
            return;
        }
        mVideoPath = path;
        super.setDataSource(path);
    }

    @Override
    public void setDataSource2(String path, Map<String, String> headers) throws IOException,
            IllegalArgumentException, SecurityException, IllegalStateException {
        if (SmbProxy.needToStream(Uri.parse(path).getScheme())){
            mProxy = SmbProxy.setDataSource(Uri.parse(path), this, headers);
            return;
        }
        mVideoPath = path;
        Class paramsType[] = new Class[2];
        paramsType[0] = String.class;
        paramsType[1] = Map.class;
        Method setDataSourceFromSuper = null;
        try {
            setDataSourceFromSuper = MediaPlayer.class.getMethod("setDataSource", paramsType);
        } catch (NoSuchMethodException e) {
        }
        if (setDataSourceFromSuper != null) {
            try {
                setDataSourceFromSuper.invoke(this, path, headers);
            } catch (IllegalAccessException e) {
            } catch (InvocationTargetException e) {
            }
        }
    }

    @Override
    public void release() {
        // TODO Auto-generated method stub
        super.release();
        if (mProxy != null) {
            mProxy.stop();
            mProxy = null;
        }
    }

    public void setOnPreparedListener(IMediaPlayer.OnPreparedListener listener) {
        setOnPreparedListener(listener == null ? null : this);
        mOnPreparedListener = listener;
    }
    public void onPrepared(MediaPlayer mp) {
        addAllTimedTexts();
        mOnPreparedListener.onPrepared(this);
    }

    public void setOnCompletionListener(IMediaPlayer.OnCompletionListener listener) {
        setOnCompletionListener(listener == null ? null : this);
        mOnCompletionListener = listener;
    }
    public void onCompletion(MediaPlayer mp) {
        mOnCompletionListener.onCompletion(this);
    }

    public void setOnInfoListener(IMediaPlayer.OnInfoListener listener) {
        setOnInfoListener(listener == null ? null : this);
        mOnInfoListener = listener;
    }
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        return mOnInfoListener.onInfo(this, what, extra);
    }

    public void setOnErrorListener(IMediaPlayer.OnErrorListener listener) {
        setOnErrorListener(listener == null ? null : this);
        mOnErrorListener = listener;
    }
    public boolean onError(MediaPlayer mp, int what, int extra) {
        return mOnErrorListener.onError(this, what, extra, null);
    }

    public void setOnBufferingUpdateListener(IMediaPlayer.OnBufferingUpdateListener listener) {
        setOnBufferingUpdateListener(listener == null ? null : this);
        mOnBufferingUpdateListener = listener;
    }
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        mOnBufferingUpdateListener.onBufferingUpdate(this, percent);
    }

    public void setOnRelativePositionUpdateListener(IMediaPlayer.OnRelativePositionUpdateListener listener) {
    }

    public void setOnSeekCompleteListener(IMediaPlayer.OnSeekCompleteListener listener) {
        setOnSeekCompleteListener(listener == null ? null : this);
        mOnSeekCompleteListener = listener;
    }
    public void onSeekComplete(MediaPlayer mp) {
        mOnSeekCompleteListener.onSeekComplete(this);
        mOnSeekCompleteListener.onAllSeekComplete(this);
    }

    public void setOnVideoSizeChangedListener(IMediaPlayer.OnVideoSizeChangedListener listener) {
        setOnVideoSizeChangedListener(listener == null ? null : this);
        mOnVideoSizeChangedListener = listener;
    }

    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
        mOnVideoSizeChangedListener.onVideoSizeChanged(this, width, height);
    }

    public void setOnNextTrackListener(IMediaPlayer.OnNextTrackListener listener) {
    }

    @Override
    public int doesCurrentFileExists() {
        return mProxy!=null?mProxy.doesCurrentFileExists():-1;
    }

    public void setOnSubtitleListener(OnSubtitleListener listener) {
        MediaPlayerProxy.setOnTimedTextListener(this, this);
        mOnSubtitleListener = listener;
    }

    public boolean setAudioTrack(int stream) throws IllegalStateException {
        if (mTracks != null) {
            if (stream >= 0 && stream < mTracks.audios.length) {
                mLastAudioSelected = mTracks.audios[stream];
                return MediaPlayerProxy.selectTrack(this, mLastAudioSelected);
            } else if (mLastAudioSelected != null) {
                MediaPlayerProxy.deselectTrack(this, mLastAudioSelected);
                mLastAudioSelected = null;
                return true;
            }
        }
        return false;
    }

    public void checkSubtitles() {
    }

    public boolean setSubtitleTrack(int stream) throws IllegalStateException {
        if (mTracks != null) {
            if (stream >= 0 && stream < mTracks.timedTexts.length) {
                mLastSubSelected = mTracks.timedTexts[stream];
                return MediaPlayerProxy.selectTrack(this, mLastSubSelected);
            } else if (mLastSubSelected != null) {
                MediaPlayerProxy.deselectTrack(this, mLastSubSelected);
                mLastSubSelected = null;
                return true;
            }
        }
        return false;
    }

    public void setSubtitleDelay(int delay) throws IllegalStateException {   
    }

    public void setSubtitleRatio(int n, int d) throws IllegalStateException {
    }

    public void setAudioFilter(int n, int night_on) throws IllegalStateException {
    }

    public void setAvDelay(int delay) throws IllegalStateException {
    }

    public void setNextTrack(String path) throws IllegalStateException {
    }

    public boolean setStartTime(int msec) {
        return false;
    }

    @Override
    public void onTimedText(MediaPlayer mp, String text) {
        if (mOnSubtitleListener != null)
            mOnSubtitleListener.onSubtitle(this, new Subtitle.TextSubtitle(text));
    }
}
