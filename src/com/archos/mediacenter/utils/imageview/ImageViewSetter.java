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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.ImageView;

import com.archos.mediascraper.MultiLock;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Sets bitmaps to ImageViews either directly or in background
 */
public class ImageViewSetter {
    /* default */ static final String TAG = "ImageViewSetter";
    /* default */ final static boolean DBG = false;

    /** Maps ImageView to key where key identifies the image to load for this view */
    private final Map<ImageView, String> mTaskMap;

    // used if configured to interrupt threads already working on a view
    /** Maps ImageView to thread currently handling that view */
    private final Map<ImageView, Thread> mThreadMap;
    /** Lock for thread synchronization */
    private final MultiLock<ImageView> mThreadLock;

    /** Handler for handling callbacks from threads in UI thread */
    private final Handler mHandler;

    /** Cache by image key */
    private final BitmapMemoryCache mCache;

    /** the while loading / fallback drawable */
    private final Drawable mDefaultDrawable;

    /** Configuration for this instance */
    private final ImageViewSetterConfiguration mConfig;

    /** Threads executed by this thing */
    private ExecutorService mExecutorService;

    // -------------------------- public api -------------------------------- //
    /**
     * Creates a new instance.
     * @param context
     * @param config - may be null, default will be used
     */
    public ImageViewSetter(Context context, ImageViewSetterConfiguration config) {
        mConfig = (config == null) ? ImageViewSetterConfiguration.getDefault() : config;
        mTaskMap = Collections.synchronizedMap(new WeakHashMap<ImageView, String>());

        if (mConfig.interruptThreads) {
            mThreadMap = Collections.synchronizedMap(new WeakHashMap<ImageView, Thread>());
            mThreadLock = new MultiLock<ImageView>();
        } else {
            mThreadMap = null;
            mThreadLock = null;
        }

        mHandler = new Handler(context.getMainLooper(), new ForegroundHandler());
        mCache = mConfig.useCache ?
                new BitmapMemoryCache(mConfig.cacheSize) : null;

        mDefaultDrawable = mConfig.whileLoading;
    }

    /**
     * This is what you use instead of {@link ImageView#setImageBitmap(Bitmap)}, use from
     * UI thread only
     * @param view the target ImageView
     * @param imageProcessor implementation that is capable of loading the bitmap
     * @param loadObject definition of an image the imageProcessor understands
     */
    public void set(ImageView view, ImageProcessor imageProcessor, Object loadObject) {

        if (Looper.getMainLooper() != Looper.myLooper()) {
            throw new RuntimeException("you need to call this from the UI thread.");
        }

        if (view == null || imageProcessor == null)
            throw new RuntimeException("set needs a view and an imageProcessor!");

        if (mThreadMap != null) {
            // interrupt thread working on view if there is one
            mThreadLock.lock(view);
            try {
                Thread worker = mThreadMap.remove(view);
                if (worker != null) {
                    if (DBG) {
                        String key = mTaskMap.get(view);
                        Log.d(TAG, "interrupting thread working on [" + key + "]");
                    }
                    worker.interrupt();
                }
            } finally {
                mThreadLock.unlock(view);
            }
        }

        String key = null;
        if (imageProcessor.canHandle(loadObject)) {
            key = imageProcessor.getKey(loadObject);
        } else if (DBG) {
            Log.d(TAG, "imageProcessor[" + imageProcessor + "] can't handle:" + loadObject);
        }

        LoadTaskItem taskItem = new LoadTaskItem();
        taskItem.weakView = new WeakReference<ImageView>(view);
        taskItem.key = key;
        taskItem.viewKeyMap = mTaskMap;
        taskItem.viewThreadMap = mThreadMap;
        taskItem.threadLock = mThreadLock;
        taskItem.cache = mCache;
        taskItem.reply = mHandler.obtainMessage();
        taskItem.sleep = mConfig.debugSleep;
        taskItem.imageProcessor = imageProcessor;
        taskItem.loadObject = loadObject;

        // can't proceed without a valid key
        if (key == null) {
            if (DBG) Log.d(TAG, "no valid key for " + loadObject);
            if (!imageProcessor.handleLoadError(view, taskItem)) {
                if (DBG) Log.d(TAG, "setting loading drawable");
                imageProcessor.setLoadingDrawable(view, mDefaultDrawable);
            }
            mTaskMap.remove(view);
            return;
        }

        if (mConfig.useCache) {
            Bitmap bitmap = mCache.get(key);
            if (bitmap != null) {
                if (DBG) Log.d(TAG, "Found Bitmap in cache: " + key);
                taskItem.result.bitmap = bitmap;
                taskItem.result.status = LoadResult.Status.LOAD_OK;
                imageProcessor.setResult(view, taskItem);
                mTaskMap.remove(view);
                // task done.
                return;
            }
        }
        // still here == nothing from cache, set default image / null

        if (DBG) Log.d(TAG, "No cache result, set loading drawable for now");
        imageProcessor.setLoadingDrawable(view, mDefaultDrawable);

        mTaskMap.put(view, key);
        if (mConfig.debugNoThreads) {
            new LoadTaskExecutor(taskItem).run();
        } else {
            ensureExecutor();
            mExecutorService.execute(new LoadTaskExecutor(taskItem));
        }
    }

    /** 
     * invalidates tasks for this view, safe to call several times, use from
     * UI thread only
     */
    public void stopLoading(ImageView view) {
        mTaskMap.remove(view);
        if (mThreadMap != null) {
            // interrupt thread working on view if there is one
            mThreadLock.lock(view);
            try {
                Thread worker = mThreadMap.remove(view);
                if (worker != null) {
                    Log.d(TAG, "interrupting " + worker.getId());
                    worker.interrupt();
                }
            } finally {
                mThreadLock.unlock(view);
            }
        }
    }

    /** invalidates all tasks, use from UI thread only */
    public void stopLoadingAll() {
        mTaskMap.clear();
        if (mConfig.interruptThreads && mExecutorService != null) {
            // easiest way to interrupt all threads
            mExecutorService.shutdownNow();
        }
    }

    /** stop using all the memories */
    public void clearCache() {
        if (mCache != null)
            mCache.clear();
    }

    // ----------------------------- private -------------------------------- //
    /** (re)creates mExecutorService if necessary */ 
    private void ensureExecutor() {
        if (mConfig.debugNoThreads)
            return;
        if (mExecutorService == null || mExecutorService.isShutdown() || mExecutorService.isTerminated()) {
            mExecutorService = Executors.newFixedThreadPool(mConfig.threadPoolSize,
                    new ThreadPriorityFactory(mConfig.threadPriority));
        }
    }

    /** Handler executed by main looper handling callbacks from background tasks */
    private static class ForegroundHandler implements Handler.Callback {
        public ForegroundHandler() { /* empty */ }

        public boolean handleMessage(Message msg) {
            LoadTaskItem task = (LoadTaskItem) msg.obj;
            ImageView view = task.getViewIfValid();
            if (view != null) {
                if (DBG) Log.d(TAG, "From background - valid: " + task.key);
                switch (task.result.status) {
                    case LOAD_OK:
                        task.imageProcessor.setResult(view, task);
                        break;
                    case LOAD_ERROR:
                    case LOAD_BAD_OBJECT:
                    case LOAD_UNFINISHED:
                        task.imageProcessor.handleLoadError(view, task);
                        break;
                    default:
                        break;
                }
                task.viewKeyMap.remove(view);
            } else {
                if (DBG) Log.d(TAG, "From background - no longer valid: " + task.key);
            }
            return true;
        }
    }

    /** creates threads with configurable priority */
    private static class ThreadPriorityFactory implements ThreadFactory {
        private final int mPriority;
        public ThreadPriorityFactory(int priority) {
            mPriority = priority;
        }

        public Thread newThread(Runnable r) {
            if (DBG) Log.d(TAG, "NEW THREAD");
            Thread t = new Thread(r, "ImageLoaderThread");
            t.setPriority(mPriority);
            return t;
        }
    }

    /** creates images of text for internal testing */
    private static Bitmap DebugImage(String text, int width, int height) {
        Bitmap bm = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        c.drawColor(Color.RED);
        Paint p = new Paint();
        p.setTextAlign(Paint.Align.CENTER);
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        p.setTextSize(20);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        float textWidth = p.measureText(text);
        float newTextSize = width / textWidth * 19.5f;
        p.setTextSize(newTextSize);
        Rect r = new Rect();
        p.getTextBounds("X", 0, 1, r);
        int textHeight = r.top - r.bottom;
        c.drawText(text, width / 2f, (height - textHeight) / 2f, p);
        return bm;
    }
}
