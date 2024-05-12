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

package com.archos.mediacenter.filecoreextension.upnp2;

import android.util.Log;

import org.fourthline.cling.model.meta.Device;

import java.util.HashMap;

/**
 * Created by vapillon on 29/05/15.
 */
public class BlockingDeviceRequest {

    private static final String TAG = "BlockingDeviceRequest";
    private static final boolean DBG = false;

    boolean semaphore = false;
    final int mDeviceKey;
    Device mDevice;

    public BlockingDeviceRequest(int deviceKey) {
        mDeviceKey = deviceKey;
    }

    /**
     * Blocking method
     * @return the found device, null if not found
     */
    public synchronized Device block() {
        while (!semaphore) try {
            if(DBG) Log.d(TAG, "Blocking for "+mDeviceKey+" thread="+Thread.currentThread().getName());
            this.wait();
        } catch (InterruptedException e) {
            if(DBG) Log.e(TAG, "block InterruptedException", e);
        }
        if(DBG) Log.d(TAG, "Released for " + mDeviceKey);
        return mDevice;
    }

    /**
     * Release the semaphore and returns true if the target device is in the given map
     * */
    public synchronized boolean checkIfDeviceIsFound(HashMap<Integer, Device> devicesMap) {
        mDevice = devicesMap.get(Integer.valueOf(mDeviceKey));
        final boolean found =  (mDevice!=null);
        if (found) {
            if(DBG) Log.d(TAG, "checkIfDeviceIsFound for "+mDeviceKey+" will unblock");
            semaphore = true;
            this.notify();
        }
        return found;
    }

    public synchronized void timeOut() {
        semaphore = true;
        this.notify();
    }
}
