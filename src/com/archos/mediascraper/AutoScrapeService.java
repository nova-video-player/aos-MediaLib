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

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteBlobTooBigException;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.core.app.NotificationCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.preference.PreferenceManager;

import android.os.Looper;
import android.provider.BaseColumns;

import com.archos.mediacenter.utils.trakt.TraktService;
import com.archos.medialib.R;
import com.archos.mediaprovider.ArchosMediaIntent;
import com.archos.mediaprovider.DeleteFileCallback;
import com.archos.environment.ArchosUtils;
import com.archos.environment.NetworkState;
import com.archos.mediaprovider.video.LoaderUtils;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediaprovider.video.VideoProvider;
import com.archos.mediaprovider.video.WrapperChannelManager;
import com.archos.mediascraper.ShowUtils;
import com.archos.mediascraper.preprocess.SearchInfo;
import com.archos.mediascraper.preprocess.SearchPreprocessor;
import com.archos.mediascraper.xml.MovieScraper3;
import com.archos.mediascraper.xml.ShowScraper4;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by alexandre on 20/05/15.
 */
public class AutoScrapeService extends Service implements DefaultLifecycleObserver {
    public static final String EXPORT_EVERYTHING = "export_everything";
    public static final String RESCAN_EVERYTHING = "rescan_everything";
    public static final String RESCAN_MOVIES = "rescan_movies";
    public static final String RESCAN_COLLECTIONS = "rescan_collections";
    public static final String RESCAN_ONLY_DESC_NOT_FOUND = "rescan_only_desc_not_found";
    private static final int PARAM_NOT_SCRAPED = 0;
    private static final int PARAM_SCRAPED = 1;
    private static final int PARAM_ALL = 2;
    private static final int PARAM_SCRAPED_NOT_FOUND = 3;
    private static final int PARAM_MOVIES = 4;
    private static final Logger log = LoggerFactory.getLogger(AutoScrapeService.class);

    public static final String PREFERENCE_LAST_TIME_VIDEO_SCRAPED_UTC = "last_time_video_scraped_utc";

    // window size used to split queries to db
    private final static int WINDOW_SIZE = 2500;

    static volatile int sNumberOfFilesRemainingToProcess = 0;
    static volatile int sTotalNumberOfFilesRemainingToProcess = 0;
    static volatile int sNumberOfFilesScraped = 0;
    static volatile int sNumberOfFilesNotScraped = 0;
    public static String KEY_ENABLE_AUTO_SCRAP ="enable_auto_scrap_key";
    public static String KEY_SCRAPE_FROM_DB ="scrape_from_database_key";
    private final static String[] SCRAPER_ACTIVITY_COLS = {
            // Columns needed by the activity
            BaseColumns._ID,
            VideoStore.MediaColumns.DATA,
            VideoStore.MediaColumns.TITLE,
            VideoStore.Video.VideoColumns.SCRAPER_MOVIE_ID,
            VideoStore.Video.VideoColumns.SCRAPER_EPISODE_ID,
            VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE,
            VideoStore.Video.VideoColumns.SCRAPER_VIDEO_ONLINE_ID,
            VideoStore.Video.VideoColumns.SCRAPER_E_SEASON
    };
    private Thread mThread;
    private boolean restartOnNextRound = false;
    private AutoScraperBinder mBinder;
    private Thread mExportingThread;
    private static Handler mHandler = new Handler(Looper.getMainLooper());

    private static Context mContext;

    private static final int NOTIFICATION_ID = 4;
    private NotificationManager nm;
    private NotificationCompat.Builder nb;
    private static final String notifChannelId = "AutoScrapeService_id";
    private static final String notifChannelName = "AutoScrapeService";
    private static final String notifChannelDescr = "AutoScrapeService";

    private static volatile boolean scrapeOnlyMovies = false;
    private static volatile boolean sRescanAll = false;
    private static volatile boolean sRescanOnlyNotFound = false;

    private volatile static boolean isForeground = true;
    private static boolean isForceAfterNetworkScan = false;
    private static int networkScanCount = 0;
    private static final Object networkScanLock = new Object();
    private static final String PREF_IS_SCRAPE_DIRTY = "is_scrape_dirty";
    private static final long OBSERVER_REFRESH_THROTTLE_MS = 1500L;
    private static final Object observerRefreshLock = new Object();
    private static long sLastObserverRefreshAtMs = 0L;
    private static final AtomicBoolean sPendingIdleCheck = new AtomicBoolean(false);

    /**
     * Ugly implementation based on a static variable, guessing that there is only one instance at a time (seems to be true...)
     * @return the number of files that are currently in the queue for scraping
     */
    public static int getNumberOfFilesRemainingToProcess() {
        return sTotalNumberOfFilesRemainingToProcess;
    }

    public static void startService(Context context) {
        if (log.isDebugEnabled()) log.debug("startService in foreground");
        mContext = context.getApplicationContext();
        Intent intent = new Intent(context, AutoScrapeService.class);
        mContext = context;
        context.startService(new Intent(context, AutoScrapeService.class));
    }

    public static void startServiceAfterNetworkScan(Context context) {
        if (log.isDebugEnabled()) log.debug("startServiceAfterNetworkScan - forced start after network scan");
        mContext = context.getApplicationContext();
        Intent intent = new Intent(context, AutoScrapeService.class);
        intent.putExtra("FORCE_AFTER_NETWORK_SCAN", true);
        context.startService(intent);
    }

    public static void resetNetworkScanCount() {
        synchronized (networkScanLock) {
            if (networkScanCount > 0) {
                log.warn("resetNetworkScanCount: resetting orphaned counter from {} to 0", networkScanCount);
            }
            networkScanCount = 0;
            isForceAfterNetworkScan = false;
        }
    }

    public static void incrementNetworkScanCount() {
        synchronized (networkScanLock) {
            networkScanCount++;
            if (log.isDebugEnabled()) log.debug("incrementNetworkScanCount: count is now {}", networkScanCount);
        }
    }

    public static void decrementNetworkScanCount() {
        synchronized (networkScanLock) {
            if (networkScanCount > 0) {
                networkScanCount--;
                if (log.isDebugEnabled()) log.debug("decrementNetworkScanCount: count is now {}", networkScanCount);
                if (networkScanCount == 0) {
                    if (log.isDebugEnabled()) log.debug("decrementNetworkScanCount: all network scans complete, resetting force flag");
                    isForceAfterNetworkScan = false;
                }
            } else {
                if (log.isDebugEnabled()) log.debug("decrementNetworkScanCount: count is already 0, this might be a standalone scan");
            }
        }
    }

    public static int getNetworkScanCount() {
        synchronized (networkScanLock) {
            return networkScanCount;
        }
    }

    public void cleanup() {
        if (log.isDebugEnabled()) log.debug("cleanup");
        if (mThread != null && mThread.isAlive()) {
            saveDirtyState(true);
        }
        LoaderUtils.setScrapeInProgress(false);
        isForeground = false;
        // Note: isForceAfterNetworkScan is now managed by networkScanCount
        // Stop the scraping thread if it's running
        if (mThread != null) {
            mThread.interrupt();
            mThread = null;
        }
        // Stop the exporting thread if it's running
        if (mExportingThread != null) {
            mExportingThread.interrupt();
            mExportingThread = null;
        }
        // Cancel the notification
        nm.cancel(NOTIFICATION_ID);
    }

    // Used by system. Don't call
    public AutoScrapeService() {
        if (log.isDebugEnabled()) log.debug("AutoScrapeService() {}", this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Reset static scraping flags to ensure clean state on service creation
        LoaderUtils.setScrapeInProgress(false);

        // need to do that early to avoid ANR on Android 26+
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel nc = new NotificationChannel(notifChannelId, notifChannelName,
                    NotificationManager.IMPORTANCE_DEFAULT);
            nc.setDescription(notifChannelDescr);
            nc.setSound(null, null);
            nc.enableVibration(false);
            if (nm != null)
                nm.createNotificationChannel(nc);
        }
        nb = new NotificationCompat.Builder(this, notifChannelId)
                .setSmallIcon(R.drawable.stat_notify_scraper)
                .setContentTitle(getString(R.string.scraping_in_progress))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setTicker(null).setOnlyAlertOnce(true).setOngoing(true).setAutoCancel(true);

        if (log.isDebugEnabled()) log.debug("onCreate: register lifecycle observer");
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
        mBinder = new AutoScraperBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            super.onStartCommand(intent, flags, startId);
            if (log.isDebugEnabled()) log.debug("onStartCommand");

            // Ensure notification manager and builder are initialized (race condition protection)
            if (nm == null) {
                nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            }
            if (nb == null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
                    NotificationChannel nc = new NotificationChannel(notifChannelId, notifChannelName,
                            NotificationManager.IMPORTANCE_DEFAULT);
                    nc.setDescription(notifChannelDescr);
                    nc.setSound(null, null);
                    nc.enableVibration(false);
                    nm.createNotificationChannel(nc);
                }
                nb = new NotificationCompat.Builder(this, notifChannelId)
                        .setSmallIcon(R.drawable.stat_notify_scraper)
                        .setContentTitle(getString(R.string.scraping_in_progress))
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setTicker(null).setOnlyAlertOnce(true).setOngoing(true).setAutoCancel(true);
            }

        } catch (Throwable t) {
            // Catch any unexpected exceptions during initialization to prevent service crash
            log.error("onStartCommand: Unexpected error during initialization", t);
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            // Check for dirty state and restart scraping if needed
            isForeground = true;
            isForceAfterNetworkScan = intent != null && intent.getBooleanExtra("FORCE_AFTER_NETWORK_SCAN", false);
            if (isForceAfterNetworkScan) {
                if (log.isDebugEnabled()) log.debug("onStartCommand: Force start after network scan - ensuring isForeground = true");
                isForeground = true;
            }
            if (isDirtyState()) {
                if (log.isDebugEnabled()) log.debug("onStartCommand: Rescanning everything due to dirty state");
                // Reset the dirty flag in SharedPreferences
                saveDirtyState(false);
                startScraping(false, false);
                // START_STICKY: Persistent service that monitors ContentObserver for new videos
                // If killed by system, it will restart and check dirty state to resume interrupted operations
                return START_STICKY;
            }

            if (log.isDebugEnabled()) if (log.isDebugEnabled() && intent != null && intent.getAction()==null) log.debug("onStartCommand: action is nul!!!");
            if (log.isDebugEnabled()) if (log.isDebugEnabled() && intent != null && intent.getAction()!=null) log.debug("onStartCommand: action {}", intent.getAction());
            try {
                if(intent!=null) {
                    if(intent.getAction()!=null&&intent.getAction().equals(EXPORT_EVERYTHING)) {
                        if (log.isDebugEnabled()) log.debug("onStartCommand: EXPORT_EVERYTHING");
                        startExporting();
                    } else if (intent.getAction()!=null&&intent.getAction().equals(RESCAN_MOVIES)) {
                        scrapeOnlyMovies = true;
                        if (log.isDebugEnabled()) log.debug("onStartCommand: RESCAN_MOVIES, scrapeOnlyMovies={}", scrapeOnlyMovies);
                        startScraping(true, intent.getBooleanExtra(RESCAN_ONLY_DESC_NOT_FOUND, false));
                    } else {
                        if (log.isDebugEnabled()) log.debug("onStartCommand: RESCAN_EVERYTHING");
                        startScraping(intent.getBooleanExtra(RESCAN_EVERYTHING, false), intent.getBooleanExtra(RESCAN_ONLY_DESC_NOT_FOUND, false));
                    }
                } else {
                    if (log.isDebugEnabled()) log.debug("onStartCommand: rescan incremental");
                    startScraping(false, false);
                }
            } catch (Exception e) {
                log.error("onStartCommand: Exception in service operation", e);
                // Save dirty state if operation was interrupted
                if (LoaderUtils.getScrapeInProgress()) {
                    saveDirtyState(true);
                }
            }
            // START_STICKY: Persistent service that monitors ContentObserver for new videos
            // If killed by system, it will restart and check dirty state to resume interrupted operations
            return START_STICKY;
        } catch (Throwable t) {
            // Catch any unexpected exceptions in scraping operations to prevent service crash
            log.error("onStartCommand: Unexpected error during scraping operations", t);
            if (LoaderUtils.getScrapeInProgress()) {
                saveDirtyState(true);
            }
            return START_STICKY;
        }
    }

    protected void startExporting() {
        if (log.isDebugEnabled()) log.debug("startExporting {}", String.valueOf(mExportingThread == null || !mExportingThread.isAlive()));
        nb.setContentTitle(getString(R.string.nfo_export_in_progress)).setWhen(System.currentTimeMillis());
        if (mExportingThread == null || !mExportingThread.isAlive()) {
            mExportingThread = new Thread() {

                public void run() {
                    Cursor cursor = getFileListCursor(PARAM_SCRAPED, null, null, null);
                    final int numberOfRows = cursor.getCount();
                    sTotalNumberOfFilesRemainingToProcess = numberOfRows;
                    cursor.close();
                    if (log.isDebugEnabled()) log.debug("starting thread {}", numberOfRows);

                    NfoWriter.ExportContext exportContext = new NfoWriter.ExportContext();

                    int index = 0;
                    int window = WINDOW_SIZE;
                    int count = 0;
                    do {
                        int processedInBatch = 0;
                        boolean overflowInBatch = false;
                        if (index + window > numberOfRows)
                            window = numberOfRows - index;
                        if (log.isDebugEnabled()) log.debug("startExporting: new batch fetching cursor from index{} over window {} entries, {}<={}", index, window, (index + window), numberOfRows);
                        cursor = getFileListCursor(PARAM_SCRAPED, BaseColumns._ID, index, window);
                        if (log.isDebugEnabled()) log.debug("startExporting: new batch cursor has size {}", cursor.getCount());

                        sNumberOfFilesRemainingToProcess = window;

                        while ((isForeground || isForceAfterNetworkScan) && !Thread.currentThread().isInterrupted()
                                && PreferenceManager.getDefaultSharedPreferences(AutoScrapeService.this).getBoolean(AutoScrapeService.KEY_ENABLE_AUTO_SCRAP, true)) {
                            try {
                                if (!cursor.moveToNext()) break;
                            } catch (SQLiteBlobTooBigException e) {
                                log.warn("startExporting: CursorWindow overflow, skipping one entry", e);
                                overflowInBatch = true;
                                break;
                            }
                            processedInBatch++;
                            Uri fileUri = Uri.parse(cursor.getString(cursor.getColumnIndex(VideoStore.MediaColumns.DATA)));
                            long movieID = cursor.getLong(cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_MOVIE_ID));
                            long episodeID = cursor.getLong(cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_EPISODE_ID));
                            final int scraperType = cursor.getInt(cursor.getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE));
                            String title = cursor.getString(cursor.getColumnIndex(VideoStore.MediaColumns.TITLE));
                            BaseTags baseTags = null;
                            if (sTotalNumberOfFilesRemainingToProcess > 0)
                                nm.notify(NOTIFICATION_ID, nb.setContentText(getString(R.string.remaining_videos_to_process) + " " + sTotalNumberOfFilesRemainingToProcess  + "\n" + getString(R.string.current_video_title) + " " + title).build());
                            if (!fileUri.toString().startsWith("upnp://")) {
                                if (log.isTraceEnabled()) log.trace("startExporting: {} fileUri {}", movieID, fileUri);
                                if (scraperType == BaseTags.TV_SHOW) {
                                    baseTags = TagsFactory.buildEpisodeTags(AutoScrapeService.this, episodeID);
                                } else if (scraperType == BaseTags.MOVIE) {
                                    baseTags = TagsFactory.buildMovieTags(AutoScrapeService.this, movieID);
                                }
                            } else {
                                if (log.isTraceEnabled()) log.trace("startExporting: Skipping UPnP file: {}", fileUri);
                            }
                            sNumberOfFilesRemainingToProcess--;
                            sTotalNumberOfFilesRemainingToProcess--;
                            if (baseTags == null)
                                continue;
                            if (log.isTraceEnabled()) log.trace("startExporting: Base tag created, exporting {}", fileUri);
                            if (exportContext != null && fileUri != null)
                                try {
                                    NfoWriter.export(fileUri, baseTags, exportContext);
                                } catch (IOException e) {
                                    log.error("caught IOException: ", e);
                                }
                        }
                        if (overflowInBatch && processedInBatch == 0) {
                            // Move forward even if the very first row in this batch is unreadable.
                            processedInBatch = 1;
                            if (sNumberOfFilesRemainingToProcess > 0) sNumberOfFilesRemainingToProcess--;
                            if (sTotalNumberOfFilesRemainingToProcess > 0) sTotalNumberOfFilesRemainingToProcess--;
                        }
                        if (processedInBatch == 0) break;
                        index += processedInBatch;
                        cursor.close();
                    } while (index < numberOfRows && (isForeground || isForceAfterNetworkScan) && !Thread.currentThread().isInterrupted());
                    LoaderUtils.setScrapeInProgress(false);
                    cursor.close();
                }
            };
            mExportingThread.start();
        }
    }
    @Override
    public void onDestroy() {
        if (log.isDebugEnabled()) log.debug("onDestroy() {}", this);
        cleanup();
        super.onDestroy();
    }

    /**
     * Register content observer and start autoscrap if enabled
     * @param context
     */
    public static void registerObserver(Context context) {
        if (log.isDebugEnabled()) log.debug("registerObserver");
        // Extract application context immediately and don't reference the original context parameter
        // This prevents the ContentObserver from capturing the Activity context
        Context appContext = context.getApplicationContext();
        registerObserverInternal(appContext);
    }

    private static void registerObserverInternal(final Context appContext) {
        appContext.getContentResolver().registerContentObserver(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, false, new ContentObserver(null) {
            @Override
            public void onChange(boolean selfChange) {
                // Check if auto scraping is enabled and the app is in the foreground (or forced after network scan)
                if (PreferenceManager.getDefaultSharedPreferences(appContext).getBoolean(KEY_ENABLE_AUTO_SCRAP, true) && (isForeground || isForceAfterNetworkScan)) {
                    // Check if a scraping operation is already in progress
                    if (LoaderUtils.getScrapeInProgress()) {
                        if (!shouldRefreshProgressCount()) {
                            if (log.isTraceEnabled()) {
                                log.trace("registerObserver.onChange: scraping in progress, skip count refresh due to throttle");
                            }
                            return;
                        }
                        int pendingCount = getPendingNotScrapedCount(appContext);
                        if (pendingCount > 0 && pendingCount > sTotalNumberOfFilesRemainingToProcess) {
                            if (log.isDebugEnabled()) {
                                log.debug("registerObserver.onChange: scraping in progress, refresh remaining count {} -> {}", sTotalNumberOfFilesRemainingToProcess, pendingCount);
                            }
                            sTotalNumberOfFilesRemainingToProcess = pendingCount;
                        } else if (log.isTraceEnabled()) {
                            log.trace("registerObserver.onChange: already scraping, pendingCount={}, currentRemaining={}", pendingCount, sTotalNumberOfFilesRemainingToProcess);
                        }
                        return;
                    }

                    // Debounce: during the initial import storm each row insert fires onChange.
                    // Accept the first notification immediately; for subsequent ones within the
                    // throttle window, schedule a single trailing check so that the last DB write
                    // in a burst (e.g. a one-file addition) is never silently dropped.
                    if (!shouldRefreshProgressCount()) {
                        if (sPendingIdleCheck.compareAndSet(false, true)) {
                            mHandler.postDelayed(() -> {
                                // Advance the timestamp so the next burst after this check
                                // doesn't immediately bypass the throttle window.
                                shouldRefreshProgressCount();
                                if (!PreferenceManager.getDefaultSharedPreferences(appContext).getBoolean(KEY_ENABLE_AUTO_SCRAP, true)) return;
                                if (!(isForeground || isForceAfterNetworkScan)) return;
                                if (LoaderUtils.getScrapeInProgress()) return;
                                int pendingCount = getPendingNotScrapedCount(appContext);
                                if (pendingCount > 0) {
                                    if (log.isDebugEnabled()) log.debug("registerObserver: trailing check getting {} videos not yet scraped, launching service.", pendingCount);
                                    AutoScrapeService.startService(appContext);
                                }
                                // Clear at the end so no concurrent observer can enqueue
                                // another runnable while this one is still executing.
                                sPendingIdleCheck.set(false);
                            }, OBSERVER_REFRESH_THROTTLE_MS);
                        }
                        if (log.isTraceEnabled()) log.trace("registerObserver.onChange: idle, skip count refresh due to throttle");
                        return;
                    }
                    // Look for all the videos not yet processed and not located in the Camera folder
                    int pendingCount = getPendingNotScrapedCount(appContext);
                    if (pendingCount > 0) {
                        if (log.isDebugEnabled()) log.debug("registerObserver: onChange getting {} videos not yet scraped, launching service.", pendingCount);
                        AutoScrapeService.startService(appContext);
                    } else {
                        if (log.isDebugEnabled()) log.debug("registerObserver: onChange getting {} videos not yet scraped -> not launching service!", pendingCount);
                    }
                }
            }
        });
    }

    private static boolean shouldRefreshProgressCount() {
        long now = SystemClock.elapsedRealtime();
        synchronized (observerRefreshLock) {
            if (now - sLastObserverRefreshAtMs < OBSERVER_REFRESH_THROTTLE_MS) {
                return false;
            }
            sLastObserverRefreshAtMs = now;
            return true;
        }
    }

    private static int getPendingNotScrapedCount(Context context) {
        String[] selectionArgs = new String[]{ Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath() + "/Camera" + "/%" };
        Cursor cursor = context.getContentResolver().query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, SCRAPER_ACTIVITY_COLS, WHERE_NOT_SCRAPED, selectionArgs, null);
        if (cursor == null) {
            return 0;
        }
        try {
            return cursor.getCount();
        } finally {
            cursor.close();
        }
    }

    public static boolean isEnable(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(AutoScrapeService.KEY_ENABLE_AUTO_SCRAP, true);
    }

    public class AutoScraperBinder extends Binder {
        public AutoScrapeService getService(){
            return AutoScrapeService.this;
        }
    }
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    protected void startScraping(final boolean rescrapAlreadySearched, final boolean onlyNotFound) {
        if (log.isDebugEnabled()) log.debug("startScraping: rescrapAlreadySearched={}, onlyNotFound={}", rescrapAlreadySearched, onlyNotFound);
        if (rescrapAlreadySearched) sRescanAll = true;
        if (onlyNotFound) sRescanOnlyNotFound = true;

        if(mThread==null || !mThread.isAlive()) {
            mThread = new Thread() {

                public int mNetworkOrScrapErrors; //when errors equals to number of files to scrap, stop looping.
                boolean notScraped;
                boolean noScrapeError;
                int totalNumberOfFilesScraped = 0;

                public void run() {
                    Cursor cursor = null;
                    try {
                        if (log.isDebugEnabled()) log.debug("startScraping: thread starting");
                        nb.setContentTitle(getString(R.string.scraping_in_progress));

                        //Global Scrape in Progress, so the browser can skip thumbs in scrape and not waste space in storage
                        LoaderUtils.setScrapeInProgress(true);

                        //Init vars we need for the scrape.
                        boolean shouldScrapeFromDB = PreferenceManager.getDefaultSharedPreferences(AutoScrapeService.this).getBoolean(AutoScrapeService.KEY_SCRAPE_FROM_DB,true);
                        NfoWriter.ExportContext exportContext = null;
                        if (NfoWriter.isNfoAutoExportEnabled(AutoScrapeService.this))
                            exportContext = new NfoWriter.ExportContext();

                        do {
                            // Upgradable parameters check at each iteration
                            boolean shouldRescrapAll = sRescanAll;
                            boolean shouldRescanOnlyNotFound = sRescanOnlyNotFound;

                            int scrapStatusParam = shouldRescrapAll && shouldRescanOnlyNotFound ? PARAM_SCRAPED_NOT_FOUND :
                                            scrapeOnlyMovies ? PARAM_MOVIES :
                                                    shouldRescrapAll ? PARAM_ALL :
                                                            PARAM_NOT_SCRAPED;

                            // Get total number of rows first
                            if (cursor != null) cursor.close();
                            cursor = getFileListCursor(scrapStatusParam, null, null, null);
                            int numberOfRows = cursor.getCount();
                            cursor.close();
                            cursor = null;

                            mNetworkOrScrapErrors = 0;
                            sNumberOfFilesScraped = 0;
                            sNumberOfFilesRemainingToProcess = 0;
                            sNumberOfFilesNotScraped = 0;
                            int numberOfBlobRowsSkipped = 0;
                            restartOnNextRound = false;

                            //Get the number of rows remaining, and exit if nothing to do.
                            if (numberOfRows <= 0) {
                                return;
                            }
                            sNumberOfFilesRemainingToProcess = numberOfRows;
                            sTotalNumberOfFilesRemainingToProcess = numberOfRows;

                            // Process in windowed batches using keyset pagination (_ID > lastSeenId)
                            // to avoid CursorWindow overflow and ensure stable pagination
                            // even when rows are modified during iteration
                            long lastSeenId = -1;
                            boolean hasMoreRows = true;
                            while (hasMoreRows && isEnable(AutoScrapeService.this) && (isForeground || isForceAfterNetworkScan) && !Thread.currentThread().isInterrupted()) {
                                if (cursor != null) cursor.close();
                                cursor = getFileListCursorAfterId(scrapStatusParam, lastSeenId, WINDOW_SIZE);
                                hasMoreRows = false;

                            while (isEnable(AutoScrapeService.this) && (isForeground || isForceAfterNetworkScan) && !Thread.currentThread().isInterrupted()) {
                                try {
                                    if (!cursor.moveToNext()) break;
                                } catch (SQLiteBlobTooBigException e) {
                                    // Break out of this batch and skip the problematic row.
                                    // Query only _ID column to avoid CursorWindow overflow on the skip query.
                                    long skippedId = getNextIdAfter(scrapStatusParam, lastSeenId);
                                    if (skippedId != -1) {
                                        log.warn("startScraping: CursorWindow overflow, skipping _ID={}", skippedId, e);
                                        lastSeenId = skippedId;
                                        numberOfBlobRowsSkipped++;
                                        if (sNumberOfFilesRemainingToProcess > 0) sNumberOfFilesRemainingToProcess--;
                                        if (sTotalNumberOfFilesRemainingToProcess > 0) sTotalNumberOfFilesRemainingToProcess--;
                                    } else {
                                        log.warn("startScraping: CursorWindow overflow, no more rows after _ID={}", lastSeenId, e);
                                    }
                                    hasMoreRows = (skippedId != -1);
                                    break;
                                }
                                hasMoreRows = true;
                                // THIS IS THE HARD STOP, call setScrapeInProgress(false) from another place
                                //in the app, like remove shortcut and I will stop the scrape for you.
                                //Also checks network. We can scrape off 5G, but cant load local NFOs (obviously)
                                //Question is, do we wait for NFO or just fallback to TMDB?
                                // TODO: I think fallback is better, and was how NoVa worked previously.
                                if (!LoaderUtils.getScrapeInProgress() || !NetworkState.isLocalNetworkConnected(AutoScrapeService.this) || !NetworkState.isNetworkConnected(AutoScrapeService.this)) {
                                    sNumberOfFilesRemainingToProcess = 0;
                                    log.debug("startScraping disconnected from network or stop requested");
                                    return;
                                }

                                // for now there is no error and file is not scraped
                                notScraped = true;

                                //Get the information we need about the current file, for use later in the scrape.
                                String title = cursor.getString(cursor.getColumnIndex(VideoStore.MediaColumns.TITLE));
                                Uri fileUri = Uri.parse(cursor.getString(cursor.getColumnIndex(VideoStore.MediaColumns.DATA)));
                                Uri scrapUri = title == null || title.isEmpty() || title.equalsIgnoreCase("null") ? fileUri : Uri.parse("/" + title + ".mp4") ;
                                long ID = cursor.getLong(cursor.getColumnIndex(BaseColumns._ID));
                                lastSeenId = ID;

                                //This get the info to reparse for UPNP, and grab the correct details.
                                boolean reparseInfo = fileUri.toString().toLowerCase().startsWith("upnp");
                                noScrapeError = !title.startsWith("VTS_");
                                if (!noScrapeError) sNumberOfFilesNotScraped++;        //VTS Skip is a non-scrape!

                                //Grab NFO Tags for the File if they exist, and skip the scrape.
                                if (noScrapeError && NfoParser.isNetworkNfoParseEnabled(AutoScrapeService.this) && !fileUri.toString().toLowerCase().startsWith("upnp")) {
                                    BaseTags tags = NfoParser.getTagForFile(fileUri, AutoScrapeService.this);
                                    if (tags != null) {
                                        if (log.isTraceEnabled()) log.trace("startScraping: found NFO");
                                        // if poster url are in nfo or in folder, download is automatic
                                        // if no poster available, try to scrap with good title,
                                        if (ID != -1) {
                                            if (log.isTraceEnabled()) log.trace("startScraping: NFO ID != -1 {}", ID);
                                            // ugly but necessary to avoid poster delete when replacing tag
                                            if (tags.getDefaultPoster() != null)
                                                DeleteFileCallback.DO_NOT_DELETE.add(tags.getDefaultPoster().getLargeFile());

                                            //Get title for notifcation while we are saving tags.
                                            if (tags instanceof EpisodeTags episodeTags) {
                                                if (episodeTags.getEpisodePicture() != null) {
                                                    DeleteFileCallback.DO_NOT_DELETE.add(episodeTags.getEpisodePicture().getLargeFile());
                                                }
                                                if (episodeTags.getShowTags() != null && episodeTags.getShowTags().getDefaultPoster() != null) {
                                                    DeleteFileCallback.DO_NOT_DELETE.add(episodeTags.getShowTags().getDefaultPoster().getLargeFile());
                                                }

                                                //Use Title - Episode Name
                                                title = episodeTags.getShowTags().getTitle() + " - S" +  String.format("%02d", episodeTags.getSeason()) + "E" + String.format("%02d", episodeTags.getEpisode());
                                            } else {
                                                //Use Title
                                                title = ((MovieTags) tags).getTitle();
                                            }

                                            if (log.isTraceEnabled()) log.trace("startScraping: NFO tags.save ID={}", ID);
                                            tags.save(AutoScrapeService.this, ID);
                                            DeleteFileCallback.DO_NOT_DELETE.clear();
                                        } else {
                                            if (log.isTraceEnabled()) log.trace("startScraping: oh oh NFO ID = -1 ");
                                        }
                                        //found NFO thus still no error but scraped
                                        notScraped = false;
                                        sNumberOfFilesScraped++;
                                        noScrapeError = true;
                                        if (tags.getPosters() == null && tags.getDefaultPoster() == null &&
                                                (!(tags instanceof EpisodeTags) || ((EpisodeTags) tags).getShowTags().getPosters() == null)) {//special case for episodes : check show
                                            if (tags.getTitle() != null && !tags.getTitle().isEmpty()) { //if a title is specified in nfo, use it to scrap file
                                                scrapUri = Uri.parse("/" + tags.getTitle() + ".mp4");
                                                if (log.isTraceEnabled()) log.trace("startScraping: no posters using title {}", tags.getTitle());
                                            }
                                            if (log.isTraceEnabled()) log.trace("startScraping: no posters ");
                                            //poster not found thus not scraped and no error
                                            notScraped = true;
                                            noScrapeError = true;
                                        }
                                        if (log.isTraceEnabled()) log.trace("startScraping: NFO found, notScaped {}, noScrapeError {} for {}", notScraped, noScrapeError, fileUri);
                                    }
                                }

                                //No NFO Tags, we need to scrape.
                                if ((notScraped && noScrapeError) || (shouldRescrapAll & noScrapeError)) { //look for online details
                                    if (log.isTraceEnabled()) log.trace("startScraping: NFO NOT found");
                                    ScrapeDetailResult result = null;
                                    boolean searchOnline = true;
                                    if (log.isTraceEnabled()) log.trace("startScraping: rescraping all");
                                    long videoID = cursor.getLong(cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_VIDEO_ONLINE_ID));
                                    final int scraperType = cursor.getInt(cursor.getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE));

                                    //This I have NEVER SEEN WORK! Always scrapes as an unknown, lets prove me wrong or this gets the chop too!
                                    if (scraperType == BaseTags.TV_SHOW) {
                                        // get the whole season
                                        long season = cursor.getLong(cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_E_SEASON));
                                        Bundle b = new Bundle();
                                        b.putInt(Scraper.ITEM_REQUEST_SEASON, (int) season);
                                        b.putInt(Scraper.ITEM_REQUEST_BASIC_VIDEO, 1);
                                        b.putInt(Scraper.ITEM_REQUEST_SEASON, (int) season);
                                        b.putInt(Scraper.ITEM_REQUEST_ALL_EPISODES, (int) season);

                                        if (log.isTraceEnabled()) log.trace("startScraping: rescraping episode for tvId {}, season {}", videoID, season);
                                        SearchResult searchResult = new SearchResult(SearchResult.tvshow, title, (int) videoID);
                                        searchResult.setFile(fileUri);
                                        searchResult.setScraper(new ShowScraper4(AutoScrapeService.this));
                                        result = ShowScraper4.getDetails(new SearchResult(SearchResult.tvshow, title, (int) videoID), b);
                                    } else if (scraperType == BaseTags.MOVIE) {
                                        if (log.isTraceEnabled()) log.trace("startScraping: rescraping movie {}", videoID);
                                        SearchResult searchResult = new SearchResult(SearchResult.movie, title, (int) videoID);
                                        searchResult.setFile(fileUri);
                                        searchResult.setScraper(new MovieScraper3(AutoScrapeService.this));
                                        result = MovieScraper3.getDetails(searchResult, null);
                                    } else {
                                        if (log.isTraceEnabled()) log.trace("startScraping: searching online " + title);
                                        SearchInfo searchInfo = SearchPreprocessor.instance().parseFileBased(fileUri, scrapUri);
                                        if (reparseInfo) searchInfo.setForceReParse(true);
                                        Scraper scraper = new Scraper(AutoScrapeService.this);
                                        searchInfo.setOriginalUri(fileUri);
                                        searchInfo.aggressiveScan = shouldRescanOnlyNotFound;
                                        searchInfo.scrapeFromDB = shouldScrapeFromDB;
                                        result = scraper.getAutoDetails(searchInfo);                //SEARCH FOR MOVIE!
                                        if (log.isTraceEnabled()) log.trace("startScraping: {} {}", ((result.tag != null) ? result.tag.getTitle() : null), ((result.tag != null) ? result.tag.getOnlineId() : null));
                                    }

                                    //HAVE WE GOT A SCRAPE OR AN NFO TAG?
                                    //Don't get movies with the word (NULL), this means (NULL) movie wont scrape automatically by who cares?
                                    if (result != null && result.tag != null && ID != -1 && result.tag.getTitle() != null && !result.tag.getTitle().equals("(NULL)")) {
                                        //IF the title is null, but we scraped OK, use Guessed Title.
                                        if (result.tag.getTitle().isEmpty()) {
                                            result.tag.setTitle(title);
                                        }

                                        //Set the ID and Video Information.
                                        result.tag.setVideoId(ID);
                                        //ugly but necessary to avoid poster delete when replacing tag
                                        if (result.tag.getDefaultPoster() != null) {
                                            DeleteFileCallback.DO_NOT_DELETE.add(result.tag.getDefaultPoster().getLargeFile());
                                        }
                                        if (result.tag instanceof EpisodeTags episodeTags) {
                                            if (episodeTags.getEpisodePicture() != null) {
                                                DeleteFileCallback.DO_NOT_DELETE.add(episodeTags.getEpisodePicture().getLargeFile());
                                            }
                                            if (episodeTags.getShowTags() != null && episodeTags.getShowTags().getDefaultPoster() != null) {
                                                DeleteFileCallback.DO_NOT_DELETE.add(episodeTags.getShowTags().getDefaultPoster().getLargeFile());
                                            }
                                            //Set the Episode title here, we don't have to do an extra isMovie check.
                                            title = episodeTags.getShowTags().getTitle() + " - S" +  String.format("%02d", episodeTags.getSeason()) + "E" + String.format("%02d", episodeTags.getEpisode());
                                        } else {
                                            //Set the Movie title.
                                            title = result.tag.getTitle();
                                        }
                                        if (log.isTraceEnabled()) log.trace("startScraping: online result.tag.save ID={}", ID);

                                        //Save the tags to the database.
                                        long savedId = result.tag.save(AutoScrapeService.this, ID);
                                        if (savedId != -1) {
                                            DeleteFileCallback.DO_NOT_DELETE.clear();
                                            // result exists thus scraped and no error for now
                                            notScraped = false;
                                            sNumberOfFilesScraped++;
                                            totalNumberOfFilesScraped++;
                                            noScrapeError = true;
                                            if (log.isTraceEnabled() && result.tag.getTitle() != null)
                                                log.trace("startScraping: info {}", result.tag.getTitle());

                                            //Export the NFO tag if set in prefs (unless we got this from NFO!)
                                            if (exportContext != null) {
                                                // also auto-export all the data
                                                if (fileUri != null) {
                                                    try {
                                                        if (log.isTraceEnabled()) log.trace("startScraping: exporting NFO");
                                                        NfoWriter.export(fileUri, result.tag, exportContext);
                                                    } catch (IOException e) {
                                                        log.error("Caught IOException: ", e);
                                                    }
                                                }
                                                if (log.isTraceEnabled()) log.trace("startScraping: online info, notScaped {}, noScrapeError {} for {}", notScraped, noScrapeError, fileUri);
                                            }
                                        } else {
                                            log.warn("startScraping: save failed for ID {}", ID);
                                            notScraped = true;
                                            noScrapeError = false;
                                        }
                                    } else if (result != null) {
                                        //not scraped, check for errors
                                        // for tvshow if search returns ScrapeStatus.OKAY but in details it returns ScrapeStaus.ERROR_PARSER it is not counted as a scraping error
                                        // this allows the video to be marked as not to be rescraped
                                        notScraped = true;
                                        noScrapeError = result.status != ScrapeStatus.ERROR && result.status != ScrapeStatus.ERROR_NETWORK && result.status != ScrapeStatus.ERROR_NO_NETWORK;
                                        if (noScrapeError) {
                                            sNumberOfFilesNotScraped++;
                                        }
                                        if (log.isTraceEnabled()) log.trace("startScraping: file {} not scraped among {}", fileUri, sNumberOfFilesNotScraped);
                                    }
                                }

                                //The notifications are at the end now, so we dont have 1 file left to process, we have 0 here at the end.
                                sNumberOfFilesRemainingToProcess--;
                                sTotalNumberOfFilesRemainingToProcess--;

                                //OK, this has to be an OR, because my VTS override actually tests the logic!
                                if (notScraped || !noScrapeError) { //in case of network error, don't go there, and don't save in case we are rescraping already scraped videos
                                    //Show the error on the notification
                                    //Using the file name stripped as the title here, the search suggestion used to scrape.
                                    nm.notify(NOTIFICATION_ID, nb.setContentText(getString(R.string.remaining_videos_to_process) + " " + sTotalNumberOfFilesRemainingToProcess  + "\n" + getString(R.string.noresult_video_title) + " " + title).build());

                                    // Failed => set the scraper fields to -1 so that we will be able
                                    // to skip this file when launching the automated process again
                                    if (log.isTraceEnabled()) log.trace("startScraping: file {} not scraped without error -> mark it as not to be scraped again", fileUri);
                                    ContentValues cv = new ContentValues(2);
                                    cv.put(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID, String.valueOf(-1));
                                    cv.put(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE, String.valueOf(-1));
                                    getContentResolver().update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, cv, BaseColumns._ID + "=?", new String[]{Long.toString(ID)});
                                    mNetworkOrScrapErrors++;
                                } else {
                                    //Show the user a pretty notification, with the Scraped Title.
                                    nm.notify(NOTIFICATION_ID, nb.setContentText(getString(R.string.remaining_videos_to_process) + " " + sTotalNumberOfFilesRemainingToProcess  + "\n" + getString(R.string.current_video_title) + " " + title).build());
                                }
                                //log.debug("startScraping: #filesProcessed={}/{} ({}), #scrapOrNetworkErrors={}, #notScraped={}, current batch #filesToProcess={}/{}", sNumberOfFilesScraped, numberOfRows, sTotalNumberOfFilesRemainingToProcess, mNetworkOrScrapErrors, sNumberOfFilesNotScraped, sNumberOfFilesRemainingToProcess, window);
                            }
                            } // end of windowed batch while loop

                            //Close cursor, and we will next open another one for the loop.
                            if (cursor != null) { cursor.close(); cursor = null; }

                            // Load another cursor and get the row count
                            cursor = getFileListCursor(scrapStatusParam, null, null, null);
                            numberOfRows = cursor.getCount();
                            cursor.close();
                            cursor = null;

                            // Restart only if we did not account for all rows still matching this mode.
                            // For PARAM_SCRAPED_NOT_FOUND, files that fail to scrape are written back
                            // with ARCHOS_MEDIA_SCRAPER_ID=-1 (same value), so they remain in the re-query.
                            // Do not restart in this case — all files have been attempted and the
                            // remaining ones simply cannot be identified; restarting causes an infinite loop.
                            if (scrapStatusParam == PARAM_SCRAPED_NOT_FOUND) {
                                restartOnNextRound = false;
                            } else {
                                restartOnNextRound = (sNumberOfFilesScraped + sNumberOfFilesNotScraped + numberOfBlobRowsSkipped != numberOfRows);
                            }
                        } while (restartOnNextRound && (isForeground || isForceAfterNetworkScan) && !Thread.currentThread().isInterrupted() && PreferenceManager.getDefaultSharedPreferences(AutoScrapeService.this).getBoolean(AutoScrapeService.KEY_ENABLE_AUTO_SCRAP,true));

                        //Refresh the Channels in the UI
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                WrapperChannelManager.refreshChannels(AutoScrapeService.this);
                            }
                        });

                        //Trakt Stuff
                        if (totalNumberOfFilesScraped > 0) {
                            // Save the last scraped timestamp in UTC seconds
                            long utcSeconds = System.currentTimeMillis() / 1000L;
                            PreferenceManager.getDefaultSharedPreferences(AutoScrapeService.this)
                                    .edit()
                                    .putLong(PREFERENCE_LAST_TIME_VIDEO_SCRAPED_UTC, utcSeconds).apply();
                            TraktService.onNewVideo(AutoScrapeService.this); // should be done only at the end to not resync in loop
                        }
                    } finally {
                        if (log.isDebugEnabled()) log.debug("startScraping: finally cleanup");
                        if (cursor != null) cursor.close();
                        // Reset static flags
                        scrapeOnlyMovies = false;
                        sRescanAll = false;
                        sRescanOnlyNotFound = false;
                        //Global Scrape in Progress, so the browser can skip thumbs in scrape and not waste space in storage
                        LoaderUtils.setScrapeInProgress(false);

                        // Notify UI that scraping is complete so boxes can be refreshed
                        Intent intent = new Intent(ArchosMediaIntent.ACTION_VIDEO_SCANNER_SCAN_FINISHED, null);
                        intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                        sendBroadcast(intent);

                        //Kill notification.
                        nm.cancel(NOTIFICATION_ID);
                        stopSelf();
                    }
                }
            };
            mThread.start();
        }
    }

    private static final String WHERE_BASE =
            VideoStore.Video.VideoColumns.ARCHOS_HIDE_FILE + "=0 AND " +
                    VideoStore.MediaColumns.DATA + " NOT LIKE ?";
    private static final String WHERE_NOT_SCRAPED =
            VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID + "=0 AND "+ WHERE_BASE;

    private static final String WHERE_SCRAPED_NOT_FOUND =
            VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID + "=-1 AND "+ WHERE_BASE;

    private static final String WHERE_SCRAPED =
            VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID + ">0 AND " + WHERE_BASE;

    private static final String WHERE_SCRAPED_ALL =
            VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID + ">=0 AND " + WHERE_BASE;

    private static final String WHERE_MOVIES =
            VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID + ">=0 AND " +
                    VideoStore.Video.VideoColumns.SCRAPER_MOVIE_ID + " IS NOT NULL AND " + WHERE_BASE;

    private String getWhereClause(int scrapStatusParam) {
        switch (scrapStatusParam) {
            case PARAM_NOT_SCRAPED:
                return WHERE_NOT_SCRAPED;
            case PARAM_SCRAPED:
                return WHERE_SCRAPED;
            case PARAM_ALL:
                return WHERE_SCRAPED_ALL;
            case PARAM_SCRAPED_NOT_FOUND:
                return WHERE_SCRAPED_NOT_FOUND;
            case PARAM_MOVIES:
                return WHERE_MOVIES;
            default:
                return WHERE_BASE;
        }
    }

    private Cursor getFileListCursor(int scrapStatusParam, String sortOrder, Integer offset, Integer limit) {
        // Look for all the videos not yet processed and not located in the Camera folder
        final String cameraPath =  Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath() + "/Camera";
        String[] selectionArgs = new String[]{ cameraPath + "/%" };
        String where = getWhereClause(scrapStatusParam);
        final String LIMIT = ((offset != null) ? offset + ",": "") + ((limit != null) ? limit : "");
        if (limit != null || offset != null) {
            return getContentResolver().query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI.buildUpon().appendQueryParameter("limit", LIMIT).build(), SCRAPER_ACTIVITY_COLS, where, selectionArgs, sortOrder);
        } else {
            return getContentResolver().query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, SCRAPER_ACTIVITY_COLS, where, selectionArgs, sortOrder);
        }
    }

    private Cursor getFileListCursorAfterId(int scrapStatusParam, long afterId, int limit) {
        final String cameraPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath() + "/Camera";
        String where = getWhereClause(scrapStatusParam) + " AND " + BaseColumns._ID + ">?";
        String[] selectionArgs = new String[]{ cameraPath + "/%", String.valueOf(afterId) };
        return getContentResolver().query(
                VideoStore.Video.Media.EXTERNAL_CONTENT_URI.buildUpon().appendQueryParameter("limit", String.valueOf(limit)).build(),
                SCRAPER_ACTIVITY_COLS, where, selectionArgs, BaseColumns._ID);
    }

    private long getNextIdAfter(int scrapStatusParam, long afterId) {
        final String cameraPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath() + "/Camera";
        String where = getWhereClause(scrapStatusParam) + " AND " + BaseColumns._ID + ">?";
        String[] selectionArgs = new String[]{ cameraPath + "/%", String.valueOf(afterId) };
        String[] idOnly = new String[]{ BaseColumns._ID };
        Cursor c = getContentResolver().query(
                VideoStore.Video.Media.EXTERNAL_CONTENT_URI.buildUpon().appendQueryParameter("limit", "1").build(),
                idOnly, where, selectionArgs, BaseColumns._ID);
        long nextId = -1;
        if (c != null) {
            if (c.moveToFirst()) {
                nextId = c.getLong(0);
            }
            c.close();
        }
        return nextId;
    }

    @Override
    public void onStop(LifecycleOwner owner) {
        // App in background
        if (log.isDebugEnabled()) log.debug("onStop: LifecycleOwner app in background, stopSelf");
        cleanup();
        stopSelf();
    }

    @Override
    public void onStart(LifecycleOwner owner) {
        if (log.isDebugEnabled()) log.debug("onStart: LifecycleOwner app in foreground");
        isForeground = true;
        if (isDirtyState()) {
            if (log.isDebugEnabled()) log.debug("onStart: Rescanning everything due to dirty state");
            // Reset the dirty flag in SharedPreferences
            saveDirtyState(false);
            startScraping(false, false);
        }
    }

    private void saveDirtyState(boolean dirtyState) {
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putBoolean(PREF_IS_SCRAPE_DIRTY, dirtyState)
                .apply();
    }

    private boolean isDirtyState() {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREF_IS_SCRAPE_DIRTY, false);
    }

}
