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

import android.app.Activity;
import android.app.LoaderManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.ViewTreeObserver.OnTouchModeChangeListener;
import android.view.WindowManager;

import com.archos.environment.ArchosFeatures;
import com.archos.mediacenter.utils.InfoDialog;
import com.archos.medialib.R;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;


public abstract class CoverGLSurfaceView extends GLSurfaceView
implements CoverProvider.Listener, GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener,
SensorEventListener, OnTouchModeChangeListener, OnFocusChangeListener {

	static final String TAG = "CoverGLSurfaceView";
	static final boolean DBG = false;
	static final boolean DBG2 = false;

	private static final int MAX_ALLOCATED_GL_TEXTURE = 40;

	private static final int STATE_INVALID = -1;
	private static final int STATE_CREATED = 0;
	private static final int STATE_SET = 1;   			// Cover, layout, texture provider set
	private static final int STATE_READY_TO_DRAW = 2;	// Default texture set, ready to draw
	private static final int STATE_RUNNING = 3; 		// Actively displaying stuff

	private int mState = STATE_INVALID;
	protected Activity mActivity;					// Used for contextmenu and dialog stuff only
	protected LoaderManager mLoaderManager;

	protected CoversRenderer mRenderer = null;	// The thing doing the GL drawing

	protected CoverProvider mCoverProvider; // FYI: It is started by child class (CoverRoll3DMusic,CoverRoll3DVideo)
	protected ArrayList<Cover> mCovers;

	private final Object mCoversLock = new Object(); // Lock on the cover collection

	protected CoverLayout mLayout = null;

	protected final ArtworkFactory mArtworkFactory;
	private final TextureProvider mTextureProvider;
	private final TextureHandler mTextureProviderHandler;

	private Bitmap mDefaultArtwork = null;		//we'll keep it in memory, don't recreate at each time!
	private Integer mDefaultTextureId = null;	//GL ID of the default artwork

	private Bitmap mGeneralLabelBitmap = null;	 	//we'll keep it in memory, don't recreate at each time!

	private Bitmap mMessageBoxBitmap = null; 		//we'll keep it in memory, don't recreate at each time!

	private final Object mTexturesLock = new Object(); // Used to synchronize between UI thread creating the bitmaps and GL thread pushing them to textures

	// Pools of GL texture IDs
	private int mAvailableTextures = MAX_ALLOCATED_GL_TEXTURE;
	private boolean mTextureRequestInProgress = false;

	private final GestureDetector mGestureDetector;
	protected boolean mGestureDetectorScrolling = false; // true when scrolling is ongoing (not to be confused with Flinging!)
	protected long mThisOnDownMustNotOpen = 0; // Remember down time stopping scrolling so that it is not used for onClick file opening
	protected AnimHandler mAnimHandler = null;
	private final Display mDisplay;
	protected float mDensity = 1.0f;    // Cached DisplayMetrics density

	// Some bad small flags for stuff that I didn't manage to include in the state automaton...
	private boolean mGLready = false; //if OpenGL is ready to process commands. Didn't manage to handle it with mState because it occurs at different time whenever activity is created or resumed
	private boolean mGLViewResumed = false;
	private boolean mPaused = false;

	/**
	 * Build the single default cover bitmap
	 */
	abstract protected Bitmap getDefaultArtwork(ArtworkFactory factory);

	/**
	 * Load the initial content of the Cover view
	 */
	abstract protected void loadInitialContent();

	/**
	 * Save what has to be saved to restore the Cover view next time
	 */
	abstract protected void saveCoverProviderContext();

	/**
	 * Implements fine tuning of the general eye distance (i.e. zoom, basically) of the GL view
	 * Usually depends on the size of the view and on the density of the screen
	 */
	abstract protected float getEyeDistance(int w, int h);

	/**
	 * Construct object, initializing with any attributes we understand from a
	 * layout file. These attributes are defined in
	 * SDK/assets/res/any/classes.xml.
	 *
	 * @see android.view.View#View(android.content.Context, android.util.AttributeSet)
	 */
	public CoverGLSurfaceView(Context context, AttributeSet attrs) {
		super(context, attrs);
		if(DBG) Log.d(TAG, "CoverGLSurfaceView()");

		// Set ZOrder to have OpenGL transparency show the activity background
		// (without this option, the OpenGL transparency shows the activity behind instead of this activity's background)
		setZOrderOnTop(true);

		// Get screen density to adjust gesture sensitivity
		mDensity = getResources().getDisplayMetrics().density;
		if (mDensity==0) mDensity=1.0f; // Am I a paranoid freak? (Is the answer in the question?)

		setDebugFlags(DEBUG_CHECK_GL_ERROR /*| DEBUG_LOG_GL_CALLS*/);

		// Configure GL: RGBA, depth buffer, no Stencil
		setEGLConfigChooser(8, 8, 8, 8, 0, 0);

		// Create renderer
		mRenderer = new CoversRenderer(this, new RendererListener());
		mRenderer.pause(); // to make explicit it is created in paused mode
		setRenderer(mRenderer);

		// Use a surface format with an Alpha channel:
		getHolder().setFormat(PixelFormat.TRANSLUCENT);

		// Don't render when there is no update
		setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

		// Allocate an empty cover list to start with. Will allow to synchronize on it
		mCovers = new ArrayList<Cover>();

		// Setup the texture provider and the attached listener
		int textureSize = 256; // Default
		if(ArchosFeatures.isAndroidTV(getContext()) || ArchosFeatures.isLUDO())
			textureSize = 512;
		/*if (mDensity < 0.8f) {	// A28/A32 are 0.75 ; A35 is 0.95
			textureSize = 128; // 128 enough for A28 & A32 (small 3' ldpi screens)
		}*/
		if(DBG) Log.d(TAG, "mDensity=" + mDensity + " => textureSize=" + textureSize);
		mArtworkFactory = new ArtworkFactory(getContext(), textureSize, textureSize);
		mTextureProvider = new TextureProvider(getContext());
		mTextureProvider.setArtworkFactory( mArtworkFactory );
		mTextureProviderHandler = new TextureHandler();
		mTextureProvider.setListener(mTextureProviderHandler);

		// Gesture detector
		mGestureDetector = new GestureDetector(getContext(),this);

		//Animator
		mAnimHandler = new AnimHandler();

		// Display
		mDisplay = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();

		// Focus management
		getViewTreeObserver().addOnTouchModeChangeListener(this);
		setOnFocusChangeListener(this);

		mState = STATE_CREATED;
		if(DBG) Log.d(TAG, "STATE_CREATED");
	}

	public void setLoaderManager(LoaderManager loaderManager) {
	    Log.d(TAG, "setLoaderManager "+loaderManager);
	    mLoaderManager = loaderManager;
	    checkIfStateIsSet(); // update state
	}

	public void setActivity(Activity activity) {
		if(DBG) Log.d(TAG, "STATE_SET");
		mActivity = activity;
	}

	private boolean checkIfStateIsSet() {
		if ((mTextureProvider != null) && (mLayout != null) && (mLoaderManager != null)) {
			mState = STATE_SET;
			if(DBG) Log.d(TAG, "STATE_SET");
		} else {
			if(DBG) Log.d(TAG, "Not set: " + (mTextureProvider!=null) +"|"+ (mLayout!=null));
		}
		return (mState == STATE_SET);
	}

    public boolean hasBeenSet() {
        return (mState == STATE_SET);
    }

	private boolean checkIfStateIsReadyToDraw() {
		if ((mState == STATE_SET) && mGLViewResumed) {
			mState = STATE_READY_TO_DRAW;
			if(DBG) Log.d(TAG, "STATE_READY_TO_DRAW");
		} else {
			if(DBG) Log.d(TAG, "Not ready: " + (mState==STATE_SET) + " " + (mGLViewResumed));
		}
		return (mState == STATE_READY_TO_DRAW);
	}

	/**
	 * Implements CoverProvider.Listener interface
	 * Is called when the cover collection is updated
	 * @param errorMessage: optional error message. null if no error
	 */
	public void coversAreUpdated(CoverProvider provider, Collection<Cover> coversToRecycle, String errorMessage) {
		if(DBG) Log.d(TAG, "coversAreUpdated. Covers to recycle: " + (coversToRecycle!=null ? coversToRecycle.size() : 0));
		// Give the covers to the 3DView
		synchronized (mCoversLock) {
			// Empty the previous texture pipe
			mTextureProvider.stop(false); //non-blocking
			mTextureProviderHandler.removeAllPendingMessages(); // (important, this is to avoid new texture requests to be sent after the pause)
			mTextureRequestInProgress = false;
			prepareUpdatingContentAnimation(); // must be called before the actual update of the content
			// Change covers
			mCovers = provider.getCovers();
			// Give the new cover list to the layout
			mLayout.setCovers(mCovers);
			// Recycle the textures from the covers to recycle
			if (coversToRecycle!=null) {
				freeCoverTextures(coversToRecycle);
			}
			//TODO!!!!!!!!!!! POTENTIAL (unlikely) BUG: AT THIS POINT mAvailableTextures may not have reset by the GL thread!!!!!!!!!!!!

			// Update error message (is removed in case errorMessage is null).
			setMessageBox(errorMessage);

			// WORKAROUND, see #7087
			// Message box texture creation doesn't work here when done synchronously here.
			// I don't understand why since GL should be "ready" already
			// The workaround is to do it asynchronously through a message sent to the texture provider handler
			mTextureProviderHandler.sendEmptyMessage(TextureHandler.MSG_ASYNC_MESSAGE_BOX_TEXTURE_LOADING);

			// Restart checking for new textures
			mTextureProvider.start();
			checkAndAskForCoverNeedingTexture();
			// Animate the content update
			startUpdatingContentAnimation();
		}
	}

	/**
	 * Implements CoverProvider.Listener interface
	 * Is called when the async content request result is ready
	 */
	public void coversAreReady(CoverProvider provider) {
		if(DBG) Log.d(TAG, "coversAreReady");
		// Build the default cover texture
		synchronized (mTexturesLock) {
			mDefaultArtwork = getDefaultArtwork(mArtworkFactory);
		}
		pushDefaultTextureToGL();
		// Give the covers to the 3DView
		setCovers(provider.getCovers());
		// Remove error message that may have been set before
		setMessageBox(null);
	}

	/**
	 * Implements CoverProvider.Listener interface
	 * Is called when the async content request returns an error
	 */
	public void coversLoadingError(CoverProvider provider, String ErrorMessage) {
		if(DBG) Log.d(TAG, "coversLoadingError");
		// Build the default cover texture anyway because we will need it once there
		// are some covers in future cursor updates (coversAreReady won't be called in that case)
		synchronized (mTexturesLock) {
			mDefaultArtwork = getDefaultArtwork(mArtworkFactory);
		}
		pushDefaultTextureToGL();
		// Set an empty list of covers (nicer to handle than a null pointer...)
		setCovers(new ArrayList<Cover>());
		// Set error message (it removes it if null)
		setMessageBox(ErrorMessage);
	}

	// Content list for the covers
	public void setCovers(ArrayList<Cover> covers) {
		if(DBG) Log.d(TAG,"setCovers (" + covers.size() + " items)");

		// Change covers on the fly
		synchronized (mCoversLock) {
			// Empty the previous texture pipe
			mTextureProvider.stop(false); //nonblocking
			mTextureProviderHandler.removeAllPendingMessages(); // (important, this is to avoid new texture requests to be sent after the pause)
			mTextureRequestInProgress = false;
			// Free the previous cover textures in GL
			if (!mCovers.isEmpty()) {
				freeCoverTextures(mCovers);
			}
			// Change covers
			mCovers = covers;
			// Give the new cover list to the layout
			mLayout.setCovers(mCovers);
			// Reset scrolling position (may be "overruled" by the loading animation)
			mLayout.setScrollingPosition(0f);
			// In case of a translation animation, it has not been reset to avoid glitch, must be now
			mLayout.resetTranslation();
			if (!mCovers.isEmpty()) {
				// Restart checking for new textures
				mTextureProvider.start();
				checkAndAskForCoverNeedingTexture();
				// Loading animation
				startLoadingContentAnimation();
			}
		}
	}

	// Layout for the covers
	protected void setLayout( CoverLayout layout ) {
		if(DBG) Log.d(TAG,"setLayout ");
		mLayout = layout;

		// the default texture ID may have been ready before the layout is set, so give it now
		if (mDefaultTextureId!=null) {
			mLayout.setDefaultTextureId(mDefaultTextureId);
		}

		checkIfStateIsSet();
	}

	// Add a bitmap-textured label for the view
	public void setGeneralLabel(String label) {
		if(DBG) Log.d(TAG,"setGeneralLabel " + label);

		if (mLayout==null) {
			throw new IllegalStateException(TAG + " setGeneralLabel() must be called once the layout is set!");
		}

		// Stress test: label may be changed while it is used by GL thread to create texture
		synchronized (mTexturesLock) {
			// Create the bitmap and keep it
			mGeneralLabelBitmap = mArtworkFactory.createLabelBitmap(label);
			// Add the label object in the layout
			mLayout.setGlobalLabel(new Icon(mGeneralLabelBitmap.getWidth(), mGeneralLabelBitmap.getHeight()));
		}

		// GL is probably ready, so push the texture now
		if(DBG) Log.d(TAG, "setGeneralLabel: Gl is " + mGLready);
		if (mGLready) {
			pushLabelTextureToGL();
		}
	}

	public void setMessageBox(String msg) {
		if(DBG) Log.d(TAG,"setMessageBox: " + msg);

		if (mLayout==null) {
			throw new IllegalStateException(TAG + " setMessageBox() must be called once the layout is set!");
		}

		// Stress test: message may be changed while it is used by GL thread to create texture
		synchronized (mTexturesLock) {
			if (msg!=null) {
				float fontsize = getResources().getDimension(R.dimen.MessageBox_fontsize);
				float msgwidth = getResources().getDimension(R.dimen.MessageBox_width);

				// Create the bitmap and keep it
				mMessageBoxBitmap = mArtworkFactory.createMessageBitmap(msg, fontsize, msgwidth);
				// Add the label object in the layout
				mLayout.setMessageBox(new Icon(mMessageBoxBitmap.getWidth(), mMessageBoxBitmap.getHeight()));
			}
			else {
				mLayout.setMessageBox(null);
			}
		}

		// GL is probably ready, so push the texture now
		if(DBG) Log.d(TAG, "setMessageBox: mGLready is " + mGLready);
		if (mGLready) {
			pushMessageBoxTextureToGL();
		}
	}

	/**
	 * ViewTreeObserver.OnTouchModeChangeListener interface
	 */
	public void onTouchModeChanged(boolean isInTouchMode) {
		if(DBG) Log.d(TAG,"onTouchModeChanged " + isInTouchMode);
		if (mLayout!=null) {
			mLayout.setDrawFocus(!isInTouchMode && isFocused());
			requestRender(); // update display
		}
	}
	/**
	 * View.OnFocusChangeListener interface
	 */
	public void onFocusChange(View v, boolean hasFocus) {
		if(DBG) Log.d(TAG,"onFocusChange " + hasFocus);
		if (mLayout!=null) {
			mLayout.setDrawFocus(hasFocus && !isInTouchMode());
			requestRender(); // update display
		}
	}

	public void onDestroy(Context context) {
		if(DBG) Log.d(TAG,"onDestroy");
		if (mCoverProvider!=null) {
			mCoverProvider.stop();
		}
		saveCoverProviderContext();
	}

	/**
	 * Stop these: animations, texture computing, cursor updates
	 * IMPORTANT: GLSurfaceView::onPause() is NOT called: GL thread is not stopped, GL texture are not lost
	 */
	public void onPauseGLDisplay() {
		if(DBG) Log.d(TAG,"onPauseGLDisplay");

		// Stop the scrolling animation (important, this is to avoid new texture requests to be sent after the pause)
		mAnimHandler.stopAllAnimations();
		// Don't let the layout in a weird transient position after stopping the animation
		mLayout.scrollingSanityCheck();

		// Reset texture provider
		if (mTextureProvider != null) {
			mTextureProvider.stop(false); // non blocking
		}
		if (mTextureProviderHandler != null) {
			mTextureProviderHandler.removeAllPendingMessages(); // (important, this is to avoid new texture requests to be sent after the pause)
		}
		mTextureRequestInProgress = false;

		if (mActivity!=null) {
			mActivity.unregisterForContextMenu(this);
		}
		mPaused = true;
		mRenderer.pause();
	}

	@Override
	public void onPause() {
		throw new IllegalStateException("Please call onPauseGLDisplay() or onStartGL() instead");
	}

	/**
	 * Restarts these: animations, texture computing, cursor updates
	 * IMPORTANT: GLSurfaceView::onResume() is NOT called
	 */
	public void onResumeGLDisplay() {
		if(DBG) Log.d(TAG,"onResumeGLDisplay");

		//NOTE: At this point, the GL stack may or may not be ready!

		// Start the texture provider thread
		mTextureProvider.start();
		// Check which textures must be computed ASAP
		checkAndAskForCoverNeedingTexture();

		if (checkIfStateIsReadyToDraw()) {
			startDrawing();
		}

		mActivity.registerForContextMenu(this);

		// Reset background covers visibility (when back from player)
		if (mLayout!=null) {
		    mLayout.setBackgroundCoversAlpha(1f);
		}

		mPaused = false;
		mRenderer.resume();
	}

	/**
	 * Make sure there are no remaining stuff displayed from previous animations or effects
	 */
	public void resetAnimationEffects() {
	    mAnimHandler.stopAllAnimations(); // Better safe than sorry

	    // Reset background covers visibility (when back from player)
        if (mLayout!=null) {
            mLayout.setBackgroundCoversAlpha(1f);
        }
        // Refresh view
        requestRender();
	}

	@Override
	protected void onVisibilityChanged(View changedView, int visibility) {

		if (visibility!=View.VISIBLE) {
			if (!mPaused) { // need this test because sometimes we get two calls, for some reason
				if(DBG) Log.d(TAG, "onVisibilityChanged NOT_VISIBLE -> onPauseGLDisplay");
				onPauseGLDisplay();
			}
		} else {
			 if (mPaused) {
				 if(DBG) Log.d(TAG, "onVisibilityChanged VISIBLE -> onResumeGLDisplay");
				 onResumeGLDisplay();
			 }
		}
		super.onVisibilityChanged(changedView, visibility);
	}

	@Override
	public void onResume() {
		throw new IllegalStateException("Please call onResumeGLDisplay() or onStopGL() instead");
	}

	/**
	 * Stop the GL layer
	 * Actually calls GLSurfaceView::onPause()
	 */
	public void onStopGL() {
		// Yes, my onStopGL actually does GLSurfaceView::onPause()
		super.onPause();
		if(DBG) Log.d(TAG,"onStopGL");

		// Renderer must not try to display anymore
		mRenderer.pause();

		// Reset texture provider (sometimes onStopGL is called without onPauseGLDisplay being called, see #7193)
		if (mTextureProvider != null) {
			mTextureProvider.stop(false); // non blocking
		}
		if (mTextureProviderHandler != null) {
			mTextureProviderHandler.removeAllPendingMessages(); // (important, this is to avoid new texture requests to be sent after the pause)
		}
		mTextureRequestInProgress = false;


		// We loose all our cover textures...
		freeCoverTextures(mCovers);

		// Tell the layout it has lost its label and message texture
		if (mLayout!=null) {
			mLayout.setGeneralLabelTextureId(null);
			mLayout.setMessageboxTextureId(null);
			//TODO!!! call mRenderer.freeGlTextures() to explicitly free the textures
		}

		// We loose the default texture
		mDefaultTextureId = null;
		if (mLayout!=null) {
			mLayout.setDefaultTextureId(null);
			//TODO!!! call mRenderer.freeGlTextures() to explicitly free the textures
		}

		// We loose the label and message textures
		if (mLayout!=null) {
			mLayout.setGeneralLabelTextureId(null);
			mLayout.setMessageboxTextureId(null);
			//TODO!!! call mRenderer.freeGlTextures() to explicitly free the textures
		}

		// Reset these pseudo state variable...
		mGLViewResumed = false;
		mGLready = false;

		if (mState != STATE_CREATED) { // onStopGL() may be call while still in CREATED, on rotation stress test for example
			mState = STATE_SET;
			if(DBG) Log.d(TAG, "STATE_SET");
		}
	}

	/**
	 * Start the GL layer
	 * Actually calls GLSurfaceView::onResume()
	 */
	public void onStartGL() {
		// Yes, my onStartGL actually does GLSurfaceView::onResume()
		super.onResume();
		if(DBG) Log.d(TAG,"onStartGL");

		//NOTE: At this point, the GL stack may or may not be ready!

		if (mState != STATE_SET) {
			// At creation we are called in READY state
			// At resume we are called in SET mode :-(
			throw new IllegalStateException(TAG + ": onStartGL requires STATE_SET ("+mState+")");
		}

		// Give layout to renderer
		mRenderer.setLayout(mLayout);

		// Check if we are ready to draw
		mGLViewResumed = true;
	}

	public void createContextMenu(Activity activity, ContextMenu menu) {
		if(DBG) Log.d(TAG,"createContextMenu");

		final Cover cover = mLayout.getFrontCover();
		if (cover==null) {
			return;
		}

		menu.setHeaderTitle(cover.getDescriptionName());

		// Animate the front cover to help understand it is the one the menu is referring to
		// The 200ms delay help to have a not-too-jerky animation despite GL animation and ContextMenu animation at the same time
		mAnimHandler.startItemClickAnimation(mLayout.getFrontCoverIndex(), null, 200);
	}

	public void prepareInfoDialog( Context context, InfoDialog infoDialog) {
		final Cover front = mLayout.getFrontCover();
		if (front!=null) {
			front.prepareInfoDialog(context, infoDialog);
		}
	}

	public static interface AnimationListener {
		void onAnimationEnd( CoverGLSurfaceView coverView );
	}

	static final float HIDE_ANIMATION_ACCELERATION = 1.5f;// MAGICAL
	static final float HIDE_ANIMATION_INITIAL_SPEED = -0.4f;// MAGICAL

	public void startHideAnimation(AnimationListener listener) {
		// Better hide the description for this animation
		mLayout.forceHideDisplayDescriptions(true);
		// Launch animation
		final AnimationListener f_listener = listener;
		final CoverGLSurfaceView f_this = this;

		mAnimHandler.startTranslationOutAnimation( HIDE_ANIMATION_ACCELERATION, new float[] {HIDE_ANIMATION_INITIAL_SPEED,0f,0f},
				new Runnable() {
					public void run() {
						if (f_listener!=null) {
							f_listener.onAnimationEnd(f_this);
						}
					};
				});
	}

	static final float SHOW_ANIMATION_FRICTION = 0.7f;// MAGICAL
	static final float HIDE_ANIMATION_INITIAL_POSITION = -6f;// MAGICAL

	public void startShowAnimation() {
		// Better hide the description for this animation
		mLayout.forceHideDisplayDescriptions(true);
		mLayout.resetTranslation();
		mAnimHandler.startTranslationInAnimation( SHOW_ANIMATION_FRICTION, new float[]{HIDE_ANIMATION_INITIAL_POSITION,0f,0f},
				new Runnable() {
					public void run() {
						// Show the descriptions at the end of the animation
						mLayout.forceHideDisplayDescriptions(false);
					};
		});
	}

	/**
	 * surfaceChanged is called when the surface size is known
	 * (may be overridden by child classes)
	 */
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		if(DBG) Log.d(TAG,"surfaceChanged("+format+","+w+","+h+")");

		super.surfaceChanged(holder, format, w, h);
	}

	private void startDrawing() {
		if(DBG) Log.d(TAG, "startDrawing");
		// Change state
		mState = STATE_RUNNING;
		if(DBG) Log.d(TAG, "STATE_RUNNING");
		mRenderer.resume();
	}

	/**
	 * Animate the loading of new content
	 */
	protected void startLoadingContentAnimation() {
		// Just be sure to refresh the display
		requestRender();
		//Nothing more here by default, but can be overridden by derived classes
	}

	/**
	 * When content is updated, this method is called just before the content is actually updated
	 * Allow to keep track of the current position, which cover is in front, etc.
	 */
	protected void prepareUpdatingContentAnimation() {
		//Nothing to do here, but can be overridden by derived classes
	}
	/**
	 * Animate the update of the content (caution, it is often overridden by derived class)
	 */
	protected void startUpdatingContentAnimation() {
		// Just be sure to refresh the display
		requestRender();
		//Nothing more here by default, but can be overridden by derived classes
	}

	// ------------- Texture Management ---------------------

	private void checkAndAskForCoverNeedingTexture() {
		if (!mTextureRequestInProgress) {
			if (DBG2) Log.d(TAG,"checkAndAskForCoverNeedingTexture");
			Cover coverInNeed = mLayout.getCoverInNeedForItsTexture( MAX_ALLOCATED_GL_TEXTURE );
			if (coverInNeed != null) {
				// Launch the texture request
				TextureRequest tr = coverInNeed.getTextureRequest();
				mTextureProvider.requestTexture(tr);
				mTextureRequestInProgress = true;
				if (DBG2) Log.d(TAG, "checkAndAskForCoverNeedingTexture: mTextureRequestInProgress = true");
			}
		}
	}

	private void freeCoverTextures(Collection<Cover> coversToRecycle) {
		Iterator<Cover> it = coversToRecycle.iterator();
		int numberOfTextures = 0;
		while (it.hasNext()) {
			numberOfTextures += it.next().getNumberOfTextureIds();
		}
		final int[] textureIDs = new int[numberOfTextures];
		int n=0;
		it = coversToRecycle.iterator(); // only way to go back to beginning of collection?
		while (it.hasNext()) {
			Cover c = it.next();
			int coverTextureNb = 0;
			Integer coverId = c.getArtTextureIdObject();
			if (coverId!=null) {
				textureIDs[n++] = coverId.intValue();
				coverTextureNb++;
			}
			Integer nameId = c.getDescriptionTextureIdObject();
			if (nameId!=null) {
				textureIDs[n++] = nameId.intValue();
				coverTextureNb++;
			}
			// Tell the cover that it has no more texture (although it will be actual only in "some" ms when freeGlTextures is executed by the GL thread)
			c.resetCoverTextureIds();
			// Keep track of the number of texture used
			mAvailableTextures+=coverTextureNb;
		}
		final int _n = n;
		// Sent order to GL stack
		queueEvent(new Runnable(){
			public void run() {
				mRenderer.freeGlTextures(textureIDs,_n);
				mAvailableTextures+=_n;
			}});
	}

	// Send the default texture to the GL world
	private void pushDefaultTextureToGL() {
		if (mDefaultArtwork!= null) {
			queueEvent(new Runnable(){
				public void run() {
					// NOTE: Here I am executed by the GL thread.
					int newId = -1;
					synchronized (mTexturesLock) {
						newId = mRenderer.addTextureToGL(mDefaultArtwork, null);
					}
					Message ack = mTextureProviderHandler.obtainMessage();
					ack.what = TextureHandler.MSG_GL_DEFAULT_TEXTURE_AVAILABLE;
					ack.arg1 = newId;
					mTextureProviderHandler.sendMessage(ack);
				}});
		}
	}
	// Send the label texture to the GL world
	private void pushLabelTextureToGL() {
		if (mGeneralLabelBitmap!= null) {
			queueEvent(new Runnable(){
				public void run() {
					// NOTE: Here I am executed by the GL thread.
					if(DBG) Log.d(TAG,"Pushing Label texture to GL");
					int newId = -1;
					synchronized (mTexturesLock) {
						newId = mRenderer.addTextureToGL(mGeneralLabelBitmap, null);
					}
					Message ack = mTextureProviderHandler.obtainMessage();
					ack.what = TextureHandler.MSG_GL_LABEL_TEXTURE_AVAILABLE;
					ack.arg1 = newId;
					mTextureProviderHandler.sendMessage(ack);
				}});
		}
	}
	// Send the message box texture to the GL world
	private void pushMessageBoxTextureToGL() {
		if (mMessageBoxBitmap!= null) {
			queueEvent(new Runnable(){
				public void run() {
					// NOTE: Here I am executed by the GL thread.
					if(DBG) Log.d(TAG,"Pushing messagebox texture to GL");
					int newId = -1;
					synchronized (mTexturesLock) {
						newId = mRenderer.addTextureToGL(mMessageBoxBitmap, null);
					}
					if (newId != CoversRenderer.FAILED_TO_ADD_TEXTURE_ERROR) {
						Message ack = mTextureProviderHandler.obtainMessage();
						ack.what = TextureHandler.MSG_GL_MESSAGEBOX_TEXTURE_AVAILABLE;
						ack.arg1 = newId;
						mTextureProviderHandler.sendMessage(ack);
					}
				}});
		}
	}

	// Handler getting the message that GL is ready
	class RendererListener extends Handler {
		static final String TAG = "RendererListener";

		static final int MSG_GL_READY = 1;
		static final int MSG_GL_CLICK_COORDINATES = 2;

		public void handleMessage(Message msg) {
			if (msg.what == MSG_GL_READY) {
				if(DBG) Log.d(TAG, "MSG_GL_READY");
				mGLready = true;
				pushDefaultTextureToGL(); 	// may not do anything if GL is ready while mDefaultArtwork is still null
				pushLabelTextureToGL();		// may not do anything if GL is ready while mGeneralLabelBitmap is still null
				pushMessageBoxTextureToGL();// may not do anything if GL is ready while mMessageBoxBitmap is still null
				// Check if we are ready to draw
				if (checkIfStateIsReadyToDraw()) {
					startDrawing();
				}
				// Initialize the content only once the GL stack is ready
				// (we want this point to be reached as soon as possible, so it's better to not start loading cursor and covers and stuff before)
				if (mCovers.isEmpty()) {
					loadInitialContent();
				}
			}
			else if (msg.what == MSG_GL_CLICK_COORDINATES) {
				float[] pos = (float[])msg.obj;
				if(DBG) Log.d(TAG, "MSG_GL_CLICK_COORDINATES (" + pos[0] + "," + pos[1] + "," + pos[2] + ")");
				// Get the cover at (x,y)
				Integer cid = mLayout.getCoverIndexAtPosition(pos[0],pos[1],pos[2]);
				if (cid==null) {
					if(DBG) Log.d(TAG,"Found no cover at the click point!");
					return;
				}
				// If the clicked cover is the front one, we open the browser
				if (cid.intValue() == mLayout.getFrontCoverIndex()) {
					// Animation (intent will be launched at the end of the animation
					final Cover c =  mLayout.getCover(cid);
					if (c!=null) {
						mAnimHandler.startItemClickAnimation(cid, getOpenAction(cid), 0);
					}
				}
				// If the clicked cover is not the front one, put it to front position
				else {
					float targetScroll = mLayout.getScrollingPositionToCenterThisCover(cid);
					mAnimHandler.startScrollingAnimPosition(targetScroll, AnimHandler.SPEED_FAST);
				}
			}
			else {
				//Unexpected message, FATAL error
				throw new IllegalStateException(TAG + ": unexpected msg " + msg.what);
			}
		}
	}

	protected abstract Runnable getOpenAction(Integer cid);

	// Handler getting the message from the texture provider
	class TextureHandler extends Handler {
		static final String TAG = "TextureHandler";
		static final boolean DBG = false;

		static final int MSG_GL_TEXTURE_AVAILABLE = 101;
		static final int MSG_GL_TEXTURE_FAILED = 102;
		static final int MSG_GL_DEFAULT_TEXTURE_AVAILABLE = 103;
		static final int MSG_GL_LABEL_TEXTURE_AVAILABLE = 104;
		static final int MSG_GL_MESSAGEBOX_TEXTURE_AVAILABLE = 105;
		static final int MSG_ASYNC_MESSAGE_BOX_TEXTURE_LOADING = 106;

		public void removeAllPendingMessages() {
			if(DBG2) Log.d(TAG, "removeAllPendingMessages");
			removeMessages(TextureProvider.MSG_TEXTURE_BITMAP_READY);
			removeMessages(TextureProvider.MSG_TEXTURE_BITMAP_ERROR);
			removeMessages(MSG_GL_TEXTURE_AVAILABLE);
			removeMessages(MSG_GL_TEXTURE_FAILED);
		}

		public void handleMessage(Message msg) {
			final Handler h = this;
			if (msg.what == TextureProvider.MSG_TEXTURE_BITMAP_READY) {
				// TextureProvider tells us a bitmap is ready
				if(DBG) Log.d(TAG, "MSG_TEXTURE_BITMAP_READY");
				final TextureRequest tr = (TextureRequest)msg.obj;
				Integer textureIdToRecycle = null;
				// Check if there is a texture available
				if (mAvailableTextures < 1) {
					// We have to recycle a cover
					Cover c = mLayout.getCoverWithRecyclableTexture();
					if (c==null) {
						// UNEXPECTED ERROR
						throw new IllegalArgumentException("handleMessage: Found no texture to recycle!");
					}
					textureIdToRecycle = c.getTextureIdToRecycle();
				}
				final Integer f_idToRecycle = textureIdToRecycle; // null if it's not recycling an existing texture
				// Send request to GL thread
				queueEvent(new Runnable(){
					public void run() {
						// NOTE: Here I am executed by the GL thread.
						final int newCoverId = mRenderer.addTextureToGL(tr.mBitmap, f_idToRecycle);
						if (newCoverId!=CoversRenderer.FAILED_TO_ADD_TEXTURE_ERROR) {
							Message ack = h.obtainMessage();
							ack.what = MSG_GL_TEXTURE_AVAILABLE;
							ack.obj = (Object)tr;
							ack.arg1 = newCoverId;
							ack.arg2 = (f_idToRecycle!=null)?1:0;
							h.sendMessage(ack);
						}
					}});
			}
			else if (msg.what == TextureProvider.MSG_TEXTURE_BITMAP_ERROR) {
				// NOTE: Here I am executed by the UI thread.
				if(DBG) Log.d(TAG, "MSG_TEXTURE_BITMAP_ERROR");
				// We must remember in the cover that there is no texture to compute for this item
				TextureRequest tr = (TextureRequest)msg.obj;
				tr.glTextureIsReady(TextureRequest.TEXTURE_NOT_AVAILABLE);
				// This is the end of this texture request
				mTextureRequestInProgress = false;
				// Check the next texture to compute
				checkAndAskForCoverNeedingTexture();
			}
			// The message below is sent from the GL thread, but by the code just above
			else if (msg.what == MSG_GL_TEXTURE_AVAILABLE) { // A new texture is ready in the GL world
				// NOTE: Here I am executed by the UI thread.
				if(DBG) Log.d(TAG, "MSG_GL_TEXTURE_AVAILABLE");
				TextureRequest tr = (TextureRequest)msg.obj;
				final int newCoverId = msg.arg1;
				// Set the new ID in the matching cover
				tr.glTextureIsReady(newCoverId);
				final boolean coverRecycled = (msg.arg2!=0);
				// One less texture to use (only if not recycled. If recycled, the texture count is the same)
				if (!coverRecycled) {
					mAvailableTextures-=1;
				}
				// Bitmap can be freed now
				tr.recycleBitmaps();
				// Refresh the scene
				requestRender();
				// This is the end of this texture request
				mTextureRequestInProgress = false;
				// Check the next texture to compute
				checkAndAskForCoverNeedingTexture();
			}
			// The message below is sent from the GL thread, but by the code some lines above
			else if (msg.what == MSG_GL_TEXTURE_FAILED) {
				// NOTE: Here I am executed by the UI thread.
				//TODO: What do we do in that case? What do we have to clean?
				Log.e(TAG, "MSG_GL_TEXTURE_FAILED glError " + msg.arg1);
				// This is the end of this texture request
				mTextureRequestInProgress = false;
				// Check the next texture to compute
				checkAndAskForCoverNeedingTexture();
			}
			// Another message sent by the GL thread:
			else if (msg.what == MSG_GL_DEFAULT_TEXTURE_AVAILABLE) {
				if(DBG) Log.d(TAG, "MSG_GL_DEFAULT_TEXTURE_AVAILABLE");
				synchronized (mTexturesLock) {
					mDefaultTextureId = Integer.valueOf(msg.arg1);
					if (mLayout!=null) { // the layout may not be ready yet
						mLayout.setDefaultTextureId(mDefaultTextureId);
					}
				}
			}
			// Another message sent by the GL thread:
			else if (msg.what == MSG_GL_LABEL_TEXTURE_AVAILABLE) {
				if(DBG) Log.d(TAG, "MSG_GL_LABEL_TEXTURE_AVAILABLE");
				// Set the label texture in the layout
				mLayout.setGeneralLabelTextureId(Integer.valueOf(msg.arg1));
				// Refresh the scene
				requestRender();
			}
			// Another message sent by the GL thread:
			else if (msg.what == MSG_GL_MESSAGEBOX_TEXTURE_AVAILABLE) {
				if(DBG) Log.d(TAG, "MSG_GL_MESSAGEBOX_TEXTURE_AVAILABLE");
				// Set the messagebox texture in the layout
				mLayout.setMessageboxTextureId(Integer.valueOf(msg.arg1));
				// Start messagebox animation
				mAnimHandler.startMessageBoxDisplayAnimation();
			}
			else if (msg.what == MSG_ASYNC_MESSAGE_BOX_TEXTURE_LOADING) {
				pushMessageBoxTextureToGL();
			}
			else {
				//Unexpected message, FATAL error
				throw new IllegalArgumentException(TAG + ": unexpected msg " + msg.what);
			}
		}
	}


	// Used by the renderer in case it requires to launch an animation
	/*public void pingAnimation() {
		// launch animation
		mAnimHandler.pingAnim();
	}*/

	public void changeCoverLayout() {
		if (mLayout==null) {
			return;
		}

		int currentCenter = mLayout.getFrontCoverIndex();
		if(DBG) Log.d(TAG, "currentCenter = " + currentCenter);

		// Center the layout at the same position than it was
		mLayout.setFrontCover(currentCenter);
		// Give the cover list to the layout
		mLayout.setCovers(mCovers);
		// Give layout to renderer
		mRenderer.setLayout(mLayout);
		// Refresh
		requestRender();
	}

	// ---------- Animation Handler -------------------------------
	class AnimHandler extends Handler {

		static final private int MSG_ANIMATE_LOOP = 12;

		static public final int SPEED_SLOW = 1;
		static public final int SPEED_FAST = 2;

		static final private int STATE_IDLE = 0;
		static final private int STATE_SPEED_ANIMATION = 1;
		static final private int STATE_POSITION_ANIMATION = 2;
		private int mAnimState = STATE_IDLE;

		static final private float SPEED_ANIMATION_FRICTION = 0.90f;
		static final private int SPEED_OVERSHOOT_DISTANCE = 100;
		private float mSpeed = 1f;
		private float mTargetPosition = 0f;

		private boolean mTranslationIn = false; // true means in, false means out
		private float mTranslationSpeed[] = null;
		private float mTranslationAcceleration = 1.0f;
		private Runnable mTranslationAnimationEnd = null;

		private Float mItemClickProgress = null;
		private int mItemClickId = -1;
		private Runnable mItentClickAction = null;

		private Float mBackgroundCoversFadeProgress = null;
		static final private float MIN_BACKGROUND_COVERS_ALPHA = 0.0f;
		static final private float BACKGROUND_COVERS_ALPHA_INCREMENT = 0.2f;

		private Float mMessageBoxDisplayProgress = null;

		public boolean isMoving() {
			return (mAnimState == STATE_IDLE);
		}

		// To trigger an animation that may be internal in the renderer
		/*public void pingAnim() {
			if(DBG) Log.d(TAG,"pingAnim");
			this.sendEmptyMessage(MSG_ANIMATE_LOOP);
			// we don't set mAnimState for renderer internal animations
		}*/

		// Animate with a given speed (target position not specified)
		public void startScrollingAnimSpeed(float speed) {
			mAnimState = STATE_SPEED_ANIMATION;
			mSpeed = speed;
			this.sendEmptyMessage(MSG_ANIMATE_LOOP);
		}
		// Animate to a target scrolling position (speed not specified)
		public void startScrollingAnimPosition(float targetPosition, int speed) {
			mAnimState = STATE_POSITION_ANIMATION;
			mTargetPosition = targetPosition;
			switch (speed) {
			case SPEED_SLOW:
				mSpeed=0.09f; break;
			case SPEED_FAST:
			default:
				mSpeed=0.6f; break;
			}
			this.sendEmptyMessage(MSG_ANIMATE_LOOP);
		}
		public void stopScrollingAnimation() {
			// no more scrolling
			mSpeed = 0f;
			// stop animation only if there is no renderer internal animation
			if (!mRenderer.requiresAnimation()) {
				removeMessages(MSG_ANIMATE_LOOP);
			}
			mAnimState = STATE_IDLE;
		}

		/**
		 * Animation of covers going out of the view, to the left or to the right
		 * @param acceleration	must be >1f
		 * @param speed		initial speed	(x,y,z)
		 * @param onTranslationAnimationEnd
		 */
		public void startTranslationOutAnimation(float acceleration, float speed[], Runnable onTranslationAnimationEnd) {
			if (DBG) Log.d(TAG, "startTranslationOutAnimation " + acceleration + " " + speed);
			mTranslationIn = false;
			mTranslationSpeed = speed;
			mTranslationAcceleration = acceleration;
			mTranslationAnimationEnd = onTranslationAnimationEnd;
			this.sendEmptyMessage(MSG_ANIMATE_LOOP);
		}
		/**
		 * Animation of covers coming inside the view. From the left or from the right
		 * @param initialPosition	initial (x,y,z)
		 * @param friction
		 * @param onTranslationAnimationEnd
		 */
		public void startTranslationInAnimation(float friction, float initialPosition[], Runnable onTranslationAnimationEnd) {
			if (DBG) Log.d(TAG, "startTranslationInAnimation " + initialPosition + " " + friction);
			mTranslationIn = true;
			mLayout.setTranslation(initialPosition);
			mTranslationSpeed = new float[]{friction,0f,0f};
			mTranslationAnimationEnd = onTranslationAnimationEnd;
			this.sendEmptyMessage(MSG_ANIMATE_LOOP);
		}
		public void startItemClickAnimation(int itemId, Runnable action, int delayMillis) {
			mItemClickId = itemId;
			mItemClickProgress = Float.valueOf(0.0f);
			mItentClickAction = action;
			this.sendEmptyMessageDelayed(MSG_ANIMATE_LOOP, delayMillis);
		}
		public void startBackgroundCoversFadeOutAnimation() {
		    mBackgroundCoversFadeProgress = Float.valueOf(1.0f);
		    //no need to start the animation pump because this animation is never used alone... :-/  //this.sendEmptyMessage(MSG_ANIMATE_LOOP);
		}
		public void startMessageBoxDisplayAnimation() {
			mMessageBoxDisplayProgress = Float.valueOf(0.0f);
			this.sendEmptyMessage(MSG_ANIMATE_LOOP);
		}
		public boolean isScrollingAnimating() {
			return (mAnimState != STATE_IDLE);
		}
		public boolean isSpeedScrollingAnimating() {
			return (mAnimState == STATE_SPEED_ANIMATION);
		}
		public boolean isPositionScrollingAnimating() {
			return (mAnimState == STATE_POSITION_ANIMATION);
		}
		public void stopAllAnimations() {
			if(DBG) Log.d(TAG, "stopAllAnimations");
			// no more scrolling
			mSpeed = 0f;
			// no more translation
			mTranslationSpeed = null;
			// no more click animation
			mItemClickProgress = null;
			// no more message box animation
			mMessageBoxDisplayProgress = null;
	         // no more background cover fade animation
            mBackgroundCoversFadeProgress = null;
			// stop animation loop
			removeMessages(MSG_ANIMATE_LOOP);
			mAnimState = STATE_IDLE;
		}
		public void handleMessage(Message msg) {
			if (msg.what == MSG_ANIMATE_LOOP) {
				boolean needToCheckForCoverNeedingTexture = false; // not needed for all animations
				if (mAnimState == STATE_SPEED_ANIMATION) {
					needToCheckForCoverNeedingTexture = true;
					// Scrolling Animation
					if (mSpeed!=0f) {
						//Log.d(TAG, "STATE_SPEED_ANIMATION " + mSpeed);
						//move
						float newPosition = mLayout.getScrollingPosition() + mSpeed;
						// Check against min and max values (taking overshoot distance into account)
						if (newPosition > mLayout.getMaximumScrollingValue()+SPEED_OVERSHOOT_DISTANCE) {
						    mSpeed = (mLayout.getMaximumScrollingValue()+SPEED_OVERSHOOT_DISTANCE - mLayout.getScrollingPosition())*.5f;
						} else if (newPosition < mLayout.getMinimumScrollingValue()-SPEED_OVERSHOOT_DISTANCE) {
						    mSpeed = (mLayout.getMinimumScrollingValue()-SPEED_OVERSHOOT_DISTANCE - mLayout.getScrollingPosition())*.5f;
						}
						mLayout.setScrollingPosition( mLayout.getScrollingPosition() + mSpeed);
						//next move
						mSpeed *= SPEED_ANIMATION_FRICTION;
						// Check if it's time to change speed a little so that it exactly ends centered on a cover
						if (Math.abs(mSpeed) < 5f) { //MAGICAL
							if(DBG) Log.d(TAG,"Changing animation mode to center on a cover");
							float currentPos = mLayout.getScrollingPosition();
							int frontCover = mLayout.getFrontCoverIndex();
							float posToCenterFront = mLayout.getScrollingPositionToCenterThisCover(frontCover);
							// we go backward and we are past the front cover -> continue until previous one
							if ((currentPos <= posToCenterFront) && (mSpeed<0)) {
								frontCover--;
								// we go forward and we are past the front cover -> continue to next one
							} else if ((currentPos >= posToCenterFront) && (mSpeed>0)) {
								frontCover++;
							}
							// Be sure to target an existing cover (needed for overshoot animation)
							if (frontCover<0) {
							    frontCover=0;
							} else if (frontCover>=mCovers.size()) {
							    frontCover = mCovers.size()-1;
							}
							// Stop the speed-based animation
							mSpeed = 0f;
							// Start position based animation to center target cover (post a runnable so that
							// it is executed at the next loop, to avoid state mix-up at the end of this function)
							final float targetPos = mLayout.getScrollingPositionToCenterThisCover(frontCover);
							this.post(new Runnable(){ public void run() {
								mAnimHandler.startScrollingAnimPosition(targetPos, SPEED_SLOW);
							}});
						}
						//Log.d(TAG, "mSpeed " + mSpeed);
						if (Math.abs(mSpeed)<0.5f) {
							mSpeed=0f;
						}
						if (mSpeed==0f) {
							mAnimState = STATE_IDLE;
						}
					}
				}
				else if (mAnimState == STATE_POSITION_ANIMATION) {
					//Log.d(TAG, "STATE_POSITION_ANIMATION");
					needToCheckForCoverNeedingTexture = true;
					float current = mLayout.getScrollingPosition();
					//move
					float wayToGo = (mTargetPosition - current);
					//Log.d(TAG, "wayToGo " + wayToGo);
					if (Math.abs(wayToGo)<0.1f) {
						mLayout.setScrollingPosition(mTargetPosition);
						mAnimState = STATE_IDLE;
					} else {
						mLayout.setScrollingPosition(current + wayToGo*mSpeed);
					}
				}

				if (mTranslationSpeed != null) {
					if(DBG) Log.d(TAG, "mTranslationSpeed");
					// This animation doesn't need to update needToCheckForCoverNeedingTexture
					if (mTranslationIn) {	// Covers coming into the view, decelerating
						float[] current = mLayout.getTranslation();
						// target translation position is (0,0,0)
						if (Math.abs(current[0])<0.01f && Math.abs(current[1])<0.01f && Math.abs(current[2])<0.01f) {
							// stop
							current[0] = current[1] = current[2] = 0f ;
							mLayout.setTranslation(current);
							mTranslationSpeed = null; // stop the animation
							if (mTranslationAnimationEnd!=null) {
								mTranslationAnimationEnd.run();
							}
						} else {
							//move
							current[0] -= current[0]*mTranslationSpeed[0];
							current[1] -= current[1]*mTranslationSpeed[1];
							current[1] -= current[2]*mTranslationSpeed[2];
							mLayout.setTranslation(current);
						}
					}
					else {	// Covers going out of the view, accelerating
						// update position
						float translation[] = mLayout.getTranslation();
						translation[0] += mTranslationSpeed[0];
						translation[1] += mTranslationSpeed[1];
						translation[2] += mTranslationSpeed[2];
						mLayout.setTranslation(translation);
						// Increase speed for the next step
						mTranslationSpeed[0] *= mTranslationAcceleration;
						mTranslationSpeed[1] *= mTranslationAcceleration;
						mTranslationSpeed[2] *= mTranslationAcceleration;
						// Check end
						if (Math.abs(translation[0]) > 6f) { //MAGICAL
							// Must not reset the translation because it would display the current content in
							// the middle of the view again. It will have to be reset after loading the new content
							mTranslationSpeed = null; // stop the animation
							if (mTranslationAnimationEnd!=null) {
								mTranslationAnimationEnd.run();
							}
						}
					}
				}

				if (mItemClickProgress != null) {
					//Log.d(TAG, "mItemClickProgress " + mItemClickProgress);
					// This animation doesn't need to update needToCheckForCoverNeedingTexture
					if (mItemClickProgress>=1f) { // should never be >, only =, but safer to check >= anyway
						mItemClickProgress=null; // stop animation
						mLayout.setItemClickProgress(-1, 0f); // reset layout effect
						// Launch the action, if there is one
						if (mItentClickAction!=null) {
							// give a little time to the GL thread to draw animation (hum...)
							try {Thread.sleep(100);} catch (InterruptedException e) {}
							// Launch the action
							mItentClickAction.run();
						}
					} else {
					    mItemClickProgress += 0.20f; //MAGICAL
					    if (mItemClickProgress>1f) {
					        mItemClickProgress=1f;
					    }
		                mLayout.setItemClickProgress(mItemClickId, mItemClickProgress.floatValue());
					}
				}

				if (mBackgroundCoversFadeProgress != null) {
				    float fade = mBackgroundCoversFadeProgress.floatValue();
			        fade -= BACKGROUND_COVERS_ALPHA_INCREMENT; // animation increment
			        if (fade<=MIN_BACKGROUND_COVERS_ALPHA) {
			            mLayout.setBackgroundCoversAlpha(MIN_BACKGROUND_COVERS_ALPHA);
			            mBackgroundCoversFadeProgress = null; // stop animation
			        } else {
			            mLayout.setBackgroundCoversAlpha(fade);
			            mBackgroundCoversFadeProgress = fade;
			        }
				}

				if (mMessageBoxDisplayProgress != null) {
					//Log.d(TAG, "mMessageBoxDisplayProgress " + mMessageBoxDisplayProgress);
					// This animation doesn't need to update needToCheckForCoverNeedingTexture
					mMessageBoxDisplayProgress += 0.1f; //MAGICAL
					if (mMessageBoxDisplayProgress > 1f) {
						mLayout.setMessageBoxAlpha(1f);
						mMessageBoxDisplayProgress = null; // stop animation
					} else {
						mLayout.setMessageBoxAlpha(mMessageBoxDisplayProgress);
					}
				}

				if (needToCheckForCoverNeedingTexture) {
					checkAndAskForCoverNeedingTexture();
				}
				requestRender(); //refresh

				if ((mAnimState != STATE_IDLE) || mRenderer.requiresAnimation() ||
						(mTranslationSpeed != null) || (mItemClickProgress != null) || (mBackgroundCoversFadeProgress != null) || (mMessageBoxDisplayProgress != null)) {
					this.sendEmptyMessageDelayed(MSG_ANIMATE_LOOP, 20);
				}
			}
		}
	}

	protected void onTranslationAnimationEnd(boolean towardLeft) {
		// Nothing here, but can be overloaded by derived classes
	}

	//-------- GestureDetector.Listener interface ----------------------
	public boolean onDown(MotionEvent e) {
		if(DBG) Log.d(TAG, "onDown: stopScrollingAnim");
		if (mAnimHandler.isSpeedScrollingAnimating()) {
			mAnimHandler.stopScrollingAnimation();
			// Remember down time stopping scrolling so that it is not used for onClick file opening
			mThisOnDownMustNotOpen = e.getDownTime();
		}
		return true;
	}

	public void onLongPress(MotionEvent e) {
		if(DBG) Log.d(TAG, "onLongPress");
		if ((mActivity != null) && (mLayout.getFrontCover() != null)) {
			mActivity.openContextMenu(this);
		}
	}

	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
		//Log.d(TAG, "onScroll " + distanceX + " " + distanceY);
		float d;
		if (mLayout.isVertical()) {
			d = distanceY;
		} else {
			d = distanceX;
		}

		mGestureDetectorScrolling = true;

		// Be sure to stop a possible current scrolling animation
		mAnimHandler.stopScrollingAnimation();

		float newPosition = mLayout.getScrollingPosition() - 1.0f*d/mDensity;
		// Check against min and max values
		if (newPosition > mLayout.getMaximumScrollingValue() || newPosition < mLayout.getMinimumScrollingValue()) {
		    // Elasticity: move slower when above the max or below the min
		    newPosition = mLayout.getScrollingPosition() - 0.3f*d/mDensity;
		}

		mLayout.setScrollingPosition( newPosition );
		checkAndAskForCoverNeedingTexture();
		requestRender();
		return true;
	}

	public void onShowPress(MotionEvent e) {
		if(DBG) Log.d(TAG, "onShowPress");
	}

	public boolean onSingleTapUp(MotionEvent e) {
		if(DBG) Log.d(TAG, "onSingleTapUp");
		//May be a double tap, must use onSingleTapConfirmed instead!
		return true;
	}

	//-------- OnDoubleTapListener interface ----------------------
	public boolean onDoubleTap(MotionEvent e) {
		return false; // can be overridden by derived classes
	}
	public boolean onDoubleTapEvent(MotionEvent e) {
		return false;
	}
	public boolean onSingleTapConfirmed(MotionEvent e) {
		final int x=(int)e.getX();
		final int y=(int)e.getY();
		Log.d(TAG,"onSingleTapConfirmed " + x + " " + y);

		// If the down event has been used to stop scrolling, it must not open the file
		if (e.getDownTime() == mThisOnDownMustNotOpen) {
			Log.d(TAG, "onSingleTapConfirmed: don't open item because view was scrolling!");
			return true; // has been consumed to stop the scrolling
		}

		// Ask the 3D world coordinates to the GL thread.
		// Will reply using the RendererListener
		/* 2010-10-26 STOP with this crappy 3D picking for now...
		queueEvent(new Runnable(){
			public void run() {
				mRenderer.click(x,y);
			}});
		 */   // Use a more basic hard-coded 2D operation instead:

		// Ask the layout for a basic 2D pseudo-picking
		Integer tappedCoverId = mLayout.getCoverIndexAtPosition(x, y);
		if (tappedCoverId==null) {
			if(DBG) Log.d(TAG,"Found no cover at the click point!");
			return false;
		}
		// If the clicked cover is the front one, we open the browser
		if (tappedCoverId.intValue() == mLayout.getFrontCoverIndex()) {
			// Animations intent will be launched at the end of the click animation
			mAnimHandler.startItemClickAnimation(tappedCoverId, getOpenAction(tappedCoverId), 0);
			mAnimHandler.startBackgroundCoversFadeOutAnimation();
		}
		// If the clicked cover is not the front one, put it to front position
		else {
			float targetScroll = mLayout.getScrollingPositionToCenterThisCover(tappedCoverId);
			mAnimHandler.startScrollingAnimPosition(targetScroll, AnimHandler.SPEED_FAST);
		}

		return true;
	}

	// I need onTouchEvent to check when the finger is released at the end of scrolling
	// (I don't get it using GestureDetector.Listener alone)
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		// Give everything to the gesture detector
		boolean retValue = false;
		if (event.getButtonState() == MotionEvent.BUTTON_SECONDARY) return retValue;
		retValue = mGestureDetector.onTouchEvent(event);

		// Check if finger is upped after scrolling
		if (mGestureDetectorScrolling && (event.getAction() == MotionEvent.ACTION_UP)) {
			if(DBG) Log.d(TAG, "ACTION_UP end of scrolling");
			mGestureDetectorScrolling = false;
			// Center to the front cover
			float targetScroll = mLayout.getScrollingPositionToCenterThisCover( mLayout.getFrontCoverIndex());
			mAnimHandler.startScrollingAnimPosition(targetScroll, AnimHandler.SPEED_FAST);
		}

		return retValue;
	}

	static final float ORIENTATION_FACTOR = 4f;
	static final float ORIENTATION_DIFF_THRESHOLD = 3f;
	static final float ORIENTATION_FRICTION = 0.02f;

	public void onSensorChanged(SensorEvent event) {

		//Log.d(TAG,"Orientation=" +  mDisplay.getOrientation() + " " + event.values[0] + " " + event.values[1] + " " + event.values[2]);
		float value;
		switch(mDisplay.getOrientation()) {
		case Surface.ROTATION_90:
			value = -event.values[1]*ORIENTATION_FACTOR;
			break;

		case Surface.ROTATION_180:
			value = -event.values[0]*ORIENTATION_FACTOR;
			break;

		case Surface.ROTATION_270:
			value = +event.values[1]*ORIENTATION_FACTOR;
			break;

		default:
			value = +event.values[0]*ORIENTATION_FACTOR;
			break;
		}

		float currentX = mRenderer.getTilt();
		float diff = currentX - value;

		if (Math.abs(diff) > ORIENTATION_DIFF_THRESHOLD) {
			mRenderer.setTilt( currentX - diff * ORIENTATION_FRICTION);
			if(DBG) Log.d(TAG,"onSensorChanged diff = " + diff);
			requestRender();
		}
	}
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		//What for?
	}
}


