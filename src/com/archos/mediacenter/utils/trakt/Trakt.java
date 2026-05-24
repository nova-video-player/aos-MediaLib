// Copyright 2017 Archos SA
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


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import androidx.preference.PreferenceManager;

import com.archos.environment.ArchosUtils;
import com.archos.mediacenter.utils.trakt.Trakt.Result.ObjectType;
import com.archos.mediacenter.utils.trakt.TraktAPI.AuthParam;
import com.archos.mediacenter.utils.trakt.TraktAPI.MovieWatchingParam;
import com.archos.mediacenter.utils.trakt.TraktAPI.ShowWatchingParam;
import com.archos.mediacenter.utils.videodb.VideoDbInfo;
import com.archos.medialib.R;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.AutoScrapeService;
import com.uwetrottmann.trakt5.TraktV2;
import com.uwetrottmann.trakt5.entities.AccessToken;
import com.uwetrottmann.trakt5.entities.BaseMovie;
import com.uwetrottmann.trakt5.entities.BaseShow;
import com.uwetrottmann.trakt5.entities.EpisodeIds;
import com.uwetrottmann.trakt5.entities.HistoryEntry;
import com.uwetrottmann.trakt5.entities.LastActivities;
import com.uwetrottmann.trakt5.entities.ListEntry;
import com.uwetrottmann.trakt5.entities.MovieIds;
import com.uwetrottmann.trakt5.entities.PlaybackResponse;
import com.uwetrottmann.trakt5.entities.ScrobbleProgress;
import com.uwetrottmann.trakt5.entities.SyncEpisode;
import com.uwetrottmann.trakt5.entities.SyncItems;
import com.uwetrottmann.trakt5.entities.SyncMovie;
import com.uwetrottmann.trakt5.entities.SyncResponse;
import com.uwetrottmann.trakt5.entities.TraktList;
import com.uwetrottmann.trakt5.entities.UserSlug;
import com.uwetrottmann.trakt5.enums.Extended;
import com.uwetrottmann.trakt5.enums.ExtendedShowsWatched;
import com.uwetrottmann.trakt5.enums.ListPrivacy;
import com.uwetrottmann.trakt5.enums.Specials;

import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threeten.bp.Instant;
import org.threeten.bp.OffsetDateTime;
import org.threeten.bp.ZoneOffset;
import org.threeten.bp.format.DateTimeFormatter;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;


public class Trakt {
    private static final Logger log = LoggerFactory.getLogger(Trakt.class);

    public static final long ASK_RELOG_FREQUENCY = 1000 * 60 * 60 * 6; // every 6 hours
    public static long sLastTraktRefreshToken = 0; //will be set by activities, representing last time a user has been asked to log again in trakt;
    public static final String TRAKT_ISSUE_REFRESH_TOKEN = "TRAKT_ISSUE_REFRESH_TOKEN";
    private static String API_KEY;
    private static String API_SECRET;
    public static final int SCROBBLE_THRESHOLD = 90;
    /** Minimum progress percentage to record as a Trakt resume point: ignore accidental short plays. */
    public static final float RESUME_THRESHOLD_PERCENT = 1.0f;
    /** Minimum play duration in milliseconds to record as a Trakt resume point (30 seconds). */
    public static final int RESUME_THRESHOLD_MS = 30000;
    // playback history size to synchronize: 50 is enough (it is anyway capped at 1k and incurs a huge processing delay)
    public static final int PLAYBACK_HISTORY_SIZE = 200;
    // page size for paginated endpoints (watched/collection/list); 1000 is the Trakt hard cap
    private static final int PAGE_LIMIT = 1000;

    private static final String XML_PREFIX = ".trakt_";
    private static final String XML_SUFFIX = "_db.xml";
    private static final int MAX_TRIAL = 3;
    public static final String ACTION_LIBRARY = "library";
    public static final String ACTION_UNLIBRARY = "unlibrary";
    public static final String ACTION_SEEN = "seen";
    public static final String ACTION_UNSEEN = "unseen";
    public static final String ACTIONS[] = new String[]{ACTION_LIBRARY, ACTION_UNLIBRARY, ACTION_SEEN, ACTION_UNSEEN};

    public static final String LIBRARY_COLLECTION = "collection";
    public static final String LIBRARY_WATCHED = "watched";
    public static final String LIBRARIES[] = new String[]{LIBRARY_COLLECTION, LIBRARY_WATCHED};
    private static final String KEY_TRAKT_REFRESH_TOKEN = "trakt_refresh_token";
    public static final String KEY_TRAKT_USER = "trakt_user";
    public static final String KEY_TRAKT_SHA1 = "trakt_sha1";
    public static final String KEY_TRAKT_ACCESS_TOKEN = "trakt_access_token";
    public static final String KEY_TRAKT_LIVE_SCROBBLING = "trakt_live_scrobbling";
    private static final String KEY_TRAKT_SYNC_FLAG = "trakt_sync_flag";
    private static final String KEY_TRAKT_SYNC_RESUME = "trakt_sync_resume";
    private static final String KEY_TRAKT_LAST_TIME_SHOW_WATCHED = "trakt_last_time_show_watched";
    private static final String KEY_TRAKT_LAST_TIME_MOVIE_WATCHED = "trakt_last_time_movie_watched";
    private static final String KEY_TRAKT_SYNC_COLLECTION = "trakt_sync_collection";
    private static final String KEY_TRAKT_ACCOUNT_LOCKED = "trakt_account_locked";

    public static final int TRAKT_DB_MARKED = 1;
    public static final int TRAKT_DB_UNMARK = 2;

    public static final int WATCHING_DELAY_MS = 600000; // 10 min
    // Back off a bit more between retries on non-auth endpoints to avoid hammering Trakt
    private static final long WAIT_BEFORE_NEXT_TRIAL = 2000;

    // Custom redirect URI registered in Trakt client (also keep http://localhost registered for legacy)
    private static final String REDIRECT_URI = "nova.trakt://auth";

    private final Context mContext;

    private Listener mListener;

    private boolean mWatchingSuccess = false;

    private Exception mLastExecException = null;

    static class MyTraktV2 extends TraktV2 {

        public MyTraktV2(String apiKey) {
            super(apiKey);
        }

        public MyTraktV2(String apiKey, String clientSecret, String redirectUri) {
            super(apiKey, clientSecret, redirectUri);
        }

        @Override
        protected void setOkHttpClientDefaults(OkHttpClient.Builder builder) {
            super.setOkHttpClientDefaults(builder);
            if (log.isTraceEnabled()) {
                HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
                logging.setLevel(HttpLoggingInterceptor.Level.BODY);
                builder.addNetworkInterceptor(logging).addInterceptor(logging);
            }
        }
    }

    private static MyTraktV2 mTraktV2;

    public static void initApiKeys(Context context){
        API_KEY = context.getString(R.string.trakt_api_key);
        API_SECRET = context.getString(R.string.trakt_api_secret);
    }

    static public String getAction(String library) {
        if (library.equals(LIBRARY_WATCHED))
            return ACTION_SEEN;
        else if (library.equals(LIBRARY_COLLECTION))
            return ACTION_LIBRARY;
        else
            return null;
    }
    static public String getAction(String library, boolean toMark) {
        if (library.equals(LIBRARY_WATCHED))
            return toMark ? ACTION_SEEN : ACTION_UNSEEN;
        else if (library.equals(LIBRARY_COLLECTION))
            return toMark ? ACTION_LIBRARY : ACTION_UNLIBRARY;
        else
            return null;
    }

    static private final class CallbackLock {
        boolean ready = false;
        Result result;

        public void notify(Result result) {
            synchronized (this) {
                this.ready = true;
                this.result = result;
                this.notifyAll();
            }
        }
        public Result waitResult() {
            synchronized (this) {
                if (!this.ready) {
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                    }
                }
                return this.result != null ? this.result : Result.getError();
            }
        }
    }

    public static class Result {
        public final Status status;
        public final Object obj;
        public final ObjectType objType;

        public static enum ObjectType  {
            NULL,
            RESPONSE,
            MOVIES,
            SHOWS_PER_SEASON,
            LAST_ACTIVITY,
            LIST,
            SYNC_RESPONSE,
            RETOFIT_RESPONSE,
            PLAYBACK_RESPONSE
        }
        public Result(Status status, Object obj, ObjectType objType) {
            this.status = status;
            this.obj = obj;
            this.objType = objType;
        }
        public static Result get(Status status) {
            return new Result(status, null, ObjectType.NULL);
        }
        public static Result getSuccess() {
            return new Result(Status.SUCCESS, null, ObjectType.NULL);
        }
        public static Result getError() {
            return new Result(Status.ERROR, null, ObjectType.NULL);
        }
        public static Result getErrorNetwork() {
            return new Result(Status.ERROR_NETWORK, null, ObjectType.NULL);
        }
        public static Result getAsync() {
            return new Result(Status.ASYNC, null, ObjectType.NULL);
        }
    }
    public static enum Status {
        SUCCESS,
        SUCCESS_ALREADY,
        ERROR,
        ERROR_NETWORK,
        ERROR_AUTH,
        ERROR_ACCOUNT_LOCKED,
        ASYNC,
    }

    public interface Listener {
        public void onResult(Result result);
    }

    private static MyTraktV2 getTraktV2() {
        if(mTraktV2 == null)
            mTraktV2 = new MyTraktV2(API_KEY, API_SECRET, REDIRECT_URI);
        return  mTraktV2;
    }

    public Trakt(Context context) {
        mContext = context;
        mTraktV2 = getTraktV2();
        mTraktV2.accessToken(
                getAccessTokenFromPreferences(
                        PreferenceManager.getDefaultSharedPreferences(context)));
        mTraktV2.refreshToken(
                getRefreshTokenFromPreferences(
                        PreferenceManager.getDefaultSharedPreferences(context)));

        //test
        //mTraktV2.setAccessToken("911cfb2e98258328fd95a12d593f6e72e5412cff3f4ce9772e2b3a7b8af121fd");
        //setRefreshToken(PreferenceManager.getDefaultSharedPreferences(context),"");
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }
    public static OAuthClientRequest getAuthorizationRequest(SharedPreferences pref) throws OAuthSystemException{
        String sampleState = new BigInteger(130, new SecureRandom()).toString(32);
        return getAuthorizationRequestWithState(sampleState);
    }

    public static OAuthClientRequest getAuthorizationRequestWithState(SharedPreferences pref, String state) throws OAuthSystemException {
        return getAuthorizationRequestWithState(state);
    }

    public static OAuthClientRequest getAuthorizationRequestWithState(String state) throws OAuthSystemException {
        String url = getTraktV2().buildAuthorizationUrl(state);
        if (log.isDebugEnabled()) log.debug("getAuthorizationRequestWithState: url is {}", url);
        return OAuthClientRequest
                .authorizationLocation(url)
                .buildQueryMessage();
    }

    // redefine minimal class not to export the whole trakt5 to Video with AccessToken
    public static class accessToken {
        public String access_token;
        public String refresh_token;
    }

    // Device code response for device authentication flow
    public static class deviceCode {
        public String device_code;
        public String user_code;
        public String verification_url;
        public int expires_in;
        public int interval;
    }

    public static class InvalidDeviceCodeResponseError extends Exception {
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static accessToken getAccessToken(String code) throws AccountLockedError, ForbiddenError, ServiceUnavailableError {
        retrofit2.Response<AccessToken> response = null;
        for (int attempt = 0; attempt < MAX_TRIAL; attempt++) {
            response = null;
            try {
                response = getTraktV2().exchangeCodeForAccessToken(code);
                if (response.isSuccessful() && response.body() != null) {
                    final accessToken mAccessToken = new accessToken();
                    mAccessToken.access_token = response.body().access_token;
                    mAccessToken.refresh_token = response.body().refresh_token;
                    if (log.isDebugEnabled()) log.debug("getAccessToken: access_token is {}", mAccessToken.access_token);
                    return mAccessToken;
                }
                break; // got a response, no need to retry
            } catch (IOException | NullPointerException e) {
                log.error("getAccessToken: caught exception (attempt {}/{})", attempt + 1, MAX_TRIAL, e);
                if (attempt < MAX_TRIAL - 1) {
                    try { Thread.sleep(WAIT_BEFORE_NEXT_TRIAL); } catch (InterruptedException ignored) {}
                }
            }
        }
        if (response != null && !response.isSuccessful()) {
            log.error("getAccessToken error: code={}, message={}", response.code(), response.message());
            if (response.code() == 403) throw new ForbiddenError();
            if (response.code() == 423) throw new AccountLockedError();
            if (response.code() == 503) throw new ServiceUnavailableError();
        }
        return null;
    }

    /**
     * Generate a device code for TV/device authentication flow.
     * Display the user_code and verification_url to the user, then poll with the device_code.
     * @return deviceCode object with codes and verification URL, or null on error
     */
    public static deviceCode generateDeviceCode() throws AccountLockedError, ForbiddenError, ServiceUnavailableError, InvalidDeviceCodeResponseError {
        retrofit2.Response<com.uwetrottmann.trakt5.entities.DeviceCode> response = null;
        try {
            // Single-shot auth call (Trakt SDK returns a synchronous Response)
            response = getTraktV2().generateDeviceCode();
            if (response.isSuccessful() && response.body() != null) {
                if (isBlank(response.body().device_code)
                        || isBlank(response.body().user_code)
                        || isBlank(response.body().verification_url)) {
                    log.error("generateDeviceCode: invalid successful response missing fields device_code={}, user_code={}, verification_url={}",
                            !isBlank(response.body().device_code),
                            !isBlank(response.body().user_code),
                            !isBlank(response.body().verification_url));
                    throw new InvalidDeviceCodeResponseError();
                }
                final deviceCode code = new deviceCode();
                code.device_code = response.body().device_code;
                code.user_code = response.body().user_code;
                code.verification_url = response.body().verification_url;
                code.expires_in = response.body().expires_in != null ? response.body().expires_in : 600;
                code.interval = response.body().interval != null ? response.body().interval : 5;
                if (log.isDebugEnabled()) log.debug("generateDeviceCode: user_code={}, verification_url={}", code.user_code, code.verification_url);
                return code;
            }
        } catch (IOException | NullPointerException e) {
            log.error("generateDeviceCode: caught exception", e);
        }
        if (response != null && !response.isSuccessful()) {
            log.error("generateDeviceCode error: code={}, message={}", response.code(), response.message());
            if (response.code() == 403) throw new ForbiddenError();
            if (response.code() == 423) throw new AccountLockedError();
            if (response.code() == 503) throw new ServiceUnavailableError();
        }
        return null;
    }

    /**
     * Exchange device code for access token.
     * Poll this method every 'interval' seconds until user authorizes or code expires.
     * @param deviceCode the device_code from generateDeviceCode()
     * @return accessToken if user authorized, null if still pending or error
     */
    public static accessToken exchangeDeviceCodeForAccessToken(String deviceCode) throws AccountLockedError, ForbiddenError, ServiceUnavailableError {
        retrofit2.Response<AccessToken> response = null;
        try {
            // Single-shot auth call (Trakt SDK returns a synchronous Response)
            response = getTraktV2().exchangeDeviceCodeForAccessToken(deviceCode);
            if (response.isSuccessful() && response.body() != null) {
                final accessToken mAccessToken = new accessToken();
                mAccessToken.access_token = response.body().access_token;
                mAccessToken.refresh_token = response.body().refresh_token;
                if (log.isDebugEnabled()) log.debug("exchangeDeviceCodeForAccessToken: access_token obtained");
                return mAccessToken;
            }
        } catch (IOException | NullPointerException e) {
            if (log.isDebugEnabled()) log.debug("exchangeDeviceCodeForAccessToken: still pending or error", e);
        }
        if (response != null && !response.isSuccessful()) {
            // 400 = pending, 404 = not found, 410 = expired, 429 = polling too fast
            if (log.isDebugEnabled()) log.debug("exchangeDeviceCodeForAccessToken: status={}", response.code());
            if (response.code() == 403) throw new ForbiddenError();
            if (response.code() == 423) throw new AccountLockedError();
            if (response.code() == 503) throw new ServiceUnavailableError();
        }
        return null;
    }

    private Result handleRet(CallbackLock lock, Exception error, Object object, Result.ObjectType objectType) {
        Result result = null;
        if (error != null || object == null || objectType == ObjectType.NULL) {
            Status status;
            status = Status.ERROR;
            if (error instanceof AuthentificationError)
                status = Status.ERROR_AUTH;
            if (error instanceof AccountLockedError)
                status = Status.ERROR_ACCOUNT_LOCKED;
            if (error instanceof IOException)
                status = Status.ERROR_NETWORK;
            result = new Result(status, error, objectType);
        } else {
            if (objectType == ObjectType.RESPONSE) {
                SyncResponse response = (SyncResponse) object;
                Status status;
                // TODO: test check if mark as seen already marked as seen generates an error
                // test nvp clear seen, launch video nvp, mark on trakt web seen, end video what happens?
                /*
                if (response.error != null)
                    status = response.error.endsWith("already") ? Status.SUCCESS_ALREADY : Status.ERROR;
                else
                    status = Status.SUCCESS;
                 */
                status = Status.SUCCESS;
                result = new Result(status, response, objectType);
            } else if (objectType == ObjectType.PLAYBACK_RESPONSE) {
                PlaybackResponse response = (PlaybackResponse) object;
                result = new Result(Status.SUCCESS, response, objectType);
            } else
                result = new Result(Status.SUCCESS, object, objectType);
        }
        if (lock != null){
            lock.notify(result);
        }
        else if (mListener != null)
            mListener.onResult(result);
        return result;
    }

    public static String getDateFormat(long currentTimeSecond) { // return UTC/GMT time from epoch seconds
        // currentTimeSecond is UTC epoch (seconds since January 1, 1970, 00:00:00 GMT)
        // Trakt API expects ISO 8601 format in UTC timezone
        if (currentTimeSecond <= 0) {
            return null;
        }
        // Convert epoch seconds directly to UTC OffsetDateTime and format as ISO 8601
        return Instant.ofEpochSecond(currentTimeSecond)
                .atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    public Result markAs(final String action, final SyncItems param, final boolean isShow, final int trial){
        if (log.isDebugEnabled()) log.debug("markAs {} trial {} for {}", action, trial, (param != null) ? param.ids : null);
        SyncResponse response = null;
        if (action.equals(Trakt.ACTION_SEEN)) {
            response = exec(mTraktV2.sync().addItemsToWatchedHistory(param));
        } else if (action.equals(Trakt.ACTION_UNSEEN)) {
            response = exec(mTraktV2.sync().deleteItemsFromWatchedHistory(param));
        } else if (action.equals(Trakt.ACTION_LIBRARY)) {
            response = exec(mTraktV2.sync().addItemsToCollection(param));
        } else if (action.equals(Trakt.ACTION_UNLIBRARY)) {
            response = exec(mTraktV2.sync().deleteItemsFromCollection(param));
        }
        if (response == null)
            return handleRet(null, mLastExecException != null ? mLastExecException : new Exception(), null, ObjectType.NULL);
        return handleRet(null, null, response, ObjectType.RESPONSE);
    }

    public Result markAs(final String action, SyncItems param, boolean isShow) {
        return markAs(action, param, isShow, 0);
    }

    public Result markAs(final String action, VideoDbInfo videoInfo) {
        if (videoInfo.isScraped) {
            if (videoInfo.isShow) {
                SyncEpisode se = new SyncEpisode();
                EpisodeIds ei = new EpisodeIds();
                ei.tmdb = Integer.valueOf(videoInfo.scraperEpisodeId);
                se.id(ei);
                // note that lastTimePlayed should always be >0 since filtered before call to avoid syncing not viewed videos
                if(videoInfo.lastTimePlayed>0) {
                    se.watchedAt(OffsetDateTime.parse(getDateFormat(videoInfo.lastTimePlayed)));
                }
                SyncItems sitems = new SyncItems();
                sitems.episodes(se);

                return markAs(action, sitems, videoInfo.isShow);
            } else {
                SyncMovie sm = new SyncMovie();
                MovieIds mi = new MovieIds();
                mi.tmdb = Integer.valueOf(videoInfo.scraperMovieId);
                if(videoInfo.lastTimePlayed>0)
                    sm.watchedAt(OffsetDateTime.parse(getDateFormat(videoInfo.lastTimePlayed)));
                sm.id(mi);
                SyncItems sitems = new SyncItems();
                sitems.movies(sm);
                return markAs(action, sitems, videoInfo.isShow);
            }
        } else {
            return Result.getError();
        }
    }

    public Result removeFromLibrary(VideoDbInfo videoInfo) {
        return markAs(ACTION_UNLIBRARY, videoInfo);
    }

    public Result markAsSeen(VideoDbInfo videoInfo) {
        return markAs(ACTION_SEEN, videoInfo);
    }

    public Result markAsUnseen(VideoDbInfo videoInfo) {
        return markAs(ACTION_UNSEEN, videoInfo);
    }

    private void cleanWatching() {
        mWatchingSuccess = false;
    }

    private AuthParam fillParam(VideoDbInfo videoInfo) {
        if (videoInfo.isShow) {
            ShowWatchingParam param = new ShowWatchingParam();
            param.tmdb_id = videoInfo.scraperShowId;
            param.episode_tmdb_id = videoInfo.scraperEpisodeId;
            param.duration = videoInfo.duration != -1 ? videoInfo.duration : 0;
            return param;
        } else {
            MovieWatchingParam param = new MovieWatchingParam();
            param.tmdb_id = videoInfo.scraperMovieId;
            param.duration = videoInfo.duration != -1 ? videoInfo.duration : 0;
            return param;
        }
    }

    public Result postWatchingStop(final VideoDbInfo videoInfo, float progress) {
        return postWatching("stop", videoInfo, progress, 0);
    }

    public Result postWatchingPause(final VideoDbInfo videoInfo, float progress) {
        return postWatching("pause", videoInfo, progress, 0);
    }

    public Result postWatching(VideoDbInfo videoInfo, float progress){
        return  postWatching("start", videoInfo, progress, 0);
    }

    public Result postWatching(final String action,final VideoDbInfo videoInfo, final float progress, final int trial) {
        if (log.isDebugEnabled()) log.debug("postWatching for action={}, progress={}, trial={}", action, progress, trial);
        PlaybackResponse playbackResponse = null;
        AuthParam param = fillParam(videoInfo);
        if (videoInfo.isShow) {
            ShowWatchingParam showParam = (ShowWatchingParam) param;
            showParam.progress = (int) progress;

            if (showParam.episode_tmdb_id == null || showParam.episode_tmdb_id.isEmpty()) {
                log.warn("postWatching: missing episode_tmdb_id for show, skipping scrobble to avoid 404");
                return handleRet(null, mLastExecException != null ? mLastExecException : new Exception(), null, ObjectType.NULL);
            }

            SyncEpisode se = new SyncEpisode();
            EpisodeIds ids = new EpisodeIds();
            try {
                if (log.isDebugEnabled()) log.debug("postWatching: showid={}", showParam.episode_tmdb_id);
                ids.tmdb = Integer.valueOf(showParam.episode_tmdb_id);
            } catch (NumberFormatException nfe) {
                log.warn("postWatching: invalid episode_tmdb_id {}, skipping scrobble", showParam.episode_tmdb_id);
                return handleRet(null, mLastExecException != null ? mLastExecException : new Exception(), null, ObjectType.NULL);
            }
            se.id(ids);
            ScrobbleProgress ep = new ScrobbleProgress(se, progress, "", "");
            if (log.isDebugEnabled()) log.debug("postWatching: EpisodeProgres={}, episode id {}", ep.progress, se.ids.tmdb);
            switch (action) {
                case "start":
                    if (log.isDebugEnabled()) log.debug("postWatching: sending startWatching");
                    playbackResponse = exec(mTraktV2.scrobble().startWatching(ep));
                    break;
                case "stop":
                    if (log.isDebugEnabled()) log.debug("postWatching: sending stopWatching");
                    playbackResponse = exec(mTraktV2.scrobble().stopWatching(ep));
                    break;
                case "pause":
                    if (log.isDebugEnabled()) log.debug("postWatching: sending pauseWatching");
                    playbackResponse = exec(mTraktV2.scrobble().pauseWatching(ep));
                    break;
                case "default":
                    log.warn("postWatching: not supported action!");
                    break;
            }
        } else {
            MovieWatchingParam movieParam = (MovieWatchingParam) param;
            movieParam.progress = (int) progress;

            if (movieParam.tmdb_id == null || movieParam.tmdb_id.isEmpty()) {
                log.warn("postWatching: missing movie tmdb_id, skipping scrobble to avoid 404");
                return handleRet(null, mLastExecException != null ? mLastExecException : new Exception(), null, ObjectType.NULL);
            }

            MovieIds mi = new MovieIds();
            try {
                mi.tmdb=Integer.valueOf(movieParam.tmdb_id);
            } catch (NumberFormatException nfe) {
                log.warn("postWatching: invalid movie tmdb_id {}, skipping scrobble", movieParam.tmdb_id);
                return handleRet(null, mLastExecException != null ? mLastExecException : new Exception(), null, ObjectType.NULL);
            }
            SyncMovie sm= new SyncMovie();
            sm.id(mi);
            ScrobbleProgress mp = new ScrobbleProgress(sm, progress, "", "");
            if (log.isDebugEnabled()) log.debug("postWatching: MovieProgress={}", mp);
            switch (action) {
                case "start":
                    if (log.isDebugEnabled()) log.debug("postWatching: sending startWatching");
                    playbackResponse = exec(mTraktV2.scrobble().startWatching(mp));
                    break;
                case "stop":
                    if (log.isDebugEnabled()) log.debug("postWatching: sending stopWatching");
                    playbackResponse = exec(mTraktV2.scrobble().stopWatching(mp));
                    break;
                case "pause":
                    if (log.isDebugEnabled()) log.debug("postWatching: sending pauseWatching");
                    playbackResponse = exec(mTraktV2.scrobble().pauseWatching(mp));
                    break;
                case "default":
                    log.warn("postWatching: not supported action!");
                    break;
            }
        }
        mWatchingSuccess = true;
        if (playbackResponse == null) {
            cleanWatching();
            return handleRet(null, null, playbackResponse, ObjectType.NULL);
        }
        return handleRet(null, null, playbackResponse, ObjectType.PLAYBACK_RESPONSE);
    }

    public static void setRefreshToken(SharedPreferences sharedPreferences, String refreshToken) {
        Editor editor = sharedPreferences.edit();
        if (log.isDebugEnabled()) log.debug("setRefreshToken: refreshToken={}", refreshToken);
        if (refreshToken != null) {
            editor.putString(KEY_TRAKT_REFRESH_TOKEN, refreshToken);
        } else {
            editor.remove(KEY_TRAKT_REFRESH_TOKEN);
        }
        editor.apply();
    }

    private String getRefreshTokenFromPreferences(SharedPreferences defaultSharedPreferences) {
        return defaultSharedPreferences.getString(KEY_TRAKT_REFRESH_TOKEN,"");
    }

    private boolean refreshAccessToken() {
        if (log.isDebugEnabled()) log.debug("refreshAccessToken()");
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mContext);
        String refreshToken = getRefreshTokenFromPreferences(pref);
        if(refreshToken==null|| refreshToken.isEmpty()){
            Intent intent = new Intent(TRAKT_ISSUE_REFRESH_TOKEN);
            intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
            mContext.sendBroadcast(intent);
        }
        else {
            try {
                retrofit2.Response<AccessToken> token = mTraktV2.refreshAccessToken(refreshToken);
                if (token.isSuccessful() && token.body() != null) {
                    mTraktV2.accessToken(token.body().access_token);
                    setAccessToken(pref, token.body().access_token);
                    setRefreshToken(pref, token.body().refresh_token);
                    return true;
                } else {
                    log.error("Failed refreshing token code={}, message={}", token.code(), token.message());
                    // If refresh token is definitively invalid (400 Bad Request or 401 Unauthorized), clear it
                    if (token.code() == 400 || token.code() == 401) {
                        wipePreferences(pref, false);
                    }
                    Intent intent = new Intent(TRAKT_ISSUE_REFRESH_TOKEN);
                    intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                    mContext.sendBroadcast(intent);
                    return false;
                }
            } catch (IOException ioe) {
                log.error("getAccessToken: caught IOException {}", ioe);
                return false;
            }
        }
        return false;
    }

    public Result getAllShows(String library) {
        return getAllShows(library, 0);
    }

    private Result getAllShows(String library, int trial){
        if (log.isDebugEnabled()) log.debug("getAllShows");
        List<BaseShow> result = new ArrayList<>();
        int page = 1;
        while (true) {
            List<BaseShow> pageData;
            if (library.equals(Trakt.LIBRARY_WATCHED))
                pageData = exec(mTraktV2.sync().watchedShows(page, PAGE_LIMIT, ExtendedShowsWatched.PROGRESS, Specials.TRUE));
            else
                pageData = exec(mTraktV2.sync().collectionShows(page, PAGE_LIMIT, Extended.EPISODES));
            if (pageData == null)
                return handleRet(null, mLastExecException != null ? mLastExecException : new Exception(), null, ObjectType.NULL);
            result.addAll(pageData);
            if (pageData.size() < PAGE_LIMIT) break;
            page++;
        }
        return handleRet(null, null, result, ObjectType.SHOWS_PER_SEASON);
    }

    // get playback status from trakt: this covers only videos with progress/resume not watched!
    public Result getPlaybackStatus(int trial){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        long lastSyncUtcSeconds = prefs.getLong(TraktService.PREFERENCE_TRAKT_LAST_TIME_SYNC_TO_DB_PROGRESS, 1);

        // Enhancement 0: Coarse-grained quick check using existing timestamps
        // Avoids detailed checks and API calls if we know nothing changed
        long movieTime = getLastTimeMovieWatched(prefs);
        long showTime = getLastTimeShowWatched(prefs);
        long coarseLastActivityUtc = Math.max(movieTime, showTime);

        if (coarseLastActivityUtc > 0 && coarseLastActivityUtc <= lastSyncUtcSeconds) {
            if (log.isDebugEnabled()) log.debug("getPlaybackStatus: coarse-grained check shows no activity since last sync (lastActivity={}, lastSync={}), skipping playback sync",
                    coarseLastActivityUtc, lastSyncUtcSeconds);
            return handleRet(null, null, new ArrayList<>(), ObjectType.MOVIES);
        }

        // Enhancement 1: Skip sync if no resume point activity since last sync (use paused_at timestamps)
        long lastActivityMoviePausedUtc = prefs.getLong(TraktService.PREFERENCE_TRAKT_LAST_ACTIVITY_MOVIE_PAUSED, 0);
        long lastActivityEpisodePausedUtc = prefs.getLong(TraktService.PREFERENCE_TRAKT_LAST_ACTIVITY_EPISODE_PAUSED, 0);
        long lastActivityPausedUtc = Math.max(lastActivityMoviePausedUtc, lastActivityEpisodePausedUtc);

        if (lastActivityPausedUtc > 0 && lastActivityPausedUtc <= lastSyncUtcSeconds) {
            if (log.isDebugEnabled()) log.debug("getPlaybackStatus: no resume point activity since last sync (lastActivityPaused={}, lastSync={}), skipping playback sync",
                    lastActivityPausedUtc, lastSyncUtcSeconds);
            return handleRet(null, null, new ArrayList<>(), ObjectType.MOVIES);
        }

        // Enhancement 2: Check if NEW CONTENT was indexed since last Trakt sync
        // If yes, must do FULL sync because:
        // - New movies exist locally that didn't exist when last sync happened
        // - Other device may have already reported playback for those new movies
        // - Incremental sync would miss them
        long lastIndexedUtcSeconds = prefs.getLong(AutoScrapeService.PREFERENCE_LAST_TIME_VIDEO_SCRAPED_UTC, 0);
        if (lastIndexedUtcSeconds > lastSyncUtcSeconds) {
            if (log.isDebugEnabled()) log.debug("getPlaybackStatus: new content indexed since last sync (lastIndexed={}, lastSync={}), forcing FULL sync to catch new movie activity",
                    lastIndexedUtcSeconds, lastSyncUtcSeconds);
            List<PlaybackResponse> list = exec(mTraktV2.sync().playback(null, null, null, PLAYBACK_HISTORY_SIZE));
            if (list == null) {
                if (log.isDebugEnabled()) log.debug("getPlaybackStatus: no playback history");
                return handleRet(null, mLastExecException != null ? mLastExecException : new Exception(), null, ObjectType.NULL);
            } else {
                if (log.isDebugEnabled()) log.debug("getPlaybackStatus: playback history size is {}", list.size());
                return handleRet(null, null, list, ObjectType.MOVIES);
            }
        }

        // Enhancement 3: Use incremental sync - no new content, safe to delta sync
        OffsetDateTime lastSync = OffsetDateTime.ofInstant(Instant.ofEpochSecond(lastSyncUtcSeconds), ZoneOffset.UTC);
        if (log.isDebugEnabled()) log.debug("getPlaybackStatus: no new content since last sync - using incremental sync since {} UTC", lastSync);

        List<PlaybackResponse> list;
        try {
            // Use upstream playback API with start_at parameter (end_at=null means "until now")
            list = exec(mTraktV2.sync().playback(lastSync, null, null, PLAYBACK_HISTORY_SIZE));
        } catch (Exception e) {
            if (log.isDebugEnabled()) log.debug("getPlaybackStatus: incremental sync failed, falling back to full history");
            list = exec(mTraktV2.sync().playback(null, null, null, PLAYBACK_HISTORY_SIZE));
        }

        if(list == null) {
            if (log.isDebugEnabled()) log.debug("getPlaybackStatus: no playback history");
            return handleRet(null, mLastExecException != null ? mLastExecException : new Exception(), null, ObjectType.NULL);
        } else {
            if (log.isDebugEnabled()) log.debug("getPlaybackStatus: playback history size is {}", list.size());
            return handleRet(null, null, list, ObjectType.MOVIES);
        }
    }

    // get playback status from trakt: this covers only fully watched videos!
    public Result getWatchedStatus(int trial){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        long lastSyncUtcSeconds = prefs.getLong(TraktService.PREFERENCE_TRAKT_LAST_TIME_SYNC_TO_DB_WATCHED, 1);

        // Enhancement 0: Coarse-grained quick check using existing timestamps
        // Avoids detailed checks and API calls if we know nothing changed
        long movieTime = getLastTimeMovieWatched(prefs);
        long showTime = getLastTimeShowWatched(prefs);
        long coarseLastActivityUtc = Math.max(movieTime, showTime);

        if (coarseLastActivityUtc > 0 && coarseLastActivityUtc <= lastSyncUtcSeconds) {
            if (log.isDebugEnabled()) log.debug("getWatchedStatus: coarse-grained check shows no activity since last sync (lastActivity={}, lastSync={}), skipping watched status sync",
                    coarseLastActivityUtc, lastSyncUtcSeconds);
            return handleRet(null, null, new ArrayList<>(), ObjectType.MOVIES);
        }

        // Enhancement 1: Skip sync if no watched activity since last sync (use watched_at timestamps)
        long lastActivityMovieWatchedUtc = prefs.getLong(TraktService.PREFERENCE_TRAKT_LAST_ACTIVITY_MOVIE_WATCHED, 0);
        long lastActivityEpisodeWatchedUtc = prefs.getLong(TraktService.PREFERENCE_TRAKT_LAST_ACTIVITY_EPISODE_WATCHED, 0);
        long lastActivityWatchedUtc = Math.max(lastActivityMovieWatchedUtc, lastActivityEpisodeWatchedUtc);

        if (lastActivityWatchedUtc > 0 && lastActivityWatchedUtc <= lastSyncUtcSeconds) {
            if (log.isDebugEnabled()) log.debug("getWatchedStatus: no watched activity since last sync (lastActivityWatched={}, lastSync={}), skipping watched status sync",
                    lastActivityWatchedUtc, lastSyncUtcSeconds);
            return handleRet(null, null, new ArrayList<>(), ObjectType.MOVIES);
        }

        // Enhancement 2: Check if NEW CONTENT was indexed since last Trakt sync
        // If yes, must do FULL sync because:
        // - New movies exist locally that didn't exist when last sync happened
        // - Other device may have already reported watched status for those new movies
        // - Incremental sync would miss them
        long lastIndexedUtcSeconds = prefs.getLong(AutoScrapeService.PREFERENCE_LAST_TIME_VIDEO_SCRAPED_UTC, 0);
        if (lastIndexedUtcSeconds > lastSyncUtcSeconds) {
            if (log.isDebugEnabled()) log.debug("getWatchedStatus: new content indexed since last sync (lastIndexed={}, lastSync={}), forcing FULL sync to catch new movie activity",
                    lastIndexedUtcSeconds, lastSyncUtcSeconds);
            List<HistoryEntry> list = exec(mTraktV2.sync().history(null, PLAYBACK_HISTORY_SIZE, null, null, null));
            if (list == null) {
                if (log.isDebugEnabled()) log.debug("getWatchedStatus: no watched history");
                return handleRet(null, mLastExecException != null ? mLastExecException : new Exception(), null, ObjectType.NULL);
            } else {
                if (log.isDebugEnabled()) log.debug("getWatchedStatus: watched history size is {}", list.size());
                return handleRet(null, null, list, ObjectType.MOVIES);
            }
        }

        // Enhancement 3: Use incremental sync - no new content, safe to delta sync
        OffsetDateTime lastSync = OffsetDateTime.ofInstant(Instant.ofEpochSecond(lastSyncUtcSeconds), ZoneOffset.UTC);
        if (log.isDebugEnabled()) log.debug("getWatchedStatus: no new content since last sync - using incremental sync since {} UTC", lastSync);

        List<HistoryEntry> list;
        try {
            // Use upstream history API with start_at parameter (end_at=null means "until now")
            list = exec(mTraktV2.sync().history(null, PLAYBACK_HISTORY_SIZE, null, lastSync, null));
        } catch (Exception e) {
            if (log.isDebugEnabled()) log.debug("getWatchedStatus: incremental sync failed, falling back to full history");
            list = exec(mTraktV2.sync().history(null, PLAYBACK_HISTORY_SIZE, null, null, null));
        }

        if(list == null) {
            if (log.isDebugEnabled()) log.debug("getWatchedStatus: no watched history");
            return handleRet(null, mLastExecException != null ? mLastExecException : new Exception(), null, ObjectType.NULL);
        } else {
            if (log.isDebugEnabled()) log.debug("getWatchedStatus: watched history size is {}", list.size());
            return handleRet(null, null, list, ObjectType.MOVIES);
        }
    }

    public Result getPlaybackStatus() {
        return getPlaybackStatus(0);
    }

    /**
     * Fetch full playback history (resume points) by paging until exhaustion.
     * Not used by default sync; provided for manual recovery scenarios.
     */
    public Result getPlaybackStatusFullHistory() {
        if (log.isDebugEnabled()) log.debug("getPlaybackStatusFullHistory: fetching full playback history (single wide request)");
        List<PlaybackResponse> list = exec(mTraktV2.sync().playback(null, null, null, 1000)); // Trakt hard cap
        if (list == null || list.isEmpty()) {
            if (log.isDebugEnabled()) log.debug("getPlaybackStatusFullHistory: no playback history");
            return handleRet(null, mLastExecException != null ? mLastExecException : new Exception(), null, ObjectType.NULL);
        }
        if (log.isDebugEnabled()) log.debug("getPlaybackStatusFullHistory: playback history size is {}", list.size());
        return handleRet(null, null, list, ObjectType.MOVIES);
    }

    /**
     * Fetch full watched history by paging until exhaustion.
     * Not used by default sync; provided for manual recovery scenarios.
     */
    public Result getWatchedStatusFullHistory() {
        if (log.isDebugEnabled()) log.debug("getWatchedStatusFullHistory: fetching full watched history (single wide requests)");
        List<HistoryEntry> all = new ArrayList<>();
        List<HistoryEntry> shows = exec(mTraktV2.sync().history(null, 1000, null, null, null));
        if (shows != null) all.addAll(shows);
        // If needed, add movies separately; API returns combined history so above is usually enough
        if (all.isEmpty()) {
            if (log.isDebugEnabled()) log.debug("getWatchedStatusFullHistory: no watched history");
            return handleRet(null, mLastExecException != null ? mLastExecException : new Exception(), null, ObjectType.NULL);
        }
        if (log.isDebugEnabled()) log.debug("getWatchedStatusFullHistory: watched history size is {}", all.size());
        return handleRet(null, null, all, ObjectType.MOVIES);
    }

    public Result getWatchedStatus() {
        return getWatchedStatus(0);
    }

    public Result getAllMovies(String library, boolean sync) {
        return getAllMovies(library, 0);
    }

    public static class AuthentificationError extends Exception{};
    public static class AccountLockedError extends Exception{};
    public static class ForbiddenError extends Exception{};
    public static class ServiceUnavailableError extends Exception{};

    public <T> T exec(retrofit2.Call<T> call) {
        mLastExecException = null;
        return exec(call, MAX_TRIAL);
    }
    public <T> T exec(retrofit2.Call<T> call, int remaining) {
        if (log.isDebugEnabled()) log.debug("exec: call, remaining trials={}", remaining);
        try {
            retrofit2.Response<T> res = call.execute();
            if (!res.isSuccessful()) {
                log.error("exec request error code is {}", res.code(), new Throwable());
                    // TODO check this new retry case with 409
                    // 409	Conflict - resource already created is happening often but no retry...
                    if (res.code() == 401 || res.code() == 409 ) {
                        if (remaining > 0) {
                            if (refreshAccessToken()) {
                                return exec(call.clone(), 0);
                            } else {
                                throw new AuthentificationError();
                            }
                        } else {
                            throw new AuthentificationError();
                        }
                    } else if (res.code() == 404) {
                        log.error("exec request error: resource not found (404)");
                        return null; // Handle 404 error specifically
                    } else if (res.code() == 423) {
                        log.error("exec request error: account locked (423) - no retry");
                        return null; // Trakt account is locked
                    } else {
                        throw new Exception(res.errorBody().toString());
                }
            }
            return res.body();
        } catch(AuthentificationError e) {
            mLastExecException = e;
            return null;
        } catch(Exception e) {
            mLastExecException = e;
            try { Thread.sleep(WAIT_BEFORE_NEXT_TRIAL); } catch (Exception a) {}
            if(remaining == 0) {
                return null;
            }
            return exec(call.clone(), remaining-1);
        }
    }

    public Result getAllMovies(final String library, int trial) {
        if (log.isDebugEnabled()) log.debug("getAllMovies");
        List<BaseMovie> result = new ArrayList<>();
        int page = 1;
        while (true) {
            List<BaseMovie> pageData;
            if (library.equals(Trakt.LIBRARY_WATCHED))
                pageData = exec(mTraktV2.sync().watchedMovies(page, PAGE_LIMIT, null));
            else
                pageData = exec(mTraktV2.sync().collectionMovies(page, PAGE_LIMIT, Extended.FULL));
            if (pageData == null)
                return handleRet(null, mLastExecException != null ? mLastExecException : new Exception(), null, ObjectType.NULL);
            result.addAll(pageData);
            if (pageData.size() < PAGE_LIMIT) break;
            page++;
        }
        return handleRet(null, null, result, ObjectType.MOVIES);
    }

    public Result getLastActivity(int trial) {
        if (log.isDebugEnabled()) log.debug("getLastActivity");
        LastActivities ret = exec(mTraktV2.sync().lastActivities());
        if(ret == null)
            return handleRet(null, mLastExecException != null ? mLastExecException : new Exception(), null, ObjectType.NULL);
        return handleRet(null, null, ret, ObjectType.LAST_ACTIVITY);
    }

    /* add new list to trakt profile */
    public Result addList(int trial, String title) {
        if (log.isDebugEnabled()) log.debug("addList");
        TraktList list = new TraktList();
        list.name = title;
        list.privacy = ListPrivacy.PRIVATE;

        TraktList result = exec(mTraktV2.users().createList(UserSlug.ME, list));
        if (result == null)
            return handleRet(null, mLastExecException != null ? mLastExecException : new Exception(), null, ObjectType.NULL);
        return handleRet(null, null, result, ObjectType.LIST);
    }

    public Result deleteList(int trial, String id) {
        if (log.isDebugEnabled()) log.debug("deleteList");
        Void response = exec(mTraktV2.users().deleteList(UserSlug.ME, id));
        if (response == null)
            return handleRet(null, mLastExecException != null ? mLastExecException : new Exception(), null, ObjectType.NULL);
        return handleRet(null, null, response, ObjectType.NULL);
        /*
        if (response.getStatus() == 200)
            return handleRet(null, null, response, ObjectType.RETOFIT_RESPONSE);
        else
            return Result.getError();
         */
    }

    public Result getLists(int trial) {
        if (log.isDebugEnabled()) log.debug("getLists");
        List<TraktList> lists = exec(mTraktV2.users().lists(UserSlug.ME));
        if (lists == null)
            return handleRet(null, mLastExecException != null ? mLastExecException : new Exception(), null, ObjectType.NULL);
        return handleRet(null, null, lists, ObjectType.LIST);
    }

    public Result getListContent(int trial, int listId) {
        if (log.isDebugEnabled()) log.debug("getListContent");
        List<ListEntry> result = new ArrayList<>();
        String id = String.valueOf(listId);
        int page = 1;
        while (true) {
            List<ListEntry> pageData = exec(mTraktV2.users().listItems(UserSlug.ME, id, page, PAGE_LIMIT, null));
            if (pageData == null)
                return handleRet(null, mLastExecException != null ? mLastExecException : new Exception(), null, ObjectType.NULL);
            result.addAll(pageData);
            if (pageData.size() < PAGE_LIMIT) break;
            page++;
        }
        return handleRet(null, null, result, ObjectType.LIST);
    }

    public Result removeVideoFromList(int trial, int listId, ListEntry onlineItem) {
        if (log.isDebugEnabled()) log.debug("removeVideoFromLit");
        SyncItems syncItems = new SyncItems();
        if(onlineItem.episode!=null) {
            SyncEpisode syncEpisode = new SyncEpisode();
            syncEpisode.id(onlineItem.episode.ids);
            syncItems.episodes(syncEpisode);
        } else {
            SyncMovie syncMovie = new SyncMovie ();
            syncMovie .id(onlineItem.movie.ids);
            syncItems.movies(syncMovie);
        }
        SyncResponse ret = exec(mTraktV2.users().deleteListItems(UserSlug.ME, String.valueOf(listId), syncItems));
        if (ret == null)
            return handleRet(null, mLastExecException != null ? mLastExecException : new Exception(), null, ObjectType.NULL);
        if(ret.deleted.episodes+ret.deleted.movies>0)
            return handleRet(null, null, ret, ObjectType.SYNC_RESPONSE);
        else
            return handleRet(null, mLastExecException != null ? mLastExecException : new Exception(), null, ObjectType.NULL);
    }

    public Result addVideoToList(int trial, int listId, VideoStore.VideoList.VideoItem videoItem) {
        if (log.isDebugEnabled()) log.debug("addVideoToList");
        SyncResponse ret = null;
        if (videoItem.episodeId > 0) {
            SyncEpisode se = new SyncEpisode();
            EpisodeIds ei = new EpisodeIds();
            ei.tmdb = Integer.valueOf(videoItem.episodeId);
            se.id(ei);
            SyncItems sitems = new SyncItems();
            sitems.episodes(se);
            ret = exec(mTraktV2.users().addListItems(UserSlug.ME, String.valueOf(listId), sitems));
        } else {
            SyncMovie sm = new SyncMovie();
            MovieIds mi = new MovieIds();
            mi.tmdb = Integer.valueOf(videoItem.movieId);
            sm.id(mi);
            SyncItems sitems = new SyncItems();
            sitems.movies(sm);
            ret = exec(mTraktV2.users().addListItems(UserSlug.ME, String.valueOf(listId), sitems));
        }
        if (ret == null)
            return handleRet(null, mLastExecException != null ? mLastExecException : new Exception(), null, ObjectType.NULL);
        if(ret.added.episodes+ret.added.movies>0)
            return handleRet(null, null, ret, ObjectType.SYNC_RESPONSE);
        else
            return handleRet(null, mLastExecException != null ? mLastExecException : new Exception(), null, ObjectType.NULL);
    }

    public Result getLastActivity() {
        return getLastActivity(0);
    }

    public static String getXmlName(String action) {
        return XML_PREFIX + action + XML_SUFFIX;
    }

    public static String getUserFromPreferences(SharedPreferences pref) {
        return pref.getString(KEY_TRAKT_USER, null);
    }

    public static String getSha1FromPreferences(SharedPreferences pref) {
        return pref.getString(KEY_TRAKT_SHA1, null);
    }

    public static void setLoginPreferences(SharedPreferences pref, String user, String sha1) {
        Editor editor = pref.edit();
        if (user != null && sha1 != null) {
            editor.putString(KEY_TRAKT_USER, user);
            editor.putString(KEY_TRAKT_SHA1, sha1);
        } else {
            editor.remove(KEY_TRAKT_USER);
            editor.remove(KEY_TRAKT_SHA1);
        }
        editor.apply();
    }

    public static void setAccessToken(SharedPreferences pref, String accessToken) {
        Editor editor = pref.edit();
        if (accessToken != null) {
            editor.putString(KEY_TRAKT_ACCESS_TOKEN, accessToken);
        } else {
            editor.remove(KEY_TRAKT_ACCESS_TOKEN);
        }
        editor.apply();
    }

    public static String getAccessTokenFromPreferences(SharedPreferences pref) {
        return pref.getString(KEY_TRAKT_ACCESS_TOKEN, null);
    }

    public static boolean isTraktV1Enabled(Context context, SharedPreferences pref) {
        return getUserFromPreferences(pref)!=null;
    }

    public static boolean isTraktV2Enabled(Context context, SharedPreferences pref) {
        return getAccessTokenFromPreferences(pref)!=null;
    }

    public static boolean isLiveScrobblingEnabled(SharedPreferences pref) {
        return pref.getBoolean(KEY_TRAKT_LIVE_SCROBBLING, true);
    }

    public static int getFlagSyncPreference(SharedPreferences preferences) {
        return preferences.getInt(KEY_TRAKT_SYNC_FLAG, 0);
    }

    public static boolean getSyncPlaybackPreference(SharedPreferences preferences) {
        return preferences.getBoolean(KEY_TRAKT_SYNC_RESUME, false);
    }

    /** Last successful DB -> Trakt watched sync UTC seconds */
    public static long getLastTimeWatchedSync(SharedPreferences preferences) {
        return preferences.getLong(TraktService.PREFERENCE_TRAKT_LAST_TIME_SYNC_WATCHED, 0);
    }

    public static void setLastTimeWatchedSync(SharedPreferences preferences, long utcSeconds) {
        Editor editor = preferences.edit();
        editor.putLong(TraktService.PREFERENCE_TRAKT_LAST_TIME_SYNC_WATCHED, utcSeconds);
        editor.apply();
    }

    // Track the highest watched timestamp pushed in current run (reset at call sites)
    private static final ThreadLocal<Long> lastPushedWatchedTime = new ThreadLocal<>();

    public static void resetLastPushedWatchedTime() {
        lastPushedWatchedTime.set(0L);
    }

    public static void updateLastPushedWatchedTime(long utcSeconds) {
        Long current = lastPushedWatchedTime.get();
        if (current == null || utcSeconds > current) {
            lastPushedWatchedTime.set(utcSeconds);
        }
    }

    public static long getLastPushedWatchedTime() {
        Long v = lastPushedWatchedTime.get();
        return v == null ? 0 : v;
    }

    public static boolean isAccountLocked(SharedPreferences preferences) {
        return preferences.getBoolean(KEY_TRAKT_ACCOUNT_LOCKED, false);
    }

    public static void setAccountLocked(SharedPreferences preferences, boolean locked) {
        Editor editor = preferences.edit();
        editor.putBoolean(KEY_TRAKT_ACCOUNT_LOCKED, locked);
        editor.apply();
    }

    public static boolean isFirstSyncDone(SharedPreferences preferences) {
        return preferences.getBoolean(TraktService.PREFERENCE_TRAKT_FIRST_SYNC_DONE, false);
    }

    public static void setFirstSyncDone(SharedPreferences preferences, boolean done) {
        Editor editor = preferences.edit();
        editor.putBoolean(TraktService.PREFERENCE_TRAKT_FIRST_SYNC_DONE, done);
        editor.apply();
    }

    /**
     * Disables Trakt by clearing all authentication tokens and marking account as locked.
     * User must re-authenticate to enable Trakt again.
     */
    public static void disableTraktOnAccountLock(Context context, SharedPreferences preferences) {
        log.warn("disableTraktOnAccountLock: Disabling Trakt due to account lock (423)");
        Editor editor = preferences.edit();
        // Clear authentication tokens
        editor.remove(KEY_TRAKT_ACCESS_TOKEN);
        editor.remove(KEY_TRAKT_REFRESH_TOKEN);
        // Mark as locked so user knows why it was disabled
        editor.putBoolean(KEY_TRAKT_ACCOUNT_LOCKED, true);
        clearTraktSyncState(editor);
        editor.apply();
        log.info("disableTraktOnAccountLock: Trakt disabled - user must re-authenticate to enable");
    }

    /** Clear local sync timestamps and flags when user disconnects Trakt. */
    public static void clearTraktSyncState(Editor editor) {
        editor.remove(KEY_TRAKT_SYNC_FLAG);
        editor.remove(KEY_TRAKT_LAST_TIME_SHOW_WATCHED);
        editor.remove(KEY_TRAKT_LAST_TIME_MOVIE_WATCHED);
        editor.remove(KEY_TRAKT_SYNC_RESUME);
        editor.remove(TraktService.PREFERENCE_TRAKT_LAST_TIME_SYNC_TO_DB_WATCHED);
        editor.remove(TraktService.PREFERENCE_TRAKT_LAST_TIME_SYNC_TO_DB_PROGRESS);
        editor.remove(TraktService.PREFERENCE_TRAKT_LAST_TIME_SYNC_WATCHED);
        editor.remove(TraktService.PREFERENCE_TRAKT_LAST_TIME_SYNC_PROGRESS);
        editor.remove(TraktService.PREFERENCE_TRAKT_LAST_ACTIVITY);
        editor.remove(TraktService.PREFERENCE_TRAKT_LAST_ACTIVITY_MOVIE_WATCHED);
        editor.remove(TraktService.PREFERENCE_TRAKT_LAST_ACTIVITY_MOVIE_PAUSED);
        editor.remove(TraktService.PREFERENCE_TRAKT_LAST_ACTIVITY_EPISODE);
        editor.remove(TraktService.PREFERENCE_TRAKT_LAST_ACTIVITY_EPISODE_WATCHED);
        editor.remove(TraktService.PREFERENCE_TRAKT_LAST_ACTIVITY_EPISODE_PAUSED);
        editor.remove(TraktService.PREFERENCE_TRAKT_LAST_ACTIVITY_LIST);
        editor.remove(TraktService.PREFERENCE_TRAKT_LAST_ACTIVITY_TIME_CHECKED_UTC);
        editor.remove(TraktService.PREFERENCE_TRAKT_LAST_SYNC_STATUS);
        editor.remove(TraktService.PREFERENCE_TRAKT_SKIPPED_NO_DURATION);
        editor.remove(TraktService.PREFERENCE_TRAKT_SKIPPED_NO_METADATA);
        editor.remove("trakt_last_sync");
    }

    public static void setFlagSyncPreference(SharedPreferences preferences, int flag) {
        int oldFlag = preferences.getInt(KEY_TRAKT_SYNC_FLAG, 0);
        if (flag != 0)
            flag |= oldFlag;
        Editor editor = preferences.edit();
        editor.putInt(KEY_TRAKT_SYNC_FLAG, flag);
        editor.apply();
    }

    public static long getLastTimeShowWatched(SharedPreferences preferences) {
        return preferences.getLong(KEY_TRAKT_LAST_TIME_SHOW_WATCHED, 0);
    }

    public static void setLastTimeShowWatched(SharedPreferences preferences, long time) {
        Editor editor = preferences.edit();
        editor.putLong(KEY_TRAKT_LAST_TIME_SHOW_WATCHED, time);
        editor.apply();
    }

    public static long getLastTimeMovieWatched(SharedPreferences preferences) {
        return preferences.getLong(KEY_TRAKT_LAST_TIME_MOVIE_WATCHED, 0);
    }

    public static void setLastTimeMovieWatched(SharedPreferences preferences, long time) {
        Editor editor = preferences.edit();
        editor.putLong(KEY_TRAKT_LAST_TIME_MOVIE_WATCHED, time);
        editor.apply();
    }

    public static boolean getSyncCollection(SharedPreferences preferences) {
        return preferences.getBoolean(KEY_TRAKT_SYNC_COLLECTION, false);
    }

    public static void wipePreferences(SharedPreferences pref, boolean userChanged) {
        Editor editor = pref.edit();
        if (!userChanged) {
            editor.remove(Trakt.KEY_TRAKT_USER);
            editor.remove(Trakt.KEY_TRAKT_SHA1);
            editor.remove(Trakt.KEY_TRAKT_LIVE_SCROBBLING);
            editor.remove(Trakt.KEY_TRAKT_SYNC_COLLECTION);
            editor.remove(Trakt.KEY_TRAKT_ACCESS_TOKEN);
            editor.remove(Trakt.KEY_TRAKT_REFRESH_TOKEN);
        }
        if (userChanged) {
            clearTraktSyncState(editor);
        }
        editor.apply();
    }

    public static boolean shouldMarkAsSeen(float progress) {
        if (log.isDebugEnabled()) log.debug("shouldMarkAsSeen: {}", progress);
        return progress >= SCROBBLE_THRESHOLD;
    }

}
