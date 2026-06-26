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
        SQLiteDatabase db = mDb;
        // double checked works if using volatile
        // also reopen when the cached connection has been closed underneath us
        // (e.g. media library backup/restore replacing media.db or low memory recycling)
        if (db == null || !db.isOpen()) {
            // not 100% correct in all cases but it's enough for logging
            if (mLock.isLocked() && !mLock.isHeldByCurrentThread()) {
                logUiThread();
            }

            mLock.lock();
            try {
                db = mDb;
                if (db == null || !db.isOpen()) {
                    db = openDatabase();
                    mDb = db;
                }
            } finally {
                mLock.unlock();
            }
        }
        return db;
    }

    public void close() {
        mLock.lock();
        try {
            if (mDb != null) {
                if (mDb.isOpen()) {
                    mDb.close();
                }
                mDb = null;
            }
            mDbHelper.close();
        } finally {
            mLock.unlock();
        }
    }

    /**
     * Acquire exclusive access to the database for a destructive operation (e.g.
     * media library backup/restore that deletes and replaces the underlying file).
     * Closes the cached connection and the helper so that any concurrent get()
     * blocks on the lock instead of opening a connection on a file that is being
     * deleted or rewritten. Must be paired with {@link #unlockExclusive()} in a
     * finally block. After unlock, the next get() reopens a fresh, consistent
     * connection.
     */
    public void lockExclusive() {
        mLock.lock();
        if (mDb != null) {
            if (mDb.isOpen()) {
                mDb.close();
            }
            mDb = null;
        }
        mDbHelper.close();
    }

    public void unlockExclusive() {
        mLock.unlock();
    }

    private SQLiteDatabase openDatabase() {
        try {
            return mDbHelper.getWritableDatabase();
        } catch (IllegalStateException e) {
            // "attempt to re-open an already-closed object": the helper's cached
            // connection was closed concurrently. Reset the helper and reopen a
            // fresh connection.
            Log.w(ArchosMediaCommon.TAG_PREFIX + DbHolder.class.getSimpleName(),
                    "get: database was closed, reopening", e);
            mDbHelper.close();
            return mDbHelper.getWritableDatabase();
        }
    }

    private static void logUiThread() {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            Exception e = new Exception("CREATING DATABASE ON MAIN THREAD");
            e.fillInStackTrace();
            Log.w(ArchosMediaCommon.TAG_PREFIX + DbHolder.class.getSimpleName(), e);
        }
    }
}
