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

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;

import com.archos.mediaprovider.ArchosMediaIntent;
import com.archos.mediaprovider.video.VideoStore.MediaColumns;

import java.util.HashSet;

public class NetworkScannerReceiver extends BroadcastReceiver {
    private static final boolean DBG = false;
    private static final String TAG = "NetworkScannerReceiver";

    private static final HashSet<String> sCurrentlyScanned = new HashSet<String>();

    @Override
    public void onReceive(Context context, Intent intent) {
        Uri uri = intent.getData();
        String action = intent.getAction();
        if (DBG) Log.d(TAG, "onReceive intent:" + intent + " uri:" + uri);
        // we need uri telling us what is scanned
        if (uri == null)
            return;
        if (ArchosMediaIntent.ACTION_VIDEO_SCANNER_SCAN_STARTED.equals(action)) {
            add(uri);
        } else if (ArchosMediaIntent.ACTION_VIDEO_SCANNER_SCAN_FINISHED.equals(action)) {
            remove(uri);
        }
        if (DBG) dump();
    }

    private static synchronized void add(Uri uri) {
        if (uri == null)
            return;
        String path = uri.toString();
        sCurrentlyScanned.add(path);
    }

    private static synchronized void remove(Uri uri) {
        if (uri == null)
            return;
        String path = uri.toString();
        sCurrentlyScanned.remove(path);
    }



    private static void dump() {
        Log.d(TAG, "> --- DUMP --- <");
        for (String key : sCurrentlyScanned) {
            Log.d(TAG, "> [" + key + "]");
        }
        Log.d(TAG, "> ------------ <");
    }

    public interface ScannerListener {
        public void onScannerStateChanged(String path);
    }


    public static synchronized boolean isScannerWorking() {
        // if there is a path in here then scanner is working.
        return sCurrentlyScanned.size() > 0;
    }
}
