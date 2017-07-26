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
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import java.io.FileDescriptor;
import java.util.Map;

public interface IMediaMetadataRetriever {

    public static final int TYPE_AVOS = 0;
    public static final int TYPE_ANDROID = 1;
    public int getType();

    /**
     * Sets the data source (file pathname) to use. Call this
     * method before the rest of the methods in this class. This method may be
     * time-consuming.
     * 
     * @param path The path of the input media file.
     * @throws IllegalArgumentException If the path is invalid.
     */
    public void setDataSource(String path) throws IllegalArgumentException;

    /**
     * Sets the data source (URI) to use. Call this
     * method before the rest of the methods in this class. This method may be
     * time-consuming.
     *
     * @param uri The URI of the input media.
     * @param headers the headers to be sent together with the request for the data
     * @throws IllegalArgumentException If the URI is invalid.
     */
    public void setDataSource(String uri,  Map<String, String> headers)
            throws IllegalArgumentException;

    /**
     * Sets the data source (FileDescriptor) to use.  It is the caller's
     * responsibility to close the file descriptor. It is safe to do so as soon
     * as this call returns. Call this method before the rest of the methods in
     * this class. This method may be time-consuming.
     * 
     * @param fd the FileDescriptor for the file you want to play
     * @param offset the offset into the file where the data to be played starts,
     * in bytes. It must be non-negative
     * @param length the length in bytes of the data to be played. It must be
     * non-negative.
     * @throws IllegalArgumentException if the arguments are invalid
     */
    public void setDataSource(FileDescriptor fd, long offset, long length)
            throws IllegalArgumentException;
    
    /**
     * Sets the data source (FileDescriptor) to use. It is the caller's
     * responsibility to close the file descriptor. It is safe to do so as soon
     * as this call returns. Call this method before the rest of the methods in
     * this class. This method may be time-consuming.
     * 
     * @param fd the FileDescriptor for the file you want to play
     * @throws IllegalArgumentException if the FileDescriptor is invalid
     */
    public void setDataSource(FileDescriptor fd)
            throws IllegalArgumentException;
    
    /**
     * Sets the data source as a content Uri. Call this method before 
     * the rest of the methods in this class. This method may be time-consuming.
     * 
     * @param context the Context to use when resolving the Uri
     * @param uri the Content URI of the data you want to play
     * @throws IllegalArgumentException if the Uri is invalid
     * @throws SecurityException if the Uri cannot be used due to lack of
     * permission.
     */
    public void setDataSource(Context context, Uri uri)
        throws IllegalArgumentException, SecurityException;

    /**
     * Call this method after setDataSource(). This method retrieves the 
     * meta data value associated with the keyCode.
     * 
     * The keyCode currently supported is listed below as METADATA_XXX
     * constants. With any other value, it returns a null pointer.
     * 
     * @param keyCode One of the constants listed below at the end of the class.
     * @return The meta data value associate with the given keyCode on success; 
     * null on failure.
     */
    public String extractMetadata(int keyCode);

    public MediaMetadata getMediaMetadata();

    /**
     * Call this method after setDataSource(). This method finds a
     * representative frame close to the given time position by considering
     * the given option if possible, and returns it as a bitmap. This is
     * useful for generating a thumbnail for an input data source or just
     * obtain and display a frame at the given time position.
     *
     * @param timeUs The time position where the frame will be retrieved.
     * When retrieving the frame at the given time position, there is no
     * guarantee that the data source has a frame located at the position.
     * When this happens, a frame nearby will be returned. If timeUs is
     * negative, time position and option will ignored, and any frame
     * that the implementation considers as representative may be returned.
     *
     * @param option a hint on how the frame is found. Use
     * {@link #OPTION_PREVIOUS_SYNC} if one wants to retrieve a sync frame
     * that has a timestamp earlier than or the same as timeUs. Use
     * {@link #OPTION_NEXT_SYNC} if one wants to retrieve a sync frame
     * that has a timestamp later than or the same as timeUs. Use
     * {@link #OPTION_CLOSEST_SYNC} if one wants to retrieve a sync frame
     * that has a timestamp closest to or the same as timeUs. Use
     * {@link #OPTION_CLOSEST} if one wants to retrieve a frame that may
     * or may not be a sync frame but is closest to or the same as timeUs.
     * {@link #OPTION_CLOSEST} often has larger performance overhead compared
     * to the other options if there is no sync frame located at timeUs.
     *
     * @return A Bitmap containing a representative video frame, which 
     *         can be null, if such a frame cannot be retrieved.
     */
    public Bitmap getFrameAtTime(long timeUs, int option);
    
    /**
     * Call this method after setDataSource(). This method finds a
     * representative frame close to the given time position if possible,
     * and returns it as a bitmap. This is useful for generating a thumbnail
     * for an input data source. Call this method if one does not care
     * how the frame is found as long as it is close to the given time;
     * otherwise, please call {@link #getFrameAtTime(long, int)}.
     *
     * @param timeUs The time position where the frame will be retrieved.
     * When retrieving the frame at the given time position, there is no
     * guarentee that the data source has a frame located at the position.
     * When this happens, a frame nearby will be returned. If timeUs is
     * negative, time position and option will ignored, and any frame
     * that the implementation considers as representative may be returned.
     *
     * @return A Bitmap containing a representative video frame, which
     *         can be null, if such a frame cannot be retrieved.
     *
     * @see #getFrameAtTime(long, int)
     */
    public Bitmap getFrameAtTime(long timeUs);

    /**
     * Call this method after setDataSource(). This method finds a
     * representative frame at any time position if possible,
     * and returns it as a bitmap. This is useful for generating a thumbnail
     * for an input data source. Call this method if one does not
     * care about where the frame is located; otherwise, please call
     * {@link #getFrameAtTime(long)} or {@link #getFrameAtTime(long, int)}
     *
     * @return A Bitmap containing a representative video frame, which
     *         can be null, if such a frame cannot be retrieved.
     *
     * @see #getFrameAtTime(long)
     * @see #getFrameAtTime(long, int)
     */
    public Bitmap getFrameAtTime();

    
    /**
     * Call this method after setDataSource(). This method finds the optional
     * graphic or album/cover art associated associated with the data source. If
     * there are more than one pictures, (any) one of them is returned.
     * 
     * @return null if no such graphic is found.
     */
    public byte[] getEmbeddedPicture();


    /**
     * Call it when one is done with the object. This method releases the memory
     * allocated internally.
     */
    public void release();

    /**
     * Option used in method {@link #getFrameAtTime(long, int)} to get a
     * frame at a specified location.
     *
     * @see #getFrameAtTime(long, int)
     */
    /* Do not change these option values without updating their counterparts
     * in include/media/stagefright/MediaSource.h!
     */
    /**
     * This option is used with {@link #getFrameAtTime(long, int)} to retrieve
     * a sync (or key) frame associated with a data source that is located
     * right before or at the given time.
     *
     * @see #getFrameAtTime(long, int)
     */
    public static final int OPTION_PREVIOUS_SYNC    = MediaMetadataRetriever.OPTION_PREVIOUS_SYNC;
    /**
     * This option is used with {@link #getFrameAtTime(long, int)} to retrieve
     * a sync (or key) frame associated with a data source that is located
     * right after or at the given time.
     *
     * @see #getFrameAtTime(long, int)
     */
    public static final int OPTION_NEXT_SYNC        = MediaMetadataRetriever.OPTION_NEXT_SYNC;
    /**
     * This option is used with {@link #getFrameAtTime(long, int)} to retrieve
     * a sync (or key) frame associated with a data source that is located
     * closest to (in time) or at the given time.
     *
     * @see #getFrameAtTime(long, int)
     */
    public static final int OPTION_CLOSEST_SYNC     = MediaMetadataRetriever.OPTION_CLOSEST_SYNC;
    /**
     * This option is used with {@link #getFrameAtTime(long, int)} to retrieve
     * a frame (not necessarily a key frame) associated with a data source that
     * is located closest to or at the given time.
     *
     * @see #getFrameAtTime(long, int)
     */
    public static final int OPTION_CLOSEST          = MediaMetadataRetriever.OPTION_CLOSEST;

    /*
     * Do not change these metadata key values without updating their
     * counterparts in include/media/mediametadataretriever.h!
     */
    /**
     * The metadata key to retrieve the numberic string describing the
     * order of the audio data source on its original recording.
     */
    public static final int METADATA_KEY_CD_TRACK_NUMBER = MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER;
    /**
     * The metadata key to retrieve the information about the album title
     * of the data source.
     */
    public static final int METADATA_KEY_ALBUM           = MediaMetadataRetriever.METADATA_KEY_ALBUM;
    /**
     * The metadata key to retrieve the information about the artist of
     * the data source.
     */
    public static final int METADATA_KEY_ARTIST          = MediaMetadataRetriever.METADATA_KEY_ARTIST;
    /**
     * The metadata key to retrieve the information about the author of
     * the data source.
     */
    public static final int METADATA_KEY_AUTHOR          = MediaMetadataRetriever.METADATA_KEY_AUTHOR;
    /**
     * The metadata key to retrieve the information about the composer of
     * the data source.
     */
    public static final int METADATA_KEY_COMPOSER        = MediaMetadataRetriever.METADATA_KEY_COMPOSER;
    /**
     * The metadata key to retrieve the date when the data source was created
     * or modified.
     */
    public static final int METADATA_KEY_DATE            = MediaMetadataRetriever.METADATA_KEY_DATE;
    /**
     * The metadata key to retrieve the content type or genre of the data
     * source.
     */
    public static final int METADATA_KEY_GENRE           = MediaMetadataRetriever.METADATA_KEY_GENRE;
    /**
     * The metadata key to retrieve the data source title.
     */
    public static final int METADATA_KEY_TITLE           = MediaMetadataRetriever.METADATA_KEY_TITLE;
    /**
     * The metadata key to retrieve the year when the data source was created
     * or modified.
     */
    public static final int METADATA_KEY_YEAR            = MediaMetadataRetriever.METADATA_KEY_YEAR;
    /**
     * The metadata key to retrieve the playback duration of the data source.
     */
    public static final int METADATA_KEY_DURATION        = MediaMetadataRetriever.METADATA_KEY_DURATION;
    /**
     * The metadata key to retrieve the number of tracks, such as audio, video,
     * text, in the data source, such as a mp4 or 3gpp file.
     */
    public static final int METADATA_KEY_NUM_TRACKS      = MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS;
    /**
     * The metadata key to retrieve the information of the writer (such as
     * lyricist) of the data source.
     */
    public static final int METADATA_KEY_WRITER          = MediaMetadataRetriever.METADATA_KEY_WRITER;
    /**
     * The metadata key to retrieve the mime type of the data source. Some
     * example mime types include: "video/mp4", "audio/mp4", "audio/amr-wb",
     * etc.
     */
    public static final int METADATA_KEY_MIMETYPE        = MediaMetadataRetriever.METADATA_KEY_MIMETYPE;
    /**
     * The metadata key to retrieve the information about the performers or
     * artist associated with the data source.
     */
    public static final int METADATA_KEY_ALBUMARTIST     = MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST;
    /**
     * The metadata key to retrieve the numberic string that describes which
     * part of a set the audio data source comes from.
     */
    public static final int METADATA_KEY_DISC_NUMBER     = MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER;
    /**
     * The metadata key to retrieve the music album compilation status.
     */
    public static final int METADATA_KEY_COMPILATION     = MediaMetadataRetriever.METADATA_KEY_COMPILATION;
    /**
     * If this key exists the media contains audio content.
     */
    public static final int METADATA_KEY_HAS_AUDIO       = MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO;
    /**
     * If this key exists the media contains video content.
     */
    public static final int METADATA_KEY_HAS_VIDEO       = MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO;
    /**
     * If the media contains video, this key retrieves its width.
     */
    public static final int METADATA_KEY_VIDEO_WIDTH     = MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH;
    /**
     * If the media contains video, this key retrieves its height.
     */
    public static final int METADATA_KEY_VIDEO_HEIGHT    = MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT;
    /**
     * This key retrieves the average bitrate (in bits/sec), if available.
     */
    public static final int METADATA_KEY_BITRATE         = MediaMetadataRetriever.METADATA_KEY_BITRATE;
    /**
     * This key retrieves the language code of text tracks, if available.
     * If multiple text tracks present, the return value will look like:
     * "eng:chi"
     * @hide
     */
    public static final int METADATA_KEY_TIMED_TEXT_LANGUAGES      = 21 /*MediaMetadataRetriever.METADATA_KEY_TIMED_TEXT_LANGUAGES*/;
    /**
     * If this key exists the media is drm-protected.
     * @hide
     */
    public static final int METADATA_KEY_IS_DRM          = 22 /*MediaMetadataRetriever.METADATA_KEY_IS_DRM*/;
    /**
     * This key retrieves the location information, if available.
     * The location should be specified according to ISO-6709 standard, under
     * a mp4/3gp box "@xyz". Location with longitude of -90 degrees and latitude
     * of 180 degrees will be retrieved as "-90.0000+180.0000", for instance.
     */
    public static final int METADATA_KEY_LOCATION        = MediaMetadataRetriever.METADATA_KEY_LOCATION;

    /* Avos specific */

    /**
     * The metadata key to retrieve the sample rate
     */
    public static final int METADATA_KEY_SAMPLE_RATE     = 24;
    /**
     * The metadata key to retrieve the number of channels
     */
    public static final int METADATA_KEY_NUMBER_OF_CHANNELS = 25;
    /**
     * The metadata key to retrieve the audio wave codec
     */
    public static final int METADATA_KEY_AUDIO_WAVE_CODEC = 26;
    /**
     * The metadata key to retrieve the audio bitrate
     */
    public static final int METADATA_KEY_AUDIO_BITRATE = 27;
    /**
     * The metadata key to retrieve the video FourCC codec
     */
    public static final int METADATA_KEY_VIDEO_FOURCC_CODEC = 28;
    /**
     * The metadata key to retrieve the video bitrate
     */
    public static final int METADATA_KEY_VIDEO_BITRATE = 29;
    /**
     * The metadata key to retrieve the Frames per thousand seconds
     */
    public static final int METADATA_KEY_FRAMES_PER_THOUSAND_SECONDS = 30;
    /**
     * The metadata key to retrieve the encoding profile
     */
    public static final int METADATA_KEY_ENCODING_PROFILE = 31;
    /**
     * The metadata key to retrieve the file size
     */
    public static final int METADATA_KEY_FILE_SIZE = 32;

    public static final int METADATA_KEY_NB_VIDEO_TRACK     = 9000;

    public static final int METADATA_KEY_NB_AUDIO_TRACK     = 9001;

    public static final int METADATA_KEY_NB_SUBTITLE_TRACK  = 9002;

    public static final int METADATA_KEY_VIDEO_TRACK        = 10000;

    public static final int METADATA_KEY_AUDIO_TRACK        = 20000;

    public static final int METADATA_KEY_SUBTITLE_TRACK     = 30000;

    public static final int METADATA_KEY_VIDEO_TRACK_FORMAT         = 0;
    public static final int METADATA_KEY_VIDEO_TRACK_WIDTH          = 1;
    public static final int METADATA_KEY_VIDEO_TRACK_HEIGHT         = 2;
    public static final int METADATA_KEY_VIDEO_TRACK_ASPECT_N       = 3;
    public static final int METADATA_KEY_VIDEO_TRACK_ASPECT_D       = 4;
    public static final int METADATA_KEY_VIDEO_TRACK_PIXEL_FORMAT   = 5;
    public static final int METADATA_KEY_VIDEO_TRACK_PROFILE        = 6;
    public static final int METADATA_KEY_VIDEO_TRACK_LEVEL          = 7;
    public static final int METADATA_KEY_VIDEO_TRACK_BIT_RATE       = 8;
    public static final int METADATA_KEY_VIDEO_TRACK_FPS            = 9;
    public static final int METADATA_KEY_VIDEO_TRACK_S3D_MODE       = 10;
    public static final int METADATA_KEY_VIDEO_TRACK_FPS_RATE       = 11;
    public static final int METADATA_KEY_VIDEO_TRACK_FPS_SCALE      = 12;
    public static final int METADATA_KEY_VIDEO_TRACK_MAX            = 13;

    public static final int METADATA_KEY_AUDIO_TRACK_NAME           = 0;
    public static final int METADATA_KEY_AUDIO_TRACK_FORMAT         = 1;
    public static final int METADATA_KEY_AUDIO_TRACK_BIT_RATE       = 2;
    public static final int METADATA_KEY_AUDIO_TRACK_SAMPLE_RATE    = 3;
    public static final int METADATA_KEY_AUDIO_TRACK_CHANNELS       = 4;
    public static final int METADATA_KEY_AUDIO_TRACK_VBR            = 5;
    public static final int METADATA_KEY_AUDIO_TRACK_SUPPORTED      = 6;
    public static final int METADATA_KEY_AUDIO_TRACK_MAX            = 7;

    public static final int METADATA_KEY_SUBTITLE_TRACK_NAME        = 0;
    public static final int METADATA_KEY_SUBTITLE_TRACK_PATH        = 1;
    public static final int METADATA_KEY_SUBTITLE_TRACK_MAX         = 2;
}
