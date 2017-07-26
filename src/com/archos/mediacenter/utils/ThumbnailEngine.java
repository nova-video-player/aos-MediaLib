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

package com.archos.mediacenter.utils;

import com.archos.mediacenter.utils.ThumbnailRequest;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.util.Log;

import java.util.AbstractList;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;


public abstract class ThumbnailEngine {
	private final static String TAG = "ThumbnailEngine";
	private final static boolean DBG = false;
	private final static boolean DBG2 = false;

	/**
	 * Base footprint for mdpi devices. Actual footprint will be larger on hdpi and xhdpi devices.
	 */
	private final static int THUMBNAILS_POOL_BASE_FOOTPRINT_IN_BYTES = 5 * 1024 * 1024;	//MAGICAL
	
	/**
	 *  Interface to implement to get the info when a thumbnail is ready
	 *  See @setListener()
	 */
	public interface Listener {
		/**
		 * Called when a thumbnail is ready
		 * @param request: the original request
		 * @param thumbnail: the computed thumbnail
		 */
		public void onThumbnailReady(ThumbnailRequest request, Result thumbnail);
		
		/**
		 * Called when all the requestS from last newRequestsCancellingOlderOnes call are done
		 */
		public void onAllRequestsDone();
	}
	
	/**
	 * Application context
	 */
	protected Context mContext;
	
	/**
	 * Content resolver
	 */
	protected ContentResolver mContentResolver;
	
	/**
	 * The one getting the info when a thumbnail is ready
	 */
	private Listener mListener;
	/**
	 * The Handler/Thread on which to execute the listener callback
	 */
	private Handler mListenerHandler;
	/** Lock Object to synchronize on when using / modifying mListenerHandler */
	private final Object mListenerLock = new Object();

	/**
	 * The size of the thumbnails
	 */
    protected int mThumbnailWidth;
	protected int mThumbnailHeight;

	/**
	 * The thumbnail building thread
	 */
	private final ThumbnailThread mThumbnailThread;

	/**
	 * The already computed results. The key is the MediaDB ID
	 */
	private LruCache<Object, Result> mResultsPool;
	
	/**
	 * The result object returned by the Thumbnail engine 
	 * Contains the Thumbnail.
	 */
	public static class Result {
		/**
		 * The thumbnail
		 */
		private final Bitmap mThumb;
		/**
		 * True if the listener has been notified of the result (Not the case in case of an abort)
		 */
		private boolean mListenerHasBeenNotified;
		
		public Result(Bitmap thumb) {
			mThumb = thumb;
			mListenerHasBeenNotified = false;
		}
		
		public boolean isValid() {
			return (mThumb!=null);
		}

		public Bitmap getThumbnail() {
			return mThumb;
		}

		public boolean hasListenerBeenNotified() {
			return mListenerHasBeenNotified;
		}
		
		private void listenerHasBeenNotified() {
			mListenerHasBeenNotified = true;
		}

        public boolean needRefresh(ThumbnailRequest request) {
            // basic result never need refresh. Only some derived class does
            return false;
        }
	}
	
	/**
	 * Private constructor (it's a singleton)
	 */
	protected ThumbnailEngine(Context context) {
		mContext = context;
		mContentResolver = context.getContentResolver();
		mThumbnailThread = new ThumbnailThread();
		mThumbnailThread.start();
	}

	/**
	 * Setup the one getting the info when a thumbnail is ready
	 * @param listener
	 * @param handler The Handler/Thread on which to execute the listener callback
	 */
	public void setListener(Listener listener, Handler handler) {
        if(DBG) Log.d(TAG, "setListener("+listener+")");
        synchronized (mListenerLock) {
            mListener = listener;
            mListenerHandler = handler;
        }
	}
	
	/**
	 * @param width
	 * @param height
	 */
    public void setThumbnailSize(int thumbnailWidth, int thumbnailHeight) {
        if(DBG) Log.d(TAG, "setThumbnailSize : " + thumbnailWidth + "x" + thumbnailHeight);

		if (thumbnailWidth != mThumbnailWidth || thumbnailHeight != mThumbnailHeight) {
        	if(DBG) Log.d(TAG, "Clearing the thumbnail cache " + thumbnailWidth +"/"+ mThumbnailWidth+" "+thumbnailHeight+"/"+mThumbnailHeight);
        	clearThumbnailCache();
            // The hard-coded base footprint is for a regular mdpi device.
            // We need more for hdpi or xhdpi devices
            final float density = mContext.getResources().getDisplayMetrics().scaledDensity;
            final float actualFootprint = THUMBNAILS_POOL_BASE_FOOTPRINT_IN_BYTES * density * density;
        	// Compute the number of thumbnails to *approximately* fit the memory footprint we want
        	// We guess thumbs are RGB888 -> 3 bytes per pixel
        	// We don't take the data added by the derived classes (ThumbnailEngineVideo for example) into account
            int numberOgThumbs = (int)(actualFootprint/(thumbnailWidth*thumbnailHeight*3));
            if(DBG) Log.d(TAG, "setThumbnailSize: pool size = "+numberOgThumbs);
        	mResultsPool =  new LruCache<Object, Result>(numberOgThumbs);
        }

		mThumbnailWidth = thumbnailWidth;
		mThumbnailHeight = thumbnailHeight;
    }
    
    /**
     * Caution: is overridden by child class to clean more things
     */
    protected void clearThumbnailCache() {
    	if (mResultsPool!=null) {
	    	synchronized (mResultsPool) {
	    		mResultsPool.clear();
			}
    	}
    }

    /**
     * Put thumbnail in the pool
     */
    protected void putResultInPool(long dbid, Result result) {
        synchronized (mResultsPool) {
            mResultsPool.put(Long.valueOf(dbid), result);
        }
    }

    /**
     * Put thumbnail in the pool
     */
    protected void putResultInPool(Object key, Result result) {
        synchronized (mResultsPool) {
            mResultsPool.put(key, result);
        }
    }

    /**
     * Get an already computed thumbnail. Returns null if not available.
     */
    public Result getResultFromPool(long dbId) {
    	synchronized (mResultsPool) {
    		return mResultsPool.get(Long.valueOf(dbId));
		}
    }

    /**
     * Get an already computed thumbnail. Returns null if not available.
     */
    public Result getResultFromPool(Object key) {
        synchronized (mResultsPool) {
            return mResultsPool.get(key);
        }
    }

    /**
     * Remove a result (i.e. Thumbnail) from the pool of the already computed stuff
     */
    public void removeResultFromPool(long dbId) {
    	synchronized (mResultsPool) {
    		mResultsPool.put(Long.valueOf(dbId), null);
    	}
    }

    /**
     * Remove a result (i.e. Thumbnail) from the pool of the already computed stuff
     */
    public void removeResultFromPool(Object key) {
        synchronized (mResultsPool) {
            mResultsPool.put(key, null);
        }
    }

    /**
     * Stop the engine, but only in case the listener is the given one.
     * It means that it won't be stopped in case another listener has been registered
     * @param listener
     */
    public void cancelPendingRequestsForThisListener(Listener listener) {
        if (listener == mListener) {
            if(DBG) Log.d(TAG, "cancelPendingRequestsForThisListener: yes");
            cancelPendingRequests();
        } else {
            if(DBG) Log.d(TAG, "cancelPendingRequestsForThisListener: no");
        }
    }

    /**
	 * Stop the engine
	 */
	private void cancelPendingRequests() {
		if(DBG) Log.d(TAG, "cancelPendingRequests");
		mThumbnailThread.cancelTasks();
	}
	
	/**
	 * Set a new request. Cancel all the previous ones.
	 * @param request
	 */
	public void newRequestsCancellingOlderOnes(AbstractList<ThumbnailRequest> requests) {
		if(DBG) Log.d(TAG, "newRequestsCancellingOlderOnes ("+requests.size()+" items)");
		mThumbnailThread.cancelTasks();
		mThumbnailThread.addTasks(requests);
	}

	/**
	 * The actual processing of the thumbnail
	 */
	protected abstract Result computeThumbnail(ThumbnailRequest request);

    /**
     * The thread computing the thumbnails.
     */
    private class ThumbnailThread extends Thread {
        private static final String TAG = ThumbnailEngine.TAG + "TThread";
        private static final boolean DBG = false;

        /**
         * The request queue this thread is consuming
         */
        private final BlockingQueue<ThumbnailRequest> mQueue = new ArrayBlockingQueue<ThumbnailRequest>(1024);

        private static final int STATE_NO_TASK = 0;
        private static final int STATE_WORKING = 1;
        private static final int STATE_INCOMING_CLEAR = 2;
        /**
         * Used to invalidate running tasks.
         * Regular flow is STATE_NO_TASK -> STATE_WORKING -> STATE_NO_TASK
         */
        private final AtomicInteger mTaskState = new AtomicInteger(STATE_NO_TASK);

        public ThumbnailThread() {
            super("ThumbnailThread");
            if (DBG) Log.d(TAG, "CTOR");
            setPriority(Thread.MIN_PRIORITY);
        }

        public void addTasks(Collection<? extends ThumbnailRequest> requests) {
            if (DBG) Log.d(TAG, "addTasks(n=" + requests.size() + ")");
            for (ThumbnailRequest thumbnailRequest : requests) {
                if (!mQueue.offer(thumbnailRequest)) {
                    Log.e(TAG, "Queue full, can't handle more requests");
                }
            }
        }

        /** cancels all requests */
        public void cancelTasks() {
            if (DBG) Log.d(TAG, "cancelTasks");

            // 3 step procedure guarantees clearing and invalidating the queue
            // since we can't do that atomically

            // 1. start out by invalidating the queue, thread will not process
            // tasks after this point.
            mTaskState.set(STATE_INCOMING_CLEAR);
            // 2. now clear the queue
            mQueue.clear();
            // 3. invalidated running tasks by setting them back to 0
            mTaskState.set(STATE_NO_TASK);
        }

        /** stops this thread */
        public void stopThread() {
            if (DBG) Log.d(TAG, "stopThread");
            this.interrupt();
        }

        private void notifyAllDone() {
            if (DBG) Log.d(TAG, "notifyAllDone");
            // synchronized since mListenerHandler can be set to null asynchronously
            synchronized (mListenerLock) {
                if (mListenerHandler != null) {
                    // Tell the listener that the current request are all done
                    mListenerHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if(mListener!=null) //Listener can be removed between notifyAllDone and .post, so we have to check again
                                mListener.onAllRequestsDone();
                        }
                    });
                } else if (DBG2) {
                    Log.d(TAG, "mListenerHandler is null, skipping callback");
                }
            }
        }

        private void notifyResult(final ThumbnailRequest request, final Result result) {
            if (DBG) Log.d(TAG, "notifyResult");
            // synchronized since mListenerHandler can be set to null asynchronously
            synchronized (mListenerLock) {
                if (mListenerHandler != null) {
                    mListenerHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if(mListener!=null) //Listener can be removed between notifyAllDone and .post, so we have to check again
                                mListener.onThumbnailReady(request, result);
                        }
                    });
                } else if (DBG2) {
                    Log.d(TAG, "mListenerHandler is null, skipping callback");
                }
            }
        }

        private Result process(ThumbnailRequest request) {
            if (DBG) Log.d(TAG, "process");
            Result result = null;

            if (request != null) {
                if(DBG2) Log.d(TAG, "Processing request " + request);
                // First check if it is not done and in the pool already
                result = getResultFromPool(request.getKey());
                boolean needToComputeThumbnail = result == null || result.needRefresh(request);

                if (needToComputeThumbnail) { // not found or need refresh, build it
                    if(DBG2) Log.d(TAG, "Building thumbnail for request " + request);
                    try {
                        result = computeThumbnail(request);
                        // Store result in pool
                        putResultInPool(request.getKey(), result);
                    } catch (OutOfMemoryError oom) {
                        Log.e(TAG, "run: OutOfMemoryError", oom);
                    }
                }
            }

            return result;
        }

        private void handleResult(ThumbnailRequest request, Result result) {
            if (DBG) Log.d(TAG, "handleResult");
            if ( result.isValid() &&                    // ...if the thumbnail is valid
                (!result.hasListenerBeenNotified()))    // ...if the listener has not been notified already about this result
                {
                    result.listenerHasBeenNotified();
                    notifyResult(request, result);
                }
        }

        @Override
        public void run() {
            try {
                while (!Thread.interrupted()) {
                    ThumbnailRequest request;
                    if (DBG) Log.d(TAG, "run: waiting for task");
                    // blocking in next call until the queue has an element
                    request = mQueue.take();

                    // don't process task if incoming clear
                    if (mTaskState.compareAndSet(STATE_NO_TASK, STATE_WORKING)) {
                        // if queue was valid process task
                        Result result = process(request);

                        // unless task was set back to STATE_NO_TASK publish the result
                        if (mTaskState.compareAndSet(STATE_WORKING, STATE_NO_TASK)) {
                            if (result != null)
                                handleResult(request, result);

                            // when queue is empty notify about that.
                            if (mQueue.peek() == null)
                                notifyAllDone();
                        } else if (DBG) Log.d(TAG, "run: task aborted");
                    } else if (DBG) Log.d(TAG, "run: queue invalid");
                }
            } catch (InterruptedException e) {
                // interrupted while doing things, just end.
            }
        }

    }

}
