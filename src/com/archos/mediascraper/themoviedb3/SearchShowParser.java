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
    private final static boolean SORT_POPULARITY = false;
    private final static boolean SORT_YEAR = true;

    private final static LevenshteinDistance levenshteinDistance = new LevenshteinDistance();

    public static List<SearchResult> getResult(Response<TvShowResultsPage> response,
                                               TvShowSearchInfo searchInfo, Integer year,
                                               String language, Integer maxItems, ShowScraper4 showScraper) {
        List<SearchResult> results;
        SearchParserResult searchShowParserResult = new SearchParserResult();
        if (response != null)
            searchShowParserResult = getSearchShowParserResult(response, searchInfo, year, language, showScraper);
        results = searchShowParserResult.getResults(maxItems);
        return results;
    }

    private static SearchParserResult getSearchShowParserResult(Response<TvShowResultsPage> response,
                                                                    TvShowSearchInfo searchInfo, Integer year, String language, ShowScraper4 showScraper) {
        SearchParserResult searchShowParserResult = new SearchParserResult();
        String countryOfOrigin = searchInfo.getCountryOfOrigin();
        Boolean isDecisionTaken = false;
        int levenshteinDistanceTitle, levenshteinDistanceOriginalTitle;
        log.debug("getSearchShowParserResult: examining response of " + response.body().total_results + " entries in " + language + ", for " + searchInfo.getShowName() + " and specific year " + year);

        // sort first tvshows by popularity so that distinction between levenstein distance is operated on popularity
        List<BaseTvShow> resultsTvShow = response.body().results;
        // OBSERVATION: number_of_seasons only available on id search not name search --> cannot discriminate
        // popularity sort is disabled for now to enable sort by year to pick lower year if not specified with lowest levenshtein metric
        if (SORT_POPULARITY)
            Collections.sort(resultsTvShow, new Comparator<BaseTvShow>() {
                @Override
                public int compare(final BaseTvShow btvs1, final BaseTvShow btvs2) {
                    if (btvs1.popularity > btvs2.popularity) {
                        return -1;
                    } else if (btvs1.popularity.equals(btvs2.popularity)) {
                        return 0;
                    } else {
                        return 1;
                    }
                }
            });
        // prefer older show first
        if (SORT_YEAR)
            Collections.sort(resultsTvShow, new Comparator<BaseTvShow>() {
                @Override
                public int compare(final BaseTvShow btvs1, final BaseTvShow btvs2) {
                    if (btvs1.first_air_date == null && btvs2.first_air_date == null) return 0;
                    if (btvs1.first_air_date == null) return -1;
                    if (btvs2.first_air_date == null) return 1;
                    if (btvs1.first_air_date.before(btvs2.first_air_date)) {
                        return -1;
                    } else if (btvs1.first_air_date.equals(btvs2.first_air_date)) {
                        return 0;
                    } else {
                        return 1;
                    }
                }
            });

        boolean isAirDateKnown = false;
        for (BaseTvShow series : resultsTvShow) {
            log.debug("airdate " + series.name + " airtime " + ((series.first_air_date != null) ? series.first_air_date.toString() : null));
            if (series.id != SERIES_NOT_PERMITTED_ID) {
                if (countryOfOrigin != null && ! series.origin_country.contains(countryOfOrigin)) {
                    log.debug("getSearchShowParserResult: skip " + series.original_name + " because does not contain countryOfOrigin " + countryOfOrigin);
                    continue;
                } else {
                    log.debug("getSearchShowParserResult: " + series.original_name + " contains countryOfOrigin" + countryOfOrigin);
                }
                Bundle extra = new Bundle();
                extra.putString(ShowUtils.EPNUM, String.valueOf(searchInfo.getEpisode()));
                extra.putString(ShowUtils.SEASON, String.valueOf(searchInfo.getSeason()));
                SearchResult result = new SearchResult();
                result.setTvShow();
                result.setYear((year != null) ? String.valueOf(year) : null);
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
                isAirDateKnown = (series.first_air_date != null);
                String showNameLC = searchInfo.getShowName().toLowerCase();
                // TODO (impossible): would be nice to discard show that has not enough seasons to match the search but impossible at this stage BasicTvShow instead of TvShow in response
                if (! isAirDateKnown) {
                    log.debug("getSearchShowParserResult: set aside " + series.name + " because air date is missing");
                    searchShowParserResult.resultsNoAirDate.add(result);
                    isDecisionTaken = true;
                } else {
                    if (series.poster_path == null || series.poster_path.endsWith("missing/series.jpg") || series.poster_path.endsWith("missing/movie.jpg") || series.poster_path == "") {
                        log.debug("getSearchShowParserResult: set aside " + series.name + " because poster missing i.e. image=" + series.poster_path);
                        searchShowParserResult.resultsNoPoster.add(result);
                        isDecisionTaken = true;
                    } else {
                        log.debug("getSearchShowParserResult: " + series.name + " has poster_path " + ScraperImage.TMPL + series.poster_path);
                        result.setPosterPath(series.poster_path);
                        if (series.backdrop_path == null || series.backdrop_path.endsWith("missing/series.jpg") || series.backdrop_path.endsWith("missing/movie.jpg") || series.backdrop_path == "") {
                            log.debug("getSearchShowParserResult: set aside " + series.name + " because banner missing i.e. banner=" + series.backdrop_path);
                            levenshteinDistanceTitle = levenshteinDistance.apply(showNameLC, result.getTitle().toLowerCase());
                            levenshteinDistanceOriginalTitle = levenshteinDistance.apply(showNameLC, result.getOriginalTitle().toLowerCase());
                            searchShowParserResult.resultsNoBanner.add(new Pair<>(result,
                                    Math.min(levenshteinDistanceTitle, levenshteinDistanceOriginalTitle)));
                            isDecisionTaken = true;
                        } else {
                            log.debug("getSearchShowParserResult: " + series.name + " has backdrop_path " + ScraperImage.TMBL + series.backdrop_path);
                            // TODO MARC: this generates the thumb by resizing the large image: pass the two
                            result.setBackdropPath(series.backdrop_path);
                        }
                    }
                }
                if (! isDecisionTaken) {
                    log.debug("getSearchShowParserResult: taking into account " + series.name + " because banner/image exists and known airdate");
                    isDecisionTaken = true;
                    // get the min of the levenshtein distance between cleaned file based show name and title and original title identified
                    levenshteinDistanceTitle = levenshteinDistance.apply(showNameLC, result.getTitle().toLowerCase());
                    levenshteinDistanceOriginalTitle = levenshteinDistance.apply(showNameLC, result.getOriginalTitle().toLowerCase());
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
        Collections.sort(searchShowParserResult.resultsNoBanner, new Comparator<Pair<SearchResult, Integer>>() {
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