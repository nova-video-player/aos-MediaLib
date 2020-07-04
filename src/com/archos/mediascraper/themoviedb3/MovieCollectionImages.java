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

// Get movie collection images (posters and backdrops) for a specific movie id and language (ISO 939-1 code)
public class MovieCollectionImages {
    private static final String TAG = MovieCollectionImages.class.getSimpleName();
    private static final boolean DBG = false;

    public static void downloadCollectionImage(MovieTags tag,
                                         ImageConfiguration.PosterSize posterFullSize,
                                         ImageConfiguration.PosterSize posterThumbSize,
                                         ImageConfiguration.BackdropSize backdropFullSize,
                                         ImageConfiguration.BackdropSize backdropThumbSize,
                                         String nameSeed, Context context) {
        if (tag.getCollectionId() != -1) {
            String path = tag.getCollectionPosterPath();
            if (DBG) Log.d(TAG, "getResult: treating collection poster  " + path);
            String fullUrl = ImageConfiguration.getUrl(path, posterFullSize);
            String thumbUrl = ImageConfiguration.getUrl(path, posterThumbSize);
            ScraperImage image = new ScraperImage(ScraperImage.Type.COLLECTION_POSTER, nameSeed);
            image.setLargeUrl(fullUrl);
            image.setThumbUrl(thumbUrl);
            image.generateFileNames(context);
            image.download(context);
            tag.setCollectionPosterLargeFile(image.getLargeFile());
            tag.setCollectionPosterLargeUrl(fullUrl);
            tag.setCollectionPosterThumbFile(image.getThumbFile());
            tag.setCollectionPosterThumbUrl(thumbUrl);

            path = tag.getCollectionBackdropPath();
            if (DBG) Log.d(TAG, "getResult: treating collection backdrop " + path);
            fullUrl = ImageConfiguration.getUrl(path, backdropFullSize);
            thumbUrl = ImageConfiguration.getUrl(path, backdropThumbSize);
            image = new ScraperImage(ScraperImage.Type.COLLECTION_BACKDROP, nameSeed);
            image.setLargeUrl(fullUrl);
            image.setThumbUrl(thumbUrl);
            image.generateFileNames(context);
            image.download(context);
            tag.setCollectionBackdropLargeFile(image.getLargeFile());
            tag.setCollectionBackdropLargeUrl(fullUrl);
            tag.setCollectionBackdropThumbFile(image.getThumbFile());
            tag.setCollectionBackdropThumbUrl(thumbUrl);
        }
    }
}
