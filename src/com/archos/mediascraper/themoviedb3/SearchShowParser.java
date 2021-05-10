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

package com.archos.mediascraper.themoviedb3;

import android.os.Bundle;
import android.util.Pair;

import com.archos.mediascraper.ScraperImage;
import com.archos.mediascraper.SearchResult;
import com.archos.mediascraper.ShowUtils;
import com.archos.mediascraper.preprocess.TvShowSearchInfo;
import com.archos.mediascraper.xml.ShowScraper4;
import com.uwetrottmann.tmdb2.entities.BaseTvShow;
import com.uwetrottmann.tmdb2.entities.TvShow;
import com.uwetrottmann.tmdb2.entities.TvShowResultsPage;

import org.apache.commons.text.similarity.LevenshteinDistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import retrofit2.Response;

public class SearchShowParser {
    private static final Logger log = LoggerFactory.getLogger(SearchShowParser.class);
    private final static int SERIES_NOT_PERMITTED_ID = 313081;

    private final static LevenshteinDistance levenshteinDistance = new LevenshteinDistance();

    private static List<SearchResult> normalAdd(SearchShowParserResult searchShowParserResult, int maxItems) {
        List<SearchResult> results = new LinkedList<>();
        log.debug("normalAdd: searchShowParserResult.resultsProbable.size()=" + searchShowParserResult.resultsProbable.size());
        if (searchShowParserResult.resultsProbable.size()>0)
            for (Pair<SearchResult,Integer> pair : searchShowParserResult.resultsProbable)
                if (maxItems < 0 || results.size() < maxItems)
                    results.add(pair.first);
        // skip shows without a poster
        /*
        if (searchShowParserResult.resultsNoPoster.size()>0)
            for (SearchResult result : searchShowParserResult.resultsNoPoster)
                if (maxItems < 0 || results.size() < maxItems)
                    results.add(result);
         */
        // skip shows without a banner
        /*
        if (searchShowParserResult.resultsNoBanner.size()>0)
            for (SearchResult result : searchShowParserResult.resultsNoBanner)
                if (maxItems < 0 || results.size() < maxItems)
                    results.add(result);
         */
        log.debug("normalAdd: results.size()=" + results.size());
        return results;
    }

    public static List<SearchResult> getResult(Response<TvShowResultsPage> response,
                                               TvShowSearchInfo searchInfo, String language,
                                               Integer maxItems, ShowScraper4 showScraper) {
        List<SearchResult> results;
        SearchShowParserResult searchShowParserResult = new SearchShowParserResult();
        if (response != null)
            searchShowParserResult = getSearchShowParserResult(response, searchInfo, language, showScraper);
        results = normalAdd(searchShowParserResult, maxItems);
        return results;
    }

    private static SearchShowParserResult getSearchShowParserResult(Response<TvShowResultsPage> response,
                                                                    TvShowSearchInfo searchInfo, String language, ShowScraper4 showScraper) {
        SearchShowParserResult searchShowParserResult = new SearchShowParserResult();
        int episode, season;
        Boolean isDecisionTaken = false;
        int levenshteinDistanceTitle, levenshteinDistanceOriginalTitle;
        log.debug("getSearchShowParserResult: examining response of " + response.body().total_results + " entries in " + language + ", for " + searchInfo.getShowName());
        for (BaseTvShow series : response.body().results) {
            if (series.id != SERIES_NOT_PERMITTED_ID) {
                Bundle extra = new Bundle();
                extra.putString(ShowUtils.EPNUM, String.valueOf(searchInfo.getEpisode()));
                extra.putString(ShowUtils.SEASON, String.valueOf(searchInfo.getSeason()));
                SearchResult result = new SearchResult();
                result.setTvShow();
                // set show search point of origin
                result.setOriginSearchEpisode(searchInfo.getEpisode());
                result.setOriginSearchSeason(searchInfo.getSeason());
                result.setId(series.id);
                result.setLanguage(language);
                result.setTitle(series.name);
                log.debug("getSearchShowParserResult: examining " + series.name + ", in " + language);
                result.setScraper(showScraper);
                result.setFile(searchInfo.getFile());
                result.setOriginalTitle(series.original_name);
                result.setExtra(extra);
                // Put in lower priority any entry that has no TV show banned i.e. .*missing/movie.jpg as banner
                isDecisionTaken = false;
                if (series.backdrop_path == null || series.backdrop_path.endsWith("missing/series.jpg") || series.backdrop_path.endsWith("missing/movie.jpg") || series.backdrop_path == "") {
                    log.debug("getSearchShowParserResult: set aside " + series.name + " because banner missing i.e. banner=" + series.backdrop_path);
                    searchShowParserResult.resultsNoBanner.add(result);
                    isDecisionTaken = true;
                } else {
                    log.debug("getSearchShowParserResult: " + series.name + " has backdrop_path " + ScraperImage.TMBL + series.backdrop_path);
                    // TODO MARC: this generates the thumb by resizing the large image: pass the two
                    result.setBackdropPath(ScraperImage.TMBL + series.backdrop_path);
                }
                if (series.poster_path == null || series.poster_path.endsWith("missing/series.jpg") || series.poster_path.endsWith("missing/movie.jpg") || series.poster_path == "") {
                    log.debug("getSearchShowParserResult: set aside " + series.name + " because poster missing i.e. image=" + series.poster_path);
                    searchShowParserResult.resultsNoPoster.add(result);
                    isDecisionTaken = true;
                } else {
                    log.debug("getSearchShowParserResult: " + series.name + " has poster_path " + ScraperImage.TMPL + series.poster_path);
                    result.setPosterPath(ScraperImage.TMPL + series.poster_path);
                }
                if (! isDecisionTaken) {
                    log.debug("getSearchShowParserResult: taking into account " + series.name + " because banner/image exists");
                    isDecisionTaken = true;
                    // get the min of the levenshtein distance between cleaned file based show name and title and original title identified
                    levenshteinDistanceTitle = levenshteinDistance.apply(searchInfo.getShowName().toLowerCase(),
                            result.getTitle().toLowerCase());
                    levenshteinDistanceOriginalTitle = levenshteinDistance.apply(searchInfo.getShowName().toLowerCase(),
                            result.getOriginalTitle().toLowerCase());
                    searchShowParserResult.resultsProbable.add(new Pair<>(result,
                            Math.min(levenshteinDistanceTitle, levenshteinDistanceOriginalTitle)));
                }
                if (! isDecisionTaken)
                    log.warn("getSearchShowParserResult: ignore serie since banner/image is null for " + series.name);
            }
        }
        log.debug("getSearchShowParserResult: resultsProbable=" + searchShowParserResult.resultsProbable.toString());
        Collections.sort(searchShowParserResult.resultsProbable, new Comparator<Pair<SearchResult, Integer>>() {
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
        log.debug("getSearchShowParserResult: applying Levenshtein distance resultsProbableSorted=" + searchShowParserResult.resultsProbable.toString());
        return searchShowParserResult;
    }
}
