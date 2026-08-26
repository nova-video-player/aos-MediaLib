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


package com.archos.mediascraper.xml;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import com.archos.medialib.R;
import com.archos.mediascraper.MovieTags;
import com.archos.mediascraper.ScrapeDetailResult;
import com.archos.mediascraper.ScrapeSearchResult;
import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.Scraper;
import com.archos.mediascraper.ScraperCache;
import com.archos.mediascraper.SearchResult;
import com.archos.mediascraper.TagsFactory;
import com.archos.mediascraper.preprocess.MovieSearchInfo;
import com.archos.mediascraper.preprocess.SearchInfo;
import com.archos.mediascraper.preprocess.SearchPreprocessor;
import com.archos.mediascraper.themoviedb3.CollectionInfo;
import com.archos.mediascraper.themoviedb3.CollectionResult;
import com.archos.mediascraper.themoviedb3.ImageConfiguration;
import com.archos.mediascraper.themoviedb3.MovieCollection;
import com.archos.mediascraper.themoviedb3.MovieId2;
import com.archos.mediascraper.themoviedb3.MovieIdDescription2;
import com.archos.mediascraper.themoviedb3.MovieIdResult;
import com.archos.mediascraper.themoviedb3.MyTmdb;
import com.archos.mediascraper.themoviedb3.SearchMovie2;
import com.archos.mediascraper.themoviedb3.SearchMovieResult;

import com.uwetrottmann.tmdb2.services.CollectionsService;
import com.uwetrottmann.tmdb2.services.MoviesService;
import com.uwetrottmann.tmdb2.services.SearchService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import okhttp3.Cache;

import static com.archos.mediascraper.MovieTags.isCollectionAlreadyKnown;
import static com.archos.mediascraper.themoviedb3.MovieCollectionImages.downloadCollectionImage;

public class MovieScraper3 extends BaseScraper2 {
    private static final String PREFERENCE_NAME = "themoviedb.org";

    private static final Logger log = LoggerFactory.getLogger(MovieScraper3.class);

    // Add caching for OkHttpClient so that queries for episodes from a same tvshow will get a boost in resolution
    static Cache cache;

    private static volatile MyTmdb tmdb = null;
    private static volatile SearchService searchService = null;
    private static volatile MoviesService moviesService = null;
    private static volatile CollectionsService collectionService = null;

    static String apiKey = null;

    public MovieScraper3(Context context) {
        super(context);
        // ensure cache is initialized
        synchronized (MovieScraper3.class) {
            cache = ScraperCache.getCache(context);
            apiKey = context.getString(R.string.tmdb_api_key);
        }
    }

    public static synchronized void reauth() {
        tmdb = new MyTmdb(apiKey, cache);
        searchService = tmdb.searchService();
        moviesService = tmdb.moviesService();
        collectionService = tmdb.collectionService();
    }

    public static synchronized MyTmdb getTmdb() {
        if (tmdb == null) reauth();
        return tmdb;
    }

    public static synchronized SearchService getSearchService() {
        if (searchService == null) reauth();
        return searchService;
    }

    public static synchronized MoviesService getMoviesService() {
        if (moviesService == null) reauth();
        return moviesService;
    }

    public static synchronized CollectionsService getCollectionService() {
        if (collectionService == null) reauth();
        return collectionService;
    }

    @Override
    public ScrapeSearchResult getMatches2(SearchInfo info, int maxItems) {
        // check input
        if (info == null || !(info instanceof MovieSearchInfo)) {
            log.error("bad search info: {}", info == null ? "null" : "tvshow in movie scraper");
            return new ScrapeSearchResult(null, true, ScrapeStatus.ERROR, null);
        }
        MovieSearchInfo searchInfo = (MovieSearchInfo) info;
        if (log.isDebugEnabled()) log.debug("getMatches2: movie search:{}", searchInfo.getName());

        // get configured language
        String language = Scraper.getLanguage(mContext);
        if (language == null || language.contains("null")) language = Locale.getDefault().getLanguage();

        // make sure we have a valid title.
        if (log.isDebugEnabled()) log.debug("movie search:{} year:{} language:{}", searchInfo.getName(), searchInfo.getYear(), language);

        // We use unified scoring if the year is not "confident" (i.e. not in parentheses)
        // or if we just want to be more robust for titles containing years.
        boolean useUnifiedScoring = !searchInfo.isYearConfident() && searchInfo.getYear() != null;

        List<SearchResult> allResults = new ArrayList<>();
        ScrapeStatus status = ScrapeStatus.NOT_FOUND;
        Throwable reason = null;

        // Candidate building logic:
        class SearchCandidate {
            String query;
            String year;
            SearchCandidate(String q, String y) { this.query = q; this.year = y; }
        }
        List<SearchCandidate> candidates = new ArrayList<>();

        if (useUnifiedScoring) {
            if (searchInfo.isYearAtStart()) {
                // Scenario A: Year at Start -> try full title first, then remainder with year filter
                candidates.add(new SearchCandidate(searchInfo.getOriginalName(), null));
                candidates.add(new SearchCandidate(searchInfo.getName(), searchInfo.getYear()));
            } else {
                // Scenario B: Year at End/Middle -> try remainder with year filter first, then full title
                candidates.add(new SearchCandidate(searchInfo.getName(), searchInfo.getYear()));
                candidates.add(new SearchCandidate(searchInfo.getOriginalName(), null));
            }
        } else {
            // Standard cases: cleaned name with year (if any) first, then recover from wrong years
            // by retrying the cleaned title without the year before falling back to the suggestion.
            candidates.add(new SearchCandidate(searchInfo.getName(), searchInfo.getYear()));
            if (searchInfo.getYear() != null) {
                candidates.add(new SearchCandidate(searchInfo.getName(), null));
            }
            candidates.add(new SearchCandidate(searchInfo.getSearchSuggestion(), null));

            String unaccented = removeDiacritics(searchInfo.getName());
            if (!unaccented.equals(searchInfo.getName())) {
                candidates.add(new SearchCandidate(unaccented, searchInfo.getYear()));
            }

            // Fallback for transliterated titles (e.g. German umlauts: 'ae' -> 'ä')
            boolean isGerman = (language != null && language.startsWith("de")) ||
                               "de".equals(java.util.Locale.getDefault().getLanguage());
            if (isGerman) {
                String transliterated = com.archos.mediascraper.preprocess.ParseUtils.transliterate(searchInfo.getName());
                if (!transliterated.equals(searchInfo.getName())) {
                    candidates.add(new SearchCandidate(transliterated, searchInfo.getYear()));
                }
            }
        }

        for (SearchCandidate candidate : candidates) {
            String searchQuery = candidate.query;
            String yearToUse = candidate.year;

            if (searchQuery == null || searchQuery.isBlank() || searchQuery.contains("null")) continue;
            if (log.isDebugEnabled()) log.debug("getMatches2: trying candidate '{}' with year {}", searchQuery, yearToUse);

            SearchMovieResult searchResult = null;
            if (searchInfo.scrapeFromDB) {
                //Check the Database for this Movie, we may have scraped it already on a different URI.
                searchResult = MovieTags.getMovieResultIfAlreadyKnown(mContext, searchQuery, yearToUse, searchInfo.getOriginalUri());
                if (searchResult != null && searchResult.result != null && !searchResult.result.isEmpty()) {
                    for (SearchResult result : searchResult.result) {
                        result.setScraper(this);
                        result.setFile(searchInfo.getFile());
                    }
                    if (!useUnifiedScoring)
                        return new ScrapeSearchResult(searchResult.result, true, searchResult.status, searchResult.reason);
                    allResults.addAll(searchResult.result);
                    status = ScrapeStatus.OKAY;
                }
            }

            //SEARCH TMDB FOR THE MOVIE!
            //If the Query is 5 words or more, and for as long as it is, remove a word
            //BUT! Only remove and try 3 times (Aussie Logic, backwards as fuck but works!)
            if (searchResult == null || searchResult.result == null || searchResult.result.isEmpty()) {
                for (int i = 0; i < (searchInfo.aggressiveScan ? 4 : 1); i++ ){
                    if (log.isDebugEnabled()) log.debug("getMatches2: searching TMDB for '{}' with year {}", searchQuery, yearToUse);
                    searchResult = SearchMovie2.search(searchQuery, language, yearToUse, maxItems, getSearchService(), adultScrape);

                    //Check result and try again if we need to.
                    if (searchResult.status == ScrapeStatus.OKAY && (searchResult.result != null && !searchResult.result.isEmpty())) {
                        for (SearchResult result : searchResult.result) {
                            result.setScraper(this);
                            result.setFile(searchInfo.getFile());
                        }
                        allResults.addAll(searchResult.result);
                        status = ScrapeStatus.OKAY;
                        break;
                    } else if (searchInfo.aggressiveScan && (searchResult.status == ScrapeStatus.OKAY || searchResult.status == ScrapeStatus.NOT_FOUND)) {
                        //Only if its over 3 words. (Stop false positives on small titles like "The")
                        if (searchQuery.split("[\\s\\-_.]+").length > 4) {
                            //Grab the one word off.
                            String reversed = new StringBuilder(searchQuery).reverse().toString();
                            Pattern sepPattern = Pattern.compile("[\\s\\-_.]");
                            Matcher matcher = sepPattern.matcher(reversed);
                            if (matcher.find()) {
                                searchQuery = searchQuery.substring(0, searchQuery.length() - matcher.start() - 1).trim();
                            }
                        } else break; //NOT OVER 3 WORDS. DONT SEARCH AGAIN
                    } else {
                        if (searchResult.status != ScrapeStatus.NOT_FOUND) {
                            status = searchResult.status;
                            reason = searchResult.reason;
                        }
                        break;
                    }
                }
            }

            // If we found results and we're NOT doing unified scoring, we can stop early
            if (!allResults.isEmpty() && !useUnifiedScoring) {
                if (hasExactTitleMatch(allResults, searchInfo.getName())) {
                    if (log.isDebugEnabled()) log.debug("getMatches2: found results for '{}', stopping early", searchQuery);
                    break;
                }
            }
        }

        // Fallback: if no results found with year constraint, retry primary candidate without year
        if (allResults.isEmpty()) {
            SearchCandidate primary = candidates.get(0);
            if (primary.year != null && primary.query != null && !primary.query.isBlank() && !primary.query.contains("null")) {
                if (log.isDebugEnabled()) log.debug("getMatches2: fallback trying '{}' without year", primary.query);
                SearchMovieResult searchResult = SearchMovie2.search(primary.query, language, null, maxItems, getSearchService(), adultScrape);
                if (searchResult.status == ScrapeStatus.OKAY && searchResult.result != null && !searchResult.result.isEmpty()) {
                    for (SearchResult result : searchResult.result) {
                        result.setScraper(this);
                        result.setFile(searchInfo.getFile());
                    }
                    allResults.addAll(searchResult.result);
                    status = ScrapeStatus.OKAY;
                }
            }
        }

        // Fallback: if still no results, try "Head" of the title (before first punctuation)
        if (allResults.isEmpty() && status != ScrapeStatus.AUTH_ERROR) {
            String filename = com.archos.filecorelibrary.FileUtils.getName(searchInfo.getFile());
            // Strip extension if any
            if (filename.contains(".")) {
                filename = filename.substring(0, filename.lastIndexOf('.'));
            }
            String headName = getTitleHead(filename);
            if (headName != null && !headName.equals(filename)) {
                // Clean the head name (dots -> spaces, strip common junk)
                String cleanedHead = com.archos.mediascraper.ShowUtils.cleanUpName(headName);
                // Important: also strip the year from the head if it's there (e.g. "Star Wars 1977")
                cleanedHead = com.archos.mediascraper.preprocess.ParseUtils.cleanExtractedTitle(cleanedHead);

                if (log.isDebugEnabled()) log.debug("getMatches2: trying title head '{}' (cleaned: '{}')", headName, cleanedHead);

                // Try Head with year first
                SearchMovieResult searchResult = SearchMovie2.search(cleanedHead, language, searchInfo.getYear(), maxItems, getSearchService(), adultScrape);
                if (searchResult.status == ScrapeStatus.OKAY && searchResult.result != null && !searchResult.result.isEmpty()) {
                    for (SearchResult result : searchResult.result) {
                        result.setScraper(this);
                        result.setFile(searchInfo.getFile());
                    }
                    allResults.addAll(searchResult.result);
                    status = ScrapeStatus.OKAY;
                }

                // Try Head without year as a last resort for very long titles
                if (allResults.isEmpty()) {
                    if (log.isDebugEnabled()) log.debug("getMatches2: no results for head with year, trying title head '{}' without year", cleanedHead);
                    searchResult = SearchMovie2.search(cleanedHead, language, null, maxItems, getSearchService(), adultScrape);
                    if (searchResult.status == ScrapeStatus.OKAY && searchResult.result != null && !searchResult.result.isEmpty()) {
                        for (SearchResult result : searchResult.result) {
                            result.setScraper(this);
                            result.setFile(searchInfo.getFile());
                        }
                        allResults.addAll(searchResult.result);
                        status = ScrapeStatus.OKAY;
                    }
                }
            }
        }

        if (allResults.isEmpty()) {
            return new ScrapeSearchResult(allResults, true, status, reason);
        }

        // UNIFIED SCORING:
        // If we have multiple results (likely from both stripped and unstripped queries),
        // we re-calculate the distance against the most complete reference title we have.
        String cleanedName = searchInfo.getName();
        String referenceName = searchInfo.getName(); // Use cleaned name as reference for distance
        if (log.isDebugEnabled()) log.debug("getMatches2: unified scoring against reference '{}'", referenceName);

        org.apache.commons.text.similarity.LevenshteinDistance levenshteinDistance = org.apache.commons.text.similarity.LevenshteinDistance.getDefaultInstance();
        String referenceNameLC = referenceName.toLowerCase();
        String cleanedNameLC = cleanedName.toLowerCase();

        java.util.LinkedHashMap<Integer, SearchResult> uniqueResults = new java.util.LinkedHashMap<>();
        for (SearchResult sr : allResults) {
            if (uniqueResults.containsKey(sr.getId())) continue;

            String title = sr.getTitle();
            String originalTitle = sr.getOriginalTitle();
            String titleLC = title != null ? title.toLowerCase() : "";
            String originalTitleLC = originalTitle != null ? originalTitle.toLowerCase() : "";

            int distance;
            // BOOST: Exact match for cleaned name gets priority (distance 0)
            if (titleLC.equals(cleanedNameLC) || originalTitleLC.equals(cleanedNameLC)) {
                distance = 0;
            } else {
                int distTitle = title != null ? levenshteinDistance.apply(referenceNameLC, titleLC) : Integer.MAX_VALUE;
                int distOrig = originalTitle != null ? levenshteinDistance.apply(referenceNameLC, originalTitleLC) : Integer.MAX_VALUE;
                distance = Math.min(distTitle, distOrig);
            }
            sr.setLevenshteinDistance(distance);
            if (log.isDebugEnabled()) log.debug("getMatches2: result {} ({}) distance={}", sr.getTitle(), sr.getId(), sr.getLevenshteinDistance());

            uniqueResults.put(sr.getId(), sr);
        }

        // ROMAN NUMERAL BOOST (ambiguous distances only):
        // Filenames like "Star Wars III" often score a worse (higher) Levenshtein distance
        // against the official "Star Wars: Episode III - Revenge of the Sith" than against an
        // unrelated but shorter title that happens to also contain "III" (e.g. a parody or
        // "making of" entry). When the cleaned query ends with a standalone roman numeral,
        // re-score candidates that contain that same roman numeral as a standalone word using
        // just the title's head up to the numeral (dropping any subtitle after it), which is
        // normally a much closer match. "Ambiguous" here means no candidate is already an exact
        // match (distance 0): those are left untouched, avoiding regressions on titles that
        // legitimately end in a roman numeral (e.g. "Henry IV", regnal names).
        String trailingRoman = com.archos.mediascraper.preprocess.ParseUtils.getTrailingRomanNumeral(cleanedNameLC);
        if (trailingRoman != null) {
            boolean hasExactMatch = uniqueResults.values().stream().anyMatch(sr -> sr.getLevenshteinDistance() == 0);
            if (!hasExactMatch) {
                for (SearchResult sr : uniqueResults.values()) {
                    int headDistance = romanHeadDistance(cleanedNameLC, sr.getTitle(), trailingRoman, levenshteinDistance);
                    int headDistanceOrig = romanHeadDistance(cleanedNameLC, sr.getOriginalTitle(), trailingRoman, levenshteinDistance);
                    int bestHeadDistance = Math.min(headDistance, headDistanceOrig);
                    if (bestHeadDistance < sr.getLevenshteinDistance()) {
                        if (log.isDebugEnabled()) log.debug("getMatches2: roman numeral head match for {} ({}), boosting distance {} -> {}", sr.getTitle(), sr.getId(), sr.getLevenshteinDistance(), bestHeadDistance);
                        sr.setLevenshteinDistance(bestHeadDistance);
                    }
                }
            } else {
                if (log.isDebugEnabled()) log.debug("getMatches2: trailing roman numeral '{}' found but an exact match already exists, skipping boost", trailingRoman);
            }
        }

        List<SearchResult> sortedResults = new ArrayList<>(uniqueResults.values());
        Collections.sort(sortedResults, com.archos.mediascraper.themoviedb3.SearchParserResult.comparator);

        //Return the rest we got.
        return new ScrapeSearchResult(sortedResults, true, ScrapeStatus.OKAY, null);
    }

    /**
     * Looks for {@code romanNumeralLC} as a standalone word within {@code title}, and if found,
     * returns the Levenshtein distance between {@code queryLC} and the title's head up to and
     * including that numeral (i.e. the title with any trailing subtitle dropped). Returns
     * Integer.MAX_VALUE if the title is null or does not contain the numeral as a standalone word.
     */
    private static int romanHeadDistance(String queryLC, String title, String romanNumeralLC,
                                          org.apache.commons.text.similarity.LevenshteinDistance levenshteinDistance) {
        if (title == null) return Integer.MAX_VALUE;
        String titleLC = title.toLowerCase();
        Matcher matcher = Pattern.compile("(?<![a-z0-9])" + Pattern.quote(romanNumeralLC) + "(?![a-z0-9])").matcher(titleLC);
        if (!matcher.find()) return Integer.MAX_VALUE;
        String head = titleLC.substring(0, matcher.end());
        return levenshteinDistance.apply(queryLC, head);
    }

    private String getTitleHead(String name) {
        if (name == null) return null;
        // Split by colon, comma, or " - " (dash surrounded by spaces)
        int idx = name.indexOf(':');
        if (idx == -1) idx = name.indexOf(',');
        if (idx == -1) {
            idx = name.indexOf(" - ");
            if (idx != -1) idx += 1; // point to the dash itself
        }
        if (idx != -1) {
            String head = name.substring(0, idx).trim();
            // Don't return if it's too short (e.g. "I, Robot")
            if (head.length() > 2) return head;
        }
        return name;
    }

    private String removeDiacritics(String name) {
        if (name == null) return "";
        return Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    private boolean hasExactTitleMatch(List<SearchResult> results, String query) {
        if (results == null || query == null) return false;
        String normalizedQuery = normalizeTitleForExactMatch(query);
        for (SearchResult result : results) {
            if (normalizedQuery.equals(normalizeTitleForExactMatch(result.getTitle()))
                    || normalizedQuery.equals(normalizeTitleForExactMatch(result.getOriginalTitle()))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeTitleForExactMatch(String title) {
        if (title == null) return "";
        return title.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    @Override
    protected ScrapeDetailResult getDetailsInternal(SearchResult result, Bundle options) {
        // TODO: why it searches every first level result?
        String language = Scraper.getLanguage(mContext);
        if (log.isDebugEnabled()) log.debug("getDetailsInternal: language={}", language);

        long movieId = result.getId();
        Uri searchFile = result.getFile();

        //If we got this result from the database, grab the tags from there and return them instead of going to TMDB.
        if (result.fromDB) {
            MovieTags tag = TagsFactory.buildMovieTags(mContext, movieId);
            return new ScrapeDetailResult(tag, true, null, ScrapeStatus.OKAY, null);
        }

        // get base info
        MovieIdResult search = MovieId2.getBaseInfo(movieId, language, getMoviesService(), mContext);
        if (search.status != ScrapeStatus.OKAY) {
            return new ScrapeDetailResult(search.tag, true, null, search.status, search.reason);
        }

        MovieTags tag = search.tag;
        tag.setFile(searchFile);

        // MovieCollection poster/backdrops and information are handled in the MovieTag because it is easier
        if (tag.getCollectionId() != -1 && ! isCollectionAlreadyKnown(tag.getCollectionId(), mContext)) { // in presence of a movie collection/saga
            CollectionResult collectionResult = MovieCollection.getInfo(tag.getCollectionId(), language, getCollectionService());
            if (collectionResult.status == ScrapeStatus.OKAY && collectionResult.collectionInfo != null) {
                CollectionInfo collectionInfo = collectionResult.collectionInfo;
                if (collectionInfo.name != null) tag.setCollectionName(collectionInfo.name);
                if (collectionInfo.description != null) tag.setCollectionDescription(collectionInfo.description);
                if (collectionInfo.poster != null) tag.setCollectionPosterPath(collectionInfo.poster);
                if (collectionInfo.backdrop != null) tag.setCollectionBackdropPath(collectionInfo.backdrop);
            }
            downloadCollectionImage(tag,
                    ImageConfiguration.PosterSize.W342,    // large poster
                    ImageConfiguration.PosterSize.W92,     // thumb poster
                    ImageConfiguration.BackdropSize.W1280, // large bd
                    ImageConfiguration.BackdropSize.W300,  // thumb bd
                    searchFile.toString(), mContext);
        }

        // if there was no movie description in the native language get it from default
        if (tag.getPlot() == null || tag.getPlot().trim().isEmpty()) {
            if (log.isDebugEnabled()) log.debug("ScrapeDetailResult: getting description in en because plot non existent in {}", language);
            MovieIdDescription2.addDescription(movieId, tag, getMoviesService());
        }
        tag.downloadPoster(mContext);
        tag.downloadBackdrop(mContext);
        return new ScrapeDetailResult(tag, true, null, ScrapeStatus.OKAY, null);
    }

    @Override
    protected String internalGetPreferenceName() {
        return PREFERENCE_NAME;
    }
}
