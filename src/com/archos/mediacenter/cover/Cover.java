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

import com.archos.mediacenter.utils.InfoDialog;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;

public abstract class Cover extends TextureRequester {

	static final String TAG = "Cover";
	static final boolean DBG = false;

	public static final String LAUNCH_CONTENT_BROWSER_INTENT = "LaunchAContentBrowser";
	public static final String LAUNCH_SINGLE_TRACK_INTENT = "LaunchASingleTrack";
	public static final String LAUNCH_SINGLE_TRACK_ID_EXTRA = "id";

	protected long mObjectLibraryId = -1;		// The ID of the represented object in the media library. To be set by each child class
	protected String mObjectLibraryType = null;	// The type of object in the media library. To be set by each child class

	private int mFavoriteTime = 0;   // The time at which it has been set favorite. Zero means not a favorite.

	// Keep this one private, because "users" may mix between "int" and "Integer" without even noticing
	private Integer mArtTextureId = null;
	private Integer mDescriptionTextureId = null;

	private boolean mLayoutMakesUseOfDescriptionTexture;

	public void setFavoriteTime(int favoriteTime) {
		mFavoriteTime = favoriteTime;
	}
	public int getFavoriteTime() {
		return mFavoriteTime;
	}

	// --- Cover abstract methods to be implemented by child ----------
	/**
	 *  Returns the main artwork texture
	 */
	abstract public Bitmap getArtwork(ArtworkFactory factory, boolean descriptionOnCover);
	/**
	 *	Returns a texture describing the content, usually mostly made of text
	 */
	abstract public Bitmap getDescription( ArtworkFactory factory );
	/**
	 * Returns the width (in pixels) of the texture describing the content
	 */
	abstract public int getDescriptionWidth();
	/**
	 * Returns the height (in pixels) of the texture describing the content
	 */
	abstract public int getDescriptionHeight();
	/**
	 * 	Returns a name describing the content (track title, album title, movie title, etc.)
	 */
	abstract public String getDescriptionName();
	/**
	 * 	Returns an URI describing the content (media library based if possible)
	 */
	abstract public Uri getUri();
	/**
	 * 	Returns a runnable to execute when the cover is clicked
	 */
	abstract public Runnable getOpenAction(final Context context);
	/**
	 * 	Play the content represented by the cover
	 *  May be the same action than getOpenAction(). May be not.
	 */
	abstract public void play(Context context);
	/**
	 *	Get a string ID describing the cover content
	 *	Used to identify if a cover is already in a cover collection or not when converting from a Cursor
	 */
	abstract public String getCoverID();
	/**
	 * Get a short name used to represent this item in debug logs
	 */
	abstract public String getDebugName();
	/**
	 *  Fill the info dialog fields
	 */
	abstract public void prepareInfoDialog(Context context, InfoDialog infoDialog);

	// --- Library stuff --------------------------
	public long getMediaLibraryId() {
		return mObjectLibraryId;
	}

	public String getLibraryObjectType() {
		return mObjectLibraryType;
	}

	// --- Texture stuff --------------------------
	public boolean needTexture(boolean descriptionNeeded) {
		mLayoutMakesUseOfDescriptionTexture = descriptionNeeded;
		return (mArtTextureId == null) || (descriptionNeeded && (mDescriptionTextureId == null));
	}
	public void resetCoverTextureIds() {
		// Caution! Different than "=-1" or "= 0", because we'll check against null
		if (DBG && mArtTextureId!=null) Log.d(TAG, "recycling texture " + mArtTextureId.intValue());
		if (DBG && mDescriptionTextureId!=null) Log.d(TAG, "recycling texture " + mDescriptionTextureId.intValue());
		mArtTextureId = null;
		mDescriptionTextureId = null;
	}
	public boolean hasCoverTextureAvailable() {
		return ((mArtTextureId!=null) && (mArtTextureId.intValue() != TextureRequest.TEXTURE_NOT_AVAILABLE));
	}

	public int getArtTextureId() {
		return (mArtTextureId!=null) ? mArtTextureId.intValue() : TextureRequest.TEXTURE_NOT_AVAILABLE;
	}
	public int getDescriptionTextureId() {
		return (mDescriptionTextureId!=null) ? mDescriptionTextureId.intValue() : TextureRequest.TEXTURE_NOT_AVAILABLE;
	}
	public Integer getArtTextureIdObject() {
		return mArtTextureId;
	}
	public Integer getDescriptionTextureIdObject() {
		return mDescriptionTextureId;
	}

	public int getNumberOfTextureIds() {
		int number=0;
		if (mArtTextureId!=null) number++;
		if (mDescriptionTextureId!=null) number++;
		return number;
	}

	//------- Implements TextureRequester interface ------

	@Override
	public TextureRequest getTextureRequest() {
		// First check if cover art texture is needed
		if (mArtTextureId==null) {
			return new CoverTextureRequest(this, CoverTextureRequest.TYPE_ART);
		}
		// Then check for description texture in case of a second request
		else if (mDescriptionTextureId==null) {
			return new CoverTextureRequest(this, CoverTextureRequest.TYPE_DESCRIPTION);
		}
		else {
			return null;
		}
	}

	@Override
	public void glTextureIsReady(TextureRequest tr, int glTextureId) {
		// Verify that the returned request is actually from a Cover
		final CoverTextureRequest ctr = (CoverTextureRequest)tr;

		if (ctr==null) {
			Log.e(TAG, "textureIsReady: TextureRequest should be a CoverTextureRequest!");
			throw new IllegalArgumentException(TAG);
		}

		if(DBG) Log.d(TAG, "glTextureIsReady() type " + ctr.mTextureType + " for " + ctr.getDebugName());

		if (ctr.mTextureType == CoverTextureRequest.TYPE_ART) {
			mArtTextureId = Integer.valueOf(glTextureId);
		} else if(ctr.mTextureType == CoverTextureRequest.TYPE_DESCRIPTION) {
			mDescriptionTextureId = Integer.valueOf(glTextureId);
		} else {
			Log.e(TAG, "textureIsReady: invalid CoverTextureRequest type!");
			throw new IllegalArgumentException(TAG);
		}
	}

	@Override
	public int getTextureIdToRecycle() {
		// First recycle the description texture, if there is one
		if (mDescriptionTextureId!=null) {
			final int recycledIdValue = mDescriptionTextureId.intValue();
			mDescriptionTextureId = null; // remember that we have no more description texture for this cover
			return recycledIdValue;
		}
		else if (mArtTextureId!=null) {
			final int recycledIdValue = mArtTextureId.intValue();
			mArtTextureId = null; // remember that we have no more art texture for this cover
			return recycledIdValue;
		}
		else {
			// Should never be called if there is no texture to recycle
			throw new IllegalStateException(TAG);
		}
	}

	//--------------------------------------------------------
	// Private extension of the TextureRequest class
	//--------------------------------------------------------

	private class CoverTextureRequest extends TextureRequest {
		final static int TYPE_ART = 1;
		final static int TYPE_DESCRIPTION = 2;

		public Cover mCoverRequesting;
		public int mTextureType;

		public CoverTextureRequest(Cover cover, int type) {
			// cover is the TextureRequester
			super(cover);
			mCoverRequesting = cover;
			mTextureType = type;
			//Log.d(TAG, "Create Texture Request for " + cover.mFilePath + " type " + mTextureType);
		}


		// Compute the texture, if needed
		public boolean makeBitmap(Context context, ArtworkFactory factory) {
			try {
				if (mTextureType == TYPE_ART) {
					//Log.d( TAG, "Cover Texture building...");
					mBitmap = mCoverRequesting.getArtwork( factory, !mLayoutMakesUseOfDescriptionTexture );
				}
				else if (mTextureType == TYPE_DESCRIPTION) {
					//Log.d( TAG, "Description Texture building...");
					mBitmap = mCoverRequesting.getDescription( factory );
				}
				return (mBitmap!=null);
			}
			catch (Exception e) {
				//error!
				Log.e( TAG, "Texture failed to build!", e);
				e.printStackTrace();
				return false;
			}
			catch (OutOfMemoryError e) {
				Log.e( TAG, "No more memory!", e);
				return false;
			}
		}


		@Override
		public String getDebugName() {
			return mCoverRequesting.getDebugName();
		}
	}
}

