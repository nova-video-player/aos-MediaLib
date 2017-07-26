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
import android.net.Uri;

import com.archos.filecorelibrary.MetaFile;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.mediascraper.EpisodeTags;
import com.archos.mediascraper.NfoParser;
import com.archos.mediascraper.StringMatcher;

import org.xml.sax.Attributes;

import java.util.concurrent.TimeUnit;

/**
 * Parser for tvshow.nfo files as described in
 * http://wiki.xbmc.org/index.php?title=Import-export_library#Video_.nfo_Files
 */
public class NfoEpisodeHandler extends BasicSubParseHandler {

    private static final StringMatcher STRINGS = new StringMatcher();
    private static final int ROOT = 1;

    private static final int TITLE = 2;
    private static final int RATING = 3;
    private static final int SEASON = 4;
    private static final int EPISODE = 5;
    private static final int PLOT = 6;
    private static final int MPAA = 7;
    private static final int AIRED = 8;
    private static final int DIRECTOR = 9;
    private static final int ACTOR = 10;
    private static final int NAME = 11;
    private static final int ROLE = 12;

    // fileinfo
    private static final int FILEINFO = 20;
    private static final int STREAMDETAILS = 21;
    private static final int VIDEO = 22;
    private static final int DURATIONINSECONDS = 23;

    private static final int LASTPLAYED = 24;
    private static final int RESUME = 25;
    private static final int BOOKMARK = 26;
    private static final int SHOWTITLE = 27;
    private static final int IMDBID = 28;
    private static final int TVDBID = 29;

    static {
        STRINGS.addKey("episodedetails", ROOT);

        STRINGS.addKey("title", TITLE);
        STRINGS.addKey("showtitle", SHOWTITLE);
        STRINGS.addKey("rating", RATING);
        STRINGS.addKey("season", SEASON);
        STRINGS.addKey("episode", EPISODE);
        STRINGS.addKey("plot", PLOT);
        STRINGS.addKey("mpaa", MPAA);
        STRINGS.addKey("aired", AIRED);
        STRINGS.addKey("director", DIRECTOR);
        STRINGS.addKey("actor", ACTOR);
        STRINGS.addKey("name", NAME);
        STRINGS.addKey("role", ROLE);
        STRINGS.addKey("lastplayed", LASTPLAYED);
        STRINGS.addKey("resume", RESUME);
        STRINGS.addKey("bookmark", BOOKMARK);
        STRINGS.addKey("imdbid", IMDBID);
        STRINGS.addKey("tvdbid", TVDBID);

        // fileinfo
        STRINGS.addKey("fileinfo", FILEINFO);
        STRINGS.addKey("streamdetails", STREAMDETAILS);
        STRINGS.addKey("video", VIDEO);
        STRINGS.addKey("durationinseconds", DURATIONINSECONDS);

    }

    private EpisodeTags mResult;
    private boolean mCanParse;
    private String mActorName, mActorRole;
    private boolean mInActor;
    private boolean mInFileinfo, mInStreamdetails, mInVideo;

    @Override
    protected void startFile() {
        clear();
    }

    public void clear() {
        mResult = null;
        mCanParse = false;
        mActorName = null;
        mActorRole = null;
        mInActor = false;
        mInFileinfo = false;
        mInStreamdetails = false;
        mInVideo = false;

    }

    @Override
    protected boolean startItem(int hierarchyLevel, String uri, String localName, String qName,
            Attributes attributes) {
        if (hierarchyLevel == 0 && STRINGS.match(localName) == ROOT) {
            mResult = new EpisodeTags();
            mCanParse = true;
        } else if (mCanParse) {
            return startParse(hierarchyLevel, localName, attributes);
        }
        return false;
    }

    @Override
    protected void endItem(int hierarchyLevel, String uri, String localName, String qName) {
        if (hierarchyLevel != 0 && mCanParse) {
            endParse(hierarchyLevel, localName);
        }
    }

    @Override
    protected void stopFile() {
        // empty
    }

    private boolean startParse(int hierarchyLevel, String localName, Attributes attributes) {
        switch (hierarchyLevel) {
            case 1:
                switch (STRINGS.match(localName)) {
                    // these are text nodes, return true to get text
                    case TITLE:
                    case SHOWTITLE:
                    case RATING:
                    case SEASON:
                    case EPISODE:
                    case PLOT:
                    case MPAA:
                    case AIRED:
                    case DIRECTOR:
                    case LASTPLAYED:
                    case BOOKMARK:
                    case RESUME:
                    case IMDBID:
                    case TVDBID:
                        return true;
                    // actor needs sub node parsing
                    case ACTOR:
                        mInActor = true;
                        mActorName = null;
                        mActorRole = null;
                        break;
                    case FILEINFO:
                        mInFileinfo = true;
                        break;
                    default:
                        break;
                }
                break;
            case 2:
                if (mInActor) {
                    switch (STRINGS.match(localName)) {
                        // name and role need text parsing, return true
                        case NAME:
                        case ROLE:
                            return true;
                        default:
                            break;
                    }
                }
                if (mInFileinfo && STRINGS.match(localName) == STREAMDETAILS)
                    mInStreamdetails = true;
                break;
            case 3:
                if (mInStreamdetails && STRINGS.match(localName) == VIDEO)
                    mInVideo = true;
                break;
            case 4:
                if (mInVideo && STRINGS.match(localName) == DURATIONINSECONDS)
                    return true;
                break;
            default:
                break;
        }
        return false;
    }

    private void endParse(int hierarchyLevel, String localName) {
        switch (hierarchyLevel) {
            case 1:
                switch (STRINGS.match(localName)) {
                    case TITLE:
                        mResult.setTitle(getString());
                        break;
                    case SHOWTITLE:
                        mResult.setShowTitle(getString());
                        break;
                    case RATING:
                        mResult.setRating(getFloat());
                        break;
                    case SEASON:
                        mResult.setSeason(getInt());
                        break;
                    case EPISODE:
                        mResult.setEpisode(getInt());
                        break;
                    case PLOT:
                        mResult.setPlot(getString());
                        break;
                    case MPAA:
                        mResult.setContentRating(getString());
                        break;
                    case AIRED:
                        mResult.setAired(getString());
                        break;
                    case DIRECTOR:
                        mResult.addDirectorIfAbsent(getString(), NfoParser.STRING_SPLITTERS);
                        break;
                    case ACTOR:
                        mInActor = false;
                        mResult.addActorIfAbsent(mActorName, mActorRole);
                        break;
                    case FILEINFO:
                        mInFileinfo = mInStreamdetails = mInVideo = false;
                        break;
                    case LASTPLAYED:
                        mResult.setLastPlayed(getLong(), TimeUnit.SECONDS);
                        break;
                    case RESUME:
                        mResult.setResume(getLong());
                        break;
                    case BOOKMARK:
                        mResult.setBookmark(getLong());
                        break;
                    case IMDBID:
                        mResult.setImdbId(getString());
                        break;
                    case TVDBID:
                        mResult.setOnlineId(getLong());
                        break;
                    default:
                        break;
                }
                break;
            case 2:
                if (mInActor) {
                    switch (STRINGS.match(localName)) {
                        case NAME:
                            mActorName = getString();
                            break;
                        case ROLE:
                            mActorRole = getString();
                            break;
                        default:
                            break;
                    }
                }
                if (mInFileinfo && STRINGS.match(localName) == STREAMDETAILS) {
                    mInStreamdetails = mInVideo = false;
                }
                break;
            case 3:
                if (mInVideo && STRINGS.match(localName) == VIDEO) {
                    mInVideo = false;
                }
                break;
            case 4:
                if (mInVideo && STRINGS.match(localName) == DURATIONINSECONDS) {
                    mResult.setRuntime(getLong(), TimeUnit.SECONDS);
                }
                break;
            default:
                break;
        }
    }

    public EpisodeTags getResult(Context context, Uri movieFile) {
        if (mCanParse) {
            mResult.setFile(movieFile);
            return mResult;
        }
        return null;
    }
}
