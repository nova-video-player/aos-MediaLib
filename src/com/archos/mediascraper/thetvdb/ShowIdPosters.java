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

import android.content.Context;

import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.xml.ShowScraper3;
import com.uwetrottmann.thetvdb.entities.SeriesImageQueryResultResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import retrofit2.Response;

// Get the posters for specific show id
public class ShowIdPosters {
    private static final Logger log = LoggerFactory.getLogger(ShowIdPosters.class);

    public static ShowIdPostersResult getPosters(int showId, String showTitle,
                                               boolean basicShow, boolean basicEpisode,
                                               String language, MyTheTVdb theTvdb, Context context) {
        ShowIdPostersResult myResult = new ShowIdPostersResult();
        log.debug("getPosters: for showTitle=" + showTitle + ", showId=" + showId);

        boolean authIssue = false;
        boolean notFoundIssue = true;

        boolean isPostersResponseOk = false;
        boolean isPostersResponseEmpty = false;
        boolean isSeasonsResponseOk = false;
        boolean isSeasonsResponseEmpty = false;
        boolean isGlobalPostersResponseOk = false;
        boolean isGlobalPostersResponseEmpty = false;
        boolean isGlobalSeasonsResponseOk = false;
        boolean isGlobalSeasonsResponseEmpty = false;

        log.debug("getPosters: quering thetvdb for showId " + showId);
        try {
            Response<SeriesImageQueryResultResponse> postersResponse = null;
            Response<SeriesImageQueryResultResponse> seasonsResponse = null;
            if (!basicEpisode) {
                log.debug("getPosters: quering thetvdb for poster");
                postersResponse = theTvdb.series()
                        .imagesQuery(showId, "poster", null, null, language).execute();
                if (postersResponse.code() == 401) authIssue = true; // this is an OR
                if (postersResponse.code() != 404) notFoundIssue = false; // this is an AND
                if (postersResponse.isSuccessful()) isPostersResponseOk = true;
                if (postersResponse.body() == null) isPostersResponseEmpty = true;
            }
            if (!basicShow) {
                log.debug("getPosters: quering thetvdb for season");
                seasonsResponse = theTvdb.series()
                        .imagesQuery(showId, "season", null, null, language)
                        .execute();
                if (seasonsResponse.code() == 401) authIssue = true; // this is an OR
                if (seasonsResponse.code() != 404) notFoundIssue = false; // this is an AND
                if (seasonsResponse.isSuccessful()) isSeasonsResponseOk = true;
                if (seasonsResponse.body() == null) isSeasonsResponseEmpty = true;
            }

            Response<SeriesImageQueryResultResponse> globalPostersResponse = null;
            Response<SeriesImageQueryResultResponse> globalSeasonsResponse = null;
            if (!language.equals("en")) {
                if (!basicEpisode) {
                    log.debug("getPosters: quering thetvdb for poster in en");
                    globalPostersResponse = theTvdb.series()
                            .imagesQuery(showId, "poster", null, null, "en").execute();
                    if (globalPostersResponse.code() == 401) authIssue = true; // this is an OR
                    if (globalPostersResponse.code() != 404) notFoundIssue = false; // this is an AND
                    if (globalPostersResponse.isSuccessful()) isGlobalPostersResponseOk = true;
                    if (globalPostersResponse.body() == null) isGlobalPostersResponseEmpty = true;
                }
                if (!basicShow) {
                    log.debug("getPosters: quering thetvdb for season in en");
                    globalSeasonsResponse = theTvdb.series()
                            .imagesQuery(showId, "season", null, null, "en").execute();
                    if (globalSeasonsResponse.code() == 401) authIssue = true; // this is an OR
                    if (globalSeasonsResponse.code() != 404) notFoundIssue = false; // this is an AND
                    if (globalSeasonsResponse.isSuccessful()) isGlobalSeasonsResponseOk = true;
                    if (globalSeasonsResponse.body() == null) isGlobalSeasonsResponseEmpty = true;
                }
            }

            if (authIssue) {
                log.debug("getPosters: auth error");
                myResult.status = ScrapeStatus.AUTH_ERROR;
                myResult.posters = ShowIdPostersResult.EMPTY_LIST;
                ShowScraper3.reauth();
                return myResult;
            }

            if (notFoundIssue) {
                log.debug("getPosters: not found");
                myResult.posters = ShowIdPostersResult.EMPTY_LIST;
                myResult.status = ScrapeStatus.NOT_FOUND;
            } else {
                if (isPostersResponseEmpty && isSeasonsResponseEmpty
                        && isGlobalPostersResponseEmpty && isGlobalSeasonsResponseEmpty) {
                    log.debug("getPosters: error");
                    myResult.posters = ShowIdPostersResult.EMPTY_LIST;
                    myResult.status = ScrapeStatus.ERROR_PARSER;
                } else {
                    log.debug("getPosters: found something going to parse result, isPostersResponseOk=" +
                            isPostersResponseOk +
                            ", isSeasonsResponseOk=" + isSeasonsResponseOk +
                            ", isGlobalPostersResponseOk=" + isGlobalPostersResponseOk +
                            ", isGlobalSeasonsResponseOk=" + isGlobalSeasonsResponseOk);
                    myResult = ShowIdPostersParser.getResult(showTitle,
                            (isPostersResponseOk) ? postersResponse.body() : null,
                            (isSeasonsResponseOk) ? seasonsResponse.body() : null,
                            (isGlobalPostersResponseOk) ? globalPostersResponse.body() : null,
                            (isGlobalSeasonsResponseOk) ? globalSeasonsResponse.body() : null,
                            basicShow, basicEpisode, language, context);
                    myResult.status = ScrapeStatus.OKAY;
                }
            }
        } catch (IOException e) {
            log.error("getActors: caught IOException getting actors for showId=" + showId);
            myResult.posters = ShowIdPostersResult.EMPTY_LIST;
            myResult.status = ScrapeStatus.ERROR_PARSER;
            myResult.reason = e;
        }
        return myResult;
    }
}
