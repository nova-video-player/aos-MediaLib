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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Log;

import com.archos.environment.ArchosUtils;
import com.archos.mediacenter.utils.AppState;
import com.archos.mediacenter.utils.trakt.TraktService;
import com.archos.medialib.R;
import com.archos.mediaprovider.DeleteFileCallback;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediaprovider.video.WrapperChannelManager;
import com.archos.mediascraper.preprocess.SearchInfo;
import com.archos.mediascraper.preprocess.SearchPreprocessor;
import com.archos.mediascraper.xml.MovieScraper2;
import com.archos.mediascraper.xml.ShowScraper2;

import java.io.IOException;

/**
 * Created by alexandre on 20/05/15.
 */
public class AutoScrapeService extends Service {
    public static final String EXPORT_EVERYTHING = "export_everything";
    public static final String RESCAN_EVERYTHING = "rescan_everything";
    public static final String RESCAN_ONLY_DESC_NOT_FOUND = "rescan_only_desc_not_found";
    private static final int PARAM_NOT_SCRAPED = 0;
    private static final int PARAM_SCRAPED = 1;
    private static final int PARAM_ALL = 2;
    private static final int PARAM_SCRAPED_NOT_FOUND = 3;
    private static String TAG = "AutoScrapeService";
    private static boolean DBG = false;

    // window size used to split queries to db
    private final static int WINDOW_SIZE = 2000;

    static boolean sIsScraping = false;
    static int sNumberOfFilesRemainingToProcess = 0;
    static int sNumberOfFilesScraped = 0;
    static int sNumberOfFilesNotScraped = 0;
    public static String KEY_ENABLE_AUTO_SCRAP ="enable_auto_scrap_key";
    private final static String[] SCRAPER_ACTIVITY_COLS = {
            // Columns needed by the activity
            BaseColumns._ID,
            VideoStore.MediaColumns.DATA,
            VideoStore.MediaColumns.TITLE,
            VideoStore.Video.VideoColumns.SCRAPER_MOVIE_ID,
            VideoStore.Video.VideoColumns.SCRAPER_EPISODE_ID,
            VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE,
            VideoStore.Video.VideoColumns.SCRAPER_VIDEO_ONLINE_ID
    };
    private Thread mThread;
    private boolean restartOnNextRound = false;
    private AutoScraperBinder mBinder;
    private Thread mExportingThread;
    private Handler mHandler;
    private static Context mContext;

    private static final int NOTIFICATION_ID = 4;
    private NotificationManager nm;
    private NotificationCompat.Builder nb;
    private static final String notifChannelId = "AutoScrapeService_id";
    private static final String notifChannelName = "AutoScrapeService";
    private static final String notifChannelDescr = "AutoScrapeService";

    /**
     * Ugly implementation based on a static variable, guessing that there is only one instance at a time (seems to be true...)
     * @return true if AutoScrape service is running
     */
    public static boolean isScraping() {

        return sIsScraping;
    }

    /**
     * Ugly implementation based on a static variable, guessing that there is only one instance at a time (seems to be true...)
     * @return the number of files that are currently in the queue for scraping
     */
    public static int getNumberOfFilesRemainingToProcess() {
        return sNumberOfFilesRemainingToProcess;
    }

    public static void startService(Context context) {
        if (DBG) Log.d(TAG, "startService in foreground");
        mContext = context;
        ContextCompat.startForegroundService(context, new Intent(context, AutoScrapeService.class));
    }

    public void stopService() {
        if (DBG) Log.d(TAG, "stopService: stopForeground only");
        sIsScraping = false;
        nm.cancel(NOTIFICATION_ID);
        stopForeground(true);
    }

    // Used by system. Don't call
    public AutoScrapeService() {
        if(DBG) Log.d(TAG, "AutoScrapeService() "+this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if(DBG) Log.d(TAG, "onCreate() "+this);

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
                .setSmallIcon(R.drawable.stat_notify_scraper)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setTicker(null).setOnlyAlertOnce(true).setOngoing(true).setAutoCancel(true);
        if(DBG) Log.d(TAG, "onCreate: startForeground");
        startForeground(NOTIFICATION_ID, nb.build());

        mBinder = new AutoScraperBinder();
        mHandler = new Handler();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        if(DBG) Log.d(TAG, "onStartCommand() " + this);
        if(DBG) Log.d(TAG, "onStartCommand: startForeground");
        startForeground(NOTIFICATION_ID, nb.build());
        if(intent!=null){
            if(intent.getAction()!=null&&intent.getAction().equals(EXPORT_EVERYTHING))
                startExporting();
            else
                startScraping(intent.getBooleanExtra(RESCAN_EVERYTHING, false),intent.getBooleanExtra(RESCAN_ONLY_DESC_NOT_FOUND, false));
        }
        else
            startScraping(false,false);
        return START_NOT_STICKY;
    }

    protected void startExporting() {
        if (DBG) Log.d(TAG, "startExporting " + String.valueOf(mExportingThread == null || !mExportingThread.isAlive()));
        nb.setContentTitle(getString(R.string.nfo_export_in_progress));
        if (mExportingThread == null || !mExportingThread.isAlive()) {
            mExportingThread = new Thread() {

                public void run() {
                    Cursor cursor = getFileListCursor(PARAM_SCRAPED, null);
                    final int numberOfRows = cursor.getCount();
                    int sTotalNumberOfFilesRemainingToProcess = numberOfRows;
                    cursor.close();
                    if(DBG) Log.d(TAG, "starting thread " + numberOfRows);

                    NfoWriter.ExportContext exportContext = new NfoWriter.ExportContext();

                    int index = 0;
                    int window = WINDOW_SIZE;
                    int count = 0;
                    do {
                        if (index + window > numberOfRows)
                            window = numberOfRows - index;
                        if (DBG) Log.d(TAG, "startExporting: new batch fetching cursor from index" + index + " over window " + window + " entries, " + (index + window) + "<=" + numberOfRows);
                        cursor = getFileListCursor(PARAM_SCRAPED, BaseColumns._ID + " ASC LIMIT " + index + "," + window);
                        if (DBG) Log.d(TAG, "startExporting: new batch cursor has size " + cursor.getCount());

                        sNumberOfFilesRemainingToProcess = window;

                        while (cursor.moveToNext()
                                && PreferenceManager.getDefaultSharedPreferences(AutoScrapeService.this).getBoolean(AutoScrapeService.KEY_ENABLE_AUTO_SCRAP, true)) {
                            if (sTotalNumberOfFilesRemainingToProcess > 0)
                                nm.notify(NOTIFICATION_ID, nb.setContentText(getString(R.string.remaining_videos_to_process) + " " + sTotalNumberOfFilesRemainingToProcess).build());
                            Uri fileUri = Uri.parse(cursor.getString(cursor.getColumnIndex(VideoStore.MediaColumns.DATA)));
                            long movieID = cursor.getLong(cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_MOVIE_ID));
                            long episodeID = cursor.getLong(cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_EPISODE_ID));
                            final int scraperType = cursor.getInt(cursor.getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE));
                            BaseTags baseTags = null;
                            if (DBG) Log.d(TAG, "startExporting: " + movieID + " fileUri " + fileUri);
                            if (scraperType == BaseTags.TV_SHOW) {
                                baseTags = TagsFactory.buildEpisodeTags(AutoScrapeService.this, episodeID);

                            } else if (scraperType == BaseTags.MOVIE) {
                                baseTags = TagsFactory.buildMovieTags(AutoScrapeService.this, movieID);

                            }
                            sNumberOfFilesRemainingToProcess--;
                            sTotalNumberOfFilesRemainingToProcess--;
                            if (baseTags == null)
                                continue;
                            if (DBG) Log.d(TAG, "startExporting: Base tag created, exporting" + fileUri);
                            if (exportContext != null && fileUri != null)
                                try {
                                    NfoWriter.export(fileUri, baseTags, exportContext);
                                } catch (IOException e) {
                                    Log.w(TAG, e);
                                }
                        }
                        index += window;
                        cursor.close();
                    } while (index < numberOfRows);
                    sIsScraping = false;
                    cursor.close();
                    if(DBG) Log.d(TAG, "startExporting: call stopService");
                    stopService();
                }
            };
            mExportingThread.start();
        }
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        if(DBG) Log.d(TAG, "onDestroy() " + this);
        stopService();
    }

    /**
     * Register content observer and start autoscrap if enabled
     * @param context
     */
    public static void registerObserver(final Context context) {
        final Context appContext = context.getApplicationContext();
        appContext.getContentResolver().registerContentObserver(VideoStore.ALL_CONTENT_URI, true, new ContentObserver(null) {
            @Override
            public void onChange(boolean selfChange) {
                if (PreferenceManager.getDefaultSharedPreferences(appContext).getBoolean(KEY_ENABLE_AUTO_SCRAP, true) && AppState.isForeGround()) {
                    // only look if there is something to scrape if not yet in scrape process
                    if (isScraping()) {
                        if (DBG) Log.d(TAG, "registerObserver: already scraping, not launching service!");
                        return;
                    }
                    // only launch AutoScrapeService if there is something not scraped to avoid notification popup
                    // Look for all the videos not yet processed and not located in the Camera folder
                    String[] selectionArgs = new String[]{ Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath() + "/Camera" + "/%" };
                    Cursor cursor = context.getContentResolver().query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, SCRAPER_ACTIVITY_COLS, WHERE_NOT_SCRAPED, selectionArgs, null);
                    final int cursorGetCount = cursor.getCount();
                    if (cursorGetCount > 0) {
                        if (DBG) Log.d(TAG, "registerObserver: onChange getting " + cursorGetCount + " videos not yet scraped, launching service.");
                        AutoScrapeService.startService(appContext);
                    } else {
                        if (DBG) Log.d(TAG, "registerObserver: onChange getting " + cursorGetCount + " videos not yet scraped -> not launching service!");
                    }
                    cursor.close();
                }
            }
        });
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
        if(DBG)  Log.d(TAG, "startScraping: " + String.valueOf(mThread==null || !mThread.isAlive()) );
        nb.setContentTitle(getString(R.string.scraping_in_progress));

        Intent notificationIntent = new Intent(Intent.ACTION_MAIN);
        notificationIntent.setClassName(this.getPackageName(), "com.archos.mediacenter.video.autoscraper.AutoScraperActivity");
        PendingIntent contentIntent = PendingIntent.getBroadcast(this, 0, notificationIntent, 0);
        nb.setContentIntent(contentIntent);

        if(mThread==null || !mThread.isAlive()) {
            mThread = new Thread() {

                public int mNetworkOrScrapErrors; //when errors equals to number of files to scrap, stop looping.
                boolean notScraped;
                boolean noScrapeError;

                public void run() {
                    sIsScraping = true;
                    boolean shouldRescrapAll = rescrapAlreadySearched;
                    if(DBG) Log.d(TAG, "startScraping: startThread " + String.valueOf(mThread==null || !mThread.isAlive()) );
                    if (DBG) {
                        if (shouldRescrapAll && onlyNotFound)
                            Log.d(TAG, "startScraping: go for scraped not found");
                        else if (shouldRescrapAll)
                            Log.d(TAG, "startScraping: go for scrape all");
                        else
                            Log.d(TAG, "startScraping: go for not scraped");
                    }
                    if (DBG) Log.d(TAG, "startScraping: isLocalNetworkConnected=" + ArchosUtils.isLocalNetworkConnected(AutoScrapeService.this) +
                            ", isNetworkConnected=" + ArchosUtils.isNetworkConnected(AutoScrapeService.this));
                    if (DBG) Log.d(TAG, "startScraping: is AutoScrapeService enabled? " + isEnable(AutoScrapeService.this));

                    do{
                        mNetworkOrScrapErrors = 0;
                        sNumberOfFilesScraped = 0;
                        sNumberOfFilesRemainingToProcess = 0;
                        sNumberOfFilesNotScraped = 0;
                        restartOnNextRound = false;

                        // find all videos not scraped yet looking at VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID
                        // and get the final count (it could change while scrape is in progress)
                        Cursor cursor = getFileListCursor(shouldRescrapAll&&onlyNotFound ?PARAM_SCRAPED_NOT_FOUND:shouldRescrapAll?PARAM_ALL:PARAM_NOT_SCRAPED, null);
                        int numberOfRows = cursor.getCount(); // total number of files to be processed
                        int sTotalNumberOfFilesRemainingToProcess = numberOfRows;
                        cursor.close();

                        NfoWriter.ExportContext exportContext = null;
                        if (NfoWriter.isNfoAutoExportEnabled(AutoScrapeService.this))
                            exportContext = new NfoWriter.ExportContext();

                        // now process the files to be scraped by batch of WINDOW_SIZE not to exceed the CursorWindow size limit and crash in case of large collection
                        // note that since the db is modified during the scrape process removing non scraped entries fetching WINDOW_SIZE from index 0 is the good strategy
                        int window = WINDOW_SIZE;
                        int numberOfRowsRemaining = numberOfRows;
                        do {
                            if (window > numberOfRowsRemaining)
                                window = numberOfRowsRemaining;
                            if (DBG) Log.d(TAG, "startScraping: new batch fetching cursor from index 0, window " + window + " entries <=" + numberOfRowsRemaining);
                            cursor = getFileListCursor(shouldRescrapAll&&onlyNotFound ?PARAM_SCRAPED_NOT_FOUND:shouldRescrapAll?PARAM_ALL:PARAM_NOT_SCRAPED, BaseColumns._ID + " ASC LIMIT " + window);
                            if (DBG) Log.d(TAG, "startScraping: new batch cursor has size " + cursor.getCount());

                            sNumberOfFilesRemainingToProcess = window;
                            restartOnNextRound = true;
                            while (cursor.moveToNext() && isEnable(AutoScrapeService.this)) {
                                // stop if disconnected while scraping
                                if(!ArchosUtils.isLocalNetworkConnected(AutoScrapeService.this)&&!ArchosUtils.isNetworkConnected(AutoScrapeService.this)) {
                                    cursor.close();
                                    sNumberOfFilesRemainingToProcess = 0;
                                    if(DBG) Log.d(TAG, "startScraping disconnected from network calling stopService");
                                    stopService();
                                    return;
                                }

                                String title = cursor.getString(cursor.getColumnIndex(VideoStore.MediaColumns.TITLE));
                                Uri fileUri = Uri.parse(cursor.getString(cursor.getColumnIndex(VideoStore.MediaColumns.DATA)));
                                Uri scrapUri = title != null && !title.isEmpty() ? Uri.parse("/" + title + ".mp4") : fileUri;
                                long ID = cursor.getLong(cursor.getColumnIndex(BaseColumns._ID));

                                // for now there is no error and file is not scraped
                                notScraped = true;
                                noScrapeError = true;
                                if(DBG) Log.d(TAG, "startScraping processing scrapUri " + scrapUri + ", with ID " + ID
                                        + ", number of remaining files to be processed: " + sTotalNumberOfFilesRemainingToProcess);
                                if (sTotalNumberOfFilesRemainingToProcess > 0)
                                    nm.notify(NOTIFICATION_ID, nb.setContentText(getString(R.string.remaining_videos_to_process) + " " + sTotalNumberOfFilesRemainingToProcess).build());

                                if (NfoParser.isNetworkNfoParseEnabled(AutoScrapeService.this)) {

                                    BaseTags tags = NfoParser.getTagForFile(fileUri, AutoScrapeService.this);
                                    if (tags != null) {
                                        if(DBG) Log.d(TAG, "startScraping: found NFO");
                                        // if poster url are in nfo or in folder, download is automatic
                                        // if no poster available, try to scrap with good title,
                                        if (ID != -1) {
                                            if(DBG) Log.d(TAG, "startScraping: NFO ID != -1 " + ID);
                                            // ugly but necessary to avoid poster delete when replacing tag
                                            if(tags.getDefaultPoster()!=null)
                                                DeleteFileCallback.DO_NOT_DELETE.add(tags.getDefaultPoster().getLargeFile());
                                            if(tags instanceof EpisodeTags) {
                                                if (((EpisodeTags) tags).getEpisodePicture() != null) {
                                                    DeleteFileCallback.DO_NOT_DELETE.add(((EpisodeTags) tags).getEpisodePicture().getLargeFile());
                                                }
                                                if (((EpisodeTags) tags).getShowTags() != null && ((EpisodeTags) tags).getShowTags().getDefaultPoster() != null) {
                                                    DeleteFileCallback.DO_NOT_DELETE.add(((EpisodeTags) tags).getShowTags().getDefaultPoster().getLargeFile());
                                                }
                                            }
                                            if(DBG) Log.d(TAG, "startScraping: NFO tags.save ID=" + ID);
                                            tags.save(AutoScrapeService.this, ID);
                                            DeleteFileCallback.DO_NOT_DELETE.clear();
                                            TraktService.onNewVideo(AutoScrapeService.this);
                                        } else {
                                            if(DBG) Log.d(TAG, "startScraping: oh oh NFO ID = -1 ");
                                        }
                                        //found NFO thus still no error but scraped
                                        notScraped = false;
                                        sNumberOfFilesScraped++;
                                        noScrapeError = true;
                                        if (tags.getPosters() != null&&DBG) Log.d(TAG, "startScraping: posters : " + tags.getPosters().size());
                                        else if(tags.getPosters() == null&&tags.getDefaultPoster()==null&&
                                                (!(tags instanceof EpisodeTags)||((EpisodeTags)tags).getShowTags().getPosters()==null)){//special case for episodes : check show
                                            if (tags.getTitle() != null && !tags.getTitle().isEmpty()) { //if a title is specified in nfo, use it to scrap file
                                                scrapUri = Uri.parse("/" + tags.getTitle() + ".mp4");
                                                if(DBG) Log.d(TAG, "startScraping: no posters using title " + tags.getTitle());
                                            }
                                            if(DBG) Log.d(TAG, "startScraping: no posters ");
                                            //poster not found thus not scraped and no error
                                            notScraped = true;
                                            noScrapeError = true;
                                        }
                                        if(DBG) Log.d(TAG, "startScraping: NFO found, notScaped " + notScraped + ", noScrapeError " + noScrapeError + " for " + fileUri);
                                    }
                                }
                                if (notScraped&&noScrapeError) { //look for online details
                                    if(DBG) Log.d(TAG, "startScraping: NFO NOT found");
                                    ScrapeDetailResult result = null;
                                    boolean searchOnline = !shouldRescrapAll;
                                    if(shouldRescrapAll){
                                        if (DBG) Log.d(TAG,"startScraping: rescraping all");
                                        long videoID = cursor.getLong(cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_VIDEO_ONLINE_ID));
                                        final int scraperType = cursor.getInt(cursor.getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE));

                                        if (scraperType == BaseTags.TV_SHOW) {
                                            if (DBG) Log.d(TAG,"startScraping: rescraping episode "+videoID);
                                            SearchResult searchResult = new SearchResult(0,title, (int) videoID);
                                            searchResult.setFile(fileUri);
                                            searchResult.setScraper(new ShowScraper2(AutoScrapeService.this));
                                            result = ShowScraper2.getDetails(new SearchResult(0,title, (int) videoID), null);

                                        } else if (scraperType==BaseTags.MOVIE) {
                                            if (DBG) Log.d(TAG,"startScraping: rescraping movie "+videoID);
                                            SearchResult searchResult = new SearchResult(0,title, (int) videoID);
                                            searchResult.setFile(fileUri);
                                            searchResult.setScraper(new MovieScraper2(AutoScrapeService.this));
                                            result = MovieScraper2.getDetails(searchResult, null);
                                        }
                                        else searchOnline = true;
                                    }
                                    if(searchOnline) {
                                        if (DBG) Log.d(TAG,"startScraping: searching online "+title);
                                        SearchInfo searchInfo = SearchPreprocessor.instance().parseFileBased(fileUri,scrapUri);
                                        Scraper scraper = new Scraper(AutoScrapeService.this);
                                        result = scraper.getAutoDetails(searchInfo);

                                    }

                                    if (result!=null&&result.tag != null && ID != -1) {
                                        result.tag.setVideoId(ID);
                                        //ugly but necessary to avoid poster delete when replacing tag
                                        if(result.tag.getDefaultPoster()!=null){
                                            DeleteFileCallback.DO_NOT_DELETE.add(result.tag.getDefaultPoster().getLargeFile());
                                        }
                                        if(result.tag instanceof EpisodeTags) {
                                            if (((EpisodeTags) result.tag).getEpisodePicture() != null) {
                                                DeleteFileCallback.DO_NOT_DELETE.add(((EpisodeTags) result.tag).getEpisodePicture().getLargeFile());
                                            }
                                            if (((EpisodeTags) result.tag).getShowTags() != null && ((EpisodeTags) result.tag).getShowTags().getDefaultPoster() != null) {
                                                DeleteFileCallback.DO_NOT_DELETE.add(((EpisodeTags) result.tag).getShowTags().getDefaultPoster().getLargeFile());
                                            }
                                        }
                                        if(DBG) Log.d(TAG, "startScraping: online result.tag.save ID=" + ID);

                                        result.tag.save(AutoScrapeService.this, ID);
                                        DeleteFileCallback.DO_NOT_DELETE.clear();
                                        // result exists thus scraped and no error for now
                                        notScraped = false;
                                        sNumberOfFilesScraped++;
                                        noScrapeError = true;
                                        if (result.tag.getTitle() != null)
                                            if (DBG) Log.d(TAG, "startScraping: info " + result.tag.getTitle());

                                        TraktService.onNewVideo(AutoScrapeService.this);
                                        if (exportContext != null) {
                                            // also auto-export all the data

                                            if (fileUri != null) {
                                                try {
                                                    if (DBG) Log.d(TAG, "startScraping: exporting NFO");
                                                    NfoWriter.export(fileUri, result.tag, exportContext);
                                                } catch (IOException e) {
                                                    Log.w(TAG, e);
                                                }
                                            }
                                            if(DBG) Log.d(TAG, "startScraping: online info, notScaped " + notScraped + ", noScrapeError " + noScrapeError + " for " + fileUri);
                                        }
                                    } else if (result!=null){
                                        //not scraped, check for errors
                                        // for tvshow if search returns ScrapeStatus.OKAY but in details it returns ScrapeStaus.ERROR_PARSER it is not counted as a scraping error
                                        // this allows the video to be marked as not to be rescraped
                                        notScraped = true;
                                        noScrapeError = result.status != ScrapeStatus.ERROR && result.status != ScrapeStatus.ERROR_NETWORK && result.status != ScrapeStatus.ERROR_NO_NETWORK;
                                        if (! noScrapeError) {
                                            if (DBG) Log.d(TAG, "startScraping: file " + fileUri + " scrape error");
                                        } else {
                                            sNumberOfFilesNotScraped++;
                                        }
                                        if (DBG) Log.d(TAG, "startScraping: file " + fileUri + " not scraped among " + sNumberOfFilesNotScraped);
                                    }
                                }

                                if (notScraped&&noScrapeError&&!shouldRescrapAll) { //in case of network error, don't go there, and don't save in case we are rescraping already scraped videos
                                    // Failed => set the scraper fields to -1 so that we will be able
                                    // to skip this file when launching the automated process again
                                    if (DBG) Log.d(TAG,"startScraping: file " + fileUri + " not scraped without error -> mark it as not to be scraped again");
                                    ContentValues cv = new ContentValues(2);
                                    cv.put(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID, String.valueOf(-1));
                                    cv.put(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE, String.valueOf(-1));
                                    getContentResolver().update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, cv, BaseColumns._ID + "=?", new String[]{Long.toString(ID)});
                                }
                                else if(!noScrapeError) { // condition is scrapedOrError
                                    if (DBG) Log.d(TAG,"startScraping: file " + fileUri + " scraped but with error -> increase mNetworkOrScrapErrors");
                                    mNetworkOrScrapErrors++;
                                }
                                sNumberOfFilesRemainingToProcess--;
                                sTotalNumberOfFilesRemainingToProcess--;
                                if (DBG) Log.d(TAG,"startScraping: #filesProcessed=" + sNumberOfFilesScraped + "/" + numberOfRows + "(" +
                                        + sTotalNumberOfFilesRemainingToProcess + ")" + ", #scrapOrNetworkErrors=" + mNetworkOrScrapErrors +
                                        ", #notScraped=" + sNumberOfFilesNotScraped + ", current batch #filesToProcessed=" + sNumberOfFilesRemainingToProcess + "/" + window);
                            }
                            cursor.close();
                            numberOfRowsRemaining -= window;
                        } while (numberOfRowsRemaining > 0);
                        if (numberOfRows == mNetworkOrScrapErrors) { //when as many errors, we assume we don't have the internet or that the scraper returns an error, do not loop
                            restartOnNextRound = false;
                            if(DBG) Log.d(TAG, "startScraping: no internet or scraper errors, stop iterating");
                        } else {
                            //do not restartOnNextRound if all files are processed i.e. notScraped and scraped, do it only if mNetworkOrScrapErrors
                            if (sNumberOfFilesScraped + sNumberOfFilesNotScraped >= numberOfRows) restartOnNextRound = false;
                            if(DBG) Log.d(TAG, "startScraping: numberOfRows != mNetworkOrScrapErrors, " + numberOfRows + "!=" + mNetworkOrScrapErrors +
                                    ", #Scraped=" + sNumberOfFilesScraped + ", #NotScraped=" + sNumberOfFilesNotScraped + ", restartOnNextRound =" + restartOnNextRound);
                        }
                        shouldRescrapAll = false; //to avoid rescraping on next round
                        // final check if while scanning there was no more files to scrape added
                        cursor = getFileListCursor(shouldRescrapAll&&onlyNotFound ?PARAM_SCRAPED_NOT_FOUND:shouldRescrapAll?PARAM_ALL:PARAM_NOT_SCRAPED, null);
                        if(cursor.getCount()>0) {
                            restartOnNextRound = true;
                            if (DBG) Log.d(TAG, "startScraping: new entries to scrape found most likely added during scrape process, restartOnNextRound");
                        }
                        cursor.close();
                    } while(restartOnNextRound
                            &&PreferenceManager.getDefaultSharedPreferences(AutoScrapeService.this).getBoolean(AutoScrapeService.KEY_ENABLE_AUTO_SCRAP, true)); //if we had something to do, we look for new videos
                    sIsScraping = false;
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            WrapperChannelManager.refreshChannels(AutoScrapeService.this);
                        }
                    });
                    if(DBG) Log.d(TAG, "startScraping: call stopService");
                    stopService();
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

    private Cursor getFileListCursor(int scrapStatusParam, String sortOrder) {
        // Look for all the videos not yet processed and not located in the Camera folder
        final String cameraPath =  Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath() + "/Camera";
        String[] selectionArgs = new String[]{ cameraPath + "/%" };
        String where  = null;
        switch(scrapStatusParam){
            case PARAM_NOT_SCRAPED:
                where = WHERE_NOT_SCRAPED;
                break;
            case PARAM_SCRAPED:
                where = WHERE_SCRAPED;
                break;
            case PARAM_ALL:
                where = WHERE_SCRAPED_ALL;
                break;
            case PARAM_SCRAPED_NOT_FOUND:
                where = WHERE_SCRAPED_NOT_FOUND;
                break;
            default:
                where = WHERE_BASE;
                break;
        }
        return getContentResolver().query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, SCRAPER_ACTIVITY_COLS, where, selectionArgs, sortOrder);
    }
}
