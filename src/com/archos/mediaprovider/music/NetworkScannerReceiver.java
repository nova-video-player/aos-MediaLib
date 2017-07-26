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

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;

import com.archos.mediaprovider.ArchosMediaIntent;
import com.archos.mediaprovider.music.MusicStore.MediaColumns;

import java.util.HashSet;

public class NetworkScannerReceiver extends BroadcastReceiver {
    private static final boolean DBG = false;
    private static final String TAG = "NetworkScannerReceiver";

    private static final HashSet<String> sCurrentlyScanned = new HashSet<String>();
    private static ScannerListener sListener = null;

    @Override
    public void onReceive(Context context, Intent intent) {
        Uri uri = intent.getData();
        String action = intent.getAction();
        if (DBG) Log.d(TAG, "onReceive intent:" + intent + " uri:" + uri);
        // we need uri telling us what is scanned
        if (uri == null)
            return;
        if (ArchosMediaIntent.ACTION_MUSIC_SCANNER_SCAN_STARTED.equals(action)) {
            add(uri);
        } else if (ArchosMediaIntent.ACTION_MUSIC_SCANNER_SCAN_FINISHED.equals(action)) {
            remove(uri);
        }
        if (DBG) dump();
    }

    private static synchronized void add(Uri uri) {
        if (uri == null)
            return;
        String path = uri.toString();
        sCurrentlyScanned.add(path);
        notifyListeners(path);
    }

    private static synchronized void remove(Uri uri) {
        if (uri == null)
            return;
        String path = uri.toString();
        sCurrentlyScanned.remove(path);
        notifyListeners(path);
    }

    private static void notifyListeners(String path) {
        if (sListener != null) {
            sListener.onScannerStateChanged(path);
        }
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


    public final static int STATE_UNINDEXED = 0;
    public final static int STATE_INDEXED = 1;
    public final static int STATE_WORKING = 2;

    public static void setListener(ScannerListener listener) {
        sListener = listener;
    }

    public static void unsetListener() {
        sListener = null;
    }

    private final static String[] PROJECTION = new String[] { BaseColumns._ID };
    private final static String SELECTION = MediaColumns.DATA + " LIKE ?";
    private final static String ORDER = BaseColumns._ID + " LIMIT 1";

    /**
     * Checks is scanner is working or if there is any file indexed in that path.
     * Note: Database access in UI Thread. At least it's a quick one.
     * @return 0: unindexed, 1: indexed, 2: working
     */
    public static synchronized int getState(Context context, String path) {
        if (sCurrentlyScanned.contains(path)) {
            if (DBG) Log.d(TAG, "getState(" + path + ") = working (2)");
            return STATE_WORKING;
        }
        ContentResolver cr = context.getContentResolver();
        if (!path.endsWith("/"))
            path = path + "/";
        String[] selectionArgs = new String[] { path + "%" };
        Cursor c = cr.query(MusicStore.Files.getContentUri("external"), PROJECTION, SELECTION, selectionArgs, ORDER);
        if (c != null) {
            if (c.getCount() > 0) {
                if (DBG) Log.d(TAG, "getState(" + path + ") = indexed (1)");
                return STATE_INDEXED;
            }
            c.close();
        }
        if (DBG) Log.d(TAG, "getState(" + path + ") = unindexed (0)");
        return STATE_UNINDEXED;
    }

    public static synchronized boolean isScannerWorking() {
        // if there is a path in here then scanner is working.
        return sCurrentlyScanned.size() > 0;
    }
}
