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

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.SparseArray;

import com.archos.medialib.R;
import com.archos.mediaprovider.video.ScraperStore;
import com.archos.mediascraper.EpisodeTags;
import com.archos.mediascraper.ScrapeDetailResult;
import com.archos.mediascraper.ScrapeSearchResult;
import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.Scraper;
import com.archos.mediascraper.ScraperCache;
import com.archos.mediascraper.ScraperImage;
import com.archos.mediascraper.SearchResult;
import com.archos.mediascraper.ShowTags;
import com.archos.mediascraper.ShowUtils;
import com.archos.mediascraper.preprocess.SearchInfo;
import com.archos.mediascraper.preprocess.TvShowSearchInfo;
import com.archos.mediascraper.settings.ScraperSetting;
import com.archos.mediascraper.settings.ScraperSettings;
import com.archos.mediascraper.themoviedb3.MyTmdb;
import com.archos.mediascraper.themoviedb3.SearchShow;
import com.archos.mediascraper.themoviedb3.SearchShowResult;
import com.archos.mediascraper.themoviedb3.ShowIdEpisodes;
import com.archos.mediascraper.themoviedb3.ShowIdEpisodesResult;
import com.archos.mediascraper.themoviedb3.ShowIdImagesParser;
import com.archos.mediascraper.themoviedb3.ShowIdImagesResult;
import com.archos.mediascraper.themoviedb3.ShowIdParser;
import com.archos.mediascraper.themoviedb3.ShowIdSearch;
import com.archos.mediascraper.themoviedb3.ShowIdSearchResult;
import com.uwetrottmann.tmdb2.services.TvEpisodesService;
import com.uwetrottmann.tmdb2.services.TvSeasonsService;
import com.uwetrottmann.tmdb2.services.TvService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.Cache;

public class ShowScraper4 extends BaseScraper2 {
    private static final String PREFERENCE_NAME = "themoviedb.org";

    private static final Logger log = LoggerFactory.getLogger(ShowScraper4.class);

    // Benchmarks tells that with tv shows sorted in folders, size of 100 or 10 or even provides the same cacheHits on fake collection of 30k episodes, 250 shows
    private final static LruCache<String, Map<String, EpisodeTags>> sEpisodeCache = new LruCache<>(10);

    private static ScraperSettings sSettings = null;

    // Add caching for OkHttpClient so that queries for episodes from a same tvshow will get a boost in resolution
    static Cache cache;

    static MyTmdb tmdb = null;

    static TvService tvService = null;
    static TvSeasonsService tvSeasonsService = null;
    static TvEpisodesService tvEpisodesService = null;

    static String apiKey = null;

    public ShowScraper4(Context context) {
        super(context);
        // ensure cache is initialized
        synchronized (ShowScraper4.class) {
            cache = ScraperCache.getCache(context);
            apiKey = context.getString(R.string.tmdb_api_key);
        }
    }

    public static void debugLruCache(LruCache<String, Map<String, EpisodeTags>> lruCache) {
        log.debug("debugLruCache: size=" + lruCache.size());
        log.debug("debugLruCache: putCount=" + lruCache.putCount());
        log.debug("debugLruCache: hitCount=" + lruCache.hitCount());
        log.debug("debugLruCache: missCount=" + lruCache.missCount());
        log.debug("debugLruCache: evictionCount=" + lruCache.evictionCount());
    }

    public static void reauth() {
        tmdb = new MyTmdb(apiKey, cache);
    }

    @Override
    public ScrapeSearchResult getMatches2(SearchInfo info, int maxItems) {
        // check input
        if (info == null || !(info instanceof TvShowSearchInfo)) {
            log.error("getMatches2: bad search info: " + info == null ? "null" : "movie in show scraper");
            log.debug("getMatches2: ScrapeSearchResult ScrapeStatus.ERROR");
            return new ScrapeSearchResult(null, false, ScrapeStatus.ERROR, null);
        }
        TvShowSearchInfo searchInfo = (TvShowSearchInfo) info;
        // get configured language
        String language = getLanguage(mContext);
        log.debug("getMatches2: tvshow search:" + searchInfo.getShowName()
                + " s:" + searchInfo.getSeason()
                + " e:" + searchInfo.getEpisode() + ", maxItems=" +maxItems);
        if (tmdb == null) reauth();
        SearchShowResult searchResult = SearchShow.search(searchInfo, language, maxItems, this, tmdb);
        return new ScrapeSearchResult(searchResult.result, false, searchResult.status, searchResult.reason);
    }

    @Override
    protected ScrapeDetailResult getDetailsInternal(SearchResult result, Bundle options) {
        // TODO MARC understand basicShow or basicEpisode booleans
        // TODO MARC two options used only with Manual{Show|Video}ScrappingSearchFragment.java
        boolean basicShow = options != null && options.containsKey(Scraper.ITEM_REQUEST_BASIC_SHOW);
        boolean basicEpisode = options != null && options.containsKey(Scraper.ITEM_REQUEST_BASIC_VIDEO);
        String resultLanguage = result.getLanguage();
        if (TextUtils.isEmpty(resultLanguage))
            resultLanguage = "en";
        int showId = result.getId();
        log.debug("getDetailsInternal: basicShow=" + basicShow + ", basicEpisode=" + basicEpisode + " for " + result.getTitle() + "(" + showId + ") in " + resultLanguage);
        String showKey = showId + "|" + resultLanguage;

        Map<String, EpisodeTags> allEpisodes = null;
        ShowTags showTags = null;
        ShowIdImagesResult searchImages = null;

        allEpisodes = sEpisodeCache.get(showKey);
        if (log.isTraceEnabled()) debugLruCache(sEpisodeCache);

        if (allEpisodes == null) {
            // if we get allEpisodes it means we also have global show info and there is no need to redo it
            if (tmdb == null) reauth();
            showTags = new ShowTags(); // to get the global show info
            allEpisodes = new HashMap<>(); // to get all episodes info

            // need to parse that show
            if (!basicShow && !basicEpisode) {
                // start with global show information before retrieving all episodes
                // if show not known get its info
                if (! isShowAlreadyKnown(showId, mContext)) {
                    // query first tmdb
                    ShowIdSearchResult showIdSearchResult = ShowIdSearch.getTvShowResponse(showId, resultLanguage, tmdb);
                    // parse result to get global show basic info
                    if (showIdSearchResult.status != ScrapeStatus.OKAY)
                        return new ScrapeDetailResult(showTags, true, null, showIdSearchResult.status, showIdSearchResult.reason);
                    else
                        showTags = ShowIdParser.getResult(showIdSearchResult.tvShow, mContext);

                    // if there is no title or description research in en
                    if (showTags.getPlot() == null || showTags.getTitle() == null || showTags.getPlot().length() == 0 || showTags.getTitle().length() == 0)
                        showIdSearchResult = ShowIdSearch.getTvShowResponse(showId, "en", tmdb);
                    if (showIdSearchResult.status != ScrapeStatus.OKAY)
                        return new ScrapeDetailResult(showTags, true, null, showIdSearchResult.status, showIdSearchResult.reason);
                    else
                        showTags = ShowIdParser.getResult(showIdSearchResult.tvShow, mContext);

                    // get show posters and backdrops
                    searchImages = ShowIdImagesParser.getResult(showTags.getTitle(), showIdSearchResult.tvShow.images, mContext);
                    if (!searchImages.backdrops.isEmpty())
                        showTags.setBackdrops(searchImages.backdrops);
                    if (!searchImages.posters.isEmpty())
                        showTags.setPosters(searchImages.posters);

                    // get also all episodes
                    // since query has been done just before we are sure that there is no error since scrape error has been handled before
                    ShowIdEpisodesResult searchEpisodes = ShowIdEpisodes.getEpisodes(showId, showTags, resultLanguage, tmdb, mContext);
                    if (!searchEpisodes.episodes.isEmpty()) {
                        allEpisodes = searchEpisodes.episodes;
                        // put that result in cache.
                        sEpisodeCache.put(showKey, allEpisodes);
                    }
                }
            }
            // TODO MARC for posters season poster is different and needs to be retrieved i.e. case !basicShow for specific season???
            ///if (!basicShow) query for poster of the season --> meaning that we know the season
            // get show posters and backdrops
            //if (!basicShow)
            //    searchImages = ShowIdImagesParser.getResult(showTags.getTitle(), showIdSearchResult.tvShow.SEASONS.images, mContext);

            //if (!searchImages.posters.isEmpty())
            //    showTags.setPosters(searchImages.posters);

            // TODO MARC
            // if we have episodes and posters map them to each other
            if (!allEpisodes.isEmpty() && !searchImages.posters.isEmpty())
                mapPostersEpisodes(allEpisodes, searchImages.posters, resultLanguage);
        } else {
            log.debug("getDetailsInternal: cache boost for showId (all episodes)");
            // no need to parse, we have a cached result
            // get the showTags out of one random element, they all contain the same
            Iterator<EpisodeTags> iter = allEpisodes.values().iterator();
            if (iter.hasNext()) showTags = iter.next().getShowTags();
        }
        if (showTags == null) { // if there is no info about the show there is nothing we can do
            log.debug("getDetailsInternal: ScrapeStatus.ERROR_PARSER");
            return new ScrapeDetailResult(null, false, null, ScrapeStatus.ERROR_PARSER, null);
        }
        showTags.downloadPoster(mContext);
        EpisodeTags returnValue = buildTag(allEpisodes,
                Integer.parseInt(result.getExtra().getString(ShowUtils.EPNUM, "0")),
                Integer.parseInt(result.getExtra().getString(ShowUtils.SEASON, "0")),
                showTags);
        Bundle extraOut = buildBundle(allEpisodes, options);
        log.debug("getDetailsInternal ScrapeStatus.OKAY");
        return new ScrapeDetailResult(returnValue, false, extraOut, ScrapeStatus.OKAY, null);
    }

    private static void mapPostersEpisodes(Map<String, EpisodeTags> allEpisodes, List<ScraperImage> posters, String language) {
        // array to map season -> image
        SparseArray<ScraperImage> seasonPosters = new SparseArray<ScraperImage>();
        for (ScraperImage image : posters) {
            int season = image.getSeason();
            // season -1 is invalid, set the first only
            if (season >= 0) {
                if (seasonPosters.get(season) == null)
                    seasonPosters.put(season, image);
                else if (language.equals(image.getLanguage())) { //reset if right language
                    seasonPosters.put(season, image);
                }
            }
        }
        // try to find a season poster for each episode
        for (EpisodeTags episode : allEpisodes.values()) {
            int season = episode.getSeason();
            ScraperImage image = seasonPosters.get(season);
            if (image != null) {
                episode.setPosters(image.asList());
                // not downloading that here since we don't want all posters for
                // all episodes.
            }
        }
    }

    private EpisodeTags buildTag(Map<String, EpisodeTags> allEpisodes, int epnum, int season, ShowTags showTags) {
        EpisodeTags episodeTag = null;
        if (!allEpisodes.isEmpty()) {
            String key = season + "|" + epnum;
            episodeTag = allEpisodes.get(key);
        }
        if (episodeTag == null) {
            episodeTag = new EpisodeTags();
            // assume episode / season of request
            episodeTag.setSeason(season);
            episodeTag.setEpisode(epnum);
            episodeTag.setShowTags(showTags);
            // also check if there is a poster
            List<ScraperImage> posters = showTags.getPosters();
            if (posters != null) {
                for (ScraperImage image : posters) {
                    if (image.getSeason() == season) {
                        episodeTag.setPosters(image.asList());
                        episodeTag.downloadPoster(mContext);
                        break;
                    }
                }
            }
        } else {
            episodeTag.downloadPicture(mContext);
            episodeTag.downloadPoster(mContext);
        }
        return episodeTag;
    }

    private Bundle buildBundle(Map<String, EpisodeTags> allEpisodes, Bundle options) {
        Bundle bundle = null;
        if (options != null && options.containsKey(Scraper.ITEM_REQUEST_ALL_EPISODES) && !allEpisodes.isEmpty()) {
            bundle = new Bundle();
            for (Map.Entry<String, EpisodeTags> item : allEpisodes.entrySet())
                bundle.putParcelable(item.getKey(), item.getValue());
        }
        return bundle;
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

    public static boolean isShowAlreadyKnown(Integer showId, Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        String[] selectionArgs = {String.valueOf(showId)};
        String[] baseProjection = {ScraperStore.Show.ID};
        String nameSelection = ScraperStore.Show.ID + "=?";
        Cursor cursor = contentResolver.query(ScraperStore.Show.URI.BASE, baseProjection,
                nameSelection, selectionArgs, null);
        Boolean isKnown = false;
        if (cursor != null) isKnown = cursor.moveToFirst();
        cursor.close();
        return isKnown;
    }
}
