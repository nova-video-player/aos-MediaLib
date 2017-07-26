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

import android.graphics.drawable.Drawable;

/** Configuration Object for {@link ImageViewSetter} */
public class ImageViewSetterConfiguration {

    /** holds a singleton of ImageViewSetterConfiguration */
    private static class SingletonHolder {
        // this is not executed until someone calls getDefault()
        static final ImageViewSetterConfiguration INSTANCE =
                new Builder().build();
    }

    /** gets the default config instance */
    public static ImageViewSetterConfiguration getDefault() {
        return SingletonHolder.INSTANCE;
    }

    /** Helper to build configuration objects, call {@link #createNew()} to do so */
    public static class Builder {
        // defaults
        boolean useCache = true;
        int cacheSize = 4 * 1024 * 1024;
        int threadPoolSize = 2;
        int threadPriority = Thread.NORM_PRIORITY - 1;
        boolean interruptThreads = false;
        boolean debugSleep = false;
        boolean debugNoThreads = false;
        Drawable whileLoading = null;

        /** starts creating a new configuration object, next call the set*** methods, then {@link #build()} */
        public static Builder createNew() {
            return new Builder();
        }

        /** finalizes the builer process */
        public ImageViewSetterConfiguration build() {
            return new ImageViewSetterConfiguration(this);
        }

        // setters
        /** true to enable use of cache, default is true */
        public Builder setUseCache(boolean enable) {
            useCache = enable;
            return this;
        }
        /** size in bytes to use for the cache, enables cache, default is 4MB */
        public Builder setCacheSize(int byteSize) {
            cacheSize = byteSize;
            useCache = byteSize > 0;
            return this;
        }
        /** amount of threads to be used for background loading, 1-3 sounds good, default is 2 */
        public Builder setBackgroundThreadCount(int amount) {
            threadPoolSize = amount;
            debugNoThreads = amount <= 0;
            return this;
        }
        /** thread prority of background loaders {@link Thread#NORM_PRIORITY} - 1 is default */
        public Builder setThreadPriority(int priority) {
            threadPriority = priority;
            return this;
        }
        /**
         * experimental:
         * When there is a thread that already executes for a given view but is no longer
         * required to do so send it an interrupt() <br>
         * not sure if safe to use, not every thread handles interrupts correctly, e.g. database access
         */
        public Builder setInterruptThreads(boolean interrupt) {
            interruptThreads = interrupt;
            return this;
        }
        /** adds a 1 second extra sleep to every load operation in the background */
        public Builder setDebugSleep(boolean enable) {
            debugSleep = enable;
            return this;
        }
        /** disables multi threading */
        public Builder setDebugNoThreads(boolean noThreads) {
            debugNoThreads = noThreads;
            return this;
        }
        /** The Drawable to be used immediately when there is nothing in the cache / no image could be loaded */
        public Builder setDrawableWhileLoading(Drawable drawable) {
            whileLoading = drawable;
            return this;
        }
    }

    /** "private" constructor for builder */
    /* default */ ImageViewSetterConfiguration(Builder b) {
        this.useCache = b.useCache;
        this.cacheSize = b.cacheSize;
        this.threadPoolSize = b.threadPoolSize;
        this.threadPriority = b.threadPriority;
        this.interruptThreads = b.interruptThreads;
        this.debugSleep = b.debugSleep;
        this.debugNoThreads = b.debugNoThreads;
        this.whileLoading = b.whileLoading;
    }

    /** enable to use in-memory cache, default on */
    public final boolean useCache;

    /** size in byte (approximately) of in-memory cache, default 2MB */
    public final int cacheSize;

    /** Number of Threads used to load the images, default 2 */
    public final int threadPoolSize;

    /** Thread priority, should be less than {@link Thread#NORM_PRIORITY}, default 1 below normal */
    public final int threadPriority;

    /** 
     * Threads that handle outdated tasks will get interrupt(),
     * useful when task can take really long and would block the thread pool<br>
     * default off
     */
    public final boolean interruptThreads;

    /** Adds a 1 second sleep if loading is done in the background */
    public final boolean debugSleep;

    /** executes loading directly in UI thread */
    public final boolean debugNoThreads;

    public final Drawable whileLoading;
}
