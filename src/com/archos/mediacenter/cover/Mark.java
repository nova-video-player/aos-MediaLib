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


public class Mark {

	static final String TAG = "Mark";

	private final static int VERTS = 4;

	private static float[] COORDS = {
		// X, Y, Z
		-.1f,-.1f, 0,
		.1f,-.1f, 0,
		-.1f, .1f, 0,
		.1f, .1f, 0
	};


	private FloatBuffer mFVertexBuffer;
	private ShortBuffer mIndexBuffer;

	public float mZ = 0;	// depth

	public Mark() {
		Init(0f,0f,0f);
	}

	public Mark( float x, float y, float z) {
		Init(x,y,z);
	}

	private void Init( float x, float y, float z) {

		ByteBuffer vbb = ByteBuffer.allocateDirect(VERTS * 3 * 4);
		vbb.order(ByteOrder.nativeOrder());
		mFVertexBuffer = vbb.asFloatBuffer();

		ByteBuffer ibb = ByteBuffer.allocateDirect(VERTS * 2);
		ibb.order(ByteOrder.nativeOrder());
		mIndexBuffer = ibb.asShortBuffer();

		mZ = z; // planar object

		int n = 0;
		for (int i=0; i<VERTS; i++) {
			mFVertexBuffer.put( COORDS[n++] + x);
			mFVertexBuffer.put( COORDS[n++] + y);
			mFVertexBuffer.put( COORDS[n++] + z);
		}

		for(int i = 0; i < VERTS; i++) {
			mIndexBuffer.put((short) i);
		}

		mFVertexBuffer.position(0);
		mIndexBuffer.position(0);
	}

	public float getDepth() {
		return mZ;
	}

	public void move( float dx, float dy, float dz) {
		mFVertexBuffer.position(0);
		float[] tmparray = new float[mFVertexBuffer.capacity()];
		mFVertexBuffer.get(tmparray);
		mFVertexBuffer.position(0);
		int n = 0;
		for (int i=0; i<VERTS; i++) {
			mFVertexBuffer.put( tmparray[n++] + dx);
			mFVertexBuffer.put( tmparray[n++] + dy);
			mFVertexBuffer.put( tmparray[n++] + dz);
		}
		mFVertexBuffer.position(0);

		mZ += dz;
	}


	public void draw(GL10 gl, float alpha) {

		glPushMatrix();
		glFrontFace(GL_CCW);
		glDisable(GL_TEXTURE_2D);

		// Alpha from glColor will be used thanks to GL_MODULATE mode
		glColor4f(1f, 0, 0, alpha);
		glVertexPointer(3, GL_FLOAT, 0, mFVertexBuffer);

		glDrawElements(GL_TRIANGLE_STRIP, VERTS, GL_UNSIGNED_SHORT, mIndexBuffer);

		glPopMatrix();
	}
}

