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

import androidx.preference.PreferenceManager;

import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.ScrapeDetailResult;
import com.archos.mediascraper.ScrapeSearchResult;
import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.Scraper;
import com.archos.mediascraper.SearchResult;
import com.archos.mediascraper.preprocess.SearchInfo;
import com.archos.mediascraper.preprocess.SearchPreprocessor;
import com.archos.mediascraper.preprocess.TvShowSearchInfo;
import com.archos.mediascraper.settings.ScraperSettings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public abstract class BaseScraper2 {
    private static final Logger log = LoggerFactory.getLogger(BaseScraper2.class);

    public final static String LANGUAGES = "da|fi|nl|de|it|es|fr|pl|hu|el|tr|ru|he|ja|pt|zh|cs|sl|hr|ko|en|sv|no|vi|lt";

    protected final SAXParser mParser;

    protected final Context mContext;

    private final String mName;

    boolean adultScrape = false;

    /**
     * constructor for child classes only
     */
    protected BaseScraper2(Context context) {
        SAXParserFactory parserFactory = SAXParserFactory.newInstance();
        try {
            mParser = parserFactory.newSAXParser();
        } catch (ParserConfigurationException e) {
            log.debug("Exception: " + e, e);
            throw new RuntimeException(e);
        } catch (SAXException e) {
            log.debug("Exception: " + e, e);
            throw new RuntimeException(e);
        }
        mName = internalGetPreferenceName();
        mContext = context;
        adultScrape = PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("enable_adult_scrap_key", false);
    }

    public final String getName() {
        return mName;
    }

    /**
     * Used for the SharedPreferences for this Scraper
     * @return the name this Scraper has
     */
    protected abstract String internalGetPreferenceName();

    public final static ScraperSettings getSettings(int scraperType, Context context) {
        switch (scraperType) {
            case BaseTags.MOVIE:
                return MovieScraper3.generatePreferences(context);
            case BaseTags.TV_SHOW:
                return ShowScraper4.generatePreferences(context);
            default:
                return null;
        }
    }

    /**
     * Request the detail for the first matching entry directly
     * @param info preprocessed searchInfo from {@link SearchPreprocessor#parseFileBased(MetaFile)}
     * @return The result
     * @throws IOException if something went wrong
     */
    public final ScrapeDetailResult search(SearchInfo info) {
        if (info == null) {
            log.error("no SearchInfo given");
            return new ScrapeDetailResult(null, true, null, ScrapeStatus.ERROR, null);
        }
        ScrapeDetailResult result = null;
        ScrapeSearchResult searchResult = getMatches2(info, 1);
        if (searchResult.isOkay()) {
            if (info.isTvShow()) {
                log.debug("search: tv show");
                // here info is a TvSearchInfo
                Bundle bundle = new Bundle();
                TvShowSearchInfo tvSearchInfo = (TvShowSearchInfo) info;
                bundle.putInt(Scraper.ITEM_REQUEST_SEASON, tvSearchInfo.getSeason());
                // keeping whole season boosts the perf since there is only one request for tmdb
                //bundle.putInt(Scraper.ITEM_REQUEST_EPISODE, tvSearchInfo.getEpisode());
                result = getDetails(searchResult.results.get(0), bundle);
            } else {
                log.debug("search: not tv show");
                result = getDetails(searchResult.results.get(0), null);
            }
        } else {
            result = new ScrapeDetailResult(null, searchResult.isMovie, null, searchResult.status, searchResult.reason);
        }
        return result;
    }

    public static final BaseScraper2 getScraper(int type, Context context) {
        switch (type) {
            case BaseTags.MOVIE:
                return new MovieScraper3(context);
            case BaseTags.TV_SHOW:
                return new ShowScraper4(context);
            default:
                return null;
        }
    }

    /**
     * Request Scraper to search it's online database
     * @param info Object that holds all search relevant information,
     *             get it from {@link SearchPreprocessor#parseFileBased(MetaFile)}
     * @param maxItems Limit for number of items requested
     * @return The Result
     */
    public abstract ScrapeSearchResult getMatches2(SearchInfo info, int maxItems);

    /**
     * Request details for a SearchResult
     *
     * @return The Result
     * @throws IOException if something went wrong
     */
    public final static ScrapeDetailResult getDetails(SearchResult result, Bundle options) {
        ScrapeDetailResult ret;
        if (result == null || result.getFile() == null) {
            ret = new ScrapeDetailResult(null, true, null, ScrapeStatus.ERROR, null);
        } else {
            ret = result.getScraper().getDetailsInternal(result, options);
        }
        return ret;
    }

    public String getLanguages() {
        return LANGUAGES;
    }

    protected abstract ScrapeDetailResult getDetailsInternal(SearchResult result, Bundle options);

    // (whitespace)"(anything)"(whitespace)
    private static final Pattern VERBATIM = Pattern.compile("^\\s*\"(.*)\"\\s*$");
    public static String getVerbatimOrNull(String search) {
        // no need to check null / empty
        if (search == null || search.isEmpty())
            return null;

        Matcher m = VERBATIM.matcher(search);
        if (m.matches()) {
            return m.group(1);
        }

        return null;
    }
}
