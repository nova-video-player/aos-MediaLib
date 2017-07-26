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
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite Custom function that deletes files, sadly the built-in
 * _DELETE_FILE function does not delete files that are not on primary external
 * storage (/mnt/storage/) so we have to use our own.
 */
public class DeleteFileCallback implements SQLiteDbProxy.CustomFunction {
    public static final String TAG = "DeleteFileCallback";
    public static final boolean DBG = false;

    public static final String SQLITE_FUNCTION_NAME = "_DELETE_FILE_J";
    public static final int SQLITE_NUM_ARGS = 1;

    //delete if not used : arg 0 file path, arg 1 column where it can be used arg 2 table where we can find it
    public static final String SQLITE_FUNCTION_NOT_USED_NAME = "_DELETE_FILE_IF_NOT_USED_J";
    public static final int SQLITE_NUM_NOT_USED_ARGS = 2;
    public static List<String> DO_NOT_DELETE = new ArrayList<>();
    private SQLiteDatabase mDb;

    public DeleteFileCallback() {
        // empty
    }

    public void callback(String[] args) {
        boolean success = false;
        if (args.length > 0) {
            if(args.length > 1){
                if (DBG) Log.d(TAG, SQLITE_FUNCTION_NOT_USED_NAME + "(" + args[0] + ", " + args[1] +")");

                    if (Integer.parseInt(args[1])>0) {
                        if (DBG)
                            Log.d(TAG, SQLITE_FUNCTION_NOT_USED_NAME + "(" + args[0] + ", " + args[1] + ") is still used : aborting");
                        return;

                    }
            }
            String file = args[0];
            if (file != null && !file.isEmpty() && file.charAt(0) == '/') {
                if(!DO_NOT_DELETE.contains(file)) {
                    File f = new File(args[0]);
                    success = f.delete();
                }
                else
                    if (DBG) Log.d(TAG, SQLITE_FUNCTION_NAME + " protected file");

            }
        }
        if (DBG) Log.d(TAG, SQLITE_FUNCTION_NAME + "(" + args[0] + ") = " + (success ? "OK" : "FAIL"));
    }

    public void addToDb (SQLiteDatabase db) {
        mDb = db;
        SQLiteDbProxy.installCustomFunction(db, SQLITE_FUNCTION_NAME, SQLITE_NUM_ARGS, this);
    }

    public void addToDbCheckNotUsed (SQLiteDatabase db) {
        mDb = db;
        SQLiteDbProxy.installCustomFunction(db, SQLITE_FUNCTION_NOT_USED_NAME, SQLITE_NUM_NOT_USED_ARGS, this);
    }
}
