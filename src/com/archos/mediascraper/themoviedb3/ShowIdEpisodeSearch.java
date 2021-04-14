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
import com.uwetrottmann.tmdb2.entities.TvEpisode;
import com.uwetrottmann.tmdb2.entities.TvShow;
import com.uwetrottmann.tmdb2.enumerations.AppendToResponseItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import retrofit2.Response;

// Perform show search for specific showId and language (ISO 639-1 code)
public class ShowIdEpisodeSearch {
    private static final Logger log = LoggerFactory.getLogger(ShowIdEpisodeSearch.class);

    // In theory this is to buffer two consecutive requests in ShowScraper (or 4 if there is english)
    private final static LruCache<String, ShowIdEpisodeSearchResult> sShowCache = new LruCache<>(10);

    public static ShowIdEpisodeSearchResult getEpisodeShowResponse(int showId, int season, int episode, String language, MyTmdb tmdb) {
        log.debug("getEpisodeShowResponse: quering thetvdb for showId " + showId + " season " + season + " episode " + episode + " in " + language);

        String showKey = showId + "|" + language;
        ShowIdEpisodeSearchResult myResult = sShowCache.get(showKey);
        if (log.isTraceEnabled()) debugLruCache(sShowCache);

        if (myResult == null) {
            myResult = new ShowIdEpisodeSearchResult();
            try {
                // use appendToResponse to get imdbId
                // specify image language include_image_language=en,null
                Map<String, String> options  = new HashMap<String, String>() {{
                    put("include_image_language", "en,null");
                }};
                Response<TvEpisode> seriesResponse = tmdb.tvEpisodesService().episode(showId, season, episode, language, new AppendToResponse(AppendToResponseItem.EXTERNAL_IDS, AppendToResponseItem.IMAGES), options).execute();
                switch (seriesResponse.code()) {
                    case 401: // auth issue
                        log.debug("search: auth error");
                        myResult.status = ScrapeStatus.AUTH_ERROR;
                        ShowScraper4.reauth();
                        return myResult;
                    case 404: // not found
                        myResult.status = ScrapeStatus.NOT_FOUND;
                        // fallback to english if no result
                        if (!language.equals("en")) {
                            log.debug("getEpisodeShowResponse: retrying search for showId " + showId + " in en");
                            return getEpisodeShowResponse(showId, season, episode,"en", tmdb);
                        }
                        log.debug("getEpisodeShowResponse: showId " + showId + " not found");
                        // record valid answer
                        sShowCache.put(showKey, myResult);
                        break;
                    default:
                        if (seriesResponse.isSuccessful()) {
                            if (seriesResponse.body() != null) {
                                myResult.tvEpisode = seriesResponse.body();
                                myResult.status = ScrapeStatus.OKAY;
                            } else {
                                myResult.status = ScrapeStatus.NOT_FOUND;
                            }
                            // record valid answer
                            sShowCache.put(showKey, myResult);
                        } else { // an error at this point is PARSER related
                            log.debug("getEpisodeShowResponse: error " + seriesResponse.code());
                            myResult.status = ScrapeStatus.ERROR_PARSER;
                        }
                        break;
                }
            } catch (IOException e) {
                log.error("getEpisodeShowResponse: caught IOException getting result for showId=" + showId);
                myResult.status = ScrapeStatus.ERROR_PARSER;
                myResult.reason = e;
            }
        }
        return myResult;
    }

    public static void debugLruCache(LruCache<String, ShowIdEpisodeSearchResult> lruCache) {
        log.debug("debugLruCache: size=" + lruCache.size());
        log.debug("debugLruCache: putCount=" + lruCache.putCount());
        log.debug("debugLruCache: hitCount=" + lruCache.hitCount());
        log.debug("debugLruCache: missCount=" + lruCache.missCount());
        log.debug("debugLruCache: evictionCount=" + lruCache.evictionCount());
    }
}
