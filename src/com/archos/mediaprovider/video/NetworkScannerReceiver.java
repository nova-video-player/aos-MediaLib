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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;

public class NetworkScannerReceiver extends BroadcastReceiver {
    private static final Logger log = LoggerFactory.getLogger(NetworkScannerReceiver.class);

    private static final HashSet<String> sCurrentlyScanned = new HashSet<String>();

    @Override
    public void onReceive(Context context, Intent intent) {
        Uri uri = intent.getData();
        String action = intent.getAction();
        log.debug("onReceive intent:" + intent + " uri:" + uri);
        // we need uri telling us what is scanned
        if (uri == null)
            return;
        if (ArchosMediaIntent.ACTION_VIDEO_SCANNER_SCAN_STARTED.equals(action)) {
            add(uri);
        } else if (ArchosMediaIntent.ACTION_VIDEO_SCANNER_SCAN_FINISHED.equals(action)) {
            remove(uri);
        }
        if (log.isDebugEnabled()) dump();
    }

    private static synchronized void add(Uri uri) {
        if (uri == null)
            return;
        log.debug("add uri:" + uri);
        String path = uri.toString();
        sCurrentlyScanned.add(path);
    }

    private static synchronized void remove(Uri uri) {
        if (uri == null)
            return;
        log.debug("remove uri:" + uri);
        String path = uri.toString();
        sCurrentlyScanned.remove(path);
    }

    private static void dump() {
        log.debug("> --- DUMP --- <");
        for (String key : sCurrentlyScanned) {
            log.debug("> [" + key + "]");
        }
        log.debug("> ------------ <");
    }

    public interface ScannerListener {
        public void onScannerStateChanged(String path);
    }


    public static synchronized boolean isScannerWorking() {
        // if there is a path in here then scanner is working.
        log.debug("isScannerWorking: sCurrentlyScanned.size()=" + sCurrentlyScanned.size() + " " + (sCurrentlyScanned.size() > 0));
        return sCurrentlyScanned.size() > 0;
    }
}
