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
import android.util.Log;

import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.xml.ShowScraper3;
import com.uwetrottmann.thetvdb.entities.SeriesImageQueryResultResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import retrofit2.Response;

// Get the backdrops for specific show id
public class ShowIdBackdrops {
    private static final Logger log = LoggerFactory.getLogger(ShowIdBackdrops.class);

    public static ShowIdBackdropsResult getBackdrops(int showId, String showTitle,
                                               boolean basicShow, boolean basicEpisode,
                                               String language, MyTheTVdb theTvdb, Context context) {
        ShowIdBackdropsResult myResult = new ShowIdBackdropsResult();
        log.debug("getBackdrops: for showTitle=" + showTitle + ", showId=" + showId);

        boolean authIssue = false;
        boolean notFoundIssue = true;

        boolean isFanartsResponseOk = false;
        boolean isFanartsResponseEmpty = false;
        boolean isGlobalFanartsResponseOk = false;
        boolean isGlobalFanartsResponseEmpty = false;

        log.debug("getBackdrops: quering thetvdb for showId " + showId);
        try {
            log.debug("ShowIdBackdropsResult: no boost for " + showId);
            Response<SeriesImageQueryResultResponse> fanartsResponse = null;
            fanartsResponse = theTvdb.series()
                    .imagesQuery(showId, "fanart", null, null, language).execute();
            if (fanartsResponse.code() == 401) authIssue = true; // this is an OR
            if (fanartsResponse.code() != 404) notFoundIssue = false; // this is an AND
            if (fanartsResponse.isSuccessful()) isFanartsResponseOk = true;
            if (fanartsResponse.body() == null) isFanartsResponseEmpty = true;

            Response<SeriesImageQueryResultResponse> globalFanartsResponse = null;
            if (!language.equals("en")) {
                globalFanartsResponse = theTvdb.series()
                        .imagesQuery(showId, "fanart", null, null, "en").execute();
                if (globalFanartsResponse.code() == 401) authIssue = true; // this is an OR
                if (globalFanartsResponse.code() != 404) notFoundIssue = false; // this is an AND
                if (globalFanartsResponse.isSuccessful()) isGlobalFanartsResponseOk = true;
                if (globalFanartsResponse.body() == null) isGlobalFanartsResponseEmpty = true;
            }

            if (authIssue) {
                log.debug("getBackdrops: auth error");
                myResult.status = ScrapeStatus.AUTH_ERROR;
                myResult.backdrops = ShowIdBackdropsResult.EMPTY_LIST;
                ShowScraper3.reauth();
                return myResult;
            }

            if (notFoundIssue) {
                log.debug("getBackdrops: not found");
                myResult.backdrops = ShowIdBackdropsResult.EMPTY_LIST;
                myResult.status = ScrapeStatus.NOT_FOUND;
            } else {
                if (isFanartsResponseEmpty && isGlobalFanartsResponseEmpty) {
                    log.debug("getBackdrops: error ");
                    myResult.backdrops = ShowIdBackdropsResult.EMPTY_LIST;
                    myResult.status = ScrapeStatus.ERROR_PARSER;
                } else {
                    myResult = ShowIdBackdropsParser.getResult(showTitle,
                            (isFanartsResponseOk) ? fanartsResponse.body() : null,
                            (isGlobalFanartsResponseOk) ? globalFanartsResponse.body() : null,
                            language, context);
                    myResult.status = ScrapeStatus.OKAY;
                }
            }
        } catch (IOException e) {
            log.error("getBackdrops: caught IOException getting actors for showId=" + showId);
            myResult.status = ScrapeStatus.ERROR_PARSER;
            myResult.reason = e;
        }
        return myResult;
    }
}
