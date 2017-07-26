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

import java.io.IOException;
import java.util.Map;

/**
 * Created by alexandre on 26/05/15.
 */
public abstract class Proxy {
    protected final Uri mUri;

    protected Proxy(Uri uri) {
        mUri = uri;
    }
    public static boolean needToStream(String scheme){
        return "smb".equalsIgnoreCase(scheme) || "upnp".equalsIgnoreCase(scheme)||"ftp".equalsIgnoreCase(scheme)||"ftps".equalsIgnoreCase(scheme) || "sftp".equalsIgnoreCase(scheme);
    }
    protected abstract Uri start();

    public abstract void stop();

    public static Proxy setDataSource(Uri uri, IMediaPlayer mp, Map<String, String> headers) throws IOException {
            if("upnp".equals(uri.getScheme())){
                return UpnpProxy.setDataSource(uri,mp,headers);
            }
            else
                return SmbProxy.setDataSource(uri, mp, headers);
    }

    public static Proxy setDataSource(Uri uri, IMediaMetadataRetriever mr, Map<String, String> headers) throws IllegalArgumentException {
        if("upnp".equals(uri.getScheme())){
            return UpnpProxy.setDataSource(uri,mr,headers);
        }
        else
            return SmbProxy.setDataSource(uri, mr, headers);
    }
}
