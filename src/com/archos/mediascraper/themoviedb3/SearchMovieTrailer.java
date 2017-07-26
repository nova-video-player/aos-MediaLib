// Copyright 2017 Archos SA
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

import com.archos.mediascraper.FileFetcher;
import com.archos.mediascraper.FileFetcher.FileFetchResult;
import com.archos.mediascraper.MovieTags;
import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.ScraperImage;
import com.archos.mediascraper.ScraperTrailer;
import com.archos.mediascraper.SearchResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * movie/id/videos
 *
 * Required Parameters
 * api_key
 *
 * Optional Parameters
 * language         ISO 639-1 code.
 */
public class SearchMovieTrailer {
    private static final String TAG = SearchMovieTrailer.class.getSimpleName();
    private static final boolean DBG = false;

    private static final String METHOD = "movie/%s/videos";

    private static final String KEY_LANGUAGE = "language";

    public static SearchMovieTrailerResult addTrailers(long id,MovieTags tag, String lang,  FileFetcher fetcher) {
        SearchMovieTrailerResult myResult = new SearchMovieTrailerResult();

        FileFetchResult fileResult = getFile(id,lang, fetcher);
        if (fileResult.status != ScrapeStatus.OKAY || fileResult.file == null) {
            myResult.status = fileResult.status;
            return myResult;
        }

        List<SearchMovieTrailerResult.TrailerResult> parserResult = null;
        try {
            parserResult = SearchMovieTrailerParser.getInstance().readJsonFile(fileResult.file, null);
            if(!lang.equals("en")){//also ask for english trailers
                FileFetchResult fileResult2 = getFile(id,"en", fetcher);
                List<SearchMovieTrailerResult.TrailerResult> parserResult2 = SearchMovieTrailerParser.getInstance().readJsonFile(fileResult2.file, null);
                if(parserResult2!=null)
                    parserResult.addAll(parserResult2);
            }

            myResult.result = parserResult;
            if (parserResult.isEmpty()) {

                myResult.status = ScrapeStatus.NOT_FOUND;
                tag.setTrailers(new ArrayList<ScraperTrailer>());
            } else {
                myResult.status = ScrapeStatus.OKAY;
                List<ScraperTrailer> trailers = new ArrayList<>(parserResult.size());
                for (SearchMovieTrailerResult.TrailerResult trailerResult : parserResult) {
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

    private static FileFetchResult getFile(long id, String language, FileFetcher fetcher) {
        Map<String, String> queryParams = new HashMap<String, String>();
        TheMovieDb3.putIfNonEmpty(queryParams, KEY_LANGUAGE, language);
        String requestUrl = TheMovieDb3.buildUrl(queryParams, String.format(METHOD, ""+id));
        return fetcher.getFile(requestUrl);
    }
}
