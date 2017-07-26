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

package com.archos.medialib;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import java.util.Map;

/**
 * Proxy class between android MediaMetadataRetriever class and
 * IMediaMetadataRetriever interface
 */
public class AndroidMediaMetadataRetriever extends MediaMetadataRetriever implements IMediaMetadataRetriever {

    private SmbProxy mSmbProxy = null;

    public MediaMetadata getMediaMetadata() {
        return null;
    }

    public int getType() {
        return IMediaMetadataRetriever.TYPE_ANDROID;
    }

    @Override
    public void setDataSource(Context context, Uri uri) throws IllegalArgumentException,
            SecurityException {
        String scheme = uri.getScheme();
        if (SmbProxy.needToStream(scheme)){
            mSmbProxy = SmbProxy.setDataSource(uri, this, null);
            return;
        }
        super.setDataSource(context, uri);
    }

    @Override
    public void setDataSource(String path) throws IllegalArgumentException {
        if (SmbProxy.needToStream(Uri.parse(path).getScheme())){
            mSmbProxy = SmbProxy.setDataSource(Uri.parse(path), this, null);
            return;
        }
        super.setDataSource(path);
    }

    @Override
    public void setDataSource(String uri, Map<String, String> headers)
            throws IllegalArgumentException {
        if (SmbProxy.needToStream(Uri.parse(uri).getScheme())){
            mSmbProxy = SmbProxy.setDataSource(Uri.parse(uri), this, headers);
            return;
        }
        super.setDataSource(uri, headers);
    }

    @Override
    public void release() {
        // TODO Auto-generated method stub
        super.release();
        if (mSmbProxy != null) {
            mSmbProxy.stop();
            mSmbProxy = null;
        }
    }
}
