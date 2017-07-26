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

import android.util.Log;

import java.util.Collections;
import java.util.List;

public class ScrapeSearchResult extends ScrapeResult {
    private static final String TAG = ScrapeSearchResult.class.getSimpleName();

    public final List<SearchResult> results;
    public final boolean isMovie;

    public ScrapeSearchResult(List<SearchResult> results, boolean isMovie, ScrapeStatus status, Throwable reason) {
        super(checkStatus(results, status), reason);
        this.results = results != null ? results : Collections.<SearchResult>emptyList();
        this.isMovie = isMovie;
    }

    /**
     * Checks that status matches the state of the results list.
     * Only a non-empty list of search results is okay, anything else is an error.
     **/
    private static ScrapeStatus checkStatus(List<SearchResult> results, ScrapeStatus status) {
        // if status is already some error keep it that way
        if (status != ScrapeStatus.OKAY)
            return status;
        // if status is okay but results list is null something went really wrong
        if (results == null) {
            Exception e = new Exception("status OKAY although result is null");
            e.fillInStackTrace();
            Log.w(TAG, e);
            return ScrapeStatus.ERROR;
        }
        if (results.isEmpty())
            return ScrapeStatus.NOT_FOUND;
        return ScrapeStatus.OKAY;
    }
}
