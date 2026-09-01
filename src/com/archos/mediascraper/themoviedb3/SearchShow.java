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

import android.util.LruCache;

import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.SearchResult;
import com.archos.mediascraper.ShowUtils;
import com.archos.mediascraper.preprocess.TvShowSearchInfo;
import com.archos.mediascraper.xml.ShowScraper4;
import com.uwetrottmann.tmdb2.entities.TvShowResultsPage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import retrofit2.Response;

// Search Show for name query for year in language (ISO 639-1 code)
public class SearchShow {
    private static final Logger log = LoggerFactory.getLogger(SearchShow.class);

    // Benchmarks tells that with tv shows sorted in folders, size of 200 or 20 or even 10 provides the same cacheHits on fake collection of 30k episodes, 250 shows
    private final static LruCache<String, Response<TvShowResultsPage>> showCache = new LruCache<>(50);

    public static SearchShowResult search(TvShowSearchInfo searchInfo, String language, int resultLimit, final boolean adultScrape, ShowScraper4 showScraper, MyTmdb tmdb) {
        SearchShowResult myResult = new SearchShowResult();
        Response<TvShowResultsPage> response = null;
        boolean authIssue = false;
        boolean notFoundIssue = true;
        boolean isResponseOk = false;
        boolean isResponseEmpty = false;
        boolean serviceError = false;
        String showKey = null;
        String name;
        if (log.isDebugEnabled()) log.debug("search: quering tmdb for {} year {} in {}, resultLimit={}", searchInfo.getShowName(), searchInfo.getFirstAiredYear(), language, resultLimit);
        try {
            Integer year = null;
            if (searchInfo.getFirstAiredYear() != null) {
                try {
                    year = Integer.parseInt(searchInfo.getFirstAiredYear());
                } catch (NumberFormatException nfe) {
                    log.warn("search: not valid year int {}", searchInfo.getFirstAiredYear());
                }
            }

            String searchQueryString = searchInfo.getShowName();
            // include year: tmdb searchService().tv() applies server-side year filtering, so the
            // cached response content depends on year and must not be shared across different years
            showKey = ShowUtils.cleanUpName(searchQueryString.toLowerCase()) + "|" + year + "|" + language;
            if (log.isDebugEnabled()) log.debug("SearchShowResult: cache showKey {}", showKey);
            response = showCache.get(showKey);
            if (log.isTraceEnabled()) debugLruCache(showCache);
            if (response == null) {
                if (log.isDebugEnabled()) log.debug("SearchShowResult: no boost for {} year {}", searchInfo.getShowName(), year);
                // adult search false by default
                response = tmdb.searchService().tv(searchQueryString, 1, language, year, false).execute();
                if (response.code() != 404) notFoundIssue = false; // this is an AND
                // Check https://developer.themoviedb.org/docs/errors
                switch (response.code()) {
                    case 401 -> authIssue = true; // this is an OR
                    case 404 -> notFoundIssue = true; // this is an AND
                    case 500, 503, 504 -> serviceError = true;
                }
                if (response.isSuccessful()) isResponseOk = true;
                if (response.body() == null)
                    isResponseEmpty = true;
                else {
                    if (response.body().total_results == 0 && !language.equals("en")) {
                        // Retry in English when native language search returns no results
                        // since TMDB may only index the English title
                        if (log.isDebugEnabled()) log.debug("search: no results in {}, retrying in en for {}", language, searchQueryString);
                        response = tmdb.searchService().tv(searchQueryString, 1, "en", year, false).execute();
                        if (response.isSuccessful()) isResponseOk = true;
                    }
                    if (response.body() == null || response.body().total_results == 0) {
                        // Fallback for "and" vs "&" (e.g. "Asterix and Obelix" -> "Asterix & Obelix")
                        String alternate = null;
                        if (searchQueryString.toLowerCase().contains(" and ")) {
                            alternate = searchQueryString.replaceAll("(?i)\\band\\b", "&");
                        } else if (searchQueryString.contains("&")) {
                            alternate = searchQueryString.replace("&", "and");
                        }
                        if (alternate != null) {
                            if (log.isDebugEnabled()) log.debug("search: no results, retrying with alternate name: {}", alternate);
                            response = tmdb.searchService().tv(alternate, 1, language, year, false).execute();
                            if (response.isSuccessful()) isResponseOk = true;
                            if (response.body() != null && response.body().total_results == 0 && !language.equals("en")) {
                                if (log.isDebugEnabled()) log.debug("search: no results in {} for alternate, retrying in en", language);
                                response = tmdb.searchService().tv(alternate, 1, "en", year, false).execute();
                                if (response.isSuccessful()) isResponseOk = true;
                            }
                        }
                    }
                    if (response.body() == null || response.body().total_results == 0) {
                        // Fallback for transliterated titles (e.g. German umlauts: 'ae' -> 'ä')
                        boolean isGerman = (language != null && language.startsWith("de")) ||
                                           "de".equals(java.util.Locale.getDefault().getLanguage());
                        if (isGerman) {
                            String transliterated = com.archos.mediascraper.preprocess.ParseUtils.transliterate(searchQueryString);
                            if (!transliterated.equals(searchQueryString)) {
                                if (log.isDebugEnabled()) log.debug("search: no results, retrying with transliterated name: {}", transliterated);
                                response = tmdb.searchService().tv(transliterated, 1, language, year, false).execute();
                                if (response.isSuccessful()) isResponseOk = true;
                                if (response.body() != null && response.body().total_results == 0 && !language.equals("en")) {
                                    if (log.isDebugEnabled()) log.debug("search: no results in {} for transliterated, retrying in en", language);
                                    response = tmdb.searchService().tv(transliterated, 1, "en", year, false).execute();
                                    if (response.isSuccessful()) isResponseOk = true;
                                }
                            }
                        }
                    }
                    if (response.body() == null || response.body().total_results == 0) notFoundIssue = true;
                    else notFoundIssue = false;

                    //We have a show, put it in cache before returning.
                    if (isResponseOk) {
                        if (log.isDebugEnabled()) log.debug("search: inserting in showCache {} and response ", showKey);
                        showCache.put(showKey, response);
                    }
                }
                if (log.isTraceEnabled()) debugLruCache(showCache);
            } else {
                if (log.isDebugEnabled()) log.debug("search: boost using cached searched show for {}", searchInfo.getShowName());
                isResponseOk = true;
                notFoundIssue = false;
                if (response.body() == null) isResponseEmpty = true;
            }
            if (authIssue) {
                if (log.isDebugEnabled()) log.debug("search: auth error");
                myResult.status = ScrapeStatus.AUTH_ERROR;
                myResult.result = SearchShowResult.EMPTY_LIST;
                ShowScraper4.reauth();
                return myResult;
            }
            if (notFoundIssue || serviceError) {
                if (log.isDebugEnabled()) log.debug("search: not found");
                myResult.result = SearchShowResult.EMPTY_LIST;
                if (serviceError) myResult.status = ScrapeStatus.ERROR;
                else myResult.status = ScrapeStatus.NOT_FOUND;
            } else {
                if (isResponseEmpty) {
                    if (log.isDebugEnabled()) log.debug("search: error");
                    myResult.result = SearchShowResult.EMPTY_LIST;
                    myResult.status = ScrapeStatus.ERROR_PARSER;
                } else {
                    myResult.result = SearchShowParser.getResult(
                            (isResponseOk) ? response : null,
                            searchInfo, year, language, resultLimit, showScraper);
                    myResult.status = ScrapeStatus.OKAY;
                }
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            if (log.isDebugEnabled())
                log.error("search: caught {} {}", e.getClass().getSimpleName(), e.getMessage(), e);
            else
                log.error("search: caught {}", e.getClass().getSimpleName());
            myResult.result = SearchShowResult.EMPTY_LIST;
            myResult.status = ScrapeStatus.ERROR_PARSER;
            myResult.reason = e;
        }
        return myResult;
    }

    public static void debugLruCache(LruCache<String, Response<TvShowResultsPage>> lruCache) {
        if (log.isDebugEnabled()) log.debug("debugLruCache(Tier2): size={}, put={}, hit={}, miss={}, evict={}", lruCache.size(), lruCache.putCount(), lruCache.hitCount(), lruCache.missCount(), lruCache.evictionCount());
    }

}
