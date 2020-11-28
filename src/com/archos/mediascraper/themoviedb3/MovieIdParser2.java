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
import com.archos.mediascraper.ScraperImage;
import com.uwetrottmann.tmdb2.entities.BaseCompany;
import com.uwetrottmann.tmdb2.entities.CastMember;
import com.uwetrottmann.tmdb2.entities.Credits;
import com.uwetrottmann.tmdb2.entities.CrewMember;
import com.uwetrottmann.tmdb2.entities.Genre;
import com.uwetrottmann.tmdb2.entities.Image;
import com.uwetrottmann.tmdb2.entities.Images;
import com.uwetrottmann.tmdb2.entities.Movie;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class MovieIdParser2 {

    private static final String TAG = MovieIdParser2.class.getSimpleName();
    private static final boolean DBG = false;

    private static final String DIRECTOR = "Director";

    public static MovieTags getResult(Movie movie, Credits credits, Context context) {

        MovieTags result = new MovieTags();
        if (movie.id != null) result.setOnlineId(movie.id);
        if (movie.genres != null)
            for (Genre genre: movie.genres)
                result.addGenreIfAbsent(genre.name);
        if (movie.imdb_id != null) result.setImdbId(movie.imdb_id);
        if (movie.overview != null) result.setPlot(movie.overview);
        if (movie.production_companies != null)
            for (BaseCompany productionCompany: movie.production_companies)
                result.addStudioIfAbsent(productionCompany.name);
        if (movie.release_date != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(movie.release_date);
            result.setYear(cal.get(Calendar.YEAR));
        }
        if (movie.belongs_to_collection != null) {
            if (DBG) Log.d(TAG, "getResult collection id: " + movie.belongs_to_collection.id + ", for " + movie.belongs_to_collection.name);
            result.setCollectionId(movie.belongs_to_collection.id);
            result.setCollectionBackdropPath(movie.belongs_to_collection.backdrop_path);
            result.setCollectionPosterPath(movie.belongs_to_collection.poster_path);
            result.setCollectionName(movie.belongs_to_collection.name);
            if (DBG) Log.d(TAG, "getResult collection overview: " + movie.belongs_to_collection.overview);
        } else
            result.setCollectionId(-1);
        if (movie.title != null) result.setTitle(movie.title);
        if (movie.vote_average != null)
            result.setRating(movie.vote_average.floatValue());
        if (credits != null)
            if (credits.guest_stars != null)
                for (CastMember guestStar: credits.guest_stars)
                    result.addActorIfAbsent(guestStar.name, guestStar.character);
            if (credits.cast != null)
                for (CastMember actor: credits.cast)
                    result.addActorIfAbsent(actor.name, actor.character);
            if (credits.crew != null)
                for (CrewMember crew: credits.crew)
                    if (crew.job == DIRECTOR)
                        result.addDirectorIfAbsent(crew.name);
        // TODO: missing certification i.e. setContentRating that should rely no CertificationService
        result.setContentRating(null);
        if (movie.runtime != null) result.setRuntime(movie.runtime, TimeUnit.MINUTES);

        return result;
    }
}
