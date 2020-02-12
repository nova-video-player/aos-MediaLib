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

package com.archos.mediascraper.thetvdb;

import android.content.Context;
import android.util.Log;

import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.ShowTags;
import com.archos.mediascraper.xml.ShowScraper3;
import com.uwetrottmann.thetvdb.entities.SeriesResponse;

import java.io.IOException;

import retrofit2.Response;

// Get the basic show information for a specific movie id and language (ISO 639-1 code)
public class ShowId {
    private static final String TAG = ShowId.class.getSimpleName();
    private static final boolean DBG = false;

    public static ShowIdResult getBaseInfo(int showId, String language, boolean basicShow, boolean basicEpisode, MyTheTVdb theTvdb, Context context) {
        ShowIdResult myResult = new ShowIdResult();
        ShowTags parserResult = null;

        if (DBG) Log.d(TAG, "getBaseInfo: quering thetvdb for showId " + showId + " in " + language);
        try {
            Response<SeriesResponse> seriesResponse = theTvdb.series().series(showId, language).execute();
            switch (seriesResponse.code()) {
                case 401: // auth issue
                    if (DBG) Log.d(TAG, "search: auth error");
                    myResult.status = ScrapeStatus.AUTH_ERROR;
                    ShowScraper3.reauth();
                    return myResult;
                case 404: // not found
                    myResult.status = ScrapeStatus.NOT_FOUND;
                    // fallback to english if no result
                    if (!language.equals("en")) {
                        if (DBG) Log.d(TAG, "getBaseInfo: retrying search for showId " + showId + " in en");
                        return getBaseInfo(showId, "en", basicShow, basicEpisode, theTvdb, context);
                    }
                    if (DBG) Log.d(TAG, "getBaseInfo: movieId " + showId + " not found");
                    break;
                default:
                    if (seriesResponse.isSuccessful()) {
                        if (seriesResponse.body() != null) {
                            parserResult = ShowIdParser.getResult(seriesResponse.body(), context);
                            myResult.tag = parserResult;
                            myResult.status = ScrapeStatus.OKAY;
                        } else {
                            myResult.status = ScrapeStatus.NOT_FOUND;
                        }
                    } else { // an error at this point is PARSER related
                        if (DBG) Log.d(TAG, "getBaseInfo: error " + seriesResponse.code());
                        myResult.status = ScrapeStatus.ERROR_PARSER;
                    }
                    break;
            }
        } catch (IOException e) {
            Log.e(TAG, "getBaseInfo: caught IOException getting summary for showId=" + showId);
            myResult.status = ScrapeStatus.ERROR_PARSER;
            myResult.reason = e;
        }
        return myResult;
    }
}
