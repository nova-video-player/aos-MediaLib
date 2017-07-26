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

package com.archos.mediascraper.db;

import android.database.Cursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQuery;

/*
 * Factory used to instanciate ScraperCursor class.
 */
public class ScraperCursorFactory implements SQLiteDatabase.CursorFactory {
    public Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery,
            String editTable, SQLiteQuery query) {
        return new ScraperCursor(masterQuery, editTable, query);
    }

}