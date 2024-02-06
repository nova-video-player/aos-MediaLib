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

import android.util.Pair;

import com.archos.mediascraper.ScraperImage;
import com.archos.mediascraper.SearchResult;
import com.uwetrottmann.tmdb2.entities.BaseMovie;
import com.uwetrottmann.tmdb2.entities.MovieResultsPage;

import org.apache.commons.text.similarity.LevenshteinDistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import retrofit2.Response;

public class SearchMovieParser2 {
    private static final Logger log = LoggerFactory.getLogger(SearchMovieParser2.class);

    private final static boolean SORT_POPULARITY = true; // used only if year specified
    private final static boolean SORT_YEAR = true; // used only if no year specified

    private final static LevenshteinDistance levenshteinDistance = new LevenshteinDistance();

    public static List<SearchResult> getResult(Response<MovieResultsPage> response, String movieName, String language, String year, Integer maxItems) {
        List<SearchResult> results;
        SearchParserResult searchMovieParserResult = new SearchParserResult();
        if (response != null)
            searchMovieParserResult = getSearchMovieParserResult(response, movieName, language, year);
        results = searchMovieParserResult.getResults(maxItems);
        return results;
    }

    private static SearchParserResult getSearchMovieParserResult(Response<MovieResultsPage> response, String movieName, String language, String year) {
        SearchParserResult searchMovieParserResult = new SearchParserResult();
        int levenshteinDistanceTitle, levenshteinDistanceOriginalTitle;
        log.debug("getSearchMovieParserResult: examining response of " + response.body().total_results + " entries in " + language + ", for " + movieName + " and specific year " + year);

        // sort first movies by popularity so that distinction between levenstein distance is operated on popularity
        List<BaseMovie> resultsMovie = response.body().results;
        // popularity sort is disabled to enable sort by year to pick lower year if not specified with lowest levenshtein metric
        // if year is specified pick movies with highest popularity (solves The Killer 1989 best pick)
        if (SORT_POPULARITY && year != null && ! year.isEmpty())
            Collections.sort(resultsMovie, new Comparator<BaseMovie>() {
                @Override
                public int compare(final BaseMovie bm1, final BaseMovie bm2) {
                    if (bm1.popularity == null && bm2.popularity == null) return 0;
                    if (bm1.popularity == null) return -1;
                    if (bm2.popularity == null) return 1;
                    if (bm1.popularity > bm2.popularity) {
                        return -1;
                    } else if (bm1.popularity.equals(bm2.popularity)) {
                        return 0;
                    } else {
                        return 1;
                    }
                }
            });
        // prefer older movie first if no specific year has been requested
        if (SORT_YEAR && (year == null || year.isEmpty()))
            Collections.sort(resultsMovie, new Comparator<BaseMovie>() {
                @Override
                public int compare(final BaseMovie bm1, final BaseMovie bm2) {
                    if (bm1.release_date == null && bm2.release_date == null) return 0;
                    if (bm1.release_date == null) return -1;
                    if (bm2.release_date == null) return 1;
                    if (bm1.release_date.before(bm2.release_date)) {
                        return -1;
                    } else if (bm1.release_date.equals(bm2.release_date)) {
                        return 0;
                    } else {
                        return 1;
                    }
                }
            });

        boolean isReleaseDateKnown = false;
        for (BaseMovie movie : resultsMovie) {
            log.debug("getSearchMovieParserResult: " + movie.original_title + " releaseDate " + ((movie.release_date != null) ? movie.release_date.toString() : null));

            SearchResult result = new SearchResult();
            result.setMovie();
            if (movie.id != null) result.setId(movie.id);
            if (movie.title != null) result.setTitle(movie.title);
            log.debug("getSearchMovieParserResult: taking into account " + movie.original_title);
            // add backdrop and poster here already if it exists because MovieIdImages can return empty results...
            log.debug("getSearchMovieParserResult: poster path " + movie.poster_path);
            if (movie.poster_path != null) result.setPosterPath(movie.poster_path);
            log.debug("getSearchMovieParserResult: backdrop path " + movie.backdrop_path);
            if (movie.backdrop_path != null) result.setBackdropPath(movie.backdrop_path);
            if (movie.original_title != null) result.setOriginalTitle(movie.original_title);
            result.setYear((year != null) ? String.valueOf(year) : null);
            result.setLanguage(language);

            // Put in lower priority any entry that has no movie banned i.e. .*missing/movie.jpg as banner
            isReleaseDateKnown = (movie.release_date != null);
            String movieNameLC = movieName.toLowerCase();
            levenshteinDistanceTitle = levenshteinDistance.apply(movieNameLC, result.getTitle().toLowerCase());
            levenshteinDistanceOriginalTitle = levenshteinDistance.apply(movieNameLC, result.getOriginalTitle().toLowerCase());
            log.debug("getSearchMovieParserResult: between " + movieNameLC + " and " + result.getOriginalTitle().toLowerCase() + "/" + result.getTitle().toLowerCase() + " levenshteinDistanceTitle=" + levenshteinDistanceTitle + ", levenshteinDistanceOriginalTitle=" + levenshteinDistanceOriginalTitle);

            if (! isReleaseDateKnown) {
                log.debug("getSearchMovieParserResult: set aside " + movie.title + " because release date is missing");
                searchMovieParserResult.resultsNoAirDate.add(new Pair<>(result,
                        Math.min(levenshteinDistanceTitle, levenshteinDistanceOriginalTitle)));
            } else {
                if (movie.poster_path == null || movie.poster_path.endsWith("missing/series.jpg") || movie.poster_path.endsWith("missing/movie.jpg") || movie.poster_path == "") {
                    log.debug("getSearchMovieParserResult: set aside " + movie.title + " because poster missing i.e. image=" + movie.poster_path);
                    searchMovieParserResult.resultsNoPoster.add(new Pair<>(result,
                            Math.min(levenshteinDistanceTitle, levenshteinDistanceOriginalTitle)));
                } else {
                    log.debug("getSearchMovieParserResult: " + movie.title + " has poster_path " + ScraperImage.TMPL + movie.poster_path);
                    result.setPosterPath(movie.poster_path);
                    if (movie.backdrop_path == null || movie.backdrop_path.endsWith("missing/series.jpg") || movie.backdrop_path.endsWith("missing/movie.jpg") || movie.backdrop_path == "") {
                        log.debug("getSearchMovieParserResult: set aside " + movie.title + " because banner missing i.e. banner=" + movie.backdrop_path);
                        searchMovieParserResult.resultsNoBanner.add(new Pair<>(result,
                                Math.min(levenshteinDistanceTitle, levenshteinDistanceOriginalTitle)));
                    } else {
                        log.debug("getSearchMovieParserResult: " + movie.title + " has backdrop_path " + ScraperImage.TMBL + movie.backdrop_path);
                        // TODO MARC: this generates the thumb by resizing the large image: pass the two
                        result.setBackdropPath(movie.backdrop_path);
                        // get the min of the levenshtein distance between cleaned file based show name and title and original title identified
                        searchMovieParserResult.resultsProbable.add(new Pair<>(result,
                                Math.min(levenshteinDistanceTitle, levenshteinDistanceOriginalTitle)));
                    }
                }
            }
        }
        log.debug("getSearchMovieParserResult: resultsProbable=" + searchMovieParserResult.resultsProbable.toString());

        // perform the levenshtein distance sort on all results
        Collections.sort(searchMovieParserResult.resultsProbable, SearchParserResult.comparator);
        Collections.sort(searchMovieParserResult.resultsNoBanner, SearchParserResult.comparator);
        Collections.sort(searchMovieParserResult.resultsNoPoster, SearchParserResult.comparator);
        Collections.sort(searchMovieParserResult.resultsNoAirDate, SearchParserResult.comparator);

        log.debug("getSearchMovieParserResult: applying Levenshtein distance resultsProbableSorted=" + searchMovieParserResult.resultsProbable.toString());
        return searchMovieParserResult;
    }
}
