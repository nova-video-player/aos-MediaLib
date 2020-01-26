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

import com.archos.mediascraper.FileFetcher;
import com.archos.mediascraper.MovieTags;
import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.ScraperTrailer;
import com.uwetrottmann.tmdb2.entities.BaseCompany;
import com.uwetrottmann.tmdb2.entities.CastMember;
import com.uwetrottmann.tmdb2.entities.CrewMember;
import com.uwetrottmann.tmdb2.entities.Genre;
import com.uwetrottmann.tmdb2.entities.Movie;
import com.uwetrottmann.tmdb2.entities.Videos;
import com.uwetrottmann.tmdb2.enumerations.VideoType;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MovieIdParser2 {

    private static final String TAG = MovieIdParser2.class.getSimpleName();
    private static final boolean DBG = false;

    private static final String DIRECTOR = "Director";

    public static MovieTags getResult(Movie movie) {

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
        if (movie.title != null) result.setTitle(movie.title);
        if (movie.vote_average != null) result.setRating(movie.vote_average.floatValue());
        if (movie.credits != null) {
            if (movie.credits.guest_stars != null)
                for (CastMember guestStar: movie.credits.guest_stars)
                    result.addActorIfAbsent(guestStar.name, guestStar.character);
            if (movie.credits.cast != null)
                for (CastMember actor: movie.credits.cast)
                    result.addActorIfAbsent(actor.name, actor.character);
            if (movie.credits.crew != null)
                for (CrewMember crew: movie.credits.crew)
                    if (crew.job == DIRECTOR)
                        result.addDirectorIfAbsent(crew.name);
        }
        // TODO: missing certification i.e. setContentRating that should rely no CertificationService
        // TODO: add Collection belongs_to_collection
        result.setContentRating(null);
        if (movie.runtime != null) result.setRuntime(movie.runtime, TimeUnit.MINUTES);

        return result;
    }
}
