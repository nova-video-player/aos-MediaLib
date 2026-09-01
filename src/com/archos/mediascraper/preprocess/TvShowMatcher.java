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

import com.archos.filecorelibrary.FileUtils;
import com.archos.mediascraper.ShowUtils;
import com.archos.mediascraper.StringUtils;

import java.util.Calendar;
import java.util.Map;
import java.util.regex.Pattern;

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

    // Matches a season marker embedded anywhere in a segment (e.g. the ".S01" in "神雕侠侣.1995.S01"),
    // as opposed to isSeasonFolder below which requires the whole segment to be a season marker.
    private static final Pattern SEASON_TOKEN_ANYWHERE =
            Pattern.compile("(?i)(?:^|[\\s._-])(?:s|seas|season|saison)[\\s._-]*\\d+");

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
            String year = showName.get(ShowUtils.YEAR);
            String countryOfOrigin = showName.get(ShowUtils.ORIGIN);

            if (year == null && file != null) {
                java.util.List<String> segments = file.getPathSegments();
                if (segments != null) {
                    int size = segments.size();
                    int currentYear = Calendar.getInstance().get(Calendar.YEAR);

                    // Pass 1: find a dedicated season folder (e.g. "S02", "Season 2") and remember its
                    // position. Only the segment immediately above it (its parent, i.e. the show folder,
                    // e.g. ".../DuckTales (1987)/S02 (1988)/...") gets to inherit trust from it - not
                    // every segment in the scanned window. Otherwise an unrelated ancestor further up
                    // (e.g. "download-2026" above "show/S02/episode.mkv") could wrongly borrow the
                    // season folder's evidence even though it's not part of the show's own directory.
                    int seasonFolderDepth = -1;
                    for (int i = 2; i <= 4; i++) {
                        if (size - i >= 0) {
                            String segment = segments.get(size - i);
                            if (segment.matches("(?i)^(?:s|seas|season|saison)[\\s._-]*\\d+.*")) {
                                seasonFolderDepth = i;
                                break;
                            }
                        }
                    }
                    int showFolderDepth = seasonFolderDepth >= 0 ? seasonFolderDepth + 1 : -1;

                    // Pass 2: only trust a year extracted from a segment if there is clear evidence
                    // it belongs to the show's own directory structure: either the segment itself embeds
                    // a season marker along with the year (e.g. "神雕侠侣.1995.S01"), it's the season
                    // folder itself, or it's the season folder's immediate parent (the show folder).
                    // This rejects generic organizational folders like "downloads-2026" that merely
                    // contain a year with no season context, instead of relying on TMDb's search
                    // ranking to recover from a spurious year filter.
                    String seasonYear = null;
                    for (int i = 2; i <= 4; i++) {
                        if (size - i >= 0) {
                            String segment = segments.get(size - i);
                            boolean isSeasonFolder = i == seasonFolderDepth;
                            boolean isShowFolder = i == showFolderDepth;
                            boolean hasEmbeddedSeasonToken = SEASON_TOKEN_ANYWHERE.matcher(segment).find();
                            if (isSeasonFolder || isShowFolder || hasEmbeddedSeasonToken) {
                                Pair<String, String> p = ParseUtils.extractYearAnywhere(segment, currentYear);
                                if (p.second != null && ParseUtils.isValidYear(p.second)) {
                                    if (isSeasonFolder) {
                                        if (seasonYear == null) seasonYear = p.second;
                                    } else {
                                        year = p.second;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    if (year == null && seasonYear != null) {
                        year = seasonYear;
                    }
                }
            }

            int seasonInt = StringUtils.parseInt(season, 0);
            int episodeInt = StringUtils.parseInt(episode, 0);
            return new TvShowSearchInfo(file, showTitle, seasonInt, episodeInt, year, countryOfOrigin);
        }
        return null;
    }

    @Override
    public String getMatcherName() {
        return "TVShow";
    }

}
