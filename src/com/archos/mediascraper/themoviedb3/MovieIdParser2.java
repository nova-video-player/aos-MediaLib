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

import com.archos.medialib.R;
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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MovieIdParser2 {

    private static final String TAG = MovieIdParser2.class.getSimpleName();
    private static final boolean DBG = false;

    private static final String DIRECTOR = "Director";

    private static Context mContext;

    public static MovieTags getResult(Movie movie, Credits credits, Context context) {
        mContext = context;
        MovieTags result = new MovieTags();
        if (movie.id != null) result.setOnlineId(movie.id);
        if (movie.genres != null) {
            List<String> localizedGenres = getLocalizedGenres(movie.genres);
            for (String genre : localizedGenres)
                result.addGenreIfAbsent(genre);
        }
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

    // many genres are not translated on tmdb and localized request is returned in the local language making one to
    // one mapping difficult without changing all db structure --> revert to show trick which is the only way to cope
    // with fallback search in en is perfomed when localized search returns nothing
    private static List<String> getLocalizedGenres(List<Genre> genres) {
        ArrayList<String> localizedGenres = new ArrayList<>();
        for (Genre genre : genres) {
            switch (genre.id) {
                case 28:
                    localizedGenres.add(mContext.getString(R.string.movie_genre_action));
                    break;
                case 12:
                    localizedGenres.add(mContext.getString(R.string.movie_genre_adventure));
                    break;
                case 16:
                    localizedGenres.add(mContext.getString(R.string.movie_genre_animation));
                    break;
                case 35:
                    localizedGenres.add(mContext.getString(R.string.movie_genre_comedy));
                    break;
                case 80:
                    localizedGenres.add(mContext.getString(R.string.movie_genre_crime));
                    break;
                case 99:
                    localizedGenres.add(mContext.getString(R.string.movie_genre_documentary));
                    break;
                case 18:
                    localizedGenres.add(mContext.getString(R.string.movie_genre_drama));
                    break;
                case 10751:
                    localizedGenres.add(mContext.getString(R.string.movie_family));
                    break;
                case 14:
                    localizedGenres.add(mContext.getString(R.string.movie_genre_fantasy));
                    break;
                case 36:
                    localizedGenres.add(mContext.getString(R.string.movie_genre_history));
                    break;
                case 27:
                    localizedGenres.add(mContext.getString(R.string.movie_genre_horror));
                    break;
                case 10402:
                    localizedGenres.add(mContext.getString(R.string.movie_genre_music));
                    break;
                case 9648:
                    localizedGenres.add(mContext.getString(R.string.movie_genre_mystery));
                    break;
                case 10749:
                    localizedGenres.add(mContext.getString(R.string.movie_genre_romance));
                    break;
                case 878:
                    localizedGenres.add(mContext.getString(R.string.movie_genre_science_fiction));
                    break;
                case 10770:
                    localizedGenres.add(mContext.getString(R.string.movie_genre_tv_movie));
                    break;
                case 53:
                    localizedGenres.add(mContext.getString(R.string.movie_genre_thriller));
                    break;
                case 10752:
                    localizedGenres.add(mContext.getString(R.string.movie_genre_war));
                    break;
                case 37:
                    localizedGenres.add(mContext.getString(R.string.movie_genre_western));
                    break;
                default:
                    Log.w(TAG, "unknown genre: id=" + genre.id + ", name=" + genre.name);
                    localizedGenres.add(genre.name);
            }
        }
        return localizedGenres;
    }
}
