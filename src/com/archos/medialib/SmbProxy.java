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

import android.net.Uri;

import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.MetaFile2Factory;
import com.archos.filecorelibrary.MimeUtils;
import com.archos.filecorelibrary.StreamOverHttp;
import com.archos.mediacenter.filecoreextension.UriUtils;

import java.io.IOException;
import java.util.Map;


public class SmbProxy extends Proxy{
    private StreamOverHttp mStream;

    protected SmbProxy(Uri uri) {
        super(uri);
    }
    public static boolean needToStream(String scheme){
            return "smb".equalsIgnoreCase(scheme) || "ftp".equalsIgnoreCase(scheme)||"ftps".equalsIgnoreCase(scheme) || "sftp".equalsIgnoreCase(scheme) || UriUtils.isContentUri(Uri.parse(scheme+"://test"));
    }
    protected Uri start() {
        stop();
        String mimeType = MimeUtils.guessMimeTypeFromExtension(mUri.getLastPathSegment());
        MetaFile2 file = null;
        try {
            try {
                file = MetaFile2Factory.getMetaFileForUrl(mUri);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if(file != null)
                mStream = new StreamOverHttp(file, mimeType);
            else
                mStream = new StreamOverHttp(mUri, mimeType);
        } catch (IOException e) {
            return null;
        }
        return mStream.getUri(file != null ? file.getName():mUri.getLastPathSegment());
    }
    
    public void stop() {
        if (mStream != null) {
            mStream.close();
            mStream = null;
        } 
    }
    
    public static SmbProxy setDataSource(Uri uri, IMediaPlayer mp, Map<String, String> headers) throws IOException {
        SmbProxy smbProxy = new SmbProxy(uri);
        Uri newUri = smbProxy.start();
        if (newUri != null) {
            if(headers!=null)
                mp.setDataSource2(newUri.toString(), headers);
            else
                mp.setDataSource(newUri.toString());
            return smbProxy;
        } else {
            throw new IOException();
        }
    }
    
    public static SmbProxy setDataSource(Uri uri, IMediaMetadataRetriever mr, Map<String, String> headers) throws IllegalArgumentException {
        SmbProxy smbProxy = new SmbProxy(uri);
        Uri newUri = smbProxy.start();
        if (newUri != null) {
            mr.setDataSource(newUri.toString(), headers);
            return smbProxy;
        } else {
            throw new IllegalArgumentException();
        }
    }

    public int doesCurrentFileExists() {

        return mStream.doesCurrentFileExists();
    }
}
