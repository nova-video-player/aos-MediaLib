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
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.archos.medialib.R;
import com.archos.mediascraper.HttpCache;
import com.archos.mediascraper.MediaScraper;
import com.archos.mediascraper.MovieTags;
import com.archos.mediascraper.ScrapeDetailResult;
import com.archos.mediascraper.ScrapeSearchResult;
import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.ScraperImage;
import com.archos.mediascraper.SearchResult;
import com.archos.mediascraper.preprocess.MovieSearchInfo;
import com.archos.mediascraper.preprocess.SearchInfo;
import com.archos.mediascraper.preprocess.TvShowSearchInfo;
import com.archos.mediascraper.settings.ScraperSetting;
import com.archos.mediascraper.settings.ScraperSettings;
import com.archos.mediascraper.themoviedb3.ImageConfiguration;
import com.archos.mediascraper.themoviedb3.JSONFileFetcher;
import com.archos.mediascraper.themoviedb3.MovieId;
import com.archos.mediascraper.themoviedb3.MovieIdDescription;
import com.archos.mediascraper.themoviedb3.MovieIdImages;
import com.archos.mediascraper.themoviedb3.MovieIdResult;
import com.archos.mediascraper.themoviedb3.SearchMovie;
import com.archos.mediascraper.themoviedb3.SearchMovieResult;
import com.archos.mediascraper.themoviedb3.SearchMovieTrailer;
import com.uwetrottmann.tmdb2.Tmdb;
import com.uwetrottmann.tmdb2.entities.BaseMember;
import com.uwetrottmann.tmdb2.entities.BaseMovie;
import com.uwetrottmann.tmdb2.entities.Movie;
import com.uwetrottmann.tmdb2.entities.MovieResultsPage;
import com.uwetrottmann.tmdb2.services.MoviesService;
import com.uwetrottmann.tmdb2.services.SearchService;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Response;

public class MovieScraper3 extends BaseScraper2 {
    private static final String PREFERENCE_NAME = "themoviedb.org";

    private final static String TAG = "MovieScraper3";
    private final static boolean DBG = false;
    private final static boolean DBG_RETROFIT = false;
    private final static boolean CACHE = true;

    private static ScraperSettings sSettings;
    private Response<MovieResultsPage> response = null;

    // TODO: MARC do we need cache? Can we share cache?
    // Add caching for OkHttpClient so that queries for episodes from a same tvshow will get a boost in resolution
    protected final int cacheSize = 100 * 1024 * 1024; // 100 MB (it is a directory...)
    static Cache cache;

    static MyTmdb tmdb = null;
    static SearchService searchService = null;

    public MovieScraper3(Context context) {
        super(context);
        // ensure cache is initialized
        synchronized (MovieScraper3.class) {
            if (cache == null)
                cache = new Cache(context.getCacheDir(), cacheSize);
        }
    }

    static class MyTmdb extends Tmdb {
        public MyTmdb(String apiKey) {
            super(apiKey);
        }
        public class CacheInterceptor implements Interceptor {
            @Override
            public okhttp3.Response intercept(Chain chain) throws IOException {
                okhttp3.Response response = chain.proceed(chain.request());
                CacheControl cacheControl = new CacheControl.Builder()
                        .maxAge(2, TimeUnit.HOURS) // 2 hours cache
                        .build();
                return response.newBuilder()
                        .removeHeader("Pragma")
                        .removeHeader("Cache-Control")
                        .header("Cache-Control", cacheControl.toString())
                        .build();
            }
        }
        @Override
        protected void setOkHttpClientDefaults(OkHttpClient.Builder builder) {
            super.setOkHttpClientDefaults(builder);
            if (DBG_RETROFIT) {
                HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
                logging.setLevel(HttpLoggingInterceptor.Level.BODY);
                builder.addNetworkInterceptor(logging).addInterceptor(logging);
            }
            if (CACHE) {
                builder.cache(cache).addNetworkInterceptor(new MovieScraper3.MyTmdb.CacheInterceptor());
            }
        }
    }

    private List<SearchResult> processTmDbSearch(Response<MovieResultsPage> response,
                                                 MovieSearchInfo searchInfo, int maxItems, String language) {
        List<SearchResult> results = new LinkedList<>();
        for (BaseMovie movies : response.body().results) {
            SearchResult result = new SearchResult();
            result.setId(movies.id);
            result.setLanguage(language);
            result.setTitle(movies.original_title);
            result.setScraper(this);
            result.setFile(searchInfo.getFile());
            if (maxItems < 0 || results.size() < maxItems) {
                if (DBG)
                    Log.d(TAG, "getMatches2: taking into account " + movies.original_title + " and poster exists i.e. poster=" + movies.poster_path);
                results.add(result);
            }
        }
        return results;
    }

    private Pair<List<SearchResult>, Boolean> searchMovie(MovieSearchInfo searchInfo, int maxItems, Integer year, String language) {
        Boolean error = false;
        List<SearchResult> results = new LinkedList<>();
        if (DBG) Log.d(TAG, "getMatches2: no result yet, quering tmdb for " + searchInfo.getName() + " year " + year + " in " + language);
        try {
            response = searchService.movie(searchInfo.getName(), null, language,
                    null, true, year, null).execute();
            if (response.isSuccessful() && response.body() != null) {
                results = processTmDbSearch(response, searchInfo, maxItems, language);
            } else if (response.code() != 404) { // TODO: probably treat other cases of errors
                if (DBG)
                    Log.d(TAG, "ScrapeSearchResult ScrapeStatus.ERROR response not successful or body empty");
                error = true;
            }
        } catch (IOException e) {
            Log.e(TAG, "searchMovie: caught IOException");
            error = true;
            response = null;
        }
        return new Pair<>(results, error);
    }

    @Override
    public ScrapeSearchResult getMatches2(SearchInfo info, int maxItems) {
        // check input
        if (info == null || !(info instanceof MovieSearchInfo)) {
            Log.e(TAG, "bad search info: " + info == null ? "null" : "tvshow in movie scraper");
            return new ScrapeSearchResult(null, true, ScrapeStatus.ERROR, null);
        }
        MovieSearchInfo searchInfo = (MovieSearchInfo) info;
        Pair<List<SearchResult>, Boolean> searchResults = null;
        // get configured language
        String language = getLanguage(mContext);
        response = null;
        if (DBG) Log.d(TAG, "getMatches2: movie search:" + searchInfo.getName());
        if (tmdb == null) {
            tmdb = new MyTmdb(mContext.getString(R.string.tmdb_api_key));
            searchService = tmdb.searchService();
        }
        try {
            Integer year = null;
            try {
                year = Integer.parseInt(searchInfo.getYear());
            } catch(NumberFormatException nfe) {
                Log.w(TAG, "getMatches2: searchInfo.getYear() is not an integer");
                year = null;
            }
            searchResults = searchMovie(searchInfo, maxItems, year, language);
            if (searchResults.second) return new ScrapeSearchResult(null, false, ScrapeStatus.ERROR, null);
            // fallback in english if result is empty
            if (searchResults.first.isEmpty() && !language.equals("en")) {
                searchResults = searchMovie(searchInfo, maxItems, year, "en");
                if (searchResults.second) return new ScrapeSearchResult(null, false, ScrapeStatus.ERROR, null);
            }
            if (year != null) { // search were made with a non null year
                // still no result try with empty year with default language
                if (searchResults.first.isEmpty()) {
                    searchResults = searchMovie(searchInfo, maxItems, null, language);
                    if (searchResults.second) return new ScrapeSearchResult(null, false, ScrapeStatus.ERROR, null);
                }
                // still no result try with empty year with english
                if (searchResults.first.isEmpty() && !language.equals("en")) {
                    searchResults = searchMovie(searchInfo, maxItems, null, "en");
                    if (searchResults.second) return new ScrapeSearchResult(null, false, ScrapeStatus.ERROR, null);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getMatches2", e);
            return new ScrapeSearchResult(null, false, ScrapeStatus.ERROR, null);
        }
        ScrapeStatus status = searchResults.first.isEmpty() ? ScrapeStatus.NOT_FOUND : ScrapeStatus.OKAY;
        if (DBG)
            if (searchResults.first.isEmpty()) Log.d(TAG,"ScrapeSearchResult ScrapeStatus.NOT_FOUND");
            else Log.d(TAG,"ScrapeSearchResult ScrapeStatus.OKAY found " + searchResults.first);
        return new ScrapeSearchResult(searchResults.first, false, status, null);
    }

    @Override
    protected ScrapeDetailResult getDetailsInternal(SearchResult result, Bundle options) {
        generatePreferences(mContext);

        String language = sSettings.getString("language");
        language = getLanguage(mContext);

        long movieId = result.getId();
        Uri searchFile = result.getFile();

        HttpCache cache = HttpCache.getInstance(MediaScraper.getXmlCacheDirectory(mContext),
                MediaScraper.XML_CACHE_TIMEOUT, MediaScraper.CACHE_FALLBACK_DIRECTORY,
                MediaScraper.CACHE_OVERWRITE_DIRECTORY);
        JSONFileFetcher jff = new JSONFileFetcher(cache);

        // get base info
        MovieIdResult search = MovieId.getBaseInfo(movieId, language, jff);
        if (search.status != ScrapeStatus.OKAY) {
            return new ScrapeDetailResult(search.tag, true, null, search.status, search.reason);
        }
        MovieTags tag = search.tag;
        tag.setFile(searchFile);
        SearchMovieTrailer.addTrailers(movieId, tag, language, jff);
        // add posters and backdrops
        // TODO: CHANGE POSTER SIZE HERE?
        MovieIdImages.addImages(movieId, tag, language,
                ImageConfiguration.PosterSize.W342, // large poster
                ImageConfiguration.PosterSize.W92,  // thumb poster
                ImageConfiguration.BackdropSize.W1280, // large bd
                ImageConfiguration.BackdropSize.W300,  // thumb bd
                searchFile.toString(), jff, mContext);
        ScraperImage defaultPoster = tag.getDefaultPoster();
        if (defaultPoster != null) {
            tag.setCover(defaultPoster.getLargeFileF());
        }

        // if there was no movie description in the native language get it from default
        if (tag.getPlot() == null) {
            MovieIdDescription.addDescription(movieId, tag, jff);
        }
        tag.downloadPoster(mContext);
        return new ScrapeDetailResult(tag, true, null, ScrapeStatus.OKAY, null);
    }

    public static String getLanguage(Context context) {
        return generatePreferences(context).getString("language");
    }

    protected static synchronized ScraperSettings generatePreferences(Context context) {
        if (sSettings == null) {
            sSettings = new ScraperSettings(context, PREFERENCE_NAME);
            HashMap<String, String> labelList = new HashMap<String, String>();
            String[] labels = context.getResources().getStringArray(R.array.scraper_labels_array);
            for (String label : labels) {
                String[] splitted = label.split(":");
                labelList.put(splitted[0], splitted[1]);
            }
            // <settings><setting label="info_language" type="labelenum" id="language" values="$$8" sort="yes" default="en"></setting></settings>
            ScraperSetting setting = new ScraperSetting("language", ScraperSetting.STR_LABELENUM);
            String defaultLang = Locale.getDefault().getLanguage();
            if (!TextUtils.isEmpty(defaultLang) && LANGUAGES.contains(defaultLang))
                setting.setDefault(defaultLang);
            else
                setting.setDefault("en");
            setting.setLabel(labelList.get("info_language"));
            setting.setValues(LANGUAGES);
            sSettings.addSetting("language", setting);
        }
        return sSettings;
    }

    @Override
    protected String internalGetPreferenceName() {
        return PREFERENCE_NAME;
    }
}
