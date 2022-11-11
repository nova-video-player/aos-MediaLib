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

import com.archos.mediascraper.SearchResult;
import com.uwetrottmann.tmdb2.entities.BaseMovie;
import com.uwetrottmann.tmdb2.entities.MovieResultsPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

import retrofit2.Response;

public class SearchMovieParser2 {
    private static final Logger log = LoggerFactory.getLogger(SearchMovieParser2.class);

    public static List<SearchResult> getResult(Response<MovieResultsPage> response, Integer limit) {
        List<SearchResult> results = new LinkedList<SearchResult>();
        int i = 0;
        SearchParserResult searchMovieParserResult = new SearchParserResult();

        for (BaseMovie movie : response.body().results) {
            if (i < limit) {
                SearchResult result = new SearchResult();
                result.setMovie();
                if (movie.id != null) result.setId(movie.id);
                if (movie.original_title != null) result.setTitle(movie.original_title);
                log.debug("getResult: taking into account " + movie.original_title);
                // add backdrop and poster here already if it exists because MovieIdImages can return empty results...
                log.debug("getResult: poster path " + movie.poster_path);
                if (movie.poster_path != null) result.setPosterPath(movie.poster_path);
                log.debug("getResult: backdrop path " + movie.backdrop_path);
                if (movie.backdrop_path != null) result.setBackdropPath(movie.backdrop_path);
                results.add(result);
            }
            i++;
        }
        return results;
    }
}
