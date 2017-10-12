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

import com.archos.mediascraper.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Matches Tv Shows in folders like
 * <code>/Galactica/Season 1/galactica.ep3.avi</code><p>
 * see <a href="http://wiki.xbmc.org/index.php?title=Video_library/Naming_files/TV_shows">XBMC Docu</a>
 * <p>
 * Name may only contain Alphanumerics separated by spaces
 */
class TvShowPathMatcher implements InputMatcher {

    // pattern that allows
    // "stuff / [words/numbers] / Season XX / random stuff Episode XX random stuff"
    //           ^ show title            ^ season                  ^ episode
    // e.g. "/series/Galactica/Season 1/galactica.ep3.avi"

    private static final String SHOW_SEASON_EPISODE_PATH =
             "(?i).*/((?:[\\p{L}\\p{N}]++[\\s._-]*+)++)/[^/]*?(?<![\\p{L}])(?:S|SEAS|SEASON)[\\s._-]*+(\\d{1,2})(?!\\d)[^/]*+/[^/]*?(?<![\\p{L}])(?:E|EP|EPISODE)[\\s._-]*+(\\d{1,2})(?!\\d)[^/]*+";
    private static final Pattern PATTERN_ =
            Pattern.compile(SHOW_SEASON_EPISODE_PATH);

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
        if(simplifiedUri!=null)
            fileInput = simplifiedUri;
        return PATTERN_.matcher(fileInput.toString()).matches();
    }

    @Override
    public boolean matchesUserInput(String userInput) {
        return false;
    }

    @Override
    public SearchInfo getFileInputMatch(Uri file, Uri simplifiedUri) {
        if(simplifiedUri!=null)
            file = simplifiedUri;
        Matcher matcher = PATTERN_.matcher(file.toString());
        if (matcher.matches()) {
            String showName = ParseUtils.removeInnerAndOutterSeparatorJunk(matcher.group(1));
            int season = StringUtils.parseInt(matcher.group(2), 0);
            int episode = StringUtils.parseInt(matcher.group(3), 0);
            return new TvShowSearchInfo(file, showName, season, episode);
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
