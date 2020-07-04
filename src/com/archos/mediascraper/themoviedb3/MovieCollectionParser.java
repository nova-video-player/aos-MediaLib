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

import android.content.Context;
import android.util.Log;

import com.archos.mediascraper.MovieTags;
import com.archos.mediascraper.ScraperImage;
import com.uwetrottmann.tmdb2.entities.BaseCompany;
import com.uwetrottmann.tmdb2.entities.CastMember;
import com.uwetrottmann.tmdb2.entities.Collection;
import com.uwetrottmann.tmdb2.entities.Credits;
import com.uwetrottmann.tmdb2.entities.CrewMember;
import com.uwetrottmann.tmdb2.entities.Genre;
import com.uwetrottmann.tmdb2.entities.Image;
import com.uwetrottmann.tmdb2.entities.Images;
import com.uwetrottmann.tmdb2.entities.Movie;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class MovieCollectionParser {

    private static final String TAG = MovieCollectionParser.class.getSimpleName();
    private static final boolean DBG = false;

    public static CollectionInfo getResult(Collection collection) {

        CollectionInfo result = new CollectionInfo();
        if (collection.id != null) result.id = collection.id;
        if (collection.name != null) result.name = collection.name;
        if (collection.overview != null) result.description = collection.overview;
        if (collection.poster_path != null) result.poster = collection.poster_path;
        if (collection.backdrop_path != null) result.backdrop = collection.backdrop_path;

        if (DBG) Log.d(TAG, "getResult collection id: " + collection.id + ", for " + collection.name + ", with description " + collection.overview);

        return result;
    }
}
