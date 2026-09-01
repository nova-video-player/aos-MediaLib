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
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Map;

/**
 * Proxy class between android MediaMetadataRetriever class and
 * IMediaMetadataRetriever interface
 */
public class AndroidMediaMetadataRetriever implements IMediaMetadataRetriever {

    private final MediaMetadataRetriever mRetriever = new MediaMetadataRetriever();
    private Proxy mFileProxy = null;

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
        if (Proxy.needToStream(scheme)) {
            mFileProxy = Proxy.setDataSource(uri, this, null);
            return;
        }
        mRetriever.setDataSource(context, uri);
    }

    @Override
    public void setDataSource(String path) throws IllegalArgumentException {
        if (Proxy.needToStream(Uri.parse(path).getScheme())) {
            mFileProxy = Proxy.setDataSource(Uri.parse(path), this, null);
            return;
        }
        mRetriever.setDataSource(path);
    }

    @Override
    public void setDataSource(String uri, Map<String, String> headers)
            throws IllegalArgumentException {
        if (Proxy.needToStream(Uri.parse(uri).getScheme())) {
            mFileProxy = Proxy.setDataSource(Uri.parse(uri), this, headers);
            return;
        }
        mRetriever.setDataSource(uri, headers);
    }

    @Override
    public void setDataSource(FileDescriptor fd, long offset, long length)
            throws IllegalArgumentException {
        mRetriever.setDataSource(fd, offset, length);
    }

    @Override
    public void setDataSource(FileDescriptor fd) throws IllegalArgumentException {
        mRetriever.setDataSource(fd);
    }

    @Override
    public String extractMetadata(int keyCode) {
        return mRetriever.extractMetadata(keyCode);
    }

    @Override
    public Bitmap getFrameAtTime(long timeUs, int option) {
        return mRetriever.getFrameAtTime(timeUs, option);
    }

    @Override
    public Bitmap getFrameAtTime(long timeUs) {
        return mRetriever.getFrameAtTime(timeUs);
    }

    @Override
    public Bitmap getFrameAtTime() {
        return mRetriever.getFrameAtTime();
    }

    @Override
    public byte[] getEmbeddedPicture() {
        return mRetriever.getEmbeddedPicture();
    }

    @Override
    public void release() throws IOException {
        try {
            mRetriever.release();
        } finally {
            if (mFileProxy != null) {
                mFileProxy.stop();
                mFileProxy = null;
            }
        }
    }
}
