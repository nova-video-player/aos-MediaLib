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
import com.archos.mediascraper.HttpCache;
import com.archos.mediascraper.MediaScraper;
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
import com.archos.mediascraper.saxhandler.ShowActorsHandler;
import com.archos.mediascraper.saxhandler.ShowAllDetailsHandler;
import com.archos.mediascraper.saxhandler.ShowBannersHandler;
import com.archos.mediascraper.saxhandler.ShowSearchHandler;
import com.archos.mediascraper.settings.ScraperSetting;
import com.archos.mediascraper.settings.ScraperSettings;

import org.xml.sax.SAXException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ShowScraper2 extends BaseScraper2 {

    private final static String TAG = "ShowScraper2";
    private final static boolean DBG = false;
    private final static String PREFERENCE_NAME = "TheTVDB.com";
    private final static LruCache<String, Map<String, EpisodeTags>> sEpisodeCache = new LruCache<String, Map<String,EpisodeTags>>(5);
    private final ShowAllDetailsHandler mDetailsHandler ;
    private final ShowActorsHandler mActorsHandler = new ShowActorsHandler();
    private final ShowSearchHandler mSearchHandler = new ShowSearchHandler(1);
    private final ShowBannersHandler mBannersHandler;

    public ShowScraper2(Context context) {
        super(context);
        mBannersHandler = new ShowBannersHandler(context);
        mDetailsHandler = new ShowAllDetailsHandler(context);
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

        String searchUrl = generateSearchUrl(searchInfo.getShowName(), language);

        File searchResultFile = HttpCache.getInstance(MediaScraper.getXmlCacheDirectory(mContext),
                MediaScraper.XML_CACHE_TIMEOUT, MediaScraper.CACHE_FALLBACK_DIRECTORY,
                MediaScraper.CACHE_OVERWRITE_DIRECTORY).getFile(searchUrl, true);
        if (searchResultFile == null)
            return new ScrapeSearchResult(null, false, ScrapeStatus.ERROR_NETWORK, null);

        mSearchHandler.setLimit(maxItems);
        try {
            mParser.parse(searchResultFile, mSearchHandler);
        } catch (SAXException e) {
            return new ScrapeSearchResult(null, false, ScrapeStatus.ERROR_PARSER, e);
        } catch (IOException e) {
            return new ScrapeSearchResult(null, false, ScrapeStatus.ERROR_PARSER, e);
        }
        List<SearchResult> results = mSearchHandler.getResult();
        Bundle extra = new Bundle();
        extra.putString(ShowUtils.EPNUM, String.valueOf(searchInfo.getEpisode()));
        extra.putString(ShowUtils.SEASON, String.valueOf(searchInfo.getSeason()));
        Iterator<SearchResult> i = results.iterator();
        while (i.hasNext()) {
            SearchResult searchResult = i.next();
            if (searchResult.getId() != 313081) { // ** 403: Series Not Permitted **
                searchResult.setExtra(extra);
                searchResult.setFile(searchInfo.getFile());//TODO metafilereplace
                searchResult.setScraper(this);
            }
            else {
                i.remove();
            }
        }
        ScrapeStatus status = results.isEmpty() ? ScrapeStatus.NOT_FOUND : ScrapeStatus.OKAY;
        if (DBG)
            if (results.isEmpty())
                Log.d(TAG,"ScrapeSearchResult ScrapeStatus.NOT_FOUND");
            else
                Log.d(TAG,"ScrapeSearchResult ScrapeStatus.OKAY found " + results);
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

        Map<String, EpisodeTags> allEpisodes = null;
        ShowTags showTags = null;
        allEpisodes = sEpisodeCache.get(detailsUrl);
        if (allEpisodes == null) {
            // need to parse that show
            String searchUrl = generateDetailsUrl(showId, resultLanguage);
            File searchResultFile = HttpCache.getInstance(MediaScraper.getXmlCacheDirectory(mContext),
                    MediaScraper.XML_CACHE_TIMEOUT, MediaScraper.CACHE_FALLBACK_DIRECTORY,
                    MediaScraper.CACHE_OVERWRITE_DIRECTORY).getFile(searchUrl, true);
            if (searchResultFile == null) {
                return new ScrapeDetailResult(null, false, null, ScrapeStatus.ERROR_NETWORK, null);
            }
            Map<String, String> actors = null;
            ShowBannersHandler.CoverResult banners = null;

            ZipFile zipFile = null;
            try {
                zipFile = new ZipFile(searchResultFile);
                ZipEntry zipEntry = zipFile.getEntry(resultLanguage + ".xml");
                InputStream inputStream = new BufferedInputStream(zipFile.getInputStream(zipEntry),
                        8 * 1024);
                try {
                    mParser.parse(inputStream, mDetailsHandler);
                } catch (SAXException e) {
                    zipFile.close();
                    if (DBG) Log.w(TAG,"ScrapeDetailResult serie ScrapeStatus.ERROR_PARSER for showId=" + showId);
                    return new ScrapeDetailResult(null, false, null, ScrapeStatus.ERROR_PARSER, e);
                }
                showTags = mDetailsHandler.getShowTags();
                // if there is no info about the show there is nothing we can do
                if (showTags == null) {
                    zipFile.close();
                    if (DBG) Log.w(TAG,"ScrapeDetailResult showTags ScrapeStatus.ERROR_PARSER for showId=" + showId);
                    return new ScrapeDetailResult(null, false, null, ScrapeStatus.ERROR_PARSER, null);
                }
                showTags.setGenres(getLocalizedGenres(showTags.getGenres()));

                zipEntry = zipFile.getEntry("actors.xml");
                inputStream = new BufferedInputStream(zipFile.getInputStream(zipEntry),
                        8 * 1024);
                try {
                    mParser.parse(inputStream, mActorsHandler);
                } catch (SAXException e) {
                    zipFile.close();
                    if (DBG) Log.w(TAG,"ScrapeDetailResult actors ScrapeStatus.ERROR_PARSER for showId=" + showId);
                    return new ScrapeDetailResult(null, false, null, ScrapeStatus.ERROR_PARSER, e);
                }
                actors = mActorsHandler.getResult();

                zipEntry = zipFile.getEntry("banners.xml");
                inputStream = new BufferedInputStream(zipFile.getInputStream(zipEntry),
                        8 * 1024);
                mBannersHandler.setNameSeed(showTags.getTitle());
                try {
                    mParser.parse(inputStream, mBannersHandler);
                } catch (SAXException e) {
                    zipFile.close();
                    if (DBG) Log.w(TAG,"ScrapeDetailResult banners ScrapeStatus.ERROR_PARSER for showId=" + showId);
                    return new ScrapeDetailResult(null, false, null, ScrapeStatus.ERROR_PARSER, e);
                }
                banners = mBannersHandler.getResult();
                zipFile.close();
            } catch (IOException e) {
                if (zipFile != null)
                    try {
                        zipFile.close();
                    } catch (IOException ignored) {}
                return new ScrapeDetailResult(null, false, null, ScrapeStatus.ERROR_PARSER, e);
            }
            allEpisodes = mDetailsHandler.getResult();
            if (allEpisodes != null) {
                // put that result in cache.
                sEpisodeCache.put(detailsUrl, allEpisodes);
            }

            // add backdrops & posters to show
            if (!banners.backdrops.isEmpty())
                showTags.setBackdrops(banners.backdrops);
            if (!banners.posters.isEmpty()) {
                showTags.setPosters(banners.posters);
            }
            // if we have episodes and posters map them to each other
            if (allEpisodes != null && !allEpisodes.isEmpty() &&
                    !banners.posters.isEmpty()) {

                // array to map season -> image
                SparseArray<ScraperImage> seasonPosters = new SparseArray<ScraperImage>();
                for (ScraperImage image : banners.posters) {
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
