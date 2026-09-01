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

package com.archos.mediacenter.utils.videodb;

import androidx.loader.app.LoaderManager;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.BaseColumns;
import android.provider.MediaStore;

import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.filecoreextension.UriUtils;
import com.archos.mediaprovider.video.LoaderUtils;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediaprovider.video.VideoStore.MediaColumns;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.MovieTags;
import com.archos.mediascraper.NfoParser;
import com.archos.mediascraper.ScrapeDetailResult;
import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.Scraper;
import com.archos.mediascraper.preprocess.SearchInfo;
import com.archos.mediascraper.preprocess.SearchPreprocessor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IndexHelper implements LoaderManager.LoaderCallbacks<Cursor>, Loader.OnLoadCompleteListener<Cursor> {

    private static final Logger log = LoggerFactory.getLogger(IndexHelper.class);
    // Preserve write order across player lifecycle, metadata, network and Trakt updates.
    private static final ExecutorService VIDEO_INFO_WRITER = Executors.newSingleThreadExecutor();
    private final Context mContext;
    private final LoaderManager mLoaderManager;
    private final int mLoaderManagerId;
    private Listener mListener = null;
    private boolean mAutoScrape = false;

    private Uri mUri = null;
    private long mVideoId = -1;
    private String mTitle = null;
    private boolean mWaitRemote;
    private VideoDbInfo mLocalVideoInfo = null;
    private VideoDbInfo mRemoteVideoInfo = null;

    private static final Map<Uri, ScraperTask> sScraperTasks = new HashMap<Uri, ScraperTask>();
    private boolean mHasRetrieveRemote;
    private XmlObserver mRemoteXmlObserver;
    private Loader<Cursor> mCursorLoader;

    public interface Listener {
        void onVideoDb(VideoDbInfo info, VideoDbInfo remoteInfo);
        void onScraped(ScrapeDetailResult result);
    }

    public static class ScraperTask {
        private final Context mContext;
        private final Uri mFile;
        private final long mVideoId;
        private volatile Listener mListener;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final Handler handler = new Handler(Looper.getMainLooper());
        volatile boolean isCancelled = false;

        public interface Listener {
            void onScraperTaskResult(ScrapeDetailResult result);
        }

        public ScraperTask(Context context, Uri file, String title, long id) {
            mContext = context;
            mFile = title != null ? Uri.parse("/"+title+".mp4") : file;
            mVideoId = id;
        }

        public void setListener(Listener listener) {
            mListener = listener;
        }

        public void abort() {
            sScraperTasks.remove(mFile);
            isCancelled = true;
            executor.shutdownNow();
            mListener = null;
        }

        ScrapeDetailResult doWork() {
            BaseTags tags = NfoParser.getTagForFile(mFile, mContext);
            if (tags != null) {
                if (mVideoId != -1)
                    tags.save(mContext, mVideoId);
                return new ScrapeDetailResult(tags, tags instanceof MovieTags, null, ScrapeStatus.OKAY, null);
            }
            if (isCancelled) return null;
            SearchInfo searchInfo = SearchPreprocessor.instance().parseFileBased(mFile, mFile);
            if (isCancelled) return null;
            Scraper scraper = new Scraper(mContext);
            if (isCancelled) return null;
            ScrapeDetailResult result = scraper.getAutoDetails(searchInfo);
            if (isCancelled) return null;
            return result;
        }

        public void execute() {
            if (mListener != null)
                sScraperTasks.put(mFile, this);
            executor.execute(() -> {
                ScrapeDetailResult result = null;
                try {
                    if (isCancelled || Thread.currentThread().isInterrupted()) return;
                    result = doWork();
                } catch (Exception e) {
                    log.error("ScraperTask failed", e);
                } finally {
                    executor.shutdown();
                }
                if (isCancelled) return;
                final ScrapeDetailResult finalResult = result;
                final Listener listener = mListener;
                handler.post(() -> {
                    if (isCancelled) return;
                    if (listener != null) {
                        sScraperTasks.remove(mFile);
                        listener.onScraperTaskResult(finalResult);
                    }
                });
            });
        }
    }

    private class XmlObserver implements XmlDb.ParseListener {
        private final Uri mLocation;

        public XmlObserver(Uri location) {
            mLocation = location;
        }

        @Override
        public void onParseFail(XmlDb.ParseResult parseResult) {
            XmlDb.getInstance().removeParseListener(this);
        }

        @Override
        public void onParseOk(XmlDb.ParseResult result) {
            XmlDb xmlDb = XmlDb.getInstance();
            VideoDbInfo videoInfo = null;
            if (result.success)
                videoInfo = xmlDb.getEntry(mLocation);
            onVideoDbInfo(videoInfo, !FileUtils.isLocal(mLocation));
            xmlDb.removeParseListener(this);
            mRemoteXmlObserver = null;
        }
    }
    public IndexHelper(Context context, LoaderManager loaderManager, int loaderManagerId) {
        mContext = context.getApplicationContext();
        mLoaderManager = loaderManager;
        mLoaderManagerId = loaderManagerId;
    }

    private void reset() {
        mCursorLoader = null;
        mRemoteVideoInfo=null;
        mLocalVideoInfo = null;
        mUri = null;
        mHasRetrieveRemote = false;
        mVideoId = -1;
        mListener = null;
        mAutoScrape = false;
        if(mRemoteXmlObserver!=null) {
            XmlDb.getInstance().removeParseListener(mRemoteXmlObserver);
            mRemoteXmlObserver = null;
        }
        if (mLoaderManager != null && mLoaderManagerId != -1)
            mLoaderManager.destroyLoader(mLoaderManagerId);
    }
    public void abort() {
        if(mCursorLoader!=null) {
            mCursorLoader.abandon();
            mCursorLoader.unregisterListener(this);
        }
        if (mUri != null) {
            ScraperTask task = sScraperTasks.get(mUri);
            if (task != null) {
                task.abort();
            }
        }
        reset();
    }

    private void setupArgs(Uri uri, long videoId, String title) {
        reset();

        if ("content".equals(uri.getScheme())) {
            String uriPath = uri.getPath();
            // Data contains VideoStore.Video.Media.EXTERNAL_CONTENT_URI + videoId
            if (uriPath.startsWith(VideoStore.Video.Media.EXTERNAL_CONTENT_URI.getPath())
                    || uriPath.startsWith(MediaStore.Video.Media.EXTERNAL_CONTENT_URI.getPath())) {
                String idString = FileUtils.getName(uri);
                try {
                    mVideoId = Long.parseLong(idString);
                } catch (NumberFormatException e) {
                    mVideoId = -1;
                }
            }
        } else {
            mVideoId = videoId;
        }
        mUri = uri;
        mTitle = title;
    }
    public static boolean canBeIndexed(Uri uri){
        //TODO : do not index upnp upnptotest
        return true;
    }
    /**
     * request videoDb from Uri(content://, file://, smb://...) or videoID
     */
    public void requestVideoDb(Uri uri, long videoId, String title, Listener listener, boolean autoScrape, boolean remote) {
        abort();
        if (listener == null)
            return;

        setupArgs(uri, videoId, title);
        mListener = listener;
        mHasRetrieveRemote = false;
        mAutoScrape = autoScrape;
        mWaitRemote = remote;
        if (mVideoId != -1 || (mUri != null && canBeIndexed(mUri))) {
            if(mLoaderManager!=null)
                mLoaderManager.initLoader(mLoaderManagerId, null, this);
            else {
                mCursorLoader = onCreateLoader(mLoaderManagerId, null);
                mCursorLoader.startLoading();
            }
        }
    }

    public VideoDbInfo getVideoDb(Uri uri, long videoId, String title) {
        setupArgs(uri, videoId, title);

        ContentResolver cr = mContext.getContentResolver();
        mLocalVideoInfo = mVideoId == -1 ? VideoDbInfo.fromUri(cr, mUri) : VideoDbInfo.fromId(cr, mVideoId);

        return mLocalVideoInfo;
    }


    private void onVideoDbInfo(VideoDbInfo videoInfo, boolean isRemote) {
        if (videoInfo == null) {
            videoInfo = new VideoDbInfo(mUri);
        }

        if (isRemote) {
            mRemoteVideoInfo = videoInfo;
        }
        else {
            mLocalVideoInfo = videoInfo;
            //unregister listener not to be called again
            if(mCursorLoader!=null){
                mCursorLoader.unregisterListener(this);
                mCursorLoader = null;
            }
        }
        if (mWaitRemote && (mRemoteVideoInfo == null || mLocalVideoInfo == null)&&
                !FileUtils.isLocal(videoInfo.uri)
                &&UriUtils.isCompatibleWithRemoteDB(videoInfo.uri)) { //if we haven't remote info yet (we can check this only when we have remote uri
            //if we haven't launched remote xml parsing
            if(!mHasRetrieveRemote){
                mHasRetrieveRemote = true;
                XmlDb xmlDb = XmlDb.getInstance();
                mRemoteXmlObserver = new XmlObserver(videoInfo.uri);
                xmlDb.addParseListener(mRemoteXmlObserver);
                xmlDb.parseXmlLocation(videoInfo.uri);
            }
            return;
        }
        mUri = mLocalVideoInfo.uri;
        mVideoId = mLocalVideoInfo.id;
        if (mRemoteVideoInfo != null)
            mRemoteVideoInfo.id = mVideoId;
        if (mAutoScrape && !mLocalVideoInfo.isScraped&& UriUtils.isIndexable(mUri))
            requestScraping();
        if (mListener != null) {
            if (log.isDebugEnabled()) log.debug("onVideoDbInfo {} {}", mLocalVideoInfo, mRemoteVideoInfo);
            mListener.onVideoDb(mLocalVideoInfo, mRemoteVideoInfo);
        }
    }

    private void requestScraping() {
        if (mUri == null)
            return;

        final ScraperTask.Listener listener = new ScraperTask.Listener() {
            @Override
            public void onScraperTaskResult(ScrapeDetailResult result) {
                if (mListener != null)
                    mListener.onScraped(result);
            }
        };

        ScraperTask task = sScraperTasks.get(mUri);
        if (task == null) {
            task = new ScraperTask(mContext, mUri, mTitle, mVideoId);
            task.setListener(listener);
            task.execute();
        } else {
            // already in progress
            task.setListener(listener);
        }
    }

    public ScrapeDetailResult getScraping(Uri uri, long videoId, String title) {
        setupArgs(uri, videoId, title);
        if (mUri == null)
            return null;
        ScraperTask task = new ScraperTask(mContext, mUri, mTitle, mVideoId);
        ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            return exec.submit(task::doWork).get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("getScraping failed", e);
            return null;
        } finally {
            exec.shutdown();
        }
    }


    public Loader<Cursor> onCreateLoader(int loaderID, Bundle bundle) {
        if (loaderID == mLoaderManagerId && (mUri != null || mVideoId != -1)) {
            String selection = (mVideoId != -1 ? BaseColumns._ID : MediaColumns.DATA) + "=?";
            if(LoaderUtils.mustHideUserHiddenObjects())
                selection += " AND "+LoaderUtils.HIDE_USER_HIDDEN_FILTER;
            CursorLoader cursorLoader =  new CursorLoader(
                    mContext,
                    VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
                    VideoDbInfo.COLUMNS,selection
                     ,
                    new String [] {(mVideoId != -1 ? String.valueOf(mVideoId) : mUri.toString())},
                    null);
            if(mLoaderManager==null)
                cursorLoader.registerListener(loaderID, this);
            return cursorLoader;
        }
        return null;
    }


    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        VideoDbInfo videoInfo = null;
        if (loader.getId() == mLoaderManagerId) {
            videoInfo = VideoDbInfo.fromCursor(cursor, true);
            onVideoDbInfo(videoInfo, false); // NOTE: videoInfo is null here if the video is not indexed
            if(mLoaderManager!=null)
                mLoaderManager.destroyLoader(mLoaderManagerId);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        if (log.isDebugEnabled()) log.debug("onLoaderReset");
    }

    @Override
    public void onLoadComplete(Loader<Cursor> loader, Cursor cursor) {
        try {
            onLoadFinished(loader,cursor);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public void writeVideoInfo(VideoDbInfo videoInfo, boolean exportDb) {
        if (log.isDebugEnabled()) log.debug("writeVideoInfo {}", exportDb);
        if (videoInfo == null) return;
        // Callers share and continue mutating VideoDbInfo on the main thread. Snapshot it before
        // crossing the asynchronous boundary so each queued write represents one coherent state.
        final VideoDbInfo snapshot = new VideoDbInfo(videoInfo);
        VIDEO_INFO_WRITER.execute(() -> {
            try {
                if (log.isDebugEnabled()) log.debug("position: {} - id: {}", snapshot.resume, snapshot.id);
                if (snapshot.id != -1) {
                    int playerParams = VideoStore.paramsFromTracks(snapshot.audioTrack, snapshot.subtitleTrack);
                    final String where = VideoStore.Video.VideoColumns._ID + " = " + snapshot.id;
                    ContentResolver resolver = mContext.getContentResolver();
                    ContentValues values = new ContentValues(8);
                    values.put(VideoStore.Video.VideoColumns.ARCHOS_BOOKMARK, snapshot.bookmark);
                    values.put(VideoStore.Video.VideoColumns.BOOKMARK, snapshot.resume);
                    values.put(VideoStore.Video.VideoColumns.DURATION, snapshot.duration);
                    values.put(VideoStore.Video.VideoColumns.ARCHOS_PLAYER_PARAMS, playerParams);
                    values.put(VideoStore.Video.VideoColumns.ARCHOS_PLAYER_SUBTITLE_DELAY, snapshot.subtitleDelay);
                    values.put(VideoStore.Video.VideoColumns.ARCHOS_PLAYER_SUBTITLE_RATIO, snapshot.subtitleRatio);
                    values.put(VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED, snapshot.lastTimePlayed);
                    values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_RESUME, snapshot.traktResume);
                    if (snapshot.subtitleLanguage != null && !snapshot.subtitleLanguage.isEmpty())
                        values.put(VideoStore.Video.VideoColumns.ARCHOS_SUBTITLE_LANGUAGE, snapshot.subtitleLanguage);
                    else
                        values.putNull(VideoStore.Video.VideoColumns.ARCHOS_SUBTITLE_LANGUAGE);
                    resolver.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, values, where, null);
                }
                if (log.isDebugEnabled()) log.debug("mExportDb: {} - isLocal: {} isSlowRemote {}", exportDb, FileUtils.isLocal(snapshot.uri), FileUtils.isSlowRemote(snapshot.uri));
                if (exportDb && !FileUtils.isLocal(snapshot.uri) && snapshot.duration > 0
                        && UriUtils.isCompatibleWithRemoteDB(snapshot.uri)) {
                    XmlDb.getInstance().writeXmlRemote(snapshot);
                }
            } catch (Exception e) {
                log.error("writeVideoInfo failed", e);
            }
        });
    }
}
