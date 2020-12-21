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
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import android.text.TextUtils;
import android.util.Log;

import com.archos.mediacenter.utils.AppState;
import com.archos.medialib.R;
import com.archos.mediaprovider.video.ScraperStore;
import com.archos.mediascraper.settings.ScraperSetting;
import com.archos.mediascraper.settings.ScraperSettings;
import com.archos.mediascraper.themoviedb3.CollectionInfo;
import com.archos.mediascraper.themoviedb3.CollectionResult;
import com.archos.mediascraper.themoviedb3.MovieCollection;
import com.archos.mediascraper.themoviedb3.MyTmdb;
import com.archos.mediascraper.xml.BaseScraper2;
import com.uwetrottmann.tmdb2.services.CollectionsService;
import com.uwetrottmann.tmdb2.services.MoviesService;
import com.uwetrottmann.tmdb2.services.SearchService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.Cache;

public class AllCollectionScrapeService extends IntentService {
    private static final String PREFERENCE_NAME = "themoviedb.org";

    private static final String TAG = AllCollectionScrapeService.class.getSimpleName();
    private final static boolean DBG = true;

    public static final String INTENT_RESCRAPE_COLLECTION = "archos.mediascraper.intent.action.RESCRAPE_COLLECTION";
    public static final String INTENT_RESCRAPE_ALL_COLLECTIONS = "archos.mediascraper.intent.action.RESCRAPE_ALL_COLLECTIONS";
    private static final String EXPORT_ALL_KEY = "all://";
    private static final Intent VOID_INTENT = new Intent("void");
    private static final ConcurrentHashMap<String, String> sScheduledTasks = new ConcurrentHashMap<String, String>();

    private static final int NOTIFICATION_ID = 12;
    private NotificationManager nm;
    private NotificationCompat.Builder nb;
    private static final String notifChannelId = "AllCollectionScrapeService_id";
    private static final String notifChannelName = "AllCollectionScrapeService";
    private static final String notifChannelDescr = "AllCollectionScrapeService";

    // TODO MARC: rescrape only null images, rescrape all, only one

    private static ScraperSettings sSettings = null;

    private static Context mContext;

    // Add caching for OkHttpClient so that queries for episodes from a same tvshow will get a boost in resolution
    static Cache cache;

    static MyTmdb tmdb = null;
    static SearchService searchService = null;
    static MoviesService moviesService = null;
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

    public static void rescrapeCollection(Context context, Long collectionId) {
        mContext = context;
        Intent serviceIntent = new Intent(context, AllCollectionScrapeService.class);
        serviceIntent.setAction(INTENT_RESCRAPE_COLLECTION);
        serviceIntent.putExtra("collectionId", collectionId);
        if (AppState.isForeGround()) ContextCompat.startForegroundService(context, serviceIntent);
    }

    public static void rescrapeAllCollections(Context context) {
        mContext = context;
        Intent serviceIntent = new Intent(context, AllCollectionScrapeService.class);
        serviceIntent.setAction(INTENT_RESCRAPE_ALL_COLLECTIONS);
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
                .setSmallIcon(android.R.drawable.stat_notify_sync)
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
        }
    }

    private void rescrapeAllCollections() {
        if (DBG) Log.d(TAG, "rescrapeAllCollections");
        nb.setContentText(getString(R.string.nfo_export_exporting_all));
        nm.notify(NOTIFICATION_ID, nb.build());
        handleCursor(getAllCursor());
        removeAllTask();
        stopForeground(true);
    }

    private void rescrapeCollection(Long collectionId) {
        if (DBG) Log.d(TAG, "rescrapeCollection: " + collectionId);
        if (collectionId != null && collectionId > 0) {
            // update notification
            nb.setContentText(collectionId.toString());
            nm.notify(NOTIFICATION_ID, nb.build());
            handleCursor(getCollectionCursor(collectionId));
        }
        removeTask(collectionId);
        stopForeground(true);
    }

    private void handleCursor(Cursor cursor) {
        if (tmdb == null) reauth();
        if (collectionService == null) collectionService = tmdb.collectionService();
        // get configured language
        String language = getLanguage(getApplicationContext());

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

                    if (DBG) Log.d(TAG, "handleCursor: scraping " + collectionTag.mTitle);

                    // TODO MARC: verify mContext or getApplicationContext()
                    // generates the various posters/backdrops based on URL
                    collectionTag.downloadImage(mContext);
                    /*
                    downloadCollectionImage(collectionTag,
                            ImageConfiguration.PosterSize.W342,    // large poster
                            ImageConfiguration.PosterSize.W92,     // thumb poster
                            ImageConfiguration.BackdropSize.W1280, // large bd
                            ImageConfiguration.BackdropSize.W300,  // thumb bd
                            collectionInfo.name, mContext);
                     */

                    // TODO MARC replace if exists?
                    // build list of operations

                    // TODO MARC do not do this do it with tags!!!

                    /*
                    ContentProviderOperation.Builder cop = null;
                    ArrayList<ContentProviderOperation> allOperations = new ArrayList<ContentProviderOperation>();
                    if (DBG) Log.d(TAG, "save: collection " + collectionId);
                    cop = ContentProviderOperation.newInsert(ScraperStore.MovieCollections.URI.BASE);
                    cop.withValue(ScraperStore.MovieCollections.ID, collectionId);
                    cop.withValue(ScraperStore.MovieCollections.NAME, collectionInfo.name);
                    cop.withValue(ScraperStore.MovieCollections.DESCRIPTION, collectionInfo.description);
                    cop.withValue(ScraperStore.MovieCollections.POSTER_LARGE_URL, collectionInfo.poster);

                    cop.withValue(ScraperStore.MovieCollections.POSTER_LARGE_FILE, collectionTag.getPosterLargeFile());

                    cop.withValue(ScraperStore.MovieCollections.POSTER_THUMB_URL, collectionTag.getPosterThumbUrl());
                    cop.withValue(ScraperStore.MovieCollections.POSTER_THUMB_FILE, collectionTag.getPosterThumbFile());

                    cop.withValue(ScraperStore.MovieCollections.BACKDROP_LARGE_URL, collectionInfo.backdrop);

                    cop.withValue(ScraperStore.MovieCollections.BACKDROP_LARGE_FILE, collectionTag.getBackdropLargeFile());

                    cop.withValue(ScraperStore.MovieCollections.BACKDROP_THUMB_URL, collectionTag.getBackdropThumbUrl());
                    cop.withValue(ScraperStore.MovieCollections.BACKDROP_THUMB_FILE, collectionTag.getBackdropThumbFile());

                    allOperations.add(cop.build());
                     */

                }
            }
            cursor.close();
        }
    }

    // TODO MARC to check uri
    private static final Uri URI = ScraperStore.MovieCollections.URI.BASE;
    private static final String[] PROJECTION = {
            ScraperStore.MovieCollections.ID    // 0
    };
    private static final String SELECTION_ALL = "*";
    private static final String SELECTION_COLLECTION = ScraperStore.MovieCollections.ID + " = ?";

    private Cursor getAllCursor() {
        ContentResolver cr = getContentResolver();
        return cr.query(URI, PROJECTION, SELECTION_ALL, null, null);
    }

    private Cursor getCollectionCursor(Long collectionId) {
        ContentResolver cr = getContentResolver();
        String[] selectionArgs = { collectionId.toString() };
        return cr.query(URI, PROJECTION, SELECTION_COLLECTION, selectionArgs, null);
    }

    // TODO MARC no need to generate preference there we have one already no?
    public static String getLanguage(Context context) {
        return generatePreferences(context).getString("language");
    }

    protected static synchronized ScraperSettings generatePreferences(Context context) {
        if (sSettings == null) {
            sSettings = new ScraperSettings(context, PREFERENCE_NAME);
            HashMap<String, String> labelList = new HashMap<String, String>();
            String[] labels = context.getResources().getStringArray(R.array.scraper_labels_array);
            for (String label : labels) {
                String[] splitted = label.split(":");
                labelList.put(splitted[0], splitted[1]);
            }
            // <settings><setting label="info_language" type="labelenum" id="language" values="$$8" sort="yes" default="en"></setting></settings>
            ScraperSetting setting = new ScraperSetting("language", ScraperSetting.STR_LABELENUM);
            String defaultLang = Locale.getDefault().getLanguage();
            if (DBG) Log.d(TAG, "generatePreferences: defaultLang=" + defaultLang);
            if (!TextUtils.isEmpty(defaultLang) && BaseScraper2.LANGUAGES.contains(defaultLang))
                setting.setDefault(defaultLang);
            else
                setting.setDefault("en");
            setting.setLabel(labelList.get("info_language"));
            setting.setValues(BaseScraper2.LANGUAGES);
            sSettings.addSetting("language", setting);
        }
        return sSettings;
    }

    protected String internalGetPreferenceName() {
        return PREFERENCE_NAME;
    }

}
