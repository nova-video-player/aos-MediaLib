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

package com.archos.mediacenter.filecoreextension.upnp2;

import android.content.Context;
import android.net.Uri;

import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.MimeUtils;
import com.archos.filecorelibrary.RawLister;
import com.archos.filecorelibrary.FileUtils;
import com.archos.filecorelibrary.ftp.AuthenticationException;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

import org.fourthline.cling.support.model.item.Item;

import java.io.IOException;
import java.util.List;

public class UpnpFile2 extends MetaFile2 {

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
     * Container (i.e. directory) constructor
     * @param container
     * @param parentUri
     */
    public UpnpFile2(String containerName, String containerId, Uri parentUri) {
        mName = containerName;
        mIsDirectory = true;
        mUri = Uri.withAppendedPath(parentUri, containerId).toString();
        mStreamingUri = null ;
        mLength = -1;
        mContentPath = null; // null for directories, not actual content that can be played
        mMimeType = null; // null for directories
        mThumbnailUri = null; // null for directories
    }

    /**
     * Item (i.e. file) constructor
     * @param item
     * @param parentUri
     * @param contentPath
     */
    public UpnpFile2(Item item, String mimeType, Uri parentUri, String contentPath, Uri thumbnailUri) {
        mName = item.getTitle();
        mMimeType = mimeType;
        mIsDirectory = false;
        mLength = item.getFirstResource().getSize();
        mStreamingUri = contentPath;
        mContentPath = contentPath;
        if(thumbnailUri!=null)
            mThumbnailUri = thumbnailUri.toString();
        else
            mThumbnailUri=null;

        mUri = Uri.withAppendedPath(parentUri, getName()).toString();
    }

    /**
     * Constructor only used in a very specific case:
     * when fromUri() has not parent to list from (because Uri is the root level)
     * @param indexableUri
     */
    private UpnpFile2(Uri indexableUri) {
        mIsDirectory = true;
        mUri = indexableUri.toString();
        mName = indexableUri.getLastPathSegment();
        mContentPath = null;
        mMimeType = null;
        mLength = -1;
        mStreamingUri= indexableUri.toString();
        mThumbnailUri = null;
    }

    @SuppressWarnings("unused")
    private UpnpFile2() {
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
        if (other instanceof UpnpFile2) {
            return getUri().equals( ((UpnpFile2)other).getUri());
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
        Uri parentUri = FileUtils.getParentUrl(uri);
        //we try to list parent, if we can't, we assume this is a folder
        if(parentUri!=null&&parentUri.getPath()!=null){
            RawLister lister = RawListerFactoryWithUpnp.getRawListerForUrl(parentUri);
            try {
                List<MetaFile2> files = lister.getFileList();
                for(MetaFile2 file : files){
                    if(file.getUri().equals(uri)){
                        return file;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (AuthenticationException e) {
                e.printStackTrace();
            } catch (SftpException e) {
                e.printStackTrace();
            } catch (JSchException e) {
                e.printStackTrace();
            }
            return null;
        }
        // fallback in case there is no parent (root)
        return new UpnpFile2(uri);
    }
}
