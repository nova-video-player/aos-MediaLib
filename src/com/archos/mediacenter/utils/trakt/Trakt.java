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
import android.util.Log;

import com.archos.environment.ArchosUtils;
import com.archos.mediacenter.utils.trakt.Trakt.Result.ObjectType;
import com.archos.mediacenter.utils.trakt.TraktAPI.AuthParam;
import com.archos.mediacenter.utils.trakt.TraktAPI.MovieWatchingParam;
import com.archos.mediacenter.utils.trakt.TraktAPI.ShowWatchingParam;
import com.archos.mediacenter.utils.videodb.VideoDbInfo;
import com.archos.medialib.R;
import com.archos.mediaprovider.video.VideoStore;
import com.uwetrottmann.trakt5.TraktV2;
import com.uwetrottmann.trakt5.entities.AccessToken;
import com.uwetrottmann.trakt5.entities.BaseMovie;
import com.uwetrottmann.trakt5.entities.BaseShow;
import com.uwetrottmann.trakt5.entities.EpisodeIds;
import com.uwetrottmann.trakt5.entities.EpisodeProgress;
import com.uwetrottmann.trakt5.entities.GenericProgress;
import com.uwetrottmann.trakt5.entities.LastActivities;
import com.uwetrottmann.trakt5.entities.ListEntry;
import com.uwetrottmann.trakt5.entities.MovieIds;
import com.uwetrottmann.trakt5.entities.MovieProgress;
import com.uwetrottmann.trakt5.entities.SyncEpisode;
import com.uwetrottmann.trakt5.entities.SyncItems;
import com.uwetrottmann.trakt5.entities.SyncMovie;
import com.uwetrottmann.trakt5.entities.SyncResponse;
import com.uwetrottmann.trakt5.entities.TraktList;
import com.uwetrottmann.trakt5.entities.UserSlug;
import com.uwetrottmann.trakt5.enums.Extended;
import com.uwetrottmann.trakt5.enums.ListPrivacy;

import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.threeten.bp.OffsetDateTime;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class Trakt {
    private static final String TAG = "Trakt";
    private static final boolean DBG = false;
    public static final long ASK_RELOG_FREQUENCY = 1000 * 60 * 60 * 6; // every 6 hours
    public static long sLastTraktRefreshToken = 0; //will be set by activities, representing last time a user has been asked to log again in trakt;
    public static final String TRAKT_ISSUE_REFRESH_TOKEN = "TRAKT_ISSUE_REFRESH_TOKEN";
    private static final String API_URL = "https://api.trakt.tv";
    private static String API_KEY;
    private static String API_SECRET;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ", Locale.US);
    public static final int SCROBBLE_THRESHOLD = 85;

    private static final String XML_PREFIX = ".trakt_";
    private static final String XML_SUFFIX = "_db.xml";
    private static final int MAX_TRIAL = 7;
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

    public static final int TRAKT_DB_MARKED = 1;
    public static final int TRAKT_DB_UNMARK = 2;

    public static final int WATCHING_DELAY_MS = 600000; // 10 min
    private static final long WAIT_BEFORE_NEXT_TRIAL = 2000;

    private static final String REDIRECT_URI = "http://localhost";

    private final Context mContext;

    private Listener mListener;

    private boolean mWatchingSuccess = false;

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
            if (DBG) {
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
            RETOFIT_RESPONSE
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

        //test
        //mTraktV2.setAccessToken("911cfb2e98258328fd95a12d593f6e72e5412cff3f4ce9772e2b3a7b8af121fd");
        //setRefreshToken(PreferenceManager.getDefaultSharedPreferences(context),"");
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }
    public static OAuthClientRequest getAuthorizationRequest(SharedPreferences pref) throws OAuthSystemException{
        String sampleState = new BigInteger(130, new SecureRandom()).toString(32);
        String url = getTraktV2().buildAuthorizationUrl(sampleState);
        return OAuthClientRequest
                .authorizationLocation(url)
                .buildQueryMessage();
    }

    // redefine minimal class not to export the whole trakt5 to Video with AccessToken
    public static class accessToken {
        public String access_token;
        public String refresh_token;
    }

    public static accessToken getAccessToken(String code){
        try {
            final retrofit2.Response<AccessToken> response = getTraktV2().exchangeCodeForAccessToken(code);
            final accessToken mAccessToken = new accessToken();
            mAccessToken.access_token = response.body().access_token;
            mAccessToken.refresh_token = response.body().refresh_token;
            return mAccessToken;
        } catch (IOException e) {
            Log.e(TAG, "getAccessToken: caught IoException ", e);
        }
        return null;
    }

    private Result handleRet(CallbackLock lock, Exception error, Object object, Result.ObjectType objectType) {
        Result result = null;
        if (error != null || object == null || objectType == ObjectType.NULL) {
            Status status;
            status = Status.ERROR;
            if(error instanceof AuthentificationError) {
                status = Status.ERROR_AUTH;
            }
            if(error instanceof IOException) {
                status = Status.ERROR_NETWORK;
            }

            result = new Result(status, error, objectType);
        } else {
            if (objectType == ObjectType.RESPONSE) {
                SyncResponse response = (SyncResponse) object;
                Status status;
                // TODO: test check if mark as seen already marked as seen generates an error
                // test avp clear seen, launch video avp, mark on trakt web seen, end video what happens?
                /*
                if (response.error != null)
                    status = response.error.endsWith("already") ? Status.SUCCESS_ALREADY : Status.ERROR;
                else
                    status = Status.SUCCESS;
                 */
                status = Status.SUCCESS;
                result = new Result(status, response, objectType);
            } else {
                result = new Result(Status.SUCCESS, object, objectType);
            }
        }
        if (lock != null){
        	lock.notify(result);
        }
        else if (mListener != null)
            mListener.onResult(result);
        return result;
    }

    public static String getDateFormat(long currentTimeSecond) {
        return currentTimeSecond > 0 ? DATE_FORMAT.format(new Date(currentTimeSecond * 1000L)) : null;
    }
   
    public Result markAs(final String action, final SyncItems param, final boolean isShow, final int trial){
        if (DBG) Log.d(TAG, "markAs "+action+" "+trial);
        //TraktAPI.Response response = null;
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
            return handleRet(null, new Exception(), null, ObjectType.NULL);
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
                ei.tvdb = Integer.valueOf(videoInfo.scraperEpisodeId);             
                se.id(ei);
                if(videoInfo.lastTimePlayed>0)
                	se.watchedAt(OffsetDateTime.parse(getDateFormat(videoInfo.lastTimePlayed)));
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
            param.tvdb_id = videoInfo.scraperShowId;
            param.episode_tvdb_id = videoInfo.scraperEpisodeId;
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

    public Result postWatching(VideoDbInfo videoInfo, float progress){
    	return  postWatching("start", videoInfo, progress, 0);
    }

    public Result postWatching(final String action,final VideoDbInfo videoInfo, final float progress, final int trial) {
        if (DBG) Log.d(TAG, "postWatching progress=" + progress + ", trial=" + trial);
        Void arg0 = null;
        AuthParam param = fillParam(videoInfo);
        if (videoInfo.isShow) {
            ShowWatchingParam showParam = (ShowWatchingParam )param;
            showParam.progress = (int) progress;
            SyncEpisode e = new SyncEpisode();
            EpisodeIds ids = new EpisodeIds();
            if(showParam.episode_tvdb_id!=null) {
                if (DBG) Log.d(TAG, "this is a show with id " + showParam.episode_tvdb_id);
                ids.tvdb = Integer.valueOf(showParam.episode_tvdb_id);
                e.season(videoInfo.scraperSeasonNr);
                e.number(videoInfo.scraperEpisodeNr);
            }
            e.id(ids);
            EpisodeProgress ep = new EpisodeProgress();
            ep.progress = progress;
            ep.episode=e;
            if (DBG) Log.d(TAG, "postWatching: EpisodeProgres=" + ep.progress + ", episode id " + e.ids + " season " + e.season + " number " + e.number);
            if(action.equals("start"))
                arg0 = exec(mTraktV2.scrobble().startWatching(ep));
            else
                arg0 = exec(mTraktV2.scrobble().stopWatching(ep));
        } else {
            MovieWatchingParam movieParam = (MovieWatchingParam) param;
            movieParam.progress = (int) progress;
            MovieProgress mp = new MovieProgress();
            mp.progress=progress;
            MovieIds mi = new MovieIds();
            if(movieParam.tmdb_id!=null)
                mi.tmdb=Integer.valueOf(movieParam.tmdb_id);
            SyncMovie sm= new SyncMovie();
            sm.id(mi);
            mp.movie=sm;
            if (DBG) Log.d(TAG, "postWatching: MovieProgress=" + mp);
            if(action.equals("start"))
                arg0 = exec(mTraktV2.scrobble().startWatching(mp));
            else
                arg0 = exec(mTraktV2.scrobble().stopWatching(mp));
        }
        mWatchingSuccess = true;
        if (arg0 == null) {
            cleanWatching();
            return handleRet(null, null, arg0, ObjectType.NULL);
        }
        return handleRet(null, null, arg0, ObjectType.RESPONSE);
    }

    public static void setRefreshToken(SharedPreferences sharedPreferences, String refreshToken) {
        Editor editor = sharedPreferences.edit();
        if (refreshToken != null) {
            editor.putString(KEY_TRAKT_REFRESH_TOKEN, refreshToken);
        } else {
            editor.remove(KEY_TRAKT_REFRESH_TOKEN);
        }
        editor.commit();
    }

    private String getRefreshTokenFromPreferences(SharedPreferences defaultSharedPreferences) {
        return defaultSharedPreferences.getString(KEY_TRAKT_REFRESH_TOKEN,"");
    }

    private boolean refreshAccessToken() {
        Log.d(TAG, "refreshAccessToken()");
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
                if (!token.isSuccessful()) {
                    Log.d(TAG, "Failed refreshing token " + token.toString());
                    Intent intent = new Intent(TRAKT_ISSUE_REFRESH_TOKEN);
                    intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                    mContext.sendBroadcast(intent);
                }
                mTraktV2.accessToken(token.body().access_token);
                setAccessToken(pref, token.body().access_token);
                setRefreshToken(pref, token.body().refresh_token);
                return true;
            } catch (IOException ioe) {
                Log.e(TAG, "getAccessToken: caught IOException " + ioe);
                return false;
            }
        }
        return false;
    }

    public Result getAllShows(String library) {

        return getAllShows(library, 0);
    }
    private Result getAllShows(String library, int trial){
        Log.d(TAG, "getAllShows");
        List<BaseShow> ret = null;
        if (library.equals(Trakt.LIBRARY_WATCHED)) {
            ret = exec(mTraktV2.sync().watchedShows(Extended.FULL));
            if (ret == null)
                return handleRet(null, new Exception(), null, ObjectType.NULL);
            return handleRet(null, null, ret, ObjectType.SHOWS_PER_SEASON);
        } else {
            ret = exec(mTraktV2.sync().collectionShows(Extended.FULL));
            if (ret == null)
                return handleRet(null, new Exception(), null, ObjectType.NULL);
            return handleRet(null, null, ret, ObjectType.SHOWS_PER_SEASON);
        }
    }

    public Result getPlaybackStatus(int trial){
        List<GenericProgress> list = exec(mTraktV2.sync().getPlayback());
        if(list == null)
            return handleRet(null, new Exception(), null, ObjectType.NULL);
        return handleRet(null, null, list, ObjectType.MOVIES);
    }
    public Result getPlaybackStatus() {
        return getPlaybackStatus(0);
	}

    public Result getAllMovies(String library, boolean sync) {
        return getAllMovies(library, 0);
    }

    public static class AuthentificationError extends Exception{};

    public <T> T exec(retrofit2.Call<T> call) {
        return exec(call, MAX_TRIAL);
    }
    public <T> T exec(retrofit2.Call<T> call, int remaining) {
        try {
            retrofit2.Response<T> res = call.execute();
            if (res.errorBody() != null) {
                Log.e(TAG, "exec request " + res.errorBody().string(), new Throwable());
                if (res.code() == 401) {
                    if (remaining > 0) {
                        refreshAccessToken();
                        return exec(call, 0);
                    } else {
                        throw new AuthentificationError();
                    }
                } else {
                    throw new Exception(res.errorBody().toString());
                }
            }
            return res.body();
        } catch(Exception e) {
            try { Thread.sleep(WAIT_BEFORE_NEXT_TRIAL); } catch (Exception a) {}
            if(remaining == 0) {
                return null;
            }
            return exec(call, remaining-1);
        }
    }

    public Result getAllMovies(final String library, int trial) {
        if (DBG) Log.d(TAG, "getAllMovies");
        List<BaseMovie> arg0 = null;
        if (library.equals(Trakt.LIBRARY_WATCHED))
            arg0 = exec(mTraktV2.sync().watchedMovies(Extended.FULL));
        else
            arg0 = exec(mTraktV2.sync().collectionMovies(Extended.FULL));
        if(arg0 == null)
            return handleRet(null, new Exception(), null, ObjectType.NULL);
        return handleRet(null, null, arg0, ObjectType.MOVIES);
    }

    public Result getLastActivity(int trial) {
        if (DBG) Log.d(TAG, "getLastActivity");
        LastActivities ret = exec(mTraktV2.sync().lastActivities());
        if(ret == null)
            return handleRet(null, new Exception(), null, ObjectType.NULL);
        return handleRet(null, null, ret, ObjectType.LAST_ACTIVITY);
    }

    /* add new list to trakt profile */
    public Result addList(int trial, String title) {
        if (DBG) Log.d(TAG, "addList");
        TraktList list = new TraktList();
        list.name = title;
        list.privacy = ListPrivacy.fromValue("private");

        TraktList result = exec(mTraktV2.users().createList(UserSlug.ME, list));
        if (result == null)
            return handleRet(null, new Exception(), null, ObjectType.NULL);
        return handleRet(null, null, result, ObjectType.LIST);
    }

    public Result deleteList(int trial, String id) {
        if (DBG) Log.d(TAG, "deleteList");
        Void response = exec(mTraktV2.users().deleteList(UserSlug.ME, id));
        if (response == null)
            return handleRet(null, new Exception(), null, ObjectType.NULL);
        return handleRet(null, null, response, ObjectType.NULL);
        /*
        if (response.getStatus() == 200)
            return handleRet(null, null, response, ObjectType.RETOFIT_RESPONSE);
        else
            return Result.getError();
         */
    }

    public Result getLists(int trial) {
        if (DBG) Log.d(TAG, "getLists");
        List<TraktList> lists = exec(mTraktV2.users().lists(UserSlug.ME));
        if (lists == null)
            return handleRet(null, new Exception(), null, ObjectType.NULL);
        return handleRet(null, null, lists, ObjectType.LIST);
    }

    public Result getListContent(int trial, int listId) {
        if (DBG) Log.d(TAG, "getListContent");
        List<ListEntry> items = exec(mTraktV2.users().listItems(UserSlug.ME, String.valueOf(listId), null));
        if (items == null)
            return handleRet(null, new Exception(), null, ObjectType.NULL);
        return handleRet(null, null, items, ObjectType.LIST);
    }

    public Result removeVideoFromList(int trial, int listId, ListEntry onlineItem) {
        if (DBG) Log.d(TAG, "removeVideoFromLit");
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
            return handleRet(null, new Exception(), null, ObjectType.NULL);
        if(ret.deleted.episodes+ret.deleted.movies>0)
            return handleRet(null, null, ret, ObjectType.SYNC_RESPONSE);
        else
            return handleRet(null, new Exception(), null, ObjectType.NULL);
    }

    public Result addVideoToList(int trial, int listId, VideoStore.VideoList.VideoItem videoItem) {
        if (DBG) Log.d(TAG, "addVideoToList");
        SyncResponse ret = null;
        if (videoItem.episodeId > 0) {
            SyncEpisode se = new SyncEpisode();
            EpisodeIds ei = new EpisodeIds();
            ei.tvdb = Integer.valueOf(videoItem.episodeId);
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
            return handleRet(null, new Exception(), null, ObjectType.NULL);
        if(ret.added.episodes+ret.added.movies>0)
            return handleRet(null, null, ret, ObjectType.SYNC_RESPONSE);
        else
            return handleRet(null, new Exception(), null, ObjectType.NULL);
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
        editor.commit();
    }
    
    public static void setAccessToken(SharedPreferences pref, String accessToken) {
        Editor editor = pref.edit();
        if (accessToken != null) {
            editor.putString(KEY_TRAKT_ACCESS_TOKEN, accessToken);
        } else {
            editor.remove(KEY_TRAKT_ACCESS_TOKEN);
        }
        editor.commit();
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

    public static void setFlagSyncPreference(SharedPreferences preferences, int flag) {
        int oldFlag = preferences.getInt(KEY_TRAKT_SYNC_FLAG, 0);
        if (flag != 0)
            flag |= oldFlag;
        Editor editor = preferences.edit();
        editor.putInt(KEY_TRAKT_SYNC_FLAG, flag);
        editor.commit();
    }

    public static long getLastTimeShowWatched(SharedPreferences preferences) {
        return preferences.getLong(KEY_TRAKT_LAST_TIME_SHOW_WATCHED, 0);
    }

    public static void setLastTimeShowWatched(SharedPreferences preferences, long time) {
        Editor editor = preferences.edit();
        editor.putLong(KEY_TRAKT_LAST_TIME_SHOW_WATCHED, time);
        editor.commit();
    }

    public static long getLastTimeMovieWatched(SharedPreferences preferences) {
        return preferences.getLong(KEY_TRAKT_LAST_TIME_MOVIE_WATCHED, 0);
    }

    public static void setLastTimeMovieWatched(SharedPreferences preferences, long time) {
        Editor editor = preferences.edit();
        editor.putLong(KEY_TRAKT_LAST_TIME_MOVIE_WATCHED, time);
        editor.commit();
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
        }
        editor.remove(Trakt.KEY_TRAKT_SYNC_FLAG);
        editor.remove(Trakt.KEY_TRAKT_LAST_TIME_MOVIE_WATCHED);
        editor.remove(Trakt.KEY_TRAKT_LAST_TIME_SHOW_WATCHED);
        editor.commit();
    }

    public static boolean shouldMarkAsSeen(float progress) {
        return progress >= SCROBBLE_THRESHOLD;
    }
	
}
