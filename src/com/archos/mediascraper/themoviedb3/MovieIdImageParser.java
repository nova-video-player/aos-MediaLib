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

import android.util.JsonReader;

import com.archos.mediascraper.StringMatcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class MovieIdImageParser extends JSONStreamParser<MovieIdImagesResult, String> {
    private static final MovieIdImageParser INSTANCE = new MovieIdImageParser();
    public static MovieIdImageParser getInstance() {
        return INSTANCE;
    }
    private MovieIdImageParser() {
        // empty
    }

    private static final StringMatcher MATCHER = new StringMatcher();
    // top level keys
    private static final int KEY_BACKDROPS = 1;
    private static final int KEY_FILE_PATH = 2;
    private static final int KEY_ISO_639_1 = 3;
    private static final int KEY_POSTERS = 4;

    static {
        MATCHER.addKey("backdrops", KEY_BACKDROPS);
        MATCHER.addKey("file_path", KEY_FILE_PATH);
        MATCHER.addKey("iso_639_1", KEY_ISO_639_1);
        MATCHER.addKey("posters", KEY_POSTERS);
    }

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

    @Override
    protected MovieIdImagesResult getResult(JsonReader reader, String config) throws IOException {
        MovieIdImagesResult myResult = new MovieIdImagesResult();
        LinkedList<Image> posters = new LinkedList<Image>();
        LinkedList<Image> backdrops = new LinkedList<Image>();

        reader.beginObject();
        String name;
        while ((name = getNextNotNullName(reader)) != null) {
            switch(MATCHER.match(name)) {
                case KEY_POSTERS:
                    readImages(reader, posters, config);
                    break;
                case KEY_BACKDROPS:
                    readImages(reader, backdrops, config);
                    break;
                default:
                    reader.skipValue();
            }
        }
        reader.endObject();

        myResult.posterPaths = getSortedPaths(posters);
        myResult.backdropPaths = getSortedPaths(backdrops);
        return myResult;
    }

    private static void readImages(JsonReader reader, LinkedList<Image> images, String preferredLanguage) throws IOException {
        reader.beginArray();
        int imageNumber = 0;
        while (hasNextSkipNull(reader)) {
            Image item = readImage(reader, preferredLanguage, imageNumber++);
                images.add(item);
        }
        reader.endArray();
    }

    private static Image readImage(JsonReader reader, String preferredLanguage, int imageNumber) throws IOException {
        reader.beginObject();
        String filePath = null;
        String language = null;
        String name;
        while ((name = getNextNotNullName(reader)) != null) {
            switch(MATCHER.match(name)) {
                case KEY_FILE_PATH:
                    filePath = reader.nextString();
                    break;
                case KEY_ISO_639_1:
                    language = reader.nextString();
                    break;
                default:
                    reader.skipValue();
            }
        }
        reader.endObject();
        return new Image(filePath, language, preferredLanguage, imageNumber);
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
