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

package com.archos.mediaprovider.music;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.MediaStore.Files.FileColumns;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.MediaColumns;
import android.text.TextUtils;
import android.util.Log;

import com.archos.medialib.IMediaMetadataRetriever;
import com.archos.medialib.MediaMetadata;
import com.archos.mediaprovider.ArchosMediaCommon;
import com.archos.mediaprovider.ArchosMediaFile;
import com.archos.mediaprovider.ArchosMediaFile.MediaFileType;
import com.archos.mediaprovider.BulkInserter;
import com.archos.mediaprovider.CPOExecutor;
import com.archos.mediaprovider.CustomCursorFactory.CustomCursor;
import com.archos.mediaprovider.MediaRetrieverServiceClient;
import com.archos.mediaprovider.music.MusicStore.Audio.AudioColumns;

import java.io.File;

/**
 * The media db import logic
 */
public class MusicStoreImportImpl {
    private static final String TAG =  ArchosMediaCommon.TAG_PREFIX + "MusicStoreImportImpl";
    private static final boolean LOCAL_DBG = false;
    private static final boolean DBG = ArchosMediaCommon.PACKAGE_DBG & LOCAL_DBG;
    private static final boolean DBG2 = false;

    // TODO remove once no longer required.
    private static final boolean DEBUG_DATALOSS = true;

    private final Context mContext;
    private final ContentResolver mCr;
    private final MediaRetrieverServiceClient mMediaRetrieverServiceClient;

    public MusicStoreImportImpl(Context context) {
        mContext = context;
        mCr = mContext.getContentResolver();
        mMediaRetrieverServiceClient = new MediaRetrieverServiceClient(context);
    }

    public void destroy() {
        mMediaRetrieverServiceClient.unbindAndDestroy();
    }

    public void doFullImport() {
        String countStart;
        if (DEBUG_DATALOSS) countStart = getLocalCount(mCr);
        // save max(local_id)
        String maxLocalId = getMaxLocalId(mCr);
        // replace everything with new data
        int copy = copyData(mCr, null);
        // delete everything that was not replaced
        int del = mCr.delete(MusicStoreInternal.FILES_IMPORT, "local_id <= ?", new String[] { maxLocalId });

        if (DEBUG_DATALOSS) {
            String countEnd = getLocalCount(mCr);
            Log.d(TAG, "full import +:" + copy + " -:" + del + " " + countStart + "=>" + countEnd);
        } else {
            Log.d(TAG, "full import +:" + copy + " -:" + del);
        }

        // then trigger scan of new data
        doScan(mCr);
    }

    public void doIncrementalImport() {
        String countStart;
        if (DEBUG_DATALOSS) countStart = getLocalCount(mCr);
        // 1. Delete all the files in our local db that are no present in remote.
        String existingFiles = getRemoteIdList(mCr);
        int del = mCr.delete(MusicStoreInternal.FILES_IMPORT, "_id NOT IN (" + existingFiles + ")", null);
        // 2. copy all remote files with higher id than our max id
        String maxLocal = getMaxId(mCr);
        int copy = copyData(mCr, maxLocal);
        if (DEBUG_DATALOSS) {
            String countEnd = getLocalCount(mCr);
            Log.d(TAG, "part import +:" + copy + " -:" + del + " " + countStart + "=>" + countEnd);
        } else {
            Log.d(TAG, "part import +:" + copy + " -:" + del);
        }
        // then trigger scan of new data
        doScan(mCr);
    }

    private static final String[] ID_DATA_PROJ = new String[] {
        BaseColumns._ID,
        MediaColumns.DATA
    };
    private static final String UPDATE_WHERE = "remote_id=?";
    /** scans every file in cursor and update database, also closes cursor */
    private void handleScanCursor(Cursor c, ContentResolver cr) {
        if (c == null || c.getCount() == 0) {
            if (c != null) c.close();
            if (DBG) Log.d(TAG, "no media to scan");
            return;
        }

        int scanned = 0;
        CPOExecutor operations = new CPOExecutor(MusicStore.AUTHORITY, cr, 500);
        long time = System.currentTimeMillis() / 1000L;
        String timeString = String.valueOf(time);

        while (c.moveToNext()) {
            String id = c.getString(0);
            String path = c.getString(1);
            Job job = new Job(path, id);
            if (DBG2) Log.d(TAG, "Scanning: " + job.mPath);
            // update property with current file

            ContentValues cv = null;
            try {
                cv = fromRetrieverService(job, timeString);
            } catch (InterruptedException e) {
                // won't happen but stopping as soon as we can would be desired
                break;
            } catch (MediaRetrieverServiceClient.ServiceManagementException e) {
                // something is fishy with our service, abort and try again later.
                break;
            }

            // using ContentProviderOperation so updates are done as single transaction
            operations.add(
                ContentProviderOperation.newUpdate(MusicStoreInternal.FILES)
                        .withSelection(UPDATE_WHERE, new String[] { job.mId })
                        .withValues(cv)
                        .build()
                    );
            scanned++;
        }
        c.close();
        operations.execute();
        Log.d(TAG, "media scanned:" + scanned);
    }

    private static class Job {
        public Job(String path, String id) {
            mPath = path;
            mId = id;
            mMft = ArchosMediaFile.getFileType(path);
            // default mime type / media type
            int mediaType = FileColumns.MEDIA_TYPE_NONE;
            String mimeType = "application/octet-stream";
            boolean retrieve = false;
            if (mMft != null) {
                mimeType = mMft.mimeType;
                if (!isNoMediaPath(path)) {
                    if (ArchosMediaFile.isAudioFileType(mMft.fileType)) {
                        mediaType = FileColumns.MEDIA_TYPE_AUDIO;
                        retrieve = true;
                    } else if (ArchosMediaFile.isVideoFileType(mMft.fileType)) {
                        mediaType = FileColumns.MEDIA_TYPE_VIDEO;
                    }  else if (ArchosMediaFile.isImageFileType(mMft.fileType)) {
                        mediaType = FileColumns.MEDIA_TYPE_IMAGE;
                    } else if (ArchosMediaFile.isPlayListFileType(mMft.fileType)) {
                        mediaType = FileColumns.MEDIA_TYPE_PLAYLIST;
                    }
                }
            }
            mMimeType = mimeType;
            mMediaType = mediaType;
            mRetrieve = retrieve;
        }

        public final String mPath;
        public final String mId;
        public final MediaFileType mMft;
        public final int mMediaType;
        public final boolean mRetrieve;
        public final String mMimeType;
    }

    /** removes file(s) defined by Uri */
    public void doRemove(Uri what) {
        if (what == null || !"file".equals(what.getScheme()))
            return;
        String path = what.getPath();
        File f = new File(path);
        String where = WHERE_PATH;
        if (f.isFile())
            where = WHERE_FILE;
        if (DBG) Log.d(TAG, "Removing file(s): " + path);
        int deleted = mCr.delete(MusicStoreInternal.FILES_IMPORT, where, new String[] { path });
        Log.d(TAG, "removed:" + deleted);
    }

    private static String WHERE_PATH = "_data LIKE ?||'/%'";
    private static String WHERE_FILE = "_data = ?";
    /** executes metadata scan of files defined by Uri */
    public void doScan(Uri what) {
        String path = what.getPath();
        File f = new File(path);
        if (!f.exists()) {
            Log.d(TAG, "Not scanning " + f + ", it does not exist.");
            return;
        }
        String where = WHERE_PATH;
        if (f.isFile())
            where = WHERE_FILE;
        if (DBG) Log.d(TAG, "Scanning Metadata: " + path);
        //initNoMedia(mCr);
        Cursor c = mCr.query(MusicStoreInternal.FILES, ID_DATA_PROJ, where, new String[]{ path }, null);
        handleScanCursor(c, mCr);
    }

    private static final String WHERE_UNSCANNED = "scan_state >= 0 AND scan_state < date_modified AND _id < " + MusicOpenHelper.SCANNED_ID_OFFSET;
    /** executes metadata scan of every unscanned file */
    private void doScan(ContentResolver cr) {
        Cursor c = cr.query(MusicStoreInternal.FILES, ID_DATA_PROJ, WHERE_UNSCANNED, null, null);
        handleScanCursor(c, cr);
    }

    /** get MediaMetadata object or null for a path */
    private MediaMetadata getMetadata(String path) throws RemoteException, InterruptedException, MediaRetrieverServiceClient.ServiceManagementException {
        if(path.startsWith("file://")) {
            path = path.substring("file://".length()); //we need to remove "file://"
        }
        return mMediaRetrieverServiceClient.getMetadata(path);
    }

    /** creates ContentValues via MediaRetrieverService, can't be null */
    private ContentValues fromRetrieverService(Job job, String timeString)  throws InterruptedException, MediaRetrieverServiceClient.ServiceManagementException {
        ContentValues cv = new ContentValues();
        String path = job.mPath;
        // tell mediaprovider that this update originates from here.
        cv.put(MusicStoreInternal.KEY_SCANNER, "1");
        // also put the path here so MediaProvider knows which file it is
        cv.put(MediaColumns.DATA, path);
        cv.put(BaseColumns._ID, job.mId);

        String defaultTitle = getDefaultTitle(path);
        cv.put(FileColumns.TITLE, defaultTitle);
        cv.put(MediaColumns.MIME_TYPE, job.mMimeType);
        cv.put(FileColumns.MEDIA_TYPE, String.valueOf(job.mMediaType));
        cv.put(MusicStoreInternal.FILES_EXTRA_COLUMN_SCAN_STATE, timeString);

        // try to get metadata if file is a mediafile
        MediaMetadata metadata = null;
        if (job.mRetrieve) {
            try {
                metadata = getMetadata(path);
            } catch (RemoteException e) {
                Log.w(TAG, "Blacklisting file because it killed metadata service:" + path);
                cv.put(MusicStoreInternal.FILES_EXTRA_COLUMN_SCAN_STATE, String.valueOf(MusicStoreInternal.SCAN_STATE_SCAN_FAILED));
                return cv;
            }
            if (metadata == null) {
                // file didn't kill the service but still failed to give metadata
                Log.d(TAG, "Failed to get metadata for file:" + path);
                return cv;
            }
        }

        // if we don't need to scan further also end here.
        if (!job.mRetrieve)
            return cv;

        if (DBG) Log.d(TAG, "Scanning metadata of: " + path);
        switch (job.mMediaType) {
            case FileColumns.MEDIA_TYPE_AUDIO:
                extract(cv, metadata, AudioColumns.ALBUM, IMediaMetadataRetriever.METADATA_KEY_ALBUM, null);
                extract(cv, metadata, AudioColumns.ALBUM_ARTIST, IMediaMetadataRetriever.METADATA_KEY_ALBUMARTIST, null);
                extract(cv, metadata, AudioColumns.ARTIST, IMediaMetadataRetriever.METADATA_KEY_ARTIST, null);
                extract(cv, metadata, AudioColumns.COMPILATION, IMediaMetadataRetriever.METADATA_KEY_COMPILATION, null);
                extract(cv, metadata, AudioColumns.GENRE, IMediaMetadataRetriever.METADATA_KEY_GENRE, null);
                extract(cv, metadata, AudioColumns.ARCHOS_SAMPLERATE, IMediaMetadataRetriever.METADATA_KEY_SAMPLE_RATE, "0");
                extract(cv, metadata, AudioColumns.ARCHOS_NUMBER_OF_CHANNELS, IMediaMetadataRetriever.METADATA_KEY_NUMBER_OF_CHANNELS, "0");
                extract(cv, metadata, AudioColumns.ARCHOS_AUDIO_WAVE_CODEC, IMediaMetadataRetriever.METADATA_KEY_AUDIO_WAVE_CODEC, "0");
                extract(cv, metadata, AudioColumns.ARCHOS_AUDIO_BITRATE, IMediaMetadataRetriever.METADATA_KEY_AUDIO_BITRATE, "0");
                extract(cv, metadata, AudioColumns.COMPOSER, IMediaMetadataRetriever.METADATA_KEY_COMPOSER, null);
                extract(cv, metadata, "is_drm", IMediaMetadataRetriever.METADATA_KEY_IS_DRM, null);
                extract(cv, metadata, AudioColumns.DURATION, IMediaMetadataRetriever.METADATA_KEY_DURATION, null);
                extract(cv, metadata, AudioColumns.TRACK, IMediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER, null);
                extract(cv, metadata, AudioColumns.YEAR, IMediaMetadataRetriever.METADATA_KEY_DATE, null, true);
                extract(cv, metadata, FileColumns.TITLE, IMediaMetadataRetriever.METADATA_KEY_TITLE, defaultTitle);
                break;
        }
        return cv;
    }

    /** helper to extract metadate key into ContentValues if that key is != null */
    private static void extract(ContentValues target, MediaMetadata metadata,
            String cvKey, int retrieverKey, String defaultValue) {
        extract(target, metadata, cvKey, retrieverKey, defaultValue, false);
    }

    /** helper to extract metadate key into ContentValues if that key is != null */
    private static void extract(ContentValues target, MediaMetadata metadata,
            String cvKey, int retrieverKey, String defaultValue, boolean treat0AsNull) {
        if (metadata.has(retrieverKey)) {
            String val = metadata.getString(retrieverKey);
            if (treat0AsNull && !isNumberOtherThanZero(val)) {
                target.put(cvKey, defaultValue);
            } else {
                target.put(cvKey, val);
            }
        } else {
            target.put(cvKey, defaultValue);
        }
    }

    private static boolean isNumberOtherThanZero(String input) {
        if (input == null || "0".equals(input)) return false;
        try {
            return Long.parseLong(input) != 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String getDefaultTitle(String path) {
        String ret = new File(path).getName();
        int lastDot = ret.lastIndexOf('.');
        if (lastDot > 0)
            ret = ret.substring(0, lastDot);
        return ret;
    }

    private static final String NOT_NETWORKINDEXED = "storage_id < " + ArchosMediaCommon.LIGHT_INDEX_MIN_STORAGE_ID + " AND _data NOT NULL AND _data != ''";
    private static final String WHERE_MIN_ID = NOT_NETWORKINDEXED + " AND _id > ?";
    private static final String WHERE_ALL = NOT_NETWORKINDEXED;
    private final static String[] FILES_PROJECTION = new String[] {
        BaseColumns._ID,
        MediaColumns.DATA,
        MediaColumns.DISPLAY_NAME,
        MediaColumns.SIZE,
        MediaColumns.DATE_ADDED,
        MediaColumns.DATE_MODIFIED,
        ImageColumns.BUCKET_ID,
        ImageColumns.BUCKET_DISPLAY_NAME,
        "format",
        FileColumns.PARENT,
        "storage_id"
    };
    /** copies data from Android's media db to ours */
    private static int copyData (ContentResolver cr, String minId) {
        int imported = 0;
        String where = WHERE_ALL;
        String[] whereArgs = null;
        if (minId != null)  {
            where = WHERE_MIN_ID;
            whereArgs = new String[] { minId };
        }
        Cursor allFiles = CustomCursor.wrap(cr.query(MediaStore.Files.getContentUri("external"),
                FILES_PROJECTION, where, whereArgs, BaseColumns._ID));
        if (allFiles != null) {
            int count = allFiles.getCount();
            int ccount = allFiles.getColumnCount();
            if (count > 0) {
                // transaction size limited, acts like buffered output stream and auto-flushes queue
                BulkInserter inserter = new BulkInserter(MusicStoreInternal.FILES_IMPORT, cr, 2000);
                if (DBG) Log.d(TAG, "found items to import:" + count);
                while (allFiles.moveToNext()) {
                    ContentValues cv = new ContentValues(ccount);
                    DatabaseUtils.cursorRowToContentValues(allFiles, cv);
                    inserter.add(cv);
                }
                imported = inserter.execute();
            }
            allFiles.close();
        }
        return imported;
    }

    /** helper to find the max of local_id (= newest file) in our db */
    private static final String[] MAX_LOCAL_PROJ = new String[] { "max(local_id)" };
    private static String getMaxLocalId (ContentResolver cr) {
        Cursor c = cr.query(MusicStoreInternal.FILES_IMPORT, MAX_LOCAL_PROJ, null, null, null);
        String result = null;
        if (c != null) {
            if (c.moveToFirst()) {
                result = c.getString(0);
            }
            c.close();
        }
        return TextUtils.isEmpty(result) ? "0" : result;
    }

    /**
     * helper to get count of imported files
     * TODO remove if debug no longer required.
     */
    private static final String[] COUNT_PROJ = new String[] { "count(*)" };
    private static String getLocalCount (ContentResolver cr) {
        Cursor c = cr.query(MusicStoreInternal.FILES_IMPORT, COUNT_PROJ, null, null, null);
        String result = null;
        if (c != null) {
            if (c.moveToFirst()) {
                result = c.getString(0);
            }
            c.close();
        }
        return TextUtils.isEmpty(result) ? "0" : result;
    }

    private static final String[] MAX_ID_PROJ = new String[] { "max(_id)" };
    /** helper to find the max of _id (=newest in android's db) in our db */
    private static String getMaxId (ContentResolver cr) {
        Cursor c = cr.query(MusicStoreInternal.FILES_IMPORT, MAX_ID_PROJ, null, null, null);
        String result = null;
        if (c != null) {
            if (c.moveToFirst()) {
                result = c.getString(0);
            }
            c.close();
        }
        return TextUtils.isEmpty(result) ? "0" : result;
    }

    private final static String[] REMOTE_LIST_PROJECTION = new String[] {
        "group_concat(" + BaseColumns._ID + ")"
    };
    /** helper to get a comma separated list of all ids */
    private static String getRemoteIdList (ContentResolver cr) {
        Cursor c = cr.query(MediaStore.Files.getContentUri("external"), REMOTE_LIST_PROJECTION, null, null, null);
        String result = null;
        if (c != null) {
            if (c.moveToFirst()) {
                result = c.getString(0);
            }
            c.close();
        }
        return TextUtils.isEmpty(result) ? "" : result;
    }

    /** adjusted copy of {@link MediaScanner#isNoMediaPath(String)} */
    protected static boolean isNoMediaPath(String path) {
        if (path == null) return false;

        // return true if file or any parent directory has name starting with a dot
        if (path.indexOf("/.") >= 0) return true;

        // TODO: determine if this method needs to be fully implemented to work with smb://
        if (path.startsWith("smb://")) {
            Log.w(TAG, "isNoMediaPath not fully checking " + path);
            return false;
        }

        // now check to see if any parent directories have a ".nomedia" file
        // start from 1 so we don't bother checking in the root directory
        int offset = 1;
        while (offset >= 0) {
            int slashIndex = path.indexOf('/', offset);
            // Archos NOTE: here must be >= instead of >
            if (slashIndex >= offset) {
                slashIndex++; // move past slash
                File file = new File(path.substring(0, slashIndex) + ".nomedia");
                if (file.exists()) {
                    // we have a .nomedia in one of the parent directories
                    return true;
                }
            }
            offset = slashIndex;
        }
        return isNoMediaFile(path);
    }

    private static boolean isNoMediaFile(String path) {
        File file = new File(path);
        if (file.isDirectory()) return false;

        // special case certain file names
        // I use regionMatches() instead of substring() below
        // to avoid memory allocation
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash + 2 < path.length()) {
            // ignore those ._* files created by MacOS
            if (path.regionMatches(lastSlash + 1, "._", 0, 2)) {
                return true;
            }

            /* Archos: No need to check for images
            // ignore album art files created by Windows Media Player:
            // Folder.jpg, AlbumArtSmall.jpg, AlbumArt_{...}_Large.jpg
            // and AlbumArt_{...}_Small.jpg
            if (path.regionMatches(true, path.length() - 4, ".jpg", 0, 4)) {
                if (path.regionMatches(true, lastSlash + 1, "AlbumArt_{", 0, 10) ||
                        path.regionMatches(true, lastSlash + 1, "AlbumArt.", 0, 9)) {
                    return true;
                }
                int length = path.length() - lastSlash - 1;
                if ((length == 17 && path.regionMatches(
                        true, lastSlash + 1, "AlbumArtSmall", 0, 13)) ||
                        (length == 10
                         && path.regionMatches(true, lastSlash + 1, "Folder", 0, 6))) {
                    return true;
                }
            }
            */
        }
        /* Archos: No need to check for images
        // ignores images inside Music directory (in order to don't spam gallery with music cover)
        if (path.startsWith(MUSIC_STORAGE_PATH)) {
            MediaFileType type = MediaFile.getFileType(path);
            if (type != null && MediaFile.isImageFileType(type.fileType))
                return true;
        }
        */
        return false;
    }
}
