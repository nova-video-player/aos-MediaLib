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

import android.app.Notification;
import android.app.NotificationChannel;
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
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.provider.BaseColumns;
import android.provider.MediaStore;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import android.util.Log;

import com.archos.environment.ArchosUtils;
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
    private Notification n;
    private static final String notifChannelId = "VideoStoreImportService_id";
    private static final String notifChannelName = "VideoStoreImportService";
    private static final String notifChannelDescr = "VideoStoreImportService";

    private static final int WINDOW_SIZE = 2000;

    public VideoStoreImportService() {
        if (DBG) Log.d(TAG, "VideoStoreImportService CTOR");
    }

    @Override
    protected void finalize() throws Throwable {
        if (DBG) Log.d(TAG, "VideoStoreImportService DTOR");
        super.finalize();
    }

    public static boolean startIfHandles(Context context, Intent broadcast) {
        mContext = context;
        String action = broadcast.getAction();
        if (Intent.ACTION_MEDIA_SCANNER_FINISHED.equals(action)
                || ArchosMediaIntent.ACTION_VIDEO_SCANNER_STORAGE_PERMISSION_GRANTED.equals(action)
                || Intent.ACTION_MEDIA_SCANNER_STARTED.equals(action)
                || ArchosMediaIntent.ACTION_VIDEO_SCANNER_METADATA_UPDATE.equals(action)
                || ArchosMediaIntent.isVideoRemoveIntent(action)
                || Intent.ACTION_SHUTDOWN.equals(action)
                || ArchosMediaIntent.ACTION_VIDEO_SCANNER_IMPORT_INCR.equals(action)) {
            if (DBG) Log.d(TAG, "startIfHandles is true: sending intent to VideoStoreImportService");
            Intent serviceIntent = new Intent(context, VideoStoreImportService.class);
            serviceIntent.setAction(action);
            serviceIntent.setData(broadcast.getData());
            if(broadcast.getExtras()!=null)
                serviceIntent.putExtras(broadcast.getExtras()); //in case we have an extra... such as "recordLogExtra"
            if (AppState.isForeGround()) {
                if (DBG) Log.d(TAG, "startIfHandles: apps is foreground startForegroundService and pass intent to self");
                ContextCompat.startForegroundService(context, serviceIntent);
            }
            return true;
        }
        if (DBG) Log.d(TAG, "startIfHandles is false: do nothing");
        return false;
    }

    @Override
    public void onCreate() {

        if (DBG) Log.d(TAG, "onCreate");

        // need to do that early to avoid ANR on Android 26+
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel nc = new NotificationChannel(notifChannelId, notifChannelName,
                    nm.IMPORTANCE_LOW);
            nc.setDescription(notifChannelDescr);
            if (nm != null)
                nm.createNotificationChannel(nc);
        }
        n = new NotificationCompat.Builder(this, notifChannelId)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(getString(R.string.video_store_import))
                .setContentText("")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setTicker(null).setOnlyAlertOnce(true).setOngoing(true).setAutoCancel(true)
                .build();
        startForeground(NOTIFICATION_ID, n);

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
                        if (DBG) Log.d(TAG, "onCreate: onForeGround && ImportState.isDirty MESSAGE_IMPORT_FULL");
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
        // TODO is this useful to do it at each launch --> should not?
        if (DBG) Log.d(TAG, "onCreate: MESSAGE_IMPORT_FULL, is this useful?");
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
        AppState.removeOnForeGroundListener(mForeGroundListener);
        mForeGroundListener = null;
        // stop handler thread
        mHandlerThread.quit();
        mImporter.destroy();
        // hide notification
        nm.cancel(NOTIFICATION_ID);
        stopForeground(true);
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

        if (intent == null || intent.getAction() == null)
            return START_NOT_STICKY;

        if (DBG) Log.d(TAG, "onStartCommand: startForeground");
        startForeground(NOTIFICATION_ID, n);

        // forward startId to handler thread
        // /!\ if an action is added CHECK in startIfHandles if action is listed /!\
        String action = intent.getAction();
        if (Intent.ACTION_MEDIA_SCANNER_FINISHED.equals(action) || ArchosMediaIntent.ACTION_VIDEO_SCANNER_STORAGE_PERMISSION_GRANTED.equals(action)) {
            if (DBG) Log.d(TAG, "ACTION_MEDIA_SCANNER_FINISHED " + intent.getData());
            // happens rarely, on boot and when inserting / ejecting sd cards
            removeAllMessages(mHandler);
            Message m = mHandler.obtainMessage(MESSAGE_IMPORT_FULL, startId, flags);
            mHandler.sendMessageDelayed(m, 1000);
            mNeedToInitScraper = true;
            ImportState.VIDEO.setAndroidScanning(false);
        } else if (Intent.ACTION_MEDIA_SCANNER_STARTED.equals(action)) {
            if (DBG) Log.d(TAG, "ACTION_MEDIA_SCANNER_STARTED " + intent.getData());
            removeAllMessages(mHandler);
            ImportState.VIDEO.setAndroidScanning(true);
        } else if (ArchosMediaIntent.ACTION_VIDEO_SCANNER_METADATA_UPDATE.equals(action)) {
            if (DBG) Log.d(TAG, "ACTION_VIDEO_SCANNER_METADATA_UPDATE " + intent.getData());
            // requests to update metadata are processed directly and don't impact importing
            if (DBG) Log.d(TAG, "SCAN STARTED " + intent.getData());
            Message m = mHandler.obtainMessage(MESSAGE_UPDATE_METADATA, startId, flags, intent.getData());
            m.sendToTarget();
        } else if (ArchosMediaIntent.isVideoRemoveIntent(action)) {
            // requests to remove files are processed directly and don't impact importing
            Message m = mHandler.obtainMessage(MESSAGE_REMOVE_FILE, startId, flags, intent.getData());
            m.sendToTarget();
        } else if (Intent.ACTION_SHUTDOWN.equals(action)) {
            if (DBG) Log.d(TAG, "Import disabled due to shutdown");
            sActive = false;
        } else if (ArchosMediaIntent.ACTION_VIDEO_SCANNER_IMPORT_INCR.equals(action)) {
            if (DBG) Log.d(TAG, "ACTION_VIDEO_SCANNER_IMPORT_INCR " + intent.getData());
            removeAllMessages(mHandler);
            Message m = mHandler.obtainMessage(MESSAGE_IMPORT_INCR, startId, flags);
            mHandler.sendMessageDelayed(m, 1000);
        } else {
            Log.w(TAG, "onStartCommand: intent not treated, cancelling notification and stopForeground");
            nm.cancel(NOTIFICATION_ID);
            stopForeground(true);
        }
        return Service.START_NOT_STICKY;
    }

    /**
     * allows to "bind" to this service, will cause the service to be listening to
     * content changed events while bound
     * */
    public static void startService(Context context) {
        if (DBG) Log.d(TAG, "startService");
        mContext = context;
        Intent intent = new Intent(context, VideoStoreImportService.class);
        if (AppState.isForeGround()) {
            ContextCompat.startForegroundService(context, intent);
        }
        // context.bindService(intent, new LoggingConnection(), Context.BIND_AUTO_CREATE);
    }

    // TODO MARC: problem should send kill signal intent
    public static void stopService(Context context) {
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
                if (DBG) Log.d(TAG, "handleMessage: MESSAGE_KILL");
                nm.cancel(NOTIFICATION_ID);
                stopForeground(true);
                break;
            case MESSAGE_IMPORT_INCR:
                if (DBG) Log.d(TAG, "handleMessage: MESSAGE_IMPORT_INCR");
                doImport(false);
                mHandler.obtainMessage(MESSAGE_KILL, msg.arg1, msg.arg2).sendToTarget();
                break;
            case MESSAGE_IMPORT_FULL:
                if (DBG) Log.d(TAG, "handleMessage: MESSAGE_IMPORT_FULL");
                doImport(true);
                mHandler.obtainMessage(MESSAGE_KILL, msg.arg1, msg.arg2).sendToTarget();
                break;
            case MESSAGE_UPDATE_METADATA:
                if (DBG) Log.d(TAG, "handleMessage: MESSAGE_UPDATE_METADATA");
                mImporter.doScan((Uri)msg.obj);
                mHandler.obtainMessage(MESSAGE_KILL, msg.arg1, msg.arg2).sendToTarget();
                break;
            case MESSAGE_REMOVE_FILE:
                if (DBG) Log.d(TAG, "handleMessage: MESSAGE_REMOVE_FILE");
                mImporter.doRemove((Uri)msg.obj);
                mHandler.obtainMessage(MESSAGE_KILL, msg.arg1, msg.arg2).sendToTarget();
                break;
            case MESSAGE_HIDE_VOLUME:
                if (DBG) Log.d(TAG, "handleMessage: MESSAGE_HIDE_VOLUME");
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

        nm.notify(NOTIFICATION_ID, n);

        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED ) {
            if (DBG) Log.d(TAG, "no read permission : stop import");
            return;
        }
        ImportState.VIDEO.setDirty(false);

        if (!sActive) {
            if (DBG) Log.d(TAG, "Import request ignored due to device shutdown.");
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

        // notify all that we have new stuff
        Intent intent = new Intent(ArchosMediaIntent.ACTION_VIDEO_SCANNER_SCAN_FINISHED, null);
        intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
        sendBroadcast(intent);
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
            // break down the scan in batch of WINDOW_SIZE in order to avoid SQLiteBlobTooBigException: Row too big to fit into CursorWindow crash
            // note that the db is being modified during import
            c = db.rawQuery("SELECT * FROM delete_files WHERE name IN (SELECT cover_movie FROM MOVIE UNION SELECT cover_show FROM SHOW UNION SELECT cover_episode FROM EPISODE)", null);
            int numberOfRows = c.getCount();
            int numberOfRowsRemaining = numberOfRows;
            if (DBG) Log.d(TAG, "processDeleteFileAndVobCallback: delete_files cover numberOfRows=" + numberOfRows);
            int window = WINDOW_SIZE;
            c.close();
            do {
                if (window > numberOfRowsRemaining)
                    window = numberOfRowsRemaining;
                c = db.rawQuery("SELECT * FROM delete_files WHERE name IN (SELECT cover_movie FROM MOVIE UNION SELECT cover_show FROM SHOW UNION SELECT cover_episode FROM EPISODE) ORDER BY " + BaseColumns._ID + " ASC LIMIT " + window, null);
                if (DBG) Log.d(TAG, "processDeleteFileAndVobCallback: delete_files cover new batch fetching window=" + window + " entries <=" + numberOfRowsRemaining);
                if (DBG) Log.d(TAG, "processDeleteFileAndVobCallback: delete_files cover new batch cursor has size " + c.getCount());
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
                    }
                }
                c.close();
                numberOfRowsRemaining -= window;
            } while (numberOfRowsRemaining > 0);
        } catch (SQLException | IllegalStateException e) {
            Log.e(TAG, "SQLException or IllegalStateException",e);
        } finally {
            if (c != null) c.close();
        }
        // note: seems that the delete is performed not as a table trigger anymore but elsewhere
        try {
            // break down the scan in batch of WINDOW_SIZE in order to avoid SQLiteBlobTooBigException: Row too big to fit into CursorWindow crash
            // note that the db is being modified during import
            if (c != null) c.close();
            c = db.rawQuery("SELECT * FROM delete_files", null);
            int numberOfRows = c.getCount();
            int numberOfRowsRemaining = numberOfRows;
            if (DBG) Log.d(TAG, "processDeleteFileAndVobCallback: delete_files numberOfRows=" + numberOfRows);
            int window = WINDOW_SIZE;
            c.close();
            do {
                if (window > numberOfRowsRemaining)
                    window = numberOfRowsRemaining;
                c = db.rawQuery("SELECT * FROM delete_files ORDER BY " + BaseColumns._ID + " ASC LIMIT " + window, null);
                if (DBG) Log.d(TAG, "processDeleteFileAndVobCallback: delete_files new batch fetching window=" + window + " entries <=" + numberOfRowsRemaining);
                if (DBG) Log.d(TAG, "processDeleteFileAndVobCallback: delete_files new batch cursor has size " + c.getCount());
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
                    }
                }
                c.close();
                numberOfRowsRemaining -= window;
            } while (numberOfRowsRemaining > 0);
        } catch (SQLException | IllegalStateException e) {
            Log.e(TAG, "SQLException or IllegalStateException",e);
        } finally {
            if (c != null) c.close();
        }
        try {
            // break down the scan in batch of WINDOW_SIZE in order to avoid SQLiteBlobTooBigException: Row too big to fit into CursorWindow crash
            // note that the db is being modified during import
            if (c != null) c.close();
            c = db.rawQuery("SELECT * FROM vob_insert", null);
            int numberOfRows = c.getCount();
            int numberOfRowsRemaining = numberOfRows;
            if (DBG) Log.d(TAG, "processDeleteFileAndVobCallback: vob_insert numberOfRows=" + numberOfRows);
            int window = WINDOW_SIZE;
            c.close();
            do {
                if (window > numberOfRowsRemaining)
                    window = numberOfRowsRemaining;
                c = db.rawQuery("SELECT * FROM vob_insert ORDER BY " + BaseColumns._ID + " ASC LIMIT " + window, null);
                if (DBG) Log.d(TAG, "processDeleteFileAndVobCallback: vob_insert new batch fetching window=" + window + " entries <=" + numberOfRowsRemaining);
                if (DBG) Log.d(TAG, "processDeleteFileAndVobCallback: vob_insert new batch cursor has size " + c.getCount());
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
                    }
                }
                c.close();
                numberOfRowsRemaining -= window;
            } while (numberOfRowsRemaining > 0);
        } catch (SQLException | IllegalStateException e) {
            Log.e(TAG, "SQLException or IllegalStateException",e);
        } finally {
            if (c != null) c.close();
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
            // to avoid sending message to dead thread because mHandlerThread is no more, need to relauch the service so that it is recreated in onCreate
            /*
            // happens really often
            if (importOk()) {
                removeAllMessages(mHandler);
                Message msg = mHandler.obtainMessage(MESSAGE_IMPORT_INCR, DONT_KILL_SELF, 0);
                mHandler.sendMessageDelayed(msg, 1000);
            }
             */
            // happens really often
            if (importOk()) {
                if (DBG) Log.d(TAG, "onChange: trigering VIDEO_SCANNER_IMPORT_INCR");
                Intent intent = new Intent(mContext, VideoStoreImportService.class);
                intent.setAction(ArchosMediaIntent.ACTION_VIDEO_SCANNER_IMPORT_INCR);
                if (AppState.isForeGround()) {
                    ContextCompat.startForegroundService(mContext, intent);
                }
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
    }
}
