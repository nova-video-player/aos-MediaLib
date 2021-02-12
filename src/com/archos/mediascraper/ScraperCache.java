// Copyright 2017 Courville Software
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


package com.archos.mediascraper;

import android.content.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Interceptor;
import okhttp3.Response;

/*
 * Shared cache for scraper
 */

public class ScraperCache {

    private static final Logger log = LoggerFactory.getLogger(ScraperCache.class);

    static final String SCRAPER_CACHE = "scraper-cache";
    static protected final long cacheSize = 100L * 1024L * 1024L; // 100 MB (it is a directory...)
    public static final int CONNECT_TIMEOUT_MILLIS = 15 * 1000; // 15s
    public static final int READ_TIMEOUT_MILLIS = 20 * 1000; // 20s
    static Cache cache;

    public static void dumpCacheInfo() {
        if (cache == null) {
            log.debug("dumpCacheInfo: cache not initialized");
            return;
        }
        try {
            double fillRatio = cache.size() / (double) cache.maxSize() * 100;
            double hitRatio = cache.hitCount() / (double) cache.requestCount() * 100;
            log.trace("Cache filled " + fillRatio + "% (size=" + cache.size() + "/maxsize=" + cache.maxSize() + ")" );
            log.trace("Cache hit " + hitRatio + "% (hit="+ cache.hitCount() + "/requests=" + cache.requestCount() + ")");
            log.trace("Cache request count " + cache.requestCount() + ", network count " + cache.networkCount());
        } catch (IOException e) {
            log.error("caught IOException", e);
        }
    }

    public static Cache getCache(Context context) {
        if (cache == null) {
            File cacheDir = new File(context.getCacheDir(), SCRAPER_CACHE);
            if (!cacheDir.exists()) cacheDir.mkdirs();
            cache = new Cache(cacheDir, cacheSize);
        }
        return cache;
    }

    public static class CacheInterceptor implements Interceptor {
        @Override
        public okhttp3.Response intercept(Chain chain) throws IOException {
            okhttp3.Response response = chain.proceed(chain.request());
            CacheControl cacheControl = new CacheControl.Builder()
                    .maxAge(MediaScraper.SCRAPER_CACHE_TIMEOUT_COUNT, MediaScraper.SCRAPER_CACHE_TIMEOUT_UNIT)
                    .build();
            return response.newBuilder()
                    .removeHeader("Pragma")
                    .removeHeader("Vary")
                    .removeHeader("Age")
                    .removeHeader("X-Cache")
                    .removeHeader("X-Cache-Hit")
                    .header("Cache-Control", cacheControl.toString())
                    .build();
        }
    }

    public static class isCacheResponding implements Interceptor {
        @Override
        public okhttp3.Response intercept(Chain chain) throws IOException {
            Response response = chain.proceed(chain.request());
            if (response.cacheResponse() != null) {
                log.trace("okhttp response from cache");
            } else if (response.networkResponse() != null) {
                log.trace("okhttp response from network");
            }
            dumpCacheInfo();
            return response;
        }
    }
}
