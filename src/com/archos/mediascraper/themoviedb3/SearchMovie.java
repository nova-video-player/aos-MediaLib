// Copyright 2017 Archos SA
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

import android.util.Log;

import com.archos.mediascraper.FileFetcher;
import com.archos.mediascraper.FileFetcher.FileFetchResult;
import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.SearchResult;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * /3/search/movie
 *
 * Required Parameters
 * api_key
 * query            CGI escaped string
 *
 * Optional Parameters
 * page
 * language         ISO 639-1 code.
 * include_adult    Toggle the inclusion of adult titles.
 * year             Filter results to only include this value.
 */
public class SearchMovie {
    private static final String TAG = SearchMovie.class.getSimpleName();
    private static final boolean DBG = false;

    private static final String METHOD = "search/movie";

    private static final String KEY_QUERY = "query";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_YEAR = "year";

    public static SearchMovieResult search(String query, String language, String year, int resultLimit, FileFetcher fetcher) {
        SearchMovieResult myResult = new SearchMovieResult();

        FileFetchResult fileResult = getFile(query, language, year, fetcher);
        if (fileResult.status != ScrapeStatus.OKAY) {
            myResult.status = fileResult.status;
            return myResult;
        }

        List<SearchResult> parserResult = null;
        try {
            parserResult = SearchMovieParser.getInstance().readJsonFile(fileResult.file, resultLimit);
            myResult.result = parserResult;
            if (parserResult.isEmpty()) {
                // retry without year since some movies are tagged wrong
                if (year != null) {
                    if (DBG) Log.d(TAG, "retrying search for '" + query + "' without year.");
                    return search(query, language, null, resultLimit, fetcher);
                }
                myResult.status = ScrapeStatus.NOT_FOUND;
            } else {
                myResult.status = ScrapeStatus.OKAY;
            }
        } catch (IOException e) {
            if (DBG) Log.e(TAG, e.getMessage(), e);
            myResult.result = SearchMovieResult.EMPTY_LIST;
            myResult.status = ScrapeStatus.ERROR_PARSER;
            myResult.reason = e;
        }

        return myResult;
    }

    private static FileFetchResult getFile(String query, String language, String year, FileFetcher fetcher) {
        Map<String, String> queryParams = new HashMap<String, String>();
        TheMovieDb3.putIfNonEmpty(queryParams, KEY_QUERY, query);
        TheMovieDb3.putIfNonEmpty(queryParams, KEY_LANGUAGE, language);
        TheMovieDb3.putIfNonEmpty(queryParams, KEY_YEAR, year);
        String requestUrl = TheMovieDb3.buildUrl(queryParams, METHOD);
        if (DBG) Log.d(TAG, "REQUEST: [" + requestUrl + "]");
        return fetcher.getFile(requestUrl);
    }
}
