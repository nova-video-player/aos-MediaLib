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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.archos.mediaprovider.ArchosMediaCommon;
import com.archos.mediaprovider.NetworkState;

/** Receives network state updates from Android and maintains the local state we keep */
public class NetworkStateReceiver extends BroadcastReceiver {
    private static final String TAG = ArchosMediaCommon.TAG_PREFIX + NetworkStateReceiver.class.getSimpleName();

    // 3 state: -1 unknown, 0 false, 1 true
    private static int sHasLocalNetwork = -1;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
            ConnectivityManager conn = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = conn.getActiveNetworkInfo();
            NetworkState state = NetworkState.instance(context);
            state.updateFrom(networkInfo);
            int hasLocalNetwork = state.hasLocalConnection() ? 1 : 0;
            // there should be no need for synchronization etc since this is always executed
            // on the UI thread which won't change as long as the Application exists.
            if (sHasLocalNetwork != hasLocalNetwork) {
                sHasLocalNetwork = hasLocalNetwork;
                // on network change notify smb state service
                SmbStateService.start(context);
            }
        } else {
            Log.d(TAG, "NetworkStateReceiver does not know how to handle " + intent);
        }
    }

}
