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
import com.uwetrottmann.tmdb2.entities.Movie;
import com.uwetrottmann.tmdb2.services.MoviesService;

import java.io.IOException;

import retrofit2.Response;

/*
 * /3/movie/{id}
 *
 * !Getting just "overview" from no language version
 *
 * Required Parameters
 * api_key
 *
 * Optional Parameters
 * language             ISO 639-1 code.
 */
public class MovieIdDescription2 {
    private static final String TAG = MovieIdDescription2.class.getSimpleName();
    private static final boolean DBG = false;

    public static boolean addDescription(long movieId, MovieTags tag, MoviesService moviesService) {
        if (tag == null)
            return false;
        Response<Movie> movieResponse = null;
        try {
            movieResponse = moviesService.summary((int) movieId, "en").execute();
        } catch (IOException e) {
            Log.e(TAG, "addImages: caught IOException getting summary");
        }

        if (! movieResponse.isSuccessful())
            return false;

        if (movieResponse.body() != null) {
            Movie movie = movieResponse.body();
            String description = movie.overview;
            if (description != null && !description.isEmpty())
                tag.setPlot(description);
            return true;
        }
        return false;
    }
}
