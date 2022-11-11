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

import android.util.Pair;

import com.archos.mediascraper.SearchResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

public class SearchParserResult {

    private static final Logger log = LoggerFactory.getLogger(SearchParserResult.class);

    List<SearchResult> resultsNoAirDate;
    List<SearchResult> resultsNoPoster;
    List<Pair<SearchResult,Integer>> resultsProbable;
    List<Pair<SearchResult,Integer>> resultsNoBanner;

    public SearchParserResult() {
        this.resultsNoAirDate = new LinkedList<>();
        // contains list of results without banner
        this.resultsNoBanner = new LinkedList<>();
        // contains list of results without poster
        this.resultsNoPoster = new LinkedList<>();
        // contains list of probable results (i.e. with banner and non numeric slug) with its Levenshtein distance to cleaned filename
        this.resultsProbable = new LinkedList<>();
    }

    public List<SearchResult> getResults(int maxItems) {
        List<SearchResult> results = new LinkedList<>();
        log.debug("getResults: resultsProbable.size()=" + resultsProbable.size());
        if (resultsProbable.size()>0)
            for (Pair<SearchResult,Integer> pair : resultsProbable)
                if (maxItems < 0 || results.size() < maxItems)
                    results.add(pair.first);
        // skip videos without a poster
        /*
        if (resultsNoPoster.size()>0)
            for (SearchResult result : resultsNoPoster)
                if (maxItems < 0 || results.size() < maxItems)
                    results.add(result);
         */
        // do NOT skip videos without a banner (otherwise shows like The Wrong Mans not found)
        if (resultsNoBanner.size()>0)
            for (Pair<SearchResult,Integer> pair : resultsNoBanner)
                if (maxItems < 0 || results.size() < maxItems)
                    results.add(pair.first);
        log.debug("getResults: results.size()=" + results.size());
        return results;
    }
}
