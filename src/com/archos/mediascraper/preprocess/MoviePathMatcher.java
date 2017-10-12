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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Matches Movies in folders like
 * <code>/Moviename (1996)/movie.avi</code>
 * <p>
 * see <a href="http://wiki.xbmc.org/index.php?title=Video_library/Naming_files/Movies">XBMC Docu</a>
 * <p>
 * may only contain text numbers and spaces followed by the year in brackets
 * everything after those brackets is ignored. The actual filename is also ignored.
 */
class MoviePathMatcher implements InputMatcher {

    // rather strict pattern that allows
    // "stuff / [space separated words/numbers] (year) random stuff / random stuff"
    // e.g. "/movies/Titanic (2001)/movie.avi"
    //      "/movies/Transformers (2009) [720p]/lala.mkv"
    //      "/movies/Th3 L33tspe4k (2009) [720p]/lala.mkv"
    // does not match
    //      "/movies/Transformers.2001/movie.avi"
    // will unfortunately match -> catch that before
    //      "/series/The A Team S01E02 (1978)/lala.avi

    private static final String MOVIE_YEAR_PATH =
            ".*/((?:[\\p{L}\\p{N}]++\\s*+)++)\\(((?:19|20)\\d{2})\\)[^/]*+/[^/]++";
    private static final Pattern PATTERN_ =
            Pattern.compile(MOVIE_YEAR_PATH);

    public static MoviePathMatcher instance() {
        return INSTANCE;
    }

    private static final MoviePathMatcher INSTANCE =
            new MoviePathMatcher();

    private MoviePathMatcher() {
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
            String name = ParseUtils.removeInnerAndOutterSeparatorJunk(matcher.group(1));
            String year = matcher.group(2);
            return new MovieSearchInfo(file, name, year);
        }
        return null;
    }

    @Override
    public SearchInfo getUserInputMatch(String userInput, Uri file) {
        return null;
    }

    @Override
    public String getMatcherName() {
        return "MoviePath";
    }

}
