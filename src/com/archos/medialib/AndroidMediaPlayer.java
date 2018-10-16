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
import android.os.Parcel;

import com.archos.mediacenter.utils.MediaUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

    private static class MetadataDelegate extends MediaMetadata {
        private static class Custom {
            private static class Elm {
                private static final int TYPE_STRING = 1;
                private static final int TYPE_INT = 2;
                private static final int TYPE_BOOLEAN = 3;
                private static final int TYPE_LONG = 4;
                private static final int TYPE_DOUBLE = 5;
                private static final int TYPE_BYTEARRAY = 6;
                private static final int TYPE_DATE = 7;

                private final int type;
                private final Object obj;
                private Elm(int type, Object obj) {
                    this.type = type;
                    this.obj = obj;
                }
            }

            private final HashMap<Integer, Custom.Elm> mHash = new HashMap<Integer, Custom.Elm>();

            void addString(int key, String val) {
                mHash.put(key, new Elm(Elm.TYPE_STRING, val));
            }
            void addInt(int key, int val) {
                mHash.put(key, new Elm(Elm.TYPE_INT, val));
            }
            void addBoolean(int key, boolean val) {
                mHash.put(key, new Elm(Elm.TYPE_BOOLEAN, val));
            }
            void addLong(int key, long val) {
                mHash.put(key, new Elm(Elm.TYPE_LONG, val));
            }
            void addDouble(int key, double val) {
                mHash.put(key, new Elm(Elm.TYPE_DOUBLE, val));
            }
            void addByteArray(int key, byte val[]) {
                mHash.put(key, new Elm(Elm.TYPE_BYTEARRAY, val));
            }
            void addDate(int key, Date val) {
                mHash.put(key, new Elm(Elm.TYPE_DATE, val));
            }
            private Object getElm(int key, int type) {
                Elm elm = mHash.get(key);
                if (elm != null && elm.type == type)
                    return elm.obj;
                return null;
            }
            String getString(int key) {
                return (String) getElm(key, Elm.TYPE_STRING);
            }
            int getInt(int key) {
                return (Integer) getElm(key, Elm.TYPE_INT);
            }
            boolean getBoolean(int key) {
                return (Boolean) getElm(key, Elm.TYPE_BOOLEAN);
            }
            long getLong(int key) {
                return (Long) getElm(key, Elm.TYPE_LONG);
            }
            double getDouble(int key) {
                return (Double) getElm(key, Elm.TYPE_DOUBLE);
            }
            byte[] getByteArray(int key) {
                return (byte[]) getElm(key, Elm.TYPE_BYTEARRAY);
            }
            Date getDate(int key) {
                return (Date) getElm(key, Elm.TYPE_DATE);
            }
            boolean has(int key) {
                return mHash.get(key) != null;
            }
        }

        private final Object mMetadata;
        private final Custom mCustom = new Custom();

        public MetadataDelegate(Object data, MediaPlayer mp, MediaPlayerProxy.Tracks tracks, String[] timedTextLangs) {
            mMetadata = data;
            if (tracks != null) {
                mCustom.addInt(METADATA_KEY_NB_VIDEO_TRACK, tracks.video == null ? 0 : 1);
                mCustom.addInt(METADATA_KEY_NB_AUDIO_TRACK, tracks.audios.length);
                mCustom.addInt(METADATA_KEY_NB_SUBTITLE_TRACK, tracks.timedTexts.length);

                if (tracks.video != null) {
                    int gapKey = METADATA_KEY_VIDEO_TRACK;
                    mCustom.addInt(gapKey + METADATA_KEY_VIDEO_TRACK_WIDTH, mp.getVideoWidth());
                    mCustom.addInt(gapKey + METADATA_KEY_VIDEO_TRACK_HEIGHT, mp.getVideoHeight());
                }

                for (int i = 0; i < tracks.audios.length; ++i) {
                    int gapKey = METADATA_KEY_AUDIO_TRACK + (i * METADATA_KEY_AUDIO_TRACK_MAX);
                    mCustom.addString(gapKey + METADATA_KEY_AUDIO_TRACK_NAME, tracks.audios[i].lang);
                    mCustom.addBoolean(gapKey + METADATA_KEY_AUDIO_TRACK_SUPPORTED, true);
                }
                for (int i = 0; i < tracks.timedTexts.length; ++i) {
                    int gapKey = METADATA_KEY_SUBTITLE_TRACK + (i * METADATA_KEY_SUBTITLE_TRACK_MAX);
                    String lang = null;
                    if (tracks.timedTexts[i].lang.equals("und") && timedTextLangs != null && timedTextLangs.length > i)
                        lang = timedTextLangs[i];
                    else
                        lang = tracks.timedTexts[i].lang;
                    mCustom.addString(gapKey + METADATA_KEY_SUBTITLE_TRACK_NAME, lang);
                }
            }
        }
        @Override
        public boolean equals(Object o) {
            return mMetadata.equals(o);
        }
        @Override
        public int hashCode() {
            return mMetadata.hashCode();
        }
        @Override
        public boolean parse(Parcel parcel) {
            Class paramsType[] = new Class[1];
            paramsType[0] = Parcel.class;
            try {
                return (Boolean) mMetadata.getClass().getMethod("parse", paramsType).invoke(mMetadata, parcel);
            } catch (Exception e) {
                return false;
            }
            //return mMetadata.parse(parcel);
        }
        @Override
        public String toString() {
            return mMetadata.toString();
        }
        @Override
        public Set<Integer> keySet() {
            Class paramsType[] = new Class[0];
            try {
                return (Set<Integer>) mMetadata.getClass().getMethod("keySet", paramsType).invoke(mMetadata);
            } catch (Exception e) {
                return null;
            }
            //return mMetadata.keySet();
        }
        private boolean isCustom(int key) {
            return key >= IMediaPlayer.METADATA_FIRST_CUSTOM;
        }
        @Override
        public boolean has(int metadataId) {
            if (isCustom(metadataId)) {
                return mCustom.has(metadataId);
            } else {
                try {
                    Class paramsType[] = new Class[1];
                    paramsType[0] = int.class;
                    return (Boolean) mMetadata.getClass().getMethod("has", paramsType).invoke(mMetadata, metadataId);
                } catch (Exception e) {
                    return false;
                }
            }
        }
        @Override
        public String getString(int key) {
            if (isCustom(key)) return mCustom.getString(key);
            else {
                try {
                    Class paramsType[] = new Class[1];
                    paramsType[0] = int.class;
                    return (String) mMetadata.getClass().getMethod("getString", paramsType).invoke(mMetadata, key);
                } catch (Exception e) {
                    return null;
                }
                //return mMetadata.getString(key);
            }
        }
        @Override
        public int getInt(int key) {
            if (isCustom(key)) return mCustom.getInt(key);
            else {
                try {
                    Class paramsType[] = new Class[1];
                    paramsType[0] = int.class;
                    return (Integer) mMetadata.getClass().getMethod("getInt", paramsType).invoke(mMetadata, key);
                } catch (Exception e) {
                    return 0;
                }
                //return mMetadata.getInt(key);
            }
        }
        @Override
        public boolean getBoolean(int key) {
            if (isCustom(key)) return mCustom.getBoolean(key);
            else {
                try {
                    Class paramsType[] = new Class[1];
                    paramsType[0] = int.class;
                    return (Boolean) mMetadata.getClass().getMethod("getBoolean", paramsType).invoke(mMetadata, key);
                } catch (Exception e) {
                    return false;
                }//return mMetadata.getBoolean(key);
            }
        }
        @Override
        public long getLong(int key) {
            if (isCustom(key)) return mCustom.getLong(key);
            else {
                try {
                    Class paramsType[] = new Class[1];
                    paramsType[0] = int.class;
                    return (Long) mMetadata.getClass().getMethod("getLong", paramsType).invoke(mMetadata, key);
                } catch (Exception e) {
                    return 0L;
                }
                //return mMetadata.getLong(key);
            }
        }
        @Override
        public double getDouble(int key) {
            if (isCustom(key)) return mCustom.getDouble(key);
            else {
                try {
                    Class paramsType[] = new Class[1];
                    paramsType[0] = int.class;
                    return (Double) mMetadata.getClass().getMethod("getDouble", paramsType).invoke(mMetadata, key);
                } catch (Exception e) {
                    return 0.;
                }
                //return mMetadata.getDouble(key);
            }
        }
        @Override
        public byte[] getByteArray(int key) {
            if (isCustom(key)) return mCustom.getByteArray(key);
            else {
                try {
                    Class paramsType[] = new Class[1];
                    paramsType[0] = int.class;
                    return (byte[]) mMetadata.getClass().getMethod("getByteArray", paramsType).invoke(mMetadata, key);
                } catch (Exception e) {
                    return null;
                }
                //return mMetadata.getByteArray(key);
            }
        }
        @Override
        public Date getDate(int key) {
            if (isCustom(key)) return mCustom.getDate(key);
            else {
                try {
                    Class paramsType[] = new Class[1];
                    paramsType[0] = int.class;
                    return (Date) mMetadata.getClass().getMethod("getDate", paramsType).invoke(mMetadata, key);
                } catch (Exception e) {
                    return null;
                }//return mMetadata.getDate(key);
            }
        }
    }

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
            return new MetadataDelegate(mm, this, mTracks, mTimedTextLangs);
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
