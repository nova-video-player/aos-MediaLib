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

abstract public class TextureRequester {

	static final String TAG = "TextureRequester";

	// Get a request to be sent to the TextureProvider
	abstract public TextureRequest getTextureRequest();

	// To be called by the GLview once the texture is ready in the GL stack
	abstract public void glTextureIsReady( TextureRequest tr, int glTextureId );

	// Give a GL texture ID and don't use it anymore
	abstract public int getTextureIdToRecycle();
}
