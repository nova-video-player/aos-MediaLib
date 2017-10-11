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

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.util.Log;

import com.archos.filecorelibrary.MetaFile;
import com.archos.mediaprovider.video.VideoStore.MediaColumns;
import com.archos.mediaprovider.video.VideoStore.Video;
import com.archos.mediaprovider.video.VideoStore.Video.VideoColumns;
import com.archos.mediascraper.preprocess.SearchInfo;
import com.archos.mediascraper.preprocess.SearchPreprocessor;
import com.archos.mediascraper.xml.BaseScraper2;
import com.archos.mediascraper.xml.DefaultContentScraper;
import com.archos.mediascraper.xml.MovieScraper2;
import com.archos.mediascraper.xml.ShowScraper2;
import com.archos.mediascraper.xml.ShowScraper2TVDB2;

public class Scraper {
    private static final String TAG = "Scraper";
    private static final boolean DBG = false;

    public static final int ALL_MATCHES = -1;
    public static final String ITEM_TAGS = "tags";
    public static final String ITEM_SEARCHMOVIE = "searchmovie";

    public static final String ITEM_REQUEST_ALL_EPISODES = "WantAllEps";
    public static final String ITEM_RESULT_ALL_EPISODES = "allEpisodes";

    private final Context mContext;
    public Scraper(Context context) {
        if (DBG) Log.d(TAG, "CTOR");
        mContext = context;
        mShowScraper = new ShowScraper2(mContext);
        mMovieScraper = new MovieScraper2(mContext);
    }

    private final ShowScraper2 mShowScraper;
    private final MovieScraper2 mMovieScraper;

    private ScrapeSearchResult getMatches(SearchInfo info, int maxItems) {
        info = SearchPreprocessor.instance().reParseInfo(info);
        if (info.isTvShow()) {
            return mShowScraper.getMatches2(info, maxItems);
        }
        return mMovieScraper.getMatches2(info, maxItems);
    }

    /**
     * Returns all the matches found for the provided file
     * @param info get it from {@link SearchPreprocessor#parseFileBased(MetaFile)}
     */
    public ScrapeSearchResult getAllMatches(SearchInfo info) {
        return getMatches(info, ALL_MATCHES);
    }

    /**
     * Returns the maxItems most relevant matches for the provided file
     * @param info get it from {@link SearchPreprocessor#parseFileBased(MetaFile)}
     */
    public ScrapeSearchResult getBestMatches(SearchInfo info, int maxItems) {
        return getMatches(info, maxItems);
    }

    public static ScrapeDetailResult getDetails(SearchResult result, Bundle options) {
        return BaseScraper2.getDetails(result, options);
    }

    /**
     * Bind together the two functions getMatches and getDetails.
     * Used when we need to be in autopilot, such as when we want to scan an
     * entire directory.
     * @param info get it from {@link SearchPreprocessor#parseFileBased(MetaFile)}
     */
    public ScrapeDetailResult getAutoDetails(SearchInfo info) {
        if (info == null) {
            Log.e(TAG, "getAutoDetails - no SearchInfo");
            return new ScrapeDetailResult(null, true, null, ScrapeStatus.ERROR, null);
        }

        info = SearchPreprocessor.instance().reParseInfo(info);
        if (info.isTvShow()) {
            return mShowScraper.search(info);
        }
        return mMovieScraper.search(info);
    }


    protected ScrapeDetailResult getDefaultContentAutoDetails(String path) {
        Uri file = Uri.parse(path);
        DefaultContentScraper scraper = new DefaultContentScraper(mContext);
        return scraper.search(file, null);
    }

    private static class ScrapeFileRunnable implements Runnable {
        private static final String[] PROJECTION = new String[] {
            BaseColumns._ID,
            VideoColumns.ARCHOS_MEDIA_SCRAPER_ID
        };

        private static final String SELECTION = MediaColumns.DATA  + " LIKE ? AND "
                + VideoColumns.ARCHOS_MEDIA_SCRAPER_ID + " <= 0";

        private final Context mContext_;
        private final Scraper mHost;
        private final boolean mForceUpdate;

        public ScrapeFileRunnable(boolean forceUpdate, Context context, Scraper host) {
            mContext_ = context;
            mHost = host;
            mForceUpdate = forceUpdate;
        }

        @Override
        public void run() {
            ContentResolver cr = mContext_.getContentResolver();
            for (String file : MediaScraper.DEFAULT_CONTENT) {
                processFile (file, cr);
            }
        }

        private final void processFile (String file, ContentResolver cr) {
            String[] selectionArgs = new String[] { file };
            long fileId = -1;
            int scraperId = -1;
            Cursor c = cr.query(Video.Media.getContentUriForPath(file),
                    PROJECTION, SELECTION, selectionArgs, null);
            if (c != null) {
                if (c.moveToFirst()) {
                    fileId = c.getLong(0);
                    scraperId = c.getInt(1);
                }
                c.close();
            }
            ScrapeDetailResult result = null;
            if (fileId > 0) {
                if (mForceUpdate || scraperId == 0)
                    result = mHost.getDefaultContentAutoDetails(file);
            }
            if (result != null && result.isOkay()) {
                result.tag.save(mContext_, fileId);
            }
        }
    }

    public void setupDefaultContent(boolean forceUpdate) {
        // in background since it takes some time
        AsyncTask.execute(new ScrapeFileRunnable(forceUpdate, mContext, this));
    }

}
