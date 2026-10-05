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

package com.archos.mediascraper;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.archos.mediaprovider.video.VideoStore;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "en")
public class GenreUtilsTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void formatAndParseRoundTrip() {
        assertEquals(",16,10751,", GenreUtils.formatGenreIds(Arrays.asList(16, 10751)));
        assertEquals(Arrays.asList(16, 10751), GenreUtils.parseGenreIds(",16,10751,"));
        assertNull(GenreUtils.formatGenreIds(Collections.<Integer>emptyList()));
        assertNull(GenreUtils.formatGenreIds(null));
        assertTrue(GenreUtils.parseGenreIds(null).isEmpty());
        assertTrue(GenreUtils.parseGenreIds("").isEmpty());
    }

    @Test
    public void parseIgnoresMalformedTokens() {
        assertEquals(Arrays.asList(16, 28), GenreUtils.parseGenreIds(",16,abc,28,"));
    }

    @Test
    public void reverseMapResolvesLocalizedNames() {
        assertEquals(16, GenreUtils.getGenreIdFromName(context, "Animazione", false));
        assertEquals(28, GenreUtils.getGenreIdFromName(context, "Azione", false));
        assertEquals(28, GenreUtils.getGenreIdFromName(context, "Action", false));
        assertEquals(16, GenreUtils.getGenreIdFromName(context, "Animazione", true));
        assertEquals(10759, GenreUtils.getGenreIdFromName(context, "Action & Adventure", true));
        assertEquals(-1, GenreUtils.getGenreIdFromName(context, "NotAGenre", false));
        assertEquals(-1, GenreUtils.getGenreIdFromName(context, null, false));
    }

    @Test
    public void genreIdsFromNamesMapsLegacyLocalizedList() {
        assertEquals(",16,28,", GenreUtils.genreIdsFromNames(context, "Animazione, Azione", false));
        assertEquals(",16,", GenreUtils.genreIdsFromNames(context, "Animazione", true));
        assertNull(GenreUtils.genreIdsFromNames(context, "Foo, Bar", false));
        assertNull(GenreUtils.genreIdsFromNames(context, null, false));
    }

    @Test
    public void displayNameTranslatesToCurrentLocale() {
        assertEquals("Animation", GenreUtils.getGenreDisplayName(context, "Animazione", false));
        assertEquals("Action", GenreUtils.getGenreDisplayName(context, "Azione", false));
        assertEquals("Animation", GenreUtils.getGenreDisplayName(context, "Animazione", true));
        assertEquals("1984", GenreUtils.getGenreDisplayName(context, "1984", false));
        assertNull(GenreUtils.getGenreDisplayName(context, null, false));
    }

    @Test
    public void formatGenreNamesUsesStoredIds() {
        assertEquals("Animation, Action", GenreUtils.formatGenreNames(context, ",16,28,", false));
        assertNull(GenreUtils.formatGenreNames(context, null, false));
    }

    @Test
    public void animeSelectionUsesIdsWithLegacyNameFallback() {
        String include = GenreUtils.getAnimeSelection(context, false, true);
        assertTrue(include.contains(VideoStore.Video.VideoColumns.SCRAPER_M_GENRE_IDS + " LIKE '%,16,%'"));
        assertTrue(include.contains(VideoStore.Video.VideoColumns.SCRAPER_M_GENRES + " LIKE '%Animation%'"));

        String exclude = GenreUtils.getAnimeSelection(context, true, false);
        assertTrue(exclude.contains(VideoStore.Video.VideoColumns.SCRAPER_S_GENRE_IDS + " NOT LIKE '%,16,%'"));
        assertTrue(exclude.contains(VideoStore.Video.VideoColumns.SCRAPER_S_GENRES + " NOT LIKE '%Animation%'"));
    }

    @Test
    public void genreNameExclusionListsEveryLocalizedVariant() {
        String sql = GenreUtils.getGenreNameExclusion(context, GenreUtils.ANIMATION_GENRE_ID, false, "genre.name_genre");
        assertTrue(sql.startsWith("genre.name_genre NOT IN ("));
        assertTrue(sql.contains("'Animation'"));
        assertTrue(sql.contains("'Animazione'"));
    }
}
