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


package com.archos.mediascraper.themoviedb3;

import android.util.Log;

import com.archos.mediascraper.FileFetcher;
import com.archos.mediascraper.FileFetcher.FileFetchResult;
import com.archos.mediascraper.MovieTags;
import com.archos.mediascraper.ScrapeStatus;

import java.io.IOException;

/*
 * /3/movie/{id}
 *
 * !Getting just "overview" from no language version
 *
 * Required Parameters
 * api_key
 *
 * Optional Parameters
 * language             ISO 639-1 code.
 */
public class MovieIdDescription {
    private static final String TAG = MovieIdDescription.class.getSimpleName();
    private static final boolean DBG = false;

    private static final String METHOD = "movie";

    public static boolean addDescription(long movieId, MovieTags tag, FileFetcher fetcher) {

        if (tag == null)
            return false;

        FileFetchResult fileResult = getFile(fetcher, movieId);

        if (fileResult.status != ScrapeStatus.OKAY) {
            return false;
        }

        try {
            String description = MovieIdDescriptionParser.getInstance().readJsonFile(fileResult.file, null);
            if (description != null && !description.isEmpty())
                tag.setPlot(description);
            return true;
        } catch (IOException e) {
            if (DBG) Log.e(TAG, e.getMessage(), e);
        }
        return false;
    }

    private static FileFetchResult getFile(FileFetcher fetcher, long movieId) {
        String requestUrl = TheMovieDb3.buildUrl(null, METHOD + "/" + movieId);
        if (DBG) Log.d(TAG, "REQUEST: [" + requestUrl + "]");
        return fetcher.getFile(requestUrl);
    }
}
