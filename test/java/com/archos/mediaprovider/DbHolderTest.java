// Copyright 2026 Courville Software
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

import static org.junit.Assert.*;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class DbHolderTest {

    private static class TestHelper extends SQLiteOpenHelper {
        private final AtomicInteger getDatabaseCallCount = new AtomicInteger(0);
        private boolean throwOnce = false;

        public TestHelper(Context context, String name) {
            super(context, name, null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE test (id INTEGER PRIMARY KEY, value TEXT)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

        @Override
        public SQLiteDatabase getWritableDatabase() {
            getDatabaseCallCount.incrementAndGet();
            if (throwOnce) {
                throwOnce = false;
                throw new IllegalStateException("attempt to re-open an already-closed object: SQLiteDatabase");
            }
            return super.getWritableDatabase();
        }
    }

    @Test
    public void testGetAndCaching() {
        Context context = ApplicationProvider.getApplicationContext();
        String dbName = "test_holder.db";
        context.deleteDatabase(dbName);

        TestHelper helper = new TestHelper(context, dbName);
        DbHolder holder = new DbHolder(helper);

        // First call should open
        SQLiteDatabase db1 = holder.get();
        assertNotNull(db1);
        assertTrue(db1.isOpen());
        assertEquals(1, helper.getDatabaseCallCount.get());

        // Second call should return cached instance
        SQLiteDatabase db2 = holder.get();
        assertSame(db1, db2);
        assertEquals(1, helper.getDatabaseCallCount.get());
    }

    @Test
    public void testRecoverWhenClosedUnderneath() {
        Context context = ApplicationProvider.getApplicationContext();
        String dbName = "test_recover.db";
        context.deleteDatabase(dbName);

        TestHelper helper = new TestHelper(context, dbName);
        DbHolder holder = new DbHolder(helper);

        SQLiteDatabase db1 = holder.get();
        assertTrue(db1.isOpen());

        // Close the database connection directly (simulating it being closed underneath)
        db1.close();
        assertFalse(db1.isOpen());

        // get() should detect it is closed and reopen a new connection
        SQLiteDatabase db2 = holder.get();
        assertNotNull(db2);
        assertTrue(db2.isOpen());
        assertNotSame(db1, db2);
        assertEquals(2, helper.getDatabaseCallCount.get());
    }

    @Test
    public void testRecoverFromIllegalStateException() {
        Context context = ApplicationProvider.getApplicationContext();
        String dbName = "test_exception.db";
        context.deleteDatabase(dbName);

        TestHelper helper = new TestHelper(context, dbName);
        DbHolder holder = new DbHolder(helper);

        SQLiteDatabase db1 = holder.get();
        assertTrue(db1.isOpen());

        // Direct close of connection
        db1.close();

        // Configure helper to throw IllegalStateException on the next open attempt
        helper.throwOnce = true;

        // get() should catch the exception, close the helper, and successfully open a new connection
        SQLiteDatabase db2 = holder.get();
        assertNotNull(db2);
        assertTrue(db2.isOpen());
        assertNotSame(db1, db2);
        // Call count should be 3: 1 (first get), 2 (first attempt in second get, throws), 3 (retry after close)
        assertEquals(3, helper.getDatabaseCallCount.get());
    }

    @Test
    public void testExplicitClose() {
        Context context = ApplicationProvider.getApplicationContext();
        String dbName = "test_close.db";
        context.deleteDatabase(dbName);

        TestHelper helper = new TestHelper(context, dbName);
        DbHolder holder = new DbHolder(helper);

        SQLiteDatabase db1 = holder.get();
        assertTrue(db1.isOpen());

        // Explicit close
        holder.close();
        assertFalse(db1.isOpen());

        // Next get should open a new connection
        SQLiteDatabase db2 = holder.get();
        assertNotNull(db2);
        assertTrue(db2.isOpen());
        assertNotSame(db1, db2);
        assertEquals(2, helper.getDatabaseCallCount.get());
    }
}
