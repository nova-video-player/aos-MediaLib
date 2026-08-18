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
import android.os.Parcel;
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
import com.archos.mediascraper.TagsFactory;
import com.archos.mediascraper.ShowTags;
import com.archos.mediascraper.ShowUtils;
import com.archos.mediascraper.preprocess.SearchInfo;
import com.archos.mediascraper.preprocess.TvShowSearchInfo;
import com.archos.filecorelibrary.FileUtils;
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

import org.apache.commons.text.similarity.LevenshteinDistance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import java.util.Map;

import okhttp3.Cache;

import static com.archos.mediascraper.TagsFactory.buildShowTagsOnlineId;

import androidx.preference.PreferenceManager;

public class ShowScraper4 extends BaseScraper2 {
    private static final String PREFERENCE_NAME = "themoviedb.org";

    private static final Logger log = LoggerFactory.getLogger(ShowScraper4.class);

    // Wrapper class to cache show metadata including number of seasons
    private static class ShowMetadata {
        ShowTags showTags;
        int numberOfSeasons;

        ShowMetadata(ShowTags showTags, int numberOfSeasons) {
            this.showTags = showTags;
            this.numberOfSeasons = numberOfSeasons;
        }
    }

    // Benchmarks tells that with tv shows sorted in folders, size of 100 or 10 or even provides the same cacheHits on fake collection of 30k episodes, 250 shows
    private final static LruCache<String, Map<String, EpisodeTags>> sEpisodeCache = new LruCache<>(100);

    // Cache season poster mappings to avoid repeated DB queries when scraping multiple episodes from same show
    private final static LruCache<String, SparseArray<ScraperImage>> sSeasonPosterCache = new LruCache<>(50);

    // Cache show metadata (ShowTags + number_of_seasons) to avoid redundant API calls when scraping multiple episodes/seasons from same show
    private final static LruCache<String, ShowMetadata> sShowMetadataCache = new LruCache<>(200);

    // Add caching for OkHttpClient so that queries for episodes from a same tvshow will get a boost in resolution
    static Cache cache;

    private static volatile MyTmdb tmdb = null;
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
        if (log.isDebugEnabled()) log.debug("debugLruCache(Episodes): size={}, put={}, hit={}, miss={}, evict={}", lruCache.size(), lruCache.putCount(), lruCache.hitCount(), lruCache.missCount(), lruCache.evictionCount());
    }

    public static synchronized void reauth() {
        tmdb = new MyTmdb(apiKey, cache);
    }
    
    public static synchronized MyTmdb getTmdb() {
        if (tmdb == null) reauth();
        return tmdb;
    }

    @Override
    public ScrapeSearchResult getMatches2(SearchInfo info, int maxItems) {
        // maxItems = -1 means all
        // check input
        // can return a tvshow with less seasons that the one required in info
        if (info == null || !(info instanceof TvShowSearchInfo)) {
            log.error("getMatches2: bad search info: {}", info == null ? "null" : "movie in show scraper");
            if (log.isDebugEnabled()) log.debug("getMatches2: ScrapeSearchResult ScrapeStatus.ERROR");
            return new ScrapeSearchResult(null, false, ScrapeStatus.ERROR, null);
        }
        TvShowSearchInfo searchInfo = (TvShowSearchInfo) info;
        // get configured language
        String language = Scraper.getLanguage(mContext);
        if (log.isDebugEnabled()) log.debug("getMatches2: tvshow search:{} s:{} e:{}, maxItems={}, language={}",
                searchInfo.getShowName(), searchInfo.getSeason(), searchInfo.getEpisode(), maxItems, language);

        //Make sure the presference is enabled.
        SearchShowResult searchResult = null;
        if (searchInfo.scrapeFromDB) {
            //Check the Database for this Movie, we may have scraped it already on a different URI.
            searchResult = ShowTags.getEpisodeResultIfAlreadyKnown(mContext, searchInfo.getShowName(), String.valueOf(searchInfo.getSeason()), String.valueOf(searchInfo.getEpisode()), searchInfo.getOriginalUri());
            if (searchResult != null) {
                for (SearchResult result : searchResult.result) {
                    result.setScraper(this);
                    result.setFile(searchInfo.getFile());
                }
                return new ScrapeSearchResult(searchResult.result, false, searchResult.status, searchResult.reason);
            }
        }

        List<SearchCandidate> candidates = new ArrayList<>();
        // Prefer cleaned name with year filter (if any) first
        candidates.add(new SearchCandidate(searchInfo.getShowName(), searchInfo.getFirstAiredYear()));
        if (searchInfo.getFirstAiredYear() != null && !searchInfo.getFirstAiredYear().isEmpty()) {
            // Fallback: try cleaned name without year filter if year search returns no results
            candidates.add(new SearchCandidate(searchInfo.getShowName(), null));
            // Fallback to suggestion (name + year) without year filter
            candidates.add(new SearchCandidate(searchInfo.getShowName() + " " + searchInfo.getFirstAiredYear(), null));
        }

        for (SearchCandidate candidate : candidates) {
            if (log.isDebugEnabled()) log.debug("getMatches2: trying candidate '{}' with year {}", candidate.query, candidate.year);
            // Temporarily update searchInfo with candidate values for the search call
            TvShowSearchInfo candidateInfo = new TvShowSearchInfo(
                    searchInfo.getFile(),
                    candidate.query,
                    searchInfo.getSeason(),
                    searchInfo.getEpisode(),
                    candidate.year,
                    searchInfo.getCountryOfOrigin()
            );
            searchResult = SearchShow.search(candidateInfo, language, maxItems, adultScrape, this, getTmdb());
            if (searchResult.status == ScrapeStatus.OKAY && !searchResult.result.isEmpty()) {
                if (log.isDebugEnabled()) log.debug("getMatches2: found results for '{}', stopping early", candidate.query);
                break;
            }
        }

        if (log.isDebugEnabled()) if (searchResult != null && !searchResult.result.isEmpty()) log.debug("getMatches2: match found {} id {}", searchResult.result.get(0).getTitle(), searchResult.result.get(0).getId());
        return new ScrapeSearchResult(searchResult.result, false, searchResult.status, searchResult.reason);
    }

    private static class SearchCandidate {
        String query;
        String year;
        SearchCandidate(String query, String year) {
            this.query = query;
            this.year = year;
        }
    }

    // Number of ranked candidates to consider for the title-collision fallback below.
    // Costs no extra TMDB request: the initial tv() search already returns up to 20 results in
    // one page, trimming to maxItems is done client-side in SearchParserResult.getResults().
    private static final int CASCADE_CANDIDATE_LIMIT = 5;

    /**
     * TV-specific auto-scrape entry point used by {@link Scraper#getAutoDetails}. Mirrors
     * {@link BaseScraper2#search} (which is final and shared with movies, so it cannot be
     * overridden here) but adds one behavior: when the top-ranked candidate's requested
     * season/episode cannot genuinely be found (see buildTag()), retries against the next
     * candidates that are exactly tied in Levenshtein distance with the top one, e.g. two shows
     * sharing the exact same title such as a classic show and its reboot.
     * <p>
     * This is deliberately restricted to distance-tied candidates and only triggers extra TMDB
     * requests in the failure branch, to avoid mis-attributing episodes to an unrelated,
     * lower-ranked show and to avoid any extra cost in the (overwhelmingly common) success case.
     */
    public ScrapeDetailResult searchWithTitleCollisionFallback(SearchInfo info) {
        if (info == null || !(info instanceof TvShowSearchInfo)) {
            log.error("searchWithTitleCollisionFallback: bad search info");
            return new ScrapeDetailResult(null, true, null, ScrapeStatus.ERROR, null);
        }
        ScrapeSearchResult searchResult = getMatches2(info, CASCADE_CANDIDATE_LIMIT);
        if (!searchResult.isOkay() || searchResult.results == null || searchResult.results.isEmpty()) {
            return new ScrapeDetailResult(null, searchResult.isMovie, null, searchResult.status, searchResult.reason);
        }

        TvShowSearchInfo tvSearchInfo = (TvShowSearchInfo) info;
        Bundle bundle = new Bundle();
        bundle.putInt(Scraper.ITEM_REQUEST_BASIC_VIDEO, 1);
        bundle.putInt(Scraper.ITEM_REQUEST_SEASON, tvSearchInfo.getSeason());
        bundle.putInt(Scraper.ITEM_REQUEST_EPISODE, tvSearchInfo.getEpisode());
        // keeping whole season boosts the perf since there is only one request for tmdb
        bundle.putInt(Scraper.ITEM_REQUEST_ALL_EPISODES, tvSearchInfo.getSeason());

        int topDistance = searchResult.results.get(0).getLevenshteinDistance();
        ScrapeDetailResult result = null;
        for (SearchResult candidate : searchResult.results) {
            if (candidate.getLevenshteinDistance() != topDistance) break; // only tied candidates
            result = getDetails(candidate, bundle);
            boolean genuineMatch = result != null && result.tag instanceof EpisodeTags
                    && ((EpisodeTags) result.tag).getTitle() != null;
            if (genuineMatch) {
                if (log.isDebugEnabled()) log.debug("searchWithTitleCollisionFallback: genuine match for '{}' (id {})", candidate.getTitle(), candidate.getId());
                return result;
            }
            log.info("searchWithTitleCollisionFallback: no genuine episode match for '{}' (id {}), trying next tied candidate", candidate.getTitle(), candidate.getId());
        }
        // no tied candidate yielded a genuine match: return the last attempted result (top
        // candidate's placeholder), preserving existing behavior for legitimately-not-found
        // episodes of the correct show
        return result;
    }

    @Override
    protected ScrapeDetailResult getDetailsInternal(SearchResult result, Bundle options) {
        // result is the global tvShow
        boolean doRebuildShowTag = false;
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
            if (log.isDebugEnabled()) log.debug("getDetailsInternal: options is null");

        String resultLanguage = result.getLanguage();
        if (TextUtils.isEmpty(resultLanguage))
            resultLanguage = "en";

        //Save the Show ID and Cleanup the Name now for the Cache Keys.
        int showId = result.getId();

        //If we got this result from the database, grab the tags from there and return them instead of going to TMDB.
        if (result.fromDB){
            EpisodeTags tag = TagsFactory.buildEpisodeTags(mContext, showId);
            return new ScrapeDetailResult(tag, false, null, ScrapeStatus.OKAY, null);
        }

        // Parse season and episode numbers once to avoid repeated Integer.parseInt() calls
        // Post refactor, this info may be the exact same as above, just took a longer path to get it!
        int requestedSeason = Integer.parseInt(result.getExtra().getString(ShowUtils.SEASON, "0"));
        int requestedEpisode = Integer.parseInt(result.getExtra().getString(ShowUtils.EPNUM, "0"));

        // Remap SxxE00 (special episode encoded in regular season) to S00Eyy via title matching
        if (requestedEpisode == 0 && requestedSeason > 0 && result.getFile() != null) {
            String filename = FileUtils.getFileNameWithoutExtension(result.getFile());
            String episodeTitle = ShowUtils.extractEpisodeTitle(filename, requestedSeason, 0);
            if (episodeTitle != null && !episodeTitle.isEmpty()) {
                if (log.isDebugEnabled()) log.debug("getDetailsInternal: SxxE00 detected, extracted title '{}', attempting season 0 remapping", episodeTitle);
                String resultLanguage0 = TextUtils.isEmpty(result.getLanguage()) ? "en" : result.getLanguage();
                String cleanShowName0 = ShowUtils.cleanUpName(result.getOriginalTitle().toLowerCase());
                int matchedEpisode = -1;

                // Try matching in the configured language first
                String season0Key = cleanShowName0 + "|" + showId + "|0|all|" + resultLanguage0;
                ShowIdSeasonSearchResult season0Result = ShowIdSeasonSearch.getSeasonShowResponse(season0Key, showId, 0, resultLanguage0, adultScrape, getTmdb());
                if (season0Result.status == ScrapeStatus.OKAY && season0Result.tvSeason != null && season0Result.tvSeason.episodes != null) {
                    matchedEpisode = fuzzyMatchEpisodeByTitle(episodeTitle, season0Result.tvSeason.episodes);
                }

                // Fallback to English if no match found and language is not already English
                // (filename titles are almost always in English)
                if (matchedEpisode < 0 && !"en".equals(resultLanguage0)) {
                    if (log.isDebugEnabled()) log.debug("getDetailsInternal: no match in {}, retrying season 0 in English", resultLanguage0);
                    String season0KeyEn = cleanShowName0 + "|" + showId + "|0|all|en";
                    ShowIdSeasonSearchResult season0ResultEn = ShowIdSeasonSearch.getSeasonShowResponse(season0KeyEn, showId, 0, "en", adultScrape, getTmdb());
                    if (season0ResultEn.status == ScrapeStatus.OKAY && season0ResultEn.tvSeason != null && season0ResultEn.tvSeason.episodes != null) {
                        matchedEpisode = fuzzyMatchEpisodeByTitle(episodeTitle, season0ResultEn.tvSeason.episodes);
                    }
                }

                if (matchedEpisode >= 0) {
                    log.info("getDetailsInternal: remapped S{}E00 '{}' -> S00E{}", requestedSeason, episodeTitle, matchedEpisode);
                    requestedSeason = 0;
                    requestedEpisode = matchedEpisode;
                    season = 0;
                    episode = matchedEpisode;
                } else {
                    if (log.isDebugEnabled()) log.debug("getDetailsInternal: no fuzzy match found in season 0 for title '{}'", episodeTitle);
                }
            }
        }

        // Use OPTIONS values if available (for re-scraping), otherwise use SearchResult extras (for manual scraping)
        // Manual scraping doesn't set season/episode in OPTIONS but has them in SearchResult.extras
        // Re-scraping sets season in OPTIONS but may not have SearchResult.extras populated
        int keySeasonValue = (season != -1) ? season : requestedSeason;
        int keyEpisodeValue = (episode != -1) ? episode : requestedEpisode;

        //Build the Show and Episode keys.
        String cleanShowName = ShowUtils.cleanUpName(result.getOriginalTitle().toLowerCase());
        String showKey = cleanShowName + "|" + showId + "|" + resultLanguage;
        String seasonKey =  cleanShowName + "|" + showId + "|" + keySeasonValue  + "|all|" + resultLanguage;
        String episodeKey = showId + "|" + keySeasonValue + "|" + keyEpisodeValue + "|" + resultLanguage;

        if (log.isDebugEnabled()) log.debug("getDetailsInternal: {}({}) {} in {} (basicShow={}/basicEpisode={})",
                result.getTitle(), showId, episodeKey, resultLanguage, basicShow, basicEpisode);

        Map<String, EpisodeTags> allEpisodes = null;
        ShowTags showTags = null;
        ShowIdImagesResult searchImages = null;
        // set when the single-episode lookup below had to be remapped to TMDb's absolute
        // episode numbering (see comment there); used to restore the local season/episode
        // numbering on the returned EpisodeTags once metadata has been fetched
        boolean absoluteNumberingRemap = false;

        if (log.isDebugEnabled()) log.debug("getDetailsInternal: probing cache for showKey {}", showKey);
        allEpisodes = sEpisodeCache.get(seasonKey);
        if (log.isTraceEnabled()) debugLruCache(sEpisodeCache);

        if (allEpisodes == null) {
            if (log.isDebugEnabled()) log.debug("getDetailsInternal: allEpisodes is null, need to get show");

            // if we get allEpisodes it means we also have global show info and there is no need to redo it
            
            showTags = new ShowTags(); // to get the global show info
            allEpisodes = new HashMap<>(); // to get all episodes info

            int number_of_seasons = -1;

            // need to parse that show
            // start with global show information before retrieving all episodes
            // check if show metadata is already in database to avoid redundant API calls
            // however, for getAllEpisodes we still need fresh data in case new episodes/seasons were added

            //CHECK HERE, SHOW MAY HAVE TO BE KNOWN IT IS NOW IN CACHE AS WELL
            Boolean isShowKnown = isShowAlreadyKnown(showId, mContext);
            if (log.isDebugEnabled()) log.debug("getDetailsInternal: show known {}", isShowKnown);

            if (!isShowKnown) {
                String lang = resultLanguage;
                // for getAllEpisodes we need to get the number of seasons thus get it
                if (log.isDebugEnabled()) log.debug("getDetailsInternal: show {} not known or getAllEpisodes {}", showId, getAllEpisodes);

                // Check metadata cache first
                ShowMetadata cachedMetadata = sShowMetadataCache.get(showKey);
                ShowIdTvSearchResult showIdTvSearchResult = null;

                if (cachedMetadata == null) {
                    if (log.isDebugEnabled()) log.debug("getDetailsInternal: show metadata cache miss, fetching from API");
                    // query first tmdb
                    showIdTvSearchResult = ShowIdTvSearch.getTvShowResponse(showKey, showId, resultLanguage, adultScrape, getTmdb());

                    // parse result to get global show basic info
                    if (showIdTvSearchResult.status != ScrapeStatus.OKAY)
                        return new ScrapeDetailResult(new ShowTags(), true, null, showIdTvSearchResult.status, showIdTvSearchResult.reason);
                    else showTags = ShowIdParser.getResult(showIdTvSearchResult.tvShow, result.getYear(), mContext);
                    
                    if (log.isDebugEnabled()) log.debug("getDetailsInternal: downloaded showTags {} {}", showTags.getOnlineId(), showTags.getTitle());

                    // if there is no title or description research in en
                    if (showTags.getPlot() == null || showTags.getTitle() == null || showTags.getPlot().trim().length() == 0 || showTags.getTitle().trim().length() == 0) {
                        // use an "en" scoped key: showKey (built with resultLanguage) is already cached
                        // with the localized (empty) result, reusing it here would just return that same
                        // cached entry instead of querying tmdb in English
                        String fallbackShowKey = cleanShowName + "|" + showId + "|en";
                        ShowIdTvSearchResult enShowIdTvSearchResult = ShowIdTvSearch.getTvShowResponse(fallbackShowKey, showId, "en", adultScrape, getTmdb());
                        if (enShowIdTvSearchResult.status == ScrapeStatus.OKAY) {
                            ShowTags enShowTags = ShowIdParser.getResult(enShowIdTvSearchResult.tvShow, result.getYear(), mContext);
                            // merge only the missing fields, preserve the rest of the localized showTags (images, etc.)
                            if (showTags.getPlot() == null || showTags.getPlot().trim().length() == 0) showTags.setPlot(enShowTags.getPlot());
                            if (showTags.getTitle() == null || showTags.getTitle().trim().length() == 0) showTags.setTitle(enShowTags.getTitle());
                        } else {
                            if (log.isDebugEnabled()) log.debug("getDetailsInternal: en fallback for show {} failed with status {}", showId, enShowIdTvSearchResult.status);
                        }
                    }

                    // now we have the number of seasons if we need getAllEpisodes
                    number_of_seasons = showIdTvSearchResult.tvShow.number_of_seasons;
                    if (number_of_seasons < season) log.warn("getDetailsInternal: season ({}) > number_of_seasons ({})", season, number_of_seasons);
                    // no need to do this if show known
                    if (!isShowKnown) {
                        if (log.isDebugEnabled()) log.debug("getDetailsInternal: get all images for show {}", showId);

                        // get show posters and backdrops
                        searchImages = ShowIdImagesParser.getResult(showTags.getTitle(), showIdTvSearchResult.tvShow, lang, mContext);
                        if (!searchImages.backdrops.isEmpty())
                            showTags.setBackdrops(searchImages.backdrops);
                        else if (log.isDebugEnabled()) log.debug("getDetailsInternal: backdrops empty!");
                        // needs to be done after setBackdrops not to be erased
                        if (result.getBackdropPath() != null)  showTags.addDefaultBackdropTMDB(mContext, result.getBackdropPath());
                        if (!searchImages.posters.isEmpty())
                            showTags.setPosters(searchImages.posters);
                        else if (log.isDebugEnabled()) log.debug("getDetailsInternal: posters empty!");
                        // needs to be done after setPosters not to be erased
                        if (result.getPosterPath() != null) showTags.addDefaultPosterTMDB(mContext, result.getPosterPath());

                        // only downloads main backdrop/poster and not the entire collection (x8 in size)
                        showTags.downloadPoster(mContext);
                        showTags.downloadBackdrop(mContext);
                        // Skip downloading all posters/backdrops here to avoid performance bottleneck during auto-scrape.
                        // They will be lazy-loaded by the UI when displayed.
                        //showTags.downloadPosters(mContext);
                        //showTags.downloadBackdrops(mContext);
                    } else {
                        doRebuildShowTag = true;
                    }

                    // Cache the show metadata (including number_of_seasons) for future scrapes
                    sShowMetadataCache.put(showKey, new ShowMetadata(showTags, number_of_seasons));
                    if (log.isDebugEnabled()) log.debug("getDetailsInternal: cached show metadata for show {} with {} seasons", showId, number_of_seasons);
                } else {
                    if (log.isDebugEnabled()) log.debug("getDetailsInternal: show metadata cache hit for show {}", showId);
                    // Extract cached data
                    showTags = cachedMetadata.showTags;
                    number_of_seasons = cachedMetadata.numberOfSeasons;
                    if (log.isDebugEnabled()) log.debug("getDetailsInternal: using cached number_of_seasons={}", number_of_seasons);
                }
            } else {
                doRebuildShowTag = true;
            }

            //The show is known in the database, so we rebuild tags instead of TMDB scrape.
            if (doRebuildShowTag) {
                if (log.isDebugEnabled()) log.debug("getDetailsInternal: show {} is known: rebuild from tag", showId);
                // showTags exits we get it from db
                showTags = buildShowTagsOnlineId(mContext, showId);
                if (showTags == null) {
                    log.warn("getDetailsInternal: show {} not found in db, cannot rebuild tags", showId);
                    return new ScrapeDetailResult(null, true, null, ScrapeStatus.ERROR_PARSER, null);
                }
            }

            // retreive now the desired episodes
            List<TvEpisode> tvEpisodes = new ArrayList<>();
            Map<Integer, TvSeason> tvSeasons = new HashMap<Integer, TvSeason>();
            boolean fetchedFullSeason = false;

            if (getAllEpisodes) {
                //I WILL GET EACH EASON AS NEEDED, I ONLY HAVE SOME SEASONS OF SOME SHOWS
                ShowIdSeasonSearchResult showIdSeason = ShowIdSeasonSearch.getSeasonShowResponse(seasonKey, showId, requestedSeason, resultLanguage, adultScrape, tmdb);
                if (showIdSeason.status == ScrapeStatus.OKAY) {
                    tvEpisodes.addAll(showIdSeason.tvSeason.episodes);
                    if (! tvSeasons.containsKey(showIdSeason.tvSeason.season_number))
                        tvSeasons.put(showIdSeason.tvSeason.season_number, showIdSeason.tvSeason);
                    fetchedFullSeason = true;
                } else {
                    log.warn("getDetailsInternal: scrapeStatus for s" + requestedSeason + " is NOK!");
                    return new ScrapeDetailResult(new EpisodeTags(showTags, requestedSeason, requestedEpisode), true, null, showIdSeason.status, showIdSeason.reason);
                }
            
            } else {
                if (episode != -1) {
                    // get a single episode: should never get there since it means that we cannot infer poster/backdrop from single episode (need season)
                    if (log.isDebugEnabled()) log.debug("getDetailsInternal: get single episode for show {} s{}e{}", showId, season, episode);
                    ShowIdEpisodeSearchResult showIdEpisode = ShowIdEpisodeSearch.getEpisodeShowResponse(episodeKey, showId, season, episode, resultLanguage, adultScrape, getTmdb());
                    if (showIdEpisode.status == ScrapeStatus.OKAY)
                        tvEpisodes.add(showIdEpisode.tvEpisode);
                    else if (showIdEpisode.status == ScrapeStatus.NOT_FOUND) {
                        // some long-running shows (mostly anime split into arbitrary TMDb
                        // "seasons") do not restart episode_number at 1 for each season, so a
                        // direct season/episode lookup using the file's own per-season numbering
                        // can 404 even though the show/season exist. Detect this by fetching the
                        // full season and checking whether its first episode is numbered 1; if
                        // not, remap the requested episode to the equivalent absolute number.
                        if (log.isDebugEnabled()) log.debug("getDetailsInternal: s{}e{} not found, checking for absolute episode numbering", season, episode);
                        ShowIdSeasonSearchResult showIdSeason = ShowIdSeasonSearch.getSeasonShowResponse(seasonKey, showId, season, resultLanguage, adultScrape, getTmdb());
                        TvEpisode matched = null;
                        if (showIdSeason.status == ScrapeStatus.OKAY && showIdSeason.tvSeason != null
                                && showIdSeason.tvSeason.episodes != null && !showIdSeason.tvSeason.episodes.isEmpty()) {
                            TvEpisode firstEpisode = showIdSeason.tvSeason.episodes.get(0);
                            if (firstEpisode.episode_number != null && firstEpisode.episode_number != 1) {
                                int absoluteEpisode = firstEpisode.episode_number + episode - 1;
                                if (log.isDebugEnabled()) log.debug("getDetailsInternal: season {} uses absolute numbering starting at {}, remapping e{} -> e{}", season, firstEpisode.episode_number, episode, absoluteEpisode);
                                for (TvEpisode tvEpisode : showIdSeason.tvSeason.episodes) {
                                    if (tvEpisode.episode_number != null && tvEpisode.episode_number == absoluteEpisode) {
                                        matched = tvEpisode;
                                        break;
                                    }
                                }
                            }
                            if (matched != null) {
                                tvSeasons.putIfAbsent(showIdSeason.tvSeason.season_number, showIdSeason.tvSeason);
                                // the fetched episode carries TMDb's absolute episode_number: adjust
                                // episodeKey to match so the later allEpisodes.get(episodeKey) lookup
                                // in buildTag succeeds instead of falling through to an empty tag
                                episodeKey = showId + "|" + matched.season_number + "|" + matched.episode_number + "|" + resultLanguage;
                                absoluteNumberingRemap = true;
                            }
                        }
                        if (matched != null) {
                            log.info("getDetailsInternal: remapped absolute numbering s{}e{} -> e{} for show {}", season, episode, matched.episode_number, showId);
                            tvEpisodes.add(matched);
                        } else {
                            log.warn("getDetailsInternal: scrapeStatus for s{}e{} is NOK!", season, episode);
                            // save showtag even if episodetag is empty
                            EpisodeTags episodeTag = new EpisodeTags();
                            episodeTag.setShowTags(showTags);
                            // even if this is nok record season and episode not to end up with s00e00
                            episodeTag.setSeason(requestedSeason);
                            episodeTag.setEpisode(requestedEpisode);
                            return new ScrapeDetailResult(episodeTag, true, null, showIdEpisode.status, showIdEpisode.reason);
                        }
                    } else {
                        log.warn("getDetailsInternal: scrapeStatus for s{}e{} is NOK!", season, episode);
                        // save showtag even if episodetag is empty
                        EpisodeTags episodeTag = new EpisodeTags();
                        episodeTag.setShowTags(showTags);
                        // even if this is nok record season and episode not to end up with s00e00
                        episodeTag.setSeason(requestedSeason);
                        episodeTag.setEpisode(requestedEpisode);
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
                    if (log.isDebugEnabled()) log.debug("getDetailsInternal: get full season for show {} s{}", showId, season);
                    ShowIdSeasonSearchResult showIdSeason = ShowIdSeasonSearch.getSeasonShowResponse(seasonKey, showId, season, resultLanguage, adultScrape, getTmdb());
                    if (showIdSeason.status == ScrapeStatus.OKAY) {
                        tvEpisodes.addAll(showIdSeason.tvSeason.episodes);
                        tvSeasons.putIfAbsent(showIdSeason.tvSeason.season_number, showIdSeason.tvSeason);
                        fetchedFullSeason = true;
                    } else {
                        // save showtag even if episodetag is empty
                        EpisodeTags episodeTag = new EpisodeTags();
                        episodeTag.setShowTags(showTags);
                        // even if this is nok record season and episode not to end up with s00e00
                        episodeTag.setSeason(requestedSeason);
                        episodeTag.setEpisode(requestedEpisode);
                        log.warn("getDetailsInternal: scrapeStatus for season {} is NOK!", season);
                        return new ScrapeDetailResult(episodeTag, true, null, showIdSeason.status, showIdSeason.reason);
                    }
                }
            }

            // some long-running shows (mostly anime split into arbitrary TMDb "seasons") do not
            // restart episode_number at 1 for each season, so the file's own per-season episode
            // number can miss the actual season's episode list entirely. Detect this once the
            // full season is available (getAllEpisodes and season-only fetches above always
            // pull the whole season) and remap requestedEpisode to the equivalent absolute
            // TMDb episode number so the later allEpisodes.get(episodeKey) lookup succeeds.
            if (fetchedFullSeason && !absoluteNumberingRemap && !tvEpisodes.isEmpty()) {
                TvEpisode firstEpisode = tvEpisodes.get(0);
                if (firstEpisode.episode_number != null && firstEpisode.episode_number != 1) {
                    int absoluteEpisode = firstEpisode.episode_number + requestedEpisode - 1;
                    for (TvEpisode tvEpisode : tvEpisodes) {
                        if (tvEpisode.episode_number != null && tvEpisode.episode_number == absoluteEpisode) {
                            if (log.isDebugEnabled()) log.debug("getDetailsInternal: season {} uses absolute numbering starting at {}, remapping e{} -> e{}", requestedSeason, firstEpisode.episode_number, requestedEpisode, absoluteEpisode);
                            episodeKey = showId + "|" + tvEpisode.season_number + "|" + tvEpisode.episode_number + "|" + resultLanguage;
                            absoluteNumberingRemap = true;
                            break;
                        }
                    }
                }
            }

            // get now all episodes in tvEpisodes
            Map<String, EpisodeTags> searchEpisodes = ShowIdEpisodes.getEpisodes(seasonKey, showId, tvEpisodes, tvSeasons, showTags, resultLanguage, adultScrape, getTmdb(), mContext);
            if (!searchEpisodes.isEmpty()) {
                allEpisodes = searchEpisodes;
                // Cache only when the fetch path populated a full season map.
                // Manual single-episode searches still bypass the season cache.
                if (fetchedFullSeason) {
                    if (log.isDebugEnabled()) log.debug("getDetailsInternal: sEpisodeCache put allEpisodes with key {}", seasonKey);
                    sEpisodeCache.put(seasonKey, allEpisodes);
                } else {
                    if (log.isDebugEnabled()) log.debug("getDetailsInternal: single episode fetch, not caching under season key {}", seasonKey);
                }

                SparseArray<ScraperImage> seasonPosters = sSeasonPosterCache.get(showKey);
                if (seasonPosters == null && showTags != null) {
                    List<ScraperImage> postersFromDb = showTags.getAllPostersInDb(mContext);
                    if (postersFromDb != null && !postersFromDb.isEmpty()) {
                        seasonPosters = buildSeasonPosterMap(postersFromDb, resultLanguage);
                        sSeasonPosterCache.put(showKey, seasonPosters);
                        if (log.isDebugEnabled()) log.debug("getDetailsInternal: cached season posters for show {}", showId);
                    }
                } else {
                    if (log.isDebugEnabled()) log.debug("getDetailsInternal: using cached season posters for show {}", showId);
                }
                if (seasonPosters != null)
                    mapPostersToEpisodes(allEpisodes, seasonPosters);

                // Skip downloading all episode images here to avoid primary performance bottleneck during auto-scrape.
                // The specific episode being scraped is handled in buildTag() and the UI lazy-loads others on-demand.
                /*
                if (getAllEpisodes) {
                    downloadEpisodeImages(allEpisodes);
                }
                 */
            }
        } else {
            if (log.isDebugEnabled()) log.debug("getDetailsInternal: cache boost for showId (episodes)");
            // no need to parse, we have a cached result
            // get the showTags out of one random element, they all contain the same
            Iterator<EpisodeTags> iter = allEpisodes.values().iterator();
            if (iter.hasNext()) showTags = iter.next().getShowTags();

            // the cached season may use TMDb absolute episode numbering (see comment on
            // absoluteNumberingRemap above): a fresh fetch would have been remapped, but a
            // cache hit skips that logic, so redo the same detection against the cached map.
            // Derive minEpisode from the map's own keys (showId|season|episode|language, set
            // by ShowIdEpisodes.getEpisodes from TMDb's season_number/episode_number) rather
            // than from the cached EpisodeTags' own getSeason()/getEpisode(): those objects are
            // shared across requests and must never be relied upon for this, only their key.
            if (!allEpisodes.containsKey(episodeKey) && !allEpisodes.isEmpty()) {
                int minEpisode = Integer.MAX_VALUE;
                for (String key : allEpisodes.keySet()) {
                    String[] parts = key.split("\\|");
                    if (parts.length != 4) continue;
                    try {
                        if (Integer.parseInt(parts[1]) == keySeasonValue) {
                            int ep = Integer.parseInt(parts[2]);
                            if (ep < minEpisode) minEpisode = ep;
                        }
                    } catch (NumberFormatException ignored) {}
                }
                if (minEpisode != Integer.MAX_VALUE && minEpisode != 1) {
                    int absoluteEpisode = minEpisode + requestedEpisode - 1;
                    String remappedKey = showId + "|" + keySeasonValue + "|" + absoluteEpisode + "|" + resultLanguage;
                    if (allEpisodes.containsKey(remappedKey)) {
                        if (log.isDebugEnabled()) log.debug("getDetailsInternal: cached season {} uses absolute numbering starting at {}, remapping e{} -> e{}", keySeasonValue, minEpisode, requestedEpisode, absoluteEpisode);
                        episodeKey = remappedKey;
                        absoluteNumberingRemap = true;
                    }
                }
            }
        }
        if (showTags == null) { // if there is no info about the show there is nothing we can do
            if (log.isDebugEnabled()) log.debug("getDetailsInternal: ScrapeStatus.ERROR_PARSER");
            return new ScrapeDetailResult(null, false, null, ScrapeStatus.ERROR_PARSER, null);
        }
        String episodeTitleHint = null;
        if (result.getFile() != null) {
            String filenameForTitle = FileUtils.getFileNameWithoutExtension(result.getFile());
            episodeTitleHint = ShowUtils.extractEpisodeTitle(filenameForTitle, keySeasonValue, keyEpisodeValue);
        }
        EpisodeTags returnValue = buildTag(allEpisodes, episodeKey, requestedEpisode, requestedSeason, showTags, episodeTitleHint);
        if (absoluteNumberingRemap) {
            // buildTag() returns the very instance stored in allEpisodes (and hence in
            // sEpisodeCache): mutating it in place would permanently corrupt the shared cache
            // entry with this request's local season/episode, breaking later lookups/remaps
            // for other episodes of the same cached season. Clone before overriding so only
            // the value handed back to the caller reflects the local file's own per-season
            // numbering (matches how the rest of the season is organized on disk), even though
            // the fetched metadata came from TMDb's absolute episode_number.
            Parcel parcel = Parcel.obtain();
            try {
                returnValue.writeToParcel(parcel, 0);
                parcel.setDataPosition(0);
                returnValue = EpisodeTags.CREATOR.createFromParcel(parcel);
            } finally {
                parcel.recycle();
            }
            returnValue.setSeason(requestedSeason);
            returnValue.setEpisode(requestedEpisode);
        }
        if (log.isDebugEnabled()) log.debug("getDetailsInternal : ScrapeStatus.OKAY {} {} {}", returnValue.getShowTitle(), returnValue.getShowId(), returnValue.getTitle());
        Bundle extraOut = buildBundle(allEpisodes, options);
        return new ScrapeDetailResult(returnValue, false, extraOut, ScrapeStatus.OKAY, null);
    }

    private void downloadEpisodeImages(Map<String, EpisodeTags> allEpisodes) {
        for (EpisodeTags episode : allEpisodes.values()) {
            episode.downloadPicture(mContext);
            episode.downloadPoster(mContext);
        }
    }

    private static SparseArray<ScraperImage> buildSeasonPosterMap(List<ScraperImage> posters, String language) {
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
        return seasonPosters;
    }

    private static void mapPostersToEpisodes(Map<String, EpisodeTags> allEpisodes, SparseArray<ScraperImage> seasonPosters) {
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

    private EpisodeTags buildTag(Map<String, EpisodeTags> allEpisodes, String episodeKey, int epnum, int season, ShowTags showTags, String episodeTitleHint) {
        if (log.isDebugEnabled()) log.debug("buildTag allEpisodes.size={} epnum={}, season={}, showId={}", allEpisodes.size(), epnum, season, showTags.getId());
        EpisodeTags episodeTag = null;
        if (!allEpisodes.isEmpty()) {
            if (log.isDebugEnabled()) log.debug("buildTag: allEpisodes not empty trying to find {}", episodeKey);
            episodeTag = allEpisodes.get(episodeKey);
        }
        if (episodeTag == null && !allEpisodes.isEmpty()) {
            // requested season/episode number was not found in the already-fetched season:
            // try to recover it by matching the filename's episode title against the fetched
            // episodes instead, without any extra TMDB request
            episodeTag = fuzzyMatchEpisodeByTitle(episodeTitleHint, allEpisodes.values());
        }
        if (episodeTag == null) {
            if (log.isDebugEnabled()) log.debug("buildTag: shoot episode not in allEpisodes");
            episodeTag = new EpisodeTags();
            // assume episode / season of request
            episodeTag.setSeason(season);
            episodeTag.setEpisode(epnum);
            episodeTag.setShowTags(showTags);
            // also check if there is a poster
            List<ScraperImage> posters = showTags.getPosters();
            if (posters != null) {
                if (log.isDebugEnabled()) log.debug("buildTag: posters not null");
                for (ScraperImage image : posters) {
                    if (image.getSeason() == season) {
                        if (log.isDebugEnabled()) log.debug("buildTag: {} season poster s{} {}", showTags.getTitle(), season, image.getLargeUrl());
                        episodeTag.setPosters(image.asList());
                        episodeTag.downloadPoster(mContext);
                        break;
                    }
                }
            }
        } else {
            if (log.isDebugEnabled()) log.debug("buildTag: episodeTag not null");
            if (episodeTag.getPosters() == null) {
                log.warn("buildTag: {} has null posters!", episodeTag.getTitle());
            } else if (episodeTag.getPosters().isEmpty()) {
                log.warn("buildTag: {} has empty posters!", episodeTag.getTitle());
            }
            if (episodeTag.getDefaultPoster() == null) {
                log.warn("buildTag: {} has no defaultPoster! Should add default show one.", episodeTag.getTitle());
                // Add poster fallback for single-episode fetches: try season poster first, then show poster
                List<ScraperImage> posters = showTags.getPosters();
                ScraperImage seasonPoster = null;
                if (posters != null) {
                    for (ScraperImage image : posters) {
                        if (image.getSeason() == episodeTag.getSeason()) {
                            seasonPoster = image;
                            if (log.isDebugEnabled()) log.debug("buildTag: found season poster for s{}", episodeTag.getSeason());
                            break;
                        }
                    }
                }
                if (seasonPoster != null) {
                    episodeTag.addDefaultPoster(seasonPoster);
                } else if (showTags.getDefaultPoster() != null) {
                    if (log.isDebugEnabled()) log.debug("buildTag: using show poster as fallback");
                    episodeTag.addDefaultPoster(showTags.getDefaultPoster());
                }
            }
            if (episodeTag.getShowTags() == null) {
                log.warn("buildTag: {} has empty showTags!", episodeTag.getTitle());
            }
            // download still & poster because episode has been selected here
            episodeTag.downloadPicture(mContext);
            episodeTag.downloadPoster(mContext);
        }
        if (log.isDebugEnabled()) log.debug("buildTag: {} {} {}", episodeTag.getShowTitle(), episodeTag.getShowId(), episodeTag.getTitle());
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

    /**
     * Fuzzy-matches an extracted episode title against TMDB season 0 episodes.
     * Uses Levenshtein distance on lowercased titles with a threshold of 40% of the longer string length.
     *
     * @param extractedTitle the cleaned episode title from the filename
     * @param episodes the list of episodes from TMDB season 0
     * @return the matched episode number, or -1 if no match found
     */
    private static int fuzzyMatchEpisodeByTitle(String extractedTitle, List<TvEpisode> episodes) {
        if (extractedTitle == null || extractedTitle.isEmpty() || episodes == null || episodes.isEmpty()) {
            return -1;
        }

        LevenshteinDistance ld = new LevenshteinDistance();
        String normalizedTitle = extractedTitle.toLowerCase(java.util.Locale.ROOT).trim();
        int bestDistance = Integer.MAX_VALUE;
        int bestEpisodeNumber = -1;

        for (TvEpisode ep : episodes) {
            if (ep.name == null || ep.episode_number == null) continue;
            String epName = ep.name.toLowerCase(java.util.Locale.ROOT).trim();

            int distance = ld.apply(normalizedTitle, epName);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestEpisodeNumber = ep.episode_number;
            }
        }

        // threshold: allow up to 40% of the longer string length as edit distance
        if (bestEpisodeNumber >= 0) {
            int maxLen = Math.max(normalizedTitle.length(), 1);
            for (TvEpisode ep : episodes) {
                if (ep.episode_number != null && ep.episode_number == bestEpisodeNumber && ep.name != null) {
                    maxLen = Math.max(normalizedTitle.length(), ep.name.length());
                    break;
                }
            }
            int threshold = (int) Math.ceil(maxLen * 0.4);
            if (bestDistance <= threshold) {
                if (log.isDebugEnabled()) log.debug("fuzzyMatchEpisodeByTitle: matched '{}' to episode {} with distance {}/{}", extractedTitle, bestEpisodeNumber, bestDistance, threshold);
                return bestEpisodeNumber;
            } else {
                if (log.isDebugEnabled()) log.debug("fuzzyMatchEpisodeByTitle: best match for '{}' was episode {} but distance {} exceeds threshold {}", extractedTitle, bestEpisodeNumber, bestDistance, threshold);
            }
        }

        return -1;
    }

    /**
     * Fuzzy-matches an extracted episode title against an already-fetched season's episodes.
     * Used as a fallback when the requested season/episode number does not exist in the
     * fetched season data (e.g. local filename numbering diverges from TMDB's), so the
     * episode can still be recovered without any additional TMDB request.
     * Uses Levenshtein distance on lowercased titles with a threshold of 40% of the longer string length.
     *
     * @param extractedTitle the cleaned episode title from the filename
     * @param episodes the already-fetched episodes for the requested season
     * @return the matched EpisodeTags, or null if no confident match found
     */
    private static EpisodeTags fuzzyMatchEpisodeByTitle(String extractedTitle, java.util.Collection<EpisodeTags> episodes) {
        if (extractedTitle == null || extractedTitle.isEmpty() || episodes == null || episodes.isEmpty()) {
            return null;
        }

        LevenshteinDistance ld = new LevenshteinDistance();
        String normalizedTitle = extractedTitle.toLowerCase(java.util.Locale.ROOT).trim();
        int bestDistance = Integer.MAX_VALUE;
        EpisodeTags bestMatch = null;

        for (EpisodeTags ep : episodes) {
            if (ep.getTitle() == null) continue;
            String epName = ep.getTitle().toLowerCase(java.util.Locale.ROOT).trim();

            int distance = ld.apply(normalizedTitle, epName);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestMatch = ep;
            }
        }

        if (bestMatch != null) {
            int maxLen = Math.max(normalizedTitle.length(), bestMatch.getTitle().trim().length());
            int threshold = (int) Math.ceil(maxLen * 0.4);
            if (bestDistance <= threshold) {
                if (log.isDebugEnabled()) log.debug("fuzzyMatchEpisodeByTitle: matched '{}' to episode {} '{}' with distance {}/{}",
                        extractedTitle, bestMatch.getEpisode(), bestMatch.getTitle(), bestDistance, threshold);
                return bestMatch;
            } else {
                if (log.isDebugEnabled()) log.debug("fuzzyMatchEpisodeByTitle: best match for '{}' was episode {} '{}' but distance {} exceeds threshold {}",
                        extractedTitle, bestMatch.getEpisode(), bestMatch.getTitle(), bestDistance, threshold);
            }
        }

        return null;
    }

    public static boolean isShowAlreadyKnown(Integer showId, Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        String[] baseProjection = {ScraperStore.Show.ONLINE_ID};
        Cursor cursor = contentResolver.query(
                ContentUris.withAppendedId(ScraperStore.Show.URI.ONLINE_ID, showId),
                baseProjection, null, null, null);
        boolean isKnown = false;
        if (cursor != null) {
            try {
                isKnown = cursor.moveToFirst();
            } finally {
                cursor.close();
            }
        }
        if (log.isDebugEnabled()) log.debug("isShowAlreadyKnown: {} {}", showId, isKnown);
        return isKnown;
    }

    @Override
    protected String internalGetPreferenceName() {
        return PREFERENCE_NAME;
    }
}
