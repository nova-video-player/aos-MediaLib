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

import android.util.Log;

import java.util.ArrayList;

import javax.microedition.khronos.opengles.GL10;

public abstract class CoverLayout {

	public final static String TAG = "CoverLayout";
	public final static boolean DBG = false;

	public final static int LAYOUT_FLOW = 1;
	public final static int LAYOUT_GRID_2 = 2;
	public final static int LAYOUT_GRID_3 = 3;
	public final static int LAYOUT_ROLL = 4;


	protected ArrayList<Cover> mCovers = null;
	protected ArrayList<CoverModel> mModels = null;

	final protected Object mCoversLock = new Object(); // Lock on the cover collection

	protected int mWidth;	// Viewport width
	protected int mHeight;	// Viewport height

	protected Icon mMessageBox = null;
	protected float mMessageBoxAlpha = 1f;

	protected Icon mGlobalLabel = null;
	protected float mGLobalLabelScroll = 0;

	protected Integer mDefaultTextureId;

	protected float mScrollingPosition = 0f;

	protected float mTranslationXyz[] = null;
	final protected Object mTranslationLock = new Object();

	protected boolean mDisplayFloatingNames = false; // Display floating names next to covers? (requires a secondary texture)
	protected boolean mForceHideDescriptions = false; // Temporarily hide the floating names

	protected boolean mDrawFocus = false;	// Display the focus on the top cover

	int mItemClickItemID;
	float mItemClickAnimationProgress = 0f;

	public final static int ORIENTATION_VERTICAL = 12;
	public final static int ORIENTATION_HORIZONTAL = 42;
	protected int mOrientation = ORIENTATION_VERTICAL;

	protected CoverLayout() {
		// Better start with an empty table instead than a null pointer
		mCovers = new ArrayList<Cover>(0);
		mModels = new ArrayList<CoverModel>(0);
	}

	//------- Content -------
	public void setCovers(ArrayList<Cover> covers) {
		if(DBG) Log.d(TAG,"setCovers " + covers.size());
		synchronized (mCoversLock) {
			// info about the covers
			mCovers = covers;
			// 3D models of the covers
			mModels = new ArrayList<CoverModel>(covers.size());
			for (int i=0; i<mCovers.size(); i++) {
				mModels.add(new CoverModel());
			}
		}
		updateNumberOfCovers(mCovers.size());
	}

	public Cover getCover(int index) {
		synchronized (mCoversLock) {
			if (mCovers==null) {
				return null;
			}
			if (index < 0) {
				return null;
			} else {
				return mCovers.get(index);
			}
		}
	}

	public void setViewport(int width, int height) {
		mWidth = width;
		mHeight = height;
	}

	// Add an Icon texture, which is expected to be a global label depicting the current view
	public void setGlobalLabel(Icon label) {
		mGlobalLabel = label;
		mGLobalLabelScroll = 0f;
	}

	// Add a (error) message
	public void setMessageBox(Icon message) {
		mMessageBox = message;
	}

	public void setMessageBoxAlpha(float alpha) {
		mMessageBoxAlpha = alpha;
	}

	/**
	 * Allow to temporarily hide the descriptions, independently of the view size
	 */
	public void forceHideDisplayDescriptions (boolean hide) {
		mForceHideDescriptions = hide;
	}

	/**
	 * Set whenever the focus on the selected cover must be displayed
	 */
	public void setDrawFocus(boolean drawFocus) {
		mDrawFocus = drawFocus;
	}

	//------- Orientation -------
	public void setOrientation(int orientation) {
		switch (orientation) {
		case ORIENTATION_VERTICAL:
		case ORIENTATION_HORIZONTAL:
			mOrientation = orientation;
			break;
		default:
			throw new IllegalArgumentException(TAG + ": invalid orientation " + orientation);
		}
	}
	public boolean isHorizontal () {
		return (mOrientation==ORIENTATION_HORIZONTAL);
	}
	public boolean isVertical () {
		return (mOrientation==ORIENTATION_VERTICAL);
	}

	//------- Scrolling -------
	public void setScrollingPosition(float pos) {
		mScrollingPosition = pos;
	}
	public float getScrollingPosition() {
		return mScrollingPosition;
	}
	/**
	 * Be sure that the layout scrolling position is valid
	 * @return true if the scrolling position is changed by this call
	 */
	public boolean scrollingSanityCheck() {
		if (mScrollingPosition<getMinimumScrollingValue()) {
			mScrollingPosition=0;
			return true;
		}
		if (mScrollingPosition>getMaximumScrollingValue()) {
			mScrollingPosition=getMaximumScrollingValue();
			return true;
		}
		return false;
	}

	public Cover getFrontCover() {
		synchronized (mCoversLock) {
			if (mCovers==null) {
				return null;
			}
			final int frontIndex = getFrontCoverIndex();
			if (!isInRange(frontIndex)) {
				return null;
			} else {
				return mCovers.get(frontIndex);
			}
		}
	}

	//------Translation-------
	public void setTranslation(float xyz[]) {
		mTranslationXyz = xyz;
		if (DBG && xyz!=null) {
			Log.d(TAG, "\t"+xyz[0]+"\t"+xyz[1]+"\t"+xyz[2]);
		}
	}
	public void resetTranslation() {
		synchronized (mTranslationLock) {
			mTranslationXyz = null;
		}
	}
	public float[] getTranslation() {
		synchronized (mTranslationLock) {
			if (mTranslationXyz!=null) {
				return mTranslationXyz;
			} else {
				float[] t = new float[3];
				t[0] = t[1] = t[2] = 0f;
				return t;
			}
		}
	}

	public void scrollGlobalLabel(float scroll) {
		mGLobalLabelScroll = scroll;
	}

	//---------- Item Click animation (0 < animationProgress < 1f) ----------
	public void setItemClickProgress(int itemID, float animationProgress) {
		// Better safe than sorry
		if (animationProgress>1f) {
			animationProgress = 1f;
		} else if (animationProgress<0f) {
			animationProgress = 0f;
		}
		mItemClickAnimationProgress = animationProgress;
		mItemClickItemID = itemID;
	}

	// Abstract Public
	abstract protected void updateNumberOfCovers(int n);
	abstract public int getFrontCoverIndex();
	abstract public void setFrontCover(int index);
	abstract public void setBackgroundCoversAlpha(float alpha);
	abstract public Integer getCoverIndexAtPosition(int x, int y);
	abstract public float getScrollingPositionToCenterThisCover(int coverIndex);
	abstract public float getMinimumScrollingValue();
	abstract public float getMaximumScrollingValue();
	abstract public void drawAll(GL10 gl);

	// Protected
	protected float getRotation(int index) {
		//do nothing in default case, can be overridden by actual layouts
		return 0f;
	}

	// Return a cover that need its texture to be computed, knowing that we have a limited number of textures available anyway
	public Cover getCoverInNeedForItsTexture(int maxNumberOfTexture) {

		synchronized (mCoversLock) {
			// Empty cover array case
			if (mCovers.isEmpty()) {
				return null;
			}

			// Get the cover at the center of the view
			int centerIndex = getFrontCoverIndex();

			// First check the center cover
			Cover c = mCovers.get(centerIndex);
			//Log.d(TAG,"CENTER = " + c.mAlbumName);
			// If this cover need a texture, returns it
			if (c.needTexture(mDisplayFloatingNames)) {
				return c;
			}

			int nbTextureFound = 0; // Number of cover found with a valid texture

			// Then scan on both side of the center cover, and increment the distance
			boolean scanning=true;
			int increment = 0;
			while (scanning) {
				scanning = false;
				int left = centerIndex-increment;
				if (isInRange(left)) {
					c = mCovers.get(left);
					// If this cover need a texture, returns it
					if (c.needTexture(mDisplayFloatingNames)) {
						return c;
					}
					nbTextureFound += c.getNumberOfTextureIds();
					scanning = true; // we stop scanning when neither left not right is is range
				}
				int right = centerIndex+increment;
				if (isInRange(right)) {
					c = mCovers.get(right);
					// If this cover need a texture, returns it
					if (c.needTexture(mDisplayFloatingNames)) {
						return c;
					}
					nbTextureFound += c.getNumberOfTextureIds();
					scanning = true; // we stop scanning when neither left not right is is range
				}

				if (nbTextureFound >= maxNumberOfTexture) {
					//Log.d(TAG, "All centered covers have textures, no need to compute new textures");
					return null;
				}
				increment++;
			}
			// If we reach this point, it means we found nothing
			//Log.d(TAG, "Found no texture in need for a texture");
			return null;
		}
	}

	// Return a cover which texture can be recycled
	public Cover getCoverWithRecyclableTexture() {
		if(DBG) Log.d(TAG,"getCoverWithRecyclableTexture");
		synchronized (mCoversLock) {
			// Get the cover at the center of the view
			int centerIndex = getFrontCoverIndex();
			// Start looking from the position the farther from the center
			int scope = Math.max(centerIndex, mCovers.size()-1 - centerIndex);

			while (scope>0) {
				//scan left
				int left = centerIndex - scope;
				// Check if the cover has a texture
				if (isInRange(left) && mCovers.get(left).getNumberOfTextureIds() > 0) {
					// Found a valid texture to recycle
					return mCovers.get(left);
				}
				//scan right
				int right = centerIndex + scope;
				// Check if the cover has a texture
				if (isInRange(right) && mCovers.get(right).getNumberOfTextureIds() > 0) {
					// Found a valid texture to recycle
					return mCovers.get(right);
				}
				scope--;
			}

			// If we reach this point, it means we found nothing (should not happen...)
			Log.e(TAG, "Reaching an unexpected point in getCoverWithRecyclableTexture...");
			return null;
		}
	}

	public void setDefaultTextureId(Integer defaultTextureId) {
		mDefaultTextureId = defaultTextureId;
	}

	public void setGeneralLabelTextureId(Integer generalLabelTextureId) {
		if (mGlobalLabel!=null) {
			mGlobalLabel.setTextureId(generalLabelTextureId);
		}
	}

	public void setMessageboxTextureId(Integer messageboxTextureId) {
		if (mMessageBox!=null) {
			mMessageBox.setTextureId(messageboxTextureId);
		}
	}

	// to be overridden by subclasses
	public boolean isGlobalLabelArea(float x, float y) {
		return false;
	}

	//--------------- Picking -------------------
	public Integer getCoverIndexAtPosition(float x, float y, float z) {
		synchronized (mCoversLock) {
			// Empty cover array case
			if (mCovers.isEmpty()) {
				return null;
			}

			// First try the center cover (the most frequent case), the go further in both directions of the list
			int centerIndex = getFrontCoverIndex();
			CoverModel cm = mModels.get(centerIndex);
			if (cm.containsPoint(x, y, z)) {
				return centerIndex;
			}

			// Then scan on both side of the center cover, and increment the distance
			boolean scanning=true;
			int increment = 0;
			while (scanning) {
				scanning = false;
				int left = centerIndex-increment;
				if (isInRange(left)) {
					cm = mModels.get(left);
					// 	If this cover has no texture, returns it
					if (cm.containsPoint(x, y, z)) {
						return left;
					}
					scanning = true; // we stop scanning when neither left not right is is range
				}
				int right = centerIndex+increment;
				if (isInRange(right)) {
					cm = mModels.get(right);
					// 	If this cover has no texture, returns it
					if (cm.containsPoint(x, y, z)) {
						return right;
					}
					scanning = true; // we stop scanning when neither left not right is is range
				}
				increment++;
			}
			// If we reach this point, it means we found nothing
			if(DBG) Log.d(TAG, "Found no cover at (x,y,z)");
			return null;
		}
	}

	//--------------- Protected -------------------
	protected boolean isInRange (int index) {
		synchronized (mCoversLock) {
			// Sanity checks
			if (index <0) {
				return false;
			} else if (index >= mCovers.size()) {
				return false;
			}
			else {
				return true;
			}
		}
	}

	protected void drawMessageBox(GL10 gl) {
		if (mMessageBox!=null) {

			gl.glMatrixMode(GL10.GL_PROJECTION);
			gl.glPushMatrix();

			// orthographic projection is easier to place a 2D object where I want on the screen...
			gl.glLoadIdentity();
			gl.glOrthof( 0, mWidth, 0, mHeight, -10, 10);

			// Center horizontally, bottom vertically (y sign is weird, linked to glOrthof above, but in a way I didn't figure out)
			mMessageBox.setPosition(mWidth/2, -mHeight/2, 0f);

			// Translation animation
			float[] labelTranslation = null;
			synchronized (mTranslationLock) {
				if (mTranslationXyz != null) {
					labelTranslation = new float[3];
					labelTranslation[0] = mTranslationXyz[0]*mWidth/2; // MAGICAL
					labelTranslation[1] = 0f; // don't slide label vertically
					labelTranslation[2] = 0f; // don't slide label in z
				}
			}
			//scrolling animation
			if (mGLobalLabelScroll!=0f) {
				if (labelTranslation == null) {
					labelTranslation = new float[] {mGLobalLabelScroll,0f,0f};
				} else {
					labelTranslation[0] += mGLobalLabelScroll;
				}
			}

			if (mDrawFocus) {
				mMessageBox.drawFocus(gl, labelTranslation);
			} else {
				mMessageBox.draw(gl, mMessageBoxAlpha, labelTranslation);
			}

			gl.glPopMatrix();
		}
	}
}
