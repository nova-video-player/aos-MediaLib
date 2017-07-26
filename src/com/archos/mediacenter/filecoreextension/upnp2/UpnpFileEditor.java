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

import com.archos.filecorelibrary.FileEditor;

import java.io.InputStream;
import java.io.OutputStream;

public class UpnpFileEditor extends FileEditor {


    public UpnpFileEditor(Uri uri) {
        super(uri);
    }

    @Override
    public boolean touchFile() {
        return false;
    }

    @Override
    public boolean mkdir() {
        return false;
    }

    @Override
    public InputStream getInputStream() throws Exception {
        return null;
    }

    @Override
    public InputStream getInputStream(long from) throws Exception {
        return null;
    }

    @Override
    public OutputStream getOutputStream() throws Exception {
        return null;
    }

    @Override
    public void delete() throws Exception {

    }

    @Override
    public boolean rename(String newName) {
        return false;
    }

    @Override
    public boolean move(Uri uri) {
        return false;
    }

    @Override
    public boolean exists() {
        return false;
    }


}
