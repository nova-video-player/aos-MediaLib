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
import com.archos.mediascraper.NfoParser;
import com.archos.mediascraper.ScraperImage;
import com.archos.mediascraper.ShowTags;
import com.archos.mediascraper.StringMatcher;

import org.xml.sax.Attributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for tvshow.nfo files as described in
 * http://wiki.xbmc.org/index.php?title=Import-export_library#Video_.nfo_Files
 */
public class NfoShowHandler extends BasicHandler {

    private static final StringMatcher STRINGS = new StringMatcher();
    private static final int ROOT = 1;

    private static final int TITLE = 2;
    private static final int RATING = 3;
    private static final int PLOT = 4;
    private static final int MPAA = 5;
    private static final int PREMIERED = 6;
    private static final int STUDIO = 7;
    private static final int ID = 8;
    private static final int GENRE = 9;
    private static final int ACTOR = 10;
    private static final int NAME = 11;
    private static final int ROLE = 12;
    private static final int THUMB = 13;
    private static final int FANART = 14;
    private static final int IMDBID = 15;

    static {
        STRINGS.addKey("tvshow", ROOT);

        STRINGS.addKey("title", TITLE);
        STRINGS.addKey("rating", RATING);
        STRINGS.addKey("plot", PLOT);
        STRINGS.addKey("mpaa", MPAA);
        STRINGS.addKey("premiered", PREMIERED);
        STRINGS.addKey("studio", STUDIO);
        STRINGS.addKey("id", ID);
        STRINGS.addKey("genre", GENRE);
        STRINGS.addKey("actor", ACTOR);
        STRINGS.addKey("name", NAME);
        STRINGS.addKey("role", ROLE);
        STRINGS.addKey("thumb", THUMB);
        STRINGS.addKey("fanart", FANART);
        STRINGS.addKey("imdbid", IMDBID);
    }

    private ShowTags mResult;
    private final LinkedHashMap<String, Integer> mPosters = new LinkedHashMap<String, Integer>();
    private final ArrayList<String> mBackdrops = new ArrayList<String>();
    private boolean mCanParse;

    private String mActorName, mActorRole;
    private boolean mInActor;
    private boolean mInFanart;
    private int mPosterSeason;

    @Override
    protected void startFile() {
        clear();
    }

    public void clear() {
        mResult = null;
        mPosters.clear();
        mBackdrops.clear();
        mCanParse = false;
        mActorName = null;
        mActorRole = null;
        mInActor = false;
        mInFanart = false;
        mPosterSeason = 0;
    }

    @Override
    protected boolean startItem(int hierarchyLevel, String uri, String localName, String qName,
            Attributes attributes) {
        if (hierarchyLevel == 0 && STRINGS.match(localName) == ROOT) {
            mResult = new ShowTags();
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
                    case RATING:
                    case PLOT:
                    case MPAA:
                    case PREMIERED:
                    case STUDIO:
                    case ID:
                    case GENRE:
                    case IMDBID:
                        return true;
                    case THUMB:
                        mPosterSeason = parseInt(attributes.getValue("", "season"));
                        return true;
                    // actor needs sub node parsing
                    case ACTOR:
                        mInActor = true;
                        mActorName = null;
                        mActorRole = null;
                        break;
                    case FANART:
                        mInFanart = true;
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
                if (mInFanart && STRINGS.match(localName) == THUMB)
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
                    case RATING:
                        mResult.setRating(getFloat());
                        break;
                    case PLOT:
                        mResult.setPlot(getString());
                        break;
                    case MPAA:
                        mResult.setContentRating(getString());
                        break;
                    case PREMIERED:
                        mResult.setPremiered(getString());
                        break;
                    case STUDIO:
                        mResult.addStudioIfAbsent(getString(), NfoParser.STRING_SPLITTERS);
                        break;
                    case ID:
                        mResult.setOnlineId(getLong());
                        break;
                    case GENRE:
                        mResult.addGenreIfAbsent(getString(), NfoParser.STRING_SPLITTERS);
                        break;
                    case IMDBID:
                        mResult.setImdbId(getString());
                        break;
                    case ACTOR:
                        mInActor = false;
                        mResult.addActorIfAbsent(mActorName, mActorRole);
                        break;

                    case THUMB:
                        String url = getString();
                        // posters can exist both with and without season.
                        // discard posters w/o season (= lower value)
                        Integer old = mPosters.put(url, Integer.valueOf(mPosterSeason));
                        if (old != null && old.intValue() > mPosterSeason) {
                            mPosters.put(url, old);
                        }
                        break;

                    case FANART:
                        mInFanart = false;
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
                if (mInFanart && STRINGS.match(localName) == THUMB) {
                    mBackdrops.add(getString());
                }
                break;
            default:
                break;
        }
    }

    public ShowTags getResult(Context context, Uri movieFile) {
        if (mCanParse) {
            String seed = mResult.getTitle();
            if (seed == null) {
                // fallback to something useful
                seed = movieFile.toString();
            }
            if (!mPosters.isEmpty()) {
                ArrayList<ScraperImage> images = new ArrayList<ScraperImage>(mPosters.size());
                for (Entry<String, Integer> entry : mPosters.entrySet()) {
                    String url = entry.getKey();
                    int season = entry.getValue().intValue();
                    if (url != null && !url.isEmpty() && url.startsWith("http")) {
                        ScraperImage.Type type = season >= 0 ? ScraperImage.Type.EPISODE_POSTER : ScraperImage.Type.SHOW_POSTER;
                        ScraperImage image = new ScraperImage(type, seed);
                        image.setLargeUrl(rewriteUrl(url));
                        image.setThumbUrl(rewriteUrl(url));
                        image.setSeason(season);
                        image.generateFileNames(context);
                        images.add(image);
                    }
                }
                mResult.setPosters(images);
            }
            if (!mBackdrops.isEmpty()) {
                ArrayList<ScraperImage> images = new ArrayList<ScraperImage>(mBackdrops.size());
                for (String url : mBackdrops) {
                    if (url != null && !url.isEmpty() && url.startsWith("http")) {
                        ScraperImage image = new ScraperImage(ScraperImage.Type.SHOW_BACKDROP, seed);
                        image.setLargeUrl(url);
                        image.setThumbUrl(rewriteUrl(url));
                        image.generateFileNames(context);
                        images.add(image);
                    }
                }
                mResult.setBackdrops(images);
            }
            return mResult;
        }
        return null;
    }

    private static int parseInt(String string) {
        int result = -1;
        if (string != null) {
            try {
                result = Integer.parseInt(string);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return result;
    }

    // matches both http and https
    private static final Pattern URL_PATTERN = Pattern.compile("https?://(?:www\\.)?thetvdb\\.com/banners/(?!_)(.+)");

    private static String rewriteUrl(String url) {
        String result = url;
        if (url != null) {
            Matcher matcher = URL_PATTERN.matcher(url);
            if (matcher.matches()) {
                result = url.substring(0, matcher.start(1)) + "_cache/" + url.substring(matcher.start(1));
                // force http to https rewrite since Android O does not like it anymore
                result = result.replace("http://","https://");
            }
        }
        return result;
    }

}
