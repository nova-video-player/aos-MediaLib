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

import com.uwetrottmann.tmdb2.entities.Image;
import com.uwetrottmann.tmdb2.entities.Images;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class MovieIdImagesParser2 {
    private static final String TAG = MovieIdImagesParser2.class.getSimpleName();
    private static final boolean DBG = false;

    public static MovieIdImagesResult getResult(Images images, String preferredLanguage) {
        MovieIdImagesResult myResult = new MovieIdImagesResult();
        LinkedList<ImageUtils.Image> posters = new LinkedList<ImageUtils.Image>();
        LinkedList<ImageUtils.Image> backdrops = new LinkedList<ImageUtils.Image>();
        if (images != null) {
            if (images.posters != null) {
                int imageNumber = 0;
                for (com.uwetrottmann.tmdb2.entities.Image poster : images.posters) {
                    String filePath = null;
                    String language = null;
                    if (DBG) Log.d(TAG, "getResult: poster " + poster.file_path);
                    if (poster.file_path != null) filePath = poster.file_path;
                    if (poster.iso_639_1 != null) language = poster.iso_639_1;
                    ImageUtils.Image image = new ImageUtils.Image(filePath, language, preferredLanguage, imageNumber);
                    posters.add(image);
                    imageNumber++;
                }
            }
            if (images.backdrops != null) {
                int imageNumber = 0;
                for (com.uwetrottmann.tmdb2.entities.Image backdrop: images.backdrops) {
                    String filePath = null;
                    String language = null;
                    if (DBG) Log.d(TAG, "getResult: backdrop " + backdrop.file_path);
                    if (backdrop.file_path != null) filePath = backdrop.file_path;
                    if (backdrop.iso_639_1 != null) language = backdrop.iso_639_1;
                    ImageUtils.Image image = new ImageUtils.Image(filePath, language, preferredLanguage, imageNumber);
                    backdrops.add(image);
                    imageNumber++;
                }
            }
        }
        myResult.posterPaths = ImageUtils.getSortedPaths(posters);
        myResult.backdropPaths = ImageUtils.getSortedPaths(backdrops);
        return myResult;
    }
}
