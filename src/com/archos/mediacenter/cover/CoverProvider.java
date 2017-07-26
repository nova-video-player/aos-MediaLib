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
import android.net.Uri;
import android.util.Log;

import com.archos.mediaprovider.music.MusicStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;


public abstract class CoverProvider {

	static final String TAG = "CoverProvider";
	static final boolean DBG = false;
	
	/**
	 * Each Cover provider sub-class must have a different ID for LoaderManager to make the difference between the cursor loaders
	 */
	protected static final int MUSIC_ALL_ALBUMS_LOADER_ID = 101;
	protected static final int MUSIC_ALL_ARTISTS_LOADER_ID = 102;
	protected static final int MUSIC_RECENTLY_ADDED_LOADER_ID = 103;
	protected static final int MUSIC_RECENTLY_PLAYED_LOADER_ID = 104;
	protected static final int MUSIC_FAVORITE_TITLES_LOADER_ID = 105;
	protected static final int MUSIC_FAVORITE_ALBUMS_LOADER_ID = 106;
	protected static final int MUSIC_FAVORITE_ARTISTS_LOADER_ID = 107;
	protected static final int VIDEO_ALL_VIDEOS_LOADER_ID = 201;
	protected static final int VIDEO_ALL_MOVIES_LOADER_ID = 202;
	protected static final int VIDEO_ALL_TV_SHOWS_LOADER_ID = 203;
	protected static final int VIDEO_RECENTLY_ADDED_LOADER_ID = 204;

	protected static final Uri ALBUM_ARTWORK_URI = MusicStore.Audio.Albums.ALBUM_ART_URI;

	protected Context mContext;
	protected LoaderManager mLoaderManager;
	protected Listener mListener;
	
	protected ArrayList<Cover> mCoverArray;

	// Keep track of the COver we have, by ID. Used when updating the collection
	protected HashMap<String, Cover> mCoverIdMap;
	
	// Index used to mimic a cursor navigation
	protected int mPosition = 0;

	// Error message
	protected String mErrorMessage = null;

	public CoverProvider(Context context) {
	    mContext = context;
	}
	
	/**
	 *	Start the data request
	 */
	public void start(LoaderManager loaderManager, Listener listener) {
		if(DBG) Log.d(TAG, "start");
		mLoaderManager = loaderManager;
		mCoverIdMap = new HashMap<String, Cover>();
		mListener = listener;
	}
	
	/**
	 *	Free stuff
	 */
	public void stop() {
		// Nothing in the default implementation 
	}

	// Returns null if there is no error
	public String getError() {
		return mErrorMessage;
	}

	public ArrayList<Cover> getCovers() {
		return mCoverArray;
	}

	public int getCount() {
		if (mCoverArray==null) {
			return 0;
		}
		else {
			return mCoverArray.size();
		}
	}

	/**
	 * Return the style of animation to use when the content is updated
	 * @return	either UDPATE_ANIMATION_StayAtCurrentPosition or UDPATE_ANIMATION_ReturnToBeginning
	 */
	protected int getUpdateAnimationStyle() {
		return UDPATE_ANIMATION_StayAtCurrentPosition; // default implementation
	}
	public final static int UDPATE_ANIMATION_StayAtCurrentPosition = 0;
	public final static int UDPATE_ANIMATION_ReturnToBeginning = 1;

	/**
	 * Public interface to get results
	 */
	public interface Listener {
		abstract public void coversLoadingError(CoverProvider provider, String ErrorMessage);
		abstract public void coversAreReady(CoverProvider provider);
		abstract public void coversAreUpdated(CoverProvider provider, Collection<Cover> coversToRecycle, String errorMessage);
	}
}
