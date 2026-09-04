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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Covers the "Bonus"/"Extras" folder-segment heuristic (see MediaLib/doc/scraper_improvements.md):
 * files filed in a directory named exactly one of these should be skipped before any TMDb/TVDb
 * query, since they essentially never resolve to a correct match.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class NonScrapableFolderTest {

    @Test
    public void bonusFolder_isNonScrapable() {
        assertTrue(ParseUtils.isNonScrapableFolder(Uri.parse(
                "file:///volume1/video/serie-fr/Kaamelott_(2005)/Livre I/Bonus/Pilote 07 - La Carte.mkv")));
    }

    @Test
    public void extrasFolder_isNonScrapable() {
        assertTrue(ParseUtils.isNonScrapableFolder(Uri.parse(
                "file:///volume1/video/hd/Some Movie (2015)/Extras/Deleted Scene 1.mkv")));
    }

    @Test
    public void bonusFolder_caseInsensitiveAndUnderscored() {
        assertTrue(ParseUtils.isNonScrapableFolder(Uri.parse(
                "file:///volume1/video/hd/BONUS/foo.mkv")));
        assertTrue(ParseUtils.isNonScrapableFolder(Uri.parse(
                "file:///volume1/video/hd/Bonus_Features/foo.mkv")));
    }

    @Test
    public void regularMovieFile_isNotAffected() {
        assertFalse(ParseUtils.isNonScrapableFolder(Uri.parse(
                "file:///volume1/video/hd/Inception (2010) 1080p BluRay x264.mkv")));
    }

    @Test
    public void movieTitledBonus_isNotAffected() {
        // The filename itself is never checked, only ancestor folders - so a real movie called
        // "Bonus" living directly in a normal folder must not be skipped.
        assertFalse(ParseUtils.isNonScrapableFolder(Uri.parse(
                "file:///volume1/video/hd/Bonus (1964).mkv")));
    }

    @Test
    public void specialsFolder_isNotAffected() {
        // "Specials"/season-0 is legitimate TV structure, must never be treated as a bonus folder.
        assertFalse(ParseUtils.isNonScrapableFolder(Uri.parse(
                "file:///volume1/video/serie/Doctor_Who_(1963)/Specials/03 Bonus - Over the Edge.mkv")));
    }

    @Test
    public void substringFolderName_isNotAffected() {
        // Whole-segment match only: a folder just containing "bonus" as part of its name (not equal
        // to it) must not trigger the skip.
        assertFalse(ParseUtils.isNonScrapableFolder(Uri.parse(
                "file:///volume1/video/hd/Bonusville (2020)/foo.mkv")));
    }

    @Test
    public void searchPreprocessor_marksSkipScrapingForBonusFolder() {
        Uri uri = Uri.parse("file:///volume1/video/serie-fr/Kaamelott_(2005)/Livre I/Bonus/Pilote 07 - La Carte.mkv");
        SearchInfo info = SearchPreprocessor.instance().parseFileBased(uri, uri);
        assertTrue("expected skipScraping=true for a file in a Bonus folder", info.skipScraping);
    }

    @Test
    public void searchPreprocessor_doesNotMarkSkipScrapingForRegularFile() {
        Uri uri = Uri.parse("file:///volume1/video/hd/Inception (2010) 1080p BluRay x264.mkv");
        SearchInfo info = SearchPreprocessor.instance().parseFileBased(uri, uri);
        assertFalse("expected skipScraping=false for a regular movie file", info.skipScraping);
    }
}
