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
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.opengles.GL10;


public class FadeOutBand {

	static final String TAG = "FadeOutBand";

	private final static int VERTS = 4;

	private static final float[] COORDS = {
		// X, Y, Z
		-0.5f,-0.5f, 0,
		0.5f,-0.5f, 0,
		-0.5f, 0.5f, 0,
		0.5f, 0.5f, 0
	};
	
	private static final int one = 0x10000;

	private static final int COLORS[] = {
            one, one, one,  one,
            one, one, one,  one,
            one, one, one,  0,
            one, one, one,  0,
    };

	private float mWidth = 1f;
	private float mHeight = 1f;

	private FloatBuffer mFVertexBuffer;
	private ShortBuffer mIndexBuffer;
	private IntBuffer mColorBuffer;

	public FadeOutBand() {
		Init(1f,1f,0f,0f,0f);
	}

	public FadeOutBand( float w, float h, float pos[] ) {
		Init(w,h,pos[0],pos[1],pos[2]);
	}

	public FadeOutBand( float w, float h) {
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


        ByteBuffer cbb = ByteBuffer.allocateDirect(COLORS.length*4);
        cbb.order(ByteOrder.nativeOrder());
        mColorBuffer = cbb.asIntBuffer();
        mColorBuffer.put(COLORS);
        
		mFVertexBuffer.position(0);
		mIndexBuffer.position(0);
        mColorBuffer.position(0);
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

	public void draw(GL10 gl) {
		
		gl.glEnableClientState(GL_VERTEX_ARRAY);
		gl.glEnableClientState(GL_COLOR_ARRAY);

		gl.glDisableClientState(GL_TEXTURE_COORD_ARRAY); // Get a crash on K1 if this is not explicitly disabled
		gl.glDisable(GL_TEXTURE_2D);

		gl.glEnable(GL_BLEND);
		glBlendFunc(GL_ZERO, GL_ONE_MINUS_SRC_ALPHA); // mColorBuffer is used for its alpha only
		
		gl.glVertexPointer(3, GL10.GL_FLOAT, 0, mFVertexBuffer);
		gl.glColorPointer(4, GL10.GL_FIXED, 0, mColorBuffer);

		glDrawElements(GL_TRIANGLE_STRIP, VERTS, GL_UNSIGNED_SHORT, mIndexBuffer);
	}
}

