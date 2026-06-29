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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class AutoScrapeServicePersistenceTest {

    private Thread claimedWorker;

    @After
    public void tearDown() {
        if (claimedWorker != null) {
            AutoScrapeService.releaseScrapeWorker(claimedWorker);
        }
    }

    @Test
    public void onlyOneScrapeWorkerCanOwnPersistence() {
        Thread first = new Thread();
        Thread second = new Thread();

        assertTrue(AutoScrapeService.claimScrapeWorker(first));
        claimedWorker = first;
        assertFalse(AutoScrapeService.claimScrapeWorker(second));

        AutoScrapeService.releaseScrapeWorker(first);
        claimedWorker = null;
        assertTrue(AutoScrapeService.claimScrapeWorker(second));
        claimedWorker = second;
    }

    @Test
    public void interruptedWorkerRetainsOwnershipUntilItExits() {
        Thread first = new Thread();
        Thread second = new Thread();

        assertTrue(AutoScrapeService.claimScrapeWorker(first));
        claimedWorker = first;
        first.interrupt();

        assertFalse(AutoScrapeService.claimScrapeWorker(second));
        AutoScrapeService.releaseScrapeWorker(first);
        claimedWorker = null;
        assertTrue(AutoScrapeService.claimScrapeWorker(second));
        claimedWorker = second;
    }

    @Test
    public void databaseSaveFailureRemainsRetryable() {
        assertFalse(AutoScrapeService.shouldMarkAsNotFound(true, true, false));
    }

    @Test
    public void metadataMissIsMarkedAsNotFound() {
        assertTrue(AutoScrapeService.shouldMarkAsNotFound(false, true, true));
    }

    @Test
    public void persistenceFailureRetriesUntilTheHardRoundLimit() {
        assertTrue(AutoScrapeService.shouldRunAnotherScrapeRound(1, true));
        assertTrue(AutoScrapeService.shouldRunAnotherScrapeRound(2, true));
        assertTrue(AutoScrapeService.shouldRunAnotherScrapeRound(3, true));
        assertFalse(AutoScrapeService.shouldRunAnotherScrapeRound(4, true));
    }

    @Test
    public void completedRoundDoesNotRestartWithoutARequest() {
        assertFalse(AutoScrapeService.shouldRunAnotherScrapeRound(1, false));
    }

    @Test
    public void nullAndEmptyPosterListsBothMeanNoArtwork() {
        MovieTags nullPosters = new MovieTags();
        MovieTags emptyPosters = new MovieTags();
        emptyPosters.setPosters(Collections.emptyList());

        assertTrue(AutoScrapeService.hasNoUsableNfoPoster(nullPosters));
        assertTrue(AutoScrapeService.hasNoUsableNfoPoster(emptyPosters));
    }

    @Test
    public void episodeWithoutPosterUsesOriginalFileUri() {
        EpisodeTags tags = new EpisodeTags();
        ShowTags showTags = new ShowTags();
        showTags.setTitle("Example Show");
        tags.setShowTags(showTags);
        tags.setTitle("Pilot");
        Uri fileUri = Uri.parse("smb://server/Example.Show.S01E01.mkv");

        Uri result = AutoScrapeService.getMissingNfoPosterScrapeUri(tags, fileUri,
                Uri.parse("/Pilot.mp4"));

        assertEquals(fileUri, result);
    }

    @Test
    public void movieWithoutPosterUsesNfoTitle() {
        MovieTags tags = new MovieTags();
        tags.setTitle("Curated Title");

        Uri result = AutoScrapeService.getMissingNfoPosterScrapeUri(tags,
                Uri.parse("smb://server/file.mkv"), Uri.parse("/old.mp4"));

        assertEquals(Uri.parse("/Curated Title.mp4"), result);
    }
}
