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

import android.os.Bundle;
import android.util.Log;

public class ScrapeDetailResult extends ScrapeResult {
    public static final String TAG = ScrapeDetailResult.class.getSimpleName();

    public final BaseTags tag;
    public final boolean isMovie;
    public final Bundle extras;

    public ScrapeDetailResult(BaseTags tag, boolean isMovie, Bundle extras, ScrapeStatus status, Throwable reason) {
        super(checkStatus(tag, status), reason);
        this.tag = tag;
        this.isMovie = isMovie;
        this.extras = extras != null ? extras : Bundle.EMPTY;
    }

    /**
     * Checks that status matches the state of the results.
     * Only a non-empty list of search results is okay, anything else is an error.
     **/
    private static ScrapeStatus checkStatus(BaseTags tag, ScrapeStatus status) {
        // if status is already some error keep it that way
        if (status != ScrapeStatus.OKAY)
            return status;
        // if status is okay but result is null something went really wrong
        if (tag == null) {
            Exception e = new Exception("status OKAY although result is null");
            e.fillInStackTrace();
            Log.w(TAG, e);
            return ScrapeStatus.ERROR;
        }
        return ScrapeStatus.OKAY;
    }
}
