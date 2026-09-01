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

import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.provider.BaseColumns;
import android.util.Pair;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.jcifs.JcifsFileEditor;
import com.archos.filecorelibrary.samba.SambaDiscovery;
import com.archos.mediacenter.filecoreextension.upnp2.FileEditorFactoryWithUpnp;
import com.archos.mediacenter.filecoreextension.upnp2.UpnpServiceManager;
import com.archos.environment.NetworkState;
import com.archos.mediaprovider.video.VideoStore.MediaColumns;

import org.jupnp.model.meta.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** handles visibility updates of smb://server/share type servers in the database */
public class RemoteStateService extends Service implements UpnpServiceManager.Listener, DefaultLifecycleObserver {
    private static final Logger log = LoggerFactory.getLogger(RemoteStateService.class);

    private static volatile boolean isForeground = true;
    private static volatile boolean mHandleDbInProgress = false;

    private static final int REMOTE_CHECK_RETRY_COUNT = 3;
    private static final int REMOTE_CHECK_RETRY_DELAY_MS = 5000; // 5 seconds

    private static final Uri NOTIFY_URI = VideoStore.ALL_CONTENT_URI;
    private static final Uri SERVER_URI = VideoStore.SmbServer.getContentUri();

    private static final String[] PROJECTION_SERVERS = new String[] {
            BaseColumns._ID,
            MediaColumns.DATA,
            VideoStore.SmbServer.SmbServerColumns.ACTIVE
    };
    private static final int COLUMN_ID = 0;
    private static final int COLUMN_DATA = 1;
    private static final int COLUMN_ACTIVE = 2;

    private static final String SELECTION_ID = BaseColumns._ID + "=?";
    private static final String SELECTION_LOCAL_REMOTE =
            MediaColumns.DATA + " LIKE 'smb://%' OR " +
                    MediaColumns.DATA + " LIKE 'upnp://%' OR " +
                    MediaColumns.DATA + " LIKE 'smbj://%'";
    private static final String SELECTION_DISTANT_REMOTE =
            MediaColumns.DATA + " LIKE 'ftps://%' OR " +
                    MediaColumns.DATA + " LIKE 'ftp://%' OR " +
                    MediaColumns.DATA + " LIKE 'sftp://%' OR " +
                    MediaColumns.DATA + " LIKE 'sshj://%' OR " +
                    MediaColumns.DATA + " LIKE 'webdav://%' OR " +
                    MediaColumns.DATA + " LIKE 'webdavs://%'";
    private static final String SELECTION_ALL_NETWORK = SELECTION_LOCAL_REMOTE+" OR "+SELECTION_DISTANT_REMOTE;
    public static final String ACTION_CHECK_SMB = "archos.intent.action.CHECK_SMB";
    private ConcurrentHashMap<String, Pair<Long, Integer>> mUpnpId; //store name, id and active state
    private boolean mUpnpDiscoveryStarted;
    private boolean mServerDbUpdated;

    @Override
    public void onCreate() {
        super.onCreate();
        if (log.isDebugEnabled()) log.debug("onCreate() {}", this);
        // Register as a lifecycle observer
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    }

    @Override
    public void onDestroy() {
        if (log.isDebugEnabled()) log.debug("onDestroy");
        ProcessLifecycleOwner.get().getLifecycle().removeObserver(this);
        UpnpServiceManager.startServiceIfNeeded(this).removeListener(this);
        if (mUpnpId != null) {
            mUpnpId.clear();
            mUpnpId = null;
        }
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (log.isDebugEnabled()) log.debug("onStartCommand: {}", intent);
        if (!isForeground) return START_NOT_STICKY; // prevent to be executed if app is in background
        if (intent != null && ACTION_CHECK_SMB.equals(intent.getAction())) {
            // Prevent multiple concurrent handleDb operations to avoid thread explosion
            // and concurrent database access issues
            if (mHandleDbInProgress) {
                if (log.isDebugEnabled()) log.debug("onStartCommand: handleDb already in progress, skipping");
                return START_STICKY;
            }
            mHandleDbInProgress = true;
            NetworkState state = NetworkState.instance(this);
            state.updateFrom();
            // Move database operations to background thread to prevent ANR on main thread
            final boolean hasConnection = state.isConnected();
            final boolean hasLocalConnection = state.hasLocalConnection();
            new Thread(() -> {
                try {
                    handleDb(RemoteStateService.this, hasConnection, hasLocalConnection);
                } finally {
                    mHandleDbInProgress = false;
                }
            }).start();
        }
        // Keep the service running
        return START_STICKY;
    }

    /** use to issue a check of the smb:// state */
    public static void start(Context context) {
        if (log.isDebugEnabled()) log.debug("start");
        if (!isForeground) return;
        Intent intent = new Intent(context, RemoteStateService.class);
        intent.setAction(ACTION_CHECK_SMB);
        try {
            context.startService(intent);
        } catch (IllegalStateException e) {
            log.warn("start: Failed to start RemoteStateService despite lifecycle check - timing issue", e);
        }
    }

    public static void stop(Context context) {
        if (log.isDebugEnabled()) log.debug("stop");
        Intent intent = new Intent(context, RemoteStateService.class);
        try {
            context.stopService(intent);
        } catch (Exception e) {
            log.error("DeadSystemException caught while stopping RemoteStateService", e);
        }
    }

    protected void handleDb(Context context, boolean hasConnection, boolean hasLocalConnection) {
        if(mUpnpId==null) mUpnpId =new ConcurrentHashMap<>();
        else mUpnpId.clear();
        final ContentResolver cr = context.getContentResolver();
        if (log.isDebugEnabled()) log.debug("handleDb: hasConnection={}, hasLocalConnection={}", hasConnection, hasLocalConnection);
        if (hasConnection) {
            //Lmhosts.reset();
            final long now = System.currentTimeMillis() / 1000;
            // list all servers in the db
            Cursor c = cr.query(SERVER_URI, PROJECTION_SERVERS, SELECTION_ALL_NETWORK, null, null);
            if (c != null) {
                if (log.isDebugEnabled()) log.debug("found {} servers", c.getCount());
                mServerDbUpdated = false;
                while (c.moveToNext() && isForeground) {
                    final long id = c.getLong(COLUMN_ID);
                    final String server = c.getString(COLUMN_DATA);
                    final int active = c.getInt(COLUMN_ACTIVE);
                    if (log.isDebugEnabled()) log.debug("handleDb: server: {} active: {}", server, active);
                    if(server.startsWith("sftp")||server.startsWith("ftp")||server.startsWith("webdav")||server.startsWith("sshj")) { //for distant folders, we don't check existence (for now)
                        if (log.isDebugEnabled()) log.debug("handleDb: ftp server is assumed to exist: {}", server);
                        if (updateServerDb(id, cr, active, 1, now))
                            mServerDbUpdated = true;
                    } else if(!server.startsWith("upnp")) { // SMB goes there even if on cellular only
                        if (hasLocalConnection) { // perform the check of the server existing only if hasLocalConnection
                            Uri serverUri = Uri.parse(server + "/");
                            final FileEditor serverFile;
                            // always use jcifs-ng to check if server exists
                            if ("smb".equalsIgnoreCase(serverUri.getScheme()) || "smbj".equalsIgnoreCase(serverUri.getScheme()))
                                serverFile = new JcifsFileEditor(serverUri);
                            else serverFile = FileEditorFactoryWithUpnp.getFileEditorForUrl(serverUri, null);
                            if (serverFile == null) {
                                log.warn("bad server [{}]", server);
                                continue;
                            }
                            // To check: with mdns it might take long to get IP of server (there is no longer a resolver available)
                            // thus on netstate change it might think share is not available
                            new Thread() {
                                @Override
                                public void run() {
                                    boolean serverExists = false;
                                    for (int i = 0; i < REMOTE_CHECK_RETRY_COUNT; i++) {
                                        if (serverFile.exists()) {
                                            serverExists = true;
                                            break;
                                        }
                                        if (i < REMOTE_CHECK_RETRY_COUNT - 1) { // do not sleep after last attempt
                                            try {
                                                if (log.isDebugEnabled()) log.debug("handleDb: server {} not found, retrying in {}ms ({}/{})", server, REMOTE_CHECK_RETRY_DELAY_MS, (i + 1), REMOTE_CHECK_RETRY_COUNT);
                                                Thread.sleep(REMOTE_CHECK_RETRY_DELAY_MS);
                                            } catch (InterruptedException e) {
                                                log.error("handleDb: sleep interrupted", e);
                                            }
                                        }
                                    }
                                    if (serverExists) {
                                        if (log.isDebugEnabled()) log.debug("handleDb: server exists: {}", server);
                                        if (updateServerDb(id, cr, active, 1, now))
                                            mServerDbUpdated = true;
                                    } else {
                                        String smbDiscoveryInfo = SambaDiscovery.getIpFromShareName(serverUri.getHost());
                                        if (smbDiscoveryInfo == null) {
                                            if (log.isDebugEnabled()) log.debug("handleDb: server does not exist after {} retries: {}", REMOTE_CHECK_RETRY_COUNT, server);
                                            if (updateServerDb(id, cr, active, 0, now))
                                                mServerDbUpdated = true;
                                        } else {
                                            if (log.isDebugEnabled()) log.debug("handleDb: server exists in SambaDiscovery, not jcifs-ng: {}", server);
                                            if (updateServerDb(id, cr, active, 1, now))
                                                mServerDbUpdated = true;
                                        }
                                    }
                                }
                            }.start();
                        } else {
                            if (log.isDebugEnabled()) log.debug("handleDb: no local connectivity setting all smb servers inactive");
                            setLocalServersInactive(context, cr);
                        }
                    } else if(server.startsWith("upnp")) {
                        mUpnpId.put(server,new Pair<>(id, active));
                    }
                }
                if(mUpnpId != null && !mUpnpId.isEmpty() &&hasLocalConnection){
                    if(!mUpnpDiscoveryStarted) {
                        if (log.isDebugEnabled()) log.debug("handleDb: start upnp discovery");
                        //we start upnp discovery but we don't want to add the listener twice
                        UpnpServiceManager.startServiceIfNeeded(context).addListener(this);
                        mUpnpDiscoveryStarted=true;
                    } else {
                        if (log.isDebugEnabled()) log.debug("handleDb: upnp discovery already started");
                    }
                    onDeviceListUpdate(new ArrayList<Device>(UpnpServiceManager.startServiceIfNeeded(context).getDevices()));
                } else {
                    if (log.isDebugEnabled()) log.debug("handleDb: no upnp server listed, no need to launch upnp discovery to get state information");
                }
                c.close();
                if (mServerDbUpdated) {
                    // notify about a change in the db
                    cr.notifyChange(NOTIFY_URI, null);
                }
            } else if (log.isDebugEnabled()) log.debug("handleDb: server query returned NULL");
        } else {
            if (log.isDebugEnabled()) log.debug("handleDb: no connectivity setting all smb servers inactive");
            setAllServersInactive(context, cr);
        }
    }

    protected final static void setAllServersInactive(Context context, ContentResolver contentResolver) {
        if (log.isDebugEnabled()) log.debug("setAllServersInactive");
        // no more servers.
        ContentValues cv = new ContentValues(1);
        cv.put(VideoStore.SmbServer.SmbServerColumns.ACTIVE, "0");
        // update everything with inactive.
        contentResolver.update(SERVER_URI, cv, SELECTION_ALL_NETWORK, null);
        // and tell the world
        contentResolver.notifyChange(NOTIFY_URI, null);
    }

    protected final static void setLocalServersInactive(Context context, ContentResolver contentResolver) {
        if (log.isDebugEnabled()) log.debug("setLocalServersInactive");
        // no more local (smb+upnp) servers.
        ContentValues cv = new ContentValues(1);
        cv.put(VideoStore.SmbServer.SmbServerColumns.ACTIVE, "0");
        // update local remote with inactive.
        contentResolver.update(SERVER_URI, cv, SELECTION_LOCAL_REMOTE, null);
        // and tell the world
        contentResolver.notifyChange(NOTIFY_URI, null);
    }

    protected final static boolean updateServerDb(long id, ContentResolver cr, int oldState,
                                                  int newState, long time) {
        if (log.isDebugEnabled()) log.debug("updateServerDb: id={}, oldState={}, newState={}", id, oldState, newState);
        if (oldState == newState) return false;
        ContentValues cv = new ContentValues();
        cv.put(VideoStore.SmbServer.SmbServerColumns.ACTIVE, String.valueOf(newState));
        if (newState != 0) {
            if (log.isDebugEnabled()) log.debug("updateServerDb: tag as last seen now");
            // update last seen only if it's active now
            cv.put(VideoStore.SmbServer.SmbServerColumns.LAST_SEEN, String.valueOf(time));
        }
        String[] selectionArgs = new String[] {
                String.valueOf(id)
        };
        if (log.isDebugEnabled()) log.debug("DB: update server: {} values:{}", id, cv);
        int result = cr.update(SERVER_URI, cv, SELECTION_ID, selectionArgs);
        return result > 0;
    }

    @Override
    public void onDeviceListUpdate(List<Device> devices) {
        final long now = System.currentTimeMillis() / 1000;
        final ContentResolver cr = getContentResolver();
        for(String deviceName : mUpnpId.keySet()){
            boolean isInList = false;
            for(Device device : devices){
                if(deviceName.startsWith("upnp://" + device.hashCode())){
                    isInList = true;
                    break;
                }
            }
            if (log.isDebugEnabled()) log.debug("UPNP : is in list ?  {} {}", deviceName, String.valueOf(isInList));
            long id = mUpnpId.get(deviceName).first;
            updateServerDb(id, cr, mUpnpId.get(deviceName).second, isInList?1:0, now);
            mUpnpId.put(deviceName, new Pair<>(id,isInList?1:0 ));
        }
        cr.notifyChange(NOTIFY_URI, null);
    }

    public static boolean isNetworkShortcutAvailable(Uri uri, Context context) {
        // server state should be tracked by RemoteStateService correctly and continuously
        String server = uri.getHost();
        String scheme = uri.getScheme();
        int port = uri.getPort();
        String SELECTION;
        if (port != -1) SELECTION = "_data LIKE '" + scheme + "://" + server + ":" + port + "/%'";
        else SELECTION = "_data LIKE '" + scheme + "://" + server + "/%'";
        Cursor c = context.getContentResolver().query(VideoStore.SmbServer.getContentUri(), new String[]{"_id", "_data", "active"}, SELECTION, null, null);
        boolean active = false;
        if (c != null)
            while (c.moveToNext() && !active && isForeground)
                active = (c.getInt(2) == 1);
        return active;
    }

    @Override
    public void onStop(LifecycleOwner owner) {
        // App in background
        if (log.isDebugEnabled()) log.debug("onStop: LifecycleOwner app in background, stopSelf");
        isForeground = false;
        stopSelf();
    }

    @Override
    public void onStart(LifecycleOwner owner) {
        if (log.isDebugEnabled()) log.debug("onStart: LifecycleOwner app in foreground");
        isForeground = true;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }
}