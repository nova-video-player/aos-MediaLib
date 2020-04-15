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
import android.view.SurfaceHolder;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Map;

abstract class GenericMediaPlayer implements IMediaPlayer {
    private static final String TAG = "GenericMediaPlayer";

    private long mMediaPlayerHandle = 0;     // Read-only, reserved for JNI
    private long mNativeWindowHandle = 0;    // Read-only, reserved for JNI

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

    protected OnPreparedListener mOnPreparedListener = null;
    protected OnCompletionListener mOnCompletionListener = null;
    protected OnInfoListener mOnInfoListener = null;
    protected OnErrorListener mOnErrorListener = null;
    protected OnBufferingUpdateListener mOnBufferingUpdateListener = null;
    protected OnRelativePositionUpdateListener mOnRelativePositionUpdateListener = null;
    protected OnSeekCompleteListener mOnSeekCompleteListener = null;
    protected OnVideoSizeChangedListener mOnVideoSizeChangedListener = null;
    protected OnNextTrackListener mOnNextTrackListener = null;
    protected OnSubtitleListener mOnSubtitleListener = null;
    protected EventHandler mEventHandler;
    protected PowerManager.WakeLock mWakeLock = null;
    protected boolean mScreenOnWhilePlaying;
    protected SurfaceHolder mSurfaceHolder;
    protected SmbProxy mSmbProxy = null;


    private boolean mStayAwake;

    protected void updateSurfaceScreenOn() {
        if (mSurfaceHolder != null) {
            mSurfaceHolder.setKeepScreenOn(mScreenOnWhilePlaying && mStayAwake);
        }
    }

    protected void stayAwake(boolean awake) {
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

    public void setAudioStreamType(int streamtype) {
        // TODO Auto-generated method stub

    }

    public void setVolume(float leftVolume, float rightVolume) {
        // TODO Auto-generated method stub

    }

    public void setOnPreparedListener(OnPreparedListener listener) {
        mOnPreparedListener = listener;
    }

    public void setOnCompletionListener(OnCompletionListener listener) {
        mOnCompletionListener = listener;
    }

    public void setOnInfoListener(OnInfoListener listener) {
        mOnInfoListener = listener;
    }

    public void setOnErrorListener(OnErrorListener listener) {
        mOnErrorListener = listener;
    }

    public void setOnBufferingUpdateListener(OnBufferingUpdateListener listener) {
        mOnBufferingUpdateListener = listener;
    }

    public void setOnRelativePositionUpdateListener(OnRelativePositionUpdateListener listener) {
        mOnRelativePositionUpdateListener = listener;
    }

    public void setOnSeekCompleteListener(OnSeekCompleteListener listener) {
        mOnSeekCompleteListener = listener;
    }

    public void setOnVideoSizeChangedListener(OnVideoSizeChangedListener listener) {
        mOnVideoSizeChangedListener = listener;
    }

    public void setOnNextTrackListener(OnNextTrackListener listener) {
        mOnNextTrackListener = listener;
    }

    public void setOnSubtitleListener(OnSubtitleListener listener) {
        mOnSubtitleListener = listener;
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

    public void setDataSource(FileDescriptor fd) throws IOException, IllegalArgumentException,
            IllegalStateException {
        setDataSource(fd, 0, 0);
    }

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

    @Override
    public int doesCurrentFileExists() {
        return mSmbProxy!=null?mSmbProxy.doesCurrentFileExists():-1;
    }

    protected class EventHandler extends Handler
    {
        private GenericMediaPlayer mMediaPlayer;

        public EventHandler(GenericMediaPlayer mp, Looper looper) {
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
