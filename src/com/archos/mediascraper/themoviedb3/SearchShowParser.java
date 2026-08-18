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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import retrofit2.Response;

public class SearchShowParser {
    private static final Logger log = LoggerFactory.getLogger(SearchShowParser.class);
    private final static int SERIES_NOT_PERMITTED_ID = 313081;

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
        int levenshteinDistanceTitle, levenshteinDistanceOriginalTitle;
        if (log.isDebugEnabled()) log.debug("getSearchShowParserResult: examining response of {} entries in {}, for {} and specific year {}", response.body().total_results, language, searchInfo.getShowName(), year);

        List<BaseTvShow> resultsTvShow = new ArrayList<>(response.body().results);

        boolean isAirDateKnown = false;
        for (BaseTvShow series : resultsTvShow) {
            if (log.isDebugEnabled()) log.debug("airdate {} airtime {}", series.name, ((series.first_air_date != null) ? series.first_air_date.toString() : null));
            if (series.id != SERIES_NOT_PERMITTED_ID) {
                if (countryOfOrigin != null && !countryOfOrigin.isEmpty() && !series.origin_country.contains(countryOfOrigin)) {
                    if (log.isDebugEnabled()) log.debug("getSearchShowParserResult: skip {} because does not contain countryOfOrigin {}", series.original_name, countryOfOrigin);
                    continue;
                } else {
                    if (log.isDebugEnabled()) log.debug("getSearchShowParserResult: {} contains countryOfOrigin{}", series.original_name, countryOfOrigin);
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
                if (log.isDebugEnabled()) log.debug("getSearchShowParserResult: examining {}, in {}", series.name, language);
                result.setScraper(showScraper);
                result.setFile(searchInfo.getFile());
                result.setOriginalTitle(series.original_name);
                result.setExtra(extra);
                if (series.popularity != null) {
                    result.setPopularity((float) series.popularity.doubleValue());
                } else {
                    result.setPopularity(null);
                }
                // Put in lower priority any entry that has no TV show banned i.e. .*missing/movie.jpg as banner
                isAirDateKnown = (series.first_air_date != null);
                String showNameLC = searchInfo.getShowName().toLowerCase();
                // TODO (impossible): would be nice to discard show that has not enough seasons to match the search but impossible at this stage BasicTvShow instead of TvShow in response
                // get the min of the levenshtein distance between cleaned file based show name and title and original title identified
                levenshteinDistanceTitle = levenshteinDistance.apply(showNameLC, result.getTitle().toLowerCase());
                levenshteinDistanceOriginalTitle = levenshteinDistance.apply(showNameLC, result.getOriginalTitle().toLowerCase());
                int distance = Math.min(levenshteinDistanceTitle, levenshteinDistanceOriginalTitle);
                // Acronym boost: shows are often titled "ACRONYM: Full Description" (e.g. "CSI: Crime
                // Scene Investigation", "CSI: NY", "HPI : Haut Potentiel Intellectuel"). Raw Levenshtein
                // distance penalizes these against an unrelated but shorter title that happens to share
                // more characters overall, so treat an exact match of the query against the title's head
                // up to the colon as a strong/exact match.
                if (isAcronymHeadMatch(showNameLC, result.getTitle().toLowerCase()) || isAcronymHeadMatch(showNameLC, result.getOriginalTitle().toLowerCase())) {
                    if (log.isDebugEnabled()) log.debug("getSearchShowParserResult: acronym head match for {} against {}/{}, boosting distance to 0", showNameLC, result.getOriginalTitle().toLowerCase(), result.getTitle().toLowerCase());
                    distance = 0;
                }
                result.setLevenshteinDistance(distance);
                result.setReleaseOrFirstAiredDate(series.first_air_date);
                if (log.isDebugEnabled()) log.debug("getSearchShowParserResult: between {} and {}/{} levenshteinDistanceTitle={}, levenshteinDistanceOriginalTitle={}, popularity={}, airdate={}, year={}", showNameLC, result.getOriginalTitle().toLowerCase(), result.getTitle().toLowerCase(), levenshteinDistanceTitle, levenshteinDistanceOriginalTitle, result.getPopularity(), series.first_air_date, result.getYear());

                if (series.poster_path == null || series.poster_path.endsWith("missing/series.jpg") || series.poster_path.endsWith("missing/movie.jpg") || series.poster_path == "") {
                    if (log.isDebugEnabled()) log.debug("getSearchShowParserResult: set aside {} because poster missing i.e. image={}", series.name, series.poster_path);
                    searchShowParserResult.resultsNoPoster.add(result);
                } else {
                    if (log.isDebugEnabled()) log.debug("getSearchShowParserResult: {} has poster_path {}{}", series.name, ScraperImage.TMPL, series.poster_path);
                    result.setPosterPath(series.poster_path);
                    if (series.backdrop_path == null || series.backdrop_path.endsWith("missing/series.jpg") || series.backdrop_path.endsWith("missing/movie.jpg") || series.backdrop_path == "") {
                        if (log.isDebugEnabled()) log.debug("getSearchShowParserResult: set aside {} because banner missing i.e. banner={}", series.name, series.backdrop_path);
                        searchShowParserResult.resultsNoBanner.add(result);
                    } else {
                        if (log.isDebugEnabled()) log.debug("getSearchShowParserResult: {} has backdrop_path {}{} -> taking into account {} because banner/image exists and known airdate", series.name, ScraperImage.TMBL, series.backdrop_path, series.name);
                        // TODO MARC: this generates the thumb by resizing the large image: pass the two
                        result.setBackdropPath(series.backdrop_path);
                        if (! isAirDateKnown) {
                            if (log.isDebugEnabled()) log.debug("getSearchShowParserResult: set aside {} because air date is missing", series.name);
                            searchShowParserResult.resultsNoAirDate.add(result);
                        } else {
                            searchShowParserResult.resultsProbable.add(result);
                        }
                    }
                }
            }
        }
        if (log.isDebugEnabled()) log.debug("getSearchShowParserResult: resultsProbable={}", searchShowParserResult.resultsProbable.toString());

        // perform the levenshtein distance sort on all results
        Collections.sort(searchShowParserResult.resultsProbable, SearchParserResult.comparator);
        Collections.sort(searchShowParserResult.resultsNoBanner, SearchParserResult.comparator);
        Collections.sort(searchShowParserResult.resultsNoPoster, SearchParserResult.comparator);
        Collections.sort(searchShowParserResult.resultsNoAirDate, SearchParserResult.comparator);

        // dump
        if (log.isTraceEnabled()) log.trace("getSearchShowParserResult: applying Levenshtein distance resultsProbableSorted={}", searchShowParserResult.resultsProbable.toString());
        return searchShowParserResult;
    }

    /**
     * Detects the "ACRONYM: Subtitle" show naming convention (e.g. "CSI: Crime Scene
     * Investigation", "CSI: NY", "CSI: Miami"). Matches the query against the title's head up to
     * the first colon, trimming spaces around the colon to also handle locales such as French
     * that write it as "ACRONYM : Subtitle".
     */
    private static boolean isAcronymHeadMatch(String queryLC, String titleLC) {
        int idx = titleLC.indexOf(':');
        if (idx < 0) return false;
        String head = titleLC.substring(0, idx).trim();
        return !head.isEmpty() && head.equals(queryLC);
    }
}