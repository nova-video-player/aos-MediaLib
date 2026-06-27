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

    private int getWritableSchema(SQLiteDatabase db) {
        Cursor cursor = db.rawQuery("PRAGMA writable_schema", null);
        cursor.moveToFirst();
        int enabled = cursor.getInt(0);
        cursor.close();
        return enabled;
    }

    private static class VideoOpenHelper extends com.archos.mediaprovider.video.VideoOpenHelper {
        public VideoOpenHelper(Context context, String name, int version) {
            super(context, name, version);
        }
    }
}
