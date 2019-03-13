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

package com.archos.mediacenter.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Log;

import com.archos.filecorelibrary.FileUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public enum BlacklistedDbAdapter {

    VIDEO(BlacklistedDbAdapter.DATABASE_VIDEO_TABLE, BlacklistedDbAdapter.DATABASE_CREATE_VIDEO);

    private static final String TAG = "BlacklistedDbAdapter";
    protected final static boolean DBG = false;

    // To be incremented each time the architecture of the database is changed
    private static final int DATABASE_VERSION = 1;

    public static final String KEY_PATH = "path";
    public static final String KEY_ROWID = "_id";

    private static final String DATABASE_NAME = "blacklisteds_db";
    private static final String DATABASE_VIDEO_TABLE = "blacklisteds_table_video";

    private static final String DATABASE_CREATE_VIDEO =
        "create table blacklisteds_table_video (_id integer primary key autoincrement, " + KEY_PATH + " text not null);";

    private static final String[] BLACKLISTED_COLS = { KEY_ROWID, KEY_PATH };

    private DatabaseHelper mDbHelper;
    // The path is the only info the other classes need to know.
    // The database id is only needed locally for the database management
    private SQLiteDatabase mDb;
    private final String mDatabaseTable;

    public static class Blacklisted implements Serializable{
        private final String mUri;
        private Long mId;

        public String getUri() {
            return mUri;
        }
        public Long getID() {
            return mId;
        }
        public void setID(Long id) {
            mId = id;
        }
        public Blacklisted(String uri){
            mUri = uri;
            mId = Long.valueOf(-1);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Blacklisted blacklisted = (Blacklisted) o;

            return !(mUri != null ? !mUri.equals(blacklisted.mUri) : blacklisted.mUri != null);

        }

        @Override
        public int hashCode() {
            return mUri != null ? mUri.hashCode() : 0;
        }
    }
    BlacklistedDbAdapter(String databaseTable, String databaseCreate) {
        mDatabaseTable = databaseTable;
    }

    /*
     * Open the blacklisted database
     */
    private void open(Context ct) throws SQLException {
        if(mDbHelper==null&&ct!=null)
            mDbHelper = new DatabaseHelper(ct.getApplicationContext());
        if(mDbHelper!=null)
            mDb = mDbHelper.getWritableDatabase();
    }

    /*
     * Close the blacklisted database
     */
    public void close() {
        if (mDb != null) {
            mDb.close();
        }
        if (mDbHelper != null) {
            mDbHelper.close();
        }
    }

    public Cursor queryAllBlacklisteds(Context context) {

        return getAllBlacklisteds(context, null, null);
    }

    public boolean addBlacklisted(Context context, Blacklisted blacklisted) {
        SQLiteDatabase db = new DatabaseHelper(context).getWritableDatabase();
        ContentValues val = new ContentValues(1);
        val.put(KEY_PATH, blacklisted.getUri());
        long id = db.insert(mDatabaseTable, null, val);
        boolean success = (id != -1);
        blacklisted.setID(id);

        db.close();
        return success;
    }

    public boolean deleteBlacklisted(Context context, String uri) {

        // Delete the blacklisted stored in the provided row
        boolean ret;
        String [] where = {
                uri
        };
            open(context);
            ret = mDb.delete(mDatabaseTable, KEY_PATH + " = ?", where) > 0;
            close();

        return ret;
    }

    public Cursor getAllBlacklisteds(Context context, String where, String [] whereArgs) {
        try {

            open(context);
            Cursor cursor = mDb.query(mDatabaseTable,
                    BLACKLISTED_COLS,
                    where,
                    whereArgs,
                    null,
                    null,
                    null);

            return cursor;
        }
        catch (SQLiteException e) {
            // The table corresponding to this type does not exist yet
            Log.w(TAG, e);
            return null;
        }
    }
    private static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            // This method is only called once when the database is created for the first time
            db.execSQL(DATABASE_CREATE_VIDEO);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.d(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);
        }
    }

}