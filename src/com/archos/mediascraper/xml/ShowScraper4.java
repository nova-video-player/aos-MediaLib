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
import android.content.ContentUris;
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
import com.archos.mediascraper.themoviedb3.ShowIdEpisodeSearch;
import com.archos.mediascraper.themoviedb3.ShowIdEpisodeSearchResult;
import com.archos.mediascraper.themoviedb3.ShowIdEpisodes;
import com.archos.mediascraper.themoviedb3.ShowIdImagesParser;
import com.archos.mediascraper.themoviedb3.ShowIdImagesResult;
import com.archos.mediascraper.themoviedb3.ShowIdParser;
import com.archos.mediascraper.themoviedb3.ShowIdSeasonSearch;
import com.archos.mediascraper.themoviedb3.ShowIdSeasonSearchResult;
import com.archos.mediascraper.themoviedb3.ShowIdTvSearch;
import com.archos.mediascraper.themoviedb3.ShowIdTvSearchResult;
import com.uwetrottmann.tmdb2.entities.TvEpisode;
import com.uwetrottmann.tmdb2.entities.TvSeason;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.Cache;

import static com.archos.mediascraper.TagsFactory.buildShowTagsOnlineId;

public class ShowScraper4 extends BaseScraper2 {
    private static final String PREFERENCE_NAME = "themoviedb.org";

    private static final Logger log = LoggerFactory.getLogger(ShowScraper4.class);

    // Benchmarks tells that with tv shows sorted in folders, size of 100 or 10 or even provides the same cacheHits on fake collection of 30k episodes, 250 shows
    private final static LruCache<String, Map<String, EpisodeTags>> sEpisodeCache = new LruCache<>(100);

    private static ScraperSettings sSettings = null;

    // Add caching for OkHttpClient so that queries for episodes from a same tvshow will get a boost in resolution
    static Cache cache;

    static MyTmdb tmdb = null;
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
        // maxItems = -1 means all
        // check input
        // can return a tvshow with less seasons that the one required in info
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
                + " e:" + searchInfo.getEpisode() + ", maxItems=" + maxItems);
        if (tmdb == null) reauth();
        SearchShowResult searchResult = SearchShow.search(searchInfo, language, maxItems, this, tmdb);
        if (searchResult.result.size() > 0) log.debug("getMatches2: match found " + searchResult.result.get(0).getTitle() + " id " + searchResult.result.get(0).getId());
        return new ScrapeSearchResult(searchResult.result, false, searchResult.status, searchResult.reason);
    }

    @Override
    protected ScrapeDetailResult getDetailsInternal(SearchResult result, Bundle options) {
        // result is the global tvShow

        boolean doRebuildShowTag = false;
        // never reuse old show info since there could be new episodes/seasons
        final boolean useOldShow = false;
        // ITEM_REQUEST_BASIC_SHOW = true means show (without episodes) is to be scraped manually (ManualShowScrappingSearchFragment)
        //  --> no need to get full season or else we have already all info in getMatch2
        // ITEM_REQUEST_BASIC_VIDEO = true means single episode is to be scraped manually (ManualVideoScrappingSearchFragment/VideoInfoScraperSearchFragment)
        //  --> no need to get full season but in this case we have already ITEM_REQUEST_EPISODE set (TBC)

        boolean basicShow = options != null && options.containsKey(Scraper.ITEM_REQUEST_BASIC_SHOW);
        boolean basicEpisode = options != null && options.containsKey(Scraper.ITEM_REQUEST_BASIC_VIDEO);
        boolean getAllEpisodes = options != null && options.containsKey(Scraper.ITEM_REQUEST_ALL_EPISODES);
        int season = -1;
        int episode = -1;
        if (options != null) {
            season = options.getInt(Scraper.ITEM_REQUEST_SEASON, -1);
            episode = options.getInt(Scraper.ITEM_REQUEST_EPISODE, -1);
        } else
            log.debug("getDetailsInternal: options is null");

        if (episode != -1) log.error("getDetailsInternal: episode should NEVER be -1 since cannot get on single episode season poster!!!");

        String resultLanguage = result.getLanguage();
        if (TextUtils.isEmpty(resultLanguage))
            resultLanguage = "en";
        int showId = result.getId();
        String key = (getAllEpisodes ? "all" : (season != -1 ? "s" + season : "") + (episode != -1 ? "e" + episode : ""));
        String showKey = showId + "|" + key + "|" + resultLanguage;
        log.debug("getDetailsInternal: " + result.getTitle() + "(" + showId + ") " + key + " in " + resultLanguage +
                " (basicShow=" + basicShow + "/basicEpisode=" + basicEpisode + ")");

        Map<String, EpisodeTags> allEpisodes = null;
        ShowTags showTags = null;
        ShowIdImagesResult searchImages = null;

        log.debug("getDetailsInternal: probing cache for showKey " + showKey);
        allEpisodes = sEpisodeCache.get(showKey);
        if (log.isTraceEnabled()) debugLruCache(sEpisodeCache);

        if (allEpisodes == null) {
            log.debug("getDetailsInternal: allEpisodes is null, need to get show");

            // if we get allEpisodes it means we also have global show info and there is no need to redo it
            if (tmdb == null) reauth();
            showTags = new ShowTags(); // to get the global show info
            allEpisodes = new HashMap<>(); // to get all episodes info

            int number_of_seasons = -1;

            // need to parse that show
            // start with global show information before retrieving all episodes
            // one could think that info is to be retrieved only if show not known but it needs to be done always since there could be new episodes or seasons: rely on cache to boost things
            // result: isShowKnown always false
            Boolean isShowKnown = useOldShow && isShowAlreadyKnown(showId, mContext);
            log.debug("getDetailsInternal: show known " + isShowKnown);

            if (! isShowKnown || getAllEpisodes) {
                String lang = resultLanguage;
                // for getAllEpisodes we need to get the number of seasons thus get it
                log.debug("getDetailsInternal: show " + showId + " not known or getAllEpisodes " + getAllEpisodes);
                // query first tmdb
                ShowIdTvSearchResult showIdTvSearchResult = ShowIdTvSearch.getTvShowResponse(showId, resultLanguage, tmdb);
                // parse result to get global show basic info
                if (showIdTvSearchResult.status != ScrapeStatus.OKAY)
                    return new ScrapeDetailResult(showTags, true, null, showIdTvSearchResult.status, showIdTvSearchResult.reason);
                else showTags = ShowIdParser.getResult(showIdTvSearchResult.tvShow, result.getYear(), mContext);
                log.debug("getDetailsInternal: downloaded showTags " + showTags.getOnlineId() + " " + showTags.getTitle());

                // if there is no title or description research in en
                if (showTags.getPlot() == null || showTags.getTitle() == null || showTags.getPlot().length() == 0 || showTags.getTitle().length() == 0) {
                    showIdTvSearchResult = ShowIdTvSearch.getTvShowResponse(showId, "en", tmdb);
                    if (showIdTvSearchResult.status != ScrapeStatus.OKAY)
                        return new ScrapeDetailResult(showTags, true, null, showIdTvSearchResult.status, showIdTvSearchResult.reason);
                    else showTags = ShowIdParser.getResult(showIdTvSearchResult.tvShow, result.getYear(), mContext);
                }

                // now we have the number of seasons if we need getAllEpisodes
                number_of_seasons = showIdTvSearchResult.tvShow.number_of_seasons;
                if (number_of_seasons < season) log.warn("getDetailsInternal: season (" + season + ")" + " > number_of_seasons (" + number_of_seasons + ")");
                // no need to do this if show known
                if (!isShowKnown) {
                    log.debug("getDetailsInternal: get all images for show " + showId);

                    // get show posters and backdrops
                    searchImages = ShowIdImagesParser.getResult(showTags.getTitle(), showIdTvSearchResult.tvShow, lang, mContext);
                    if (!searchImages.backdrops.isEmpty())
                        showTags.setBackdrops(searchImages.backdrops);
                    else log.debug("getDetailsInternal: backdrops empty!");
                    // needs to be done after setBackdrops not to be erased
                    if (result.getBackdropPath() != null)  showTags.addDefaultBackdropTMDB(mContext, result.getBackdropPath());

                    if (!searchImages.networklogos.isEmpty())
                        showTags.setNetworkLogos(searchImages.networklogos);
                    else log.debug("getDetailsInternal: networklogos empty!");
                    // needs to be done after setNetworkLogos not to be erased
                    if (result.getNetworkLogoPath() != null)  showTags.addNetworkLogoGITHUB(mContext, result.getNetworkLogoPath());

                    if (!searchImages.actorphotos.isEmpty())
                        showTags.setActorPhotos(searchImages.actorphotos);
                    else log.debug("getDetailsInternal: actorphotos empty!");
                    // needs to be done after setActorPhotos not to be erased
                    if (result.getActorPhotoPath() != null)  showTags.addActorPhotoTMDB(mContext, result.getActorPhotoPath());

                    if (!searchImages.clearlogos.isEmpty())
                        showTags.setClearLogos(searchImages.clearlogos);
                    else log.debug("getDetailsInternal: clearlogos empty!");
                    // needs to be done after setClearLogos not to be erased
                    if (result.getClearLogoPath() != null)  showTags.addClearLogoFTV(mContext, result.getClearLogoPath());

                    if (!searchImages.studiologos.isEmpty())
                        showTags.setStudioLogos(searchImages.studiologos);
                    else log.debug("getDetailsInternal: studiologos empty!");
                    // needs to be done after setStudioLogos not to be erased
                    if (result.getStudioLogoPath() != null)  showTags.addStudioLogoGITHUB(mContext, result.getStudioLogoPath());

                    if (!searchImages.posters.isEmpty())
                        showTags.setPosters(searchImages.posters);
                    else log.debug("getDetailsInternal: posters empty!");
                    // needs to be done after setPosters not to be erased
                    if (result.getPosterPath() != null) showTags.addDefaultPosterTMDB(mContext, result.getPosterPath());

                    // only downloads main backdrop/poster and not the entire collection (x8 in size)
                    showTags.downloadPoster(mContext);
                    showTags.downloadBackdrop(mContext);
                    showTags.downloadNetworkLogo(mContext);
                    showTags.downloadActorPhoto(mContext);
                    showTags.downloadClearLogo(mContext);
                    showTags.downloadStudioLogo(mContext);
                    //showTags.downloadPosters(mContext);
                    //showTags.downloadBackdrops(mContext);
                    showTags.downloadNetworkLogos(mContext);
                    showTags.downloadStudioLogos(mContext);
                    showTags.downloadActorPhotos(mContext);
                    //showTags.downloadClearLogos(mContext);


                } else {
                    doRebuildShowTag = true;
                }
            } else {
                doRebuildShowTag = true;
            }

            if (doRebuildShowTag == true) {
                log.debug("getDetailsInternal: show " + showId + " is known: rebuild from tag");
                // showTags exits we get it from db
                showTags = buildShowTagsOnlineId(mContext, showId);
                if (showTags == null)
                    log.warn("getDetailsInternal: show " + showId + " tag is null but known!");
                else log.debug("getDetailsInternal: show " + showId + " " + key +
                        " in " + resultLanguage + " already known: " + showTags.getTitle() + ", plot: " + showTags.getPlot());
            }

            // retreive now the desired episodes
            List<TvEpisode> tvEpisodes = new ArrayList<>();
            Map<Integer, TvSeason> tvSeasons = new HashMap<Integer, TvSeason>();

            if (getAllEpisodes) {
                // get all episodes: loop over seasons and concatenate
                for (int s = 1; s <= number_of_seasons; s++) {
                    log.debug("getDetailsInternal: get episodes for show " + showId + " s" + s);
                    ShowIdSeasonSearchResult showIdSeason = ShowIdSeasonSearch.getSeasonShowResponse(showId, s, resultLanguage, tmdb);
                    if (showIdSeason.status == ScrapeStatus.OKAY) {
                        tvEpisodes.addAll(showIdSeason.tvSeason.episodes);
                        if (! tvSeasons.containsKey(showIdSeason.tvSeason.season_number))
                            tvSeasons.put(showIdSeason.tvSeason.season_number, showIdSeason.tvSeason);
                    } else {
                        log.warn("getDetailsInternal: scrapeStatus for s" + s + " is NOK!");
                        return new ScrapeDetailResult(new EpisodeTags(), true, null, showIdSeason.status, showIdSeason.reason);
                    }
                }
            } else {
                if (episode != -1) {
                    // get a single episode: should never get there since it means that we cannot infer poster/backdrop from single episode (need season)
                    log.debug("getDetailsInternal: get single episode for show " + showId + " s" + season + "e" + episode);
                    ShowIdEpisodeSearchResult showIdEpisode = ShowIdEpisodeSearch.getEpisodeShowResponse(showId, season, episode, resultLanguage, tmdb);
                    if (showIdEpisode.status == ScrapeStatus.OKAY)
                        tvEpisodes.add(showIdEpisode.tvEpisode);
                    else {
                        log.warn("getDetailsInternal: scrapeStatus for s" + season + "e" + episode + " is NOK!");
                        // save showtag even if episodetag is empty
                        EpisodeTags episodeTag = new EpisodeTags();
                        episodeTag.setShowTags(showTags);
                        // even if this is nok record season and episode not to end up with s00e00
                        episodeTag.setSeason(Integer.parseInt(result.getExtra().getString(ShowUtils.SEASON, "0")));
                        episodeTag.setEpisode(Integer.parseInt(result.getExtra().getString(ShowUtils.EPNUM, "0")));
                        return new ScrapeDetailResult(episodeTag, true, null, showIdEpisode.status, showIdEpisode.reason);
                    }
                } else {
                    // by default we get the whole season on which the show has been identified
                    if (season == -1) {
                        log.error("getDetailsInternal: season cannot be -1!!!");
                        // save showtag even if episodetag is empty
                        EpisodeTags episodeTag = new EpisodeTags();
                        episodeTag.setShowTags(showTags);
                        return new ScrapeDetailResult(episodeTag, true, null, ScrapeStatus.ERROR_PARSER, null);
                    }
                    log.debug("getDetailsInternal: get full season for show " + showId + " s" + season);
                    ShowIdSeasonSearchResult showIdSeason = ShowIdSeasonSearch.getSeasonShowResponse(showId, season, resultLanguage, tmdb);
                    if (showIdSeason.status == ScrapeStatus.OKAY) {
                        tvEpisodes.addAll(showIdSeason.tvSeason.episodes);
                        if (! tvSeasons.containsKey(showIdSeason.tvSeason.season_number))
                            tvSeasons.put(showIdSeason.tvSeason.season_number, showIdSeason.tvSeason);
                    } else {
                        // save showtag even if episodetag is empty
                        EpisodeTags episodeTag = new EpisodeTags();
                        episodeTag.setShowTags(showTags);
                        // even if this is nok record season and episode not to end up with s00e00
                        episodeTag.setSeason(Integer.parseInt(result.getExtra().getString(ShowUtils.SEASON, "0")));
                        episodeTag.setEpisode(Integer.parseInt(result.getExtra().getString(ShowUtils.EPNUM, "0")));
                        log.warn("getDetailsInternal: scrapeStatus for season " + season + " is NOK!");
                        return new ScrapeDetailResult(episodeTag, true, null, showIdSeason.status, showIdSeason.reason);
                    }
                }
            }

            // get now all episodes in tvEpisodes
            Map<String, EpisodeTags> searchEpisodes = ShowIdEpisodes.getEpisodes(showId, tvEpisodes, tvSeasons, showTags, resultLanguage, tmdb, mContext);
            if (!searchEpisodes.isEmpty()) {
                allEpisodes = searchEpisodes;
                // put that result in cache.
                log.debug("getDetailsInternal: sEpisodeCache put allEpisodes with key " + showKey);
                sEpisodeCache.put(showKey, allEpisodes);
            }

            // if we have episodes and posters map them to each other
            if (!allEpisodes.isEmpty() && !showTags.getAllPostersInDb(mContext).isEmpty())
                mapPostersEpisodes(allEpisodes, showTags.getAllPostersInDb(mContext), resultLanguage);
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
        EpisodeTags returnValue = buildTag(allEpisodes,
                Integer.parseInt(result.getExtra().getString(ShowUtils.EPNUM, "0")),
                Integer.parseInt(result.getExtra().getString(ShowUtils.SEASON, "0")),
                showTags);
        log.debug("getDetailsInternal : ScrapeStatus.OKAY " + returnValue.getShowTitle() + " " + returnValue.getShowId() + " " + returnValue.getTitle());
        Bundle extraOut = buildBundle(allEpisodes, options);
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
        log.debug("buildTag allEpisodes.size=" + allEpisodes.size() + " epnum=" + epnum + ", season=" + season + ", showId=" + showTags.getId());
        EpisodeTags episodeTag = null;
        if (!allEpisodes.isEmpty()) {
            String key = season + "|" + epnum;
            log.debug("buildTag: allEpisodes not empty trying to find " + key);
            episodeTag = allEpisodes.get(key);
        }
        if (episodeTag == null) {
            log.debug("buildTag: shoot episode not in allEpisodes");
            episodeTag = new EpisodeTags();
            // assume episode / season of request
            episodeTag.setSeason(season);
            episodeTag.setEpisode(epnum);
            episodeTag.setShowTags(showTags);
            // also check if there is a poster
            List<ScraperImage> posters = showTags.getPosters();
            if (posters != null) {
                log.debug("buildTag: posters not null");
                for (ScraperImage image : posters) {
                    if (image.getSeason() == season) {
                        log.debug("buildTag: " + showTags.getTitle() + " season poster s" + season + " " + image.getLargeUrl());
                        episodeTag.setPosters(image.asList());
                        episodeTag.downloadPoster(mContext);
                        break;
                    }
                }
            }
        } else {
            log.debug("buildTag: episodeTag not null");
            if (episodeTag.getPosters() == null) {
                log.warn("buildTag: " + episodeTag.getTitle() + " has null posters!");
            } else if (episodeTag.getPosters().isEmpty()) {
                log.warn("buildTag: " + episodeTag.getTitle() + " has empty posters!");
            }
            if (episodeTag.getDefaultPoster() == null) {
                log.warn("buildTag: " + episodeTag.getTitle() + " has no defaultPoster! Should add default show one.");
            }
            if (episodeTag.getShowTags() == null) {
                log.warn("buildTag: " + episodeTag.getTitle() + " has empty showTags!");
            }
            // download still & poster because episode has been selected here
            episodeTag.downloadPicture(mContext);
            episodeTag.downloadPoster(mContext);
        }
        log.debug("buildTag: " + episodeTag.getShowTitle() + " " + episodeTag.getShowId() + " " + episodeTag.getTitle());
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
        String[] baseProjection = {ScraperStore.Show.ONLINE_ID};
        Cursor cursor = contentResolver.query(
                ContentUris.withAppendedId(ScraperStore.Show.URI.ONLINE_ID, showId),
                baseProjection, null, null, null);
        Boolean isKnown = false;
        if (cursor != null) isKnown = cursor.moveToFirst();
        cursor.close();
        log.debug("isShowAlreadyKnown: " + showId + " " + isKnown);
        return isKnown;
    }
}
