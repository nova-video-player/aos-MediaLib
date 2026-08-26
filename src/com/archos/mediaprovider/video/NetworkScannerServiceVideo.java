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

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentProviderOperation;
import android.content.ContentProviderOperation.Builder;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteFullException;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;

import androidx.core.app.NotificationCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Pair;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.FileUtils;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.mediacenter.filecoreextension.UriUtils;
import com.archos.mediacenter.filecoreextension.upnp2.MetaFileFactoryWithUpnp;
import com.archos.mediacenter.filecoreextension.upnp2.UpnpFile2;
import com.archos.mediacenter.filecoreextension.upnp2.UpnpServiceManager;
import com.archos.mediacenter.utils.ShortcutDbAdapter;
import com.archos.medialib.R;
import com.archos.mediaprovider.ArchosMediaCommon;
import com.archos.mediaprovider.ArchosMediaFile;
import com.archos.mediaprovider.ArchosMediaFile.MediaFileType;
import com.archos.mediaprovider.ArchosMediaIntent;
import com.archos.mediaprovider.BulkInserter;
import com.archos.mediaprovider.CPOExecutor;
import com.archos.mediaprovider.video.VideoStore.Files.FileColumns;
import com.archos.mediaprovider.video.VideoStore.MediaColumns;
import com.archos.mediaprovider.video.VideoStore.Video.VideoColumns;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.NfoParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static android.net.wifi.WifiManager.WIFI_MODE_FULL;
import static android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressLint("LongLogTag")
public class NetworkScannerServiceVideo extends Service implements Handler.Callback, DefaultLifecycleObserver {
    /*
        explanation about upnp indexing behaviour
        a movie Dumbo.mkv is in /Video/All Videos/ and /Video/Movies/Folder1 and Video/Movies/Folder2
        this is the same file
        we index /Video/Movies
        the file will be found twice but indexed once with the URI of the first one
        some time after, we index /Video/All Videos/
        dumbo will be found again and identified as the same file, and the uri will be updated

     */

    private static final Logger log = LoggerFactory.getLogger(NetworkScannerServiceVideo.class);

    private static final boolean SCAN_MEDIA_ONLY = true;

    private static final int RECURSION_LIMIT =  15;

    // handler message ids
    private static final int MESSAGE_KILL = 1;
    private static final int MESSAGE_DO_SCAN = 2;
    private static final int MESSAGE_DO_UNSCAN = 3;
    public static final String RECORD_ON_FAIL_PREFERENCE = "record_on_fail_preference_extra";
    public static final String RECORD_END_OF_SCAN_PREFERENCE = "record_on_end_preference_extra";
    // Identifies the network scan batch a scan belongs to (see AutoScrapeService batch accounting).
    public static final String EXTRA_SCAN_BATCH_ID = "scan_batch_id_extra";

    private Handler mHandler;
    private HandlerThread mHandlerThread;
    private final Object mDummy = new Object();
    private final ConcurrentHashMap<String, Object> mScanRequests = new ConcurrentHashMap<String, Object>(4, 0.75f, 2);
    private final ConcurrentHashMap<String, Object> mUnScanRequests = new ConcurrentHashMap<String, Object>(4, 0.75f, 2);
    private Blacklist mBlacklist;
    private static boolean sIsScannerAlive;
    private String mRecordOnFailPreference;
    private String mRecordEndOfScanPreference;
    WifiLock wifiLock;

    private static final int NOTIFICATION_ID = 1;
    private NotificationManager nm;
    private NotificationCompat.Builder nb;
    private Notification n;
    private static final String notifChannelId = "NetworkScannerServiceVideo_id";
    private static final String notifChannelName = "NetworkScannerServiceVideo";
    private static final String notifChannelDescr = "NetworkScannerServiceVideo";

    private static volatile boolean isForeground = true;
    private static volatile int sFilesFoundCount = 0;
    private Thread mScanThread;
    private Thread mRemoveFilesThread;

    public static int getFilesFoundCount() {
        return sFilesFoundCount;
    }

    public static boolean startIfHandles(Context context, Intent broadcast) {
        if (log.isDebugEnabled()) log.debug("startIfHandles");
        String action = broadcast.getAction();
        Uri data = broadcast.getData();
        if ((ArchosMediaIntent.isVideoScanIntent(action) || ArchosMediaIntent.isVideoRemoveIntent(action))
                && willBeScanned(data)) {
            if (log.isDebugEnabled()) log.debug("startIfHandles is true: sending intent to NetworkScannerServiceVideo");
            Intent serviceIntent = new Intent(context, NetworkScannerServiceVideo.class);
            serviceIntent.setAction(action);
            serviceIntent.setData(data);
            // Set identifier to avoid StrictMode UnsafeIntentLaunchViolation
            if (data != null && Build.VERSION.SDK_INT >= 29) { // setIdentifier added in API 29
                serviceIntent.setIdentifier(data.toString());
            }
            copyStringExtraIfPresent(broadcast, serviceIntent, RECORD_ON_FAIL_PREFERENCE);
            copyStringExtraIfPresent(broadcast, serviceIntent, RECORD_END_OF_SCAN_PREFERENCE);
            serviceIntent.putExtra(ArchosMediaIntent.EXTRA_INDEXED_ROOT_REMOVED,
                    broadcast.getBooleanExtra(ArchosMediaIntent.EXTRA_INDEXED_ROOT_REMOVED, false));
            long batchId = broadcast.getLongExtra(EXTRA_SCAN_BATCH_ID,
                    com.archos.mediascraper.AutoScrapeService.STANDALONE_SCAN_BATCH_ID);
            serviceIntent.putExtra(EXTRA_SCAN_BATCH_ID, batchId);
            int pendingScans = com.archos.mediascraper.AutoScrapeService.getNetworkScanCount();
            if (isForeground || pendingScans > 0) {
                if (log.isDebugEnabled()) log.debug("startIfHandles: starting service (isForeground={}, pendingScans={})", isForeground, pendingScans);
                context.startService(serviceIntent);
            } else {
                if (log.isDebugEnabled()) log.debug("startIfHandles: app in background and no pending scans, ignoring");
            }
            return true;
        }
        if (log.isDebugEnabled()) log.debug("startIfHandles is false: do nothing");
        return false;
    }

    private static void copyStringExtraIfPresent(Intent source, Intent dest, String key) {
        String value = source.getStringExtra(key);
        if (value != null) {
            dest.putExtra(key, value);
        }
    }

    public static boolean willBeScanned(Uri uri){ //returns whether or not a video will be scanned by NetworkScannerServiceVideo
        return (!FileUtils.isLocal(uri)||UriUtils.isContentUri(uri))&& UriUtils.isIndexable(uri); // http(s)/smb/upnp/(s)ftp(s)/content
    }
    private static boolean isSmbUri(Uri uri) {
        String schema = uri != null ? uri.getScheme() : null;
        return "smb".equals(schema);
    }

    public NetworkScannerServiceVideo() {
        if (log.isDebugEnabled()) log.debug("NetworkScannerServiceVideo CTOR");
    }

    private static  List<ScannerListener> sListener = new ArrayList<>();

    public interface ScannerListener {
        public void onScannerStateChanged();
    }
    public static boolean isScannerAlive() {
        return sIsScannerAlive;
    }
    public synchronized static void addListener(ScannerListener listener) {
        sListener.add(listener);
    }

    public synchronized static void removeListener(ScannerListener listener) {
        sListener.remove(listener);
    }
    public synchronized static void notifyListeners() {
        if (sListener != null) {
            for (ScannerListener listener : sListener) {
                if (listener instanceof Fragment) {
                    ((Fragment) listener).getActivity().runOnUiThread(() -> listener.onScannerStateChanged());
                } else {
                    listener.onScannerStateChanged();
                }
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        if (log.isDebugEnabled()) log.debug("NetworkScannerServiceVideo DTOR");
        super.finalize();
    }

    @Override
    public void onCreate() {
        if (log.isDebugEnabled()) log.debug("onCreate");

        // need to do that early to avoid ANR on Android 26+
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel nc = new NotificationChannel(notifChannelId, notifChannelName,
                    NotificationManager.IMPORTANCE_DEFAULT);
            nc.setDescription(notifChannelDescr);
            nc.setSound(null, null);
            nc.enableVibration(false);
            if (nm != null)
                nm.createNotificationChannel(nc);
        }
        nb = new NotificationCompat.Builder(this, notifChannelId)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(getString(R.string.scraping_in_progress))
                .setContentText("")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setTicker(null).setOnlyAlertOnce(true).setOngoing(true).setAutoCancel(true);
        n = nb.build();

        if (log.isDebugEnabled()) log.debug("onCreate: created notification + startService {} notification null? {}", NOTIFICATION_ID, (n == null));

        sIsScannerAlive = true;
        notifyListeners();
        UpnpServiceManager.getSingleton(this).lockStop();
        /*
             during all scan process, we need to keep UpnpService on, otherwise listing will fail.
             To avoid blinking, we keep it on in the networkscanner instead of the lister
         */
        // setup handler
        mHandlerThread = new HandlerThread("ScanWorker", Process.THREAD_PRIORITY_BACKGROUND);
        mHandlerThread.start();
        Looper looper = mHandlerThread.getLooper();
        mHandler = new Handler(looper, this);

        mBlacklist = Blacklist.getInstance(this);

        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifiManager != null) {
            wifiLock = wifiManager.createWifiLock(WIFI_MODE_FULL_HIGH_PERF, "ArchosNetworkIndexer");
            wifiLock.setReferenceCounted(true);
        }

        // Register as a lifecycle observer
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    }

    @Override
    public void onDestroy() {
        if (log.isDebugEnabled()) log.debug("onDestroy");
        cleanup();
        // Additional cleanup if necessary
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // intents delivered here
        if (log.isDebugEnabled()) log.debug("onStartCommand:{} flags:{} startId:{} getAction {}", intent, flags, startId, ((intent != null) ? intent.getAction() : "null"));
        
        if (intent == null || intent.getAction() == null)
            return START_NOT_STICKY;
        String recordOnFailPreference = intent.getStringExtra(RECORD_ON_FAIL_PREFERENCE);
        String recordEndOfScanPreference = intent.getStringExtra(RECORD_END_OF_SCAN_PREFERENCE);
        if (recordOnFailPreference != null || recordEndOfScanPreference != null) {
            if (log.isDebugEnabled()) log.debug("scanner preference extras present");
            mRecordOnFailPreference = recordOnFailPreference;
            if(mRecordEndOfScanPreference==null) //reset only when null to avoid pred not being written when another intent with no pref comes just after (this will be written when service stops)
                mRecordEndOfScanPreference = recordEndOfScanPreference;
        } else {
            if (log.isDebugEnabled()) log.debug("extra null");
            mRecordOnFailPreference = null;
            mRecordEndOfScanPreference =  null;
        }
        
        // forward events to background handler
        String action = intent.getAction();
        if (ArchosMediaIntent.isVideoScanIntent(action)) {
            Uri data = intent.getData();
            String key = data.toString();
            long batchId = intent.getLongExtra(EXTRA_SCAN_BATCH_ID,
                    com.archos.mediascraper.AutoScrapeService.STANDALONE_SCAN_BATCH_ID);
            if (mScanRequests.putIfAbsent(key, mDummy) == null) {
                Message m = mHandler.obtainMessage(MESSAGE_DO_SCAN, startId, flags, data);
                Bundle scanData = new Bundle();
                scanData.putLong(EXTRA_SCAN_BATCH_ID, batchId);
                m.setData(scanData);
                mHandler.sendMessage(m);
            } else {
                // This URI is already queued, so this request will not run its own doScan().
                // Release its batch membership here, otherwise the batch counter would never
                // reach zero and post-scan scraping would never start. Standalone requests
                // (no batch id) are a no-op for the active batch.
                if (log.isDebugEnabled()) log.debug("skip scanning {}, already in queue", key);
                com.archos.mediascraper.AutoScrapeService.NetworkScanCompletion completion =
                        com.archos.mediascraper.AutoScrapeService.completeNetworkScan(batchId, false, false);
                com.archos.mediascraper.AutoScrapeService.handleNetworkScanCompletion(this, completion);
            }
        } else if (ArchosMediaIntent.isVideoRemoveIntent(action)) {
            Uri data = intent.getData();
            boolean indexedRootRemoved = intent.getBooleanExtra(
                    ArchosMediaIntent.EXTRA_INDEXED_ROOT_REMOVED, false);
            String key = unscanRequestKey(data, indexedRootRemoved);
            if (mUnScanRequests.putIfAbsent(key, mDummy) == null) {
                Message m = mHandler.obtainMessage(MESSAGE_DO_UNSCAN, startId, flags, data);
                Bundle removeData = new Bundle();
                removeData.putBoolean(ArchosMediaIntent.EXTRA_INDEXED_ROOT_REMOVED, indexedRootRemoved);
                m.setData(removeData);
                mHandler.sendMessage(m);
            } else if (log.isDebugEnabled()) log.debug("skip unscanning {}, already in queue", key);
        }
        return Service.START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (log.isDebugEnabled()) log.debug("onBind");
        // this service can't bind
        return null;
    }

    /** handler implementation */
    @Override
    public boolean handleMessage(Message msg) {
        if (log.isDebugEnabled()) log.debug("handleMessage:{} what:{} startid:{}", msg, msg.what, msg.arg1);
        Uri uri;
        String key;
        switch (msg.what) {
            case MESSAGE_KILL:
                if (log.isDebugEnabled()) log.debug("handleMessage: MESSAGE_KILL");
                if (msg.arg1 != -1) {
                    // Check if there are more pending scans
                    int remainingScans = com.archos.mediascraper.AutoScrapeService.getNetworkScanCount();
                    if (log.isDebugEnabled()) log.debug("handleMessage: MESSAGE_KILL, remainingScans={}, isForeground={}", remainingScans, isForeground);

                    if (remainingScans == 0) {
                        sIsScannerAlive = false;
                        notifyListeners();
                    }
                            
                    nm.cancel(NOTIFICATION_ID);
                    sIsScannerAlive = false;
                    notifyListeners();
                    stopSelf(msg.arg1);
                }
                break;
            case MESSAGE_DO_SCAN:
                uri = (Uri) msg.obj;
                key = uri.toString();
                if (log.isDebugEnabled()) log.debug("handleMessage: MESSAGE_DO_SCAN {}", uri);
                int pendingScans = com.archos.mediascraper.AutoScrapeService.getNetworkScanCount();
                final long scanBatchId = msg.getData().getLong(EXTRA_SCAN_BATCH_ID,
                        com.archos.mediascraper.AutoScrapeService.STANDALONE_SCAN_BATCH_ID);
                if (isForeground || pendingScans > 0) {
                    if (log.isDebugEnabled()) log.debug("handleMessage: processing scan (isForeground={}, pendingScans={})", isForeground, pendingScans);
                    mScanThread = new Thread(() -> {
                        doScan(uri, scanBatchId);
                        // *** Send MESSAGE_KILL after doScan() completes ***
                        if (isHandlerThreadAlive()) {
                            mHandler.post(() -> mHandler.obtainMessage(MESSAGE_KILL, msg.arg1, msg.arg2).sendToTarget());
                        }
                    });
                    mScanThread.start();
                } else {
                    if (log.isDebugEnabled()) log.debug("handleMessage: skipping scan, app in background and no pending scans");
                }
                mScanRequests.remove(key);
                break;
            case MESSAGE_DO_UNSCAN:
                uri = (Uri) msg.obj;
                final boolean indexedRootRemoved = msg.getData().getBoolean(
                        ArchosMediaIntent.EXTRA_INDEXED_ROOT_REMOVED, false);
                key = unscanRequestKey(uri, indexedRootRemoved);
                if (log.isDebugEnabled()) log.debug("handleMessage: MESSAGE_DO_UNSCAN {}", uri);
                if (isForeground) {
                    mRemoveFilesThread = new Thread(() -> {
                        doRemoveFiles(uri, indexedRootRemoved);
                        // *** Send MESSAGE_KILL after doRemoveFiles() completes ***
                        if (isHandlerThreadAlive()) {
                            mHandler.post(() -> mHandler.obtainMessage(MESSAGE_KILL, msg.arg1, msg.arg2).sendToTarget());
                        }
                    });
                    mRemoveFilesThread.start();
                }
                mUnScanRequests.remove(key);
                break;
            default:
                if (log.isDebugEnabled()) log.debug("handleMessage: message not found!");
                break;
        }
        return true;
    }

    // -----------------------------------------------------------------------//
    // --  import / delete logic                                            --//
    // -----------------------------------------------------------------------//

    /** removes files from our db */
    private void doRemoveFiles(Uri data, boolean indexedRootRemoved) {
        if (log.isDebugEnabled()) log.debug("doRemoveFiles {}", data);
        if (data == null) return;
        ContentResolver cr = getContentResolver();

        String path = data.toString();
        // send out a sticky broadcast telling the world that we started scanning
        Intent scannerIntent = new Intent(ArchosMediaIntent.ACTION_VIDEO_SCANNER_SCAN_STARTED, data);
        scannerIntent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
        sendBroadcast(scannerIntent);
        // also show a notification.
        nm.notify(NOTIFICATION_ID, nb.setContentTitle(getString(R.string.network_unscan_msg)).setContentText(path).build());

        int deleted;
        List<Uri> remainingRoots = indexedRootRemoved
                ? ShortcutDbAdapter.VIDEO.getIndexedUris(this) : new ArrayList<>();
        boolean endpointStillIndexed = remainingRoots == null
                || hasRootOnSameEndpoint(data, remainingRoots);
        RemovalSelection removal = buildRemovalSelection(data,
                remainingRoots != null ? remainingRoots : new ArrayList<>());
        deleted = removal == null ? 0 : cr.delete(VideoStoreInternal.FILES_SCANNED,
                removal.selection, removal.args);
        if (indexedRootRemoved && !endpointStillIndexed) {
            removeServerIfOrphaned(cr, data);
        }
        if (log.isDebugEnabled()) log.debug("removed: {}", deleted);

        // send a "done" notification
        Intent intent = new Intent(ArchosMediaIntent.ACTION_VIDEO_SCANNER_SCAN_FINISHED, data);
        intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
        sendBroadcast(intent);
        
        // and cancel the Notification
        nm.cancel(NOTIFICATION_ID);
    }

    private void removeServerIfOrphaned(ContentResolver cr, Uri removedRoot) {
        List<Pair<Long, Uri>> matchingServers = new ArrayList<>();
        Cursor servers = cr.query(VideoStore.SmbServer.getContentUri(),
                new String[]{BaseColumns._ID, MediaColumns.DATA}, null, null, null);
        if (servers == null) return;
        try {
            while (servers.moveToNext()) {
                long id = servers.getLong(0);
                Uri server = Uri.parse(servers.getString(1));
                if (sameNetworkEndpoint(removedRoot, server)) {
                    matchingServers.add(Pair.create(id, server));
                }
            }
        } finally {
            servers.close();
        }
        for (Pair<Long, Uri> server : matchingServers) {
            Cursor files = cr.query(VideoStoreInternal.FILES,
                    new String[]{BaseColumns._ID},
                    FileColumns.ARCHOS_SMB_SERVER + "=?",
                    new String[]{Long.toString(server.first)}, null);
            if (files == null) continue;
            boolean orphaned;
            try {
                orphaned = !files.moveToFirst();
            } finally {
                files.close();
            }
            if (orphaned) {
                cr.delete(VideoStore.SmbServer.getContentUri(server.first), null, null);
                log.info("Removed orphaned network server {}", server.second);
            }
        }
    }

    private static boolean hasRootOnSameEndpoint(Uri removedRoot, List<Uri> roots) {
        for (Uri root : roots) {
            if (sameNetworkEndpoint(removedRoot, root)) return true;
        }
        return false;
    }

    private static boolean sameNetworkEndpoint(Uri first, Uri second) {
        if (first == null || second == null || first.getScheme() == null
                || second.getScheme() == null || first.getHost() == null
                || second.getHost() == null) return false;
        return first.getScheme().equalsIgnoreCase(second.getScheme())
                && first.getHost().equalsIgnoreCase(second.getHost())
                && effectivePort(first) == effectivePort(second);
    }

    private static int effectivePort(Uri uri) {
        if (uri.getPort() != -1) return uri.getPort();
        String scheme = uri.getScheme();
        if ("smb".equalsIgnoreCase(scheme)) return 445;
        if ("ftp".equalsIgnoreCase(scheme)) return 21;
        if ("sftp".equalsIgnoreCase(scheme)) return 22;
        if ("http".equalsIgnoreCase(scheme)) return 80;
        if ("https".equalsIgnoreCase(scheme)) return 443;
        return -1;
    }

    private static RemovalSelection buildRemovalSelection(Uri removedRoot, List<Uri> remainingRoots) {
        String removed = trimTrailingSlash(removedRoot.toString());
        List<String> protectedRoots = new ArrayList<>();
        for (Uri root : remainingRoots) {
            if (!sameNetworkEndpoint(removedRoot, root)) continue;
            String candidate = trimTrailingSlash(root.toString());
            if (isSameOrDescendant(removed, candidate)) return null;
            if (isSameOrDescendant(candidate, removed)) protectedRoots.add(candidate);
        }

        StringBuilder selection = new StringBuilder("(")
                .append(MediaColumns.DATA).append("=? OR ")
                .append(MediaColumns.DATA).append(" LIKE ? ESCAPE '\\')");
        List<String> args = new ArrayList<>();
        args.add(removed);
        args.add(escapeLike(removed) + "/%");
        for (String root : protectedRoots) {
            selection.append(" AND NOT (").append(MediaColumns.DATA).append("=? OR ")
                    .append(MediaColumns.DATA).append(" LIKE ? ESCAPE '\\')");
            args.add(root);
            args.add(escapeLike(root) + "/%");
        }
        return new RemovalSelection(selection.toString(), args.toArray(new String[0]));
    }

    private static boolean isSameOrDescendant(String path, String root) {
        return path.equals(root) || path.startsWith(root + "/");
    }

    private static String trimTrailingSlash(String value) {
        while (value.endsWith("/") && value.length() > value.indexOf("://") + 3) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String unscanRequestKey(Uri uri, boolean indexedRootRemoved) {
        return uri.toString() + (indexedRootRemoved ? "#indexed-root" : "#file");
    }

    private static final class RemovalSelection {
        final String selection;
        final String[] args;

        RemovalSelection(String selection, String[] args) {
            this.selection = selection;
            this.args = args;
        }
    }

    /** Utility class to build a comma separated string of ids */
    private static class DeleteString {

        public DeleteString() { /* empty */ }

        private final StringBuilder mStringBuilder = new StringBuilder();
        private boolean mNeedComma;
        private int mCount;

        public void add(long id) {
            if (mNeedComma)
                mStringBuilder.append(',');
            else
                mNeedComma = true;
            mStringBuilder.append(id);
            mCount++;
        }

        @Override
        public String toString() {
            return mStringBuilder.toString();
        }

        public int getCount() {
            return mCount;
        }
    }
    private static int mFoundFiles = 0;
    private static final String IN_FOLDER_SELECT = MediaColumns.DATA + " LIKE ?||'%'";
    private static final String SELECT_ID = BaseColumns._ID + "=?";
    /** scans files into our db */
    void doScan(Uri what, long batchId) {
        if (log.isDebugEnabled()) log.debug("doScan {} (batch {})", what, batchId);
        long start = log.isDebugEnabled() ? System.currentTimeMillis() : 0;
        boolean scanResolved = false;
        boolean scanHadDbError = false;
        try {
            ScanResult result = performScan(what);
            scanResolved = result.resolved;
            scanHadDbError = result.hadDbError;
        } finally {
            // Always record this scan's completion, even if the scan body threw an unchecked
            // error, so the batch counter cannot be left permanently non-zero (which would
            // strand the batch and never start post-scan scraping). The error and success
            // state are aggregated across the whole batch under a single lock, so the decision
            // no longer depends on whichever thread happens to finish last.
            com.archos.mediascraper.AutoScrapeService.NetworkScanCompletion completion =
                    com.archos.mediascraper.AutoScrapeService.completeNetworkScan(batchId, scanHadDbError, scanResolved);
            if (log.isDebugEnabled()) {
                log.debug("doScan: completed network scan, completedBatch={}, batchHadError={}, batchHadSuccess={}",
                        completion.completedBatch, completion.batchHadError, completion.batchHadSuccess);
            }
            com.archos.mediascraper.AutoScrapeService.handleNetworkScanCompletion(this, completion);
        }

        if (log.isDebugEnabled()) {
            long end = System.currentTimeMillis();
            log.debug("doScan took:{}ms", (end - start));
        }
    }

    /** Outcome of a single folder scan, used to drive batch completion accounting. */
    static final class ScanResult {
        final boolean resolved;
        final boolean hadDbError;
        ScanResult(boolean resolved, boolean hadDbError) {
            this.resolved = resolved;
            this.hadDbError = hadDbError;
        }
    }

    /**
     * Performs the actual scan of a single folder. Returns whether the target resolved and
     * indexed without a database error, and whether a database error occurred. Expected
     * storage failures are handled gracefully; unexpected database errors are reported to
     * Sentry but still handled so the caller can always complete the batch.
     */
    private String mCurrentRootUri = null;

    ScanResult performScan(Uri what) {
        mFoundFiles = 0;
        sFilesFoundCount = 0;
        MetaFile2 f = null;
        boolean scanHadDbError = false;
        try {
            f = MetaFileFactoryWithUpnp.getMetaFileForUrl(what);
        } catch (Exception e) {
            log.error("doScan: caught Exception failed to get MetaFile for {}", what, e);
        }
        if (f != null) {
            mCurrentRootUri = f.getUri().toString();
            if (log.isDebugEnabled()) log.debug("doScan path resolved to:{}", f.getUri().toString());
            ContentResolver cr = getContentResolver();

            WifiLock lock = wifiLock;
            boolean lockAcquired = false;
            if (lock != null) {
                lock.acquire();
                lockAcquired = true;
            }

            try {
                // send out a sticky broadcast telling the world that we started scanning
                Intent scannerIntent = new Intent(ArchosMediaIntent.ACTION_VIDEO_SCANNER_SCAN_STARTED, what);
                scannerIntent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                sendBroadcast(scannerIntent);
                // also show a notification.
                updateScanNotification(f.getUri().toString(), sFilesFoundCount);

                String path;
                String upnpUri = null;
             /*
             special case with upnp : we need to get all indexed files of specified host,
             because a specific file can have already been indexed in another folder
             (a file can be in different folders)
             idea : be aware that : we are listing a specific uri. prescan is
             useful to update instead of adding a new file but ALSO to delete
             indexed files that are not physically there anymore.
             if we get the whole list of indexed files of current host,
             some can not be listed later because they are in another indexed folder.
             So we have to set  item.needsDelete = false for each
             item that are not in currently listed folder */
                if ("upnp".equals(f.getUri().getScheme())) {
                    path = "upnp://" + f.getUri().getHost() + "/";
                    upnpUri = f.getUri().toString();
                    if (f.isDirectory() && !upnpUri.endsWith("/"))
                        upnpUri = upnpUri + "/";
                } else {
                    path = f.getUri().toString();
                    if (f.isDirectory() && !path.endsWith("/"))
                        path = path + "/";
                }
                if (log.isDebugEnabled()) log.debug("doScan: path identified is {}", path);
                // query database for all files we have already in that directory
                String[] selectionArgs = new String[]{path};
                Cursor prescan = cr.query(VideoStoreInternal.FILES_SCANNED, PrescanItem.PROJECTION, IN_FOLDER_SELECT, selectionArgs, null);
                // hashmap to contain all knows files + data, keyed by path
                HashMap<String, PrescanItem> prescanItemsMap = new HashMap<String, NetworkScannerServiceVideo.PrescanItem>();
                if (prescan != null) {
                    while (prescan.moveToNext() && isForeground) {
                        PrescanItem item = new PrescanItem(prescan);
                        if (upnpUri != null && !item._data.startsWith(upnpUri)) { // if this isn't in folder about to be listed, we won't need to delete it
                            item.needsDelete = false;
                        }
                        if (log.isTraceEnabled()) log.trace("doScan: prescan item._data {}", item._data);
                        if (item.unique_id != null && !item.unique_id.isEmpty())
                            prescanItemsMap.put(item.unique_id, item);
                        else
                            prescanItemsMap.put(item._data, item);
                    }
                    prescan.close();
                }
              
                boolean nfoScanEnabled = NfoParser.isNetworkNfoParseEnabled(this);
                BulkOperationHandler bulkHandler = new BulkOperationHandler(nfoScanEnabled, this);

                // prescan list is modified by FileVisitListener to indicate whether
                // a file needs to be deleted or not
                // ! this is the actual scanning process !
                // extract server id string / number
                // Note that extractSmbServer is not smb specific...
                final String server = extractSmbServer(f.getUri());
                final long serverId = getLightIndexServerId(server);
                FileVisitListener fileVisitListener = new FileVisitListener(
                        mBlacklist, prescanItemsMap, nfoScanEnabled, bulkHandler, serverId, this);

                FileVisitor.visit(f, RECURSION_LIMIT, fileVisitListener);
                boolean traversalHadError = fileVisitListener.hadListingError();

                String finalDir = fileVisitListener.getLastNotifiedDirectory();
                if (finalDir == null) finalDir = f.getUri().toString();
                updateScanNotification(finalDir, sFilesFoundCount);
                                  
                // once all files where visited we have inserted, updated or deleted files in the db.
                // Nfo has also been processed
                List<MetaFile2> lastPlayedDbs = fileVisitListener.getLastPlayedDbs();

                int insertCount = bulkHandler.getInsertHandled();
                int updateCount = bulkHandler.getUpdatesHandled();
                int deleteCount = bulkHandler.getDeletesHandled();
                if (log.isDebugEnabled()) log.debug("added:{} modified:{} deleted:{}", insertCount, updateCount, deleteCount);

                int newSubs = handleSubtitles(cr);
                if (log.isDebugEnabled()) log.debug("added subtitles:{}", newSubs);
                // send a "done" notification
                WrapperChannelManager.refreshChannels(this);
                Intent intent = new Intent(ArchosMediaIntent.ACTION_VIDEO_SCANNER_SCAN_FINISHED, what);
                intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                sendBroadcast(intent);

                // and cancel the Notification
                nm.cancel(NOTIFICATION_ID);
                if (log.isTraceEnabled()) log.trace("doScan: added:{} modified:{} deleted:{} listed files {}", insertCount, updateCount, deleteCount, mFoundFiles);
                if (traversalHadError && mRecordOnFailPreference != null) {
                    PreferenceManager.getDefaultSharedPreferences(this).edit().putInt(mRecordOnFailPreference, -1).commit();
                }
            } catch (SQLiteFullException | SQLiteDiskIOException e) {
                // Expected storage failure: a bulk insert/update/delete aborted
                // mid-transaction, typically a full disk (SQLITE_FULL) or a transient
                // I/O error on low-storage devices. Abort this scan cleanly instead of
                // crashing the scanner thread: record the failure for diagnostics, flag
                // the batch so post-scan scraping is skipped, and notify the world it
                // finished so the UI does not stay stuck.
                log.error("doScan: aborting scan of {} due to storage error", what, e);
                scanHadDbError = true;
                if (mRecordOnFailPreference != null) {
                    PreferenceManager.getDefaultSharedPreferences(this).edit().putInt(mRecordOnFailPreference, -1).commit();
                }
                Intent intent = new Intent(ArchosMediaIntent.ACTION_VIDEO_SCANNER_SCAN_FINISHED, what);
                intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                sendBroadcast(intent);
                nm.cancel(NOTIFICATION_ID);
            } catch (SQLiteException e) {
                // Unexpected database error (not a known storage failure). Abort the scan
                // cleanly so the batch can still complete and the UI is not left stuck,
                // but explicitly report it to Sentry so we keep visibility on bugs that the
                // narrow storage handling above intentionally does not mask.
                log.error("doScan: aborting scan of {} due to unexpected database error", what, e);
                io.sentry.Sentry.captureException(e);
                scanHadDbError = true;
                if (mRecordOnFailPreference != null) {
                    PreferenceManager.getDefaultSharedPreferences(this).edit().putInt(mRecordOnFailPreference, -1).commit();
                }
                Intent intent = new Intent(ArchosMediaIntent.ACTION_VIDEO_SCANNER_SCAN_FINISHED, what);
                intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                sendBroadcast(intent);
                nm.cancel(NOTIFICATION_ID);
            } finally {
                if (lockAcquired && lock != null && lock.isHeld()) {
                    lock.release();
                }
            }
        } else if(mRecordOnFailPreference!=null){
            PreferenceManager.getDefaultSharedPreferences(this).edit().putInt(mRecordOnFailPreference, -1).commit();//unable to reach server
        }

        // resolved successfully when the target was reachable and no database error occurred
        return new ScanResult((f != null) && !scanHadDbError, scanHadDbError);
    }

    public static String[] formatNotificationBody(String rootUriStr, String currentPathStr) {
        if (currentPathStr == null || currentPathStr.isEmpty()) {
            return new String[] { rootUriStr != null ? rootUriStr : "", "/" };
        }
        String root = (rootUriStr != null && !rootUriStr.isEmpty()) ? rootUriStr : currentPathStr;

        String normRoot = root.endsWith("/") && root.length() > 1 ? root.substring(0, root.length() - 1) : root;
        String normCurrent = currentPathStr.endsWith("/") && currentPathStr.length() > 1 ? currentPathStr.substring(0, currentPathStr.length() - 1) : currentPathStr;

        String shareLine = normRoot;
        String subDirLine = "/";

        if (normCurrent.startsWith(normRoot)) {
            String rel = normCurrent.substring(normRoot.length());
            if (!rel.isEmpty()) {
                subDirLine = rel.startsWith("/") ? rel : "/" + rel;
            }
        } else {
            try {
                Uri uri = Uri.parse(currentPathStr);
                String scheme = uri.getScheme();
                if (scheme != null && !scheme.equals("file") && !scheme.equals("content")) {
                    List<String> segments = uri.getPathSegments();
                    if (segments != null && !segments.isEmpty()) {
                        String shareName = segments.get(0);
                        String host = uri.getHost();
                        int port = uri.getPort();
                        StringBuilder shareSb = new StringBuilder(scheme).append("://").append(host != null ? host : "");
                        if (port > 0) shareSb.append(":").append(port);
                        shareSb.append("/").append(shareName);
                        shareLine = shareSb.toString();

                        StringBuilder subSb = new StringBuilder();
                        for (int i = 1; i < segments.size(); i++) {
                            subSb.append("/").append(segments.get(i));
                        }
                        subDirLine = subSb.length() > 0 ? subSb.toString() : "/";
                    }
                } else {
                    shareLine = currentPathStr;
                }
            } catch (Exception e) {
                shareLine = currentPathStr;
            }
        }

        return new String[] { shareLine, subDirLine };
    }

    void updateScanNotification(String path, int count) {
        if (nm != null && nb != null) {
            String title = (count > 0) ? getString(R.string.network_scan_msg) + " (" + count + ")" : getString(R.string.network_scan_msg);
            String[] bodyLines = formatNotificationBody(mCurrentRootUri, path);
            String shareLine = bodyLines[0];
            String subDirLine = bodyLines[1];

            String contentText = shareLine + " - " + subDirLine;
            String bigText = shareLine + "\n" + subDirLine;

            nb.setContentTitle(title)
              .setContentText(contentText)
              .setStyle(new NotificationCompat.BigTextStyle().bigText(bigText));
            nm.notify(NOTIFICATION_ID, nb.build());
        }
    }

    // ---------------------------------------------------------------------- //
    // -- Recursive file scanner magic                                     -- //
    // ---------------------------------------------------------------------- //
    private static class FileVisitListener implements FileVisitor.Listener {
        /**
         * A stale network share can contain many removed files.  Keep each delete
         * transaction comfortably below the five-second input-ANR budget, and
         * release the SQLite writer between groups so UI/provider work can run.
         */
        private static final int DELETE_BATCH_SIZE = 50;

        private final BulkOperationHandler mBulkHandler;
        private final HashMap<String, PrescanItem> mPrescanItemsMap;
        private final List<MetaFile2> mLastPlayedDbs = new ArrayList<MetaFile2>();
        private final boolean mNfoScanEnabled;
        private final long mServerId;
        private final HashSet<String> mAlreadyAddedUpnpFiles; //for files analysed DURING scan process
        private final NetworkScannerServiceVideo mService;
        private int mStorageId;

        private final Blacklist mBlacklist;

        public FileVisitListener(Blacklist blacklist, HashMap<String, PrescanItem> prescanItemsMap,
                boolean nfoScanEnabled, BulkOperationHandler bulkHandler, long serverId, NetworkScannerServiceVideo service) {
            if (log.isDebugEnabled()) log.debug("FileVisitListener: serverId={}", serverId);
            mBlacklist = blacklist;
            mPrescanItemsMap = prescanItemsMap;
            mNfoScanEnabled = nfoScanEnabled;
            mBulkHandler = bulkHandler;
            mServerId = serverId;
            mAlreadyAddedUpnpFiles = new HashSet<>();
            mService = service;
        }

        private boolean mHadListingError;

        public List<MetaFile2> getLastPlayedDbs() {
            return mLastPlayedDbs;
        }

        public boolean hadListingError() {
            return mHadListingError;
        }

        private String mLastNotifiedDirectory = null;

        public String getLastNotifiedDirectory() {
            return mLastNotifiedDirectory;
        }

        @Override
        public void onStart(MetaFile2 root) {
            mStorageId = getStorageId(root.getUri().toString());
            if (root != null && root.getUri() != null) {
                mLastNotifiedDirectory = root.getUri().toString();
            }
        }

        @Override
        public boolean onFilesList(List<MetaFile2> files){
            // directories with a .nomedia file are not scanned
            for(MetaFile2 file : files){
                if(file.getName().equals(".nomedia"))
                    return false;
            }
            return true;
        }

        @Override
        public boolean onDirectory(MetaFile2 directory) {
            // hidden directories are not scanned
            if (ArchosMediaFile.isHiddenFile(directory)) {
                if (log.isDebugEnabled()) log.debug("skipping {}, .hidden!", (directory != null ? directory.getName() : "null"));
                return false;
            }

            // Check if the directory is blacklisted (e.g. #recycle on synology)
            if (mBlacklist.isDirectoryBlacklisted(directory.getName())) {
                if (log.isDebugEnabled()) log.debug("skipping {}, blacklisted directory!", directory.getName());
                return false;
            }

            if (mService != null && directory != null && directory.getUri() != null) {
                String dirPath = directory.getUri().toString();
                if (!dirPath.equals(mLastNotifiedDirectory)) {
                    mLastNotifiedDirectory = dirPath;
                    mService.updateScanNotification(dirPath, sFilesFoundCount);
                }
            }

            // everything else is scanned
            return true;
        }

        @Override
        public void onFile(MetaFile2 file) {
            mFoundFiles ++;
            int fileType = getFileType(file);
            if (!isValidType(fileType)) return;
            if (ArchosMediaFile.isHiddenFile(file)) return;
            // shortcut for blacklist check for trailer/sample, full should be isBlacklisted
            if (mBlacklist.isFilenameBlacklisted(FileUtils.getName(file.getUri()))) return;
            sFilesFoundCount++;
            if (mService != null) {
                String dirPath = FileUtils.getParentUrl(file.getUri().toString());
                if (dirPath == null) dirPath = file.getUri().toString();
                boolean isNewDir = !dirPath.equals(mLastNotifiedDirectory);
                boolean isCountMilestone = (sFilesFoundCount == 1 || sFilesFoundCount % 25 == 0);
                if (isNewDir || isCountMilestone) {
                    mLastNotifiedDirectory = dirPath;
                    mService.updateScanNotification(dirPath, sFilesFoundCount);
                }
            }
            if (log.isTraceEnabled()) log.trace("FileVisitListener.onFile: File {}", file.getUri().toString());
            String p = file.getUri().toString();
            PrescanItem existingItem = null;
            String uniqueId = "";
            //special case for upnp : use unique id
            if(file instanceof UpnpFile2){
                if (log.isDebugEnabled()) log.debug("FileVisitListener.onFile: File is upnp {}", ((UpnpFile2)file).getUniqueHash());
                uniqueId = ((UpnpFile2)file).getUniqueHash();
                existingItem = mPrescanItemsMap.get(uniqueId);
            }
            else{
                existingItem = mPrescanItemsMap.get(p);
                uniqueId = p;
            }
            if (log.isTraceEnabled()) log.trace("FileVisitListener.onFile: existingItem {}", existingItem);
            if ((existingItem) != null) {
                // file was already scanned, it does not need to be deleted
                existingItem.needsDelete = false;
                if (log.isTraceEnabled()) log.trace("FileVisitListener.onFile: File isn't new:{}", file.getName());
                // check if it is untouched or needs an update
                long knownDate = existingItem.date_modified;
                long newDate = file.lastModified() / 1000;
                if (Math.abs(knownDate - newDate) > 3 || !file.getUri().toString().equals(existingItem._data)) {
                    if (log.isDebugEnabled()) log.debug("FileVisitListener.onFile: Updating {}", file.getName());
                    // file has changed - add the update
                    mBulkHandler.addUpdate(new FileScanInfo(file, mStorageId),
                            existingItem._id);
                }
            } else if(!mAlreadyAddedUpnpFiles.contains(uniqueId)){
                // file is new, add as insert
                if (log.isTraceEnabled()) log.trace("FileVisitListener.onFile: File is new, serverId={}, {}", mServerId, file.getUri().toString());
                mAlreadyAddedUpnpFiles.add(uniqueId); // needed because main difference with usual indexing : a same file can be found twice in one round
                mBulkHandler.addInsert(new FileScanInfo(file, mStorageId), mServerId);
            }
            else if (log.isTraceEnabled()) log.trace("FileVisitListener.onFile: File already scanned {}", file.getName());
            // nfo are now handled in autoscrapeservice
        }

        @Override
        public void onOtherType(MetaFile2 file) {
            // ignored
        }

        @Override
        public void onStop(MetaFile2 root) {
            if (log.isDebugEnabled()) log.debug("onStop");
            // once we are done traversing the directories check for files that
            // were not seen and delete them
            DeleteString deletes = new DeleteString();
            for (PrescanItem item : mPrescanItemsMap.values()) {
                if (item.needsDelete) {
                    // append id to delete string
                    deletes.add(item._id);
                    if (deletes.getCount() == DELETE_BATCH_SIZE) {
                        mBulkHandler.executeDelete(deletes);
                        deletes = new DeleteString();
                    }
                }
            }
            mBulkHandler.executeDelete(deletes);

            // force execution of all pending operations
            mBulkHandler.executePending();
        }

        @Override
        public void onListingError(MetaFile2 directory, Exception exception) {
            mHadListingError = true;
            if (directory == null) {
                return;
            }
            String directoryPath = directory.getUri().toString();
            String prefix = directoryPath;
            if (!prefix.endsWith("/")) {
                prefix = prefix + "/";
            }
            log.warn("onListingError: skip deletions under {} due to traversal error", prefix);
            for (PrescanItem item : mPrescanItemsMap.values()) {
                if (item._data == null) {
                    continue;
                }
                if (item._data.equals(directoryPath) || item._data.startsWith(prefix)) {
                    item.needsDelete = false;
                }
            }
        }

        /** checks if the file should be scanned */
        private static boolean isValidType(int fileType) {
            if (!SCAN_MEDIA_ONLY) return true;
            return ArchosMediaFile.isVideoFileType(fileType) || ArchosMediaFile.isSubtitleFileType(fileType);
        }

        /** gets the ArchosMediaFile fileType int */
        private static int getFileType(MetaFile2 f) {
            MediaFileType mft = ArchosMediaFile.getFileType(f.getExtension());
            if (mft == null) {
                return -1;
            }
            return mft.fileType;
        }
        /** calculates the storage id for network, offset by a big value */
        private static int getStorageId(String path) {
            if (path.startsWith("smb://"))
                // 0: EXTERNAL_SMB_PATH, 1: EXTERNAL_UPNP_PATH -> "smb://" = 2
                return getStorageId(2 + ArchosMediaCommon.LIGHT_INDEX_STORAGE_ID_OFFSET);
            // note that below is not really needed because nobody checks this
            else if (path.startsWith("ftp://"))
                return getStorageId(3 + ArchosMediaCommon.LIGHT_INDEX_STORAGE_ID_OFFSET);
            else if (path.startsWith("sftp://"))
                return getStorageId(4 + ArchosMediaCommon.LIGHT_INDEX_STORAGE_ID_OFFSET);
            else if (path.startsWith("ftps://"))
                return getStorageId(5 + ArchosMediaCommon.LIGHT_INDEX_STORAGE_ID_OFFSET);
            else if (path.startsWith("webdav://"))
                return getStorageId(6 + ArchosMediaCommon.LIGHT_INDEX_STORAGE_ID_OFFSET);
            else if (path.startsWith("webdavs://"))
                return getStorageId(7 + ArchosMediaCommon.LIGHT_INDEX_STORAGE_ID_OFFSET);
            else if (path.startsWith("smbj://"))
                return getStorageId(8 + ArchosMediaCommon.LIGHT_INDEX_STORAGE_ID_OFFSET);
            else if (path.startsWith("sshj://"))
                return getStorageId(9 + ArchosMediaCommon.LIGHT_INDEX_STORAGE_ID_OFFSET);
            else log.warn("path has no valid storage id: {}", path);
            return 0;
        }

        public static int getStorageId(int index) {
            // storage ID is 0x00010001 for primary storage,
            // then 0x00020001, 0x00030001, etc. for secondary storages
            return ((index + 1) << 16) + 16962;
        }
    }

    private static class BulkOperationHandler {
        // each insert / update thing is ~1kB in size, two separate things
        // roughly 2x 1MB memory consumption
        private static final int BULK_LIMIT_UPSERT = 1024;
        // each nfo thing is ~4kB in size, 2MB memory consumption
        private static final int BULK_LIMIT_NFO = 512;

        // > should amount to roughly 4MB consumed here

        private final CPOExecutor mUpdateExecutor;
        private final BulkInserter mInsertExecutor;

        private int mDeletes;

        public BulkOperationHandler(boolean nfoScanEnabled, Context context) {
            ContentResolver cr = context.getContentResolver();
            mUpdateExecutor = new CPOExecutor(VideoStore.AUTHORITY, cr, BULK_LIMIT_UPSERT);
            mInsertExecutor = new BulkInserter(VideoStoreInternal.FILES_SCANNED, cr, BULK_LIMIT_UPSERT);
        }

        public void addUpdate(FileScanInfo update, long fileId) {
            Builder builder = ContentProviderOperation.newUpdate(VideoStoreInternal.FILES_SCANNED);
            builder.withValues(update.toContentValues());
            builder.withSelection(SELECT_ID, new String[]{ String.valueOf(fileId) });
            mUpdateExecutor.add(builder.build());
        }

        /**
         * Deletes one bounded stale-file group in its own provider transaction.
         * CPOExecutor's limit is a limit on operations, not IDs inside an IN
         * clause, so merely queuing multiple delete operations would still keep
         * all groups in one long applyBatch transaction.
         */
        public void executeDelete(DeleteString deletes) {
            int deleteCount = deletes.getCount();
            if (deleteCount > 0) {
                // Preserve the original ordering: apply discovered-file updates
                // before removing stale entries, but do not retain the writer
                // while every stale entry on a large share is deleted.
                mUpdateExecutor.execute();

                Builder delete = ContentProviderOperation.newDelete(VideoStoreInternal.FILES_SCANNED);
                String deleteSelection = BaseColumns._ID + " IN (" + deletes.toString() + ")";
                if (log.isDebugEnabled()) log.debug("delete {} stale files WHERE {}", deleteCount, deleteSelection);
                delete.withSelection(deleteSelection, null);

                mUpdateExecutor.add(delete.build());
                mUpdateExecutor.execute();
                mDeletes += deleteCount;
            }
        }

        public void addInsert(FileScanInfo insert, long serverId) {
            if (log.isDebugEnabled()) log.debug("addInsert: adding in VideoStore and calling executor {} for serverId={}", insert._data, serverId);
            ContentValues item = insert.toContentValues();
            item.put(VideoStore.Files.FileColumns.ARCHOS_SMB_SERVER, serverId);
            mInsertExecutor.add(item);
        }

        public void executePending() {
            if (log.isDebugEnabled()) log.debug("executePending: process updates");
            mUpdateExecutor.execute();
            if (log.isDebugEnabled()) log.debug("executePending: process inserts");
            mInsertExecutor.execute();
            if (log.isDebugEnabled()) log.debug("executePending: done");
        }

        public int getInsertHandled() {
            return mInsertExecutor.getInsertCount();
        }
        public int getUpdatesHandled() {
            return mUpdateExecutor.getExecutionCount();
        }
        public int getDeletesHandled() {
            return mDeletes;
        }

    }
    // ---------------------------------------------------------------------- //
    // -- Subtitle scanner                                                 -- //
    // ---------------------------------------------------------------------- //
    private static class SubtitleInfo {
        public SubtitleInfo(long id, String accessPath, long size) {
            this.id = id;
            this.accessPath = accessPath;
            this.nameNoExt = ArchosMediaFile.getFileTitle(accessPath);
            this.size = size;
        }

        public final String nameNoExt;

        private final long id;
        private final String accessPath;
        private final long size;

        public boolean matchesVideo(String videoNameNoExt) {
            return nameNoExt.startsWith(videoNameNoExt);
        }

        // the diff between video.name & video.name.lang (here: "lang"), leading junk removed
        private String getLang(String videoNameNoExt) {
            String lang = null;
            if (nameNoExt.startsWith(videoNameNoExt)) {
                lang = nameNoExt.substring(videoNameNoExt.length());
                while (lang.length() > 0 && !Character.isLetterOrDigit(lang.charAt(0))) {
                    lang = lang.substring(1);
                }
                lang = lang.trim();
                if (lang.isEmpty()) {
                    lang = null;
                }
            }
            return lang;
        }

        public ContentValues getForVideo(long video_id, String videoNameNoExt) {
            ContentValues ret = new ContentValues();
            ret.put(VideoStore.Subtitle.SubtitleColumns.VIDEO_ID, Long.valueOf(video_id));
            ret.put(VideoStore.Subtitle.SubtitleColumns.FILE_ID, Long.valueOf(id));
            ret.put(VideoStore.Subtitle.SubtitleColumns.LANG, getLang(videoNameNoExt));
            ret.put(VideoStore.Subtitle.SubtitleColumns.DATA, accessPath);
            ret.put(VideoStore.Subtitle.SubtitleColumns.SIZE, Long.valueOf(size));
            return ret;
        }
    }

    private static final Uri SUBS_URI = VideoStore.Subtitle.CONTENT_URI;
    private static final String[] PROJ_ID_DATA_SIZE = {
        BaseColumns._ID,                        // 0
        VideoStore.MediaColumns.DATA,           // 1
        VideoStore.MediaColumns.SIZE,           // 2
        VideoStore.Files.FileColumns.BUCKET_ID, // 3
        VideoStore.Files.FileColumns.MEDIA_TYPE,// 4
    };
    // all videos and subtitles that are in a bucket that has subtitles ( type 5 )
    private static final String SEL_VIDS_N_SUBS = "bucket_id IN ( SELECT bucket_id FROM files WHERE media_type = 5 )" +
            " AND (media_type = 3 OR media_type = 5)";
    private static final String SEL_NEW_VIDS_N_SUBS = SEL_VIDS_N_SUBS + " AND _id NOT IN (SELECT file_id FROM subtitles)";

    private static int handleSubtitles(ContentResolver cr) {
        Uri uri = VideoStore.Files.getContentUri("external");
        String sortOrder = VideoStore.Video.VideoColumns.BUCKET_ID;
        // videos need name and id
        List<Pair<String, Long>> videos = new ArrayList<Pair<String, Long>>();
        // subtitles need id, name, size, language, ...
        List<SubtitleInfo> subs = new ArrayList<SubtitleInfo>();
        // inserts to do
        List<ContentValues> inserts = new ArrayList<ContentValues>();

        Cursor c = null;
        try {
            c = cr.query(uri, PROJ_ID_DATA_SIZE, SEL_NEW_VIDS_N_SUBS, null, sortOrder);
            if (c != null) {
                String lastBucket = null;
                while (c.moveToNext() & isForeground) {
                    long id = c.getLong(0);
                    String file = c.getString(1);
                    long size = c.getLong(2);
                    String bucketId = c.getString(3);
                    int mediaType = c.getInt(4);
                    // if bucket switches, handle old bucket
                    if (!bucketId.equals(lastBucket)) {
                        handleSubtitleBucket(videos, subs, inserts);
                        // update current bucket & empty lists
                        lastBucket = bucketId;
                        videos.clear();
                        subs.clear();
                    }
                    // add videos & subtitles to their lists
                    switch (mediaType) {
                        case VideoStore.Files.FileColumns.MEDIA_TYPE_VIDEO:
                            videos.add(Pair.create(ArchosMediaFile.getFileTitle(file), Long.valueOf(id)));
                            break;
                        case VideoStore.Files.FileColumns.MEDIA_TYPE_SUBTITLE:
                            subs.add(new SubtitleInfo(id, file, size));
                            break;
                        default:
                            // should be impossible
                            log.error("Bad MediaType:{} when scanning videos and subtitles", mediaType);
                            break;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error querying subtitles", e);
        } finally {
            if (c != null) c.close();
        }
        // handle any remaining videos and subtitles
        handleSubtitleBucket(videos, subs, inserts);

        // insert new subtitle associations
        if (inserts.size() > 0) {
            ContentValues[] values = new ContentValues[inserts.size()];
            values = inserts.toArray(values);
            return cr.bulkInsert(SUBS_URI, values);
        }
        return 0;
    }

    private static void handleSubtitleBucket(List<Pair<String, Long>> videos,
            List<SubtitleInfo> subs, List<ContentValues> inserts) {
        // no need to iterate over videos when there are no subs
        if (subs.size() == 0)
            return;
        // for each video check all subs
        for (Pair<String,Long> video : videos) {
            for (SubtitleInfo subtitle : subs) {
                if (subtitle.matchesVideo(video.first)) {
                    inserts.add(subtitle.getForVideo(video.second, video.first));
                }
            }
        }
    }

    // ---------------------------------------------------------------------- //
    // -- Utility logic                                                    -- //
    // ---------------------------------------------------------------------- //

    /**
     * transforms '/mnt/network/smb/GROUP/SERVER/..' into 'GROUP/SERVER'
     */
    public static String extractSmbServer(Uri uri) {
        if (!FileUtils.isLocal(uri)) {
            //special for smb :
            //for
            /*if(uri.getScheme().equals("smb://")){
                String path = uri.toString();
                String afterSmb = path.substring(6); // "smb://".length()
                int slash = afterSmb.indexOf(File.separatorChar);
                int secondSlash = slash;
                if (slash != -1)
                    secondSlash = afterSmb.indexOf(File.separatorChar, slash + 1);
                if (secondSlash != -1)
                    return path.substring(0, secondSlash + 7);
                return path;

            }
            else*/
                return uri.getScheme()+"://"+uri.getHost()+(uri.getPort()!=-1?":"+uri.getPort():"");

        }
        // this should not happen but we need to return some identifier
        // results in "/mnt/network/smb" considered the smb server.
        return "";
    }

    private static final String SMB_SERVER_SELECTION = MediaColumns.DATA + "=?";
    private static final String[] ID_PROJECTION = new String[] {
        BaseColumns._ID,
    };
    /** query database to find a certain server id, if it does not exist it is created in the database */
    private long getLightIndexServerId (String server) {
        Uri uri = VideoStore.SmbServer.getContentUri();
        ContentResolver cr = getContentResolver();
        String[] selectionArgs = new String[] { server };
        Cursor c = cr.query(uri, ID_PROJECTION, SMB_SERVER_SELECTION, selectionArgs, null);
        if (c != null) {
            Boolean returnResult = false;
            long result = -1;
            if (c.moveToFirst()) {
                // server is present
                returnResult = true;
                result = c.getLong(0);
            }
            c.close();
            if (returnResult)
                return result;
        }
        // if we are still here we need to insert that server.
        ContentValues cv = new ContentValues();
        cv.put(MediaColumns.DATA, server);
        cv.put(VideoStore.SmbServer.SmbServerColumns.LAST_SEEN, String.valueOf(System.currentTimeMillis() / 1000));
        cv.put(VideoStore.SmbServer.SmbServerColumns.ACTIVE, "1");
        long id = 0;
        Uri result = cr.insert(uri, cv);
        if (result != null) {
            String idString = FileUtils.getName(result);
            try {
                id = Long.parseLong(idString);
            } catch (NumberFormatException e) {
                log.error("Could not insert new SMB Servers", e);
            }
        }
        return id;
    }

    /**
     * class that holds info about data in database<p>
     * _id, _data, date_modified
     **/
    private static class PrescanItem {
        public static String[] PROJECTION = new String[] {
            BaseColumns._ID,
            MediaColumns.DATA,
            MediaColumns.DATE_MODIFIED,
            VideoStore.Video.VideoColumns.ARCHOS_UNIQUE_ID, //special for upnp
        };

        public final long _id;
        public String _data;
        public final long date_modified;
        public final String unique_id;
        // if false then file stayed untouched
        public boolean needsDelete = true;

        public PrescanItem(Cursor c) {
            if (c != null && !c.isClosed() && c.getCount() > 0) {
                _id = c.getLong(0);
                _data = c.getString(1);
                date_modified = c.getLong(2);
                unique_id = c.getString(3);
            } else {
                _id = -1;
                _data = null;
                date_modified = -1;
                unique_id = null;
            }
        }
    }

    /** class that contains scanned information for a file */
    private static class FileScanInfo {
        public String _data;
        public String _display_name;
        public long _size;
        public String mime_type;
        public long date_added;
        public long date_modified;
        public String title;
        public int bucket_id;
        public String bucket_display_name;
        public int format;
        public long parent;
        public int media_type;
        public long storage_id;
        public int video_stereo;
        public int video_definition;
        public String videoFormat;
        public String audioFormat;
        public String unique_id;
        // stolen from MTP - we actually don't need them..
        private static final int FORMAT_UNDEFINED = 0x3000;
        private static final int FORMAT_ASSOCIATION = 0x3001;

        public FileScanInfo(MetaFile2 f, long storageId) {
            if (f == null ) {
                log.error("Not exists / null:{}", f);
                return;
            }
            boolean isDir = f.isDirectory();
            Uri parentUri = FileUtils.getParentUrl(f.getUri());
            if (parentUri == null) {
                parentUri = Uri.parse("/");
            }
            _data = f.getUri().toString();
            _display_name = f.getName();
            if(f instanceof UpnpFile2)
                unique_id = ((UpnpFile2)f).getUniqueHash();
            else
                unique_id = "";
            _size = isDir ? 0 : f.length();
            MediaFileType mft = isDir ? null : ArchosMediaFile.getFileType(f.getExtension());
            mime_type = mft != null ? mft.mimeType : null;
            date_added = System.currentTimeMillis() / 1000L;
            // -1 if info not available, fallback to date_added, aka now
            long last_modified = f.lastModified();
            date_modified = last_modified > 0 ? last_modified / 1000L : date_added;
            title = f.getNameWithoutExtension();
            bucket_id = FileUtils.getBucketId(parentUri);
            bucket_display_name = parentUri.getLastPathSegment();
            format = isDir ? FORMAT_ASSOCIATION : FORMAT_UNDEFINED;
            // do not want that - it's slow
            parent = -1;
            int fileType = mft != null ? mft.fileType : 0;
            if (ArchosMediaFile.isAudioFileType(fileType)) {
                media_type = FileColumns.MEDIA_TYPE_AUDIO;
            } else if (ArchosMediaFile.isVideoFileType(fileType)) {
                media_type = FileColumns.MEDIA_TYPE_VIDEO;
            } else if (ArchosMediaFile.isImageFileType(fileType)) {
                media_type = FileColumns.MEDIA_TYPE_IMAGE;
            } else if (ArchosMediaFile.isPlayListFileType(fileType)) {
                media_type = FileColumns.MEDIA_TYPE_PLAYLIST;
            } else if (ArchosMediaFile.isSubtitleFileType(fileType)) {
                media_type = FileColumns.MEDIA_TYPE_SUBTITLE;
            } else {
                media_type = 0;
            }
            storage_id = storageId;

            if (media_type == FileColumns.MEDIA_TYPE_VIDEO) {
                /* Extract some info from the file name */
                VideoNameProcessor.ExtractedInfo nameInfo = VideoNameProcessor.extractInfoFromPath(f.getUri().toString());
                video_stereo = nameInfo.stereoType;
                video_definition = nameInfo.definition;
                videoFormat = nameInfo.videoFormat;
                audioFormat = nameInfo.audioFormat;
            }
        }

        public ContentValues toContentValues() {
            ContentValues cv = new ContentValues();

            cv.put(MediaColumns.DATA, _data);
            cv.put(MediaColumns.DISPLAY_NAME, _display_name);
            cv.put(MediaColumns.SIZE, String.valueOf(_size));
            cv.put(MediaColumns.DATE_ADDED, String.valueOf(date_added));
            cv.put(MediaColumns.DATE_MODIFIED, String.valueOf(date_modified));
            cv.put(FileColumns.MIME_TYPE, mime_type);
            cv.put(FileColumns.TITLE, title);
            cv.put(VideoColumns.BUCKET_ID, String.valueOf(bucket_id));
            cv.put(VideoColumns.BUCKET_DISPLAY_NAME, bucket_display_name);
            cv.put(FileColumns.FORMAT, String.valueOf(format));
            cv.put(FileColumns.PARENT, String.valueOf(parent));
            cv.put(FileColumns.MEDIA_TYPE, String.valueOf(media_type));
            cv.put(FileColumns.STORAGE_ID, String.valueOf(storage_id));
            cv.put(VideoColumns.ARCHOS_VIDEO_STEREO, String.valueOf(video_stereo));
            cv.put(VideoColumns.ARCHOS_VIDEO_DEFINITION, String.valueOf(video_definition));
            cv.put(VideoColumns.ARCHOS_UNIQUE_ID, String.valueOf(unique_id));
            cv.put(VideoColumns.ARCHOS_GUESSED_VIDEO_FORMAT, videoFormat);
            cv.put(VideoColumns.ARCHOS_GUESSED_AUDIO_FORMAT, audioFormat);
            return cv;
        }
    }

    private static final String WHERE_BUCKET = VideoColumns.BUCKET_ID + "=?";

    // ----------------- nfo file parsing --------------------------------
    private int handleNfoFiles(List<NfoParser.NfoFile> nfoFiles) {
        int handled = 0;

        if (nfoFiles == null)
            return handled;

        NfoParser.ImportContext importContext = new NfoParser.ImportContext();
        ContentResolver cr = getContentResolver();
        for (NfoParser.NfoFile nfo : nfoFiles) {
            // skip parsing if file has info already
            if (!hasScraperInfo(nfo.videoFile, cr)) {
                BaseTags tag = NfoParser.getTagForFile(nfo, this, importContext);
                if (tag != null) {
                    tag.save(this, nfo.videoFile);
                    handled++;
                }
            }
        }
        return handled;
    }

    private static final String WHERE_FILE = VideoStore.MediaColumns.DATA + "=?";
    private static final String[] PROJECT_ID = { BaseColumns._ID, VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID };
    private static boolean hasScraperInfo(Uri video, ContentResolver cr) {
        boolean result = false;
        String[] selectionArgs = { video.toString() };
        Cursor c = cr.query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, PROJECT_ID, WHERE_FILE, selectionArgs, null);
        if (c != null) {
            if (c.moveToFirst()) {
                // if ScraperID > 0
                if (c.getInt(1) > 0)
                    result = true;
            }
            c.close();
        }
        return result;
    }

    private boolean isHandlerThreadAlive() {
        return mHandlerThread != null && mHandlerThread.isAlive();
    }

    public void cleanup() {
        if (log.isDebugEnabled()) log.debug("cleanup");
        isForeground = false;
        // Stop the handler thread safely
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely(); // Safely quit the handler thread
        }
        if (mScanThread != null) {
            mScanThread.interrupt();
            mScanThread = null;
        }
        if (mRemoveFilesThread != null) {
            mRemoveFilesThread.interrupt();
            mRemoveFilesThread = null;
        }
        //stop Upnp service if on background
        UpnpServiceManager.getSingleton(this).releaseStopLock();
        if(! isForeground){
            UpnpServiceManager.stopServiceIfLaunched();
        }
        // Release any acquired locks (e.g., WifiLock)
        wifiLock = null;
        // Notify listeners that the scanner is stopping
        sIsScannerAlive = false;
        notifyListeners();
        // Handle any necessary cleanup or state management here
        nm.cancel(NOTIFICATION_ID);
    }

    @Override
    public void onStop(LifecycleOwner owner) {
        // App in background
        int pendingScans = com.archos.mediascraper.AutoScrapeService.getNetworkScanCount();
        if (log.isDebugEnabled()) log.debug("onStop: LifecycleOwner app in background, pendingScans={}", pendingScans);
        isForeground = false;
        // Only stop service if there are no pending network scans
        if (pendingScans == 0) {
            if (log.isDebugEnabled()) log.debug("onStop: no pending scans, stopping service");
            cleanup();
            stopSelf();
        } else {
            if (log.isDebugEnabled()) log.debug("onStop: {} pending scans, keeping service alive", pendingScans);
        }
    }

    @Override
    public void onStart(LifecycleOwner owner) {
        // App in foreground
        if (log.isDebugEnabled()) log.debug("onStart: LifecycleOwner app in foreground");
        isForeground = true;
    }
}
