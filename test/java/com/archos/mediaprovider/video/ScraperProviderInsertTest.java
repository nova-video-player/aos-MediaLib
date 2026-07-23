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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import android.app.Application;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.content.UriMatcher;

import androidx.test.core.app.ApplicationProvider;

import com.archos.mediaprovider.DbHolder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ScraperProviderInsertTest {

    private static final String DB_NAME = "scraper-provider-insert-test.db";

    private Application application;
    private DbHolder dbHolder;
    private ScraperProvider provider;

    @Before
    public void setUp() {
        application = ApplicationProvider.getApplicationContext();
        application.deleteDatabase(DB_NAME);
        VideoOpenHelper helper = new VideoOpenHelper(application, DB_NAME,
                VideoOpenHelper.getDatabaseVersion());
        dbHolder = new DbHolder(helper);
        ScraperProvider.hookUriMatcher(new UriMatcher(UriMatcher.NO_MATCH));
        provider = new ScraperProvider(application, dbHolder);
    }

    @After
    public void tearDown() {
        if (dbHolder != null) {
            dbHolder.close();
        }
        if (application != null) {
            application.deleteDatabase(DB_NAME);
        }
    }

    @Test
    public void duplicateCollectionInsertReusesCollectionId() {
        ContentValues values = new ContentValues();
        values.put(ScraperStore.MovieCollections.ID, 1234L);
        values.put(ScraperStore.MovieCollections.NAME, "Collection");

        Uri first = provider.insert(ScraperStore.MovieCollections.URI.BASE, values);
        Uri second = provider.insert(ScraperStore.MovieCollections.URI.BASE, values);

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(1234L, ContentUris.parseId(first));
        assertEquals(1234L, ContentUris.parseId(second));
        assertEquals(1L, DatabaseUtils.queryNumEntries(dbHolder.get(),
                ScraperTables.MOVIE_COLLECTION_TABLE_NAME));
    }

    @Test
    public void duplicateShowInsertIsIgnoredForCallerRecovery() {
        ContentValues values = new ContentValues();
        values.put(ScraperStore.Show.NAME, "Concurrent show");
        values.put(ScraperStore.Show.ONLINE_ID, 5678L);

        Uri first = provider.insert(ScraperStore.Show.URI.BASE, values);
        Uri second = provider.insert(ScraperStore.Show.URI.BASE, values);

        assertNotNull(first);
        assertNull(second);
        assertEquals(1L, DatabaseUtils.queryNumEntries(dbHolder.get(),
                ScraperTables.SHOW_TABLE_NAME));
    }

    @Test
    public void artworkConflictLookupIsScopedToItsOwner() {
        dbHolder.get().execSQL("INSERT INTO files(_id,remote_id,_data) VALUES(1,1,'movie-1.mkv')");
        dbHolder.get().execSQL("INSERT INTO files(_id,remote_id,_data) VALUES(2,2,'movie-2.mkv')");
        dbHolder.get().execSQL("INSERT INTO MOVIE(_id,video_id,name_movie) VALUES(101,1,'Movie 1')");
        dbHolder.get().execSQL("INSERT INTO MOVIE(_id,video_id,name_movie) VALUES(102,2,'Movie 2')");

        ContentValues firstOwner = new ContentValues();
        firstOwner.put(ScraperStore.MoviePosters.MOVIE_ID, 101L);
        firstOwner.put(ScraperStore.MoviePosters.THUMB_FILE, "shared-thumb.jpg");
        firstOwner.put(ScraperStore.MoviePosters.LARGE_FILE, "shared-large.jpg");

        Uri first = provider.insert(ScraperStore.MoviePosters.URI.BASE, firstOwner);
        Uri duplicate = provider.insert(ScraperStore.MoviePosters.URI.BASE, firstOwner);

        ContentValues secondOwner = new ContentValues(firstOwner);
        secondOwner.put(ScraperStore.MoviePosters.MOVIE_ID, 102L);
        Uri sharedBySecondOwner =
                provider.insert(ScraperStore.MoviePosters.URI.BASE, secondOwner);

        assertNotNull(first);
        assertNotNull(duplicate);
        assertNotNull(sharedBySecondOwner);
        assertEquals(ContentUris.parseId(first), ContentUris.parseId(duplicate));
        assertNotEquals(ContentUris.parseId(first), ContentUris.parseId(sharedBySecondOwner));
        assertEquals(2L, DatabaseUtils.queryNumEntries(dbHolder.get(),
                ScraperTables.MOVIE_POSTERS_TABLE_NAME));
    }
}
