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

import com.uwetrottmann.tmdb2.entities.Movie;
import com.uwetrottmann.tmdb2.entities.Videos;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SearchMovieTrailerParser2 {
    public static List<SearchMovieTrailerResult.TrailerResult> getResult(Movie movie) {
        List<SearchMovieTrailerResult.TrailerResult> result = new LinkedList<SearchMovieTrailerResult.TrailerResult>();
        final int limit = 40; // limit number of trailers
        int i = 0;
        for (Videos.Video trailer : movie.videos.results) {
            if (i < limit) {
                SearchMovieTrailerResult.TrailerResult item = new SearchMovieTrailerResult.TrailerResult();
                if (trailer.site != null) item.setService(trailer.site);
                if (trailer.iso_639_1 != null) {
                    Locale locale = new Locale(trailer.iso_639_1);
                    item.setLanguage(locale.getDisplayLanguage());
                }
                if (trailer.key != null) item.setKey(trailer.key);
                if (trailer.name != null) item.setName(trailer.name);
                if (trailer.type != null) item.setType(trailer.type.toString());
                result.add(item);
            }
            i++;
        }
        return result;
    }
}
