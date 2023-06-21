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

import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.SearchResult;
import com.archos.mediascraper.xml.MovieScraper3;
import com.uwetrottmann.tmdb2.entities.MovieResultsPage;
import com.uwetrottmann.tmdb2.services.SearchService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import retrofit2.Response;

import static com.archos.mediascraper.preprocess.ParseUtils.yearExtractor;

// Search Movie for name query for year in language (ISO 639-1 code)
// does not include_adult (Toggle the inclusion of adult titles)
public class SearchMovie2 {
    private static final Logger log = LoggerFactory.getLogger(SearchMovie2.class);

    public static SearchMovieResult search(String query, String language, String year, int resultLimit, SearchService searchService, boolean adultScrape) {
        SearchMovieResult myResult = new SearchMovieResult();
        List<SearchResult> parserResult = null;
        Response<MovieResultsPage> response = null;
        Boolean notFound = false;

        log.debug("search " + query + " for year " + year + " in "+ language);

        Integer annee = null;
        if (year != null) {
            try {
                annee = Integer.parseInt(year);
            } catch (NumberFormatException nfe) {
                log.warn("search: year is not an integer");
                annee = null;
            }
        }
        log.debug("search: quering tmdb for " + query + " year " + annee + " in " + language);
        try {
            // by default no adult search
            response = searchService.movie(query, null, language,
                    null, adultScrape, null, annee).execute();
            // Check https://developer.themoviedb.org/docs/errors
            switch (response.code()) {
                case 401 -> { // auth issue
                    log.debug("search: auth error");
                    myResult.result = SearchMovieResult.EMPTY_LIST;
                    myResult.status = ScrapeStatus.AUTH_ERROR;
                    MovieScraper3.reauth();
                    return myResult;
                }
                case 404 -> { // not found
                    myResult.status = ScrapeStatus.NOT_FOUND;
                    notFound = true;
                    log.debug("search: " + query + " not found");
                }
                case 500, 503, 504 -> { // internal server error
                    log.error("search: internal server error");
                    myResult.result = SearchMovieResult.EMPTY_LIST;
                    myResult.status = ScrapeStatus.ERROR;
                }
                default -> {
                    log.debug("search: found");
                    if (response.isSuccessful()) {
                        if (response.body() != null) {
                            log.debug("search: response body has " + response.body().total_results + " results");
                            if (response.body().total_results == 0)
                                notFound = true;
                            parserResult = SearchMovieParser2.getResult(response, query, language, year, resultLimit);
                            myResult.result = parserResult;
                            myResult.status = ScrapeStatus.OKAY;
                        } else {
                            notFound = true;
                            log.debug("search: response body is null");
                            myResult.status = ScrapeStatus.NOT_FOUND;
                        }
                    } else { // an error at this point is PARSER related
                        log.debug("search: response is not successful for " + query);
                        myResult.status = ScrapeStatus.ERROR_PARSER;
                    }
                }
            }
        } catch (IOException e) {
            log.error("searchMovie: caught IOException");
            if (log.isDebugEnabled()) log.debug(e.getMessage(), e);
            myResult.result = SearchMovieResult.EMPTY_LIST;
            myResult.status = ScrapeStatus.ERROR_PARSER;
            myResult.reason = e;
        }
        if (notFound) {
            if (year == null) { // perhaps year is in title without (year)
                // reprocess name with year_extractor without parenthesis since we need to match The.Flash.2014.sXXeYY but not first to cope with Paris.Police.1900
                Pair<String, String> nameYear = yearExtractor(query);
                log.debug("search: not found trying to extract year name=" + nameYear.first + ", year=" + nameYear.second);
                if (nameYear.second != null) // avoid infinite loop
                    return search(nameYear.first, language, nameYear.second, resultLimit, searchService, adultScrape);
                else myResult.status = ScrapeStatus.NOT_FOUND;
            } else {
                log.debug("search: retrying search for '" + query + "' without year.");
                return search(query, language, null, resultLimit, searchService, adultScrape);
            }
        }
        return myResult;
    }
}
