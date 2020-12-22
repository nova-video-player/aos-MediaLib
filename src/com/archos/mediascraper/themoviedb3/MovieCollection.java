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

import android.util.Log;

import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.xml.MovieScraper3;
import com.uwetrottmann.tmdb2.entities.Collection;
import com.uwetrottmann.tmdb2.services.CollectionsService;

import java.io.IOException;

import retrofit2.Response;

// Get the movie collection information for a specific collection id and language (ISO 639-1 code)
public class MovieCollection {
    private static final String TAG = MovieCollection.class.getSimpleName();
    private static final boolean DBG = true;

    public static CollectionResult getInfo(long collectionId, String language, CollectionsService collectionsService) {
        CollectionResult myResult = new CollectionResult();
        Response<Collection> collectionResponse = null;
        CollectionInfo parserResult = null;

        if (DBG) Log.d(TAG, "getInfo: quering tmdb for collectionId " + collectionId + " in " + language);
        try {
            collectionResponse = collectionsService.summary((int) collectionId, language).execute();
            switch (collectionResponse.code()) {
                case 401: // auth issue
                    if (DBG) Log.d(TAG, "search: auth error");
                    myResult.status = ScrapeStatus.AUTH_ERROR;
                    MovieScraper3.reauth();
                    return myResult;
                case 404: // not found
                    myResult.status = ScrapeStatus.NOT_FOUND;
                    // fallback to english if no result
                    if (!language.equals("en")) {
                        if (DBG) Log.d(TAG, "getInfo: retrying search for movieId " + collectionId + " in en");
                        return getInfo(collectionId, "en", collectionsService);
                    }
                    if (DBG) Log.d(TAG, "getInfo: collectionId " + collectionId + " not found");
                    break;
                default:
                    if (collectionResponse.isSuccessful()) {
                        if (collectionResponse.body() != null) {
                            parserResult = MovieCollectionParser.getResult(collectionResponse.body());
                            myResult.collectionInfo = parserResult;
                            myResult.status = ScrapeStatus.OKAY;
                        } else {
                            myResult.status = ScrapeStatus.NOT_FOUND;
                        }
                    } else { // an error at this point is PARSER related
                        if (DBG) Log.d(TAG, "getBaseInfo: error " + collectionResponse.code());
                        myResult.status = ScrapeStatus.ERROR_PARSER;
                    }
                    break;
            }
        } catch (IOException e) {
            Log.e(TAG, "getInfo: caught IOException getting summary for movieId=" + collectionId);
            myResult.status = ScrapeStatus.ERROR_PARSER;
            myResult.reason = e;
        }
        return myResult;
    }
}
