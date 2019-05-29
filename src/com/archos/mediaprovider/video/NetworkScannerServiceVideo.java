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
import android.app.PendingIntent;
import android.support.v4.app.NotificationCompat;
import android.app.Service;
import android.content.ContentProviderOperation;
import android.content.ContentProviderOperation.Builder;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Log;
import android.util.Pair;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.FileUtils;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.mediacenter.filecoreextension.UriUtils;
import com.archos.mediacenter.filecoreextension.upnp2.MetaFileFactoryWithUpnp;
import com.archos.mediacenter.filecoreextension.upnp2.UpnpFile2;
import com.archos.mediacenter.filecoreextension.upnp2.UpnpServiceManager;
import com.archos.mediacenter.utils.AppState;
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
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
@SuppressLint("LongLogTag")
public class NetworkScannerServiceVideo extends Service implements Handler.Callback {
    /*
        explanation about upnp indexing behaviour
        a movie Dumbo.mkv is in /Video/All Videos/ and /Video/Movies/Folder1 and Video/Movies/Folder2
        this is the same file
        we index /Video/Movies
        the file will be found twice but indexed once with the URI of the first one
        some time after, we index /Video/All Videos/
        dumbo will be found again and identified as the same file, and the uri will be updated

     */

    private static final String TAG =  ArchosMediaCommon.TAG_PREFIX + "NetworkScannerServiceVideo";
    private static final boolean LOCAL_DBG = false;
    private static final boolean DBG = ArchosMediaCommon.PACKAGE_DBG & LOCAL_DBG;

    private static final boolean SCAN_MEDIA_ONLY = true;

    // handler message ids
    private static final int MESSAGE_KILL = 1;
    private static final int MESSAGE_DO_SCAN = 2;
    private static final int MESSAGE_DO_UNSCAN = 3;
    public static final String RECORD_SCAN_LOG_EXTRA = "record_scan_log_extra";
    public static final String RECORD_ON_FAIL_PREFERENCE = "record_on_fail_preference_extra";
    public static final String RECORD_END_OF_SCAN_PREFERENCE = "record_on_end_preference_extra";

    private Handler mHandler;
    private HandlerThread mHandlerThread;
    private final Object mDummy = new Object();
    private final ConcurrentHashMap<String, Object> mScanRequests = new ConcurrentHashMap<String, Object>(4, 0.75f, 2);
    private final ConcurrentHashMap<String, Object> mUnScanRequests = new ConcurrentHashMap<String, Object>(4, 0.75f, 2);
    private Blacklist mBlacklist;
    private static boolean sIsScannerAlive;
    private boolean mRecordLog;
    private String mRecordOnFailPreference;
    private String mRecordEndOfScanPreference;

    public static boolean startIfHandles(Context context, Intent broadcast) {
        String action = broadcast.getAction();
        Uri data = broadcast.getData();
        if ((ArchosMediaIntent.isVideoScanIntent(action) || ArchosMediaIntent.isVideoRemoveIntent(action))
                && willBeScanned(data)) {
            if (DBG) Log.d(TAG, "startIfHandles");
            Intent serviceIntent = new Intent(context, NetworkScannerServiceVideo.class);
            serviceIntent.setAction(action);
            serviceIntent.setData(data);
            if(broadcast.getExtras()!=null)
                serviceIntent.putExtras(broadcast.getExtras()); //in case we have an extra... such as "recordLogExtra"
            context.startService(serviceIntent);
            return true;
        }
        return false;
    }
    public static boolean willBeScanned(Uri uri){ //returns whether or not a video will be scanned by NetworkScannerServiceVideo
        return (!FileUtils.isLocal(uri)||UriUtils.isContentUri(uri))&& UriUtils.isIndexable(uri);
    }
    private static boolean isSmbUri(Uri uri) {
        String schema = uri != null ? uri.getScheme() : null;
        return "smb".equals(schema);
    }

    public NetworkScannerServiceVideo() {
        if (DBG) Log.d(TAG, "NetworkScannerServiceVideo CTOR");
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
            for(ScannerListener listener : sListener)
                listener.onScannerStateChanged();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        if (DBG) Log.d(TAG, "NetworkScannerServiceVideo DTOR");
        super.finalize();
    }

    @Override
    public void onCreate() {
        if (DBG) Log.d(TAG, "onCreate");
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
    }

    @Override
    public void onDestroy() {
        sIsScannerAlive = false;
        notifyListeners();
        //stop Upnp service if on background
        UpnpServiceManager.getSingleton(this).releaseStopLock();
        if(!AppState.isForeGround()){
            UpnpServiceManager.stopServiceIfLaunched();
        }
        if(mRecordEndOfScanPreference!=null) //time to set end of scan
            PreferenceManager.getDefaultSharedPreferences(this).edit().putLong(mRecordEndOfScanPreference, System.currentTimeMillis()).apply();
        if (DBG) Log.d(TAG, "onDestroy");
        // remove handler
        mHandlerThread.quit();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // intents delivered here
        if (DBG) Log.d(TAG, "onStartCommand:" + intent + " flags:" + flags + " startId:" + startId);

        if (intent == null || intent.getAction() == null)
            return START_NOT_STICKY;
        if(intent.getExtras()!=null) {
            if (DBG) Log.d(TAG, "extra not null");
            mRecordLog = intent.getExtras().getBoolean(RECORD_SCAN_LOG_EXTRA, false);
            mRecordOnFailPreference = intent.getExtras().getString(RECORD_ON_FAIL_PREFERENCE, null);
            if(mRecordEndOfScanPreference==null) //reset only when null to avoid pred not being written when another intent with no pref comes just after (this will be written when service stops)
            mRecordEndOfScanPreference = intent.getExtras().getString(RECORD_END_OF_SCAN_PREFERENCE,null);
        }
        else {
            if (DBG) Log.d(TAG, "extra null");
            mRecordLog = false;
            mRecordOnFailPreference = null;
            mRecordEndOfScanPreference =  null;
        }
        
        // forward events to background handler
        String action = intent.getAction();
        if (ArchosMediaIntent.isVideoScanIntent(action)) {
            Uri data = intent.getData();
            String key = data.toString();
            if (mScanRequests.putIfAbsent(key, mDummy) == null) {
                Message m = mHandler.obtainMessage(MESSAGE_DO_SCAN, startId, flags, data);
                mHandler.sendMessage(m);
            } else if (DBG) Log.d(TAG, "skip scanning " + key + ", already in queue");
        } else if (ArchosMediaIntent.isVideoRemoveIntent(action)) {
            Uri data = intent.getData();
            String key = data.toString();
            if (mUnScanRequests.putIfAbsent(key, mDummy) == null) {
                Message m = mHandler.obtainMessage(MESSAGE_DO_UNSCAN, startId, flags, data);
                mHandler.sendMessage(m);
            } else if (DBG) Log.d(TAG, "skip unscanning " + key + ", already in queue");
        }
        return Service.START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (DBG) Log.d(TAG, "onBind");
        // this service can't bind
        return null;
    }

    /** handler implementation */
    @Override
    public boolean handleMessage(Message msg) {
        if (DBG) Log.d(TAG, "handleMessage:" + msg + " what:" + msg.what + " startid:" + msg.arg1);
        Uri uri;
        String key;
        switch (msg.what) {
            case MESSAGE_KILL:
                if (msg.arg1 != -1)
                    stopSelf(msg.arg1);
                break;
            case MESSAGE_DO_SCAN:
                uri = (Uri) msg.obj;
                key = uri.toString();
                doScan(uri);
                mScanRequests.remove(key);
                mHandler.obtainMessage(MESSAGE_KILL, msg.arg1, msg.arg2).sendToTarget();
                break;
            case MESSAGE_DO_UNSCAN:
                uri = (Uri) msg.obj;
                key = uri.toString();
                doRemoveFiles(uri);
                mUnScanRequests.remove(key);
                mHandler.obtainMessage(MESSAGE_KILL, msg.arg1, msg.arg2).sendToTarget();
                break;
            default:
                break;
        }
        return true;
    }

    // -----------------------------------------------------------------------//
    // --  import / delete logic                                            --//
    // -----------------------------------------------------------------------//

    /** removes files from our db */
    private void doRemoveFiles(Uri data) {
        if (DBG) Log.d(TAG, "doRemoveFiles " + data);
        if (data == null) return;
        ContentResolver cr = getContentResolver();

        String path = data.toString();
        String[] selectionArgs = { path };
        // send out a sticky broadcast telling the world that we started scanning
        Intent scannerIntent = new Intent(ArchosMediaIntent.ACTION_VIDEO_SCANNER_SCAN_STARTED, data);
        scannerIntent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
        sendBroadcast(scannerIntent);
        // also show a notification.
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        showNotification(nm, data.toString(), R.string.network_unscan_msg);

        int deleted = cr.delete(VideoStoreInternal.FILES_SCANNED, IN_FOLDER_SELECT, selectionArgs);
        Log.d(TAG, "removed: " + deleted);

        // send a "done" notification
        Intent intent = new Intent(ArchosMediaIntent.ACTION_VIDEO_SCANNER_SCAN_FINISHED, data);
        intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
        sendBroadcast(intent);
        // and cancel the Notification
        hideNotification(nm);
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
    private void doScan(Uri what) {
        if (DBG) Log.d(TAG, "doScan " + what);
        mFoundFiles = 0;
        long start = DBG ? System.currentTimeMillis() : 0;
        MetaFile2 f = null;
        try {
            f = MetaFileFactoryWithUpnp.getMetaFileForUrl(what);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (f != null) {
            if (DBG) Log.d(TAG, "doScan path resolved to:" + f.getUri().toString());
            ContentResolver cr = getContentResolver();
            if(mRecordLog) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss.SSS");
                    Date dt = new Date();
                    String S = sdf.format(dt);
                    FileWriter fw = NetworkAutoRefresh.getDebugFileWriter(this);
                    fw.append(S + ": start of scan for " + what + "\n");
                    fw.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
            WifiLock wifiLock = wifiManager.createWifiLock("ArchosNetworkIndexer");
            wifiLock.acquire();

            // send out a sticky broadcast telling the world that we started scanning
            Intent scannerIntent = new Intent(ArchosMediaIntent.ACTION_VIDEO_SCANNER_SCAN_STARTED, what);
            scannerIntent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
            sendBroadcast(scannerIntent);
            // also show a notification.
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            showNotification(nm, f.getUri().toString(), R.string.network_scan_msg);


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
            if("upnp".equals(f.getUri().getScheme())){
                path = "upnp://" + f.getUri().getHost()+"/";
                upnpUri = f.getUri().toString();
                if (f.isDirectory()&&!upnpUri.endsWith("/"))
                    upnpUri = upnpUri + "/";
            }
            else {
                path = f.getUri().toString();
                if (f.isDirectory()&&!path.endsWith("/"))
                    path = path + "/";
            }
            // query database for all files we have already in that directory
            String[] selectionArgs = new String[] { path };
            Cursor prescan = cr.query(VideoStoreInternal.FILES_SCANNED, PrescanItem.PROJECTION, IN_FOLDER_SELECT, selectionArgs, null);
            // hashmap to contain all knows files + data, keyed by path
            HashMap<String, PrescanItem> prescanItemsMap = new HashMap<String, NetworkScannerServiceVideo.PrescanItem>();
            if (prescan != null) {
                while (prescan.moveToNext()) {
                    PrescanItem item = new PrescanItem(prescan);
                    if(upnpUri!=null&&!item._data.startsWith(upnpUri)) { // if this isn't in folder about to be listed, we won't need to delete it
                        item.needsDelete = false;
                    }

                    if(item.unique_id!=null && !item.unique_id.isEmpty())
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
            String server = extractSmbServer(f.getUri());
            long serverId = getLightIndexServerId(server);
            FileVisitListener fileVisitListener = new FileVisitListener(
                    mBlacklist, prescanItemsMap, nfoScanEnabled, bulkHandler, serverId);

            FileVisitor.visit(f, 15, fileVisitListener);
            // once all files where visited we have inserted, updated or deleted files in the db.
            // Nfo has also been processed
            List<MetaFile2> lastPlayedDbs = fileVisitListener.getLastPlayedDbs();

            int insertCount = bulkHandler.getInsertHandled();
            int updateCount = bulkHandler.getUpdatesHandled();
            int deleteCount = bulkHandler.getDeletesHandled();
            Log.d(TAG, "added:" + insertCount + " modified:" + updateCount + " deleted:" + deleteCount);

            int newSubs = handleSubtitles(cr);
            Log.d(TAG, "subtitles:" + newSubs);
            // send a "done" notification
            Intent intent = new Intent(ArchosMediaIntent.ACTION_VIDEO_SCANNER_SCAN_FINISHED, what);
            intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
            sendBroadcast(intent);
            // and cancel the Notification
            hideNotification(nm);
            if(mRecordLog) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss.SSS");
                    Date dt = new Date();
                    String S = sdf.format(dt);
                    FileWriter fw = NetworkAutoRefresh.getDebugFileWriter(this);
                    fw.append(S + " end of scan for " + what + "\n");
                    fw.append("added:" + insertCount + " modified:" + updateCount + " deleted:" + deleteCount + " listed files " + mFoundFiles + "\n");
                    fw.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            wifiLock.release();

        }
        else if(mRecordLog&&mRecordOnFailPreference!=null){
            PreferenceManager.getDefaultSharedPreferences(this).edit().putInt(mRecordOnFailPreference, -1).commit();//unable to reach server
        }
        if (DBG) {
            long end = System.currentTimeMillis();
            Log.d(TAG, "doScan took:" + (end - start) + "ms");
        }
    }

    // ---------------------------------------------------------------------- //
    // -- Recursive file scanner magic                                     -- //
    // ---------------------------------------------------------------------- //
    private static class FileVisitListener implements FileVisitor.Listener {
        private final BulkOperationHandler mBulkHandler;
        private final HashMap<String, PrescanItem> mPrescanItemsMap;
        private final List<MetaFile2> mLastPlayedDbs = new ArrayList<MetaFile2>();
        private final boolean mNfoScanEnabled;
        private final long mServerId;
        private final ArrayList<String> mAlreadyAddedUpnpFiles; //for files analysed DURING scan process
        private int mStorageId;

        private final Blacklist mBlacklist;

        public FileVisitListener(Blacklist blacklist, HashMap<String, PrescanItem> prescanItemsMap,
                boolean nfoScanEnabled, BulkOperationHandler bulkHandler, long serverId) {
            mBlacklist = blacklist;
            mPrescanItemsMap = prescanItemsMap;
            mNfoScanEnabled = nfoScanEnabled;
            mBulkHandler = bulkHandler;
            mServerId = serverId;
            mAlreadyAddedUpnpFiles = new ArrayList<>();
        }

        public List<MetaFile2> getLastPlayedDbs() {
            return mLastPlayedDbs;
        }

        @Override
        public void onStart(MetaFile2 root) {
            mStorageId = getStorageId(root.getUri().toString());
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
                Log.d(TAG, "skipping " + directory + ", .hidden!");
                return false;
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
            if (mBlacklist.isBlacklisted(file.getUri())) return;

            if (DBG) Log.d(TAG, "File:" + file.getUri().toString());
            String p = file.getUri().toString();
            PrescanItem existingItem = null;
            String uniqueId = "";
            //special case for upnp : use unique id
            if(file instanceof UpnpFile2){
                Log.d(TAG, "File is upnp " + ((UpnpFile2)file).getUniqueHash());
                uniqueId = ((UpnpFile2)file).getUniqueHash();
                existingItem = mPrescanItemsMap.get(((UpnpFile2)file).getUniqueHash());
            }
            else{
                existingItem = mPrescanItemsMap.get(p);
                uniqueId = p;
            }

            if ((existingItem) != null) {
                // file was already scanned, it does not need to be deleted
                existingItem.needsDelete = false;
                if (DBG) Log.d(TAG, "File isn't new:" + file.getName());
                // check if it is untouched or needs an update
                long knownDate = existingItem.date_modified;
                long newDate = file.lastModified() / 1000;
                if (Math.abs(knownDate - newDate) > 3 || !file.getUri().toString().equals(existingItem._data)) {
                    if (DBG) Log.d(TAG, "Updating:" + file.getName());
                    // file has changed - add the update
                    mBulkHandler.addUpdate(new FileScanInfo(file, mStorageId),
                            existingItem._id);
                }
            } else if(!mAlreadyAddedUpnpFiles.contains(uniqueId)){
                // file is new, add as insert
                if (DBG) Log.d(TAG, "File is new:" + file.getUri().toString());
                mAlreadyAddedUpnpFiles.add(uniqueId); // needed because main difference with usual indexing : a same file can be found twice in one round
                mBulkHandler.addInsert(new FileScanInfo(file, mStorageId), mServerId);
            }
            else
            if (DBG) Log.d(TAG, "Filealready scanned :" + file.getName());
            // nfo are now handled in autoscrapeservice
        }

        @Override
        public void onOtherType(MetaFile2 file) {
            // ignored
        }

        @Override
        public void onStop(MetaFile2 root) {
            // once we are done traversing the directories check for files that
            // were not seen and delete them
            DeleteString deletes = new DeleteString();
            for (PrescanItem item : mPrescanItemsMap.values()) {
                if (item.needsDelete) {
                    // append id to delete string
                    deletes.add(item._id);
                }
            }
            mBulkHandler.addDelete(deletes);

            // force execution of all pending operations
            mBulkHandler.executePending();
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

            Log.w(TAG, "path has no valid storage id: " + path);
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

        public void addDelete(DeleteString deletes) {
            int deleteCount = deletes.getCount();
            if (deleteCount > 0) {
                Builder delete = ContentProviderOperation.newDelete(VideoStoreInternal.FILES_SCANNED);
                String deleteSelection = BaseColumns._ID + " IN (" + deletes.toString() + ")";
                if (DBG) Log.d(TAG, "delete WHERE " + deleteSelection);
                delete.withSelection(deleteSelection, null);

                mUpdateExecutor.add(delete.build());
                mDeletes += deleteCount;
            }
        }

        public void addInsert(FileScanInfo insert, long serverId) {
            ContentValues item = insert.toContentValues();
            item.put(VideoStore.Files.FileColumns.ARCHOS_SMB_SERVER, Long.valueOf(serverId));
            mInsertExecutor.add(item);
        }

        public void executePending() {
            mUpdateExecutor.execute();
            mInsertExecutor.execute();

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

    private static int handleSubtitles(ContentResolver cr) {
        Uri uri = VideoStore.Files.getContentUri("external");
        String sortOrder = VideoStore.Video.VideoColumns.BUCKET_ID;
        // videos need name and id
        List<Pair<String, Long>> videos = new ArrayList<Pair<String, Long>>();
        // subtitles need id, name, size, language, ...
        List<SubtitleInfo> subs = new ArrayList<SubtitleInfo>();
        // inserts to do
        List<ContentValues> inserts = new ArrayList<ContentValues>();

        Cursor c = cr.query(uri, PROJ_ID_DATA_SIZE, SEL_VIDS_N_SUBS, null, sortOrder);
        if (c != null) {
            String lastBucket = null;
            while (c.moveToNext()) {
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
                        Log.e(TAG, "Bad MediaType:" + mediaType + " when scanning videos and subtitles");
                        break;
                }

            }
            c.close();
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
    private static final int NOTIFICATION_ID = 1;
    private static final String notifChannelId = "NetworkScannerServiceVideo_id";
    private static final String notifChannelName = "NetworkScannerServiceVideo";
    private static final String notifChannelDescr = "NetworkScannerServiceVideo";
    /** shows a notification */
    private void showNotification(NotificationManager nm, String path, int titleId){
        // Create the NotificationChannel, but only on API 26+ because the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel mNotifChannel = new NotificationChannel(notifChannelId, notifChannelName,
                    nm.IMPORTANCE_LOW);
            mNotifChannel.setDescription(notifChannelDescr);
            if (nm != null)
                nm.createNotificationChannel(mNotifChannel);
        }
        Intent notificationIntent = new Intent(this, NetworkScannerServiceVideo.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        String notifyPath = path;
        NotificationCompat.Builder n = new NotificationCompat.Builder(this, notifChannelId)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(getString(titleId))
                .setContentText(notifyPath)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true).setTicker(null).setOnlyAlertOnce(true).setContentIntent(contentIntent).setOngoing(true);
        nm.notify(NOTIFICATION_ID, n.build());
    }
    /** cancels the notification */
    private static void hideNotification(NotificationManager nm) {
        nm.cancel(NOTIFICATION_ID);
    }

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
            String idString = result.getLastPathSegment();
            try {
                id = Long.parseLong(idString);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Could not insert new SMB Servers", e);
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
            _id = c.getLong(0);
            _data = c.getString(1);
            date_modified = c.getLong(2);
            unique_id = c.getString(3);
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
        public int storage_id;
        public int video_stereo;
        public int video_definition;
        public String videoFormat;
        public String audioFormat;
        public String unique_id;
        // stolen from MTP - we actually don't need them..
        private static final int FORMAT_UNDEFINED = 0x3000;
        private static final int FORMAT_ASSOCIATION = 0x3001;

        public FileScanInfo(MetaFile2 f, int storageId) {
            if (f == null ) {
                Log.e(TAG, "Not exists / null:" + f);
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
}
