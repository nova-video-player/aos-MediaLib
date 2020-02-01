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
import com.archos.mediascraper.ScraperTrailer;
import com.archos.mediascraper.xml.MovieScraper3;
import com.uwetrottmann.tmdb2.entities.Videos;
import com.uwetrottmann.tmdb2.services.MoviesService;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Response;

// get all the trailers for movieID in language (ISO 639-1 code)
public class SearchMovieTrailer2 {
    private static final String TAG = SearchMovieTrailer2.class.getSimpleName();
    private static final boolean DBG = false;

    public static SearchMovieTrailerResult addTrailers(long movieId, MovieTags tag, String language, MoviesService moviesService) {
        List<SearchMovieTrailerResult.TrailerResult> parserResult = null;
        SearchMovieTrailerResult myResult = new SearchMovieTrailerResult();
        Response<Videos> videosResponse = null;
        Response<Videos> videosResponseEn = null;
        try {
            videosResponse = moviesService.videos((int) movieId, language).execute();
            if (videosResponse.code() == 401) {
                myResult.status = ScrapeStatus.AUTH_ERROR;
                MovieScraper3.reauth();
                return myResult;
            }
            if (videosResponse.isSuccessful() && videosResponse.body() != null)
                parserResult = SearchMovieTrailerParser2.getResult(videosResponse.body());
            if (!language.equals("en")) { //also ask for english trailers
                videosResponseEn = moviesService.videos((int) movieId, "en").execute();
                if (videosResponseEn.code() == 401) {
                    myResult.status = ScrapeStatus.AUTH_ERROR;
                    MovieScraper3.reauth();
                    return myResult;
                }
                if (videosResponseEn.isSuccessful() && videosResponseEn.body() != null) {
                    List<SearchMovieTrailerResult.TrailerResult> parserResultEn = SearchMovieTrailerParser2.getResult(videosResponseEn.body());
                    if (parserResultEn != null)
                        parserResult.addAll(parserResultEn);
                }
            }
            myResult.result = parserResult;
            if (parserResult.isEmpty()) {
                myResult.status = ScrapeStatus.NOT_FOUND;
                tag.setTrailers(new ArrayList<ScraperTrailer>());
            } else {
                myResult.status = ScrapeStatus.OKAY;
                List<ScraperTrailer> trailers = new ArrayList<>(parserResult.size());
                for (SearchMovieTrailerResult.TrailerResult trailerResult : parserResult) {
                    if (DBG) Log.d(TAG, "addTrailers: found " + trailerResult.getName() + " for service " + trailerResult.getService() + " of type " + trailerResult.getType());
                    if(trailerResult.getService().equals("YouTube") &&("Trailer".equals(trailerResult.getType())||"Teaser".equals(trailerResult.getType()))) {
                        ScraperTrailer trailer = new ScraperTrailer(ScraperTrailer.Type.MOVIE_TRAILER, trailerResult.getName(), trailerResult.getKey(),
                                trailerResult.getService(), trailerResult.getLanguage());
                        trailers.add(trailer);
                    }
                }
                tag.setTrailers(trailers);
            }
        } catch(Exception e) {
            if (DBG) Log.e(TAG, e.getMessage(), e);
            myResult.result = SearchMovieTrailerResult.EMPTY_LIST;
            myResult.status = ScrapeStatus.ERROR_PARSER;
            myResult.reason = e;
        }
        return myResult;
    }
}
