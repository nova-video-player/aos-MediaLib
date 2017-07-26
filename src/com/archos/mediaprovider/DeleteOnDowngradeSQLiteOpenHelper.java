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
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.File;

public abstract class DeleteOnDowngradeSQLiteOpenHelper extends SQLiteOpenHelper {
    private static final String TAG =  ArchosMediaCommon.TAG_PREFIX + "DeleteOnDowngradeSQLiteOpenHelper";
    private static final boolean LOCAL_DBG = false;
    private static final boolean DBG = ArchosMediaCommon.PACKAGE_DBG & LOCAL_DBG;

    private final File mDatabaseFile;

    public DeleteOnDowngradeSQLiteOpenHelper(Context context, String name, CursorFactory factory, int version,
            DatabaseErrorHandler errorHandler) {
        super(context, name, factory, version, errorHandler);
        mDatabaseFile = context.getDatabasePath(name);
    }

    public DeleteOnDowngradeSQLiteOpenHelper(Context context, String name, CursorFactory factory, int version) {
        super(context, name, factory, version);
        mDatabaseFile = context.getDatabasePath(name);
    }

    @Override
    public synchronized SQLiteDatabase getWritableDatabase() {
        try {
            return super.getWritableDatabase();
        } catch (SQLiteDbDowngradeFailedException e) {
            // we need to downgrade now.
            Log.w(TAG, "Database downgrade not supported. Deleting database.");
        }

        // try to delete the file
        if (mDatabaseFile.delete()) {
            // optional callback that could be overwritten to
            // do additional cleanup.
            onDatabaseDeleted(mDatabaseFile);
        }

        // now return a freshly created database
        // or throw the error if it still does not work.
        return super.getWritableDatabase();
    }

    @Override
    public final void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // not calling super here, that would throw an exception we don't want
        // throw a custom Exception instead
        throw new SQLiteDbDowngradeFailedException("Can't downgrade database from version " +
                oldVersion + " to " + newVersion);
    }

    public void onDatabaseDeleted(File database) {
        // noop - overwrite it if you want to be notified about deletion.
    }

    public void deleteDatabase() {
        throw new SQLiteDbDowngradeFailedException();
    }

    static class SQLiteDbDowngradeFailedException extends SQLiteException {
        public SQLiteDbDowngradeFailedException() {}

        public SQLiteDbDowngradeFailedException(String error) {
            super(error);
        }
    }

}
