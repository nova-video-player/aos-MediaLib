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
 * Matches if the file is a VIDEO_TS or VTS_xx_xx.VOB file and returns the parent path
 * returned result is set for re-parsing
 */
class MovieDVDMatcher implements InputMatcher {

    public static MovieDVDMatcher instance() {
        return INSTANCE;
    }

    private static final MovieDVDMatcher INSTANCE =
            new MovieDVDMatcher();

    private MovieDVDMatcher() {
        // singleton
    }

    @Override
    public boolean matchesFileInput(Uri fileInput, Uri simplifiedUri) {
        if(simplifiedUri!=null)
            fileInput = simplifiedUri;
        return DVD_PATH_PATTERN.matcher(fileInput.toString()).matches();
    }

    @Override
    public boolean matchesUserInput(String userInput) {
        // matches only on file input
        return false;
    }

    @Override
    public SearchInfo getFileInputMatch(Uri file, Uri simplifiedUri) {
        if(simplifiedUri!=null)
            file = simplifiedUri;
        Matcher matcher = DVD_PATH_PATTERN.matcher(file.toString());
        if (matcher.matches()) {
            SearchInfo result = new MovieSearchInfo(file, matcher.group(1), null);
            // we only extract the folder name, parse that one to see if it can be parsed better
            result.setForceReParse(true);
            return result;
        }
        return null;
    }

    @Override
    public SearchInfo getUserInputMatch(String userInput, Uri file) {
        // only working for file input
        return null;
    }

    @Override
    public String getMatcherName() {
        return "MovieDVD";
    }

    // matches if a path ends with /vts_00_0.vob or /video_ts.vob - capture group is the parent path
    // ignoring potential video_ts folders. Also has to be at least 1 folder deep
    // yes: smb://192.168.0.1/[Extracted]/VIDEO_TS/VTS_01_1.VOB
    // yes: smb://192.168.0.1/[Extracted]/VTS_01_1.VOB
    // no : smb://192.168.0.1/VIDEO_TS/VTS_01_1.VOB -- not 1 folder deep
    // no : smb://192.168.0.1/VTS_01_1.VOB -- not 1 folder deep
    private static final String DVD_PATH =
            "(?i).*?/[^/]+/(?!VIDEO_TS)([^/]+)/(?:VIDEO_TS/)?(?:VTS_\\d\\d_\\d|VIDEO_TS)\\.VOB";
    private static final Pattern DVD_PATH_PATTERN = Pattern.compile(DVD_PATH);

}
