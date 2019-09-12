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

package com.archos.mediaprovider;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.archos.environment.ArchosUtils;

public class NetworkScanner {

    /** sends broadcast that triggers mediacenter-video network scanning */
    public static void scanVideos(Context context, String location) {
        if (location != null)
            scanVideos(context, Uri.parse(location));
    }

    /** sends broadcast that triggers mediacenter-video network scanning */
    public static void scanVideos(Context context, Uri uri) {
        if (context != null && uri != null) {
            Intent intent = new Intent(ArchosMediaIntent.ACTION_VIDEO_SCANNER_SCAN_FILE, uri);
            intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
            context.sendBroadcast(intent);
        }
    }

    /** sends broadcast that triggers mediacenter-video removal of files */
    public static void removeVideos(Context context, String location) {
        if (location != null)
            removeVideos(context, Uri.parse(location));
    }

    /** sends broadcast that triggers mediacenter-video removal of files */
    public static void removeVideos(Context context, Uri uri) {
        if (context != null && uri != null) {
            Intent intent = new Intent(ArchosMediaIntent.ACTION_VIDEO_SCANNER_REMOVE_FILE, uri);
            intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
            context.sendBroadcast(intent);
        }
    }
}
