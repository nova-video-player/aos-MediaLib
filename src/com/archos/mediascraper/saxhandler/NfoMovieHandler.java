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
import android.util.Log;

import com.archos.mediascraper.MovieTags;
import com.archos.mediascraper.NfoParser;
import com.archos.mediascraper.ScraperImage;
import com.archos.mediascraper.StringMatcher;
import com.archos.mediascraper.themoviedb3.ImageConfiguration;
import com.archos.mediascraper.themoviedb3.ImageConfiguration.BackdropSize;
import com.archos.mediascraper.themoviedb3.ImageConfiguration.PosterSize;

import org.xml.sax.Attributes;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static com.archos.mediascraper.themoviedb3.MovieCollectionImages.downloadCollectionImage;

/**
 * Parser for movie .nfo files as described in
 * http://wiki.xbmc.org/index.php?title=Import-export_library#Video_.nfo_Files
 */
public class NfoMovieHandler extends BasicSubParseHandler {

    private final static String TAG = "NfoMovieHandler";
    private final static boolean DBG = false;

    private static final StringMatcher STRINGS = new StringMatcher();
    private static final int ROOT_MOVIE = 1;

    private static final int TITLE = 4;
    private static final int RATING = 5;
    private static final int YEAR = 6;
    private static final int OUTLINE = 7;
    private static final int THUMB = 8;
    private static final int MPAA = 9;
    private static final int ID = 10;
    private static final int GENRE = 11;
    private static final int DIRECTOR = 12;
    private static final int ACTOR = 13;
    private static final int NAME = 14;
    private static final int ROLE = 15;
    private static final int FANART = 16;
    private static final int STUDIO = 17;
    private static final int TMDBID = 18;
    private static final int RUNTIME = 19;
    private static final int FILEINFO = 20;
    private static final int STREAMDETAILS = 21;
    private static final int VIDEO = 22;
    private static final int DURATIONINSECONDS = 23;
    private static final int LASTPLAYED = 24;
    private static final int RESUME = 25;
    private static final int BOOKMARK = 26;
    private static final int SET = 27;
    private static final int OVERVIEW = 28;
    private static final int POSTERLARGE = 29;
    private static final int POSTERTHUMB = 30;
    private static final int BACKDROPLARGE = 31;
    private static final int BACKDROPTHUMB = 32;
    private static final int WRITER = 33;
    private static final int TAGLINE = 34;

    static {
        STRINGS.addKey("movie", ROOT_MOVIE);
        STRINGS.addKey("title", TITLE);
        STRINGS.addKey("rating", RATING);
        STRINGS.addKey("year", YEAR);
        STRINGS.addKey("outline", OUTLINE);
        STRINGS.addKey("thumb", THUMB);
        STRINGS.addKey("mpaa", MPAA);
        STRINGS.addKey("id", ID);
        STRINGS.addKey("genre", GENRE);
        STRINGS.addKey("director", DIRECTOR);
        STRINGS.addKey("writer", WRITER);
        STRINGS.addKey("tagline", TAGLINE);
        STRINGS.addKey("actor", ACTOR);
        STRINGS.addKey("name", NAME);
        STRINGS.addKey("role", ROLE);
        STRINGS.addKey("fanart", FANART);
        STRINGS.addKey("studio", STUDIO);
        STRINGS.addKey("tmdbid", TMDBID);
        STRINGS.addKey("runtime", RUNTIME);
        STRINGS.addKey("lastplayed", LASTPLAYED);
        STRINGS.addKey("resume", RESUME);
        STRINGS.addKey("bookmark", BOOKMARK);
        // fileinfo
        STRINGS.addKey("fileinfo", FILEINFO);
        STRINGS.addKey("streamdetails", STREAMDETAILS);
        STRINGS.addKey("video", VIDEO);
        STRINGS.addKey("durationinseconds", DURATIONINSECONDS);
        // STRINGS.addKey("lastplayed", LASTPLAYED); // no way to use that atm
        STRINGS.addKey("set", SET);
        STRINGS.addKey("overview", OVERVIEW);
        STRINGS.addKey("posterLarge", POSTERLARGE);
        STRINGS.addKey("posterThumb", POSTERTHUMB);
        STRINGS.addKey("backdropLarge", BACKDROPLARGE);
        STRINGS.addKey("backdropThumb", BACKDROPTHUMB);
    }

    private MovieTags mMovie;
    private final ArrayList<String> mMoviePosterUrls = new ArrayList<String>();
    private final ArrayList<String> mMovieBackdropUrls = new ArrayList<String>();
    private final ArrayList<String> mActorPhotos = new ArrayList<String>();
    private final ArrayList<String> mStudioLogos = new ArrayList<String>();
    private boolean mCanParse;

    private String mActorName, mActorRole;
    private boolean mInActor;
    private boolean mInFanart;
    private boolean mInSet;
    private int mInSetId;
    private String mInSetName, mInSetOverview, mInSetPosterLarge, mInSetPosterThumb, mInSetBackdropLarge, mInSetBackdropThumb;
    private boolean mInFileinfo, mInStreamdetails, mInVideo;

    @Override
    protected void startFile() {
        clear();
    }

    public void clear() {
        mMovie = null;
        mMoviePosterUrls.clear();
        mMovieBackdropUrls.clear();
        mActorPhotos.clear();
        mStudioLogos.clear();
        mCanParse = false;
        mActorName = null;
        mActorRole = null;
        mInActor = false;
        mInFanart = false;
        mInFileinfo = false;
        mInStreamdetails = false;
        mInVideo = false;
        mInSet = false;
        mInSetId = -1;
        mInSetName = null;
        mInSetOverview = null;
        mInSetPosterLarge = null;
        mInSetPosterThumb = null;
        mInSetBackdropLarge = null;
        mInSetBackdropThumb = null;
    }

    @Override
    protected boolean startItem(int hierarchyLevel, String uri, String localName, String qName,
            Attributes attributes) {
        if (hierarchyLevel == 0) {
            switch(STRINGS.match(localName)) {
                case ROOT_MOVIE:
                    mMovie = new MovieTags();
                    mCanParse = true;
                    break;
                default:
                    break;
            }
        } else {
            if (mCanParse)
                return startMovie(hierarchyLevel, localName);
        }
        return false;
    }

    @Override
    protected void endItem(int hierarchyLevel, String uri, String localName, String qName) {
        if (hierarchyLevel != 0 && mCanParse) {
            endMovie(hierarchyLevel, localName);
        }
    }

    @Override
    protected void stopFile() {
        // empty
    }

    private boolean startMovie(int hierarchyLevel, String localName) {
        switch (hierarchyLevel) {
            case 1:
                switch (STRINGS.match(localName)) {
                    // these are text nodes, return true to get text
                    case TITLE:
                    case RATING:
                    case YEAR:
                    case OUTLINE:
                    case THUMB:
                    case MPAA:
                    case ID:
                    case GENRE:
                    case DIRECTOR:
                    case WRITER:
                    case TAGLINE:
                    case STUDIO:
                    case TMDBID:
                    case RUNTIME:
                    case LASTPLAYED:
                    case BOOKMARK:
                    case RESUME:
                        return true;
                    // actor needs sub node parsing
                    case SET:
                        mInSet = true;
                        mInSetId = -1;
                        mInSetName = null;
                        mInSetOverview = null;
                        mInSetPosterLarge = null;
                        mInSetPosterThumb = null;
                        mInSetBackdropLarge = null;
                        mInSetBackdropThumb = null;
                        break;
                    case ACTOR:
                        mInActor = true;
                        mActorName = null;
                        mActorRole = null;
                        break;
                    case FANART:
                        mInFanart = true;
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
                if (mInFanart) {
                    if (STRINGS.match(localName) == THUMB) {
                        return true;
                    }
                }
                if (mInFileinfo && STRINGS.match(localName) == STREAMDETAILS) {
                    mInStreamdetails = true;
                }
                if (mInSet) {
                    switch (STRINGS.match(localName)) {
                        // name and role need text parsing, return true
                        case ID:
                        case NAME:
                        case OVERVIEW:
                        case POSTERLARGE:
                        case POSTERTHUMB:
                        case BACKDROPLARGE:
                        case BACKDROPTHUMB:
                            return true;
                        default:
                            break;
                    }
                }
                break;
            case 3:
                if (mInStreamdetails && STRINGS.match(localName) == VIDEO) {
                    mInVideo = true;
                }
                break;
            case 4:
                if (mInVideo && STRINGS.match(localName) == DURATIONINSECONDS) {
                    return true;
                }
                break;
            default:
                break;
        }
        return false;
    }

    private void endMovie(int hierarchyLevel, String localName) {
        switch (hierarchyLevel) {
            case 1:
                switch (STRINGS.match(localName)) {
                    case TITLE:
                        mMovie.setTitle(getString());
                        break;
                    case RATING:
                        mMovie.setRating(getFloat());
                        break;
                    case YEAR:
                        mMovie.setYear(getInt());
                        break;
                    case OUTLINE:
                        mMovie.setPlot(getString());
                        break;
                    case THUMB:
                        mMoviePosterUrls.add(getString());
                        break;
                    case MPAA:
                        mMovie.setContentRating(getString());
                        break;
                    case ID:
                        mMovie.setImdbId(getString());
                        break;
                    case GENRE:
                        mMovie.addGenreIfAbsent(getString(), NfoParser.STRING_SPLITTERS);
                        break;
                    case DIRECTOR:
                        mMovie.addDirectorIfAbsent(getString(), NfoParser.STRING_SPLITTERS);
                        break;
                    case WRITER:
                        mMovie.addWriterIfAbsent(getString(), NfoParser.STRING_SPLITTERS);
                        break;
                    case TAGLINE:
                        mMovie.addTaglineIfAbsent(getString(), NfoParser.STRING_SPLITTERS);
                        break;
                    case STUDIO:
                        mMovie.addStudioIfAbsent(getString(), NfoParser.STRING_SPLITTERS);
                        break;
                    case TMDBID:
                        mMovie.setOnlineId(getLong());
                        break;
                    case ACTOR:
                        mInActor = false;
                        mMovie.addActorIfAbsent(mActorName, mActorRole);
                        break;
                    case FANART:
                        mInFanart = false;
                        break;
                    case RUNTIME:
                        mMovie.setRuntime(getLong(), TimeUnit.MINUTES);
                        break;
                    case FILEINFO:
                        mInFileinfo = mInStreamdetails = mInVideo = false;
                        break;
                    case LASTPLAYED:
                        mMovie.setLastPlayed(getLong(), TimeUnit.SECONDS);
                        break;
                    case RESUME:
                        mMovie.setResume(getLong());
                        break;
                    case BOOKMARK:
                        mMovie.setBookmark(getLong());
                        break;
                    case SET:
                        mInSet = false;
                        mMovie.setCollectionId(mInSetId);
                        mMovie.setCollectionName(mInSetName);
                        mMovie.setCollectionDescription(mInSetOverview);
                        mMovie.setCollectionPosterLargeUrl(mInSetPosterLarge);
                        if (mInSetPosterLarge != null) // need to isolate fileName at the end of url but keep the start /
                            mMovie.setCollectionPosterPath(mInSetPosterLarge.substring(mInSetPosterLarge.lastIndexOf('/')));
                        else
                            mMovie.setCollectionPosterPath(null);
                        mMovie.setCollectionPosterThumbUrl(mInSetPosterThumb);
                        mMovie.setCollectionBackdropLargeUrl(mInSetBackdropLarge);
                        if (mInSetBackdropLarge != null) // need to isolate fileName at the end of url but keep the start /
                            mMovie.setCollectionBackdropPath(mInSetBackdropLarge.substring(mInSetBackdropLarge.lastIndexOf('/')));
                        else
                            mMovie.setCollectionBackdropPath(null);
                        mMovie.setCollectionBackdropThumbUrl(mInSetBackdropThumb);
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
                if (mInFanart) {
                    if (STRINGS.match(localName) == THUMB) {
                        mMovieBackdropUrls.add(getString());
                    }
                }
                if (mInFileinfo && STRINGS.match(localName) == STREAMDETAILS) {
                    mInStreamdetails = mInVideo = false;
                }
                if (mInSet) {
                    switch (STRINGS.match(localName)) {
                        // name and role need text parsing, return true
                        case ID:
                            mInSetId = getInt();
                            break;
                        case NAME:
                            mInSetName = getString();
                            break;
                        case OVERVIEW:
                            mInSetOverview = getString();
                            break;
                        case POSTERLARGE:
                            mInSetPosterLarge = getString();
                            break;
                        case POSTERTHUMB:
                            mInSetPosterThumb = getString();
                            break;
                        case BACKDROPLARGE:
                            mInSetBackdropLarge = getString();
                            break;
                        case BACKDROPTHUMB:
                            mInSetBackdropThumb = getString();
                            break;
                        default:
                            break;
                    }
                }
                break;
            case 3:
                if (mInVideo && STRINGS.match(localName) == VIDEO) {
                    mInVideo = false;
                }
                break;
            case 4:
                if (mInVideo && STRINGS.match(localName) == DURATIONINSECONDS) {
                    long durationInSeconds = getLong();
                    mMovie.setRuntime(durationInSeconds, TimeUnit.SECONDS);
                }
                break;
            default:
                break;
        }
    }

    public MovieTags getResult(Context context, Uri movieFile) {
        if (DBG) Log.d(TAG, "getResult: processing " + movieFile.getPath());
        if (mCanParse) {
            if (!mMoviePosterUrls.isEmpty()) {
                ArrayList<ScraperImage> images = new ArrayList<ScraperImage>(mMoviePosterUrls.size());
                for (String url : mMoviePosterUrls) {
                    if (url != null && !url.isEmpty() && url.startsWith("http")) {
                        ScraperImage image = new ScraperImage(ScraperImage.Type.MOVIE_POSTER, movieFile.toString());
                        image.setLargeUrl(ImageConfiguration.rewriteUrl(url, PosterSize.W342));
                        image.setThumbUrl(ImageConfiguration.rewriteUrl(url, PosterSize.W92));
                        image.generateFileNames(context);
                        images.add(image);
                    }
                }
                mMovie.setPosters(images);
            }
            if (!mMovieBackdropUrls.isEmpty()) {
                ArrayList<ScraperImage> images = new ArrayList<ScraperImage>(mMovieBackdropUrls.size());
                for (String url : mMovieBackdropUrls) {
                    if (url != null && !url.isEmpty() && url.startsWith("http")) {
                        ScraperImage image = new ScraperImage(ScraperImage.Type.MOVIE_BACKDROP, movieFile.toString());
                        image.setLargeUrl(ImageConfiguration.rewriteUrl(url, BackdropSize.W1280));
                        image.setThumbUrl(ImageConfiguration.rewriteUrl(url, BackdropSize.W300));
                        image.generateFileNames(context);
                        images.add(image);
                    }
                }
                mMovie.setBackdrops(images);
            }

            if (!mActorPhotos.isEmpty()) {
                ArrayList<ScraperImage> images = new ArrayList<ScraperImage>(mActorPhotos.size());
                for (String url : mActorPhotos) {
                    if (url != null && !url.isEmpty() && url.startsWith("http")) {
                        ScraperImage image = new ScraperImage(ScraperImage.Type.MOVIE_ACTORPHOTO, movieFile.toString());
                        image.setLargeUrl(url);
                        image.setThumbUrl(url);
                        image.generateFileNames(context);
                        images.add(image);
                    }
                }
                mMovie.setActorPhotos(images);
            }

            if (!mStudioLogos.isEmpty()) {
                ArrayList<ScraperImage> images = new ArrayList<ScraperImage>(mStudioLogos.size());
                for (String url : mStudioLogos) {
                    if (url != null && !url.isEmpty() && url.startsWith("http")) {
                        ScraperImage image = new ScraperImage(ScraperImage.Type.MOVIE_STUDIOLOGO, movieFile.toString());
                        image.setLargeUrl(url);
                        image.setThumbUrl(url);
                        image.generateFileNames(context);
                        images.add(image);
                    }
                }
                mMovie.setStudioLogos(images);
            }

            if (mMovie.getCollectionId() > 0)
                downloadCollectionImage(mMovie,
                        ImageConfiguration.PosterSize.W342,    // large poster
                        ImageConfiguration.PosterSize.W92,     // thumb poster
                        ImageConfiguration.BackdropSize.W1280, // large bd
                        ImageConfiguration.BackdropSize.W300,  // thumb bd
                        mInSetPosterLarge, context);

            mMovie.setFile(movieFile);
            return mMovie;
        }
        return null;
    }
}
