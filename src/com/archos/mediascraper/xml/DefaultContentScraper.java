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
import android.text.TextUtils;
import android.util.Log;

import com.archos.filecorelibrary.FileUtils;
import com.archos.medialib.R;
import com.archos.mediascraper.FileFetcher;
import com.archos.mediascraper.HttpCache;
import com.archos.mediascraper.MediaScraper;
import com.archos.mediascraper.MovieTags;
import com.archos.mediascraper.ScrapeDetailResult;
import com.archos.mediascraper.ScrapeSearchResult;
import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.ScraperImage;
import com.archos.mediascraper.SearchResult;
import com.archos.mediascraper.preprocess.SearchInfo;
import com.archos.mediascraper.settings.ScraperSetting;
import com.archos.mediascraper.settings.ScraperSettings;
import com.archos.mediascraper.themoviedb3.ImageConfiguration;
import com.archos.mediascraper.themoviedb3.MovieId;
import com.archos.mediascraper.themoviedb3.MovieIdDescription;
import com.archos.mediascraper.themoviedb3.MovieIdImages;
import com.archos.mediascraper.themoviedb3.MovieIdResult;
import com.archos.mediascraper.themoviedb3.SearchMovie;
import com.archos.mediascraper.themoviedb3.SearchMovieResult;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class provides the necessary information for "Demo video" and "Big Buck Bunny"
 * It's a modified copy from MovieScraper2 that does not use an Internet connection
 * and also does not mess with the state of MovieScraper2.
 */
public class DefaultContentScraper extends BaseScraper2 {

    public DefaultContentScraper(Context context) {
        super(context);
    }

    // we need the MovieScraper2 Settings
    private static final String PREFERENCE_NAME = "themoviedb.org";

    private final static String TAG = "DefaultContentScraper";

    private static ScraperSettings sSettings;

    @Override
    public ScrapeSearchResult getMatches2(SearchInfo info, int maxItems) {
        return getMatches2(info.getFile(), null, maxItems);//TODO metafilereplace
    }

    /**
     * only used in DefaultContentScraper
     * Request the detail for the first matching entry directly
     * @param file The file used to query
     * @param searchString Optional String to search for instead of using the Filename
     * @return The result
     * @throws IOException if something went wrong
     *
     * @deprecated use {@link #search(SearchInfo)}
     */
    @Deprecated
    public final ScrapeDetailResult search(Uri file, String searchString) {
        if (file == null) {
            Log.e(TAG, "search - no file given");
            return new ScrapeDetailResult(null, true, null, ScrapeStatus.ERROR, null);
        }
        ScrapeDetailResult result = null;
        ScrapeSearchResult searchResult = getMatches2(file, searchString, 1);
        if (searchResult.isOkay()) {
            result = getDetails(searchResult.results.get(0), null);
        } else {
            result = new ScrapeDetailResult(null, searchResult.isMovie, null, searchResult.status, searchResult.reason);
        }
        return result;
    }
    
    public ScrapeSearchResult getMatches2(Uri file, String searchString, int maxItems) {
        String search;
        if (searchString != null && !searchString.isEmpty()) {
            search = searchString;
        } else {
            search = stripExtension(FileUtils.getName(file));
        }
        // if we have a Movie.Name.2012.XZY.avi like name use regex
        if (matchesScene(search)) {
            search = getSceneName(search);
        } else {
            // else fallback to old schema
            search = formatFilename(search);
        }
        generatePreferences(mContext);
        String language = sSettings.getString("language");

        FakeFileFetcher jff = new FakeFileFetcher();
        SearchMovieResult searchResult = SearchMovie.search(search, language, null, maxItems, jff);
        if (searchResult.status == ScrapeStatus.OKAY) {
            for (SearchResult result : searchResult.result) {
                result.setScraper(this);
                result.setFile(file);
            }
        }
        return new ScrapeSearchResult(searchResult.result, true, searchResult.status, searchResult.reason);
    }

    @Override
    protected ScrapeDetailResult getDetailsInternal(SearchResult result, Bundle options) {
        generatePreferences(mContext);

        String language = sSettings.getString("language");
        long movieId = result.getId();
        Uri searchFile = result.getFile();

        FakeFileFetcher jff = new FakeFileFetcher();

        // get base info
        MovieIdResult search = MovieId.getBaseInfo(movieId, language, jff);
        if (search.status != ScrapeStatus.OKAY) {
            return new ScrapeDetailResult(search.tag, true, null, search.status, search.reason);
        }

        MovieTags tag = search.tag;
        tag.setFile(searchFile);//TODO metafilereplace

        // add posters and backdrops
        MovieIdImages.addImages(movieId, tag, language,
                ImageConfiguration.PosterSize.W342, // large poster
                ImageConfiguration.PosterSize.W92,  // thumb poster
                ImageConfiguration.BackdropSize.W1280, // large bd
                ImageConfiguration.BackdropSize.W300,  // thumb bd
                searchFile.toString(), jff, mContext);
        ScraperImage defaultPoster = tag.getDefaultPoster();
        if (defaultPoster != null) {
            tag.setCover(defaultPoster.getLargeFileF());
            // don't use the internets to download the poster
            defaultPoster.downloadFake(mContext);
        }

        // if there was no movie description in the native language get it from default
        if (tag.getPlot() == null) {
            MovieIdDescription.addDescription(movieId, tag, jff);
        }

        return new ScrapeDetailResult(tag, true, null, ScrapeStatus.OKAY, null);
    }

    // NOT ( whitespace | punctuation), matches A-Z, 0-9, localized characters etc
    private static String CHARACTER = "[^\\s\\p{Punct}]";
    // ( whitespace | punctuation), matches dots, spaces, brackets etc
    private static String NON_CHARACTER = "[\\s\\p{Punct}]";
    // matches "word"
    private static String CHARACTER_GROUP = CHARACTER + "+";
    // matches shortest "word.word.word."
    private static String MULTIPLE_GROUP_AND_SEPARATOR = "(?:" + CHARACTER_GROUP + NON_CHARACTER + ")+?";
    // matches "19XX and 20XX"
    private static String YEAR = "(?:19|20)\\d{2}";
    // matches "word.word.word.1986", capturing group
    private static String MOVIENAME_YEAR_GROUP = "(" + MULTIPLE_GROUP_AND_SEPARATOR + YEAR + ")";
    // matches ".junk.junk.junk"
    private static String REMAINING_JUNK = "(?:" + NON_CHARACTER + CHARACTER_GROUP + ")+";
    // matches "Movie.Name.2011.JUNK.JUNK.avi"
    private static String MOVIENAME_YEAR_JUNK = MOVIENAME_YEAR_GROUP + REMAINING_JUNK;
    private static Pattern SCENE_NAME = Pattern.compile(MOVIENAME_YEAR_JUNK);

    /** true if input is like "Name.Name.2011.JUNK.JUNK" */
    public static boolean matchesScene(String input) {
        if (input == null || input.isEmpty())
            return false;
        Matcher m = SCENE_NAME.matcher(input);
        return m.matches() && m.groupCount() > 0;
    }

    /** "Name.Name.2011.JUNK.JUNK" -> "name name 2011" */
    public static String getSceneName(String input) {
        Matcher m = SCENE_NAME.matcher(input);
        if (m.matches()) {
            String nameWithDots = m.group(1);
            return nameWithDots.replaceAll(NON_CHARACTER, " ").toLowerCase(Locale.US);
        }
        return input;
    }

    // Most of the common garbage in movies name we want to strip out
    // (they can be part of the name or correspond to extensions as well).
    private static final String[] GARBAGE_LOWERCASE = {
            " dvdrip ", "dvd rip ", " dvdscr ", " dvd scr ",
            " brrip ", " br rip ", " bdrip", " bd rip ", " blu ray ", " bluray ",
            " hddvd ", " hd dvd ", " hdrip ", " hd rip ",
            " webrip ", " web rip ",
            " 720p ", " 1080p ", " 1080i ", " 720 ", " 1080 ", " 480i ",
            " hdtv ", " sdtv ",
            " h264 ", " x264 ", " aac ", " ac3 ", " ogm ", " dts ",
            " avi ", " mkv ", " xvid ", " divx ", " wmv ", " mpg ", " mpeg ", " flv ", " f4v ",
            " asf ", " vob ", " mp4 ", " mov ",
            " directors cut ", " dircut ", " readnfo ", " read nfo ", " repack ", " rerip ",
    };
    // stuff that could be present in real names is matched with tight case sensitive syntax
    private static final String[] GARBAGE_CASESENSITIVE = {
        ".FRENCH.", ".TRUEFRENCH.", ".DUAL.", ".MULTi.",
        ".COMPLETE.", ".PROPER", ".iNTERNAL",
        ".SUBBED.", ".ANiME.", ".LIMITED.",
        ".TS.", ".TC.", ".REAL.",
        ".EN.", ".DE.", ".FR.", ".ES.", ".IT.", ".NL.",
    };
    public static String formatFilename(String filename) {
        String ret = filename;
        // Extract the release year.
        ret = ret.replaceAll(NON_CHARACTER + "(" + YEAR + ")" + NON_CHARACTER, " $1 ");
        // Strip out everything else in brackets <[{( .. )})>, most of the time teams names, etc
        ret = ret.replaceAll("[<\\(\\[\\{].+?[>\\)\\]\\}]", "");
        // strip away known case sensitive garbage
        ret = sanitizeFilename(ret, GARBAGE_CASESENSITIVE);
        // replace all remaining whitespace & punctuation with a single space
        ret = ret.replaceAll(NON_CHARACTER + "+", " ");
        // lowercase it
        ret = ret.toLowerCase(Locale.US);
        // append a " " to aid next step
        ret = ret + " ";
        // try to remove more garbage, this time " garbage " syntax
        ret = sanitizeFilename(ret, GARBAGE_LOWERCASE);
        // Remove any white space characters at the beginning and at the end of
        // the string which could be left after replacing the dots with spaces
        ret = ret.trim();
        return ret;
    }

    public static final String stripExtension(String filename) {
        String ret = filename;
        // truncate the file extension (if any)
        int lastDot = ret.lastIndexOf('.');
        if (lastDot > 0) {
            ret = ret.substring(0, lastDot);
        }
        return ret;
    }

    /** deletes everything at the first garbage match - the title is always first. */
    public static final String sanitizeFilename(String filename, String[] garbage) {
        String tmp = filename;
        int firstGarbage = tmp.length();
        for (String test : garbage) {
            int index = tmp.indexOf(test);
            if (index > -1 && index < firstGarbage)
                firstGarbage = index;
        }
        tmp = tmp.substring(0, firstGarbage);
        return tmp;
    }

    protected static ScraperSettings generatePreferences(Context context) {
        synchronized (ShowScraper2.class) {
            if (sSettings != null) {
                return sSettings;
            }
            sSettings = new ScraperSettings(context, PREFERENCE_NAME);
            HashMap<String, String> labelList = new HashMap<String, String>();
            String[] labels = context.getResources().getStringArray(R.array.scraper_labels_array);
            for (String label : labels) {
                String[] splitted = label.split(":");
                labelList.put(splitted[0], splitted[1]);
            }
            // <settings><setting label="info_language" type="labelenum" id="language" values="$$8" sort="yes" default="en"></setting></settings>
            ScraperSetting setting = new ScraperSetting("language", ScraperSetting.STR_LABELENUM);
            String defaultLang = Locale.getDefault().getLanguage();
            if (!TextUtils.isEmpty(defaultLang) && LANGUAGES.contains(defaultLang))
                setting.setDefault(defaultLang);
            else
                setting.setDefault("en");
            setting.setLabel(labelList.get("info_language"));
            setting.setValues(LANGUAGES);
            sSettings.addSetting("language", setting);

            return sSettings;
        }

    }

    @Override
    protected String internalGetPreferenceName() {
        return PREFERENCE_NAME;
    }

    private static class FakeFileFetcher extends FileFetcher {
        public FakeFileFetcher() { /* Nothing */ }

        @Override
        public FileFetchResult getFile(String url) {
            FileFetchResult result = new FileFetchResult();
            File f = HttpCache.getStaticFile(url,
                    MediaScraper.CACHE_FALLBACK_DIRECTORY,
                    MediaScraper.CACHE_OVERWRITE_DIRECTORY);
            result.file = f;
            result.status = f != null ? ScrapeStatus.OKAY : ScrapeStatus.ERROR_NETWORK;
            return result;
        }
    }

}
