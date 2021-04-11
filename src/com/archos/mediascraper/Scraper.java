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

import android.content.Context;
import android.os.Bundle;
import android.util.Log;


import com.archos.mediascraper.preprocess.SearchInfo;
import com.archos.mediascraper.preprocess.SearchPreprocessor;
import com.archos.mediascraper.xml.BaseScraper2;
import com.archos.mediascraper.xml.MovieScraper3;
import com.archos.mediascraper.xml.ShowScraper4;

public class Scraper {
    private static final String TAG = Scraper.class.getSimpleName();
    private static final boolean DBG = false;

    public static final int ALL_MATCHES = -1;
    public static final String ITEM_TAGS = "tags";
    public static final String ITEM_SEARCHMOVIE = "searchmovie";

    public static final String ITEM_REQUEST_ALL_EPISODES = "WantAllEps";
    public static final String ITEM_RESULT_ALL_EPISODES = "allEpisodes";

    public static final String ITEM_REQUEST_BASIC_SHOW = "basicShow";
    public static final String ITEM_REQUEST_BASIC_VIDEO = "basicVideo";

    private final Context mContext;
    public Scraper(Context context) {
        if (DBG) Log.d(TAG, "CTOR");
        mContext = context;
        mShowScraper = new ShowScraper4(mContext);
        mMovieScraper = new MovieScraper3(mContext);
    }

    private final ShowScraper4 mShowScraper;
    private final MovieScraper3 mMovieScraper;

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

}
