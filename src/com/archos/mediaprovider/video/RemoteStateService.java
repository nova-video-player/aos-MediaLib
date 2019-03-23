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
import android.util.Log;
import android.util.Pair;

import com.archos.filecorelibrary.FileEditor;
import com.archos.mediacenter.filecoreextension.upnp2.FileEditorFactoryWithUpnp;
import com.archos.mediacenter.filecoreextension.upnp2.UpnpServiceManager;
import com.archos.mediaprovider.ArchosMediaCommon;
import com.archos.mediaprovider.NetworkState;
import com.archos.mediaprovider.video.VideoStore.MediaColumns;

import org.fourthline.cling.model.meta.Device;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import jcifs2.netbios.Lmhosts;

/** handles visibility updates of smb://server/share type servers in the database */
public class RemoteStateService extends IntentService implements UpnpServiceManager.Listener {
    private static final String TAG = ArchosMediaCommon.TAG_PREFIX + RemoteStateService.class.getSimpleName();
    private static final boolean DBG = false;

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

    public RemoteStateService() {
        super(RemoteStateService.class.getSimpleName());
        if (DBG) Log.d(TAG, "SmbStateService CTOR");
        setIntentRedelivery(true);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (DBG) Log.d(TAG, "onHandleIntent " + intent);
        if (ACTION_CHECK_SMB.equals(intent.getAction())) {
            NetworkState state = NetworkState.instance(this);
            state.updateFrom(this);
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
        if(mUpnpId==null)
            mUpnpId =new ConcurrentHashMap<>();
        mUpnpId.clear();
        final ContentResolver cr = context.getContentResolver();
        Log.d(TAG, "hasConnection "+String.valueOf(hasConnection));
        if (hasConnection) {
            Lmhosts.reset();
            final long now = System.currentTimeMillis() / 1000;
            // list all servers in the db
            Cursor c = cr.query(SERVER_URI, PROJECTION_SERVERS, SELECTION_ALL_NETWORK, null, null);
            if (c != null) {
                if (DBG) Log.d(TAG, "found " + c.getCount() + " servers");
                while (c.moveToNext()) {
                    final long id = c.getLong(COLUMN_ID);
                    final String server = c.getString(COLUMN_DATA);
                    final int active = c.getInt(COLUMN_ACTIVE);
                    if(!server.startsWith("upnp")) {
                        final FileEditor serverFile = FileEditorFactoryWithUpnp.getFileEditorForUrl(Uri.parse(server+"/"), null);
                        if (serverFile == null) {
                            Log.d(TAG, "bad server [" + server + "]");
                            continue;
                        }
                        new Thread() {
                            @Override
                            public void run() {
                                if (serverFile.exists()) {
                                    if (DBG)
                                        Log.d(TAG, "server exists: " + server);
                                    updateServerDb(id, cr, active, 1, now);
                                } else {
                                    if (DBG)
                                        Log.d(TAG, "server does not exist: " + server);
                                    updateServerDb(id, cr, active, 0, now);
                                }
                            }
                        }.start();
                    }
                    else if(server.startsWith("upnp")){
                        mUpnpId.put(server,new Pair<>(id, active));
                    }
                    else
                        updateServerDb(id, cr, active, 1, now); //for distant folders, we don't check existence (for now)
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
                // notify about a change in the db
                cr.notifyChange(NOTIFY_URI, null);
            } else if (DBG) {
                Log.d(TAG, "server query returned NULL");
            }
        } else{
                if (DBG) Log.d(TAG, "setting all smb servers inactive");
                // no more smb servers.
                ContentValues cv = new ContentValues(1);
                cv.put(VideoStore.SmbServer.SmbServerColumns.ACTIVE, "0");
                // update everything with inactive.
                cr.update(SERVER_URI, cv, SELECTION_ALL_NETWORK, null);
                // and tell the world

                cr.notifyChange(NOTIFY_URI, null);
        }
    }

    protected final static void updateServerDb(long id, ContentResolver cr, int oldState,
            int newState, long time) {
        if (oldState == newState) return;
        ContentValues cv = new ContentValues();
        cv.put(VideoStore.SmbServer.SmbServerColumns.ACTIVE, String.valueOf(newState));
        if (newState != 0) {
            // update last seen only if it's active now
            cv.put(VideoStore.SmbServer.SmbServerColumns.LAST_SEEN, String.valueOf(time));
        }
        String[] selectionArgs = new String[] {
            String.valueOf(id)
        };
        if (DBG) Log.d(TAG, "DB: update server: " + id + " values:" + cv);
        cr.update(SERVER_URI, cv, SELECTION_ID, selectionArgs);
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
            if (DBG) Log.d(TAG, "UPNP : is in list ?  "+deviceName+ " "+String.valueOf(isInList));
            long id = mUpnpId.get(deviceName).first;
            updateServerDb(id, cr, mUpnpId.get(deviceName).second, isInList?1:0, now);
            mUpnpId.put(deviceName, new Pair<>(id,isInList?1:0 ));
       }
        cr.notifyChange(NOTIFY_URI, null);
    }
}
