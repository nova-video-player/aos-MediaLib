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
import android.preference.PreferenceManager;
import android.util.Log;

import com.archos.environment.ArchosUtils;
import com.archos.mediacenter.utils.trakt.Trakt.Result.ObjectType;
import com.archos.mediacenter.utils.trakt.TraktAPI.AuthParam;
import com.archos.mediacenter.utils.trakt.TraktAPI.MovieWatchingParam;
import com.archos.mediacenter.utils.trakt.TraktAPI.Response;
import com.archos.mediacenter.utils.trakt.TraktAPI.ShowWatchingParam;
import com.archos.mediacenter.utils.videodb.VideoDbInfo;
import com.archos.medialib.R;
import com.archos.mediaprovider.video.VideoStore;
import com.uwetrottmann.trakt.v2.TraktHttpClient;
import com.uwetrottmann.trakt.v2.TraktV2;
import com.uwetrottmann.trakt.v2.entities.BaseMovie;
import com.uwetrottmann.trakt.v2.entities.BaseShow;
import com.uwetrottmann.trakt.v2.entities.EpisodeIds;
import com.uwetrottmann.trakt.v2.entities.EpisodeProgress;
import com.uwetrottmann.trakt.v2.entities.GenericProgress;
import com.uwetrottmann.trakt.v2.entities.LastActivities;
import com.uwetrottmann.trakt.v2.entities.ListEntry;
import com.uwetrottmann.trakt.v2.entities.MovieIds;
import com.uwetrottmann.trakt.v2.entities.MovieProgress;
import com.uwetrottmann.trakt.v2.entities.SyncEpisode;
import com.uwetrottmann.trakt.v2.entities.SyncItems;
import com.uwetrottmann.trakt.v2.entities.SyncMovie;
import com.uwetrottmann.trakt.v2.entities.SyncResponse;
import com.uwetrottmann.trakt.v2.enums.Extended;
import com.uwetrottmann.trakt.v2.enums.ListPrivacy;
import com.uwetrottmann.trakt.v2.exceptions.OAuthUnauthorizedException;

import org.apache.oltu.oauth2.client.OAuthClient;
import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.client.response.OAuthAccessTokenResponse;
import org.apache.oltu.oauth2.client.response.OAuthJSONAccessTokenResponse;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.joda.time.DateTime;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.android.AndroidLog;

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

    private final Context mContext;

    private Listener mListener;

    private boolean mRequestPending;
    private boolean mWatchingSuccess = false;
    private TraktV2 mTraktV2;

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

    public Trakt(Context context) {
        mContext = context;
        mTraktV2 = new TraktV2();
        if (DBG)
            mTraktV2.setIsDebug(true);
        else
            mTraktV2.setIsDebug(false);
        mTraktV2.setAccessToken(
                getAccessTokenFromPreferences(
                        PreferenceManager.getDefaultSharedPreferences(context)));

        //test
        //mTraktV2.setAccessToken("911cfb2e98258328fd95a12d593f6e72e5412cff3f4ce9772e2b3a7b8af121fd");
        //setRefreshToken(PreferenceManager.getDefaultSharedPreferences(context),"");

        mTraktV2.setApiKey(API_KEY);

        RestAdapter restAdapter = new RestAdapter.Builder()
                .setEndpoint(API_URL)
                .setLog(new AndroidLog(TAG))
                .setRequestInterceptor(new RequestInterceptor() {
                    @Override
                    public void intercept(RequestFacade request) {
    /*  String string = "Basic " + Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
        request.addHeader("Accept", "application/json");
        request.addHeader("Authorization", string); */
                    }
                })
                .setExecutors(Executors.newSingleThreadExecutor(), Executors.newSingleThreadExecutor())
                .build();
        if (DBG)
            restAdapter.setLogLevel(RestAdapter.LogLevel.FULL);
        else
            restAdapter.setLogLevel(RestAdapter.LogLevel.NONE);
        restAdapter.create(TraktAPI.class);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }
    public static OAuthClientRequest getAuthorizationRequest(SharedPreferences pref) throws OAuthSystemException{
    	return TraktV2.getAuthorizationRequest(API_KEY,"http://localhost","test",getUserFromPreferences(pref)!=null?getUserFromPreferences(pref):"");
    }
    public static OAuthAccessTokenResponse getAccessToken(String code){
    	OAuthAccessTokenResponse res=null;
    	try {
    		res = TraktV2.getAccessToken(Trakt.API_KEY, API_SECRET, "http://localhost", code);
    	} catch (OAuthSystemException e) {
    		// TODO Auto-generated catch block
    		e.printStackTrace();
    	} catch (OAuthProblemException e) {
    		// TODO Auto-generated catch block
    		e.printStackTrace();
    	}

    	return res;
    }




    private Result handleRet(CallbackLock lock, RetrofitError error, Object object, Result.ObjectType objectType) {
        Result result = null;
        if (error != null || object == null || objectType == ObjectType.NULL) {
            Status status;
            if (error!=null&&error.isNetworkError()) {
                status = Status.ERROR_NETWORK;
            } else if (error!=null&&error.getResponse() != null) {
                final int httpStatus = error.getResponse().getStatus();
                if (httpStatus == 401)
                    status = Status.ERROR_AUTH;
                else if (httpStatus == 503)
                    status = Status.ERROR_NETWORK;
                else
                    status = Status.ERROR;
            } else {
                status = Status.ERROR;
            }

            result = new Result(status, error, objectType);
        } else {
            if (objectType == ObjectType.RESPONSE) {
                Response response = (Response) object;
                Status status;
                if (response.error != null)
                    status = response.error.endsWith("already") ? Status.SUCCESS_ALREADY : Status.ERROR;
                else
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
        Log.d(TAG, "markAs "+action+" "+trial);

        TraktAPI.Response response = null;
        try {
            if (action.equals(Trakt.ACTION_SEEN))
                try {
                    response = mTraktV2.sync().addItemsToWatchedHistory(param);
                    return  handleRet(null, null, response, ObjectType.RESPONSE);
                } catch (OAuthUnauthorizedException e) {
                    if(trial<1&&refreshAccessToken()){

                        markAs(action, param, isShow, trial + 1);
                    }
                    e.printStackTrace();
                }
            if (action.equals(Trakt.ACTION_UNSEEN)) {
                try {
                    response = mTraktV2.sync().deleteItemsFromWatchedHistory(param);
                    return handleRet(null, null, response, ObjectType.RESPONSE);
                } catch (OAuthUnauthorizedException e) {
                    if(trial<1&&refreshAccessToken()){
                        markAs(action, param,isShow,  trial+1);
                    }
                }
            } else if (action.equals(Trakt.ACTION_LIBRARY)) {
                try {
                    response = mTraktV2.sync().addItemsToCollection(param);
                    return handleRet(null, null, response, ObjectType.RESPONSE);
                } catch (OAuthUnauthorizedException e) {
                    if(trial<1&&refreshAccessToken()){
                        markAs(action, param,isShow,  trial+1);
                    }
                }
            } else if (action.equals(Trakt.ACTION_UNLIBRARY)) {
                try {
                    response = mTraktV2.sync().deleteItemsFromCollection(param);
                    return handleRet(null, null, response, ObjectType.RESPONSE);
                } catch (OAuthUnauthorizedException e) {
                    if(trial<1&&refreshAccessToken()){
                        markAs(action, param,isShow,  trial+1);
                    }
                }
            }
        }
        catch(RetrofitError error){
            mRequestPending = false;

            if(trial<MAX_TRIAL&&!error.isNetworkError()){

                try {
                    Thread.sleep(WAIT_BEFORE_NEXT_TRIAL);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return markAs(action, param, isShow, trial + 1);
            }
            else{

                return handleRet(null, error, null, ObjectType.NULL);
            }
        }
          return Result.getAsync();
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
                	se.watchedAt(DateTime.parse(getDateFormat(videoInfo.lastTimePlayed)));
                SyncItems sitems = new SyncItems();
                sitems.episodes(se);

                return markAs(action, sitems, videoInfo.isShow);
            } else {
                SyncMovie sm = new SyncMovie();
                MovieIds mi = new MovieIds();
                mi.tmdb = Integer.valueOf(videoInfo.scraperMovieId);
                if(videoInfo.lastTimePlayed>0)
                	sm.watchedAt(DateTime.parse(getDateFormat(videoInfo.lastTimePlayed)));
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
    public Result postWatching(VideoDbInfo videoInfo, float progress){
    	return  postWatching("start", videoInfo, progress, 0);
    }
    public Result postWatching(final String action,final VideoDbInfo videoInfo, final float progress, final int trial) {
        Log.d(TAG, "postWatching "+trial);
        try {
            TraktAPI.Response arg0=null;
            AuthParam param = fillParam(videoInfo);
            if (videoInfo.isShow) {
                ShowWatchingParam showParam = (ShowWatchingParam )param;
                showParam.progress = (int) progress;
                SyncEpisode e = new SyncEpisode();
                EpisodeIds ids = new EpisodeIds();
                if(showParam.episode_tvdb_id!=null)
                    ids.tvdb = Integer.valueOf(showParam.episode_tvdb_id);
                e.id(ids);
                EpisodeProgress ep = new EpisodeProgress();
                ep.progress = progress;
                ep.episode=e;
                try{
                    if(action.equals("start"))
                        arg0 = mTraktV2.scrobble().startWatching(ep);
                    else
                        arg0 = mTraktV2.scrobble().stopWatching(ep);
                } catch (OAuthUnauthorizedException e1){
                    if(trial<1&&refreshAccessToken()){
                        postWatching(action, videoInfo,progress,  trial+1);
                    }
                }
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
                try {
                    if(action.equals("start"))
                        arg0 = mTraktV2.scrobble().startWatching(mp);
                    else
                        arg0 = mTraktV2.scrobble().stopWatching(mp);
                } catch (OAuthUnauthorizedException e) {
                    if(trial<1&&refreshAccessToken()){
                        postWatching(action, videoInfo,progress,  trial+1);
                    }
                }
            }
            mWatchingSuccess = true;
            return handleRet(null, null, arg0, ObjectType.RESPONSE);
        }
        catch (RetrofitError arg0) {
            Log.d(TAG, "failure: isNetworkError: " + arg0.isNetworkError());
            if (!arg0.isNetworkError()&& trial>=MAX_TRIAL){
                cleanWatching();
                return handleRet(null, arg0, null, ObjectType.NULL);
            }
            else if(!arg0.isNetworkError()&&  trial <MAX_TRIAL){
                try {
                    Thread.sleep(WAIT_BEFORE_NEXT_TRIAL);
                    return postWatching(action, videoInfo, progress, trial + 1);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            }
            else if(isAuthError(arg0)&&trial <1){
                Log.d(TAG, "postWatching bis");
                if(refreshAccessToken())
                    return postWatching(action, videoInfo, progress, trial + 1);
                else
                    return  handleRet(null, arg0, null, ObjectType.NULL);
            }
            else return  handleRet(null, arg0, null, ObjectType.NULL);
        }
        return Result.getAsync();
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

    private boolean isAuthError(RetrofitError arg0) {

        return false;
    }

    private boolean refreshAccessToken() {
        Log.d(TAG, "refreshAccessToken()");
        try {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mContext);
            String refreshToken = getRefreshTokenFromPreferences(pref);
            if(refreshToken==null|| refreshToken.isEmpty()){
                Intent intent = new Intent(TRAKT_ISSUE_REFRESH_TOKEN);
                intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                mContext.sendBroadcast(intent);
            }
            else {
                OAuthClientRequest request = TraktV2.getAccessTokenRefreshRequest(API_KEY, API_SECRET, "http://localhost", refreshToken);

                OAuthClient client = new OAuthClient(new TraktHttpClient());
                OAuthJSONAccessTokenResponse res = client.accessToken(request);
                if (res != null && res.getAccessToken() != null && !res.getAccessToken().isEmpty()) {
                    setAccessToken(pref, res.getAccessToken());
                    mTraktV2.setAccessToken(res.getAccessToken());
                    setRefreshToken(pref, res.getRefreshToken());
                    return true;
                }
            }

        } catch (OAuthSystemException e) {
            Intent intent = new Intent(TRAKT_ISSUE_REFRESH_TOKEN);
            intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
            mContext.sendBroadcast(intent);
        } catch (OAuthProblemException e) {
            Intent intent = new Intent(TRAKT_ISSUE_REFRESH_TOKEN);
            intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
            mContext.sendBroadcast(intent);
        }
        return false;
    }



    public Result postWatchingStop(final VideoDbInfo videoInfo, float progress) {
        return postWatching("stop", videoInfo, progress, 0);
    }

    public Result getAllShows(String library) {

        return getAllShows(library, 0);
    }
    private Result getAllShows(String library, int trial){
        Log.d(TAG, "getAllShows");
        List<BaseShow> ret = null;
        try {
            if (library.equals(Trakt.LIBRARY_WATCHED))
                try {
                    ret = mTraktV2.sync().watchedShows(Extended.DEFAULT_MIN);

                    return handleRet(null, null, ret, ObjectType.SHOWS_PER_SEASON);
                } catch (OAuthUnauthorizedException e) {
                    if(trial<1&&refreshAccessToken())
                        return  getAllShows( library, trial + 1);
                }
            else
                try {
                    ret = mTraktV2.sync().collectionShows(Extended.DEFAULT_MIN);
                    return handleRet(null, null, ret, ObjectType.SHOWS_PER_SEASON);
                } catch (OAuthUnauthorizedException e) {
                    if(trial<1&&refreshAccessToken())
                        return  getAllShows( library, trial + 1);
                }
        }
        catch(RetrofitError error){
            return handleRet(null, error, null, ObjectType.NULL);
        }

        return Result.getAsync();
    }
    public Result getPlaybackStatus(int trial){
        Log.d(TAG, "getPlaybackStatus "+trial);
        try {
            List<GenericProgress> list = mTraktV2.sync().getPlayback();
            return handleRet(null, null, list, ObjectType.MOVIES);

        } catch (OAuthUnauthorizedException e) {
            if(trial<1&&refreshAccessToken()){
                getPlaybackStatus(trial+1);
            }
            e.printStackTrace();
        } catch(retrofit.RetrofitError error){
            try {if(!error.isNetworkError()&&trial<MAX_TRIAL&&error.getResponse()!=null&&error.getResponse().getStatus()!=403) {
                Thread.sleep(WAIT_BEFORE_NEXT_TRIAL);
                return getPlaybackStatus(trial+1);
            }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
        return Result.getAsync();
    }
    public Result getPlaybackStatus() {
        return getPlaybackStatus(0);
	}
    public Result getAllMovies(String library, boolean sync) {

        return getAllMovies(library, 0);
    }
    public Result getAllMovies(final String library, int trial) {
        Log.d(TAG, "getAllMovies");
        List<BaseMovie> arg0 = null;
        try {
            if (library.equals(Trakt.LIBRARY_WATCHED))
                try {
                    arg0 = mTraktV2.sync().watchedMovies(Extended.DEFAULT_MIN);
                } catch (OAuthUnauthorizedException e) {
                    if (trial < 1 && refreshAccessToken()) {
                        return getAllMovies(library, trial + 1);
                    }
                }
            else
                try {
                    arg0 = mTraktV2.sync().collectionMovies(Extended.DEFAULT_MIN);
                } catch (OAuthUnauthorizedException e) {
                    if (trial < 1 && refreshAccessToken()) {
                        return getAllMovies(library, trial + 1);
                    }
                }
            return handleRet(null, null, arg0, ObjectType.MOVIES);
        } catch (RetrofitError error) {
            if (!error.isNetworkError() && trial < MAX_TRIAL && error.getResponse() != null && error.getResponse().getStatus() != 403) {
                try {
                    Thread.sleep(WAIT_BEFORE_NEXT_TRIAL);
                    return getAllMovies(library, trial + 1);
                } catch (InterruptedException e) {
                    return handleRet(null, error, null, ObjectType.NULL);
                }
            } else {
                return handleRet(null, error, null, ObjectType.NULL);
            }
        }
    }

    public Result getLastActivity(int trial) {
        Log.d(TAG, "getLastActivity");
        try {
            LastActivities ret = mTraktV2.sync().lastActivities();
            return handleRet(null, null, ret, ObjectType.LAST_ACTIVITY);
        } catch (OAuthUnauthorizedException e) {
            if (trial < 1 && refreshAccessToken()) {
                return getLastActivity(trial + 1);
            }
        } catch (RetrofitError error) {
            if (!error.isNetworkError() && trial < MAX_TRIAL) {
                try {
                    Thread.sleep(WAIT_BEFORE_NEXT_TRIAL);
                    return getLastActivity(trial + 1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else
                return handleRet(null, error, null, ObjectType.NULL);
        }
        return Result.getAsync();
    }

    /* add new list to trakt profile */
    public Result addList(int trial, String title) {
        com.uwetrottmann.trakt.v2.entities.List list = new com.uwetrottmann.trakt.v2.entities.List();
        list.name = title;
        list.privacy = ListPrivacy.fromValue("private");
        try {
            com.uwetrottmann.trakt.v2.entities.List result = mTraktV2.users().createList(mTraktV2.users().settings().user.username, list);

            return handleRet(null, null, result, ObjectType.LIST);
        } catch (OAuthUnauthorizedException e) {
            if (trial < 1 && refreshAccessToken()) {
                return addList(trial + 1, title);
            } else
                return Result.getError();
        }
        catch (RetrofitError error) {
            if (!error.isNetworkError() && trial < MAX_TRIAL) {
                try {
                    Thread.sleep(WAIT_BEFORE_NEXT_TRIAL);
                    return addList(trial+1,title);
                } catch (InterruptedException e) {
                    return Result.getError();
                }
            } else
                return Result.getError();
        }
    }

    public Result deleteList(int trial, String id) {
        try {
            retrofit.client.Response response = mTraktV2.users().deleteList(mTraktV2.users().settings().user.username, id);
            if (response.getStatus() == 200)
                return handleRet(null, null, response, ObjectType.RETOFIT_RESPONSE);
            else
                return Result.getError();
        } catch (OAuthUnauthorizedException e) {
            if (trial < 1 && refreshAccessToken()) {
                return deleteList(trial + 1, id);
            } else
                return Result.getError();
        }
        catch (RetrofitError error) {
            if (!error.isNetworkError() && trial < MAX_TRIAL) {
                try {
                    Thread.sleep(WAIT_BEFORE_NEXT_TRIAL);
                    return deleteList(trial + 1,id);
                } catch (InterruptedException e) {
                    return Result.getError();
                }
            } else
                return Result.getError();
        }
    }

    public Result getLists(int trial) {
        try {
            List<com.uwetrottmann.trakt.v2.entities.List> lists = mTraktV2.users().lists(mTraktV2.users().settings().user.username);
            return handleRet(null, null, lists, ObjectType.LIST);
        } catch (OAuthUnauthorizedException e) {
            if (trial < 1 && refreshAccessToken()) {
                return getLists(trial + 1);
            } else
                return Result.getError();
        }
        catch (RetrofitError error) {
            if (!error.isNetworkError() && trial < MAX_TRIAL) {
                try {
                    Thread.sleep(WAIT_BEFORE_NEXT_TRIAL);
                    return getLists(trial + 1);
                } catch (InterruptedException e) {
                    return Result.getError();
                }
            } else
                return Result.getError();
        }
    }

    public Result getListContent(int trial, int listId) {
        try {
            List<ListEntry> items = mTraktV2.users().listItems(mTraktV2.users().settings().user.username, String.valueOf(listId), null);
            return handleRet(null, null, items, ObjectType.LIST);

        } catch (OAuthUnauthorizedException e) {
            e.printStackTrace();
            if (trial < 1 && refreshAccessToken()) {
                return getListContent(trial + 1, listId);
            } else
                return Result.getError();
        }
        catch (retrofit.RetrofitError e) {
            e.printStackTrace();
            if (!e.isNetworkError() && trial < MAX_TRIAL) {
                return getListContent(trial + 1, listId);
            } else
                return Result.getError();
        }
    }

    public Result removeVideoFromList(int trial, int listId, ListEntry onlineItem) {
        try {
            SyncItems syncItems = new SyncItems();
            if(onlineItem.episode!=null) {
                SyncEpisode syncEpisode = new SyncEpisode();
                syncEpisode.id(onlineItem.episode.ids);
                syncItems.episodes(syncEpisode);
            }else{
                SyncMovie syncMovie = new SyncMovie ();
                syncMovie .id(onlineItem.movie.ids);
                syncItems.movies(syncMovie);
            }

            SyncResponse ret = mTraktV2.users().deleteListItems(mTraktV2.users().settings().user.username, String.valueOf(listId), syncItems);
            if(ret.deleted.episodes+ret.deleted.movies>0)
                return handleRet(null, null, ret, ObjectType.SYNC_RESPONSE);
            else
                return Result.getError();
        }catch (OAuthUnauthorizedException e) {
            e.printStackTrace();
            if (trial < 1 && refreshAccessToken()) {
                return removeVideoFromList(trial + 1, listId, onlineItem);
            } else
                return Result.getError();
        }
        catch (retrofit.RetrofitError e) {
            e.printStackTrace();
            if (!e.isNetworkError() && trial < MAX_TRIAL) {
                return removeVideoFromList(trial + 1, listId, onlineItem);
            } else
                return Result.getError();
        }
    }

    public Result addVideoToList(int trial, int listId, VideoStore.VideoList.VideoItem videoItem) {
        try {
            if (videoItem.episodeId > 0) {
                SyncEpisode se = new SyncEpisode();
                EpisodeIds ei = new EpisodeIds();
                ei.tvdb = Integer.valueOf(videoItem.episodeId);
                se.id(ei);
                SyncItems sitems = new SyncItems();
                sitems.episodes(se);
                SyncResponse ret = mTraktV2.users().addListItems(mTraktV2.users().settings().user.username, String.valueOf(listId), sitems);
                if(ret.added.episodes+ret.added.movies>0)
                    return handleRet(null, null, ret, ObjectType.SYNC_RESPONSE);
                else
                    return Result.getError();
            } else {
                SyncMovie sm = new SyncMovie();
                MovieIds mi = new MovieIds();
                mi.tmdb = Integer.valueOf(videoItem.movieId);
                sm.id(mi);
                SyncItems sitems = new SyncItems();
                sitems.movies(sm);
                SyncResponse ret = mTraktV2.users().addListItems(mTraktV2.users().settings().user.username, String.valueOf(listId), sitems);
                if(ret.added.episodes+ret.added.movies>0)
                    return handleRet(null, null, ret, ObjectType.SYNC_RESPONSE);
                else
                    return Result.getError();            }
        } catch (OAuthUnauthorizedException e) {
            e.printStackTrace();
            if(trial<1&&refreshAccessToken()){
                return addVideoToList(trial+1, listId, videoItem);
            }
            else
                return Result.getError();
        }
        catch (retrofit.RetrofitError e) {
            e.printStackTrace();
            if (!e.isNetworkError() &&  trial < MAX_TRIAL) {
                return addVideoToList(trial + 1, listId, videoItem);
            } else
                return Result.getError();
        }
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
