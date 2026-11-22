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

package com.archos.mediacenter.utils.trakt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.archos.environment.ArchosUtils;
import com.uwetrottmann.trakt5.TraktV2Authenticator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class TraktTokenRefreshTest {

    private Application app;
    private SharedPreferences pref;
    private static final String SYNC_TIME = TraktService.PREFERENCE_TRAKT_LAST_TIME_SYNC_TO_DB_PROGRESS;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        ArchosUtils.setGlobalContext(app);
        // Existing installations already have these keys on disk.
        pref = PreferenceManager.getDefaultSharedPreferences(app);
        pref.edit().putString(Trakt.KEY_TRAKT_ACCESS_TOKEN, "old-access")
                .putString("trakt_refresh_token", "old-refresh").putLong(SYNC_TIME, 123L)
                .putBoolean(TraktService.PREFERENCE_TRAKT_FIRST_SYNC_DONE, true).apply();
    }

    private Trakt.MyTraktV2 client(int refreshCode, Runnable beforeRefreshResponse) {
        Trakt.MyTraktV2 client = new Trakt.MyTraktV2("key", "secret", "nova.trakt://auth") {
            @Override
            protected void setOkHttpClientDefaults(OkHttpClient.Builder builder) {
                super.setOkHttpClientDefaults(builder);
                builder.addInterceptor(chain -> {
                    Request request = chain.request();
                    boolean refresh = request.url().encodedPath().equals("/oauth/token");
                    if (refresh) beforeRefreshResponse.run();
                    int code = refresh ? refreshCode : 409;
                    String json = refresh && code == 200
                            ? "{\"access_token\":\"new-access\",\"refresh_token\":\"new-refresh\"}" : "{}";
                    return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                            .code(code).message("Local test response")
                            .body(ResponseBody.create(json, MediaType.get("application/json"))).build();
                });
            }
        };
        client.accessToken("old-access").refreshToken("old-refresh");
        return client;
    }

    private Request authenticate(Trakt.MyTraktV2 client) throws IOException {
        Request request = new Request.Builder().url("https://api.trakt.tv/sync/last_activities").build();
        Response unauthorized = new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(401).message("Unauthorized").build();
        return TraktV2Authenticator.handleAuthenticate(unauthorized, client);
    }

    private void replaceLogin() {
        Trakt.accessToken tokens = new Trakt.accessToken();
        tokens.access_token = "other-access";
        tokens.refresh_token = "other-refresh";
        Trakt.completeSignIn(app, tokens);
    }

    private boolean prompted() {
        for (Intent intent : shadowOf(app).getBroadcastIntents()) {
            if (Trakt.TRAKT_ISSUE_REFRESH_TOKEN.equals(intent.getAction())) return true;
        }
        return false;
    }

    @Test
    public void tokensRotatedByTraktJavaAuthenticatorArePersisted() throws Exception {
        Trakt.MyTraktV2 client = client(200, () -> {});
        assertEquals("Bearer new-access", authenticate(client).header("Authorization"));
        assertEquals("new-access", Trakt.getAccessTokenFromPreferences(pref));
        assertEquals("new-refresh", pref.getString("trakt_refresh_token", null));
        assertEquals(123L, pref.getLong(SYNC_TIME, 0));
        assertTrue(Trakt.isFirstSyncDone(pref));
        assertNull(shadowOf(app).getNextStartedService());
    }

    @Test
    public void logoutDuringAutomaticRefreshKeepsCredentialsCleared() {
        Trakt.MyTraktV2 client = client(200, () -> Trakt.wipePreferences(pref, false));
        assertThrows(IOException.class, () -> authenticate(client));
        assertNull(Trakt.getAccessTokenFromPreferences(pref));
        assertFalse(pref.contains("trakt_refresh_token"));
        assertFalse(prompted());
    }

    @Test
    public void rejectedFallbackCannotClearReplacementLoginOrPrompt() {
        Trakt.MyTraktV2 client = client(400, this::replaceLogin);
        new Trakt(app, client).exec(client.sync().lastActivities2());
        assertEquals("other-access", Trakt.getAccessTokenFromPreferences(pref));
        assertEquals("other-refresh", pref.getString("trakt_refresh_token", null));
        assertFalse(prompted());
    }

    @Test
    public void explicitReplacementWipesBeforeSyncAndRejectsOldSuccess() {
        Trakt.MyTraktV2 client = client(200, this::replaceLogin);
        assertThrows(IOException.class, () -> authenticate(client));
        assertEquals("other-access", Trakt.getAccessTokenFromPreferences(pref));
        assertEquals("other-refresh", pref.getString("trakt_refresh_token", null));
        assertFalse(pref.contains(SYNC_TIME));
        assertFalse(Trakt.isFirstSyncDone(pref));
        assertTrue(shadowOf(app).getNextStartedService().getAction().endsWith(".WIPE"));
        assertTrue(shadowOf(app).getNextStartedService().getAction().endsWith(".SYNC"));
    }

    @Test
    public void rejectedCurrentRefreshClearsCredentialsAndPrompts() {
        Trakt.MyTraktV2 client = client(401, () -> {});
        new Trakt(app, client).exec(client.sync().lastActivities2());
        assertFalse(pref.contains(Trakt.KEY_TRAKT_ACCESS_TOKEN));
        assertFalse(pref.contains("trakt_refresh_token"));
        assertTrue(prompted());
    }

    @Test
    public void unavailableRefreshEndpointKeepsCredentialsAndPrompts() {
        Trakt.MyTraktV2 client = client(503, () -> {});
        new Trakt(app, client).exec(client.sync().lastActivities2());
        assertEquals("old-access", Trakt.getAccessTokenFromPreferences(pref));
        assertEquals("old-refresh", pref.getString("trakt_refresh_token", null));
        assertTrue(prompted());
    }
}
