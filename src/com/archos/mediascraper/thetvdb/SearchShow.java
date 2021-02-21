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

import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.preprocess.TvShowSearchInfo;
import com.archos.mediascraper.xml.ShowScraper3;
import com.uwetrottmann.thetvdb.entities.SeriesResultsResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import retrofit2.Response;

// Search Show for name query for year in language (ISO 639-1 code) and en
public class SearchShow {
    private static final Logger log = LoggerFactory.getLogger(SearchShow.class);

    public static SearchShowResult search(TvShowSearchInfo searchInfo, String language, int resultLimit, ShowScraper3 showScraper, MyTheTVdb theTvdb) {
        SearchShowResult myResult = new SearchShowResult();
        Response<SeriesResultsResponse> response = null;
        Response<SeriesResultsResponse> globalResponse = null;

        boolean authIssue = false;
        boolean notFoundIssue = true;

        boolean isResponseOk = false;
        boolean isResponseEmpty = false;
        boolean isGlobalResponseOk = false;
        boolean isGlobalResponseEmpty = false;

        log.debug("search: quering thetvdb for " + searchInfo.getShowName() + " in " + language);
        try {
            response = theTvdb.search().series(searchInfo.getShowName(), null, null, null, language).execute();
            if (response.code() == 401) authIssue = true; // this is an OR
            if (response.code() != 404) notFoundIssue = false; // this is an AND
            if (response.isSuccessful()) isResponseOk = true;
            if (response.body() == null) isResponseEmpty = true;
            if (!language.equals("en")) {
                globalResponse = theTvdb.search().series(searchInfo.getShowName(), null, null, null, "en").execute();
                if (globalResponse.code() == 401) authIssue = true; // this is an OR
                if (globalResponse.code() != 404) notFoundIssue = false; // this is an AND
                if (globalResponse.isSuccessful()) isGlobalResponseOk = true;
                if (globalResponse.body() == null) isGlobalResponseEmpty = true;
            }
            if (authIssue) {
                log.debug("search: auth error");
                myResult.status = ScrapeStatus.AUTH_ERROR;
                myResult.result = SearchShowResult.EMPTY_LIST;
                ShowScraper3.reauth();
                return myResult;
            }
            if (notFoundIssue) {
                log.debug("search: not found");
                myResult.result = SearchShowResult.EMPTY_LIST;
                myResult.status = ScrapeStatus.NOT_FOUND;
            } else {
                if (isResponseEmpty && isGlobalResponseEmpty) {
                    log.debug("search: error");
                    myResult.result = SearchShowResult.EMPTY_LIST;
                    myResult.status = ScrapeStatus.ERROR_PARSER;
                } else {
                    myResult.result = SearchShowParser.getResult(
                            (isResponseOk) ? response.body() : null,
                            (isGlobalResponseOk) ? globalResponse.body() : null,
                            searchInfo, language, resultLimit, showScraper);
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
}
