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

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;

/** ContentProviderOperation bulk executor */
public class BulkInserter {
    private static final String TAG = ArchosMediaCommon.TAG_PREFIX + BulkInserter.class.getSimpleName();
    private static final boolean DBG = false;

    private final ContentResolver mCr;
    private final ArrayList<ContentValues> mCVList;
    private final Uri mUri;
    private final int mLimit;
    private int mInsertCount;

    public BulkInserter(Uri uri, ContentResolver cr, int limit) {
        mUri = uri;
        mCr = cr;
        mCVList = new ArrayList<ContentValues>(limit);
        mLimit = limit;
    }

    public void add(ContentValues operation) {
        if (mCVList.size() >= mLimit) {
            if (DBG) Log.d(TAG, "execute() at " + mCVList.size() + "/" + mLimit);
            execute();
        }
        mCVList.add(operation);
    }

    public int execute() {
        if (mCVList.size() <= 0)
            return mInsertCount;

        mInsertCount += mCr.bulkInsert(mUri, convert(mCVList));
        // got to clear the list.
        mCVList.clear();

        return mInsertCount;
    }

    public int getInsertCount() {
        return mInsertCount;
    }

    private static ContentValues[] convert(ArrayList<ContentValues> list) {
        ContentValues[] ret = new ContentValues[list.size()];
        return list.toArray(ret);
    }

}