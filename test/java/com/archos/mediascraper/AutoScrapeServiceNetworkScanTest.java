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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Intent;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Shadows;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class AutoScrapeServiceNetworkScanTest {

    private static final long STANDALONE = AutoScrapeService.STANDALONE_SCAN_BATCH_ID;
    private Application application;
    private ShadowApplication shadowApplication;

    @Before
    public void setUp() {
        AutoScrapeService.resetNetworkScanCount();
        application = ApplicationProvider.getApplicationContext();
        shadowApplication = Shadows.shadowOf(application);
        drainStartedServices();
        PreferenceManager.getDefaultSharedPreferences(application).edit()
                .putBoolean(AutoScrapeService.KEY_ENABLE_AUTO_SCRAP, true)
                .commit();
    }

    @After
    public void tearDown() {
        AutoScrapeService.resetNetworkScanCount();
        drainStartedServices();
    }

    private void drainStartedServices() {
        while (shadowApplication.getNextStartedService() != null) {
            // Drain Robolectric's service-start queue between tests.
        }
    }

    @Test
    public void completionHandlerStartsForcedScrapeForCleanSuccessfulOwner() {
        AutoScrapeService.NetworkScanCompletion completion =
                new AutoScrapeService.NetworkScanCompletion(true, false, true);

        AutoScrapeService.handleNetworkScanCompletion(application, completion);

        Intent started = shadowApplication.getNextStartedService();
        assertNotNull(started);
        assertEquals(AutoScrapeService.class.getName(), started.getComponent().getClassName());
        assertTrue(started.getBooleanExtra("FORCE_AFTER_NETWORK_SCAN", false));
        assertNull(shadowApplication.getNextStartedService());
    }

    @Test
    public void completionHandlerDoesNotStartScrapeForIneligibleOutcomes() {
        AutoScrapeService.handleNetworkScanCompletion(application,
                new AutoScrapeService.NetworkScanCompletion(false, false, true));
        AutoScrapeService.handleNetworkScanCompletion(application,
                new AutoScrapeService.NetworkScanCompletion(true, true, true));
        AutoScrapeService.handleNetworkScanCompletion(application,
                new AutoScrapeService.NetworkScanCompletion(true, false, false));

        assertNull(shadowApplication.getNextStartedService());
    }

    @Test
    public void standaloneScanCompletesImmediately() {
        AutoScrapeService.NetworkScanCompletion completion =
                AutoScrapeService.completeNetworkScan(STANDALONE, false, true);

        assertTrue(completion.completedBatch);
        assertFalse(completion.batchHadError);
        assertTrue(completion.batchHadSuccess);
        assertEquals(0, AutoScrapeService.getNetworkScanCount());
    }

    @Test
    public void standaloneErrorDoesNotLeakIntoNextScan() {
        AutoScrapeService.NetworkScanCompletion failed =
                AutoScrapeService.completeNetworkScan(STANDALONE, true, false);
        AutoScrapeService.NetworkScanCompletion next =
                AutoScrapeService.completeNetworkScan(STANDALONE, false, true);

        assertTrue(failed.completedBatch);
        assertTrue(failed.batchHadError);
        assertFalse(failed.batchHadSuccess);
        assertTrue(next.completedBatch);
        assertFalse(next.batchHadError);
        assertTrue(next.batchHadSuccess);
    }

    @Test
    public void batchCompletesOnlyAfterLastScanAndAggregatesErrors() {
        long batchId = AutoScrapeService.startNetworkScanBatch(2);

        AutoScrapeService.NetworkScanCompletion first =
                AutoScrapeService.completeNetworkScan(batchId, true, false);
        AutoScrapeService.NetworkScanCompletion last =
                AutoScrapeService.completeNetworkScan(batchId, false, true);

        assertFalse(first.completedBatch);
        assertFalse(first.batchHadError);
        assertTrue(last.completedBatch);
        assertTrue(last.batchHadError);
        assertEquals(0, AutoScrapeService.getNetworkScanCount());
    }

    @Test
    public void resetClearsCountAndPendingBatchError() {
        long batchId = AutoScrapeService.startNetworkScanBatch(2);
        AutoScrapeService.completeNetworkScan(batchId, true, false);

        AutoScrapeService.resetNetworkScanCount();
        long nextBatchId = AutoScrapeService.startNetworkScanBatch(1);
        AutoScrapeService.NetworkScanCompletion completion =
                AutoScrapeService.completeNetworkScan(nextBatchId, false, true);

        assertTrue(completion.completedBatch);
        assertFalse(completion.batchHadError);
        assertTrue(completion.batchHadSuccess);
        assertEquals(0, AutoScrapeService.getNetworkScanCount());
    }

    /**
     * The batch membership is registered atomically at batch start, so the count reflects
     * the whole batch before any member reports back.
     */
    @Test
    public void startRegistersAllMembersAtomically() {
        long batchId = AutoScrapeService.startNetworkScanBatch(3);
        assertEquals(3, AutoScrapeService.getNetworkScanCount());

        assertFalse(AutoScrapeService.completeNetworkScan(batchId, false, true).completedBatch);
        assertFalse(AutoScrapeService.completeNetworkScan(batchId, false, true).completedBatch);
        assertTrue(AutoScrapeService.completeNetworkScan(batchId, false, true).completedBatch);
        assertEquals(0, AutoScrapeService.getNetworkScanCount());
    }

    /**
     * Finding 1: an overlapping auto-refresh must not reset a still-active batch. Starting a
     * batch while one is in progress is rejected (sentinel id) and leaves the active count intact.
     */
    @Test
    public void overlappingBatchIsRejectedAndDoesNotResetActiveBatch() {
        long firstBatch = AutoScrapeService.startNetworkScanBatch(2);
        assertNotEquals(STANDALONE, firstBatch);
        assertEquals(2, AutoScrapeService.getNetworkScanCount());

        // a second refresh while the first batch is still active must be rejected
        long rejected = AutoScrapeService.startNetworkScanBatch(3);
        assertEquals(STANDALONE, rejected);
        // the active batch is untouched
        assertEquals(2, AutoScrapeService.getNetworkScanCount());

        // the first batch still completes normally via its own two members
        assertFalse(AutoScrapeService.completeNetworkScan(firstBatch, false, true).completedBatch);
        assertTrue(AutoScrapeService.completeNetworkScan(firstBatch, false, true).completedBatch);
        assertEquals(0, AutoScrapeService.getNetworkScanCount());
    }

    /**
     * Finding 2: a member whose scan request is rejected (e.g. a duplicate already queued)
     * releases its slot via completeNetworkScan, so the batch still reaches zero and the
     * earlier members' success is preserved.
     */
    @Test
    public void rejectedDuplicateMemberReleasesBatchSlot() {
        long batchId = AutoScrapeService.startNetworkScanBatch(2);

        // first member scans and indexes successfully
        AutoScrapeService.NetworkScanCompletion first =
                AutoScrapeService.completeNetworkScan(batchId, false, true);
        assertFalse(first.completedBatch);

        // second member is rejected as a duplicate and releases its slot (resolved=false)
        AutoScrapeService.NetworkScanCompletion released =
                AutoScrapeService.completeNetworkScan(batchId, false, false);

        assertTrue(released.completedBatch);
        assertFalse(released.batchHadError);
        assertTrue(released.batchHadSuccess);
        assertEquals(0, AutoScrapeService.getNetworkScanCount());
    }

    /**
     * Concern 4: a standalone scan that runs while a counted batch is active must complete
     * as its own one-off batch and must not decrement the active batch's count.
     */
    @Test
    public void standaloneCompletionWhileBatchActiveDoesNotTouchBatch() {
        long batchId = AutoScrapeService.startNetworkScanBatch(2);
        assertEquals(2, AutoScrapeService.getNetworkScanCount());

        AutoScrapeService.NetworkScanCompletion standalone =
                AutoScrapeService.completeNetworkScan(STANDALONE, false, true);

        // standalone completes itself but leaves the active batch untouched
        assertTrue(standalone.completedBatch);
        assertEquals(2, AutoScrapeService.getNetworkScanCount());

        // the batch still completes only after its own two members finish
        AutoScrapeService.NetworkScanCompletion first =
                AutoScrapeService.completeNetworkScan(batchId, false, true);
        AutoScrapeService.NetworkScanCompletion last =
                AutoScrapeService.completeNetworkScan(batchId, false, true);
        assertFalse(first.completedBatch);
        assertTrue(last.completedBatch);
        assertEquals(0, AutoScrapeService.getNetworkScanCount());
    }

    /**
     * Concern 2: the batch must report success when an earlier member resolved successfully
     * even if the final member to complete the batch did not resolve (e.g. unreachable server).
     */
    @Test
    public void unresolvedFinalMemberStillReportsBatchSuccess() {
        long batchId = AutoScrapeService.startNetworkScanBatch(2);

        // earlier member resolved and indexed successfully
        AutoScrapeService.NetworkScanCompletion first =
                AutoScrapeService.completeNetworkScan(batchId, false, true);
        // final member could not resolve (no error, but nothing indexed)
        AutoScrapeService.NetworkScanCompletion last =
                AutoScrapeService.completeNetworkScan(batchId, false, false);

        assertFalse(first.completedBatch);
        assertTrue(last.completedBatch);
        assertFalse(last.batchHadError);
        assertTrue(last.batchHadSuccess);
    }

    /**
     * Concern 4: error aggregation must be isolated per batch. An error in one batch must
     * not leak into a later, freshly started batch.
     */
    @Test
    public void errorAggregationIsolatedByBatchId() {
        long firstBatch = AutoScrapeService.startNetworkScanBatch(1);
        AutoScrapeService.NetworkScanCompletion firstDone =
                AutoScrapeService.completeNetworkScan(firstBatch, true, false);
        assertTrue(firstDone.completedBatch);
        assertTrue(firstDone.batchHadError);

        long secondBatch = AutoScrapeService.startNetworkScanBatch(1);
        assertNotEquals(firstBatch, secondBatch);
        AutoScrapeService.NetworkScanCompletion secondDone =
                AutoScrapeService.completeNetworkScan(secondBatch, false, true);

        assertTrue(secondDone.completedBatch);
        assertFalse(secondDone.batchHadError);
        assertTrue(secondDone.batchHadSuccess);
    }

    /**
     * Concern 4: a stale completion carrying an old batch id (e.g. a scan that outlived its
     * batch) must be ignored and must not decrement or complete the current batch.
     */
    @Test
    public void staleCompletionFromOlderBatchIsIgnored() {
        long oldBatch = AutoScrapeService.startNetworkScanBatch(1);
        // old batch is closed (e.g. via reset) before its scan reports back
        AutoScrapeService.resetNetworkScanCount();

        long newBatch = AutoScrapeService.startNetworkScanBatch(1);
        assertEquals(1, AutoScrapeService.getNetworkScanCount());

        // the stale completion from the old batch must not touch the new batch
        AutoScrapeService.NetworkScanCompletion stale =
                AutoScrapeService.completeNetworkScan(oldBatch, true, true);
        assertFalse(stale.completedBatch);
        assertFalse(stale.batchHadError);
        assertFalse(stale.batchHadSuccess);
        assertEquals(1, AutoScrapeService.getNetworkScanCount());

        // the new batch still completes cleanly via its own member, unaffected by the
        // stale error
        AutoScrapeService.NetworkScanCompletion newDone =
                AutoScrapeService.completeNetworkScan(newBatch, false, true);
        assertTrue(newDone.completedBatch);
        assertFalse(newDone.batchHadError);
        assertTrue(newDone.batchHadSuccess);
        assertEquals(0, AutoScrapeService.getNetworkScanCount());
    }

    @Test
    public void concurrentCompletionHasSingleOwnerAndAggregatesError() throws Exception {
        final int scanCount = 16;
        final long batchId = AutoScrapeService.startNetworkScanBatch(scanCount);

        ExecutorService executor = Executors.newFixedThreadPool(scanCount);
        CountDownLatch ready = new CountDownLatch(scanCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<AutoScrapeService.NetworkScanCompletion>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < scanCount; i++) {
                final boolean hadError = i == 0;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return AutoScrapeService.completeNetworkScan(batchId, hadError, !hadError);
                }));
            }

            assertTrue("Workers did not become ready", ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int completionOwners = 0;
            boolean completedBatchHadError = false;
            for (Future<AutoScrapeService.NetworkScanCompletion> future : futures) {
                AutoScrapeService.NetworkScanCompletion completion =
                        future.get(5, TimeUnit.SECONDS);
                if (completion.completedBatch) {
                    completionOwners++;
                    completedBatchHadError = completion.batchHadError;
                }
            }

            assertEquals(1, completionOwners);
            assertTrue(completedBatchHadError);
            assertEquals(0, AutoScrapeService.getNetworkScanCount());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue("Executor did not terminate", executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
