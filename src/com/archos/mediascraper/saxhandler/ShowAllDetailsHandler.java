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

import android.content.Context;
import android.util.Log;

import org.xml.sax.Attributes;

import com.archos.mediascraper.EpisodeTags;
import com.archos.mediascraper.ShowTags;
import java.util.HashMap;
import java.util.Map;

/**
 * Sax Handler for results from
 * http://www.thetvdb.com/api/api-key/series/$ID$/all/$LANGUAGE$.xml
 */
public class ShowAllDetailsHandler extends BasicHandler {
    private final Context mContext;
    // private final static String TAG = "ShowAllDetailsHandler";
    // private final static boolean DBG = true;

    private boolean mInEpisodes = false;

    private Map<String, EpisodeTags> mResult = null;
    private EpisodeTags mCurrentData = null;
    private ShowTags mShowTags = null;

    public static final String DEFAULT_LANGUAGE = "en";

    private final static String ELEMENT_1SERIES = "Series";
    private final static String ELEMENT_2ID = "id";
    private final static String ELEMENT_2CONTENT_RATING = "ContentRating";
    private final static String ELEMENT_2IMDB_ID = "IMDB_ID";
    private final static String ELEMENT_2FIRST_AIRED = "FirstAired";
    private final static String ELEMENT_2GENRE = "Genre";
    private final static String ELEMENT_2NETWORK = "Network";
    private final static String ELEMENT_2OVERVIEW = "Overview";
    private final static String ELEMENT_2RATING = "Rating";
    private final static String ELEMENT_2SERIESNAME = "SeriesName";

    private final static String ELEMENT_1EPISODE = "Episode";
    private final static String ELEMENT_2EDIRECTOR = "Director";
    private final static String ELEMENT_2EEP_NAME = "EpisodeName";
    private final static String ELEMENT_2EEP_FILENAME = "filename";
    private final static String ELEMENT_2EEP_NUMBER = "EpisodeNumber";
    private final static String ELEMENT_2EGUESTS = "GuestStars";
    private final static String ELEMENT_2ESEAS_NUMBER = "SeasonNumber";

    private final static int PARSE_CONTENT_RATING = 0;
    private final static int PARSE_IMDB_ID = 1;
    private final static int PARSE_EDIRECTOR = 2;
    private final static int PARSE_EEP_NAME = 3;
    private final static int PARSE_EEP_NUMBER = 4;
    private final static int PARSE_EGUESTS = 6;
    private final static int PARSE_ESEAS_NUMBER = 9;
    private final static int PARSE_FIRSTAIRED = 11;
    private final static int PARSE_GENRE = 12;
    private final static int PARSE_ID = 13;
    private final static int PARSE_NETWORK = 14;
    private final static int PARSE_OVERVIEW = 15;
    private final static int PARSE_RATING = 17;
    private final static int PARSE_SERIESNAME = 18;
    private final static int PARSE_FILENAME = 19;
    private final static HashMap<String, Integer> WANT_CONTENT = new HashMap<String, Integer>(20);
    static {
        WANT_CONTENT.put(ELEMENT_2CONTENT_RATING, Integer.valueOf(PARSE_CONTENT_RATING));
        WANT_CONTENT.put(ELEMENT_2IMDB_ID, Integer.valueOf(PARSE_IMDB_ID));
        WANT_CONTENT.put(ELEMENT_2EDIRECTOR, Integer.valueOf(PARSE_EDIRECTOR));
        WANT_CONTENT.put(ELEMENT_2EEP_NAME, Integer.valueOf(PARSE_EEP_NAME));
        WANT_CONTENT.put(ELEMENT_2EEP_NUMBER, Integer.valueOf(PARSE_EEP_NUMBER));
        WANT_CONTENT.put(ELEMENT_2EGUESTS, Integer.valueOf(PARSE_EGUESTS));
        WANT_CONTENT.put(ELEMENT_2ESEAS_NUMBER, Integer.valueOf(PARSE_ESEAS_NUMBER));
        WANT_CONTENT.put(ELEMENT_2FIRST_AIRED, Integer.valueOf(PARSE_FIRSTAIRED));
        WANT_CONTENT.put(ELEMENT_2GENRE, Integer.valueOf(PARSE_GENRE));
        WANT_CONTENT.put(ELEMENT_2ID, Integer.valueOf(PARSE_ID));
        WANT_CONTENT.put(ELEMENT_2NETWORK, Integer.valueOf(PARSE_NETWORK));
        WANT_CONTENT.put(ELEMENT_2OVERVIEW, Integer.valueOf(PARSE_OVERVIEW));
        WANT_CONTENT.put(ELEMENT_2RATING, Integer.valueOf(PARSE_RATING));
        WANT_CONTENT.put(ELEMENT_2SERIESNAME, Integer.valueOf(PARSE_SERIESNAME));
        WANT_CONTENT.put(ELEMENT_2EEP_FILENAME, Integer.valueOf(PARSE_FILENAME));
    }

    public ShowAllDetailsHandler(Context context) {
        mContext = context;
    }

    public Map<String, EpisodeTags> getResult() {
        Map<String, EpisodeTags> result = mResult;
        mResult = null;
        mCurrentData = null;
        return result;
    }

    public ShowTags getShowTags() {
        ShowTags showTags = mShowTags;
        mShowTags = null;
        return showTags;
    }

    public static final String getKey(int season, int episode) {
        return String.valueOf(season) + '|' + episode;
    }

    @Override
    protected void startFile() {
        mResult = new HashMap<String, EpisodeTags>();
    }

    @Override
    protected boolean startItem(int hierarchyLevel, String uri, String localName, String qName,
            Attributes attributes) {
        if (hierarchyLevel == 1) {
            if (ELEMENT_1SERIES.equals(localName)) {
                mInEpisodes = false;
                mShowTags = new ShowTags();
                return false;
            } else if (ELEMENT_1EPISODE.equals(localName)) {
                mInEpisodes = true;
                mCurrentData = new EpisodeTags();
                return false;
            }
        } else if (hierarchyLevel == 2) {
            return WANT_CONTENT.containsKey(localName);
        }
        return false;
    }

    @Override
    protected void endItem(int hierarchyLevel, String uri, String localName, String qName) {
        if (hierarchyLevel == 1) {
            if (ELEMENT_1EPISODE.equals(localName)) {
                // Add the <Episode> element just processed to the result
                mCurrentData.setShowTags(mShowTags);
                mResult.put(getKey(mCurrentData.getSeason(), mCurrentData.getEpisode()),
                        mCurrentData);
            }
        } else if (hierarchyLevel == 2) {
            Integer parseItem = WANT_CONTENT.get(localName);
            if (parseItem == null) {
                return;
            }
            if (mInEpisodes) {
                switch (parseItem.intValue()) {
                    case PARSE_ID:
                        mCurrentData.setOnlineId(getLong());
                        break;
                    case PARSE_IMDB_ID:
                        mCurrentData.setImdbId(getString());
                        break;
                    case PARSE_EDIRECTOR:
                        mCurrentData.addDirectorIfAbsent(getString(), '|', ',');
                        break;
                    case PARSE_EEP_NAME:
                        mCurrentData.setTitle(getString());
                        break;
                    case PARSE_EEP_NUMBER:
                        mCurrentData.setEpisode(getInt());
                        break;
                    case PARSE_FILENAME:
                        mCurrentData.setEpisodePicture(getString(), mContext);
                        break;
                    case PARSE_FIRSTAIRED:
                        mCurrentData.setAired(getString());
                        break;
                    case PARSE_EGUESTS:
                        mCurrentData.addActorIfAbsent(getString(), '|', ',');
                        break;
                    case PARSE_OVERVIEW:
                        mCurrentData.setPlot(getString());
                        break;
                    case PARSE_RATING:
                        mCurrentData.setRating(getFloat());
                        break;
                    case PARSE_ESEAS_NUMBER:
                        mCurrentData.setSeason(getInt());
                        break;
                    default:
                        break;
                }
            } else {
                switch (parseItem.intValue()) {
                    case PARSE_CONTENT_RATING:
                        mShowTags.setContentRating(getString());
                        break;
                    case PARSE_IMDB_ID:
                        mShowTags.setImdbId(getString());
                        break;
                    case PARSE_FIRSTAIRED:
                        mShowTags.setPremiered(getString());
                        break;
                    case PARSE_GENRE:
                        mShowTags.addGenreIfAbsent(getString(), '|', ',');
                        break;
                    case PARSE_ID:
                        mShowTags.setOnlineId(getLong());
                        break;
                    case PARSE_NETWORK:
                        mShowTags.addStudioIfAbsent(getString(), '|', ',');
                        break;
                    case PARSE_OVERVIEW:
                        mShowTags.setPlot(getString());
                        break;
                    case PARSE_RATING:
                        mShowTags.setRating(getFloat());
                        break;
                    case PARSE_SERIESNAME:
                        mShowTags.setTitle(getString());
                        break;
                    default:
                        break;
                }
            }
        }
    }

    @Override
    protected void stopFile() {
        // nothing
    }
}
