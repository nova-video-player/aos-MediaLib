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

import java.util.Locale;

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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
    private static final int PARAM_PERSISTENCE_RETRY = 5;
    private static final int MAX_AUTOSCRAPE_ROUNDS = 4;
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
    private static final AtomicReference<Thread> sScrapeWorker = new AtomicReference<>();
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
    // True when any scan in the current network scan batch hit a database error
    // (e.g. full disk). Guarded by networkScanLock together with networkScanCount so
    // the count and the error state of a batch are always observed consistently.
    private static boolean networkScanBatchHadError = false;
    // True when at least one scan in the current batch resolved and indexed successfully.
    // Post-scan scraping is started based on this batch outcome rather than on whichever
    // thread happened to finish the batch last (which may itself have failed to resolve).
    private static boolean networkScanBatchHadSuccess = false;
    // Identifies the batch a scan belongs to. Only scans carrying the current batch id
    // affect the active batch; standalone scans (STANDALONE_SCAN_BATCH_ID) and stale
    // completions from an older batch are ignored so they cannot decrement or complete a
    // batch they never joined.
    public static final long STANDALONE_SCAN_BATCH_ID = 0L;
    private static long currentNetworkScanBatchId = STANDALONE_SCAN_BATCH_ID;
    private static long nextNetworkScanBatchId = 1L;
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
        try {
            context.startService(new Intent(context, AutoScrapeService.class));
        } catch (Exception e) {
            log.warn("startService: Failed to start AutoScrapeService - timing or background restriction issue", e);
        }
    }

    public static void startServiceAfterNetworkScan(Context context) {
        if (log.isDebugEnabled()) log.debug("startServiceAfterNetworkScan - forced start after network scan");
        mContext = context.getApplicationContext();
        Intent intent = new Intent(context, AutoScrapeService.class);
        intent.putExtra("FORCE_AFTER_NETWORK_SCAN", true);
        try {
            context.startService(intent);
        } catch (Exception e) {
            log.warn("startServiceAfterNetworkScan: Failed to start AutoScrapeService - timing or background restriction issue", e);
        }
    }

    /**
     * Begins a new network scan batch with all its members registered atomically, clearing
     * any leftover state from a previous one, and returns the id that every member of this
     * batch must carry. Registering the full membership up front (instead of incrementing as
     * each scan is scheduled) guarantees no early scan can observe a partially-built batch,
     * complete it, and make later registrations stale. Each member then reports back exactly
     * once via {@link #completeNetworkScan(long, boolean, boolean)} (a member whose request is
     * rejected, e.g. as a duplicate, releases its slot with the same call).
     *
     * @param memberCount the number of scans that belong to this batch (must be &gt; 0)
     * @return the new batch id, or {@link #STANDALONE_SCAN_BATCH_ID} if a batch is already
     *         active and this request was therefore rejected
     */
    public static long startNetworkScanBatch(int memberCount) {
        synchronized (networkScanLock) {
            if (networkScanCount > 0) {
                // A batch is still in progress: refuse to replace it so an overlapping
                // auto-refresh cannot wipe out a valid active batch's accounting. The caller
                // must check the sentinel return and not schedule anything.
                log.warn("startNetworkScanBatch: a batch with {} pending scans is already active; rejecting new batch request", networkScanCount);
                return STANDALONE_SCAN_BATCH_ID;
            }
            networkScanCount = Math.max(0, memberCount);
            networkScanBatchHadError = false;
            networkScanBatchHadSuccess = false;
            isForceAfterNetworkScan = false;
            currentNetworkScanBatchId = nextNetworkScanBatchId++;
            if (currentNetworkScanBatchId == STANDALONE_SCAN_BATCH_ID) {
                // never hand out the sentinel value as a real batch id
                currentNetworkScanBatchId = nextNetworkScanBatchId++;
            }
            if (log.isDebugEnabled()) log.debug("startNetworkScanBatch: batch id {} with {} members", currentNetworkScanBatchId, networkScanCount);
            return currentNetworkScanBatchId;
        }
    }

    public static void resetNetworkScanCount() {
        synchronized (networkScanLock) {
            if (networkScanCount > 0) {
                log.warn("resetNetworkScanCount: resetting orphaned counter from {} to 0", networkScanCount);
            }
            networkScanCount = 0;
            networkScanBatchHadError = false;
            networkScanBatchHadSuccess = false;
            currentNetworkScanBatchId = STANDALONE_SCAN_BATCH_ID;
            isForceAfterNetworkScan = false;
        }
    }

    /** Result of {@link #completeNetworkScan(long, boolean, boolean)}. */
    public static class NetworkScanCompletion {
        /** true if this caller was the one that finished the batch (count reached 0). */
        public final boolean completedBatch;
        /** true if any scan in the just-completed batch hit a database error. */
        public final boolean batchHadError;
        /** true if any scan in the just-completed batch resolved and indexed successfully. */
        public final boolean batchHadSuccess;
        NetworkScanCompletion(boolean completedBatch, boolean batchHadError, boolean batchHadSuccess) {
            this.completedBatch = completedBatch;
            this.batchHadError = batchHadError;
            this.batchHadSuccess = batchHadSuccess;
        }
    }

    /**
     * Atomically records the completion of one network scan. A scan only affects the
     * batch whose id it carries: a standalone scan ({@link #STANDALONE_SCAN_BATCH_ID})
     * completes as its own one-off batch, and a completion from a stale/older batch is
     * ignored. For a member of the active batch this decrements the count and, when it
     * reaches zero, reports that this caller owns the completion together with the
     * aggregated error and success state of the whole batch. Keeping the decrement and
     * the flags under a single lock prevents two threads from both observing zero and
     * racing on post-scan handling.
     *
     * @param batchId the batch this scan belongs to, or {@link #STANDALONE_SCAN_BATCH_ID}
     * @param hadError true if this particular scan aborted on a database error
     * @param resolvedSuccessfully true if this scan resolved its target and indexed without error
     * @return completion state; only the caller with {@code completedBatch == true}
     *         should run end-of-batch work
     */
    public static NetworkScanCompletion completeNetworkScan(long batchId, boolean hadError, boolean resolvedSuccessfully) {
        synchronized (networkScanLock) {
            if (batchId == STANDALONE_SCAN_BATCH_ID) {
                // standalone scan that was never counted: it is its own completed batch so
                // post-scan handling runs once, without touching the active batch's state.
                if (log.isDebugEnabled()) log.debug("completeNetworkScan: standalone scan, hadError={}, resolved={}", hadError, resolvedSuccessfully);
                return new NetworkScanCompletion(true, hadError, resolvedSuccessfully);
            }
            if (batchId != currentNetworkScanBatchId) {
                // stale completion from an older batch: ignore so it cannot complete or
                // corrupt the current batch's accounting.
                log.warn("completeNetworkScan: ignoring stale completion for batch {} (current {})", batchId, currentNetworkScanBatchId);
                return new NetworkScanCompletion(false, false, false);
            }
            if (hadError) networkScanBatchHadError = true;
            if (resolvedSuccessfully) networkScanBatchHadSuccess = true;
            boolean completedBatch;
            if (networkScanCount > 0) {
                networkScanCount--;
                if (log.isDebugEnabled()) log.debug("completeNetworkScan: count is now {}", networkScanCount);
                completedBatch = (networkScanCount == 0);
            } else {
                // member of the current batch but the count is already 0: treat as the
                // batch completion so post-scan handling still runs once.
                if (log.isDebugEnabled()) log.debug("completeNetworkScan: count already 0 for current batch");
                completedBatch = true;
            }
            boolean batchHadError = false;
            boolean batchHadSuccess = false;
            if (completedBatch) {
                isForceAfterNetworkScan = false;
                batchHadError = networkScanBatchHadError;
                batchHadSuccess = networkScanBatchHadSuccess;
                networkScanBatchHadError = false;
                networkScanBatchHadSuccess = false;
                currentNetworkScanBatchId = STANDALONE_SCAN_BATCH_ID;
            }
            return new NetworkScanCompletion(completedBatch, batchHadError, batchHadSuccess);
        }
    }

    public static int getNetworkScanCount() {
        synchronized (networkScanLock) {
            return networkScanCount;
        }
    }

    /**
     * Runs the shared end-of-batch handling for a {@link NetworkScanCompletion}. Every caller
     * that obtains a completion (a finished scan, a rejected duplicate, or a scan whose request
     * could never be scheduled) must route it here so the batch outcome is acted on exactly once,
     * regardless of which member happened to own the final completion. Only the owning caller
     * ({@code completedBatch == true}) triggers post-scan work; on a clean batch with at least one
     * successful index it starts the auto-scrape service.
     */
    public static void handleNetworkScanCompletion(Context context, NetworkScanCompletion completion) {
        if (!completion.completedBatch) return;
        if (completion.batchHadError) {
            log.warn("handleNetworkScanCompletion: skipping AutoScrapeService, a database error occurred during this scan batch");
        } else if (completion.batchHadSuccess && isEnable(context)) {
            if (log.isDebugEnabled()) log.debug("handleNetworkScanCompletion: batch completed successfully, starting AutoScrapeService");
            startServiceAfterNetworkScan(context);
        }
    }

    public void cleanup() {
        if (log.isDebugEnabled()) log.debug("cleanup");
        Thread scrapeWorker = sScrapeWorker.get();
        if (scrapeWorker != null && scrapeWorker.isAlive()) {
            saveDirtyState(true);
        }
        LoaderUtils.setScrapeInProgress(false);
        isForeground = false;
        // Note: isForceAfterNetworkScan is now managed by networkScanCount
        // Stop the scraping thread if it's running
        if (scrapeWorker != null) {
            scrapeWorker.interrupt();
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
                            Uri fileUri = Uri.parse(cursor.getString(cursor.getColumnIndexOrThrow(VideoStore.MediaColumns.DATA)));
                            long movieID = cursor.getLong(cursor.getColumnIndexOrThrow(VideoStore.Video.VideoColumns.SCRAPER_MOVIE_ID));
                            long episodeID = cursor.getLong(cursor.getColumnIndexOrThrow(VideoStore.Video.VideoColumns.SCRAPER_EPISODE_ID));
                            final int scraperType = cursor.getInt(cursor.getColumnIndexOrThrow(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE));
                            String title = cursor.getString(cursor.getColumnIndexOrThrow(VideoStore.MediaColumns.TITLE));
                            BaseTags baseTags = null;
                            if (sTotalNumberOfFilesRemainingToProcess > 0)
                                nm.notify(NOTIFICATION_ID, nb.setContentTitle(getString(R.string.nfo_export_in_progress) + " (" + sTotalNumberOfFilesRemainingToProcess + ")")
                                        .setContentText(getString(R.string.current_video_title) + " " + title).build());
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
                    // exports are dispatched asynchronously; wait for them before finishing
                    NfoWriter.awaitPendingExports();
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
                                try {
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
                                } finally {
                                    // Always clear so future bursts can enqueue a new trailing check.
                                    sPendingIdleCheck.set(false);
                                }
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

        Thread worker = new Thread() {

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

                        Set<Long> pendingPersistenceRetryIds = new HashSet<>();
                        int completedScrapeRounds = 0;
                        do {
                            completedScrapeRounds++;
                            // Upgradable parameters check at each iteration
                            boolean shouldRescrapAll = sRescanAll;
                            boolean shouldRescanOnlyNotFound = sRescanOnlyNotFound;

                            int scrapStatusParam = shouldRescrapAll && shouldRescanOnlyNotFound ? PARAM_SCRAPED_NOT_FOUND :
                                            scrapeOnlyMovies ? PARAM_MOVIES :
                                                    shouldRescrapAll ? PARAM_ALL :
                                                            PARAM_NOT_SCRAPED;

                            Set<Long> persistenceRetryIds = new HashSet<>(pendingPersistenceRetryIds);
                            pendingPersistenceRetryIds.clear();
                            boolean persistenceRetryRound = !persistenceRetryIds.isEmpty();
                            int cursorStatusParam = persistenceRetryRound
                                    ? PARAM_PERSISTENCE_RETRY : scrapStatusParam;
                            if (persistenceRetryRound) {
                                log.warn("startScraping: retrying {} database persistence failures (round {}/{})",
                                        persistenceRetryIds.size(), completedScrapeRounds, MAX_AUTOSCRAPE_ROUNDS);
                            }

                            // Get total number of rows first
                            int numberOfRows;
                            if (persistenceRetryRound) {
                                numberOfRows = persistenceRetryIds.size();
                            } else {
                                if (cursor != null) cursor.close();
                                cursor = getFileListCursor(cursorStatusParam, null, null, null);
                                numberOfRows = cursor.getCount();
                                cursor.close();
                                cursor = null;
                            }

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
                                cursor = getFileListCursorAfterId(cursorStatusParam, lastSeenId, WINDOW_SIZE);
                                hasMoreRows = false;

                            while (isEnable(AutoScrapeService.this) && (isForeground || isForceAfterNetworkScan) && !Thread.currentThread().isInterrupted()) {
                                try {
                                    if (!cursor.moveToNext()) break;
                                } catch (SQLiteBlobTooBigException e) {
                                    // Break out of this batch and skip the problematic row.
                                    // Query only _ID column to avoid CursorWindow overflow on the skip query.
                                    long skippedId = getNextIdAfter(cursorStatusParam, lastSeenId);
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
                                long ID = cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns._ID));
                                lastSeenId = ID;
                                if (persistenceRetryRound && !persistenceRetryIds.contains(ID)) {
                                    continue;
                                }
                                // THIS IS THE HARD STOP, call setScrapeInProgress(false) from another place
                                //in the app, like remove shortcut and I will stop the scrape for you.
                                //Also checks network. We can scrape off 5G, but cant load local NFOs (obviously)
                                //Question is, do we wait for NFO or just fallback to TMDB?
                                // TODO: I think fallback is better, and was how NoVa worked previously.
                                if (!LoaderUtils.getScrapeInProgress() ||
                                        (!NetworkState.isLocalNetworkConnected(AutoScrapeService.this) && !NetworkState.isNetworkConnected(AutoScrapeService.this))) {
                                    sNumberOfFilesRemainingToProcess = 0;
                                    log.debug("startScraping disconnected from network or stop requested");
                                    return;
                                }

                                // for now there is no error and file is not scraped
                                notScraped = true;
                                boolean persistenceFailed = false;
                                // a structurally valid NFO whose tags were persisted is authoritative
                                boolean nfoPersisted = false;

                                //Get the information we need about the current file, for use later in the scrape.
                                String title = cursor.getString(cursor.getColumnIndexOrThrow(VideoStore.MediaColumns.TITLE));
                                Uri fileUri = Uri.parse(cursor.getString(cursor.getColumnIndexOrThrow(VideoStore.MediaColumns.DATA)));
                                Uri scrapUri = title == null || title.isEmpty() || title.equalsIgnoreCase("null") ? fileUri : Uri.parse("/" + title + ".mp4") ;

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
                                                title = episodeTags.getShowTags().getTitle() + " - S" +  String.format(Locale.ROOT, "%02d", episodeTags.getSeason()) + "E" + String.format(Locale.ROOT, "%02d", episodeTags.getEpisode());
                                            } else {
                                                //Use Title
                                                title = ((MovieTags) tags).getTitle();
                                            }

                                            if (log.isTraceEnabled()) log.trace("startScraping: NFO tags.save ID={}", ID);
                                            if (Thread.currentThread().isInterrupted()) return;
                                            long savedId = tags.save(AutoScrapeService.this, ID);
                                            DeleteFileCallback.DO_NOT_DELETE.clear();
                                            if (savedId < 0) {
                                                log.warn("startScraping: NFO save failed for ID {}", ID);
                                                persistenceFailed = true;
                                                pendingPersistenceRetryIds.add(ID);
                                            } else {
                                                nfoPersisted = true;
                                            }
                                        } else {
                                            if (log.isTraceEnabled()) log.trace("startScraping: oh oh NFO ID = -1 ");
                                        }
                                        // Only a persisted NFO counts as scraped. ID == -1 never saves,
                                        // so it must stay "not scraped" and fall through to online identification.
                                        notScraped = !nfoPersisted;
                                        if (nfoPersisted) {
                                            sNumberOfFilesScraped++;
                                        }
                                        noScrapeError = !persistenceFailed;
                                        // A successfully persisted NFO is authoritative for identity and
                                        // season/episode ordering. A missing poster must NOT re-enable the
                                        // online identification path, because that re-identify+save overwrites
                                        // the curated NFO data (see #1782). Only log the missing artwork; an
                                        // artwork-only fetch can be added later without re-identifying.
                                        if (nfoPersisted && hasNoUsableNfoPoster(tags)) {
                                            if (log.isTraceEnabled()) log.trace("startScraping: NFO saved but no usable poster, keeping NFO data authoritative for {}", fileUri);
                                        }
                                        if (log.isTraceEnabled()) log.trace("startScraping: NFO found, notScaped {}, noScrapeError {} for {}", notScraped, noScrapeError, fileUri);
                                    }
                                }

                                //No NFO Tags, we need to scrape.
                                if (shouldIdentifyOnline(nfoPersisted, persistenceFailed, notScraped, noScrapeError, shouldRescrapAll)) { //look for online details
                                    if (log.isTraceEnabled()) log.trace("startScraping: NFO NOT found");
                                    ScrapeDetailResult result = null;
                                    boolean searchOnline = true;
                                    if (log.isTraceEnabled()) log.trace("startScraping: rescraping all");
                                    long videoID = cursor.getLong(cursor.getColumnIndexOrThrow(VideoStore.Video.VideoColumns.SCRAPER_VIDEO_ONLINE_ID));
                                    final int scraperType = cursor.getInt(cursor.getColumnIndexOrThrow(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE));

                                    //This I have NEVER SEEN WORK! Always scrapes as an unknown, lets prove me wrong or this gets the chop too!
                                    if (scraperType == BaseTags.TV_SHOW) {
                                        // get the whole season
                                        long season = cursor.getLong(cursor.getColumnIndexOrThrow(VideoStore.Video.VideoColumns.SCRAPER_E_SEASON));
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
                                            title = episodeTags.getShowTags().getTitle() + " - S" +  String.format(Locale.ROOT, "%02d", episodeTags.getSeason()) + "E" + String.format(Locale.ROOT, "%02d", episodeTags.getEpisode());
                                        } else {
                                            //Set the Movie title.
                                            title = result.tag.getTitle();
                                        }
                                        if (log.isTraceEnabled()) log.trace("startScraping: online result.tag.save ID={}", ID);

                                        //Save the tags to the database.
                                        if (Thread.currentThread().isInterrupted()) return;
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
                                            persistenceFailed = true;
                                            pendingPersistenceRetryIds.add(ID);
                                            DeleteFileCallback.DO_NOT_DELETE.clear();
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
                                if (persistenceFailed) {
                                    log.error("startScraping: keeping file {} retryable after database save failure", fileUri);
                                    mNetworkOrScrapErrors++;
                                } else if (shouldMarkAsNotFound(persistenceFailed, notScraped, noScrapeError)) { //in case of network error, don't go there, and don't save in case we are rescraping already scraped videos
                                    //Show the error on the notification
                                    //Using the file name stripped as the title here, the search suggestion used to scrape.
                                    nm.notify(NOTIFICATION_ID, nb.setContentTitle(getString(R.string.scraping_in_progress) + " (" + sTotalNumberOfFilesRemainingToProcess + ")")
                                            .setContentText(getString(R.string.noresult_video_title) + " " + title).build());

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
                                    nm.notify(NOTIFICATION_ID, nb.setContentTitle(getString(R.string.scraping_in_progress) + " (" + sTotalNumberOfFilesRemainingToProcess + ")")
                                            .setContentText(getString(R.string.current_video_title) + " " + title).build());
                                }
                                //log.debug("startScraping: #filesProcessed={}/{} ({}), #scrapOrNetworkErrors={}, #notScraped={}, current batch #filesToProcess={}/{}", sNumberOfFilesScraped, numberOfRows, sTotalNumberOfFilesRemainingToProcess, mNetworkOrScrapErrors, sNumberOfFilesNotScraped, sNumberOfFilesRemainingToProcess, window);
                            }
                            } // end of windowed batch while loop

                            //Close cursor, and we will next open another one for the loop.
                            if (cursor != null) { cursor.close(); cursor = null; }

                            if (!persistenceRetryRound) {
                                // Load another cursor and get the row count
                                cursor = getFileListCursor(scrapStatusParam, null, null, null);
                                numberOfRows = cursor.getCount();
                                cursor.close();
                                cursor = null;
                            }

                            // Restart only if we did not account for all rows still matching this mode.
                            // For PARAM_SCRAPED_NOT_FOUND, files that fail to scrape are written back
                            // with ARCHOS_MEDIA_SCRAPER_ID=-1 (same value), so they remain in the re-query.
                            // Do not restart in this case — all files have been attempted and the
                            // remaining ones simply cannot be identified; restarting causes an infinite loop.
                            if (!pendingPersistenceRetryIds.isEmpty()) {
                                restartOnNextRound = true;
                            } else if (persistenceRetryRound || scrapStatusParam == PARAM_SCRAPED_NOT_FOUND) {
                                restartOnNextRound = false;
                            } else {
                                restartOnNextRound = (sNumberOfFilesScraped + sNumberOfFilesNotScraped + numberOfBlobRowsSkipped != numberOfRows);
                            }
                        } while (shouldRunAnotherScrapeRound(completedScrapeRounds, restartOnNextRound)
                                && (isForeground || isForceAfterNetworkScan)
                                && !Thread.currentThread().isInterrupted()
                                && PreferenceManager.getDefaultSharedPreferences(AutoScrapeService.this)
                                        .getBoolean(AutoScrapeService.KEY_ENABLE_AUTO_SCRAP, true));

                        if (restartOnNextRound && completedScrapeRounds >= MAX_AUTOSCRAPE_ROUNDS) {
                            log.warn("startScraping: stopping after the hard limit of {} rounds",
                                    MAX_AUTOSCRAPE_ROUNDS);
                        }
                        if (!pendingPersistenceRetryIds.isEmpty()) {
                            log.error("startScraping: {} database persistence failures remain after the bounded retry",
                                    pendingPersistenceRetryIds.size());
                        }

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
                        Thread current = Thread.currentThread();
                        try {
                            if (log.isDebugEnabled()) log.debug("startScraping: finally cleanup");
                            // Flush NFO exports queued during scraping before the service stops.
                            // This runs on every exit path (normal completion and early returns).
                            // If scraping was interrupted (user-requested stop) abandon the queued
                            // exports for a prompt shutdown rather than blocking on them; the NFOs
                            // are re-derivable on the next scrape.
                            if (current.isInterrupted()) {
                                if (log.isDebugEnabled()) log.debug("startScraping: interrupted, abandoning queued NFO exports");
                            } else {
                                NfoWriter.awaitPendingExports();
                            }
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
                        } finally {
                            releaseScrapeWorker(current);
                        }
                    }
                }
            };
        if (!claimScrapeWorker(worker)) {
            if (log.isDebugEnabled()) log.debug("startScraping: a scrape worker is already active");
            return;
        }
        try {
            // Give every scrape pass one opportunity to replace each generated local-artwork file.
            // Shared show/season targets are deduplicated for the rest of the pass by ScraperImage.
            ScraperImage.resetLocalArtworkRefreshState();
            worker.start();
        } catch (RuntimeException e) {
            releaseScrapeWorker(worker);
            throw e;
        }
    }

    static boolean claimScrapeWorker(Thread worker) {
        return sScrapeWorker.compareAndSet(null, worker);
    }

    static void releaseScrapeWorker(Thread worker) {
        sScrapeWorker.compareAndSet(worker, null);
    }

    static boolean shouldMarkAsNotFound(boolean persistenceFailed, boolean notScraped,
            boolean noScrapeError) {
        return !persistenceFailed && (notScraped || !noScrapeError);
    }

    static boolean shouldRunAnotherScrapeRound(int completedRounds, boolean restartRequested) {
        return restartRequested && completedRounds < MAX_AUTOSCRAPE_ROUNDS;
    }

    /**
     * Decides whether the online identification (re-identify + save) path should run.
     * A persisted NFO is authoritative for identity and season/episode ordering, so it
     * suppresses online identification even during "rescrape all" (see #1782). A failed
     * NFO persistence does not trigger an online attempt either; it stays retryable.
     */
    static boolean shouldIdentifyOnline(boolean nfoPersisted, boolean persistenceFailed,
            boolean notScraped, boolean noScrapeError, boolean shouldRescrapAll) {
        if (nfoPersisted || persistenceFailed) {
            return false;
        }
        return (notScraped && noScrapeError) || (shouldRescrapAll && noScrapeError);
    }

    static boolean hasNoUsableNfoPoster(BaseTags tags) {
        boolean noTagPoster = (tags.getPosters() == null || tags.getPosters().isEmpty())
                && tags.getDefaultPoster() == null;
        if (!(tags instanceof EpisodeTags)) {
            return noTagPoster;
        }
        EpisodeTags episodeTags = (EpisodeTags) tags;
        ShowTags showTags = episodeTags.getShowTags();
        boolean noShowPoster = showTags == null || showTags.getPosters() == null
                || showTags.getPosters().isEmpty();
        return noTagPoster && noShowPoster;
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
            case PARAM_PERSISTENCE_RETRY:
                return WHERE_BASE;
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
