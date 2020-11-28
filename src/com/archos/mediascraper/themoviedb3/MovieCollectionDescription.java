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

import com.archos.mediascraper.MovieTags;
import com.archos.mediascraper.xml.MovieScraper3;
import com.uwetrottmann.tmdb2.entities.Collection;
import com.uwetrottmann.tmdb2.services.CollectionsService;

import java.io.IOException;

import retrofit2.Response;

// set overview for collectionId in english
// return boolean for success
public class MovieCollectionDescription {
    private static final String TAG = MovieCollectionDescription.class.getSimpleName();
    private static final boolean DBG = false;

    public static boolean addDescription(long collectionId, MovieTags tag, CollectionsService collectionsService) {
        if (tag == null)
            return false;
        Response<Collection> collectionResponse = null;
        try {
            collectionResponse = collectionsService.summary((int) collectionId, "en").execute();
            switch (collectionResponse.code()) {
                case 401: // auth issue
                    if (DBG) Log.d(TAG, "search: auth error");
                    MovieScraper3.reauth();
                    return false;
                case 404: // not found
                    if (DBG) Log.d(TAG, "addDescription: movieId " + collectionId + " not found");
                    return false;
                default:
                    if (collectionResponse.isSuccessful()) {
                        if (collectionResponse.body() != null) {
                            String description = collectionResponse.body().overview;
                            if (description != null && !description.isEmpty())
                                tag.setCollectionDescription(description);
                            return true;
                        } else
                            return false;
                    } else // an error at this point is PARSER related
                        return false;
            }
        } catch (IOException e) {
            Log.e(TAG, "addDescription: caught IOException getting summary for movieId=" + collectionId);
            return false;
        }
    }
}
