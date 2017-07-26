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

import android.net.Uri;

import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.MetaFile2Factory;
import com.archos.mediacenter.filecoreextension.HttpFile2;

public class MetaFileFactoryWithUpnp {

    public static MetaFile2 getMetaFileForUrl(Uri uri) throws Exception {

        if ("upnp".equals(uri.getScheme())) {
            return UpnpFile2.fromUri(uri);
        }
        if ("http".equals(uri.getScheme())||"https".equals(uri.getScheme())) {
            return HttpFile2.fromUri(uri);
        }
        else return MetaFile2Factory.getMetaFileForUrl(uri);
    }
}
