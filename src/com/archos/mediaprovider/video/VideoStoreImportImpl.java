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

package com.archos.mediaprovider.video;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteBlobTooBigException;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.MediaStore.Files.FileColumns;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.MediaColumns;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.archos.filecorelibrary.FileEditor;
import com.archos.mediacenter.filecoreextension.upnp2.FileEditorFactoryWithUpnp;
import com.archos.mediacenter.utils.trakt.TraktService;
import com.archos.medialib.IMediaMetadataRetriever;
import com.archos.medialib.MediaMetadata;
import com.archos.mediaprovider.ArchosMediaCommon;
import com.archos.mediaprovider.ArchosMediaFile;
import com.archos.mediaprovider.ArchosMediaFile.MediaFileType;
import com.archos.mediaprovider.BulkInserter;
import com.archos.mediaprovider.CPOExecutor;
import com.archos.mediaprovider.CustomCursorFactory.CustomCursor;
import com.archos.mediaprovider.ImportState;
import com.archos.mediaprovider.ImportState.State;
import com.archos.mediaprovider.MediaRetrieverServiceClient;
import com.archos.mediaprovider.video.VideoStore.Video.VideoColumns;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.NfoParser;

import java.io.File;

/**
 * The media db import logic
 */
public class VideoStoreImportImpl {
    private static final String TAG =  ArchosMediaCommon.TAG_PREFIX + "VideoStoreImportImpl";
    private static final boolean LOCAL_DBG = false;
    private static final boolean DBG = ArchosMediaCommon.PACKAGE_DBG & LOCAL_DBG;
    private static final boolean DBG2 = false;

    private final Context mContext;
    private final ContentResolver mCr;
    private final Blacklist mBlackList;
    private final MediaRetrieverServiceClient mMediaRetrieverServiceClient;

    public VideoStoreImportImpl(Context context) {
        mContext = context;
        mCr = mContext.getContentResolver();
        mBlackList = Blacklist.getInstance(context);
        mMediaRetrieverServiceClient = new MediaRetrieverServiceClient(context);
    }

    public void destroy() {
        mMediaRetrieverServiceClient.unbindAndDestroy();
    }

    public void doFullImport() {
        int countStart = getLocalCount(mCr);
        ImportState.VIDEO.setState(countStart == 0 ? State.INITIAL_IMPORT : State.REGULAR_IMPORT);

        // save max(local_id)
        String maxLocalId = getMaxLocalId(mCr);

        // replace everything with new data
        int copy = copyData(mCr, null);

        // delete everything that was not replaced, ! only if it is on primary local storage !
        String[] selectionArgs = new String[] { maxLocalId };
        int del = 0;

        // set files not seen to hidden state. They might be deleted but we don't know for sure.
        ContentValues cv = new ContentValues();
        cv.put("volume_hidden", Long.valueOf(System.currentTimeMillis() / 1000));
        mCr.update(VideoStoreInternal.FILES_IMPORT, cv, "local_id <= ?", selectionArgs);

        int countEnd = getLocalCount(mCr);
        Log.d(TAG, "full import +:" + copy + " -:" + del + " " + countStart + "=>" + countEnd);

        // then trigger scan of new data
        doScan(mCr, mContext, mBlackList);

        ImportState.VIDEO.setState(State.IDLE);
    }

    public void doIncrementalImport() {
        int countStart = getLocalCount(mCr);
        ImportState.VIDEO.setState(countStart == 0 ? State.INITIAL_IMPORT : State.REGULAR_IMPORT);

        String existingFiles = getRemoteIdList(mCr);
        int del = 0;

        // 1. Hide files that are currently not visible, they might be removed at that point but we don't know for sure.
        ContentValues cv = new ContentValues();
        cv.put("volume_hidden", Long.valueOf(System.currentTimeMillis() / 1000));
        mCr.update(VideoStoreInternal.FILES_IMPORT, cv, "_id NOT IN (" + existingFiles + ")", null);

        // 2. copy all remote files with higher id than our max id
        String maxLocal = getMaxId(mCr);
        int copy = copyData(mCr, maxLocal);
        int countEnd = getLocalCount(mCr);
        Log.d(TAG, "part import +:" + copy + " -:" + del + " " + countStart + "=>" + countEnd);
        // then trigger scan of new data
        doScan(mCr, mContext, mBlackList);

        ImportState.VIDEO.setState(State.IDLE);
    }

    private static final String[] ID_DATA_PROJ = new String[] {
        BaseColumns._ID,
        MediaColumns.DATA,
        VideoColumns.ARCHOS_MEDIA_SCRAPER_ID
    };
    private static final String UPDATE_WHERE = "remote_id=?";
    /** scans every file in cursor and update database, also closes cursor */
    private void handleScanCursor(Cursor c, ContentResolver cr, Context context, Blacklist blacklist) {
        if (c == null || c.getCount() == 0) {
            if (c != null) c.close();
            if (DBG) Log.d(TAG, "no media to scan");
            return;
        }

        int scanned = 0;
        int scraped = 0;
        int remaining = c.getCount();

        CPOExecutor operations = new CPOExecutor(VideoStore.AUTHORITY, cr, 500);
        long time = System.currentTimeMillis() / 1000L;
        String timeString = String.valueOf(time);


        NfoParser.ImportContext importContext = new NfoParser.ImportContext();

        try {
            while (c.moveToNext()) {
                ImportState.VIDEO.setRemainingCount(remaining--);
                String id;
                String path;
                int scraperID;
                try {
                    id = c.getString(0);
                    path = c.getString(1);
                    if (path.startsWith("/"))
                        path = "file://" + path;
                    scraperID = c.getInt(2);
                } catch (IllegalStateException ignored) {
                    //we silently ignore empty lines - it means content has been deleted while scanning
                    continue;
                }
                Job job = new Job(path, id, blacklist);
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
                        ContentProviderOperation.newUpdate(VideoStoreInternal.FILES)
                                .withSelection(UPDATE_WHERE, new String[]{job.mId})
                                .withValues(cv)
                                .build()
                );
                scanned++;

                // .nfo auto-parsing
                if (job.mRetrieve && scraperID <= 0) {
                    Uri videoFile = job.mPath;
                    if (videoFile != null) {
                        if (DBG) Log.d(TAG, "searching .nfo for " + videoFile);
                        NfoParser.NfoFile nfo = NfoParser.determineNfoFile(videoFile);
                        if (nfo != null && nfo.hasNfo()) {
                            if (DBG)
                                Log.d(TAG, ".nfo found for " + videoFile + " : " + nfo.videoNfo);
                            BaseTags tagForFile = NfoParser.getTagForFile(nfo, context, importContext);
                            if (tagForFile != null) {
                                if (DBG)
                                    Log.d(TAG, ".nfo contains valid BaseTags for " + videoFile);
                                long videoId = parseLong(job.mId, -1);
                                if (videoId > 0) {
                                    tagForFile.save(context, videoId);
                                    if (DBG) Log.d(TAG, "BaseTags saved for " + videoFile);
                                    scraped++;
                                }
                            }
                        }
                    }
                }

                if (job.mMediaType == FileColumns.MEDIA_TYPE_VIDEO) {
                    /* Process the FileName for more information */
                    ContentValues cvExtra = VideoNameProcessor.extractValuesFromPath(path);
                    operations.add(
                            ContentProviderOperation.newUpdate(VideoStoreInternal.FILES)
                                    .withSelection(UPDATE_WHERE, new String[]{job.mId})
                                    .withValues(cvExtra)
                                    .build()
                    );
                }
            }
        } catch(SQLiteBlobTooBigException e) {
            Log.w(TAG, "handleScanCursor caught SQLiteBlobTooBigException in c.moveToNext() " + "media scanned:" + scanned + " nfo-scraped:" + scraped + " remaining:" + remaining);
            Toast.makeText(context, "SQLiteBlobTooBigException VideoStoreImportImpl, " +
                    "scanned:" + scanned + "  scraped:" + scraped + " remaining:" + remaining, Toast.LENGTH_LONG).show();
        }

        c.close();
        operations.execute();
        Log.d(TAG, "media scanned:" + scanned + " nfo-scraped:" + scraped);
        if (scraped > 0)
            TraktService.onNewVideo(context);
    }

    private static class Job {
        public Job(String path, String id, Blacklist blacklist) {
            mPath = Uri.parse(path);
            mId = id;
            mMft = ArchosMediaFile.getFileType(path);
            // default mime type / media type
            int mediaType = FileColumns.MEDIA_TYPE_NONE;
            String mimeType = "application/octet-stream";
            boolean retrieve = false;
            if (mMft != null) {
                mimeType = mMft.mimeType;
                if (!isNoMediaPath(path) && !blacklist.isBlacklisted(mPath)) {
                    if (ArchosMediaFile.isAudioFileType(mMft.fileType)) {
                        mediaType = FileColumns.MEDIA_TYPE_AUDIO;
                    } else if (ArchosMediaFile.isVideoFileType(mMft.fileType)) {
                        mediaType = FileColumns.MEDIA_TYPE_VIDEO;
                        retrieve = true;
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

        public final Uri mPath;
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
        int deleted = mCr.delete(VideoStoreInternal.FILES_IMPORT, where, new String[]{path});
        Log.d(TAG, "removed:" + deleted);
    }

    private static String WHERE_PATH = "_data LIKE ?||'/%'";
    private static String WHERE_FILE = "_data = ?";
    /** executes metadata scan of files defined by Uri */
    public void doScan(Uri what) {
        String path = what.getPath();

        if (path.endsWith("/"))
            path = path.substring(0, path.length() - 1);

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
        Cursor c = mCr.query(VideoStoreInternal.FILES, ID_DATA_PROJ, where, new String[]{ path }, null);
        handleScanCursor(c, mCr, mContext, mBlackList);
    }

    private static final String SELF_NEEDS_SCAN = "scan_state < date_modified";
    private static final String DIRECTORY_NEEDS_SCAN = "scan_state < (select date_modified from files as files_parent where files_parent._id = files.parent)";
    private static final String WHERE_UNSCANNED = "scan_state >= 0 AND ("
            + SELF_NEEDS_SCAN
            + " OR "
            + DIRECTORY_NEEDS_SCAN
            + ") AND _id < " + VideoOpenHelper.SCANNED_ID_OFFSET;
    /** executes metadata scan of every unscanned file */
    private void doScan(ContentResolver cr, Context context, Blacklist blacklist) {
        Cursor c = cr.query(VideoStoreInternal.FILES, ID_DATA_PROJ, WHERE_UNSCANNED, null, null);
        handleScanCursor(c, cr, context, blacklist);
    }

    /** get MediaMetadata object or null for a path */
    private MediaMetadata getMetadata(String path) throws RemoteException, InterruptedException, MediaRetrieverServiceClient.ServiceManagementException {
        if(path.startsWith("file://")) {
            path = path.substring("file://".length()); //we need to remove "file://"
        }
        return mMediaRetrieverServiceClient.getMetadata(path);
    }

    /** creates ContentValues via MediaRetrieverService, can't be null */
    private ContentValues fromRetrieverService(Job job, String timeString) throws InterruptedException, MediaRetrieverServiceClient.ServiceManagementException {
        ContentValues cv = new ContentValues();
        String path = job.mPath.toString();
        // tell mediaprovider that this update originates from here.
        cv.put(VideoStoreInternal.KEY_SCANNER, "1");
        // also put the path here so MediaProvider knows which file it is
        cv.put(MediaColumns.DATA, path);
        cv.put(BaseColumns._ID, job.mId);

        String defaultTitle = getDefaultTitle(path);
        cv.put(FileColumns.TITLE, defaultTitle);
        cv.put(MediaColumns.MIME_TYPE, job.mMimeType);
        cv.put(FileColumns.MEDIA_TYPE, String.valueOf(job.mMediaType));
        cv.put(VideoStoreInternal.FILES_EXTRA_COLUMN_SCAN_STATE, timeString);

        // try to get metadata if file is a mediafile
        MediaMetadata metadata = null;
        if (job.mRetrieve) {
            try {
                metadata = getMetadata(path);
            } catch (RemoteException e) {
                Log.w(TAG, "Blacklisting file because it killed metadata service:" + path);
                cv.put(VideoStoreInternal.FILES_EXTRA_COLUMN_SCAN_STATE, String.valueOf(VideoStoreInternal.SCAN_STATE_SCAN_FAILED));
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
            case FileColumns.MEDIA_TYPE_VIDEO:
                extract(cv, metadata, VideoColumns.ARCHOS_ENCODING_PROFILE, IMediaMetadataRetriever.METADATA_KEY_ENCODING_PROFILE, "0");
                extract(cv, metadata, VideoColumns.ARCHOS_FRAMES_PER_THOUSAND_SECONDS, IMediaMetadataRetriever.METADATA_KEY_FRAMES_PER_THOUSAND_SECONDS, "0");
                extract(cv, metadata, VideoColumns.ARCHOS_NUMBER_OF_AUDIO_TRACKS, IMediaMetadataRetriever.METADATA_KEY_NB_AUDIO_TRACK, "-1");
                extract(cv, metadata, VideoColumns.ARCHOS_NUMBER_OF_SUBTITLE_TRACKS, IMediaMetadataRetriever.METADATA_KEY_NB_SUBTITLE_TRACK, "-1");
                extract(cv, metadata, VideoColumns.ARCHOS_VIDEO_BITRATE, IMediaMetadataRetriever.METADATA_KEY_VIDEO_BITRATE, "0");
                extract(cv, metadata, VideoColumns.ARCHOS_VIDEO_FOURCC_CODEC, IMediaMetadataRetriever.METADATA_KEY_VIDEO_FOURCC_CODEC, "0");
                extract(cv, metadata, MediaColumns.HEIGHT, IMediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT, "0");
                extract(cv, metadata, MediaColumns.WIDTH, IMediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH, "0");
                extract(cv, metadata, VideoColumns.DURATION, IMediaMetadataRetriever.METADATA_KEY_DURATION, "0");
                extract(cv, metadata, VideoColumns.ARCHOS_SAMPLERATE, IMediaMetadataRetriever.METADATA_KEY_SAMPLE_RATE, "0");
                extract(cv, metadata, VideoColumns.ARCHOS_NUMBER_OF_CHANNELS, IMediaMetadataRetriever.METADATA_KEY_NUMBER_OF_CHANNELS, "0");
                extract(cv, metadata, VideoColumns.ARCHOS_AUDIO_WAVE_CODEC, IMediaMetadataRetriever.METADATA_KEY_AUDIO_WAVE_CODEC, "0");
                extract(cv, metadata, VideoColumns.ARCHOS_AUDIO_BITRATE, IMediaMetadataRetriever.METADATA_KEY_AUDIO_BITRATE, "0");
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

    private static long parseLong(String input, long fallback) {
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String getDefaultTitle(String path) {
        String ret = new File(path).getName();
        int lastDot = ret.lastIndexOf('.');
        if (lastDot > 0)
            ret = ret.substring(0, lastDot);
        return ret;
    }

    private static final String NOT_NETWORKINDEXED_BP = "storage_id & 1 AND _data NOT NULL AND _data != ''";
    private static final String NOT_NETWORKINDEXED_AP = "_data NOT NULL AND _data != ''";
    private static final String WHERE_MIN_ID_BP = NOT_NETWORKINDEXED_BP + " AND _id > ?";
    private static final String WHERE_MIN_ID_AP = NOT_NETWORKINDEXED_AP + " AND _id > ?";
    private static final String WHERE_ALL_BP = NOT_NETWORKINDEXED_BP;
    private static final String WHERE_ALL_AP = NOT_NETWORKINDEXED_AP;
    //storage_id does not exist in Android P causing a crash
    //two versions: before Android P (BP) and after Android P (AP)
    private final static String[] FILES_PROJECTION_AP = new String[]{
            BaseColumns._ID,
            MediaColumns.DATA,
            MediaColumns.DISPLAY_NAME,
            MediaColumns.SIZE,
            MediaColumns.DATE_ADDED,
            MediaColumns.DATE_MODIFIED,
            ImageColumns.BUCKET_ID,
            ImageColumns.BUCKET_DISPLAY_NAME,
            "format",
            FileColumns.PARENT
    };
    private final static String[] FILES_PROJECTION_BP = new String[]{
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
        String where = null;
        String[] whereArgs = null;
        Cursor allFiles = null;
        ContentValues cv = null;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            where = WHERE_ALL_AP;
            if (minId != null) {
                where = WHERE_MIN_ID_AP;
                whereArgs = new String[]{minId};
            }
            allFiles = CustomCursor.wrap(cr.query(MediaStore.Files.getContentUri("external"),
                    FILES_PROJECTION_AP, where, whereArgs, BaseColumns._ID));
        } else {
            where = WHERE_ALL_BP;
            if (minId != null)  {
                where = WHERE_MIN_ID_BP;
                whereArgs = new String[] { minId };
            }
            allFiles = CustomCursor.wrap(cr.query(MediaStore.Files.getContentUri("external"),
                    FILES_PROJECTION_BP, where, whereArgs, BaseColumns._ID));
        }
        if (allFiles != null) {
            int count = allFiles.getCount();
            int ccount = allFiles.getColumnCount();
            if (count > 0) {
                // transaction size limited, acts like buffered output stream and auto-flushes queue
                BulkInserter inserter = new BulkInserter(VideoStoreInternal.FILES_IMPORT, cr, 2000);
                if (DBG) Log.d(TAG, "found items to import:" + count);
                while (allFiles.moveToNext()) {
                    try {
                        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
                            cv = new ContentValues(ccount + 1);
                            DatabaseUtils.cursorRowToContentValues(allFiles, cv);
                            // since storage_id does not exist on P and above, set it to 1, not sure it is really used now
                            cv.put("storage_id", 1);
                        } else {
                            cv = new ContentValues(ccount);
                            DatabaseUtils.cursorRowToContentValues(allFiles, cv);
                        }
                        inserter.add(cv);
                    } catch (IllegalStateException ignored) {} //we silently ignore empty lines - it means content has been deleted while scanning
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
        Cursor c = cr.query(VideoStoreInternal.FILES_IMPORT, MAX_LOCAL_PROJ, null, null, null);
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
     */
    private static final String[] COUNT_PROJ = new String[] { "count(*)" };
    private static int getLocalCount (ContentResolver cr) {
        Cursor c = cr.query(VideoStoreInternal.FILES_IMPORT, COUNT_PROJ, null, null, null);
        int result = 0;
        if (c != null) {
            if (c.moveToFirst()) {
                result = c.getInt(0);
            }
            c.close();
        }
        return result;
    }

    private static final String[] MAX_ID_PROJ = new String[] { "max(_id)" };
    /** helper to find the max of _id (=newest in android's db) in our db */
    private static String getMaxId (ContentResolver cr) {
        Cursor c = cr.query(VideoStoreInternal.FILES_IMPORT, MAX_ID_PROJ, null, null, null);
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
        BaseColumns._ID
    };
    /** helper to get a comma separated list of all ids */
    private static String getRemoteIdList (ContentResolver cr) {
        Cursor c = cr.query(MediaStore.Files.getContentUri("external"), REMOTE_LIST_PROJECTION, null, null, null);
        StringBuilder sb = new StringBuilder();
        String prefix = "";
        if (c != null) {
            c.moveToFirst();
            try {
                while (!c.isAfterLast()) {
                    sb.append(prefix).append(c.getString(0));
                    prefix = ",";
                    c.moveToNext();
                }
            } catch(SQLiteBlobTooBigException e) {
                Log.w(TAG, "getRemoteIdList caught SQLiteBlobTooBigException in c.moveToNext()");
            }
            c.close();
        }
        String result = sb.toString();
        return TextUtils.isEmpty(result) ? "" : result;
    }


    public static boolean isNoMediaPath(Uri uri) {
        String path = uri.toString();
        if (path == null) return false;

        // return true if file or any parent directory has name starting with a dot
        if (path.indexOf("/.") >= 0) return true;

        // now check to see if any parent directories have a ".nomedia" file
        // start from 1 so we don't bother checking in the root directory
        int offset = Uri.parse(path).getScheme()!=null?Uri.parse(path).getScheme().length()+3:1;//go after smb://
        while (offset >= 0) {
            int slashIndex = path.indexOf('/', offset);
            // Archos NOTE: here must be >= instead of >
            if (slashIndex >= offset) {
                slashIndex++; // move past slash
                Uri file = Uri.parse(path.substring(0, slashIndex) + ".nomedia");

                FileEditor fe = FileEditorFactoryWithUpnp.getFileEditorForUrl(file, null);
                if (fe.exists()) {
                    // we have a .nomedia in one of the parent directories
                    return true;
                }
            }
            offset = slashIndex;
        }
        return false;
    }
    /** adjusted copy of {@link MediaScanner#isNoMediaPath(String)} */
    public static boolean isNoMediaPath(String path) {
        Uri uri = Uri.parse(path);
        if(uri.getScheme()!=null) //removing file://
            path = uri.getPath();
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
