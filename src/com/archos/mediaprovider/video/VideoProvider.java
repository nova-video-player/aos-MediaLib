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

import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;

import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.filecoreextension.upnp2.FileEditorFactoryWithUpnp;
import com.archos.mediacenter.filecoreextension.upnp2.UpnpServiceManager;
import com.archos.mediacenter.utils.AppState;
import com.archos.medialib.IMediaMetadataRetriever;
import com.archos.medialib.MediaFactory;
import com.archos.mediaprovider.ArchosMediaCommon;
import com.archos.mediaprovider.DbHolder;
import com.archos.mediaprovider.IMediaThumbnailService;
import com.archos.mediaprovider.MediaRetrieverService;
import com.archos.mediaprovider.MediaThumbnailService;
import com.archos.mediaprovider.NetworkState;
import com.archos.mediaprovider.video.VideoStore.MediaColumns;
import com.archos.mediaprovider.video.VideoStore.Video;
import com.archos.mediaprovider.video.VideoStore.Files.FileColumns;
import com.archos.mediaprovider.video.VideoStore.Video.VideoColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

public class VideoProvider extends ContentProvider {
    private static final String TAG =  ArchosMediaCommon.TAG_PREFIX + "VideoProvider";
    public static final String TAG_DOCTOR_WHO =  "DoctorWhoDebug";

    private static final boolean LOCAL_DBG = false;
    private static final boolean DBG = false;
    private static final boolean DBG_NET = false; // network state handling

    private DbHolder mDbHolder;
    private Handler mThumbHandler;
    private VobHandler mVobHandler;
    private ScraperProvider mScraperProvider;
    private OnSharedPreferenceChangeListener mPreferencechChangeListener;

    private static final int IMAGE_THUMB = 2;
    // yes max retry of 5 is not enough I saw it fail and succeed at 7...
    private static final int THUMB_TRY_MAX = 10    ;
    private ContentResolver mCr;

    private static final int LIGHT_INDEX_STORAGE_MIN_ID = ArchosMediaCommon.LIGHT_INDEX_MIN_STORAGE_ID;

    private static final String LIGHT_INDEX_STORAGE_QUERY = "SELECT " + BaseColumns._ID +
            " FROM files WHERE " + BaseColumns._ID + "=?";// AND storage_id<" + LIGHT_INDEX_STORAGE_MIN_ID;

    /** place for (video) image thumbs */
    private String mImageThumbFolder;

    private static final String IMAGE_THUMB_FOLDER_NAME = "image_thumbs";
    public static final String PREFERENCE_CREATE_REMOTE_THUMBS = "pref_create_remote_thumbs";

    public VideoProvider() {
    }

    @Override
    public boolean onCreate() {
        if (DBG) Log.d(TAG, "onCreate");
        final Context context = getContext();
        mImageThumbFolder = context.getDir(IMAGE_THUMB_FOLDER_NAME, Context.MODE_PRIVATE).getPath();

        mVobHandler = new VobHandler(context);
        mDbHolder = new DbHolder(new VideoOpenHelper(context));

        mCr = context.getContentResolver();
        // implementation that handles scraper requests
        mScraperProvider = new ScraperProvider(context, mDbHolder);
        mPreferencechChangeListener =  new OnSharedPreferenceChangeListener() {
            
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if ("vpn_mobile".equals(key)) {
                    RemoteStateService.start(context);
                }
            }
        };
        PreferenceManager.getDefaultSharedPreferences(context).registerOnSharedPreferenceChangeListener(mPreferencechChangeListener);

        try {
            VideoStoreImportService.start(context);
        }catch(java.lang.IllegalStateException e){

        }
        // handles connectivity changes
        AppState.addOnForeGroundListener(mForeGroundListener);
        handleForeGround(AppState.isForeGround());

        HandlerThread ht = new HandlerThread("thumbs thread", Process.THREAD_PRIORITY_BACKGROUND);
        ht.start();
        mThumbHandler = new Handler(ht.getLooper()) {
            private static final int HDD_MEDIAPROVIDER_TIMEOUT = 25;
            private static final int HDD_MEDIAPROVIDER_DELAY = (HDD_MEDIAPROVIDER_TIMEOUT + 2) * 1000;

            @Override
            public void handleMessage(Message msg) {
                if (msg.what == IMAGE_THUMB) {
                    synchronized (mMediaThumbQueue) {
                        mCurrentThumbRequest = mMediaThumbQueue.poll();
                    }
                    if (mCurrentThumbRequest == null) {
                        Log.w(TAG, "Have message but no request?");
                    } else {
                        try {
                            // In the past "uri needs to be encoded to check if file exists : fixes thumbnail creation with non ascii names".
                            // However this breaks thumbs generation on smb:// when dealing with file names with spaces that are turned into %20 making the file not found.
                            // Limit uri encoding to upnp.
                            FileEditor editor;
                            if (mCurrentThumbRequest.mPath.startsWith("upnp"))
                                editor = FileEditorFactoryWithUpnp.getFileEditorForUrl(FileUtils.encodeUri(Uri.parse(mCurrentThumbRequest.mPath)), null);
                            else
                                editor = FileEditorFactoryWithUpnp.getFileEditorForUrl(Uri.parse(mCurrentThumbRequest.mPath), null);
                            if(DBG)
                                Log.d(TAG_DOCTOR_WHO,mCurrentThumbRequest.mPath+" does file exists ? "+ String.valueOf(editor.exists()));

                            if (editor.exists()) {
                                Log.d(TAG,"mCurrentThumbRequest");
                                mCurrentThumbRequest.execute();
                            } else {
                                // original file hasn't been stored yet
                                synchronized (mMediaThumbQueue) {
                                    Log.w(TAG, "original file hasn't been stored yet: " + mCurrentThumbRequest.mPath);
                                }
                            }
                        } catch (IOException ex) {
                            Log.w(TAG, ex);
                        } catch (UnsupportedOperationException ex) {
                            // This could happen if we unplug the sd card during insert/update/delete
                            // See getDatabaseForUri.
                            Log.w(TAG, ex);
                        } catch (OutOfMemoryError err) {
                            /*
                             * Note: Catching Errors is in most cases considered
                             * bad practice. However, in this case it is
                             * motivated by the fact that corrupt or very large
                             * images may cause a huge allocation to be
                             * requested and denied. The bitmap handling API in
                             * Android offers no other way to guard against
                             * these problems than by catching OutOfMemoryError.
                             */
                            Log.w(TAG, err);
                        } finally {
                            synchronized (mCurrentThumbRequest) {
                                mCurrentThumbRequest.mState = MediaThumbRequest.State.DONE;
                                mCurrentThumbRequest.notifyAll();
                            }
                        }
                    }
                }
            }
        };
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projectionIn, String selection, String[] selectionArgs,
            String sort) {
        if (DBG) Log.d(TAG, "QUERY " + uri);
        int table = URI_MATCHER.match(uri);

        // let ScraperProvider handle that
        if (ScraperProvider.handles(table))
            return mScraperProvider.query(uri, projectionIn, selection, selectionArgs, sort);

        SQLiteDatabase db = mDbHolder.get();

        // forward raw query requests to .rawQuery using selection as sql string
        if (table == RAWQUERY) {
            Cursor c = db.rawQuery(selection, selectionArgs);
            if (c != null) {
                // notify for any change in the db
                c.setNotificationUri(mCr, VideoStore.ALL_CONTENT_URI);
            }
            return c;
        }

        String limit = uri.getQueryParameter("limit");
        String groupby = uri.getQueryParameter("group");
        String having = uri.getQueryParameter("having");


        // query our custom files tables directly
        if (table == RAW) {
            String tableName = uri.getLastPathSegment();
            return db.query(tableName, projectionIn, selection, selectionArgs, groupby, having, sort, limit);
        }

        List<String> prependArgs = new ArrayList<String>();

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        if (uri.getQueryParameter("distinct") != null) {
            qb.setDistinct(true);

        }
        boolean hasThumbnailId = false;

        switch (table) {
            case FILES_ID:
                qb.appendWhere("_id=?");
                prependArgs.add(uri.getLastPathSegment());
                //$FALL-THROUGH$
            case FILES:
                qb.setTables(VideoOpenHelper.FILES_TABLE_NAME);
                break;
            case VIDEO_MEDIA_ID:
                qb.appendWhere("_id=?");
                prependArgs.add(uri.getLastPathSegment());
                //$FALL-THROUGH$
            case VIDEO_MEDIA:
                qb.setTables(VideoOpenHelper.VIDEO_VIEW_NAME);
                break;
            case VIDEO_LIST: {
                qb.setTables(ListTables.VIDEO_LIST_TABLE);
                qb.appendWhere(VideoStore.List.Columns.ID+"=?");
                prependArgs.add(uri.getLastPathSegment());
                break;
            }
            case LIST:{
                qb.setTables(ListTables.LIST_TABLE);
                break;
            }
            case VIDEO_THUMBNAILS_ID:
                hasThumbnailId = true;
                //$FALL-THROUGH$
            case VIDEO_THUMBNAILS:
                if (!queryThumbnail(qb, uri, VideoOpenHelper.VIDEOTHUMBNAIL_TABLE_NAME, "video_id", hasThumbnailId)) {
                    return null;
                }
                break;
            case ARCHOS_SMB_SERVER_ID:
                qb.appendWhere("_id=?");
                prependArgs.add(uri.getPathSegments().get(2));
                //$FALL-THROUGH$
            case ARCHOS_SMB_SERVER:
                qb.setTables(VideoOpenHelper.SMB_SERVER_TABLE_NAME);
                break;
            case SUBS_MEDIA_ID:
                qb.appendWhere("_id=?");
                prependArgs.add(uri.getLastPathSegment());
                //$FALL-THROUGH$
            case SUBS_MEDIA:
                qb.setTables(VideoOpenHelper.SUBTITLES_TABLE_NAME);
                break;
            case SUBS_MEDIA_VIDEO_ID:
                qb.appendWhere("video_id=?");
                prependArgs.add(uri.getLastPathSegment());
                qb.setTables(VideoOpenHelper.SUBTITLES_TABLE_NAME);
                break;
            default:
                throw new IllegalStateException("Unknown Uri : " + uri);
        }
        Cursor c = qb.query(db, projectionIn, selection,
                combine(prependArgs, selectionArgs), groupby, having, sort, limit);

        if (c != null) {
            c.setNotificationUri(mCr, uri);
        }

        return c;
    }

    private static String[] combine(List<String> prepend, String[] userArgs) {
        int presize = prepend.size();
        if (presize == 0) {
            return userArgs;
        }

        int usersize = (userArgs != null) ? userArgs.length : 0;
        String [] combined = new String[presize + usersize];
        for (int i = 0; i < presize; i++) {
            combined[i] = prepend.get(i);
        }
        for (int i = 0; i < usersize; i++) {
            combined[presize + i] = userArgs[i];
        }
        return combined;
    }

    private static final String[] MIME_TYPE_PROJECTION = new String[] {
        BaseColumns._ID, // 0
        MediaColumns.MIME_TYPE, // 1
    };
    @Override
    public String getType(Uri url) {
        if (DBG) Log.d(TAG, "getType" + url);

        // determine match
        int match = URI_MATCHER.match(url);

        // let ScraperProvider handle what it can
        if (ScraperProvider.handles(match))
            return mScraperProvider.getType(url);

        // return what we can
        switch (match) {
            case VIDEO_MEDIA_ID:
            case FILES_ID:
                Cursor c = null;
                try {
                    c = query(url, MIME_TYPE_PROJECTION, null, null, null);
                    if (c != null && c.getCount() == 1) {
                        c.moveToFirst();
                        String mimeType = c.getString(1);
                        c.deactivate();
                        return mimeType;
                    }
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }
                break;

            case VIDEO_MEDIA:
                return Video.Media.CONTENT_TYPE;
        }
        throw new IllegalStateException("Unknown URL : " + url);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (DBG) Log.d(TAG, "INSRT " + uri + " PID:" + Process.myPid() + " TID:" + Process.myTid());
        int match = URI_MATCHER.match(uri);

        // let ScraperProvider handle that
        if (ScraperProvider.handles(match))
            return mScraperProvider.insert(uri, values);

        SQLiteDatabase db = mDbHolder.get();

        // insert into our custom files tables.
        if (match == RAW) {
            String table = uri.getLastPathSegment();
            long rowId = db.insert(table, null, values);
            if (rowId > 0) {
                Uri result = ContentUris.withAppendedId(uri, rowId);
                if (!db.inTransaction()) {
                    mCr.notifyChange(VideoStore.ALL_CONTENT_URI, null);
                }
                return result;
            }
            return null;
        }
        long rowId = -1;
        Uri newUri = null;
        switch (match) {
            case VIDEO_THUMBNAILS: {
                ContentValues newValues = ensureFile(values, ".jpg", mImageThumbFolder);
                rowId = db.insert(VideoOpenHelper.VIDEOTHUMBNAIL_TABLE_NAME, "_id", newValues);
                if (rowId > 0) {
                    newUri = ContentUris.withAppendedId(VideoStore.Video.Thumbnails.
                            getContentUri(uri.getPathSegments().get(0)), rowId);
                }
                break;
            }
            case ARCHOS_SMB_SERVER: {
                rowId = db.insert(VideoOpenHelper.SMB_SERVER_TABLE_NAME, BaseColumns._ID, values);
                if (rowId > 0) {
                    newUri = VideoStore.SmbServer.getContentUri(rowId);
                }
                break;
            }
            case SUBS_MEDIA: {
                rowId = db.insert(VideoOpenHelper.SUBTITLES_TABLE_NAME, BaseColumns._ID, values);
                if (rowId > 0) {
                    newUri = VideoStore.Subtitle.getContentUri(rowId);
                }
                break;
            }
            case VIDEO_LIST: {
                int listId = Integer.valueOf(uri.getLastPathSegment());
                values.put(VideoStore.VideoList.Columns.LIST_ID,listId);
                db.insertWithOnConflict(ListTables.VIDEO_LIST_TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
                newUri = uri;
                mCr.notifyChange(VideoStore.ALL_CONTENT_URI, null);
                break;
            }
            case LIST:{
                rowId = db.insert(ListTables.LIST_TABLE, null, values);
                if (rowId > 0) {
                    newUri = VideoStore.List.getListUri(rowId);
                }
                mCr.notifyChange(VideoStore.ALL_CONTENT_URI, null);
                break;
            }
            default:
                throw new IllegalStateException("Unknown Uri : " + uri);
        }
        if (newUri != null && !db.inTransaction()) {
            mCr.notifyChange(newUri, null);
        }
        return newUri;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {

        ParcelFileDescriptor pfd = null;
        int match = URI_MATCHER.match(uri);

        // let scraper handle it's part
        if (ScraperProvider.handles(match))
            return mScraperProvider.openFile(uri, mode);

        try {
            pfd = openFileHelper(uri, mode);
        } catch (FileNotFoundException ex) {
            if (mode.contains("w")) {
                // if the file couldn't be created, we shouldn't extract album art
                throw ex;
            }

            if (pfd == null) {
                throw ex;
            }
        }
        return pfd;
    }

    private static ContentValues ensureFile(ContentValues initialValues,
            String preferredExtension, String directoryName) {
        ContentValues values;
        String file = initialValues.getAsString(VideoStore.MediaColumns.DATA);
        if (TextUtils.isEmpty(file)) {
            file = generateFileName(preferredExtension, directoryName);
            values = new ContentValues(initialValues);
            values.put(VideoStore.MediaColumns.DATA, file);
        } else {
            values = initialValues;
        }

        if (!ensureFileExists(file)) {
            throw new IllegalStateException("Unable to create new file: " + file);
        }
        return values;
    }

    private static boolean ensureFileExists(String path) {
        File file = new File(path);
        if (file.exists()) {
            return true;
        }
        // we will not attempt to create the first directory in the path
        // (for example, do not create /sdcard if the SD card is not mounted)
        int secondSlash = path.indexOf('/', 1);
        if (secondSlash < 1) return false;
        String directoryPath = path.substring(0, secondSlash);
        File directory = new File(directoryPath);
        if (!directory.exists())
            return false;
        // it's possible that we cannot create the directory
        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
            Log.e(TAG, "could not create " + file.getParent());
            return false;
        }
        try {
            boolean ret = file.createNewFile();
            // file needs  to be world readable, enforce that here.
            if (ret)
                file.setReadable(true, false);
            return ret;
        } catch(IOException ioe) {
            Log.e(TAG, "File creation failed", ioe);
        }
        return false;
    }

    private static String generateFileName(String preferredExtension, String directoryName) {
        // create a random file
        String name = String.valueOf(System.currentTimeMillis());
        return directoryName + "/" + name + preferredExtension;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        if (DBG) Log.d(TAG, "DELTE " + uri);
        int match = URI_MATCHER.match(uri);

        // let ScraperProvider handle that
        if (ScraperProvider.handles(match))
            return mScraperProvider.delete(uri, selection, selectionArgs);

        SQLiteDatabase db = mDbHolder.get();

        switch (match) {
            case UriMatcher.NO_MATCH:
            case FILES:
            case FILES_ID:
            case VIDEO_MEDIA:
            case VIDEO_MEDIA_ID:
                // those must be deleted in Android's db and the result imported
                throw new IllegalStateException("delete not supported, has to be done via Android's MediaStore");
            case RAW:
                String tableName = uri.getLastPathSegment();
                int result = db.delete(tableName, selection, selectionArgs);
                if (result > 0 && !db.inTransaction()) {
                    mCr.notifyChange(VideoStore.ALL_CONTENT_URI, null);
                }
                return result;
            case VIDEO_LIST:
                selection+= " AND "+ VideoStore.VideoList.Columns.LIST_ID+" = ?";
                List<String> whereArgs = new ArrayList<String>(Arrays.asList(selectionArgs));
                whereArgs.add(uri.getLastPathSegment());
                result = db.delete(ListTables.VIDEO_LIST_TABLE, selection, whereArgs.toArray(new String[0]));
                mCr.notifyChange(VideoStore.ALL_CONTENT_URI, null);
                return result;
            case LIST:
                result = db.delete(ListTables.LIST_TABLE, selection, selectionArgs);
                mCr.notifyChange(VideoStore.ALL_CONTENT_URI, null);
                return result;
        }

        // the rest uses the usual way as in Android
        int count;

        GetTableAndWhereOutParameter tableAndWhere = sGetTableAndWhereParam.get();
        getTableAndWhere(uri, match, selection, tableAndWhere);

        count = db.delete(tableAndWhere.table, tableAndWhere.where, selectionArgs);
        if (count > 0 && !db.inTransaction())
            mCr.notifyChange(VideoStore.ALL_CONTENT_URI, null);
        return count;
    }

    @Override
    public int update(Uri uri, ContentValues initialValues, String userWhere,
            String[] whereArgs) {
        if (DBG) Log.d(TAG, "UPDTE " + uri);
        int count;
        // Log.v(TAG, "update for uri="+uri+", initValues="+initialValues);
        int match = URI_MATCHER.match(uri);

        // let ScraperProvider handle that
        if (ScraperProvider.handles(match))
            return mScraperProvider.update(uri, initialValues, userWhere, whereArgs);

        SQLiteDatabase db = mDbHolder.get();

        switch (match) {
            case RAW: {
                String tableName = uri.getLastPathSegment();
                if (VideoOpenHelper.FILES_TABLE_NAME.equals(tableName)) {
                    // if KEY_SCANNER is present that update was generated by our scanner
                    if (initialValues.containsKey(VideoStoreInternal.KEY_SCANNER)) {
                        initialValues.remove(VideoStoreInternal.KEY_SCANNER);
                    }
                    initialValues.remove(BaseColumns._ID);
                    initialValues.remove(MediaColumns.DATA);
                }
                int result = db.update(tableName, initialValues, userWhere, whereArgs);
                if (result > 0 && !db.inTransaction()) {
                    mCr.notifyChange(VideoStore.ALL_CONTENT_URI, null);
                }
                return result;
            }
            case VIDEO_LIST: {
                userWhere+= " AND "+ VideoStore.VideoList.Columns.LIST_ID+" = ?";
                List<String> whereArgs2 = new ArrayList<String>(Arrays.asList(whereArgs));
                whereArgs2.add(uri.getLastPathSegment());
                int result = db.update(ListTables.VIDEO_LIST_TABLE, initialValues, userWhere, whereArgs2.toArray(new String[0]));
                mCr.notifyChange(VideoStore.ALL_CONTENT_URI, null);
                return result;
            }
            case LIST: {
                int result = db.update(ListTables.LIST_TABLE, initialValues, userWhere, whereArgs);
                mCr.notifyChange(VideoStore.ALL_CONTENT_URI, null);
                return result;
            }
            case VIDEO_MEDIA:
            case VIDEO_MEDIA_ID:
            case VIDEO_THUMBNAILS:
            case VIDEO_THUMBNAILS_ID:
            case ARCHOS_SMB_SERVER:
            case ARCHOS_SMB_SERVER_ID:
                break; // continue below
            default:
                throw new IllegalStateException("can't update Uri" + uri);
        }

        GetTableAndWhereOutParameter tableAndWhere = sGetTableAndWhereParam.get();
        getTableAndWhere(uri, match, userWhere, tableAndWhere);
        String table = tableAndWhere.table;
        String where = tableAndWhere.where;

            switch (match) {
                case VIDEO_MEDIA:
                case VIDEO_MEDIA_ID:
                {
                    ContentValues values = new ContentValues(initialValues);
                    // Don't allow imported stuff to be updated.
                    valuesRemove(values, BaseColumns._ID);
                    valuesRemove(values, MediaColumns.DATA);
                    valuesRemove(values, MediaColumns.DISPLAY_NAME);
                    valuesRemove(values, MediaColumns.SIZE);
                    valuesRemove(values, MediaColumns.DATE_ADDED);
                    valuesRemove(values, MediaColumns.DATE_MODIFIED);
                    valuesRemove(values, VideoColumns.BUCKET_ID);
                    valuesRemove(values, VideoColumns.BUCKET_DISPLAY_NAME);
                    valuesRemove(values, VideoStore.Files.FileColumns.FORMAT);
                    valuesRemove(values, VideoStore.Files.FileColumns.PARENT);
                    valuesRemove(values, VideoStore.Files.FileColumns.STORAGE_ID);
                    if (values.size() < 1) {
                        Log.e(TAG, "no more Values, aborting update.");
                        return 0;
                    }
                    count = db.update(table, values, where, whereArgs);
                    // if this is a request from MediaScanner, DATA should contains file path
                    // we only process update request from media scanner, otherwise the requests
                    // could be duplicate.
                    if (count > 0 && values.getAsString(VideoStore.MediaColumns.DATA) != null) {
                        Cursor c = db.query(table,
                                READY_FLAG_PROJECTION, where,
                                whereArgs, null, null, null);
                        if (c != null) {
                            try {
                                while (c.moveToNext()) {
                                    long magic = c.getLong(2);
                                    if (magic == 0) {
                                        requestMediaThumbnail(c.getString(1), uri,
                                                MediaThumbRequest.PRIORITY_NORMAL, 0);
                                    }
                                }
                            } finally {
                                c.close();
                            }
                        }
                    }
                }
                break;
                default:
                    count = db.update(table, initialValues, where, whereArgs);
                    break;
            }
        // in a transaction, the code that began the transaction should be taking
        // care of notifications once it ends the transaction successfully
        if (count > 0 && !db.inTransaction()) {
            mCr.notifyChange(uri, null);
        }
        return count;
    }

    static class PipeByteWriter implements PipeDataWriter<byte[]> {
        @Override
        public void writeDataToPipe(ParcelFileDescriptor output, Uri uri, String mimeType, Bundle opts, byte[] args) {
            FileOutputStream fout = new FileOutputStream(output.getFileDescriptor());
            try {
                fout.write(args);
            } catch (IOException e) {
                Log.w(TAG, e);
            } finally {
                try {
                    fout.close();
                } catch (IOException e) {
                    // ignored
                }
            }
        }
    }

    private static final String[] READY_FLAG_PROJECTION = new String[] {
        BaseColumns._ID,
        MediaColumns.DATA,
        VideoColumns.MINI_THUMB_MAGIC
    };

    private static void valuesRemove(ContentValues cv, String what) {
        if (cv.containsKey(what)) {
            Log.e(TAG, "Removing: " + what + " since that is not supported.");
            cv.remove(what);
        }
    }

    private static final class GetTableAndWhereOutParameter {
        public GetTableAndWhereOutParameter() { /* empty */ }
        public String table;
        public String where;
    }

    static final ThreadLocal<GetTableAndWhereOutParameter> sGetTableAndWhereParam
            = new ThreadLocal<VideoProvider.GetTableAndWhereOutParameter>() {
        @Override
        protected GetTableAndWhereOutParameter initialValue() {
            return new GetTableAndWhereOutParameter();
        }
    };

    private static void getTableAndWhere(Uri uri, int match, String userWhere,
            GetTableAndWhereOutParameter out) {
        String where = null;
        switch (match) {
            case VIDEO_MEDIA:
                out.table = VideoOpenHelper.FILES_TABLE_NAME;
                where = FileColumns.MEDIA_TYPE + "=" + FileColumns.MEDIA_TYPE_VIDEO;
                break;

            case VIDEO_MEDIA_ID:
                out.table = VideoOpenHelper.FILES_TABLE_NAME;
                where = "_id=" + uri.getLastPathSegment();
                break;

            case VIDEO_THUMBNAILS_ID:
                where = "_id=" + uri.getLastPathSegment();
                //$FALL-THROUGH$
            case VIDEO_THUMBNAILS:
                out.table = VideoOpenHelper.VIDEOTHUMBNAIL_TABLE_NAME;
                break;

            case ARCHOS_SMB_SERVER_ID:
                where = "_id=" + uri.getLastPathSegment();
                //$FALL-THROUGH$
            case ARCHOS_SMB_SERVER:
                out.table = VideoOpenHelper.SMB_SERVER_TABLE_NAME;
                break;


            case FILES_ID:
            //case MTP_OBJECTS_ID:
                where = "_id=" + uri.getPathSegments().get(2);
                //$FALL-THROUGH$
            case FILES:
            //case MTP_OBJECTS:
                out.table = VideoOpenHelper.FILES_TABLE_NAME;
                break;

            default:
                throw new UnsupportedOperationException(
                        "Unknown or unsupported URL: " + uri.toString());
        }

        // Add in the user requested WHERE clause, if needed
        if (!TextUtils.isEmpty(userWhere)) {
            if (!TextUtils.isEmpty(where)) {
                out.where = where + " AND (" + userWhere + ")";
            } else {
                out.where = userWhere;
            }
        } else {
            out.where = where;
        }
    }

    // The lock of mMediaThumbQueue protects both mMediaThumbQueue and mCurrentThumbRequest.
    protected volatile MediaThumbRequest mCurrentThumbRequest = null;
    protected final PriorityQueue<MediaThumbRequest> mMediaThumbQueue =
            new PriorityQueue<MediaThumbRequest>(MediaThumbRequest.PRIORITY_NORMAL,
                    MediaThumbRequest.getComparator());

    private boolean queryThumbnail(SQLiteQueryBuilder qb, Uri uri, String table,
            String column, boolean hasThumbnailId) {
        qb.setTables(table);
        if (hasThumbnailId) {
            // For uri dispatched to this method, the 4th path segment is always
            // the thumbnail id.
            qb.appendWhere("_id = " + uri.getPathSegments().get(3));
            // client already knows which thumbnail it wants, bypass it.
            return true;
        }
        String origId = uri.getQueryParameter("orig_id");
        // We can't query ready_flag unless we know original id
        if (origId == null) {
            // this could be thumbnail query for other purpose, bypass it.
            return true;
        }

        boolean needBlocking = "1".equals(uri.getQueryParameter("blocking"));
        boolean cancelRequest = "1".equals(uri.getQueryParameter("cancel"));
        Uri origUri = uri.buildUpon().encodedPath(
                uri.getPath().replaceFirst("thumbnails", "media"))
                .appendPath(origId).build();

        if (needBlocking && !waitForThumbnailReady(origUri)) {
            if (DBG) Log.w(TAG, "original media doesn't exist or it's canceled.");
            return false;
        } else if (cancelRequest) {
            String groupId = uri.getQueryParameter("group_id");
            boolean isVideo = "video".equals(uri.getPathSegments().get(1));
            int pid = Binder.getCallingPid();
            long id = -1;
            long gid = -1;

            try {
                id = Long.parseLong(origId);
                gid = Long.parseLong(groupId);
            } catch (NumberFormatException ex) {
                // invalid cancel request
                return false;
            }

            synchronized (mMediaThumbQueue) {
                if (mCurrentThumbRequest != null &&
                        matchThumbRequest(mCurrentThumbRequest, pid, id, gid, isVideo)) {
                    synchronized (mCurrentThumbRequest) {
                        mCurrentThumbRequest.mState = MediaThumbRequest.State.CANCEL;
                        mCurrentThumbRequest.notifyAll();
                    }
                }
                for (MediaThumbRequest mtq : mMediaThumbQueue) {
                    if (matchThumbRequest(mtq, pid, id, gid, isVideo)) {
                        synchronized (mtq) {
                            mtq.mState = MediaThumbRequest.State.CANCEL;
                            mtq.notifyAll();
                        }

                        mMediaThumbQueue.remove(mtq);
                    }
                }
            }
        }

        if (origId != null) {
            qb.appendWhere(column + " = " + origId);
        }
        return true;
    }
    /**
     * This method blocks until thumbnail is ready.
     *
     * @param thumbUri
     * @return
     */
    private boolean waitForThumbnailReady(Uri origUri) {
        Log.d(TAG,"waitForThumbnailReady");

        String origId = origUri.getLastPathSegment();
        String[] whereArgs = new String[] { origId };
        Cursor c = query(origUri, new String[] { BaseColumns._ID, MediaColumns.DATA,
                VideoColumns.MINI_THUMB_MAGIC, VideoColumns.ARCHOS_THUMB_TRY}, LIGHT_INDEX_STORAGE_QUERY, whereArgs , null);
        if(DBG) Log.d(TAG_DOCTOR_WHO, "is cursor null ? "+String.valueOf(c==null));
        if (c == null) return false;

        boolean result = false;

        if (c.moveToFirst()) {

            long id = c.getLong(0);
            String path = c.getString(1);
            if(DBG) Log.d(TAG_DOCTOR_WHO, "trying to create thumb for "+path);

            long magic = c.getLong(2);
            int nbTry = c.getInt(3);
            if (magic == 0 && nbTry >= THUMB_TRY_MAX|| !FileUtils.isLocal(Uri.parse(path))&&!PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(PREFERENCE_CREATE_REMOTE_THUMBS, false)) {
                // thumbnail creation failed more than one time: abort.
                if(DBG) Log.d(TAG_DOCTOR_WHO, "thumbnail creation failed more than "+THUMB_TRY_MAX+" times: abort. ");

                c.close();
                return false;
            }

            MediaThumbRequest req = requestMediaThumbnail(path, origUri,
                    MediaThumbRequest.PRIORITY_HIGH, magic);
            if(DBG) Log.d(TAG_DOCTOR_WHO, "is MediaThumbRequest null ? "+String.valueOf(req==null));
            if (req == null) {
                return false;
            }
            synchronized (req) {
                try {
                    while (req.mState == MediaThumbRequest.State.WAIT) {
                        req.wait();
                    }
                } catch (InterruptedException e) {
                    Log.w(TAG, e);
                }
                if (req.mState == MediaThumbRequest.State.DONE) {
                    result = true;
                    if (magic == 0) {
                        /*
                         *  previous magic = 0, thumbnail was never created,
                         *  retrieve the new magic after requestMediaThumbnail
                         *  call to check if thumbnail is valid after that call.
                         */
                        c.close();

                        c = query(origUri, new String[] { VideoColumns.MINI_THUMB_MAGIC,
                                VideoColumns.ARCHOS_THUMB_TRY}, null, null, null);
                        if (c == null) return result;
                        if (c.moveToFirst()) {
                            magic = c.getLong(0);
                            nbTry = c.getInt(1) + 1;
                            if(DBG) Log.d(TAG_DOCTOR_WHO, " MediaThumbRequest set try to "+String.valueOf(nbTry));

                            if (magic == 0) {
                                ContentValues values = new ContentValues();
                                values.put(VideoColumns.ARCHOS_THUMB_TRY, nbTry);
                                update(origUri, values, null, null);
                            }
                        }
                    }
                }
            }
        }
        c.close();

        return result;
    }

    private static boolean matchThumbRequest(MediaThumbRequest req, int pid, long id, long gid,
            boolean isVideo) {
        boolean cancelAllOrigId = (id == -1);
        boolean cancelAllGroupId = (gid == -1);
        return (req.mCallingPid == pid) &&
                (cancelAllGroupId || req.mGroupId == gid) &&
                (cancelAllOrigId || req.mOrigId == id) &&
                (req.mIsVideo == isVideo);
    }

    private MediaThumbRequest requestMediaThumbnail(String path, Uri uri, int priority, long magic) {
        synchronized (mMediaThumbQueue) {
            MediaThumbRequest req = null;
            try {
                req = new MediaThumbRequest(
                        getContext(), path, uri, priority, magic);
                mMediaThumbQueue.add(req);
                // Trigger the handler.
                Message msg = mThumbHandler.obtainMessage(IMAGE_THUMB);
                msg.sendToTarget();
            } catch (Throwable t) {
                Log.w(TAG, t);
            }
            return req;
        }
    }

    private static final UriMatcher URI_MATCHER =
            new UriMatcher(UriMatcher.NO_MATCH);

    private static final int VIDEO_MEDIA = 200;
    private static final int VIDEO_MEDIA_ID = 201;
    private static final int VIDEO_THUMBNAILS = 202;
    private static final int VIDEO_THUMBNAILS_ID = 203;

    private static final int FILES = 700;
    private static final int FILES_ID = 701;

    private static final int ARCHOS_SMB_SERVER = 803;
    private static final int ARCHOS_SMB_SERVER_ID = 804;

    private static final int RAW = 900;
    private static final int RAWQUERY = 901;

    private static final int SUBS_MEDIA = 1000;
    private static final int SUBS_MEDIA_ID = 1001;
    private static final int SUBS_MEDIA_VIDEO_ID = 1002;

    private static final int LIST = 1100;
    private static final int VIDEO_LIST = 1101;

    static {
        URI_MATCHER.addURI(VideoStore.AUTHORITY, "raw/*", RAW);
        URI_MATCHER.addURI(VideoStore.AUTHORITY, "rawquery", RAWQUERY);

        URI_MATCHER.addURI(VideoStore.AUTHORITY, "list", LIST);
        URI_MATCHER.addURI(VideoStore.AUTHORITY, "list/#", VIDEO_LIST);
        URI_MATCHER.addURI(VideoStore.AUTHORITY, "*/video/media", VIDEO_MEDIA);
        URI_MATCHER.addURI(VideoStore.AUTHORITY, "*/video/media/#", VIDEO_MEDIA_ID);
        URI_MATCHER.addURI(VideoStore.AUTHORITY, "*/video/thumbnails", VIDEO_THUMBNAILS);
        URI_MATCHER.addURI(VideoStore.AUTHORITY, "*/video/thumbnails/#", VIDEO_THUMBNAILS_ID);

        URI_MATCHER.addURI(VideoStore.AUTHORITY, "*/file", FILES);
        URI_MATCHER.addURI(VideoStore.AUTHORITY, "*/file/#", FILES_ID);

        URI_MATCHER.addURI(VideoStore.AUTHORITY, "*/smb_server/#", ARCHOS_SMB_SERVER_ID);
        URI_MATCHER.addURI(VideoStore.AUTHORITY, "*/smb_server", ARCHOS_SMB_SERVER);

        URI_MATCHER.addURI(VideoStore.AUTHORITY, "*/subtitles/media", SUBS_MEDIA);
        URI_MATCHER.addURI(VideoStore.AUTHORITY, "*/subtitles/media/#", SUBS_MEDIA_ID);
        URI_MATCHER.addURI(VideoStore.AUTHORITY, "*/subtitles/media/video/#", SUBS_MEDIA_VIDEO_ID);

        // registering ScraperProvider's uris here
        ScraperProvider.hookUriMatcher(URI_MATCHER);
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        if (DBG) Log.d(TAG, "bulkInsert " + uri);
        int match = URI_MATCHER.match(uri);

        // let ScraperProvider handle that
        if (ScraperProvider.handles(match))
            return mScraperProvider.bulkInsert(uri, values);

        if (match != -1) {
            int result = 0;
            mVobHandler.onBeginTransaction();
            SQLiteDatabase db = mDbHolder.get();
            db.beginTransactionNonExclusive();
            try {
                int numValues = values.length;
                int yield = 100;
                for (int i = 0; i < numValues; i++) {
                    insert(uri, values[i]);
                    if (yield-- < 0) {
                        yield = 100;
                        db.yieldIfContendedSafely();
                    }
                }
                result = numValues;
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
                mVobHandler.onEndTransaction();
            }
            if (result > 0)
                mCr.notifyChange(VideoStore.ALL_CONTENT_URI, null);
            return result;
        }
        return 0;
    }

    @Override
    public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations)
            throws OperationApplicationException {
        if (DBG) Log.d(TAG, "applyBatch");
        ContentProviderResult[] result = null;
        SQLiteDatabase db = mDbHolder.get();
        mVobHandler.onBeginTransaction();
        db.beginTransactionNonExclusive();
        try {
            final int numOperations = operations.size();
            final ContentProviderResult[] results = new ContentProviderResult[numOperations];
            int yield = 100;
            for (int i = 0; i < numOperations; i++) {
                results[i] = operations.get(i).apply(this, results, i);
                if (yield-- < 0) {
                    yield = 100;
                    db.yieldIfContendedSafely();
                }
            }
            result = results;
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            mVobHandler.onEndTransaction();
        }
        if (result != null) {
            mCr.notifyChange(VideoStore.ALL_CONTENT_URI, null);
            mCr.notifyChange(ScraperStore.ALL_CONTENT_URI, null);
        }
        return result;
    }

/**
 * Instances of this class are created and put in a queue to be executed sequentially to see if
 * it needs to (re)generate the thumbnails.
 */
static class MediaThumbRequest {
    private static final String TAG = ArchosMediaCommon.TAG_PREFIX + "MediaThumbRequest";
    private static final boolean DBG = false;
    static final int PRIORITY_LOW = 20;
    static final int PRIORITY_NORMAL = 10;
    static final int PRIORITY_HIGH = 5;
    static final int PRIORITY_CRITICAL = 0;
    static enum State {WAIT, DONE, CANCEL}
    private static final String[] THUMB_PROJECTION = new String[] {
        BaseColumns._ID // 0
    };

    ContentResolver mCr;
    Context mContext;
    String mPath;
    long mRequestTime = System.currentTimeMillis();
    int mCallingPid = Binder.getCallingPid();
    long mGroupId;
    int mPriority;
    Uri mUri;
    Uri mThumbUri;
    String mOrigColumnName;
    boolean mIsVideo;
    long mOrigId;
    State mState = State.WAIT;
    long mMagic;

    private static final Random sRandom = new Random();

    static Comparator<MediaThumbRequest> getComparator() {
        return new Comparator<MediaThumbRequest>() {
            public int compare(MediaThumbRequest r1, MediaThumbRequest r2) {
                if (r1.mPriority != r2.mPriority) {
                    return r1.mPriority < r2.mPriority ? -1 : 1;
                }
                return r1.mRequestTime == r2.mRequestTime ? 0 :
                    r1.mRequestTime < r2.mRequestTime ? -1 : 1;
            }
        };
    }

    MediaThumbRequest(Context ctx, String path, Uri uri, int priority, long magic) {
        mContext = ctx;
        mCr = ctx.getContentResolver();
        mPath = path;
        mPriority = priority;
        mMagic = magic;
        mUri = uri;
        mIsVideo = "video".equals(uri.getPathSegments().get(1));
        mOrigId = ContentUris.parseId(uri);
        mThumbUri = VideoStore.Video.Thumbnails.EXTERNAL_CONTENT_URI;
        mOrigColumnName = VideoStore.Video.Thumbnails.VIDEO_ID;
        // Only requests from Thumbnail API has this group_id parameter. In other cases,
        // mGroupId will always be zero and can't be canceled due to pid mismatch.
        String groupIdParam = uri.getQueryParameter("group_id");
        if (groupIdParam != null) {
            mGroupId = Long.parseLong(groupIdParam);
        }
    }

    Uri updateDatabase(Bitmap thumbnail) {
        Cursor c = mCr.query(mThumbUri, THUMB_PROJECTION,
                mOrigColumnName+ " = " + mOrigId, null, null);
        if (c == null) return null;
        try {
            if (c.moveToFirst()) {
                return ContentUris.withAppendedId(mThumbUri, c.getLong(0));
            }
        } finally {
            if (c != null) c.close();
        }

        ContentValues values = new ContentValues(4);
        values.put(Video.Thumbnails.KIND, Integer.valueOf(Video.Thumbnails.MINI_KIND));
        values.put(mOrigColumnName, Long.valueOf(mOrigId));
        values.put(Video.Thumbnails.WIDTH, Integer.valueOf(thumbnail.getWidth()));
        values.put(Video.Thumbnails.HEIGHT, Integer.valueOf(thumbnail.getHeight()));
        try {
            if (DBG) Log.d(TAG, "insert Thumbnail " + mThumbUri + " val:" + values);
            return mCr.insert(mThumbUri, values);
        } catch (Exception ex) {
            Log.w(TAG, ex);
            return null;
        }
    }

    /**
     * Check if the corresponding thumbnail and mini-thumb have been created
     * for the given uri. This method creates both of them if they do not
     * exist yet or have been changed since last check. After thumbnails are
     * created, MINI_KIND thumbnail is stored in JPEG file and MICRO_KIND
     * thumbnail is stored in a random access file (MiniThumbFile).
     *
     * @throws IOException
     */
    void execute() throws IOException {
        if(DBG) Log.d(TAG_DOCTOR_WHO," executing thumb creation ");

        long magic = mMagic;
        if (magic != 0) {
            Cursor c = null;
            ParcelFileDescriptor pfd = null;
            try {
                c = mCr.query(mThumbUri, THUMB_PROJECTION,
                        mOrigColumnName + " = " + mOrigId, null, null);
                if (c != null && c.moveToFirst()) {
                    pfd = mCr.openFileDescriptor(
                            mThumbUri.buildUpon().appendPath(c.getString(0)).build(), "r");
                }
            } catch (IOException ex) {
                // MINI_THUMBNAIL not exists, ignore the exception and generate one.
            } finally {
                if (c != null) c.close();
                if (pfd != null) {
                    pfd.close();
                    if (DBG) Log.d(TAG, "ThumbRequest, already exists.");
                }
            }
            return;
        }
        if (DBG) Log.d(TAG, "ThumbRequest, creating.");
        // If we can't retrieve the thumbnail, first check if there is one
        // embedded in the EXIF data. If not, or it's not big enough,
        // decompress the full size image.
        Bitmap bitmap = null;

        if (mPath != null) {
            if (mIsVideo) {
                // ARCHOS: this uses libavos
                if(DBG) Log.d(TAG_DOCTOR_WHO,"is video");

                bitmap = createVideoThumbnail(mContext, mPath,
                        Video.Thumbnails.MINI_KIND);
                if(DBG) Log.d(TAG_DOCTOR_WHO, "test 2 for bitmap  "+String.valueOf(bitmap==null));

            }
            if (bitmap == null) {
                Log.w(TAG, "Can't create mini thumbnail for " + mPath);
                return;
            }

            Uri uri = updateDatabase(bitmap);
            if (uri != null) {
                OutputStream thumbOut = mCr.openOutputStream(uri);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, thumbOut);
                thumbOut.close();
                if (DBG) Log.d(TAG, "ThumbRequest written bitmap");
                // also put some random mini_thumb_magic
                do {
                    magic = sRandom.nextLong();
                } while (magic == 0);

                ContentValues values = new ContentValues();

                values.put(VideoColumns.MINI_THUMB_MAGIC, magic);
                mCr.update(mUri, values, null, null);
            }
        }
    }

    /**
     * Create a video thumbnail for a video. May return null if the video is
     * corrupt or the format is not supported.
     *
     * @param filePath the path of video file
     * @param kind could be MINI_KIND or MICRO_KIND
     */
    public static Bitmap createVideoThumbnail(Context ctx, String filePath, int kind) {
        Bitmap res = createVideoThumbnail_(ctx, filePath, kind);
        if (DBG) Log.d(TAG, "createVideoThumbnail: " + res);
        return res;
    }
    private static class Result{
        Bitmap bm;
        public Result(){

        }
        public void setBitmap(Bitmap bm){
            this.bm= bm;
        }
    }
    public static Bitmap createVideoThumbnail_(final Context ctx, final String filePath, int kind) {
        Bitmap bitmap = null;
        final Result result = new Result();
        final IMediaThumbnailService service = MediaThumbnailService.bind_sync(ctx);
        if ( service!= null) {
            try {
                Thread t = new Thread(){
                    public void run(){
                        try {
                            if(DBG) Log.d(TAG_DOCTOR_WHO, "get Thumb for "+filePath);
                            result.setBitmap(service.getThumbnail(filePath, -1));

                        } catch (RemoteException e) {
                            if(DBG) Log.d(TAG_DOCTOR_WHO, "get Thumb for "+filePath+ " failed (RemoteException)");

                            Log.e(TAG, "can't get thumbnail, service crashed?", e);
                        }
                    }
                };
                t.start();
                t.join();
                bitmap = result.bm;


                if (DBG) Log.d(TAG, "MediaThumbnailService gave us: " + bitmap);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            MediaThumbnailService.release(ctx);
        } else {
            Log.d(TAG, "no Thumbnail service, crash?");
            if(DBG) Log.d(TAG_DOCTOR_WHO, "no Thumbnail service, crash?");

            IMediaMetadataRetriever retriever = MediaFactory.createMetadataRetriever(ctx);
            try {
                retriever.setDataSource(filePath);
                if(DBG) Log.d(TAG_DOCTOR_WHO, "getFrameAtTime -1 ");

                bitmap = retriever.getFrameAtTime(-1);

            } catch (IllegalArgumentException ex) {
                // Assume this is a corrupt video file
                if(DBG) Log.d(TAG_DOCTOR_WHO, "IllegalArgumentException "+ex.toString());

            } catch (RuntimeException ex) {
                // Assume this is a corrupt video file.
                if(DBG) Log.d(TAG_DOCTOR_WHO, "RuntimeException "+ex.toString());

            } finally {
                try {
                    retriever.release();
                } catch (RuntimeException ex) {
                    // Ignore failures while cleaning up.
                }
            }
        }

        if (bitmap == null) {
            if(DBG) Log.d(TAG_DOCTOR_WHO, "bitmap is null ");

            return null;
        }

        if(DBG) Log.d(TAG_DOCTOR_WHO, "bitmap is not null ");
        if (kind == Video.Thumbnails.MINI_KIND) {
            if(DBG) Log.d(TAG_DOCTOR_WHO, "MINI_KIND ? ");
            // Scale down the bitmap if it's too large.
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int max = Math.max(width, height);
            if (max > 512) {
                float scale = 512f / max;
                int w = Math.round(scale * width);
                int h = Math.round(scale * height);
                bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true);
                if(DBG) Log.d(TAG_DOCTOR_WHO, "createScaledBitmap");

            }
        }
        return bitmap;
    }
}

    // ---------------------------------------------------------------------- //
    // ------------ Network State change handling --------------------------- //
    // ---------------------------------------------------------------------- //
    protected int mNetworkState = -1;
    protected static final IntentFilter INTENT_FILTER = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
    private boolean mNetworkStateReceiverRegistered = false;
    private final BroadcastReceiver mNetworkStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            NetworkState networkState = NetworkState.instance(context);
            networkState.updateFrom(context);
            int newState = (networkState.isConnected() ? 1 : 0 )+(networkState.hasLocalConnection() ? 1 : 0);
            if (newState != mNetworkState) {
                if (DBG_NET) Log.d(TAG, "NetworkState changed " + mNetworkState + " -> " + newState);
                mNetworkState = newState;
                RemoteStateService.start(context);
            }
        }
    };
    private final AppState.OnForeGroundListener mForeGroundListener = new AppState.OnForeGroundListener() {

        @Override
        public void onForeGroundState(Context applicationContext, boolean foreground) {
            if(foreground)
                VideoStoreImportService.start(applicationContext);
            else
                VideoStoreImportService.stop(applicationContext);
            handleForeGround(foreground);
        }
    };

    protected void handleForeGround(boolean foreground) {
        final Context context = getContext();
        if (foreground) {
            if (DBG_NET) Log.d(TAG, "App now in ForeGround");
            // coming back to front: register network receiver
            if (!mNetworkStateReceiverRegistered) {
                context.registerReceiver(mNetworkStateReceiver, INTENT_FILTER);
                mNetworkStateReceiverRegistered = true;
            }
            UpnpServiceManager.restartUpnpServiceIfWasStartedBefore();

            // get current network state
            NetworkState networkState = NetworkState.instance(context);
            networkState.updateFrom(context);
            mNetworkState = (networkState.isConnected() ? 1 : 0 )+(networkState.hasLocalConnection() ? 1 : 0);
            // force check
            RemoteStateService.start(context);
        } else {
            if (DBG_NET) Log.d(TAG, "App now in BackGround");
            // going back to background, unregister receiver and set network state
            // to unknown
            if (mNetworkStateReceiverRegistered) {
                context.unregisterReceiver(mNetworkStateReceiver);
                mNetworkStateReceiverRegistered = false;
            }
            UpnpServiceManager.stopServiceIfLaunched();
            mNetworkState = -1;
        }
    }
}

