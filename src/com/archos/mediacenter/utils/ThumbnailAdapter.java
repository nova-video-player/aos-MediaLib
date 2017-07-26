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

import android.database.DataSetObserver;

/**
 * This interface must be implemented by the adapter to be interfaced with a ThumbnailEngineVideo/ThumbnailRequester
 *
 */
public interface ThumbnailAdapter {

	/**
	 * Return true if the item at the given position requires a thumbnail
	 */
	public boolean doesItemNeedAThumbnail(int position);
	
	/**
	 * Just a pass-through to the regular Adapter::registerDataSetObserver
	 */
	public void registerDataSetObserver(DataSetObserver observer);
}
