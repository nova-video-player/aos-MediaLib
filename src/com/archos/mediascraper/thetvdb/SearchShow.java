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

import android.util.Log;

import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.SearchResult;
import com.archos.mediascraper.preprocess.TvShowSearchInfo;
import com.archos.mediascraper.xml.ShowScraper3;
import com.uwetrottmann.thetvdb.entities.SeriesResultsResponse;

import java.io.IOException;
import java.util.List;

import retrofit2.Response;

// Search Show for name query for year in language (ISO 639-1 code)
public class SearchShow {
    private static final String TAG = SearchShow.class.getSimpleName();
    private static final boolean DBG = false;

    public static SearchShowResult search(TvShowSearchInfo searchInfo, String language, int resultLimit, ShowScraper3 showScraper, MyTheTVdb theTvdb) {
        SearchShowResult myResult = new SearchShowResult();

        List<SearchResult> parserResult = null;
        Response<SeriesResultsResponse> response = null;

        if (DBG) Log.d(TAG, "getMatches2: quering thetvdb for " + searchInfo.getShowName() + " in " + language);
        try {
            response = theTvdb.search().series(searchInfo.getShowName(), null, null, null, language).execute();
            switch (response.code()) {
                case 401: // auth issue
                    if (DBG) Log.d(TAG, "search: auth error");
                    myResult.result = SearchShowResult.EMPTY_LIST;
                    myResult.status = ScrapeStatus.AUTH_ERROR;
                    ShowScraper3.reauth();
                    return myResult;
                case 404: // not found
                    myResult.status = ScrapeStatus.NOT_FOUND;
                    if (!language.equals("en")) {
                        if (DBG) Log.d(TAG, "search: retrying search for '" + searchInfo.getShowName() + "' in en.");
                        return search(searchInfo, "en", resultLimit, showScraper, theTvdb);
                    }
                    if (DBG) Log.d(TAG, "search: " + searchInfo.getShowName() + " not found");
                    break;
                default:
                    // TODO: combine search in en + language sort by language and levenstein then treat by id to revert to language
                    if (response.isSuccessful()) {
                        if (response.body() != null) {
                            parserResult = SearchShowParser.getResult(response, searchInfo, language, resultLimit, showScraper);
                            myResult.result = parserResult;
                            myResult.status = ScrapeStatus.OKAY;
                        } else {
                            myResult.status = ScrapeStatus.NOT_FOUND;
                        }
                    } else { // an error at this point is PARSER related
                        if (DBG) Log.d(TAG, "search: response is not successful for " + searchInfo.getShowName());
                        myResult.status = ScrapeStatus.ERROR_PARSER;
                    }
                    break;
            }
        } catch (IOException e) {
            Log.e(TAG, "search: caught IOException");
            if (DBG) Log.e(TAG, e.getMessage(), e);
            myResult.result = SearchShowResult.EMPTY_LIST;
            myResult.status = ScrapeStatus.ERROR_PARSER;
            myResult.reason = e;
        }
        return myResult;
    }
}
