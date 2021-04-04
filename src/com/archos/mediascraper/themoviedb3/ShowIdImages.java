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

import android.content.Context;

import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.xml.ShowScraper3;
import com.uwetrottmann.tmdb2.entities.Images;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import retrofit2.Response;

// Get the backdrops/posters for specific show id
public class ShowIdImages {
    private static final Logger log = LoggerFactory.getLogger(ShowIdBackdrops.class);

    public static ShowIdImagesResult getImages(int showId, String showTitle,
                                               boolean basicShow, boolean basicEpisode,
                                               String language, MyTmdb tmdb, Context context) {
        ShowIdImagesResult myResult = new ShowIdImagesResult();
        log.debug("getImages: for showTitle=" + showTitle + ", showId=" + showId);

        boolean authIssue = false;
        boolean notFoundIssue = true;

        boolean isFanartsResponseOk = false;
        boolean isFanartsResponseEmpty = false;
        boolean isGlobalFanartsResponseOk = false;
        boolean isGlobalFanartsResponseEmpty = false;

        log.debug("getImages: quering thetvdb for showId " + showId);
        try {
            Response<Images> fanartsResponse = null;
            fanartsResponse = tmdb.tvService().images(showId, language).execute();
            if (fanartsResponse.code() == 401) authIssue = true; // this is an OR
            if (fanartsResponse.code() != 404) notFoundIssue = false; // this is an AND
            if (fanartsResponse.isSuccessful()) isFanartsResponseOk = true;
            if (fanartsResponse.body() == null) isFanartsResponseEmpty = true;

            Response<Images> globalFanartsResponse = null;
            if (!language.equals("en")) {
                globalFanartsResponse = tmdb.tvService().images(showId, "en").execute();
                if (globalFanartsResponse.code() == 401) authIssue = true; // this is an OR
                if (globalFanartsResponse.code() != 404) notFoundIssue = false; // this is an AND
                if (globalFanartsResponse.isSuccessful()) isGlobalFanartsResponseOk = true;
                if (globalFanartsResponse.body() == null) isGlobalFanartsResponseEmpty = true;
            }

            if (authIssue) {
                log.debug("getImages: auth error");
                myResult.status = ScrapeStatus.AUTH_ERROR;
                myResult.backdrops = ShowIdBackdropsResult.EMPTY_LIST;
                ShowScraper3.reauth();
                return myResult;
            }

            if (notFoundIssue) {
                log.debug("getImages: not found");
                myResult.backdrops = ShowIdBackdropsResult.EMPTY_LIST;
                myResult.status = ScrapeStatus.NOT_FOUND;
            } else {
                if (isFanartsResponseEmpty && isGlobalFanartsResponseEmpty) {
                    log.debug("getBackdrops: error ");
                    myResult.backdrops = ShowIdBackdropsResult.EMPTY_LIST;
                    myResult.status = ScrapeStatus.ERROR_PARSER;
                } else {
                    myResult = ShowIdImagesParser.getResult(showTitle,
                            (isFanartsResponseOk) ? fanartsResponse.body() : null,
                            (isGlobalFanartsResponseOk) ? globalFanartsResponse.body() : null,
                            language, context);
                    myResult.status = ScrapeStatus.OKAY;
                }
            }
        } catch (IOException e) {
            log.error("getImages: caught IOException getting actors for showId=" + showId);
            myResult.status = ScrapeStatus.ERROR_PARSER;
            myResult.reason = e;
        }
        return myResult;
    }
}
