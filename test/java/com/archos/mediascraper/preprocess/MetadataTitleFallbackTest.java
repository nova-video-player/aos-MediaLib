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

package com.archos.mediascraper.preprocess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Covers issue #1987: the scrape identity must come from the real file name/path (release name)
 * rather than the stored metadata title, which may be a localized on-screen title. The metadata
 * title is only a fallback for cryptic file names and for UPNP/content URIs without a real name.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class MetadataTitleFallbackTest {

    private static final String RELEASE_FILE =
            "file:///storage/emulated/0/Download/Ted.Lasso.S04E01.MULTi.1080p.WEB.H265-GL0P-small.mkv";

    @Test
    public void releaseFileNameWinsOverEpisodeMetadataTitle() {
        SearchInfo info = SearchPreprocessor.instance()
                .parseFileBasedWithTitleFallback(Uri.parse(RELEASE_FILE), "Chez soi");
        assertTrue("release file name should identify a TV show", info.isTvShow());
        TvShowSearchInfo tv = (TvShowSearchInfo) info;
        assertEquals("Ted Lasso", tv.getShowName());
        assertEquals(4, tv.getSeason());
        assertEquals(1, tv.getEpisode());
    }

    @Test
    public void folderShowIdentificationHandlesCrypticFileName() {
        Uri uri = Uri.parse("file:///storage/emulated/0/Download/Ted.Lasso.S04E01.MULTi.1080p.WEB.H265-GL0P/a1b2c3d4.mkv");
        SearchInfo info = SearchPreprocessor.instance().parseFileBasedWithTitleFallback(uri, "Chez soi");
        assertTrue("release folder name should identify the TV show", info.isTvShow());
        TvShowSearchInfo tv = (TvShowSearchInfo) info;
        assertEquals("Ted Lasso", tv.getShowName());
        assertEquals(4, tv.getSeason());
        assertEquals(1, tv.getEpisode());
    }

    @Test
    public void crypticFileNameFallsBackToMetadataTitle() {
        Uri uri = Uri.parse("file:///storage/emulated/0/Download/a1b2c3d4.mkv");
        SearchInfo info = SearchPreprocessor.instance().parseFileBasedWithTitleFallback(uri, "Ted Lasso S04E01");
        assertTrue("metadata title should be used when the file name is cryptic", info.isTvShow());
        TvShowSearchInfo tv = (TvShowSearchInfo) info;
        assertEquals("Ted Lasso", tv.getShowName());
        assertEquals(4, tv.getSeason());
        assertEquals(1, tv.getEpisode());
    }

    @Test
    public void opaqueContentUriUsesMetadataTitle() {
        Uri uri = Uri.parse("content://media/external/video/media/123");
        SearchInfo info = SearchPreprocessor.instance().parseFileBasedWithTitleFallback(uri, "Ted Lasso S04E01");
        assertTrue("content URIs have no real file name and must use the metadata title", info.isTvShow());
        TvShowSearchInfo tv = (TvShowSearchInfo) info;
        assertEquals("Ted Lasso", tv.getShowName());
        assertEquals(4, tv.getSeason());
        assertEquals(1, tv.getEpisode());
    }

    @Test
    public void fallbackFlagReflectsCatchAllMatcher() {
        SearchInfo release = SearchPreprocessor.instance().parseFileBased(Uri.parse(RELEASE_FILE), null);
        assertFalse("a structured release name is not a fallback match", release.isFallbackMatch());

        SearchInfo cryptic = SearchPreprocessor.instance()
                .parseFileBased(Uri.parse("file:///storage/emulated/0/Download/a1b2c3d4.mkv"), null);
        assertTrue("a cryptic file name reaches the catch-all matcher", cryptic.isFallbackMatch());
    }

    @Test
    public void readableMovieNamesWinEvenWithDefaultMatcher() {
        for (String name : new String[] {"Arrival", "Arrival (2016)", "1917", "Amélie", "THX1138"}) {
            Uri uri = Uri.parse("file:///Movies/" + name + ".mkv");
            SearchInfo expected = SearchPreprocessor.instance().parseFileBased(uri, null);
            SearchInfo actual = SearchPreprocessor.instance()
                    .parseFileBasedWithTitleFallback(uri, "Ted Lasso S04E01");
            assertFalse(name, actual.isTvShow());
            assertEquals(name, expected.getSearchSuggestion(), actual.getSearchSuggestion());
        }
    }

    @Test
    public void structuredMovieAndDvdFoldersWinOverMetadata() {
        for (String path : new String[] {"Arrival.2016.1080p.mkv",
                "Arrival (2016)/a1b2c3d4.mkv", "Arrival (2016)/VIDEO_TS/VTS_01_1.VOB"}) {
            Uri uri = Uri.parse("smb://server/Movies/" + path);
            SearchInfo info = SearchPreprocessor.instance()
                    .parseFileBasedWithTitleFallback(uri, "Ted Lasso S04E01");
            assertFalse(path, info.isTvShow());
            assertEquals(path, "Arrival", ((MovieSearchInfo) info).getName());
            assertEquals(path, "2016", ((MovieSearchInfo) info).getYear());
        }
    }

    @Test
    public void seasonAndEpisodePathWinsOverMetadata() {
        SearchInfo info = SearchPreprocessor.instance().parseFileBasedWithTitleFallback(
                Uri.parse("smb://server/Shows/Ted Lasso/Season 4/Episode 1.mkv"), "Chez soi");
        assertTrue(info.isTvShow());
        assertEquals("Ted Lasso", ((TvShowSearchInfo) info).getShowName());
        assertEquals(4, ((TvShowSearchInfo) info).getSeason());
        assertEquals(1, ((TvShowSearchInfo) info).getEpisode());
    }

    @Test
    public void hashAndUuidNamesUseMovieMetadata() {
        for (String name : new String[] {"a1b2c3d4", "abcdefabcdefabcdef",
                "550e8400-e29b-41d4-a716-446655440000"}) {
            Uri uri = Uri.parse("file:///Movies/" + name + ".mkv");
            SearchInfo info = SearchPreprocessor.instance()
                    .parseFileBasedWithTitleFallback(uri, "Arrival (2016)");
            assertEquals(name, "Arrival", ((MovieSearchInfo) info).getName());
            assertEquals(name, "2016", ((MovieSearchInfo) info).getYear());
            assertEquals(uri, info.getFile());
            assertEquals(uri, info.getOriginalUri());
        }
    }

    @Test
    public void uuidEpisodeLikeFragmentsDoNotOverrideReleaseFolder() {
        Uri uri = Uri.parse("file:///Shows/Ted.Lasso.S04E01/550e8400-e29b-41d4-a716-446655440000.mkv");
        SearchInfo info = SearchPreprocessor.instance().parseFileBasedWithTitleFallback(uri, "Arrival");
        assertTrue(info.isTvShow());
        assertEquals("Ted Lasso", ((TvShowSearchInfo) info).getShowName());
        assertEquals(1, ((TvShowSearchInfo) info).getEpisode());
    }

    @Test
    public void contentUuidEpisodeLikeFragmentsUseMetadata() {
        Uri uri = Uri.parse("content://provider/video/550e8400-e29b-41d4-a716-446655440000.mkv");
        SearchInfo info = SearchPreprocessor.instance().parseFileBasedWithTitleFallback(uri, "Arrival");
        assertFalse(info.isTvShow());
        assertEquals("Arrival", info.getSearchSuggestion());
        assertEquals(uri, info.getFile());
    }

    @Test
    public void missingAndSentinelTitlesKeepFilename() {
        for (String title : new String[] {null, "", "  \t", "null", " NULL "}) {
            Uri uri = Uri.parse("file:///Movies/a1b2c3d4.mkv");
            SearchInfo info = SearchPreprocessor.instance().parseFileBasedWithTitleFallback(uri, title);
            assertEquals("a1b2c3d4", info.getSearchSuggestion());
            assertEquals(uri, info.getFile());
        }
    }

    @Test
    public void titlePunctuationIsTextAndCannotCreateExtrasFolder() {
        Uri uri = Uri.parse("file:///Movies/a1b2c3d4.mkv");
        for (String title : new String[] {"Who? Am I", "Bonus/Arrival", "Film #1", "100% Amélie"}) {
            SearchInfo expected = MovieDefaultMatcher.instance().getUserInputMatch(title, uri);
            SearchInfo info = SearchPreprocessor.instance().parseFileBasedWithTitleFallback(uri, title);
            assertEquals(title, expected.getSearchSuggestion(), info.getSearchSuggestion());
            assertFalse(title, info.skipScraping);
            assertEquals(uri, info.getFile());
        }
    }

    @Test
    public void contentFilenameWinsOverMetadata() {
        for (String path : new String[] {
                "content://provider/videos/Arrival.mkv",
                "content://com.android.externalstorage.documents/document/primary%3AMovies%2FArrival.mkv"}) {
            Uri uri = Uri.parse(path);
            SearchInfo info = SearchPreprocessor.instance()
                    .parseFileBasedWithTitleFallback(uri, "Ted Lasso S04E01");
            assertFalse(info.isTvShow());
            assertEquals("Arrival", info.getSearchSuggestion());
            assertEquals(uri, info.getFile());
        }
    }

    @Test
    public void contentEpisodeFilenameWinsOverMetadata() {
        Uri uri = Uri.parse("content://com.android.externalstorage.documents/document/"
                + "primary%3ADownload%2FTed.Lasso.S04E01.mkv");
        SearchInfo info = SearchPreprocessor.instance().parseFileBasedWithTitleFallback(uri, "Chez soi");
        assertTrue(info.isTvShow());
        assertEquals("Ted Lasso", ((TvShowSearchInfo) info).getShowName());
        assertEquals(uri, info.getFile());
    }

    @Test
    public void opaqueUrisKeepRealFileAndOriginalUriWhenReparsed() {
        for (String path : new String[] {"content://media/external/video/media/123",
                "upnp://server/123", "UPNP-AV://server/123"}) {
            Uri uri = Uri.parse(path);
            SearchInfo info = SearchPreprocessor.instance()
                    .parseFileBasedWithTitleFallback(uri, " Ted Lasso S04E01 ");
            assertTrue(info.isTvShow());
            info.setForceReParse(true);
            info = SearchPreprocessor.instance().reParseInfo(info);
            assertTrue(info.isTvShow());
            assertEquals(uri, info.getFile());
            assertEquals(uri, info.getOriginalUri());
        }
    }

    @Test
    public void opaqueUriWithoutTitleStillReturnsBestEffort() {
        Uri uri = Uri.parse("content://media/external/video/media/123");
        SearchInfo info = SearchPreprocessor.instance().parseFileBasedWithTitleFallback(uri, null);
        assertEquals("123", info.getSearchSuggestion());
        assertEquals(uri, info.getFile());
    }

    @Test
    public void extrasExclusionSurvivesTitleFallbackAndReparse() {
        Uri uri = Uri.parse("file:///Movies/Extras/a1b2c3d4.mkv");
        SearchInfo info = SearchPreprocessor.instance()
                .parseFileBasedWithTitleFallback(uri, "Ted Lasso S04E01");
        assertTrue(info.skipScraping);
        info.setUserInput("Arrival");
        info = SearchPreprocessor.instance().reParseInfo(info);
        assertTrue(info.skipScraping);
        assertEquals(uri, info.getFile());
        assertEquals(uri, info.getOriginalUri());
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullFileIsRejectedEvenWithTitle() {
        SearchPreprocessor.instance().parseFileBasedWithTitleFallback(null, "Arrival");
    }
}
