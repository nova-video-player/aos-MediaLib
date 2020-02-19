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

package com.archos.mediascraper.thetvdb;

import android.os.Bundle;
import android.util.Log;
import android.util.Pair;

import com.archos.mediascraper.SearchResult;
import com.archos.mediascraper.ShowUtils;
import com.archos.mediascraper.preprocess.TvShowSearchInfo;
import com.archos.mediascraper.xml.ShowScraper3;
import com.uwetrottmann.thetvdb.entities.Series;
import com.uwetrottmann.thetvdb.entities.SeriesResultsResponse;

import org.apache.commons.text.similarity.LevenshteinDistance;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import retrofit2.Response;

public class SearchShowParser {

    private static final String TAG = SearchShowParser.class.getSimpleName();
    private static final boolean DBG = false;

    private final static int SERIES_NOT_PERMITTED_ID = 313081;

    private final static LevenshteinDistance levenshteinDistance = new LevenshteinDistance();

    public static List<SearchResult> getResult(Response<SeriesResultsResponse> response, TvShowSearchInfo searchInfo, String language, Integer maxItems, ShowScraper3 showScraper) {
        List<SearchResult> results = new LinkedList<>();
        List<SearchResult> resultsNumericSlug = new LinkedList<>();
        List<SearchResult> resultsNoBanner = new LinkedList<>();
        List<Pair<SearchResult,Integer>> resultsProbable = new LinkedList<>();
        List<SearchResult> resultsProbableSorted = new LinkedList<>();
        Boolean isDecisionTaken = false;
        for (Series series : response.body().data) {
            if (series.id != SERIES_NOT_PERMITTED_ID) {
                Bundle extra = new Bundle();
                extra.putString(ShowUtils.EPNUM, String.valueOf(searchInfo.getEpisode()));
                extra.putString(ShowUtils.SEASON, String.valueOf(searchInfo.getSeason()));
                SearchResult result = new SearchResult();
                result.setId(series.id);
                result.setLanguage(language);
                result.setTitle(series.seriesName);
                result.setScraper(showScraper);
                result.setFile(searchInfo.getFile());
                result.setExtra(extra);
                // Put in lower priority any entry that has no TV show banned i.e. .*missing/movie.jpg as banner
                isDecisionTaken = false;
                if (series.banner != null) {
                    if (series.banner.endsWith("missing/series.jpg") || series.banner.endsWith("missing/movie.jpg")) {
                        if (DBG)
                            Log.d(TAG, "getMatches2: set aside " + series.seriesName + " because banner missing i.e. banner=" + series.banner);
                        resultsNoBanner.add(result);
                        isDecisionTaken = true;
                    }
                } else if (series.image != null) {
                    if (series.image.endsWith("missing/series.jpg") || series.image.endsWith("missing/movie.jpg")) {
                        if (DBG)
                            Log.d(TAG, "getMatches2: set aside " + series.seriesName + " because image missing i.e. image=" + series.image);
                        resultsNoBanner.add(result);
                        isDecisionTaken = true;
                    }
                }
                if (! isDecisionTaken) {
                    if (DBG)
                        Log.d(TAG, "getMatches2: taking into account " + series.seriesName + " because banner/image exists");
                    if (series.slug.matches("^[0-9]+$")) {
                        // Put in lower priority any entry that has numeric slug
                        if (DBG)
                            Log.d(TAG, "getMatches2: set aside " + series.seriesName + " because slug is only numeric slug=" + series.slug);
                        isDecisionTaken = true;
                        resultsNumericSlug.add(result);
                    } else {
                        if (DBG)
                            Log.d(TAG, "getMatches2: take into account " + series.seriesName + " because slug is not only numeric slug=" + series.slug);
                        isDecisionTaken = true;
                        resultsProbable.add(new Pair<>(result,
                                levenshteinDistance.apply(searchInfo.getShowName().toLowerCase(),
                                        result.getTitle().toLowerCase())));
                    }
                    if (! isDecisionTaken)
                        Log.w(TAG, "processTheTvDbSearch: ignore serie since banner/image is null for " + series.seriesName);
                }
            }
        }
        if (DBG) Log.d(TAG, "getMatches2: resultsProbable=" + resultsProbable.toString());
        Collections.sort(resultsProbable, new Comparator<Pair<SearchResult, Integer>>() {
            @Override
            public int compare(final Pair<SearchResult, Integer> sr1, final Pair<SearchResult, Integer> sr2) {
                if (sr1.second < sr2.second) {
                    return -1;
                } else if (sr1.second.equals(sr2.second)) {
                    return 0;
                } else {
                    return 1;
                }
            }
        });
        if (DBG) Log.d(TAG, "getMatches2: appplying Levenshtein distance resultsProbableSorted=" + resultsProbable.toString());
        if (resultsProbable.size()>0)
            for (Pair<SearchResult,Integer> pair : resultsProbable)
                if (maxItems < 0 || results.size() < maxItems)
                    results.add(pair.first);
        if (resultsNumericSlug.size()>0)
            for (SearchResult result : resultsNumericSlug)
                if (maxItems < 0 || results.size() < maxItems)
                    results.add(result);
        // skip shows without a banner/poster
        /*
        if (resultsNoBanner.size()>0)
            for (SearchResult result : resultsNoBanner)
                if (maxItems < 0 || results.size() < maxItems)
                    results.add(result);
         */
        return results;
    }
}
