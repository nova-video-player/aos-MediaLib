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

import java.text.Normalizer;
import java.util.Locale;

public class TvShowSearchInfo extends SearchInfo {

    /** package private, use {@link SearchPreprocessor} */
    TvShowSearchInfo(Uri file, String showName, int season, int episode) {
        super(file);
        mShowName = Normalizer.normalize(showName, Normalizer.Form.NFC);
        mSeason = season;
        mEpisode = episode;
    }

    private static final String FULL_FORMAT = "%s S%02dE%02d";
    private static final String SXEY_FORMAT = "S%02dE%02d";

    private final String mShowName;
    private final int mSeason;
    private final int mEpisode;

    public String getShowName() {
        return mShowName;
    }

    public int getSeason() {
        return mSeason;
    }

    public int getEpisode() {
        return mEpisode;
    }

    @Override
    protected String createSearchSuggestion() {
        return String.format(Locale.ROOT, FULL_FORMAT, mShowName, mSeason, mEpisode);
    }

    public String getEpisodeCode() {
        return String.format(Locale.ROOT, SXEY_FORMAT, mSeason, mEpisode);
    }

    @Override
    public boolean isTvShow() {
        return true;
    }

}
