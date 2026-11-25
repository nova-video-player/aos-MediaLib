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
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.UriMatcher;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.archos.mediaprovider.DbHolder;
import com.archos.mediascraper.ShowTags;
import com.archos.mediascraper.themoviedb3.SearchShowResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Calendar;

/**
 * The scrape-from-DB shortcut must not hand an episode of one show to a file of another show
 * sharing the same title (Avatar: The Last Airbender 2005 animated vs 2024 live action).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class EpisodeDbReuseTest {

    private static final String DB_NAME = "episode-db-reuse-test.db";
    private static final String QUERY = "Avatar The Last Airbender";

    private Application application;
    private DbHolder dbHolder;
    private Context context;

    @Before
    public void setUp() {
        application = ApplicationProvider.getApplicationContext();
        application.deleteDatabase(DB_NAME);
        VideoOpenHelper helper = new VideoOpenHelper(application, DB_NAME,
                VideoOpenHelper.getDatabaseVersion());
        dbHolder = new DbHolder(helper);
        ScraperProvider.hookUriMatcher(new UriMatcher(UriMatcher.NO_MATCH));
        ScraperProvider provider = new ScraperProvider(application, dbHolder);
        ContentResolver resolver = mock(ContentResolver.class);
        when(resolver.query(any(Uri.class), any(String[].class), nullable(String.class),
                nullable(String[].class), nullable(String.class))).thenAnswer(invocation ->
                provider.query(invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(2), invocation.getArgument(3),
                        invocation.getArgument(4)));
        context = mock(Context.class);
        when(context.getContentResolver()).thenReturn(resolver);
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

    private void insertShow(long id, String name, long onlineId, int premieredYear) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(premieredYear, Calendar.FEBRUARY, 22);
        dbHolder.get().execSQL("INSERT INTO SHOW(_id,name_show,s_online_id,premiered_show) VALUES(?,?,?,?)",
                new Object[] { id, name, onlineId, cal.getTimeInMillis() });
    }

    private void insertEpisode(long id, long showId, String name) {
        dbHolder.get().execSQL("INSERT INTO files(_id,remote_id,_data) VALUES(?,?,?)",
                new Object[] { id, id, "episode-" + id + ".mkv" });
        dbHolder.get().execSQL("INSERT INTO EPISODE(_id,video_id,name_episode,season_episode,number_episode,show_episode) VALUES(?,?,?,2,1,?)",
                new Object[] { id, id, name, showId });
    }

    private SearchShowResult lookup(String year) {
        return ShowTags.getEpisodeResultIfAlreadyKnown(context, QUERY, "2", "1", year,
                Uri.parse("file:///volume1/video/serie/episode.mkv"));
    }

    @Test
    public void yearSelectsTheShowPremieredThatYear() {
        insertShow(1, "Avatar: The Last Airbender", 82452, 2024);
        insertEpisode(11, 1, "Somewhere Safe");
        insertShow(2, "Avatar: The Last Airbender 2005", 246, 2005);
        insertEpisode(21, 2, "The Avatar State");

        SearchShowResult remake = lookup("2024");
        assertNotNull(remake);
        assertEquals(11, remake.result.get(0).getId());
        SearchShowResult animated = lookup("2005");
        assertNotNull(animated);
        assertEquals(21, animated.result.get(0).getId());
    }

    @Test
    public void yearOfAnotherShowIsNotReused() {
        insertShow(1, "Avatar: The Last Airbender", 82452, 2024);
        insertEpisode(11, 1, "Somewhere Safe");

        assertNull(lookup("2005"));
        assertNotNull(lookup("2024"));
    }

    @Test
    public void noYearWithSeveralShowsSearchesOnline() {
        insertShow(1, "Avatar: The Last Airbender", 82452, 2024);
        insertEpisode(11, 1, "Somewhere Safe");
        insertShow(2, "Avatar: The Last Airbender 2005", 246, 2005);
        insertEpisode(21, 2, "The Avatar State");

        assertNull(lookup(null));
    }

    @Test
    public void noYearWithSingleShowIsReused() {
        insertShow(1, "Avatar: The Last Airbender", 82452, 2024);
        insertEpisode(11, 1, "Somewhere Safe");

        SearchShowResult result = lookup(null);
        assertNotNull(result);
        assertEquals(11, result.result.get(0).getId());
    }
}
