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

import android.content.Context;
import android.util.Log;

import com.archos.mediascraper.FileFetcher;
import com.archos.mediascraper.MovieTags;
import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.FileFetcher.FileFetchResult;
import com.archos.mediascraper.ScraperImage;
import com.archos.mediascraper.ScraperImage.Type;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/*
 * 3/movie/{id}/images
 *
 * Get the images (posters and backdrops) for a specific movie id.
 *
 * Required Parameters
 * api_key
 *
 * Optional Parameters
 * language            ISO 639-1 code.
 */
public class MovieIdImages {
    private static final String TAG = MovieIdImages.class.getSimpleName();
    private static final boolean DBG = false;

    private static final String METHOD = "movie";
    private static final String SUBMETHOD = "images";

    public static boolean addImages(long movieId, MovieTags tag, String language,
            ImageConfiguration.PosterSize posterFullSize,
            ImageConfiguration.PosterSize posterThumbSize,
            ImageConfiguration.BackdropSize backdropFullSize,
            ImageConfiguration.BackdropSize backdropThumbSize,
            String nameSeed,
            FileFetcher fetcher,
            Context context) {

        if (tag == null)
            return false;
        FileFetchResult fileResult = getFile(fetcher, movieId);

        if (fileResult.status != ScrapeStatus.OKAY) {//first failure, try again
            try {
                Thread.sleep(3000);//when requesting immediately after failure, it fails again.
                                   // 2500ms had still some failure
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
                fileResult = getFile(fetcher, movieId);
            if (fileResult.status != ScrapeStatus.OKAY) {
                return false;

            }

        }

        MovieIdImagesResult parserResult = null;
        try {
            parserResult = MovieIdImageParser.getInstance().readJsonFile(fileResult.file, language);
            List<ScraperImage> posters = new ArrayList<ScraperImage>(parserResult.posterPaths.size());
            for (String path : parserResult.posterPaths) {
                String fullUrl = ImageConfiguration.getUrl(path, posterFullSize);
                String thumbUrl = ImageConfiguration.getUrl(path, posterThumbSize);
                ScraperImage image = new ScraperImage(Type.MOVIE_POSTER, nameSeed);
                image.setLargeUrl(fullUrl);
                image.setThumbUrl(thumbUrl);
                image.generateFileNames(context);
                posters.add(image);
            }
            tag.setPosters(posters);
            List<ScraperImage> backdrops = new ArrayList<ScraperImage>(parserResult.backdropPaths.size());
            for (String path : parserResult.backdropPaths) {
                String fullUrl = ImageConfiguration.getUrl(path, backdropFullSize);
                String thumbUrl = ImageConfiguration.getUrl(path, backdropThumbSize);
                ScraperImage image = new ScraperImage(Type.MOVIE_BACKDROP, nameSeed);
                image.setLargeUrl(fullUrl);
                image.setThumbUrl(thumbUrl);
                image.generateFileNames(context);
                backdrops.add(image);
            }
            tag.setBackdrops(backdrops);
            return true;
        } catch (IOException e) {
            if (DBG) Log.e(TAG, e.getMessage(), e);
        }
        return false;
    }

    private static FileFetchResult getFile(FileFetcher fetcher, long movieId) {
        String requestUrl = TheMovieDb3.buildUrl(null, METHOD + "/" + movieId + "/" + SUBMETHOD);
        if (DBG) Log.d(TAG, "REQUEST: [" + requestUrl + "]");
        return fetcher.getFile(requestUrl);
    }
}
