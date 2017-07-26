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

package com.archos.mediacenter.filecoreextension;

import android.content.Context;
import android.net.Uri;

import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.MimeUtils;
import com.archos.filecorelibrary.RawLister;
import com.archos.mediacenter.filecoreextension.upnp2.UpnpRawLister;

public class HttpFile2 extends MetaFile2 {

    private final long mLength;
    private String mName;
    private final boolean mIsDirectory;
    private final String mMimeType;

    /** the Upnp Uri */
    private final String mStreamingUri;

    /** the http url to play the content */
    private final String mContentPath;
    private final String mUri;

    /** the http url to get a thumbnail image */
    private final String mThumbnailUri;


    /**
     * Constructor only used in a very specific case:
     * when fromUri() has not parent to list from (because Uri is the root level)
     * @param indexableUri
     */
    private HttpFile2(Uri indexableUri) {
        mIsDirectory = false;
        mUri = indexableUri.toString();
        mName = indexableUri.getLastPathSegment();
        mContentPath = indexableUri.toString();
        mMimeType = null;
        mLength = -1;
        mStreamingUri= indexableUri.toString();
        mThumbnailUri = null;
    }

    @SuppressWarnings("unused")
    private HttpFile2() {
        throw new IllegalArgumentException("Unauthorized to create a UpnpFile2 from nothing! Can only be created from a java.io.File");
    }

    @Override
    public Uri getUri(){
        return Uri.parse(mUri);
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public String getNameWithoutExtension() {
        return mName;
    }
    /**
     * Get the lowercase file extension of this file. Can be null
     * Is Override because the parent version is based on the file name.
     * In UPNP case the name usually does not contain the extension.
     * However sometimes the mContentPath does not contain the extension either! */
    @Override
    public String getExtension() {
        return MimeUtils.getExtension(mContentPath);
    }

    @Override
    public boolean isDirectory() {
        return mIsDirectory;
    }

    @Override
    public boolean isFile() {
        return !mIsDirectory;
    }

    @Override
    public long lastModified() {
        return -1;
    }

    @Override
    public long length() {
        return mLength;
    }

    @Override
    public Uri getStreamingUri() {
        return mStreamingUri!=null?Uri.parse(mStreamingUri):getUri();
    }

    @Override
    public boolean canRead() {
        return true;
    }

    @Override
    public boolean canWrite() {
        return false;
    }

    @Override
    public boolean isRemote() {
        return true;
    }

    public String getUniqueHash(){
        return String.format("%016x", getUri().getHost().hashCode()+length() +getName().hashCode());

    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof HttpFile2) {
            return getUri().equals( ((HttpFile2)other).getUri());
        } else {
            return false;
        }
    }

    /** In UPnP the mimeType is not computed from the Uri like for other MetaFiles */
    @Override
    public String getMimeType() {
        return mMimeType;
    }

    public Uri getThumbnailUri() {
        if(mThumbnailUri!=null)
            return Uri.parse(mThumbnailUri);
        return null;
    }

    @Override
    public RawLister getRawListerInstance() {
        return new UpnpRawLister(getUri());
    }

    @Override
    public FileEditor getFileEditorInstance(Context ct) {
        return null; // TODO ?
    }

    /**
     * get metafile2 object from a uri (please use this only if absolutely necessary)
     * WARNING this has been specifically made for indexing !
     *
     */
    public static MetaFile2 fromUri(Uri uri){ // to build ONLY from indexable Uri !!

        return new HttpFile2(uri);
    }
}
