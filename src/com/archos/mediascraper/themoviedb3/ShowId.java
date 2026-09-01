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

import android.content.Context;

import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.ShowTags;
import com.archos.mediascraper.xml.ShowScraper4;
import com.uwetrottmann.tmdb2.entities.AppendToResponse;
import com.uwetrottmann.tmdb2.entities.TvShow;
import com.uwetrottmann.tmdb2.enumerations.AppendToResponseItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import retrofit2.Response;

// Get the basic show information for a specific movie id and language (ISO 639-1 code)
public class ShowId {
    private static final Logger log = LoggerFactory.getLogger(ShowId.class);

    public static ShowIdResult getBaseInfo(int showId, String language, MyTmdb tmdb, Context context) {
        ShowIdResult myResult = new ShowIdResult();
        ShowTags parserResult = null;

        if (log.isDebugEnabled()) log.debug("getBaseInfo: quering tmdb for showId {} in {}", showId, language);
        try {
            // use appendToResponse to get imdbId
            Response<TvShow> seriesResponse = tmdb.tvService().tv(showId, language, new AppendToResponse(AppendToResponseItem.EXTERNAL_IDS, AppendToResponseItem.TRANSLATIONS)).execute();
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
                        if (log.isDebugEnabled()) log.debug("getBaseInfo: retrying search for showId {} in en", showId);
                        return getBaseInfo(showId, "en", tmdb, context);
                    }
                    if (log.isDebugEnabled()) log.debug("getBaseInfo: movieId {} not found", showId);
                    break;
                default:
                    if (seriesResponse.isSuccessful()) {
                        if (seriesResponse.body() != null) {
                            // TODO change year null but ShowId not used for now
                            parserResult = ShowIdParser.getResult(seriesResponse.body(), null, context, language);
                            myResult.tag = parserResult;
                            myResult.status = ScrapeStatus.OKAY;
                        } else {
                            if (!language.equals("en")) {
                                if (log.isDebugEnabled()) log.debug("getBaseInfo: retrying search for showId {} in en", showId);
                                return getBaseInfo(showId, "en", tmdb, context);
                            }
                            myResult.status = ScrapeStatus.NOT_FOUND;
                        }
                    } else { // an error at this point is PARSER related
                        if (log.isDebugEnabled()) log.debug("getBaseInfo: error {}", seriesResponse.code());
                        myResult.status = ScrapeStatus.ERROR_PARSER;
                    }
                    break;
            }
        } catch (Exception e) {
            log.error("getBaseInfo: caught {} getting summary for showId={}", e.getClass().getSimpleName(), showId);
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            myResult.status = ScrapeStatus.ERROR_PARSER;
            myResult.reason = e;
        }
        return myResult;
    }
}
