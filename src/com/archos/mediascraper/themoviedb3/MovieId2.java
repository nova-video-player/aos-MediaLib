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

import com.archos.mediascraper.MovieTags;
import com.archos.mediascraper.ScrapeStatus;
import com.uwetrottmann.tmdb2.entities.Credits;
import com.uwetrottmann.tmdb2.entities.Movie;
import com.uwetrottmann.tmdb2.services.MoviesService;

import java.io.IOException;

import retrofit2.Response;

// Get the basic movie information for a specific movie id and language (ISO 639-1 code)
public class MovieId2 {
    private static final String TAG = MovieId2.class.getSimpleName();
    private static final boolean DBG = false;

    public static MovieIdResult getBaseInfo(long movieId, String language, MoviesService moviesService) {
        MovieIdResult myResult = new MovieIdResult();
        Response<Movie> movieResponse = null;
        Response<Credits> creditsResponse = null;
        MovieTags parserResult = null;

        if (DBG) Log.d(TAG, "getBaseInfo: quering tmdb for movieId " + movieId + " in " + language);
        try {
            movieResponse = moviesService.summary((int) movieId, language).execute();
            creditsResponse = moviesService.credits((int) movieId).execute();
            switch (movieResponse.code()) {
                case 401: // auth issue
                    if (DBG) Log.d(TAG, "search: auth error");
                    myResult.status = ScrapeStatus.AUTH_ERROR;
                    //TODO: MovieScraper3.reauth();
                    return myResult;
                case 404: // not found
                    // TODO: check year parsing because scraping still put a ( and do not remove it
                    myResult.status = ScrapeStatus.NOT_FOUND;
                    // fallback to english if no result
                    if (!language.equals("en")) {
                        if (DBG) Log.d(TAG, "getBaseInfo: retrying search for movieId " + movieId + " in en");
                        return getBaseInfo(movieId, "en", moviesService);
                    }
                    if (DBG) Log.d(TAG, "getBaseInfo: movieId " + movieId + " not found");
                    break;
                default:
                    if (movieResponse.isSuccessful()) {
                        if (movieResponse.body() != null) {
                            parserResult = MovieIdParser2.getResult(movieResponse.body(), creditsResponse.body());
                            myResult.tag = parserResult;
                            myResult.status = ScrapeStatus.OKAY;
                        } else {
                            myResult.status = ScrapeStatus.NOT_FOUND;
                        }
                    } else { // an error at this point is PARSER related
                        if (DBG) Log.d(TAG, "getBaseInfo: error " + movieResponse.code());
                        myResult.status = ScrapeStatus.ERROR_PARSER;
                    }
                    break;
            }
        } catch (IOException e) {
            Log.e(TAG, "getBaseInfo: caught IOException getting summary for movieId=" + movieId);
            myResult.status = ScrapeStatus.ERROR_PARSER;
            myResult.reason = e;
        }
        return myResult;
    }
}
