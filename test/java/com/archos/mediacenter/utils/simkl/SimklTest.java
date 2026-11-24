// Copyright 2026 Nova Video Player Project
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

package com.archos.mediacenter.utils.simkl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.archos.environment.ArchosUtils;
import com.archos.mediacenter.utils.videodb.VideoDbInfo;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SimklTest {

    private Application app;
    private SharedPreferences prefs;
    private MockWebServer server;

    @Before
    public void setUp() throws Exception {
        app = ApplicationProvider.getApplicationContext();
        ArchosUtils.setGlobalContext(app);
        prefs = PreferenceManager.getDefaultSharedPreferences(app);
        Simkl.wipe(prefs);
        SimklSyncQueue.getInstance(app).clear();

        server = new MockWebServer();
        server.start();
        Simkl.setApiBaseUrlForTests(server.url("/").toString());

        Simkl.AccessToken token = new Simkl.AccessToken();
        token.access_token = "access-1";
        token.refresh_token = "refresh-1";
        Simkl.completeSignIn(app, prefs, token);
        Simkl.setUser(prefs, "alice");
    }

    @After
    public void tearDown() throws Exception {
        Simkl.awaitPendingTasksForTests();
        Simkl.setApiBaseUrlForTests(null);
        Simkl.wipe(prefs);
        SimklSyncQueue.getInstance(app).clear();
        server.shutdown();
    }

    @Test
    public void tokenRefreshOn401UsesV2EndpointAndPersistsRotatedTokens() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(401));
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"access_token\":\"access-2\",\"refresh_token\":\"refresh-2\"}"));
        server.enqueue(new MockResponse().setResponseCode(201)
                .setBody("{\"added\":{\"movies\":1,\"shows\":0,\"episodes\":0}}"));

        Simkl.markMovieAsWatched(101L, true, prefs);
        Simkl.awaitPendingTasksForTests();

        assertEquals(3, server.getRequestCount());
        RecordedRequest first = server.takeRequest();
        assertEquals("/sync/history", first.getPath());
        assertEquals("Bearer access-1", first.getHeader("Authorization"));

        RecordedRequest refresh = server.takeRequest();
        assertEquals("/oauth2/token", refresh.getPath());

        RecordedRequest retry = server.takeRequest();
        assertEquals("/sync/history", retry.getPath());
        assertEquals("Bearer access-2", retry.getHeader("Authorization"));

        assertEquals("access-2", Simkl.getAccessToken(prefs));
        assertEquals("refresh-2", Simkl.getRefreshToken(prefs));
        assertTrue(SimklSyncQueue.getInstance(app).getPendingActionsForAccount("alice").isEmpty());
    }

    @Test
    public void rejectedRefreshClearsSessionAndBroadcastsRelog() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(401));
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"error\":\"invalid_grant\"}"));

        Simkl.markMovieAsWatched(102L, true, prefs);
        Simkl.awaitPendingTasksForTests();

        assertNull(Simkl.getAccessToken(prefs));
        assertNull(Simkl.getRefreshToken(prefs));
        assertNull(Simkl.getUser(prefs));

        boolean relogBroadcastSent = false;
        for (Intent intent : shadowOf(app).getBroadcastIntents()) {
            if (Simkl.SIMKL_ISSUE_REFRESH_TOKEN.equals(intent.getAction())) {
                relogBroadcastSent = true;
                break;
            }
        }
        assertTrue(relogBroadcastSent);
    }

    @Test
    public void newerUnmarkCancelsOfflineMarkBeforeFlush() throws Exception {
        // 1. Offline mark (503) -> queued
        server.enqueue(new MockResponse().setResponseCode(503));
        Simkl.markMovieAsWatched(202L, true, prefs);
        Simkl.awaitPendingTasksForTests();

        assertEquals(1, SimklSyncQueue.getInstance(app).getPendingActionsForAccount("alice").size());

        // 2. Newer unmark succeeds (201) -> removes the older offline mark
        server.enqueue(new MockResponse().setResponseCode(201)
                .setBody("{\"deleted\":{\"movies\":1,\"shows\":0,\"episodes\":0}}"));
        Simkl.markMovieAsWatched(202L, false, prefs);
        Simkl.awaitPendingTasksForTests();

        assertTrue(SimklSyncQueue.getInstance(app).getPendingActionsForAccount("alice").isEmpty());

        // 3. Flush must NOT re-send the old watched=true mark
        int beforeFlushCount = server.getRequestCount();
        Simkl.flushPendingActions(app, prefs);
        Simkl.awaitPendingTasksForTests();
        assertEquals(beforeFlushCount, server.getRequestCount());
    }

    @Test
    public void offlineMarkFlushesWithOriginalWatchedAtTimestamp() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503));
        Simkl.markMovieAsWatched(303L, true, prefs);
        Simkl.awaitPendingTasksForTests();

        List<SimklSyncQueue.PendingAction> pending = SimklSyncQueue.getInstance(app).getPendingActionsForAccount("alice");
        assertEquals(1, pending.size());

        server.enqueue(new MockResponse().setResponseCode(201)
                .setBody("{\"added\":{\"movies\":1,\"shows\":0,\"episodes\":0}}"));
        Simkl.flushPendingActions(app, prefs);
        Simkl.awaitPendingTasksForTests();

        server.takeRequest(); // 503
        RecordedRequest flushReq = server.takeRequest();
        assertEquals("/sync/history", flushReq.getPath());
        JSONObject body = new JSONObject(flushReq.getBody().readUtf8());
        JSONObject movie = body.getJSONArray("movies").getJSONObject(0);
        assertEquals(303L, movie.getJSONObject("ids").getLong("tmdb"));
        assertTrue(movie.has("watched_at"));
        assertTrue(movie.getString("watched_at").endsWith("Z"));
        assertTrue(SimklSyncQueue.getInstance(app).getPendingActionsForAccount("alice").isEmpty());
    }

    @Test
    public void accountSwitchIsolatesPendingQueue() throws Exception {
        // Queue offline action for alice
        server.enqueue(new MockResponse().setResponseCode(503));
        Simkl.markMovieAsWatched(404L, true, prefs);
        Simkl.awaitPendingTasksForTests();
        assertEquals(1, SimklSyncQueue.getInstance(app).getPendingActionsForAccount("alice").size());

        // Switch to bob: alice's pending action must be preserved in SQLite, not sent by bob
        Simkl.AccessToken bobToken = new Simkl.AccessToken();
        bobToken.access_token = "bob-access";
        bobToken.refresh_token = "bob-refresh";
        Simkl.completeSignIn(app, prefs, bobToken);
        Simkl.setUser(prefs, "bob");

        int beforeBobFlushCount = server.getRequestCount();
        Simkl.flushPendingActions(app, prefs);
        Simkl.awaitPendingTasksForTests();

        assertEquals(beforeBobFlushCount, server.getRequestCount());
        assertTrue(SimklSyncQueue.getInstance(app).getPendingActionsForAccount("bob").isEmpty());
        assertEquals(1, SimklSyncQueue.getInstance(app).getPendingActionsForAccount("alice").size());

        // Switch back to alice and flush: her preserved action is now sent
        Simkl.AccessToken aliceToken2 = new Simkl.AccessToken();
        aliceToken2.access_token = "alice-access-2";
        aliceToken2.refresh_token = "alice-refresh-2";
        Simkl.completeSignIn(app, prefs, aliceToken2);
        Simkl.setUser(prefs, "alice");

        server.enqueue(new MockResponse().setResponseCode(201)
                .setBody("{\"added\":{\"movies\":1,\"shows\":0,\"episodes\":0}}"));
        Simkl.flushPendingActions(app, prefs);
        Simkl.awaitPendingTasksForTests();

        assertEquals(beforeBobFlushCount + 1, server.getRequestCount());
        assertTrue(SimklSyncQueue.getInstance(app).getPendingActionsForAccount("alice").isEmpty());
    }

    @Test
    public void seasonQueueDeduplicatesPerEpisodeAcrossPartialOverlaps() throws Exception {
        // 1. Offline season mark for episodes [1, 2] -> queued per-episode
        server.enqueue(new MockResponse().setResponseCode(503));
        Simkl.markSeasonEpisodesAsWatched(700L, 1, Arrays.asList(1, 2), true, prefs);
        Simkl.awaitPendingTasksForTests();
        assertEquals(2, SimklSyncQueue.getInstance(app).getPendingActionsForAccount("alice").size());

        // 2. Newer unmark for episodes [1, 2, 3] succeeds -> removes both 1 and 2 from queue
        server.enqueue(new MockResponse().setResponseCode(201)
                .setBody("{\"deleted\":{\"movies\":0,\"shows\":0,\"episodes\":3}}"));
        Simkl.markSeasonEpisodesAsWatched(700L, 1, Arrays.asList(1, 2, 3), false, prefs);
        Simkl.awaitPendingTasksForTests();
        assertTrue(SimklSyncQueue.getInstance(app).getPendingActionsForAccount("alice").isEmpty());

        // 3. Flush must send nothing
        int beforeFlushCount = server.getRequestCount();
        Simkl.flushPendingActions(app, prefs);
        Simkl.awaitPendingTasksForTests();
        assertEquals(beforeFlushCount, server.getRequestCount());
    }

    @Test
    public void http201WithNotFoundIsTreatedAsPermanentFailure() throws Exception {
        // Queue offline movie first, then flush gets 201 with added=0 and not_found
        server.enqueue(new MockResponse().setResponseCode(503));
        Simkl.markMovieAsWatched(505L, true, prefs);
        Simkl.awaitPendingTasksForTests();

        assertEquals(1, SimklSyncQueue.getInstance(app).getPendingActionsForAccount("alice").size());

        server.enqueue(new MockResponse().setResponseCode(201)
                .setBody("{\"added\":{\"movies\":0,\"shows\":0,\"episodes\":0},\"not_found\":{\"movies\":[{\"ids\":{\"tmdb\":505}}]}}"));
        Simkl.flushPendingActions(app, prefs);
        Simkl.awaitPendingTasksForTests();

        // Dropped from queue as permanent failure, not retried again
        assertTrue(SimklSyncQueue.getInstance(app).getPendingActionsForAccount("alice").isEmpty());
    }

    @Test
    public void animeSeasonPayloadAndScrobbleMinProgressAreIncluded() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201)
                .setBody("{\"added\":{\"movies\":0,\"shows\":0,\"episodes\":2}}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        // Partially indexed anime season with sparse episode numbers [1, 4]
        Simkl.markSeasonEpisodesAsWatched(606L, 2, Arrays.asList(1, 4), true, prefs);

        VideoDbInfo info = new VideoDbInfo();
        info.isShow = true;
        info.scraperShowId = "606";
        info.scraperTitle = "Test Anime";
        info.scraperSeasonNr = 2;
        info.scraperEpisodeNr = 3;
        Simkl.scrobbleStart(info, 10f, prefs);
        Simkl.scrobbleStop(info, 92f, prefs);
        Simkl.awaitPendingTasksForTests();

        RecordedRequest seasonReq = server.takeRequest();
        JSONObject seasonBody = new JSONObject(seasonReq.getBody().readUtf8());
        JSONObject showObj = seasonBody.getJSONArray("shows").getJSONObject(0);
        assertTrue(showObj.getBoolean("use_tvdb_anime_seasons"));
        JSONArray eps = showObj.getJSONArray("seasons").getJSONObject(0).getJSONArray("episodes");
        assertEquals(2, eps.length());
        assertEquals(1, eps.getJSONObject(0).getInt("number"));
        assertEquals(4, eps.getJSONObject(1).getInt("number"));

        RecordedRequest startReq = server.takeRequest();
        assertEquals("/scrobble/start", startReq.getPath());
        JSONObject startBody = new JSONObject(startReq.getBody().readUtf8());
        assertEquals(90, startBody.getInt("min_progress"));

        RecordedRequest stopReq = server.takeRequest();
        assertEquals("/scrobble/stop", stopReq.getPath());
        JSONObject stopBody = new JSONObject(stopReq.getBody().readUtf8());
        assertEquals(90, stopBody.getInt("min_progress"));
    }

    @Test
    public void movieWatchlistPreservedOnScrobbleBelowThresholdAndSyncHistory() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201)
                .setBody("{\"added\":{\"movies\":1,\"shows\":0,\"episodes\":0}}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        // Marking a movie watched (e.g. one already on the user's Watchlist) posts only to /sync/history
        Simkl.markMovieAsWatched(808L, true, prefs);

        // Stopping below 90% threshold sends pause, never stop or /sync/history/remove
        VideoDbInfo movieInfo = new VideoDbInfo();
        movieInfo.isShow = false;
        movieInfo.scraperMovieId = "808";
        movieInfo.scraperTitle = "Watchlist Movie";
        Simkl.scrobbleStop(movieInfo, 85f, prefs);
        Simkl.awaitPendingTasksForTests();

        RecordedRequest historyReq = server.takeRequest();
        assertEquals("/sync/history", historyReq.getPath());
        JSONObject historyBody = new JSONObject(historyReq.getBody().readUtf8());
        assertEquals(808L, historyBody.getJSONArray("movies").getJSONObject(0).getJSONObject("ids").getLong("tmdb"));

        RecordedRequest scrobbleReq = server.takeRequest();
        assertEquals("/scrobble/pause", scrobbleReq.getPath());
        JSONObject scrobbleBody = new JSONObject(scrobbleReq.getBody().readUtf8());
        assertEquals(85.0, scrobbleBody.getDouble("progress"), 0.01);
        assertFalse(scrobbleBody.has("min_progress"));
    }

    @Test
    public void mixed201WithPartialSuccessAndNotFoundFlagsPartialWithoutRetry() throws Exception {
        String mixedBody = "{\"added\":{\"movies\":1,\"shows\":0,\"episodes\":0},\"not_found\":{\"movies\":[{\"ids\":{\"tmdb\":999}}]}}";
        assertEquals(Simkl.SYNC_PARTIAL_NOT_FOUND, Simkl.analyzeSyncResponse(mixedBody, true));
        assertEquals(Simkl.SYNC_FAILED_PERMANENT,
                Simkl.analyzeSyncResponse("{\"not_found\":{\"movies\":[{\"ids\":{\"tmdb\":999}}]}}", true));

        server.enqueue(new MockResponse().setResponseCode(201).setBody(mixedBody));
        Simkl.markMoviesAsWatched(Arrays.asList(101L, 999L), true, prefs);
        Simkl.awaitPendingTasksForTests();

        assertEquals(1, server.getRequestCount());
        assertTrue(SimklSyncQueue.getInstance(app).getPendingActionsForAccount("alice").isEmpty());
    }

    @Test
    public void accountSwitchBeforeWorkerRunsNeverSendsNewTokenAndPreservesOldAction() throws Exception {
        final CountDownLatch firstInFlight = new CountDownLatch(1);
        final CountDownLatch releaseFirst = new CountDownLatch(1);

        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                if ("/sync/history".equals(request.getPath())) {
                    String body = request.getBody().clone().readUtf8();
                    if (body.contains("400")) {
                        firstInFlight.countDown();
                        assertTrue(releaseFirst.await(5, TimeUnit.SECONDS));
                    }
                    return new MockResponse().setResponseCode(201)
                            .setBody("{\"added\":{\"movies\":1,\"shows\":0,\"episodes\":0}}");
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        // Start a blocking request as alice so the single-thread executor is occupied
        Simkl.markMovieAsWatched(400L, true, prefs);
        assertTrue(firstInFlight.await(5, TimeUnit.SECONDS));

        // Submit second action as alice, then immediately switch to bob before worker reaches 405
        Simkl.markMovieAsWatched(405L, true, prefs);
        Simkl.AccessToken bobToken = new Simkl.AccessToken();
        bobToken.access_token = "bob-access";
        bobToken.refresh_token = "bob-refresh";
        Simkl.completeSignIn(app, prefs, bobToken);
        Simkl.setUser(prefs, "bob");

        releaseFirst.countDown();
        Simkl.awaitPendingTasksForTests();

        // Only alice's first request (400) hit the wire; bob's token was never used for 405
        assertEquals(1, server.getRequestCount());
        RecordedRequest firstReq = server.takeRequest();
        assertEquals("Bearer access-1", firstReq.getHeader("Authorization"));
        assertTrue(SimklSyncQueue.getInstance(app).getPendingActionsForAccount("bob").isEmpty());

        // Bob flushing must not touch alice's preserved 405 action
        Simkl.flushPendingActions(app, prefs);
        Simkl.awaitPendingTasksForTests();
        assertEquals(1, server.getRequestCount());
        List<SimklSyncQueue.PendingAction> alicePending = SimklSyncQueue.getInstance(app).getPendingActionsForAccount("alice");
        assertEquals(1, alicePending.size());
        assertEquals(405L, alicePending.get(0).tmdbId);

        // Switching back to alice delivers her preserved 405 action
        Simkl.AccessToken aliceToken2 = new Simkl.AccessToken();
        aliceToken2.access_token = "alice-access-2";
        aliceToken2.refresh_token = "alice-refresh-2";
        Simkl.completeSignIn(app, prefs, aliceToken2);
        Simkl.setUser(prefs, "alice");

        Simkl.flushPendingActions(app, prefs);
        Simkl.awaitPendingTasksForTests();

        assertEquals(2, server.getRequestCount());
        RecordedRequest secondReq = server.takeRequest();
        assertEquals("Bearer alice-access-2", secondReq.getHeader("Authorization"));
        assertTrue(secondReq.getBody().readUtf8().contains("405"));
        assertTrue(SimklSyncQueue.getInstance(app).getPendingActionsForAccount("alice").isEmpty());
    }

    @Test
    public void pendingLoginWithoutUsernameQueuesOn503AndFlushesAfterSetUser() throws Exception {
        Simkl.wipe(prefs);
        Simkl.AccessToken pendingToken = new Simkl.AccessToken();
        pendingToken.access_token = "pending-access";
        pendingToken.refresh_token = "pending-refresh";
        Simkl.completeSignIn(app, prefs, pendingToken);
        assertNull(Simkl.getUser(prefs));
        String pendingAccountKey = Simkl.getAccountKey(prefs);
        assertNotNull(pendingAccountKey);
        assertTrue(pendingAccountKey.startsWith("pending_"));

        server.enqueue(new MockResponse().setResponseCode(503));
        Simkl.markMovieAsWatched(601L, true, prefs);
        Simkl.awaitPendingTasksForTests();

        assertEquals(1, SimklSyncQueue.getInstance(app).getPendingActionsForAccount(pendingAccountKey).size());

        Simkl.setUser(prefs, "alice");
        assertTrue(SimklSyncQueue.getInstance(app).getPendingActionsForAccount(pendingAccountKey).isEmpty());
        assertEquals(1, SimklSyncQueue.getInstance(app).getPendingActionsForAccount("alice").size());

        server.enqueue(new MockResponse().setResponseCode(201)
                .setBody("{\"added\":{\"movies\":1,\"shows\":0,\"episodes\":0}}"));
        Simkl.flushPendingActions(app, prefs);
        Simkl.awaitPendingTasksForTests();

        assertTrue(SimklSyncQueue.getInstance(app).getPendingActionsForAccount("alice").isEmpty());
        server.takeRequest(); // 503
        RecordedRequest flushReq = server.takeRequest();
        assertTrue(flushReq.getBody().readUtf8().contains("601"));
    }

    @Test
    public void signOutDuringInFlightRefreshDoesNotRestoreOldTokens() throws Exception {
        final CountDownLatch refreshInFlight = new CountDownLatch(1);
        final CountDownLatch releaseRefresh = new CountDownLatch(1);

        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                if ("/sync/history".equals(request.getPath())) {
                    return new MockResponse().setResponseCode(401);
                }
                if ("/oauth2/token".equals(request.getPath())) {
                    refreshInFlight.countDown();
                    assertTrue(releaseRefresh.await(5, TimeUnit.SECONDS));
                    return new MockResponse().setResponseCode(200)
                            .setBody("{\"access_token\":\"stale-access\",\"refresh_token\":\"stale-refresh\"}");
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        Simkl.markMovieAsWatched(501L, true, prefs);
        assertTrue(refreshInFlight.await(5, TimeUnit.SECONDS));

        // User signs out while refresh HTTP call is in flight
        Simkl.wipe(prefs);
        releaseRefresh.countDown();
        Simkl.awaitPendingTasksForTests();

        assertNull(Simkl.getAccessToken(prefs));
        assertNull(Simkl.getRefreshToken(prefs));
        assertNull(Simkl.getUser(prefs));
        assertTrue(SimklSyncQueue.getInstance(app).getPendingActionsForAccount("alice").isEmpty());
        // Retry must not fire after wipe
        assertEquals(2, server.getRequestCount());
    }
}
