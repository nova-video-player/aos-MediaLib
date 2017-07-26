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
import android.graphics.Bitmap;

abstract public class TextureRequest {

	static final String TAG = "TextureRequest";

	public final static int TEXTURE_NOT_AVAILABLE = -1;  // not available at all, will never be

	public TextureRequester mRequester;	// object asking the texture
	public Bitmap mBitmap; 				// bitmap computed (it's the result of the request)

	public TextureRequest( TextureRequester requester ) {
		mRequester = requester;
		mBitmap = null;
	}

	public void recycleBitmaps() {
		if (mBitmap!=null) {
			mBitmap.recycle();
		}
	}

	// Compute the texture, to be implemented by each child of TextureRequest
	abstract public boolean makeBitmap(Context context, ArtworkFactory factory);

	// Get a name to represent this request in debug, to be implemented by each child of TextureRequest
	abstract public String getDebugName();

	// To be called by the GLview once the texture is ready in the GL stack
	public void glTextureIsReady( int glTextureId ) {
		mRequester.glTextureIsReady(this, glTextureId);
	}
}
