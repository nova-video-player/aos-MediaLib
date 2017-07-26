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
import android.util.LruCache;

/** {@link LruCache} implementation for {@link Bitmap} limited by byte size */
public class BitmapLruCache extends LruCache<String, Bitmap> {
    public static final int KILOBYTE = 1024;
    public static final int MEGABYTE = KILOBYTE * 1024;

    public BitmapLruCache(int maxSize) {
        super(maxSize);
    }

    @Override
    protected int sizeOf(String key, Bitmap value) {
        // assume size of recycled images = 1
        int result = 1;
        if (!value.isRecycled()) {
            result = value.getByteCount();
        }
        return result;
    }
}
