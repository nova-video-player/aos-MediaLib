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
import android.util.Log;

import com.archos.filecorelibrary.MetaFile;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.ScrapeDetailResult;
import com.archos.mediascraper.ScrapeSearchResult;
import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.SearchResult;
import com.archos.mediascraper.preprocess.SearchInfo;
import com.archos.mediascraper.preprocess.SearchPreprocessor;
import com.archos.mediascraper.settings.ScraperSettings;

import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public abstract class BaseScraper2 {
    private static final String TAG = "BaseScraper2";

    protected final static String LANGUAGES = "da|fi|nl|de|it|es|fr|pl|hu|el|tr|ru|he|ja|pt|zh|cs|sl|hr|ko|en|sv|no";

    protected final SAXParser mParser;

    protected final Context mContext;

    private final String mName;

    /**
     * constructor for child classes only
     */
    protected BaseScraper2(Context context) {
        SAXParserFactory parserFactory = SAXParserFactory.newInstance();
        try {
            mParser = parserFactory.newSAXParser();
        } catch (ParserConfigurationException e) {
            Log.d(TAG, "Exception: " + e, e);
            throw new RuntimeException(e);
        } catch (SAXException e) {
            Log.d(TAG, "Exception: " + e, e);
            throw new RuntimeException(e);
        }
        mName = internalGetPreferenceName();
        mContext = context;
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
                return MovieScraper2.generatePreferences(context);
            case BaseTags.TV_SHOW:
                return ShowScraper2.generatePreferences(context);
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
            Log.e(TAG, "no SearchInfo given");
            return new ScrapeDetailResult(null, true, null, ScrapeStatus.ERROR, null);
        }
        ScrapeDetailResult result = null;
        ScrapeSearchResult searchResult = getMatches2(info, 1);
        if (searchResult.isOkay()) {
            result = getDetails(searchResult.results.get(0), null);
        } else {
            result = new ScrapeDetailResult(null, searchResult.isMovie, null, searchResult.status, searchResult.reason);
        }
        return result;
    }

    public static final BaseScraper2 getScraper(int type, Context context) {
        switch (type) {
            case BaseTags.MOVIE:
                return new MovieScraper2(context);
            case BaseTags.TV_SHOW:
                return new ShowScraper2(context);
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
