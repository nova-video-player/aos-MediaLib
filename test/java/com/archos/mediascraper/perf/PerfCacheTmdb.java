// Copyright 2026 Courville Software
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

package com.archos.mediascraper.perf;

import com.archos.mediascraper.themoviedb3.MyTmdb;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.OkHttpClient;
import okhttp3.Response;

/**
 * Test-only TMDb client used by the perf tests (see {@link ScraperPerfQueryCountTest}) to
 * measure how many search requests {@code MovieScraper3.getMatches2()} issues, without having
 * to hit the network on every re-run. Unlike the production {@code ScraperCache} (2h TTL, see
 * {@code MediaScraper.SCRAPER_CACHE_TIMEOUT_COUNT}), responses recorded here are cached for a
 * full year on disk, so the same run can be replayed many times over days/weeks - across
 * different versions of the cascade logic under test - while only ever hitting the real TMDb
 * API once per distinct query. This class is never referenced by production code.
 */
public class PerfCacheTmdb extends MyTmdb {

    /** Number of requests that actually reached the network (i.e. were not cache hits). */
    public final AtomicInteger networkRequestCount = new AtomicInteger(0);
    /** Number of requests served from the on-disk cache without hitting the network. */
    public final AtomicInteger cacheHitCount = new AtomicInteger(0);

    private final Cache perfCache;

    public PerfCacheTmdb(String apiKey, File cacheDir) {
        // The base class's own cache (passed as null here) is intentionally unused: this class
        // completely overrides setOkHttpClientDefaults() below instead of calling super, so it
        // can install its own long-lived cache and count network vs. cache-hit requests.
        super(apiKey, null);
        cacheDir.mkdirs();
        this.perfCache = new Cache(cacheDir, 500L * 1024L * 1024L); // 500MB: plenty for a whole library
    }

    @Override
    protected void setOkHttpClientDefaults(OkHttpClient.Builder builder) {
        // IMPORTANT: call super first. Tmdb.setOkHttpClientDefaults() adds the TmdbInterceptor
        // (injects the required api_key query parameter) and TmdbAuthenticator. Skipping this
        // makes every request 401, which in turn makes SearchMovie2 call MovieScraper3.reauth()
        // and silently replace this client with a real (uncounted, short-TTL-cached) production
        // MyTmdb for the remainder of the run - defeating the whole point of this class.
        super.setOkHttpClientDefaults(builder);
        builder.cache(perfCache);
        // Network interceptor: only invoked when a request actually goes out over the wire.
        builder.addNetworkInterceptor(chain -> {
            networkRequestCount.incrementAndGet();
            Response response = chain.proceed(chain.request());
            if (response.code() == 404 || response.code() == 401) {
                // Do not cache errors, same as the production ScraperCache.CacheInterceptor.
                return response.newBuilder().header("Cache-Control", "no-store").build();
            }
            CacheControl longLived = new CacheControl.Builder().maxAge(365, TimeUnit.DAYS).build();
            return response.newBuilder().header("Cache-Control", longLived.toString()).build();
        });
        // Application interceptor: invoked for every request, cache hit or not.
        builder.addInterceptor(chain -> {
            Response response = chain.proceed(chain.request());
            if (response.cacheResponse() != null) cacheHitCount.incrementAndGet();
            return response;
        });
        builder.connectTimeout(15, TimeUnit.SECONDS);
        builder.readTimeout(20, TimeUnit.SECONDS);
    }

    /** Total logical search requests issued so far (network + cache hits). */
    public int totalRequestCount() {
        return networkRequestCount.get() + cacheHitCount.get();
    }
}
