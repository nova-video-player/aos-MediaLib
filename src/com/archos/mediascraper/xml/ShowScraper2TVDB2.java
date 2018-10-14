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
import android.util.SparseArray;

import com.archos.environment.ArchosUtils;
import com.archos.medialib.R;
import com.archos.mediascraper.EpisodeTags;
import com.archos.mediascraper.ScrapeDetailResult;
import com.archos.mediascraper.ScrapeSearchResult;
import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.Scraper;
import com.archos.mediascraper.ScraperImage;
import com.archos.mediascraper.SearchResult;
import com.archos.mediascraper.ShowTags;
import com.archos.mediascraper.ShowUtils;
import com.archos.mediascraper.StringMatcher;
import com.archos.mediascraper.preprocess.SearchInfo;
import com.archos.mediascraper.preprocess.TvShowSearchInfo;
import com.archos.mediascraper.saxhandler.ShowAllDetailsHandler;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import retrofit2.Response;

public class ShowScraper2TVDB2 extends BaseScraper2 {

    private final static String TAG = "ShowScraper2TVDB2";
    private final static boolean DBG = false;
    private final static String PREFERENCE_NAME = "TheTVDB.com";
    private final static LruCache<String, Map<String, EpisodeTags>> sEpisodeCache = new LruCache<String, Map<String,EpisodeTags>>(5);

    private final static String COVER_START = "https://www.thetvdb.com/banners/_cache/";
    private final static String COVER_THUMB_START = "https://www.thetvdb.com/banners/";
    private final static String BACKDROP_START = "https://www.thetvdb.com/banners/";
    private final static String BACKDROP_THUMB_START = "https://www.thetvdb.com/banners/_cache/";

    public ShowScraper2TVDB2(Context context) {
        super(context);
    }

    @Override
    public ScrapeSearchResult getMatches2(SearchInfo info, int maxItems) {
        // check input
        if (info == null || !(info instanceof TvShowSearchInfo)) {
            Log.e(TAG, "bad search info: " + info == null ? "null" : "movie in show scraper");
            return new ScrapeSearchResult(null, false, ScrapeStatus.ERROR, null);
        }
        TvShowSearchInfo searchInfo = (TvShowSearchInfo) info;
        String language = getLanguage(mContext);

        if (DBG) Log.d(TAG, "tvshow search:" + searchInfo.getShowName()
                + " s:" + searchInfo.getSeason()
                + " e:" + searchInfo.getEpisode());

        String searchUrl = generateSearchUrl(searchInfo.getShowName(), language);
        Bundle extra = new Bundle();
        extra.putString(ShowUtils.EPNUM, String.valueOf(searchInfo.getEpisode()));
        extra.putString(ShowUtils.SEASON, String.valueOf(searchInfo.getSeason()));
        TheTvdb theTvdb = new TheTvdb(mContext.getString(R.string.tvdb_api_key));
        List<SearchResult> results = new ArrayList<>();
        try {
            Response<SeriesResultsResponse> result = theTvdb.search().series(searchInfo.getShowName(), null, null, language).execute();
            if(result.isSuccessful()){
                for(Series series : result.body().data){
                    SearchResult searchResult = new SearchResult();
                    searchResult.setTitle(series.seriesName);
                    searchResult.setId(series.id);
                    searchResult.setLanguage(language);
                    searchResult.setExtra(extra);
                    searchResult.setFile(searchInfo.getFile());//TODO metafilereplace
                    searchResult.setScraper(this);
                    results.add(searchResult);

                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        ScrapeStatus status = results.isEmpty() ? ScrapeStatus.NOT_FOUND : ScrapeStatus.OKAY;
        return new ScrapeSearchResult(results, false, status, null);
    }

    private static String generateSearchUrl(String showName, String language) {
        String encode = ShowUtils.urlEncode(showName);
        String langencode = ShowUtils.urlEncode(language);
        String url = "https://www.thetvdb.com/api/GetSeries.php?seriesname=" + encode
                + "&language=" + langencode;
        return url;
    }

    private static String generateDetailsUrl(int showId, String language) {
        return "https://www.thetvdb.com/api/"+ ArchosUtils.getGlobalContext().getString(R.string.tvdb_api_key)+"/series/" + showId + "/all/"
                + language + ".zip";
    }

    private static ScraperSettings sSettings;

    @Override
    protected ScrapeDetailResult getDetailsInternal(SearchResult result, Bundle options) {
        String resultLanguage = result.getLanguage();
        if (TextUtils.isEmpty(resultLanguage))
            resultLanguage = ShowAllDetailsHandler.DEFAULT_LANGUAGE;
        int showId = result.getId();
        String detailsUrl = generateDetailsUrl(showId, resultLanguage);

        Map<String, EpisodeTags> allEpisodes = new HashMap<>();
        ShowTags showTags = null;
        allEpisodes = sEpisodeCache.get(detailsUrl);
        if (allEpisodes == null) {
            allEpisodes = new HashMap<>();
            // need to parse that show

            TheTvdb theTvdb = new TheTvdb(mContext.getString(R.string.tvdb_api_key));
            Map<String, String> actors = new HashMap<>();
            List<ScraperImage> posters = new ArrayList<>();
            List<ScraperImage> backdrops = new ArrayList<>();
            try {
                showTags = new ShowTags();
                Response<SeriesResponse> response = theTvdb.series().series(showId, resultLanguage+",en").execute();
                if(response.isSuccessful() && response.body().data!=null){
                    Series series = response.body().data;
                    showTags.setContentRating(series.rating);
                    showTags.setImdbId(series.imdbId);
                    showTags.setPremiered(series.airsTime);
                    showTags.addAllGenres(series.genre);
                    showTags.setOnlineId(series.id);
                    showTags.setImdbId(series.imdbId);
                    showTags.addStudioIfAbsent(series.network,'|',',');
                    showTags.setPlot(series.overview);
                    showTags.setRating(series.siteRating.floatValue());
                    showTags.setTitle(series.seriesName);

                } else return error();

                Response<SeriesImageQueryResultResponse> res = theTvdb.series().imagesQuery(showId,"poster",null,null, "en").execute();
                if(res.isSuccessful()) {
                    if(res.body().data!=null)

                        for (SeriesImageQueryResult imageQueryResult : res.body().data) {
                            ScraperImage image = new ScraperImage(ScraperImage.Type.SHOW_POSTER, imageQueryResult.fileName);
                            image.setLargeUrl(COVER_THUMB_START + imageQueryResult.fileName);
                            image.setThumbUrl(COVER_START + imageQueryResult.thumbnail);
                            image.generateFileNames(mContext);
                            posters.add(image);

                        }
                } else return error();
                res = theTvdb.series().imagesQuery(showId,"fanart",null,null, "en").execute();
                if(res.isSuccessful()) {
                    if(res.body().data!=null)
                        for (SeriesImageQueryResult imageQueryResult : res.body().data) {
                            ScraperImage image = new ScraperImage(ScraperImage.Type.SHOW_BACKDROP,imageQueryResult.fileName);
                            image.setLargeUrl(BACKDROP_START + imageQueryResult.fileName);
                            image.setThumbUrl(BACKDROP_THUMB_START + imageQueryResult.thumbnail);
                            image.generateFileNames(mContext);
                            backdrops.add(image);

                        }
                } else return error();
                res = theTvdb.series().imagesQuery(showId,"season",null,null, "en").execute();
                if(res.isSuccessful()) {
                    if(res.body().data!=null)
                        for (SeriesImageQueryResult imageQueryResult : res.body().data) {
                            ScraperImage image = new ScraperImage(ScraperImage.Type.EPISODE_POSTER, imageQueryResult.fileName);
                            image.setLargeUrl(COVER_THUMB_START + imageQueryResult.fileName);
                            image.setThumbUrl(COVER_START + imageQueryResult.fileName);
                            image.generateFileNames(mContext);
                            image.setSeason(Integer.parseInt(imageQueryResult.subKey));
                            posters.add(image);
                        }
                } else return error();


                Response<ActorsResponse> actorRes = theTvdb.series().actors((int) showTags.getOnlineId()).execute();
                if(actorRes.isSuccessful()) {
                    if(actorRes.body().data!=null)
                        for (Actor actor : actorRes.body().data){
                            actors.put(actor.name, actor.role);
                        }

                } else return error();


                Response<EpisodesResponse> resEpisodes = theTvdb.series().episodes((int) showTags.getOnlineId(), 0, resultLanguage+",en").execute();
                List<Episode> episodes = new ArrayList<>();
                int lastPage = 0;
                if(resEpisodes.isSuccessful()) {
                    while(resEpisodes.isSuccessful() && resEpisodes.body().data !=null){
                        episodes.addAll(resEpisodes.body().data);
                        lastPage ++;
                        resEpisodes = theTvdb.series().episodes((int) showTags.getOnlineId(), lastPage, resultLanguage+",en").execute();
                    }


                    for(Episode episode : episodes){
                        EpisodeTags episodeTags = new EpisodeTags();
                        episodeTags.setOnlineId(episode.id);
                        episodeTags.setTitle(episode.episodeName);
                        episodeTags.setAired(episode.firstAired);
                        episodeTags.setEpisode(episode.airedEpisodeNumber);
                        //missing imdb
                        //missing mCurrentData.addDirectorIfAbsent(getString(), '|', ',');
                        //mCurrentData.setRating(getFloat());
                        //mCurrentData.setEpisodePicture(getString(), mContext);
                        //mCurrentData.addActorIfAbsent(getString(), '|', ',');
                        episodeTags.setPlot(episode.overview);
                        episodeTags.setSeason(episode.airedSeason);
                        episodeTags.setShowTags(showTags);
                        allEpisodes.put(ShowAllDetailsHandler.getKey(episode.airedSeason, episode.airedEpisodeNumber),episodeTags);
                    }
                } else return error();




            } catch (IOException e) {
                e.printStackTrace();
                return error();
            }

            if (allEpisodes != null) {
                // put that result in cache.
                sEpisodeCache.put(detailsUrl, allEpisodes);
            }

            // add backdrops & posters to show
            if (!backdrops.isEmpty())
                showTags.setBackdrops(backdrops);
            if (!posters.isEmpty()) {
                showTags.setPosters(posters);
            }
            // if we have episodes and posters map them to each other
            if (allEpisodes != null && !allEpisodes.isEmpty() &&
                    !posters.isEmpty()) {

                // array to map season -> image
                SparseArray<ScraperImage> seasonPosters = new SparseArray<ScraperImage>();
                for (ScraperImage image : posters) {
                    int season = image.getSeason();
                    // season -1 is invalid, set the first only
                    if (season >= 0) {
                        if(seasonPosters.get(season) == null)
                            seasonPosters.put(season, image);
                        else if(resultLanguage.equals(image.getLanguage())){ //reset if right language
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
        if (showTags == null)
            return new ScrapeDetailResult(null, false, null, ScrapeStatus.ERROR_PARSER, null);

        showTags.downloadPoster(mContext);

        EpisodeTags returnValue = null;
        Bundle extra = result.getExtra();
        int epnum = Integer.parseInt(extra.getString(ShowUtils.EPNUM, "0"));
        int season = Integer.parseInt(extra.getString(ShowUtils.SEASON, "0"));
        if (allEpisodes != null) {
            String key = ShowAllDetailsHandler.getKey(season, epnum);
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
        if (options != null && options.containsKey(Scraper.ITEM_REQUEST_ALL_EPISODES)
                && allEpisodes != null && !allEpisodes.isEmpty()) {
            extraOut = new Bundle();
            for (Entry<String, EpisodeTags> item : allEpisodes.entrySet()) {
                extraOut.putParcelable(item.getKey(), item.getValue());
            }
        }
        return new ScrapeDetailResult(returnValue, false, extraOut, ScrapeStatus.OKAY, null);
    }

    private ScrapeDetailResult error(){
        return new ScrapeDetailResult(null, false, null, ScrapeStatus.ERROR_NETWORK, null);
    }

    // in a subclass so it's lazy loaded if required
    private static class LanguageHolder {
        static final StringMatcher LANGUAGE_IDS = new StringMatcher();
        static {
            LANGUAGE_IDS.addKey("en", 7);
            LANGUAGE_IDS.addKey("sv", 8);
            LANGUAGE_IDS.addKey("no", 9);
            LANGUAGE_IDS.addKey("da", 10);
            LANGUAGE_IDS.addKey("fi", 11);
            LANGUAGE_IDS.addKey("nl", 13);
            LANGUAGE_IDS.addKey("de", 14);
            LANGUAGE_IDS.addKey("it", 15);
            LANGUAGE_IDS.addKey("es", 16);
            LANGUAGE_IDS.addKey("fr", 17);
            LANGUAGE_IDS.addKey("pl", 18);
            LANGUAGE_IDS.addKey("hu", 19);
            LANGUAGE_IDS.addKey("el", 20);
            LANGUAGE_IDS.addKey("tr", 21);
            LANGUAGE_IDS.addKey("ru", 22);
            LANGUAGE_IDS.addKey("he", 24);
            LANGUAGE_IDS.addKey("ja", 25);
            LANGUAGE_IDS.addKey("pt", 26);
            LANGUAGE_IDS.addKey("zh", 27);
            LANGUAGE_IDS.addKey("cs", 28);
            LANGUAGE_IDS.addKey("sl", 30);
            LANGUAGE_IDS.addKey("hr", 31);
            LANGUAGE_IDS.addKey("ko", 32);
        }
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
