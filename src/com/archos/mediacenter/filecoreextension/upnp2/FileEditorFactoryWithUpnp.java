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
import com.archos.filecorelibrary.FileEditorFactory;
import com.archos.mediacenter.filecoreextension.HttpFileEditor;

/**
 * create a file editor
 * @author alexandre
 *
 */
public class FileEditorFactoryWithUpnp {
    public static FileEditor getFileEditorForUrl(Uri uri, Context ct) {
        if ("upnp".equals(uri.getScheme())) {
            return new UpnpFileEditor(uri);
        }
        if ("http".equals(uri.getScheme())||"https".equals(uri.getScheme())) {
            return new HttpFileEditor(uri);
        }
        else {
            return FileEditorFactory.getFileEditorForUrl(uri,ct);
        }
    }
}
