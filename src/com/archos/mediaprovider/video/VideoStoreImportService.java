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

import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.provider.MediaStore;

import androidx.core.content.ContextCompat;
import android.util.Log;

import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.utils.AppState;
import com.archos.medialib.R;
import com.archos.mediaprovider.ArchosMediaCommon;
import com.archos.mediaprovider.ArchosMediaIntent;
import com.archos.mediaprovider.DeleteFileCallback;
import com.archos.mediaprovider.ImportState;
import com.archos.mediaprovider.VideoDb;
import com.archos.mediaprovider.VolumeState;
import com.archos.mediaprovider.ImportState.State;
import com.archos.mediaprovider.VolumeState.Volume;
import com.archos.mediascraper.Scraper;

public class VideoStoreImportService extends Service implements Handler.Callback {
    private static final String TAG =  ArchosMediaCommon.TAG_PREFIX + "VideoStoreImportService";
    private static final boolean LOCAL_DBG = false;
    private static final boolean DBG = ArchosMediaCommon.PACKAGE_DBG & LOCAL_DBG;

    // handler message ids
    private static final int MESSAGE_KILL = 1;
    private static final int MESSAGE_IMPORT_FULL = 2;
    private static final int MESSAGE_IMPORT_INCR = 3;
    private static final int MESSAGE_UPDATE_METADATA = 4;
    private static final int MESSAGE_REMOVE_FILE = 5;
    private static final int MESSAGE_HIDE_VOLUME = 6;

    // handler.arg1 contains startId or this
    private static final int DONT_KILL_SELF = -1;

    // true until shutdown. Static because this is true for every instance
    private static volatile boolean sActive = true;

    protected Handler mHandler;
    private HandlerThread mHandlerThread;
    private VideoStoreImportImpl mImporter;
    private ContentObserver mContentObserver;
    private boolean mNeedToInitScraper = false;
    private AppState.OnForeGroundListener mForeGroundListener;

    protected VolumeState mVolumeState;
    protected VolumeState.Observer mVolumeStateObserver;

    private static Context mContext;

    private static final int NOTIFICATION_ID = 6;
    private NotificationManager nm;
    private static final String notifChannelId = "VideoStoreImportService_id";
    private static final String notifChannelName = "VideoStoreImportService";
    private static final String notifChannelDescr = "VideoStoreImportService";

    public VideoStoreImportService() {
        if (DBG) Log.d(TAG, "VideoStoreImportService CTOR");
    }

    @Override
    protected void finalize() throws Throwable {
        if (DBG) Log.d(TAG, "VideoStoreImportService DTOR");
        super.finalize();
    }

    @Override
    public void onCreate() {

        if (DBG) Log.d(TAG, "onCreate");
        // importer logic
        mImporter = new VideoStoreImportImpl(this);
        // setup background worker thread
        mHandlerThread = new HandlerThread("ImportWorker", Process.THREAD_PRIORITY_BACKGROUND);
        mHandlerThread.start();
        Looper looper = mHandlerThread.getLooper();
        // associate a handler with the new thread
        mHandler = new Handler(looper, this);
        // associate content observer that reports in background thread
        mContentObserver = new ContentChangeObserver(mHandler);

        // handles changes to mounted / unmounted volumes, needs to exist before foreground state
        // handler because it's used in there
        mVolumeState = new VolumeState(this);
        mVolumeStateObserver = new VolumeState.Observer() {
            @Override
            public void onMountStateChanged(Volume... volumes) {
                for (Volume volume : volumes) {
                    if (DBG) Log.d(TAG, "Change:" + volume.getMountPoint() + " to " + volume.getMountState());
                    if (!volume.getMountState()) {
                        mHandler
                            .obtainMessage(MESSAGE_HIDE_VOLUME, DONT_KILL_SELF, volume.getStorageId())
                            .sendToTarget();
                    }
                }
            }
        };
        mVolumeState.addObserver(mVolumeStateObserver);
        // fetch initial state
        mVolumeState.updateState();
        if (AppState.isForeGround()) {
            // we are most likely started in foreground but won't get a notification
            // on the listener so register receiver manually.
            mVolumeState.registerReceiver();
        }

        mForeGroundListener = new AppState.OnForeGroundListener() {
            @Override
            public void onForeGroundState(Context applicationContext, boolean foreground) {
                if (DBG) Log.d(TAG, "onForeGroundState:" + foreground);
                // when switching to foreground state and db
                // has potentially changed: trigger db import
                if (foreground) {
                    mVolumeState.registerReceiver();
                    mVolumeState.updateState();

                    if (ImportState.VIDEO.isDirty()) {
                        if (DBG) Log.d(TAG, "onForeGround && ImportState.isDirty");
                        mHandler
                            .obtainMessage(MESSAGE_IMPORT_FULL, DONT_KILL_SELF, 0)
                            .sendToTarget();
                    }
                } else {
                    mVolumeState.unregisterReceiver();
                }
            }
        };
        AppState.addOnForeGroundListener(mForeGroundListener);

        // register contentobserver for files and videos, on change we import them
        getContentResolver().registerContentObserver(MediaStore.Files.getContentUri("external"),
                true, mContentObserver);
        getContentResolver().registerContentObserver(MediaStore.Video.Media.getContentUri("external"),
                true, mContentObserver);
        // do a full import here to make sure that we have initial data
        Message m = mHandler.obtainMessage(MESSAGE_IMPORT_FULL, DONT_KILL_SELF, 0);
        // assume this is the initial import although there could be data in the db already.
        ImportState.VIDEO.setState(State.INITIAL_IMPORT);
        // we also may need to init scraper default content
        mNeedToInitScraper = true;
        mHandler.sendMessageDelayed(m, 1000);
    }

    @Override
    public void onDestroy() {
        if (DBG) Log.d(TAG, "onDestroy");
        FileUtils.hideNotification(nm, NOTIFICATION_ID);
        AppState.removeOnForeGroundListener(mForeGroundListener);
        mForeGroundListener = null;
        // stop handler thread
        mHandlerThread.quit();
        mImporter.destroy();
    }

    /** wether it's ok do do an import now, will mark db dirty if not */
    protected static boolean importOk() {
        if (AppState.isForeGround()) {
            return true;
        }
        if (DBG) Log.d(TAG, "Ignoring DB update, App is not in foreground.");
        ImportState.VIDEO.setDirty(true);
        return false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // intents are delivered here.
        if (DBG) Log.d(TAG, "onStartCommand:" + intent + " flags:" + flags + " startId:" + startId);
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (intent == null || intent.getAction() == null)
            return START_NOT_STICKY;

        // forward startId to handler thread
        String action = intent.getAction();
        if (Intent.ACTION_MEDIA_SCANNER_FINISHED.equals(action)|| ArchosMediaIntent.ACTION_VIDEO_SCANNER_STORAGE_PERMISSION_GRANTED.equals(action)) {
            // happens rarely, on boot and when inserting / ejecting sd cards
            removeAllMessages(mHandler);
            Message m = mHandler.obtainMessage(MESSAGE_IMPORT_FULL, startId, flags);
            mHandler.sendMessageDelayed(m, 1000);
            mNeedToInitScraper = true;
            ImportState.VIDEO.setAndroidScanning(false);
            if (DBG) Log.d(TAG, "SCAN FINISHED " + intent.getData());
        } else if (Intent.ACTION_MEDIA_SCANNER_STARTED.equals(action)) {
            removeAllMessages(mHandler);
            ImportState.VIDEO.setAndroidScanning(true);
            if (DBG) Log.d(TAG, "SCAN STARTED " + intent.getData());
        } else if (ArchosMediaIntent.ACTION_VIDEO_SCANNER_METADATA_UPDATE.equals(action)) {
            // requests to update metadata are processed directly and don't impact importing
            Message m = mHandler.obtainMessage(MESSAGE_UPDATE_METADATA, startId, flags, intent.getData());
            m.sendToTarget();
        } else if (ArchosMediaIntent.isVideoRemoveIntent(action)) {
            // requests to remove files are processed directly and don't impact importing
            Message m = mHandler.obtainMessage(MESSAGE_REMOVE_FILE, startId, flags, intent.getData());
            m.sendToTarget();
        } else if (Intent.ACTION_SHUTDOWN.equals(action)) {
            if (DBG) Log.d(TAG, "Import disabled due to shutdown");
            sActive = false;
        }
        return Service.START_NOT_STICKY;
    }

    /**
     * allows to "bind" to this service, will cause the service to be listening to
     * content changed events while bound
     * */
    public static void start(Context context) {
        Intent intent = new Intent(context, VideoStoreImportService.class);
        if (AppState.isForeGround()) {
            context.startService(intent);
        }
        // context.bindService(intent, new LoggingConnection(), Context.BIND_AUTO_CREATE);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, VideoStoreImportService.class);
        context.stopService(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (DBG) Log.d(TAG, "onBind:" + intent);

        return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (DBG) Log.d(TAG, "onUnbind:" + intent);
        // unregister content observer
        getContentResolver().unregisterContentObserver(mContentObserver);
        return super.onUnbind(intent);
    }

    /** handler implementation, called in background thread */
    public boolean handleMessage(Message msg) {
        if (DBG) Log.d(TAG, "handleMessage:" + msg + " what:" + msg.what + " startid:" + msg.arg1);
        switch (msg.what) {
            case MESSAGE_KILL:
                // this service used to be created through bind. So it couldn't be killed with stopself unless it was unbind
                // (which wasn't done). To have the same behavior, do not stop service for now
                /*if (msg.arg1 != DONT_KILL_SELF){
                    Log.d(TAG, "stopSelf");
                    stopSelf(msg.arg1);
                }*/
                break;
            case MESSAGE_IMPORT_INCR:
                doImport(false);
                mHandler.obtainMessage(MESSAGE_KILL, msg.arg1, msg.arg2).sendToTarget();
                break;
            case MESSAGE_IMPORT_FULL:
                doImport(true);
                mHandler.obtainMessage(MESSAGE_KILL, msg.arg1, msg.arg2).sendToTarget();
                break;
            case MESSAGE_UPDATE_METADATA:
                mImporter.doScan((Uri)msg.obj);
                mHandler.obtainMessage(MESSAGE_KILL, msg.arg1, msg.arg2).sendToTarget();
                break;
            case MESSAGE_REMOVE_FILE:
                mImporter.doRemove((Uri)msg.obj);
                mHandler.obtainMessage(MESSAGE_KILL, msg.arg1, msg.arg2).sendToTarget();
                break;
            case MESSAGE_HIDE_VOLUME:
                // insert the storage_id that's to be hidden into the special trigger view thingy
                ContentValues cv = new ContentValues();
                cv.put(VideoStore.Files.FileColumns.STORAGE_ID, String.valueOf(msg.arg2));
                VideoStoreImportService.this.getContentResolver().insert(VideoStoreInternal.HIDE_VOLUME, cv);
                mHandler.obtainMessage(MESSAGE_KILL, msg.arg1, msg.arg2).sendToTarget();
                break;

            default:
                Log.w(TAG, "ImportBgHandler - unknown msg.what: " + msg.what);
                break;
        }
        if (mNeedToInitScraper) {
            initializeScraperData();
            mNeedToInitScraper = false;
        }
        return true;
    }

    /** starts import, fullMode decides which import implementation is used */
    private void doImport(boolean fullMode) {
        // TODO determine when / if we need both import implementations
        FileUtils.showNotification(this, VideoStoreImportService.class, nm, NOTIFICATION_ID,
                "", R.string.video_store_import, notifChannelId, notifChannelName,  notifChannelDescr);
        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED ) {
            if (DBG) Log.d(TAG, "no read permission : stop import");
            FileUtils.hideNotification(nm, NOTIFICATION_ID);
            return;
        }
        ImportState.VIDEO.setDirty(false);

        if (!sActive) {
            if (DBG) Log.d(TAG, "Import request ignored due to device shutdown.");
            FileUtils.hideNotification(nm, NOTIFICATION_ID);
            return;
        }

        if (DBG) Log.d(TAG, "doImport");

        long start = System.currentTimeMillis();
        if (fullMode)
            mImporter.doFullImport();
        else
            mImporter.doIncrementalImport();
        long end = System.currentTimeMillis();
        if (DBG) Log.d(TAG, "doImport took:" + (end - start) + "ms full:" + fullMode);

        // perform no longer possible delete_file and vob_insert db callbacks after incr or full import
        // this will also flush delete_files and vob_insert buffer tables
        processDeleteFileAndVobCallback();
        FileUtils.hideNotification(nm, NOTIFICATION_ID);
    }

    private void processDeleteFileAndVobCallback() {
        Cursor c = null;
        VobHandler mVobHandler;
        mVobHandler = new VobHandler(this);
        VobUpdateCallback vobCb = new VobUpdateCallback(mVobHandler);
        SQLiteDatabase db = VideoDb.get(this);
        DeleteFileCallback delCb = new DeleteFileCallback();
        String[] DeleteFileCallbackArgs = null;
        String[] VobUpdateCallbackArgs = null;
        try {
            c = db.rawQuery("SELECT * FROM delete_files WHERE name IN (SELECT cover_movie FROM MOVIE UNION SELECT cover_show FROM SHOW UNION SELECT cover_episode FROM EPISODE)", null);
            c.moveToPosition(-1);
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String path = c.getString(1);
                long count = c.getLong(2);
                if (DBG) Log.d(TAG, "clean delete_files " + String.valueOf(id) + " path " + path + " count " + String.valueOf(count));
                // purge the db: delete row even if file delete callback fails (file deletion could be handled elsewhere
                try {
                    db.execSQL("DELETE FROM delete_files WHERE _id=" + String.valueOf(id) + " AND name='" + path + "'");
                } catch (SQLException sqlE) {
                    Log.e(TAG, "SQLException", sqlE);
                } finally {
                }
            }
        } catch (SQLException | IllegalStateException e) {
            Log.e(TAG, "SQLException or IllegalStateException",e);
        } finally {
            if (c != null)
                c.close();
        }
        // note: seems that the delete is performed not as a table trigger anymore but elsewhere
        try {
            c = db.rawQuery("SELECT * FROM delete_files", null);
            c.moveToPosition(-1);
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String path = c.getString(1);
                long count = c.getLong(2);
                if (DBG) Log.d(TAG, "delete_files " + String.valueOf(id) + " path " + path + " count " + String.valueOf(count));
                // delete callback
                DeleteFileCallbackArgs = new String[] {path, String.valueOf(count)};
                delCb.callback(DeleteFileCallbackArgs);
                // purge the db: delete row even if file delete callback fails (file deletion could be handled elsewhere
                try {
                    db.execSQL("DELETE FROM delete_files WHERE _id=" + String.valueOf(id) + " AND name='" + path + "'");
                } catch (SQLException sqlE) {
                    Log.e(TAG, "SQLException", sqlE);
                } finally {
                }
            }
        } catch (SQLException | IllegalStateException e) {
            Log.e(TAG, "SQLException or IllegalStateException",e);
        } finally {
            if (c != null)
                c.close();
        }
        try {
            c = db.rawQuery("SELECT * FROM vob_insert", null);
            c.moveToPosition(-1);
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String path = c.getString(1);
                if (DBG) Log.d(TAG, "vob_insert " + String.valueOf(id) + " path " + path);
                // delete callback
                VobUpdateCallbackArgs = new String[] {path};
                vobCb.callback(VobUpdateCallbackArgs);
                // purge the db: delete row
                try {
                    db.execSQL("DELETE FROM vob_insert WHERE _id=" + String.valueOf(id) + " AND name='" + path + "'");
                } catch (SQLException sqlE) {
                    Log.e(TAG, "SQLException", sqlE);
                } finally {
                }
            }
        } catch (SQLException | IllegalStateException e) {
            Log.e(TAG, "SQLException or IllegalStateException",e);
        } finally {
            if (c != null)
                c.close();
        }
        // don't db.close() - shared connection
    }

    /** removes all messages from handler */
    protected static void removeAllMessages(Handler handler) {
        handler.removeMessages(MESSAGE_KILL);
        handler.removeMessages(MESSAGE_IMPORT_FULL);
        handler.removeMessages(MESSAGE_IMPORT_INCR);
    }

    /** ContentObserver that triggers import when data changed. */
    private static class ContentChangeObserver extends ContentObserver {

        private final Handler mHandler;
        public ContentChangeObserver(Handler handler) {
            super(handler);
            mHandler = handler;
        }

        @Override
        public void onChange(boolean selfChange) {
            if (DBG) Log.d(TAG, "onChange");
            // happens really often
            if (importOk()) {
                removeAllMessages(mHandler);
                Message msg = mHandler.obtainMessage(MESSAGE_IMPORT_INCR, DONT_KILL_SELF, 0);
                mHandler.sendMessageDelayed(msg, 1000);
            }
        }
    }

    /** ServiceConnection that will only do logging */
    private static class LoggingConnection implements ServiceConnection {

        public LoggingConnection() {
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            if (DBG) Log.d(TAG, "onServiceConnected");
        }

        public void onServiceDisconnected(ComponentName name) {
            if (DBG) Log.d(TAG, "onServiceDisconnected");
        }
    }

    /** calls {@link IScraperService#setupDefaultContent(boolean) }*/
    private void initializeScraperData() {
        if (DBG) Log.d(TAG, "initializeScraperData()");
        Scraper scraper = new Scraper(this);
        scraper.setupDefaultContent(false);
    }
}
