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

import com.archos.filecorelibrary.FileUtils;
import com.archos.mediascraper.ShowUtils;
import com.archos.mediascraper.StringUtils;

import java.util.Map;

/**
 * Matches all sorts of "Tv Show title S01E01" and similar things
 */
class TvShowMatcher implements InputMatcher {

    public static TvShowMatcher instance() {
        return INSTANCE;
    }

    private static final TvShowMatcher INSTANCE =
            new TvShowMatcher();

    protected TvShowMatcher() {
        // singleton
    }

    @Override
    public boolean matchesFileInput(Uri fileInput, Uri simplifiedUri) {
        if(simplifiedUri!=null)
            fileInput = simplifiedUri;
        return ShowUtils.isTvShow(fileInput, null);
    }

    @Override
    public boolean matchesUserInput(String userInput) {
        return ShowUtils.isTvShow(null, userInput);
    }

    @Override
    public SearchInfo getFileInputMatch(Uri file, Uri simplifiedUri) {
        if(simplifiedUri!=null)
            file = simplifiedUri;
        return getMatch(FileUtils.getName(file), file);
    }

    @Override
    public SearchInfo getUserInputMatch(String userInput, Uri file) {
        return getMatch(userInput, file);
    }

    private static SearchInfo getMatch(String matchString, Uri file) {
        Map<String, String> showName = ShowUtils.parseShowName(matchString);
        if (showName != null) {
            String showTitle = showName.get(ShowUtils.SHOW);
            String season = showName.get(ShowUtils.SEASON);
            String episode = showName.get(ShowUtils.EPNUM);
            int seasonInt = StringUtils.parseInt(season, 0);
            int episodeInt = StringUtils.parseInt(episode, 0);
            return new TvShowSearchInfo(file, showTitle, seasonInt, episodeInt);
        }
        return null;
    }

    @Override
    public String getMatcherName() {
        return "TVShow";
    }

}
