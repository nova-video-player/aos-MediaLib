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

import android.util.Log;

import com.archos.mediascraper.FileFetcher;
import com.archos.mediascraper.MovieTags;
import com.archos.mediascraper.ScrapeDetailResult;
import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.FileFetcher.FileFetchResult;
import com.uwetrottmann.tmdb2.entities.Movie;
import com.uwetrottmann.tmdb2.services.MoviesService;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import retrofit2.Response;

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
public class MovieId2 {
    private static final String TAG = MovieId2.class.getSimpleName();
    private static final boolean DBG = false;

    public static MovieIdResult getBaseInfo(long movieId, String language, MoviesService moviesService) {
        MovieIdResult myResult = new MovieIdResult();

        Response<Movie> movieResponse = null;

        try {
            movieResponse = moviesService.summary((int) movieId, language).execute();
            // TODO ADD if (! movieResponse.isSuccessful())
            // fallback to english if no result
            // TODO: check if this works as a fallback
            if (movieResponse.body() == null && !language.equals("en")) {
                // TODO ADD if (! movieResponse.isSuccessful())
                movieResponse = moviesService.summary((int) movieId, "en").execute();
            }
        } catch (IOException e) {
            Log.e(TAG, "getDetailsInternal: caught IOException getting summary");
        }
        if (movieResponse.isSuccessful() && movieResponse.body() != null) {
            if (DBG) Log.d(TAG, "getBaseInfo: found something");
            myResult.status  = ScrapeStatus.OKAY;
        } else { // TODO: probably treat other cases of errors
            myResult.status = ScrapeStatus.ERROR;
            if (DBG) Log.d(TAG, "getBaseInfo: error " + movieResponse.code());
            return myResult;
        }

        MovieTags parserResult = null;
        // TODO MARC: there is no exception there --> remove
        try {
            parserResult = MovieIdParser2.getResult(movieResponse.body());
            myResult.tag = parserResult;
            myResult.status = ScrapeStatus.OKAY;
        } catch (Exception e) {
            if (DBG) Log.e(TAG, e.getMessage(), e);
            myResult.status = ScrapeStatus.ERROR_PARSER;
            myResult.reason = e;
        }
        return myResult;
    }

}
