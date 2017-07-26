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

import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteQuery;
import android.os.Bundle;

/*
 * Subclass of android's Cursor, overriding non implemented Cursor's extra
 * mechanism. It will allow us to pass infos from the Provider to the client,
 * apart from the data contained in the cursor itself.
 */
public class ScraperCursor extends SQLiteCursor {
    private Bundle mExtras = Bundle.EMPTY;

    public ScraperCursor(SQLiteCursorDriver driver,
            String editTable, SQLiteQuery query) {
        super(driver, editTable, query);
    }

    @Override
    public Bundle getExtras() { return mExtras; }

    @Override
    public Bundle respond(Bundle extras) {
        if(extras != null) {
            mExtras = extras;
        }
        return mExtras;
    }
}
