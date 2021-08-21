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

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Pair;

import com.archos.filecorelibrary.FileEditor;
import com.archos.mediacenter.filecoreextension.upnp2.FileEditorFactoryWithUpnp;
import com.archos.mediacenter.filecoreextension.upnp2.UpnpServiceManager;
import com.archos.mediacenter.utils.AppState;
import com.archos.mediaprovider.ArchosMediaCommon;
import com.archos.environment.NetworkState;
import com.archos.mediaprovider.video.VideoStore.MediaColumns;

import org.fourthline.cling.model.meta.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** handles visibility updates of smb://server/share type servers in the database */
public class RemoteStateService extends IntentService implements UpnpServiceManager.Listener {
    private static final Logger log = LoggerFactory.getLogger(RemoteStateService.class);

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
    private static final String SELECTION_LOCAL_REMOTE = MediaColumns.DATA + " LIKE 'smb://%' OR "+MediaColumns.DATA + " LIKE 'upnp://%'";
    private static final String SELECTION_DISTANT_REMOTE = MediaColumns.DATA + " LIKE 'ftps://%' OR "+MediaColumns.DATA  + " LIKE 'ftp://%' OR "+MediaColumns.DATA + " LIKE 'sftp://%'";
    private static final String SELECTION_ALL_NETWORK = SELECTION_LOCAL_REMOTE+" OR "+SELECTION_DISTANT_REMOTE;
    public static final String ACTION_CHECK_SMB = "archos.intent.action.CHECK_SMB";
    private ConcurrentHashMap<String, Pair<Long, Integer>> mUpnpId; //store name, id and active state
    private boolean mUpnpDiscoveryStarted;
    private boolean mServerDbUpdated;

    public RemoteStateService() {
        super(RemoteStateService.class.getSimpleName());
        log.debug("SmbStateService CTOR");
        setIntentRedelivery(true);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        log.debug("onHandleIntent " + intent);
        // prevent to be executed if app is in background
        if (!AppState.isForeGround()) {
            log.warn("onHandleIntent: app is in background exiting!");
            return;
        }
        if (ACTION_CHECK_SMB.equals(intent.getAction())) {
            log.debug("onHandleIntent: app is not in background, updating networkstate");
            NetworkState state = NetworkState.instance(this);
            state.updateFrom();
            handleDb(this, state.isConnected(), state.hasLocalConnection());
        }
    }

    /** use to issue a check of the smb:// state */
    public static void start(Context context) {
        Intent intent = new Intent(context, RemoteStateService.class);
        intent.setAction(ACTION_CHECK_SMB);
        context.startService(intent);
    }

    protected void handleDb(Context context, boolean hasConnection, boolean hasLocalConnection) {
        if(mUpnpId==null) mUpnpId =new ConcurrentHashMap<>();
        mUpnpId.clear();
        final ContentResolver cr = context.getContentResolver();
        log.debug("handleDb: hasConnection=" + hasConnection + ", hasLocalConnection=" + hasLocalConnection);
        if (hasConnection) {
            //Lmhosts.reset();
            final long now = System.currentTimeMillis() / 1000;
            // list all servers in the db
            Cursor c = cr.query(SERVER_URI, PROJECTION_SERVERS, SELECTION_ALL_NETWORK, null, null);
            if (c != null) {
                log.debug("found " + c.getCount() + " servers");
                mServerDbUpdated = false;
                while (c.moveToNext()) {
                    final long id = c.getLong(COLUMN_ID);
                    final String server = c.getString(COLUMN_DATA);
                    final int active = c.getInt(COLUMN_ACTIVE);
                    if(server.startsWith("sftp")||server.startsWith("ftp")) { //for distant folders, we don't check existence (for now)
                        log.debug("ftp server is assumed to exist: " + server);
                        if (updateServerDb(id, cr, active, 1, now))
                            mServerDbUpdated = true;
                    } else if(!server.startsWith("upnp")) { // SMB goes there even if on cellular only
                        if (hasLocalConnection) { // perform the check of the server existing only if hasLocalConnection
                            final FileEditor serverFile = FileEditorFactoryWithUpnp.getFileEditorForUrl(Uri.parse(server + "/"), null);
                            if (serverFile == null) {
                                log.warn("bad server [" + server + "]");
                                continue;
                            }
                            // TODO MARC this checks visibility of smb shares.
                            // To check: with mdns it might take long to get IP of server (there is no longer a resolver available)
                            // thus on netstate change it might think share is not available
                            new Thread() {
                                @Override
                                public void run() {
                                    if (serverFile.exists()) {
                                        log.debug("server exists: " + server);
                                        if (updateServerDb(id, cr, active, 1, now))
                                            mServerDbUpdated = true;
                                    } else {
                                        log.debug("server does not exist: " + server);
                                        if (updateServerDb(id, cr, active, 0, now))
                                            mServerDbUpdated = true;
                                    }
                                }
                            }.start();
                        } else {
                            log.debug("no local connectivity setting all smb servers inactive");
                            setLocalServersInactive(context, cr);
                        }
                    } else if(server.startsWith("upnp")) {
                        mUpnpId.put(server,new Pair<>(id, active));
                    }
                }
                if(mUpnpId.size()>0&&hasLocalConnection){
                    if(!mUpnpDiscoveryStarted) {
                        //we start upnp discovery but we don't want to add the listener twice
                        UpnpServiceManager.startServiceIfNeeded(context).addListener(this);
                        mUpnpDiscoveryStarted=true;
                    }
                    onDeviceListUpdate(new ArrayList<Device>(UpnpServiceManager.startServiceIfNeeded(context).getDevices()));
                }
                c.close();
                if (mServerDbUpdated) {
                    // notify about a change in the db
                    cr.notifyChange(NOTIFY_URI, null);
                }
            } else log.debug("server query returned NULL");
        } else {
            log.debug("no connectivity setting all smb servers inactive");
            setAllServersInactive(context, cr);
        }
    }

    protected final static void setAllServersInactive(Context context, ContentResolver contentResolver) {
        // no more servers.
        ContentValues cv = new ContentValues(1);
        cv.put(VideoStore.SmbServer.SmbServerColumns.ACTIVE, "0");
        // update everything with inactive.
        contentResolver.update(SERVER_URI, cv, SELECTION_ALL_NETWORK, null);
        // and tell the world
        contentResolver.notifyChange(NOTIFY_URI, null);
    }

    protected final static void setLocalServersInactive(Context context, ContentResolver contentResolver) {
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
        log.debug("updateServerDb: id=" + id + ", oldState=" + oldState + ", newState=" + newState);
        if (oldState == newState) return false;
        ContentValues cv = new ContentValues();
        cv.put(VideoStore.SmbServer.SmbServerColumns.ACTIVE, String.valueOf(newState));
        if (newState != 0) {
            log.debug("updateServerDb: tag as last seen now");
            // update last seen only if it's active now
            cv.put(VideoStore.SmbServer.SmbServerColumns.LAST_SEEN, String.valueOf(time));
        }
        String[] selectionArgs = new String[] {
            String.valueOf(id)
        };
        log.debug("DB: update server: " + id + " values:" + cv);
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
            log.debug("UPNP : is in list ?  "+deviceName+ " "+String.valueOf(isInList));
            long id = mUpnpId.get(deviceName).first;
            updateServerDb(id, cr, mUpnpId.get(deviceName).second, isInList?1:0, now);
            mUpnpId.put(deviceName, new Pair<>(id,isInList?1:0 ));
       }
        cr.notifyChange(NOTIFY_URI, null);
    }
}
