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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import androidx.preference.PreferenceManager;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.ftp.Session;
import com.archos.filecorelibrary.sftp.SFTPSession;
import com.archos.mediacenter.filecoreextension.upnp2.UpnpServiceManager;
import com.archos.mediacenter.utils.AppState;
import com.archos.mediacenter.utils.ShortcutDbAdapter;
import com.archos.mediaprovider.ArchosMediaIntent;
import com.archos.environment.NetworkState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by alexandre on 26/06/15.
 */
public class NetworkAutoRefresh extends BroadcastReceiver {

    private static final Logger log = LoggerFactory.getLogger(NetworkAutoRefresh.class);

    public static final String ACTION_RESCAN_INDEXED_FOLDERS = "com.archos.mediaprovider.video.NetworkAutoRefresh";
    public static final String ACTION_FORCE_RESCAN_INDEXED_FOLDERS = "com.archos.mediaprovider.video.NetworkAutoRefresh_force";

    private static final String AUTO_RESCAN_ON_APP_RESTART = "auto_rescan_on_app_restart";

    public static final String AUTO_RESCAN_STARTING_TIME_PREF = "auto_rescan_starting_time";
    public static final String AUTO_RESCAN_PERIOD = "auto_rescan_period";
    public static final String AUTO_RESCAN_LAST_SCAN = "auto_rescan_last_scan";
    public static final String AUTO_RESCAN_ERROR = "auto_rescan_error";
    public static final int AUTO_RESCAN_ERROR_UNABLE_TO_REACH_HOST = -1;
    public static final int AUTO_RESCAN_ERROR_NO_WIFI = -2;

    @Override
    public void onReceive(Context context, Intent intent) {

        if(intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            //reset alarm on boot
            int startingTime = PreferenceManager.getDefaultSharedPreferences(context).getInt(AUTO_RESCAN_STARTING_TIME_PREF, -1);
            int periode = PreferenceManager.getDefaultSharedPreferences(context).getInt(AUTO_RESCAN_PERIOD,-1);
            if(startingTime!=-1&&periode>0){
                NetworkScannerUtil.scheduleNewRescan(context,startingTime,periode,false);
            }
            //start rescan if lastscan + period < current time (when has booted after scheduled time)
        }
        else if(intent.getAction().equals(ACTION_RESCAN_INDEXED_FOLDERS)||
                intent.getAction().equals(ACTION_FORCE_RESCAN_INDEXED_FOLDERS)) {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
            /*
                do not scan if auto scan and already scan lately (for example on restart of device) or if already scanning
             */
            if(((pref.getInt(AUTO_RESCAN_PERIOD,0)<=0)
                    &&!intent.getAction().equals(ACTION_FORCE_RESCAN_INDEXED_FOLDERS))
                    || com.archos.mediaprovider.video.NetworkScannerServiceVideo.isScannerAlive()
                    ) {
                SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss.SSS");
                Date dt = new Date();
                String S = sdf.format(dt);
                log.debug("onReceive: skipping rescan : " + S + " period = " + pref.getInt(AUTO_RESCAN_PERIOD, 0)+" is scanning ? "+String.valueOf(com.archos.mediaprovider.video.NetworkScannerReceiver.isScannerWorking()));
                return;
            }
            pref.edit().putLong(AUTO_RESCAN_LAST_SCAN, System.currentTimeMillis()).commit();
            log.debug("onReceive: received rescan intent");
            //updating
            Cursor cursor = ShortcutDbAdapter.VIDEO.queryAllShortcuts(context);
            List<Uri> toUpdate = new ArrayList<>();
            if (cursor.getCount() > 0) {
                int pathKey = cursor.getColumnIndex(ShortcutDbAdapter.KEY_PATH);
                cursor.moveToFirst();
                do {
                    Uri uri = Uri.parse(cursor.getString(pathKey));
                    toUpdate.add(uri);
                }
                while (cursor.moveToNext());
            }
            cursor.close();
            ShortcutDbAdapter.VIDEO.close();
            if(NetworkState.isLocalNetworkConnected(context)) {
                PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(AUTO_RESCAN_ERROR, 0).commit();//reset error
                for (Uri uri : toUpdate) {
                    log.debug("onReceive: scanning "+uri);
                    if("upnp".equals(uri.getScheme())){ //start upnp service
                        UpnpServiceManager.startServiceIfNeeded(context);
                    }
                    if("ftp".equalsIgnoreCase(uri.getScheme())||"ftps".equals(uri.getScheme()))
                        Session.getInstance().removeFTPClient(uri);
                    if("sftp".equalsIgnoreCase(uri.getScheme()))
                        SFTPSession.getInstance().removeSession(uri);
                    Intent refreshIntent = new Intent(ArchosMediaIntent.ACTION_VIDEO_SCANNER_SCAN_FILE, uri);
                    refreshIntent.putExtra(NetworkScannerServiceVideo.RECORD_ON_FAIL_PREFERENCE, AUTO_RESCAN_ERROR);
                    refreshIntent.putExtra(NetworkScannerServiceVideo.RECORD_END_OF_SCAN_PREFERENCE, AUTO_RESCAN_LAST_SCAN);
                    refreshIntent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                    context.sendBroadcast(refreshIntent);
                }
            }
            else{
                PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(AUTO_RESCAN_ERROR, AUTO_RESCAN_ERROR_NO_WIFI).commit();//reset error
                NetworkScannerServiceVideo.notifyListeners();
            }
            log.debug("onReceive: received rescan intent end");
        }
    }
    private static final AppState.OnForeGroundListener sForeGroundListener = new AppState.OnForeGroundListener() {
        @Override
        public void onForeGroundState(Context applicationContext, boolean foreground) {
            if (foreground&&autoRescanAtStart(applicationContext))
                forceRescan(applicationContext);
        }
    };
    public static void init() {
        AppState.addOnForeGroundListener(sForeGroundListener);
    }

    public static void forceRescan(Context context){
        Intent intent = new Intent(context, NetworkAutoRefresh.class);
        intent.setAction(ACTION_FORCE_RESCAN_INDEXED_FOLDERS);
        intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
        context.sendBroadcast(intent);
    }

    public static int getLastError(Context context){
        return  PreferenceManager.getDefaultSharedPreferences(context).getInt(AUTO_RESCAN_ERROR, 0);
    }
    public static boolean autoRescanAtStart(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(AUTO_RESCAN_ON_APP_RESTART,false);
    }
    public static void setAutoRescanAtStart(Context context, boolean autoRescanAtStart) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(AUTO_RESCAN_ON_APP_RESTART,autoRescanAtStart).apply();
    }

    public static int getRescanPeriod(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getInt(AUTO_RESCAN_PERIOD, 0);
    }
}
