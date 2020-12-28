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
import android.os.Bundle;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.SparseArray;

import com.archos.medialib.R;
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
import com.archos.mediascraper.thetvdb.MyTheTVdb;
import com.archos.mediascraper.thetvdb.SearchShow;
import com.archos.mediascraper.thetvdb.SearchShowResult;
import com.archos.mediascraper.thetvdb.ShowId;
import com.archos.mediascraper.thetvdb.ShowIdActors;
import com.archos.mediascraper.thetvdb.ShowIdActorsResult;
import com.archos.mediascraper.thetvdb.ShowIdBackdrops;
import com.archos.mediascraper.thetvdb.ShowIdBackdropsResult;
import com.archos.mediascraper.thetvdb.ShowIdDescription;
import com.archos.mediascraper.thetvdb.ShowIdEpisodes;
import com.archos.mediascraper.thetvdb.ShowIdEpisodesResult;
import com.archos.mediascraper.thetvdb.ShowIdPosters;
import com.archos.mediascraper.thetvdb.ShowIdPostersResult;
import com.archos.mediascraper.thetvdb.ShowIdResult;
import com.uwetrottmann.tmdb2.services.MoviesService;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.Cache;

public class ShowScraper3 extends BaseScraper2 {
    private static final String PREFERENCE_NAME = "TheTVDB.com";

    private static final Logger log = LoggerFactory.getLogger(ShowScraper3.class);

    private final static LruCache<String, Map<String, EpisodeTags>> sEpisodeCache = new LruCache<>(5);

    private static ScraperSettings sSettings = null;

    // Add caching for OkHttpClient so that queries for episodes from a same tvshow will get a boost in resolution
    static Cache cache;

    static MyTheTVdb theTvdb = null;

    static String apiKey = null;

    public ShowScraper3(Context context) {
        super(context);
        // ensure cache is initialized
        synchronized (ShowScraper3.class) {
            cache = ScraperCache.getCache(context);
            apiKey = context.getString(R.string.tvdb_api_2_key);
        }
    }

    public static void reauth() {
        theTvdb = new MyTheTVdb(apiKey, cache);
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
                + " e:" + searchInfo.getEpisode());
        if (theTvdb == null) reauth();
        SearchShowResult searchResult = SearchShow.search(searchInfo, language, maxItems, this, theTvdb);
        return new ScrapeSearchResult(searchResult.result, false, searchResult.status, searchResult.reason);
    }

    @Override
    protected ScrapeDetailResult getDetailsInternal(SearchResult result, Bundle options) {
        log.debug("getDetailsInternal: treating result show " + result.getTitle());
        boolean basicShow = options != null && options.containsKey(Scraper.ITEM_REQUEST_BASIC_SHOW);
        boolean basicEpisode = options != null && options.containsKey(Scraper.ITEM_REQUEST_BASIC_VIDEO);
        log.debug("getDetailsInternal: basicShow=" + basicShow + ", basicEpisode=" + basicEpisode);
        String resultLanguage = result.getLanguage();
        if (TextUtils.isEmpty(resultLanguage))
            resultLanguage = "en";
        int showId = result.getId();
        String showKey = showId + "|" + resultLanguage;

        Map<String, EpisodeTags> allEpisodes = null;
        ShowTags showTags = null;
        ShowIdPostersResult searchPosters = null;
        ShowIdBackdropsResult searchBackdrops = null;

        allEpisodes = sEpisodeCache.get(showKey);
        if (allEpisodes == null) {
            if (theTvdb == null) reauth();
            allEpisodes = new HashMap<>();
            showTags = new ShowTags();
            // need to parse that show
            if (!basicShow && !basicEpisode) {
                // series id basic info
                ShowIdResult searchId = ShowId.getBaseInfo(showId, resultLanguage, basicShow, basicEpisode, theTvdb, mContext);
                if (searchId.status != ScrapeStatus.OKAY)
                    return new ScrapeDetailResult(searchId.tag, true, null, searchId.status, searchId.reason);
                // for some obscur shows sometimes getPlot() or getTitle() returns null, try to fill it again with addDescription in "en" that will set it to "" if there is a problem
                if (searchId.tag.getPlot() == null || searchId.tag.getTitle() == null)
                    ShowIdDescription.addDescription(showId, searchId.tag, theTvdb);
                else // if there was no show description in the native language get it from default
                    if ((searchId.tag.getPlot().length() == 0 || searchId.tag.getTitle().length() == 0) && !resultLanguage.equals("en"))
                        ShowIdDescription.addDescription(showId, searchId.tag, theTvdb);
                showTags = searchId.tag;
                // actors
                ShowIdActorsResult searchActors = ShowIdActors.getActors(showId, theTvdb);
                if (!searchActors.actors.isEmpty())
                    showTags.addActorIfAbsent(searchActors.actors);
                // backdrops
                searchBackdrops = ShowIdBackdrops.getBackdrops(showId, showTags.getTitle(),
                        basicShow, basicEpisode, resultLanguage, theTvdb, mContext);
                if (!searchBackdrops.backdrops.isEmpty())
                    showTags.setBackdrops(searchBackdrops.backdrops);
            }
            // add backdrops & posters to show
            searchPosters = ShowIdPosters.getPosters(showId, showTags.getTitle(),
                    basicShow, basicEpisode, resultLanguage, theTvdb, mContext);
            if (!searchPosters.posters.isEmpty())
                showTags.setPosters(searchPosters.posters);
            if (!basicShow && !basicEpisode) {
                // episodes
                ShowIdEpisodesResult searchEpisodes = ShowIdEpisodes.getEpisodes(showId, showTags, resultLanguage, theTvdb, mContext);
                if (!searchEpisodes.episodes.isEmpty()) {
                    allEpisodes = searchEpisodes.episodes;
                    // put that result in cache.
                    sEpisodeCache.put(showKey, allEpisodes);
                }
            }
            // if we have episodes and posters map them to each other
            if (!allEpisodes.isEmpty() && !searchPosters.posters.isEmpty())
                mapPostersEpisodes(allEpisodes, searchPosters, resultLanguage);
        } else {
            log.debug("using cached Episode List");
            // no need to parse, we have a cached result
            // get the showTags out of one random element, they all contain the same
            Iterator<EpisodeTags> iter = allEpisodes.values().iterator();
            if (iter.hasNext()) showTags = iter.next().getShowTags();
        }
        if (showTags == null) { // if there is no info about the show there is nothing we can do
            log.debug("ScrapeDetailResult ScrapeStatus.ERROR_PARSER");
            return new ScrapeDetailResult(null, false, null, ScrapeStatus.ERROR_PARSER, null);
        }
        showTags.downloadPoster(mContext);
        EpisodeTags returnValue = buildTag(allEpisodes,
                Integer.parseInt(result.getExtra().getString(ShowUtils.EPNUM, "0")),
                Integer.parseInt(result.getExtra().getString(ShowUtils.SEASON, "0")),
                showTags);
        Bundle extraOut = buildBundle(allEpisodes, options);
        log.debug("ScrapeDetailResult ScrapeStatus.OKAY");
        return new ScrapeDetailResult(returnValue, false, extraOut, ScrapeStatus.OKAY, null);
    }

    private static void mapPostersEpisodes(Map<String, EpisodeTags> allEpisodes, ShowIdPostersResult searchPosters, String language) {
        // array to map season -> image
        SparseArray<ScraperImage> seasonPosters = new SparseArray<ScraperImage>();
        for (ScraperImage image : searchPosters.posters) {
            int season = image.getSeason();
            // season -1 is invalid, set the first only
            if (season >= 0) {
                if (seasonPosters.get(season) == null)
                    seasonPosters.put(season, image);
                else if (language.equals(image.getLanguage()) && !searchPosters.equals(seasonPosters.get(season).getLanguage())) { //reset if right language
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
}
