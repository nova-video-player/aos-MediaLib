// Copyright 2020 Courville Software
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
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import android.util.Log;

import com.archos.mediacenter.utils.AppState;
import com.archos.medialib.R;
import com.archos.mediaprovider.video.ScraperStore;
import com.archos.mediascraper.settings.ScraperSettings;
import com.archos.mediascraper.themoviedb3.CollectionInfo;
import com.archos.mediascraper.themoviedb3.CollectionResult;
import com.archos.mediascraper.themoviedb3.MovieCollection;
import com.archos.mediascraper.themoviedb3.MyTmdb;
import com.archos.mediascraper.xml.MovieScraper3;
import com.uwetrottmann.tmdb2.services.CollectionsService;

import java.util.concurrent.ConcurrentHashMap;

import okhttp3.Cache;

public class AllCollectionScrapeService extends IntentService {
    private static final String PREFERENCE_NAME = "themoviedb.org";

    private static final String TAG = AllCollectionScrapeService.class.getSimpleName();
    private final static boolean DBG = false;

    public static final String INTENT_RESCRAPE_COLLECTION = "archos.mediascraper.intent.action.RESCRAPE_COLLECTION";
    public static final String INTENT_RESCRAPE_NOIMAGE_COLLECTIONS = "archos.mediascraper.intent.action.RESCRAPE_NOIMAGE_COLLECTIONS";
    public static final String INTENT_RESCRAPE_ALL_COLLECTIONS = "archos.mediascraper.intent.action.RESCRAPE_ALL_COLLECTIONS";
    private static final String EXPORT_ALL_KEY = "all://";
    private static final String EXPORT_NOIMAGE_KEY = "noimage://";
    private static final Intent VOID_INTENT = new Intent("void");
    private static final ConcurrentHashMap<String, String> sScheduledTasks = new ConcurrentHashMap<String, String>();

    private static final int NOTIFICATION_ID = 12;
    private NotificationManager nm;
    private NotificationCompat.Builder nb;
    private static final String notifChannelId = "AllCollectionScrapeService_id";
    private static final String notifChannelName = "AllCollectionScrapeService";
    private static final String notifChannelDescr = "AllCollectionScrapeService";
    private static ScraperSettings sSettings = null;

    private static Context mContext;

    // Add caching for OkHttpClient so that queries for episodes from a same tvshow will get a boost in resolution
    static Cache cache;

    static MyTmdb tmdb = null;
    static CollectionsService collectionService = null;

    static String apiKey = null;

    public static void reauth() {
        tmdb = new MyTmdb(apiKey, cache);
    }

    /**
     * simple guard against multiple tasks of the same directory
     * @return true if this collectionId or an export all task is not scheduled already
     **/
    private static boolean addTask(Long collectionId) {
        if (collectionId != null) {
            // skip task if we are already exporting everything
            if (sScheduledTasks.containsKey(EXPORT_ALL_KEY))
                return false;
            // test is not atomic here, but we don't care.

            // skip if exact matching task is present
            return sScheduledTasks.putIfAbsent(collectionId.toString(), "") == null;
        }
        return false;
    }

    private static void removeTask(Long collectionId) {
        if (collectionId != null) {
            sScheduledTasks.remove(collectionId.toString());
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

    private static boolean addNoImageTask() {
        return sScheduledTasks.putIfAbsent(EXPORT_NOIMAGE_KEY, "") == null;
    }
    private static void removeNoImageTask() {
        sScheduledTasks.remove(EXPORT_NOIMAGE_KEY);
    }

    public static void rescrapeCollection(Context context, Long collectionId) {
        Intent serviceIntent = new Intent(context, AllCollectionScrapeService.class);
        serviceIntent.setAction(INTENT_RESCRAPE_COLLECTION);
        serviceIntent.putExtra("collectionId", collectionId);
        if (AppState.isForeGround()) ContextCompat.startForegroundService(context, serviceIntent);
    }

    public static void rescrapeAllCollections(Context context) {
        Intent serviceIntent = new Intent(context, AllCollectionScrapeService.class);
        serviceIntent.setAction(INTENT_RESCRAPE_ALL_COLLECTIONS);
        if (AppState.isForeGround()) ContextCompat.startForegroundService(context, serviceIntent);
    }

    public static void rescrapeNoImageCollections(Context context) {
        Intent serviceIntent = new Intent(context, AllCollectionScrapeService.class);
        serviceIntent.setAction(INTENT_RESCRAPE_NOIMAGE_COLLECTIONS);
        if (AppState.isForeGround()) ContextCompat.startForegroundService(context, serviceIntent);
    }

    public AllCollectionScrapeService() {
        super(TAG);
        if (DBG) Log.d(TAG, "AllCollectionScrapeService");
        setIntentRedelivery(true);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (DBG) Log.d(TAG, "onCreate");

        // ensure cache is initialized
        synchronized (AllCollectionScrapeService.class) {
            cache = ScraperCache.getCache(getApplicationContext());
            apiKey = getString(R.string.tmdb_api_key);
        }

        // need to do that early to avoid ANR on Android 26+
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel nc = new NotificationChannel(notifChannelId, notifChannelName, nm.IMPORTANCE_LOW);
            nc.setDescription(notifChannelDescr);
            if (nm != null) nm.createNotificationChannel(nc);
        }
        nb = new NotificationCompat.Builder(this, notifChannelId)
                .setSmallIcon(R.drawable.stat_notify_scraper)
                .setContentTitle(getString(R.string.rescraping_collections))
                .setContentText("")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setTicker(null).setOnlyAlertOnce(true).setOngoing(true).setAutoCancel(true);
        startForeground(NOTIFICATION_ID, nb.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        Long collectionId = intent != null ? intent.getLongExtra("collectionId", -1) : null;
        startForeground(NOTIFICATION_ID, nb.build());
        boolean processIntent = false;
        if (INTENT_RESCRAPE_COLLECTION.equals(action)) {
            if (addTask(collectionId))
                processIntent = true;
        } else if (INTENT_RESCRAPE_ALL_COLLECTIONS.equals(action)) {
            if (addAllTask())
                processIntent = true;
        } else if (INTENT_RESCRAPE_NOIMAGE_COLLECTIONS.equals(action)) {
            if (addNoImageTask() && addAllTask()) // if already AllTask, no need for NoImageTask
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
        Long collectionId = intent != null ? intent.getLongExtra("collectionId", -1) : null;

        if (INTENT_RESCRAPE_COLLECTION.equals(action)) {
            rescrapeCollection(collectionId);
        } else if (INTENT_RESCRAPE_ALL_COLLECTIONS.equals(action)) {
            rescrapeAllCollections();
        } else if (INTENT_RESCRAPE_NOIMAGE_COLLECTIONS.equals(action)) {
            rescrapeNoImageCollections();
        }
    }

    private void rescrapeAllCollections() {
        if (DBG) Log.d(TAG, "rescrapeAllCollections");
        nb.setContentText(getString(R.string.rescraping_collections));
        nm.notify(NOTIFICATION_ID, nb.build());
        handleCursor(getAllCursor());
        removeAllTask();
        stopForeground(true);
    }

    private void rescrapeNoImageCollections() {
        if (DBG) Log.d(TAG, "rescrapeNoImageCollections");
        nb.setContentText(getString(R.string.rescraping_noimage_collections));
        nm.notify(NOTIFICATION_ID, nb.build());
        handleCursor(getNoImageCursor());
        removeNoImageTask();
        stopForeground(true);
    }

    private void rescrapeCollection(Long collectionId) {
        if (DBG) Log.d(TAG, "rescrapeCollection: " + collectionId);
        if (collectionId != null && collectionId > 0) {
            // update notification
            nb.setContentText(getString(R.string.rescraping_collection) + " " + collectionId.toString());
            nm.notify(NOTIFICATION_ID, nb.build());
            handleCursor(getCollectionCursor(collectionId));
        }
        removeTask(collectionId);
        stopForeground(true);
    }

    private void handleCursor(Cursor cursor) {

        if (DBG) Log.d(TAG, "bind: " + DatabaseUtils.dumpCursorToString(cursor));

        if (tmdb == null) reauth();
        if (collectionService == null) collectionService = tmdb.collectionService();
        // get configured language
        String language = MovieScraper3.getLanguage(getApplicationContext());

        if (cursor != null) {
            // do the processing
            while (cursor.moveToNext()) {
                long collectionId = cursor.getLong(0);
                if (DBG) Log.d(TAG, "handleCursor: scraping " + collectionId);
                // scrape collectionId
                CollectionResult collectionResult = MovieCollection.getInfo(collectionId, language, collectionService);

                if (collectionResult.status == ScrapeStatus.OKAY && collectionResult.collectionInfo != null) {
                    CollectionInfo collectionInfo = collectionResult.collectionInfo;
                    CollectionTags collectionTag = new CollectionTags();
                    collectionTag.setId(collectionInfo.id);
                    collectionTag.setTitle(collectionInfo.name);
                    collectionTag.setPlot(collectionInfo.description);
                    collectionTag.setPosterPath(collectionInfo.poster);
                    collectionTag.setBackdropPath(collectionInfo.backdrop);
                    if (DBG) Log.d(TAG, "handleCursor: scraping " + collectionTag.mTitle);
                    // generates the various posters/backdrops based on URL
                    collectionTag.downloadImage(getApplicationContext());
                    collectionTag.save(getApplicationContext(), true);
                }
            }
            cursor.close();
        }
    }

    private static final Uri URI = ScraperStore.MovieCollections.URI.BASE;
    private static final String[] PROJECTION = {
            ScraperStore.MovieCollections.ID    // 0
    };
    private static final String SELECTION_ALL = ScraperStore.MovieCollections.ID + " > 0";
    private static final String SELECTION_COLLECTION = ScraperStore.MovieCollections.ID + " = ?";

    private static final String SELECTION_NOIMAGE = ScraperStore.MovieCollections.ID + " > 0 AND ( "
            + ScraperStore.MovieCollections.POSTER_LARGE_FILE + " IS NULL OR "
            + ScraperStore.MovieCollections.POSTER_LARGE_FILE + " IS NULL )";

    private Cursor getAllCursor() {
        ContentResolver cr = getContentResolver();
        return cr.query(URI, PROJECTION, SELECTION_ALL, null, null);
    }

    private Cursor getNoImageCursor() {
        ContentResolver cr = getContentResolver();
        return cr.query(URI, PROJECTION, SELECTION_NOIMAGE, null, null);
    }

    private Cursor getCollectionCursor(Long collectionId) {
        ContentResolver cr = getContentResolver();
        String[] selectionArgs = { collectionId.toString() };
        return cr.query(URI, PROJECTION, SELECTION_COLLECTION, selectionArgs, null);
    }
}
