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

public enum ShortcutDbAdapter {

    VIDEO(ShortcutDbAdapter.DATABASE_VIDEO_TABLE, ShortcutDbAdapter.DATABASE_CREATE_VIDEO);

    private static final String TAG = "ShortcutDbAdapter";
    protected final static boolean DBG = false;

    // To be incremented each time the architecture of the database is changed
    private static final int DATABASE_VERSION = 4;

    public static final String KEY_PATH = "path";
    public static final String KEY_IPPATH = "ippath";
    public static final String KEY_FRIENDLY_URI= "friendly_url";

    public static final String KEY_NAME = "name";
    public static final String KEY_ROWID = "_id";

    private static final String DATABASE_NAME = "shortcuts_db";
    private static final String DATABASE_VIDEO_TABLE = "shortcuts_table_video";

    private static final String DATABASE_CREATE_VIDEO =
        "create table shortcuts_table_video (_id integer primary key autoincrement, " + KEY_PATH + " text not null, " +  KEY_NAME + " text not null, " + KEY_IPPATH + " text" + ", "+KEY_FRIENDLY_URI+" text);";


    private static final String[] SHORTCUT_COLS = { KEY_ROWID, KEY_PATH, KEY_IPPATH, KEY_NAME, KEY_FRIENDLY_URI };

    private DatabaseHelper mDbHelper;
    // The path is the only info the other classes need to know.
    // The database id is only needed locally for the database management
    private SQLiteDatabase mDb;
    private final String mDatabaseTable;

    public boolean isHimselfOrAncestorShortcut(Context context, String path) {
        open(context);
        Uri uri = Uri.parse(path);
        List<String> uriToCheck = new ArrayList<>();// every ancestors
        String where = KEY_PATH + " in (";
        while (uri != null && (uri.getHost() != null && !uri.getHost().isEmpty()
                || uri.getPath() != null && !uri.getPath().isEmpty())) { // while we have something like scheme://host/path or scheme://host or scheme://path
            String uriString = uri.toString();
            uriToCheck.add(uriString);
            if(uriString.endsWith("/")) //also removing last "/"
                uriToCheck.add(uriString.substring(0, uriString.length() - 1));
            uri = FileUtils.getParentUrl(uri);

        }
        if (!uriToCheck.isEmpty()) { //building where string : " path in {?, ?, ?}
            boolean isFirst = true;
            for (String stringUri : uriToCheck) {
                if (!isFirst)
                    where += ", ";
                where += "?";
                isFirst = false;

            }
            where += ")";
            Cursor c = mDb.query(mDatabaseTable,
                    SHORTCUT_COLS,
                    where,
                    uriToCheck.toArray(new String[0]),
                    null,
                    null,
                    null);
            c.moveToFirst();
            boolean ret = c.getCount()>0;
            c.close();
            close();
            return ret;
        }
        close();
        return false;
    }


    public static class Shortcut implements Serializable{
        private final String mName;
        private final String mUri;
        private final String mFriendlyUri;
        private Long mId;

        public String getUri() {
            return mUri;
        }
        public String getName() {
            return mName;
        }
        public Long getID() {
            return mId;
        }
        public void setID(Long id) {
            mId = id;
        }
        public String getFriendlyUri() {
            return mFriendlyUri;
        }
        public Shortcut(String name, String uri, String friendlyUri){
            mName = name;
            mUri = uri;
            mId = Long.valueOf(-1);
            if(friendlyUri!=null&&!friendlyUri.isEmpty())
                mFriendlyUri = friendlyUri;
            else
                mFriendlyUri = mUri;
        }
        public Shortcut(String name, String uri){
            this(name, uri, uri);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Shortcut shortcut = (Shortcut) o;

            return !(mUri != null ? !mUri.equals(shortcut.mUri) : shortcut.mUri != null);

        }

        @Override
        public int hashCode() {
            return mUri != null ? mUri.hashCode() : 0;
        }
    }
    ShortcutDbAdapter(String databaseTable, String databaseCreate) {
        mDatabaseTable = databaseTable;
    }

    /*
     * Open the shortcut database
     */
    private void open(Context ct) throws SQLException {
        if(mDbHelper==null&&ct!=null)
            mDbHelper = new DatabaseHelper(ct.getApplicationContext());
        if(mDbHelper!=null)
            mDb = mDbHelper.getWritableDatabase();
    }

    /*
     * Close the shortcut database
     */
    public void close() {
        if (mDb != null) {
            mDb.close();
        }
        if (mDbHelper != null) {
            mDbHelper.close();
        }
    }

    /**
     * NEW 2015: better have direct access to the cursor
     * @return
     */
    public Cursor queryAllShortcuts(Context context) {

        return getAllShortcuts(context, null, null);
    }

    /**
     * NEW 2015: better have direct access to the DB
     * This new version does not need the ipPath parameter, it just use the regular path + a name
     * @return true if it succeeded
     */
    public boolean addShortcut(Context context, Shortcut shortcut) {
        SQLiteDatabase db = new DatabaseHelper(context).getWritableDatabase();
        ContentValues val = new ContentValues(2);
        val.put(KEY_PATH, shortcut.getUri());
        val.put(KEY_IPPATH, shortcut.getUri()); // no need for specific IpPath, just using the regular one...
        val.put(KEY_NAME, shortcut.getName());
        val.put(KEY_FRIENDLY_URI, shortcut.getFriendlyUri());
        long id = db.insert(mDatabaseTable, null, val);
        boolean success = (id != -1);
        shortcut.setID(id);

        db.close();
        return success;
    }

    /**
     * NEW 2015: better have direct access to the DB
     * @return true if it succeeded
     */
    public boolean deleteShortcut(Context context, long shortcutId) {
        open(context);
        SQLiteDatabase db = new DatabaseHelper(context).getWritableDatabase();
        boolean success = (db.delete(mDatabaseTable, KEY_ROWID + " = ?", new String[]{Long.toString(shortcutId)}) > 0);

        db.close();
        return success;
    }

    /**
     * NEW 2015: better have direct access to the DB
     * @return the (first) shortcut ID if this path is in the shortcut database
     */
    public long isShortcut(Context context, String path) {
        open(context);
        Cursor c = mDb.query(mDatabaseTable,
                SHORTCUT_COLS,
                KEY_PATH + " = ?",
                new String[]{path},
                null,
                null,
                null);
        c.moveToFirst();
        long id=-1;
        if (c.getCount()>0) {
            id = c.getLong(c.getColumnIndexOrThrow(KEY_ROWID));
        }
        c.close();
        close();
        return id;
    }




    public boolean deleteShortcut(Context context, String uri) {

        // Delete the shortcut stored in the provided row
        boolean ret;
        String [] where = {
                uri, uri
        };
            open(context);
            ret = mDb.delete(mDatabaseTable, KEY_PATH + " = ? OR "+KEY_IPPATH +" = ?", where) > 0;
            close();

        return ret;
    }


    public Cursor getAllShortcuts(Context context, String where, String [] whereArgs) {
        try {

            open(context);
            Cursor cursor = mDb.query(mDatabaseTable,
                    SHORTCUT_COLS,
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
    public List<Shortcut> cursorToShortcutList(Cursor cursor){
        List<Shortcut>shortcuts = new ArrayList<>();
        if(cursor.getCount()>0){
            cursor.moveToFirst();
            do{
                shortcuts.add(cursorToShortcut(cursor));
            }while (cursor.moveToNext());
        }
        return shortcuts;
    }
    private Shortcut cursorToShortcut(Cursor cursor){
        int rowIdColumnIndex = cursor.getColumnIndex(KEY_ROWID);
        int pathColumnIndex = cursor.getColumnIndex(KEY_PATH);
        int nameColumnIndex = cursor.getColumnIndex(KEY_NAME);
        int friendlyUriColumnIndex = cursor.getColumnIndex(KEY_FRIENDLY_URI);
        String path = cursor.getString(pathColumnIndex);
        String name = cursor.getString(nameColumnIndex);
        String friendlyuri = cursor.getString(friendlyUriColumnIndex);
        Shortcut shortcut = new Shortcut(name, path, friendlyuri);
        long rowId = cursor.getLong(rowIdColumnIndex);
        shortcut.setID(rowId);
        return shortcut;
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
            if (oldVersion < 2) {
                    db.execSQL("ALTER TABLE "+DATABASE_VIDEO_TABLE+" ADD COLUMN " + KEY_IPPATH + " TEXT");
            }
            if (oldVersion < 3) {

                db.execSQL("ALTER TABLE "+DATABASE_VIDEO_TABLE+" ADD COLUMN " + KEY_NAME + " TEXT");
            }
            if (oldVersion < 4) {

                db.execSQL("ALTER TABLE "+DATABASE_VIDEO_TABLE+" ADD COLUMN " + KEY_FRIENDLY_URI + " TEXT");
            }
        }
    }


}
