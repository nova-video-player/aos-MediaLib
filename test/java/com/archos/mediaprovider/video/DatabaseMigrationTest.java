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

import static org.junit.Assert.*;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class DatabaseMigrationTest {

    @Test
    public void testUpgradeFromEverySupportedVersion() {
        Context context = ApplicationProvider.getApplicationContext();
        int currentVersion = com.archos.mediaprovider.video.VideoOpenHelper.getDatabaseVersion();

        for (int sourceVersion = 36; sourceVersion < currentVersion; sourceVersion++) {
            String dbName = "test_upgrade_v" + sourceVersion + ".db";
            context.deleteDatabase(dbName);

            VideoOpenHelper sourceHelper = new VideoOpenHelper(context, dbName, sourceVersion);
            sourceHelper.getWritableDatabase().close();

            VideoOpenHelper currentHelper = new VideoOpenHelper(context, dbName, currentVersion);
            SQLiteDatabase db = currentHelper.getWritableDatabase();

            assertEquals("Failed upgrade from version " + sourceVersion, currentVersion, db.getVersion());
            assertEquals("Integrity failure after upgrading version " + sourceVersion,
                    "ok", querySingleString(db, "PRAGMA integrity_check"));
            assertEquals("Foreign-key failure after upgrading version " + sourceVersion,
                    0, foreignKeyViolationCount(db));
            assertTrue(triggerExists(db, "movie_delete"));
            assertTrue(triggerExists(db, "show_delete"));
            assertTrue(triggerExists(db, "episode_delete"));
            assertEquals(0, getWritableSchema(db));

            db.close();
            context.deleteDatabase(dbName);
        }
    }

    @Test
    public void testCreateAtVersion50StopsBeforeV51() {
        Context context = ApplicationProvider.getApplicationContext();
        String dbName = "test_create_v50.db";
        context.deleteDatabase(dbName);

        VideoOpenHelper helper = new VideoOpenHelper(context, dbName, 50);
        SQLiteDatabase db = helper.getWritableDatabase();

        assertEquals(50, db.getVersion());
        assertFalse(getSchemaSql(db, "table", "movie_posters").contains("UNIQUE"));
        assertTrue(columnExists(db, "movie", ScraperStore.Movie.RELEASE_DATE));
        assertTrue(columnExists(db, "files", "Archos_subtitleLanguage"));

        db.close();
        context.deleteDatabase(dbName);
    }

    @Test
    public void testMigrationV58AddsOriginalLanguageAndBackfillsUnknown() {
        Context context = ApplicationProvider.getApplicationContext();
        String dbName = "test_migration_v58.db";
        context.deleteDatabase(dbName);

        VideoOpenHelper helper57 = new VideoOpenHelper(context, dbName, 57);
        SQLiteDatabase db = helper57.getWritableDatabase();
        // movie.video_id has a foreign key to files. The fixture only needs a legacy movie row.
        db.execSQL("PRAGMA foreign_keys = OFF");
        db.execSQL("INSERT INTO movie (_id, video_id, name_movie) VALUES (1, 1, 'Legacy movie')");
        db.execSQL("INSERT INTO show (_id, name_show) VALUES (1, 'Legacy show')");
        db.close();

        VideoOpenHelper helper58 = new VideoOpenHelper(context, dbName, 58);
        db = helper58.getWritableDatabase();

        assertTrue(columnExists(db, "movie", ScraperStore.Movie.ORIGINAL_LANGUAGE));
        assertTrue(columnExists(db, "movie", ScraperStore.Movie.ORIGINAL_TITLE));
        assertTrue(columnExists(db, "movie", ScraperStore.Movie.SPOKEN_LANGUAGES));
        assertEquals("und", querySingleString(db, "SELECT " +
                ScraperStore.Movie.ORIGINAL_LANGUAGE + " FROM movie WHERE _id = 1"));
        assertEquals("", querySingleString(db, "SELECT " +
                ScraperStore.Movie.ORIGINAL_TITLE + " FROM movie WHERE _id = 1"));
        assertEquals("", querySingleString(db, "SELECT " +
                ScraperStore.Movie.SPOKEN_LANGUAGES + " FROM movie WHERE _id = 1"));
        assertTrue(columnExists(db, "show", ScraperStore.Show.ORIGINAL_LANGUAGE));
        assertTrue(columnExists(db, "show", ScraperStore.Show.ORIGINAL_TITLE));
        assertTrue(columnExists(db, "show", ScraperStore.Show.SPOKEN_LANGUAGES));
        assertTrue(columnExists(db, "video", VideoStore.Video.VideoColumns.SCRAPER_ORIGINAL_LANGUAGE));
        assertEquals("und", querySingleString(db, "SELECT " +
                ScraperStore.Show.ORIGINAL_LANGUAGE + " FROM show WHERE _id = 1"));
        assertEquals("", querySingleString(db, "SELECT " +
                ScraperStore.Show.ORIGINAL_TITLE + " FROM show WHERE _id = 1"));
        assertEquals("", querySingleString(db, "SELECT " +
                ScraperStore.Show.SPOKEN_LANGUAGES + " FROM show WHERE _id = 1"));

        db.execSQL("PRAGMA foreign_keys = OFF");
        db.execSQL("DELETE FROM movie WHERE _id = 1");
        db.execSQL("DELETE FROM show WHERE _id = 1");
        db.close();
        context.deleteDatabase(dbName);
    }

    @Test
    public void testMigrationV43RecreatesScannerTriggers() {
        Context context = ApplicationProvider.getApplicationContext();
        String dbName = "test_migration_v43.db";
        context.deleteDatabase(dbName);

        VideoOpenHelper helper41 = new VideoOpenHelper(context, dbName, 41);
        helper41.getWritableDatabase().close();

        VideoOpenHelper helper43 = new VideoOpenHelper(context, dbName, 43);
        SQLiteDatabase db = helper43.getWritableDatabase();

        assertEquals(43, db.getVersion());
        assertTrue(triggerExists(db, "after_insert_files_scanned"));
        assertTrue(triggerExists(db, "after_delete_files_scanned"));
        assertTrue(triggerExists(db, "after_update_uri_files_scanned"));
        assertEquals("writable_schema should be disabled after migration", 0, getWritableSchema(db));

        db.close();
        context.deleteDatabase(dbName);
    }

    @Test
    public void testMigrationV51RebuildsArtworkTablesAndRestoresVideoView() {
        Context context = ApplicationProvider.getApplicationContext();
        String dbName = "test_migration_v51.db";
        context.deleteDatabase(dbName);

        VideoOpenHelper helper50 = new VideoOpenHelper(context, dbName, 50);
        SQLiteDatabase db = helper50.getWritableDatabase();
        db.execSQL("INSERT INTO movie_posters " +
                "(_id, movie_id, m_po_thumb_url, m_po_thumb_file, m_po_large_url, m_po_large_file) " +
                "VALUES (1, NULL, NULL, 'duplicate-poster.jpg', NULL, NULL)");
        db.execSQL("INSERT INTO movie_posters " +
                "(_id, movie_id, m_po_thumb_url, m_po_thumb_file, m_po_large_url, m_po_large_file) " +
                "VALUES (2, NULL, NULL, 'duplicate-poster.jpg', NULL, NULL)");
        db.execSQL("INSERT INTO movie_backdrops " +
                "(_id, movie_id, m_bd_thumb_url, m_bd_thumb_file, m_bd_large_url, m_bd_large_file) " +
                "VALUES (1, NULL, NULL, 'duplicate-backdrop.jpg', NULL, NULL)");
        db.execSQL("INSERT INTO movie_backdrops " +
                "(_id, movie_id, m_bd_thumb_url, m_bd_thumb_file, m_bd_large_url, m_bd_large_file) " +
                "VALUES (2, NULL, NULL, 'duplicate-backdrop.jpg', NULL, NULL)");
        db.close();

        VideoOpenHelper helper51 = new VideoOpenHelper(context, dbName, 51);
        SQLiteDatabase upgradedDb = helper51.getWritableDatabase();

        assertEquals(51, upgradedDb.getVersion());
        assertTrue(getSchemaSql(upgradedDb, "table", "movie_posters").contains("UNIQUE"));
        assertTrue(getSchemaSql(upgradedDb, "table", "movie_backdrops").contains("UNIQUE"));
        assertTrue(getSchemaSql(upgradedDb, "view", "video").startsWith("CREATE VIEW video"));
        assertEquals("1", querySingleString(upgradedDb, "SELECT count(*) FROM movie_posters"));
        assertEquals("1", querySingleString(upgradedDb, "SELECT count(*) FROM movie_backdrops"));
        assertEquals("0", querySingleString(upgradedDb, "SELECT count(*) FROM video"));
        assertEquals("ok", querySingleString(upgradedDb, "PRAGMA integrity_check"));

        upgradedDb.close();
        context.deleteDatabase(dbName);
    }

    @Test
    public void testFreshCreateCurrentSchema() {
        Context context = ApplicationProvider.getApplicationContext();
        String dbName = "test_create_current.db";
        context.deleteDatabase(dbName);

        VideoOpenHelper helper = new VideoOpenHelper(context, dbName, com.archos.mediaprovider.video.VideoOpenHelper.getDatabaseVersion());
        SQLiteDatabase db = helper.getWritableDatabase();

        assertEquals(com.archos.mediaprovider.video.VideoOpenHelper.getDatabaseVersion(), db.getVersion());
        assertTrue(triggerExists(db, "movie_delete"));
        assertTrue(triggerExists(db, "show_delete"));
        assertTrue(triggerExists(db, "episode_delete"));
        assertFalse(getSchemaSql(db, "trigger", "hide_volume_cmd_trigger")
                .contains("DELETE FROM files_import"));
        assertTrue(getSchemaSql(db, "trigger", "hide_volume_cmd_trigger")
                .contains("UPDATE files_import SET volume_hidden"));
        assertOwnerAwareArtworkSchema(db);
        assertTrue(columnExists(db, "movie", ScraperStore.Movie.ORIGINAL_LANGUAGE));
        assertTrue(columnExists(db, "movie", ScraperStore.Movie.ORIGINAL_TITLE));
        assertTrue(columnExists(db, "movie", ScraperStore.Movie.SPOKEN_LANGUAGES));
        assertTrue(columnExists(db, "show", ScraperStore.Show.ORIGINAL_LANGUAGE));
        assertTrue(columnExists(db, "show", ScraperStore.Show.ORIGINAL_TITLE));
        assertTrue(columnExists(db, "show", ScraperStore.Show.SPOKEN_LANGUAGES));
        insertFile(db, 1, "/storage/usb/Fresh-movie.mkv");
        db.execSQL("INSERT INTO movie (_id, video_id, name_movie) VALUES (1, 1, 'Fresh movie')");
        assertEquals("und", querySingleString(db, "SELECT " +
                ScraperStore.Movie.ORIGINAL_LANGUAGE + " FROM movie WHERE _id = 1"));
        assertEquals("", querySingleString(db, "SELECT " +
                ScraperStore.Movie.ORIGINAL_TITLE + " FROM movie WHERE _id = 1"));
        assertEquals("", querySingleString(db, "SELECT " +
                ScraperStore.Movie.SPOKEN_LANGUAGES + " FROM movie WHERE _id = 1"));
        db.execSQL("INSERT INTO show (_id, name_show) VALUES (1, 'Fresh show')");
        assertEquals("und", querySingleString(db, "SELECT " +
                ScraperStore.Show.ORIGINAL_LANGUAGE + " FROM show WHERE _id = 1"));
        assertEquals("", querySingleString(db, "SELECT " +
                ScraperStore.Show.ORIGINAL_TITLE + " FROM show WHERE _id = 1"));
        assertEquals("", querySingleString(db, "SELECT " +
                ScraperStore.Show.SPOKEN_LANGUAGES + " FROM show WHERE _id = 1"));
        assertEquals("ok", querySingleString(db, "PRAGMA integrity_check"));
        assertEquals(0, foreignKeyViolationCount(db));
        assertEquals("writable_schema should be disabled after creation", 0, getWritableSchema(db));

        db.close();
        context.deleteDatabase(dbName);
    }

    @Test
    public void testMigrationV56RepairsCrossOwnerArtworkReferences() {
        Context context = ApplicationProvider.getApplicationContext();
        String dbName = "test_migration_v56_cross_owner.db";
        context.deleteDatabase(dbName);

        VideoOpenHelper helper55 = new VideoOpenHelper(context, dbName, 55);
        SQLiteDatabase db = helper55.getWritableDatabase();
        insertFile(db, 1001, "/storage/usb/Movie-A.mkv");
        insertFile(db, 1002, "smb://server/Movies/Movie-A.mkv");
        db.execSQL("INSERT INTO MOVIE(_id,video_id,name_movie,m_online_id,cover_movie,backdrop_movie,m_poster_id,m_backdrop_id) " +
                "VALUES(281,1001,'Movie A',699,'shared-poster.jpg','shared-backdrop.jpg',10,20)");
        db.execSQL("INSERT INTO MOVIE(_id,video_id,name_movie,m_online_id,cover_movie,backdrop_movie,m_poster_id,m_backdrop_id) " +
                "VALUES(398,1002,'Movie A copy',699,'shared-poster.jpg','shared-backdrop.jpg',10,20)");
        db.execSQL("INSERT INTO movie_posters" +
                "(_id,movie_id,m_po_thumb_url,m_po_thumb_file,m_po_large_url,m_po_large_file) " +
                "VALUES(10,281,'poster-thumb-url','shared-poster-thumb.jpg','poster-large-url','shared-poster.jpg')");
        db.execSQL("INSERT INTO movie_backdrops" +
                "(_id,movie_id,m_bd_thumb_url,m_bd_thumb_file,m_bd_large_url,m_bd_large_file) " +
                "VALUES(20,281,'backdrop-thumb-url','shared-backdrop-thumb.jpg','backdrop-large-url','shared-backdrop.jpg')");
        db.execSQL("INSERT INTO movie_posters" +
                "(_id,movie_id,m_po_thumb_url,m_po_thumb_file,m_po_large_url,m_po_large_file) " +
                "VALUES(11,281,'poster-2-thumb-url','poster-2-thumb.jpg','poster-2-large-url','poster-2.jpg')");
        db.execSQL("INSERT INTO movie_backdrops" +
                "(_id,movie_id,m_bd_thumb_url,m_bd_thumb_file,m_bd_large_url,m_bd_large_file) " +
                "VALUES(21,281,'backdrop-2-thumb-url','backdrop-2-thumb.jpg','backdrop-2-large-url','backdrop-2.jpg')");

        db.execSQL("INSERT INTO SHOW(_id,name_show,s_online_id,cover_show,backdrop_show,s_poster_id,s_backdrop_id) " +
                "VALUES(501,'Show A',700,'shared-show-poster.jpg','shared-show-backdrop.jpg',30,40)");
        db.execSQL("INSERT INTO SHOW(_id,name_show,s_online_id,cover_show,backdrop_show,s_poster_id,s_backdrop_id) " +
                "VALUES(502,'Show A copy',700,'shared-show-poster.jpg','shared-show-backdrop.jpg',30,40)");
        db.execSQL("INSERT INTO show_posters" +
                "(_id,show_id,s_po_thumb_url,s_po_thumb_file,s_po_large_url,s_po_large_file,s_po_season) " +
                "VALUES(30,501,'show-poster-thumb-url','shared-show-poster-thumb.jpg'," +
                "'show-poster-large-url','shared-show-poster.jpg',-1)");
        db.execSQL("INSERT INTO show_backdrops" +
                "(_id,show_id,s_bd_thumb_url,s_bd_thumb_file,s_bd_large_url,s_bd_large_file) " +
                "VALUES(40,501,'show-backdrop-thumb-url','shared-show-backdrop-thumb.jpg'," +
                "'show-backdrop-large-url','shared-show-backdrop.jpg')");
        db.execSQL("INSERT INTO show_posters" +
                "(_id,show_id,s_po_thumb_url,s_po_thumb_file,s_po_large_url,s_po_large_file,s_po_season) " +
                "VALUES(31,501,'show-poster-2-thumb-url','show-poster-2-thumb.jpg'," +
                "'show-poster-2-large-url','show-poster-2.jpg',-1)");
        db.execSQL("INSERT INTO show_backdrops" +
                "(_id,show_id,s_bd_thumb_url,s_bd_thumb_file,s_bd_large_url,s_bd_large_file) " +
                "VALUES(41,501,'show-backdrop-2-thumb-url','show-backdrop-2-thumb.jpg'," +
                "'show-backdrop-2-large-url','show-backdrop-2.jpg')");
        db.close();

        VideoOpenHelper helper56 = new VideoOpenHelper(context, dbName, 56);
        SQLiteDatabase upgradedDb = helper56.getWritableDatabase();

        assertOwnerAwareArtworkSchema(upgradedDb);
        assertEquals("2", querySingleString(upgradedDb,
                "SELECT count(*) FROM movie_posters WHERE m_po_large_file='shared-poster.jpg'"));
        assertEquals("2", querySingleString(upgradedDb,
                "SELECT count(*) FROM movie_backdrops WHERE m_bd_large_file='shared-backdrop.jpg'"));
        assertEquals("2", querySingleString(upgradedDb,
                "SELECT count(*) FROM show_posters WHERE s_po_large_file='shared-show-poster.jpg'"));
        assertEquals("2", querySingleString(upgradedDb,
                "SELECT count(*) FROM show_backdrops WHERE s_bd_large_file='shared-show-backdrop.jpg'"));
        assertEquals("2", querySingleString(upgradedDb,
                "SELECT count(*) FROM movie_posters WHERE movie_id=398"));
        assertEquals("2", querySingleString(upgradedDb,
                "SELECT count(*) FROM movie_backdrops WHERE movie_id=398"));
        assertEquals("2", querySingleString(upgradedDb,
                "SELECT count(*) FROM show_posters WHERE show_id=502"));
        assertEquals("2", querySingleString(upgradedDb,
                "SELECT count(*) FROM show_backdrops WHERE show_id=502"));
        assertSelectedImageOwnedBy(upgradedDb, "MOVIE", 281, "m_poster_id",
                "movie_posters", "movie_id");
        assertSelectedImageOwnedBy(upgradedDb, "MOVIE", 398, "m_poster_id",
                "movie_posters", "movie_id");
        assertSelectedImageOwnedBy(upgradedDb, "MOVIE", 281, "m_backdrop_id",
                "movie_backdrops", "movie_id");
        assertSelectedImageOwnedBy(upgradedDb, "MOVIE", 398, "m_backdrop_id",
                "movie_backdrops", "movie_id");
        assertSelectedImageOwnedBy(upgradedDb, "SHOW", 501, "s_poster_id",
                "show_posters", "show_id");
        assertSelectedImageOwnedBy(upgradedDb, "SHOW", 502, "s_poster_id",
                "show_posters", "show_id");
        assertSelectedImageOwnedBy(upgradedDb, "SHOW", 501, "s_backdrop_id",
                "show_backdrops", "show_id");
        assertSelectedImageOwnedBy(upgradedDb, "SHOW", 502, "s_backdrop_id",
                "show_backdrops", "show_id");
        assertEquals("poster-large-url", querySingleString(upgradedDb,
                "SELECT m_po_large_url FROM movie_posters WHERE movie_id=398"));
        assertEquals("backdrop-large-url", querySingleString(upgradedDb,
                "SELECT m_bd_large_url FROM movie_backdrops WHERE movie_id=398"));
        assertEquals("0", querySingleString(upgradedDb, "SELECT count(*) FROM delete_files"));

        // Deleting one metadata owner queues its paths, but the existing central
        // protection pass must retain files still referenced by another owner.
        upgradedDb.execSQL("DELETE FROM MOVIE WHERE _id=398");
        upgradedDb.execSQL("DELETE FROM SHOW WHERE _id=502");
        protectReferencedArtwork(upgradedDb);
        assertEquals("0", querySingleString(upgradedDb,
                "SELECT count(*) FROM delete_files WHERE name IN (" +
                        "'shared-poster.jpg','shared-poster-thumb.jpg'," +
                        "'shared-backdrop.jpg','shared-backdrop-thumb.jpg'," +
                        "'shared-show-poster.jpg','shared-show-poster-thumb.jpg'," +
                        "'shared-show-backdrop.jpg','shared-show-backdrop-thumb.jpg')"));
        assertEquals("2", querySingleString(upgradedDb,
                "SELECT count(*) FROM movie_posters WHERE movie_id=281"));
        assertEquals("2", querySingleString(upgradedDb,
                "SELECT count(*) FROM movie_backdrops WHERE movie_id=281"));
        assertEquals("2", querySingleString(upgradedDb,
                "SELECT count(*) FROM show_posters WHERE show_id=501"));
        assertEquals("2", querySingleString(upgradedDb,
                "SELECT count(*) FROM show_backdrops WHERE show_id=501"));
        assertEquals("ok", querySingleString(upgradedDb, "PRAGMA integrity_check"));
        assertEquals(0, foreignKeyViolationCount(upgradedDb));

        upgradedDb.close();
        context.deleteDatabase(dbName);
    }

    @Test
    @Config(sdk = 23)
    public void testDirectUpgradeFromV50PreservesDuplicateArtworkForDifferentOwners() {
        Context context = ApplicationProvider.getApplicationContext();
        String dbName = "test_migration_v50_to_v56.db";
        context.deleteDatabase(dbName);

        VideoOpenHelper helper50 = new VideoOpenHelper(context, dbName, 50);
        SQLiteDatabase db = helper50.getWritableDatabase();
        insertFile(db, 2001, "/storage/usb/Movie-B.mkv");
        insertFile(db, 2002, "/storage/usb-copy/Movie-B.mkv");
        db.execSQL("INSERT INTO MOVIE(_id,video_id,name_movie,cover_movie,m_poster_id) " +
                "VALUES(601,2001,'Movie B','same-poster.jpg',61)");
        db.execSQL("INSERT INTO MOVIE(_id,video_id,name_movie,cover_movie,m_poster_id) " +
                "VALUES(602,2002,'Movie B copy','same-poster.jpg',62)");
        db.execSQL("INSERT INTO movie_posters" +
                "(_id,movie_id,m_po_thumb_file,m_po_large_file) " +
                "VALUES(61,601,'same-thumb.jpg','same-poster.jpg')");
        db.execSQL("INSERT INTO movie_posters" +
                "(_id,movie_id,m_po_thumb_file,m_po_large_file) " +
                "VALUES(62,602,'same-thumb.jpg','same-poster.jpg')");
        db.close();

        VideoOpenHelper helper56 = new VideoOpenHelper(context, dbName, 56);
        SQLiteDatabase upgradedDb = helper56.getWritableDatabase();

        assertEquals("2", querySingleString(upgradedDb,
                "SELECT count(*) FROM movie_posters WHERE m_po_large_file='same-poster.jpg'"));
        assertEquals("61", querySingleString(upgradedDb,
                "SELECT m_poster_id FROM MOVIE WHERE _id=601"));
        assertEquals("62", querySingleString(upgradedDb,
                "SELECT m_poster_id FROM MOVIE WHERE _id=602"));
        assertEquals(0, foreignKeyViolationCount(upgradedDb));

        upgradedDb.close();
        context.deleteDatabase(dbName);
    }

    @Test
    public void testMigrationV55() {
        Context context = ApplicationProvider.getApplicationContext();
        String dbName = "test_migration.db";
        context.deleteDatabase(dbName);

        // 1. Create a database with the full schema at version 54
        VideoOpenHelper helper54 = new VideoOpenHelper(context, dbName, 54);
        SQLiteDatabase db = helper54.getWritableDatabase();
        assertEquals(54, db.getVersion());

        // 2. Manually downgrade the triggers to the "broken" state (-1/-1 and old pre-v39 performance issues)
        db.execSQL("DROP TRIGGER IF EXISTS movie_delete");
        db.execSQL("CREATE TRIGGER movie_delete AFTER DELETE ON movie BEGIN " +
                   "delete from actor where _id in (select _id from v_actor_deletable); " + // simulate old slow cleanup
                   "UPDATE files SET ArchosMediaScraper_id=-1, ArchosMediaScraper_type=-1 " +
                   "WHERE ArchosMediaScraper_id = OLD._id; END");
        
        db.execSQL("DROP TRIGGER IF EXISTS episode_delete");
        db.execSQL("CREATE TRIGGER episode_delete AFTER DELETE ON episode BEGIN " +
                   "delete from actor where _id in (select _id from v_actor_deletable); " + // simulate old slow cleanup
                   "UPDATE files SET ArchosMediaScraper_id=-1, ArchosMediaScraper_type=-1 " +
                   "WHERE ArchosMediaScraper_id = OLD._id; END");

        // Verify old triggers exist and contain both issues (-1 and slow cleanup)
        assertTrue("movie_delete should contain -1", checkTriggerContains(db, "movie_delete", "-1"));
        assertTrue("movie_delete should contain v_actor_deletable", checkTriggerContains(db, "movie_delete", "v_actor_deletable"));

        db.close();

        // 3. Run the upgrade to version 55 using VideoOpenHelper
        VideoOpenHelper helper55 = new VideoOpenHelper(context, dbName, 55);
        SQLiteDatabase upgradedDb = helper55.getWritableDatabase();
        assertEquals(55, upgradedDb.getVersion());

        // 4. Verify the triggers are updated to 0/0 AND maintain v39 performance contract
        assertTrue("movie_delete should contain 0 after migration", checkTriggerContains(upgradedDb, "movie_delete", "ArchosMediaScraper_id=0"));
        assertTrue("episode_delete should contain 0 after migration", checkTriggerContains(upgradedDb, "episode_delete", "ArchosMediaScraper_id=0"));
        assertTrue("show_delete should be recreated after migration", triggerExists(upgradedDb, "show_delete"));
        assertEquals("writable_schema should be disabled after migration", 0, getWritableSchema(upgradedDb));
        
        assertFalse("movie_delete should NOT contain -1 after migration", checkTriggerContains(upgradedDb, "movie_delete", "-1"));
        
        // Performance Contract Verification: Ensure triggers DO NOT contain the slow deletable-view cleanups
        assertFalse("movie_delete should NOT contain v_actor_deletable (v39 optimization)", checkTriggerContains(upgradedDb, "movie_delete", "v_actor_deletable"));
        assertFalse("movie_delete should NOT contain v_director_deletable (v39 optimization)", checkTriggerContains(upgradedDb, "movie_delete", "v_director_deletable"));
        assertFalse("episode_delete should NOT contain v_actor_deletable (v39 optimization)", checkTriggerContains(upgradedDb, "episode_delete", "v_actor_deletable"));
        
        upgradedDb.close();
        context.deleteDatabase(dbName);
    }

    @Test
    public void testMigrationV57MovesExpiryDeleteOutOfHideVolumeTrigger() {
        Context context = ApplicationProvider.getApplicationContext();
        String dbName = "test_migration_v57.db";
        context.deleteDatabase(dbName);

        VideoOpenHelper helper56 = new VideoOpenHelper(context, dbName, 56);
        SQLiteDatabase db = helper56.getWritableDatabase();
        assertTrue(checkTriggerContains(db, "hide_volume_cmd_trigger", "DELETE FROM files_import"));
        db.close();

        VideoOpenHelper helper57 = new VideoOpenHelper(context, dbName, 57);
        SQLiteDatabase upgraded = helper57.getWritableDatabase();
        String triggerSql = getSchemaSql(upgraded, "trigger", "hide_volume_cmd_trigger");
        assertFalse(triggerSql.contains("DELETE FROM files_import"));
        assertTrue(triggerSql.contains("UPDATE files_import SET volume_hidden"));
        assertEquals("ok", querySingleString(upgraded, "PRAGMA integrity_check"));
        assertEquals(0, foreignKeyViolationCount(upgraded));
        assertEquals(0, getWritableSchema(upgraded));
        upgraded.close();
        context.deleteDatabase(dbName);
    }

    private boolean checkTriggerContains(SQLiteDatabase db, String triggerName, String expectedContent) {
        Cursor cursor = db.rawQuery("SELECT sql FROM sqlite_master WHERE type='trigger' AND name=?", new String[]{triggerName});
        if (cursor != null && cursor.moveToFirst()) {
            String sql = cursor.getString(0);
            cursor.close();
            return sql != null && sql.contains(expectedContent);
        }
        if (cursor != null) cursor.close();
        return false;
    }

    private boolean triggerExists(SQLiteDatabase db, String triggerName) {
        Cursor cursor = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='trigger' AND name=?", new String[]{triggerName});
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }

    private boolean indexExists(SQLiteDatabase db, String indexName) {
        Cursor cursor = db.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type='index' AND name=?",
                new String[]{indexName});
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }

    private void assertOwnerAwareArtworkSchema(SQLiteDatabase db) {
        assertTrue(normalizeSql(getSchemaSql(db, "table", "movie_posters"))
                .contains("UNIQUE(MOVIE_ID,M_PO_LARGE_FILE)"));
        assertTrue(normalizeSql(getSchemaSql(db, "table", "movie_backdrops"))
                .contains("UNIQUE(MOVIE_ID,M_BD_LARGE_FILE)"));
        assertTrue(normalizeSql(getSchemaSql(db, "table", "show_posters"))
                .contains("UNIQUE(SHOW_ID,S_PO_LARGE_FILE)"));
        assertTrue(normalizeSql(getSchemaSql(db, "table", "show_backdrops"))
                .contains("UNIQUE(SHOW_ID,S_BD_LARGE_FILE)"));
        assertTrue(triggerExists(db, "movie_posters_delete"));
        assertTrue(triggerExists(db, "movie_backdrops_delete"));
        assertTrue(triggerExists(db, "show_posters_delete"));
        assertTrue(triggerExists(db, "show_backdrops_delete"));
        assertTrue(indexExists(db, "movie_posters_idx"));
        assertTrue(indexExists(db, "movie_backdrops_idx"));
        assertTrue(indexExists(db, "show_posters_idx"));
        assertTrue(indexExists(db, "show_backdrops_idx"));
        assertTrue(indexExists(db, "movie_posters_large_file_idx"));
        assertTrue(indexExists(db, "movie_backdrops_large_file_idx"));
        assertTrue(indexExists(db, "show_posters_large_file_idx"));
        assertTrue(indexExists(db, "show_backdrops_large_file_idx"));
    }

    private String normalizeSql(String sql) {
        return sql.toUpperCase().replaceAll("\\s+", "");
    }

    private void insertFile(SQLiteDatabase db, long id, String path) {
        db.execSQL("INSERT INTO files(_id,remote_id,_data) VALUES(?,?,?)",
                new Object[] { id, id, path });
    }

    private void assertSelectedImageOwnedBy(SQLiteDatabase db, String ownerTable,
            long ownerId, String selectedColumn, String imageTable, String imageOwnerColumn) {
        assertEquals("1", querySingleString(db,
                "SELECT count(*) FROM " + ownerTable + " o JOIN " + imageTable +
                        " i ON i._id=o." + selectedColumn +
                        " WHERE o._id=" + ownerId + " AND i." + imageOwnerColumn + "=o._id"));
    }

    private void protectReferencedArtwork(SQLiteDatabase db) {
        String[] tablesAndColumns = {
                "MOVIE:cover_movie", "MOVIE:backdrop_movie",
                "SHOW:cover_show", "SHOW:backdrop_show",
                "movie_posters:m_po_large_file", "movie_posters:m_po_thumb_file",
                "movie_backdrops:m_bd_large_file", "movie_backdrops:m_bd_thumb_file",
                "show_posters:s_po_large_file", "show_posters:s_po_thumb_file",
                "show_backdrops:s_bd_large_file", "show_backdrops:s_bd_thumb_file"
        };
        for (String item : tablesAndColumns) {
            String[] split = item.split(":");
            db.execSQL("DELETE FROM delete_files WHERE name IN (SELECT " + split[1] +
                    " FROM " + split[0] + " WHERE " + split[1] + " IS NOT NULL)");
        }
    }

    private boolean columnExists(SQLiteDatabase db, String tableName, String columnName) {
        Cursor cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null);
        while (cursor.moveToNext()) {
            if (columnName.equals(cursor.getString(cursor.getColumnIndexOrThrow("name")))) {
                cursor.close();
                return true;
            }
        }
        cursor.close();
        return false;
    }

    private String getSchemaSql(SQLiteDatabase db, String type, String name) {
        Cursor cursor = db.rawQuery("SELECT sql FROM sqlite_master WHERE type=? AND name=?", new String[]{type, name});
        assertTrue("Missing schema object " + name, cursor.moveToFirst());
        String sql = cursor.getString(0);
        cursor.close();
        return sql;
    }

    private String querySingleString(SQLiteDatabase db, String sql) {
        Cursor cursor = db.rawQuery(sql, null);
        assertTrue(cursor.moveToFirst());
        String value = cursor.getString(0);
        cursor.close();
        return value;
    }

    private int getWritableSchema(SQLiteDatabase db) {
        Cursor cursor = db.rawQuery("PRAGMA writable_schema", null);
        cursor.moveToFirst();
        int enabled = cursor.getInt(0);
        cursor.close();
        return enabled;
    }

    private int foreignKeyViolationCount(SQLiteDatabase db) {
        Cursor cursor = db.rawQuery("PRAGMA foreign_key_check", null);
        int count = cursor.getCount();
        cursor.close();
        return count;
    }

    private static class VideoOpenHelper extends com.archos.mediaprovider.video.VideoOpenHelper {
        public VideoOpenHelper(Context context, String name, int version) {
            super(context, name, version);
        }
    }
}
