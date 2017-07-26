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
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnKeyListener;

import com.archos.environment.ArchosFeatures;


public abstract class CoverRoll3D extends CoverGLSurfaceView implements OnKeyListener {

	static final String TAG = "CoverRoll3D";
	static final boolean DBG = false;

	public static final int MSG_CHANGE_CONTENT_CATEGORY = 1001;

	static final float TRANSLATION_SPEED_X = 0.2f;
	static final float TRANSLATION_SPEED_Y = 0.0f;
	static final float TRANSLATION_SPEED_Z = -0.3f;

	private float mLabelScrolling = 0f;

	// We must make sure that the same content change animation is not triggered by both Flinging and Scrolling
	// For that purpose we keep a reference to the finger release event, at the end of the scroll.
	// We compare it to the second event in the onFling callback.
	// They may be the same (not same instance, but same data inside)
	private MotionEvent mContentChangeEvent = null;

	private static final float NONE = -1f;
	private float mNonConfigurationPosition = NONE;

	Cover mFrontCover; // used for startUpdatingContentAnimation

	/**
	 * Construct object, initializing with any attributes we understand from a
	 * layout file. These attributes are defined in
	 * SDK/assets/res/any/classes.xml.
	 *
	 * @see android.view.View#View(android.content.Context, android.util.AttributeSet)
	 */
	public CoverRoll3D(Context context, AttributeSet attrs) {
		super(context, attrs);
		setOnKeyListener(this);
		init();
	}

	private void init() {
		// Set roll layout
		setLayout( new CoverRollLayout() );
	}

	/**
	 * Set the content to be displayed at launch (if not called, the last one will be read from the settings)
	 */
	abstract public void setContentId(String content_id);

	/**
	 * Same principle than Activity::onRetainNonConfigurationInstance()
	 */
    public Object onRetainNonConfigurationInstance() {
        Bundle b = new Bundle(1);
        // Save the position in the roll
        if(DBG) Log.d(TAG, "onRetainNonConfigurationInstance: saving position " + mLayout.getScrollingPosition());
        b.putFloat("position", mLayout.getScrollingPosition());
        return b;
    }
    public void setLastNonConfigurationInstance(Object nonConfigurationInstance) {
    	if (nonConfigurationInstance != null) {
    		Bundle b = (Bundle)nonConfigurationInstance;
    		mNonConfigurationPosition = b.getFloat("position", NONE);
    	}
    }

	/**
	 * Change content when user swipe horizontally
	 * To be implemented by child classes
	 */
	abstract protected void changeContent( int directionChange );

	/**
	 * Animate the loading of new content
	 */
	protected void startLoadingContentAnimation() {
		if(DBG) Log.d(TAG, "startLoadingContentAnimation");

		if (mNonConfigurationPosition!=NONE) {
			if(DBG) Log.d(TAG, "startLoadingContentAnimation: Init position with mNonConfigurationPosition");
			mLayout.setScrollingPosition(mNonConfigurationPosition);
			mNonConfigurationPosition = NONE; // reset, because must not be when changing the content later
			return;
		}

		// First set the layout to (virtual) cover -5, so that it comes from outside the view
		mLayout.setScrollingPosition( mLayout.getScrollingPositionToCenterThisCover(-5) );
		// Then we animate the scrolling to first cover
		int targetCover = 0;
		// Launch animation to reach targetCover
		mAnimHandler.startScrollingAnimPosition( mLayout.getScrollingPositionToCenterThisCover(targetCover), AnimHandler.SPEED_SLOW);
	}

	/**
	 * When content is updated, this method is called just before the content is actually updated
	 * Allow to keep track of the current position, which cover is in front, etc.
	 */
	protected void prepareUpdatingContentAnimation() {
		mFrontCover = mLayout.getFrontCover();
		return;
	}
	/**
	 * Animate the update of the content
	 */
	protected void startUpdatingContentAnimation() {
		if(DBG) Log.d(TAG, "startUpdatingContentAnimation");
		if (mCoverProvider.getUpdateAnimationStyle() == CoverProvider.UDPATE_ANIMATION_ReturnToBeginning) {
			// Check if the previous front cover is still here
			final int idx = mCovers.indexOf(mFrontCover);
			if (idx!=-1) {
				// Stay on the same cover than before the update
				mLayout.setScrollingPosition(mLayout.getScrollingPositionToCenterThisCover(idx));
			}
			// Launch animation to reach position zero
			mAnimHandler.startScrollingAnimPosition( mLayout.getScrollingPositionToCenterThisCover(0), AnimHandler.SPEED_SLOW);
		}
		else if (mCoverProvider.getUpdateAnimationStyle() == CoverProvider.UDPATE_ANIMATION_StayAtCurrentPosition) {
			// Check if the previous front cover is still here
			final int idx = mCovers.indexOf(mFrontCover);
			if (idx!=-1) {
				// Stay on the same cover than before the update
				mLayout.setScrollingPosition(mLayout.getScrollingPositionToCenterThisCover(idx));
				// Be sure to refresh
				requestRender();
			} else {
				// The previous front cover doesn't exist anymore, just stay at the same position
				mLayout.setFrontCover(mLayout.getFrontCoverIndex()); // This allow to fix some cases of ill-positionning
				// be sure to refresh
				requestRender();
			}
		}
		else {
			// No animation, just be sure to refresh the display
			requestRender();
		}
		mFrontCover = null; // avoid a temporary "leak"
	}

	//-------- OnKeyListener interface ----------------------

	boolean mChangeFocusOnUp = true; // used to send the focus change order on the ACTION_UP event, because we consume the ACTION_DOWN for something else

	public boolean onKey(View v, int keyCode, KeyEvent event) {
	    //if(DBG) Log.d(TAG, "onKey  keyCode="+keyCode+" action="+event.getAction()+" long="+event.isLongPress()+" tracking="+event.isTracking()+" canceled="+event.isCanceled());

		// First handle DPAD_RIGHT and DPAD_LEFT
		if ((keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) || (keyCode == KeyEvent.KEYCODE_DPAD_LEFT)) {
			if (event.getAction() == KeyEvent.ACTION_DOWN) {
				if (event.isLongPress()) {
					if ((event.getRepeatCount()-1)%7==0) { // Don't repeat too fast, there is a 'long' animation when changing
						mChangeFocusOnUp = false; // don't do the focus change on the next ACTION_UP
						if(DBG) Log.d(TAG, "ACTION_DOWN LongPress => Change roll content");
						mAnimHandler.stopAllAnimations();
						final int direction = (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) ? 1 : -1;
						mAnimHandler.startTranslationOutAnimation( 1.1f, new float[] {direction*TRANSLATION_SPEED_X, TRANSLATION_SPEED_Y, TRANSLATION_SPEED_Z},
								new Runnable() {
									public void run() {
										changeContent(direction);
									};
								});
					}
					return true; // consume the longpress
				} else {
				    if(DBG) Log.d(TAG, "ACTION_DOWN ShortPress");
					return true; // consume the short-press in order to have a chance to get the long-press before losing the focus
				}
			}
			else if (event.getAction() == KeyEvent.ACTION_UP) {
			    if(DBG) Log.d(TAG, "ACTION_UP");
				if (mChangeFocusOnUp) {
					// Give focus
					if(DBG) Log.d(TAG, "Change focus on ACTION_UP");
					View nextFocus = focusSearch((keyCode == KeyEvent.KEYCODE_DPAD_LEFT)? FOCUS_LEFT : FOCUS_RIGHT);
					if (nextFocus!=null) {
						Log.d(TAG, "    Found a nextFocus!");
						nextFocus.requestFocus();
					}
				}
				else {
					// The latest DPAD_RIGHT or DPAD_LEFT has been used for a long press
					mChangeFocusOnUp = true; // reset for next time
				}
				return true;
			}
		}

		// UP/DOWN scrolling has to be handled on ACTION_DOWN to get key repeat
		if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
			((keyCode == KeyEvent.KEYCODE_DPAD_UP) || (keyCode == KeyEvent.KEYCODE_DPAD_DOWN))) {
			final int direction = (keyCode == KeyEvent.KEYCODE_DPAD_UP) ? 1 : -1;
			final int index = mLayout.getFrontCoverIndex();
			if (((direction==-1)&&(index<1)) || ((direction==1)&&(index>=mCovers.size()-1))) {
				if(DBG) Log.d(TAG,"onKey: reached end!");
			} else {
				float targetScroll = mLayout.getScrollingPositionToCenterThisCover(index + direction);
				mAnimHandler.startScrollingAnimPosition(targetScroll, AnimHandler.SPEED_FAST);
				return true;
			}
		}

		// OK has to be handled on ACTION_UP else it conflicts with long press
		else if ((event.getAction() == KeyEvent.ACTION_UP) && (!event.isCanceled())) {
			if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER)||(keyCode == KeyEvent.KEYCODE_ENTER)) {
				if(DBG) Log.d(TAG, "ACTION_UP  KEYCODE_DPAD_CENTER");
				int centerId = mLayout.getFrontCoverIndex();
				if (centerId < 0) {
					if(DBG) Log.d(TAG,"Error, no valid center cover!");
					return false;
				}
				// Animation (intent will be launched at the end of the animation
				Cover cover = mLayout.getCover(centerId);
				if (cover != null) {
					mAnimHandler.startItemClickAnimation(centerId, getOpenAction(centerId), 0);
					return true;
				}
			}
			else {
				return false;
			}
		}

		return false;
	}

	// Handle mouse wheel
	@Override
	public boolean onGenericMotionEvent(MotionEvent event) {

		if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0) {
			switch (event.getAction()) {
			case MotionEvent.ACTION_SCROLL: {
				final float vscroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
				if(DBG) Log.d(TAG,"onGenericMotionEvent ACTION_SCROLL vscroll="+vscroll);
				if (vscroll != 0) {
					final int index = mLayout.getFrontCoverIndex();
					int targetIndex;
					if (index+vscroll<0) {
						targetIndex=0;
					} else if (index+vscroll >= mCovers.size()-1) {
						targetIndex = mCovers.size()-1;
					} else {
						targetIndex = (int)(index+vscroll+0.5);
					}
					float targetScroll = mLayout.getScrollingPositionToCenterThisCover(targetIndex);
					mAnimHandler.startScrollingAnimPosition(targetScroll, AnimHandler.SPEED_FAST);
				}
			}
			}
		}
		return super.onGenericMotionEvent(event);
	}

	//-------- Part of GestureDetector.Listener interface (some is in CoverGLSurfaceView) -----------------
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
		if(DBG) Log.d(TAG, "onFling("+velocityX+","+velocityY+")");
		//Log.d(TAG, "onFling e2 = "+e2);

		// IMPORTANT: this flag must be reset else the ACTION_UP event will stop the fling animation!
		mGestureDetectorScrolling = false;

		// Strong Horizontal fling = change content
		if ((Math.abs(velocityX) > 500f) && (Math.abs(velocityX) > Math.abs(velocityY))) {
			// Check if the content change has not been triggered by a scroll event already
			if (!AreEquals(e2, mContentChangeEvent)) {
				final int direction = (velocityX<0)?-1:+1;
				//Log.d(TAG, "onFling: launching content change animation");
				// Start the animation removing the current content
				mAnimHandler.stopAllAnimations();
				mAnimHandler.startTranslationOutAnimation( 1.1f, new float[] {direction*TRANSLATION_SPEED_X, TRANSLATION_SPEED_Y, TRANSLATION_SPEED_Z},
						new Runnable() {
							public void run() {
								changeContent(direction);
							};
						});
			}
			else {
				mContentChangeEvent = null; //reset
			}
		}
		// else just scroll
		else {
			mAnimHandler.startScrollingAnimSpeed(velocityY/(mDensity*30f)); //MAGICAL
		}
		return true;
	}

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
		//First check if the original click was in the label area or not
		if (mLayout.isGlobalLabelArea(e1.getX(), e1.getY())) {
			mLabelScrolling = (e2.getX() - e1.getX());
			// Move label according to horizontal scroll
			mLayout.scrollGlobalLabel(mLabelScrolling);
			requestRender();
			return true;
		}
		else {
			// not in label area, just call the regular onScroll
			return super.onScroll(e1, e2, distanceX, distanceY);
		}
	}

	// I need onTouchEvent to check when the finger is released at the end of scrolling
	// (I don't get it using GestureDetector.Listener alone)
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if(DBG) Log.d(TAG, "onTouchEvent e = "+event);
		// Check if finger is "upped" after scrolling
                if (event.getButtonState() == MotionEvent.BUTTON_SECONDARY) return false;
		if ((mLabelScrolling!=0) && (event.getAction() == MotionEvent.ACTION_UP)) {
			// Check if the scrolling has been far enough or if the label must go back to original position
			if (Math.abs(mLabelScrolling)<getWidth()/6) { //MAGICAL
				// Go back to original position
				Log.d(TAG, "onTouchEvent: reseting the label scrolling");
				mLayout.scrollGlobalLabel(0);
				requestRender();
			} else {
				// Change content
				Log.d(TAG, "onTouchEvent: label has been scrolled far enough, change content!");
				final int direction = (mLabelScrolling<0)?-1:+1;
				// Start the animation removing the current content
				mAnimHandler.stopAllAnimations();
				mAnimHandler.startTranslationOutAnimation( 1.1f, new float[] {direction*TRANSLATION_SPEED_X, TRANSLATION_SPEED_Y, TRANSLATION_SPEED_Z},
						new Runnable() {
							public void run() {
								changeContent(direction);
							};
						});
			}
			// We must make sure that a content change is not triggered by a onFling callback
			// For that purpose we keep a reference to the current event, which is the same than e2 for onFling (same data, not same instance)
			mContentChangeEvent = event;
			// Reset label scrolling
			mLabelScrolling = 0;
			// consume the event so that it is not taken into account by the onFling
			return true;
		}

		// Some stuff may have to be done in the mother class
		return super.onTouchEvent(event);
	}

	private boolean AreEquals(MotionEvent e1, MotionEvent e2) {
		if ((e1==null) || (e2==null)) return false;
		if (e1.getAction() != e2.getAction()) return false;
		if (e1.getX() != e2.getX()) return false;
		if (e1.getY() != e2.getY()) return false;
		if (e1.getEventTime() != e2.getEventTime()) return false;
		return true;
	}

	/**
	 * Implements fine tuning of the general eye distance (i.e. zoom, basically) of the GL view
	 * Usually depends on the size of the view and on the density of the screen
	 */
	protected float getEyeDistance( int w, int h) {
	    if(DBG) Log.d(TAG, "getEyeDistance: w="+w+" h="+h+"h/w="+h/(float)w);
	    float multiply =1;
	    // Convert width and height to density-independent values
	    if(ArchosFeatures.isAndroidTV(getContext()))
	    	return -5.8f;
	    final float width = w / getResources().getDisplayMetrics().density;
	    final float height = h / getResources().getDisplayMetrics().density;
	    final boolean bPortrait = (height/width > 1.5f);

	    if(DBG) Log.d(TAG, "getEyeDistance: width="+width+" height="+height+" height/width="+height/width);

		// PORTRAIT
		if (bPortrait) {
			final boolean bA100 = (height>1000);
			final boolean bA80 = (width>450);
			if (bA100) {
				return -8.6f; // A101 Portrait
			} else if (bA80) {
				return -7.0f; // A80 Portrait
			} else {
			    return -8.0f; //  A70 Portrait
			}
		}
		// LANDSCAPE
		else {
			final boolean bA100 = (width>750);
			final boolean bA80 = (height>550);
			if (bA100) {
				return -5.8f; // A101 Landscape
			} else if (bA80) {
				return -6.5f; // A80 Landscape
			} else {
			    return -4.9f; // A70 Landscape
			}
		}
	}
}


