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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentProviderOperation;
import android.content.ContentProviderOperation.Builder;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.provider.BaseColumns;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.archos.filecorelibrary.MetaFile;
import com.archos.medialib.R;
import com.archos.mediaprovider.ArchosMediaCommon;
import com.archos.mediaprovider.ArchosMediaFile;
import com.archos.mediaprovider.ArchosMediaIntent;
import com.archos.mediaprovider.ArchosMediaFile.MediaFileType;
import com.archos.mediaprovider.music.MusicStore.Audio.AudioColumns;
import com.archos.mediaprovider.music.MusicStore.MediaColumns;
import com.archos.mediaprovider.music.MusicStore.Files.FileColumns;
import com.archos.environment.ArchosUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkScannerServiceMusic extends Service implements Handler.Callback {
    private static final String TAG =  ArchosMediaCommon.TAG_PREFIX + "NetworkScannerServiceMusic";
    private static final boolean LOCAL_DBG = false;
    private static final boolean DBG = ArchosMediaCommon.PACKAGE_DBG & LOCAL_DBG;

    private static final boolean SCAN_MEDIA_ONLY = true;

    // handler message ids
    private static final int MESSAGE_KILL = 1;
    private static final int MESSAGE_DO_SCAN = 2;
    private static final int MESSAGE_DO_UNSCAN = 3;

    private Handler mHandler;
    private HandlerThread mHandlerThread;
    private final Object mDummy = new Object();
    private final ConcurrentHashMap<String, Object> mScanRequests = new ConcurrentHashMap<String, Object>(4, 0.75f, 2);
    private final ConcurrentHashMap<String, Object> mUnScanRequests = new ConcurrentHashMap<String, Object>(4, 0.75f, 2);

    public static boolean startIfHandles(Context context, Intent broadcast) {
        String action = broadcast.getAction();
        Uri data = broadcast.getData();
        if ((ArchosMediaIntent.isMusicScanIntent(action) || ArchosMediaIntent.isMusicRemoveIntent(action))
                && isSmbUri(data)) {
            Intent serviceIntent = new Intent(context, NetworkScannerServiceMusic.class);
            serviceIntent.setAction(action);
            serviceIntent.setData(data);
            context.startService(serviceIntent);
            return true;
        }
        return false;
    }

    private static boolean isSmbUri(Uri uri) {
        String schema = uri != null ? uri.getScheme() : null;
        return "smb".equals(schema);
    }

    public NetworkScannerServiceMusic() {
        if (DBG) Log.d(TAG, "NetworkScannerServiceMusic CTOR");
    }

    @Override
    protected void finalize() throws Throwable {
        if (DBG) Log.d(TAG, "NetworkScannerServiceMusic DTOR");
        super.finalize();
    }

    @Override
    public void onCreate() {
        if (DBG) Log.d(TAG, "onCreate");
        // setup handler
        mHandlerThread = new HandlerThread("ScanWorker", Process.THREAD_PRIORITY_BACKGROUND);
        mHandlerThread.start();
        Looper looper = mHandlerThread.getLooper();
        mHandler = new Handler(looper, this);
    }

    @Override
    public void onDestroy() {
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

        // forward events to background handler
        String action = intent.getAction();
        if (ArchosMediaIntent.isMusicScanIntent(action)) {
            Uri data = intent.getData();
            String key = data.toString();
            if (mScanRequests.putIfAbsent(key, mDummy) == null) {
                Message m = mHandler.obtainMessage(MESSAGE_DO_SCAN, startId, flags, data);
                mHandler.sendMessage(m);
            } else if (DBG) Log.d(TAG, "skip scanning " + key + ", already in queue");
        } else if (ArchosMediaIntent.isMusicRemoveIntent(action)) {
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
        MetaFile f = MetaFile.from(data);
        if (f == null) return;
        ContentResolver cr = getContentResolver();

        String path = f.getAccessPath();
        if (f.isJavaFile() && f.isDirectory())
            path = path + "/";
        String[] selectionArgs = { path };
        // send out a sticky broadcast telling the world that we started scanning
        Intent scannerIntent = new Intent(ArchosMediaIntent.ACTION_MUSIC_SCANNER_SCAN_STARTED, data);
        scannerIntent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
        sendBroadcast(scannerIntent);
        // also show a notification.
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        showNotification(nm, f.getDisplayPath(), R.string.network_unscan_msg);

        int deleted = cr.delete(MusicStoreInternal.FILES_SCANNED, IN_FOLDER_SELECT, selectionArgs);
        Log.d(TAG, "removed: " + deleted);

        // send a "done" notification
        Intent intent = new Intent(ArchosMediaIntent.ACTION_MUSIC_SCANNER_SCAN_FINISHED, data);
        intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
        sendBroadcast(intent);
        // and cancel the Notification
        hideNotification(nm);
    }

    private static final String IN_FOLDER_SELECT = MediaColumns.DATA + " LIKE ?||'%'";
    private static final String SELECT_ID = BaseColumns._ID + "=?";
    /** scans files into our db */
    private void doScan(Uri what) {
        if (DBG) Log.d(TAG, "doScan " + what);

        long start = DBG ? System.currentTimeMillis() : 0;
        MetaFile f = MetaFile.from(what);
        if (f != null) {
            if (DBG) Log.d(TAG, "doScan path resolved to:" + f.getAccessPath());
            ContentResolver cr = getContentResolver();

            // send out a sticky broadcast telling the world that we started scanning
            Intent scannerIntent = new Intent(ArchosMediaIntent.ACTION_MUSIC_SCANNER_SCAN_STARTED, what);
            scannerIntent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
            sendBroadcast(scannerIntent);
            // also show a notification.
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            showNotification(nm, f.getDisplayPath(), R.string.network_scan_msg);

            // extract server id string / number
            String server = extractSmbServer(f);
            long serverId = getLightIndexServerId(server);

            String path = f.getAccessPath();
            if (f.isJavaFile() && f.isDirectory())
                path = path + "/";

            // query database for all files we have already in that directory
            String[] selectionArgs = new String[] { path };
            Cursor prescan = cr.query(MusicStoreInternal.FILES_SCANNED, PrescanItem.PROJECTION, IN_FOLDER_SELECT, selectionArgs, null);
            // hashmap to contain all knows files + data, keyed by path
            HashMap<String, PrescanItem> prescanItemsMap = new HashMap<String, NetworkScannerServiceMusic.PrescanItem>();
            if (prescan != null) {
                while (prescan.moveToNext()) {
                    PrescanItem item = new PrescanItem(prescan);
                    prescanItemsMap.put(item._data, item);
                }
                prescan.close();
            }

            // prescan list is modified by listFilesRecursive to indicate whether
            // a file needs update or delete, newFiles will contain the new files.
            // ! this is the actual scanning process !
            List<FileScanInfo> newFiles = listFilesRecursive(f, prescanItemsMap);

            // insert new files - build a bulkInsert so this is only 1 transaction
            LinkedList<ContentValues> insertList = new LinkedList<ContentValues>();
            for (FileScanInfo fei : newFiles) {
                ContentValues item = fei.toContentValues();
                item.put(MusicStore.Files.FileColumns.ARCHOS_SMB_SERVER, String.valueOf(serverId));
                insertList.add(item);
                if (DBG) Log.d(TAG, "INSERT: " + fei._data);
            }
            ContentValues[] values = insertList.toArray(new ContentValues[insertList.size()]);
            // actual insert now
            int insertCount = values != null ? values.length : 0;
            cr.bulkInsert(MusicStoreInternal.FILES_SCANNED, values);

            // update & delete the rest - build ContentProviderOperations here so we need only 1 transaction
            StringBuilder deletes = new StringBuilder();
            int deleteCount = 0;
            int updateCount = 0;
            ArrayList<ContentProviderOperation> updates = new ArrayList<ContentProviderOperation>();
            for (PrescanItem item : prescanItemsMap.values()) {
                if (item.needsDelete) {
                    if (DBG) Log.d(TAG, "DELETE: " + item._data);
                    // append id to stringbuilder
                    deletes.append(item._id).append(',');
                    deleteCount++;
                } else if (item.update != null) {
                    if (DBG) Log.d(TAG, "UPDATE: " + item._data);
                    Builder update = ContentProviderOperation.newUpdate(MusicStoreInternal.FILES_SCANNED);
                    update.withValues(item.update.toContentValues());
                    update.withSelection(SELECT_ID, new String[]{ String.valueOf(item._id) });
                    updates.add(update.build());
                    updateCount++;
                } else {
                    if (DBG) Log.d(TAG, "IGNORE: " + item._data);
                }
            }
            // either null or the string w/o the last ','
            String deleteList = deletes.length() > 0 ? deletes.substring(0, deletes.length() - 1) : null;
            if (deleteList != null) {
                Builder delete = ContentProviderOperation.newDelete(MusicStoreInternal.FILES_SCANNED);
                String deleteSelection = BaseColumns._ID + " IN (" + deleteList + ")";
                if (DBG) Log.d(TAG, "delete WHERE " + deleteSelection);
                delete.withSelection(deleteSelection, null);
                updates.add(delete.build());
            }

            // perform the operations we built above
            if (updates.size() > 0) {
                if (DBG) Log.d(TAG, "performing operations:" + updates.size());
                try {
                    cr.applyBatch(MusicStore.AUTHORITY, updates);
                } catch (RemoteException e) {
                    Log.e(TAG, "Error during database update", e);
                } catch (OperationApplicationException e) {
                    Log.e(TAG, "Error during database update", e);
                }
            }

            Log.d(TAG, "added:" + insertCount + " modified:" + updateCount + " deleted:" + deleteCount);
            // send a "done" notification
            Intent intent = new Intent(ArchosMediaIntent.ACTION_MUSIC_SCANNER_SCAN_FINISHED, what);
            intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
            sendBroadcast(intent);
            // and cancel the Notification
            hideNotification(nm);
        }
        if (DBG) {
            long end = System.currentTimeMillis();
            Log.d(TAG, "doScan took:" + (end - start) + "ms");
        }
    }

    // ---------------------------------------------------------------------- //
    // -- Recursive file scanner magic                                     -- //
    // ---------------------------------------------------------------------- //
    private static List<FileScanInfo> listFilesRecursive(MetaFile start, HashMap<String,PrescanItem> prescanItemsMap) {
        List<FileScanInfo> result = new LinkedList<FileScanInfo>();
        int storageId = getStorageId(start.getAccessPath());
        addFilesRecursive(start, result, 0, storageId, prescanItemsMap);
        return result;
    }

    private static void addFilesRecursive(MetaFile path, List<FileScanInfo> list, int recursionDepth,
            int storageId, HashMap<String, PrescanItem> prescanItemsMap) {
        if (recursionDepth > 15) {
            Log.w(TAG, "too much recursion, not scanning " + path);
            return;
        }
        if (path != null && path.exists()) {
            if (path.isFile()) {
                if (!isValidType(path)) return;
                //if (ArchosMediaFile.isHiddenFile(path)) return;

                if (DBG) Log.d(TAG, "File:" + path);
                String p = path.getAccessPath();
                PrescanItem existingItem = null;
                if ((existingItem = prescanItemsMap.get(p)) != null) {
                    // file was already scanned - check if it changed
                    long knownDate = existingItem.date_modified;
                    long newDate = path.lastModified();
                    if (Math.abs(knownDate - newDate) < 3) {
                        existingItem.needsDelete = false;
                        return;
                    }
                    // file has changed - add as update
                    existingItem.needsDelete = false;
                    existingItem.update = new FileScanInfo(path, storageId);
                    return;
                }
                // file is new, add to list of new files
                list.add(new FileScanInfo(path, storageId));
                return;
            }
            if (path.isDirectory()) {
                MetaFile noMedia = MetaFile.from(path, ".nomedia");
                if (noMedia != null && noMedia.exists()) {
                    Log.d(TAG, "skipping " + path + " .nomedia!");
                    return;
                }
                MetaFile[] listDir = path.listFiles();
                if (listDir != null) {
                    for (MetaFile file : listDir) {
                        addFilesRecursive(file, list, recursionDepth + 1, storageId, prescanItemsMap);
                    }
                }
            } else {
                Log.w(TAG, "not a dir / file ?!:" + path);
            }
        } else {
            Log.w(TAG, "null or not exist:" + path);
        }
    }

    /** checks if the file should be scanned */
    private static boolean isValidType(MetaFile f) {
        if (!SCAN_MEDIA_ONLY) return true;
        MediaFileType mft = ArchosMediaFile.getFileType(f.getAccessPath());
        if (mft == null) {
            return false;
        }
        return ArchosMediaFile.isAudioFileType(mft.fileType);
    }

    /** calculates the storage id for network, offset by a big value */
    private static int getStorageId(String path) {
        if (path.startsWith("smb://"))
            // 0: EXTERNAL_SMB_PATH, 1: EXTERNAL_UPNP_PATH -> "smb://" = 2
            return getStorageId(2 + ArchosMediaCommon.LIGHT_INDEX_STORAGE_ID_OFFSET);

        Log.w(TAG, "path has no valid storage id:" + path);
        return 0;
    }

    public static int getStorageId(int index) {
        // storage ID is 0x00010001 for primary storage,
        // then 0x00020001, 0x00030001, etc. for secondary storages
        return ((index + 1) << 16) + 1;
    }

    // ---------------------------------------------------------------------- //
    // -- Utility logic                                                    -- //
    // ---------------------------------------------------------------------- //
    private static final int NOTIFICATION_ID = 1;
    private static final String notifChannelId = "NetworkScannerServiceMusic_id";
    private static final String notifChannelName = "NetworkScannerServiceMusic";
    private static final String notifChannelDescr = "NetworkScannerServiceMusic";
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
        String notifyPath = path;
        Intent notificationIntent = new Intent(this, NetworkScannerServiceMusic.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
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
    public static String extractSmbServer(MetaFile f) {
        if (f.isSmbFile()) {
            String path = f.getAccessPath();
            String afterSmb = path.substring(6); // "smb://".length()
            int slash = afterSmb.indexOf(File.separatorChar);
            int secondSlash = slash;
            if (slash != -1)
                secondSlash = afterSmb.indexOf(File.separatorChar, slash + 1);
            if (secondSlash != -1)
                return path.substring(0, secondSlash + 7);
            return path;
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
        Uri uri = MusicStore.SmbServer.getContentUri();
        ContentResolver cr = getContentResolver();
        String[] selectionArgs = new String[] { server };
        Cursor c = cr.query(uri, ID_PROJECTION, SMB_SERVER_SELECTION, selectionArgs, null);
        if (c != null) {
            if (c.moveToFirst()) {
                // server is present
                return c.getLong(0);
            }
            c.close();
        }
        // if we are still here we need to insert that server.
        ContentValues cv = new ContentValues();
        cv.put(MediaColumns.DATA, server);
        cv.put(MusicStore.SmbServer.SmbServerColumns.LAST_SEEN, String.valueOf(System.currentTimeMillis() / 1000));
        cv.put(MusicStore.SmbServer.SmbServerColumns.ACTIVE, "1");
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

    /** class that holds info about data in database */
    private static class PrescanItem {
        public static String[] PROJECTION = new String[] {
            BaseColumns._ID,
            MediaColumns.DATA,
            MediaColumns.DATE_MODIFIED
        };

        public final long _id;
        public final String _data;
        public final long date_modified;

        // points to updated data if present
        public FileScanInfo update = null;
        // if false then file stayed untouched
        public boolean needsDelete = true;

        public PrescanItem(Cursor c) {
            _id = c.getLong(0);
            _data = c.getString(1);
            date_modified = c.getLong(2);
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

        // stolen from MTP - we actually don't need them..
        private static final int FORMAT_UNDEFINED = 0x3000;
        private static final int FORMAT_ASSOCIATION = 0x3001;

        public FileScanInfo(MetaFile f, int storageId) {
            if (f == null || !f.exists()) {
                Log.e(TAG, "Not exists / null:" + f);
                return;
            }
            boolean isDir = f.isDirectory();
            MetaFile parentFile = f.getParentFile();
            if (parentFile == null) {
                parentFile = MetaFile.from(new File("/"));
            }
            _data = f.getAccessPath();
            _display_name = f.getName();
            _size = isDir ? 0 : f.length();
            MediaFileType mft = isDir ? null : ArchosMediaFile.getFileType(_data);
            mime_type = mft != null ? mft.mimeType : null;
            date_added = System.currentTimeMillis() / 1000L;
            // -1 if info not available, fallback to date_added, aka now
            long last_modified = f.lastModified();
            date_modified = last_modified > 0 ? last_modified / 1000L : date_added;
            title = getTitle(_display_name);
            bucket_id = parentFile.getAccessPath().toLowerCase(Locale.ROOT).hashCode();
            bucket_display_name = parentFile.getName();
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
            } else {
                media_type = 0;
            }
            storage_id = storageId;
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
            cv.put(AudioColumns.BUCKET_ID, String.valueOf(bucket_id));
            cv.put(AudioColumns.BUCKET_DISPLAY_NAME, bucket_display_name);
            cv.put(FileColumns.FORMAT, String.valueOf(format));
            cv.put(FileColumns.PARENT, String.valueOf(parent));
            cv.put(FileColumns.MEDIA_TYPE, String.valueOf(media_type));
            cv.put(FileColumns.STORAGE_ID, String.valueOf(storage_id));
            return cv;
        }

        private static String getTitle(String fileName) {
            // truncate the file extension (if any)
            int lastDot = fileName.lastIndexOf('.');
            if (lastDot > 0) {
                return fileName.substring(0, lastDot);
            }
            return fileName;
        }
    }

}
