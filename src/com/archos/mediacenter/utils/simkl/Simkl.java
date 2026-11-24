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
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.archos.mediacenter.utils.videodb.VideoDbInfo;
import com.archos.medialib.R;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
    /** OAuth2 token refresh endpoint (grant_type=refresh_token). */
    public static final String SIMKL_REFRESH_TOKEN_URL = SIMKL_API_BASE_URL + "/oauth2/token";
    public static final String SIMKL_DEFAULT_VERIFICATION_URL = "https://simkl.com/pin";

    public static final String SIMKL_ISSUE_REFRESH_TOKEN = "com.archos.mediacenter.utils.simkl.ISSUE_REFRESH_TOKEN";
    public static final long ASK_RELOG_FREQUENCY = 10000L;
    public static long sLastSimklRefreshToken = 0;

    public static final String KEY_SIMKL_ACCESS_TOKEN = "simkl_access_token";
    public static final String KEY_SIMKL_REFRESH_TOKEN = "simkl_refresh_token";
    public static final String KEY_SIMKL_USER = "simkl_user";
    public static final String KEY_SIMKL_ACCOUNT_ID = "simkl_account_id";
    public static final String KEY_SIMKL_LIVE_SCROBBLING = "simkl_live_scrobbling";

    public static final String SIMKL_USER_SETTINGS_URL = SIMKL_API_BASE_URL + "/users/settings";
    public static final String SIMKL_SCROBBLE_URL = SIMKL_API_BASE_URL + "/scrobble/";
    public static final String SIMKL_HISTORY_URL = SIMKL_API_BASE_URL + "/sync/history";
    /**
     * SIMKL /sync/history/remove removes an item from BOTH the watch history AND the Watchlist
     * (Plan to Watch). Ordinary unmark in Nova stays local to preserve Watchlist membership;
     * this endpoint is reserved for explicit removal actions.
     */
    public static final String SIMKL_HISTORY_REMOVE_URL = SIMKL_API_BASE_URL + "/sync/history/remove";

    private static volatile String sApiBaseUrl = SIMKL_API_BASE_URL;

    /**
     * Only an explicit login, sign-out, or rejected-refresh credential clear changes the session.
     * Routine token rotation preserves the current generation.
     */
    private static volatile long sLoginGeneration = 0;
    private static final Set<Long> sWipedGenerations = new HashSet<>();

    private static final class SessionSnapshot {
        final long generation;
        final String token;
        final String accountKey;
        final boolean liveScrobbling;

        SessionSnapshot(long generation, String token, String accountKey, boolean liveScrobbling) {
            this.generation = generation;
            this.token = token;
            this.accountKey = accountKey;
            this.liveScrobbling = liveScrobbling;
        }
    }

    private static synchronized SessionSnapshot captureSessionSnapshot(SharedPreferences prefs) {
        if (prefs == null) return null;
        String token = getAccessToken(prefs);
        if (token == null || token.trim().isEmpty()) return null;
        return new SessionSnapshot(
                sLoginGeneration,
                token,
                getAccountKey(prefs),
                isLiveScrobblingEnabled(prefs)
        );
    }

    private static synchronized boolean isSessionActive(long expectedGeneration) {
        return sLoginGeneration == expectedGeneration;
    }

    private static synchronized boolean wasGenerationWiped(long expectedGeneration) {
        return sWipedGenerations.contains(expectedGeneration);
    }

    private static synchronized String getTokenIfSessionActive(SharedPreferences prefs, long expectedGeneration) {
        if (sLoginGeneration != expectedGeneration) return null;
        String token = getAccessToken(prefs);
        return (token != null && !token.isEmpty()) ? token : null;
    }

    private static String CLIENT_ID = "";

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static OkHttpClient sHttpClient;
    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();

    /**
     * Guards against concurrent token-refresh storms.
     * Only one refresh attempt runs at a time; others wait and reuse the result.
     */
    private static final Object sRefreshLock = new Object();
    /** true while a refresh is in progress; prevents re-entrant refresh loops. */
    private static final AtomicBoolean sRefreshing = new AtomicBoolean(false);

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

    public static void setApiBaseUrlForTests(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            sApiBaseUrl = SIMKL_API_BASE_URL;
        } else {
            String trimmed = baseUrl.trim();
            while (trimmed.endsWith("/")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            sApiBaseUrl = trimmed;
        }
    }

    private static String getApiBaseUrl() {
        return sApiBaseUrl;
    }

    static long getLoginGeneration() {
        return sLoginGeneration;
    }

    public static void awaitPendingTasksForTests() throws Exception {
        sExecutor.submit(() -> {}).get(10, TimeUnit.SECONDS);
    }

    public static void initApiKeys(Context context) {
        if (context == null) return;
        try {
            CLIENT_ID = context.getString(R.string.simkl_client_id);
        } catch (Exception e) {
            log.error("initApiKeys: failed to load simkl client ID from resources", e);
        }
    }

    public static String getClientId() {
        if (CLIENT_ID == null || CLIENT_ID.isEmpty()) {
            initApiKeys(com.archos.environment.ArchosUtils.getGlobalContext());
        }
        return CLIENT_ID;
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

    public static synchronized void setAccessToken(SharedPreferences prefs, String token) {
        if (prefs == null) return;
        if (token == null) {
            sWipedGenerations.add(sLoginGeneration);
        }
        sLoginGeneration++;
        SharedPreferences.Editor editor = prefs.edit();
        if (token != null) {
            editor.putString(KEY_SIMKL_ACCESS_TOKEN, token);
            if (prefs.getString(KEY_SIMKL_ACCOUNT_ID, null) == null) {
                editor.putString(KEY_SIMKL_ACCOUNT_ID, "pending_" + UUID.randomUUID().toString());
            }
        } else {
            editor.remove(KEY_SIMKL_ACCESS_TOKEN).remove(KEY_SIMKL_REFRESH_TOKEN).remove(KEY_SIMKL_ACCOUNT_ID);
        }
        editor.apply();
    }

    public static String getRefreshToken(SharedPreferences prefs) {
        if (prefs == null) return null;
        return prefs.getString(KEY_SIMKL_REFRESH_TOKEN, null);
    }

    public static synchronized void setRefreshToken(SharedPreferences prefs, String token) {
        if (prefs == null) return;
        sLoginGeneration++;
        prefs.edit().putString(KEY_SIMKL_REFRESH_TOKEN, token).apply();
    }

    public static String getUser(SharedPreferences prefs) {
        if (prefs == null) return null;
        return prefs.getString(KEY_SIMKL_USER, null);
    }

    public static synchronized void setUser(SharedPreferences prefs, String user) {
        if (prefs == null) return;
        String oldAccountId = prefs.getString(KEY_SIMKL_ACCOUNT_ID, null);
        SharedPreferences.Editor editor = prefs.edit().putString(KEY_SIMKL_USER, user);
        if (user != null && !user.trim().isEmpty()) {
            editor.remove(KEY_SIMKL_ACCOUNT_ID);
        }
        editor.apply();

        if (oldAccountId != null && !oldAccountId.isEmpty() && user != null && !user.trim().isEmpty()) {
            try {
                Context ctx = com.archos.environment.ArchosUtils.getGlobalContext();
                if (ctx != null) {
                    SimklSyncQueue.getInstance(ctx).reassignAccount(oldAccountId, user.trim());
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Returns a stable non-null account identifier for the active SIMKL session,
     * falling back to a per-login pending ID until the username is fetched.
     */
    public static synchronized String getAccountKey(SharedPreferences prefs) {
        if (!isSimklEnabled(null, prefs)) return null;
        String user = getUser(prefs);
        if (user != null && !user.trim().isEmpty()) {
            return user.trim();
        }
        String accountId = prefs.getString(KEY_SIMKL_ACCOUNT_ID, null);
        if (accountId == null || accountId.trim().isEmpty()) {
            accountId = "pending_" + UUID.randomUUID().toString();
            prefs.edit().putString(KEY_SIMKL_ACCOUNT_ID, accountId).apply();
        }
        return accountId;
    }

    /**
     * Complete an explicit OAuth login. Replacing an existing link starts a new login generation
     * while preserving any previously queued offline actions under their own account key.
     */
    public static synchronized void completeSignIn(Context context, AccessToken token) {
        if (context == null || token == null || token.access_token == null) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        completeSignIn(context, prefs, token);
    }

    public static synchronized void completeSignIn(Context context, SharedPreferences prefs, AccessToken token) {
        if (prefs == null || token == null || token.access_token == null) return;
        sLoginGeneration++;
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_SIMKL_ACCESS_TOKEN, token.access_token);
        if (token.refresh_token != null && !token.refresh_token.isEmpty()) {
            editor.putString(KEY_SIMKL_REFRESH_TOKEN, token.refresh_token);
        } else {
            editor.remove(KEY_SIMKL_REFRESH_TOKEN);
        }
        editor.remove(KEY_SIMKL_USER);
        editor.putString(KEY_SIMKL_ACCOUNT_ID, "pending_" + UUID.randomUUID().toString());
        editor.apply();
    }

    public static boolean isLiveScrobblingEnabled(SharedPreferences prefs) {
        if (prefs == null) return false;
        return isSimklEnabled(null, prefs) && prefs.getBoolean(KEY_SIMKL_LIVE_SCROBBLING, true);
    }

    public static synchronized void wipe(SharedPreferences prefs) {
        if (prefs == null) return;
        String user = getUser(prefs);
        String accountId = prefs.getString(KEY_SIMKL_ACCOUNT_ID, null);
        sWipedGenerations.add(sLoginGeneration);
        sLoginGeneration++;
        prefs.edit()
                .remove(KEY_SIMKL_ACCESS_TOKEN)
                .remove(KEY_SIMKL_REFRESH_TOKEN)
                .remove(KEY_SIMKL_USER)
                .remove(KEY_SIMKL_ACCOUNT_ID)
                .remove(KEY_SIMKL_LIVE_SCROBBLING)
                .apply();
        try {
            Context ctx = com.archos.environment.ArchosUtils.getGlobalContext();
            if (ctx != null) {
                SimklSyncQueue queue = SimklSyncQueue.getInstance(ctx);
                if (user != null) queue.clearAccount(user);
                if (accountId != null) queue.clearAccount(accountId);
            }
        } catch (Exception ignored) {}
    }

    private static void clearSessionOnRejectedRefresh(SharedPreferences prefs) {
        sLoginGeneration++;
        prefs.edit()
                .remove(KEY_SIMKL_ACCESS_TOKEN)
                .remove(KEY_SIMKL_REFRESH_TOKEN)
                .remove(KEY_SIMKL_USER)
                .remove(KEY_SIMKL_ACCOUNT_ID)
                .apply();
        try {
            Context ctx = com.archos.environment.ArchosUtils.getGlobalContext();
            if (ctx != null) {
                Intent intent = new Intent(SIMKL_ISSUE_REFRESH_TOKEN);
                intent.setPackage(ctx.getPackageName());
                ctx.sendBroadcast(intent);
            }
        } catch (Exception ignored) {}
    }

    // -------------------------------------------------------------------------
    // Token refresh
    // -------------------------------------------------------------------------

    /**
     * Attempts to obtain a new access_token using the stored refresh_token.
     * Thread-safe: at most one refresh runs at a time; concurrent callers wait
     * and re-read the (now updated) access_token from prefs after the lock is released.
     *
     * @param prefs SharedPreferences holding KEY_SIMKL_REFRESH_TOKEN
     * @return new access_token on success, null on failure or if no refresh_token is stored
     */
    private static String refreshAccessToken(SharedPreferences prefs, long expectedGeneration, String expectedAccessToken) {
        if (prefs == null) return null;
        synchronized (sRefreshLock) {
            final String currentRefreshToken;
            synchronized (Simkl.class) {
                if (sLoginGeneration != expectedGeneration) {
                    log.debug("refreshAccessToken: session changed before refresh, aborting");
                    return null;
                }
                String currentAccessToken = prefs.getString(KEY_SIMKL_ACCESS_TOKEN, null);
                if (currentAccessToken != null && !currentAccessToken.isEmpty()
                        && expectedAccessToken != null && !expectedAccessToken.equals(currentAccessToken)) {
                    return currentAccessToken;
                }
                currentRefreshToken = prefs.getString(KEY_SIMKL_REFRESH_TOKEN, null);
                if (currentRefreshToken == null || currentRefreshToken.isEmpty()) {
                    log.warn("refreshAccessToken: no refresh_token stored, cannot renew");
                    return null;
                }
            }

            if (sRefreshing.getAndSet(true)) {
                // Should not happen since we are inside synchronized, but guard anyway
                log.warn("refreshAccessToken: re-entrant refresh prevented");
                return null;
            }

            try {
                String clientId = getClientId();
                if (clientId == null || clientId.isEmpty()) {
                    log.error("refreshAccessToken: CLIENT_ID not initialised");
                    return null;
                }

                JSONObject jsonBody = new JSONObject();
                jsonBody.put("grant_type", "refresh_token");
                jsonBody.put("refresh_token", currentRefreshToken);
                jsonBody.put("client_id", clientId);

                RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
                Request request = new Request.Builder()
                        .url(getApiBaseUrl() + "/oauth2/token")
                        .addHeader("simkl-api-key", clientId)
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build();

                try (Response response = getHttpClient().newCall(request).execute()) {
                    String respBody = response.body() != null ? response.body().string() : "";
                    int code = response.code();
                    synchronized (Simkl.class) {
                        if (sLoginGeneration != expectedGeneration
                                || !currentRefreshToken.equals(prefs.getString(KEY_SIMKL_REFRESH_TOKEN, null))) {
                            log.info("refreshAccessToken: session changed during refresh, discarding result");
                            return null;
                        }
                        if (response.isSuccessful() && !respBody.isEmpty()) {
                            JSONObject json = new JSONObject(respBody);
                            String newAccessToken = json.optString("access_token", null);
                            String newRefreshToken = json.optString("refresh_token", null);
                            if (newAccessToken != null && !newAccessToken.isEmpty()) {
                                SharedPreferences.Editor editor = prefs.edit()
                                        .putString(KEY_SIMKL_ACCESS_TOKEN, newAccessToken);
                                if (newRefreshToken != null && !newRefreshToken.isEmpty()) {
                                    editor.putString(KEY_SIMKL_REFRESH_TOKEN, newRefreshToken);
                                }
                                editor.apply();
                                log.info("refreshAccessToken: token renewed successfully");
                                return newAccessToken;
                            } else {
                                log.error("refreshAccessToken: success HTTP but no access_token in response");
                            }
                        } else {
                            log.error("refreshAccessToken: HTTP {} - {}", code, respBody);
                            if (code >= 400 && code < 500 && code != 408 && code != 429) {
                                log.warn("refreshAccessToken: refresh token rejected (HTTP {}), clearing session", code);
                                clearSessionOnRejectedRefresh(prefs);
                            }
                        }
                    }
                }
            } catch (IOException | JSONException e) {
                log.error("refreshAccessToken: exception during refresh", e);
            } finally {
                sRefreshing.set(false);
            }
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Device authorization flow
    // -------------------------------------------------------------------------

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
                    .url(getApiBaseUrl() + "/oauth2/device")
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
            jsonBody.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");

            RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
            Request request = new Request.Builder()
                    .url(getApiBaseUrl() + "/oauth2/token")
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

    // -------------------------------------------------------------------------
    // User profile
    // -------------------------------------------------------------------------

    /**
     * Fetch user profile settings from SIMKL to obtain user information (e.g. username).
     * @param accessToken Bearer token
     * @return username String or null on error
     */
    public static String fetchUserProfile(String accessToken) {
        if (accessToken == null || accessToken.isEmpty()) return null;
        try {
            Request request = new Request.Builder()
                    .url(getApiBaseUrl() + "/users/settings")
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

    // -------------------------------------------------------------------------
    // Live scrobble
    // -------------------------------------------------------------------------

    public static final int SCROBBLE_THRESHOLD = 90;

    public static boolean shouldMarkAsSeen(float progress) {
        return progress >= SCROBBLE_THRESHOLD;
    }

    public static void scrobbleStart(VideoDbInfo videoInfo, float progress, SharedPreferences prefs) {
        scrobble("start", videoInfo, progress, prefs);
    }

    public static void scrobblePause(VideoDbInfo videoInfo, float progress, SharedPreferences prefs) {
        scrobble("pause", videoInfo, progress, prefs);
    }

    public static void scrobbleStop(VideoDbInfo videoInfo, float progress, SharedPreferences prefs) {
        if (shouldMarkAsSeen(progress)) {
            scrobble("stop", videoInfo, progress, prefs);
        } else {
            // Under 90% threshold, pause instead of stop so SIMKL does not mark as watched prematurely
            scrobble("pause", videoInfo, progress, prefs);
        }
    }

    /**
     * Dispatch scrobble event asynchronously to SIMKL.
     *
     * <p><b>Anime note</b>: SIMKL distinguishes between shows and anime via the item type.
     * Nova's scraper stores TMDB IDs for all series. When SIMKL receives a TMDB show ID
     * it will resolve it internally, including anime series catalogued in TMDB.
     * If the TMDB ID is unavailable or unrecognised by SIMKL, the title + season/episode
     * is used as a fallback for matching. No MAL/AniDB ID is available from the local
     * scraper database, so dedicated anime-id lookup is not supported at this time.</p>
     *
     * <p>On HTTP 401 Unauthorized the method will attempt a one-time token refresh using
     * the stored refresh_token and retry the request automatically.</p>
     *
     * @param action "start", "pause", or "stop"
     * @param videoInfo video information
     * @param progressPercent progress in percentage (0 to 100)
     * @param prefs SharedPreferences to check credentials and refresh token on 401
     */
    public static void scrobble(final String action, final VideoDbInfo videoInfo, final float progressPercent, final SharedPreferences prefs) {
        if (videoInfo == null || prefs == null) return;
        final String token = getAccessToken(prefs);
        if (token == null || token.isEmpty()) return;
        if (!isLiveScrobblingEnabled(prefs)) return;

        // Require a strictly positive valid numeric media ID before scrobbling so unmatched filenames stay local
        long validTmdbId = -1;
        try {
            String rawId = videoInfo.isShow ? videoInfo.scraperShowId : videoInfo.scraperMovieId;
            if (rawId != null && !rawId.trim().isEmpty()) {
                validTmdbId = Long.parseLong(rawId.trim());
            }
        } catch (NumberFormatException ignored) {}

        if (validTmdbId <= 0) {
            if (log.isDebugEnabled()) log.debug("scrobble: skipping media without valid positive online ID: {}", videoInfo.title);
            return;
        }
        final SessionSnapshot session = captureSessionSnapshot(prefs);
        if (session == null || !session.liveScrobbling) return;
        final long tmdbOnlineId = validTmdbId;
        final long actionTimestamp = System.currentTimeMillis();

        sExecutor.execute(() -> {
            final String activeToken = getTokenIfSessionActive(prefs, session.generation);
            if (activeToken == null) {
                if (!wasGenerationWiped(session.generation) && "stop".equals(action) && shouldMarkAsSeen(progressPercent)) {
                    enqueueScrobbleStop(session.accountKey, videoInfo, tmdbOnlineId, actionTimestamp);
                }
                return;
            }
            boolean success = false;
            int lastHttpCode = 0;
            try {
                JSONObject payload = new JSONObject();
                float progressClamped = Math.max(0f, Math.min(100f, progressPercent));
                payload.put("progress", (double) Math.round(progressClamped * 10.0) / 10.0);
                if ("start".equals(action) || "stop".equals(action)) {
                    payload.put("min_progress", SCROBBLE_THRESHOLD);
                }

                if (videoInfo.isShow) {
                    JSONObject showObj = new JSONObject();
                    String showTitle = videoInfo.scraperTitle != null ? videoInfo.scraperTitle : videoInfo.title;
                    if (showTitle != null) showObj.put("title", showTitle);

                    JSONObject idsObj = new JSONObject();
                    idsObj.put("tmdb", tmdbOnlineId);
                    showObj.put("ids", idsObj);
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
                    idsObj.put("tmdb", tmdbOnlineId);
                    movieObj.put("ids", idsObj);
                    payload.put("movie", movieObj);
                }

                String url = getApiBaseUrl() + "/scrobble/" + action;
                String clientId = getClientId();
                RequestBody body = RequestBody.create(payload.toString(), JSON);
                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer " + activeToken)
                        .addHeader("simkl-api-key", clientId != null ? clientId : "")
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build();

                if (log.isDebugEnabled()) log.debug("scrobble {}: sending payload {}", action, payload);

                try (Response response = getHttpClient().newCall(request).execute()) {
                    lastHttpCode = response.code();
                    String respBody = response.body() != null ? response.body().string() : "";
                    if (log.isDebugEnabled()) log.debug("scrobble {} result code={}, body={}", action, response.code(), respBody);
                    if (response.isSuccessful()) {
                        success = true;
                    } else if (response.code() == 401 && isSessionActive(session.generation)) {
                        log.info("scrobble: 401 Unauthorized, attempting token refresh");
                        String newToken = refreshAccessToken(prefs, session.generation, activeToken);
                        if (newToken != null && isSessionActive(session.generation)) {
                            // Retry once with the new token
                            Request retryRequest = request.newBuilder()
                                    .header("Authorization", "Bearer " + newToken)
                                    .build();
                            try (Response retryResponse = getHttpClient().newCall(retryRequest).execute()) {
                                lastHttpCode = retryResponse.code();
                                String retryBody = retryResponse.body() != null ? retryResponse.body().string() : "";
                                if (log.isDebugEnabled()) log.debug("scrobble {} retry result code={}, body={}", action, lastHttpCode, retryBody);
                                success = retryResponse.isSuccessful();
                            }
                        } else {
                            log.warn("scrobble: token refresh failed, scrobble {} dropped", action);
                        }
                    }
                }

                if (wasGenerationWiped(session.generation)) return;
                // If stop action reached the watch threshold and failed due to network/server issue or session switch, enqueue as watched action
                // Only enqueue if transient failure (not permanent 4xx other than 401/408/429)
                boolean isPermanentClientError = (!success && lastHttpCode >= 400 && lastHttpCode < 500 && lastHttpCode != 401 && lastHttpCode != 408 && lastHttpCode != 429);
                if (!success && !isPermanentClientError && "stop".equals(action) && shouldMarkAsSeen(progressPercent)) {
                    enqueueScrobbleStop(session.accountKey, videoInfo, tmdbOnlineId, actionTimestamp);
                }
            } catch (Exception e) {
                log.error("scrobble {} failed with exception", action, e);
                // Also enqueue on connection/timeout exception if stopped with >= threshold
                if (!wasGenerationWiped(session.generation) && "stop".equals(action) && shouldMarkAsSeen(progressPercent)) {
                    enqueueScrobbleStop(session.accountKey, videoInfo, tmdbOnlineId, actionTimestamp);
                }
            }
        });
    }

    private static void enqueueScrobbleStop(String accountKey, VideoDbInfo videoInfo, long tmdbOnlineId, long actionTimestamp) {
        Context ctx = com.archos.environment.ArchosUtils.getGlobalContext();
        if (ctx == null || accountKey == null) return;
        SimklSyncQueue queue = SimklSyncQueue.getInstance(ctx);
        if (videoInfo.isShow) {
            if (videoInfo.scraperSeasonNr >= 0 && videoInfo.scraperEpisodeNr >= 0) {
                queue.enqueueEpisode(accountKey, tmdbOnlineId, videoInfo.scraperSeasonNr, videoInfo.scraperEpisodeNr, true, actionTimestamp);
            }
        } else {
            queue.enqueueMovie(accountKey, tmdbOnlineId, true, actionTimestamp);
        }
        if (log.isDebugEnabled()) log.debug("scrobble stop: network failed, enqueued offline watched action for account={}, tmdbId={}", accountKey, tmdbOnlineId);
    }

    // -------------------------------------------------------------------------
    // Sync history (mark watched / unwatched)
    // -------------------------------------------------------------------------

    public static void markMovieAsWatched(long tmdbId, boolean watched, SharedPreferences prefs) {
        if (tmdbId <= 0) return;
        markMoviesAsWatched(Collections.singletonList(tmdbId), watched, prefs);
    }

    public static void markMoviesAsWatched(List<Long> tmdbIds, boolean watched, SharedPreferences prefs) {
        if (tmdbIds == null || tmdbIds.isEmpty() || prefs == null) return;
        final SessionSnapshot session = captureSessionSnapshot(prefs);
        if (session == null) return;
        final long actionTimestamp = System.currentTimeMillis();

        sExecutor.execute(() -> {
            int syncResult = SYNC_FAILED_RETRYABLE;
            boolean hasValidIds = false;
            for (Long tmdbId : tmdbIds) {
                if (tmdbId != null && tmdbId > 0) {
                    hasValidIds = true;
                    break;
                }
            }
            if (!hasValidIds) return;
            if (isSessionActive(session.generation)) {
                try {
                    JSONObject payload = new JSONObject();
                    JSONArray moviesArr = new JSONArray();
                    for (Long tmdbId : tmdbIds) {
                        if (tmdbId != null && tmdbId > 0) {
                            JSONObject movieObj = new JSONObject();
                            JSONObject idsObj = new JSONObject();
                            idsObj.put("tmdb", tmdbId);
                            movieObj.put("ids", idsObj);
                            moviesArr.put(movieObj);
                        }
                    }
                    payload.put("movies", moviesArr);

                    syncResult = postSyncHistory(payload, watched, prefs, session.generation);
                } catch (Exception e) {
                    log.error("markMoviesAsWatched failed, watched=" + watched, e);
                }
            }
            if (wasGenerationWiped(session.generation)) return;
            Context ctx = com.archos.environment.ArchosUtils.getGlobalContext();
            if (ctx != null && session.accountKey != null) {
                SimklSyncQueue queue = SimklSyncQueue.getInstance(ctx);
                if (syncResult == SYNC_FAILED_RETRYABLE) {
                    for (Long tmdbId : tmdbIds) {
                        if (tmdbId != null && tmdbId > 0) {
                            queue.enqueueMovie(session.accountKey, tmdbId, watched, actionTimestamp);
                        }
                    }
                } else {
                    for (Long tmdbId : tmdbIds) {
                        if (tmdbId != null && tmdbId > 0) {
                            queue.removeActionIfOlderOrEqual(session.accountKey, SimklSyncQueue.TYPE_MOVIE, tmdbId, -1, "", actionTimestamp);
                        }
                    }
                }
            }
        });
    }

    public static void markShowAsWatched(long showTmdbId, boolean watched, SharedPreferences prefs) {
        if (showTmdbId <= 0 || prefs == null) return;
        final SessionSnapshot session = captureSessionSnapshot(prefs);
        if (session == null) return;
        final long actionTimestamp = System.currentTimeMillis();

        sExecutor.execute(() -> {
            int syncResult = SYNC_FAILED_RETRYABLE;
            if (isSessionActive(session.generation)) {
                try {
                    JSONObject payload = new JSONObject();
                    JSONArray showsArr = new JSONArray();
                    JSONObject showObj = new JSONObject();
                    JSONObject idsObj = new JSONObject();
                    idsObj.put("tmdb", showTmdbId);
                    showObj.put("ids", idsObj);
                    showsArr.put(showObj);
                    payload.put("shows", showsArr);

                    syncResult = postSyncHistory(payload, watched, prefs, session.generation);
                } catch (Exception e) {
                    log.error("markShowAsWatched failed, watched=" + watched, e);
                }
            }
            if (wasGenerationWiped(session.generation)) return;
            Context ctx = com.archos.environment.ArchosUtils.getGlobalContext();
            if (ctx != null && session.accountKey != null) {
                SimklSyncQueue queue = SimklSyncQueue.getInstance(ctx);
                if (syncResult == SYNC_FAILED_RETRYABLE) {
                    queue.enqueueShow(session.accountKey, showTmdbId, watched, actionTimestamp);
                } else {
                    queue.removeActionIfOlderOrEqual(session.accountKey, SimklSyncQueue.TYPE_SHOW, showTmdbId, -1, "", actionTimestamp);
                    queue.removeShowEpisodesIfOlderOrEqual(session.accountKey, showTmdbId, actionTimestamp);
                }
            }
        });
    }

    public static void markSeasonEpisodesAsWatched(long showTmdbId, int seasonNumber, List<Integer> episodeNumbers, boolean watched, SharedPreferences prefs) {
        if (showTmdbId <= 0 || seasonNumber < 0 || episodeNumbers == null || episodeNumbers.isEmpty() || prefs == null) return;
        final SessionSnapshot session = captureSessionSnapshot(prefs);
        if (session == null) return;
        final long actionTimestamp = System.currentTimeMillis();

        sExecutor.execute(() -> {
            int syncResult = SYNC_FAILED_RETRYABLE;
            boolean hasValidEpisodes = false;
            for (Integer epNum : episodeNumbers) {
                if (epNum != null && epNum >= 0) {
                    hasValidEpisodes = true;
                    break;
                }
            }
            if (!hasValidEpisodes) return;
            if (isSessionActive(session.generation)) {
                try {
                    JSONObject payload = new JSONObject();
                    JSONArray showsArr = new JSONArray();
                    JSONObject showObj = new JSONObject();
                    JSONObject idsObj = new JSONObject();
                    idsObj.put("tmdb", showTmdbId);
                    showObj.put("ids", idsObj);
                    showObj.put("use_tvdb_anime_seasons", true);

                    JSONArray seasonsArr = new JSONArray();
                    JSONObject seasonObj = new JSONObject();
                    seasonObj.put("number", seasonNumber);

                    JSONArray epArr = new JSONArray();
                    for (Integer epNum : episodeNumbers) {
                        if (epNum != null && epNum >= 0) {
                            JSONObject epObj = new JSONObject();
                            epObj.put("number", epNum);
                            epArr.put(epObj);
                        }
                    }
                    seasonObj.put("episodes", epArr);

                    seasonsArr.put(seasonObj);
                    showObj.put("seasons", seasonsArr);

                    showsArr.put(showObj);
                    payload.put("shows", showsArr);

                    syncResult = postSyncHistory(payload, watched, prefs, session.generation);
                } catch (Exception e) {
                    log.error("markSeasonEpisodesAsWatched failed, watched=" + watched, e);
                }
            }
            if (wasGenerationWiped(session.generation)) return;
            Context ctx = com.archos.environment.ArchosUtils.getGlobalContext();
            if (ctx != null && session.accountKey != null) {
                SimklSyncQueue queue = SimklSyncQueue.getInstance(ctx);
                for (Integer epNum : episodeNumbers) {
                    if (epNum != null && epNum >= 0) {
                        if (syncResult == SYNC_FAILED_RETRYABLE) {
                            queue.enqueueEpisode(session.accountKey, showTmdbId, seasonNumber, epNum, watched, actionTimestamp);
                        } else {
                            queue.removeActionIfOlderOrEqual(session.accountKey, SimklSyncQueue.TYPE_EPISODE, showTmdbId, seasonNumber, String.valueOf(epNum), actionTimestamp);
                        }
                    }
                }
            }
        });
    }

    public static void markSeasonAsWatched(long showTmdbId, int seasonNumber, boolean watched, SharedPreferences prefs) {
        if (showTmdbId <= 0 || seasonNumber < 0 || prefs == null) return;
        final SessionSnapshot session = captureSessionSnapshot(prefs);
        if (session == null) return;

        sExecutor.execute(() -> {
            if (!isSessionActive(session.generation)) return;
            try {
                JSONObject payload = new JSONObject();
                JSONArray showsArr = new JSONArray();
                JSONObject showObj = new JSONObject();
                JSONObject idsObj = new JSONObject();
                idsObj.put("tmdb", showTmdbId);
                showObj.put("ids", idsObj);
                showObj.put("use_tvdb_anime_seasons", true);

                JSONArray seasonsArr = new JSONArray();
                JSONObject seasonObj = new JSONObject();
                seasonObj.put("number", seasonNumber);
                seasonsArr.put(seasonObj);
                showObj.put("seasons", seasonsArr);

                showsArr.put(showObj);
                payload.put("shows", showsArr);

                postSyncHistory(payload, watched, prefs, session.generation);
            } catch (Exception e) {
                log.error("markSeasonAsWatched failed, watched=" + watched, e);
            }
        });
    }

    public static void markEpisodeAsWatched(long showTmdbId, int seasonNumber, int episodeNumber, boolean watched, SharedPreferences prefs) {
        if (showTmdbId <= 0 || seasonNumber < 0 || episodeNumber < 0 || prefs == null) return;
        final SessionSnapshot session = captureSessionSnapshot(prefs);
        if (session == null) return;
        final long actionTimestamp = System.currentTimeMillis();

        sExecutor.execute(() -> {
            int syncResult = SYNC_FAILED_RETRYABLE;
            if (isSessionActive(session.generation)) {
                try {
                    JSONObject payload = new JSONObject();
                    JSONArray showsArr = new JSONArray();
                    JSONObject showObj = new JSONObject();
                    JSONObject idsObj = new JSONObject();
                    idsObj.put("tmdb", showTmdbId);
                    showObj.put("ids", idsObj);
                    showObj.put("use_tvdb_anime_seasons", true);

                    JSONArray seasonsArr = new JSONArray();
                    JSONObject seasonObj = new JSONObject();
                    seasonObj.put("number", seasonNumber);

                    JSONArray epArr = new JSONArray();
                    JSONObject epObj = new JSONObject();
                    epObj.put("number", episodeNumber);
                    epArr.put(epObj);
                    seasonObj.put("episodes", epArr);

                    seasonsArr.put(seasonObj);
                    showObj.put("seasons", seasonsArr);

                    showsArr.put(showObj);
                    payload.put("shows", showsArr);

                    syncResult = postSyncHistory(payload, watched, prefs, session.generation);
                } catch (Exception e) {
                    log.error("markEpisodeAsWatched failed, watched=" + watched, e);
                }
            }
            if (wasGenerationWiped(session.generation)) return;
            Context ctx = com.archos.environment.ArchosUtils.getGlobalContext();
            if (ctx != null && session.accountKey != null) {
                SimklSyncQueue queue = SimklSyncQueue.getInstance(ctx);
                if (syncResult == SYNC_FAILED_RETRYABLE) {
                    queue.enqueueEpisode(session.accountKey, showTmdbId, seasonNumber, episodeNumber, watched, actionTimestamp);
                } else {
                    queue.removeActionIfOlderOrEqual(session.accountKey, SimklSyncQueue.TYPE_EPISODE, showTmdbId, seasonNumber, String.valueOf(episodeNumber), actionTimestamp);
                }
            }
        });
    }

    /**
     * POST to /sync/history (mark watched) or /sync/history/remove (explicit removal).
     *
     * <p><b>Watchlist warning</b>: SIMKL's /sync/history/remove endpoint removes an item
     * from BOTH the watch history AND the Watchlist ("Plan to Watch"). Ordinary unmark in
     * Nova should stay local to preserve Watchlist membership.</p>
     *
     * <p>On HTTP 401 Unauthorized the method will attempt a one-time token refresh using
     * the stored refresh_token and retry the request automatically.</p>
     */
    public static final int SYNC_PARTIAL_NOT_FOUND = 2;
    public static final int SYNC_SUCCESS = 1;
    public static final int SYNC_FAILED_RETRYABLE = 0;
    public static final int SYNC_FAILED_PERMANENT = -1;

    private static boolean hasNonEmptyArray(JSONObject parent, String key) {
        if (parent == null) return false;
        JSONArray arr = parent.optJSONArray(key);
        return arr != null && arr.length() > 0;
    }

    /**
     * Classifies a 2xx /sync/history or /sync/history/remove body into full success,
     * partial success with unmatched items in not_found, or permanent failure when nothing matched.
     */
    static int analyzeSyncResponse(String respBody, boolean watched) {
        if (respBody == null || respBody.trim().isEmpty()) return SYNC_SUCCESS;
        try {
            JSONObject json = new JSONObject(respBody);
            JSONObject counts = json.optJSONObject(watched ? "added" : "deleted");
            JSONObject notFound = json.optJSONObject("not_found");
            if (notFound == null) return SYNC_SUCCESS;
            boolean anyNotFound = hasNonEmptyArray(notFound, "movies")
                    || hasNonEmptyArray(notFound, "shows")
                    || hasNonEmptyArray(notFound, "episodes");
            if (!anyNotFound) return SYNC_SUCCESS;
            int total = counts != null
                    ? counts.optInt("movies", 0) + counts.optInt("shows", 0) + counts.optInt("episodes", 0)
                    : 0;
            return total == 0 ? SYNC_FAILED_PERMANENT : SYNC_PARTIAL_NOT_FOUND;
        } catch (JSONException ignored) {
            return SYNC_SUCCESS;
        }
    }

    /**
     * Classifies a 2xx /sync/history or /sync/history/remove body and logs unmatched items.
     */
    private static int classifyAndLogSyncResponse(String respBody, boolean watched, String contextLabel) {
        int outcome = analyzeSyncResponse(respBody, watched);
        if (outcome == SYNC_FAILED_PERMANENT) {
            log.warn("{}: SIMKL returned 2xx with added/deleted=0 and not_found: {}", contextLabel, respBody);
        } else if (outcome == SYNC_PARTIAL_NOT_FOUND) {
            log.warn("{}: SIMKL returned 2xx with partial success and not_found items: {}", contextLabel, respBody);
        }
        return outcome;
    }

    private static String formatIsoUtc(long epochMs) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date(epochMs));
    }

    /**
     * Executes the sync history HTTP POST.
     * @return SYNC_SUCCESS (1) on 2xx with all items matched,
     *         SYNC_PARTIAL_NOT_FOUND (2) on 2xx with some matched items and some in not_found,
     *         SYNC_FAILED_PERMANENT (-1) on permanent client error (400/403/404) or 2xx with added/deleted=0 and not_found,
     *         SYNC_FAILED_RETRYABLE (0) on network drops, timeouts, 5xx server issues, or session change.
     */
    private static int postSyncHistory(JSONObject payload, boolean watched, SharedPreferences prefs, long expectedGeneration) {
        String token = getTokenIfSessionActive(prefs, expectedGeneration);
        if (token == null) return SYNC_FAILED_RETRYABLE;
        String url = getApiBaseUrl() + (watched ? "/sync/history" : "/sync/history/remove");
        String clientId = getClientId();

        RequestBody body = RequestBody.create(payload.toString(), JSON);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("simkl-api-key", clientId != null ? clientId : "")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        if (log.isDebugEnabled()) log.debug("postSyncHistory (watched={}): sending payload {}", watched, payload);

        try (Response response = getHttpClient().newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            int code = response.code();
            if (log.isDebugEnabled()) log.debug("postSyncHistory result code={}, body={}", code, respBody);

            if (response.isSuccessful()) {
                return classifyAndLogSyncResponse(respBody, watched, "postSyncHistory");
            } else if (code == 401 && isSessionActive(expectedGeneration)) {
                log.info("postSyncHistory: 401 Unauthorized, attempting token refresh");
                String newToken = refreshAccessToken(prefs, expectedGeneration, token);
                if (newToken != null && isSessionActive(expectedGeneration)) {
                    Request retryRequest = request.newBuilder()
                            .header("Authorization", "Bearer " + newToken)
                            .build();
                    try (Response retryResponse = getHttpClient().newCall(retryRequest).execute()) {
                        String retryBody = retryResponse.body() != null ? retryResponse.body().string() : "";
                        int retryCode = retryResponse.code();
                        if (log.isDebugEnabled()) log.debug("postSyncHistory retry result code={}, body={}", retryCode, retryBody);
                        if (retryResponse.isSuccessful()) {
                            return classifyAndLogSyncResponse(retryBody, watched, "postSyncHistory retry");
                        }
                        if (retryCode >= 400 && retryCode < 500 && retryCode != 401 && retryCode != 408 && retryCode != 429) {
                            return SYNC_FAILED_PERMANENT;
                        }
                        return SYNC_FAILED_RETRYABLE;
                    }
                } else {
                    log.warn("postSyncHistory: token refresh failed, action will remain pending for retry");
                    return SYNC_FAILED_RETRYABLE;
                }
            } else if (code >= 400 && code < 500 && code != 408 && code != 429 && code != 401) {
                // Permanent client errors (400, 403, 404, etc.) should not block the queue forever (408 Request Timeout and 429 Rate Limit are transient)
                log.error("postSyncHistory: permanent client error code={}", code);
                return SYNC_FAILED_PERMANENT;
            } else {
                // 5xx server errors, 429 rate limit, or 401 after session change are transient/retryable
                return SYNC_FAILED_RETRYABLE;
            }
        } catch (Exception e) {
            log.error("postSyncHistory failed with exception (retryable network issue)", e);
            return SYNC_FAILED_RETRYABLE;
        }
    }

    /**
     * Drains the pending offline queue and sends stored actions to SIMKL.
     * Safely runs in the background single-thread executor.
     */
    public static void flushPendingActions(final Context context, final SharedPreferences prefs) {
        if (context == null || prefs == null) return;
        final SessionSnapshot session = captureSessionSnapshot(prefs);
        if (session == null || session.accountKey == null) return;

        sExecutor.execute(() -> {
            if (!isSessionActive(session.generation)) return;
            SimklSyncQueue queue = SimklSyncQueue.getInstance(context);
            List<SimklSyncQueue.PendingAction> pending = queue.getPendingActionsForAccount(session.accountKey);
            if (pending == null || pending.isEmpty()) return;

            log.info("flushPendingActions: processing {} pending SIMKL actions for account={}", pending.size(), session.accountKey);
            for (SimklSyncQueue.PendingAction action : pending) {
                if (!isSessionActive(session.generation)) {
                    log.info("flushPendingActions: session changed, stopping flush for account={}", session.accountKey);
                    break;
                }
                int syncResult = SYNC_FAILED_RETRYABLE;
                try {
                    String watchedAtIso = action.watched ? formatIsoUtc(action.timestamp) : null;
                    JSONObject payload = new JSONObject();
                    if (SimklSyncQueue.TYPE_MOVIE.equals(action.itemType)) {
                        JSONArray moviesArr = new JSONArray();
                        JSONObject movieObj = new JSONObject();
                        JSONObject idsObj = new JSONObject();
                        idsObj.put("tmdb", action.tmdbId);
                        movieObj.put("ids", idsObj);
                        if (watchedAtIso != null) movieObj.put("watched_at", watchedAtIso);
                        moviesArr.put(movieObj);
                        payload.put("movies", moviesArr);
                    } else if (SimklSyncQueue.TYPE_SHOW.equals(action.itemType)) {
                        JSONArray showsArr = new JSONArray();
                        JSONObject showObj = new JSONObject();
                        JSONObject idsObj = new JSONObject();
                        idsObj.put("tmdb", action.tmdbId);
                        showObj.put("ids", idsObj);
                        if (watchedAtIso != null) showObj.put("watched_at", watchedAtIso);
                        showsArr.put(showObj);
                        payload.put("shows", showsArr);
                    } else if (SimklSyncQueue.TYPE_SEASON.equals(action.itemType) || SimklSyncQueue.TYPE_EPISODE.equals(action.itemType)) {
                        JSONArray showsArr = new JSONArray();
                        JSONObject showObj = new JSONObject();
                        JSONObject idsObj = new JSONObject();
                        idsObj.put("tmdb", action.tmdbId);
                        showObj.put("ids", idsObj);
                        showObj.put("use_tvdb_anime_seasons", true);

                        JSONArray seasonsArr = new JSONArray();
                        JSONObject seasonObj = new JSONObject();
                        seasonObj.put("number", action.season);

                        JSONArray epArr = new JSONArray();
                        if (action.episodes != null && !action.episodes.isEmpty()) {
                            String[] eps = action.episodes.split(",");
                            for (String epStr : eps) {
                                try {
                                    int epNum = Integer.parseInt(epStr.trim());
                                    JSONObject epObj = new JSONObject();
                                    epObj.put("number", epNum);
                                    if (watchedAtIso != null) epObj.put("watched_at", watchedAtIso);
                                    epArr.put(epObj);
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                        if (epArr.length() > 0) {
                            seasonObj.put("episodes", epArr);
                            seasonsArr.put(seasonObj);
                            showObj.put("seasons", seasonsArr);
                            showsArr.put(showObj);
                            payload.put("shows", showsArr);
                        }
                    }

                    if (payload.length() > 0) {
                        syncResult = postSyncHistory(payload, action.watched, prefs, session.generation);
                    }
                } catch (Exception e) {
                    log.error("flushPendingActions: failed to execute action id=" + action.id, e);
                }

                if (wasGenerationWiped(session.generation)) {
                    log.info("flushPendingActions: session wiped during request, aborting queue removal");
                    break;
                }
                if (syncResult == SYNC_SUCCESS) {
                    queue.removeActionIfUnchanged(action.id, action.timestamp);
                    if (log.isDebugEnabled()) log.debug("flushPendingActions: successfully synced and removed action id={}", action.id);
                } else if (syncResult == SYNC_FAILED_PERMANENT || syncResult == SYNC_PARTIAL_NOT_FOUND) {
                    queue.removeActionIfUnchanged(action.id, action.timestamp);
                    log.error("flushPendingActions: action id={} finished with non-retryable failure (status={}), dropped from queue to unblock queue", action.id, syncResult);
                } else {
                    log.warn("flushPendingActions: action id={} encountered transient network/server issue, stopping batch until connectivity is restored", action.id);
                    break; // stop on transient network failure to preserve chronological order
                }
            }
        });
    }
}
