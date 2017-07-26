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


public class ThumbnailRequest {
	
	/**
	 * Position in the ListView (or GridView)
	 */
	private final int mListPosition;
	
	/**
	 * The ID in the Media database. -1 if not in the Media database 
	 */
	private final long mMediaDbId;

	
	public ThumbnailRequest(int listPosition, long mediaDbId) {
		mListPosition = listPosition;
		mMediaDbId = mediaDbId;
	}

	public int getListPosition() {
		return mListPosition;
	}
	public long getMediaDbId() {
		return mMediaDbId;
	}

	/**
	 * @return the key to be used for storing / getting pooled results from thumb engine
	 */
	public Object getKey() {
	    return Long.valueOf(mMediaDbId);
	}

	@Override
	public String toString() {
		return ("("+mListPosition+"/"+mMediaDbId+")");
	}
}

