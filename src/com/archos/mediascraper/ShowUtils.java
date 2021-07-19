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

package com.archos.mediascraper;

import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import com.archos.filecorelibrary.FileUtils;
import com.archos.mediascraper.preprocess.ParseUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.archos.mediascraper.StringUtils.removeTrailingSlash;
import static com.archos.mediascraper.preprocess.ParseUtils.getCountryOfOrigin;
import static com.archos.mediascraper.preprocess.ParseUtils.parenthesisYearExtractor;
import static com.archos.mediascraper.preprocess.ParseUtils.removeAfterEmptyParenthesis;

/**
 * Class used to parse the file names and try to guess if we have a tv show.
 */
public final class ShowUtils {

    private static final Logger log = LoggerFactory.getLogger(ShowUtils.class);

    // disable for now the SxxEyy-showName does not exist and makes ./serie/The Flash/S02/S02E01 lahlah.mkv not identified
    private static final boolean ENABLE_PATTERNS_EPISODE_FIRST = false;

    public static final String EPNUM = "epnum";
    public static final String SEASON = "season";
    public static final String SHOW = "show";
    public static final String YEAR = "year";
    public static final String ORIGIN = "origin";

    /** These are considered equivalent to space */
    public static final char[] REPLACE_ME = new char[] {
        '.', // "The.Good.Wife" will not be found
        '_', // "The_Good_Wife" would be found but we need the clean name for local display
    };

    private ShowUtils() {
        // do not make instances of me
    }

    // Separators: Punctuation or Whitespace
    // remove the "(" and ")" in punctuation to avoid matching end parenthesis of date in "show (1987) s01e01 title.mkv"
    private static final String SEP_OPTIONAL = "[[\\p{Punct}&&[^()]]\\s]*+";
    private static final String SEP_MANDATORY = "[[\\p{Punct}&&[^()]]\\s]++";

    // Name patterns where the show is present first. Examples below.
    private static final Pattern[] patternsShowFirst = {
            // almost anything that has S 00 E 00 in it and recognize shows with year as season number
            Pattern.compile("(.+?)" + SEP_MANDATORY + "(?:s|seas|season)" + SEP_OPTIONAL + "(20\\d{2}|\\d{1,2})" + SEP_OPTIONAL + "(?:e|ep|episode)" + SEP_OPTIONAL + "(\\d{1,3})(?!\\d).*", Pattern.CASE_INSENSITIVE),
            // almost anything that has 00 x 00, note mandatory separator to fixe detection of movies 5.1x264 as Season 1 episode 264
            Pattern.compile("(.+?)" + SEP_MANDATORY + "(20\\d{2}|\\d{1,2})" + SEP_OPTIONAL + "x" + SEP_MANDATORY + "(\\d{1,3})(?!\\d).*", Pattern.CASE_INSENSITIVE),
            // special case to avoid x264 or x265
            Pattern.compile("(.+?)" + SEP_MANDATORY + "(20\\d{2}|\\d{1,2})" + SEP_OPTIONAL + "x" + SEP_OPTIONAL + "(?!(?:264|265|720))(\\d{1,3})(?!\\d).*", Pattern.CASE_INSENSITIVE),
            // Disable following pattern since it makes L.627 or OSS 117 movies identified as TV serie
            // foo.103 and similar
            // Note: can detect movies that contain 3 digit numbers like "127 hours" or shows that have such numbers in their name like "zoey 101"
            // Limit first digit to be >0 in order not to identify "James Bond 007" as tv show
            //Pattern.compile("(.+)" + SEP_MANDATORY + "(?!(?:264|265|720))([1-9])(\\d{2,2})" + SEP_MANDATORY + ".*", Pattern.CASE_INSENSITIVE),
        };
    // Name patterns which begin with the number of the episode
    private static final Pattern[] patternsEpisodeFirst = {
            // anything that starts with S 00 E 00, text after "-" getting ignored
            Pattern.compile(SEP_OPTIONAL + "(?:s|seas|season)" + SEP_OPTIONAL + "(\\d{1,2})" + SEP_OPTIONAL + "(?:e|ep|episode)" + SEP_OPTIONAL + "(\\d{1,3})(?!\\d)" + SEP_OPTIONAL + "([^-]*+).*", Pattern.CASE_INSENSITIVE),
            // anything that starts with 00 x 00, text after "-" getting ignored like in "S01E15 - ShowName - Ignored - still ignored"
            Pattern.compile(SEP_OPTIONAL + "(\\d{1,2})" + SEP_OPTIONAL + "x" + SEP_OPTIONAL + "(\\d{1,3})(?!\\d)" + SEP_OPTIONAL + "([^-]*+).*", Pattern.CASE_INSENSITIVE),
        };

    public static String cleanUpName(String name) {
        name = ParseUtils.unifyApostrophes(name);
        name = ParseUtils.removeNumbering(name);
        name = ParseUtils.replaceAcronyms(name);
        name = StringUtils.replaceAllChars(name, REPLACE_ME, ' ');
        return name.trim();
    }

    /**
     *  Parse the filename and returns a Map containing the keys "show",
     *  "season" and "epnum" and their associated values.
     *  If the filename doesn't match a tv show pattern, returns null.
     */
    public static Map<String, String> parseShowName(String filename) {
        log.debug("parseShowName: " + filename);
        final HashMap<String, String> buffer = new HashMap<String, String>();
        Pair<String, String> nameYear;
        Pair<String, String> nameCountry;
        String name;
        for(Pattern regexp: patternsShowFirst) {
            Matcher matcher = regexp.matcher(filename);
            try {
                if(matcher.find()) {
                    nameYear = parenthesisYearExtractor(matcher.group(1));
                    // remove junk behind () that was containing year
                    // applies to movieName (1928) junk -> movieName () junk -> movieName
                    name = removeAfterEmptyParenthesis(nameYear.first);
                    name = cleanUpName(name);
                    nameCountry = getCountryOfOrigin(name);
                    log.debug("getMatch: patternsShowFirst " + nameCountry.first + " season " + matcher.group(2) + " episode " + matcher.group(3) + " year " + nameYear.second + " country " + nameCountry.second);
                    buffer.put(SHOW, nameCountry.first);
                    buffer.put(SEASON, matcher.group(2));
                    buffer.put(EPNUM, matcher.group(3));
                    buffer.put(YEAR, nameYear.second);
                    buffer.put(ORIGIN, nameCountry.second);
                    return buffer;
                }
            } catch (IllegalArgumentException ignored) {}
        }
        if (ENABLE_PATTERNS_EPISODE_FIRST)
            for(Pattern regexp: patternsEpisodeFirst) {
                Matcher matcher = regexp.matcher(filename);
                try {
                    if(matcher.find()) {
                        nameYear = parenthesisYearExtractor(matcher.group(3));
                        // remove junk behind () that was containing year
                        // applies to movieName (1928) junk -> movieName () junk -> movieName
                        name = removeAfterEmptyParenthesis(nameYear.first);
                        name = cleanUpName(name);
                        nameCountry = getCountryOfOrigin(name);
                        log.debug("getMatch: patternsEpisodeFirst " + nameCountry.first + " season " + matcher.group(1) + " episode " + matcher.group(2) + " year " + nameYear.second);
                        buffer.put(SHOW, nameCountry.first);
                        buffer.put(SEASON, matcher.group(1));
                        buffer.put(EPNUM, matcher.group(2));
                        buffer.put(YEAR, nameYear.second);
                        buffer.put(ORIGIN, nameCountry.second);
                        return buffer;
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        return null;
    }

    /**
     * Function to know if a given filename matches one of the patterns and as
     * such, can have show and episode number extracted.
     * Uses <b>searchString</b> instead of filename if present.
     */
    public static boolean isTvShow(Uri file, String searchString) {
        String filename;
        if (searchString != null && !searchString.isEmpty()) {
            filename = searchString;
        } else {
            if (file == null)
                return false;
            filename = FileUtils.getName(file);
        }
        // remove trailing '/' if it exists
        filename = removeTrailingSlash(filename);
        log.debug("isTvShow: parsing " + filename);
        for(Pattern regexp: patternsShowFirst) {
            Matcher m = regexp.matcher(filename);
            try {
                if(m.matches()) {
                    log.debug("isTvShow: match found " + regexp.toString());
                    return true;
                } else {
                    log.debug("isTvShow: match not found " + regexp.toString());
                }
            } catch (IllegalArgumentException ignored) {
                if (log.isDebugEnabled()) log.debug("isTvShow: IllegalArgumentException");
            }
        }
        if (ENABLE_PATTERNS_EPISODE_FIRST)
            for(Pattern regexp: patternsEpisodeFirst) {
            Matcher m = regexp.matcher(filename);
            try {
                if(m.matches())
                    return true;
            } catch (IllegalArgumentException ignored) {}
        }
        return false;
    }

    public static boolean isTvShow(String path) {
        return isTvShow(Uri.parse(path), null);
    }

    public static String urlEncode(String input) {
        String encode = "";
        try {
            encode = URLEncoder.encode(input, "UTF-8");
        } catch (UnsupportedEncodingException e1) {
            log.error(ShowUtils.class.getSimpleName(), "Error: " + e1, e1);
        }
        return encode;
    }


}
