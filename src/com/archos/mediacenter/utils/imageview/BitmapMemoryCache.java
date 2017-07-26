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

package com.archos.mediacenter.utils.imageview;

import android.graphics.Bitmap;

import java.lang.ref.WeakReference;
import java.util.HashMap;

/**
 * cache of Bitmaps that keeps a (byte size) limited amount of keeps strong references
 * plus weak references in case the GC did not pick up the image but it was removed from
 * the strong referenced cache
 */
public class BitmapMemoryCache {

    private final BitmapLruCache mHardCache;
    private final HashMap<String, WeakReference<Bitmap>> mWeakCache;

    /** constructs a cache limiting strong references to bytes */
    public BitmapMemoryCache(int bytes) {
        mHardCache = new BitmapLruCache(bytes);
        mWeakCache = new HashMap<String, WeakReference<Bitmap>>();
    }

    /**
     * @param key - something uniquely identifying an image, see {@link #put(String, Bitmap)}
     * @return the bitmap if it was found in the cache
     * @see android.util.LruCache#get(java.lang.Object)
     */
    public final synchronized Bitmap get(String key) {
        // check hard cache
        Bitmap result = mHardCache.get(key);
        if (result != null && !result.isRecycled())
            return result;
        // remove if is recycled
        mHardCache.remove(key);

        // fallback to weak cache
        WeakReference<Bitmap> weakBitmap = mWeakCache.get(key);
        if (weakBitmap != null) {
            result = weakBitmap.get();
            if (result != null && !result.isRecycled()) {
                // put the reference back into hard cache
                mHardCache.put(key, result);
                return result;
            }
            // remove if it is gone / recycled
            mWeakCache.remove(key);
        }

        // nothing found
        return null;
    }

    /**
     * Put a Bitmap into the cache
     * @param key something uniquely identifying an image, see {@link #get(String)}
     * @param value the Bitmap to cache
     * @see android.util.LruCache#put(java.lang.Object, java.lang.Object)
     */
    public final synchronized void put(String key, Bitmap value) {
        mHardCache.put(key, value);
        mWeakCache.put(key, new WeakReference<Bitmap>(value));
    }

    /**
     * @return approximate cache size in bytes
     * @see android.util.LruCache#size()
     */
    public final int size() {
        return mHardCache.size();
    }

    /** stop using the memories */
    public final synchronized void clear() {
        mHardCache.evictAll();
        mWeakCache.clear();
    }

}
