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

import com.archos.mediascraper.MediaScraper;
import com.uwetrottmann.tmdb2.Tmdb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

public class MyTmdb extends Tmdb {

    private static final Logger log = LoggerFactory.getLogger(MyTmdb.class);
    private final static boolean CACHE = true;
    private static Cache mCache;

    public MyTmdb(String apiKey, Cache cache) {
        super(apiKey);
        mCache = cache;
    }

    public class CacheInterceptor implements Interceptor {
        @Override
        public okhttp3.Response intercept(Chain chain) throws IOException {
            okhttp3.Response response = chain.proceed(chain.request());
            CacheControl cacheControl = new CacheControl.Builder()
                    .maxAge(MediaScraper.SCRAPER_CACHE_TIMEOUT_COUNT, MediaScraper.SCRAPER_CACHE_TIMEOUT_UNIT)
                    .build();
            return response.newBuilder()
                    .removeHeader("Pragma")
                    .removeHeader("Cache-Control")
                    .header("Cache-Control", cacheControl.toString())
                    .build();
        }
    }

    @Override
    protected void setOkHttpClientDefaults(OkHttpClient.Builder builder) {
        super.setOkHttpClientDefaults(builder);
        if (log.isTraceEnabled()) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            builder.addNetworkInterceptor(logging).addInterceptor(logging);
        }
        if (CACHE) {
            builder.cache(mCache).addNetworkInterceptor(new MyTmdb.CacheInterceptor());
        }
    }
}
