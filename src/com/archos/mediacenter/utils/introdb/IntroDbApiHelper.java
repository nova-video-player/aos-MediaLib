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

package com.archos.mediacenter.utils.introdb;

import android.content.Context;

import com.archos.mediascraper.ScraperCache;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

// TheIntroDB api helper, see https://theintrodb.org/docs (openapi v3).
// GET-only (no api key required for /media). Modeled on OpenSubtitlesApiHelper,
// with the OkHttp disk-cache + trace logging trick from MyTmdb for easier debug.
public class IntroDbApiHelper {

    private static final Logger log = LoggerFactory.getLogger(IntroDbApiHelper.class);

    private static volatile IntroDbApiHelper sInstance;

    private static final String API_BASE_URL = "https://api.theintrodb.org/v3/";
    private static final String USER_AGENT = "User-Agent";
    private static final String USER_AGENT_VALUE = "novavideoplayer";

    public static final int RESULT_CODE_OK = 200;
    public static final int RESULT_CODE_BAD_REQUEST = 400;
    public static final int RESULT_CODE_NOT_FOUND = 404;
    public static final int RESULT_CODE_TOO_MANY_REQUESTS = 429;
    public static final int RESULT_CODE_SERVER_ISSUE = 500;

    private static int LAST_QUERY_RESULT = RESULT_CODE_OK;
    private static String LAST_QUERY_MESSAGE = "";

    private static OkHttpClient httpClient;
    private static String baseUrl;

    private IntroDbApiHelper(Cache cache) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        // MyTmdb trick: shared disk cache + cache-control rewrite (skips caching 404/401),
        // plus an optional cache-hit logger when tracing.
        if (cache != null) {
            builder.cache(cache).addNetworkInterceptor(new ScraperCache.CacheInterceptor());
            if (log.isTraceEnabled()) builder.addInterceptor(new ScraperCache.isCacheResponding());
        }
        if (log.isTraceEnabled()) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            builder.addNetworkInterceptor(logging);
        }
        builder.connectTimeout(ScraperCache.CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        builder.readTimeout(ScraperCache.READ_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        httpClient = builder.build();
        baseUrl = API_BASE_URL;
    }

    // Initialize with a context to enable the shared OkHttp disk cache (recommended).
    public static void init(Context context) {
        synchronized (IntroDbApiHelper.class) {
            sInstance = new IntroDbApiHelper(ScraperCache.getCache(context.getApplicationContext()));
        }
    }

    // Falls back to a no-cache client if init(Context) was never called.
    public static IntroDbApiHelper getInstance() {
        if (sInstance == null) {
            synchronized (IntroDbApiHelper.class) {
                if (sInstance == null) sInstance = new IntroDbApiHelper(null);
            }
        }
        return sInstance;
    }

    public static int getLastQueryResult() { return LAST_QUERY_RESULT; }
    public static String getLastQueryMessage() { return LAST_QUERY_MESSAGE; }

    // Query GET /media. Returns null on error or when no usable data was returned.
    // The API uses ids in priority order tmdb_id -> tvdb_id -> imdb_id.
    public static IntroDbResult getMedia(IntroDbQueryParams params) throws IOException {
        getInstance();
        if (params == null || !params.hasIdentifier()) {
            log.warn("getMedia: no identifier provided");
            LAST_QUERY_RESULT = RESULT_CODE_BAD_REQUEST;
            return null;
        }
        if (log.isDebugEnabled()) log.debug("getMedia: {}", params);

        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + "media").newBuilder();
        if (params.getTmdbId() != null) urlBuilder.addQueryParameter("tmdb_id", String.valueOf(params.getTmdbId()));
        if (params.getTvdbId() != null) urlBuilder.addQueryParameter("tvdb_id", String.valueOf(params.getTvdbId()));
        if (params.getImdbId() != null) urlBuilder.addQueryParameter("imdb_id", params.getImdbId());
        if (params.isShow()) {
            if (params.getSeason() != null) urlBuilder.addQueryParameter("season", String.valueOf(params.getSeason()));
            if (params.getEpisode() != null) urlBuilder.addQueryParameter("episode", String.valueOf(params.getEpisode()));
        }
        if (params.getDurationMs() != null && params.getDurationMs() > 0)
            urlBuilder.addQueryParameter("duration_ms", String.valueOf(params.getDurationMs()));
        String url = urlBuilder.build().toString();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader(USER_AGENT, USER_AGENT_VALUE)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            parseResponse(response);
            if (!response.isSuccessful()) {
                if (LAST_QUERY_RESULT == RESULT_CODE_NOT_FOUND)
                    log.debug("getMedia: no data for {} (404)", params);
                else
                    log.warn("getMedia: not successful, code={}, message={}", LAST_QUERY_RESULT, LAST_QUERY_MESSAGE);
                return null;
            }
            String responseBody = response.body().string();
            try {
                JSONObject json = new JSONObject(responseBody);
                IntroDbResult result = parseMedia(json);
                if (log.isDebugEnabled()) log.debug("getMedia: parsed {}", result);
                return result;
            } catch (JSONException e) {
                log.error("getMedia: caught JSONException", e);
            }
        }
        return null;
    }

    private static IntroDbResult parseMedia(JSONObject json) {
        IntroDbResult result = new IntroDbResult();
        result.setType(json.optString("type", null));
        if (json.has("tmdb_id") && !json.isNull("tmdb_id")) result.setTmdbId(json.optInt("tmdb_id"));
        if (json.has("season") && !json.isNull("season")) result.setSeason(json.optInt("season"));
        if (json.has("episode") && !json.isNull("episode")) result.setEpisode(json.optInt("episode"));
        parseSegments(json, "intro", result.getIntro());
        parseSegments(json, "recap", result.getRecap());
        parseSegments(json, "credits", result.getCredits());
        parseSegments(json, "preview", result.getPreview());
        return result;
    }

    private static void parseSegments(JSONObject root, String key, List<IntroDbResult.Segment> out) {
        if (!root.has(key) || root.isNull(key)) return;
        JSONArray array = root.optJSONArray(key);
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONObject segment = array.optJSONObject(i);
            if (segment == null) continue;
            out.add(new IntroDbResult.Segment(
                    optNullableLong(segment, "start_ms"),
                    optNullableLong(segment, "end_ms")));
        }
    }

    private static Long optNullableLong(JSONObject object, String key) {
        if (object == null || !object.has(key) || object.isNull(key)) return null;
        return object.optLong(key);
    }

    private static int parseResponse(Response response) {
        if (response == null) {
            log.warn("parseResponse: response is null");
            LAST_QUERY_RESULT = RESULT_CODE_SERVER_ISSUE;
            return LAST_QUERY_RESULT;
        }
        int status = response.code();
        LAST_QUERY_MESSAGE = response.message();
        if (status != 200) log.warn("parseResponse: status={}, message={}", status, LAST_QUERY_MESSAGE);
        else if (log.isTraceEnabled()) log.trace("parseResponse: status={}, message={}", status, LAST_QUERY_MESSAGE);
        switch (status) {
            case 200 -> LAST_QUERY_RESULT = RESULT_CODE_OK;
            case 400 -> LAST_QUERY_RESULT = RESULT_CODE_BAD_REQUEST;
            case 404 -> LAST_QUERY_RESULT = RESULT_CODE_NOT_FOUND;
            case 429 -> LAST_QUERY_RESULT = RESULT_CODE_TOO_MANY_REQUESTS;
            default -> LAST_QUERY_RESULT = (status >= 500 && status <= 599) ? RESULT_CODE_SERVER_ISSUE : status;
        }
        return LAST_QUERY_RESULT;
    }
}
