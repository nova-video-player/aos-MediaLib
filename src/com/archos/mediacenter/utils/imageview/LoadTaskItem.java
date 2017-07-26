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

import android.os.Message;
import android.util.Log;
import android.widget.ImageView;

import com.archos.mediascraper.MultiLock;

import java.lang.ref.WeakReference;
import java.util.Map;

/**
 * Provides context for a task
 */
public class LoadTaskItem implements Cloneable {
    // internal stuff passed on but not intended to be used
    /** internal reference to the target imageview, to get use {@link #getViewIfValid()}, don't modify */
    /* default */ WeakReference<ImageView> weakView;
    /** internal key identifying the image, don't modify */
    /* default */ String key;
    /** internal global map from imageview to key, don't modify */
    /* default */ Map<ImageView, String> viewKeyMap;
    /** internal map used for thread interruption, may be null, don't modify */
    /* default */ Map<ImageView, Thread> viewThreadMap;
    /** internal lock used for thread interruption, may be null, don't modify */
    /* default */ MultiLock<ImageView> threadLock;
    /** internal Message object that gets send back to UI thread handler */
    /* default */ Message reply;
    /** internal cache for images by {@link key}, null if cache disabled */
    /* default */ BitmapMemoryCache cache;
    /** internal, true if debug sleep is configured */
    /* default */ boolean sleep;
    /** internal reference to the processor of the current task */
    /* default */ ImageProcessor imageProcessor;

    // part that is intended to be used by implementations
    /** object to load, this is of interest for IImageProcessor implementations */
    public Object loadObject;
    /** result of loading objects, set subitems of this in {@link ImageProcessor#loadBitmap(LoadTaskItem)} */
    public final LoadResult result = new LoadResult();

    /**
     * @return the ImageView if the task is still valid, null otherwise
     */
    /* default */ ImageView getViewIfValid() {
        ImageView ret = null;
        if (weakView != null && key != null) {
            ImageView view = weakView.get();
            if (view != null) {
                String currentKey = viewKeyMap.get(view);
                if (key.equals(currentKey)) {
                    ret = view;
                }
            }
        }
        return ret;
    }

    /**
     * @return true if this task item still needs processing, does not check thread interrupt status
     */
    public boolean taskStillValid() {
        return getViewIfValid() != null;
    }

    /** adds this thread to the thread map & interrupts old thread that was mapped */
    /* default */ void putThreadMapping() {
        if (viewThreadMap != null && weakView != null && key != null) {
            ImageView view = weakView.get();
            if (view != null) {
                threadLock.lock(view);
                // only this thread can change viewThreadMap for view now
                try {
                    String currentKey = viewKeyMap.get(view);
                    if (key.equals(currentKey)) {
                        Thread me = Thread.currentThread();
                        Thread other = viewThreadMap.put(view, me);
                        // interrupt other thread that was working on this view
                        if (other != null && other != me) {
                            if (ImageViewSetter.DBG) {
                                Log.d(ImageViewSetter.TAG, "LoadTaskItem#putThreadMapping interrupting thread working on " + currentKey);
                            }
                            other.interrupt();
                        }
                    }
                } finally {
                    threadLock.unlock(view);
                }
            }
        }
    }

    /** removes this thread from thread map */
    /* default */ void removeThreadMapping() {
        if (viewThreadMap != null && weakView != null && key != null) {
            ImageView view = weakView.get();
            if (view != null) {
                threadLock.lock(view);
                // only this thread can change viewThreadMap for view now
                try {
                    String currentKey = viewKeyMap.get(view);
                    if (key.equals(currentKey)) {
                        // this thread is still the correct thread to work on view
                        Thread me = Thread.currentThread();
                        Thread other = viewThreadMap.remove(view);
                        // me and other should be the same, if not code is broken somewhere
                        if (me != other) {
                            Log.e(ImageViewSetter.TAG, "LoadTaskItem#removeThread() failed!");
                        }
                    }
                } finally {
                    threadLock.unlock(view);
                }
            }
        }
    }

    /**
     * @return this.clone()
     */
    /* default */ LoadTaskItem duplicate() {
        try {
            return (LoadTaskItem) clone();
        } catch (CloneNotSupportedException e) {
            Log.e(ImageViewSetter.TAG, "LoadTaskItem duplicate()", e);
            return null;
        }
    }
}