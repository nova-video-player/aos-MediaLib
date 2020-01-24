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


package com.archos.mediascraper.xml;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

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

import java.util.HashMap;
import java.util.Locale;

public class MovieScraper2 extends BaseScraper2 {
    private static final String PREFERENCE_NAME = "themoviedb.org";

    private final static String TAG = "MovieScraper2";
    private final static boolean DBG = false;

    private static ScraperSettings sSettings;

    public MovieScraper2(Context context) {
        super(context);
    }

    @Override
    public ScrapeSearchResult getMatches2(SearchInfo info, int maxItems) {
        // check input
        if (info == null || !(info instanceof MovieSearchInfo)) {
            Log.e(TAG, "bad search info: " + info == null ? "null" : "tvshow in movie scraper");
            return new ScrapeSearchResult(null, true, ScrapeStatus.ERROR, null);
        }
        MovieSearchInfo searchInfo = (MovieSearchInfo) info;
        // get configured language
        String language = getLanguage(mContext);

        HttpCache cache = HttpCache.getInstance(MediaScraper.getXmlCacheDirectory(mContext),
                MediaScraper.XML_CACHE_TIMEOUT, MediaScraper.CACHE_FALLBACK_DIRECTORY,
                MediaScraper.CACHE_OVERWRITE_DIRECTORY);

        JSONFileFetcher jff = new JSONFileFetcher(cache);
        if (DBG) Log.d(TAG, "movie search:" + searchInfo.getName() + " year:" + searchInfo.getYear());
        SearchMovieResult searchResult = SearchMovie.search(searchInfo.getName(), language, searchInfo.getYear(), maxItems, jff);
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
        generatePreferences(mContext);

        String language = sSettings.getString("language");
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
