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


import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Map;

public class AvosMediaPlayer implements IMediaPlayer {
    private static final String TAG = "AvosMediaPlayer";
    private static final boolean DBG = false;

    private long mMediaPlayerHandle = 0;     // Read-only, reserved for JNI
    private long mNativeWindowHandle = 0;    // Read-only, reserved for JNI

    private IMediaPlayer.OnPreparedListener mOnPreparedListener = null;
    private IMediaPlayer.OnCompletionListener mOnCompletionListener = null;
    private IMediaPlayer.OnInfoListener mOnInfoListener = null;
    private IMediaPlayer.OnErrorListener mOnErrorListener = null;
    private IMediaPlayer.OnBufferingUpdateListener mOnBufferingUpdateListener = null;
    private IMediaPlayer.OnRelativePositionUpdateListener mOnRelativePositionUpdateListener = null;
    private IMediaPlayer.OnSeekCompleteListener mOnSeekCompleteListener = null;
    private IMediaPlayer.OnVideoSizeChangedListener mOnVideoSizeChangedListener = null;
    private IMediaPlayer.OnNextTrackListener mOnNextTrackListener = null;
    private IMediaPlayer.OnSubtitleListener mOnSubtitleListener = null;
    private EventHandler mEventHandler;
    private PowerManager.WakeLock mWakeLock = null;
    private boolean mScreenOnWhilePlaying;
    private SurfaceHolder mSurfaceHolder;
    private boolean mStayAwake;
    private SmbProxy mSmbProxy = null;

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

    private native void setDataSourceFD(FileDescriptor fd, long offset, long length)
            throws IOException, IllegalArgumentException, IllegalStateException;
    
    public void setDataSource(FileDescriptor fd, long offset, long length)
            throws IOException, IllegalArgumentException, IllegalStateException {
        setDataSourceFD(fd, offset, length);
    }

    public void setDataSource(Context context, Uri uri, Map<String, String> headers) throws IOException,
            IllegalArgumentException, SecurityException, IllegalStateException {

        String scheme = uri.getScheme();
        if (SmbProxy.needToStream(scheme)){
            mSmbProxy = SmbProxy.setDataSource(uri, this, null);
            return;
        }

        if(scheme == null || scheme.equals("file")) {
            String uriString = uri.getPath();
            if (uri.getQuery() != null)
		    uriString += "?"+uri.getQuery();
            setDataSource2(uriString, headers);
            return;
        }

        AssetFileDescriptor fd = null;
        try {
            ContentResolver resolver = context.getContentResolver();
            fd = resolver.openAssetFileDescriptor(uri, "r");
            if (fd == null) {
                return;
            }
            // Note: using getDeclaredLength so that our behavior is the same
            // as previous versions when the content provider is returning
            // a full file.
            if (fd.getDeclaredLength() < 0) {
                setDataSource(fd.getFileDescriptor());
            } else {
                setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getDeclaredLength());
            }
            return;
        } catch (SecurityException ex) {
        } catch (IOException ex) {
        } finally {
            if (fd != null) {
                fd.close();
            }
        }

        Log.d(TAG, "Couldn't open file on client side, trying server side");
        setDataSource2(uri.toString(), headers);
        return;
    }

    public void setDataSource(Context context, Uri uri) throws IOException,
            IllegalArgumentException, SecurityException, IllegalStateException {
        setDataSource(context, uri, null);
    }

    public void setDataSource(String path) throws IOException, IllegalArgumentException,
            SecurityException, IllegalStateException {
         setDataSource2(path, null);
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

    public void setDataSource(FileDescriptor fd) throws IOException, IllegalArgumentException,
            IllegalStateException {
        setDataSource(fd, 0, 0);  
    }

    private void updateSurfaceScreenOn() {
        if (mSurfaceHolder != null) {
            mSurfaceHolder.setKeepScreenOn(mScreenOnWhilePlaying && mStayAwake);
        }
    }

    private void stayAwake(boolean awake) {
        if (mWakeLock != null) {
            if (awake && !mWakeLock.isHeld()) {
                mWakeLock.acquire();
            } else if (!awake && mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }
        mStayAwake = awake;
        updateSurfaceScreenOn();
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

    public void setWakeMode(Context context, int mode) {
        boolean washeld = false;
        if (mWakeLock != null) {
            if (mWakeLock.isHeld()) {
                washeld = true;
                mWakeLock.release();
            }
            mWakeLock = null;
        }

        PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(mode|PowerManager.ON_AFTER_RELEASE, MediaPlayer.class.getName());
        mWakeLock.setReferenceCounted(false);
        if (washeld) {
            mWakeLock.acquire();
        }
    }

    public void setScreenOnWhilePlaying(boolean screenOn) {
        mScreenOnWhilePlaying = screenOn;
    }

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

    public void setAudioStreamType(int streamtype) {
        // TODO Auto-generated method stub
        
    }

    public void setVolume(float leftVolume, float rightVolume) {
        // TODO Auto-generated method stub
        
    }

    public void setOnPreparedListener(IMediaPlayer.OnPreparedListener listener) {
        mOnPreparedListener = listener;
    }

    public void setOnCompletionListener(IMediaPlayer.OnCompletionListener listener) {
        mOnCompletionListener = listener;
    }

    public void setOnInfoListener(IMediaPlayer.OnInfoListener listener) {
        mOnInfoListener = listener;
    }

    public void setOnErrorListener(IMediaPlayer.OnErrorListener listener) {
        mOnErrorListener = listener;
    }

    public void setOnBufferingUpdateListener(IMediaPlayer.OnBufferingUpdateListener listener) {
        mOnBufferingUpdateListener = listener;
    }

    public void setOnRelativePositionUpdateListener(IMediaPlayer.OnRelativePositionUpdateListener listener) {
        mOnRelativePositionUpdateListener = listener;
    }

    public void setOnSeekCompleteListener(IMediaPlayer.OnSeekCompleteListener listener) {
        mOnSeekCompleteListener = listener;
    }

    public void setOnVideoSizeChangedListener(IMediaPlayer.OnVideoSizeChangedListener listener) {
        mOnVideoSizeChangedListener = listener;
    }

    public void setOnNextTrackListener(IMediaPlayer.OnNextTrackListener listener) {
        mOnNextTrackListener = listener;
    }

    public void setOnSubtitleListener(OnSubtitleListener listener) {
        mOnSubtitleListener = listener;
    }

    private native void nativeRelease();
    public void release() {
        stayAwake(false);
        updateSurfaceScreenOn();
        mOnPreparedListener = null;
        mOnCompletionListener = null;
        mOnInfoListener = null;
        mOnErrorListener = null;
        mOnBufferingUpdateListener = null;
        mOnRelativePositionUpdateListener = null;
        mOnSeekCompleteListener = null;
        mOnVideoSizeChangedListener = null;
        mOnNextTrackListener = null;
        mOnSubtitleListener = null;
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

    @Override
    public int doesCurrentFileExists() {
        return mSmbProxy!=null?mSmbProxy.doesCurrentFileExists():-1;
    }

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

    /* Do not change these values without updating their counterparts
     * in include/media/mediaplayer.h!
     */
    private static final int MEDIA_NOP = 0; // interface test message
    private static final int MEDIA_PREPARED = 1;
    private static final int MEDIA_PLAYBACK_COMPLETE = 2;
    private static final int MEDIA_BUFFERING_UPDATE = 3;
    private static final int MEDIA_SEEK_COMPLETE = 4;
    private static final int MEDIA_SET_VIDEO_SIZE = 5;
    private static final int MEDIA_RELATIVE_POSITION_UPDATE = 6;
    private static final int MEDIA_NEXT_TRACK = 7;
    private static final int MEDIA_SET_VIDEO_ASPECT = 8;
    private static final int MEDIA_ERROR = 100;
    private static final int MEDIA_INFO = 200;
    private static final int MEDIA_SUBTITLE = 1000;

    private class EventHandler extends Handler
    {
        private AvosMediaPlayer mMediaPlayer;

        public EventHandler(AvosMediaPlayer mp, Looper looper) {
            super(looper);
            mMediaPlayer = mp;
        }

        @Override
        public void handleMessage(Message msg) {
            if (mMediaPlayerHandle == 0) {
                Log.w(TAG, "mediaplayer went away with unhandled events");
                return;
            }
            switch(msg.what) {
            case MEDIA_PREPARED:
                if (mOnPreparedListener != null)
                    mOnPreparedListener.onPrepared(mMediaPlayer);
                return;

            case MEDIA_PLAYBACK_COMPLETE:
                if (mOnCompletionListener != null)
                    mOnCompletionListener.onCompletion(mMediaPlayer);
                stayAwake(false);
                return;
            case MEDIA_NEXT_TRACK:
                if (mOnNextTrackListener != null)
                    mOnNextTrackListener.onNextTrack(mMediaPlayer);
                return;
            case MEDIA_BUFFERING_UPDATE:
                if (mOnBufferingUpdateListener != null)
                    mOnBufferingUpdateListener.onBufferingUpdate(mMediaPlayer, msg.arg1);
                return;

            case MEDIA_RELATIVE_POSITION_UPDATE:
                if (mOnRelativePositionUpdateListener != null)
                    mOnRelativePositionUpdateListener.onRelativePositionUpdate(mMediaPlayer, msg.arg1);
                return;
            case MEDIA_SEEK_COMPLETE:
              if (mOnSeekCompleteListener != null) {
                  mOnSeekCompleteListener.onSeekComplete(mMediaPlayer);
                  if (msg.arg1 == 0)
                      mOnSeekCompleteListener.onAllSeekComplete(mMediaPlayer);
              }
              return;

            case MEDIA_SET_VIDEO_SIZE:
              if (mOnVideoSizeChangedListener != null)
                  mOnVideoSizeChangedListener.onVideoSizeChanged(mMediaPlayer, msg.arg1, msg.arg2);
              return;

            case MEDIA_SET_VIDEO_ASPECT:
                if (mOnVideoSizeChangedListener != null)
                    mOnVideoSizeChangedListener.onVideoAspectChanged(mMediaPlayer, (double)msg.arg1 / (double) msg.arg2);
                return;

            case MEDIA_ERROR:
                // For PV specific error values (msg.arg2) look in
                // opencore/pvmi/pvmf/include/pvmf_return_codes.h
                Log.e(TAG, "Error (" + msg.arg1 + "," + msg.arg2 + ")");
                boolean error_was_handled = false;
                if (mOnErrorListener != null) {
                    error_was_handled = mOnErrorListener.onError(mMediaPlayer, msg.arg1, msg.arg2,
                            msg.obj != null && msg.obj instanceof String ? (String)msg.obj : null);
                }
                if (mOnCompletionListener != null && ! error_was_handled) {
                    mOnCompletionListener.onCompletion(mMediaPlayer);
                }
                stayAwake(false);
                return;

            case MEDIA_INFO:
                if (msg.arg1 != MEDIA_INFO_VIDEO_TRACK_LAGGING) {
                    Log.i(TAG, "Info (" + msg.arg1 + "," + msg.arg2 + ")");
                }
                if (mOnInfoListener != null) {
                    mOnInfoListener.onInfo(mMediaPlayer, msg.arg1, msg.arg2);
                }
                // No real default action so far.
                return;
            case MEDIA_SUBTITLE:
                if (mOnSubtitleListener != null) {
                    if (msg.obj == null) {
                        Log.e(TAG, "MEDIA_SUBTITLE with null object");
                        return;
                    }
                    if (msg.obj instanceof Subtitle)
                        mOnSubtitleListener.onSubtitle(mMediaPlayer, (Subtitle)msg.obj);
                    else
                        Log.e(TAG, "MEDIA_SUBTITLE with wrong object");
                }
                return;
            case MEDIA_NOP: // interface test message - ignore
                break;

            default:
                Log.e(TAG, "Unknown message type " + msg.what);
                return;
            }
        }
    }
}
