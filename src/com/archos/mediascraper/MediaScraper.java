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

package com.archos.mediascraper;

import static com.archos.mediacenter.utils.MediaUtils.getExternalCacheDir;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * MediaScraper Constants. So far not that much
 */
public final class MediaScraper {
    private MediaScraper() { /* u no make instance of me */ }

    /**
     * returns the poster storage directory in context private dir, e.g.<p>
     * <code>/data/data/com.archos.mediacenter.video/app_scraper_posters</code>
     */
    public static File getPosterDirectory(Context context) {
        return context.getDir("scraper_posters", Context.MODE_PRIVATE);
    }

    /**
     * returns the picture storage directory in context private dir, e.g.<p>
     * <code>/data/data/com.archos.mediacenter.video/app_scraper_pictures</code>
     */
    public static File getPictureDirectory(Context context) {
        return context.getDir("scraper_pictures", Context.MODE_PRIVATE);
    }

    /**
     * returns the backdrop download cache directory in external context dir, e.g.<p>
     * <code>/mnt/storage/Android/data/com.archos.mediacenter.video/cache/pictures</code><p>
     * External cache is NOT cleared when using the clear cache button in system settings > apps
     */
    public static File getPictureCacheDirectory(Context context) {
        return new File(getExternalCacheDir(context), "pictures");
    }

    /**
     * returns the xml downloads cache directory in context private dir, e.g.<p>
     * <code>/data/data/com.archos.mediacenter.video/cache/xml</code><p>
     * Internal cache is cleared when using the clear cache button in system settings > apps
     */
    public static File getXmlCacheDirectory(Context context) {
        return new File(context.getCacheDir(), "xml");
    }
    /**
     * returns the image downloads cache directory in context private dir, e.g.<p>
     * <code>/data/data/com.archos.mediacenter.video/cache/images</code><p>
     * Internal cache is cleared when using the clear cache button in system settings > apps
     */
    public static File getImageCacheDirectory(Context context) {
        return new File(context.getCacheDir(), "images");
    }

    // Backdrops stored on external storage = hdd for H devices.
    /**
     * returns the backdrop storage directory in external context dir, e.g.<p>
     * <code>/mnt/storage/Android/data/com.archos.mediacenter.video/files/backdrops</code><p>
     * External cache is NOT cleared when using the clear cache button in system settings > apps
     */
    public static File getBackdropDirectory(Context context) {
        return context.getDir("scraper_backdrops", Context.MODE_PRIVATE);
    }
    /**
     * returns the backdrop download cache directory in external context dir, e.g.<p>
     * <code>/mnt/storage/Android/data/com.archos.mediacenter.video/cache/backdrops</code><p>
     * External cache is NOT cleared when using the clear cache button in system settings > apps
     */
    public static File getBackdropCacheDirectory(Context context) {
        return new File(getExternalCacheDir(context), "backdrops");
    }

    /** Timeout for {@link HttpCache} - 1 day since xml responses are quite dynamic */
    public static final long XML_CACHE_TIMEOUT = HttpCache.ONE_DAY;

    /** Timeout for {@link HttpCache} - 30 days since images should almost never change */
    public static final long IMAGE_CACHE_TIMEOUT = HttpCache.ONE_DAY * 30L;

    /** Timeout for {@link HttpCache} - 2 days since images are large and shall not take all the space */
    public static final long BACKDROP_CACHE_TIMEOUT = HttpCache.ONE_DAY * 2L;

    /** Timeout for {@link ScraperCache} - 2 hours */
    public static final int SCRAPER_CACHE_TIMEOUT_COUNT = 2;
    public static final TimeUnit SCRAPER_CACHE_TIMEOUT_UNIT = TimeUnit.HOURS;

}
