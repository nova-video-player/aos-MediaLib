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

package com.archos.mediaprovider.video;

import android.net.Uri;

// Made public in 2015 for remote video un-indexing
/* package */ public class VideoStoreInternal {

    /* package */ static final Uri RAW =
    Uri.parse("content://" + VideoStore.AUTHORITY + "/raw");

    /* package */ static final Uri RAWQUERY =
    Uri.parse("content://" + VideoStore.AUTHORITY + "/rawquery");

    /* package */ static final Uri FILES_IMPORT =
    Uri.parse("content://" + VideoStore.AUTHORITY + "/raw/" + VideoOpenHelper.FILES_IMPORT_TABLE_NAME);
    /* package */ static final Uri FILES =
    Uri.parse("content://" + VideoStore.AUTHORITY + "/raw/" + VideoOpenHelper.FILES_TABLE_NAME);

    // Made public in 2015 for remote video un-indexing
    /* package */ public static final Uri FILES_SCANNED =
    Uri.parse("content://" + VideoStore.AUTHORITY + "/raw/" + VideoOpenHelper.FILES_SCANNED_TABLE_NAME);

    /* package */ static final Uri HIDE_VOLUME =
    Uri.parse("content://" + VideoStore.AUTHORITY + "/raw/" + VideoOpenHelper.HIDE_VOLUMES_VIEW_NAME);

    /** Access to any table / view via this Uri */
    /* package */ static Uri getRawUri(String tableName) {
        return RAW.buildUpon().appendPath(tableName).build();
    }

    /* package */ static final String KEY_SCANNER = "scanner_update";
    /* package */ static final String FILES_EXTRA_COLUMN_SCAN_STATE = "scan_state";
    /* package */ static final String SCAN_STATE_UNSCANNED = "0";
    /** anything above 0 is scanned */
    /* package */ static final String SCAN_STATE_SCANNED = "1";
    /* package */ static final String SCAN_STATE_SCAN_FAILED = "-888";

    private VideoStoreInternal() { /* empty */ }
}
