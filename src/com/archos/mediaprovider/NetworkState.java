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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.util.Log;


import java.util.WeakHashMap;

/** network state updated from NetworkStateReceiver, should always represent the current state */
public class NetworkState {
    private static final String TAG = ArchosMediaCommon.TAG_PREFIX + NetworkState.class.getSimpleName();
    private static final boolean DBG = false;

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

    private int mNetworkType = -1;
    private boolean mConnected;
    private boolean mConnectedOrConnecting;
    private boolean mAvailable;
    private boolean mHasLocalConnection;
    // abusing WeakHashMap to have a list of WeakReferences to Observers
    private final WeakHashMap<Observer, Void> mObservers = new WeakHashMap<Observer, Void>();
    private Context mContext;

    // ------------------------------ PUBLIC API ---------------------------- //
    public boolean hasLocalConnection() {
        return mHasLocalConnection;
    }

    public boolean isConnected() {
        return mConnected;
    }

    public boolean isConnectedOrConnecting() {
        return mConnectedOrConnecting;
    }

    public boolean isAvailable() {
        return mAvailable;
    }

    public void addObserver(Observer observer) {
        if (observer == null) {
            throw new NullPointerException();
        }
        synchronized (this) {
            if (!mObservers.containsKey(observer))
                mObservers.put(observer, null);
        }
    }

    public synchronized void removeObserver(Observer observer) {
        mObservers.remove(observer);
    }

    public boolean updateFrom(Context context) {
        ConnectivityManager conn = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = conn.getActiveNetworkInfo();
        return updateFrom(networkInfo);
    }

    /** update with new NetworkInfo, returns true when that changes hasLocalConnection */
    public boolean updateFrom(NetworkInfo info) {
        if (DBG) Log.d(TAG, "NetState update:" + info);
        if (info != null) {
            setNetworkType(info.getType());
            setAvailable(info.isAvailable());
            setConnected(info.isConnected());
            setConnectedOrConnecting(info.isConnectedOrConnecting());
        } else {
            reset();
        }

        if (updateLocalConnection()) {
            handleChange();
            return true;
        }
        return false;
    }

    private synchronized void handleChange() {
        for(Observer observer : mObservers.keySet()) {
            if (observer != null)
                observer.onLocalNetworkState(mHasLocalConnection);
        }
    }

    private boolean updateLocalConnection() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        boolean localConnection = mConnected &&
                (mNetworkType == ConnectivityManager.TYPE_ETHERNET
                || mNetworkType == ConnectivityManager.TYPE_WIFI
                || preferences.getBoolean("vpn_mobile", false));

        if (mHasLocalConnection != localConnection) {
            if (DBG) Log.d(TAG, "localConnection changed " + mHasLocalConnection + " -> " + localConnection);
            mHasLocalConnection = localConnection;
            return true;
        }
        return false;
    }

    private boolean setConnected(boolean value) {
        if (mConnected != value) {
            if (DBG) Log.d(TAG, "connected changed " + mConnected + " -> " + value);
            mConnected = value;
            return true;
        }
        return false;
    }

    private boolean setConnectedOrConnecting(boolean value) {
        if (mConnectedOrConnecting != value) {
            if (DBG) Log.d(TAG, "connectedOrConnecting changed " + mConnectedOrConnecting + " -> " + value);
            mConnectedOrConnecting = value;
            return true;
        }
        return false;
    }

    private boolean setAvailable(boolean value) {
        if (mAvailable != value) {
            if (DBG) Log.d(TAG, "available changed " + mConnectedOrConnecting + " -> " + value);
            mAvailable = value;
            return true;
        }
        return false;
    }

    private boolean setNetworkType(int type) {
        if (mNetworkType != type) {
            if (DBG) Log.d(TAG, "networkType changed " + mNetworkType + " -> " + type);
            mNetworkType = type;
            return true;
        }
        return false;
    }

    private boolean reset() {
        boolean change = false;
        change |= setNetworkType(-1);
        change |= setAvailable(false);
        change |= setConnected(false);
        change |= setConnectedOrConnecting(false);
        return change;
    }

    protected NetworkState(Context context) {
        mContext = context;
        ConnectivityManager conn = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = conn.getActiveNetworkInfo();
        updateFrom(networkInfo);
    }

}
