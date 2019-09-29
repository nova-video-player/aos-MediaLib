/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.archos.mediaprovider.video;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.util.Log;

import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.filecoreextension.UriUtils;
import com.archos.mediaprovider.ArchosMediaCommon;
import com.archos.mediaprovider.ArchosMediaIntent;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Modified copy of Android's MediaProvider. It may give you Uris for stock mediaprovider.<br>
 * The Media provider contains meta data for all available media on both internal
 * and external storage devices.
 */
public final class VideoStore {
    private static final String TAG =  ArchosMediaCommon.TAG_PREFIX + VideoStore.class.getSimpleName();
    private static final boolean LOCAL_DBG = false;
    private static final boolean DBG = ArchosMediaCommon.PACKAGE_DBG & LOCAL_DBG;

    public static final String AUTHORITY = ArchosMediaCommon.AUTHORITY_VIDEO;
    private static final String CONTENT_AUTHORITY_SLASH = ArchosMediaCommon.CONTENT_AUTHORITY_SLASH_VIDEO;

    // used other classes to notify changes on everything in the database
    public static final Uri ALL_CONTENT_URI = Uri.parse(CONTENT_AUTHORITY_SLASH + "external");

    /**
     * Sends a broadcast intent that requests scanning a file or directory
     * <p>
     * Either to Android's MediaScanner so new data is imported
     * or to our network scanner. In both cases new data should show up
     * soon in the database. Can take quite some time if scanner is busy.
     * Requests are queued up.
     * @param uri
     * @param context
     */

    public static void requestIndexing(Uri uri, Context context) {
        requestIndexing(uri, context, true);
    }

    /**
     * set hidden false when reindex is true
     * */

    public static void requestIndexing(Uri uri, Context context, boolean reIndex) {
        if (uri == null || context == null) {
            Log.w(TAG, "requestIndexing: file or context null");
            return;
        }
        //first check if video is hidden
        if(reIndex) {
            Uri tmp = uri;
            if ("file".equals(tmp.getScheme())) {
                tmp = Uri.parse(uri.toString().substring("file://".length()));
            }
            String whereR = VideoStore.MediaColumns.DATA + " = ?";
            String[] whereRArgs = { tmp.toString() };

            Cursor cursor = context.getContentResolver().query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
                    new String[] { VideoStore.MediaColumns.DATA }, whereR, whereRArgs, null);
            
            if (cursor.getCount() > 0) {
                final ContentValues cvR = new ContentValues(1);
                String col = VideoStore.Video.VideoColumns.ARCHOS_HIDDEN_BY_USER;
                cvR.put(col, 0);
                context.getContentResolver().update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, cvR, whereR, whereRArgs);
            }
            else {
                whereR = MediaStore.Files.FileColumns.DATA + " = ?";
                cursor.close();
                cursor = context.getContentResolver().query(MediaStore.Files.getContentUri("external"),
                        new String[] { MediaStore.Files.FileColumns.DATA }, whereR, whereRArgs, null);

                final ContentValues cvR = new ContentValues(1);

                if (cursor.getCount() > 0) {
                    long newId = 0;

                    cursor.close();
                    cursor = context.getContentResolver().query(MediaStore.Files.getContentUri("external"),
                            new String[] { "MAX(" + MediaStore.Files.FileColumns._ID + ")" }, null, null, null);

                    if (cursor.getCount() > 0) {
                        cursor.moveToFirst();

                        long id = cursor.getLong(0);
                        newId = id + 1;
                    }

                    String col = MediaStore.Files.FileColumns._ID;
                    cvR.put(col, newId);
                    context.getContentResolver().update(MediaStore.Files.getContentUri("external"), cvR, whereR, whereRArgs);
                }
                else {
                    String col = MediaStore.Files.FileColumns.DATA;
                    cvR.put(col, tmp.toString());
                    context.getContentResolver().insert(MediaStore.Files.getContentUri("external"), cvR);
                }
            }
            cursor.close();
        }
        String action;
        if ((!FileUtils.isLocal(uri)||UriUtils.isContentUri(uri))&& UriUtils.isIndexable(uri)) {
            action = ArchosMediaIntent.ACTION_VIDEO_SCANNER_SCAN_FILE;
        }
        else {
            action = Intent.ACTION_MEDIA_SCANNER_SCAN_FILE;
            if(uri.getScheme()==null)
                uri = Uri.parse("file://"+uri.toString());
        }
        Intent scanIntent = new Intent(action);
        scanIntent.setData(uri);
        scanIntent.setPackage(context.getPackageName());
        if(!UriUtils.isContentUri(uri)) { // doesn't work with content
            context.sendBroadcast(scanIntent);
        }
        else {
            NetworkScannerServiceVideo.startIfHandles(context, scanIntent);
        }
    }
    private static final int ARCHOS_PARAMS_SIZE = 10;
    private static final int ARCHOS_PARAMS_MASK = (1 << ARCHOS_PARAMS_SIZE) - 1;

    /** for {@link VideoStore.Video.VideoColumns#ARCHOS_PLAYER_PARAMS} */
    public static int paramsToAudioTrack(int params) {
        return params & ARCHOS_PARAMS_MASK;
    }

    /** for {@link VideoStore.Video.VideoColumns#ARCHOS_PLAYER_PARAMS} */
    public static int   paramsToSubtitleTrack(int params) {
        return ((params >> ARCHOS_PARAMS_SIZE) & ARCHOS_PARAMS_MASK)-1;
    }

    /** for {@link VideoStore.Video.VideoColumns#ARCHOS_PLAYER_PARAMS} */
    public static int paramsFromTracks(int audioTrack, int subtitleTrack) {
        return (((subtitleTrack+1) & ARCHOS_PARAMS_MASK) << ARCHOS_PARAMS_SIZE) |
                (audioTrack & ARCHOS_PARAMS_MASK);
    }

    /**
     * Perform raw sql queries using the "selection" parameter as sql string
     * and optionally "selectionArgs" as args (if the sql string contains '?').
     * Other parameters (projection, ..) are ignored
     * <p>
     * see {@link SQLiteDatabase#rawQuery(String, String[])} for details
     * <p><b>Example</b>
     * <pre>
     * ContentResolver cr = context.getContentResolver();
     * Uri uri = VideoStore.RAW_QUERY;
     * String selection = "SELECT * FROM video WHERE _id=? ORDER BY title";
     * String[] selectionArgs = { "1" };
     * Cursor c = cr.query(uri, null, selection, selectionArgs, null);
     * </pre>
     */
    public static final Uri RAW_QUERY = VideoStoreInternal.RAWQUERY;

    // ---------------------------------------------------------------------- //
    // Most parts below are just a copy of MediaStore.java from the framework //
    // The biggest difference is that this version hands out our provider     //
    // Uri for parts of the database that we can server locally and hands out //
    // the Uri of Android's media provider for parts we don't have locally    //
    // ---------------------------------------------------------------------- //

    /**
     * The string that is used when a media attribute is not known. For example,
     * if an audio file does not have any meta data, the artist and album columns
     * will be set to this value.
     */
   public static final String UNKNOWN_STRING = "<unknown>";

    /**
     * Common fields for most MediaProvider tables
     */

    public interface MediaColumns extends BaseColumns {
        /**
         * The data stream for the file
         * <P>Type: DATA STREAM</P>
         */
        public static final String DATA = "_data";

        /**
         * The size of the file in bytes
         * <P>Type: INTEGER (long)</P>
         */
        public static final String SIZE = "_size";

        /**
         * The display name of the file
         * <P>Type: TEXT</P>
         */
        public static final String DISPLAY_NAME = "_display_name";

        /**
         * The title of the content
         * <P>Type: TEXT</P>
         */
        public static final String TITLE = "title";

        /**
         * The time the file was added to the media provider
         * Units are seconds since 1970.
         * <P>Type: INTEGER (long)</P>
         */
        public static final String DATE_ADDED = "date_added";

        /**
         * The time the file was last modified
         * Units are seconds since 1970.
         * NOTE: This is for internal use by the media scanner.  Do not modify this field.
         * <P>Type: INTEGER (long)</P>
         */
        public static final String DATE_MODIFIED = "date_modified";

        /**
         * The MIME type of the file
         * <P>Type: TEXT</P>
         */
        public static final String MIME_TYPE = "mime_type";

        /**
         * The width of the image/video in pixels.
         */
        public static final String WIDTH = "width";

        /**
         * The height of the image/video in pixels.
         */
        public static final String HEIGHT = "height";
     }

    /**
     * Media provider table containing an index of all files in the media storage,
     * including non-media files.  This should be used by applications that work with
     * non-media file types (text, HTML, PDF, etc) as well as applications that need to
     * work with multiple media file types in a single query.
     */
    public static final class Files {

        /**
         * Get the content:// style URI for the files table on the
         * given volume. SERVED FROM LOCAL DB.
         *
         * @param volumeName the name of the volume to get the URI for
         * @return the URI to the files table on the given volume
         */
        public static Uri getContentUri(String volumeName) {
            return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName +
                    "/file");
        }

        /**
         * Get the content:// style URI for a single row in the files table on the
         * given volume. SERVED FROM LOCAL DB.
         *
         * @param volumeName the name of the volume to get the URI for
         * @param rowId the file to get the URI for
         * @return the URI to the files table on the given volume
         */
        public static final Uri getContentUri(String volumeName,
                long rowId) {
            return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName
                    + "/file/" + rowId);
        }

        /**
         * Fields for master table for all media files.
         * Table also contains MediaColumns._ID, DATA, SIZE and DATE_MODIFIED.
         */
        public interface FileColumns extends MediaColumns {
            /**
             * The MTP storage ID of the file
             * <P>Type: INTEGER</P>
             */
            public static final String STORAGE_ID = "storage_id";

            /**
             * The MTP format code of the file
             * <P>Type: INTEGER</P>
             */
            public static final String FORMAT = "format";

            /**
             * The index of the parent directory of the file
             * <P>Type: INTEGER</P>
             */
            public static final String PARENT = "parent";

            /**
             * The MIME type of the file
             * <P>Type: TEXT</P>
             */
            public static final String MIME_TYPE = "mime_type";

            /**
             * The title of the content
             * <P>Type: TEXT</P>
             */
            public static final String TITLE = "title";

            /**
             * The media type (audio, video, image or playlist)
             * of the file, or 0 for not a media file
             * <P>Type: TEXT</P>
             */
            public static final String MEDIA_TYPE = "media_type";

            /**
             * The Synchronization ID
             * <P>Type: TEXT</P>
             */
            public static final String ARCHOS_SYNC_ID = "Archos_syncId";

            /**
             * Is this file consumable or not?
             * <P>Type: TEXT</P>
             */
            public static final String ARCHOS_NON_CONSUMABLE = "Archos_nonConsumable";

            /**
             * Used by MediaScanner in light-indexing mode to store the remote server id
             * <P>Type: INTEGER</P>
             */
            public static final String ARCHOS_SMB_SERVER = "Archos_smbserver";

            /**
             * The bucket id of the video. This is a read-only property that
             * is automatically computed from the DATA column.
             * <P>Type: TEXT</P>
             */
            public static final String BUCKET_ID = "bucket_id";

            /**
             * Constant for the {@link #MEDIA_TYPE} column indicating that file
             * is not an audio, image, video or playlist file.
             */
            public static final int MEDIA_TYPE_NONE = 0;

            /**
             * Constant for the {@link #MEDIA_TYPE} column indicating that file is an image file.
             */
            public static final int MEDIA_TYPE_IMAGE = 1;

            /**
             * Constant for the {@link #MEDIA_TYPE} column indicating that file is an audio file.
             */
            public static final int MEDIA_TYPE_AUDIO = 2;

            /**
             * Constant for the {@link #MEDIA_TYPE} column indicating that file is an video file.
             */
            public static final int MEDIA_TYPE_VIDEO = 3;

            /**
             * Constant for the {@link #MEDIA_TYPE} column indicating that file is an playlist file.
             */
            public static final int MEDIA_TYPE_PLAYLIST = 4;

            /**
             * Constant for the {@link #MEDIA_TYPE} column indicating that file is a subtitle file.
             */
            public static final int MEDIA_TYPE_SUBTITLE = 5;
        }
    }

    public static final class SmbServer {

        /**
         * Get the content:// style URI for the smb_server table on the
         * external volume.
         * @return the URI to the smb_server table on the given volume
         */
        public static Uri getContentUri() {
            return Uri.parse(CONTENT_AUTHORITY_SLASH + "external/smb_server");
        }
        /**
         * Get the content:// style URI for a single row in the smb_server table.
         * @param rowId the file to get the URI for
         * @return the URI to the smb_server table on the external volume
         */
        public static final Uri getContentUri(long rowId) {
            return Uri.parse(CONTENT_AUTHORITY_SLASH + "external/smb_server/" + rowId);
        }
        /**
         * Fields for smb_server table.
         * MediaColumns._ID is the server id, DATA is the server key.
         */
        public interface SmbServerColumns extends MediaColumns {
            /**
             * unix timestamp where this server was last seen.
             * <P>Type: INTEGER</P>
             */
            public static final String LAST_SEEN = "last_seen";

            /**
             * Server active? 0 : No, no wifi or server not there, 1: yes
             * <P>Type: INTEGER</P>
             */
            public static final String ACTIVE = "active";
        }
    }

    /**
     * This class is used internally by Images.Thumbnails and Video.Thumbnails, it's not intended
     * to be accessed elsewhere.
     */
    private static class InternalThumbnails implements BaseColumns {
        private static final int MINI_KIND = 1;

        private static final String[] PROJECTION = new String[] {_ID, MediaColumns.DATA};
        static final int DEFAULT_GROUP_ID = 0;

        private static Bitmap getMiniThumbFromFile(Cursor c, Uri baseUri, ContentResolver cr, BitmapFactory.Options options) {
            Bitmap bitmap = null;
            Uri thumbUri = null;
            try {
                long thumbId = c.getLong(0);
                thumbUri = ContentUris.withAppendedId(baseUri, thumbId);
                ParcelFileDescriptor pfdInput = cr.openFileDescriptor(thumbUri, "r");
                bitmap = BitmapFactory.decodeFileDescriptor(
                        pfdInput.getFileDescriptor(), null, options);
                pfdInput.close();
            } catch (FileNotFoundException ex) {
                Log.e(TAG, "couldn't open thumbnail " + thumbUri + "; " + ex);
            } catch (IOException ex) {
                Log.e(TAG, "couldn't open thumbnail " + thumbUri + "; " + ex);
            } catch (OutOfMemoryError ex) {
                Log.e(TAG, "failed to allocate memory for thumbnail "
                        + thumbUri + "; " + ex);
            }
            return bitmap;
        }

        /**
         * This method cancels the thumbnail request so clients waiting for getThumbnail will be
         * interrupted and return immediately. Only the original process which made the getThumbnail
         * requests can cancel their own requests.
         *
         * @param cr ContentResolver
         * @param origId original image or video id. use -1 to cancel all requests.
         * @param groupId the same groupId used in getThumbnail
         * @param baseUri the base URI of requested thumbnails
         */
        static void cancelThumbnailRequest(ContentResolver cr, long origId, Uri baseUri,
                long groupId) {
            Uri cancelUri = baseUri.buildUpon().appendQueryParameter("cancel", "1")
                    .appendQueryParameter("orig_id", String.valueOf(origId))
                    .appendQueryParameter("group_id", String.valueOf(groupId)).build();
            Cursor c = null;
            try {
                c = cr.query(cancelUri, PROJECTION, null, null, null);
            }
            finally {
                if (c != null) c.close();
            }
        }
        /**
         * This method ensure thumbnails associated with origId are generated and decode the
         * file (MINI_KIND).
         *
         * @param cr ContentResolver
         * @param origId original image or video id
         * @param kind could be MINI_KIND
         * @param options this is only used for MINI_KIND when decoding the Bitmap
         * @param baseUri the base URI of requested thumbnails
         * @param groupId the id of group to which this request belongs
         * @return Bitmap bitmap of specified thumbnail kind
         */
        static Bitmap getThumbnail(ContentResolver cr, long origId, long groupId, int kind,
                BitmapFactory.Options options, Uri baseUri, boolean isVideo, boolean createNewThumb) {
            Bitmap bitmap = null;

            // we don't support images, just videos.
            if (!isVideo) return null;

            Cursor c = null;
            try {
                if (kind == MINI_KIND) {
                    // try to decode already existing image
                    String column = isVideo ? "video_id=" : "image_id=";
                    c = cr.query(baseUri, PROJECTION, column + origId, null, null);
                    if (c != null && c.moveToFirst()) {
                        bitmap = getMiniThumbFromFile(c, baseUri, cr, options);
                        if (bitmap != null) {
                            return bitmap;
                        }
                    }
                }

                // still here: no existing image / decoding failed. -> request image generation blocking
                if(createNewThumb) {
                    Uri blockingUri = baseUri.buildUpon().appendQueryParameter("blocking", "1")
                            .appendQueryParameter("orig_id", String.valueOf(origId))
                            .appendQueryParameter("group_id", String.valueOf(groupId)).build();
                    if (c != null) c.close();
                    c = cr.query(blockingUri, PROJECTION, null, null, null);
                    // This happens when original image/video doesn't exist.
                    if (c == null) return null;

                    // Assuming thumbnail has been generated, at least original image exists.
                    if (kind == MINI_KIND) {
                        if (c.moveToFirst()) {
                            bitmap = getMiniThumbFromFile(c, baseUri, cr, options);
                        }
                    } else {
                        throw new IllegalArgumentException("Unsupported kind: " + kind);
                    }
                }

            } catch (SQLiteException ex) {
                Log.w(TAG, ex);
            } finally {
                if (c != null) c.close();
            }
            return bitmap;
        }
    }


    public static class VideoList{
        public interface Columns {
            String LIST_ID = "list_id";
            String M_ONLINE_ID = Video.Media.SCRAPER_M_ONLINE_ID;
            String E_ONLINE_ID = Video.Media.SCRAPER_E_ONLINE_ID;
            String[] COLUMNS = {M_ONLINE_ID, E_ONLINE_ID, List.Columns.SYNC_STATUS};
            String ID = "_id";
        }
        public static class VideoItem {
            public long listId = -1;
            public int movieId;
            public int episodeId;
            public int syncStatus = List.SyncStatus.STATUS_NOT_SYNC;

            public VideoItem(long listId, int movieId, int episodeId, int syncStatus){
                this.listId = listId;
                this.movieId = movieId;
                this.episodeId = episodeId;
                this.syncStatus = syncStatus;
            }

            public ContentValues toContentValues() {
                ContentValues contentValues = new ContentValues();
                if(listId != -1)
                    contentValues.put(Columns.LIST_ID, listId);
                contentValues.put(Columns.M_ONLINE_ID, movieId);
                contentValues.put(Columns.E_ONLINE_ID, episodeId);
                contentValues.put(List.Columns.SYNC_STATUS, syncStatus);
                return contentValues;
            }

            public String getDBWhereString(){
                String whereString;
                if(episodeId > 0){
                    whereString = VideoStore.VideoList.Columns.E_ONLINE_ID + " = ?";
                }
                else{
                    whereString = VideoStore.VideoList.Columns.M_ONLINE_ID + " = ?";
                }
                return whereString;
            }

            public String[] getDBWhereArgs(){
                String [] whereArgs;
                if(episodeId > 0){
                    whereArgs = new String[]{String.valueOf(episodeId)};
                }
                else{
                    whereArgs = new String[]{String.valueOf(movieId)};
                }
                return whereArgs;
            }

            public int deleteFromDb(Context context){
                //was deleted online
                String whereString = getDBWhereString();
                String [] whereArgs = getDBWhereArgs();
                return context.getContentResolver().delete(List.getListUri(listId), whereString, whereArgs);
            }
        }
    }
    public static class List {

        public interface Columns {

            /**
             * unique id needed : title can change and
             * trakt-id might not been set.
             * <P>Type: Integer</P>
             */
            String ID = "_id";

            /**
             * List title.
             * <P>Type: String</P>
             */
            String TITLE = "title";

            /**
             * Trakt sync status, values from SyncStatus
             * <P>Type: Integer</P>
             */
            String SYNC_STATUS = "sync_status";

            /**
             * Trakt Id0
             * <P>Type: Integer</P>
             */
            String TRAKT_ID = "trakt_id";

            String[] COLUMNS = {ID, TITLE, TRAKT_ID, SYNC_STATUS};

        }

        public static class SyncStatus{
            public static final int STATUS_OK = 0;
            public static final int STATUS_NOT_SYNC = 1;
            public static final int STATUS_DELETED = 2;
            public static final int STATUS_RENAMED = 3;
        }

        public static class ListObj {
            public String title;
            public int traktId;
            public int syncStatus = SyncStatus.STATUS_NOT_SYNC;
            public int id;

            public ListObj(String title, int traktId, int syncStatus){
                this(-1, title, traktId, syncStatus);
            }

            public ListObj(int id, String title, int traktId, int syncStatus){
                this.id = id;
                this.title = title;
                this.traktId = traktId;
                this.syncStatus = syncStatus;
            }

            public ContentValues toContentValues() {
                ContentValues contentValues = new ContentValues();
                contentValues.put(Columns.TITLE, title);
                contentValues.put(Columns.SYNC_STATUS, syncStatus);
                if(traktId != -1) //must be unique
                    contentValues.put(Columns.TRAKT_ID, traktId);
                return contentValues;
            }
        }

        public static final Uri LIST_CONTENT_URI = Uri.parse(CONTENT_AUTHORITY_SLASH+"list");

        public static Uri getListUri(long listID) {

            return Uri.withAppendedPath(LIST_CONTENT_URI, String.valueOf(listID));
        }
    }

    public static final class Subtitle {
        public interface SubtitleColumns extends BaseColumns {
            // _id - BaseColumns

            /** path of subtitle file */
            public static final String DATA = MediaColumns.DATA;

            /** filesize of file */
            public static final String SIZE = MediaColumns.SIZE;

            /** id of video from videos table */
            public static final String VIDEO_ID = "video_id";

            /** id of subtitle from files table */
            public static final String FILE_ID = "file_id";

            /** language of subtitle, null if unknown */
            public static final String LANG = "lang";
        }

        public static final Uri CONTENT_URI = Uri.parse(CONTENT_AUTHORITY_SLASH +
                "external/subtitles/media");

        /**
         * Get the content:// style URI for the subtitle table.
         */
        public static Uri getContentUri() {
            return CONTENT_URI;
        }
        /**
         * Get the content:// style URI for the subtitle table.
         * <p>for 1 subtitle file by it's id
         */
        public static Uri getContentUri(long id) {
            return ContentUris.withAppendedId(CONTENT_URI, id);
        }
        /**
         * Get the content:// style URI for the subtitle table.
         * <p>for n subtitle files that belong to video with videoId
         */
        public static Uri getContentUriByVideoId(long videoId) {
            return Uri.parse(CONTENT_AUTHORITY_SLASH +
                    "external/subtitles/media/video/" + videoId);
        }

        /**
         * The MIME type for this table.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/subtitle";

    }

    public static final class Video {

        /**
         * The default sort order for this table.
         */
        public static final String DEFAULT_SORT_ORDER = MediaColumns.DISPLAY_NAME;

        public static final Cursor query(ContentResolver cr, Uri uri, String[] projection) {
            return cr.query(uri, projection, null, null, DEFAULT_SORT_ORDER);
        }

        public interface VideoColumns extends MediaColumns {

            /**
             * The duration of the video file, in ms
             * <P>Type: INTEGER (long)</P>
             */
            public static final String DURATION = "duration";

            /**
             * The artist who created the video file, if any
             * <P>Type: TEXT</P>
             */
            public static final String ARTIST = "artist";

            /**
             * The album the video file is from, if any
             * <P>Type: TEXT</P>
             */
            public static final String ALBUM = "album";

            /**
             * The resolution of the video file, formatted as "XxY"
             * <P>Type: TEXT</P>
             */
            public static final String RESOLUTION = "resolution";

            /**
             * The description of the video recording
             * <P>Type: TEXT</P>
             */
            public static final String DESCRIPTION = "description";

            /**
             * Whether the video should be published as public or private
             * <P>Type: INTEGER</P>
             */
            public static final String IS_PRIVATE = "isprivate";

            /**
             * The user-added tags associated with a video
             * <P>Type: TEXT</P>
             */
            public static final String TAGS = "tags";

            /**
             * The YouTube category of the video
             * <P>Type: TEXT</P>
             */
            public static final String CATEGORY = "category";

            /**
             * The language of the video
             * <P>Type: TEXT</P>
             */
            public static final String LANGUAGE = "language";

            /**
             * The latitude where the image was captured.
             * <P>Type: DOUBLE</P>
             */
            public static final String LATITUDE = "latitude";

            /**
             * The longitude where the image was captured.
             * <P>Type: DOUBLE</P>
             */
            public static final String LONGITUDE = "longitude";

            /**
             * The date & time that the image was taken in units
             * of milliseconds since jan 1, 1970.
             * <P>Type: INTEGER</P>
             */
            public static final String DATE_TAKEN = "datetaken";

            /**
             * The mini thumb id.
             * <P>Type: INTEGER</P>
             */
            public static final String MINI_THUMB_MAGIC = "mini_thumb_magic";

            /**
             * The bucket id of the video. This is a read-only property that
             * is automatically computed from the DATA column.
             * <P>Type: TEXT</P>
             */
            public static final String BUCKET_ID = "bucket_id";

            /**
             * The bucket display name of the video. This is a read-only property that
             * is automatically computed from the DATA column.
             * <P>Type: TEXT</P>
             */
            public static final String BUCKET_DISPLAY_NAME = "bucket_display_name";

            /**
             * The bookmark for the video. Time in ms. Represents the location in the video that the
             * video should start playing at the next time it is opened. If the value is null or
             * out of the range 0..DURATION-1 then the video should start playing from the
             * beginning.
             * <P>Type: INTEGER</P>
             */
            public static final String BOOKMARK = "bookmark";

            /**
             * Private archos flag to mark an entry as favorite (date entry is marked)
             * Units are seconds since 1970.
             * <P>Type: INTEGER (long)</P>
             */
            public static final String IS_ARCHOS_FAVORITE = "Archos_favorite_track";

            /**
             * Private archos flag
             * <P>Type: INTEGER</P>
             */
            public static final String ARCHOS_BOOKMARK = "Archos_bookmark";

            /**
             * unique ID used for upnp
             */
            public static final String ARCHOS_UNIQUE_ID = "Archos_unique_id";

            /**
            * Private archos flag to store the last date a media was played
             * Units are seconds since 1970.
             * <P>Type: INTEGER (long)</P>
             */
            public static final String ARCHOS_LAST_TIME_PLAYED = "Archos_lastTimePlayed";

            /**
             * Private archos flag to store data needed by the Avos video player
             * <P>Type: INTEGER</P>
             */
            public static final String ARCHOS_PLAYER_PARAMS = "Archos_playerParams";

            /**
             * Private archos flag to store the subtitle delay (needed by the Avos video player)
             * <P>Type: INTEGER</P>
             */
            public static final String ARCHOS_PLAYER_SUBTITLE_DELAY = "Archos_playerSubtitleDelay";

            /**
             * Private archos flag to store the subtitle (speed) ratio (needed by the Avos video player)<br>
             * <ul>
             * <li> 0: No Correction
             * <li> 1 : NTSC -> PAL
             * <li> 2 : PAL -> NTSC
             * </ol>
             * <P>Type: INTEGER</P>
             */
            public static final String ARCHOS_PLAYER_SUBTITLE_RATIO = "Archos_playerSubtitleRatio";

            /**
             * Private archos flag to store the associated scraper id
             * <P>Type: INTEGER</P>
             */
            public static final String ARCHOS_MEDIA_SCRAPER_ID = "ArchosMediaScraper_id";

            /**
             * Private archos flag to store the associated scraper media type
             * <P>Type: INTEGER</P>
             */
            public static final String ARCHOS_MEDIA_SCRAPER_TYPE = "ArchosMediaScraper_type";

            /**
             * Private archos flag to store the the number of subtitle tracks
             * <P>Type: INTEGER</P>
             */
            public static final String ARCHOS_NUMBER_OF_SUBTITLE_TRACKS = "Archos_numberOfSubtitleTracks";

            /**
             * Private archos flag to store the number of audio tracks
             * <P>Type: INTEGER</P>
             */
            public static final String ARCHOS_NUMBER_OF_AUDIO_TRACKS = "Archos_numberOfAudioTracks";

            /**
             * Private archos flag to store the video FourCCCodec
             * <P>Type: INTEGER</P>
             */
            public static final String ARCHOS_VIDEO_FOURCC_CODEC = "Archos_videoFourCCCodec";

            /**
             * Private archos flag to store the video bitrate
             * <P>Type: INTEGER</P>
             */
            public static final String ARCHOS_VIDEO_BITRATE = "Archos_videoBitRate";

            /**
             * Private archos flag to store the frames per thousand seconds
             * <P>Type: INTEGER</P>
             */
            public static final String ARCHOS_FRAMES_PER_THOUSAND_SECONDS = "Archos_framesPerThousandSeconds";

            /**
             * Private archos flag to store the encoding profile
             * <P>Type: TEXT</P>
             */
            public static final String ARCHOS_ENCODING_PROFILE = "Archos_encodingProfile";

            /**
             * Private archos flag to store the scan type
             * <P>TYPE: INTEGER<P>
             */
            public static final String ARCHOS_SCAN_TYPE = "Archos_scanType";

            /**
             * Private archos flag to hide some special Files. Used for multipart DVD video vobs.
             * <P>TYPE: INTEGER<P>
             */
            public static final String ARCHOS_HIDE_FILE = "Archos_hideFile";

            /**
             * Private archos flag to hide files on user request.
             * <P>TYPE: INTEGER<P>
             */
            public static final String ARCHOS_HIDDEN_BY_USER = "Archos_hiddenByUser";

            /**
             * Custom title set by our apps. Use TITLE to query, it will show this one if it is set.
             * <P>TYPE: TEXT<P>
             */
            public static final String ARCHOS_TITLE = "Archos_title";

            /**
             * Private archos flag to store if video is seen on trakt.
             * <P>TYPE: INTEGER<P>
             */
            public static final String ARCHOS_TRAKT_SEEN = "Archos_traktSeen";

            /**
             * Private archos flag to store if video is on trakt library.
             * <P>TYPE: INTEGER<P>
             */
            public static final String ARCHOS_TRAKT_LIBRARY = "Archos_traktLibrary";

            /**
             * Private archos flag to store trakt resume point for synchronisation.
             * <P>TYPE: INTEGER<P>
             */
            
            public static final String ARCHOS_TRAKT_RESUME = "Archos_traktResume";
            
            /**
             * Private archos flag to store if video is 3D, and which type if it is.
             * <ul>
             * <li> 0 : 2D
             * <li> 1 : 3D Unknown type
             * <li> 2 : 3D (H)Side by Side
             * <li> 3 : 3D (H)Top Bottom
             * <li> 4 : 3D Anaglyph
             * </ol>
             * <P>Type: INTEGER</P>
             */
            public static final String ARCHOS_VIDEO_STEREO = "Archos_videoStereo";

            /**
             * Constant for the {@link #ARCHOS_VIDEO_STEREO} column indicating that the video is 2D.
             */
            public static final int ARCHOS_STEREO_2D = 0;

            /**
             * Constant for the {@link #ARCHOS_VIDEO_STEREO} column indicating that the video is 3D (Unknown type).
             */
            public static final int ARCHOS_STEREO_3D_UNKNOWN = 1;

            /**
             * Constant for the {@link #ARCHOS_VIDEO_STEREO} column indicating that the video is 3D Side by Side.
             */
            public static final int ARCHOS_STEREO_3D_SBS = 2;

            /**
             * Constant for the {@link #ARCHOS_VIDEO_STEREO} column indicating that the video is 3D Top Bottom.
             */
            public static final int ARCHOS_STEREO_3D_TB = 3;

            /**
             * Constant for the {@link #ARCHOS_VIDEO_STEREO} column indicating that the video is 3D Anaglyph.
             */
            public static final int ARCHOS_STEREO_3D_ANAGLYPH = 4;

            /**
             * Private archos flag to store the video definition.
             * <ul>
             * <li> 0 : SD
             * <li> 1 : 720p
             * <li> 2 : 1080p
             * </ol>
             * <P>Type: INTEGER</P>
             */
            public static final String ARCHOS_VIDEO_DEFINITION = "Archos_videoDefinition";

            /**
             * Constant for the {@link #ARCHOS_VIDEO_DEFINITION} column indicating that we do not know the definition.
             */
            public static final int ARCHOS_DEFINITION_UNKNOWN = 0;

            /**
             * Constant for the {@link #ARCHOS_VIDEO_DEFINITION} column indicating that the definition is 720p.
             */
            public static final int ARCHOS_DEFINITION_720P = 1;

            /**
             * Constant for the {@link #ARCHOS_VIDEO_DEFINITION} column indicating that the definition is 1080p.
             */
            public static final int ARCHOS_DEFINITION_1080P = 2;

            /**
             * Constant for the {@link #ARCHOS_VIDEO_DEFINITION} column indicating that the definition is 4K/2160p.
             */
            public static final int ARCHOS_DEFINITION_4K = 3;

            /**
             * Constant for the {@link #ARCHOS_VIDEO_DEFINITION} column indicating that the definition is less than HD.
             */
            public static final int ARCHOS_DEFINITION_SD = 4;

            public static final String ARCHOS_GUESSED_VIDEO_FORMAT = "Archos_guessedVideoFormat";

            public static final String ARCHOS_GUESSED_AUDIO_FORMAT = "Archos_guessedAudioFormat";

            public static final String ARCHOS_CALCULATED_VIDEO_FORMAT = "Archos_calculatedVideoFormat";

            public static final String ARCHOS_CALCULATED_BEST_AUDIOTRACK_CHANNELS = "Archos_bestAudiotrack";

            public static final String ARCHOS_CALCULATED_BEST_AUDIOTRACK_FORMAT = "Archos_bestAudiotrackFormat";


            public static final int ARCHOS_AUDIO_FIVEDOTONE = 1;

            public static final int ARCHOS_AUDIO_SD = 0;
            // MediaScraper additions - anything will be null if not scraped.
            /**
             * MediaScraper: Movie id for queries of e.g. <code>ScraperStore.Movie.URI.ID</code>
             * <P>TYPE: <code>long</code> or <code>null</code> if not a movie<P>
             */
            public static final String SCRAPER_MOVIE_ID = "m_id";
            /**
             * MediaScraper: Show id for queries of e.g. <code>ScraperStore.Show.URI.ID</code>
             * <P>TYPE: <code>long</code> or <code>null</code> if not an episode/show<P>
             */
            public static final String SCRAPER_SHOW_ID = "s_id";
            /**
             * MediaScraper: Episode id for queries of e.g. <code>ScraperStore.Episode.URI.ID</code>
             * <P>TYPE: <code>long</code> or <code>null</code> if not an episode/show<P>
             */
            public static final String SCRAPER_EPISODE_ID = "e_id";
            /**
             * MediaScraper: Movie or Show title
             * <P>TYPE: <code>String</code> or <code>null</code> if not scraped
             */
            public static final String SCRAPER_TITLE = "scraper_name";
            /**
             * MediaScraper: Movie title
             * <P>TYPE: <code>String</code> or <code>null</code> if not a movie
             */
            public static final String SCRAPER_M_NAME = "m_name";
            /**
             * MediaScraper: Show title
             * <P>TYPE: <code>String</code> or <code>null</code> if not a show / episode
             */
            public static final String SCRAPER_S_NAME = "s_name";
            /**
             * MediaScraper: Episode title
             * <P>TYPE: <code>String</code> or <code>null</code> if not a show / episode
             */
            public static final String SCRAPER_E_NAME = "e_name";
            /**
             * MediaScraper: Season number of an episode
             * <P>TYPE: <code>int</code> or <code>null</code> if not a show / episode
             */
            public static final String SCRAPER_E_SEASON = "e_season";
            /**
             * MediaScraper: Episode number of an episode
             * <P>TYPE: <code>int</code> or <code>null</code> if not a show / episode
             */
            public static final String SCRAPER_E_EPISODE = "e_episode";

            /**
             * MediaScraper: Picture of an episode (like a thumbnail but official)
             *
             */
            public static final String SCRAPER_E_PICTURE = "e_picture";

            /**
             * MediaScraper: Episode aired date
             * <P>TYPE: <code>long</code>(milliseconds) or <code>null</code> if not a show / episode
             */
            public static final String SCRAPER_E_AIRED = "e_aired";
            /**
             * MediaScraper: Show premiered date
             * <P>TYPE: <code>long</code>(milliseconds) or <code>null</code> if not a show / episode
             */
            public static final String SCRAPER_S_PREMIERED = "s_premiered";
            /**
             * MediaScraper: Movie year
             * <P>TYPE: <code>int</code>(just the year) or <code>null</code> if not a movie
             */
            public static final String SCRAPER_M_YEAR = "m_year";
            /**
             * MediaScraper: Movie or Episode rating
             * <P>TYPE: <code>float</code>(0.0 - 10.0) or <code>null</code> if not scraped
             */
            public static final String SCRAPER_RATING = "rating";
            /**
             * MediaScraper: Movie rating
             * <P>TYPE: <code>float</code>(0.0 - 10.0) or <code>null</code> if not a movie
             */
            public static final String SCRAPER_M_RATING = "m_rating";
            /**
             * MediaScraper: Episode rating
             * <P>TYPE: <code>float</code>(0.0 - 10.0) or <code>null</code> if not a show / episode
             */
            public static final String SCRAPER_E_RATING = "e_rating";
            /**
             * MediaScraper: Show rating
             * <P>TYPE: <code>float</code>(0.0 - 10.0) or <code>null</code> if not a show / episode
             */
            public static final String SCRAPER_S_RATING = "s_rating";
            /**
             * MediaScraper: Movie or Episode plot
             * <P>TYPE: <code>String</code> or <code>null</code> if not scraped
             */
            public static final String SCRAPER_PLOT = "plot";
            /**
             * MediaScraper: Movie plot
             * <P>TYPE: <code>String</code> or <code>null</code> if not a movie
             */
            public static final String SCRAPER_M_PLOT = "m_plot";
            /**
             * MediaScraper: Episode plot
             * <P>TYPE: <code>String</code> or <code>null</code> if not a show / episode
             */
            public static final String SCRAPER_E_PLOT = "e_plot";
            /**
             * MediaScraper: Show plot
             * <P>TYPE: <code>String</code> or <code>null</code> if not a show / episode
             */
            public static final String SCRAPER_S_PLOT = "s_plot";
            /**
             * MediaScraper: Movie or Show actors & roles
             * <P>TYPE: <code>String</code> or <code>null</code> if not scraped
             * <P>FORMAT: "Kiefer Sutherland (Jack Bauer), Carlos Bernard (Tony Almeida), ..."
             */
            public static final String SCRAPER_ACTORS = "actors";
            /**
             * MediaScraper: Movie Actors
             * <P>TYPE: <code>String</code> or <code>null</code> if not a movie
             * <P>FORMAT: "Kiefer Sutherland (Jack Bauer), Carlos Bernard (Tony Almeida), ..."
             */
            public static final String SCRAPER_M_ACTORS = "m_actors";
            /**
             * MediaScraper: Show Actors
             * <P>TYPE: <code>String</code> or <code>null</code> if not a show / episode
             * <P>FORMAT: "Kiefer Sutherland (Jack Bauer), Carlos Bernard (Tony Almeida), ..."
             */
            public static final String SCRAPER_S_ACTORS = "s_actors";
            /**
             * MediaScraper: Episode Actors / Guests
             * <P>TYPE: <code>String</code> or <code>null</code> if not a show / episode
             * <P>FORMAT: "Kiefer Sutherland (Jack Bauer), Carlos Bernard (Tony Almeida), ..."
             */
            public static final String SCRAPER_E_ACTORS = "e_actors";
            /**
             * MediaScraper: Movie or Episode Directors
             * <P>TYPE: <code>String</code> or <code>null</code> if not scraped
             * <P>FORMAT: "Clint Eastwood, Roland Emmerich, ..."
             */
            public static final String SCRAPER_DIRECTORS = "directors";
            /**
             * MediaScraper: Movie Directors
             * <P>TYPE: <code>String</code> or <code>null</code> if not a movie
             * <P>FORMAT: "Clint Eastwood, Roland Emmerich, ..."
             */
            public static final String SCRAPER_M_DIRECTORS = "m_directors";
            /**
             * MediaScraper: Episode Directors
             * <P>TYPE: <code>String</code> or <code>null</code> if not a show / episode
             * <P>FORMAT: "Clint Eastwood, Roland Emmerich, ..."
             */
            public static final String SCRAPER_E_DIRECTORS = "e_directors";
            /**
             * MediaScraper: Show Directors (seems to be unused)
             * <P>TYPE: <code>String</code> or <code>null</code> if not a show / episode
             * <P>FORMAT: "Clint Eastwood, Roland Emmerich, ..."
             */
            public static final String SCRAPER_S_DIRECTORS = "s_directors";
            /**
             * MediaScraper: Movie / Show Genres
             * <P>TYPE: <code>String</code> or <code>null</code> if not scraped
             * <P>FORMAT: "Action, Commedy, ..."
             */
            public static final String SCRAPER_GENRES = "genres";
            /**
             * MediaScraper: Movie Genres
             * <P>TYPE: <code>String</code> or <code>null</code> if not a movie
             * <P>FORMAT: "Action, Commedy, ..."
             */
            public static final String SCRAPER_M_GENRES = "m_genres";
            /**
             * MediaScraper: Show Genres
             * <P>TYPE: <code>String</code> or <code>null</code> if not a show / episode
             * <P>FORMAT: "Action, Commedy, ..."
             */
            public static final String SCRAPER_S_GENRES = "s_genres";
            /**
             * MediaScraper: Movie Studios / Show Tv Stations
             * <P>TYPE: <code>String</code> or <code>null</code> if not scraped
             * <P>FORMAT: "Pixar, Universal, ..."
             */
            public static final String SCRAPER_STUDIOS = "studios";
            /**
             * MediaScraper: Movie Studios
             * <P>TYPE: <code>String</code> or <code>null</code> if not a movie
             * <P>FORMAT: "Pixar, Universal, ..."
             */
            public static final String SCRAPER_M_STUDIOS = "m_studios";
            /**
             * MediaScraper: Show Tv Stations
             * <P>TYPE: <code>String</code> or <code>null</code> if not a show / episode
             * <P>FORMAT: "FOX, Commedy Central, ..."
             */
            public static final String SCRAPER_S_STUDIOS = "s_studios";
            /**
             * MediaScraper: Movie or Episode / Show Cover, using episode cover over show cover if present
             * <P>TYPE: <code>String</code>(path to file) or <code>null</code> if not scraped
             */
            public static final String SCRAPER_COVER = "cover";
            /**
             * MediaScraper: Movie Cover
             * <P>TYPE: <code>String</code>(path to file) or <code>null</code> if not a movie
             */
            public static final String SCRAPER_M_COVER = "m_cover";
            /**
             * MediaScraper: Episode Cover (= Season Cover)
             * <P>TYPE: <code>String</code>(path to file) or <code>null</code> if not present
             */
            public static final String SCRAPER_E_COVER = "e_cover";
            /**
             * MediaScraper: Show Cover
             * <P>TYPE: <code>String</code>(path to file) or <code>null</code> if not present
             */
            public static final String SCRAPER_S_COVER = "s_cover";
            /**
             * MediaScraper: Backdrop (Fanart) Url
             * <P>TYPE: <code>String</code>("http://..") or <code>null</code> if not present
             */
            public static final String SCRAPER_BACKDROP_URL = "bd_url";
            /**
             * MediaScraper: Backdrop (Fanart) Url - Movie
             * <P>TYPE: <code>String</code>("http://..") or <code>null</code> if not a movie
             */
            public static final String SCRAPER_M_BACKDROP_URL = "m_bd_url";
            /**
             * MediaScraper: Backdrop (Fanart) Url - Show
             * <P>TYPE: <code>String</code>("http://..") or <code>null</code> if not a show / episode
             */
            public static final String SCRAPER_S_BACKDROP_URL = "s_bd_url";
            /**
             * MediaScraper: Backdrop (Fanart) file (NOT PRESENT ATM)
             * <P>TYPE: <code>String</code>(path to file) or <code>null</code> if not present
             */
            public static final String SCRAPER_BACKDROP_FILE = "bd_file";
            /**
             * MediaScraper: Backdrop (Fanart) file (NOT PRESENT ATM) Movie
             * <P>TYPE: <code>String</code>(path to file) or <code>null</code> if not a movie
             */
            public static final String SCRAPER_M_BACKDROP_FILE = "m_bd_file";
            /**
             * MediaScraper: Backdrop (Fanart) file (NOT PRESENT ATM) Show
             * <P>TYPE: <code>String</code>(path to file) or <code>null</code> if not a show
             */
            public static final String SCRAPER_S_BACKDROP_FILE = "s_bd_file";

            /**
             * MediaScraper: Poster ID
             * <P>TYPE: <code>int</code> or <code>null</code>
             */
            public static final String SCRAPER_POSTER_ID = "poster_id";
            /**
             * MediaScraper: Backdrop ID
             * <P>TYPE: <code>int</code> or <code>null</code>
             */
            public static final String SCRAPER_BACKDROP_ID = "backdrop_id";
            /**
             * MediaScraper: Poster (full resolution) - url to download from
             * <P>TYPE: <code>String</code> or <code>null</code>
             */
            public static final String SCRAPER_POSTER_LARGE_URL = "po_large_url";
            /**
             * MediaScraper: Poster (full resolution) - file on storage
             * <P>TYPE: <code>String</code> or <code>null</code>
             */
            public static final String SCRAPER_POSTER_LARGE_FILE = "po_large_file";
            /**
             * MediaScraper: Backdrop (full resolution) - url to download from
             * <P>TYPE: <code>String</code> or <code>null</code>
             */
            public static final String SCRAPER_BACKDROP_LARGE_URL = "bd_large_url";
            /**
             * MediaScraper: Backdrop (full resolution) - file on storage
             * <P>TYPE: <code>String</code> or <code>null</code>
             */
            public static final String SCRAPER_BACKDROP_LARGE_FILE = "bd_large_file";

            /**
             * MediaScraper: Poster (thumb resolution) - url to download from
             * <P>TYPE: <code>String</code> or <code>null</code>
             */
            public static final String SCRAPER_POSTER_THUMB_URL = "po_thumb_url";
            /**
             * MediaScraper: Poster (thumb resolution) - file on storage
             * <P>TYPE: <code>String</code> or <code>null</code>
             */
            public static final String SCRAPER_POSTER_THUMB_FILE = "po_thumb_file";
            /**
             * MediaScraper: Backdrop (thumb resolution) - url to download from
             * <P>TYPE: <code>String</code> or <code>null</code>
             */
            public static final String SCRAPER_BACKDROP_THUMB_URL = "bd_thumb_url";
            /**
             * MediaScraper: Backdrop (thumb resolution) - file on storage
             * <P>TYPE: <code>String</code> or <code>null</code>
             */
            public static final String SCRAPER_BACKDROP_THUMB_FILE = "bd_thumb_file";
            /**
             * MediaScraper: Show Poster ID
             * <P>TYPE: <code>int</code> or <code>null</code>
             */
            public static final String SCRAPER_S_POSTER_ID = "s_poster_id";
            /**
             * MediaScraper: Show Poster (full resolution) - url to download from
             * <P>TYPE: <code>String</code> or <code>null</code>
             */
            public static final String SCRAPER_S_POSTER_LARGE_URL = "s_po_large_url";
            /**
             * MediaScraper: Show Poster (full resolution) - file on storage
             * <P>TYPE: <code>String</code> or <code>null</code>
             */
            public static final String SCRAPER_S_POSTER_LARGE_FILE = "s_po_large_file";
            /**
             * MediaScraper: Show Poster (thumb resolution) - url to download from
             * <P>TYPE: <code>String</code> or <code>null</code>
             */
            public static final String SCRAPER_S_POSTER_THUMB_URL = "s_po_thumb_url";
            /**
             * MediaScraper: Show Poster (thumb resolution) - file on storage
             * <P>TYPE: <code>String</code> or <code>null</code>
             */
            public static final String SCRAPER_S_POSTER_THUMB_FILE = "s_po_thumb_file";

            /**
             * MediaScraper: Movie / Show Online (thetvdb.com / themoviedb.org) id
             * <P>TYPE: <code>String</code> or <code>null</code>
             */
            public static final String SCRAPER_ONLINE_ID = "online_id";

            /**
             * MediaScraper: Movie / Episode Online (thetvdb.com / themoviedb.org) id
             * <P>TYPE: <code>String</code> or <code>null</code>
             */
            public static final String SCRAPER_VIDEO_ONLINE_ID = "video_online_id";

            /**
             * MediaScraper: themoviedb.org id<br>
             * "1858" > http://www.themoviedb.org/movie/1858
             * <P>TYPE: <code>String</code> or <code>null</code>
             */
            public static final String SCRAPER_M_ONLINE_ID = ScraperStore.Movie.ONLINE_ID;
            /**
             * MediaScraper: thetvdb.com show id<br>
             * "73255" > http://thetvdb.com/?tab=series&id=73255
             * <P>TYPE: <code>String</code> or <code>null</code>
             */
            public static final String SCRAPER_S_ONLINE_ID = ScraperStore.Show.ONLINE_ID;
            /**
             * MediaScraper: thetvdb.com episode id<br>
             * requires {@link #SCRAPER_S_ONLINE_ID} to be useful<br>
             * "306192" > http://thetvdb.com/?tab=episode&seriesid=73255&id=306192
             * <P>TYPE: <code>String</code> or <code>null</code>
             */
            public static final String SCRAPER_E_ONLINE_ID = ScraperStore.Episode.ONLINE_ID;

            /**
             * MediaScraper: Movie / Show IMDb id<br>
             * "tt0285331" > http://www.imdb.com/title/tt0285331
             * <P>TYPE: <code>String</code> or <code>null</code>
             */
            public static final String SCRAPER_IMDB_ID = "imdb_id";
            /**
             * MediaScraper: Movie IMDb id<br>
             * "tt0285331" > http://www.imdb.com/title/tt0285331
             * <P>TYPE: <code>String</code> or <code>null</code>
             */
            public static final String SCRAPER_M_IMDB_ID = ScraperStore.Movie.IMDB_ID;
            /**
             * MediaScraper: Show IMDb id<br>
             * "tt0285331" > http://www.imdb.com/title/tt0285331
             * <P>TYPE: <code>String</code> or <code>null</code>
             */
            public static final String SCRAPER_S_IMDB_ID = ScraperStore.Show.IMDB_ID;
            /**
             * MediaScraper: Episode IMDb id (very rarely set)<br>
             * "tt0285331" > http://www.imdb.com/title/tt0285331
             * <P>TYPE: <code>String</code> or <code>null</code>
             */
            public static final String SCRAPER_E_IMDB_ID = ScraperStore.Episode.IMDB_ID;

            /**
             * MediaScraper: Movie / Show Content Rating<br>
             * like "PG-13" (movie) or "TV-14" (show)
             * <P>TYPE: <code>String</code> or <code>null</code>
             */
            public static final String SCRAPER_CONTENT_RATING = "content_rating";
            /**
             * MediaScraper: Movie Content Rating (MPAA)<br>
             * "G" "PG" "PG-13" "R" "NC-17"
             * <P>TYPE: <code>String</code> or <code>null</code>
             */
            public static final String SCRAPER_M_CONTENT_RATING = ScraperStore.Movie.CONTENT_RATING;
            /**
             * MediaScraper: Show Content Rating (TV Parental Guidelines)<br>
             * "TV-Y" "TV-Y7" "TV-G" "TV-PG" "TV-14" "TV-MA"
             * <P>TYPE: <code>String</code> or <code>null</code>
             */
            public static final String SCRAPER_S_CONTENT_RATING = ScraperStore.Show.CONTENT_RATING;

            /**
             * Private archos flag to store the number of time a thumbnail creation
             * has failed for this media.
             * <P>Type: INTEGER</P>
             */
            public static final String ARCHOS_THUMB_TRY = "Archos_thumbTry";

            /**
             * Private archos flag to store the samplerate
             * <P> Type: INTEGER (long)</P>
             */
            public static final String ARCHOS_SAMPLERATE = "Archos_sampleRate";

            /**
             * Private archos flag to store the number of Channels
             * <P> Type: INTEGER (long)</P>
             */
            public static final String ARCHOS_NUMBER_OF_CHANNELS = "Archos_numberOfChannels";

            /**
             * Private archos flag to store the audio bitrate
             * <P> Type: INTEGER (long) </P>
             */
            public static final String ARCHOS_AUDIO_BITRATE = "Archos_audioBitRate";

            /**
             * Private archos flag to store the audio wave codec
             * <P> Type: INTEGER (long) </P>
             */
            public static final String ARCHOS_AUDIO_WAVE_CODEC = "Archos_audioWaveCodec";

            /**
             * Amount of subtitles assiciated with this video in Subtitles table
             * <P> Type: INTEGER (long) </P>
             * This does NOT include file internal (mkv etc) subtitles
             */
            public static final String SUBTITLE_COUNT_EXTERNAL = "subtitle_count_ext";

            /**
             * Autoscraper status field, for better state keeping in there
             * <P> Type: INTEGER</P>
             */
            public static final String AUTOSCRAPER_STATUS = "autoscrape_status";

            /**
             * Private nova flag to store if video is pinned.
             * <P>TYPE: INTEGER<P>
             */
            public static final String NOVA_PINNED = "Nova_pinned";
        }

        public static final class Media implements VideoColumns {
            /**
             * Get the content:// style URI for the video media table on the
             * given volume.
             *
             * @param volumeName the name of the volume to get the URI for
             * @return the URI to the video media table on the given volume
             */
            public static Uri getContentUri(String volumeName) {
                return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName +
                        "/video/media");
            }

            public static Uri getContentUriForPath(String path) {
                return (path.startsWith(Environment.getRootDirectory().getPath()) ?
                        INTERNAL_CONTENT_URI : EXTERNAL_CONTENT_URI);
            }

            /**
             * The content:// style URI for the internal storage.
             */
            public static final Uri INTERNAL_CONTENT_URI =
                    getContentUri("internal");

            /**
             * The content:// style URI for the "primary" external storage
             * volume.
             */
            public static final Uri EXTERNAL_CONTENT_URI =
                    getContentUri("external");

            /**
             * The content:// style URI for lists
             */
            public static final Uri LIST_CONTENT_URI =
                    Uri.withAppendedPath(getContentUri("external"),"list");

            /**
             * The MIME type for this table.
             */
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/video";

            /**
             * The default sort order for this table
             */
            public static final String DEFAULT_SORT_ORDER = TITLE;
        }

        /**
         * This class allows developers to query and get two kinds of thumbnails:
         * MINI_KIND: 512 x 384 thumbnail
         *
         */
        public static class Thumbnails implements BaseColumns {
            /**
             * This method cancels the thumbnail request so clients waiting for getThumbnail will be
             * interrupted and return immediately. Only the original process which made the getThumbnail
             * requests can cancel their own requests.
             *
             * @param cr ContentResolver
             * @param origId original video id
             */
            public static void cancelThumbnailRequest(ContentResolver cr, long origId) {
                InternalThumbnails.cancelThumbnailRequest(cr, origId, EXTERNAL_CONTENT_URI,
                        InternalThumbnails.DEFAULT_GROUP_ID);
            }

            /**
             * This method checks if the thumbnails of the specified image (origId) has been created.
             * It will be blocked until the thumbnails are generated.
             *
             * @param cr ContentResolver used to dispatch queries to MediaProvider.
             * @param origId Original image id associated with thumbnail of interest.
             * @param kind The type of thumbnail to fetch. Should be MINI_KIND.
             * @param options this is only used for MINI_KIND when decoding the Bitmap
             * @return A Bitmap instance. It could be null if the original image
             *         associated with origId doesn't exist or memory is not enough.
             */
            public static Bitmap getThumbnail(ContentResolver cr, long origId, int kind,
                    BitmapFactory.Options options) {
                return InternalThumbnails.getThumbnail(cr, origId,
                        InternalThumbnails.DEFAULT_GROUP_ID, kind, options,
                        EXTERNAL_CONTENT_URI, true, true);
            }


            public static Bitmap getThumbnail(ContentResolver cr, long origId, int kind,
                                              BitmapFactory.Options options, boolean createNewThumb) {
                return InternalThumbnails.getThumbnail(cr, origId,
                        InternalThumbnails.DEFAULT_GROUP_ID, kind, options,
                        EXTERNAL_CONTENT_URI, true, createNewThumb);
            }

            /**
             * This method checks if the thumbnails of the specified image (origId) has been created.
             * It will be blocked until the thumbnails are generated.
             *
             * @param cr ContentResolver used to dispatch queries to MediaProvider.
             * @param origId Original image id associated with thumbnail of interest.
             * @param groupId the id of group to which this request belongs
             * @param kind The type of thumbnail to fetch. Should be MINI_KIND
             * @param options this is only used for MINI_KIND when decoding the Bitmap
             * @return A Bitmap instance. It could be null if the original image associated with
             *         origId doesn't exist or memory is not enough.
             */
            public static Bitmap getThumbnail(ContentResolver cr, long origId, long groupId,
                    int kind, BitmapFactory.Options options) {
                return InternalThumbnails.getThumbnail(cr, origId, groupId, kind, options,
                        EXTERNAL_CONTENT_URI, true, true);
            }

            /**
             * This method cancels the thumbnail request so clients waiting for getThumbnail will be
             * interrupted and return immediately. Only the original process which made the getThumbnail
             * requests can cancel their own requests.
             *
             * @param cr ContentResolver
             * @param origId original video id
             * @param groupId the same groupId used in getThumbnail.
             */
            public static void cancelThumbnailRequest(ContentResolver cr, long origId, long groupId) {
                InternalThumbnails.cancelThumbnailRequest(cr, origId, EXTERNAL_CONTENT_URI, groupId);
            }

            /**
             * Get the content:// style URI for the image media table on the
             * given volume.
             *
             * @param volumeName the name of the volume to get the URI for
             * @return the URI to the image media table on the given volume
             */
            public static Uri getContentUri(String volumeName) {
                return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName +
                        "/video/thumbnails");
            }

            /**
             * The content:// style URI for the internal storage.
             */
            public static final Uri INTERNAL_CONTENT_URI =
                    getContentUri("internal");

            /**
             * The content:// style URI for the "primary" external storage
             * volume.
             */
            public static final Uri EXTERNAL_CONTENT_URI =
                    getContentUri("external");

            /**
             * The default sort order for this table
             */
            public static final String DEFAULT_SORT_ORDER = "video_id ASC";

            /**
             * The data stream for the thumbnail
             * <P>Type: DATA STREAM</P>
             */
            public static final String DATA = "_data";

            /**
             * The original image for the thumbnal
             * <P>Type: INTEGER (ID from Video table)</P>
             */
            public static final String VIDEO_ID = "video_id";

            /**
             * The kind of the thumbnail
             * <P>Type: INTEGER (One of the values below)</P>
             */
            public static final String KIND = "kind";

            public static final int MINI_KIND = 1;

            /**
             * The width of the thumbnal
             * <P>Type: INTEGER (long)</P>
             */
            public static final String WIDTH = "width";

            /**
             * The height of the thumbnail
             * <P>Type: INTEGER (long)</P>
             */
            public static final String HEIGHT = "height";
        }
    }

    /**
     * Name of the file signaling the media scanner to ignore media in the containing directory
     * and its subdirectories. Developers should use this to avoid application graphics showing
     * up in the Gallery and likewise prevent application sounds and music from showing up in
     * the Music app.
     */
    public static final String MEDIA_IGNORE_FILENAME = ".nomedia";

    /**
     * Get the media provider's version.
     * Applications that import data from the media provider into their own caches
     * can use this to detect that the media provider changed, and reimport data
     * as needed. No other assumptions should be made about the meaning of the version.
     * @param context Context to use for performing the query.
     * @return A version string, or null if the version could not be determined.
     */
    public static String getVersion(Context context) {
        Cursor c = context.getContentResolver().query(
                Uri.parse(ArchosMediaCommon.CONTENT_AUTHORITY_SLASH_ANDROID + "none/version"),
                null, null, null, null);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    return c.getString(0);
                }
            } finally {
                c.close();
            }
        }
        return null;
    }

}
