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


package com.archos.mediascraper;

import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.NetworkOnMainThreadException;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import android.util.Log;

import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.MetaFile2Factory;
import com.archos.mediacenter.utils.AppState;
import com.archos.medialib.R;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediaprovider.video.VideoStore.MediaColumns;
import com.archos.mediaprovider.video.VideoStore.Video.VideoColumns;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class NfoExportService extends IntentService {
    private static final String TAG = "NfoExportService";
    private final static boolean DBG = false;

    private static final String INTENT_EXPORT_FILE = "archos.mediascraper.intent.action.EXPORT_FILE";
    private static final String INTENT_EXPORT_ALL = "archos.mediascraper.intent.action.EXPORT_ALL";

    private static final String EXPORT_ALL_KEY = "all://";
    private static final Intent VOID_INTENT = new Intent("void");
    private static final ConcurrentHashMap<String, String> sScheduledTasks =
            new ConcurrentHashMap<String, String>();

    private static final int NOTIFICATION_ID = 8;
    private NotificationManager nm;
    private NotificationCompat.Builder nb;
    private static final String notifChannelId = "NfoExportService_id";
    private static final String notifChannelName = "NfoExportService";
    private static final String notifChannelDescr = "NfoExportService";


    /**
     * simple guard against multiple tasks of the same directory
     * @return true if this uri or an export all task is not scheduled already
     **/
    private static boolean addDirTask(Uri data) {
        if (data != null) {
            // skip task if we are already exporting everything
            if (sScheduledTasks.containsKey(EXPORT_ALL_KEY))
                return false;
            // test is not atomic here, but we don't care.

            // skip if exact matching task is present
            return sScheduledTasks.putIfAbsent(data.toString(), "") == null;
        }
        return false;
    }

    private static void removeDirTask(Uri data) {
        if (data != null) {
            sScheduledTasks.remove(data.toString());
        }
    }

    /**
     * simple guard against multiple "export all" tasks
     * @return true if no such task is already scheduled
     **/
    private static boolean addAllTask() {
        return sScheduledTasks.putIfAbsent(EXPORT_ALL_KEY, "") == null;
    }
    private static void removeAllTask() {
        sScheduledTasks.remove(EXPORT_ALL_KEY);
    }


    public static void exportDirectory(Context context, Uri directory) {
        Intent serviceIntent = new Intent(context, NfoExportService.class);
        serviceIntent.setAction(INTENT_EXPORT_FILE);
        serviceIntent.setData(directory);
        if (AppState.isForeGround()) ContextCompat.startForegroundService(context, serviceIntent);
    }
    public static void exportAll(Context context) {
        Intent serviceIntent = new Intent(context, NfoExportService.class);
        serviceIntent.setAction(INTENT_EXPORT_ALL);
        if (AppState.isForeGround()) ContextCompat.startForegroundService(context, serviceIntent);
    }

    public NfoExportService() {
        super(TAG);
        if (DBG) Log.d(TAG, "NfoExportService");
        setIntentRedelivery(true);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (DBG) Log.d(TAG, "onCreate");

        // need to do that early to avoid ANR on Android 26+
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel nc = new NotificationChannel(notifChannelId, notifChannelName,
                    nm.IMPORTANCE_LOW);
            nc.setDescription(notifChannelDescr);
            if (nm != null)
                nm.createNotificationChannel(nc);
        }
        nb = new NotificationCompat.Builder(this, notifChannelId)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(getString(R.string.nfo_export_exporting))
                .setContentText("")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setTicker(null).setOnlyAlertOnce(true).setOngoing(true).setAutoCancel(true);
        startForeground(NOTIFICATION_ID, nb.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        Uri data = intent != null ? intent.getData() : null;
        startForeground(NOTIFICATION_ID, nb.build());
        boolean processIntent = false;
        if (INTENT_EXPORT_FILE.equals(action)) {
            if (addDirTask(data))
                processIntent = true;
        } else if (INTENT_EXPORT_ALL.equals(action)) {
            if (addAllTask())
                processIntent = true;
        }
        if (processIntent) {
            return super.onStartCommand(intent, flags, startId);
        }

        // super will pass an intent to onHandleIntent that is not handled
        return super.onStartCommand(VOID_INTENT, flags, startId);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        Uri data = intent != null ? intent.getData() : null;

        if (INTENT_EXPORT_FILE.equals(action)) {
            exportFile(data);
        } else if (INTENT_EXPORT_ALL.equals(action)) {
            exportAll();
        }
    }

    private void exportAll() {
        if (DBG) Log.d(TAG, "exportAll");
        nb.setContentText(getString(R.string.nfo_export_exporting_all));
        nm.notify(NOTIFICATION_ID, nb.build());
        handleCursor(getAllCursor());
        removeAllTask();
        stopForeground(true);
    }

    private void exportFile(Uri data) {
        if (DBG) Log.d(TAG, "exportFile: " + data.getPath());
        MetaFile2 file = null;
        try {
            file = MetaFile2Factory.getMetaFileForUrl(data);
        } catch (Exception e) {
            if(e instanceof NetworkOnMainThreadException)
                throw new NetworkOnMainThreadException();
            else
                e.printStackTrace();
        }
        if (file != null && file.isDirectory()) {
            nb.setContentText(data.toString());
            nm.notify(NOTIFICATION_ID, nb.build());
            handleCursor(getInDirectoryCursor(data));
        }
        removeDirTask(data);
        stopForeground(true);
    }

    private void handleCursor(Cursor cursor) {
        if (cursor != null) {
            NfoWriter.ExportContext exportContext = new NfoWriter.ExportContext();
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                int type = cursor.getInt(1);
                BaseTags tags = null;
                switch (type) {
                    case BaseTags.MOVIE:
                        tags = TagsFactory.buildMovieTags(this, id);
                        break;
                    case BaseTags.TV_SHOW:
                        tags = TagsFactory.buildEpisodeTags(this, id);
                        break;
                    default:
                        Log.w(TAG, "can't export file of type: " + type);
                        break;
                }
                if (tags != null) {
                    try {
                        NfoWriter.export(tags.getFile(), tags, exportContext);
                    } catch (IOException e) {
                        // can't export, folder not writable?
                    }
                }
            }
            cursor.close();
        }
    }

    private static final Uri URI = VideoStore.Video.Media.EXTERNAL_CONTENT_URI;
    private static final String[] PROJECTION = {
            VideoColumns.ARCHOS_MEDIA_SCRAPER_ID,   // 0
            VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE, // 1
    };
    private static final String SELECTION_ALL =
            VideoColumns.ARCHOS_MEDIA_SCRAPER_ID + " > 0 AND " +
            VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE + " > 0";
    private static final String SELECTION_FOLDER =
            VideoColumns.ARCHOS_MEDIA_SCRAPER_ID + " > 0 AND " +
            VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE + " > 0 AND " +
            MediaColumns.DATA + " LIKE ?||'/%'";
    private static final String ORDER = MediaColumns.DATA;

    private Cursor getAllCursor() {
        ContentResolver cr = getContentResolver();
        return cr.query(URI, PROJECTION, SELECTION_ALL, null, ORDER);
    }

    private Cursor getInDirectoryCursor(Uri folder) {
        String path = folder.toString();
        if (path.endsWith("/"))
            path = path.substring(0, path.length() - 1);
        ContentResolver cr = getContentResolver();
        String[] selectionArgs = {
            path
        };
        return cr.query(URI, PROJECTION, SELECTION_FOLDER, selectionArgs, ORDER);
    }

}
