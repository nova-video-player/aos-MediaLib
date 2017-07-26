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

package com.archos.mediaprovider.music;

import android.net.Uri;

/* package */ class MusicStoreInternal {

    /* package */ static final Uri RAW =
    Uri.parse("content://" + MusicStore.AUTHORITY + "/raw");

    /* package */ static final Uri FILES_IMPORT =
    Uri.parse("content://" + MusicStore.AUTHORITY + "/raw/" + MusicOpenHelper.FILES_IMPORT_TABLE_NAME);
    /* package */ static final Uri FILES =
    Uri.parse("content://" + MusicStore.AUTHORITY + "/raw/" + MusicOpenHelper.FILES_TABLE_NAME);
    /* package */ static final Uri FILES_SCANNED =
    Uri.parse("content://" + MusicStore.AUTHORITY + "/raw/" + MusicOpenHelper.FILES_SCANNED_TABLE_NAME);

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

    private MusicStoreInternal() { /* empty */ }
}
