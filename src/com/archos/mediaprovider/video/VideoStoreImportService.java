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

import android.app.ForegroundServiceStartNotAllowedException;
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

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.archos.environment.ArchosUtils;
import com.archos.medialib.R;
import com.archos.mediaprovider.ArchosMediaIntent;
import com.archos.mediaprovider.DeleteFileCallback;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediaprovider.ImportState;
import com.archos.mediaprovider.MediaRetrieverService;
import com.archos.mediaprovider.VideoDb;
import com.archos.mediaprovider.VolumeState;
import com.archos.mediaprovider.ImportState.State;
import com.archos.mediaprovider.VolumeState.Volume;
import com.archos.mediascraper.Scraper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.sentry.SentryLevel;

public class VideoStoreImportService extends Service implements Handler.Callback, DefaultLifecycleObserver {
    private static final Logger log = LoggerFactory.getLogger(VideoStoreImportService.class);

    // handler message ids
    private static final int MESSAGE_KILL = 1;
    private static final int MESSAGE_IMPORT_FULL = 2;
    private static final int MESSAGE_IMPORT_INCR = 3;
    private static final int MESSAGE_UPDATE_METADATA = 4;
    private static final int MESSAGE_REMOVE_FILE = 5;
    private static final int MESSAGE_HIDE_VOLUME = 6;
    private static final int MESSAGE_VOLUME_MOUNTED = 7;

    // handler.arg1 contains startId or this
    private static final int DONT_KILL_SELF = -1;

    // true until shutdown. Static because this is true for every instance
    private static volatile boolean sActive = false;

    protected Handler mHandler;
    private HandlerThread mHandlerThread;
    private volatile boolean mCleanupCalled = false;
    private VideoStoreImportImpl mImporter;
    private ContentObserver mContentObserver;
    private boolean mNeedToInitScraper = false;

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

    private final static boolean CRASH_ON_ERROR = false;

    private static volatile boolean isForeground = true;

    public VideoStoreImportService() {
        if (log.isDebugEnabled()) log.debug("VideoStoreImportService CTOR");
    }

    @Override
    protected void finalize() throws Throwable {
        if (log.isDebugEnabled()) log.debug("VideoStoreImportService DTOR");
        super.finalize();
    }

    @SuppressWarnings("deprecation") // ACTION_MEDIA_SCANNER_SCAN_FILE reused as internal IPC action string
    public static boolean startIfHandles(Context context, Intent broadcast) {
        String action = broadcast.getAction();
        if (log.isDebugEnabled()) log.debug("startIfHandles: action {}, data {}, extra {}", action, broadcast.getData(), broadcast.getAction());
        if (! ProcessLifecycleOwner.get().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
            if (log.isDebugEnabled()) log.debug("startIfHandles: not in foreground, do nothing");
            return false;
        }
        mContext = context;
        if (Intent.ACTION_MEDIA_SCANNER_FINISHED.equals(action)
                || ArchosMediaIntent.ACTION_VIDEO_SCANNER_STORAGE_PERMISSION_GRANTED.equals(action)
                || Intent.ACTION_MEDIA_SCANNER_STARTED.equals(action)
                || Intent.ACTION_MEDIA_SCANNER_SCAN_FILE.equals(action)
                || ArchosMediaIntent.ACTION_VIDEO_SCANNER_METADATA_UPDATE.equals(action)
                || ArchosMediaIntent.isVideoRemoveIntent(action)
                || Intent.ACTION_SHUTDOWN.equals(action)
                || ArchosMediaIntent.ACTION_VIDEO_SCANNER_IMPORT_INCR.equals(action)) {
            if (log.isDebugEnabled()) log.debug("startIfHandles is true: sending intent to VideoStoreImportService");
            Intent serviceIntent = new Intent(context, VideoStoreImportService.class);
            serviceIntent.setAction(action);
            serviceIntent.setData(broadcast.getData());
            if (log.isDebugEnabled()) log.debug("startIfHandles: apps is foreground startService and pass intent to self");
            ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.startIfHandles", "apps is foreground mContext.startService and pass intent to self");
            context.startService(serviceIntent);
            return true;
        }
        if (log.isDebugEnabled()) log.debug("startIfHandles is false: do nothing");
        return false;
    }

    private Notification createNotification() {
        if (log.isDebugEnabled()) log.debug("createNotification");
        // need to do that early to avoid ANR on Android 26+
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel nc = new NotificationChannel(notifChannelId, notifChannelName,
                    NotificationManager.IMPORTANCE_LOW);
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
        if (log.isDebugEnabled()) log.debug("onCreate");
        // executed on each startService
        n = createNotification();
        if (log.isDebugEnabled()) log.debug("onCreate: create notification + startService {}", NOTIFICATION_ID);
        ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onCreate", "created notification + startService " + NOTIFICATION_ID + " notification null? " + (n == null));
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
        mVolumeStateObserver = volumes -> {
            for (Volume volume : volumes) {
                if (log.isDebugEnabled()) log.debug("Change:{} to {}", volume.getMountPoint(), volume.getMountState());
                if (!volume.getMountState()) {
                    mHandler
                        .obtainMessage(MESSAGE_HIDE_VOLUME, DONT_KILL_SELF, volume.getStorageId())
                        .sendToTarget();
                } else {
                    mHandler
                        .obtainMessage(MESSAGE_VOLUME_MOUNTED, DONT_KILL_SELF, volume.getStorageId())
                        .sendToTarget();
                }
            }
        };
        mVolumeState.addObserver(mVolumeStateObserver);
        // fetch initial state
        mVolumeState.updateState();
        // we are most likely started in foreground but won't get a notification
        // on the listener so register receiver manually.
        if (isForeground) mVolumeState.registerReceiver();

        // register contentobserver for files and videos, on change we import them
        try {
            getContentResolver().registerContentObserver(MediaStore.Files.getContentUri("external"),
                    true, mContentObserver);
            getContentResolver().registerContentObserver(MediaStore.Video.Media.getContentUri("external"),
                    true, mContentObserver);
        } catch (SecurityException e) {
            log.warn("Failed to register MediaStore ContentObserver - MediaProvider may not be available on this device", e);
            // Continue without MediaStore observation - the service will still function
            // but won't automatically detect external changes to the MediaStore database
        }

        // Register as a lifecycle observer AFTER all initialization is complete
        // This prevents onStart() from being called before mHandler is created
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    }

    @Override
    public void onDestroy() {
        if (log.isDebugEnabled()) log.debug("onDestroy - ImportState before: {}", ImportState.VIDEO.getState());
        isForeground = false;
        cleanup();
        // Reset ImportState to IDLE regardless of current state to prevent stuck spinner
        if (ImportState.VIDEO.isInitialImport() || ImportState.VIDEO.isRegularImport()) {
            if (log.isDebugEnabled()) log.debug("onDestroy - Resetting ImportState to IDLE");
            ImportState.VIDEO.setState(State.IDLE);
        }
        if (log.isDebugEnabled()) log.debug("onDestroy - ImportState after: {}", ImportState.VIDEO.getState());
    }

    /** whether it's ok do do an import now, will mark db dirty if not */
    protected static boolean importOk() {
        return ! ImportState.VIDEO.isDirty();
    }

    @SuppressWarnings("deprecation") // ACTION_MEDIA_SCANNER_SCAN_FILE reused as internal IPC action string
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // intents are delivered here.
        if (log.isDebugEnabled()) log.debug("onStartCommand:{} flags:{} startId:{} getAction {}", intent, flags, startId, ((intent != null) ? intent.getAction() : "null"));
        ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onStartCommand", "created notification + startService " + NOTIFICATION_ID + " notification null? " + (n == null));
        if (intent == null || intent.getAction() == null) {
            removeAllMessages(mHandler);
            Message m;
            if (sActive) { // not first start
                // Post-Android P: Use incremental import since our enhanced volume management handles external storage properly
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                    if (log.isDebugEnabled()) log.debug("onStartCommand: intent == null || intent.getAction() == null, sActive == true, do MESSAGE_IMPORT_INCR (post-Android P)");
                    ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onStartCommand", "intent null, sActive true do MESSAGE_IMPORT_INCR (post-Android P)");
                    m = mHandler.obtainMessage(MESSAGE_IMPORT_INCR, DONT_KILL_SELF, 0);
                } else {
                    // Pre-Android P: Keep full import for external USB storage compatibility
                    if (log.isDebugEnabled()) log.debug("onStartCommand: intent == null || intent.getAction() == null, sActive == true, do MESSAGE_IMPORT_FULL (pre-Android P)");
                    ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onStartCommand", "intent null, sActive true do MESSAGE_IMPORT_FULL (pre-Android P)");
                    m = mHandler.obtainMessage(MESSAGE_IMPORT_FULL, DONT_KILL_SELF, 0);
                }
                ImportState.VIDEO.setState(State.REGULAR_IMPORT);
            } else {
                // do a full import here to make sure that we have initial data
                if (log.isDebugEnabled()) log.debug("onStartCommand: intent == null || intent.getAction() == null, sActive == false, do MESSAGE_IMPORT_FULL");
                ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onStartCommand", "intent null, sActive false do MESSAGE_IMPORT_FULL");
                m = mHandler.obtainMessage(MESSAGE_IMPORT_FULL, DONT_KILL_SELF, 0);
                sActive = true;
                ImportState.VIDEO.setState(State.INITIAL_IMPORT);
            }
            // assume this is the initial import although there could be data in the db already.
            // we also may need to init scraper default content
            mNeedToInitScraper = true;
            mHandler.sendMessageDelayed(m, 1000);
        } else {
            // forward startId to handler thread
            // /!\ if an action is added CHECK in startIfHandles if action is listed /!\
            String action = intent.getAction();
            // stopForeground needs to be called at each action finished when service gets idle: this is taken care by handleMessage
            if (Intent.ACTION_MEDIA_SCANNER_FINISHED.equals(action) || ArchosMediaIntent.ACTION_VIDEO_SCANNER_STORAGE_PERMISSION_GRANTED.equals(action)) {
                if (log.isDebugEnabled()) log.debug("ACTION_MEDIA_SCANNER_FINISHED {}", intent.getData());
                ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onStartCommand", "ACTION_MEDIA_SCANNER_FINISHED" + intent.getData());
                // happens rarely, on boot and when inserting / ejecting sd cards
                removeAllMessages(mHandler);
                Message m = mHandler.obtainMessage(MESSAGE_IMPORT_FULL, startId, flags);
                mHandler.sendMessageDelayed(m, 1000);
                mNeedToInitScraper = true;
                if (log.isTraceEnabled()) log.trace("onStartCommand: ImportState.VIDEO.setAndroidScanning(false)");
                ImportState.VIDEO.setAndroidScanning(false);
            } else if (Intent.ACTION_MEDIA_SCANNER_STARTED.equals(action)) {
                if (log.isDebugEnabled()) log.debug("ACTION_MEDIA_SCANNER_STARTED {}", intent.getData());
                ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onStartCommand", "ACTION_MEDIA_SCANNER_STARTED " + intent.getData());
                removeAllMessages(mHandler);
                if (log.isTraceEnabled()) log.trace("onStartCommand: ImportState.VIDEO.setAndroidScanning(true)");
                ImportState.VIDEO.setAndroidScanning(true);
            } else if (ArchosMediaIntent.ACTION_VIDEO_SCANNER_METADATA_UPDATE.equals(action)) {
                if (log.isDebugEnabled()) log.debug("onStartCommand: ACTION_VIDEO_SCANNER_METADATA_UPDATE {}", intent.getData());
                ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onStartCommand", "ACTION_MEDIA_SCANNER_STARTED " + intent.getData());
                // requests to update metadata are processed directly and don't impact importing
                if (log.isDebugEnabled()) log.debug("onStartCommand: SCAN STARTED {}", intent.getData());
                Message m = mHandler.obtainMessage(MESSAGE_UPDATE_METADATA, startId, flags, intent.getData());
                m.sendToTarget();
            } else if (ArchosMediaIntent.isVideoRemoveIntent(action)) {
                ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onStartCommand", "removeIntent " + intent.getData());
                // requests to remove files are processed directly and don't impact importing
                Message m = mHandler.obtainMessage(MESSAGE_REMOVE_FILE, startId, flags, intent.getData());
                m.sendToTarget();
            } else if (Intent.ACTION_SHUTDOWN.equals(action)) {
                if (log.isDebugEnabled()) log.debug("onStartCommand: Import disabled due to shutdown");
                ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onStartCommand", "ACTION_SHUTDOWN");
                Message m = mHandler.obtainMessage(MESSAGE_KILL, startId, flags);
                mHandler.sendMessageDelayed(m, 1000);
            } else if (ArchosMediaIntent.ACTION_VIDEO_SCANNER_IMPORT_INCR.equals(action)) {
                if (log.isDebugEnabled()) log.debug("onStartCommand: ACTION_VIDEO_SCANNER_IMPORT_INCR {}", intent.getData());
                ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onStartCommand", "ACTION_VIDEO_SCANNER_IMPORT_INCR " + intent.getData());
                removeAllMessages(mHandler);
                Message m = mHandler.obtainMessage(MESSAGE_IMPORT_INCR, startId, flags);
                mHandler.sendMessageDelayed(m, 1000);
            } else if (Intent.ACTION_MEDIA_SCANNER_SCAN_FILE.equals(action)) {
                if (log.isDebugEnabled()) log.debug("onStartCommand: ACTION_MEDIA_SCANNER_SCAN_FILE {}", intent.getData());
                ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onStartCommand", "ACTION_MEDIA_SCANNER_SCAN_FILE " + intent.getData());
                Message m = mHandler.obtainMessage(MESSAGE_UPDATE_METADATA, startId, flags, intent.getData());
                m.sendToTarget();
            } else {
                ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onStartCommand", "intent not treated, stopForeground");
                log.warn("onStartCommand: intent not treated, stopForeground");
                // not calling handleMessage thus stopForeground
                stopSelf();
            }
        }
        return Service.START_NOT_STICKY;
    }

    public static void startService(Context context) {
        // this one is called only by VideoProvider at start or when app turns background->foreground
        if (log.isDebugEnabled()) log.debug("startService");
        if (! ProcessLifecycleOwner.get().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) return;
        
        // Ensure MediaRetrieverService is running before starting VideoStoreImportService
        Intent mediaRetrieverIntent = new Intent(context, MediaRetrieverService.class);
        try {
            context.startService(mediaRetrieverIntent);
            if (log.isDebugEnabled()) log.debug("startService: MediaRetrieverService start requested");
        } catch (IllegalStateException e) {
            log.warn("startService: Failed to start MediaRetrieverService despite lifecycle check - timing issue", e);
        }
        
        mContext = context;
        Intent intent = new Intent(context, VideoStoreImportService.class);
        ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.startService", "app in foreground calling startService");
        if (log.isDebugEnabled()) log.debug("startService: app in foreground, starting service");
        try {
            context.startService(intent); // triggers an initial video import on local storage because files might have been created meanwhile
        } catch (IllegalStateException e) {
            log.warn("startService: Failed to start VideoStoreImportService despite lifecycle check - timing issue", e);
        }
    }

    public static void stopService(Context context) {
        if (log.isDebugEnabled()) log.debug("stopService");
        ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.stopService", "stopping service");
        Intent intent = new Intent(context, VideoStoreImportService.class);
        intent.setAction(Intent.ACTION_SHUTDOWN);
        context.stopService(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (log.isDebugEnabled()) log.debug("onBind:{}", intent);
        return null;
    }
    @Override
    public boolean onUnbind(Intent intent) {
        if (log.isDebugEnabled()) log.debug("onUnbind:{}", intent);
        // unregister content observer
        if (mContentObserver != null) {
            try {
                getContentResolver().unregisterContentObserver(mContentObserver);
            } catch (Exception e) {
                log.warn("Failed to unregister ContentObserver", e);
            }
        }
        return super.onUnbind(intent);
    }

    /** handler implementation, called in background thread */
    public boolean handleMessage(Message msg) {
        if (log.isDebugEnabled()) log.debug("handleMessage:{} what:{} startid:{}", msg, msg.what, msg.arg1);
        switch (msg.what) {
            case MESSAGE_KILL:
                if (log.isDebugEnabled()) log.debug("handleMessage: MESSAGE_KILL - ImportState before: {}", ImportState.VIDEO.getState());
                // Reset ImportState to IDLE regardless of current state to prevent stuck spinner
                if (ImportState.VIDEO.isInitialImport() || ImportState.VIDEO.isRegularImport()) {
                    if (log.isDebugEnabled()) log.debug("handleMessage: MESSAGE_KILL - Resetting ImportState to IDLE");
                    ImportState.VIDEO.setState(State.IDLE);
                }
                if (log.isDebugEnabled()) log.debug("handleMessage: MESSAGE_KILL - ImportState after: {}", ImportState.VIDEO.getState());
                // this service used to be created through bind. So it couldn't be killed with stopself unless it was unbind
                // (which wasn't done). To have the same behavior, do not stop service for now
                if (log.isDebugEnabled()) log.debug("handleMessage: MESSAGE_KILL -> leaving foreground");
                // Always clear the foreground notification when processing has finished. Some
                // commands (remove file, metadata update, etc.) do not go through doImport()
                // and were previously leaving the foreground notification visible forever.
                stopForeground(true);
                if (msg.arg1 != DONT_KILL_SELF){
                    if (log.isDebugEnabled()) log.debug("handleMessage: stopSelf");
                    ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.handleMessage", "MESSAGE_KILL: stopSelf");
                    sActive = false;
                    stopSelf(msg.arg1);
                } else {
                    ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.handleMessage", "MESSAGE_KILL: do not stopSelf");
                    if (log.isDebugEnabled()) log.debug("handleMessage: MESSAGE_KILL: do not stopSelf");
                }
                break;
            case MESSAGE_IMPORT_INCR:
                // Incremental import: runs while user may be active (bookmarks, watched state).
                // Do not throttle notifications so user-visible updates remain responsive.
                if (log.isDebugEnabled()) log.debug("handleMessage: MESSAGE_IMPORT_INCR");
                doImport(false);
                if (!mCleanupCalled) mHandler.obtainMessage(MESSAGE_KILL, DONT_KILL_SELF, msg.arg2).sendToTarget();
                break;
            case MESSAGE_IMPORT_FULL:
                // Full import: long-running bulk write (startup or new storage). Throttle
                // notifications to avoid flooding UI CursorLoaders with SQLite interrupted errors.
                if (log.isDebugEnabled()) log.debug("handleMessage: MESSAGE_IMPORT_FULL");
                VideoProvider.setImportInProgress(true);
                try {
                    doImport(true);
                } finally {
                    VideoProvider.setImportInProgress(false);
                    // Unconditional refresh: covers both normal completion and partial writes
                    // if doImport() throws after DB mutations have already occurred.
                    getContentResolver().notifyChange(VideoStore.ALL_CONTENT_URI, null);
                }
                if (!mCleanupCalled) mHandler.obtainMessage(MESSAGE_KILL, DONT_KILL_SELF, msg.arg2).sendToTarget();
                break;
            case MESSAGE_UPDATE_METADATA:
                if (log.isDebugEnabled()) log.debug("handleMessage: MESSAGE_UPDATE_METADATA");
                mImporter.doScan((Uri)msg.obj);
                if (!mCleanupCalled) mHandler.obtainMessage(MESSAGE_KILL, DONT_KILL_SELF, msg.arg2).sendToTarget();
                break;
            case MESSAGE_REMOVE_FILE:
                if (log.isDebugEnabled()) log.debug("handleMessage: MESSAGE_REMOVE_FILE");
                mImporter.doRemove((Uri)msg.obj);
                if (!mCleanupCalled) mHandler.obtainMessage(MESSAGE_KILL, DONT_KILL_SELF, msg.arg2).sendToTarget();
                break;
            case MESSAGE_HIDE_VOLUME:
                if (log.isDebugEnabled()) log.debug("handleMessage: MESSAGE_HIDE_VOLUME storageId={}", msg.arg2);
                // Trigger import to hide files using path-based volume checking
                // This ensures volume state is properly checked and files are hidden
                // while other mounted volumes remain visible
                mHandler.obtainMessage(MESSAGE_IMPORT_INCR, DONT_KILL_SELF, msg.arg2).sendToTarget();
                break;
            case MESSAGE_VOLUME_MOUNTED:
                if (log.isDebugEnabled()) log.debug("handleMessage: MESSAGE_VOLUME_MOUNTED storageId={}", msg.arg2);
                handleVolumeMounted(msg.arg2);
                // After restoring visibility trigger a new import to refresh metadata
                Message importMsg = mHandler.obtainMessage(MESSAGE_IMPORT_FULL, DONT_KILL_SELF, msg.arg2);
                mHandler.sendMessageDelayed(importMsg, 1000);
                break;
            default:
                log.warn("ImportBgHandler - unknown msg.what: {} stopSelf", msg.what);
                stopSelf();
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
        if (log.isDebugEnabled()) log.debug("doImport: fullMode={}, notificationId={}", fullMode, NOTIFICATION_ID);
        if (! canReadExternalStorage(this)) {
            if (log.isDebugEnabled()) log.debug("doImport: no read permission : stop import");
            return;
        } else
            if (log.isDebugEnabled()) log.debug("doImport: read permission : continue import");
        if (!isForeground) {
            if (log.isDebugEnabled()) log.debug("doImport: import request ignored due to device shutdown.");
            return;
        }
        long start = System.currentTimeMillis();
        if (fullMode)
            mImporter.doFullImport();
        else
            mImporter.doIncrementalImport();
        long end = System.currentTimeMillis();
        if (log.isDebugEnabled()) log.debug("doImport took:{}ms full:{}", (end - start), fullMode);
        // perform no longer possible delete_file and vob_insert db callbacks after incr or full import
        // this will also flush delete_files and vob_insert buffer tables
        processDeleteFileAndVobCallback();
        ImportState.VIDEO.setDirty(false);
        if (log.isDebugEnabled()) log.debug("doImport: not dirty anymore");
        // notify all that we have new stuff
        Intent intent = new Intent(ArchosMediaIntent.ACTION_VIDEO_SCANNER_SCAN_FINISHED, null);
        intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
        sendBroadcast(intent);

        // Explicitly start AutoScrapeService after scan completes to ensure scraping happens
        // This is needed because the ContentObserver may not reliably trigger during batch inserts
        if (com.archos.mediascraper.AutoScrapeService.isEnable(this)) {
            if (log.isDebugEnabled()) log.debug("doImport: starting AutoScrapeService after scan completion");
            com.archos.mediascraper.AutoScrapeService.startService(this);
        }
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
        int cCount = 0;

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
        while (isForeground) {
            try {
                c = db.rawQuery("SELECT * FROM delete_files WHERE name IN (" +
                        "SELECT cover_movie FROM MOVIE UNION " +
                        "SELECT backdrop_movie FROM MOVIE UNION " +
                        "SELECT cover_show FROM SHOW UNION " +
                        "SELECT backdrop_show FROM SHOW UNION " +
                        "SELECT cover_episode FROM EPISODE UNION " +
                        "SELECT picture_episode FROM EPISODE UNION " +
                        "SELECT m_po_large_file FROM movie_posters UNION " +
                        "SELECT m_po_thumb_file FROM movie_posters UNION " +
                        "SELECT m_bd_large_file FROM movie_backdrops UNION " +
                        "SELECT m_bd_thumb_file FROM movie_backdrops UNION " +
                        "SELECT s_po_large_file FROM show_posters UNION " +
                        "SELECT s_po_thumb_file FROM show_posters UNION " +
                        "SELECT s_bd_large_file FROM show_backdrops UNION " +
                        "SELECT s_bd_thumb_file FROM show_backdrops UNION " +
                        "SELECT m_coll_po_large_file FROM movie_collection UNION " +
                        "SELECT m_coll_bd_large_file FROM movie_collection UNION " +
                        "SELECT m_coll_po_thumb_file FROM movie_collection UNION " +
                        "SELECT m_coll_bd_thumb_file FROM movie_collection" +
                        ") ORDER BY " + BaseColumns._ID + " ASC LIMIT " + WINDOW_SIZE, null);
                cCount = c.getCount();
                if (log.isDebugEnabled()) log.debug("processDeleteFileAndVobCallback: delete_files cover_movie new batch fetching window={} -> cursor has size {}", WINDOW_SIZE, cCount);
                if (cCount == 0) {
                    if (log.isDebugEnabled()) log.debug("processDeleteFileAndVobCallback: delete_files cover_movie no more data");
                    break; // break out if no more data
                }
                while (c.moveToNext() && isForeground) {
                    long id = c.getLong(0);
                    String path = c.getString(1);
                    long count = c.getLong(2);
                    if (log.isDebugEnabled()) log.debug("processDeleteFileAndVobCallback: clean delete_files {} path {} count {}", id, path, count);
                    // purge the db: delete row even if file delete callback fails (file deletion could be handled elsewhere
                    try {
                        // path should not be null but deal with it and remove entry in this case
                        if (path == null)
                            db.execSQL("DELETE FROM delete_files WHERE _id=" + String.valueOf(id));
                        else
                            db.execSQL("DELETE FROM delete_files WHERE _id=" + String.valueOf(id) + " AND name='" + path + "'");
                    } catch (SQLException sqlE) {
                        log.error("processDeleteFileAndVobCallback: SQLException", sqlE);
                    }
                }
            } catch (RuntimeException e) {
                log.error("processDeleteFileAndVobCallback: SQLException or IllegalStateException",e);
                if (CRASH_ON_ERROR) throw new RuntimeException(e);
                break;
            } finally {
                if (c != null) c.close();
            }
        }

        // note: seems that the delete is performed not as a table trigger anymore but elsewhere
        // break down the scan in batch of WINDOW_SIZE in order to avoid SQLiteBlobTooBigException: Row too big to fit into CursorWindow crash
        // note that the db is being modified during import
        while (isForeground) {
            try {
                c = db.rawQuery("SELECT * FROM delete_files ORDER BY " + BaseColumns._ID + " ASC LIMIT " + WINDOW_SIZE, null);
                cCount = c.getCount();
                if (log.isDebugEnabled()) log.debug("processDeleteFileAndVobCallback: delete_files new batch fetching window={} -> cursor has size {}", WINDOW_SIZE, cCount);
                if (cCount == 0) {
                    if (log.isDebugEnabled()) log.debug("processDeleteFileAndVobCallback: delete_files no more data");
                    break; // break out if no more data
                }
                while (c.moveToNext() && isForeground) {
                    long id = c.getLong(0);
                    String path = c.getString(1);
                    long count = c.getLong(2);
                    if (log.isTraceEnabled()) log.trace("processDeleteFileAndVobCallback: delete_files {} path {} count {}", id, path, count);
                    DeleteFileCallbackArgs = new String[] {path, String.valueOf(count)};
                    delCb.callback(DeleteFileCallbackArgs);
                    // purge the db: delete row even if file delete callback fails (file deletion could be handled elsewhere
                    try {
                        // path should not be null but deal with it and remove entry in this case
                        if (path == null)
                            db.execSQL("DELETE FROM delete_files WHERE _id=" + String.valueOf(id));
                        else
                            db.execSQL("DELETE FROM delete_files WHERE _id=" + String.valueOf(id) + " AND name='" + path + "'");
                    } catch (SQLException sqlE) {
                        log.error("processDeleteFileAndVobCallback: SQLException", sqlE);
                    }
                }
            } catch (RuntimeException e) {
                log.error("processDeleteFileAndVobCallback: SQLException or IllegalStateException or CursorWindowAllocationException",e);
                if (CRASH_ON_ERROR) throw new RuntimeException(e);
                break;
            } finally {
                if (c != null) c.close();
            }
        }

        // break down the scan in batch of WINDOW_SIZE in order to avoid SQLiteBlobTooBigException: Row too big to fit into CursorWindow crash
        // note that the db is being modified during import
        while (isForeground) {
            try {
                c = db.rawQuery("SELECT * FROM vob_insert ORDER BY " + BaseColumns._ID + " ASC LIMIT " + WINDOW_SIZE, null);
                cCount = c.getCount();
                if (log.isDebugEnabled()) log.debug("processDeleteFileAndVobCallback: delete_files new batch fetching window={} -> cursor has size {}", WINDOW_SIZE, cCount);
                // TOFIX crashes with CursorWindowAllocationException when doing getCount() though rawQuery is paginated catch it via RuntimeException
                if (cCount == 0) {
                    if (log.isDebugEnabled()) log.debug("processDeleteFileAndVobCallback: vob_insert no more data");
                    break; // break out if no more data
                }
                while (c.moveToNext() && isForeground) {
                    long id = c.getLong(0);
                    String path = c.getString(1);
                    if (log.isTraceEnabled()) log.trace("processDeleteFileAndVobCallback: vob_insert {} path {}", id, path);
                    VobUpdateCallbackArgs = new String[] {path};
                    vobCb.callback(VobUpdateCallbackArgs);
                    // purge the db: delete row even if file delete callback fails (file deletion could be handled elsewhere
                    try {
                        if (path == null)
                            db.execSQL("DELETE FROM delete_files WHERE _id=" + String.valueOf(id));
                        else
                            db.execSQL("DELETE FROM vob_insert WHERE _id=" + String.valueOf(id) + " AND name='" + path + "'");
                    } catch (SQLException sqlE) {
                        log.error("processDeleteFileAndVobCallback: SQLException", sqlE);
                    }
                }
            } catch (RuntimeException e) {
                ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.processDeleteFileAndVobCallback", "crash " + e.getMessage());
                log.error("processDeleteFileAndVobCallback: SQLException or IllegalStateException",e);
                if (CRASH_ON_ERROR) throw new RuntimeException(e);
                break;
            } finally {
                if (c != null) c.close();
            }
        }
        // don't db.close() - shared connection
    }

    private void handleVolumeMounted(int storageId) {
        if (log.isDebugEnabled()) log.debug("handleVolumeMounted: storageId={}", storageId);

        // For post-Android P, we use path-based unhiding in updateVolumeHiddenStates()
        // instead of storage_id-based unhiding here
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            if (log.isDebugEnabled()) log.debug("handleVolumeMounted: post-Android P, skipping storage_id-based unhiding");
        } else {
            // Pre-Android P: use existing storage_id-based unhiding
            ContentValues cv = new ContentValues();
            cv.put("volume_hidden", 0);
            int updated = getContentResolver().update(
                    VideoStoreInternal.FILES_IMPORT,
                    cv,
                    "storage_id=? AND volume_hidden != 0",
                    new String[]{String.valueOf(storageId)});
            if (log.isDebugEnabled()) log.debug("handleVolumeMounted: cleared volume_hidden for {} rows", updated);
        }

        // Key change: Use incremental import instead of full import for remounts
        // Files are already unhidden by updateVolumeHiddenStates(), so incremental scan will pick them up
        Message importMsg;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            // Post-Android P: Use incremental import (much faster)
            importMsg = mHandler.obtainMessage(MESSAGE_IMPORT_INCR, DONT_KILL_SELF, storageId);
            if (log.isDebugEnabled()) log.debug("handleVolumeMounted: triggering incremental import for post-Android P");
        } else {
            // Pre-Android P: Keep existing full import behavior
            importMsg = mHandler.obtainMessage(MESSAGE_IMPORT_FULL, DONT_KILL_SELF, storageId);
            if (log.isDebugEnabled()) log.debug("handleVolumeMounted: triggering full import for pre-Android P");
        }

        // Don't mark as dirty for post-Android P since we're doing incremental
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            ImportState.VIDEO.setDirty(true);
        }

        mHandler.sendMessageDelayed(importMsg, 1000);
    }

    /** removes all messages from handler */
    protected static void removeAllMessages(Handler handler) {
        if (log.isDebugEnabled()) log.debug("removeAllMessages");
        if (handler == null) {
            log.warn("removeAllMessages: handler is null, skipping");
            return;
        }
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
            if (log.isDebugEnabled()) log.debug("onChange");
            // to avoid sending message to dead thread because mHandlerThread is no more, need to relaunch the service so that it is recreated in onCreate
            // happens really often
            if (importOk() && mHandler != null) {
                if (log.isDebugEnabled()) log.debug("onChange: triggering VIDEO_SCANNER_IMPORT_INCR");
                removeAllMessages(mHandler);
                Message msg = mHandler.obtainMessage(MESSAGE_IMPORT_INCR, DONT_KILL_SELF, 0);
                mHandler.sendMessageDelayed(msg, 1000);
            } else {
                if (log.isDebugEnabled()) log.debug("onChange: NOT triggering import - importOk={}, mHandler is {}", importOk(), (mHandler == null ? "null" : "not null"));
            }
        }
    }

    /** ServiceConnection that will only do logging */
    private static class LoggingConnection implements ServiceConnection {
        public LoggingConnection() {
        }
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (log.isDebugEnabled()) log.debug("onServiceConnected");
        }
        public void onServiceDisconnected(ComponentName name) {
            if (log.isDebugEnabled()) log.debug("onServiceDisconnected");
        }
    }

    /** calls {@link IScraperService#setupDefaultContent(boolean) }*/
    private void initializeScraperData() {
        if (log.isDebugEnabled()) log.debug("initializeScraperData()");
        Scraper scraper = new Scraper(this);
    }

    @Override
    public void onStop(LifecycleOwner owner) {
        // App in background
        isForeground = false;
        if (log.isDebugEnabled()) log.debug("onStop: LifecycleOwner app in background, stopSelf");
        ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onStop (lifecycle)", "app is in background stopSelf");
        cleanup();
        stopSelf();
    }

    private void cleanup() {
        if (log.isDebugEnabled()) log.debug("cleanup");
        mCleanupCalled = true;

        // Remove lifecycle observer to prevent callbacks to destroyed service
        ProcessLifecycleOwner.get().getLifecycle().removeObserver(this);

        // Stop MediaRetrieverService when VideoStoreImportService is being cleaned up
        Intent mediaRetrieverIntent = new Intent(this, MediaRetrieverService.class);
        stopService(mediaRetrieverIntent);
        if (log.isDebugEnabled()) log.debug("cleanup: MediaRetrieverService stop requested");

        // Stop the handler thread
        if (mHandlerThread != null) {
            mHandlerThread.quit();
            mHandlerThread = null;
        }
        // Clean up the importer
        if (mImporter != null) {
            mImporter.interruptImport();
            mImporter.destroy();
        }
        // Unregister the ContentObserver
        if (mContentObserver != null) {
            getContentResolver().unregisterContentObserver(mContentObserver);
            mContentObserver = null;
        }
        // Remove the VolumeState observer
        if (mVolumeState != null) {
            mVolumeState.unregisterReceiver();
            mVolumeState.removeObserver(mVolumeStateObserver); // Also remove the observer
            mVolumeStateObserver = null;
            mVolumeState = null;
        }
        // Cancel the notification
        nm.cancel(NOTIFICATION_ID);
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        // Safety check: ensure handler is initialized before proceeding
        if (mHandler == null) {
            log.warn("onStart: mHandler is null, initialization not complete yet, ignoring");
            return;
        }

        // App in foreground - restart MediaRetrieverService
        isForeground = true;
        if (log.isDebugEnabled()) log.debug("onStart: LifecycleOwner app in foreground, restarting MediaRetrieverService");
        
        // Restart MediaRetrieverService for foreground operation
        Intent mediaRetrieverIntent = new Intent(this, MediaRetrieverService.class);
        startService(mediaRetrieverIntent);
        if (log.isDebugEnabled()) log.debug("onStart: MediaRetrieverService restart requested");
        
        // when switching to foreground state and db
        // has potentially changed: trigger db import
        if (mVolumeState == null) {
            if (log.isDebugEnabled()) log.debug("onStart: onForeGround && mVolumeState == null, recreating VolumeState");
            // Recreate VolumeState if it was cleaned up (e.g., after background)
            mVolumeState = new VolumeState(this);
            mVolumeStateObserver = volumes -> {
                for (Volume volume : volumes) {
                    if (log.isDebugEnabled()) log.debug("Change:{} to {}", volume.getMountPoint(), volume.getMountState());
                    if (!volume.getMountState()) {
                        mHandler
                            .obtainMessage(MESSAGE_HIDE_VOLUME, DONT_KILL_SELF, volume.getStorageId())
                            .sendToTarget();
                    } else {
                        mHandler
                            .obtainMessage(MESSAGE_VOLUME_MOUNTED, DONT_KILL_SELF, volume.getStorageId())
                            .sendToTarget();
                    }
                }
            };
            mVolumeState.addObserver(mVolumeStateObserver);
        }
        if (log.isDebugEnabled()) log.debug("onStart: onForeGround, registering VolumeState receiver and updating state");
        mVolumeState.registerReceiver();
        mVolumeState.updateState();

        // Always trigger an import when returning to foreground to check volume states
        // (checkDatabaseForUnmountedVolumes() is called during import via updateVolumeHiddenStatesByPath())
        if (ImportState.VIDEO.isDirty()) {
            if (log.isDebugEnabled()) log.debug("onStart: onForeGround && ImportState.isDirty MESSAGE_IMPORT_FULL");
            ArchosUtils.addBreadcrumb(SentryLevel.INFO, "VideoStoreImportService.onStart", "app is foreground ImportState.isDirty MESSAGE_IMPORT_FULL");
            mHandler.obtainMessage(MESSAGE_IMPORT_FULL, DONT_KILL_SELF, 0).sendToTarget();
        }
    }

}
