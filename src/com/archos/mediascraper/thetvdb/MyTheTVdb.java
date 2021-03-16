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

package com.archos.mediascraper.thetvdb;

import com.archos.mediascraper.ScraperCache;
import com.uwetrottmann.thetvdb.TheTvdb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

public class MyTheTVdb extends TheTvdb {

    private static final Logger log = LoggerFactory.getLogger(MyTheTVdb.class);
    private final static boolean CACHE = false;
    private static Cache mCache;

    public MyTheTVdb(String apiKey, Cache cache) {
        super(apiKey);
        mCache = cache;
    }

    @Override
    protected void setOkHttpClientDefaults(OkHttpClient.Builder builder) {
        super.setOkHttpClientDefaults(builder);
        if (CACHE) {
            builder.cache(mCache).addNetworkInterceptor(new ScraperCache.CacheInterceptor());
            if (log.isTraceEnabled()) {
                builder.addInterceptor(new ScraperCache.isCacheResponding());
            }
        }
        if (log.isTraceEnabled()) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            builder.addNetworkInterceptor(logging);
        }
        builder.connectTimeout(ScraperCache.CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        builder.readTimeout(ScraperCache.READ_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }
}