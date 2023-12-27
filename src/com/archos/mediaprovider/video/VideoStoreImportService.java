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

import static com.archos.filecorelibrary.FileUtils.canReadExternalStorage;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
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

import com.archos.environment.ArchosUtils;
import com.archos.mediacenter.utils.AppState;
import com.archos.medialib.R;
import com.archos.mediaprovider.ArchosMediaIntent;
import com.archos.mediaprovider.DeleteFileCallback;
import com.archos.mediaprovider.ImportState;
import com.archos.mediaprovider.VideoDb;
import com.archos.mediaprovider.VolumeState;
import com.archos.mediaprovider.ImportState.State;
import com.archos.mediaprovider.VolumeState.Volume;
import com.archos.mediascraper.Scraper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.sentry.SentryLevel;

public class VideoStoreImportService extends Service implements Handler.Callback {
    private static final Logger log = LoggerFactory.getLogger(VideoStoreImportService.class);

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
    private static volatile boolean sActive = false;

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
        log.debug("VideoStoreImportService CTOR");
    }

    @Override
    protected void finalize() throws Throwable {
        log.debug("VideoStoreImportService DTOR");
        super.finalize();
    }

    public static boolean startIfHandles(Context context, Intent broadcast) {
        log.debug("startIfHandles");
        mContext = context;
        String action = broadcast.getAction();
        if (Intent.ACTION_MEDIA_SCANNER_FINISHED.equals(action)
                || ArchosMediaIntent.ACTION_VIDEO_SCANNER_STORAGE_PERMISSION_GRANTED.equals(action)
                || Intent.ACTION_MEDIA_SCANNER_STARTED.equals(action)
                || Intent.ACTION_MEDIA_SCANNER_SCAN_FILE.equals(action)
                || ArchosMediaIntent.ACTION_VIDEO_SCANNER_METADATA_UPDATE.equals(action)
                || ArchosMediaIntent.isVideoRemoveIntent(action)
                || Intent.ACTION_SHUTDOWN.equals(action)
                || ArchosMediaIntent.ACTION_VIDEO_SCANNER_IMPORT_INCR.equals(action)) {
            log.debug("startIfHandles is true: sending intent to VideoStoreImportService");
            Intent serviceIntent = new Intent(context, VideoStoreImportService.class);
            serviceIntent.setAction(action);
            serviceIntent.setData(broadcast.getData());
            if(broadcast.getExtras()!=null)
                serviceIntent.putExtras(broadcast.getExtras()); //in case we have an extra... such as "recordLogExtra"
            if (AppState.isForeGround()) {
                log.debug("startIfHandles: apps is foreground startForegroundService and pass intent to self");
                ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.startIfHandles", "apps is foreground startForegroundService and pass intent to self");
                ContextCompat.startForegroundService(context, serviceIntent);
            }
            return true;
        }
        log.debug("startIfHandles is false: do nothing");
        return false;
    }

    private Notification createNotification() {
        log.debug("createNotification");
        // need to do that early to avoid ANR on Android 26+
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel nc = new NotificationChannel(notifChannelId, notifChannelName,
                    nm.IMPORTANCE_LOW);
            nc.setDescription(notifChannelDescr);
            if (nm != null)
                nm.createNotificationChannel(nc);
        }
        return new NotificationCompat.Builder(this, notifChannelId)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(getString(R.string.video_store_import))
                .setContentText("")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setTicker(null).setOnlyAlertOnce(true).setOngoing(true).setAutoCancel(true)
                .build();
    }

    @Override
    public void onCreate() {
        // executed on each startService
        if (nm == null && n == null) {
            n = createNotification();
        }
        startForeground(NOTIFICATION_ID, n);
        log.debug("onCreate: created notification + startForeground " + NOTIFICATION_ID + " notification null? " + (n == null));
        ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onCreate", "created notification + startForeground " + NOTIFICATION_ID + " notification null? " + (n == null) + " isForeground=" + AppState.isForeGround());

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
                    log.debug("Change:" + volume.getMountPoint() + " to " + volume.getMountState());
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
                log.debug("onForeGroundState:" + foreground);
                // when switching to foreground state and db
                // has potentially changed: trigger db import
                if (foreground) {
                    mVolumeState.registerReceiver();
                    mVolumeState.updateState();

                    if (ImportState.VIDEO.isDirty()) {
                        log.debug("onCreate: onForeGround && ImportState.isDirty MESSAGE_IMPORT_FULL");
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
    }

    @Override
    public void onDestroy() {
        log.debug("onDestroy");
        sActive = false;
        AppState.removeOnForeGroundListener(mForeGroundListener);
        mForeGroundListener = null;
        // stop handler thread
        mHandlerThread.quit();
        mImporter.destroy();
        if (ImportState.VIDEO.isInitialImport()) ImportState.VIDEO.setState(State.IDLE);
        // hide notification
        if (AppState.isForeGround()) {
            log.debug("onDestroy: app is in foreground stopForeground");
            ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onDestroy", "app is in foreground stopForeground");
            stopForeground(true);
        } else {
            log.debug("onDestroy: app is in background stopForeground+stopSelf");
            // if app goes in background stop foreground service and stopSelf
            ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onDestroy", "app is in background stopForeground+stopSelf");
            stopForeground(true);
            stopSelf();
        }
    }

    /** wether it's ok do do an import now, will mark db dirty if not */
    protected static boolean importOk() {
        if (AppState.isForeGround()) {
            return true;
        }
        log.debug("Ignoring DB update, App is not in foreground.");
        ImportState.VIDEO.setDirty(true);
        return false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // intents are delivered here.
        log.debug("onStartCommand:" + intent + " flags:" + flags + " startId:" + startId + ((intent != null) ? ", getAction " + intent.getAction() : " getAction null"));

        ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onStartCommand", "created notification + startForeground " + NOTIFICATION_ID + " notification null? " + (n == null));
        log.debug("onStartCommand: created notification + startForeground " + NOTIFICATION_ID + " notification null? " + (n == null));

        startForeground(NOTIFICATION_ID, n);

        if (intent == null || intent.getAction() == null) {
            // do a full import here to make sure that we have initial data
            log.debug("onStartCommand: intent == null || intent.getAction() == null do MESSAGE_IMPORT_FULL");
            ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onStartCommand", "intent null: do MESSAGE_IMPORT_FULL");
            removeAllMessages(mHandler);
            Message m;
            if (sActive) { // not first start
                // TODO should be incremental but for now it is always full import
                //m = mHandler.obtainMessage(MESSAGE_IMPORT_INCR, DONT_KILL_SELF, 0);
                m = mHandler.obtainMessage(MESSAGE_IMPORT_FULL, DONT_KILL_SELF, 0);
            } else { // first start set sActive and do full import
                m = mHandler.obtainMessage(MESSAGE_IMPORT_FULL, DONT_KILL_SELF, 0);
                sActive = true;
            }
            // assume this is the initial import although there could be data in the db already.
            log.trace("onStartCommand: ImportState.VIDEO.setState(State.INITIAL_IMPORT)");
            ImportState.VIDEO.setState(State.INITIAL_IMPORT);
            // we also may need to init scraper default content
            mNeedToInitScraper = true;
            mHandler.sendMessageDelayed(m, 1000);
        } else {
            // forward startId to handler thread
            // /!\ if an action is added CHECK in startIfHandles if action is listed /!\
            String action = intent.getAction();
            // stopForeground needs to be called at each action finished when service gets idle: this is taken care by handleMessage
            if (Intent.ACTION_MEDIA_SCANNER_FINISHED.equals(action) || ArchosMediaIntent.ACTION_VIDEO_SCANNER_STORAGE_PERMISSION_GRANTED.equals(action)) {
                log.debug("ACTION_MEDIA_SCANNER_FINISHED " + intent.getData());
                ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onStartCommand", "ACTION_MEDIA_SCANNER_FINISHED" + intent.getData());
                // happens rarely, on boot and when inserting / ejecting sd cards
                removeAllMessages(mHandler);
                Message m = mHandler.obtainMessage(MESSAGE_IMPORT_FULL, startId, flags);
                mHandler.sendMessageDelayed(m, 1000);
                mNeedToInitScraper = true;
                log.trace("onStartCommand: ImportState.VIDEO.setAndroidScanning(false)");
                ImportState.VIDEO.setAndroidScanning(false);
            } else if (Intent.ACTION_MEDIA_SCANNER_STARTED.equals(action)) {
                log.debug("ACTION_MEDIA_SCANNER_STARTED " + intent.getData());
                ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onStartCommand", "ACTION_MEDIA_SCANNER_STARTED " + intent.getData());
                removeAllMessages(mHandler);
                log.trace("onStartCommand: ImportState.VIDEO.setAndroidScanning(true)");
                ImportState.VIDEO.setAndroidScanning(true);
            } else if (ArchosMediaIntent.ACTION_VIDEO_SCANNER_METADATA_UPDATE.equals(action)) {
                log.debug("onStartCommand: ACTION_VIDEO_SCANNER_METADATA_UPDATE " + intent.getData());
                ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onStartCommand", "ACTION_MEDIA_SCANNER_STARTED " + intent.getData());
                // requests to update metadata are processed directly and don't impact importing
                log.debug("onStartCommand: SCAN STARTED " + intent.getData());
                Message m = mHandler.obtainMessage(MESSAGE_UPDATE_METADATA, startId, flags, intent.getData());
                m.sendToTarget();
            } else if (ArchosMediaIntent.isVideoRemoveIntent(action)) {
                ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onStartCommand", "removeIntent " + intent.getData());
                // requests to remove files are processed directly and don't impact importing
                Message m = mHandler.obtainMessage(MESSAGE_REMOVE_FILE, startId, flags, intent.getData());
                m.sendToTarget();
            } else if (Intent.ACTION_SHUTDOWN.equals(action)) {
                log.debug("onStartCommand: Import disabled due to shutdown");
                ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onStartCommand", "ACTION_SHUTDOWN");
                Message m = mHandler.obtainMessage(MESSAGE_KILL, startId, flags);
                mHandler.sendMessageDelayed(m, 1000);
            } else if (ArchosMediaIntent.ACTION_VIDEO_SCANNER_IMPORT_INCR.equals(action)) {
                log.debug("onStartCommand: ACTION_VIDEO_SCANNER_IMPORT_INCR " + intent.getData());
                ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onStartCommand", "ACTION_VIDEO_SCANNER_IMPORT_INCR " + intent.getData());
                removeAllMessages(mHandler);
                Message m = mHandler.obtainMessage(MESSAGE_IMPORT_INCR, startId, flags);
                mHandler.sendMessageDelayed(m, 1000);
            } else if (Intent.ACTION_MEDIA_SCANNER_SCAN_FILE.equals(action)) {
                log.debug("onStartCommand: ACTION_MEDIA_SCANNER_SCAN_FILE " + intent.getData());
                ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onStartCommand", "ACTION_MEDIA_SCANNER_SCAN_FILE " + intent.getData());
                Message m = mHandler.obtainMessage(MESSAGE_UPDATE_METADATA, startId, flags, intent.getData());
                m.sendToTarget();
            } else {
                ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onStartCommand", "intent not treated, stopForeground");
                log.warn("onStartCommand: intent not treated, stopForeground");
                // not calling handleMessage thus stopForeground
                stopForeground(true);
            }
        }
        return Service.START_NOT_STICKY;
    }

    public static void startService(Context context) {
        // this one is called only by VideoProvider at start or when app turns background->foreground
        log.debug("startService");
        mContext = context;
        Intent intent = new Intent(context, VideoStoreImportService.class);
        if (AppState.isForeGround()) {
            ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.startService", "app in foreground calling ContextCompat.startForegroundService");
            ContextCompat.startForegroundService(context, intent); // triggers an initial video import on local storage because files might have been created meanwhile
            log.debug("startService: app in foreground calling ContextCompat.startForegroundService");
        } else {
            ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.startService", "app in background NOT calling ContextCompat.startForegroundService");
            log.debug("startService: app in background NOT calling ContextCompat.startForegroundService");
        }
        // it used to be a bound service but with Android O it is not anymore
        // context.bindService(intent, new LoggingConnection(), Context.BIND_AUTO_CREATE);
    }

    public static void stopService(Context context) {
        log.debug("stopService");
        ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.stopService", "stopping service");
        Intent intent = new Intent(context, VideoStoreImportService.class);
        intent.setAction(Intent.ACTION_SHUTDOWN);
        context.stopService(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        log.debug("onBind:" + intent);
        return null;
    }
    @Override
    public boolean onUnbind(Intent intent) {
        log.debug("onUnbind:" + intent);
        // unregister content observer
        getContentResolver().unregisterContentObserver(mContentObserver);
        return super.onUnbind(intent);
    }

    /** handler implementation, called in background thread */
    public boolean handleMessage(Message msg) {
        log.debug("handleMessage:" + msg + " what:" + msg.what + " startid:" + msg.arg1);
        switch (msg.what) {
            case MESSAGE_KILL:
                log.debug("handleMessage: MESSAGE_KILL: stopForeground");
                if (ImportState.VIDEO.isInitialImport()) ImportState.VIDEO.setState(State.IDLE);
                ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.handleMessage", "MESSAGE_KILL: stopForeground");
                stopForeground(true);
                // this service used to be created through bind. So it couldn't be killed with stopself unless it was unbind
                // (which wasn't done). To have the same behavior, do not stop service for now
                if (msg.arg1 != DONT_KILL_SELF){
                    log.debug("handleMessage: stopSelf");
                    ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.handleMessage", "MESSAGE_KILL: stopSelf");
                    sActive = false;
                    stopSelf(msg.arg1);
                } else {
                    ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.handleMessage", "MESSAGE_KILL: do not stopSelf");
                    log.debug("handleMessage: MESSAGE_KILL: do not stopSelf");
                }
                break;
            case MESSAGE_IMPORT_INCR:
                log.debug("handleMessage: MESSAGE_IMPORT_INCR");
                doImport(false);
                mHandler.obtainMessage(MESSAGE_KILL, DONT_KILL_SELF, msg.arg2).sendToTarget();
                break;
            case MESSAGE_IMPORT_FULL:
                log.debug("handleMessage: MESSAGE_IMPORT_FULL");
                doImport(true);
                mHandler.obtainMessage(MESSAGE_KILL, DONT_KILL_SELF, msg.arg2).sendToTarget();
                break;
            case MESSAGE_UPDATE_METADATA:
                log.debug("handleMessage: MESSAGE_UPDATE_METADATA");
                mImporter.doScan((Uri)msg.obj);
                mHandler.obtainMessage(MESSAGE_KILL, DONT_KILL_SELF, msg.arg2).sendToTarget();
                break;
            case MESSAGE_REMOVE_FILE:
                log.debug("handleMessage: MESSAGE_REMOVE_FILE");
                mImporter.doRemove((Uri)msg.obj);
                mHandler.obtainMessage(MESSAGE_KILL, DONT_KILL_SELF, msg.arg2).sendToTarget();
                break;
            case MESSAGE_HIDE_VOLUME:
                log.debug("handleMessage: MESSAGE_HIDE_VOLUME");
                // insert the storage_id that's to be hidden into the special trigger view thingy
                ContentValues cv = new ContentValues();
                cv.put(VideoStore.Files.FileColumns.STORAGE_ID, String.valueOf(msg.arg2));
                VideoStoreImportService.this.getContentResolver().insert(VideoStoreInternal.HIDE_VOLUME, cv);
                mHandler.obtainMessage(MESSAGE_KILL, DONT_KILL_SELF, msg.arg2).sendToTarget();
                break;
            default:
                log.warn("ImportBgHandler - unknown msg.what: " + msg.what + " stopForeground");
                stopForeground(true);
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
        log.debug("doImport: notify notificationId=" + NOTIFICATION_ID + " with tag 'import'");
        if (! canReadExternalStorage(this)) {
            log.debug("doImport: no read permission : stop import");
            return;
        } else
            log.debug("doImport: read permission : continue import");
        ImportState.VIDEO.setDirty(false);
        if (!sActive) {
            log.debug("doImport: import request ignored due to device shutdown.");
            return;
        }
        long start = System.currentTimeMillis();
        if (fullMode)
            mImporter.doFullImport();
        else
            mImporter.doIncrementalImport();
        long end = System.currentTimeMillis();
        log.debug("doImport took:" + (end - start) + "ms full:" + fullMode);
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
            // tidy up the accumulated actor director writer studio genre piled up in v_.*_deletable tables in one shot during deletes
            // it has been moved from scraperTables triggers here to gain in efficiency
            db.execSQL("delete from actor where _id in (select _id from v_actor_deletable)");
            db.execSQL("delete from director where _id in (select _id from v_director_deletable)");
            db.execSQL("delete from writer where _id in (select _id from v_writer_deletable)");
            db.execSQL("delete from studio where _id in (select _id from v_studio_deletable)");
            db.execSQL("delete from genre where _id in (select _id from v_genre_deletable)");
        } catch (SQLException | IllegalStateException e) {
            log.error("processDeleteFileAndVobCallback: SQLException or IllegalStateException",e);
        } finally {
            if (c != null) c.close();
        }

        // break down the scan in batch of WINDOW_SIZE in order to avoid SQLiteBlobTooBigException: Row too big to fit into CursorWindow crash
        // note that the db is being modified during import
        while (true) {
            try {
                c = db.rawQuery("SELECT * FROM delete_files WHERE name IN (SELECT cover_movie FROM MOVIE UNION SELECT cover_show FROM SHOW UNION SELECT cover_episode FROM EPISODE) ORDER BY " + BaseColumns._ID + " ASC LIMIT " + WINDOW_SIZE, null);
                log.debug("processDeleteFileAndVobCallback: delete_files cover_movie new batch fetching window=" + WINDOW_SIZE + " -> cursor has size " + c.getCount());
                if (c.getCount() == 0) {
                    log.debug("processDeleteFileAndVobCallback: delete_files cover_movie no more data");
                    break; // break out if no more data
                }
                while (c.moveToNext()) {
                    long id = c.getLong(0);
                    String path = c.getString(1);
                    long count = c.getLong(2);
                    log.debug("processDeleteFileAndVobCallback: clean delete_files " + String.valueOf(id) + " path " + path + " count " + String.valueOf(count));
                    // purge the db: delete row even if file delete callback fails (file deletion could be handled elsewhere
                    try {
                        db.execSQL("DELETE FROM delete_files WHERE _id=" + String.valueOf(id) + " AND name='" + path + "'");
                    } catch (SQLException sqlE) {
                        log.error("processDeleteFileAndVobCallback: SQLException", sqlE);
                    }
                }
            } catch (SQLException | IllegalStateException e) {
                log.error("processDeleteFileAndVobCallback: SQLException or IllegalStateException",e);
            } finally {
                if (c != null) c.close();
            }
        }

        // note: seems that the delete is performed not as a table trigger anymore but elsewhere
        // break down the scan in batch of WINDOW_SIZE in order to avoid SQLiteBlobTooBigException: Row too big to fit into CursorWindow crash
        // note that the db is being modified during import
        while (true) {
            try {
                c = db.rawQuery("SELECT * FROM delete_files ORDER BY " + BaseColumns._ID + " ASC LIMIT " + WINDOW_SIZE, null);
                log.debug("processDeleteFileAndVobCallback: delete_files new batch fetching window=" + WINDOW_SIZE + " -> cursor has size " + c.getCount());
                if (c.getCount() == 0) {
                    log.debug("processDeleteFileAndVobCallback: delete_files no more data");
                    break; // break out if no more data
                }
                while (c.moveToNext()) {
                    long id = c.getLong(0);
                    String path = c.getString(1);
                    long count = c.getLong(2);
                    log.debug("processDeleteFileAndVobCallback: delete_files " + String.valueOf(id) + " path " + path + " count " + String.valueOf(count));
                    DeleteFileCallbackArgs = new String[] {path, String.valueOf(count)};
                    delCb.callback(DeleteFileCallbackArgs);
                    // purge the db: delete row even if file delete callback fails (file deletion could be handled elsewhere
                    try {
                        db.execSQL("DELETE FROM delete_files WHERE _id=" + String.valueOf(id) + " AND name='" + path + "'");
                    } catch (SQLException sqlE) {
                        log.error("processDeleteFileAndVobCallback: SQLException", sqlE);
                    }
                }
            } catch (SQLException | IllegalStateException e) {
                log.error("processDeleteFileAndVobCallback: SQLException or IllegalStateException",e);
            } finally {
                if (c != null) c.close();
            }
        }

        // break down the scan in batch of WINDOW_SIZE in order to avoid SQLiteBlobTooBigException: Row too big to fit into CursorWindow crash
        // note that the db is being modified during import
        while (true) {
            try {
                c = db.rawQuery("SELECT * FROM vob_insert ORDER BY " + BaseColumns._ID + " ASC LIMIT " + WINDOW_SIZE, null);
                log.debug("processDeleteFileAndVobCallback: delete_files new batch fetching window=" + WINDOW_SIZE + " -> cursor has size " + c.getCount());
                if (c.getCount() == 0) {
                    log.debug("processDeleteFileAndVobCallback: vob_insert no more data");
                    break; // break out if no more data
                }
                while (c.moveToNext()) {
                    long id = c.getLong(0);
                    String path = c.getString(1);
                    long count = c.getLong(2);
                    log.debug("processDeleteFileAndVobCallback: vob_insert " + String.valueOf(id) + " path " + path + " count " + String.valueOf(count));
                    VobUpdateCallbackArgs = new String[] {path};
                    vobCb.callback(VobUpdateCallbackArgs);
                    // purge the db: delete row even if file delete callback fails (file deletion could be handled elsewhere
                    try {
                        db.execSQL("DELETE FROM vob_insert WHERE _id=" + String.valueOf(id) + " AND name='" + path + "'");
                    } catch (SQLException sqlE) {
                        log.error("processDeleteFileAndVobCallback: SQLException", sqlE);
                    }
                }
            } catch (SQLException | IllegalStateException e) {
                log.error("processDeleteFileAndVobCallback: SQLException or IllegalStateException",e);
            } finally {
                if (c != null) c.close();
            }
        }
        // don't db.close() - shared connection
    }

    /** removes all messages from handler */
    protected static void removeAllMessages(Handler handler) {
        log.debug("removeAllMessages");
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
            log.debug("onChange");
            // to avoid sending message to dead thread because mHandlerThread is no more, need to relaunch the service so that it is recreated in onCreate
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
                log.debug("onChange: triggering VIDEO_SCANNER_IMPORT_INCR");
                Intent intent = new Intent(mContext, VideoStoreImportService.class);
                intent.setAction(ArchosMediaIntent.ACTION_VIDEO_SCANNER_IMPORT_INCR);
                if (AppState.isForeGround()) {
                    ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onChange", "app in foreground calling ContextCompat.startForegroundService");
                    log.debug("onChange: app in foreground calling ContextCompat.startForegroundService");
                    ContextCompat.startForegroundService(mContext, intent);
                } else {
                    ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onChange", "app in background NOT calling ContextCompat.startForegroundService");
                    log.debug("onChange: app in background NOT calling ContextCompat.startForegroundService");
                }
            }
        }
    }

    /** ServiceConnection that will only do logging */
    private static class LoggingConnection implements ServiceConnection {
        public LoggingConnection() {
        }
        public void onServiceConnected(ComponentName name, IBinder service) {
            log.debug("onServiceConnected");
        }
        public void onServiceDisconnected(ComponentName name) {
            log.debug("onServiceDisconnected");
        }
    }

    /** calls {@link IScraperService#setupDefaultContent(boolean) }*/
    private void initializeScraperData() {
        log.debug("initializeScraperData()");
        Scraper scraper = new Scraper(this);
    }
}
