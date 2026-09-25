// Copyright 2026 Courville Software
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
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import java.io.IOException;

/** Selects provider descriptors supported by AVOS's bounded pread input. */
final class ContentFileDescriptor {
    private ContentFileDescriptor() { }

    static AssetFileDescriptor open(ContentResolver resolver, Uri uri) throws IOException {
        AssetFileDescriptor asset = resolver.openAssetFileDescriptor(uri, "r");
        if (asset == null) return null;
        boolean accepted = false;
        try {
            StructStat stat = Os.fstat(asset.getFileDescriptor());
            long start = asset.getStartOffset();
            long length = asset.getDeclaredLength();
            if (!OsConstants.S_ISREG(stat.st_mode) || start < 0 || start >= stat.st_size
                    || length == 0 || (length >= 0 && length > stat.st_size - start)) {
                return null;
            }
            accepted = true;
            return asset;
        } catch (ErrnoException e) {
            throw new IOException("Unable to inspect provider descriptor", e);
        } finally {
            if (!accepted) asset.close();
        }
    }
}
