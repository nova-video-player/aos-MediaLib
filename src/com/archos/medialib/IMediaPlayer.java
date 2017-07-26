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
import android.view.Surface;
import android.view.SurfaceHolder;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Map;

public interface IMediaPlayer {

    public static final boolean METADATA_UPDATE_ONLY = true;

    public static final boolean METADATA_ALL = false;

    public static final boolean APPLY_METADATA_FILTER = true;

    public static final boolean BYPASS_METADATA_FILTER = false;
    
    public static final int TYPE_AVOS = 0;
    public static final int TYPE_ANDROID = 1;
    public int getType();

    public void setDisplay(SurfaceHolder sh);

    public void setSurface(Surface surface);

    public void setDataSource(Context context, Uri uri) throws IOException, IllegalArgumentException,
            SecurityException, IllegalStateException; 

    public void setDataSource(Context context, Uri uri, Map<String, String> headers)throws IOException, IllegalArgumentException,
            SecurityException, IllegalStateException; 

    public void setDataSource(String path) throws IOException, IllegalArgumentException,
            SecurityException, IllegalStateException;
    /** to avoid recursive call witch android mediaplayer **/
    public void setDataSource2(String path, Map<String, String> headers) throws IOException,
            IllegalArgumentException, SecurityException, IllegalStateException;

    public void setDataSource(FileDescriptor fd) throws IOException, IllegalArgumentException,
            IllegalStateException;

    public void setDataSource(FileDescriptor fd, long offset, long length) throws IOException,
            IllegalArgumentException, IllegalStateException;

    public void prepare() throws IOException, IllegalStateException;

    public void prepareAsync() throws IllegalStateException;

    public void start() throws IllegalStateException;

    public void stop() throws IllegalStateException;

    public void pause() throws IllegalStateException;

    public void setWakeMode(Context context, int mode);

    public void setScreenOnWhilePlaying(boolean screenOn);

    public boolean isPlaying();

    public void seekTo(int msec) throws IllegalStateException;

    public boolean setStartTime(int msec);

    public int getCurrentPosition();

    public int getDuration();

    public MediaMetadata getMediaMetadata(final boolean update_only, final boolean apply_filter);

    public void release();

    public void reset();

    public void setAudioStreamType(int streamtype);

    public void setLooping(boolean looping);

    public boolean isLooping();

    public void setVolume(float leftVolume, float rightVolume);

    public boolean setAudioTrack(int stream) throws IllegalStateException;

    public void checkSubtitles() throws IllegalStateException;

    public boolean setSubtitleTrack(int stream) throws IllegalStateException;

    public void setSubtitleDelay(int delay) throws IllegalStateException;

    public void setSubtitleRatio(int n, int d) throws IllegalStateException;

    public void setAudioFilter(int n, int nightOn) throws IllegalStateException;

    public void setAvDelay(int delay) throws IllegalStateException;

    public void setNextTrack(String path) throws IllegalStateException;

    public void setOnPreparedListener(OnPreparedListener listener);
    public void setOnCompletionListener(OnCompletionListener listener);
    public void setOnInfoListener(OnInfoListener listener);
    public void setOnErrorListener(OnErrorListener listener);
    public void setOnBufferingUpdateListener(OnBufferingUpdateListener listener);
    public void setOnRelativePositionUpdateListener(OnRelativePositionUpdateListener listener);
    public void setOnSeekCompleteListener(OnSeekCompleteListener listener);
    public void setOnVideoSizeChangedListener(OnVideoSizeChangedListener listener);
    public void setOnSubtitleListener(OnSubtitleListener listener);
    public void setOnNextTrackListener(OnNextTrackListener listener);
    public int  getAudioSessionId();
    /* returns 0 if it doesn't, 1 if it does, -1 if we don't know it yet */
    int doesCurrentFileExists();


    public interface OnPreparedListener {
        public void onPrepared(IMediaPlayer mp);
    }

    public interface OnCompletionListener {
        public void onCompletion(IMediaPlayer mp);
    }

    public interface OnInfoListener {
        public boolean onInfo(IMediaPlayer mp, int what, int extra);
    }

    public interface OnErrorListener {
        public boolean onError(IMediaPlayer mp, int errorCode, int errorQualCode, String msg);
    }

    public interface OnBufferingUpdateListener {
        public void onBufferingUpdate(IMediaPlayer mp, int percent);
    }

    public interface OnRelativePositionUpdateListener {
        public void onRelativePositionUpdate(IMediaPlayer mp, int permil);
    }

    public interface OnSeekCompleteListener {
        public void onSeekComplete(IMediaPlayer mp);
        public void onAllSeekComplete(IMediaPlayer mp);
    }

    public interface OnVideoSizeChangedListener {
        public void onVideoSizeChanged(IMediaPlayer mp, int width, int height);
        public void onVideoAspectChanged(IMediaPlayer mp, double aspect);
    }

    public interface OnSubtitleListener  {
        public void onSubtitle(IMediaPlayer mp, Subtitle subtitle);
    }

    public interface OnNextTrackListener {
        public void onNextTrack(IMediaPlayer mp);
    }

    // media error

    public static final int MEDIA_ERROR_UNKNOWN = MediaPlayer.MEDIA_ERROR_UNKNOWN;

    public static final int MEDIA_ERROR_SERVER_DIED = MediaPlayer.MEDIA_ERROR_SERVER_DIED;

    public static final int MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK = MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK;

    // media info

    public static final int MEDIA_INFO_UNKNOWN = MediaPlayer.MEDIA_INFO_UNKNOWN;

    public static final int MEDIA_INFO_VIDEO_TRACK_LAGGING = MediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING;

    public static final int MEDIA_INFO_BUFFERING_START = MediaPlayer.MEDIA_INFO_BUFFERING_START;

    public static final int MEDIA_INFO_BUFFERING_END = MediaPlayer.MEDIA_INFO_BUFFERING_END;

    public static final int MEDIA_INFO_BAD_INTERLEAVING = MediaPlayer.MEDIA_INFO_BAD_INTERLEAVING;

    public static final int MEDIA_INFO_NOT_SEEKABLE = MediaPlayer.MEDIA_INFO_NOT_SEEKABLE;

    public static final int MEDIA_INFO_METADATA_UPDATE = MediaPlayer.MEDIA_INFO_METADATA_UPDATE;

    // avos specific error

    public static final int MEDIA_ERROR_VE_NO_ERROR = 1000;

    public static final int MEDIA_ERROR_VE_FILE_ERROR = 1001;

    public static final int MEDIA_ERROR_VE_ERROR = 1002;

    public static final int MEDIA_ERROR_VE_CONNECTION_ERROR = 1003;

    public static final int MEDIA_ERROR_VE_USER_ABORT = 1004;

    public static final int MEDIA_ERROR_VE_VIDEO_NOT_SUPPORTED = 1005;

    public static final int MEDIA_ERROR_VE_AUDIO_NOT_SUPPORTED = 1006;

    public static final int MEDIA_ERROR_VE_VIDEO_NOT_ALLOWED = 1007;

    public static final int MEDIA_ERROR_VE_AUDIO_NOT_ALLOWED = 1008;

    public static final int MEDIA_ERROR_VE_TOO_BIG_FOR_STREAM = 1009;

    public static final int MEDIA_ERROR_VE_TOO_BIG_FOR_CODEC = 1010;

    public static final int MEDIA_ERROR_VE_NOT_INTERLEAVED = 1011;

    public static final int MEDIA_ERROR_VE_CRYPTED = 1012;

    public static final int MEDIA_ERROR_VE_MAX = 1012;

    // avos specific error qualifier

    public static final int MEDIA_ERROR_VEQ_NONE = 1000;

    public static final int MEDIA_ERROR_VEQ_MPG4_UNSUPPORTED = 1001;

    public static final int MEDIA_ERROR_VEQ_PROFILE_AND_LEVEL_UNSUPPORTED = 1002;

    public static final int MEDIA_ERROR_VEQ_AUDIO_PROFILE_AND_LEVEL_UNSUPPORTED = 1003;

    public static final int MEDIA_ERROR_VEQ_INTERLACED_NOT_SUPPORTED = 1004;

    public static final int MEDIA_ERROR_VEQ_SEE_DESCRIPTION = 1005;

    public static final int MEDIA_ERROR_VEQ_MAX = 1005;

    // Metadatas
    
    // Playback capabilities.

    /**
     * Indicate whether the media can be paused
     */
    public static final int METADATA_KEY_PAUSE_AVAILABLE         = 1; // Boolean
    /**
     * Indicate whether the media can be backward seeked
     */
    public static final int METADATA_KEY_SEEK_BACKWARD_AVAILABLE = 2; // Boolean
    /**
     * Indicate whether the media can be forward seeked
     */
    public static final int METADATA_KEY_SEEK_FORWARD_AVAILABLE  = 3; // Boolean
    /**
     * Indicate whether the media can be seeked
     */
    public static final int METADATA_KEY_SEEK_AVAILABLE          = 4; // Boolean

    // TODO: Should we use numbers compatible with the metadata retriever?

    public static final int METADATA_KEY_TITLE                   = 5; // String

    public static final int METADATA_KEY_COMMENT                 = 6; // String

    public static final int METADATA_KEY_COPYRIGHT               = 7; // String

    public static final int METADATA_KEY_ALBUM                   = 8; // String

    public static final int METADATA_KEY_ARTIST                  = 9; // String

    public static final int METADATA_KEY_AUTHOR                  = 10; // String

    public static final int METADATA_KEY_COMPOSER                = 11; // String

    public static final int METADATA_KEY_GENRE                   = 12; // String

    public static final int METADATA_KEY_DATE                    = 13; // Date

    public static final int METADATA_KEY_DURATION                = 14; // Integer(millisec)

    public static final int METADATA_KEY_CD_TRACK_NUM            = 15; // Integer 1-based

    public static final int METADATA_KEY_CD_TRACK_MAX            = 16; // Integer

    public static final int METADATA_KEY_RATING                  = 17; // String
 
    public static final int METADATA_KEY_ALBUM_ART               = 18; // byte[]

    public static final int METADATA_KEY_VIDEO_FRAME             = 19; // Bitmap

    public static final int METADATA_KEY_BIT_RATE                = 20; // Integer, Aggregate rate of
                                                          // all the streams in bps.

    public static final int METADATA_KEY_AUDIO_BIT_RATE          = 21; // Integer, bps

    public static final int METADATA_KEY_VIDEO_BIT_RATE          = 22; // Integer, bps

    public static final int METADATA_KEY_AUDIO_SAMPLE_RATE       = 23; // Integer, Hz
 
    public static final int METADATA_KEY_VIDEO_FRAME_RATE        = 24; // Integer, Hz

    // See RFC2046 and RFC4281.

    public static final int METADATA_KEY_MIME_TYPE               = 25; // String

    public static final int METADATA_KEY_AUDIO_CODEC             = 26; // String

    public static final int METADATA_KEY_VIDEO_CODEC             = 27; // String

    public static final int METADATA_KEY_VIDEO_HEIGHT            = 28; // Integer

    public static final int METADATA_KEY_VIDEO_WIDTH             = 29; // Integer

    public static final int METADATA_KEY_NUM_TRACKS              = 30; // Integer

    public static final int METADATA_KEY_DRM_CRIPPLED            = 31; // Boolean


    public static final int METADATA_ANY = 0;
    public static final int METADATA_FIRST_CUSTOM = 8192;

    // avos specific
    public static final int METADATA_KEY_FILE_SIZE               = 8200; // Integer64
    public static final int METADATA_KEY_CURRENT_AUDIO_TRACK     = 8201; // Integer
    public static final int METADATA_KEY_CURRENT_SUBTITLE_TRACK  = 8202; // Integer

    public static final int METADATA_KEY_NB_VIDEO_TRACK = 9000;
    public static final int METADATA_KEY_NB_AUDIO_TRACK = 9001;
    public static final int METADATA_KEY_NB_SUBTITLE_TRACK = 9002;

    public static final int METADATA_KEY_VIDEO_TRACK = 10000;
    public static final int METADATA_KEY_VIDEO_TRACK_FORMAT = 0;
    public static final int METADATA_KEY_VIDEO_TRACK_WIDTH = 1;
    public static final int METADATA_KEY_VIDEO_TRACK_HEIGHT = 2;
    public static final int METADATA_KEY_VIDEO_TRACK_ASPECT_N = 3;
    public static final int METADATA_KEY_VIDEO_TRACK_ASPECT_D = 4;
    public static final int METADATA_KEY_VIDEO_TRACK_PIXEL_FORMAT = 5;
    public static final int METADATA_KEY_VIDEO_TRACK_PROFILE = 6;
    public static final int METADATA_KEY_VIDEO_TRACK_LEVEL = 7;
    public static final int METADATA_KEY_VIDEO_TRACK_BIT_RATE = 8;
    public static final int METADATA_KEY_VIDEO_TRACK_FPS = 9;
    public static final int METADATA_KEY_VIDEO_TRACK_S3D = 10;
    public static final int METADATA_KEY_VIDEO_TRACK_DECODER = 11;
    public static final int METADATA_KEY_VIDEO_TRACK_FPS_RATE = 12;
    public static final int METADATA_KEY_VIDEO_TRACK_FPS_SCALE = 13;
    public static final int METADATA_KEY_VIDEO_TRACK_MAX = 14;

    public static final int METADATA_KEY_AUDIO_TRACK = 20000;
    public static final int METADATA_KEY_AUDIO_TRACK_NAME = 0;
    public static final int METADATA_KEY_AUDIO_TRACK_FORMAT = 1;
    public static final int METADATA_KEY_AUDIO_TRACK_BIT_RATE = 2;
    public static final int METADATA_KEY_AUDIO_TRACK_SAMPLE_RATE = 3;
    public static final int METADATA_KEY_AUDIO_TRACK_CHANNELS = 4;
    public static final int METADATA_KEY_AUDIO_TRACK_VBR = 5;
    public static final int METADATA_KEY_AUDIO_TRACK_SUPPORTED = 6;
    public static final int METADATA_KEY_AUDIO_TRACK_MAX = 7;

    public static final int METADATA_KEY_SUBTITLE_TRACK = 30000;
    public static final int METADATA_KEY_SUBTITLE_TRACK_NAME = 0;
    public static final int METADATA_KEY_SUBTITLE_TRACK_PATH = 1;
    public static final int METADATA_KEY_SUBTITLE_TRACK_MAX = 2;
}
