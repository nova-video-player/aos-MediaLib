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

import android.net.Uri;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Map;

public class AvosMediaPlayer extends GenericMediaPlayer {
    private static final String TAG = "AvosMediaPlayer";
    private static final boolean DBG = false;

    private native void create(Object weakReference);

    public AvosMediaPlayer() {
        Log.v(TAG, "Initializing AvosMediaPlayer");
        Looper looper;
        if ((looper = Looper.myLooper()) != null) {
            mEventHandler = new EventHandler(this, looper);
        } else if ((looper = Looper.getMainLooper()) != null) {
            mEventHandler = new EventHandler(this, looper);
        } else {
            mEventHandler = null;
        }
        create(new WeakReference<AvosMediaPlayer>(this));
    }

    protected void finalize() throws Throwable {
        release();
    }

    public int getType() {
        return IMediaPlayer.TYPE_AVOS;
    }

    public native void setDataSource(String path, String[] keys, String[] values)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException;

    public native void setDataSourceFD(FileDescriptor fd, long offset, long length)
            throws IOException, IllegalArgumentException, IllegalStateException;
    
    public void setDataSource(FileDescriptor fd, long offset, long length)
            throws IOException, IllegalArgumentException, IllegalStateException {
        setDataSourceFD(fd, offset, length);
    }

    public void setDataSource2(String path, Map<String, String> headers) throws IOException,
            IllegalArgumentException, SecurityException, IllegalStateException {
        if (SmbProxy.needToStream(Uri.parse(path).getScheme())){
            mSmbProxy = SmbProxy.setDataSource(Uri.parse(path), this, headers);
            return;
        }
        String[] keys = null;
        String[] values = null;

        if (headers != null) {
            keys = new String[headers.size()];
            values = new String[headers.size()];

            int i = 0;
            for (Map.Entry<String, String> entry: headers.entrySet()) {
                keys[i] = entry.getKey();
                values[i] = entry.getValue();
                ++i;
            }
        }

        setDataSource(path, keys, values);
    }

    private native void setVideoSurface(Surface surface);

    public void setDisplay(SurfaceHolder sh) {
        mSurfaceHolder = sh;
        Surface surface;
        if (sh != null) {
            surface = sh.getSurface();
        } else {
            surface = null;
        }
        setVideoSurface(surface);
        updateSurfaceScreenOn();
    }

    public void setSurface(Surface surface) {
        if (mScreenOnWhilePlaying && surface != null) {
            Log.w(TAG, "setScreenOnWhilePlaying(true) is ineffective for Surface");
        }
        mSurfaceHolder = null;
        setVideoSurface(surface);
    }

    public void reset() {
        stayAwake(false);
        nativeReset();
        // make sure none of the listeners get called anymore
        mEventHandler.removeCallbacksAndMessages(null);
    }

    private native void nativeReset();

    private native final byte[] getMetadata();

    public MediaMetadata getMediaMetadata(boolean update_only, boolean apply_filter) {
        AvosMediaMetadata data = new AvosMediaMetadata();

        byte[] bytes = getMetadata();
        if (bytes == null)
            return null;

        if (!data.parse(bytes))
            return null;

        return data;
    }

    private native void nativeRelease();

    public void release() {
        stayAwake(false);
        updateSurfaceScreenOn();
        setOnPreparedListener(null);
        setOnCompletionListener(null);
        setOnInfoListener(null);
        setOnErrorListener(null);
        setOnBufferingUpdateListener(null);
        setOnRelativePositionUpdateListener(null);
        setOnSeekCompleteListener(null);
        setOnVideoSizeChangedListener(null);
        setOnNextTrackListener(null);
        setOnSubtitleListener(null);
        nativeRelease();
        if (mSmbProxy != null) {
            mSmbProxy.stop();
            mSmbProxy = null;
        }
    }

    public native void prepareAsync() throws IllegalStateException;

    public native void prepare() throws IOException, IllegalStateException;
    
    private native void nativeStart() throws IllegalStateException;
    public void start() throws IllegalStateException {
        stayAwake(true);
        nativeStart();
    }

    private native void nativeStop() throws IllegalStateException;
    public void stop() throws IllegalStateException {
        stayAwake(false);
        nativeStop();
    }

    private native void nativePause() throws IllegalStateException;
    public void pause() throws IllegalStateException {
        stayAwake(false);
        nativePause();
    }

    public native int getDuration();

    public native int getCurrentPosition();

    public native int getBufferPosition();

    public native int getRelativePosition();

    public native void seekTo(int pos);

    public boolean setStartTime(int pos) {
        nativeSetStartTime(pos);
        return true;
    }

    private native void nativeSetStartTime(int pos);

    public native boolean isPlaying();

    public native void setLooping(boolean enable);

    public native boolean isLooping();

    public native int getAudioSessionId();

    public native void checkSubtitles();

    public native boolean setSubtitleTrack(int stream);

    public native void setSubtitleDelay(int delay);

    public native void setSubtitleRatio(int n, int d);

    public native boolean setAudioTrack(int stream);

    public native void setAudioFilter(int n, int night_on);

    public native void setAvDelay(int delay);

    public native void setNextTrack(String path);

    /**
     * Called from native code when an interesting event happens.  This method
     * just uses the EventHandler system to post the event back to the main app thread.
     * We use a weak reference to the original MediaPlayer object so that the native
     * code is safe from the object disappearing from underneath it.  (This is
     * the cookie passed to native_setup().)
     */
    private static void postEventFromNative(Object mediaplayer_ref,
                                            int what, int arg1, int arg2, Object obj)
    {
        AvosMediaPlayer mp = (AvosMediaPlayer)((WeakReference)mediaplayer_ref).get();
        if (mp == null) {
            return;
        }

        if (mp.mEventHandler != null) {
            Message m = mp.mEventHandler.obtainMessage(what, arg1, arg2, obj);
            mp.mEventHandler.sendMessage(m);
        }
    }

}
