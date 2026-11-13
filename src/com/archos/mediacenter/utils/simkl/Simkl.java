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

import android.content.Context;
import android.content.SharedPreferences;

import com.archos.mediacenter.utils.videodb.VideoDbInfo;
import com.archos.medialib.R;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Simkl {

    private static final Logger log = LoggerFactory.getLogger(Simkl.class);

    public static final String SIMKL_API_BASE_URL = "https://api.simkl.com";
    public static final String SIMKL_DEVICE_CODE_URL = SIMKL_API_BASE_URL + "/oauth2/device";
    public static final String SIMKL_TOKEN_URL = SIMKL_API_BASE_URL + "/oauth2/token";
    public static final String SIMKL_DEFAULT_VERIFICATION_URL = "https://simkl.com/pin";

    public static final String KEY_SIMKL_ACCESS_TOKEN = "simkl_access_token";
    public static final String KEY_SIMKL_REFRESH_TOKEN = "simkl_refresh_token";
    public static final String KEY_SIMKL_USER = "simkl_user";
    public static final String KEY_SIMKL_LIVE_SCROBBLING = "simkl_live_scrobbling";

    public static final String SIMKL_USER_SETTINGS_URL = SIMKL_API_BASE_URL + "/users/settings";
    public static final String SIMKL_SCROBBLE_URL = SIMKL_API_BASE_URL + "/scrobble/";

    private static String CLIENT_ID = "";
    private static String CLIENT_SECRET = "";

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static OkHttpClient sHttpClient;
    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();

    public static class DeviceCode {
        public String device_code;
        public String user_code;
        public String verification_url;
        public int expires_in = 900;
        public int interval = 5;
    }

    public static class AccessToken {
        public String access_token;
        public String refresh_token;
        public String token_type;
        public String scope;
        public String error;
    }

    private static synchronized OkHttpClient getHttpClient() {
        if (sHttpClient == null) {
            sHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .build();
        }
        return sHttpClient;
    }

    public static void initApiKeys(Context context) {
        if (context == null) return;
        try {
            CLIENT_ID = context.getString(R.string.simkl_client_id);
            CLIENT_SECRET = context.getString(R.string.simkl_client_secret);
        } catch (Exception e) {
            log.error("initApiKeys: failed to load simkl keys from resources", e);
        }
    }

    public static String getClientId() {
        if (CLIENT_ID == null || CLIENT_ID.isEmpty()) {
            initApiKeys(com.archos.environment.ArchosUtils.getGlobalContext());
        }
        return CLIENT_ID;
    }

    public static String getClientSecret() {
        if (CLIENT_SECRET == null || CLIENT_SECRET.isEmpty()) {
            initApiKeys(com.archos.environment.ArchosUtils.getGlobalContext());
        }
        return CLIENT_SECRET;
    }

    public static boolean isSimklEnabled(Context context, SharedPreferences prefs) {
        if (prefs == null) return false;
        String token = prefs.getString(KEY_SIMKL_ACCESS_TOKEN, null);
        return token != null && !token.trim().isEmpty();
    }

    public static String getAccessToken(SharedPreferences prefs) {
        if (prefs == null) return null;
        return prefs.getString(KEY_SIMKL_ACCESS_TOKEN, null);
    }

    public static void setAccessToken(SharedPreferences prefs, String token) {
        if (prefs == null) return;
        prefs.edit().putString(KEY_SIMKL_ACCESS_TOKEN, token).apply();
    }

    public static String getRefreshToken(SharedPreferences prefs) {
        if (prefs == null) return null;
        return prefs.getString(KEY_SIMKL_REFRESH_TOKEN, null);
    }

    public static void setRefreshToken(SharedPreferences prefs, String token) {
        if (prefs == null) return;
        prefs.edit().putString(KEY_SIMKL_REFRESH_TOKEN, token).apply();
    }

    public static String getUser(SharedPreferences prefs) {
        if (prefs == null) return null;
        return prefs.getString(KEY_SIMKL_USER, null);
    }

    public static void setUser(SharedPreferences prefs, String user) {
        if (prefs == null) return;
        prefs.edit().putString(KEY_SIMKL_USER, user).apply();
    }

    public static boolean isLiveScrobblingEnabled(SharedPreferences prefs) {
        if (prefs == null) return false;
        return isSimklEnabled(null, prefs) && prefs.getBoolean(KEY_SIMKL_LIVE_SCROBBLING, true);
    }

    public static void wipe(SharedPreferences prefs) {
        if (prefs == null) return;
        prefs.edit()
                .remove(KEY_SIMKL_ACCESS_TOKEN)
                .remove(KEY_SIMKL_REFRESH_TOKEN)
                .remove(KEY_SIMKL_USER)
                .remove(KEY_SIMKL_LIVE_SCROBBLING)
                .apply();
    }

    /**
     * Request a device code from SIMKL (OAuth2 Device Authorization Grant).
     * @return DeviceCode object with user_code and verification_url, or null on error.
     */
    public static DeviceCode generateDeviceCode() {
        try {
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("client_id", CLIENT_ID);
            jsonBody.put("scope", "media:read media:write");

            RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
            Request request = new Request.Builder()
                    .url(SIMKL_DEVICE_CODE_URL)
                    .addHeader("simkl-api-key", CLIENT_ID)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = getHttpClient().newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String respStr = response.body().string();
                    if (log.isDebugEnabled()) log.debug("generateDeviceCode: success");
                    JSONObject json = new JSONObject(respStr);
                    DeviceCode dc = new DeviceCode();
                    dc.device_code = json.optString("device_code", "");
                    dc.user_code = json.optString("user_code", "");
                    if (json.has("verification_url")) {
                        dc.verification_url = json.getString("verification_url");
                    } else if (json.has("verification_uri")) {
                        dc.verification_url = json.getString("verification_uri");
                    } else {
                        dc.verification_url = SIMKL_DEFAULT_VERIFICATION_URL;
                    }
                    dc.expires_in = json.optInt("expires_in", 900);
                    dc.interval = json.optInt("interval", 5);
                    if (dc.interval < 1) dc.interval = 5;
                    return dc;
                } else {
                    log.error("generateDeviceCode failed: HTTP {}", response.code());
                }
            }
        } catch (IOException | JSONException e) {
            log.error("generateDeviceCode caught exception", e);
        }
        return null;
    }

    /**
     * Poll SIMKL token endpoint to check if user authorized the device code.
     * @param deviceCode the device_code obtained from generateDeviceCode()
     * @return AccessToken if authorized, null if still pending or on failure.
     */
    public static AccessToken exchangeDeviceCodeForAccessToken(String deviceCode) {
        if (deviceCode == null || deviceCode.isEmpty()) return null;
        try {
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("device_code", deviceCode);
            jsonBody.put("code", deviceCode);
            jsonBody.put("client_id", CLIENT_ID);
            if (CLIENT_SECRET != null && !CLIENT_SECRET.isEmpty()) {
                jsonBody.put("client_secret", CLIENT_SECRET);
            }
            jsonBody.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");

            RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
            Request request = new Request.Builder()
                    .url(SIMKL_TOKEN_URL)
                    .addHeader("simkl-api-key", CLIENT_ID)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = getHttpClient().newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String respStr = response.body().string();
                    if (log.isDebugEnabled()) log.debug("exchangeDeviceCodeForAccessToken: success");
                    JSONObject json = new JSONObject(respStr);
                    AccessToken token = new AccessToken();
                    token.access_token = json.optString("access_token", null);
                    token.token_type = json.optString("token_type", "Bearer");
                    token.refresh_token = json.optString("refresh_token", null);
                    token.scope = json.optString("scope", "public");
                    return token;
                } else {
                    String errBody = response.body() != null ? response.body().string() : "";
                    if (log.isDebugEnabled()) log.debug("exchangeDeviceCodeForAccessToken: HTTP status {}", response.code());
                    AccessToken errToken = new AccessToken();
                    try {
                        JSONObject errJson = new JSONObject(errBody);
                        errToken.error = errJson.optString("error", "http_" + response.code());
                    } catch (Exception ignored) {
                        errToken.error = "http_" + response.code();
                    }
                    return errToken;
                }
            }
        } catch (IOException | JSONException e) {
            log.error("exchangeDeviceCodeForAccessToken error", e);
        }
        return null;
    }

    /**
     * Fetch user profile settings from SIMKL to obtain user information (e.g. username).
     * @param accessToken Bearer token
     * @return username String or null on error
     */
    public static String fetchUserProfile(String accessToken) {
        if (accessToken == null || accessToken.isEmpty()) return null;
        try {
            Request request = new Request.Builder()
                    .url(SIMKL_USER_SETTINGS_URL)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("simkl-api-key", CLIENT_ID)
                    .get()
                    .build();

            try (Response response = getHttpClient().newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String respStr = response.body().string();
                    JSONObject json = new JSONObject(respStr);
                    if (json.has("user")) {
                        JSONObject userObj = json.getJSONObject("user");
                        return userObj.optString("name", null);
                    }
                }
            }
        } catch (Exception e) {
            log.error("fetchUserProfile error", e);
        }
        return null;
    }

    public static void scrobbleStart(VideoDbInfo videoInfo, float progress, SharedPreferences prefs) {
        scrobble("start", videoInfo, progress, prefs);
    }

    public static void scrobblePause(VideoDbInfo videoInfo, float progress, SharedPreferences prefs) {
        scrobble("pause", videoInfo, progress, prefs);
    }

    public static void scrobbleStop(VideoDbInfo videoInfo, float progress, SharedPreferences prefs) {
        scrobble("stop", videoInfo, progress, prefs);
    }

    /**
     * Dispatch scrobble event asynchronously to SIMKL.
     * @param action "start", "pause", or "stop"
     * @param videoInfo video information
     * @param progressPercent progress in percentage (0 to 100)
     * @param prefs SharedPreferences to check credentials
     */
    public static void scrobble(final String action, final VideoDbInfo videoInfo, final float progressPercent, final SharedPreferences prefs) {
        if (videoInfo == null || prefs == null) return;
        final String token = getAccessToken(prefs);
        if (token == null || token.isEmpty()) return;
        if (!isLiveScrobblingEnabled(prefs)) return;

        sExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                float progressClamped = Math.max(0f, Math.min(100f, progressPercent));
                payload.put("progress", (double) Math.round(progressClamped * 10.0) / 10.0);

                if (videoInfo.isShow) {
                    JSONObject showObj = new JSONObject();
                    String showTitle = videoInfo.scraperTitle != null ? videoInfo.scraperTitle : videoInfo.title;
                    if (showTitle != null) showObj.put("title", showTitle);

                    JSONObject idsObj = new JSONObject();
                    if (videoInfo.scraperShowId != null && !videoInfo.scraperShowId.isEmpty()) {
                        try {
                            idsObj.put("tmdb", Integer.parseInt(videoInfo.scraperShowId));
                        } catch (NumberFormatException nfe) {
                            idsObj.put("tmdb", videoInfo.scraperShowId);
                        }
                    }
                    if (idsObj.length() > 0) {
                        showObj.put("ids", idsObj);
                    }
                    payload.put("show", showObj);

                    JSONObject epObj = new JSONObject();
                    if (videoInfo.scraperSeasonNr >= 0) epObj.put("season", videoInfo.scraperSeasonNr);
                    if (videoInfo.scraperEpisodeNr >= 0) epObj.put("number", videoInfo.scraperEpisodeNr);
                    payload.put("episode", epObj);
                } else {
                    JSONObject movieObj = new JSONObject();
                    String movieTitle = videoInfo.scraperTitle != null ? videoInfo.scraperTitle : videoInfo.title;
                    if (movieTitle != null) movieObj.put("title", movieTitle);

                    JSONObject idsObj = new JSONObject();
                    if (videoInfo.scraperMovieId != null && !videoInfo.scraperMovieId.isEmpty()) {
                        try {
                            idsObj.put("tmdb", Integer.parseInt(videoInfo.scraperMovieId));
                        } catch (NumberFormatException nfe) {
                            idsObj.put("tmdb", videoInfo.scraperMovieId);
                        }
                    }
                    if (idsObj.length() > 0) {
                        movieObj.put("ids", idsObj);
                    }
                    payload.put("movie", movieObj);
                }

                String url = SIMKL_SCROBBLE_URL + action;
                RequestBody body = RequestBody.create(payload.toString(), JSON);
                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer " + token)
                        .addHeader("simkl-api-key", CLIENT_ID)
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build();

                if (log.isDebugEnabled()) log.debug("scrobble {}: sending payload {}", action, payload);

                try (Response response = getHttpClient().newCall(request).execute()) {
                    String respBody = response.body() != null ? response.body().string() : "";
                    if (log.isDebugEnabled()) log.debug("scrobble {} result code={}, body={}", action, response.code(), respBody);
                }
            } catch (Exception e) {
                log.error("scrobble {} failed with exception", action, e);
            }
        });
    }
}
