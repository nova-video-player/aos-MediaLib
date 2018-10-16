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

import android.net.Uri;

import com.archos.filecorelibrary.FileUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by alexandre on 25/06/15.
 */
public class UriUtils {
    /*
      index only implemented schemes
   */
    public static List<String> sImplementedByFileCore = new ArrayList<>();
    public static List<String> sIndexableSchemes = new ArrayList<>();
    static{
        sImplementedByFileCore.add("smb");
        sImplementedByFileCore.add("sftp");
        sImplementedByFileCore.add("ftp");
        sImplementedByFileCore.add("ftps");
        sImplementedByFileCore.add("upnp");
        sImplementedByFileCore.add("content");
        sIndexableSchemes.addAll(sImplementedByFileCore);
        sIndexableSchemes.add("http");
        sIndexableSchemes.add("https");
    }

    public static boolean isIndexable(Uri uri){
        return isImplementedByFileCore(uri)||
                isWebUri(uri);
    }

    public static boolean isWebUri(Uri uri){
        return uri.getScheme().equals("https")||
                uri.getScheme().equals("http");
    }

    /**
     * returns if it has been implemented by filecore
     * @param uri
     * @return
     */

    public static boolean isImplementedByFileCore(Uri uri){
        return FileUtils.isLocal(uri)||uri.getScheme().equals("smb")||
                uri.getScheme().equals("upnp")||
                uri.getScheme().equals("ftps")||
                uri.getScheme().equals("ftp")||
                uri.getScheme().equals("sftp")||
                uri.getScheme().equals("content");
    }

    public static boolean isCompatibleWithRemoteDB(Uri uri) {
        return isImplementedByFileCore(uri)&&!"upnp".equals(uri.getScheme());
    }

    public static boolean isContentUri(Uri uri) {
        return "content".equals(uri.getScheme());
    }
}
