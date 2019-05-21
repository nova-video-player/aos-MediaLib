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

import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Log;

import com.archos.environment.ArchosUtils;
import com.archos.mediacenter.utils.trakt.TraktService;
import com.archos.mediaprovider.DeleteFileCallback;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.preprocess.SearchInfo;
import com.archos.mediascraper.preprocess.SearchPreprocessor;
import com.archos.mediascraper.xml.MovieScraper2;
import com.archos.mediascraper.xml.ShowScraper2;
import com.archos.mediascraper.xml.ShowScraper2TVDB2;

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

    static boolean sIsScraping = false;
    static int sNumberOfFilesRemainingToProcess = 0;
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
    private boolean restartOnNextRound;
    private AutoScraperBinder mBinder;
    private Thread mExportingThread;

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
        context.startService(new Intent(context, AutoScrapeService.class));
    }

    // Used by system. Don't call
    public AutoScrapeService() {
        if(DBG) Log.d(TAG, "AutoScrapeService() "+this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if(DBG) Log.d(TAG, "onCreate() "+this);
        mBinder = new AutoScraperBinder();
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        if(DBG) Log.d(TAG, "onStart() " + this);
        if(intent!=null){
            if(intent.getAction()!=null&&intent.getAction().equals(EXPORT_EVERYTHING))
                startExporting();
            else startScraping(intent.getBooleanExtra(RESCAN_EVERYTHING, false),intent.getBooleanExtra(RESCAN_ONLY_DESC_NOT_FOUND, false));

        }
        else
            startScraping(false,false);
    }

    protected void startExporting() {
        if (DBG)
            Log.d(TAG, "startExporting " + String.valueOf(mExportingThread == null || !mExportingThread.isAlive()));
        if (mExportingThread == null || !mExportingThread.isAlive()) {
            mExportingThread = new Thread() {

                public void run() {
                    Cursor cursor = getFileListCursor(PARAM_SCRAPED);
                    if(DBG) Log.d(TAG, "starting thread " + cursor.getCount());
                    NfoWriter.ExportContext exportContext = new NfoWriter.ExportContext();

                    if (cursor.getCount() > 0) {
                        cursor.moveToFirst();
                        do {
                            Log.d(TAG, "cursor.getCount() "+cursor.getCount());
                            Uri fileUri = Uri.parse(cursor.getString(cursor.getColumnIndex(VideoStore.MediaColumns.DATA)));
                            long movieID = cursor.getLong(cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_MOVIE_ID));
                            long episodeID = cursor.getLong(cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_EPISODE_ID));
                            final int scraperType = cursor.getInt(cursor.getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE));
                            BaseTags baseTags=null;
                            Log.d(TAG, movieID+ " fileUri "+fileUri);
                            if (scraperType == BaseTags.TV_SHOW) {
                                baseTags= TagsFactory.buildEpisodeTags(AutoScrapeService.this, episodeID);

                            } else if (scraperType==BaseTags.MOVIE) {
                                baseTags= TagsFactory.buildMovieTags(AutoScrapeService.this, movieID);

                            }
                            if(baseTags==null)
                                continue;
                            Log.d(TAG, "Base tag created, exporting"+fileUri);

                            if (exportContext != null) {
                                if (fileUri != null) {
                                    try {
                                            NfoWriter.export(fileUri, baseTags, exportContext);
                                    } catch (IOException e) {
                                        Log.w(TAG, e);
                                    }
                                }

                            }
                        } while (cursor.moveToNext()
                                &&PreferenceManager.getDefaultSharedPreferences(AutoScrapeService.this).getBoolean(AutoScrapeService.KEY_ENABLE_AUTO_SCRAP, true));
                        sIsScraping = false;

                    }
                    cursor.close();

                }
            };
            mExportingThread.start();
        }
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        if(DBG) Log.d(TAG, "onDestroy() " + this);
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
                if (PreferenceManager.getDefaultSharedPreferences(appContext).getBoolean(KEY_ENABLE_AUTO_SCRAP, true)) {
                    AutoScrapeService.startService(appContext);
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
        if(DBG)  Log.d(TAG, "startScraping " + String.valueOf(mThread==null || !mThread.isAlive()) );
        if(mThread==null || !mThread.isAlive()) {
            mThread = new Thread() {

                public int mNetworkOrScrapErrors; //when errors equals to number of files to scrap, stop looping.
                public void run() {
                    boolean shouldRescrapAll = rescrapAlreadySearched;
                    if(DBG)  Log.d(TAG, "startThread " + String.valueOf(mThread==null || !mThread.isAlive()) );
                    do{

                        mNetworkOrScrapErrors = 0;
                        restartOnNextRound = false;

                        Cursor cursor = getFileListCursor(shouldRescrapAll&&onlyNotFound ?PARAM_SCRAPED_NOT_FOUND:shouldRescrapAll?PARAM_ALL:PARAM_NOT_SCRAPED);
                        if(DBG) Log.d(TAG, "starting thread " + cursor.getCount());
                        NfoWriter.ExportContext exportContext = null;
                        if (NfoWriter.isNfoAutoExportEnabled(AutoScrapeService.this))
                            exportContext = new NfoWriter.ExportContext();

                        sNumberOfFilesRemainingToProcess = cursor.getCount();

                        if (cursor.getCount() > 0) {
                            restartOnNextRound = true;
                            cursor.moveToFirst();
                            sIsScraping = true;
                            do {
                                if(!ArchosUtils.isLocalNetworkConnected(AutoScrapeService.this)&&cursor.getCount()>10||!ArchosUtils.isNetworkConnected(AutoScrapeService.this)) {//if deconnected while scraping
                                    cursor.close();
                                    sNumberOfFilesRemainingToProcess = 0;
                                    return;
                                }
                                String title = cursor.getString(cursor.getColumnIndex(VideoStore.MediaColumns.TITLE));

                                Uri fileUri = Uri.parse(cursor.getString(cursor.getColumnIndex(VideoStore.MediaColumns.DATA)));
                                Uri scrapUri = title != null && !title.isEmpty() ? Uri.parse("/" + title + ".mp4") : fileUri;
                                long ID = cursor.getLong(cursor.getColumnIndex(BaseColumns._ID));
                                // for now there is no error and file is not scraped
                                boolean notScraped = true;
                                boolean noScrapeError = true;
                                if(DBG) Log.d(TAG, "fileUri "+fileUri);
                                if (NfoParser.isNetworkNfoParseEnabled(AutoScrapeService.this)) {

                                    if(DBG) Log.d(TAG, "NFO enabled");

                                    BaseTags tags = NfoParser.getTagForFile(fileUri, AutoScrapeService.this);
                                    if (tags != null) {
                                        if(DBG) Log.d(TAG, "found NFO");
                                        /*
                                        if poster url are in nfo or in folder, download is automatic
                                        if no poster available, try to scrap with good title,
                                        */
                                        if (ID != -1) {
                                            //ugly but necessary to avoid poster delete when replacing tag
                                            if(tags.getDefaultPoster()!=null){
                                                DeleteFileCallback.DO_NOT_DELETE.add(tags.getDefaultPoster().getLargeFile()); }
                                            if(tags instanceof EpisodeTags) {
                                                if (((EpisodeTags) tags).getEpisodePicture() != null) {
                                                    DeleteFileCallback.DO_NOT_DELETE.add(((EpisodeTags) tags).getEpisodePicture().getLargeFile());
                                                }
                                                if (((EpisodeTags) tags).getShowTags() != null && ((EpisodeTags) tags).getShowTags().getDefaultPoster() != null) {
                                                    DeleteFileCallback.DO_NOT_DELETE.add(((EpisodeTags) tags).getShowTags().getDefaultPoster().getLargeFile());
                                                }
                                            }
                                            tags.save(AutoScrapeService.this, ID);
                                            DeleteFileCallback.DO_NOT_DELETE.clear();
                                            TraktService.onNewVideo(AutoScrapeService.this);
                                        }
                                        //found NFO thus still no error but scraped
                                        notScraped = false;
                                        noScrapeError = true;
                                        if (tags.getPosters() != null&&DBG) {
                                            Log.d(TAG, "posters : " + tags.getPosters().size());
                                        }
                                        else if(tags.getPosters() == null&&tags.getDefaultPoster()==null&&
                                                (!(tags instanceof EpisodeTags)||((EpisodeTags)tags).getShowTags().getPosters()==null)){//special case for episodes : check show
                                            if (tags.getTitle() != null && !tags.getTitle().isEmpty()) { //if a title is specified in nfo, use it to scrap file
                                                scrapUri = Uri.parse("/" + tags.getTitle() + ".mp4");
                                                if(DBG)
                                                Log.d(TAG, "no posters using title " + tags.getTitle());
                                            }
                                            if(DBG) Log.d(TAG, "no posters ");
                                            //poster not found thus not scraped and no error
                                            notScraped = true;
                                            noScrapeError = true;
                                        }
                                    }
                                }
                                if (notScraped&&noScrapeError) { //look for online details
                                    ScrapeDetailResult result = null;
                                    boolean searchOnline = !shouldRescrapAll;
                                    if(shouldRescrapAll){
                                        Log.d(TAG,"rescraping");
                                        long videoID = cursor.getLong(cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_VIDEO_ONLINE_ID));
                                        final int scraperType = cursor.getInt(cursor.getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE));

                                        if (scraperType == BaseTags.TV_SHOW) {
                                            Log.d(TAG,"rescraping episode "+videoID);
                                            SearchResult searchResult = new SearchResult(0,title, (int) videoID);
                                            searchResult.setFile(fileUri);
                                            searchResult.setScraper(new ShowScraper2(AutoScrapeService.this));
                                            result = ShowScraper2.getDetails(new SearchResult(0,title, (int) videoID), null);

                                        } else if (scraperType==BaseTags.MOVIE) {
                                            Log.d(TAG,"rescraping movie "+videoID);
                                            SearchResult searchResult = new SearchResult(0,title, (int) videoID);
                                            searchResult.setFile(fileUri);
                                            searchResult.setScraper(new MovieScraper2(AutoScrapeService.this));
                                            result = MovieScraper2.getDetails(searchResult, null);
                                        }
                                        else searchOnline = true;

                                    }
                                    if(searchOnline) {
                                        Log.d(TAG,"searching online "+title);
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
                                        result.tag.save(AutoScrapeService.this, ID);
                                        DeleteFileCallback.DO_NOT_DELETE.clear();
                                        // result exists thus scraped and no error for now
                                        notScraped = false;
                                        noScrapeError = true;
                                        if (result.tag.getTitle() != null)
                                            Log.d(TAG, "info " + result.tag.getTitle());

                                        TraktService.onNewVideo(AutoScrapeService.this);
                                        if (exportContext != null) {
                                            // also auto-export all the data

                                            if (fileUri != null) {
                                                try {
                                                    Log.d(TAG, "exporting NFO");
                                                    NfoWriter.export(fileUri, result.tag, exportContext);
                                                } catch (IOException e) {
                                                    Log.w(TAG, e);
                                                }
                                            }
                                        }
                                    } else if (result!=null){
                                        //not scraped, check for errors
                                        notScraped = true;
                                        noScrapeError = result.status != ScrapeStatus.ERROR && result.status != ScrapeStatus.ERROR_NETWORK && result.status != ScrapeStatus.ERROR_NO_NETWORK;
                                    }
                                }

                                if (notScraped&&noScrapeError&&!shouldRescrapAll) { //in case of network error, don't go there, and don't save in case we are rescraping already scraped videos
                                    // Failed => set the scraper fields to -1 so that we will be able
                                    // to skip this file when launching the automated process again
                                    if (DBG) Log.d(TAG,"file " + fileUri + " not scraped without error -> mark it as not to be scraped again");
                                    ContentValues cv = new ContentValues(2);
                                    cv.put(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID, String.valueOf(-1));
                                    cv.put(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE, String.valueOf(-1));
                                    getContentResolver().update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, cv, BaseColumns._ID + "=?", new String[]{Long.toString(ID)});
                                }
                                else if(!noScrapeError) { // condition is scrapedOrError
                                    if (DBG) Log.d(TAG,"file " + fileUri + " scraped but with error -> increase mNetworkOrScrapErrors");
                                    mNetworkOrScrapErrors++;
                                }
                                sNumberOfFilesRemainingToProcess--;
                                if (DBG) Log.d(TAG,"remaining=" + sNumberOfFilesRemainingToProcess + ", mNetworkOrScrapErrors=" + mNetworkOrScrapErrors);

                            } while (cursor.moveToNext()
                                    &&isEnable(AutoScrapeService.this));
                            sIsScraping = false;
                            if(cursor.getCount() == mNetworkOrScrapErrors) { //when as many errors, we assume we don't have the internet or that the scraper returns an error, do not loop
                                restartOnNextRound = false;
                                if(DBG) Log.d(TAG, "no internet or scraper errors, stop iterating");
                            }
                        }
                        cursor.close();
                        shouldRescrapAll = false; //to avoid rescraping on next round
                    } while(restartOnNextRound
                            &&PreferenceManager.getDefaultSharedPreferences(AutoScrapeService.this).getBoolean(AutoScrapeService.KEY_ENABLE_AUTO_SCRAP, true)); //if we had something to do, we look for new videos
                    AutoScrapeService.this.stopSelf();
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

    private Cursor getFileListCursor(int scrapStatusParam) {
        // Look for all the videos not yet processed and not located in the Camera folder
        final String cameraPath =  Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath() + "/Camera";
        String[] selectionArgs = new String[]{ cameraPath + "/%" };
        String where  = null;
        switch(scrapStatusParam){
            case PARAM_NOT_SCRAPED:
                where=WHERE_NOT_SCRAPED;
                break;
            case PARAM_SCRAPED:
                where=WHERE_SCRAPED;
                break;
            case  PARAM_ALL:
                where = WHERE_SCRAPED_ALL;
                break;
            case  PARAM_SCRAPED_NOT_FOUND:
                where = WHERE_SCRAPED_NOT_FOUND;
                break;
            default:
                where = WHERE_BASE;
                break;
        }
        return getContentResolver().query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, SCRAPER_ACTIVITY_COLS, where, selectionArgs, null);
    }
}
