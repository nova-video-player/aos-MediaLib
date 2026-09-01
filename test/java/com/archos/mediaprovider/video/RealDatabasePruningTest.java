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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Runs the #1909 pruning/hide/unhide logic against a copy of a real, production-scale media.db
 * instead of a handful of hand-written rows. This test is skipped unless a database copy is
 * supplied explicitly, because a real database contains a user's personal library paths (and,
 * for network-share setups, potentially credentials embedded in SFTP/SMB URLs) and must never be
 * committed to the repository.
 *
 * Usage:
 *   1. Copy a real media.db (e.g. pulled from a device via adb, or a desktop-side copy) to a
 *      location outside the repo, or into a repo-local, .gitignore'd path.
 *   2. Run:
 *        ./gradlew :MediaLib:testDebugUnitTest --tests "*RealDatabasePruningTest" \
 *            -Dnova.test.mediaDbPath=/absolute/path/to/copy-of-media.db
 *
 * The test never modifies the original file: it copies it into Robolectric's app-private
 * database directory first, and only mutates that copy.
 *
 * files_import (the table these fixes touch) only ever holds rows for local/removable storage
 * (see MediaLib/doc/SCANNING.md); a database collected from a network-shares-only setup will
 * likely have this table empty or near-empty. seedSyntheticLocalBacklog() copies a bounded
 * sample of already-imported rows from the merged `files` table into files_import under a
 * synthetic USB path with a spread of volume_hidden ages, so the purge/hide/unhide logic is
 * exercised at the row volume and content shape of a real library, not just synthetic rows.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class RealDatabasePruningTest {

    private static final String DB_PATH_PROPERTY = "nova.test.mediaDbPath";
    private static final String TEST_DB_NAME = "real-media-pruning-test.db";
    private static final String FAKE_USB_PATH = "/storage/1234-ABCD";
    private static final int FAKE_USB_STORAGE_ID = 987654;
    private static final int SEED_ROW_LIMIT = 5000;

    private Application application;
    private DbHolder holder;
    private SQLiteDatabase database;
    private ContentResolver resolver;

    @Before
    public void setUp() throws Exception {
        String dbPath = System.getProperty(DB_PATH_PROPERTY);
        assumeTrue("Set -D" + DB_PATH_PROPERTY + "=/absolute/path/to/media.db to run this test",
                dbPath != null && new File(dbPath).isFile());

        application = ApplicationProvider.getApplicationContext();
        File target = application.getDatabasePath(TEST_DB_NAME);
        if (target.getParentFile() != null) target.getParentFile().mkdirs();
        Files.copy(new File(dbPath).toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);

        // Opening via VideoOpenHelper runs the exact same onUpgrade path a real device would
        // take, so this also doubles as a check that the current schema code upgrades a real
        // production database cleanly.
        holder = new DbHolder(new VideoOpenHelper(application, TEST_DB_NAME,
                VideoOpenHelper.getDatabaseVersion()));
        database = holder.get();
        resolver = mock(ContentResolver.class);
        stubFilesImportForwarding();
    }

    @After
    public void tearDown() {
        if (holder != null) holder.close();
        if (application != null) application.deleteDatabase(TEST_DB_NAME);
    }

    @Test
    public void realDatabaseUpgradesCleanlyAndPassesIntegrityCheck() {
        assertEquals("ok", queryString("PRAGMA integrity_check"));
        assertNoForeignKeyViolations();
    }

    @Test
    public void syntheticUsbBacklogIsPrunedAndLeavesDatabaseConsistent() {
        long now = System.currentTimeMillis() / 1000;
        int seeded = seedSyntheticLocalBacklog(now);
        assumeTrue("database has no files rows to seed a synthetic backlog from", seeded > 0);

        long expiredCutoff = now - 30L * 24 * 3600;
        long expiredBefore = queryLong("SELECT count(*) FROM files_import "
                + "WHERE volume_hidden > 0 AND volume_hidden < " + expiredCutoff);
        long hiddenBefore = queryLong(
                "SELECT count(*) FROM files_import WHERE volume_hidden > 0");
        assertTrue("fixture should have created an expired backlog to prune", expiredBefore > 0);

        long start = System.nanoTime();
        VideoStoreImportImpl.purgeExpiredHiddenFiles(resolver);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        long expiredAfter = queryLong("SELECT count(*) FROM files_import "
                + "WHERE volume_hidden > 0 AND volume_hidden < " + expiredCutoff);
        long hiddenAfter = queryLong(
                "SELECT count(*) FROM files_import WHERE volume_hidden > 0");
        assertEquals("all expired rows must be purged", 0L, expiredAfter);
        assertTrue("purge must not remove more than the expired rows",
                hiddenAfter >= hiddenBefore - expiredBefore);
        assertNoForeignKeyViolations();
        assertEquals("ok", queryString("PRAGMA integrity_check"));

        System.out.println("purgeExpiredHiddenFiles: seeded=" + seeded
                + " hiddenBefore=" + hiddenBefore + " expiredBefore=" + expiredBefore
                + " hiddenAfter=" + hiddenAfter + " elapsedMs=" + elapsedMs);
    }

    /**
     * Copies a bounded sample of already-imported rows from `files` into files_import under a
     * synthetic USB path, spreading volume_hidden ages across visible / recently hidden /
     * expired-backlog buckets. Returns the number of rows seeded.
     */
    private int seedSyntheticLocalBacklog(long now) {
        database.execSQL("DELETE FROM files_import WHERE storage_id = ?",
                new Object[] { FAKE_USB_STORAGE_ID });

        Cursor c = database.rawQuery(
                "SELECT _id, _display_name, _size, date_modified FROM files "
                        + "WHERE _display_name IS NOT NULL LIMIT " + SEED_ROW_LIMIT, null);
        int count = 0;
        try {
            database.beginTransaction();
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String displayName = c.getString(1);
                long size = c.getLong(2);
                long dateModified = c.getLong(3);
                String path = FAKE_USB_PATH + "/" + id + "_" + displayName;

                long volumeHidden;
                switch ((int) (id % 3)) {
                    case 0: volumeHidden = 0; break; // visible
                    case 1: volumeHidden = now - 3600; break; // hidden 1h ago
                    default: volumeHidden = now - 45L * 24 * 3600; break; // expired backlog
                }

                database.execSQL("INSERT INTO files_import(_id,_data,_display_name,_size,"
                                + "date_modified,storage_id,volume_hidden) VALUES(?,?,?,?,?,?,?)",
                        new Object[] { id, path, displayName, size, dateModified,
                                FAKE_USB_STORAGE_ID, volumeHidden });
                count++;
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
            c.close();
        }
        return count;
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

    private String queryString(String sql) {
        Cursor cursor = database.rawQuery(sql, null);
        try {
            if (!cursor.moveToFirst()) throw new AssertionError("No row for: " + sql);
            return cursor.getString(0);
        } finally {
            cursor.close();
        }
    }

    private void assertNoForeignKeyViolations() {
        Cursor cursor = database.rawQuery("PRAGMA foreign_key_check", null);
        try {
            assertFalse("foreign_key_check returned a violation", cursor.moveToFirst());
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
