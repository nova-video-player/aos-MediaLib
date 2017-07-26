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
import com.archos.mediascraper.MovieTags;
import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.FileFetcher.FileFetchResult;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/*
 * /3/movie/{id}
 *
 * Get the basic movie information for a specific movie id.
 *
 * Required Parameters
 * api_key
 *
 * Optional Parameters
 * language             ISO 639-1 code.
 */
public class MovieId {
    private static final String TAG = MovieId.class.getSimpleName();
    private static final boolean DBG = false;

    private static final String METHOD = "movie";

    private static final String KEY_LANGUAGE = "language";

    // we can now append subqueries, sadly images does not work since
    // that requires a request with no language set
    private static final String KEY_APPEND = "append_to_response";
    private static final String APPENDED = "casts,releases";

    public static MovieIdResult getBaseInfo(long movieId, String language, FileFetcher fetcher) {
        MovieIdResult myResult = new MovieIdResult();

        FileFetchResult fileResult = getFile(fetcher, movieId, language);

        if (fileResult.status != ScrapeStatus.OKAY) {
            myResult.status = fileResult.status;
            return myResult;
        }

        MovieTags parserResult = null;
        try {
            parserResult = MovieIdParser.getInstance().readJsonFile(fileResult.file, null);
            myResult.tag = parserResult;
            myResult.status = ScrapeStatus.OKAY;
        } catch (IOException e) {
            if (DBG) Log.e(TAG, e.getMessage(), e);
            myResult.status = ScrapeStatus.ERROR_PARSER;
            myResult.reason = e;
        }
        return myResult;
    }

    private static FileFetchResult getFile(FileFetcher fetcher, long movieId, String language) {
        Map<String, String> queryParams = new HashMap<String, String>();
        TheMovieDb3.putIfNonEmpty(queryParams, KEY_LANGUAGE, language);
        queryParams.put(KEY_APPEND, APPENDED);
        String requestUrl = TheMovieDb3.buildUrl(queryParams, METHOD + "/" + movieId);
        if (DBG) Log.d(TAG, "REQUEST: [" + requestUrl + "]");
        return fetcher.getFile(requestUrl);
    }
}
