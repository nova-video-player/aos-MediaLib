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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * From http://api.themoviedb.org/3/configuration?api_key=4ec3ebcebce0900124fde6d164606823,
 * could be done dynamically
 *
 * "base_url": "http://cf2.imgobject.com/t/p/",
 * "poster_sizes": [
 *   "w92",
 *   "w154",
 *   "w185",
 *   "w342",
 *   "w500",
 *   "original"
 * ],
 * "backdrop_sizes": [
 *   "w300",
 *   "w780",
 *   "w1280",
 *   "original"
 * ],
 */
public class ImageConfiguration {
    private static final String BASE_URL = "https://image.tmdb.org/t/p/";
    public enum PosterSize {
        W92("w92"),
        W154("w154"),
        W185("w185"),
        W342("w342"),
        W500("w500"),
        ORIGINAL("original");

        public final String urlValue;
        private PosterSize(String key) {
            this.urlValue = key;
        }
    }
    public enum BackdropSize {
        W300("w300"),
        W780("w780"),
        W1280("w1280"),
        ORIGINAL("original");

        public final String urlValue;
        private BackdropSize(String key) {
            this.urlValue = key;
        }
    }

    public static String getUrl(String path, PosterSize size) {
        return BASE_URL + size.urlValue + path;
    }
    public static String getUrl(String path, BackdropSize size) {
        return BASE_URL + size.urlValue + path;
    }

    /**
     * rewrites an url like
     * http://cf2.imgobject.com/t/p/original/6c8DMSlnWj0sKK9joCb46Q7KXSI.jpg
     * to
     * http://cf2.imgobject.com/t/p/[desiredSize]/6c8DMSlnWj0sKK9joCb46Q7KXSI.jpg
     * or returns the original url if it does not match
     */
    public static String rewriteUrl(String url, PosterSize size) {
        return rewriteUrl(url, size.urlValue);
    }

    /**
     * rewrites an url like
     * http://cf2.imgobject.com/t/p/original/6c8DMSlnWj0sKK9joCb46Q7KXSI.jpg
     * to
     * http://cf2.imgobject.com/t/p/[desiredSize]/6c8DMSlnWj0sKK9joCb46Q7KXSI.jpg
     * or returns the original url if it does not match
     */
    public static String rewriteUrl(String url, BackdropSize size) {
        return rewriteUrl(url, size.urlValue);
    }

    // pattern that accepts those imgobject urls
    // matches both http and https
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\w+\\.imgobject\\.com/t/p/([^/]+)/.*");
    private static String rewriteUrl(String url, String replacement) {
        String result = url;
        if (url != null) {
            Matcher matcher = URL_PATTERN.matcher(url);
            if (matcher.matches()) {
                result = url.substring(0, matcher.start(1)) + replacement + url.substring(matcher.end(1));
                // force http to https rewrite since Android O does not like it anymore
                result = result.replace("http://","https://");
            }
        }
        return result;
    }

}
