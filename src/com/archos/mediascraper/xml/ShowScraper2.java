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
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;
import android.util.Pair;
import android.util.SparseArray;

import com.archos.medialib.R;
import com.archos.mediascraper.EpisodeTags;
import com.archos.mediascraper.MediaScraper;
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
import com.uwetrottmann.thetvdb.TheTvdb;
import com.uwetrottmann.thetvdb.entities.Actor;
import com.uwetrottmann.thetvdb.entities.ActorsResponse;
import com.uwetrottmann.thetvdb.entities.Episode;
import com.uwetrottmann.thetvdb.entities.EpisodesResponse;
import com.uwetrottmann.thetvdb.entities.Series;
import com.uwetrottmann.thetvdb.entities.SeriesImageQueryResult;
import com.uwetrottmann.thetvdb.entities.SeriesImageQueryResultResponse;
import com.uwetrottmann.thetvdb.entities.SeriesResponse;
import com.uwetrottmann.thetvdb.entities.SeriesResultsResponse;
import org.apache.commons.text.similarity.LevenshteinDistance;
import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

public class ShowScraper2 extends BaseScraper2 {

    private final static String TAG = "ShowScraper2";
    private final static boolean DBG = false;
    private final static boolean DBG_RETROFIT = false;
    private final static boolean CACHE = true;
    private final static String PREFERENCE_NAME = "TheTVDB.com";
    // TODO: is there an influence in changing the size?
    private final static LruCache<String, Map<String, EpisodeTags>> sEpisodeCache = new LruCache<>(5);

    private final int SERIES_NOT_PERMITTED_ID = 313081;

    private final static LevenshteinDistance levenshteinDistance = new LevenshteinDistance();

    // Add caching for OkHttpClient so that queries for episodes from a same tvshow will get a boost in resolution
    static Cache cache;

    static MyTheTVdb theTvdb = null;

    public ShowScraper2(Context context) {
        super(context);
        // ensure cache is initialized
        synchronized (ShowScraper2.class) {
            cache = ScraperCache.getCache(context);
        }
    }

    static class MyTheTVdb extends TheTvdb {
        public MyTheTVdb(String apiKey) {
            super(apiKey);
        }
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
                builder.cache(cache).addNetworkInterceptor(new CacheInterceptor());
            }
        }
    }

    private List<SearchResult> processTheTvDbSearch(Response<SeriesResultsResponse> response, Bundle extra,
                                                    TvShowSearchInfo searchInfo, int maxItems, String language) {
        List<SearchResult> results = new LinkedList<>();
        List<SearchResult> resultsNumericSlug = new LinkedList<>();
        List<SearchResult> resultsNoBanner = new LinkedList<>();
        List<Pair<SearchResult,Integer>> resultsProbable = new LinkedList<>();
        List<SearchResult> resultsProbableSorted = new LinkedList<>();
        Boolean isDecisionTaken = false;
        for (Series series : response.body().data) {
            if (series.id != SERIES_NOT_PERMITTED_ID) {
                SearchResult result = new SearchResult();
                result.setId(series.id);
                result.setLanguage(language);
                result.setTitle(series.seriesName);
                result.setScraper(this);
                result.setFile(searchInfo.getFile());
                result.setExtra(extra);
                if (maxItems < 0 || results.size() < maxItems) {
                    // Put in lower priority any entry that has no TV show banned i.e. .*missing/movie.jpg as banner
                    isDecisionTaken = false;
                    if (series.banner != null) {
                        if (series.banner.endsWith("missing/series.jpg") || series.banner.endsWith("missing/movie.jpg")) {
                            if (DBG)
                                Log.d(TAG, "getMatches2: set aside " + series.seriesName + " because banner missing i.e. banner=" + series.banner);
                            resultsNoBanner.add(result);
                            isDecisionTaken = true;
                        }
                    } else if (series.image != null) {
                        if (series.image.endsWith("missing/series.jpg") || series.image.endsWith("missing/movie.jpg")) {
                            if (DBG)
                                Log.d(TAG, "getMatches2: set aside " + series.seriesName + " because image missing i.e. image=" + series.image);
                            resultsNoBanner.add(result);
                            isDecisionTaken = true;
                        }
                    }
                    if (! isDecisionTaken) {
                        if (DBG)
                            Log.d(TAG, "getMatches2: taking into account " + series.seriesName + " because banner/image exists");
                        if (series.slug.matches("^[0-9]+$")) {
                            // Put in lower priority any entry that has numeric slug
                            if (DBG)
                                Log.d(TAG, "getMatches2: set aside " + series.seriesName + " because slug is only numeric slug=" + series.slug);
                            isDecisionTaken = true;
                            resultsNumericSlug.add(result);
                        } else {
                            if (DBG)
                                Log.d(TAG, "getMatches2: take into account " + series.seriesName + " because slug is not only numeric slug=" + series.slug);
                            isDecisionTaken = true;
                            resultsProbable.add(new Pair<>(result,
                                    levenshteinDistance.apply(searchInfo.getShowName().toLowerCase(),
                                            result.getTitle().toLowerCase())));
                        }
                        if (! isDecisionTaken)
                            Log.w(TAG, "processTheTvDbSearch: ignore serie since banner/image is null for " + series.seriesName);
                    }
                }
            }
        }
        if (DBG) Log.d(TAG, "getMatches2: resultsProbable=" + resultsProbable.toString());
        Collections.sort(resultsProbable, new Comparator<Pair<SearchResult, Integer>>() {
            @Override
            public int compare(final Pair<SearchResult, Integer> sr1, final Pair<SearchResult, Integer> sr2) {
                if (sr1.second < sr2.second) {
                    return -1;
                } else if (sr1.second.equals(sr2.second)) {
                    return 0;
                } else {
                    return 1;
                }
            }
        });
        if (DBG) Log.d(TAG, "getMatches2: appplying Levenshtein distance resultsProbableSorted=" + resultsProbable.toString());
        if (resultsProbable.size()>0)
            for (Pair<SearchResult,Integer> pair : resultsProbable)
                resultsProbableSorted.add(pair.first);
        if (resultsProbableSorted.size()>0)
            results.addAll(resultsProbableSorted);
        if (resultsNumericSlug.size()>0)
            results.addAll(resultsNumericSlug);
        // skip shows without a banner/poster
        /*
        if (resultsNoBanner.size()>0)
            results.addAll(resultsNoBanner);
         */
        return results;
    }

    @Override
    public ScrapeSearchResult getMatches2(SearchInfo info, int maxItems) {
        // getMatches2 gets all shows matching but does not record the show banner proposed
        // check input
        if (info == null || !(info instanceof TvShowSearchInfo)) {
            Log.e(TAG, "getMatches2: bad search info: " + info == null ? "null" : "movie in show scraper");
            if (DBG) Log.d(TAG, "getMatches2: ScrapeSearchResult ScrapeStatus.ERROR");
            return new ScrapeSearchResult(null, false, ScrapeStatus.ERROR, null);
        }
        TvShowSearchInfo searchInfo = (TvShowSearchInfo) info;
        String language = getLanguage(mContext);

        if (DBG) Log.d(TAG, "getMatches2: tvshow search:" + searchInfo.getShowName()
                + " s:" + searchInfo.getSeason()
                + " e:" + searchInfo.getEpisode());

        List<SearchResult> results = new LinkedList<>();

        Bundle extra = new Bundle();
        extra.putString(ShowUtils.EPNUM, String.valueOf(searchInfo.getEpisode()));
        extra.putString(ShowUtils.SEASON, String.valueOf(searchInfo.getSeason()));

        if (theTvdb == null)
            theTvdb = new MyTheTVdb(mContext.getString(R.string.tvdb_api_2_key));
        try {
            // TODO: combine search in en + language sort by language and levenstein then treat by id to revert to language
            if (DBG) Log.d(TAG, "getMatches2: quering thetvdb for " + searchInfo.getShowName() + " in " + language);
            Response<SeriesResultsResponse> response = theTvdb.search()
                .series(searchInfo.getShowName(), null, null, null, language)
                .execute();
            if (response.isSuccessful() && response.body() != null) {
                results = processTheTvDbSearch(response, extra, searchInfo, maxItems, language);
            }
            else if (response.code() != 404) {
                if (DBG) Log.d(TAG, "ScrapeSearchResult ScrapeStatus.ERROR response not successful or body empty");
                return new ScrapeSearchResult(null, false, ScrapeStatus.ERROR, null);
            }

            // fallback in english if result is empty
            if (results.isEmpty() && !language.equals("en")) {
                if (DBG) Log.d(TAG, "getMatches2: result is empty for " + language + " thus quering thetvdb for " + searchInfo.getShowName() + " in en");
                Response<SeriesResultsResponse> globalResponse = theTvdb.search()
                    .series(searchInfo.getShowName(), null, null, null, "en")
                    .execute();
                if (globalResponse.isSuccessful() && globalResponse.body() != null)
                    results = processTheTvDbSearch(globalResponse, extra, searchInfo, maxItems, "en");
                else if (globalResponse.code() != 404) {
                    if (DBG) Log.d(TAG, "ScrapeSearchResult en ScrapeStatus.ERROR response not successful or body empty");
                    return new ScrapeSearchResult(null, false, ScrapeStatus.ERROR, null);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getMatches2", e);
            return new ScrapeSearchResult(null, false, ScrapeStatus.ERROR, null);
        }
        // TODO globalResponse.code() 404 is not found
        // TODO globalResponse.code() 401 is JWT token invalid --> check JWT lifecycle handling
        ScrapeStatus status = results.isEmpty() ? ScrapeStatus.NOT_FOUND : ScrapeStatus.OKAY;
        if (DBG)
            if (results.isEmpty())
                Log.d(TAG,"ScrapeSearchResult ScrapeStatus.NOT_FOUND");
            else
                Log.d(TAG,"ScrapeSearchResult ScrapeStatus.OKAY found " + results);
        return new ScrapeSearchResult(results, false, status, null);
    }

    private static ScraperSettings sSettings;

    @Override
    protected ScrapeDetailResult getDetailsInternal(SearchResult result, Bundle options) {
        if (DBG) Log.d(TAG, "getDetailsInternal: treating result show " + result.getTitle());
        boolean basicShow = options != null && options.containsKey(Scraper.ITEM_REQUEST_BASIC_SHOW);
        boolean basicEpisode = options != null && options.containsKey(Scraper.ITEM_REQUEST_BASIC_VIDEO);
        String resultLanguage = result.getLanguage();
        if (TextUtils.isEmpty(resultLanguage))
            resultLanguage = "en";
        int showId = result.getId();
        String showKey = showId + "|" + resultLanguage;

        Map<String, EpisodeTags> allEpisodes = null;
        ShowTags showTags = null;
        allEpisodes = sEpisodeCache.get(showKey);
        if (allEpisodes == null) {
            // need to parse that show
            Map<String, String> actors = new LinkedHashMap<>();
            List<ScraperImage> backdrops = new LinkedList<>();
            List<ScraperImage> posters = new ArrayList<>();
            allEpisodes = new HashMap<>();
            showTags = new ShowTags();

            if (theTvdb == null)
                theTvdb = new MyTheTVdb(mContext.getString(R.string.tvdb_api_2_key));
            try {
                // series
                if (!basicShow && !basicEpisode) {
                    Response<SeriesResponse> seriesResponse = theTvdb.series()
                        .series(showId, resultLanguage)
                        .execute();
                    if (seriesResponse.isSuccessful() && seriesResponse.body() != null) {
                        Series series = seriesResponse.body().data;
                        showTags.setPlot(series.overview);
                        showTags.setRating(series.siteRating.floatValue());
                        showTags.setTitle(series.seriesName);
                        showTags.setContentRating(series.rating);
                        showTags.setImdbId(series.imdbId);
                        showTags.setOnlineId(series.id);
                        showTags.setGenres(getLocalizedGenres(series.genre));
                        showTags.addStudioIfAbsent(series.network, '|', ',');
                        showTags.setPremiered(series.firstAired);
                        if (!basicShow && !basicEpisode && (series.overview == null || series.seriesName == null) && !resultLanguage.equals("en")) {
                            Response<SeriesResponse> globalSeriesResponse = theTvdb.series()
                                .series(showId, "en")
                                .execute();
                            if (globalSeriesResponse.isSuccessful() && globalSeriesResponse.body() != null) {
                                Series globalSeries = globalSeriesResponse.body().data;
                                if (series.overview == null)
                                    showTags.setPlot(globalSeries.overview);
                                if (series.seriesName == null)
                                    showTags.setTitle(globalSeries.seriesName);
                                else {
                                    if (DBG) Log.d(TAG,"ScrapeDetailResult globalSeries.seriesName is null for showId=" + showId + ", series.id=" + series.id + ", serie.imdbId=" + series.imdbId);
                                    if (showTags.getTitle() == null) showTags.setTitle("");
                                }
                            }
                            else {
                                if (DBG) Log.w(TAG,"ScrapeDetailResult serie en ScrapeStatus.ERROR_PARSER for showId=" + showId + ", series.id=" + series.id + ", serie.imdbId=" + series.imdbId);
                                if (showTags.getTitle() == null) showTags.setTitle("");
                                return new ScrapeDetailResult(null, false, null, ScrapeStatus.ERROR_PARSER, null);
                            }
                        }
                    }
                    else {
                        if (DBG) Log.w(TAG,"ScrapeDetailResult serie ScrapeStatus.ERROR_PARSER for showId=" + showId);
                        return new ScrapeDetailResult(null, false, null, ScrapeStatus.ERROR_PARSER, null);
                       }
                }
                // actors
                if (!basicShow && !basicEpisode) {
                    List<Actor> tempActors = new ArrayList<>();
                    Response<ActorsResponse> actorsResponse = theTvdb.series()
                        .actors(showId)
                        .execute();
                    if (actorsResponse.isSuccessful() && actorsResponse.body() != null) {
                        for(Actor actor : actorsResponse.body().data) {
                            tempActors.add(actor);
                        }
                    }
                    else {
                        if (DBG) Log.w(TAG,"ScrapeDetailResult actors ScrapeStatus.ERROR_PARSER");
                        // missing actor is not a deal breaker, continue but mark as a problem
                        //return new ScrapeDetailResult(null, false, null, ScrapeStatus.ERROR_PARSER, null);
                    }
                    Collections.sort(tempActors, new Comparator<Actor>() {
                        @Override
                        public int compare(Actor a1, Actor a2) {
                            return Integer.compare(a1.sortOrder, a2.sortOrder);
                        }
                    });
                    for(Actor actor : tempActors) {
                        actors.put(actor.name, actor.role);
                    }
                }

                final String BANNERS_URL = "https://www.thetvdb.com/banners/";

                // backdrops
                if (!basicShow && !basicEpisode) {
                    List<Pair<SeriesImageQueryResult, String>> tempBackdrops = new ArrayList<>();
                    Response<SeriesImageQueryResultResponse> fanartsResponse = theTvdb.series()
                        .imagesQuery(showId, "fanart", null, null, resultLanguage)
                        .execute();
                    if (fanartsResponse.isSuccessful() && fanartsResponse.body() != null) {
                        for(SeriesImageQueryResult fanart : fanartsResponse.body().data) {
                            tempBackdrops.add(Pair.create(fanart, resultLanguage));
                        }
                    }
                    else if (fanartsResponse.code() != 404) {
                        if (DBG) Log.w(TAG,"ScrapeDetailResult fanart ScrapeStatus.ERROR_PARSER");
                        return new ScrapeDetailResult(null, false, null, ScrapeStatus.ERROR_PARSER, null);
                    }
                    if (!resultLanguage.equals("en")) {
                        Response<SeriesImageQueryResultResponse> globalFanartsResponse = theTvdb.series()
                            .imagesQuery(showId, "fanart", null, null, "en")
                            .execute();
                        if (globalFanartsResponse.isSuccessful() && globalFanartsResponse.body() != null) {
                            for(SeriesImageQueryResult fanart : globalFanartsResponse.body().data) {
                                tempBackdrops.add(Pair.create(fanart, "en"));
                            }
                        }
                        else if (globalFanartsResponse.code() != 404) {
                            if (DBG) Log.w(TAG,"ScrapeDetailResult fanart en ScrapeStatus.ERROR_PARSER");
                            return new ScrapeDetailResult(null, false, null, ScrapeStatus.ERROR_PARSER, null);
                        }
                    }
                    Collections.sort(tempBackdrops, new Comparator<Pair<SeriesImageQueryResult, String>>() {
                        @Override
                        public int compare(Pair<SeriesImageQueryResult, String> b1, Pair<SeriesImageQueryResult, String> b2) {
                            return - Double.compare(b1.first.ratingsInfo.average, b2.first.ratingsInfo.average);
                        }
                    });
                    for(Pair<SeriesImageQueryResult, String> backdrop : tempBackdrops) {
                        if (DBG) Log.d(TAG,"ScrapeDetailResult: generating ScraperImage for backdrop for " + showTags.getTitle() + ", large=" + BANNERS_URL + backdrop.first.fileName + ", thumb=" + BANNERS_URL + backdrop.first.fileName);
                        ScraperImage image = new ScraperImage(ScraperImage.Type.SHOW_BACKDROP, showTags.getTitle());
                        image.setLanguage(backdrop.second);
                        image.setThumbUrl(BANNERS_URL + backdrop.first.thumbnail);
                        image.setLargeUrl(BANNERS_URL + backdrop.first.fileName);
                        image.generateFileNames(mContext);
                        backdrops.add(image);
                    }
                }

                // posters
                List<Pair<SeriesImageQueryResult, String>> tempPosters = new ArrayList<>();
                if (!basicEpisode) {
                    Response<SeriesImageQueryResultResponse> postersResponse = theTvdb.series()
                        .imagesQuery(showId, "poster", null, null, resultLanguage)
                        .execute();
                    if (postersResponse.isSuccessful() && postersResponse.body() != null) {
                        for(SeriesImageQueryResult poster : postersResponse.body().data) {
                            tempPosters.add(Pair.create(poster, resultLanguage));
                        }
                    }
                    else if (postersResponse.code() != 404) {
                        if (DBG) Log.w(TAG,"ScrapeDetailResult poster ScrapeStatus.ERROR_PARSER");
                        return new ScrapeDetailResult(null, false, null, ScrapeStatus.ERROR_PARSER, null);
                    }
                    if (!resultLanguage.equals("en")) {
                        Response<SeriesImageQueryResultResponse> globalPostersResponse = theTvdb.series()
                            .imagesQuery(showId, "poster", null, null, "en")
                            .execute();
                        if (globalPostersResponse.isSuccessful() && globalPostersResponse.body() != null) {
                            for(SeriesImageQueryResult poster : globalPostersResponse.body().data) {
                                tempPosters.add(Pair.create(poster, "en"));
                            }
                        }
                        else if (globalPostersResponse.code() != 404) {
                            if (DBG) Log.w(TAG,"ScrapeDetailResult poster en ScrapeStatus.ERROR_PARSER");
                            return new ScrapeDetailResult(null, false, null, ScrapeStatus.ERROR_PARSER, null);
                        }
                    }
                }
                if (!basicShow) {
                    Response<SeriesImageQueryResultResponse> seasonsResponse = theTvdb.series()
                        .imagesQuery(showId, "season", null, null, resultLanguage)
                        .execute();
                    if (seasonsResponse.isSuccessful() && seasonsResponse.body() != null) {
                        for(SeriesImageQueryResult season : seasonsResponse.body().data) {
                            tempPosters.add(Pair.create(season, resultLanguage));
                        }
                    }
                    else if (seasonsResponse.code() != 404) {
                        if (DBG) Log.w(TAG,"ScrapeDetailResult season ScrapeStatus.ERROR_PARSER");
                        return new ScrapeDetailResult(null, false, null, ScrapeStatus.ERROR_PARSER, null);
                    }
                    if (!resultLanguage.equals("en")) {
                        Response<SeriesImageQueryResultResponse> globalSeasonsResponse = theTvdb.series()
                            .imagesQuery(showId, "season", null, null, "en")
                            .execute();
                        if (globalSeasonsResponse.isSuccessful() && globalSeasonsResponse.body() != null) {
                            for(SeriesImageQueryResult season : globalSeasonsResponse.body().data) {
                                tempPosters.add(Pair.create(season, "en"));
                            }
                        }
                        else if (globalSeasonsResponse.code() != 404) {
                            if (DBG) Log.w(TAG,"ScrapeDetailResult season en ScrapeStatus.ERROR_PARSER");
                            return new ScrapeDetailResult(null, false, null, ScrapeStatus.ERROR_PARSER, null);
                        }
                    }
                }
                Collections.sort(tempPosters, new Comparator<Pair<SeriesImageQueryResult, String>>() {
                    @Override
                    public int compare(Pair<SeriesImageQueryResult, String> p1, Pair<SeriesImageQueryResult, String> p2) {
                        return - Double.compare(p1.first.keyType.equals("season") ? p1.first.ratingsInfo.average - 11 : p1.first.ratingsInfo.average, p2.first.keyType.equals("season") ? p2.first.ratingsInfo.average - 11 : p2.first.ratingsInfo.average);
                    }
                });
                for(Pair<SeriesImageQueryResult, String> poster : tempPosters) {
                    if (DBG) Log.d(TAG,"ScrapeDetailResult: generating ScraperImage for poster for " + showTags.getTitle() + ", large=" + BANNERS_URL + poster.first.fileName + ", thumb=" + BANNERS_URL + poster.first.fileName);
                    ScraperImage image = new ScraperImage(poster.first.keyType.equals("season") ? ScraperImage.Type.EPISODE_POSTER : ScraperImage.Type.SHOW_POSTER, showTags.getTitle());
                    image.setLanguage(poster.second);
                    image.setThumbUrl(BANNERS_URL + poster.first.thumbnail);
                    image.setLargeUrl(BANNERS_URL + poster.first.fileName);
                    image.generateFileNames(mContext);
                    try {
                        image.setSeason(poster.first.keyType.equals("season") ? Integer.parseInt(poster.first.subKey) : -1);
                    } catch (Throwable t) {
                        image.setSeason(-1);
                        Log.w(TAG, "getDetailsInternal: parseInt(" + poster.first.subKey + ") error on season for id=" + showId);
                    }
                    posters.add(image);
                }
                /*
                ScraperImage genericImage = null;
                if(!posters.isEmpty())
                    genericImage = posters.get(0);
                 */

                // episodes
                if (!basicShow && !basicEpisode) {
                    SparseArray<Episode> globalEpisodes = null;
                    Integer page = 1;
                    while (page != null) {
                        Response<EpisodesResponse> episodesResponse = theTvdb.series()
                            .episodes(showId, page, resultLanguage)
                            .execute();
                        if (episodesResponse.isSuccessful() && episodesResponse.body() != null) {
                            for(Episode episode : episodesResponse.body().data) {
                                EpisodeTags episodeTags = new EpisodeTags();
                                episodeTags.setActors(episode.guestStars);
                                episodeTags.setDirectors(episode.directors);
                                episodeTags.setPlot(episode.overview);
                                episodeTags.setRating(episode.siteRating.floatValue());
                                episodeTags.setTitle(episode.episodeName);
                                episodeTags.setImdbId(episode.imdbId);
                                episodeTags.setOnlineId(episode.id);
                                episodeTags.setAired(episode.firstAired);
                                episodeTags.setEpisode(episode.airedEpisodeNumber);
                                episodeTags.setSeason(episode.airedSeason);
                                episodeTags.setShowTags(showTags);
                                episodeTags.setEpisodePicture(episode.filename, mContext);
                                /*
                                if (genericImage != null)
                                    episodeTags.setPosters(genericImage.asList());
                                 */

                                if ((episode.overview == null || episode.episodeName == null) && !resultLanguage.equals("en")) {
                                    if (globalEpisodes == null) { // do it only once
                                        globalEpisodes = new SparseArray<>();
                                        Integer globalPage = 1;
                                        while (globalPage != null) {
                                            Response<EpisodesResponse> globalEpisodesResponse = theTvdb.series()
                                                .episodes(showId, globalPage, "en")
                                                .execute();
                                            if (globalEpisodesResponse.isSuccessful() && globalEpisodesResponse.body() != null) {
                                                for(Episode globalEpisode : globalEpisodesResponse.body().data) {
                                                    globalEpisodes.put(globalEpisode.id, globalEpisode);
                                                }
                                                globalPage = globalEpisodesResponse.body().links.next;
                                            }
                                            else {
                                                if (DBG) Log.w(TAG,"ScrapeDetailResult episode en ScrapeStatus.ERROR_PARSER");
                                                return new ScrapeDetailResult(null, false, null, ScrapeStatus.ERROR_PARSER, null);
                                            }
                                        }
                                    }
                                    Episode globalEpisode = globalEpisodes.get(episode.id);
                                    if (globalEpisode != null) {
                                        if (episode.overview == null)
                                            episodeTags.setPlot(globalEpisode.overview);
                                        if (episode.episodeName == null)
                                            episodeTags.setTitle(globalEpisode.episodeName);
                                    }
                                }
                                allEpisodes.put(episode.airedSeason + "|" + episode.airedEpisodeNumber, episodeTags);
                            }
                            page = episodesResponse.body().links.next;
                        }
                        else {
                            if (DBG) Log.w(TAG,"ScrapeDetailResult episode ScrapeStatus.ERROR_PARSER");
                            return new ScrapeDetailResult(null, false, null, ScrapeStatus.ERROR_PARSER, null);
                        }
                    }
                }
            }
            catch (Exception e) {
                Log.e(TAG, "getDetailsInternal", e);
                if (DBG) Log.w(TAG,"ScrapeDetailResult exception ScrapeStatus.ERROR_PARSER");
                return new ScrapeDetailResult(null, false, null, ScrapeStatus.ERROR_PARSER, null);
            }

            if (!allEpisodes.isEmpty()) {
                // put that result in cache.
                sEpisodeCache.put(showKey, allEpisodes);
            }

            // add backdrops & posters to show
            if (DBG) Log.d(TAG,"ScrapeDetailResult setting backdrops and posters to showTags");
            if (!backdrops.isEmpty())
                showTags.setBackdrops(backdrops);
            if (!posters.isEmpty()) {
                showTags.setPosters(posters);
            }
            // if we have episodes and posters map them to each other
            if (!allEpisodes.isEmpty() && !posters.isEmpty()) {

                // array to map season -> image
                SparseArray<ScraperImage> seasonPosters = new SparseArray<ScraperImage>();
                for (ScraperImage image : posters) {
                    int season = image.getSeason();

                    // season -1 is invalid, set the first only
                    if (season >= 0) {
                        if(seasonPosters.get(season) == null)
                            seasonPosters.put(season, image);
                        else if(resultLanguage.equals(image.getLanguage()) && !resultLanguage.equals(seasonPosters.get(season).getLanguage())){ //reset if right language
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
            if (!actors.isEmpty())
                showTags.addActorIfAbsent(actors);
        } else {
            if (DBG) Log.d(TAG, "using cached Episode List");
            // no need to parse, we have a cached result
            // get the showTags out of one random element, they all contain the
            // same
            Iterator<EpisodeTags> iter = allEpisodes.values().iterator();
            if (iter.hasNext())
                showTags = iter.next().getShowTags();
        }

        // if there is no info about the show there is nothing we can do
        if (showTags == null) {
            if (DBG) Log.w(TAG, "ScrapeDetailResult ScrapeStatus.ERROR_PARSER");
            return new ScrapeDetailResult(null, false, null, ScrapeStatus.ERROR_PARSER, null);
        }

        showTags.downloadPoster(mContext);

        EpisodeTags returnValue = null;
        Bundle extra = result.getExtra();
        int epnum = Integer.parseInt(extra.getString(ShowUtils.EPNUM, "0"));
        int season = Integer.parseInt(extra.getString(ShowUtils.SEASON, "0"));
        if (!allEpisodes.isEmpty()) {
            String key = season + "|" + epnum;
            returnValue = allEpisodes.get(key);
        }
        if (returnValue == null) {
            returnValue = new EpisodeTags();
            // assume episode / season of request
            returnValue.setSeason(season);
            returnValue.setEpisode(epnum);
            returnValue.setShowTags(showTags);
            // also check if there is a poster
            List<ScraperImage> posters = showTags.getPosters();
            if (posters != null) {
                for (ScraperImage image : posters) {
                    if (image.getSeason() == season) {
                        returnValue.setPosters(image.asList());
                        returnValue.downloadPoster(mContext);
                        break;
                    }
                }
            }
        } else {
            returnValue.downloadPicture(mContext);
            returnValue.downloadPoster(mContext);
        }
        Bundle extraOut = null;
        if (options != null && options.containsKey(Scraper.ITEM_REQUEST_ALL_EPISODES) && !allEpisodes.isEmpty()) {
            extraOut = new Bundle();
            for (Entry<String, EpisodeTags> item : allEpisodes.entrySet()) {
                extraOut.putParcelable(item.getKey(), item.getValue());
            }
        }
        if (DBG) Log.d(TAG, "ScrapeDetailResult ScrapeStatus.OKAY");
        return new ScrapeDetailResult(returnValue, false, extraOut, ScrapeStatus.OKAY, null);
    }

    private List<String> getLocalizedGenres(List<String> genres) {
        ArrayList<String> localizedGenres = new ArrayList<>();
    
        for (String genre : genres) {
            switch (genre) {
                case "Action":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_action));
                    break;
                case "Adventure":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_adventure));
                    break;
                case "Animation":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_animation));
                    break;
                case "Anime":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_anime));
                    break;
                case "Children":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_children));
                    break;
                case "Comedy":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_comedy));
                    break;
                case "Crime":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_crime));
                    break;
                case "Documentary":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_documentary));
                    break;
                case "Drama":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_drama));
                    break;
                case "Family":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_family));
                    break;
                case "Fantasy":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_fantasy));
                    break;
                case "Food":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_food));
                    break;
                case "Game Show":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_game_show));
                    break;
                case "Home and Garden":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_home_garden));
                    break;
                case "Horror":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_horror));
                    break;
                case "Mini-Series":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_mini_series));
                    break;
                case "News":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_news));
                    break;
                case "Reality":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_reality));
                    break;
                case "Romance":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_romance));
                    break;
                case "Science-Fiction":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_science_fiction));
                    break;
                case "Soap":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_soap));
                    break;
                case "Special Interest":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_special_interest));
                    break;
                case "Sport":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_sport));
                    break;
                case "Suspense":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_suspense));
                    break;
                case "Talk Show":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_talk_show));
                    break;
                case "Thriller":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_thriller));
                    break;
                case "Travel":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_travel));
                    break;
                case "Western":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_western));
                    break;
                default:
                    localizedGenres.add(genre);
            }
        }
    
        return localizedGenres;
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
            // <setting label="enable_fanart" type="bool" id="fanart" default="false"></setting>
            ScraperSetting setting = new ScraperSetting("fanart", ScraperSetting.STR_BOOL);
            setting.setDefault("false");
            setting.setLabel(labelList.get("enable_fanart"));
            sSettings.addSetting("fanart", setting);
    
            // <setting label="info_language" type="labelenum" id="language" values="aa|bb|cc|dd" sort="yes" default="en"></setting>
            setting = new ScraperSetting("language", ScraperSetting.STR_LABELENUM);
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
