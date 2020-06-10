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

import androidx.core.util.Pair;

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

    private static boolean authIssue = false;
    private static boolean notFoundIssue = true;

    private static boolean retry = false;
    private static boolean globalRetry = false;

    private static Response<Images> imagesResponse = null;
    private static Response<Images> globalImagesResponse = null;

    private static List<ScraperImage> posters = null;
    private static List<ScraperImage> backdrops = null;

    private static Pair<Response<Images>, Boolean> tryResponse(long movieId, String language, MoviesService moviesService) {
        Boolean again = false;
        Response<Images> response;
        try {
            response = moviesService.images((int) movieId, language).execute();
            if (response.code() == 401) authIssue = true; // this is an OR
            if (response.code() != 404) notFoundIssue = false; // this is an AND
        } catch (IOException e) {
            Log.e(TAG, "tryResponse: caught IOException getting summary");
            response = null;
            again = false;
        }
        if (response == null)
            again = true;
        else if (! response.isSuccessful())
            again =true;
        if (DBG) if (response == null) Log.d(TAG, "tryResponse: response null in " + language + " for movieId=" + movieId);
        if (DBG) if (! response.isSuccessful()) Log.d(TAG, "tryResponse: response not successful in " + language + " for movieId=" + movieId);
        return new Pair<>(response, again);
    }

    private static void getImages(Response<Images> response, String defaultPosterPath, String defaultBackdropPath,
                           ImageConfiguration.PosterSize posterFullSize,
                           ImageConfiguration.PosterSize posterThumbSize,
                           ImageConfiguration.BackdropSize backdropFullSize,
                           ImageConfiguration.BackdropSize backdropThumbSize,
                           String nameSeed, String language, Context context) {
        Images images = response.body();
        MovieIdImagesResult parserResult = null;
        parserResult = MovieIdImagesParser2.getResult(images, language);
        // add default poster coming from SearchMovie
        if (defaultPosterPath != null) parserResult.posterPaths.add(0, defaultPosterPath);
        for (String path : parserResult.posterPaths) {
            if (DBG) Log.d(TAG, "addImages: treating poster " + path);
            String fullUrl = ImageConfiguration.getUrl(path, posterFullSize);
            String thumbUrl = ImageConfiguration.getUrl(path, posterThumbSize);
            ScraperImage image = new ScraperImage(Type.MOVIE_POSTER, nameSeed);
            image.setLargeUrl(fullUrl);
            image.setThumbUrl(thumbUrl);
            image.generateFileNames(context);
            posters.add(image);
        }
        // add default backdrop coming from SearchMovie
        if (defaultBackdropPath != null) parserResult.backdropPaths.add(0, defaultBackdropPath);
        for (String path : parserResult.backdropPaths) {
            if (DBG) Log.d(TAG, "addImages: treating backdrop " + path);
            String fullUrl = ImageConfiguration.getUrl(path, backdropFullSize);
            String thumbUrl = ImageConfiguration.getUrl(path, backdropThumbSize);
            ScraperImage image = new ScraperImage(Type.MOVIE_BACKDROP, nameSeed);
            image.setLargeUrl(fullUrl);
            image.setThumbUrl(thumbUrl);
            image.generateFileNames(context);
            backdrops.add(image);
        }
    }

    public static boolean addImages(long movieId, MovieTags tag, String language, String defaultPosterPath, String defaultBackdropPath,
            ImageConfiguration.PosterSize posterFullSize,
            ImageConfiguration.PosterSize posterThumbSize,
            ImageConfiguration.BackdropSize backdropFullSize,
            ImageConfiguration.BackdropSize backdropThumbSize,
            String nameSeed,
            MoviesService moviesService,
            Context context) {

        if (tag == null)
            return false;
        if (DBG) Log.d(TAG, "addImages for " + tag.getTitle() + ", movieId=" + movieId + " in "+ language + " and en");

        posters = new ArrayList<ScraperImage>();
        backdrops = new ArrayList<ScraperImage>();
        retry = false;
        globalRetry = false;
        authIssue = false;
        notFoundIssue = true;
        Pair<Response<Images>, Boolean> responseRetry = tryResponse(movieId, language, moviesService);
        imagesResponse = responseRetry.first;
        retry = responseRetry.second;
        if (retry) { //first failure, try again
            authIssue = false;
            notFoundIssue = true;
            try {
                //when requesting immediately after failure, it fails again (2500ms had still some failure).
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Log.w(TAG, "addImages: caught InterruptedException");
                Thread.currentThread().interrupt();
            }
            responseRetry = tryResponse(movieId, language, moviesService);
            imagesResponse = responseRetry.first;
            retry = responseRetry.second;
        }
        // need to search images in en too since it can be empty in language...
        if (!language.equals("en")) {
            Pair<Response<Images>, Boolean> globalResponseRetry = tryResponse(movieId, "en", moviesService);
            globalImagesResponse = globalResponseRetry.first;
            globalRetry = globalResponseRetry.second;
            if (retry) { //first failure, try again
                authIssue = false;
                notFoundIssue = true;
                try {
                    //when requesting immediately after failure, it fails again (2500ms had still some failure).
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Log.w(TAG, "addImages: caught InterruptedException");
                    Thread.currentThread().interrupt();
                }
                globalResponseRetry =  tryResponse(movieId, "en", moviesService);
                globalImagesResponse = globalResponseRetry.first;
                globalRetry = globalResponseRetry.second;
            }
        }
        if (authIssue) {
            if (DBG) Log.d(TAG, "addImages: auth error");
            MovieScraper3.reauth();
            return false;
        }
        if (notFoundIssue) {
            if (DBG) Log.d(TAG, "addImages: not found");
            return false;
        }
        if (imagesResponse == null && globalImagesResponse == null) {
            if (DBG) Log.d(TAG, "addImages: all responses null");
            return false;
        } else {
            if (imagesResponse == null)
                if (! globalImagesResponse.isSuccessful())
                    return false;
            if (globalImagesResponse == null)
                if (! imagesResponse.isSuccessful())
                    return false;
            if (imagesResponse != null) {
                if (imagesResponse.isSuccessful()) {
                    if (DBG) Log.d(TAG, "addImages: imagesResponse successful");
                    getImages(imagesResponse, defaultPosterPath, defaultBackdropPath,
                            posterFullSize, posterThumbSize, backdropFullSize, backdropThumbSize,
                            nameSeed, language, context);
                }
            }
            if (globalImagesResponse != null) {
                if (globalImagesResponse.isSuccessful()) {
                    if (DBG) Log.d(TAG, "addImages: globalImagesResponse successful");
                    getImages(globalImagesResponse, null, null,
                            posterFullSize, posterThumbSize, backdropFullSize, backdropThumbSize,
                            nameSeed, "en", context);
                }
            }
        }
        if (DBG) Log.d(TAG, "addImages: adding " + backdrops.size() + " backdrops and " + posters.size() + " posters to tag");
        tag.setBackdrops(backdrops);
        tag.setPosters(posters);
        return true;
    }
}
