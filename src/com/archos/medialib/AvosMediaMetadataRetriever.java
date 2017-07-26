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

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.net.Uri;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;


public class AvosMediaMetadataRetriever implements IMediaMetadataRetriever
{
    // The field below is accessed by native methods
    private long mMediaMetadataRetrieverHandle;

    private SmbProxy mSmbProxy = null;
 
    private static final int EMBEDDED_PICTURE_TYPE_ANY = 0xFFFF;

    private native void create();

    public AvosMediaMetadataRetriever() {
        create();
    }

    public int getType() {
        return IMediaMetadataRetriever.TYPE_AVOS;
    }

    public native void setDataSource(String path, String[] keys, String[] values) throws IllegalArgumentException;

    public void setDataSource(String uri,  Map<String, String> headers)
            throws IllegalArgumentException {
        if (SmbProxy.needToStream(Uri.parse(uri).getScheme())) {
            mSmbProxy = SmbProxy.setDataSource(Uri.parse(uri), this, headers);
            return;
        }
        if (headers != null) {
            int i = 0;

            String[] keys = new String[headers.size()];
            String[] values = new String[headers.size()];
            for (Map.Entry<String, String> entry: headers.entrySet()) {
                keys[i] = entry.getKey();
                values[i] = entry.getValue();
                ++i;
            }
            setDataSource(uri, keys, values);
        } else {
            setDataSource(uri, null, null);
        }
    }

    public void setDataSource(String uri)
             throws IllegalArgumentException {
        setDataSource(uri, null);
    }

    private native void setDataSourceFD(FileDescriptor fd, long offset, long length)
            throws IllegalArgumentException;

    public void setDataSource(FileDescriptor fd, long offset, long length)
            throws IllegalArgumentException {
        setDataSourceFD(fd, offset, length);
    }

    public void setDataSource(FileDescriptor fd)
            throws IllegalArgumentException {
        // intentionally less than LONG_MAX
        setDataSource(fd, 0, 0x7ffffffffffffffL);
    }
    
    public void setDataSource(Context context, Uri uri)
        throws IllegalArgumentException, SecurityException {
        if (uri == null) {
            throw new IllegalArgumentException();
        }
        
        String scheme = uri.getScheme();
        if (SmbProxy.needToStream(scheme)){
            mSmbProxy = SmbProxy.setDataSource(uri, this, null);
            return;
        }
        if(scheme == null || scheme.equals("file")) {
            setDataSource(uri.getPath());
            return;
        }

        AssetFileDescriptor fd = null;
        try {
            ContentResolver resolver = context.getContentResolver();
            try {
                fd = resolver.openAssetFileDescriptor(uri, "r");
            } catch(FileNotFoundException e) {
                throw new IllegalArgumentException();
            }
            if (fd == null) {
                throw new IllegalArgumentException();
            }
            FileDescriptor descriptor = fd.getFileDescriptor();
            if (!descriptor.valid()) {
                throw new IllegalArgumentException();
            }
            // Note: using getDeclaredLength so that our behavior is the same
            // as previous versions when the content provider is returning
            // a full file.
            if (fd.getDeclaredLength() < 0) {
                setDataSource(descriptor);
            } else {
                setDataSource(descriptor, fd.getStartOffset(), fd.getDeclaredLength());
            }
            return;
        } catch (SecurityException ex) {
        } finally {
            try {
                if (fd != null) {
                    fd.close();
                }
            } catch(IOException ioEx) {
            }
        }
        setDataSource(uri.toString(), null, null);
    }

    private native void nativeRelease();
    public void release() {
        nativeRelease();
        if (mSmbProxy != null) {
            mSmbProxy.stop();
            mSmbProxy = null;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        release();
    }

    public native String extractMetadata(int keyCode);

    private native final byte[] getMetadata();

    public MediaMetadata getMediaMetadata() {
        AvosMediaMetadata data = new AvosMediaMetadata();

        byte[] bytes = getMetadata();
        if (bytes == null)
            return null;

        if (!data.parse(bytes))
            return null;

        return data;
    }

    private native Bitmap nativeGetFrameAtTime(long timeUs, int option);

    public Bitmap getFrameAtTime(long timeUs, int option) {
        if (option < IMediaMetadataRetriever.OPTION_PREVIOUS_SYNC ||
            option > IMediaMetadataRetriever.OPTION_CLOSEST) {
            throw new IllegalArgumentException("Unsupported option: " + option);
        }

        return nativeGetFrameAtTime(timeUs, option);
    }

    public Bitmap getFrameAtTime(long timeUs) {
        return getFrameAtTime(timeUs, IMediaMetadataRetriever.OPTION_CLOSEST_SYNC);
    }

    public Bitmap getFrameAtTime() {
        return getFrameAtTime(-1, IMediaMetadataRetriever.OPTION_CLOSEST_SYNC);
    }

    private native byte[] getEmbeddedPicture(int pictureType);

    public byte[] getEmbeddedPicture() {
        return getEmbeddedPicture(EMBEDDED_PICTURE_TYPE_ANY);
    }
}
