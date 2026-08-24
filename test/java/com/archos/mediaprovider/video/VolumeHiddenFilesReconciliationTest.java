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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.archos.mediaprovider.DbHolder;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Arrays;

/**
 * Covers the three #1909 fixes on top of a real SQLite-backed files_import table (via
 * VideoOpenHelper), routed through a mocked ContentResolver that forwards query/update/delete
 * calls to that real database. This exercises the actual SQL (including the after_update_files_import
 * trigger propagation to the files table) rather than just verifying method calls.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class VolumeHiddenFilesReconciliationTest {

    private static final String DB_NAME = "volume-hidden-test.db";
    private static final String STORAGE = "/storage/ABCD-1234";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Application application;
    private DbHolder holder;
    private SQLiteDatabase database;
    private ContentResolver resolver;

    @Before
    public void setUp() {
        application = ApplicationProvider.getApplicationContext();
        application.deleteDatabase(DB_NAME);
        holder = new DbHolder(new VideoOpenHelper(application, DB_NAME,
                VideoOpenHelper.getDatabaseVersion()));
        database = holder.get();
        resolver = mock(ContentResolver.class);
        stubFilesImportForwarding();
    }

    @After
    public void tearDown() {
        if (holder != null) holder.close();
        if (application != null) application.deleteDatabase(DB_NAME);
    }

    @Test
    public void purgeRemovesOnlyRowsHiddenPastRetention() {
        long now = System.currentTimeMillis() / 1000;
        insertRow(1, STORAGE + "/visible.mkv", 0);
        insertRow(2, STORAGE + "/recently_hidden.mkv", now - 3600);
        insertRow(3, STORAGE + "/hidden_29_days.mkv", now - 29L * 24 * 3600);
        insertRow(4, STORAGE + "/hidden_31_days.mkv", now - 31L * 24 * 3600);

        VideoStoreImportImpl.purgeExpiredHiddenFiles(resolver);

        assertEquals("still-visible row must survive", 1L, rowCount(1));
        assertEquals("recently hidden row must survive", 1L, rowCount(2));
        assertEquals("row just under retention must survive", 1L, rowCount(3));
        assertEquals("row past retention must be purged", 0L, rowCount(4));
    }

    @Test
    public void purgeIsNoOpWhenNothingIsExpired() {
        insertRow(1, STORAGE + "/visible.mkv", 0);
        insertRow(2, STORAGE + "/recently_hidden.mkv", System.currentTimeMillis() / 1000 - 60);

        VideoStoreImportImpl.purgeExpiredHiddenFiles(resolver);

        assertEquals(1L, rowCount(1));
        assertEquals(1L, rowCount(2));
    }

    @Test
    public void unhideOnlyUnhidesRowsConfirmedPresentByMediaStore() {
        insertRow(10, STORAGE + "/present.mkv", 12345L);
        insertRow(11, STORAGE + "/still_missing.mkv", 12345L);

        VideoStoreImportImpl.unhideFilesFromVolumes(resolver, Arrays.asList(STORAGE), "10");

        assertEquals("MediaStore-confirmed row must be unhidden",
                0L, queryLong("SELECT volume_hidden FROM files_import WHERE _id=10"));
        assertEquals("unconfirmed row must stay hidden until verifyAndHideDeletedFiles runs",
                12345L, queryLong("SELECT volume_hidden FROM files_import WHERE _id=11"));
        // propagation trigger must have mirrored the unhide onto the files table too
        assertEquals(0L, queryLong("SELECT volume_hidden FROM files WHERE _id=10"));
    }

    @Test
    public void unhideIsNoOpWhenExistingFilesEmpty() {
        insertRow(10, STORAGE + "/present.mkv", 12345L);

        VideoStoreImportImpl.unhideFilesFromVolumes(resolver, Arrays.asList(STORAGE), "");

        verify(resolver, never()).update(eq(VideoStoreInternal.FILES_IMPORT),
                any(ContentValues.class), anyString(), any());
        assertEquals(12345L, queryLong("SELECT volume_hidden FROM files_import WHERE _id=10"));
    }

    @Test
    public void verifyBatchesWritesIntoAtMostTwoUpdatesRegardlessOfRowCount() throws Exception {
        File present1 = temporaryFolder.newFile("present1.mkv");
        File present2 = temporaryFolder.newFile("present2.mkv");
        File present3 = temporaryFolder.newFile("present3.mkv");
        String missing1 = new File(temporaryFolder.getRoot(), "missing1.mkv").getPath();
        String missing2 = new File(temporaryFolder.getRoot(), "missing2.mkv").getPath();

        insertRow(1, present1.getPath(), 999L);
        insertRow(2, present2.getPath(), 999L);
        insertRow(3, present3.getPath(), 999L);
        insertRow(4, missing1, 0);
        insertRow(5, missing2, 0);

        String where = "_data LIKE '" + temporaryFolder.getRoot().getPath() + "/%'";
        long timestamp = 555L;
        VideoStoreImportImpl.verifyAndHideDeletedFiles(resolver, where, timestamp, "test");

        assertEquals(0L, queryLong("SELECT volume_hidden FROM files_import WHERE _id=1"));
        assertEquals(0L, queryLong("SELECT volume_hidden FROM files_import WHERE _id=2"));
        assertEquals(0L, queryLong("SELECT volume_hidden FROM files_import WHERE _id=3"));
        assertEquals(timestamp, queryLong("SELECT volume_hidden FROM files_import WHERE _id=4"));
        assertEquals(timestamp, queryLong("SELECT volume_hidden FROM files_import WHERE _id=5"));

        // Regression guard for the pre-fix per-row update loop: at most one bulk update per
        // outcome (hide / unhide), never one call per row (would be 5 calls pre-fix).
        verify(resolver, atMost(2)).update(eq(VideoStoreInternal.FILES_IMPORT),
                any(ContentValues.class), anyString(), any());
    }

    @Test
    public void verifySkipsUpdatesWhenAllRowsAreConfirmedMissing() throws Exception {
        String missing1 = new File(temporaryFolder.getRoot(), "missing1.mkv").getPath();
        String missing2 = new File(temporaryFolder.getRoot(), "missing2.mkv").getPath();
        insertRow(1, missing1, 0);
        insertRow(2, missing2, 0);

        String where = "_data LIKE '" + temporaryFolder.getRoot().getPath() + "/%'";
        VideoStoreImportImpl.verifyAndHideDeletedFiles(resolver, where, 777L, "test");

        assertEquals(777L, queryLong("SELECT volume_hidden FROM files_import WHERE _id=1"));
        assertEquals(777L, queryLong("SELECT volume_hidden FROM files_import WHERE _id=2"));
        // Only the "hide" bucket should have fired; nothing to unhide.
        verify(resolver, atMost(1)).update(eq(VideoStoreInternal.FILES_IMPORT),
                any(ContentValues.class), anyString(), any());
    }

    private void insertRow(long id, String path, long volumeHidden) {
        database.execSQL("INSERT INTO files_import(_id,_data,_display_name,_size,"
                        + "date_modified,storage_id,volume_hidden) VALUES(?,?,?,?,?,?,?)",
                new Object[] { id, path, new File(path).getName(), 1L, 100L, 1234, volumeHidden });
    }

    private long rowCount(long id) {
        return queryLong("SELECT count(*) FROM files_import WHERE _id=" + id);
    }

    private long queryLong(String sql) {
        Cursor cursor = database.rawQuery(sql, null);
        try {
            if (!cursor.moveToFirst()) throw new AssertionError("No row for: " + sql);
            return cursor.getLong(0);
        } finally {
            cursor.close();
        }
    }

    private void stubFilesImportForwarding() {
        when(resolver.query(eq(VideoStoreInternal.FILES_IMPORT), any(String[].class),
                anyString(), isNull(), isNull())).thenAnswer(invocation ->
                database.query(VideoOpenHelper.FILES_IMPORT_TABLE_NAME,
                        invocation.getArgument(1), invocation.getArgument(2),
                        null, null, null, null));
        when(resolver.update(eq(VideoStoreInternal.FILES_IMPORT), any(ContentValues.class),
                anyString(), any())).thenAnswer(invocation ->
                database.update(VideoOpenHelper.FILES_IMPORT_TABLE_NAME,
                        invocation.getArgument(1), invocation.getArgument(2),
                        invocation.getArgument(3)));
        when(resolver.delete(eq(VideoStoreInternal.FILES_IMPORT), anyString(),
                any(String[].class))).thenAnswer(invocation ->
                database.delete(VideoOpenHelper.FILES_IMPORT_TABLE_NAME,
                        invocation.getArgument(1), invocation.getArgument(2)));
    }
}
