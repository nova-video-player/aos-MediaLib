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

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.archos.medialib.R;
import com.archos.mediaprovider.video.ScraperStore;
import com.archos.mediascraper.themoviedb3.CollectionInfo;
import com.archos.mediascraper.themoviedb3.CollectionResult;
import com.archos.mediascraper.themoviedb3.MovieCollection;
import com.archos.mediascraper.themoviedb3.MyTmdb;
import com.uwetrottmann.tmdb2.services.CollectionsService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

import okhttp3.Cache;

public class AllCollectionScrapeService extends Service implements DefaultLifecycleObserver {
    private static final String PREFERENCE_NAME = "themoviedb.org";

    private static final Logger log = LoggerFactory.getLogger(AllCollectionScrapeService.class);

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
    private volatile boolean isForeground = false;

    private static Context mContext;

    // Add caching for OkHttpClient so that queries for episodes from a same tvshow will get a boost in resolution
    static Cache cache;

    private static volatile MyTmdb tmdb = null;
    private static volatile CollectionsService collectionService = null;

    static String apiKey = null;

    private volatile Looper mServiceLooper;
    private volatile ServiceHandler mServiceHandler;

    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            onHandleIntent((Intent) msg.obj);
            stopSelf(msg.arg1);
        }
    }

    public static synchronized void reauth() {
        tmdb = new MyTmdb(apiKey, cache);
        collectionService = tmdb.collectionService();
    }

    public static synchronized CollectionsService getCollectionService() {
        if (collectionService == null) reauth();
        return collectionService;
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

    public void rescrapeCollection(Context context, Long collectionId) {
        if (log.isDebugEnabled()) log.debug("rescrapeCollection: {}", collectionId);
        if (collectionId != null && collectionId > 0) {
            handleCursor(getCollectionCursor(collectionId));
        }
        removeTask(collectionId);
    }

    public void rescrapeAllCollections(Context context) {
        if (log.isDebugEnabled()) log.debug("rescrapeAllCollections");
        handleCursor(getAllCursor());
        removeAllTask();
    }

    public void rescrapeNoImageCollections(Context context) {
        if (log.isDebugEnabled()) log.debug("rescrapeNoImageCollections");
        handleCursor(getNoImageCursor());
        removeNoImageTask();
    }

    public AllCollectionScrapeService() {
        super();
        if (log.isDebugEnabled()) log.debug("AllCollectionScrapeService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (log.isDebugEnabled()) log.debug("onCreate");

        HandlerThread thread = new HandlerThread("AllCollectionScrapeService", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);

        // ensure cache is initialized
        synchronized (AllCollectionScrapeService.class) {
            cache = ScraperCache.getCache(getApplicationContext());
            apiKey = getString(R.string.tmdb_api_key);
        }

        // need to do that early to avoid ANR on Android 26+
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel nc = new NotificationChannel(notifChannelId, notifChannelName, NotificationManager.IMPORTANCE_LOW);
            nc.setDescription(notifChannelDescr);
            if (nm != null) nm.createNotificationChannel(nc);
        }
        nb = new NotificationCompat.Builder(this, notifChannelId)
                .setSmallIcon(R.drawable.stat_notify_scraper)
                .setContentTitle(getString(R.string.rescraping_collections))
                .setContentText("")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setTicker(null).setOnlyAlertOnce(true).setOngoing(true).setAutoCancel(true);

        // Register as a lifecycle observer
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    }

    @Override
    public void onDestroy() {
        if (log.isDebugEnabled()) log.debug("onDestroy");
        ProcessLifecycleOwner.get().getLifecycle().removeObserver(this);
        if (mServiceLooper != null) {
            mServiceLooper.quit();
        }
        cleanup();
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        Long collectionId = intent != null ? intent.getLongExtra("collectionId", -1) : null;
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

        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.obj = processIntent ? intent : VOID_INTENT;
        mServiceHandler.sendMessage(msg);
        return START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

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
        if (log.isDebugEnabled()) log.debug("rescrapeAllCollections");
        nb.setContentText(getString(R.string.rescraping_collections));
        nm.notify(NOTIFICATION_ID, nb.build());
        handleCursor(getAllCursor());
        removeAllTask();
    }

    private void rescrapeNoImageCollections() {
        if (log.isDebugEnabled()) log.debug("rescrapeNoImageCollections");
        nb.setContentText(getString(R.string.rescraping_noimage_collections));
        nm.notify(NOTIFICATION_ID, nb.build());
        handleCursor(getNoImageCursor());
        removeNoImageTask();
    }

    private void rescrapeCollection(Long collectionId) {
        if (log.isDebugEnabled()) log.debug("rescrapeCollection: {}", collectionId);
        if (collectionId != null && collectionId > 0) {
            // update notification
            nb.setContentText(getString(R.string.rescraping_collection) + " " + collectionId.toString());
            nm.notify(NOTIFICATION_ID, nb.build());
            handleCursor(getCollectionCursor(collectionId));
        }
        removeTask(collectionId);
    }

    private void handleCursor(Cursor cursor) {

        if (log.isDebugEnabled()) log.debug("bind: {}", DatabaseUtils.dumpCursorToString(cursor));
        
        // get configured language
        String language = Scraper.getLanguage(getApplicationContext());

        if (cursor != null) {
            // do the processing
            while (cursor.moveToNext() && isForeground) {
                long collectionId = cursor.getLong(0);
                if (log.isDebugEnabled()) log.debug("handleCursor: scraping {}", collectionId);
                // scrape collectionId
                CollectionResult collectionResult = MovieCollection.getInfo(collectionId, language, getCollectionService());

                if (collectionResult.status == ScrapeStatus.OKAY && collectionResult.collectionInfo != null) {
                    CollectionInfo collectionInfo = collectionResult.collectionInfo;
                    CollectionTags collectionTag = new CollectionTags();
                    collectionTag.setId(collectionInfo.id);
                    collectionTag.setTitle(collectionInfo.name);
                    collectionTag.setPlot(collectionInfo.description);
                    collectionTag.setPosterPath(collectionInfo.poster);
                    collectionTag.setBackdropPath(collectionInfo.backdrop);
                    if (log.isDebugEnabled()) log.debug("handleCursor: scraping {}", collectionTag.mTitle);
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

    private void cleanup() {
        isForeground = false;
        // Clear the scheduled tasks
        sScheduledTasks.clear();
        // Cancel the notification
        nm.cancel(NOTIFICATION_ID);
    }

    @Override
    public void onStop(LifecycleOwner owner) {
        // App in background
        if (log.isDebugEnabled()) log.debug("onStop: LifecycleOwner app in background stopSelf");
        cleanup();
        stopSelf();
    }

    @Override
    public void onStart(LifecycleOwner owner) {
        if (log.isDebugEnabled()) log.debug("onStart: LifecycleOwner app in foreground");
        isForeground = true;
        // App in foreground
    }
}