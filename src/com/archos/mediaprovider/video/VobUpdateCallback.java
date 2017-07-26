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

package com.archos.mediaprovider.video;

import android.database.sqlite.SQLiteDatabase;

import com.archos.mediaprovider.SQLiteDbProxy;

/**
 * SQLite Custom function that gets notified about vob file inserts
 */
public class VobUpdateCallback implements SQLiteDbProxy.CustomFunction {

    public static final String SQLITE_FUNCTION_NAME = "_VOB_INSERT";
    public static final int SQLITE_NUM_ARGS = 1;

    private final VobHandler mVobHandler;

    public VobUpdateCallback(VobHandler vobHandler) {
        mVobHandler = vobHandler;
    }

    @Override
    public void callback(String[] args) {
        if (args.length > 0) {
            mVobHandler.handleVob(args[0]);
        }
    }

    public void addToDb (SQLiteDatabase db) {
        SQLiteDbProxy.installCustomFunction(db, SQLITE_FUNCTION_NAME, SQLITE_NUM_ARGS, this);
    }
}
