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


package com.archos.mediascraper.saxhandler;

import org.xml.sax.Attributes;

import android.util.Log;

import com.archos.mediascraper.SearchResult;

import java.util.LinkedList;
import java.util.List;

/**
 * Sax Handler for results from
 * http://www.thetvdb.com/api/GetSeries.php?seriesname=$NAME$&language=$LANGUAGE$
 */
public class ShowSearchHandler extends BasicHandler {
    private final static String TAG = "ShowSearchHandler";
    private final static boolean DBG = false;

    private int mMaxItems;
    private boolean mUnlimited;

    private List<SearchResult> mResult;
    private SearchResult mCurrentData;

    /*-
     * http://www.thetvdb.com/api/GetSeries.php?seriesname=The%20Shield&language=de 
     *
     * <Data>
     *   <Series>
     *     <seriesid>78261</seriesid> 
     *     <language>de</language>
     *     <SeriesName>The Shield</SeriesName>
     *     <banner>graphical/78261-g6.jpg</banner>
     *     <Overview>Captain David...</Overview>
     *     <FirstAired>2002-03-12</FirstAired>
     *     <IMDB_ID>tt0286486</IMDB_ID>
     *     <zap2it_id>SH492273</zap2it_id>
     *     <id>78261</id>
     *   </Series>
     *   <Series></Series>
     * </Data>
     */

    // private final static String ELEMENT_0ROOT = "Data";

    private final static String ELEMENT_1SERIES = "Series";

    private final static String ELEMENT_2SERIESID = "seriesid";
    private final static String ELEMENT_2LANGUAGE = "language";
    private final static String ELEMENT_2SERIESNAME = "SeriesName";

    // private final static String ELEMENT_2BANNER = "banner";
    // private final static String ELEMENT_2OVERVIEW = "Overview";
    // private final static String ELEMENT_2FIRSTAIRED = "FirstAired";

    // private final static String ELEMENT_2IMDB_ID = "IMDB_ID";
    // private final static String ELEMENT_2ZAP2IT_ID = "zap2it_id";
    // private final static String ELEMENT_2ID = "id";

    public ShowSearchHandler(int maxItems) {
        setLimit(maxItems);
    }

    public List<SearchResult> getResult() {
        List<SearchResult> result = mResult;
        mResult = null;
        mCurrentData = null;
        return result;
    }

    @Override
    protected void startFile() {
        mResult = new LinkedList<SearchResult>();
    }

    @Override
    protected boolean startItem(int hierarchyLevel, String uri, String localName, String qName,
            Attributes attributes) {
        if (mUnlimited || mResult.size() < mMaxItems) {
            // new Element, if the string content is of interest turn ignore bit
            // off
            if (hierarchyLevel == 1) {
                if (ELEMENT_1SERIES.equals(localName)) {
                    // new <Series>, create a new Data Element
                    mCurrentData = new SearchResult();
                    return false;
                }
            } else if (hierarchyLevel == 2) {
                if (ELEMENT_2LANGUAGE.equals(localName)) {
                    return true;
                } else if (ELEMENT_2SERIESID.equals(localName)) {
                    return true;
                } else if (ELEMENT_2SERIESNAME.equals(localName)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void endItem(int hierarchyLevel, String uri, String localName, String qName) {
        if (mUnlimited || mResult.size() < mMaxItems) {
            if (hierarchyLevel == 1) {
                if (ELEMENT_1SERIES.equals(localName)) {
                    // Add the <Series> element just processed to the result
                    if (mResult.isEmpty()
                            || mResult.get(mResult.size() - 1).getId() != mCurrentData.getId()) {
                        if (DBG) Log.d(TAG, "add " + mCurrentData.getTitle());
                        mResult.add(mCurrentData);
                    } else {
                        if (DBG) Log.d(TAG, "not add " + mCurrentData.getTitle());
                    }
                }
            } else if (hierarchyLevel == 2) {
                if (ELEMENT_2LANGUAGE.equals(localName)) {
                    mCurrentData.setLanguage(getString());
                } else if (ELEMENT_2SERIESID.equals(localName)) {
                    // defaults to 0 if series id could not be parsed.
                    mCurrentData.setId(getInt(0));
                } else if (ELEMENT_2SERIESNAME.equals(localName)) {
                    mCurrentData.setTitle(getString());
                }
            }
        }
    }

    @Override
    protected void stopFile() {
        // nothing
    }

    public void setLimit(int maxItems) {
        mMaxItems = maxItems;
        mUnlimited = (maxItems < 0);
    }

}
