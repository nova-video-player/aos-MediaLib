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

import com.archos.medialib.R;
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
import com.archos.mediascraper.themoviedb3.CollectionInfo;
import com.archos.mediascraper.themoviedb3.CollectionResult;
import com.archos.mediascraper.themoviedb3.ImageConfiguration;
import com.archos.mediascraper.themoviedb3.MovieCollection;
import com.archos.mediascraper.themoviedb3.MovieCollectionImages;
import com.archos.mediascraper.themoviedb3.MovieId2;
import com.archos.mediascraper.themoviedb3.MovieIdDescription2;
import com.archos.mediascraper.themoviedb3.MovieIdImages2;
import com.archos.mediascraper.themoviedb3.MovieIdResult;
import com.archos.mediascraper.themoviedb3.MyTmdb;
import com.archos.mediascraper.themoviedb3.SearchMovie2;
import com.archos.mediascraper.themoviedb3.SearchMovieResult;
import com.archos.mediascraper.themoviedb3.SearchMovieTrailer2;
import com.uwetrottmann.tmdb2.services.CollectionsService;
import com.uwetrottmann.tmdb2.services.MoviesService;
import com.uwetrottmann.tmdb2.services.SearchService;

import java.util.HashMap;
import java.util.Locale;

import okhttp3.Cache;

import static com.archos.mediascraper.MovieTags.isCollectionAlreadyKnown;
import static com.archos.mediascraper.themoviedb3.MovieCollectionImages.downloadCollectionImage;


public class MovieScraper3 extends BaseScraper2 {
    private static final String PREFERENCE_NAME = "themoviedb.org";

    private final static String TAG = "MovieScraper3";
    private final static boolean DBG = true;

    private static ScraperSettings sSettings = null;

    // Add caching for OkHttpClient so that queries for episodes from a same tvshow will get a boost in resolution
    static Cache cache;

    static MyTmdb tmdb = null;
    static SearchService searchService = null;
    static MoviesService moviesService = null;
    static CollectionsService collectionService = null;

    static String apiKey = null;

    public MovieScraper3(Context context) {
        super(context);
        // ensure cache is initialized
        synchronized (MovieScraper3.class) {
            cache = ScraperCache.getCache(context);
            apiKey = context.getString(R.string.tmdb_api_key);
        }
    }

    public static void reauth() {
        tmdb = new MyTmdb(apiKey, cache);
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
        // TODO: this triggers scrape for all search results, is this intended?
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
        // TODO: why it searches every first level result?
        String language = getLanguage(mContext);

        long movieId = result.getId();
        Uri searchFile = result.getFile();

        if (tmdb == null) reauth();
        if (moviesService == null) moviesService = tmdb.moviesService();

        // get base info
        MovieIdResult search = MovieId2.getBaseInfo(movieId, language, moviesService, mContext);
        if (search.status != ScrapeStatus.OKAY) {
            return new ScrapeDetailResult(search.tag, true, null, search.status, search.reason);
        }

        MovieTags tag = search.tag;
        tag.setFile(searchFile);

        SearchMovieTrailer2.addTrailers(movieId, tag, language, moviesService);

        // add posters and backdrops
        // TODO: CHANGE POSTER SIZE HERE? i.e. ORIGINAL
        MovieIdImages2.addImages(movieId, tag, language, result.getPosterPath(), result.getBackdropPath(),
                ImageConfiguration.PosterSize.W342, // large poster
                ImageConfiguration.PosterSize.W92,  // thumb poster
                ImageConfiguration.BackdropSize.W1280, // large bd
                ImageConfiguration.BackdropSize.W300,  // thumb bd
                searchFile.toString(), moviesService, mContext);
        ScraperImage defaultPoster = tag.getDefaultPoster();
        if (defaultPoster != null) {
            tag.setCover(defaultPoster.getLargeFileF());
        }

        // MovieCollection poster/backdrops and information are handled in the MovieTag because it is easier
        if (tag.getCollectionId() != -1 && ! isCollectionAlreadyKnown(tag.getCollectionId(), mContext)) { // in presence of a movie collection/saga
            if (collectionService == null) collectionService = tmdb.collectionService();
            CollectionResult collectionResult = MovieCollection.getInfo(tag.getCollectionId(), language, collectionService);
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
        if (tag.getPlot().length() == 0) {
            if (DBG) Log.d(TAG, "ScrapeDetailResult: getting description in en because plot non existent in " + language);
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
            if (DBG) Log.d(TAG, "generatePreferences: defaultLang=" + defaultLang);
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
