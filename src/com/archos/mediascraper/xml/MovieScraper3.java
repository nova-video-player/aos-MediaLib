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
import com.archos.mediascraper.MediaScraper;
import com.archos.mediascraper.MovieTags;
import com.archos.mediascraper.ScrapeDetailResult;
import com.archos.mediascraper.ScrapeSearchResult;
import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.ScraperCache;
import com.archos.mediascraper.ScraperImage;
import com.archos.mediascraper.SearchResult;
import com.archos.mediascraper.preprocess.MovieSearchInfo;
import com.archos.mediascraper.preprocess.SearchInfo;
import com.archos.mediascraper.settings.ScraperSetting;
import com.archos.mediascraper.settings.ScraperSettings;
import com.archos.mediascraper.themoviedb3.ImageConfiguration;
import com.archos.mediascraper.themoviedb3.MovieId2;
import com.archos.mediascraper.themoviedb3.MovieIdDescription2;
import com.archos.mediascraper.themoviedb3.MovieIdImages2;
import com.archos.mediascraper.themoviedb3.MovieIdResult;
import com.archos.mediascraper.themoviedb3.SearchMovie2;
import com.archos.mediascraper.themoviedb3.SearchMovieResult;
import com.archos.mediascraper.themoviedb3.SearchMovieTrailer2;
import com.uwetrottmann.tmdb2.Tmdb;
import com.uwetrottmann.tmdb2.entities.BaseMovie;
import com.uwetrottmann.tmdb2.entities.MovieResultsPage;
import com.uwetrottmann.tmdb2.services.MoviesService;
import com.uwetrottmann.tmdb2.services.SearchService;

import java.io.IOException;
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
    private final static boolean DBG = true;
    private final static boolean DBG_RETROFIT = false;
    private final static boolean CACHE = true;

    private static ScraperSettings sSettings;
    private Response<MovieResultsPage> response = null;

    // Add caching for OkHttpClient so that queries for episodes from a same tvshow will get a boost in resolution
    static Cache cache;

    static MyTmdb tmdb = null;
    static SearchService searchService = null;
    static MoviesService moviesService = null;

    public MovieScraper3(Context context) {
        super(context);
        // ensure cache is initialized
        synchronized (MovieScraper3.class) {
            cache = ScraperCache.getCache(context);
        }
    }

    static class MyTmdb extends Tmdb {
        public MyTmdb(String apiKey) { super(apiKey); }
        public class CacheInterceptor implements Interceptor {
            @Override
            public okhttp3.Response intercept(Chain chain) throws IOException {
                okhttp3.Response response = chain.proceed(chain.request());
                CacheControl cacheControl = new CacheControl.Builder()
                        .maxAge(MediaScraper.SCRAPER_CACHE_TIMEOUT_COUNT, MediaScraper.SCRAPER_CACHE_TIMEOUT_UNIT)
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
                builder.cache(cache).addNetworkInterceptor(new MyTmdb.CacheInterceptor());
            }
        }
    }

    public void reauth() {
        tmdb = new MyTmdb(mContext.getString(R.string.tmdb_api_key));
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

    // TODO: isolate into a separate class with throwable
    private Pair<List<SearchResult>, Boolean> searchMovie(MovieSearchInfo searchInfo, int maxItems, Integer year, String language) {
        Boolean error = false;
        List<SearchResult> results = new LinkedList<>();
        if (searchService == null) searchService = tmdb.searchService();
        if (DBG) Log.d(TAG, "searchMovie: quering tmdb for " + searchInfo.getName() + " year " + year + " in " + language);
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
        if (DBG) Log.d(TAG, "getMatches2: movie search:" + searchInfo.getName());
        if (tmdb == null) reauth();
        if (searchService == null) searchService = tmdb.searchService();
        // get configured language
        String language = getLanguage(mContext);
        if (DBG) Log.d(TAG, "movie search:" + searchInfo.getName() + " year:" + searchInfo.getYear());
        SearchMovieResult searchResult = SearchMovie2.search(searchInfo.getName(), language, searchInfo.getYear(), maxItems, searchService);
        if (searchResult.status == ScrapeStatus.OKAY) {
            for (SearchResult result : searchResult.result) {
                result.setScraper(this);
                result.setFile(searchInfo.getFile());
            }
        }
        return new ScrapeSearchResult(searchResult.result, true, searchResult.status, searchResult.reason);
    }

    @Override
    protected ScrapeDetailResult getDetailsInternal(SearchResult result, Bundle options) {

        String language = getLanguage(mContext);

        long movieId = result.getId();
        Uri searchFile = result.getFile();

        if (tmdb == null) reauth();
        if (moviesService == null) moviesService = tmdb.moviesService();

        // get base info
        MovieIdResult search = MovieId2.getBaseInfo(movieId, language, moviesService);
        if (search.status != ScrapeStatus.OKAY) {
            return new ScrapeDetailResult(search.tag, true, null, search.status, search.reason);
        }
        MovieTags tag = search.tag;
        tag.setFile(searchFile);

        SearchMovieTrailer2.addTrailers(movieId, tag, language, moviesService);

        // add posters and backdrops
        // TODO: CHANGE POSTER SIZE HERE?
        MovieIdImages2.addImages(movieId, tag, language,
                ImageConfiguration.PosterSize.W342, // large poster
                ImageConfiguration.PosterSize.W92,  // thumb poster
                ImageConfiguration.BackdropSize.W1280, // large bd
                ImageConfiguration.BackdropSize.W300,  // thumb bd
                searchFile.toString(), moviesService, mContext);
        ScraperImage defaultPoster = tag.getDefaultPoster();
        if (defaultPoster != null) {
            tag.setCover(defaultPoster.getLargeFileF());
        }

        // TODO REALLY check if it is not better to do once the two requests one in english and one in language to serve all!!!!!
        // idea would be to fallback to en always if there is an empty field

        // if there was no movie description in the native language get it from default
        if (tag.getPlot() == null) {
            MovieIdDescription2.addDescription(movieId, tag, moviesService);
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
