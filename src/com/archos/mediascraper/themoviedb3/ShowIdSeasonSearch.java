// Copyright 2021 Courville Software
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
import com.archos.mediascraper.xml.ShowScraper4;
import com.uwetrottmann.tmdb2.entities.AppendToResponse;
import com.uwetrottmann.tmdb2.entities.TvSeason;
import com.uwetrottmann.tmdb2.entities.TvShow;
import com.uwetrottmann.tmdb2.enumerations.AppendToResponseItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import retrofit2.Response;

// Perform show search for specific showId and language (ISO 639-1 code)
public class ShowIdSeasonSearch {
    private static final Logger log = LoggerFactory.getLogger(ShowIdSeasonSearch.class);

    // Cache season API responses to reduce redundant TMDb calls
    // Key format: showId|season|language
    private final static LruCache<String, ShowIdSeasonSearchResult> sShowCache = new LruCache<>(50);

    public static ShowIdSeasonSearchResult getSeasonShowResponse(String seasonKey, int showId, int season, String language, final boolean adultScrape, MyTmdb tmdb) {
        // Build image language filter: current language + "en" + "null" (language-neutral)
        // Avoid duplicates if current language is already "en"
        final String imageLanguages = language.equals("en") ? "en,null" : language + ",en,null";
        final Map<String, String> options  = new HashMap<String, String>() {{
            put("include_image_language", imageLanguages);
            put("include_adult", String.valueOf(adultScrape));
        }};

        if (log.isDebugEnabled()) log.debug("getSeasonShowResponse: quering tmdb for showId {} season {} in {}", showId, season, language);

        ShowIdSeasonSearchResult myResult = sShowCache.get(seasonKey);
        if (log.isTraceEnabled()) debugLruCache(sShowCache);

        if (myResult == null) {
            if (log.isDebugEnabled()) log.debug("getSeasonShowResponse: not in cache fetching s{} for showId {}", season, showId);
            myResult = new ShowIdSeasonSearchResult();
            try {
                // use appendToResponse to get imdbId
                // e.g. https://api.themoviedb.org/3/tv/66732/season/1?language=en&append_to_response=credits%2Cexternal_ids%2Cimages%2Ccontent_ratings&include_image_language=en%2Cnull&api_key=051012651ba326cf5b1e2f482342eaa2
                Response<TvSeason> seriesResponse = tmdb.tvSeasonsService().season(showId, season, language, new AppendToResponse(AppendToResponseItem.EXTERNAL_IDS, AppendToResponseItem.IMAGES, AppendToResponseItem.CREDITS, AppendToResponseItem.CONTENT_RATINGS), options).execute();
                switch (seriesResponse.code()) {
                    case 401: // auth issue
                        if (log.isDebugEnabled()) log.debug("search: auth error");
                        myResult.status = ScrapeStatus.AUTH_ERROR;
                        ShowScraper4.reauth();
                        return myResult;
                    case 404: // not found
                        myResult.status = ScrapeStatus.NOT_FOUND;
                        // fallback to english if no result
                        if (!language.equals("en")) {
                            if (log.isDebugEnabled()) log.debug("getSeasonShowResponse: retrying search for showId {} in en", showId);
                            return getSeasonShowResponse( seasonKey, showId, season,"en", adultScrape, tmdb);
                        }
                        if (log.isDebugEnabled()) log.debug("getSeasonShowResponse: showId {} not found", showId);
                        // record INVALID answer
                        // Caching the NOT FOUND to save scrape again and getting same answer
                        sShowCache.put(seasonKey, myResult);
                        break;
                    default:
                        if (seriesResponse.isSuccessful()) {
                            if (seriesResponse.body() != null) {
                                myResult.tvSeason = seriesResponse.body();
                                myResult.status = ScrapeStatus.OKAY;
                            } else {
                                if (!language.equals("en")) {
                                    if (log.isDebugEnabled()) log.debug("getSeasonShowResponse: retrying search for showId {} in en", showId);
                                    return getSeasonShowResponse(seasonKey, showId, season,"en", adultScrape, tmdb);
                                }
                                myResult.status = ScrapeStatus.NOT_FOUND;
                            }
                            // record valid answer
                            sShowCache.put(seasonKey, myResult);
                        } else { // an error at this point is PARSER related
                            if (log.isDebugEnabled()) log.debug("getSeasonShowResponse: error {}", seriesResponse.code());
                            myResult.status = ScrapeStatus.ERROR_PARSER;
                        }
                        break;
                }
            } catch (Exception e) {
                log.error("getSeasonShowResponse: caught {} getting result for showId={}", e.getClass().getSimpleName(), showId);
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                myResult.status = ScrapeStatus.ERROR_PARSER;
                myResult.reason = e;
            }
        }
        return myResult;
    }

    public static void debugLruCache(LruCache<String, ShowIdSeasonSearchResult> lruCache) {
        if (log.isDebugEnabled()) log.debug("debugLruCache(Season): size={}, put={}, hit={}, miss={}, evict={}", lruCache.size(), lruCache.putCount(), lruCache.hitCount(), lruCache.missCount(), lruCache.evictionCount());
    }
}
