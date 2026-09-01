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
import android.os.Bundle;
import android.provider.MediaStore;

import androidx.test.core.app.ApplicationProvider;

import com.archos.mediaprovider.DbHolder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class VideoStoreIdRemapDatabaseTest {

    private static final String DB_NAME = "video-store-id-remap-test.db";
    private static final String STORAGE = "/storage/ABCD-1234";
    private static final String OLD_PATH = STORAGE + "/FolderA/Movie.mkv";
    private static final String NEW_PATH = STORAGE + "/FolderB/Movie.mkv";
    private static final long OLD_ID = 44L;
    private static final long NEW_ID = 84L;

    private Application application;
    private DbHolder holder;
    private SQLiteDatabase database;
    private ContentResolver resolver;
    private VideoProvider provider;

    @Before
    public void setUp() throws Exception {
        application = ApplicationProvider.getApplicationContext();
        application.deleteDatabase(DB_NAME);
        holder = new DbHolder(new VideoOpenHelper(application, DB_NAME,
                VideoOpenHelper.getDatabaseVersion()));
        database = holder.get();
        resolver = mock(ContentResolver.class);
        provider = new VideoProvider();
        setField(provider, "mDbHolder", holder);
        setField(provider, "mVobHandler", mock(VobHandler.class));
        setField(provider, "mCr", resolver);
        stubNovaQueries();
        when(resolver.applyBatch(eq(VideoStore.AUTHORITY), any())).thenAnswer(invocation ->
                provider.applyBatch(invocation.getArgument(1)));
        insertScrapedSource();
    }

    @After
    public void tearDown() {
        if (holder != null) holder.close();
        if (application != null) application.deleteDatabase(DB_NAME);
    }

    @Test
    public void changedMediaStoreIdPreservesAllRelationshipsAndVolumeVisibility() {
        stubMediaStore(newMediaStoreCursor());

        VideoStoreImportImpl.LocalReconciliationResult result =
                VideoStoreImportImpl.reconcileChangedMediaStoreIds(
                        resolver, STORAGE, 1234);

        assertEquals(1, result.updated);
        assertEquals(0, result.removed);
        assertEquals(0L, queryLong("SELECT count(*) FROM files WHERE _id=" + OLD_ID));
        assertEquals(1L, queryLong("SELECT count(*) FROM files WHERE _id=" + NEW_ID
                + " AND remote_id=" + NEW_ID + " AND _data='" + NEW_PATH + "'"));
        assertEquals(1L, queryLong("SELECT count(*) FROM files_import WHERE _id=" + NEW_ID
                + " AND _data='" + NEW_PATH + "'"));
        assertEquals(123L, queryLong("SELECT bookmark FROM files WHERE _id=" + NEW_ID));
        assertEquals(456L, queryLong(
                "SELECT Archos_lastTimePlayed FROM files WHERE _id=" + NEW_ID));
        assertEquals(1L, queryLong("SELECT count(*) FROM movie WHERE _id=1000 AND video_id="
                + NEW_ID));
        assertEquals(1L, queryLong("SELECT count(*) FROM movie_posters WHERE movie_id=1000"));
        assertEquals(1L, queryLong("SELECT count(*) FROM movie_backdrops WHERE movie_id=1000"));
        assertEquals(1L, queryLong("SELECT count(*) FROM subtitles WHERE video_id=" + NEW_ID
                + " AND file_id=500"));
        assertEquals(1L, queryLong("SELECT count(*) FROM videothumbnails WHERE video_id="
                + NEW_ID));
        assertEquals(1L, queryLong("SELECT count(*) FROM video WHERE _id=" + NEW_ID
                + " AND m_id=1000"));

        ContentValues hidden = new ContentValues();
        hidden.put("volume_hidden", 123456L);
        assertEquals(1, database.update(VideoOpenHelper.FILES_IMPORT_TABLE_NAME, hidden,
                "_id=?", new String[] { String.valueOf(NEW_ID) }));
        assertEquals(123456L, queryLong("SELECT volume_hidden FROM files WHERE _id=" + NEW_ID));
        assertForeignKeysValid();
    }

    @Test
    public void destinationIdConflictRollsBackEntireRemap() {
        database.execSQL("INSERT INTO files(_id,remote_id,_data) VALUES(?,?,?)",
                new Object[] { NEW_ID, NEW_ID, STORAGE + "/Other/Conflict.mkv" });
        stubMediaStore(newMediaStoreCursor());

        VideoStoreImportImpl.LocalReconciliationResult result =
                VideoStoreImportImpl.reconcileChangedMediaStoreIds(
                        resolver, STORAGE, 1234);

        assertEquals(0, result.updated);
        assertEquals(1L, queryLong("SELECT count(*) FROM files WHERE _id=" + OLD_ID
                + " AND remote_id=" + OLD_ID + " AND _data='" + OLD_PATH + "'"));
        assertEquals(1L, queryLong("SELECT count(*) FROM files_import WHERE _id=" + OLD_ID
                + " AND _data='" + OLD_PATH + "'"));
        assertEquals(1L, queryLong("SELECT count(*) FROM movie WHERE video_id=" + OLD_ID));
        assertEquals(1L, queryLong("SELECT count(*) FROM videothumbnails WHERE video_id="
                + OLD_ID));
        assertForeignKeysValid();
    }

    @Test
    public void retainedScrapedSourceRepairsAnUnscrapedImportedDestination() {
        database.execSQL("INSERT INTO files_import "
                        + "(_id,_data,_display_name,_size,date_modified,storage_id,volume_hidden) "
                        + "VALUES(?,?,?,?,?,?,0)",
                new Object[] { NEW_ID, NEW_PATH, "Movie.mkv", 1234L, 202L, 1234 });
        assertEquals(0L, queryLong("SELECT ArchosMediaScraper_id FROM files WHERE _id=" + NEW_ID));
        stubMediaStore(newMediaStoreCursor());

        VideoStoreImportImpl.LocalReconciliationResult result =
                VideoStoreImportImpl.reconcileChangedMediaStoreIds(
                        resolver, STORAGE, 1234);

        assertEquals(1, result.updated);
        assertEquals(1L, queryLong("SELECT count(*) FROM files WHERE _id=" + NEW_ID
                + " AND remote_id=" + NEW_ID + " AND bookmark=123"));
        assertEquals(1L, queryLong("SELECT count(*) FROM files_import WHERE _id=" + NEW_ID
                + " AND _data='" + NEW_PATH + "'"));
        assertEquals(1L, queryLong("SELECT count(*) FROM movie WHERE video_id=" + NEW_ID));
        assertEquals(1L, queryLong("SELECT count(*) FROM movie_posters WHERE movie_id=1000"));
        assertEquals(1L, queryLong("SELECT count(*) FROM movie_backdrops WHERE movie_id=1000"));
        assertForeignKeysValid();
    }

    @Test
    public void changedMediaStoreIdCascadesEpisodeRelationship() {
        database.execSQL("INSERT INTO show(_id,name_show) VALUES(4000,?)",
                new Object[] { "Show" });
        database.execSQL("INSERT INTO episode(_id,video_id,show_episode,name_episode) "
                        + "VALUES(5000,?,4000,?)",
                new Object[] { OLD_ID, "Episode" });
        stubMediaStore(newMediaStoreCursor());

        VideoStoreImportImpl.LocalReconciliationResult result =
                VideoStoreImportImpl.reconcileChangedMediaStoreIds(
                        resolver, STORAGE, 1234);

        assertEquals(1, result.updated);
        assertEquals(1L, queryLong("SELECT count(*) FROM episode WHERE _id=5000 "
                + "AND video_id=" + NEW_ID + " AND show_episode=4000"));
        assertEquals(1L, queryLong("SELECT count(*) FROM video WHERE _id=" + NEW_ID
                + " AND e_id=5000"));
        assertForeignKeysValid();
    }

    @Test
    public void importedDestinationWithUserStateIsNotReplaced() {
        database.execSQL("INSERT INTO files_import "
                        + "(_id,_data,_display_name,_size,date_modified,storage_id,volume_hidden) "
                        + "VALUES(?,?,?,?,?,?,0)",
                new Object[] { NEW_ID, NEW_PATH, "Movie.mkv", 1234L, 202L, 1234 });
        database.execSQL("UPDATE files SET bookmark=789 WHERE _id=?",
                new Object[] { NEW_ID });
        stubMediaStore(newMediaStoreCursor());

        VideoStoreImportImpl.LocalReconciliationResult result =
                VideoStoreImportImpl.reconcileChangedMediaStoreIds(
                        resolver, STORAGE, 1234);

        assertEquals(0, result.updated);
        assertEquals(1L, queryLong("SELECT count(*) FROM files WHERE _id=" + OLD_ID
                + " AND remote_id=" + OLD_ID + " AND bookmark=123"));
        assertEquals(1L, queryLong("SELECT count(*) FROM files WHERE _id=" + NEW_ID
                + " AND remote_id=" + NEW_ID + " AND bookmark=789"));
        assertEquals(1L, queryLong("SELECT count(*) FROM movie WHERE video_id=" + OLD_ID));
        assertForeignKeysValid();
    }

    private void insertScrapedSource() {
        assertEquals(1L, queryLong("PRAGMA foreign_keys"));
        database.execSQL("INSERT INTO files_import "
                        + "(_id,_data,_display_name,_size,date_modified,storage_id,volume_hidden) "
                        + "VALUES(?,?,?,?,?,?,?)",
                new Object[] { OLD_ID, OLD_PATH, "Movie.mkv", 1234L, 200L, 1234, 98765L });
        database.execSQL("UPDATE files SET media_type=3,bookmark=123,"
                        + "Archos_lastTimePlayed=456 WHERE _id=?",
                new Object[] { OLD_ID });
        database.execSQL("INSERT INTO movie(_id,video_id,name_movie) VALUES(1000,?,?)",
                new Object[] { OLD_ID, "Movie" });
        database.execSQL("INSERT INTO movie_posters"
                        + "(_id,movie_id,m_po_thumb_file,m_po_large_file) VALUES(2000,1000,?,?)",
                new Object[] { "poster-thumb.jpg", "poster-large.jpg" });
        database.execSQL("INSERT INTO movie_backdrops"
                        + "(_id,movie_id,m_bd_thumb_file,m_bd_large_file) VALUES(3000,1000,?,?)",
                new Object[] { "backdrop-thumb.jpg", "backdrop-large.jpg" });
        database.execSQL("INSERT INTO files(_id,remote_id,_data) VALUES(500,500,?)",
                new Object[] { STORAGE + "/FolderA/Movie.srt" });
        database.execSQL("INSERT INTO subtitles(_id,_data,video_id,file_id) VALUES(1,?,?,500)",
                new Object[] { STORAGE + "/FolderA/Movie.srt", OLD_ID });
        database.execSQL("INSERT INTO videothumbnails(_id,_data,video_id,kind) "
                + "VALUES(1,NULL," + OLD_ID + ",1)");
    }

    private Cursor newMediaStoreCursor() {
        android.database.MatrixCursor cursor = new android.database.MatrixCursor(new String[] {
                "_id", "_data", "_display_name", "_size", "date_added", "date_modified",
                "bucket_id", "bucket_display_name", "format", "parent"
        });
        cursor.addRow(new Object[] {
                NEW_ID, NEW_PATH, "Movie.mkv", 1234L, 100L, 202L,
                "bucket", "FolderB", 0, 1
        });
        return cursor;
    }

    private void stubNovaQueries() {
        when(resolver.query(eq(VideoStoreInternal.FILES_IMPORT), any(String[].class),
                anyString(), any(String[].class), isNull())).thenAnswer(invocation ->
                database.query(VideoOpenHelper.FILES_IMPORT_TABLE_NAME,
                        invocation.getArgument(1), invocation.getArgument(2),
                        invocation.getArgument(3), null, null, null));
        when(resolver.query(eq(VideoStoreInternal.FILES_IMPORT), any(String[].class),
                any(Bundle.class), isNull())).thenAnswer(invocation -> {
                    Bundle queryArgs = invocation.getArgument(2);
                    return database.query(VideoOpenHelper.FILES_IMPORT_TABLE_NAME,
                            invocation.getArgument(1),
                            queryArgs.getString(ContentResolver.QUERY_ARG_SQL_SELECTION),
                            queryArgs.getStringArray(
                                    ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS),
                            null, null, "_id ASC",
                            String.valueOf(queryArgs.getInt(ContentResolver.QUERY_ARG_LIMIT)));
                });
        when(resolver.query(eq(VideoStoreInternal.FILES), any(String[].class),
                anyString(), any(String[].class), isNull())).thenAnswer(invocation ->
                database.query(VideoOpenHelper.FILES_TABLE_NAME,
                        invocation.getArgument(1), invocation.getArgument(2),
                        invocation.getArgument(3), null, null, null));
    }

    private void stubMediaStore(Cursor cursor) {
        when(resolver.query(eq(MediaStore.Files.getContentUri("external")),
                any(String[].class), any(Bundle.class), isNull()))
                .thenReturn(cursor);
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

    private void assertForeignKeysValid() {
        Cursor cursor = database.rawQuery("PRAGMA foreign_key_check", null);
        try {
            assertFalse("foreign_key_check returned a violation", cursor.moveToFirst());
        } finally {
            cursor.close();
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = VideoProvider.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
