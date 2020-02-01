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
import com.archos.mediascraper.ScraperImage.Type;
import com.archos.mediascraper.xml.MovieScraper3;
import com.uwetrottmann.tmdb2.entities.Images;
import com.uwetrottmann.tmdb2.services.MoviesService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Response;

// Get the images (posters and backdrops) for a specific movie id and language (ISO 939-1 code)
public class MovieIdImages2 {
    private static final String TAG = MovieIdImages2.class.getSimpleName();
    private static final boolean DBG = false;

    public static boolean addImages(long movieId, MovieTags tag, String language,
            ImageConfiguration.PosterSize posterFullSize,
            ImageConfiguration.PosterSize posterThumbSize,
            ImageConfiguration.BackdropSize backdropFullSize,
            ImageConfiguration.BackdropSize backdropThumbSize,
            String nameSeed,
            MoviesService moviesService,
            Context context) {
        Response<Images> imagesResponse = null;

        if (tag == null)
            return false;
        if (DBG) Log.d(TAG, "addImages for " + tag.getTitle() + " in "+ language);
        try {
            imagesResponse = moviesService.images((int) movieId, language).execute();
        } catch (IOException e) {
            Log.e(TAG, "addImages: caught IOException getting summary");
            imagesResponse = null;
        }
        boolean retry = false;
        if (imagesResponse == null)
            retry = true;
        else if (! imagesResponse.isSuccessful())
            retry =true;
        if (retry) { //first failure, try again
            try {
                //when requesting immediately after failure, it fails again (2500ms had still some failure).
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Log.w(TAG, "addImages: caught InterruptedException");
                Thread.currentThread().interrupt();
            }
            try {
                imagesResponse = moviesService.images((int) movieId, language).execute();
            } catch (IOException e) {
                Log.e(TAG, "addImages: caught IOException getting summary");
                return false;
            }
            if (imagesResponse == null)
                return false;
            else if (! imagesResponse.isSuccessful())
                return false;
        }
        switch (imagesResponse.code()) {
            case 401: // auth issue
                if (DBG) Log.d(TAG, "search: auth error");
                MovieScraper3.reauth();
                return false;
            case 404: // not found
                if (DBG) Log.d(TAG, "getBaseInfo: movieId " + movieId + " not found");
                return false;
            default:
                if (imagesResponse.isSuccessful()) {
                    if (imagesResponse.body() != null) {
                        Images images = imagesResponse.body();
                        MovieIdImagesResult parserResult = null;
                        parserResult = MovieIdImageParser2.getResult(images, language);
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
                    } else
                        return false;
                } else // an error at this point is PARSER related
                    return false;
        }
    }
}
