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

package com.archos.mediacenter.cover;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.util.concurrent.Semaphore;
import java.util.LinkedList;

public class TextureProvider {

	static final String TAG = "TextureProvider";
	static final boolean DBG = false;

	// MSG_TEXTURE_READY is sent to the "listener" Handler when a texture is ready
	public final static int MSG_TEXTURE_BITMAP_READY = 42;

	// MSG_TEXTURE_ERROR is sent to the "listener" Handler when a texture failed to build
	public final static int MSG_TEXTURE_BITMAP_ERROR = 666;

	// internal States
	private final static int STATE_STOPPED = 0;
	private final static int STATE_RUNNING = 1;

	private int mState = STATE_STOPPED;

	// Thread priority:
	// - With Thread.MIN_PRIORITY, the rendering is very smooth, the textures are loaded very quickly
	//   when you don't move too much, and quite slowly when you scroll a lot in the list
	// - With Thread.NORM_PRIORITY, the rendering is very jerky, and the texture loading doesn't seem much faster
	private final static int THREAD_PRIORITY = Thread.MIN_PRIORITY;

	private final Context mContext;
	private Handler mListener;
	private ArtworkFactory mArtworkFactory;

	// Store the requests for Textures
	private final LinkedList<TextureRequest> mRequestList;

	private Semaphore mBuildSema = null;

	private BuildTextureThread mBuildThread = null;

	// Nested class for Thread in charge of building the textures
	private class BuildTextureThread extends Thread {
		boolean mAbort = false;
		Handler mListener = null;

		public void setListener(Handler listener) {
			mListener = listener;
		}
		public void abort()  {
			mAbort = true;
		}
		public void run(){
			while(!mAbort) {
				try {
					mBuildSema.acquire();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				// Get first texture to compute in the pending list
				TextureRequest tr = null;
				synchronized (mRequestList) {
					if (!mAbort) { // may have been aborted while waiting for the semaphore!
						tr = mRequestList.poll();
					}
				}
				if (tr != null) { // should not have to test that, but sometimes I get semaphore even if the pending list is empty...

					boolean ret;
					// Build the texture, synchronous call
					ret = tr.makeBitmap(mContext, mArtworkFactory);

					// Send OK or KO message to listener (if we are not aborting)
					if ((mListener!=null) && !mAbort) {
						if (ret) {
							Message msg = mListener.obtainMessage(MSG_TEXTURE_BITMAP_READY);
							msg.obj = (Object)tr;
							if(DBG) Log.d(TAG, "Sending MSG_TEXTURE_READY for texture " + tr.getDebugName());
							mListener.sendMessage(msg);
						}
						else {
							Message msg = mListener.obtainMessage(MSG_TEXTURE_BITMAP_ERROR);
							msg.obj = (Object)tr;
							if(DBG) Log.d(TAG, "Sending MSG_TEXTURE_ERROR for texture " + tr.getDebugName());
							mListener.sendMessage(msg);
						}
					}
				}
			}
		}
	}

	public TextureProvider(Context context) {
		mContext = context;
		mRequestList = new LinkedList<TextureRequest>();
	}

	// Start/Resume the texture building thread
	public void start() {
		if (mState == STATE_STOPPED) {
			if(DBG) Log.d(TAG, "start: launching a new thread");
			createAndStartThread();
			mState = STATE_RUNNING;
		}
		else {
			Log.e(TAG, "start: state error " + mState);
		}
	}

	// Stop the texture building thread
	// Returns once the thread is actually stopped
	public void stop(boolean blocking) {

		if (mState == STATE_RUNNING) {
			// empty the request list
			synchronized (mRequestList) {
				mRequestList.clear();
			}
			// Stop the thread
			mBuildThread.abort();
			mBuildSema.release(); // Be sure to release the thread in case it is waiting for the semaphore
			if (blocking) {
				try {
					mBuildThread.join();
					if(DBG) Log.d(TAG, "stop_blocking: texture thread stopped");
				} catch (InterruptedException e) {
					Log.e(TAG, "stop_blocking: error joining the texture thread", e);
					e.printStackTrace();
				}
			}
			mState = STATE_STOPPED;
		}
		else {
			Log.e(TAG, "pause: state error " + mState);
		}
	}

	public void setListener(Handler listener) {
		mListener = listener;
		if (mBuildThread != null) {
			mBuildThread.setListener(mListener);
		}
	}
	public void setArtworkFactory(ArtworkFactory factory) {
		mArtworkFactory = factory;
	}

	// add in the list, but do not build it
	public void requestTexture(TextureRequest t) {
		// put in the pending list
		synchronized (mRequestList) {
			mRequestList.addLast(t);
		}
		// tell the thread to process it
		mBuildSema.release();
	}

	// PRIVATE PRIVATE PRIVATE PRIVATE PRIVATE PRIVATE PRIVATE PRIVATE

	private void createAndStartThread() {
		mBuildThread = new BuildTextureThread();
		mBuildThread.setListener(mListener);
		mBuildThread.setPriority(THREAD_PRIORITY);

		// Semaphore intended to ping the building thread when a new texture is asked
		mBuildSema = new Semaphore(0);

		// Go, fight!
		mBuildThread.start();
	}
}
