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

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.preference.PreferenceManager;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.FileUtils;
import com.archos.filecorelibrary.ftp.Session;
import com.archos.filecorelibrary.sftp.SFTPSession;
import com.archos.mediacenter.filecoreextension.upnp2.UpnpServiceManager;
import com.archos.mediacenter.utils.ShortcutDbAdapter;
import com.archos.mediaprovider.ArchosMediaIntent;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediaprovider.video.VideoStore.MediaColumns;
import com.archos.mediascraper.AutoScrapeService;
import com.archos.environment.NetworkState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by alexandre on 26/06/15.
 */
public class NetworkAutoRefresh extends BroadcastReceiver implements DefaultLifecycleObserver {

    private static final Logger log = LoggerFactory.getLogger(NetworkAutoRefresh.class);

    private static volatile boolean isForeground = true;
    // Prevents two background threads from executing doRescan() concurrently.
    // Note: cleared as soon as delayed scan broadcasts are queued, not when the scans complete,
    // so it does not protect against overlapping scan batches — that is pre-existing behaviour.
    private static final AtomicBoolean sRescanInProgress = new AtomicBoolean(false);
    private static Application mApplication;

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
            // goAsync keeps the BroadcastReceiver context alive until finish() is called,
            // allowing the DB queries inside handleRescan to run on a background thread.
            final PendingResult pendingResult = goAsync();
            new Thread(() -> {
                try {
                    handleRescan(context, intent);
                } finally {
                    pendingResult.finish();
                }
            }, "NetworkAutoRefresh-bg").start();
        }
    }

    private void handleRescan(Context context, Intent intent) {
        if (!sRescanInProgress.compareAndSet(false, true)) {
            if (log.isDebugEnabled()) log.debug("handleRescan: rescan already in progress, skipping duplicate intent");
            return;
        }
        try {
            doRescan(context, intent);
        } finally {
            sRescanInProgress.set(false);
        }
    }

    private void doRescan(Context context, Intent intent) {
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
            if (log.isDebugEnabled()) log.debug("onReceive: skipping rescan : {} period = {} is scanning ? {}", S, pref.getInt(AUTO_RESCAN_PERIOD, 0), String.valueOf(com.archos.mediaprovider.video.NetworkScannerReceiver.isScannerWorking()));
            return;
        }
        pref.edit().putLong(AUTO_RESCAN_LAST_SCAN, System.currentTimeMillis()).commit();
        if (log.isDebugEnabled()) log.debug("onReceive: received rescan intent");
        // Use getRescanUris() which opens a local DatabaseHelper, copies results, and closes:
        // avoids touching the singleton ShortcutDbAdapter.VIDEO mDb from a background thread.
        List<Uri> toUpdate = ShortcutDbAdapter.VIDEO.getRescanUris(context);
        for (Uri uri : toUpdate) {
            if (log.isDebugEnabled()) log.debug("onReceive: add to scan list {}", uri);
        }
        if(NetworkState.isLocalNetworkConnected(context)) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(AUTO_RESCAN_ERROR, 0).commit();//reset error
            // Reset network scan counter at the start of a new batch to prevent orphaned counts
            AutoScrapeService.resetNetworkScanCount();
            boolean triggeredScan = false;
            int scanCount = 0;
            Handler handler = new Handler(Looper.getMainLooper());
            for (Uri uri : toUpdate) {
                if (log.isDebugEnabled()) log.debug("onReceive: scanning {}", uri);
                if (shouldSkipScanForInactiveServer(context, uri)) {
                    if (log.isDebugEnabled()) log.debug("onReceive: skip scan for inactive server {}", uri);
                    continue;
                }
                if("upnp".equals(uri.getScheme())){ //start upnp service
                    UpnpServiceManager.startServiceIfNeeded(context);
                }
                if("ftp".equalsIgnoreCase(uri.getScheme())||"ftps".equals(uri.getScheme()))
                    Session.getInstance().removeFTPClient(uri);
                if("sftp".equalsIgnoreCase(uri.getScheme()))
                    SFTPSession.getInstance().removeSession(uri);
                final Uri scanUri = uri;
                final long delayMs = 100L + (scanCount * 2000L);
                // Use global application context in the lambda: it fires after pendingResult.finish()
                // so the receiver's context may already be invalidated.
                final Context appContext = ArchosUtils.getGlobalContext();
                handler.postDelayed(() -> {
                    Intent refreshIntent = new Intent(ArchosMediaIntent.ACTION_VIDEO_SCANNER_SCAN_FILE, scanUri);
                    refreshIntent.putExtra(NetworkScannerServiceVideo.RECORD_ON_FAIL_PREFERENCE, AUTO_RESCAN_ERROR);
                    refreshIntent.putExtra(NetworkScannerServiceVideo.RECORD_END_OF_SCAN_PREFERENCE, AUTO_RESCAN_LAST_SCAN);
                    refreshIntent.setPackage(appContext.getPackageName());
                    appContext.sendBroadcast(refreshIntent);
                }, delayMs);
                triggeredScan = true;
                scanCount++;
                // Increment the network scan counter for each folder
                AutoScrapeService.incrementNetworkScanCount();
                if (log.isDebugEnabled()) log.debug("onReceive: queued scan for {} with delay {}ms", uri, delayMs);
            }

            // Start AutoScrapeService after network scanning to scrape newly found videos
            if (triggeredScan && AutoScrapeService.isEnable(context)) {
                if (log.isDebugEnabled()) log.debug("onReceive: starting AutoScrapeService after network scan, total folders: {}", scanCount);
                try {
                    AutoScrapeService.startServiceAfterNetworkScan(context);
                } catch (Exception e) {
                    // Catch any exceptions that might bubble up from startServiceAfterNetworkScan
                    // to prevent BroadcastReceiver from crashing and causing RuntimeException to propagate
                    log.error("onReceive: Failed to start AutoScrapeService after network scan", e);
                }
            }
        } else {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(AUTO_RESCAN_ERROR, AUTO_RESCAN_ERROR_NO_WIFI).commit();//reset error
            NetworkScannerServiceVideo.notifyListeners();
        }
        if (log.isDebugEnabled()) log.debug("onReceive: received rescan intent end");
    }

    private static boolean shouldSkipScanForInactiveServer(Context context, Uri uri) {
        if (uri == null) {
            return false;
        }
        if (!FileUtils.isNetworkShare(uri)) {
            return false;
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            return false;
        }
        int port = uri.getPort();
        StringBuilder whereBuilder = new StringBuilder();
        whereBuilder.append(MediaColumns.DATA).append(" LIKE '").append(scheme).append("://").append(host);
        if (port != -1) {
            whereBuilder.append(":").append(port);
        }
        whereBuilder.append("/%'");
        String selection = whereBuilder.toString();

        ContentResolver cr = context.getContentResolver();
        Cursor c = null;
        boolean hasRow = false;
        boolean active = false;
        try {
            c = cr.query(VideoStore.SmbServer.getContentUri(), new String[]{"_id", VideoStore.SmbServer.SmbServerColumns.ACTIVE}, selection, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    hasRow = true;
                    if (c.getInt(1) == 1) {
                        active = true;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.error("shouldSkipScanForInactiveServer: query failed for {}", uri, e);
            return false;
        } finally {
            if (c != null) c.close();
        }
        // If we have no row we cannot decide, so allow scan.
        return hasRow && !active;
    }

    public static void init(Application application) {
        mApplication = application;
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new NetworkAutoRefresh());
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

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        if (log.isDebugEnabled()) log.debug("onStop: lifecycle foreground");
        isForeground = true;
        if (autoRescanAtStart(mApplication)) {
            forceRescan(mApplication);
        }
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        isForeground = false;
        if (log.isDebugEnabled()) log.debug("onStop: lifecycle background");
    }
}
