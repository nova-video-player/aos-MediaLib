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

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

// introdb.app api helper, see https://introdb.app/docs/api.
// GET-only (no api key required for /segments). Sibling of IntroDbApiHelper
// (theintrodb.org); same MyTmdb debug trick (shared disk cache + trace logging).
// Reads are imdb_id + season + episode, so this provider is TV-only for lookups.
public class IntroDbAppApiHelper {

    private static final Logger log = LoggerFactory.getLogger(IntroDbAppApiHelper.class);

    private static volatile IntroDbAppApiHelper sInstance;

    private static final String API_BASE_URL = "https://api.introdb.app/";
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

    private IntroDbAppApiHelper(Cache cache) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
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

    public static void init(Context context) {
        synchronized (IntroDbAppApiHelper.class) {
            sInstance = new IntroDbAppApiHelper(ScraperCache.getCache(context.getApplicationContext()));
        }
    }

    public static IntroDbAppApiHelper getInstance() {
        if (sInstance == null) {
            synchronized (IntroDbAppApiHelper.class) {
                if (sInstance == null) sInstance = new IntroDbAppApiHelper(null);
            }
        }
        return sInstance;
    }

    public static int getLastQueryResult() { return LAST_QUERY_RESULT; }
    public static String getLastQueryMessage() { return LAST_QUERY_MESSAGE; }

    // Query GET /segments. Returns null on error or when no usable data was returned.
    public static IntroDbAppResult getSegments(IntroDbAppQueryParams params) throws IOException {
        getInstance();
        if (params == null || !params.isValid()) {
            log.warn("getSegments: invalid params {}", params);
            LAST_QUERY_RESULT = RESULT_CODE_BAD_REQUEST;
            return null;
        }
        if (log.isDebugEnabled()) log.debug("getSegments: {}", params);

        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + "segments").newBuilder();
        urlBuilder.addQueryParameter("imdb_id", params.getImdbId());
        urlBuilder.addQueryParameter("season", String.valueOf(params.getSeason()));
        urlBuilder.addQueryParameter("episode", String.valueOf(params.getEpisode()));
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
                    log.debug("getSegments: no data for {} (404)", params);
                else
                    log.warn("getSegments: not successful, code={}, message={}", LAST_QUERY_RESULT, LAST_QUERY_MESSAGE);
                return null;
            }
            String responseBody = response.body().string();
            try {
                JSONObject json = new JSONObject(responseBody);
                IntroDbAppResult result = parseSegments(json);
                if (log.isDebugEnabled()) log.debug("getSegments: parsed {}", result);
                return result;
            } catch (JSONException e) {
                log.error("getSegments: caught JSONException", e);
            }
        }
        return null;
    }

    private static IntroDbAppResult parseSegments(JSONObject json) {
        IntroDbAppResult result = new IntroDbAppResult();
        result.setImdbId(json.optString("imdb_id", null));
        if (json.has("season") && !json.isNull("season")) result.setSeason(json.optInt("season"));
        if (json.has("episode") && !json.isNull("episode")) result.setEpisode(json.optInt("episode"));
        result.setIntro(parseSegment(json.optJSONObject("intro")));
        result.setRecap(parseSegment(json.optJSONObject("recap")));
        result.setOutro(parseSegment(json.optJSONObject("outro")));
        return result;
    }

    private static IntroDbAppResult.Segment parseSegment(JSONObject segment) {
        if (segment == null) return null;
        // start_ms / end_ms are always provided alongside the _sec variants
        long startMs = segment.optLong("start_ms", -1);
        long endMs = segment.optLong("end_ms", -1);
        if (startMs < 0 || endMs < 0 || endMs <= startMs) return null;
        return new IntroDbAppResult.Segment(
                startMs,
                endMs,
                segment.optDouble("confidence", 0),
                segment.optInt("submission_count", 0));
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
