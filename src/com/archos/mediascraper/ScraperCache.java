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
import android.util.Log;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.CacheControl;

/*
 * Shared cache for scraper
 */

public class ScraperCache {
    private static final String TAG = "ScraperCache";
    private static final boolean DBG = false;

    static protected final int cacheSize = 100 * 1024 * 1024; // 100 MB (it is a directory...)
    static Cache cache;

    public void dumpCacheInfo() {
        if (cache == null) {
            Log.d(TAG, "Cache not initialized");
            return;
        }
        try {
            double fillRatio = (cache.maxSize() / (double) cache.size());
            double hitRatio = cache.hitCount() / (double) cache.requestCount();
            Log.d(TAG, "Cache filled " + fillRatio + "%");
            Log.d(TAG, "Cache hit " + hitRatio + "%");
        } catch (IOException e) {
            Log.e(TAG, "caght IOException", e);
        }
    }

    public static Cache getCache(Context context) {
        if (cache == null)
            cache = new Cache(context.getCacheDir(), cacheSize);
        return cache;
    }
}
