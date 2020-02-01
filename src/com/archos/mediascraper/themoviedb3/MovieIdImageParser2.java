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

import com.uwetrottmann.tmdb2.entities.Images;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class MovieIdImageParser2 {

    private static class Image implements Comparable<Image>{
        // 2 bit << 29 = 31 bit
        private static final int PRIO_PREFERRED = 0 << 29;
        private static final int PRIO_ENGLISH = 1 << 29;
        private static final int PRIO_NOLANG = 2 << 29;
        private static final int PRIO_OTHER = 3 << 29;
        public Image(String path, String language, String preferredLanguage, int imageNumber) {
            int priorityClass;
            if (language == null || language.isEmpty()) {
                priorityClass = PRIO_NOLANG;
            } else if (language.equals(preferredLanguage)) {
                priorityClass = PRIO_PREFERRED;
            } else if (language.equals("en")) {
                priorityClass = PRIO_ENGLISH;
            } else {
                priorityClass = PRIO_OTHER;
            }
            this.priority = priorityClass + imageNumber;
            this.imagePath = path;
        }
        public final String imagePath;
        public final int priority;

        @Override
        public int compareTo(Image another) {
            // a negative integer if this instance is less than another;
            // a positive integer if this instance is greater than another;
            // 0 if this instance has the same order as another.
            int other = another != null ? another.priority : 0;
            return priority - other;
        }
    }

    public static MovieIdImagesResult getResult(Images images, String preferredLanguage) {
        MovieIdImagesResult myResult = new MovieIdImagesResult();
        LinkedList<Image> posters = new LinkedList<Image>();
        LinkedList<Image> backdrops = new LinkedList<Image>();
        if (images != null) {
            if (images.posters != null) {
                int imageNumber = 0;
                for (com.uwetrottmann.tmdb2.entities.Image poster :images.posters) {
                    String filePath = null;
                    String language = null;
                    String name;
                    if (poster.file_path != null) filePath = poster.file_path;
                    if (poster.iso_639_1 != null) language = poster.iso_639_1;
                    Image image = new Image(filePath, language, preferredLanguage, imageNumber);
                    posters.add(image);
                    imageNumber++;
                }
            }
            if (images.backdrops != null) {
                int imageNumber = 0;
                for (com.uwetrottmann.tmdb2.entities.Image backdrop: images.backdrops) {
                    String filePath = null;
                    String language = null;
                    String name;
                    if (backdrop.file_path != null) filePath = backdrop.file_path;
                    if (backdrop.iso_639_1 != null) language = backdrop.iso_639_1;
                    Image image = new Image(filePath, language, preferredLanguage, imageNumber);
                    backdrops.add(image);
                    imageNumber++;
                }
            }
        }
        myResult.posterPaths = getSortedPaths(posters);
        myResult.backdropPaths = getSortedPaths(backdrops);
        return myResult;
    }

    private static List<String> getSortedPaths(List<Image> images) {
        Collections.sort(images);
        ArrayList<String> ret = new ArrayList<String>(images.size());
        for (Image image : images) {
            ret.add(image.imagePath);
        }
        return ret;
    }
}
