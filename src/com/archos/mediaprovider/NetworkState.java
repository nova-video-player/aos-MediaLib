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
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import android.util.Log;

import com.archos.environment.ArchosUtils;

import java.util.WeakHashMap;

/** network state updated from NetworkStateReceiver, should always represent the current state */
public class NetworkState {
    private static final String TAG = ArchosMediaCommon.TAG_PREFIX + NetworkState.class.getSimpleName();
    private static final boolean DBG = false;

    private boolean mConnected;
    private boolean mConnectedOrConnecting;
    private boolean mHasLocalConnection;
    // abusing WeakHashMap to have a list of WeakReferences to Observers
    private final WeakHashMap<Observer, Void> mObservers = new WeakHashMap<Observer, Void>();
    private Context mContext;

    /** implement & register for observing hasLocalConnection() */
    public interface Observer {
        public void onLocalNetworkState(boolean available);
    }

    // singleton, volatile to make double-checked-locking work correctly
    private static volatile NetworkState sInstance;

    /** may return null but no Context required */
    public static NetworkState peekInstance() {
        return sInstance;
    }

    /** get's the instance, context is used for initial update */
    public static NetworkState instance(Context context) {
        if (sInstance == null) {
            synchronized (NetworkState.class) {
                if (sInstance == null) {
                    NetworkState state = new NetworkState(context);
                    sInstance = state;
                }
            }
        }
        return sInstance;
    }

    protected NetworkState(Context context) {
        mContext = context;
        updateFrom(context);
    }

    public boolean hasLocalConnection() { return mHasLocalConnection; }

    public boolean isConnected() { return mConnected; }

    public boolean updateFrom(Context context) {
        // returns true when that changes hasLocalConnection
        boolean returnBoolean = false;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean connected = ArchosUtils.isNetworkConnected(context);
        if (connected != mConnected) {
            if (DBG) Log.d(TAG, "updateFrom: connected changed " + mConnected + "->" +  connected);
            mConnected = connected;
        }
        boolean hasLocalConnection = ArchosUtils.isLocalNetworkConnected(mContext) || preferences.getBoolean("vpn_mobile", false);
        if (hasLocalConnection != mHasLocalConnection) {
            if (DBG) Log.d(TAG, "updateFrom: connected changed " + mHasLocalConnection + "->" +  hasLocalConnection);
            mHasLocalConnection = hasLocalConnection;
            returnBoolean = true;
            handleChange();
        }
        return returnBoolean;
    }

    private synchronized void handleChange() {
        for(Observer observer : mObservers.keySet()) {
            if (observer != null)
                observer.onLocalNetworkState(mHasLocalConnection);
        }
    }

}
