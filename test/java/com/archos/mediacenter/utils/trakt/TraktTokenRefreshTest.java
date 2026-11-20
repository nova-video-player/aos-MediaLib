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

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.archos.environment.ArchosUtils;
import com.uwetrottmann.trakt5.TraktV2Authenticator;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class TraktTokenRefreshTest {

    @Test
    public void tokensRotatedByTraktJavaAuthenticatorArePersisted() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        ArchosUtils.setGlobalContext(context);
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        Trakt.setAccessToken(pref, "old-access");
        Trakt.setRefreshToken(pref, "old-refresh");

        Trakt.MyTraktV2 trakt = new Trakt.MyTraktV2("key", "secret", "nova.trakt://auth") {
            @Override
            protected void setOkHttpClientDefaults(OkHttpClient.Builder builder) {
                super.setOkHttpClientDefaults(builder);
                // answer POST /oauth/token locally instead of calling Trakt
                builder.addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create(
                                "{\"access_token\":\"new-access\",\"refresh_token\":\"new-refresh\",\"expires_in\":86400}",
                                MediaType.get("application/json")))
                        .build());
            }
        };
        trakt.accessToken("old-access").refreshToken("old-refresh");

        // a Trakt API call answered 401: trakt-java refreshes the tokens on its own
        Request request = new Request.Builder().url("https://api.trakt.tv/sync/last_activities").build();
        Response unauthorized = new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .build();
        Request retry = TraktV2Authenticator.handleAuthenticate(unauthorized, trakt);

        assertEquals("Bearer new-access", retry.header("Authorization"));
        // what the next Trakt instance loads must be the rotated tokens, not the used refresh token
        assertEquals("new-access", Trakt.getAccessTokenFromPreferences(pref));
        assertEquals("new-refresh", pref.getString("trakt_refresh_token", null));
    }
}
