// Copyright 2020 Courville Software
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

package com.archos.mediascraper.themoviedb3;

import com.archos.mediascraper.SearchResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class SearchParserResult {

    private static final Logger log = LoggerFactory.getLogger(SearchParserResult.class);

    List<SearchResult> resultsNoAirDate;
    List<SearchResult> resultsNoPoster;
    List<SearchResult> resultsProbable;
    List<SearchResult> resultsNoBanner;

    public SearchParserResult() {
        // contains list of results without air date
        this.resultsNoAirDate = new LinkedList<>();
        // contains list of results without banner
        this.resultsNoBanner = new LinkedList<>();
        // contains list of results without poster
        this.resultsNoPoster = new LinkedList<>();
        // contains list of probable results (i.e. with banner, poster, air date etc.) with its Levenshtein distance to cleaned filename
        this.resultsProbable = new LinkedList<>();
    }

    public static Comparator<SearchResult> comparator = (sr1, sr2) -> {
        // we want lowest levenshtein distance of title
        if (sr1.getLevenshteinDistance() != sr2.getLevenshteinDistance()) {
            return Integer.compare(sr1.getLevenshteinDistance(), sr2.getLevenshteinDistance());
        }
        Date date1 = sr1.getReleaseOrFirstAiredDate();
        Date date2 = sr2.getReleaseOrFirstAiredDate();
        if (shouldPreferOlderMovie(sr1, sr2, date1, date2)) {
            return Long.compare(date1.getTime(), date2.getTime());
        }
        Float pop1 = sr1.getPopularity();
        Float pop2 = sr2.getPopularity();
        // or highest popularity if it failed
        if (!Objects.equals(pop1, pop2)) {
            if (pop2 == null) return -1; // sr1 is considered less than sr2 i.e. to be considered first
            if (pop1 == null) return 1;  // sr2 is considered less than sr1 i.e. to be considered first
            return Float.compare(pop2, pop1);
        }
        // Or newest if it failed: first use releaseDate (movie) or firstAiredDate (show) and after year if it fails
        if (date1 == null && date2 == null) {
            //NB: year is a String not int
            if (sr1.getYear() != null && ! sr1.getYear().isEmpty() && sr2.getYear() != null && ! sr2.getYear().isEmpty())
                return sr2.getYear().compareTo(sr1.getYear());
            return 0;
        }
        if (date2 == null) return -1; // date1 is considered less than date2 (date1 has a year thus only consider this one)
        if (date1 == null) return 1;  // date2 is considered less than date1 (date2 has a year this only consider this one)
        return Long.compare(date1.getTime(), date2.getTime());
    };

    private static boolean shouldPreferOlderMovie(SearchResult sr1, SearchResult sr2, Date date1, Date date2) {
        if (!sr1.isMovie() || !sr2.isMovie() || date1 == null || date2 == null) return false;
        if (sr1.getYear() != null || sr2.getYear() != null) return false;

        String title1 = sr1.getTitle();
        String title2 = sr2.getTitle();
        if (title1 == null || title2 == null || !title1.equalsIgnoreCase(title2)) return false;

        String normalizedTitle = title1.replaceAll("[^\\p{L}\\p{N}]+", "");
        return normalizedTitle.length() >= 4 && !normalizedTitle.matches("\\d+");
    }

    public List<SearchResult> getResults(int maxItems) {
        List<SearchResult> results = new LinkedList<>();
        if (log.isDebugEnabled()) log.debug("getResults: resultsProbable.size()={}", resultsProbable.size());
        if (resultsProbable.size()>0)
            for (SearchResult sr : resultsProbable)
                if (maxItems < 0 || results.size() < maxItems)
                    results.add(sr);
        // skip videos without an air date only if resultsProbable is empty
        if (resultsNoAirDate.size()>0 && resultsProbable.size() == 0)
            for (SearchResult sr : resultsNoAirDate)
                if (maxItems < 0 || results.size() < maxItems)
                    results.add(sr);
        // do NOT skip videos without a banner but with a poster (otherwise shows like The Wrong Mans not found)
        if (resultsNoBanner.size()>0)
            for (SearchResult sr : resultsNoBanner)
                if (maxItems < 0 || results.size() < maxItems)
                    results.add(sr);
        // skip videos without a poster only if resultsProbable is empty
        if (resultsNoPoster.size()>0 && resultsProbable.size() == 0)
            for (SearchResult sr : resultsNoPoster)
                if (maxItems < 0 || results.size() < maxItems)
                    results.add(sr);
        if (log.isDebugEnabled()) log.debug("getResults: results.size()={}", results.size());
        return results;
    }
}
