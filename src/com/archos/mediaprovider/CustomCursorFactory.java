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

import android.database.Cursor;
import android.database.CursorWrapper;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteQuery;
import android.util.Log;
/**
 * Our providers uses this to create custom Cursors.
 *
 * So far it's just a workaround for
 * http://code.google.com/p/android/issues/detail?id=32472
 *
 * The move methods can throw IllegalStateException if Cursor needs to
 * move the CursorWindow to a position that no longer exists in the database.
 * Updates of the window use the current database state while the implementation
 * expects the state of the initial query.
 */
public class CustomCursorFactory implements CursorFactory {
    protected static final String TAG = ArchosMediaCommon.TAG_PREFIX + CustomCursorFactory.class.getSimpleName();

    public static class CustomCursor extends CursorWrapper {

        public static CustomCursor wrap(Cursor cursor) {
            return cursor != null ? new CustomCursor(cursor) : null;
        }

        public CustomCursor(Cursor cursor) {
            super(cursor);
        }

        @Override
        public boolean move(int offset) {
            try {
                return super.move(offset);
            } catch (IllegalStateException e) {
                Log.w(TAG, "Case of http://b.android.com/32472 - crash successfully prevented.", e);
                return false;
            }
        }

        @Override
        public boolean moveToFirst() {
            try {
                return super.moveToFirst();
            } catch (IllegalStateException e) {
                Log.w(TAG, "Case of http://b.android.com/32472 - crash successfully prevented.", e);
                return false;
            }
        }

        @Override
        public boolean moveToLast() {
            try {
                return super.moveToLast();
            } catch (IllegalStateException e) {
                Log.w(TAG, "Case of http://b.android.com/32472 - crash successfully prevented.", e);
                return false;
            }
        }

        @Override
        public boolean moveToNext() {
            try {
                return super.moveToNext();
            } catch (IllegalStateException e) {
                Log.w(TAG, "Case of http://b.android.com/32472 - crash successfully prevented.", e);
                return false;
            }
        }

        @Override
        public boolean moveToPrevious() {
            try {
                return super.moveToPrevious();
            } catch (IllegalStateException e) {
                Log.w(TAG, "Case of http://b.android.com/32472 - crash successfully prevented.", e);
                return false;
            }
        }

        @Override
        public boolean moveToPosition(int position) {
            try {
                return super.moveToPosition(position);
            } catch (IllegalStateException e) {
                Log.w(TAG, "Case of http://b.android.com/32472 - crash successfully prevented.", e);
                return false;
            }
        }
    }

    public CustomCursorFactory() { /* empty */ }

    @Override
    public Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery,
            String editTable, SQLiteQuery query) {
        Cursor cursor = new SQLiteCursor(masterQuery, editTable, query);
        return new CustomCursor(cursor);
    }
}
