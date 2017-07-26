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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;



public class CoverModel implements Comparable<CoverModel> {

	static final String TAG = "CoverModel";

	private final static int VERTS = 4;
	public  final static int INVALID_TEXTURE_ID = -1;

	private final static float[] COORDS = {
			// X, Y, Z
			-1f,-1f, 0,
			 1f,-1f, 0,
			-1f, 1f, 0,
			 1f, 1f, 0
	};

	private float mRotationAngle = 0f;

	private FloatBuffer mFVertexBuffer;
	private FloatBuffer mTexBuffer;
	private ShortBuffer mIndexBuffer;

	private int mCoverTextureId = INVALID_TEXTURE_ID;

	private Integer mNameTextureId = null; // used only for set/get, to attach a value to the object

	private int mDescriptionWidth;	// Not about the cover itself but about the description texture... It's basically a hack for CoverRollLayout.java
	private int mDescriptionHeight;	// Not about the cover itself but about the description texture... It's basically a hack for CoverRollLayout.java

	public CoverModel() {
		Init(0f,0f,0f);
	}

	public CoverModel( float x, float y, float z) {
		Init(x,y,z);
	}

	private void Init( float x, float y, float z) {

		ByteBuffer vbb = ByteBuffer.allocateDirect(VERTS * 3 * 4);
		vbb.order(ByteOrder.nativeOrder());
		mFVertexBuffer = vbb.asFloatBuffer();

		ByteBuffer tbb = ByteBuffer.allocateDirect(VERTS * 2 * 4);
		tbb.order(ByteOrder.nativeOrder());
		mTexBuffer = tbb.asFloatBuffer();

		ByteBuffer ibb = ByteBuffer.allocateDirect(VERTS * 2);
		ibb.order(ByteOrder.nativeOrder());
		mIndexBuffer = ibb.asShortBuffer();

		int n = 0;
		for (int i=0; i<VERTS; i++) {
			mFVertexBuffer.put( COORDS[n++] + x);
			mFVertexBuffer.put( COORDS[n++] + y);
			mFVertexBuffer.put( COORDS[n++] + z);
		}

		for (int i = 0; i < VERTS; i++) {
			for(int j = 0; j < 2; j++) {
				mTexBuffer.put(COORDS[i*3+j] * 0.5f + 0.5f);
			}
		}

		for(int i = 0; i < VERTS; i++) {
			mIndexBuffer.put((short) i);
		}

		mFVertexBuffer.position(0);
		mTexBuffer.position(0);
		mIndexBuffer.position(0);
	}

	public void setCoverTextureId( int textureId) {
		mCoverTextureId = textureId;
	}

	public void setNameTextureIdObject( Integer textureId) {
		mNameTextureId = textureId;
	}
	public Integer getNameTextureIdObject() {
		return mNameTextureId;
	}

	public float getHeight() {
		return mFVertexBuffer.get(2);
	}

	public void setPosition(float x, float y, float z) {
		mFVertexBuffer.position(0);
		int n = 0;
		for (int i=0; i<VERTS; i++) {
			mFVertexBuffer.put(COORDS[n++] + x);
			mFVertexBuffer.put(COORDS[n++] + y);
			mFVertexBuffer.put(COORDS[n++] + z);
		}
		mFVertexBuffer.position(0);
	}

	public void getPosition(float[] position) {
		position[0] = mFVertexBuffer.get(0) - COORDS[0];
		position[1] = mFVertexBuffer.get(1) - COORDS[1];
		position[2] = mFVertexBuffer.get(2) - COORDS[2];
	}

	public void rotate(float angle) {
		mRotationAngle = angle;
	}
	public float getRotation() {
		return mRotationAngle;
	}

	public void draw(GL10 gl, float alpha) {
		//Log.d(TAG, "draw " + " " + alpha);
		if (mCoverTextureId==INVALID_TEXTURE_ID) {
			// No texture, don't draw!
			return;
		}
		glPushMatrix();
		glFrontFace(GL_CCW);
		glRotatef(mRotationAngle,0f,0f,1f);
		// Alpha Blending
        glEnable(GL_BLEND);
		glBlendFunc (GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

		// Texture blending
		glEnableClientState(GL_TEXTURE_COORD_ARRAY);
		glEnable(GL_TEXTURE_2D);
		if (alpha>=1f) {
			alpha = 1f;
			// Use color and alpha from the texture only
			glTexEnvx(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL11.GL_COMBINE);
			glTexEnvx(GL_TEXTURE_ENV, GL11.GL_COMBINE_RGB, GL11.GL_REPLACE); // color from texture only (not glColor)
			glTexEnvx(GL_TEXTURE_ENV, GL11.GL_COMBINE_ALPHA, GL11.GL_REPLACE); // alpha from texture only (not glColor)
		}
		else {
			// Use mColorBuffer as a global alpha mask:
			glTexEnvx(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL11.GL_COMBINE);
			glTexEnvx(GL_TEXTURE_ENV, GL11.GL_COMBINE_RGB, GL11.GL_REPLACE); // don't take color from glColor but from texture only
			glTexEnvx(GL_TEXTURE_ENV, GL11.GL_COMBINE_ALPHA, GL11.GL_MODULATE); // mix with alpha from glColor
		}

		// Alpha from glColor will be used thanks to GL_MODULATE mode
		glColor4f(0, 0, 0, alpha);

		glVertexPointer(3, GL_FLOAT, 0, mFVertexBuffer);
		glBindTexture(GL_TEXTURE_2D, mCoverTextureId);
		glTexCoordPointer(2, GL_FLOAT, 0, mTexBuffer);
		glDrawElements(GL_TRIANGLE_STRIP, VERTS, GL_UNSIGNED_SHORT, mIndexBuffer);

        glPopMatrix();
	}

	public void drawFocus(GL10 gl) {
		if (mCoverTextureId==INVALID_TEXTURE_ID) {
			// No texture, don't draw!
			return;
		}
		glPushMatrix();
		glFrontFace(GL_CCW);
		glRotatef(mRotationAngle,0f,0f,1f);
		// Alpha Blending
        glEnable(GL_BLEND);
		glBlendFunc (GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

		glDisableClientState(GL_COLOR_ARRAY); // be sure this has not been enabled by someone else in the scene

		// Texture blending
		glEnableClientState(GL_TEXTURE_COORD_ARRAY);
		glEnable(GL_TEXTURE_2D);
		// Use mColorBuffer as a global alpha mask:
		glTexEnvx(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL11.GL_COMBINE);
		glTexEnvx(GL_TEXTURE_ENV, GL11.GL_COMBINE_RGB, GL11.GL_ADD); // mix selection color and texture color
		glTexEnvx(GL_TEXTURE_ENV, GL11.GL_COMBINE_ALPHA, GL11.GL_MODULATE); // mix with alpha from glColor
		glColor4f(0f, 0.7f, 1f, 1f); // MAGICAL, selection color

		glVertexPointer(3, GL_FLOAT, 0, mFVertexBuffer);
		glBindTexture(GL_TEXTURE_2D, mCoverTextureId);
		glTexCoordPointer(2, GL_FLOAT, 0, mTexBuffer);
		glDrawElements(GL_TRIANGLE_STRIP, VERTS, GL_UNSIGNED_SHORT, mIndexBuffer);

        glPopMatrix();
	}

	public void drawStencilMask() {
		glPushMatrix();
		glFrontFace(GL_CCW);
		glRotatef(mRotationAngle,0f,0f,1f);

		glEnable(GL_STENCIL_TEST);					// Enable Stencil Buffer For "marking" The Floor
		glStencilFunc(GL_ALWAYS, 1, 1);				// Always Passes, 1 Bit Plane, 1 As Mask
		glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);	// We Set The Stencil Buffer To 1 Where We Draw Any Polygon

		glColorMask(false, false, false, false);

		//Draw primitive in stencil
		glDrawElements(GL_TRIANGLE_STRIP, VERTS, GL_UNSIGNED_SHORT, mIndexBuffer);
		glColorMask(true,true,true,true);

		glStencilFunc(GL_EQUAL, 1, 1);			// We Draw Only Where The Stencil Is 1
												// (I.E. Where The Floor Was Drawn)
		glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);	// Don't Change The Stencil Buffer

        glPopMatrix();
	}

	public int compareTo(CoverModel another) {
		float me = getHeight();
		float other = another.getHeight();

		if (me > other)
			return -1;
		else if (me < other)
			return 1;
		else
			return 0;
	}

	// Very approximative way to check if (x,y) is in the cover (doesn't take rotation and Z into account)
	public boolean containsPoint(float x, float y, float z) {
		mFVertexBuffer.position(0);

		mFVertexBuffer.position(0);
		if (x<mFVertexBuffer.get(0) || x>mFVertexBuffer.get(1*3 + 0)) {
			//Log.d(TAG, "out of x bounds (" + mFVertexBuffer.get(0)+","+mFVertexBuffer.get(1*VERTS + 0)+")");
			return false; // out of x bounds
		}
		if (y<mFVertexBuffer.get(1) || y>mFVertexBuffer.get(2*3 + 1)) {
			//Log.d(TAG, "out of y bounds (" + mFVertexBuffer.get(1)+","+mFVertexBuffer.get(2*VERTS + 1)+")");
			return false; // out of x bounds
		}
		return true;
	}

	public void setDescriptionWidth(int descriptionWidth) {
		mDescriptionWidth = descriptionWidth;
	}
	public void setDescriptionHeight(int descriptionHeight) {
		mDescriptionHeight = descriptionHeight;
	}
	public int getDescriptionWidth() {
		return mDescriptionWidth;
	}
	public int getDescriptionHeight() {
		return mDescriptionHeight;
	}
}

