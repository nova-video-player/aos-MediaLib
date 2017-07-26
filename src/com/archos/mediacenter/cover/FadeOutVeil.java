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


public class FadeOutVeil {

	static final String TAG = "FadeOutVeil";

	private final static int VERTS = 4;

	private static final float[] COORDS = {
		// X, Y, Z
		-0.5f,-0.5f, 0,
		0.5f,-0.5f, 0,
		-0.5f, 0.5f, 0,
		0.5f, 0.5f, 0
	};

	private float mWidth = 1f;
	private float mHeight = 1f;

	private FloatBuffer mFVertexBuffer;
	private ShortBuffer mIndexBuffer;

	public FadeOutVeil() {
		Init(1f,1f,0f,0f,0f);
	}

	public FadeOutVeil( float w, float h, float pos[] ) {
		Init(w,h,pos[0],pos[1],pos[2]);
	}

	public FadeOutVeil( float w, float h) {
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

		ByteBuffer ibb = ByteBuffer.allocateDirect(VERTS * 2);
		ibb.order(ByteOrder.nativeOrder());
		mIndexBuffer = ibb.asShortBuffer();

		int n = 0;
		for (int i=0; i<VERTS; i++) {
			mFVertexBuffer.put( COORDS[n++]*mWidth  + x);
			mFVertexBuffer.put( COORDS[n++]*mHeight + y);
			mFVertexBuffer.put( COORDS[n++]         + z);
		}

		for(int i = 0; i < VERTS; i++) {
			mIndexBuffer.put((short) i);
		}
        
		mFVertexBuffer.position(0);
		mIndexBuffer.position(0);
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

	public void draw(GL10 gl, float alpha) {

	    gl.glDisable(GL_TEXTURE_2D);
	    gl.glEnable(GL_BLEND);
		gl.glEnableClientState(GL_VERTEX_ARRAY);
		gl.glDisableClientState(GL_COLOR_ARRAY);

		// Use mColorBuffer as a global alpha mask:
		glTexEnvx(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL11.GL_COMBINE);
		glTexEnvx(GL_TEXTURE_ENV, GL11.GL_COMBINE_RGB, GL11.GL_REPLACE);
		glTexEnvx(GL_TEXTURE_ENV, GL11.GL_COMBINE_ALPHA, GL11.GL_MODULATE); // mix with alpha from glColor

		// Alpha from glColor will be used thanks to GL_MODULATE mode
		glColor4f(1f, 1f, 1f, 1f-alpha);

		glBlendFunc(GL_ZERO, GL_ONE_MINUS_SRC_ALPHA);

		gl.glVertexPointer(3, GL10.GL_FLOAT, 0, mFVertexBuffer);

		glDrawElements(GL_TRIANGLE_STRIP, VERTS, GL_UNSIGNED_SHORT, mIndexBuffer);
	}
}

