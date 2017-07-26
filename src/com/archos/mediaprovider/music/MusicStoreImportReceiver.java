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
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.archos.mediaprovider.ArchosMediaCommon;

/**
 * receiver for events that trigger mediastore import
 */
public class MusicStoreImportReceiver extends BroadcastReceiver {
    private static final String TAG =  ArchosMediaCommon.TAG_PREFIX + "MusicStoreImportReceiver";
    private static final boolean LOCAL_DBG = true;
    private static final boolean DBG = ArchosMediaCommon.PACKAGE_DBG & LOCAL_DBG;

    public MusicStoreImportReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (DBG) Log.d(TAG, "onReceive:" + intent);
        // start network scan / removal service
        NetworkScannerServiceMusic.startIfHandles(context, intent);

        // in addition and all other cases inform import service about the event
        Intent serviceIntent = new Intent(context, MusicStoreImportService.class);
        serviceIntent.setAction(intent.getAction());
        serviceIntent.setData(intent.getData());
        context.startService(serviceIntent);
    }

}
