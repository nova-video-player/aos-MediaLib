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

import android.database.DataSetObserver;
import android.util.Log;
import android.widget.AbsListView;

import java.util.ArrayList;

/**
 * As it is now it should be called ThumbnailRequesterVideo...
 * May evolve later to be Video/Music/Anything agnostic
 */
public abstract class ThumbnailRequester extends DataSetObserver  implements AbsListView.OnScrollListener{
	private final static String TAG = "ThumbnailRequester";
	private final static boolean DBG = false;
	private final static boolean DBG2 = false;

	private final static int BEFORE = -1;
	private final static int CENTER = 0;
	private final static int AFTER = +1;
	
	private final ThumbnailEngine mEngine;
	protected final ThumbnailAdapter mAdapter;
	
	private int mFirstVisibleItem;
	private int mVisibleItemCount;
	private int mTotalItemCount;
	
	private int mCurrentListItemsToCompute;

	/**
	 * Create a ThumbnailRequester associated with a ThumbnailEngine
	 * @param engine: the one and only ThumbnailEngine to which this requester is attached to
	 */
	public ThumbnailRequester(ThumbnailEngine engine, ThumbnailAdapter adapter) {
		mEngine = engine;
		mAdapter = adapter;
		reset();
		
		mAdapter.registerDataSetObserver(this);
	}

	/**
	 *
	 * @param position
	 * @param debugLog
	 * @return
	 */
	public abstract ThumbnailRequest getThumbnailRequest(int position, String debugLog);

	public void reset() {
		if(DBG) Log.d(TAG, "reset");
		mFirstVisibleItem = -1;
		mVisibleItemCount = -1;
		mTotalItemCount = -1;
		mCurrentListItemsToCompute = CENTER;
	}
	
	/**
	 * Check if some thumbnails are needed.
	 * To be called to restart the Thumbnail engine after is has been stopped, for some reason.
	 * ('listView' has to be given by the caller because ThumbnailRequester doesn't know it (it only gets callbacks from it))
	 */
	public void refresh(AbsListView listView) {
		// First be sure that onScrollStateChanged() won't think it has not moved, hence need no request
		reset();
		// Sanity check
		if (listView.getCount()<1) {
			return;
		}
		// Simulate an onScroll call
		onScroll( listView, listView.getFirstVisiblePosition(),
				listView.getLastVisiblePosition()-listView.getFirstVisiblePosition(),
				listView.getCount());
	}
	
	/**
	 * Overrides {@link DataSetObserver}
	 */
	@Override
	public void onChanged() {
		if(DBG) Log.d(TAG, "onChanged()");
		reset();
		super.onChanged();
	}
	
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		if(DBG) Log.d(TAG, "onScrollStateChanged "+scrollState);
	}

	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		if(DBG) Log.d(TAG, "onScroll: "+firstVisibleItem+" > "+(firstVisibleItem+visibleItemCount)+" -----------------------");

		// Workaround, should not happen
		if (firstVisibleItem==-1) {
			if(DBG2) Log.d(TAG, "onScroll: firstVisibleItem==-1, return");
			return;
		}
		
		// Empty list
		if (visibleItemCount <= 0) {
			if(DBG2) Log.d(TAG, "onScroll: visibleItemCount<=0, return");
			return;
		}

		// When onScroll occurs, we don't care about thumbnails above screen and below screen => reset to CENTER
		mCurrentListItemsToCompute = CENTER;
		
		// Position didn't change
		if ((firstVisibleItem == mFirstVisibleItem) && (visibleItemCount == mVisibleItemCount)) {
			if(DBG2) Log.d(TAG, "onScroll: same call than last call, return | " + firstVisibleItem + " " + visibleItemCount);
			return;
		}

		// Remember last call
		mFirstVisibleItem = firstVisibleItem;
		mVisibleItemCount = visibleItemCount;
		mTotalItemCount = totalItemCount;

		// Build the request list
		ArrayList<ThumbnailRequest> requests = new ArrayList<ThumbnailRequest>(visibleItemCount);
		String dbgString = "";

		for (int i=0; i<visibleItemCount; i++) {
			final int n = i + firstVisibleItem;
			if (mAdapter.doesItemNeedAThumbnail(n)) {
			    final ThumbnailRequest tr = getThumbnailRequest(n, dbgString);
			    if (tr!=null) {
			        requests.add(tr);
			    }
			}
		}
		if (requests.size()>0) {
		    if(DBG) Log.d(TAG, "onScroll: Requesting "+dbgString);
		    mEngine.newRequestsCancellingOlderOnes(requests);
		}
	}
	
	public void onAllRequestsDone() {
		if(DBG) Log.d(TAG, "onAllRequestsDone");
		
		ArrayList<ThumbnailRequest> requests = null;
		String dbgString = "";
		
		if (mCurrentListItemsToCompute == CENTER) {
			// prefetch the thumbnails below now
			mCurrentListItemsToCompute = AFTER;
			if(DBG) Log.d(TAG, "onAllRequestsDone: AFTER");
			int count=mVisibleItemCount;
			int max = mFirstVisibleItem + mVisibleItemCount + count;
			if (max>mTotalItemCount) {
				count -= (max-mTotalItemCount);
			}
			if(DBG) Log.d(TAG, "count = "+count);
			if (count>0) {
				requests = new ArrayList<ThumbnailRequest>(count);
				for (int i=0; i<count; i++) {
					final int n = mFirstVisibleItem + mVisibleItemCount + i;
					if (mAdapter.doesItemNeedAThumbnail(n)) {
						final ThumbnailRequest tr = getThumbnailRequest(n, dbgString);
						if (tr!=null) {
						    requests.add(tr);
						}
					}
				}
			}
		}
		else if (mCurrentListItemsToCompute == AFTER) {
			// prefetch the thumbnails above now
			mCurrentListItemsToCompute = BEFORE;
			if(DBG) Log.d(TAG, "onAllRequestsDone: BEFORE");
			int count=mVisibleItemCount;
			int min = mFirstVisibleItem - count;
			if (min<0) {
				count += min;
			}
			if(DBG) Log.d(TAG, "count = "+count);
			if (count>0) {
				requests = new ArrayList<ThumbnailRequest>(count);
				for (int i=0; i<count; i++) {
					final int n = mFirstVisibleItem - 1 - i;
					if (mAdapter.doesItemNeedAThumbnail(n)) {
                        final ThumbnailRequest tr = getThumbnailRequest(n, dbgString);
                        if (tr!=null) {
                            requests.add(tr);
                        }
					}
				}
			}
		}
		else {
			// before and after already prefetched. nothing more to do 
			return;
		}
		
		// Send the request
		if ((requests!=null) && (requests.size() > 0)) {
			if(DBG) Log.d(TAG, "onAllRequestsDone: Requesting "+dbgString);
			mEngine.newRequestsCancellingOlderOnes(requests);
		}
	}

	/**
	 * Returns true if the request is still matching the content of the list (it may have been changed since the request was sent)
	 * Default implementation always returns true
	 */
	public boolean isRequestStillValid(ThumbnailRequest request) {
		return true;
	}
}
