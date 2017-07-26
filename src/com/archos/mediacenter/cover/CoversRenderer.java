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

import static android.opengl.GLES10.*;

import com.archos.mediacenter.cover.CoverGLSurfaceView.RendererListener;

import android.graphics.Bitmap;
import android.opengl.GLException;
import android.opengl.GLSurfaceView;
import android.opengl.GLU;
import android.opengl.GLUtils;
import android.util.Log;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


/**
 * A GLSurfaceView.Renderer that uses the Android-specific
 * android.opengl.GLESXXX static OpenGL ES APIs. The static APIs
 * expose more of the OpenGL ES features than the
 * javax.microedition.khronos.opengles APIs, and also
 * provide a programming model that is closer to the C OpenGL ES APIs, which
 * may make it easier to reuse code and documentation written for the
 * C OpenGL ES APIs.
 *
 */
public class CoversRenderer implements GLSurfaceView.Renderer{

	static final String TAG = "CoversRenderer";
	static final boolean DBG = false;

	public static final int FAILED_TO_ADD_TEXTURE_ERROR=0;

	//private final boolean DEBUG_PICKING = false;
	//private final boolean CROSS_FADE_THE_2_LATEST_COVERS = false;

	//private final float LAYOUT_CHANGE_ANIMATION_FRICTION = 0.7f;

    //private CoverGLSurfaceView mParentView; // The view for which I am the Renderer
    private final RendererListener mListener;	// Listener (probably from parent view) wanting to know when GL is ready

    private boolean mPaused;

	private CoverLayout mLayout;
    final private Object mLayoutLock = new Object();

	// private GL10 mGL = null;
	private int mW;
	private int mH;
	// private int[] mViewport;
	//private MatrixGrabber mMatrixGrabber = new MatrixGrabber();
	//private float[] mClickPoint = null;

	private float mEyeDistance = -3.666f; // better have some default value anyway
    private float mTilt;

    public final static int LAYOUT_FLOW = 1;
    public final static int LAYOUT_GRID = 2;
    public final static int LAYOUT_GRID_SMALL = 3;
    public final static int LAYOUT_ROLL = 4;

    private final static int mLayoutType = LAYOUT_ROLL;

    static final int MSG_GL_READY = 421;

    public CoversRenderer(CoverGLSurfaceView parentView, RendererListener listener) {
        //mParentView = parentView;
        mListener = listener;
    }

    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    	if(DBG) Log.d(TAG, "onSurfaceCreated");
    	// mGL = gl;

    	// Tell the listener as soon as GL stack is ready
    	if(DBG) Log.d(TAG,"GL_READY");
        mListener.sendEmptyMessage(RendererListener.MSG_GL_READY);

        // Depth management is done "by hand", by sorting covers. Because of Blending.
        // Sometimes I even want to display cover behind, to mimic transparency
        glDisable(GL_DEPTH_TEST);

        glHint(GL_PERSPECTIVE_CORRECTION_HINT, GL_FASTEST);

        // Enable stuff (...)
        glEnableClientState(GL_VERTEX_ARRAY);
        glDisableClientState(GL_COLOR_ARRAY); // This must be disabled to use glColor for covers
        glEnableClientState(GL_TEXTURE_COORD_ARRAY);
    }

    public void setLayout(CoverLayout layout) {
        synchronized (mLayoutLock) {
    		mLayout = layout;
    		mLayout.setViewport(mW, mH);
    	}
	}

    public void setEyeDistance(float eyeDistance) {
    	mEyeDistance = eyeDistance;
    }

    public void pause() {
    	mPaused = true;
    }
    public void resume() {
    	mPaused = false;
    }

    public int getLayoutType() {
    	return mLayoutType;
    }

	// MUST BE CALLED FROM GL THREAD ONLY
	// Returns a texture ID
    public int addTextureToGL( Bitmap bitmap, Integer idOfTextureToFree) {

		if(DBG) Log.d(TAG, "addTextureToGL: " + idOfTextureToFree);
		int[] textureIdArray = new int[1];

		// Free a texture first, if asked
		if (idOfTextureToFree != null) {
			textureIdArray[0] = idOfTextureToFree.intValue();
			glDeleteTextures(1, textureIdArray, 0);
		}

		// Get a new texture id
		glGenTextures( 1, textureIdArray, 0);
		// Push the texture
		glBindTexture(GL_TEXTURE_2D, textureIdArray[0]);
		glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		glTexEnvf(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_REPLACE);
	    GLUtils.texImage2D(GL_TEXTURE_2D, 0, bitmap, 0);

	    // Doesn't return an error in case of memory limitation
	    // But does return an error in case the GL stack is paused
	    int glError = glGetError();
	    if (glError != GL_NO_ERROR) {
	    	Log.e(TAG,"GL ERROR! " + Integer.toHexString(glError));
	    	// Is '0' never used as a valid texture ID? It seems not, but I have to admit i am not sure...
	    	return FAILED_TO_ADD_TEXTURE_ERROR;
	    }
    	return textureIdArray[0];
	}

	// MUST BE CALLED FROM GL THREAD ONLY
	// Free a set of textures from GL stack
    public void freeGlTextures( int[] IDs, int nb) {
    	glDeleteTextures(nb, IDs, 0);
    }

    public float getTilt() {
    	return mTilt;
    }
    public void setTilt( float t ) {
    	mTilt = t;
    }

	public boolean requiresAnimation() {
		//may require animation for layout transition
		return false;//!!!(mLayoutTransitionPositions != null);
	}

	public void onDrawFrame(GL10 gl) {

    	if (mPaused || gl == null) {
    		return;
    	}

        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        try {
            GLU.gluLookAt(gl,
            		0f, 0f, mEyeDistance,	//eye
            		0f, 0f, 0f,   			//center
            		0f, 1.0f, 0.0f); 		//up
        } catch (GLException e) {
            Log.e(TAG, "onDrawFrame GLException", e);
            return;
        }

        // Rotate
        glRotatef(180f, 0, 0, 1.0f); // upside down
        glRotatef(- mTilt, 0, 1.0f, 0); // tilt

        // Draw covers
        synchronized (mLayoutLock) {
			mLayout.drawAll(gl);
		}

        // Click mark (debug)
        /*if ((DEBUG_PICKING) && (mClickPoint != null)) {
        	Mark m = new Mark(mClickPoint[0], mClickPoint[1], mClickPoint[2]);
        	m.draw(mGL, 1.0f);
        }*/
    }

	public void onSurfaceChanged(GL10 gl, int w, int h) {
		if(DBG) Log.d(TAG, "onSurfaceChanged: "+w+" x "+h);

		mW = w;
		mH = h;
		// mViewport = new int[4];
		// mViewport[0] = mViewport[1] = 0;
		// mViewport[2] = mW;
		// mViewport[3] = mH;

        glViewport(0,0,mW,mH);

        synchronized (mLayoutLock) {
            if (mLayout != null) {
                mLayout.setViewport(mW, mH);
            }
        }
        /*
        * Set our projection matrix. This doesn't have to be done
        * each time we draw, but usually a new projection needs to
        * be set when the viewport is resized.
        */
        float ratio = (float) w / h;
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glFrustumf(-ratio, ratio, -1, 1, 3, 20);
    }

	/* 2010-10-26 STOP with this crappy 3D picking for now...
	public void click(float x, float y) {

		mMatrixGrabber.getCurrentState(new MatrixTrackingGL(mGL));

		if (mClickPoint == null) {
			mClickPoint = new float[4];
		}
		int ret = GLU.gluUnProject(x,y,0f,mMatrixGrabber.mModelView,0, mMatrixGrabber.mProjection,0, mViewport,0, mClickPoint,0);

		mClickPoint[1] *= mViewport[3]/(float)mViewport[2]; //Approximately taking view aspect ratio into account

		Log.d(TAG,"unproject = " + ret + " | " + mClickPoint[0]+ " " + mClickPoint[1] + " " + mClickPoint[2]+ " " + mClickPoint[3]);
		if (DEBUG_PICKING) {
			mParentView.requestRender();
		}

		// Send coordinates back to main (UI) thread
		Message msg = mListener.obtainMessage(RendererListener.MSG_GL_CLICK_COORDINATES);
		msg.obj = mClickPoint;
		mListener.sendMessage(msg);
	}
	*/
}
