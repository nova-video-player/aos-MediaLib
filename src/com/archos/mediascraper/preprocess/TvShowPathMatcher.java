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


package com.archos.mediascraper.preprocess;

import android.net.Uri;
import android.util.Pair;

import com.archos.mediascraper.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.archos.mediascraper.ShowUtils.cleanUpName;
import static com.archos.mediascraper.preprocess.ParseUtils.getCountryOfOrigin;
import static com.archos.mediascraper.preprocess.ParseUtils.parenthesisYearExtractor;
import static com.archos.mediascraper.preprocess.ParseUtils.yearExtractorEndString;

/**
 * Matches Tv Shows in folders like
 * <code>/Galactica/Season 1/galactica.ep3.avi</code><p>
 * see <a href="http://wiki.xbmc.org/index.php?title=Video_library/Naming_files/TV_shows">XBMC Docu</a>
 * <p>
 * Name may only contain Alphanumerics separated by spaces
 */
class TvShowPathMatcher implements InputMatcher {
    private static final Logger log = LoggerFactory.getLogger(TvShowPathMatcher.class);

    // pattern that allows
    // "stuff / [words/numbers] / Season XX / random stuff Episode XX random stuff"
    //           ^ show title            ^ season                  ^ episode
    // e.g. "/series/Galactica/Season 1/galactica.ep3.avi"

    // TODO match year too...
    // https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html
    // https://www.regular-expressions.info/unicode.html
    // check https://regex101.com/r/hE9gB4/1
    // (?i) match case insensitive
    // ?: nothing but
    // \p{L} all letters even accents \p{N} numerical \s whitespace (instead of \w)
    // better lazy .+? than greedy .+ vs. .* or .*?
    // ++ one or more, *+ zero or more
    // negative look behind (?<![\p{L}]) previous letter was not a Letter
    // (?i).*\/((?:[\p{L}\p{N}]++[\s._-]*+)++)\/[^\/]*?(?<![\p{L}])(?:S|SEAS|SEASON)[\s._-]*+(\d{1,2})(?!\d)[^\/]*+\/[^\/]*?(?<![\p{L}])(?:E|EP|EPISODE)[\s._-]*+(\d{1,2})(?!\d)[^\/]*+
    private static final String SEP_OPTIONAL = "[[\\p{Punct}&&[^()]]\\s]*+";
    private static final String SEP_MANDATORY = "[[\\p{Punct}&&[^()]]\\s]++";
    private static final String NOT_SLASH_LAZY = "[^/]*?"; // lazy
    private static final String NOT_SLASH_GREEDY = "[^/]*+"; // lazy
    private static final String PREVIOUS_NOT_LETTER = "(?<!\\p{L})";
    private static final String CASE_INSENSITIVE = "(?i)";
    private static final String WHATEVER = ".*";
    private static final String SLASH = "/";
    private static final String SEASON = "(?:S|SEAS|SEASON)";
    private static final String EPISODE = "(?:E|EP|EPISODE)";
    private static final String SEASON_NUMBER = "20\\d{2}|\\d{1,2}";
    // for episodes beyond 999 (e.g. 1000) we need to allow 4 digits but force the first digit to be 1 to avoid year matching
    private static final String EPISODE_NUMBER = "1?\\d{1,3}";
    private static final String NOT_DECIMAL = "(?!\\d).*";
    private static final String LETTER_NUMBER_SEP = "(?:[\\p{L}\\p{N}]++[\\s._-]*+)"; // contains no date between ()...

    // Compared to above use of NOT_DECIMAL "(?!\\d).*" which is adding .* at the end compared to orignal string
    private static final String SHOW_SEASON_EPISODE_PATH =
            CASE_INSENSITIVE + WHATEVER + SLASH + "(" + LETTER_NUMBER_SEP + "++" + ")" + SLASH +
                    NOT_SLASH_LAZY + PREVIOUS_NOT_LETTER + SEASON + SEP_OPTIONAL + "(" + SEASON_NUMBER + ")" +
                    NOT_DECIMAL + NOT_SLASH_GREEDY + SLASH + NOT_SLASH_LAZY + PREVIOUS_NOT_LETTER + EPISODE +
                    SEP_OPTIONAL + "(" + EPISODE_NUMBER + ")" + NOT_DECIMAL + NOT_SLASH_GREEDY;

    private static final Pattern PATTERN_ = Pattern.compile(SHOW_SEASON_EPISODE_PATH);

    public static TvShowPathMatcher instance() {
        return INSTANCE;
    }

    private static final TvShowPathMatcher INSTANCE =
            new TvShowPathMatcher();

    private TvShowPathMatcher() {
        // singleton
    }

    @Override
    public boolean matchesFileInput(Uri fileInput, Uri simplifiedUri) {
        log.debug("matchesFileInput: processing " + ((fileInput != null) ? fileInput.getPath() : null) + " and " + ((simplifiedUri != null) ? simplifiedUri.getPath() : null));
        return PATTERN_.matcher(fileInput.toString()).matches();
    }

    @Override
    public boolean matchesUserInput(String userInput) {
        return false;
    }

    @Override
    public SearchInfo getFileInputMatch(Uri file, Uri simplifiedUri) {
        log.debug("getFileInputMatch: processing " + ((file != null) ? file.getPath() : null));
        Matcher matcher = PATTERN_.matcher(file.toString());
        if (matcher.matches()) {
            String showName = ParseUtils.removeInnerAndOutterSeparatorJunk(matcher.group(1));
            Pair<String, String> nameYear = parenthesisYearExtractor(showName);
            String name = cleanUpName(nameYear.first);
            Pair<String, String>  nameCountry = getCountryOfOrigin(name);
            int season = StringUtils.parseInt(matcher.group(2), 0);
            int episode = StringUtils.parseInt(matcher.group(3), 0);
            String year = nameYear.second;
            if (year == null || year.isEmpty()) { // if year empty perhaps this is Eric.2024-s01e01, find year in the end of the string
                nameYear = yearExtractorEndString(nameCountry.first);
                if (nameYear.first != null && ! nameYear.first.isEmpty()) { // do it only if the remaining name is not empty
                    name = nameYear.first;
                    year = nameYear.second;
                }
            }
            log.debug("getFileInputMatch: " + name + " season " + season + " episode " + episode + " year " + year + " country " + nameCountry.second);
            return new TvShowSearchInfo(file, name, season, episode, year, nameCountry.second);
        } else {
            log.debug("getFileInputMatch: no match");
        }
        return null;
    }

    @Override
    public SearchInfo getUserInputMatch(String userInput, Uri file) {
        return null;
    }

    @Override
    public String getMatcherName() {
        return "TvShowPath";
    }

}
