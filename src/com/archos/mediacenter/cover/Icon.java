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


public class Icon {

	static final String TAG = "Icon";

	private final static int VERTS = 4;

	private static float[] COORDS = {
		// X, Y, Z
		-0.5f,-0.5f, 0,
		0.5f,-0.5f, 0,
		-0.5f, 0.5f, 0,
		0.5f, 0.5f, 0
	};

	private static float[] TEX_COORDS = {
		// X, Y
		0f,0f,
		1f,0f,
		0f,1f,
		1f,1f,
	};

	private float mWidth = 1f;
	private float mHeight = 1f;

	private float mRotationAngle = 0f;

	private FloatBuffer mFVertexBuffer;
	private FloatBuffer mTexBuffer;
	private ShortBuffer mIndexBuffer;

	private Integer mTextureId = null;

	public Icon() {
		Init(1f,1f,0f,0f,0f);
	}

	public Icon( float w, float h, float pos[] ) {
		Init(w,h,pos[0],pos[1],pos[2]);
	}

	public Icon( float w, float h) {
		Init(w,h,0f,0f,0f);
	}

	public float getWidth() {
		return mWidth;
	}
	public float getHeight() {
		return mHeight;
	}

	private void Init( float w, float h, float x, float y, float z ) {

		mWidth = w;
		mHeight = h;

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
			mFVertexBuffer.put( COORDS[n++]*mWidth  + x);
			mFVertexBuffer.put( COORDS[n++]*mHeight + y);
			mFVertexBuffer.put( COORDS[n++]         + z);
		}

		for (int i = 0; i < 2*VERTS; i++) {
			mTexBuffer.put(TEX_COORDS[i]);	//TODO ???
		}

		for(int i = 0; i < VERTS; i++) {
			mIndexBuffer.put((short) i);
		}

		mFVertexBuffer.position(0);
		mTexBuffer.position(0);
		mIndexBuffer.position(0);
	}

	public void setTextureId( Integer textureId) {
		mTextureId = textureId;
	}
	public Integer getTextureId() {
		return mTextureId;
	}

	public void setPosition(float[] pos) {
		setPosition(pos[0],pos[1],pos[2]);
	}

	public void setSizeAndPosition(float w, float h, float[] pos) {
		mWidth = w;
		mHeight = h;
		setPosition(pos[0],pos[1],pos[2]);
	}
	
	public void setPosition(float x, float y, float z) {
		mFVertexBuffer.position(0);
		int n = 0;
		for (int i=0; i<VERTS; i++) {
			mFVertexBuffer.put(COORDS[n++]*mWidth  + x);
			mFVertexBuffer.put(COORDS[n++]*mHeight + y);
			mFVertexBuffer.put(COORDS[n++]         + z);
		}
		mFVertexBuffer.position(0);
	}

	public void rotate(float angle) {
		mRotationAngle = angle;
	}
	public float getRotation() {
		return mRotationAngle;
	}

	public void draw(GL10 gl, float alpha, float translation[]) {
		//Log.d(TAG, "draw " + " " + alpha + " " + translation);
		if (mTextureId == null) {
			//Log.e(TAG, "No texture ID to draw Icon/label!");
			return;
		}

		glRotatef(mRotationAngle,0f,0f,1f);

		if (translation!=null) {
			glTranslatef(translation[0], translation[1], translation[2]);
		}
		// Alpha Blending
		glEnable(GL_BLEND);
		glBlendFunc (GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

		// Be sure to use the alpha from glColor
		gl.glDisableClientState(GL_COLOR_ARRAY);

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

		glBindTexture(GL_TEXTURE_2D, mTextureId);

		glTexCoordPointer(2, GL_FLOAT, 0, mTexBuffer);
		glDrawElements(GL_TRIANGLE_STRIP, VERTS, GL_UNSIGNED_SHORT, mIndexBuffer);
	}
	
	public void drawFocus(GL10 gl, float translation[]) {
		//Log.d(TAG, "drawFocus " + " " + translation);

		if (mTextureId == null) {
			//Log.e(TAG, "No texture ID to draw Icon/label!");
			return;
		}

		glRotatef(mRotationAngle,0f,0f,1f);

		if (translation!=null) {
			glTranslatef(translation[0], translation[1], translation[2]);
		}
		// Alpha Blending
		glEnable(GL_BLEND);
		glBlendFunc (GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

		// Texture blending
		glEnableClientState(GL_TEXTURE_COORD_ARRAY);
		glEnable(GL_TEXTURE_2D);
		// Use mColorBuffer as a global alpha mask:
		glTexEnvx(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL11.GL_COMBINE);
		glTexEnvx(GL_TEXTURE_ENV, GL11.GL_COMBINE_RGB, GL11.GL_ADD); // mix selection color and texture color
		glTexEnvx(GL_TEXTURE_ENV, GL11.GL_COMBINE_ALPHA, GL11.GL_MODULATE); // mix with alpha from glColor
		glColor4f(0f, 0.7f, 1f, 1f); // MAGICAL, selection color
		
		glVertexPointer(3, GL_FLOAT, 0, mFVertexBuffer);
		glBindTexture(GL_TEXTURE_2D, mTextureId);
		glTexCoordPointer(2, GL_FLOAT, 0, mTexBuffer);
		glDrawElements(GL_TRIANGLE_STRIP, VERTS, GL_UNSIGNED_SHORT, mIndexBuffer);
	}
}

