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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.ftp.Session;
import com.archos.filecorelibrary.sftp.SFTPSession;
import com.archos.mediacenter.filecoreextension.upnp2.UpnpServiceManager;
import com.archos.mediacenter.utils.AppState;
import com.archos.mediacenter.utils.ShortcutDbAdapter;
import com.archos.mediaprovider.ArchosMediaIntent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Created by alexandre on 26/06/15.
 */
public class NetworkAutoRefresh extends BroadcastReceiver {
    private static final String TAG = "NetworkAutoRefresh";
    private static String DEBUG_FILE_PATH ="autorefreshdebug";
    private static int ALARM_ID = 33;
    private static PendingIntent mAlarmIntent;
    private static final String ACTION_RESCAN_INDEXED_FOLDERS = "com.archos.mediaprovider.video.NetworkAutoRefresh";
    private static final String ACTION_FORCE_RESCAN_INDEXED_FOLDERS = "com.archos.mediaprovider.video.NetworkAutoRefresh_force";

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
                scheduleNewRescan(context,startingTime,periode,false);
            }
            //start rescan if lastscan + period < current time (when has booted after scheduled time)
        }
        else if(intent.getAction().equals(ACTION_RESCAN_INDEXED_FOLDERS)||
                intent.getAction().equals(ACTION_FORCE_RESCAN_INDEXED_FOLDERS)) {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);

            SimpleDateFormat sdf2 = new SimpleDateFormat("HH:mm:ss.SSS");
            Date dt2 = new Date();
            dt2.setTime(System.currentTimeMillis() - pref.getLong(AUTO_RESCAN_LAST_SCAN, 0));
            String S2 = sdf2.format(dt2);
            /*
                do not scan if auto scan and already scan lately (for example on restart of device) or if already scanning
             */
            if(((pref.getInt(AUTO_RESCAN_PERIOD,0)<=0)
                    &&!intent.getAction().equals(ACTION_FORCE_RESCAN_INDEXED_FOLDERS))
                    || com.archos.mediaprovider.video.NetworkScannerServiceVideo.isScannerAlive()
                    ) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss.SSS");
                    Date dt = new Date();
                    String S = sdf.format(dt);
                    FileWriter fw =getDebugFileWriter(context);
                    fw.append(S + " Skipping rescan : period = " + pref.getInt(AUTO_RESCAN_PERIOD, 0)+" is scanning ? "+String.valueOf(com.archos.mediaprovider.video.NetworkScannerReceiver.isScannerWorking())+"\n");


                    fw.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return;
            }
            pref.edit().putLong(AUTO_RESCAN_LAST_SCAN, System.currentTimeMillis()).commit();
            Log.d(TAG, "received rescan intent");
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss.SSS");
            Date dt = new Date();
            String S = sdf.format(dt); // formats to 09/23/2009 13:53:28.238
            try {
                FileWriter fw =getDebugFileWriter(context);
                fw.append(S+" scanning \n");
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

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
            if(ArchosUtils.isLocalNetworkConnected(context)) {
                PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(AUTO_RESCAN_ERROR, 0).commit();//reset error
                for (Uri uri : toUpdate) {
                    Log.d(TAG, "scanning "+uri);
                    if("upnp".equals(uri.getScheme())){ //start upnp service
                        UpnpServiceManager.startServiceIfNeeded(context);
                    }
                    if("ftp".equalsIgnoreCase(uri.getScheme())||"ftps".equals(uri.getScheme()))
                        Session.getInstance().removeFTPClient(uri);
                    if("sftp".equalsIgnoreCase(uri.getScheme()))
                        SFTPSession.getInstance().removeSession(uri);
                    Intent refreshIntent = new Intent(ArchosMediaIntent.ACTION_VIDEO_SCANNER_SCAN_FILE, uri);
                    refreshIntent.putExtra(NetworkScannerServiceVideo.RECORD_SCAN_LOG_EXTRA, true);
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
            Log.d(TAG, "received rescan intent end");
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
    public static FileWriter getDebugFileWriter(Context context) throws IOException {

        String path =context.getExternalFilesDir("misc")+"/"+DEBUG_FILE_PATH ;
        File file= new File (path);
        FileWriter fw;
        if (file.exists())
        {
            fw = new FileWriter(path,true);//if file exists append to file. Works fine.
        }
        else
        {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();

            } catch (IOException e) {
                e.printStackTrace();
            }
            fw = new FileWriter(path);
        }
       return fw;
    }
    /**
     * Schedule rescan and cancel last one
     * @param context
     * @param startingTimeOfDay in millis (1 am = 1*60*1000)
     * @param periode in millis
     * @param setPreference save
     */
    public static void scheduleNewRescan(Context context, int startingTimeOfDay, int periode, boolean setPreference){
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(context.ALARM_SERVICE);
        try {
            FileWriter fw = getDebugFileWriter(context);
            fw.append("\n");
            fw.append("\n");
            fw.append("Resetting alarm: \n");
            fw.append("Starting at: "+startingTimeOfDay/1000/60/60+"h \n");
            fw.append("Period:"+periode/1000/60/60+"h \n");
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(startingTimeOfDay!=-1&&periode>0) {
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY,0);
            c.set(Calendar.MINUTE,0);
            c.set(Calendar.SECOND,0);
            c.set(Calendar.MILLISECOND,0);
            long nextStart = c.getTimeInMillis()+startingTimeOfDay;
            while(nextStart < System.currentTimeMillis())
                nextStart+=periode;
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS");
            Date dt = new Date(nextStart);
            String S = sdf.format(dt);
            try {
                FileWriter fw = getDebugFileWriter(context);
                fw.append("next launch at: " + S+"\n");
                        fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Log.d(TAG, "periode "+periode/1000/60/60);
            Log.d(TAG, "launching at "+S);
            Log.d(TAG, "launching in " + (nextStart - System.currentTimeMillis()) / 1000 / 60 + " minutes");
            Intent intent = new Intent(context, NetworkAutoRefresh.class);
            intent.setAction(ACTION_RESCAN_INDEXED_FOLDERS);
            mAlarmIntent = PendingIntent.getBroadcast(context, ALARM_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            alarmMgr.setInexactRepeating(AlarmManager.RTC_WAKEUP,
                    nextStart, periode , mAlarmIntent);
            Log.d(TAG, "setting alarm");

        }
        else{
            Intent intent = new Intent(context, NetworkAutoRefresh.class);
            intent.setAction(ACTION_RESCAN_INDEXED_FOLDERS);
            mAlarmIntent = PendingIntent.getBroadcast(context, ALARM_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            alarmMgr.cancel(mAlarmIntent);
        }
        if(setPreference){
            Log.d(TAG, "preference");
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(AUTO_RESCAN_STARTING_TIME_PREF,startingTimeOfDay).commit();
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(AUTO_RESCAN_PERIOD, periode).commit();

        }
        try {
            FileWriter fw = getDebugFileWriter(context);
            fw.append("\n");
            fw.append("\n");

            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static int getLastError(Context context){
        return  PreferenceManager.getDefaultSharedPreferences(context).getInt(AUTO_RESCAN_ERROR, 0);
    }
    public static boolean autoRescanAtStart(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(AUTO_RESCAN_ON_APP_RESTART,false);
    }
    public static void setAutoRescanAtStart(Context context, boolean autoRescanAtStart) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(AUTO_RESCAN_ON_APP_RESTART,autoRescanAtStart).commit();
    }

    public static int getRescanPeriod(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getInt(AUTO_RESCAN_PERIOD, 0);
    }
}
