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

import com.archos.environment.ArchosUtils;
import com.archos.medialib.BuildConfig;

import android.os.Environment;

public final class ArchosMediaCommon {
    private ArchosMediaCommon() {}

    /** Package debugging enable */
    public static final boolean PACKAGE_DBG = false;
    /** common logtag prefix, makes grep easier */
    public static final String TAG_PREFIX = "AMX";

    // paths
    public static final String EXTERNAL_STORAGE_PATH = Environment.getExternalStorageDirectory().getPath();

    // database related
    public static final int LIGHT_INDEX_STORAGE_ID_OFFSET = 32;
    // calculation like in MtpStorage.getStorageId()
    public static final int LIGHT_INDEX_MIN_STORAGE_ID = ((LIGHT_INDEX_STORAGE_ID_OFFSET + 1) << 16) + 1;

    private static final String CONTENT = "content://";
    private static final String SLASH = "/";

    public static final String AUTHORITY_ANDROID = "media";
    public static final String CONTENT_AUTHORITY_SLASH_ANDROID = CONTENT + AUTHORITY_ANDROID + SLASH;

    public static final String AUTHORITY_VIDEO; // = "com.archos.media.video";
    public static final String CONTENT_AUTHORITY_SLASH_VIDEO; // = CONTENT + AUTHORITY_VIDEO + SLASH;

    public static final String AUTHORITY_MUSIC = "com.archos.media.music";
    public static final String CONTENT_AUTHORITY_SLASH_MUSIC = CONTENT + AUTHORITY_MUSIC + SLASH;

    public static final String AUTHORITY_SCRAPER; // = "com.archos.media.scraper";
    public static final String CONTENT_AUTHORITY_SLASH_SCRAPER; // = CONTENT + AUTHORITY_SCRAPER + SLASH;

    /** Scanned files get _id >= this value. (Integer.MAX_VALUE / 2) rounded to human readable form */
    public static final long SCANNED_ID_OFFSET = 1000000000;

    static {
        if (BuildConfig.FLAVOR.contains("free")) {
            AUTHORITY_VIDEO = "com.archos.media.videofree";
            AUTHORITY_SCRAPER = "com.archos.media.scraperfree";
        } else if (BuildConfig.FLAVOR.contains("community")) {
            AUTHORITY_VIDEO = "com.archos.media.videocommunity";
            AUTHORITY_SCRAPER = "com.archos.media.scrapercommunity";
        }
         else {
            AUTHORITY_VIDEO = "com.archos.media.video";
            AUTHORITY_SCRAPER = "com.archos.media.scraper";
        }
        CONTENT_AUTHORITY_SLASH_VIDEO = CONTENT + AUTHORITY_VIDEO + SLASH;
        CONTENT_AUTHORITY_SLASH_SCRAPER = CONTENT + AUTHORITY_SCRAPER + SLASH;
    }
}
