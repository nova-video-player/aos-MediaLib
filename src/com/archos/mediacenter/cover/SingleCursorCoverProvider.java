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

import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;

import com.archos.mediacenter.utils.MediaUtils;


public abstract class SingleCursorCoverProvider extends CoverProvider implements LoaderManager.LoaderCallbacks<Cursor> {

	static final String TAG = "SingleCursorCoverProvider";
	static final boolean DBG = false;

	protected Cursor mCursor;

	public SingleCursorCoverProvider(Context context) {
	    super(context);
	}

	/**
	 * These functions are to be implemented by child classes
	 */

	protected abstract CursorLoader getCursorLoader();

	/**
	 * Allow to indentify each type of loader
	 */
	protected abstract int getLoaderManagerId();

	/**
	 * Convert the result of the query into a list of covers
	 * @param cursor	in
	 * @param update	true if this in an update, false if this is first time request
	 * @param ready		out: true if the provider is ready to provide covers (false in case several queries have been sent, and still waiting for at least one)
	 * @return covers to free (because they are not in the cursor anymore)
	 */
	protected abstract Collection<Cover> convertCursorToCovers(Cursor c, boolean update);
	//----------------------------------------------------------

	/**
	 *	Start the asynchronous request
	 *	Result will be received through the given listener
	 */
	@Override
	public void start(LoaderManager loaderManager, Listener listener) {
		if(DBG) Log.d(TAG, "start");
		super.start(loaderManager, listener);

		loaderManager.initLoader( getLoaderManagerId(), null, this);
	}

	/**
	 *	Free cursor and observer
	 */
	@Override
	public void stop() {
		if(DBG) Log.d(TAG, "stop");
		super.stop();

		mCursor = null;   // The framework will take care of closing the old cursor
		mCoverIdMap = null;

		mLoaderManager.destroyLoader(getLoaderManagerId());
	}

    /**
     * Implements LoaderManager.LoaderCallbacks<Cursor>
     * @param id
     * @param args
     * @return
     */
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if(DBG) Log.d(TAG, "onCreateLoader "+id);
        return getCursorLoader();
    }

    /**
     * Implements LoaderManager.LoaderCallbacks<Cursor>
     * @param loader
     * @param data
     */
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        final boolean thisIsAnUpdate = (mCursor!=null);
        if(DBG) Log.d(TAG, "onLoadFinished update="+thisIsAnUpdate);
        mCursor = data; // Keep it locally to handle updates later

        if (mCursor == null || !LibraryUtils.hasStorage()) {
            mErrorMessage = MediaUtils.getDatabaseErrorMessage(mContext);
            Log.e(TAG,"Error: " + mErrorMessage);
            mListener.coversLoadingError(this, mErrorMessage);
            return;
        }
        if (!thisIsAnUpdate) {
            convertCursorToCovers(mCursor, false);
            // don't deactivate the cursor, in order to get updates
            if (mCoverArray.size()==0) {
                // in that case mErrorMessage should have been set by convertCursorToCovers()
                if(DBG) Log.d(TAG, "onLoadFinished: no covers: "+mErrorMessage);
                mListener.coversLoadingError(this, mErrorMessage);
            } else {
                if(DBG) Log.d(TAG, "onLoadFinished calls coversAreReady()");
                mListener.coversAreReady(this);
            }
        }
        else {
            ArrayList<Cover> oldCovers = mCoverArray; // keep track of the current collection before updating
            Collection<Cover> coversToFree = convertCursorToCovers(mCursor, true);
            if (DBG && coversToFree!=null) {
                Log.d(TAG, "onLoadFinished: Cover to Recycle = " + coversToFree.size());
            }
            // Check if the covers have actually changed (content can be reported changed but with no impact on the Cover collection)
            boolean actualUpdate = false;
            // first check obvious sign of actual update
            actualUpdate |= ((coversToFree!=null) && !coversToFree.isEmpty());
            actualUpdate |= (mCoverArray.size()!=oldCovers.size());
            if (!actualUpdate) {
                for (int i=0; i<oldCovers.size(); i++) {
                    final Cover oldc = oldCovers.get(i);
                    final Cover newc = mCoverArray.get(i);
                    // worth checking that first, since when there is no changes, the covers will often be the same Objects
                    if (!oldc.equals(newc) && !oldc.getCoverID().equals( newc.getCoverID())) {
                        actualUpdate = true;
                        break;
                    }
                }
            }
            if (actualUpdate) {
                if(DBG) Log.d(TAG, "onChange calls coversAreUpdated()");
                mListener.coversAreUpdated(this, coversToFree, null);
            } else {
                if(DBG) Log.d(TAG, "onChange: nothing to actually update");
            }
        }
    }

    /**
     * Implements LoaderManager.LoaderCallbacks<Cursor>
     * This is called when the last Cursor provided to onLoadFinished()
     * above is about to be closed.  We need to make sure we are no
     * longer using it.
     * @param loader
     */
    public void onLoaderReset(Loader<Cursor> loader) {
        if(DBG) Log.d(TAG, "onLoaderReset");
    }
}
