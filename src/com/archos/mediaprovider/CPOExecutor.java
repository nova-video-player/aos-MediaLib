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

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.OperationApplicationException;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;

/** ContentProviderOperation bulk executor */
public class CPOExecutor {
    private static final String TAG = ArchosMediaCommon.TAG_PREFIX + CPOExecutor.class.getSimpleName();
    private static final boolean DBG = false;

    private final ContentResolver mCr;
    private final ArrayList<ContentProviderOperation> mOpList;
    private final String mAuthority;
    private final int mLimit;
    private int mExecuted;

    public CPOExecutor(String authority, ContentResolver cr, int limit) {
        mAuthority = authority;
        mCr = cr;
        mOpList = new ArrayList<ContentProviderOperation>(limit);
        mLimit = limit;
    }

    public void add(ContentProviderOperation operation) {
        if (mOpList.size() >= mLimit) {
            if (DBG) Log.d(TAG, "execute() at " + mOpList.size() + "/" + mLimit);
            execute();
        }
        mOpList.add(operation);
    }

    public int execute() {
        if (mOpList.size() <= 0)
            return mExecuted;

        try {
            mCr.applyBatch(mAuthority, mOpList);
            mExecuted += mOpList.size();
        } catch (RemoteException e) {
            Log.e(TAG, e.toString(), e);
        } catch (OperationApplicationException e) {
            Log.e(TAG, e.toString(), e);
        }
        // got to clear the list anyways.
        mOpList.clear();
        return mExecuted;
    }

    public int getExecutionCount() {
        return mExecuted;
    }
}