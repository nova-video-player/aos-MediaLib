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

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.locks.ReentrantLock;

public class DbHolder {

    private final SQLiteOpenHelper mDbHelper;
    private final ReentrantLock mLock = new ReentrantLock();

    // singleton
    private volatile SQLiteDatabase mDb;

    public DbHolder(SQLiteOpenHelper openHelper) {
        mDbHelper = openHelper;
    }

    public SQLiteDatabase get() {
        // double checked works if using volatile
        if (mDb == null) {
            // not 100% correct in all cases but it's enough for logging
            if (mLock.isLocked() && !mLock.isHeldByCurrentThread()) {
                logUiThread();
            }

            mLock.lock();
            try {
                if (mDb == null) {
                    mDb = mDbHelper.getWritableDatabase();
                }
            } finally {
                mLock.unlock();
            }
        }
        return mDb;
    }

    private static void logUiThread() {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            Exception e = new Exception("CREATING DATABASE ON MAIN THREAD");
            e.fillInStackTrace();
            Log.w(ArchosMediaCommon.TAG_PREFIX + DbHolder.class.getSimpleName(), e);
        }
    }
}
