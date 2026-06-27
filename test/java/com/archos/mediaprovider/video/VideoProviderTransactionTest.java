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

package com.archos.mediaprovider.video;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;

import com.archos.mediaprovider.DbHolder;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.ArrayList;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class VideoProviderTransactionTest {

    private static final Uri TEST_URI = Uri.parse("content://test/video");

    private SQLiteDatabase database;
    private DbHolder holder;
    private VobHandler vobHandler;
    private ContentResolver contentResolver;
    private VideoProvider provider;

    @Before
    public void setUp() throws Exception {
        database = mock(SQLiteDatabase.class);
        holder = mock(DbHolder.class);
        when(holder.get()).thenReturn(database);
        vobHandler = mock(VobHandler.class);
        contentResolver = mock(ContentResolver.class);
        provider = new VideoProvider() {
            @Override
            public Uri insert(Uri uri, ContentValues values) {
                return TEST_URI;
            }
        };
        setField(provider, "mDbHolder", holder);
        setField(provider, "mVobHandler", vobHandler);
        setField(provider, "mCr", contentResolver);
    }

    @Test
    public void applyBatchPreservesOperationFailureWhenRollbackAlsoFails() throws Exception {
        SQLiteException operationFailure = new SQLiteException("operation failed");
        SQLiteException rollbackFailure = new SQLiteException("cannot rollback - no transaction is active");
        provider = new VideoProvider() {
            @Override
            public Uri insert(Uri uri, ContentValues values) {
                throw operationFailure;
            }
        };
        copyDependenciesTo(provider);
        doThrow(rollbackFailure).when(database).endTransaction();

        try {
            provider.applyBatch(singleInsert());
            fail("Expected the operation failure");
        } catch (SQLiteException actual) {
            assertSame(operationFailure, actual);
        }

        verify(vobHandler).onBeginTransaction();
        verify(vobHandler).onEndTransaction();
        verifyNoInteractions(contentResolver);
    }

    @Test
    public void applyBatchPropagatesCommitFailureWithoutNotifying() throws Exception {
        SQLiteException commitFailure = new SQLiteException("cannot commit");
        doThrow(commitFailure).when(database).endTransaction();

        try {
            provider.applyBatch(singleInsert());
            fail("Expected the commit failure");
        } catch (SQLiteException actual) {
            assertSame(commitFailure, actual);
        }

        verify(database).setTransactionSuccessful();
        verify(vobHandler).onBeginTransaction();
        verify(vobHandler).onEndTransaction();
        verifyNoInteractions(contentResolver);
    }

    private ArrayList<ContentProviderOperation> singleInsert() {
        ArrayList<ContentProviderOperation> operations = new ArrayList<>();
        operations.add(ContentProviderOperation.newInsert(TEST_URI)
                .withValues(new ContentValues())
                .build());
        return operations;
    }

    private void copyDependenciesTo(VideoProvider target) throws Exception {
        setField(target, "mDbHolder", holder);
        setField(target, "mVobHandler", vobHandler);
        setField(target, "mCr", contentResolver);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = VideoProvider.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
