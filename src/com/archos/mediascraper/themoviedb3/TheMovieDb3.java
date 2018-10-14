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

import android.net.Uri;
import android.net.Uri.Builder;

import com.archos.environment.ArchosUtils;
import com.archos.medialib.R;

import java.util.Map;
import java.util.Map.Entry;

public class TheMovieDb3 {
    private static final String API_BASE_URL = "https://api.themoviedb.org/3";

    private static final String API_KEY_KEY = "api_key";

    private static final Uri BASE_URI = Uri.parse(API_BASE_URL);

    public static String buildUrl(Map<String, String> queryParams, String method) {
        Builder builder = BASE_URI.buildUpon().appendEncodedPath(method);
        builder.appendQueryParameter(API_KEY_KEY, ArchosUtils.getGlobalContext().getString(R.string.tmdb_api_key));
        if (queryParams != null) {
            for (Entry<String, String> param : queryParams.entrySet()) {
                builder.appendQueryParameter(param.getKey(), param.getValue());
            }
        }
        return builder.build().toString();
    }

    public static void putIfNonEmpty(Map<String, String> input, String key, String value) {
        if (value != null && !value.isEmpty())
            input.put(key, value);
    }
}
