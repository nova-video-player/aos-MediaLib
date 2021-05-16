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
import android.util.Log;

import com.archos.mediascraper.MovieTags;
import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.xml.MovieScraper3;
import com.uwetrottmann.tmdb2.entities.AppendToResponse;
import com.uwetrottmann.tmdb2.entities.Credits;
import com.uwetrottmann.tmdb2.entities.Movie;
import com.uwetrottmann.tmdb2.enumerations.AppendToResponseItem;
import com.uwetrottmann.tmdb2.services.MoviesService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import retrofit2.Response;

// Get the basic movie information for a specific movie id and language (ISO 639-1 code)
public class MovieId2 {
    private static final Logger log = LoggerFactory.getLogger(MovieId2.class);

    // specify image language include_image_language=en,null
    private final static Map<String, String> options  = new HashMap<String, String>() {{
        put("include_image_language", "en,null");
    }};

    public static MovieIdResult getBaseInfo(long movieId, String language, MoviesService moviesService, Context context) {
        MovieIdResult myResult = new MovieIdResult();
        Response<Movie> movieResponse = null;
        Response<Credits> creditsResponse = null;
        MovieTags parserResult = null;

        log.debug("getBaseInfo: quering tmdb for movieId " + movieId + " in " + language);
        try {
            movieResponse = moviesService.summary((int) movieId, language, new AppendToResponse(AppendToResponseItem.EXTERNAL_IDS, AppendToResponseItem.IMAGES, AppendToResponseItem.CREDITS, AppendToResponseItem.CONTENT_RATINGS, AppendToResponseItem.VIDEOS), options).execute();
            switch (movieResponse.code()) {
                case 401: // auth issue
                    log.debug("search: auth error");
                    myResult.status = ScrapeStatus.AUTH_ERROR;
                    MovieScraper3.reauth();
                    return myResult;
                case 404: // not found
                    myResult.status = ScrapeStatus.NOT_FOUND;
                    // fallback to english if no result
                    if (!language.equals("en")) {
                        log.debug("getBaseInfo: retrying search for movieId " + movieId + " in en");
                        return getBaseInfo(movieId, "en", moviesService, context);
                    }
                    log.debug("getBaseInfo: movieId " + movieId + " not found");
                    break;
                default:
                    if (movieResponse.isSuccessful()) {
                        if (movieResponse.body() != null) {
                            parserResult = MovieIdParser2.getResult(movieResponse.body(), context);
                            myResult.tag = parserResult;
                            myResult.status = ScrapeStatus.OKAY;
                        } else {
                            if (!language.equals("en")) {
                                log.debug("getBaseInfo: retrying search for movieId " + movieId + " in en");
                                return getBaseInfo(movieId, "en", moviesService, context);
                            }
                            myResult.status = ScrapeStatus.NOT_FOUND;
                        }
                    } else { // an error at this point is PARSER related
                        log.debug("getBaseInfo: error " + movieResponse.code());
                        myResult.status = ScrapeStatus.ERROR_PARSER;
                    }
                    break;
            }
        } catch (IOException e) {
            log.error("getBaseInfo: caught IOException getting summary for movieId=" + movieId);
            myResult.status = ScrapeStatus.ERROR_PARSER;
            myResult.reason = e;
        }
        return myResult;
    }
}
