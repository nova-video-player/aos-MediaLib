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

import static android.opengl.GLES10.GL_MODELVIEW;

import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import java.util.Arrays;

import javax.microedition.khronos.opengles.GL10;

public class CoverRollLayout extends CoverLayout {

	private static final String TAG = "CoverRollLayout";
	private static final boolean DBG = false;

	private static class FineTuningValues {
		public float X_SPACE;
		public float Y_SPACE;
		public float Z_SPACE;
		public float X_SPACE_TALL;
		public float Y_SPACE_TALL;
		public float Z_SPACE_TALL;
		public float ANGLE;
		public float ANGLE_TALL;
		public float FLOATING_NAME_X_OFFSET;
		public float FLOATING_NAME_Y_OFFSET;
		public float FLOATING_NAME_Z_OFFSET;
		FineTuningValues(float x, float y, float z, float xt, float yt, float zt, float angle, float anglet, float xo, float yo, float zo) {
			X_SPACE=x; Y_SPACE=y; Z_SPACE=z;
			X_SPACE_TALL=xt; Y_SPACE_TALL=yt; Z_SPACE_TALL=zt;
			ANGLE=angle; ANGLE_TALL=anglet;
			FLOATING_NAME_X_OFFSET=xo; FLOATING_NAME_Y_OFFSET=yo; FLOATING_NAME_Z_OFFSET=zo;
		}
	}
	//                                                                             x    y    z       xt   yt   zt     a   at    xo   yo   zo
	final static private FineTuningValues FineTuningMusic = new FineTuningValues(0.10f,0.6f,0.6f,  0.10f,0.6f,0.6f,  -5f,-5f,  3.5f,5.0f,8.0f);
	final static private FineTuningValues FineTuningVideo = new FineTuningValues(0.20f,0.8f,0.8f , 0.07f,0.8f,0.8f,  -5f,-5f,  2.8f,3.0f,8.0f);
	final static private FineTuningValues FineTuningDefault = FineTuningMusic;

	public static final int FINE_TUNE_DEFAULT = 0;
	public static final int FINE_TUNE_FOR_MUSIC = 1;
	public static final int FINE_TUNE_FOR_VIDEO = 2;

	private FineTuningValues mFineTuningValues; // the one to use

	private float mBackgroundCoversAlpha = 1.0f;   // Allow to dim all the covers but the front one


	private static final int ROLL_CROP_SIZE = 20;	// The max number of covers actually displayed in the 3D scene
	private int mCropSize;							// The actual number of covers actually displayed in the 3D scene

	CoverModel[] mSortedList;			// Array used to copy CoverModels for sorting them
	private Icon mFloatingNameIcons[];	// Used to display "floating names" next to the covers

	private FadeOutBand mFadeOutBand;	// fade out at the top of the view
	private static final int FADEOUTBAND_HEIGHT = 28;

	private FadeOutVeil mFadeVeil;     // fade all the covers but the front one

	private final static float SCROLLING_RATIO = 100.0f;

	float[] mPosBuffer = new float[3]; // just a buffer. Don't allocated it at each frame draw.

	public CoverRollLayout() {
		this(FINE_TUNE_DEFAULT); // default
	}

	public CoverRollLayout( int fineTuning ) {
		super();
		setFineTuningValues(fineTuning);
		displayDescriptions(false); // default
	}

	protected void updateNumberOfCovers(int n) {
		mCropSize = Math.min(n, ROLL_CROP_SIZE);
		mSortedList = new CoverModel[mCropSize];
	}

	/**
	 * @param fineTuning: FINE_TUNE_FOR_MUSIC or FINE_TUNE_FOR_VIDEO
	 */
	public void setFineTuningValues( int fineTuning ) {
		switch (fineTuning) {
		case FINE_TUNE_FOR_MUSIC:
			mFineTuningValues = FineTuningMusic;
			break;
		case FINE_TUNE_FOR_VIDEO:
			mFineTuningValues = FineTuningVideo;
			break;
		case FINE_TUNE_DEFAULT:
		default:
			mFineTuningValues = FineTuningDefault;
		}
	}

	/**
	 * Allow to dim all the covers but the front one
	 * @param alpha, 0.0 means invisible, 1.0 means fully visible
	 */
	public void setBackgroundCoversAlpha(float alpha) {
	    mBackgroundCoversAlpha = alpha;
	}

	/**
	 * Choose if the description is displayed next to the covers
	 */
	public void displayDescriptions(boolean display) {
		synchronized (mCoversLock) {
			mDisplayFloatingNames = display;
			// Allocate icons for the (max three) floating names if needed
			if (mDisplayFloatingNames) {
				mFloatingNameIcons = new Icon[3];
				for (int i=0; i<mFloatingNameIcons.length; i++) {
					mFloatingNameIcons[i] = new Icon();
				}
			} else {
				mFloatingNameIcons = null;
			}
		}
	}

	/**
	 * CoverRoll is always horizontal
	 */
	@Override
	public boolean isHorizontal () {
		return false;
	}
	/**
	 * CoverRoll is always horizontal
	 */
	@Override
	public boolean isVertical () {
		return true;
	}

	@Override
	public void setGlobalLabel(Icon label) {
		super.setGlobalLabel(label);
		// Set position
		label.setPosition(0f, 0f, 0f);
	}

	@Override
	public boolean isGlobalLabelArea(float x, float y) {
		if (mGlobalLabel == null) {
			return false;
		}
		// Global label is at the bottom of the view
		if (y > (mHeight - mGlobalLabel.getHeight())) {
			if (DBG) Log.d(TAG,"isGlobalLabelArea return true");
			return true;
		}
		else {
			return false;
		}
	}

	// Basic 2D picking
	public Integer getCoverIndexAtPosition(int x, int y) {
		synchronized (mCoversLock) {
			// Empty cover array case
			if (mCovers.size()==0) {
				return null;
			}
			int centerCoverHeight;
			if ((float)mHeight / (float)mWidth > 1.5 ) { //portrait
				centerCoverHeight = (int)(0.4f * mHeight);
			} else { // landscape
				centerCoverHeight = (int)(0.6f * mHeight);
			}
			int coverIndex = getFrontCoverIndex();

			final int middle = mHeight/2;
			if (y < middle-centerCoverHeight/2) {
				// Clicked above the center cover
				int result = coverIndex+1;
				if (result>=mCovers.size()) {
					return null;
				} else {
					return result;
				}
			} else if (y > middle+centerCoverHeight/2) {
				// Clicked below the center cover
				int result = coverIndex-1;
				if (result<0) {
					return null;
				} else {
					return result;
				}
			} else {
				return coverIndex; // Center cover
			}
		}
	}

	@Override
	public int getFrontCoverIndex() {
		synchronized (mCoversLock) {
			int centerIndex = (int)(mScrollingPosition/SCROLLING_RATIO + 0.5);
			// Sanity checks
			if (centerIndex <0) {
				centerIndex = 0;
			}
			else if (centerIndex >= mCovers.size()) {
				centerIndex = mCovers.size() -1;
			}
			return centerIndex;
		}
	}

	@Override
	public void setFrontCover(int index) {
		mScrollingPosition = index*SCROLLING_RATIO;
	}

	// Returns the scrolling position to set to get the cover with the given index centered
	public float getScrollingPositionToCenterThisCover(int coverIndex) {
		return coverIndex*SCROLLING_RATIO;
	}

	public float getMinimumScrollingValue() {
		return 0f;
	}
	public float getMaximumScrollingValue() {
		return (mCovers.size()-1)*SCROLLING_RATIO;
	}

	static int mFpsCount = 0;
	static long mFpsStartTime = 0;

	@Override
	public void drawAll(GL10 gl) {

		// No CoverRoll on emulator
		if ("goldfish".equals( Build.HARDWARE )) {
			return;
		}

		if (DBG) {
			final long now = SystemClock.elapsedRealtime();
			if (mFpsStartTime==0) {
				mFpsStartTime = now;
			}
			if (mFpsCount++>=100) {
				float fps = (1000 * mFpsCount) / (float)((now - mFpsStartTime));
				Log.d(TAG, "FPS = "+ fps);
				mFpsCount = 0;
				mFpsStartTime = now;
			}
		}

		float x,y,z,roll_angle;

		float angle;
		float x_space;
		float y_space;
		float z_space;
		if (mHeight > mWidth*1.5) {
			angle = mFineTuningValues.ANGLE_TALL;
			x_space = mFineTuningValues.X_SPACE_TALL;
			y_space = mFineTuningValues.Y_SPACE_TALL;
			z_space = mFineTuningValues.Z_SPACE_TALL;
		} else {
			angle = mFineTuningValues.ANGLE;
			x_space = mFineTuningValues.X_SPACE;
			y_space = mFineTuningValues.Y_SPACE;
			z_space = mFineTuningValues.Z_SPACE;
		}

		if (DBG) Log.d(TAG, "drawAllCovers " + mScrollingPosition);

		synchronized (mCoversLock) {
			if (mCovers.size()>0) {

				final int iFront = getFrontCoverIndex();
				int iCropStart;
				if (iFront<(mModels.size()-ROLL_CROP_SIZE/2)) {
					iCropStart = Math.max(0, iFront-ROLL_CROP_SIZE/2);
				} else {
					iCropStart = Math.max(0, mModels.size()-ROLL_CROP_SIZE);
				}
				final int iCropEnd = Math.min(iCropStart+ROLL_CROP_SIZE, mModels.size());
				if (DBG) Log.d(TAG,"CROP: ["+iCropStart+"--"+iFront+"--"+iCropEnd+"]  ("+mCropSize+")");
				if (DBG) {
					if (iCropEnd - iCropStart > mSortedList.length) {
						throw new IllegalStateException("Cover Roll: Inconsistant crop size!");
					}
				}

				// copy the scrolling value to be sure it is not changed by the UI thread while inside the loop below
				final float lScrolling = mScrollingPosition;

				// Set positions
				for (int i=iCropStart; i<iCropEnd; i++) {

					final float l = i - lScrolling/SCROLLING_RATIO;

					x = 0f; // all in one line!

					if (l>=0f){
						x = -l*x_space;
						y = -l*y_space;
						z = +l*z_space;
					}
					else if (l<0f){
						x = +l*x_space;
						y = -l*y_space;
						z = -l*z_space;
					} else {
						//suppress a warning...
						x = y = z = 0f;
					}

					if (mDisplayFloatingNames) x -= .7f; //MAGICAL

					// roll angle
					roll_angle = l*angle;

					// General translation (for wipe out animation)
					synchronized(mTranslationLock) {
						if (mTranslationXyz != null) {
							x += mTranslationXyz[0];
							y += mTranslationXyz[1];
							z += mTranslationXyz[2];
						}
					}

					Cover c = mCovers.get(i);
					CoverModel model = mModels.get(i);

					// Animate the Z position of the clicked item
					if (i == mItemClickItemID) {
						// Animate the Z position, up and down
						if (mItemClickAnimationProgress<.5f) {
							z +=  mItemClickAnimationProgress*-1f;	// up from regular position
						} else {
							z += (1f-mItemClickAnimationProgress)*-1f; // down back to zero
						}
					}

					model.setPosition(x,y,z);
					model.rotate(roll_angle);

					if ((c.getArtTextureIdObject() == null) || (c.getArtTextureId() == TextureRequest.TEXTURE_NOT_AVAILABLE)) {
						if (mDefaultTextureId==null) {
							model.setCoverTextureId(CoverModel.INVALID_TEXTURE_ID);
						} else {
							model.setCoverTextureId(mDefaultTextureId.intValue());
						}
					} else {
						model.setCoverTextureId(c.getArtTextureId());
					}

					// Copy the size of the cover description texture in the cover model
					// (because we will loose the Cover<>CoverModel bijection when sorting the CoverModels
					model.setDescriptionWidth(c.getDescriptionWidth()/64); // MAGICAL: texture-pixel-size/64 = opengl-geometry-size
					model.setDescriptionHeight(c.getDescriptionHeight()/64); // MAGICAL: texture-pixel-size/64 = opengl-geometry-size

					if ((c.getDescriptionTextureIdObject() == null) || (c.getDescriptionTextureId() == TextureRequest.TEXTURE_NOT_AVAILABLE)) {
						model.setNameTextureIdObject(null);
					} else {
						model.setNameTextureIdObject(c.getDescriptionTextureIdObject()); //TODO DONT ALLOCATE
					}
				}

				// Build another list to sort the covers by z value
				for (int i=iCropStart; i<iCropEnd; i++) {
					mSortedList[i-iCropStart] = mModels.get(i);
				}
				Arrays.sort(mSortedList);

				// draw covers (all but the top one
				for (int i=0; i<mSortedList.length-1; i++) {
					mSortedList[i].draw(gl, 1f);
				}

				// Fade the background covers, if needed
				if (mBackgroundCoversAlpha!=1.0f) {
					drawFadeVeil(gl, mBackgroundCoversAlpha);
				}

				// Draw focus on top cover: first draw focus, then draw cover on top of it
				if (mDrawFocus) {
					mSortedList[mSortedList.length-1].drawFocus(gl);
				}
				mSortedList[mSortedList.length-1].draw(gl, 1f);

				// Draw covers floating names for the top 3 covers
				if (mDisplayFloatingNames && !mForceHideDescriptions) {
					for (int n=3; n>0; n--) { //MAGICAL: 3 description textures displayed max
						if (mSortedList.length-n >= 0) {
							CoverModel cm = mSortedList[mSortedList.length-n];
							cm.getPosition(mPosBuffer);
							mPosBuffer[0] += mFineTuningValues.FLOATING_NAME_X_OFFSET;
							mPosBuffer[1] *= mFineTuningValues.FLOATING_NAME_Y_OFFSET;
							mPosBuffer[2] += mFineTuningValues.FLOATING_NAME_Z_OFFSET;
							mFloatingNameIcons[n-1].setSizeAndPosition(cm.getDescriptionWidth(),cm.getDescriptionHeight(),mPosBuffer);
							mFloatingNameIcons[n-1].rotate( cm.getRotation() );
							Integer textureId = cm.getNameTextureIdObject();
							if (textureId!=null) {
								mFloatingNameIcons[n-1].setTextureId(textureId);
								gl.glPushMatrix();
								mFloatingNameIcons[n-1].draw( gl, (1f-Math.abs(mPosBuffer[1]/4f)), null); //MAGICAL: floating names vertical alpha attenuation
								gl.glPopMatrix();
							}
						}
					}
					/*if(DBG) Log.d(TAG,"Name texture IDs: "
					+sortedList[sortedList.length-3].getNameTextureId() + " "
					+sortedList[sortedList.length-2].getNameTextureId() + " "
					+sortedList[sortedList.length-1].getNameTextureId() + " ");*/
				}

			}
		}

		// draw global label
		if (mGlobalLabel != null) {

			gl.glMatrixMode(GL10.GL_PROJECTION);
			gl.glPushMatrix();

			// orthographic projection is easier to place a 2D object where I want on the screen...
			gl.glLoadIdentity();
			gl.glOrthof( 0, mWidth, 0, mHeight, -10, 10);

			// Center horizontally, bottom vertically (y sign is weird, linked to glOrthof above, but in a way I didn't figure out)
			mGlobalLabel.setPosition(mWidth/2, -mGlobalLabel.getHeight()/2f, 0f);

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

			// draw it now!
			mGlobalLabel.draw(gl, 1.0f, labelTranslation);

			gl.glPopMatrix();
		}

		// Draw the message Box (if any)
		drawMessageBox(gl);

		// Fade out gradient at the top
		drawTopFadeOutBand(gl);
	}

	// Fade the whole view
	private void drawFadeVeil(GL10 gl, float alpha) {
        gl.glMatrixMode(GL10.GL_PROJECTION);
        gl.glPushMatrix();

        // orthographic projection is easier to place a 2D object where I want on the screen...
        gl.glLoadIdentity();
        gl.glOrthof( 0, mWidth, 0, mHeight, -10, 10);

        // Allocate object if not done yet
        if (mFadeVeil==null) {
            mFadeVeil = new FadeOutVeil(mWidth,mHeight);
            mFadeVeil.setPosition(mWidth/2, -mHeight/2, 0f);
        }
        mFadeVeil.draw(gl, alpha);

        gl.glPopMatrix();
        gl.glMatrixMode(GL_MODELVIEW);
	}

	// Fade out gradient at the top of the Roll
	private void drawTopFadeOutBand(GL10 gl) {
        gl.glMatrixMode(GL10.GL_PROJECTION);
        gl.glPushMatrix();

        // orthographic projection is easier to place a 2D object where I want on the screen...
        gl.glLoadIdentity();
        gl.glOrthof( 0, mWidth, 0, mHeight, -10, 10);

        // Allocate object if not done yet
        if (mFadeOutBand==null) {
            mFadeOutBand = new FadeOutBand(mWidth,FADEOUTBAND_HEIGHT);
            mFadeOutBand.setPosition(mWidth/2, -(mHeight-FADEOUTBAND_HEIGHT/2), 0f);
        }
        mFadeOutBand.draw(gl);

        gl.glPopMatrix();
        gl.glMatrixMode(GL_MODELVIEW);
	}
}
