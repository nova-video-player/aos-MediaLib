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
import android.util.Pair;

import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.preprocess.TvShowSearchInfo;
import com.archos.mediascraper.xml.ShowScraper4;
import com.uwetrottmann.tmdb2.entities.TvShowResultsPage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import retrofit2.Response;

import static com.archos.mediascraper.preprocess.ParseUtils.yearExtractor;

// Search Show for name query for year in language (ISO 639-1 code)
public class SearchShow {
    private static final Logger log = LoggerFactory.getLogger(SearchShow.class);

    // Benchmarks tells that with tv shows sorted in folders, size of 200 or 20 or even 10 provides the same cacheHits on fake collection of 30k episodes, 250 shows
    private final static LruCache<String, Response<TvShowResultsPage>> showCache = new LruCache<>(50);

    public static SearchShowResult search(TvShowSearchInfo searchInfo, String language, int resultLimit, ShowScraper4 showScraper, MyTmdb tmdb) {
        SearchShowResult myResult = new SearchShowResult();
        Response<TvShowResultsPage> response = null;
        boolean authIssue = false;
        boolean notFoundIssue = true;
        boolean isResponseOk = false;
        boolean isResponseEmpty = false;
        String showKey = null;
        String name;
        log.debug("search: quering tmdb for " + searchInfo.getShowName() + " year " + searchInfo.getFirstAiredYear() + " in " + language + ", resultLimit=" + resultLimit);
        try {
            Integer year = null;
            if (searchInfo.getFirstAiredYear() != null) {
                try {
                    year = Integer.parseInt(searchInfo.getFirstAiredYear());
                } catch (NumberFormatException nfe) {
                    log.warn("search: not valid year int " + searchInfo.getFirstAiredYear());
                }
            }

            showKey = searchInfo.getShowName() + "|" + year + "|" + language;
            log.debug("SearchShowResult: cache showKey " + showKey);
            response = showCache.get(showKey);
            if (log.isTraceEnabled()) debugLruCache(showCache);
            if (response == null) {
                log.debug("SearchShowResult: no boost for " + searchInfo.getShowName() + " year " + year);
                // adult search false by default
                response = tmdb.searchService().tv(searchInfo.getShowName(), null, language, year, false).execute();
                if (response.code() == 401) authIssue = true; // this is an OR
                if (response.code() != 404) notFoundIssue = false; // this is an AND
                if (response.isSuccessful()) isResponseOk = true;
                if (response.body() == null)
                    isResponseEmpty = true;
                else {
                    if (response.body().total_results == 0) notFoundIssue = true;
                    log.debug("search: response body has " + response.body().total_results + " results");
                    if (notFoundIssue && searchInfo.getFirstAiredYear() == null) {
                        // reprocess name with year_extractor without parenthesis since we need to match The.Flash.2014.sXXeYY but not first to cope with Paris.Police.1900
                        name = searchInfo.getShowName();
                        Pair<String, String> nameYear = yearExtractor(name);
                        log.debug("search: not found trying to extract year name=" + nameYear.first + ", year=" + nameYear.second);
                        if (nameYear.second != null) { // avoid infinite loop
                            // remember that it is a reboot show with date year to add to name to discriminate
                            myResult.year = nameYear.second;
                            return search(new TvShowSearchInfo(searchInfo.getFile(), nameYear.first, searchInfo.getSeason(), searchInfo.getEpisode(), nameYear.second, searchInfo.getCountryOfOrigin()),
                                    language, resultLimit, showScraper, tmdb);
                        }
                    }
                }
                if (isResponseOk || isResponseEmpty) {
                    log.debug("search: inserting in showCache " + showKey + " and response ");
                    showCache.put(showKey, response);
                }
            } else {
                log.debug("search: boost using cached searched show for " + searchInfo.getShowName());
                isResponseOk = true;
                notFoundIssue = false;
                if (response.body() == null) isResponseEmpty = true;
            }
            if (authIssue) {
                log.debug("search: auth error");
                myResult.status = ScrapeStatus.AUTH_ERROR;
                myResult.result = SearchShowResult.EMPTY_LIST;
                ShowScraper4.reauth();
                return myResult;
            }
            if (notFoundIssue) {
                log.debug("search: not found");
                myResult.result = SearchShowResult.EMPTY_LIST;
                myResult.status = ScrapeStatus.NOT_FOUND;
            } else {
                if (isResponseEmpty) {
                    log.debug("search: error");
                    myResult.result = SearchShowResult.EMPTY_LIST;
                    myResult.status = ScrapeStatus.ERROR_PARSER;
                } else {
                    myResult.result = SearchShowParser.getResult(
                            (isResponseOk) ? response : null,
                            searchInfo, year, language, resultLimit, showScraper);
                    myResult.status = ScrapeStatus.OKAY;
                }
            }
        } catch (IOException e) {
            if (log.isDebugEnabled())
                log.error("search: caught IOException " + e.getMessage(), e);
            else
                log.error("search: caught IOException");
            myResult.result = SearchShowResult.EMPTY_LIST;
            myResult.status = ScrapeStatus.ERROR_PARSER;
            myResult.reason = e;
        }
        return myResult;
    }

    public static void debugLruCache(LruCache<String, Response<TvShowResultsPage>> lruCache) {
        log.debug("debugLruCache: size=" + lruCache.size());
        log.debug("debugLruCache: putCount=" + lruCache.putCount());
        log.debug("debugLruCache: hitCount=" + lruCache.hitCount());
        log.debug("debugLruCache: missCount=" + lruCache.missCount());
        log.debug("debugLruCache: evictionCount=" + lruCache.evictionCount());
    }

}
