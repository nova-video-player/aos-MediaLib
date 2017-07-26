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

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.Locale;

public class SQLiteUtils {

    public static void dropTable(SQLiteDatabase db, String name) {
        db.execSQL("DROP TABLE IF EXISTS " + name);
    }
    public static void dropView(SQLiteDatabase db, String name) {
        db.execSQL("DROP VIEW IF EXISTS " + name);
    }
    public static void dropTrigger(SQLiteDatabase db, String name) {
        db.execSQL("DROP TRIGGER IF EXISTS " + name);
    }
    public static void dropIndex(SQLiteDatabase db, String name) {
        db.execSQL("DROP INDEX IF EXISTS " + name);
    }

    /** utility that renames old table, creates a new version and inserts data back */
    public static void alterTable(SQLiteDatabase db, String table, String recreateStatement) {
        String tmpTable = table + "__tmp";
        db.beginTransaction();
        try {
            db.execSQL("ALTER TABLE " + table + " RENAME TO " + tmpTable);
            db.execSQL(recreateStatement);
            db.execSQL("INSERT INTO " + table + " SELECT * FROM " + tmpTable);
            db.execSQL("DROP TABLE " + tmpTable);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static final String[] ID_DATA_PROJECTION = { "_id", "_data" };
    private static final String WHERE_ID = "_id=?";
    private static final String WHERE_SMB = "_data LIKE 'smb://%@%'";
    public static void removeCredentials(SQLiteDatabase db, String table, boolean updateBuckets) {
        Cursor c = db.query(table, ID_DATA_PROJECTION, WHERE_SMB, null, null, null, null);
        if (c != null) {
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String data = c.getString(1);
                int atSign = (data != null && data.startsWith("smb://")) ? data.indexOf('@') : -1;
                if (atSign > 0) {
                    String cleanData = "smb://" + data.substring(atSign + 1);
                    ContentValues cv = new ContentValues(2);
                    cv.put("_data", cleanData);
                    if (updateBuckets) {
                        int bucketId = cleanData.toLowerCase(Locale.ROOT).hashCode();
                        cv.put("bucket_id", bucketId);
                    }
                    db.update(table, cv, WHERE_ID, new String[] { String.valueOf(id) });
                }
            }
            c.close();
        }
    }

    private SQLiteUtils() { /* no instance pls */ }
}
