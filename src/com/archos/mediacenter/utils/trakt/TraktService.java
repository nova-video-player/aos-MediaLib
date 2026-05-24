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

import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import androidx.core.content.IntentCompat;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.widget.Toast;

import com.archos.mediacenter.utils.trakt.Trakt.Status;
import com.archos.mediacenter.utils.videodb.VideoDbInfo;
import com.archos.medialib.R;
import com.archos.environment.NetworkState;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.ScrapeStatus;
import com.uwetrottmann.trakt5.entities.BaseEpisode;
import com.uwetrottmann.trakt5.entities.BaseMovie;
import com.uwetrottmann.trakt5.entities.BaseSeason;
import com.uwetrottmann.trakt5.entities.BaseShow;
import com.uwetrottmann.trakt5.entities.EpisodeIds;
import com.uwetrottmann.trakt5.entities.GenericProgress;
import com.uwetrottmann.trakt5.entities.HistoryEntry;
import com.uwetrottmann.trakt5.entities.LastActivities;
import com.uwetrottmann.trakt5.entities.ListEntry;
import com.uwetrottmann.trakt5.entities.MovieIds;
import com.uwetrottmann.trakt5.entities.PlaybackResponse;
import com.uwetrottmann.trakt5.entities.SyncEpisode;
import com.uwetrottmann.trakt5.entities.SyncItems;
import com.uwetrottmann.trakt5.entities.SyncMovie;
import com.uwetrottmann.trakt5.entities.TraktList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.OffsetDateTime;
import org.threeten.bp.ZoneOffset;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TraktService extends Service implements DefaultLifecycleObserver {

    private static final Logger log = LoggerFactory.getLogger(TraktService.class);
    private static final String TAG = "TraktService";

    private SharedPreferences mPreferences;

    private boolean mNetworkStateListenerAdded = false;
    private boolean mWaitBeforeSync = false;
    private static long mLastTimeSync = -1;

    private Trakt mTrakt = null;
    private boolean mBusy = false;
    private final IBinder mBinder = new TraktBinder();
    private HandlerThread mBackgroundHandlerThread;
    private TraktHandler mBackgroundHandler;
    private Handler mUiHandler;
    private Toast mToast = null;
    private NetworkState mNetworkState;

    private static final int MSG_RESULT = 0;
    private static final int MSG_INTENT = 1;
    private static final int MSG_NETWORK_ON = 2;
    private static final int MSG_HANDLER_LIST[] = { MSG_INTENT, MSG_NETWORK_ON };

    private static final long NETWORK_NETWORK_ON_DELAY = 600000; // in ms: 10min
    private static final long NOTIFY_DELAY = 5000; // in ms: 5sec

    private static final String INTENT_ACTION_WATCHING = "archos.mediacenter.utils.trakt.action.WATCHING";
    private static final String INTENT_ACTION_WATCHING_STOP = "archos.mediacenter.utils.trakt.action.WATCHING_STOP";
    private static final String INTENT_ACTION_WATCHING_PAUSE = "archos.mediacenter.utils.trakt.action.WATCHING_PAUSE";
    private static final String INTENT_ACTION_MARK_AS = "archos.mediacenter.utils.trakt.action.MARK_AS";
    private static final String INTENT_ACTION_WIPE = "archos.mediacenter.utils.trakt.action.WIPE";
    private static final String INTENT_ACTION_WIPE_COLLECTION = "archos.mediacenter.utils.trakt.action.WIPE_COLLECTION";
    private static final String INTENT_ACTION_SYNC = "archos.mediacenter.utils.trakt.action.SYNC";
    private static final String INTENT_ACTION_FORCE_PUSH = "archos.mediacenter.utils.trakt.action.FORCE_PUSH";
    private static final String INTENT_ACTION_FORCE_PULL = "archos.mediacenter.utils.trakt.action.FORCE_PULL";

    private boolean mForcePush = false;
    private boolean mForcePull = false;
    /** Count of resume-point downloads skipped in the current sync session due to unknown duration. */
    private int mSyncSkippedNoDuration = 0;

    public static final int FLAG_SYNC_AUTO =                0x001;
    public static final int FLAG_SYNC_LAST_ACTIVITY_VETO =  0x002;
    public static final int FLAG_SYNC_MOVIES =              0x004;
    public static final int FLAG_SYNC_SHOWS =               0x008;
    public static final int FLAG_SYNC_TO_DB_WATCHED =       0x010; // need to resync watched status from trakt to db
    public static final int FLAG_SYNC_TO_DB_COLLECTION =    0x020;
    public static final int FLAG_SYNC_TO_TRAKT_WATCHED =    0x040;
    public static final int FLAG_SYNC_TO_TRAKT_COLLECTION = 0x080;
    public static final int FLAG_SYNC_NOW =                 0x100;
    public static final int FLAG_SYNC_PROGRESS =            0x200; // need to sync resume points
    public static final int FLAG_SYNC_TO_DB = FLAG_SYNC_TO_DB_WATCHED | FLAG_SYNC_TO_DB_COLLECTION;
    public static final int FLAG_SYNC_TO_TRAKT = FLAG_SYNC_TO_TRAKT_WATCHED | FLAG_SYNC_TO_TRAKT_COLLECTION;
    public static final int FLAG_SYNC_FULL = FLAG_SYNC_TO_DB | FLAG_SYNC_TO_TRAKT | FLAG_SYNC_MOVIES | FLAG_SYNC_SHOWS;

    private static final long TRAKT_SYNC_DELAY = 30; // in sec

    public static final String PREFERENCE_TRAKT_LAST_ACTIVITY = "trakt_last_activity";
    public static final String PREFERENCE_TRACK_LAST_ACTIVITY_MOVIE = "trakt_last_activity_movie";
    public static final String PREFERENCE_TRAKT_LAST_ACTIVITY_MOVIE_WATCHED = "trakt_last_activity_movie_watched";
    public static final String PREFERENCE_TRAKT_LAST_ACTIVITY_MOVIE_PAUSED = "trakt_last_activity_movie_paused";
    public static final String PREFERENCE_TRAKT_LAST_ACTIVITY_EPISODE = "trakt_last_activity_episode";
    public static final String PREFERENCE_TRAKT_LAST_ACTIVITY_EPISODE_WATCHED = "trakt_last_activity_episode_watched";
    public static final String PREFERENCE_TRAKT_LAST_ACTIVITY_EPISODE_PAUSED = "trakt_last_activity_episode_paused";
    public static final String PREFERENCE_TRAKT_LAST_ACTIVITY_LIST = "trakt_last_activity_list";
    public static final String PREFERENCE_TRAKT_LAST_ACTIVITY_TIME_CHECKED_UTC = "trakt_last_activity_time_checked_utc";
    public static final String PREFERENCE_TRAKT_LAST_TIME_SYNC_TO_DB_WATCHED = "trakt_last_time_sync_to_db_watched";
    public static final String PREFERENCE_TRAKT_LAST_TIME_SYNC_TO_DB_PROGRESS = "trakt_last_time_sync_to_db_progress";
    public static final String PREFERENCE_TRAKT_LAST_TIME_SYNC_WATCHED = "trakt_last_time_sync_watched";
    public static final String PREFERENCE_TRAKT_LAST_TIME_SYNC_PROGRESS = "trakt_last_time_sync_progress";
    public static final String PREFERENCE_TRAKT_LAST_TIME_SYNC_LIST = "trakt_last_time_sync_list";
    public static final String PREFERENCE_TRAKT_LAST_TIME_SYNC_TO_DB_LIST = "trakt_last_time_sync_to_db_list";
    public static final String PREFERENCE_TRAKT_FIRST_SYNC_DONE = "trakt_first_sync_done";
    /** Outcome of the last background sync: 0=success, 1=network error, 2=auth error, 3=account locked. */
    public static final String PREFERENCE_TRAKT_LAST_SYNC_STATUS = "trakt_last_sync_status";
    public static final int SYNC_STATUS_SUCCESS = 0;
    public static final int SYNC_STATUS_ERROR_NETWORK = 1;
    public static final int SYNC_STATUS_ERROR_AUTH = 2;
    public static final int SYNC_STATUS_ERROR_ACCOUNT_LOCKED = 3;
    /** Number of resume-point downloads skipped in the last sync because the local file duration was unknown. */
    public static final String PREFERENCE_TRAKT_SKIPPED_NO_DURATION = "trakt_skipped_no_duration";
    /** Cumulative number of live scrobble attempts skipped because the video lacked Trakt-compatible metadata. Reset on sign-out. */
    public static final String PREFERENCE_TRAKT_SKIPPED_NO_METADATA = "trakt_skipped_no_metadata";

    private static volatile boolean isForeground = true;

    private NetworkState networkState = null;
    private PropertyChangeListener propertyChangeListener = null;
    public class TraktBinder extends Binder {
        TraktService getService() {
            return TraktService.this;
        }
    }

    private class TraktHandler extends Handler {
        private final Context mContext;
        public TraktHandler(Looper looper, Context context) {
            super(looper);
            mContext = context;
        }

        public boolean hasOtherMessagesPending(int what) {
            for (int i : MSG_HANDLER_LIST) {
                if (i != what && hasMessages(what))
                    return true;
            }
            return false;
        }

        @Override
        public void handleMessage(Message msg) {
            if (log.isDebugEnabled()) log.debug("handleMessage msg={}", msg.toString());
            if (msg.what == MSG_INTENT) {
                Intent intent = (Intent) msg.obj;
                String action = intent.getAction();

                VideoDbInfo videoInfo = IntentCompat.getParcelableExtra(intent, "video_info", VideoDbInfo.class);
                final long videoID = intent.getLongExtra("video_id", -1);
                final Messenger messenger = IntentCompat.getParcelableExtra(intent, "messenger", Messenger.class);
                final boolean notify = intent.getBooleanExtra("notify", false);
                final long intentTime = notify ? intent.getLongExtra("notify_time", -1) : -1;
                Trakt.Result result = null;
                String traktAction = null;
                int lastActivityFlag = 0;

                if (mTrakt == null) {
                    Trakt trakt = new Trakt(TraktService.this);
                    if (trakt.isTraktV2Enabled(TraktService.this, PreferenceManager.getDefaultSharedPreferences(mContext)))
                        mTrakt = trakt;
                }

                if (mTrakt != null) {
                if (action.equals(INTENT_ACTION_WATCHING)) {
                        final float progress = intent.getFloatExtra("progress", -1);
                        if (log.isDebugEnabled()) log.debug("postWatching progress={}", progress);
                        if (videoInfo == null && videoID >= 0)
                            videoInfo = VideoDbInfo.fromId(getContentResolver(), videoID);
                        if (videoInfo != null) {
                            // track scrobbles that will be skipped due to missing Trakt-compatible metadata
                            final String metaId = videoInfo.isShow ? videoInfo.scraperEpisodeId : videoInfo.scraperMovieId;
                            if (metaId == null || metaId.isEmpty()) {
                                final int newSkipCount = mPreferences.getInt(PREFERENCE_TRAKT_SKIPPED_NO_METADATA, 0) + 1;
                                mPreferences.edit().putInt(PREFERENCE_TRAKT_SKIPPED_NO_METADATA, newSkipCount).apply();
                                log.info("postWatching: missing metadata for {}, scrobble will be skipped — count now {}",
                                        videoInfo.scraperTitle, newSkipCount);
                            }
                            final float finalProgress = progress >= 0 ? progress : -progress;
                            result = mTrakt.postWatching(videoInfo, finalProgress);
                            if (videoInfo.traktResume < 0 && (result.status == Trakt.Status.SUCCESS || result.status == Trakt.Status.SUCCESS_ALREADY)) {
                                videoInfo.traktResume = Math.abs(videoInfo.traktResume);
                                ContentValues values = new ContentValues();
                                values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_RESUME, videoInfo.traktResume);
                                getContentResolver().update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
                                        values, VideoStore.Video.VideoColumns._ID + " = " + videoInfo.id, null);

                            }
                            if (log.isDebugEnabled()) log.debug("postWatching, result: {}", result.status);
                            if (result.status != Status.ERROR) {
                                // continue the service even in case of network error
                                mBusy = true;
                            }
                        }
                    } else if (action.equals(INTENT_ACTION_WATCHING_STOP)) {
                        final float progress = intent.getFloatExtra("progress", -1);
                        if (log.isDebugEnabled()) log.debug("postWatchingStop progress={}", progress);
                        if (videoInfo == null && videoID >= 0)
                            videoInfo = VideoDbInfo.fromId(getContentResolver(), videoID);
                        if (videoInfo != null) {
                            if (Trakt.shouldMarkAsSeen(progress)) {
                                traktAction = Trakt.ACTION_SEEN;
                                // get last activity before doing some activity on trakt
                                result = mTrakt.getLastActivity();
                                lastActivityFlag = getFlagsFromTraktLastActivity(result);
                            }
                            if (result == null || result.status != Trakt.Status.ERROR_NETWORK) {

                                final float finalProgress = progress >= 0 ? progress : -progress;
                                result = mTrakt.postWatchingStop(videoInfo, finalProgress);
                                if (videoInfo.traktResume < 0 && (result.status == Trakt.Status.SUCCESS || result.status == Trakt.Status.SUCCESS_ALREADY)) {
                                    // videoinf.traktresume can be negative, positive value means sync ok
                                    videoInfo.traktResume = Math.abs(videoInfo.traktResume);
                                    ContentValues values = new ContentValues();
                                    values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_RESUME, videoInfo.traktResume);
                                    getContentResolver().update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
                                            values, VideoStore.Video.VideoColumns._ID + " = " + videoInfo.id, null);

                                }
                            }
                            if (log.isDebugEnabled()) log.debug("postWatchingStop, result: {}", result.status);
                            mBusy = false;
                        }
                    } else if (action.equals(INTENT_ACTION_WATCHING_PAUSE)) {
                        final float progress = intent.getFloatExtra("progress", -1);
                        if (log.isDebugEnabled()) log.debug("postWatchingPause progress={}", progress);
                        if (videoInfo == null && videoID >= 0)
                            videoInfo = VideoDbInfo.fromId(getContentResolver(), videoID);
                        if (videoInfo != null) {
                            if (Trakt.shouldMarkAsSeen(progress)) {
                                traktAction = Trakt.ACTION_SEEN;
                                // get last activity before doing some activity on trakt
                                result = mTrakt.getLastActivity();
                                lastActivityFlag = getFlagsFromTraktLastActivity(result);
                            }
                            if (result == null || result.status != Trakt.Status.ERROR_NETWORK) {

                                final float finalProgress = progress >= 0 ? progress : -progress;
                                result = mTrakt.postWatchingPause(videoInfo, finalProgress);
                                if (videoInfo.traktResume < 0 && (result.status == Trakt.Status.SUCCESS || result.status == Trakt.Status.SUCCESS_ALREADY)) {
                                    //  videoinf.traktresume can be negative, positive value means sync ok
                                    videoInfo.traktResume = Math.abs(videoInfo.traktResume);
                                    ContentValues values = new ContentValues();
                                    values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_RESUME, videoInfo.traktResume);
                                    getContentResolver().update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
                                            values, VideoStore.Video.VideoColumns._ID + " = " + videoInfo.id, null);

                                }
                            }
                            if (log.isDebugEnabled()) log.debug("postWatchingPause, result: {}", result.status);
                            mBusy = false;
                        }
                    } else if (action.equals(INTENT_ACTION_MARK_AS)) {
                        // get last activity before doing some activity on trakt
                        traktAction = intent.getStringExtra("action");
                        if (log.isDebugEnabled()) log.debug("markAs: {}", traktAction);

                        if (traktAction != null && videoInfo != null) {
                            final boolean wasBusy = mBusy;
                            mBusy = true;
                            // TODO MARC no need of this
                            result = mTrakt.getLastActivity();

                            lastActivityFlag = getFlagsFromTraktLastActivity(result);
                            if (result != null && result.status != Trakt.Status.ERROR_NETWORK) {
                                result = mTrakt.markAs(traktAction, videoInfo);
                            } else

                                Trakt.setFlagSyncPreference(mPreferences, FLAG_SYNC_TO_TRAKT_WATCHED);
                            mBusy = wasBusy;
                        }
                    } else if (action.equals(INTENT_ACTION_SYNC)) {
                        int flag = intent.getIntExtra("flag_sync", 0);
                        result = sync(flag);
                        // trakt_last_sync and sync status are written by handleSyncStatus() inside sync()
                    }
                }
                else if (action.equals(INTENT_ACTION_FORCE_PUSH)) {
                    if (log.isDebugEnabled()) log.debug("INTENT_ACTION_FORCE_PUSH received");
                    mForcePush = true;
                    mForcePull = false;
                    sync(FLAG_SYNC_NOW);
                    mForcePush = false;
                }
                else if (action.equals(INTENT_ACTION_FORCE_PULL)) {
                    if (log.isDebugEnabled()) log.debug("INTENT_ACTION_FORCE_PULL received");
                    mForcePull = true;
                    mForcePush = false;
                    sync(FLAG_SYNC_NOW);
                    mForcePull = false;
                }
                // action that can be run with a null mTrakt
                if (action.equals(INTENT_ACTION_WIPE)) {
                    result = wipe();
                } else if (action.equals(INTENT_ACTION_WIPE_COLLECTION)) {
                    result = wipeCollection();
                }

                if (result == null)
                    result = Trakt.Result.getError();
                if (traktAction != null && videoInfo != null) {
                    final Status status = result.status;
                    if (log.isDebugEnabled()) log.debug("markAs: Trakt.Status: {}, scrapeStatus: {}", status, videoInfo.scrapeStatus);
                    if (status == Status.SUCCESS || status == Status.SUCCESS_ALREADY) {
                        if (lastActivityFlag == 0) {
                            // last activity is us
                            final long time = getCurrentTraktTime();
                            if (videoInfo.isShow)
                                Trakt.setLastTimeShowWatched(mPreferences, time);
                            else
                                Trakt.setLastTimeMovieWatched(mPreferences, time);
                        }
                        saveTraktStatus(videoInfo, traktAction);
                        sync(lastActivityFlag | FLAG_SYNC_AUTO | FLAG_SYNC_LAST_ACTIVITY_VETO /* don't need to check lastActivity again */);
                    } else if (status == Status.ERROR_NETWORK ||
                            videoInfo.scrapeStatus == null ||
                            videoInfo.scrapeStatus != ScrapeStatus.NOT_FOUND) {
                        //saveCache(videoInfo, traktAction);
                    }
                }
                if (notify && result.status == Status.SUCCESS && (System.currentTimeMillis() - intentTime) <= NOTIFY_DELAY) {
                    if (result.objType == Trakt.Result.ObjectType.RESPONSE && result.obj != null) {
                        TraktAPI.Response resp = (TraktAPI.Response) result.obj;
                        if (resp.message != null)
                            showToast(resp.message);
                    }
                }
                if (messenger != null) {
                    Message remoteMsg = Message.obtain();
                    remoteMsg.what = MSG_RESULT;
                    Bundle bundle = new Bundle();
                    bundle.putSerializable("status", result.status);
                    remoteMsg.obj = bundle;
                    try {
                        messenger.send(remoteMsg);
                    } catch (RemoteException e) {
                    }
                }
            } else if (msg.what == MSG_NETWORK_ON) {
                if (mTrakt != null) {
                    if (log.isDebugEnabled()) log.debug("MSG_NETWORK_ON: sync");
                    sync(FLAG_SYNC_AUTO);
                }
            }
            /*
            if (mTrakt != null) {
                if (needSendCache(TraktService.this)) {
                    //addListener(false);
                    addListener();
                } else {
                    removeListener();
                }
            }
             */
            if (mTrakt == null ||
                    (!mBusy && !mNetworkStateListenerAdded && !mWaitBeforeSync && !hasOtherMessagesPending(msg.what)))
                stopSelf();
        }
    }

    private static boolean needSendCache(Context context) {
        for (String action : Trakt.ACTIONS) {
            String xmlName = Trakt.getXmlName(action);
            File xmlFile = context.getFileStreamPath(xmlName);
            if (xmlFile != null && xmlFile.exists())
                return true;
        }
        if (Trakt.getFlagSyncPreference(PreferenceManager.getDefaultSharedPreferences(context)) != 0)
            return true;
        return false;
    }

    private void saveTraktStatus(VideoDbInfo videoInfo, String action) {
        final boolean markSeen = (action.equals(Trakt.ACTION_SEEN) || action.equals(Trakt.ACTION_UNSEEN));
        if (markSeen)
            videoInfo.traktSeen = action.equals(Trakt.ACTION_SEEN) ? 1 : 0;
        else
            videoInfo.traktLibrary = action.equals(Trakt.ACTION_LIBRARY) ? 1 : 0;

        if (videoInfo.id != -1) {
            if (log.isDebugEnabled()) log.debug("saveTraktStatus, id: {}", videoInfo.id);
            final String where = VideoStore.Video.VideoColumns._ID + " = " + videoInfo.id;
            ContentResolver resolver = getContentResolver();
            ContentValues values = new ContentValues(1);
            if (markSeen)
                values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN, videoInfo.traktSeen);
            else
                values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_LIBRARY, videoInfo.traktLibrary);
            resolver.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
                    values, where, null);
        }
    }

    private static final String WIPE_SELECTION = VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN + " = 1 OR " +
            VideoStore.Video.VideoColumns.ARCHOS_TRAKT_LIBRARY + " = 1";

    private static final String WIPE_COLLECTION_SELECTION = VideoStore.Video.VideoColumns.ARCHOS_TRAKT_LIBRARY + " = 1";

    private static final String MOVIE_ONLINE_ID_PROJECTION[] = new String[] {
            BaseColumns._ID,
            VideoStore.Video.VideoColumns.SCRAPER_M_ONLINE_ID,
            VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED
    };

    private static final String SHOW_ONLINE_ID_PROJECTION[] = new String[] {
            BaseColumns._ID,
            VideoStore.Video.VideoColumns.SCRAPER_S_ONLINE_ID,
            VideoStore.Video.VideoColumns.SCRAPER_E_SEASON,
            VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE,
            VideoStore.Video.VideoColumns.SCRAPER_E_ONLINE_ID,
            VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED
    };

    private static final String SYNC_PROGRESS_PROJECTION[] = new String[] {
            BaseColumns._ID,
    };

    private static String getVideoToMarkSelection(String library, int scraperType, boolean toMark) {
        if (library.equals(Trakt.LIBRARY_WATCHED)) {
            if (toMark)
                return "(" + VideoStore.Video.VideoColumns.BOOKMARK + " = -2 AND " +
                        VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE + " = " + scraperType + " AND " +
                        VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN + " = 0)";
            else
                return "(" + VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE + " = " + scraperType + " AND " +
                        VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN + " = " + Trakt.TRAKT_DB_UNMARK + ")";
        } else {
            if (toMark)
                return "(" + VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE + " = " + scraperType + " AND " +
                        VideoStore.Video.VideoColumns.ARCHOS_TRAKT_LIBRARY + " = 0)";
            else
                return "(" + VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE + " = " + scraperType + " AND " +
                        VideoStore.Video.VideoColumns.ARCHOS_TRAKT_LIBRARY + " = " + Trakt.TRAKT_DB_UNMARK + ")";
        }
    }

    private static class InBuilder {
        String mSelection = null;
        int mCount;

        public InBuilder(String inSelection) {
            mSelection = inSelection + " IN (";
            mCount = 0;
        }
        public void addParam(String arg) {
            if (mCount == 0)
                mSelection += arg;
            else
                mSelection += ", " + arg;
            mCount ++;
        }
        public void addParam(int arg) {
            addParam(String.valueOf(arg));
        }
        public String get() {
            if (mCount > 0) {
                mSelection += ")";
                return mSelection;
            } else {
                return null;
            }
        }
    }

    private Trakt.Result wipe() {
        removeListener();
        // wipe trakt* from db
        final ContentResolver cr = getContentResolver();
        ContentValues values = new ContentValues(2);
        values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN, 0);
        values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_LIBRARY, 0);
        cr.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, values, WIPE_SELECTION, null);
        return Trakt.Result.getSuccess();
    }

    private Trakt.Result wipeCollection() {
        final ContentResolver cr = getContentResolver();
        ContentValues values = new ContentValues(1);
        values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_LIBRARY, 0);
        cr.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, values, WIPE_COLLECTION_SELECTION, null);
        return Trakt.Result.getSuccess();
    }

    private static ContentValues getValuesMarkAs(String library, boolean mark) {
        ContentValues values;
        if (library.equals(Trakt.LIBRARY_WATCHED)) {
            values = new ContentValues(2);
            if (mark) {
                values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN, 1);
                values.put(VideoStore.Video.VideoColumns.BOOKMARK, -2);
            } else {
                values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN, 0);
                values.put(VideoStore.Video.VideoColumns.BOOKMARK, -1);
            }
        } else {
            values = new ContentValues(1);
            values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_LIBRARY, mark ? 1 : 0);
        }
        return values;
    }

    /**
     * When linking trakt for the first time, skip pushing the whole local history to trakt and
     * instead mark already-watched items as "synced" so future runs only send fresh events.
     */
    private int seedLocalWatchedAsSynced() {
        final ContentResolver cr = getContentResolver();
        final ContentValues values = new ContentValues(1);
        values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN, 1);
        final String watchedMovies = getVideoToMarkSelection(Trakt.LIBRARY_WATCHED, BaseTags.MOVIE, true);
        final String watchedShows = getVideoToMarkSelection(Trakt.LIBRARY_WATCHED, BaseTags.TV_SHOW, true);
        final String selection = watchedMovies + " OR " + watchedShows;
        final int updated = cr.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, values, selection, null);
        if (log.isDebugEnabled()) log.debug("seedLocalWatchedAsSynced: seeded {} watched videos as already synced", updated);
        return updated;
    }

    private Trakt.Status syncFlushEpisodeList(String library, TraktAPI.EpisodeListParam param,
                                              ArrayList<TraktAPI.Episode> episodeList, ContentResolver cr, String selection, boolean mark) {
        if (!episodeList.isEmpty()) {
            param.episodes = new TraktAPI.Episode[episodeList.size()];
            param.episodes = episodeList.toArray(param.episodes);
            ArrayList<SyncEpisode> eps = new ArrayList<SyncEpisode>();
            for (TraktAPI.Episode ep : param.episodes){
                SyncEpisode se = new SyncEpisode();
                EpisodeIds ids = new EpisodeIds();
                ids.tmdb = ep.tmdb ;
                se.id(ids);
                if (mark && library.equals(Trakt.LIBRARY_WATCHED) && ep.last_played != null) {
                    // Preserve actual watch date instead of "now" when backfilling
                    se.watchedAt(OffsetDateTime.parse(ep.last_played));
                }
                eps.add(se);
            }
            SyncItems si = new SyncItems();
            si.episodes(eps);
            final Trakt.Result result = mTrakt.markAs(Trakt.getAction(library, mark), si, true);
            if (result.status == Trakt.Status.ERROR_NETWORK)
                return Trakt.Status.ERROR_NETWORK;
            if (selection != null && (result.status == Trakt.Status.SUCCESS ||
                    result.status == Trakt.Status.SUCCESS_ALREADY)){
                cr.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
                        getValuesMarkAs(library, mark), selection, null);
            }
        }
        return Trakt.Status.SUCCESS;
    }

    /**
     * @deprecated This method is replaced by {@link #syncPlaybackStatusHybrid()}
     * which provides the same functionality with better architecture.
     * 
     * DEPRECATED: sync playback status i.e. resume time and last time played (not capturing fully watched videos) both ways (db/trakt)
     * This method is being replaced by syncPlaybackStatusHybrid() which uses separate 
     * syncResumePointsToTrakt() and syncWatchedStatusToDb() methods while preserving the original logic.
     * 
     * @see #syncPlaybackStatusHybrid()
     * TODO: Remove this method in future release once hybrid approach is fully tested
     */
    @Deprecated
    @SuppressWarnings("unused") // Kept for emergency fallback during transition period
    private Trakt.Status syncPlaybackStatusLegacy(){
        if (log.isDebugEnabled()) log.debug("syncPlaybackStatus start");
        final ContentResolver cr = getContentResolver();
        // get all playback status from trakt since last sync
        Trakt.Result resultTrakt = mTrakt.getPlaybackStatus();
        java.util.List<PlaybackResponse> videos = null;
        // MOVIES here is either MOVIE or EPISODE
        if (resultTrakt.status == Trakt.Status.SUCCESS &&
                resultTrakt.objType == Trakt.Result.ObjectType.MOVIES) {
            videos = (java.util.List<PlaybackResponse>) resultTrakt.obj;
        }
        if (log.isDebugEnabled()) log.debug("syncPlaybackStatus: processing batch of {}", ((videos !=null) ? videos.size() : "null"));
        //from db to trakt
        // get all videos watched on device not yet synced to trakt (traktResume < 0: negative traktResume means set but not yet synced)
        // filter videos that are scraped and that have been played and not synced yet
        // SELECT _data, Archos_lastTimePlayed, Archos_traktSeen, Archos_traktLibrary, Archos_traktResume from video WHERE ArchosMediaScraper_id > 0 AND Archos_lastTimePlayed > 0 AND Archos_traktResume < 0
        Cursor c1= cr.query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, VideoDbInfo.COLUMNS,
                VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID + " > 0 AND " + VideoStore.Video.VideoColumns.ARCHOS_TRAKT_RESUME + " < 0 AND " + VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED + " > 0",
                null, null);
        if (c1 != null) {
            if (c1.getCount() > 0) {
                c1.moveToFirst();
                do {
                    VideoDbInfo videoInfo = VideoDbInfo.fromCursor(c1, false);
                    if (videoInfo != null &&
                            (videoInfo.scraperMovieId != null || videoInfo.scraperEpisodeId != null) &&
                            videoInfo.traktResume < 0) {
                        boolean send = true;
                        GenericProgress gprog = null;
                        if (videos != null)
                            // check if trakt has a more recent progress than videoInfo.watched_at we are about to send , if this is the case, we don't send
                            for (PlaybackResponse video : videos) { // video is from trakt and videoInfo is from db
                                if ((video.movie != null
                                        && video.movie.ids != null
                                        && videoInfo.scraperMovieId != null
                                        && Objects.equals(video.movie.ids.tmdb, Integer.valueOf(videoInfo.scraperMovieId))
                                        && video.progress > -videoInfo.traktResume) || // negative traktResume means set but not yet synced
                                        (video.episode != null
                                                && video.episode.ids != null
                                                && videoInfo.scraperEpisodeId != null
                                                && Objects.equals(video.episode.ids.tmdb, Integer.valueOf(videoInfo.scraperEpisodeId))
                                                && video.progress > -videoInfo.traktResume)) {
                                    //trakt mark is more advanced, we don't send anything
                                    send = false;
                                    if (log.isDebugEnabled()) log.debug("syncPlaybackStatus: db->trakt {}{} not sent, trakt progress is more advanced", videoInfo.scraperTitle, videoInfo.isShow ? ", s" + videoInfo.scraperSeasonNr + "e" + videoInfo.scraperEpisodeNr : "");
                                    gprog = video;
                                    break;
                                }
                            }
                        if (send) {
                            if (log.isDebugEnabled()) log.debug("syncPlaybackStatus: db->trakt {}{}", videoInfo.scraperTitle, (videoInfo.isShow ? ", s" + videoInfo.scraperSeasonNr + "e" + videoInfo.scraperEpisodeNr : ""));
                            ContentValues values = new ContentValues();
                            Trakt.Result result;
                            if (Trakt.shouldMarkAsSeen(Math.abs(videoInfo.traktResume))) {
                                result = mTrakt.markAs(Trakt.ACTION_SEEN, videoInfo);
                                if (result.status == Trakt.Status.SUCCESS || result.status == Trakt.Status.SUCCESS_ALREADY) {
                                    videoInfo.traktSeen = 1;
                                }
                            }
                            else {
                                result = mTrakt.postWatchingStop(videoInfo, -videoInfo.traktResume);
                            }
                            if (result.status == Trakt.Status.SUCCESS || result.status == Trakt.Status.SUCCESS_ALREADY) {
                                if (gprog != null)
                                    gprog.progress = Double.valueOf(Math.abs(videoInfo.traktResume));
                                videoInfo.traktResume = Math.abs(videoInfo.traktResume);
                                values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_RESUME, videoInfo.traktResume);
                                values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN, videoInfo.traktSeen);
                                getContentResolver().update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
                                        values, VideoStore.Video.VideoColumns._ID + " = " + videoInfo.id, null);
                            }
                        }
                    }
                } while (c1.moveToNext());
            }
            c1.close();
        }

        // save sync time
        PreferenceManager.getDefaultSharedPreferences(TraktService.this).edit().putLong(PREFERENCE_TRAKT_LAST_TIME_SYNC_PROGRESS, System.currentTimeMillis() / 1000L).apply();

        //from trakt to db

        if (videos != null && !videos.isEmpty()) {
            String whereR;
            for (PlaybackResponse video : videos){
                if (video.movie != null) { // it is a movie
                    if (video.movie.ids == null || video.movie.ids.tmdb == null) continue;
                    whereR = VideoStore.Video.VideoColumns._ID+" IN ("+
                            "SELECT video_id FROM movie where " + VideoStore.Video.VideoColumns.SCRAPER_M_ONLINE_ID + "= " + video.movie.ids.tmdb+")";
                } else { // it is an episode
                    if (video.episode == null || video.episode.ids == null || video.episode.ids.tmdb == null) continue;
                    whereR = VideoStore.Video.VideoColumns._ID+" IN ("+
                            "SELECT video_id FROM episode where " + VideoStore.Video.VideoColumns.SCRAPER_E_ONLINE_ID + "= " + video.episode.ids.tmdb+")";
                }
                Cursor c = cr.query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, SYNC_PROGRESS_PROJECTION, whereR, null, null);
                if (c != null) {
                    if (c.getCount() > 0) {
                        final int idIdx = c.getColumnIndex(BaseColumns._ID);

                        while (c.moveToNext()) {
                            int id = c.getInt(idIdx);
                            VideoDbInfo i = VideoDbInfo.fromId(cr, id);
                            // i is the video from db and video is the video from trakt
                            if (i != null && video.progress != null) {
                                int newResumePercent = (int) Math.round(video.progress);
                                int newResume = (int) (video.progress/100.0*i.duration);
                                long lastWatched = 1; // 1st second of 1970 by default
                                OffsetDateTime lastWatchedOffsetDateTime = video.paused_at;
                                String lastPlayedDateString = "1";
                                if (lastWatchedOffsetDateTime != null) {
                                    lastWatched = lastWatchedOffsetDateTime.toEpochSecond();
                                    lastPlayedDateString = LocalDateTime.ofEpochSecond(lastWatched, 0, ZoneOffset.UTC).toString();
                                }
                                if (log.isDebugEnabled()) log.debug("syncPlaybackStatus: trakt->db {}{} db traktResume={}%, traktSeen={}, resume={}, lastTimePlayed={}; trakt resume={}%, resume {}, lastTimePlayed {}, lastWatched={}",
                                        i.scraperTitle, i.isShow ? "-s" + i.scraperSeasonNr + "e" + i.scraperEpisodeNr : "", i.traktResume, i.traktSeen, i.resume, i.lastTimePlayed, newResumePercent, newResume, lastWatched, lastPlayedDateString);
                                boolean toConsider = false;
                                ContentValues values = new ContentValues();
                                if (i.lastTimePlayed < lastWatched && newResumePercent > 0) {
                                    // trakt lastTimePlayed > db lastTimePlayed: in this case update archos last time played since trakt was the latest compared to db
                                    // exclude null newResumePercent since some other players use this to store library (e.g. infuse) and avoid pollution
                                    if (log.isDebugEnabled()) log.debug("syncPlaybackStatus: trakt->db update Archos last time played by trakt which is the latest {}", lastPlayedDateString);
                                    toConsider = true;
                                    values.put(VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED, lastWatched);
                                }
                                if (Math.abs(i.traktResume) != newResumePercent && // trakt resume % != db resume %
                                                i.traktSeen != 1 && // marked not watched on trakt (even if replayed)
                                                newResume > i.resume && // trakt resume time > db resume time
                                                i.resume != -2) { //not end of file (i.resume = -2 is file end)
                                    // trakt resume time is ahead of device one: only update device one in this case
                                    if (log.isDebugEnabled()) log.debug("syncPlaybackStatus: trakt->db trakt has the latest bookmark {}% for {}{}, use this one", newResumePercent, i.scraperTitle, i.isShow ? "-s" + i.scraperSeasonNr + "e" + i.scraperEpisodeNr : "");
                                    toConsider = true;
                                    values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_RESUME, newResumePercent);
                                    values.put(VideoStore.Video.VideoColumns.BOOKMARK, newResume);
                                    if (newResumePercent > Trakt.SCROBBLE_THRESHOLD) { // we are at end of file
                                        if (log.isDebugEnabled()) log.debug("syncPlaybackStatus: trakt->db trakt {}{} has been completed on trakt, mark it viewed", i.scraperTitle, i.isShow ? "-s" + i.scraperSeasonNr + "e" + i.scraperEpisodeNr : "");
                                        values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_RESUME, 99); // resume%
                                        values.put(VideoStore.Video.VideoColumns.BOOKMARK, -2); // file end
                                        values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN, 1); // align watched state immediately
                                    }
                                }
                                if (toConsider) {
                                    cr.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
                                            values, VideoStore.Video.VideoColumns._ID+" = '"+i.id+"'", null);
                                }
                            }
                        }
                    }
                    c.close();
                } else {
                    if (log.isDebugEnabled()) log.debug("syncPlaybackStatus: trakt->db cursor null!");
                }
            }
        }
        if (log.isDebugEnabled()) log.debug("syncPlaybackStatus complete");

        // save sync time
        long maxPaused = getMaxPausedAtSeconds(videos);
        if (maxPaused <= 0) maxPaused = System.currentTimeMillis() / 1000L;
        PreferenceManager.getDefaultSharedPreferences(TraktService.this)
                .edit()
                .putLong(PREFERENCE_TRAKT_LAST_TIME_SYNC_TO_DB_PROGRESS, maxPaused)
                .apply();

        return Trakt.Status.SUCCESS;
    }

    // HYBRID APPROACH: Combines original logic with new architecture
    // This method preserves the original's smart conflict resolution while using cleaner separation
    private Trakt.Status syncPlaybackStatusHybrid() {
        if (log.isDebugEnabled()) log.debug("syncPlaybackStatusHybrid start - combining original logic with new architecture");
        
        // 1. FETCH TRAKT DATA ONCE (like original) - single API call for efficiency
        Trakt.Result resultTraktProgress = mTrakt.getPlaybackStatus();
        java.util.List<PlaybackResponse> traktProgress = null;
        if (resultTraktProgress.status == Trakt.Status.SUCCESS &&
                resultTraktProgress.objType == Trakt.Result.ObjectType.MOVIES) {
            traktProgress = (java.util.List<PlaybackResponse>) resultTraktProgress.obj;
        }
        
        Trakt.Result resultTraktWatched = mTrakt.getWatchedStatus();
        java.util.List<HistoryEntry> traktWatched = null;
        if (resultTraktWatched.status == Trakt.Status.SUCCESS &&
                resultTraktWatched.objType == Trakt.Result.ObjectType.MOVIES) {
            traktWatched = (java.util.List<HistoryEntry>) resultTraktWatched.obj;
        }
        
        // 2. UPLOAD WITH ORIGINAL CONFLICT CHECKING (DB → Trakt)
        // This preserves the critical "don't overwrite newer progress" logic
        Trakt.Status uploadStatus = syncResumePointsToTrakt(traktProgress);
        if (uploadStatus == Trakt.Status.ERROR_NETWORK || uploadStatus == Trakt.Status.ERROR_ACCOUNT_LOCKED || uploadStatus == Trakt.Status.ERROR_AUTH) {
            return uploadStatus;
        }
        
        // Upload watched status (minimal for now since most logic is in resume points)
        uploadStatus = syncWatchedStatusToTrakt(traktWatched);
        if (uploadStatus == Trakt.Status.ERROR_NETWORK || uploadStatus == Trakt.Status.ERROR_ACCOUNT_LOCKED || uploadStatus == Trakt.Status.ERROR_AUTH) {
            return uploadStatus;
        }
        
        // 3. DOWNLOAD WITH NEW IMPROVED LOGIC (Trakt → DB)
        // This uses your enhanced conflict resolution and specification compliance
        Trakt.Status downloadStatus = syncResumePointsToDb(traktProgress);
        if (downloadStatus == Trakt.Status.ERROR_NETWORK || downloadStatus == Trakt.Status.ERROR_ACCOUNT_LOCKED || downloadStatus == Trakt.Status.ERROR_AUTH) {
            return downloadStatus;
        }

        downloadStatus = syncWatchedStatusToDb(traktWatched);
        if (downloadStatus == Trakt.Status.ERROR_NETWORK || downloadStatus == Trakt.Status.ERROR_ACCOUNT_LOCKED || downloadStatus == Trakt.Status.ERROR_AUTH) {
            return downloadStatus;
        }

        // save sync time based on newest paused_at we processed
        long maxPaused = getMaxPausedAtSeconds(traktProgress);
        if (maxPaused <= 0) maxPaused = System.currentTimeMillis() / 1000L;
        PreferenceManager.getDefaultSharedPreferences(TraktService.this)
                .edit()
                .putLong(PREFERENCE_TRAKT_LAST_TIME_SYNC_TO_DB_PROGRESS, maxPaused)
                .apply();

        if (log.isDebugEnabled()) log.debug("syncPlaybackStatusHybrid complete - preserved original logic with new architecture");
        return Trakt.Status.SUCCESS;
    }

    private long getMaxPausedAtSeconds(java.util.List<PlaybackResponse> videos) {
        long max = 0;
        if (videos != null) {
            for (PlaybackResponse v : videos) {
                if (v != null && v.paused_at != null) {
                    max = Math.max(max, v.paused_at.toEpochSecond());
                }
            }
        }
        return max;
    }

    // Upload resume points from DB to Trakt with original conflict checking logic
    private Trakt.Status syncResumePointsToTrakt(java.util.List<PlaybackResponse> traktVideos) {
        if (log.isDebugEnabled()) log.debug("syncResumePointsToTrakt start");
        final ContentResolver cr = getContentResolver();
        
        // Get all videos watched on device not yet synced to trakt (traktResume < 0: negative traktResume means set but not yet synced)
        // This is the EXACT same query from the original method
        Cursor c1= cr.query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, VideoDbInfo.COLUMNS,
                VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID + " > 0 AND " + 
                VideoStore.Video.VideoColumns.ARCHOS_TRAKT_RESUME + " < 0 AND " + 
                VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED + " > 0",
                null, null);
        
        if (c1 != null) {
            if (c1.getCount() > 0) {
                c1.moveToFirst();
                do {
                    VideoDbInfo videoInfo = VideoDbInfo.fromCursor(c1, false);
                    if (videoInfo != null &&
                            (videoInfo.scraperMovieId != null || videoInfo.scraperEpisodeId != null) &&
                            videoInfo.traktResume < 0) {
                        // Skip upload if progress is below the minimum threshold: max(30s, 1%).
                        // This guards against accidental short plays that slipped through PlayerService
                        // (e.g. no duration available at stop time).
                        final int pendingPercent = Math.abs(videoInfo.traktResume);
                        final float minPercent = videoInfo.duration > 0
                                ? Math.max(Trakt.RESUME_THRESHOLD_PERCENT, Trakt.RESUME_THRESHOLD_MS * 100.0f / videoInfo.duration)
                                : Trakt.RESUME_THRESHOLD_PERCENT;
                        if (pendingPercent < minPercent) {
                            if (log.isDebugEnabled()) log.debug("syncResumePointsToTrakt: db->trakt {}{} skipped, progress {}% below threshold {}% — clearing pending marker",
                                    videoInfo.scraperTitle, videoInfo.isShow ? ", s" + videoInfo.scraperSeasonNr + "e" + videoInfo.scraperEpisodeNr : "",
                                    pendingPercent, minPercent);
                            // Clear the pending marker so this row is not reconsidered on every sync.
                            ContentValues clearValues = new ContentValues();
                            clearValues.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_RESUME, 0);
                            cr.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, clearValues,
                                    VideoStore.Video.VideoColumns._ID + " = " + videoInfo.id, null);
                            continue;
                        }
                        boolean send = true;
                        GenericProgress gprog = null;

                        // PRESERVED ORIGINAL LOGIC: Check if Trakt has more recent progress than what we're about to send
                        if (traktVideos != null) {
                            for (PlaybackResponse video : traktVideos) { // video is from trakt and videoInfo is from db
                                if ((video.movie != null
                                        && video.movie.ids != null
                                        && videoInfo.scraperMovieId != null
                                        && Objects.equals(video.movie.ids.tmdb, Integer.valueOf(videoInfo.scraperMovieId))
                                        && video.progress > -videoInfo.traktResume) || // negative traktResume means set but not yet synced
                                        (video.episode != null
                                                && video.episode.ids != null
                                                && videoInfo.scraperEpisodeId != null
                                                && Objects.equals(video.episode.ids.tmdb, Integer.valueOf(videoInfo.scraperEpisodeId))
                                                && video.progress > -videoInfo.traktResume)) {
                                    //trakt mark is more advanced, we don't send anything
                                    send = false;
                                    if (log.isDebugEnabled()) log.debug("syncResumePointsToTrakt: db->trakt {}{} not sent, trakt progress is more advanced", videoInfo.scraperTitle, videoInfo.isShow ? ", s" + videoInfo.scraperSeasonNr + "e" + videoInfo.scraperEpisodeNr : "");
                                    gprog = video;
                                    break;
                                }
                            }
                        }
                        
                        if (send) {
                            if (log.isDebugEnabled()) log.debug("syncResumePointsToTrakt: db->trakt {}{}", videoInfo.scraperTitle, (videoInfo.isShow ? ", s" + videoInfo.scraperSeasonNr + "e" + videoInfo.scraperEpisodeNr : ""));
                            ContentValues values = new ContentValues();
                            Trakt.Result result;
                            
                            // PRESERVED ORIGINAL LOGIC: Check if should mark as seen or just update progress
                            if (Trakt.shouldMarkAsSeen(Math.abs(videoInfo.traktResume))) {
                                result = mTrakt.markAs(Trakt.ACTION_SEEN, videoInfo);
                                if (result.status == Trakt.Status.SUCCESS || result.status == Trakt.Status.SUCCESS_ALREADY) {
                                    videoInfo.traktSeen = 1;
                                }
                            } else {
                                result = mTrakt.postWatchingStop(videoInfo, -videoInfo.traktResume);
                            }
                            
                            // PRESERVED ORIGINAL LOGIC: Update local DB on successful upload
                            if (result.status == Trakt.Status.SUCCESS || result.status == Trakt.Status.SUCCESS_ALREADY) {
                                if (gprog != null)
                                    gprog.progress = Double.valueOf(Math.abs(videoInfo.traktResume));
                                videoInfo.traktResume = Math.abs(videoInfo.traktResume);
                                values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_RESUME, videoInfo.traktResume);
                                values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN, videoInfo.traktSeen);
                                cr.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
                                        values, VideoStore.Video.VideoColumns._ID + " = " + videoInfo.id, null);
                            } else if (result.status == Trakt.Status.ERROR_NETWORK || result.status == Trakt.Status.ERROR_ACCOUNT_LOCKED) {
                                c1.close();
                                return result.status;
                            }
                        }
                    }
                } while (c1.moveToNext());
            }
            c1.close();
        }

        // save sync time
        PreferenceManager.getDefaultSharedPreferences(TraktService.this).edit().putLong(PREFERENCE_TRAKT_LAST_TIME_SYNC_PROGRESS, System.currentTimeMillis() / 1000L).apply();
        if (log.isDebugEnabled()) log.debug("syncResumePointsToTrakt complete");
        return Trakt.Status.SUCCESS;
    }

    // Upload watched status from DB to Trakt (placeholder for future implementation)
    private Trakt.Status syncWatchedStatusToTrakt(java.util.List<HistoryEntry> traktWatched) {
        if (log.isDebugEnabled()) log.debug("syncWatchedStatusToTrakt start");
        // TODO: Implement watched status upload logic if needed
        // Currently, watched status is primarily uploaded via syncResumePointsToTrakt when progress > scrobble threshold
        if (log.isDebugEnabled()) log.debug("syncWatchedStatusToTrakt complete");
        return Trakt.Status.SUCCESS;
    }

    // Overloaded method: sync watched status using pre-fetched data (for hybrid approach)
    private Trakt.Status syncWatchedStatusToDb(java.util.List<HistoryEntry> traktWatched) {
        if (log.isDebugEnabled()) log.debug("syncWatchedStatusToDb start (using pre-fetched data)");
        final ContentResolver cr = getContentResolver();
        
        if (traktWatched != null && !traktWatched.isEmpty()) {
            String whereR;
            for (HistoryEntry video : traktWatched){
                if (video.movie != null) { // it is a movie
                    if (video.movie.ids == null || video.movie.ids.tmdb == null) continue;
                    whereR = VideoStore.Video.VideoColumns._ID+" IN ("+
                            "SELECT video_id FROM movie where " + VideoStore.Video.VideoColumns.SCRAPER_M_ONLINE_ID + "= " + video.movie.ids.tmdb+")";
                } else { // it is an episode
                    if (video.episode == null || video.episode.ids == null || video.episode.ids.tmdb == null) continue;
                    whereR = VideoStore.Video.VideoColumns._ID+" IN ("+
                            "SELECT video_id FROM episode where " + VideoStore.Video.VideoColumns.SCRAPER_E_ONLINE_ID + "= " + video.episode.ids.tmdb+")";
                }
                Cursor c = cr.query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, SYNC_PROGRESS_PROJECTION, whereR, null, null);
                if (c != null) {
                    if (c.getCount() > 0) {
                        final int idIdx = c.getColumnIndex(BaseColumns._ID);
                        while (c.moveToNext()) {
                            int id = c.getInt(idIdx);
                            VideoDbInfo i = VideoDbInfo.fromId(cr, id);
                            // i is the video from db and video is the video from trakt
                            if (i != null) {
                                long lastWatched = 1; // 1st second of 1970 by default
                                OffsetDateTime lastWatchedOffsetDateTime = video.watched_at;
                                String lastPlayedDateString = "1";
                                if (lastWatchedOffsetDateTime != null) {
                                    lastWatched = lastWatchedOffsetDateTime.toEpochSecond();
                                    lastPlayedDateString = LocalDateTime.ofEpochSecond(lastWatched, 0, ZoneOffset.UTC).toString();
                                }
                                if (log.isDebugEnabled()) log.debug("syncWatchedStatusToDb: trakt->db {}{} db traktResume= {}%, traktSeen={}, resume={}, lastTimePlayed={}; trakt lastTimePlayed={}, lastWatched={}",
                                        i.scraperTitle, i.isShow ? "-s" + i.scraperSeasonNr + "e" + i.scraperEpisodeNr : "", i.traktResume, i.traktSeen, i.resume, i.lastTimePlayed, lastWatched, lastPlayedDateString);
                                // Conflict resolution: handle multi-device scenarios per specification
                                ContentValues values = new ContentValues();
                                if (i.lastTimePlayed < lastWatched) {
                                    // Video was completed on another device more recently
                                    // This implements Rule 3: Videos marked as watched on any device must disappear from "Recently Played" on all devices
                                    if (log.isDebugEnabled()) log.debug("syncWatchedStatusToDb: trakt->db {} completed on another device, updating local state to hide from Recently Played", 
                                              i.scraperTitle + (i.isShow ? "-s" + i.scraperSeasonNr + "e" + i.scraperEpisodeNr : ""));
                                    values.put(VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED, lastWatched);
                                    values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN, 1); 
                                    values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_RESUME, 99); 
                                    values.put(VideoStore.Video.VideoColumns.BOOKMARK, -2); // This will hide from "Recently Played"
                                } else {
                                    // Device was played more recently, just mark as seen on Trakt to prevent re-sync
                                    // Keep existing resume and timestamp - don't override newer local data
                                    if (log.isDebugEnabled()) log.debug("syncWatchedStatusToDb: trakt->db {} marked as watched on Trakt but played more recently on device, keeping device state", 
                                              i.scraperTitle + (i.isShow ? "-s" + i.scraperSeasonNr + "e" + i.scraperEpisodeNr : ""));
                                    values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN, 1); // Mark as seen to prevent re-sync
                                    // Don't change ARCHOS_LAST_TIME_PLAYED, BOOKMARK, or ARCHOS_TRAKT_RESUME
                                }
                                cr.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, values, VideoStore.Video.VideoColumns._ID + " = '" + i.id + "'", null);
                            }
                        }
                    }
                    c.close();
                } else {
                    if (log.isDebugEnabled()) log.debug("syncWatchedStatusToDb: trakt->db cursor null!");
                }
            }
        }
        if (log.isDebugEnabled()) log.debug("syncWatchedStatusToDb complete (pre-fetched)");
        
        // Save sync time to prevent re-downloading same data
        PreferenceManager.getDefaultSharedPreferences(TraktService.this).edit().putLong(PREFERENCE_TRAKT_LAST_TIME_SYNC_TO_DB_WATCHED, System.currentTimeMillis() / 1000L).apply();
        
        return Trakt.Status.SUCCESS;
    }

    // Original method: sync watched status by fetching data first (kept for backward compatibility)
    private Trakt.Status syncWatchedStatusToDb(){
        if (log.isDebugEnabled()) log.debug("syncWatchedStatusToDb start");
        final ContentResolver cr = getContentResolver();
        // get all playback status from trakt since last sync
        Trakt.Result resultTrakt = mTrakt.getWatchedStatus();
        java.util.List<HistoryEntry> videos = null;
        // MOVIES here is either MOVIE or EPISODE
        if (resultTrakt.status == Trakt.Status.SUCCESS &&
                resultTrakt.objType == Trakt.Result.ObjectType.MOVIES) {
            videos = (java.util.List<HistoryEntry>) resultTrakt.obj;
        }
        if (log.isDebugEnabled()) log.debug("syncWatchedStatusToDb: processing batch of " + ((videos !=null) ? videos.size() : "null"));
        //from trakt to db
        if (videos != null && !videos.isEmpty()) {
            String whereR;
            for (HistoryEntry video : videos){
                if (video.movie != null) { // it is a movie
                    if (video.movie.ids == null || video.movie.ids.tmdb == null) continue;
                    whereR = VideoStore.Video.VideoColumns._ID+" IN ("+
                            "SELECT video_id FROM movie where " + VideoStore.Video.VideoColumns.SCRAPER_M_ONLINE_ID + "= " + video.movie.ids.tmdb+")";
                } else { // it is an episode
                    if (video.episode == null || video.episode.ids == null || video.episode.ids.tmdb == null) continue;
                    whereR = VideoStore.Video.VideoColumns._ID+" IN ("+
                            "SELECT video_id FROM episode where " + VideoStore.Video.VideoColumns.SCRAPER_E_ONLINE_ID + "= " + video.episode.ids.tmdb+")";
                }
                Cursor c = cr.query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, SYNC_PROGRESS_PROJECTION, whereR, null, null);
                if (c != null) {
                    if (c.getCount() > 0) {
                        final int idIdx = c.getColumnIndex(BaseColumns._ID);
                        while (c.moveToNext()) {
                            int id = c.getInt(idIdx);
                            VideoDbInfo i = VideoDbInfo.fromId(cr, id);
                            // i is the video from db and video is the video from trakt
                            if (i != null) {
                                long lastWatched = 1; // 1st second of 1970 by default
                                OffsetDateTime lastWatchedOffsetDateTime = video.watched_at;
                                String lastPlayedDateString = "1";
                                if (lastWatchedOffsetDateTime != null) {
                                    lastWatched = lastWatchedOffsetDateTime.toEpochSecond();
                                    lastPlayedDateString = LocalDateTime.ofEpochSecond(lastWatched, 0, ZoneOffset.UTC).toString();
                                }
                                if (log.isDebugEnabled()) log.debug("syncWatchedStatusToDb: trakt->db {}{} db traktResume= {}%, traktSeen={}, resume={}, lastTimePlayed={}; trakt lastTimePlayed={}, lastWatched={}",
                                        i.scraperTitle, i.isShow ? "-s" + i.scraperSeasonNr + "e" + i.scraperEpisodeNr : "", i.traktResume, i.traktSeen, i.resume, i.lastTimePlayed, lastWatched, lastPlayedDateString);
                                // Conflict resolution: handle multi-device scenarios per specification
                                ContentValues values = new ContentValues();
                                if (i.lastTimePlayed < lastWatched) {
                                    // Video was completed on another device more recently
                                    // This implements Rule 3: Videos marked as watched on any device must disappear from "Recently Played" on all devices
                                    if (log.isDebugEnabled()) log.debug("syncWatchedStatusToDb: trakt->db {} completed on another device, updating local state to hide from Recently Played", 
                                              i.scraperTitle + (i.isShow ? "-s" + i.scraperSeasonNr + "e" + i.scraperEpisodeNr : ""));
                                    values.put(VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED, lastWatched);
                                    values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN, 1); 
                                    values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_RESUME, 99); 
                                    values.put(VideoStore.Video.VideoColumns.BOOKMARK, -2); // This will hide from "Recently Played"
                                } else {
                                    // Device was played more recently, just mark as seen on Trakt to prevent re-sync
                                    // Keep existing resume and timestamp - don't override newer local data
                                    if (log.isDebugEnabled()) log.debug("syncWatchedStatusToDb: trakt->db {} marked as watched on Trakt but played more recently on device, keeping device state", 
                                              i.scraperTitle + (i.isShow ? "-s" + i.scraperSeasonNr + "e" + i.scraperEpisodeNr : ""));
                                    values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN, 1); // Mark as seen to prevent re-sync
                                    // Don't change ARCHOS_LAST_TIME_PLAYED, BOOKMARK, or ARCHOS_TRAKT_RESUME
                                }
                                cr.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, values, VideoStore.Video.VideoColumns._ID + " = '" + i.id + "'", null);
                            }
                        }
                    }
                    c.close();
                } else {
                    if (log.isDebugEnabled()) log.debug("syncWatchedStatusToDb: trakt->db cursor null!");
                }
            }
        }
        if (log.isDebugEnabled()) log.debug("syncWatchedStatusToDb complete");
        
        // Save sync time to prevent re-downloading same data
        PreferenceManager.getDefaultSharedPreferences(TraktService.this).edit().putLong(PREFERENCE_TRAKT_LAST_TIME_SYNC_TO_DB_WATCHED, System.currentTimeMillis() / 1000L).apply();
        
        return Trakt.Status.SUCCESS;
    }

    // Overloaded method: sync resume points using pre-fetched data (for hybrid approach)
    private Trakt.Status syncResumePointsToDb(java.util.List<PlaybackResponse> traktVideos) {
        if (log.isDebugEnabled()) log.debug("syncResumePointsToDb start (using pre-fetched data)");
        final ContentResolver cr = getContentResolver();
        
        if (traktVideos != null && !traktVideos.isEmpty()) {
            String whereR;
            for (PlaybackResponse video : traktVideos){
                if (video.movie != null) { // it is a movie
                    if (video.movie.ids == null || video.movie.ids.tmdb == null) continue;
                    whereR = VideoStore.Video.VideoColumns._ID+" IN ("+
                            "SELECT video_id FROM movie where " + VideoStore.Video.VideoColumns.SCRAPER_M_ONLINE_ID + "= " + video.movie.ids.tmdb+")";
                } else { // it is an episode
                    if (video.episode == null || video.episode.ids == null || video.episode.ids.tmdb == null) continue;
                    whereR = VideoStore.Video.VideoColumns._ID+" IN ("+
                            "SELECT video_id FROM episode where " + VideoStore.Video.VideoColumns.SCRAPER_E_ONLINE_ID + "= " + video.episode.ids.tmdb+")";
                }
                Cursor c = cr.query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, SYNC_PROGRESS_PROJECTION, whereR, null, null);
                if (c != null) {
                    if (c.getCount() > 0) {
                        final int idIdx = c.getColumnIndex(BaseColumns._ID);
                        while (c.moveToNext()) {
                            int id = c.getInt(idIdx);
                            VideoDbInfo i = VideoDbInfo.fromId(cr, id);
                            // i is the video from db and video is the video from trakt
                            if (i != null && video.progress != null) {
                                int newResumePercent = (int) Math.round(video.progress);
                                int newResume = (int) (video.progress/100.0*i.duration);
                                long lastWatched = 1; // 1st second of 1970 by default
                                OffsetDateTime lastWatchedOffsetDateTime = video.paused_at;
                                String lastPlayedDateString = "1";
                                if (lastWatchedOffsetDateTime != null) {
                                    lastWatched = lastWatchedOffsetDateTime.toEpochSecond();
                                    lastPlayedDateString = LocalDateTime.ofEpochSecond(lastWatched, 0, ZoneOffset.UTC).toString();
                                }
                                if (log.isDebugEnabled()) log.debug("syncResumePointsToDb: trakt->db {}{} db traktResume={}%, traktSeen={}, resume={}, lastTimePlayed={}; trakt resume={}%, resume {}, lastTimePlayed {}, lastWatched={}",
                                        i.scraperTitle, i.isShow ? "-s" + i.scraperSeasonNr + "e" + i.scraperEpisodeNr : "", i.traktResume, i.traktSeen, i.resume, i.lastTimePlayed, newResumePercent, newResume, lastWatched, lastPlayedDateString);
                                if (i.duration <= 0 && newResumePercent > 0) {
                                    // Duration is unknown: we cannot compute a valid local bookmark from the Trakt percentage.
                                    // Skip entirely to avoid writing BOOKMARK=0 or updating ARCHOS_LAST_TIME_PLAYED, which would
                                    // cause the item to appear in Recently Played at position 0ms.
                                    // A later sync after the file is scanned will convert the Trakt percentage correctly.
                                    log.info("syncResumePointsToDb: trakt->db skipping {}{} because local duration is unknown ({}ms), will retry after file is scanned", i.scraperTitle, i.isShow ? "-s" + i.scraperSeasonNr + "e" + i.scraperEpisodeNr : "", i.duration);
                                    mSyncSkippedNoDuration++;
                                    continue;
                                }
                                boolean toConsider = false;
                                ContentValues values = new ContentValues();
                                if (i.lastTimePlayed < lastWatched && newResumePercent > 0) {
                                    // Trakt has more recent progress update - this implements Rule 2: Cross-Device Resume Point Consistency
                                    // Update ARCHOS_LAST_TIME_PLAYED so video appears in "Recently Played" with correct timestamp order
                                    if (log.isDebugEnabled()) log.debug("syncResumePointsToDb: trakt->db updating timestamp to most recent playback time {}", lastPlayedDateString);
                                    toConsider = true;
                                    values.put(VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED, lastWatched);
                                }
                                if (Math.abs(i.traktResume) != newResumePercent && // trakt resume % != db resume %
                                        i.traktSeen != 1 && // marked not watched on trakt (even if replayed)
                                        newResume > i.resume && // trakt resume time > db resume time
                                        i.resume != -2) { //not end of file (i.resume = -2 is file end)
                                    // trakt resume time is ahead of device one: only update device one in this case
                                    if (log.isDebugEnabled()) log.debug("syncResumePointsToDb: trakt->db trakt has the latest bookmark {}% for {}{}, use this one", newResumePercent, i.scraperTitle, i.isShow ? "-s" + i.scraperSeasonNr + "e" + i.scraperEpisodeNr : "");
                                    toConsider = true;
                                    values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_RESUME, newResumePercent);
                                    values.put(VideoStore.Video.VideoColumns.BOOKMARK, newResume);
                                    if (newResumePercent > Trakt.SCROBBLE_THRESHOLD) { // we are at end of file
                                        if (log.isDebugEnabled()) log.debug("syncResumePointsToDb: trakt->db trakt {}{} has been completed on trakt, mark it viewed and hide from Recently Played", i.scraperTitle, i.isShow ? "-s" + i.scraperSeasonNr + "e" + i.scraperEpisodeNr : "");
                                        values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_RESUME, 99); // resume%
                                        values.put(VideoStore.Video.VideoColumns.BOOKMARK, -2); // file end - this hides from Recently Played
                                        values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN, 1); // mark as seen to match watched status
                                    }
                                }
                                if (toConsider) {
                                    cr.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
                                            values, VideoStore.Video.VideoColumns._ID+" = '"+i.id+"'", null);
                                }
                            }
                        }
                    }
                    c.close();
                } else {
                    if (log.isDebugEnabled()) log.debug("syncResumePointsToDb: trakt->db cursor null!");
                }
            }
        }
        if (log.isDebugEnabled()) log.debug("syncResumePointsToDb complete (pre-fetched)");
        
        // save sync time
        long maxPaused = getMaxPausedAtSeconds(traktVideos);
        if (maxPaused <= 0) maxPaused = System.currentTimeMillis() / 1000L;
        PreferenceManager.getDefaultSharedPreferences(TraktService.this)
                .edit()
                .putLong(PREFERENCE_TRAKT_LAST_TIME_SYNC_TO_DB_PROGRESS, maxPaused)
                .apply();
        
        return Trakt.Status.SUCCESS;
    }

    // Original method: sync resume points by fetching data first (kept for backward compatibility)
    private Trakt.Status syncResumePointsToDb(){
        if (log.isDebugEnabled()) log.debug("syncResumePointsToDb start");
        final ContentResolver cr = getContentResolver();
        // get all playback status from trakt since last sync
        Trakt.Result resultTrakt = mTrakt.getPlaybackStatus();
        java.util.List<PlaybackResponse> videos = null;
        // MOVIES here is either MOVIE or EPISODE
        if (resultTrakt.status == Trakt.Status.SUCCESS &&
                resultTrakt.objType == Trakt.Result.ObjectType.MOVIES) {
            videos = (java.util.List<PlaybackResponse>) resultTrakt.obj;
        }
        if (log.isDebugEnabled()) log.debug("syncResumePointsToDb: processing batch of " + ((videos !=null) ? videos.size() : "null"));
        if (videos != null && !videos.isEmpty()) {
            String whereR;
            for (PlaybackResponse video : videos){
                if (video.movie != null) { // it is a movie
                    if (video.movie.ids == null || video.movie.ids.tmdb == null) continue;
                    whereR = VideoStore.Video.VideoColumns._ID+" IN ("+
                            "SELECT video_id FROM movie where " + VideoStore.Video.VideoColumns.SCRAPER_M_ONLINE_ID + "= " + video.movie.ids.tmdb+")";
                } else { // it is an episode
                    if (video.episode == null || video.episode.ids == null || video.episode.ids.tmdb == null) continue;
                    whereR = VideoStore.Video.VideoColumns._ID+" IN ("+
                            "SELECT video_id FROM episode where " + VideoStore.Video.VideoColumns.SCRAPER_E_ONLINE_ID + "= " + video.episode.ids.tmdb+")";
                }
                Cursor c = cr.query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, SYNC_PROGRESS_PROJECTION, whereR, null, null);
                if (c != null) {
                    if (c.getCount() > 0) {
                        final int idIdx = c.getColumnIndex(BaseColumns._ID);
                        while (c.moveToNext()) {
                            int id = c.getInt(idIdx);
                            VideoDbInfo i = VideoDbInfo.fromId(cr, id);
                            // i is the video from db and video is the video from trakt
                            if (i != null && video.progress != null) {
                                int newResumePercent = (int) Math.round(video.progress);
                                int newResume = (int) (video.progress/100.0*i.duration);
                                long lastWatched = 1; // 1st second of 1970 by default
                                OffsetDateTime lastWatchedOffsetDateTime = video.paused_at;
                                String lastPlayedDateString = "1";
                                if (lastWatchedOffsetDateTime != null) {
                                    lastWatched = lastWatchedOffsetDateTime.toEpochSecond();
                                    lastPlayedDateString = LocalDateTime.ofEpochSecond(lastWatched, 0, ZoneOffset.UTC).toString();
                                }
                                if (log.isDebugEnabled()) log.debug("syncResumePointsToDb: trakt->db {}{} db traktResume={}%, traktSeen={}, resume={}, lastTimePlayed={}; trakt resume={}%, resume {}, lastTimePlayed {}, lastWatched={}",
                                        i.scraperTitle, i.isShow ? "-s" + i.scraperSeasonNr + "e" + i.scraperEpisodeNr : "", i.traktResume, i.traktSeen, i.resume, i.lastTimePlayed, newResumePercent, newResume, lastWatched, lastPlayedDateString);
                                if (i.duration <= 0 && newResumePercent > 0) {
                                    // Duration is unknown: we cannot compute a valid local bookmark from the Trakt percentage.
                                    // Skip entirely to avoid writing BOOKMARK=0 or updating ARCHOS_LAST_TIME_PLAYED, which would
                                    // cause the item to appear in Recently Played at position 0ms.
                                    // A later sync after the file is scanned will convert the Trakt percentage correctly.
                                    log.info("syncResumePointsToDb: trakt->db skipping {}{} because local duration is unknown ({}ms), will retry after file is scanned", i.scraperTitle, i.isShow ? "-s" + i.scraperSeasonNr + "e" + i.scraperEpisodeNr : "", i.duration);
                                    mSyncSkippedNoDuration++;
                                    continue;
                                }
                                boolean toConsider = false;
                                ContentValues values = new ContentValues();
                                if (i.lastTimePlayed < lastWatched && newResumePercent > 0) {
                                    // Trakt has more recent progress update - this implements Rule 2: Cross-Device Resume Point Consistency
                                    // Update ARCHOS_LAST_TIME_PLAYED so video appears in "Recently Played" with correct timestamp order
                                    if (log.isDebugEnabled()) log.debug("syncResumePointsToDb: trakt->db updating timestamp to most recent playback time {}", lastPlayedDateString);
                                    toConsider = true;
                                    values.put(VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED, lastWatched);
                                }
                                if (Math.abs(i.traktResume) != newResumePercent && // trakt resume % != db resume %
                                        i.traktSeen != 1 && // marked not watched on trakt (even if replayed)
                                        newResume > i.resume && // trakt resume time > db resume time
                                        i.resume != -2) { //not end of file (i.resume = -2 is file end)
                                    // trakt resume time is ahead of device one: only update device one in this case
                                    if (log.isDebugEnabled()) log.debug("syncResumePointsToDb: trakt->db trakt has the latest bookmark {}% for {}{}, use this one", newResumePercent, i.scraperTitle, i.isShow ? "-s" + i.scraperSeasonNr + "e" + i.scraperEpisodeNr : "");
                                    toConsider = true;
                                    values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_RESUME, newResumePercent);
                                    values.put(VideoStore.Video.VideoColumns.BOOKMARK, newResume);
                                    if (newResumePercent > Trakt.SCROBBLE_THRESHOLD) { // we are at end of file
                                        if (log.isDebugEnabled()) log.debug("syncResumePointsToDb: trakt->db trakt {}{} has been completed on trakt, mark it viewed and hide from Recently Played", i.scraperTitle, i.isShow ? "-s" + i.scraperSeasonNr + "e" + i.scraperEpisodeNr : "");
                                        values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_RESUME, 99); // resume%
                                        values.put(VideoStore.Video.VideoColumns.BOOKMARK, -2); // file end - this hides from Recently Played
                                        values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN, 1); // mark as seen to match watched status
                                    }
                                }
                                if (toConsider) {
                                    cr.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
                                            values, VideoStore.Video.VideoColumns._ID+" = '"+i.id+"'", null);
                                }
                            }
                        }
                    }
                    c.close();
                } else {
                    if (log.isDebugEnabled()) log.debug("syncResumePointsToDb: trakt->db cursor null!");
                }
            }
        }
        if (log.isDebugEnabled()) log.debug("syncResumePointsToDb complete");
        return Trakt.Status.SUCCESS;
    }

    private Trakt.Status syncMoviesToDb(String library) {
        if (log.isDebugEnabled()) log.debug("syncMoviesToDb: library={}", library);
        final ContentResolver cr = getContentResolver();

        Trakt.Result result = mTrakt.getAllMovies(library, true);
        if (result.status == Trakt.Status.ERROR_NETWORK)
            return Trakt.Status.ERROR_NETWORK;
        if (result.status == Trakt.Status.SUCCESS &&
                result.objType == Trakt.Result.ObjectType.MOVIES) {
            java.util.List<BaseMovie> movies = (java.util.List<BaseMovie>) result.obj;
            if (log.isDebugEnabled()) log.debug("syncMoviesToDb: found {} movies to sync", movies.size());
            if (movies != null && !movies.isEmpty()) {
                InBuilder inBuilder = new InBuilder(VideoStore.Video.VideoColumns.SCRAPER_M_ONLINE_ID);
                for (BaseMovie movie : movies){
                    if (movie != null && movie.movie != null && movie.movie.ids != null && movie.movie.ids.tmdb != null) {
                        inBuilder.addParam(movie.movie.ids.tmdb);
                        if (log.isTraceEnabled()) log.trace("syncMoviesToDb: marking {}", movie.movie.title);
                    } else {
                        if (log.isDebugEnabled()) log.debug("syncMoviesToDb: skipping movie with null entry or tmdb id");
                    }
                }
                final String inSelection = inBuilder.get();
                if (inSelection != null) {
                    final String selection = "_id IN ("+
                            "SELECT video_id FROM movie WHERE " + inSelection + ")";
                    cr.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
                            getValuesMarkAs(library, true), selection, null);
                }
            }
        }
        return Trakt.Status.SUCCESS;
    }

    private Trakt.Status syncShowsToDb(String library) {
        if (log.isDebugEnabled()) log.debug("syncShowsToDb: library={}", library);
        final ContentResolver cr = getContentResolver();
        Trakt.Result result = mTrakt.getAllShows(library);
        if (result.status == Trakt.Status.ERROR_NETWORK)
            return Trakt.Status.ERROR_NETWORK;
        if (result.status == Trakt.Status.SUCCESS &&
                result.objType == Trakt.Result.ObjectType.SHOWS_PER_SEASON) {
            java.util.List<BaseShow> shows = (java.util.List<BaseShow> ) result.obj;
            if (log.isDebugEnabled()) log.debug("syncShowsToDb: found {} shows to sync", shows.size());
            if (!shows.isEmpty()) {
                for (BaseShow show : shows) {
                    if (show.show == null || show.show.ids == null || show.show.ids.tmdb == null) {
                        if (log.isDebugEnabled()) log.debug("syncShowsToDb: skipping show with null tmdb id");
                        continue;
                    }
                    if (show.seasons == null) continue;
                    for (BaseSeason season : show.seasons) {
                        InBuilder inBuilder = new InBuilder("number_episode");
                        if (season.episodes == null) continue;
                        for (BaseEpisode episode : season.episodes) {
                            if (show.show.title != null && season.number != null && episode.number != null) {
                                if (log.isTraceEnabled()) log.trace("syncShowsToDb: marking {} s{}e{}", show.show.title, season.number, episode.number);
                                inBuilder.addParam(episode.number);
                            }
                        }
                        final String inSelection = inBuilder.get();
                        if (inSelection != null && season.number != null) {
                            final String selection = "_id IN ("+
                                    "SELECT video_id FROM episode where season_episode = " + season.number +
                                    " AND " + inSelection +
                                    " AND show_episode IN (SELECT _id FROM show WHERE s_online_id = " + show.show.ids.tmdb +"))";
                            cr.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, getValuesMarkAs(library, true), selection, null);
                        }
                    }
                }
            }
        }
        return Trakt.Status.SUCCESS;
    }

    private Trakt.Status syncMoviesToTrakt(String library, boolean toMark) {
        final ContentResolver cr = getContentResolver();
        final String action = Trakt.getAction(library, toMark);
        Trakt.resetLastPushedWatchedTime();

        Cursor c = cr.query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
                MOVIE_ONLINE_ID_PROJECTION,
                getVideoToMarkSelection(library, com.archos.mediascraper.BaseTags.MOVIE, toMark)
                        + " AND " + VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED + " > ?",
                new String[]{String.valueOf(Trakt.getLastTimeWatchedSync(mPreferences) + 1)},
                null);
                if (c != null) {
                    if (c.getCount() > 0) {
                        final int mOnlineIdIdx = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_M_ONLINE_ID);
                        final int lastPlayedIdx = c.getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED);
                        final int idIdx = c.getColumnIndex(BaseColumns._ID);

                TraktAPI.MovieListParam param = new TraktAPI.MovieListParam();
                ArrayList<TraktAPI.Movie> movieList = new ArrayList<TraktAPI.Movie>();

                InBuilder inBuilder = new InBuilder(VideoStore.Video.VideoColumns._ID);

                while (c.moveToNext()) {
                    final String mOnlineId = c.getString(mOnlineIdIdx);
                    final long lastTimePlayed = c.getLong(lastPlayedIdx);
                    final int id = c.getInt(idIdx);
                    if (mOnlineId != null && lastTimePlayed > 0) {
                        TraktAPI.Movie movie = new TraktAPI.Movie();
                        movie.tmdb_id = mOnlineId;
                        movie.last_played = Trakt.getDateFormat(lastTimePlayed);
                        movieList.add(movie);
                        inBuilder.addParam(id);
                        Trakt.updateLastPushedWatchedTime(lastTimePlayed);
                    }
                }
                if (!movieList.isEmpty()) {
                    param.movies = new TraktAPI.Movie[movieList.size()];
                    param.movies = movieList.toArray(param.movies);
                    ArrayList<SyncMovie> eps = new ArrayList<SyncMovie>();
                    for (TraktAPI.Movie m : param.movies){
                        SyncMovie se = new SyncMovie();
                        MovieIds ids = new MovieIds();
                        ids.tmdb =  Integer.valueOf(m.tmdb_id);
                        se.id(ids);
                        if (toMark && library.equals(Trakt.LIBRARY_WATCHED) && m.last_played != null) {
                            // Preserve original watch date instead of "today"
                            se.watchedAt(OffsetDateTime.parse(m.last_played));
                        }
                        eps.add(se);
                    }
                    SyncItems si = new SyncItems();
                    si.movies(eps);

                    Trakt.Result result = mTrakt.markAs(action,si, false);
                    if (result.status == Trakt.Status.ERROR_NETWORK) {
                        c.close();
                        return Trakt.Status.ERROR_NETWORK;
                    }
                    if (result.status == Trakt.Status.SUCCESS ||
                            result.status == Trakt.Status.SUCCESS_ALREADY) {
                        String selection = inBuilder.get();
                        if (selection != null)
                            cr.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
                                    getValuesMarkAs(library, toMark), selection, null);
                    }
                }
            }
            c.close();
        }
        return Trakt.Status.SUCCESS;
    }

    private Trakt.Status syncShowsToTrakt(String library, boolean toMark) {
        final ContentResolver cr = getContentResolver();
        Trakt.resetLastPushedWatchedTime();

        Cursor c = cr.query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
                SHOW_ONLINE_ID_PROJECTION,
                getVideoToMarkSelection(library, com.archos.mediascraper.BaseTags.TV_SHOW, toMark)
                        + " AND " + VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED + " > ?",
                new String[]{String.valueOf(Trakt.getLastTimeWatchedSync(mPreferences) + 1)},
                VideoStore.Video.VideoColumns.SCRAPER_S_ONLINE_ID);
        if (c != null) {
            if (c.getCount() > 0) {
                final int sOnlineIdIdx = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_S_ONLINE_ID);
                final int eSeasonNrIdx = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_E_SEASON);
                final int eEpisodeNrIdx = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE);
                final int lastPlayedIdx = c.getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED);
                final int tmdbIdx = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_E_ONLINE_ID);
                final int idIdx = c.getColumnIndex(BaseColumns._ID);

                TraktAPI.EpisodeListParam param = new TraktAPI.EpisodeListParam();
                ArrayList<TraktAPI.Episode> episodeList = new ArrayList<TraktAPI.Episode>();

                String sOnlineIdPrev = null;
                InBuilder inBuilder = null;
                while (c.moveToNext()) {
                    final String sOnlineId = c.getString(sOnlineIdIdx);
                    final int eSeasonNr = c.getInt(eSeasonNrIdx);
                    final int eEpisodeNr = c.getInt(eEpisodeNrIdx);
                    final String tmdbId = c.getString(tmdbIdx);
                    final long lastTimePlayed = c.getLong(lastPlayedIdx);
                    final int id = c.getInt(idIdx);

                    if (sOnlineId != null && eSeasonNr >= 0 && eEpisodeNr >= 0 && lastTimePlayed > 0) {
                        if (sOnlineIdPrev == null || !sOnlineIdPrev.equals(sOnlineId)) {
                            Trakt.Status status = syncFlushEpisodeList(library, param, episodeList, cr, inBuilder != null ? inBuilder.get() : null, toMark);
                            if (status == Trakt.Status.ERROR_NETWORK) {
                                c.close();
                                return status;
                            }
                            episodeList.clear();
                            param.tmdb_id = sOnlineId;
                            sOnlineIdPrev = sOnlineId;
                            inBuilder = new InBuilder(VideoStore.Video.VideoColumns._ID);
                        }

                        TraktAPI.Episode episode = new TraktAPI.Episode();

                        episode.season = eSeasonNr;
                        episode.episode = eEpisodeNr;
                        episode.tmdb = Integer.valueOf(tmdbId);
                        episode.last_played = Trakt.getDateFormat(lastTimePlayed);
                        episodeList.add(episode);
                        inBuilder.addParam(id);
                        Trakt.updateLastPushedWatchedTime(lastTimePlayed);
                    }
                }
                Trakt.Status status = syncFlushEpisodeList(library, param, episodeList, cr, inBuilder != null ? inBuilder.get() : null, toMark);
                if (status == Trakt.Status.ERROR_NETWORK) {
                    c.close();
                    return status;
                }
            }
            c.close();
        }
        return Trakt.Status.SUCCESS;
    }

    private static long getCurrentTraktTime() {
        return (System.currentTimeMillis() / 1000) + TRAKT_SYNC_DELAY; // add a delay in case trakt date is earlier than us.
    }

    // similar usage to simplify:
    // VideoUtils.getLastTimeVideoPlayedUtc()
    // VideoUtils.getLastTimeVideoScrapedUtc()
    // Trakt.getLastTimeMovieWatched(mPreferences)
    // Trakt.getLastTimeShowWatched(mPreferences)
    // PREFERENCE_TRAKT_LAST_TIME_SYNC_LIST PREFERENCE_TRAKT_LAST_TIME_SYNC_TO_DB_LIST
    // PREFERENCE_TRAKT_LAST_TIME_SYNC_PROGRESS PREFERENCE_TRAKT_LAST_TIME_SYNC_TO_DB_PROGRESS
    // PREFERENCE_TRAKT_LAST_TIME_SYNC_WATCHED PREFERENCE_TRAKT_LAST_TIME_SYNC_TO_DB_WATCHED
    //PreferenceManager.getDefaultSharedPreferences(TraktService.this).edit().putLong(PREFERENCE_TRAKT_LAST_ACTIVITY_TIME_CHECKED_UTC, System.currentTimeMillis() / 1000L).apply();

    // Design we need to capture the following scenarios with device 1 and 2 with remote network shares A and B
    // - D1 is out of date with NSA syncs on trakt, refresh NSA revealing new video files -> cannot resync on trakt since last sync because could miss resume points/watched status on the new video files -> need to sync last TRAKT_HISTORY backlog

    // design rules:
    // - only watched status is synced to trakt, not resume points because we do not want to show resume points from other devices
    // - trakt resume point is proposed when playing a video and checked at this time (TO BE VERIFIED)
    // - on multiple devices: e.g. one in the living room and one in the bedroom, if you have children binwatching cartoons you do not want to see the resume points of the other parent bedroom device. Conversely, you do not want to expose bedroom gore show to children in the living room. This means no sync of recently played resume points to fully sync devices.

    // limitations:
    // - cannot use VideoUtils.getLastTimeVideoScrapedUtc() since there could be multiple indexed folders and could have refreshed only one folder and the timestamp does not account for all indexed folders

    // improvements:
    // - VideoUtils.getLastTimeVideoScrapedUtc() < PREFERENCE_TRAKT_LAST_TIME_SYNC_TO_DB_PROGRESS -> can I do a delta time sync? -> answer is NO! (cf. limitations section)
    // - lastActivity.all.toEpochSecond() < PREFERENCE_TRAKT_LAST_TIME_SYNC_WATCHED do nothing (already the case) -> already done in the code

    // TODO verify resume checked when playing a video or network resume time (thus no sync on trakt)
    // TODO check if I need to sync with delta time (if there is really a use case) cf. improvements
    // TODO check the sync calls and getFlagsFromTraktLastActivity

    private int getFlagsFromTraktLastActivity(Trakt.Result result, long movieTime, long showTime) {
        int flag = 0;
        if (log.isDebugEnabled()) log.debug("getFlagsFromTraktLastActivity: getLastActivity input is flag={}, movieTime={}, showTime={}", flag, movieTime, showTime);
        if (result != null && result.status == Trakt.Status.SUCCESS &&
                result.objType == Trakt.Result.ObjectType.LAST_ACTIVITY) {
            LastActivities lastActivity = (LastActivities) result.obj;
            if (log.isDebugEnabled()) log.debug("lastActivity: movie: {} vs {}", lastActivity.movies.watched_at.toEpochSecond(), movieTime);
            if (lastActivity.movies.watched_at.toEpochSecond()> movieTime) { // new fully watched videos more recent than last sync movies
                if (log.isDebugEnabled()) log.debug("getFlagsFromTraktLastActivity: new activity watched on movies on trakt side detected");
                flag |= FLAG_SYNC_TO_DB_WATCHED | FLAG_SYNC_MOVIES; // need to sync watched states and movies
            }
            if (log.isDebugEnabled()) log.debug("lastActivity: show: {} vs {}", lastActivity.episodes.watched_at.toEpochSecond(), showTime);
            if (lastActivity.episodes.watched_at.toEpochSecond()>showTime) { // new fully watched videos more recent than last sync shows
                if (log.isDebugEnabled()) log.debug("getFlagsFromTraktLastActivity: new activity watched on shows on trakt side detected");
                flag |= FLAG_SYNC_TO_DB_WATCHED | FLAG_SYNC_SHOWS; // need to sync watched states and shows
            }

            // otherwise we do a full memory depth sync to get all resume points possibly on new videos
            if (lastActivity.movies.paused_at.toEpochSecond()>movieTime||lastActivity.episodes.paused_at.toEpochSecond()>showTime) { // new resume points more recent than last sync
                if (log.isDebugEnabled()) log.debug("getFlagsFromTraktLastActivity: new activity on progress on trakt side detected either for movie or show");
                flag |= FLAG_SYNC_PROGRESS; // need to sync resume points
            }
            // save last time in preference - discriminate between watched and paused_at (resume points)
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(TraktService.this).edit();
            editor.putLong(PREFERENCE_TRAKT_LAST_ACTIVITY, lastActivity.all.toEpochSecond());

            // Movies: watched and paused timestamps
            if (lastActivity.movies.watched_at != null) {
                editor.putLong(PREFERENCE_TRAKT_LAST_ACTIVITY_MOVIE_WATCHED, lastActivity.movies.watched_at.toEpochSecond());
            }
            if (lastActivity.movies.paused_at != null) {
                editor.putLong(PREFERENCE_TRAKT_LAST_ACTIVITY_MOVIE_PAUSED, lastActivity.movies.paused_at.toEpochSecond());
            }
            editor.putLong(PREFERENCE_TRACK_LAST_ACTIVITY_MOVIE, lastActivity.movies.watched_at.toEpochSecond());

            // Episodes: watched and paused timestamps
            if (lastActivity.episodes.watched_at != null) {
                editor.putLong(PREFERENCE_TRAKT_LAST_ACTIVITY_EPISODE_WATCHED, lastActivity.episodes.watched_at.toEpochSecond());
            }
            if (lastActivity.episodes.paused_at != null) {
                editor.putLong(PREFERENCE_TRAKT_LAST_ACTIVITY_EPISODE_PAUSED, lastActivity.episodes.paused_at.toEpochSecond());
            }
            editor.putLong(PREFERENCE_TRAKT_LAST_ACTIVITY_EPISODE, lastActivity.episodes.watched_at.toEpochSecond());

            //editor.putLong(PREFERENCE_TRAKT_LAST_ACTIVITY_LIST, lastActivity.lists.updated_at.toEpochSecond());
            editor.putLong(PREFERENCE_TRAKT_LAST_ACTIVITY_TIME_CHECKED_UTC, System.currentTimeMillis() / 1000L);
            editor.apply();
        }
        return flag;
    }

    private int getFlagsFromTraktLastActivity(Trakt.Result result) {
        return getFlagsFromTraktLastActivity(result,
                Trakt.getLastTimeMovieWatched(mPreferences),
                Trakt.getLastTimeShowWatched(mPreferences));
    }

    private Trakt.Result handleSyncStatus(Status status, int flag, String details) {
        switch (status) {
            case ERROR_NETWORK:
                Trakt.setFlagSyncPreference(mPreferences, flag);
                String errorMessage = getString(R.string.trakt_toast_syncing_error);
                //try to be more precise
                if(details!=null)
                    errorMessage +=" "+details;
                if (log.isDebugEnabled()) showToast(errorMessage);
                log.warn(errorMessage);
                mPreferences.edit().putInt(PREFERENCE_TRAKT_LAST_SYNC_STATUS, SYNC_STATUS_ERROR_NETWORK).apply();
                break;
            case ERROR_ACCOUNT_LOCKED:
                // Disable Trakt and clear authentication tokens
                Trakt.disableTraktOnAccountLock(this, mPreferences);
                // Show toast notification to user
                String accountLockedMessage = getString(R.string.trakt_toast_account_locked);
                showToast(accountLockedMessage);
                log.error(accountLockedMessage);
                // Don't set flag for retry since account is locked
                mPreferences.edit().putInt(PREFERENCE_TRAKT_LAST_SYNC_STATUS, SYNC_STATUS_ERROR_ACCOUNT_LOCKED).apply();
                break;
            case ERROR_AUTH:
                log.warn("Trakt sync aborted due to authentication failure: {}", details);
                // Don't set flag for retry since auth is failed
                Trakt.setFlagSyncPreference(mPreferences, 0);
                mPreferences.edit().putInt(PREFERENCE_TRAKT_LAST_SYNC_STATUS, SYNC_STATUS_ERROR_AUTH).apply();
                break;
            case SUCCESS:
                if ((flag & FLAG_SYNC_TO_TRAKT_WATCHED) != 0 || (flag & FLAG_SYNC_TO_DB_WATCHED) != 0) {
                    final long time = getCurrentTraktTime();
                    if ((flag & FLAG_SYNC_MOVIES) != 0)
                        Trakt.setLastTimeMovieWatched(mPreferences, time);
                    if ((flag & FLAG_SYNC_SHOWS) != 0)
                        Trakt.setLastTimeShowWatched(mPreferences, time);
                }
                mPreferences.edit()
                        .putInt(PREFERENCE_TRAKT_LAST_SYNC_STATUS, SYNC_STATUS_SUCCESS)
                        .putLong("trakt_last_sync", System.currentTimeMillis() / 1000L)
                        .putInt(PREFERENCE_TRAKT_SKIPPED_NO_DURATION, mSyncSkippedNoDuration)
                        .apply();
            default:
                // SUCCESS go here too
                Trakt.setFlagSyncPreference(mPreferences, 0);
        }
        return Trakt.Result.get(status);
    }

    private Trakt.Result sync(int flag) {
        /*
         *  XXX hmm instead of a simple 0/1 flag, you could also take something like current time
         *  in ms and after the update set everything back to 0 where value < time. that would kill flags
         *  for items removed from trakt
         */

        mSyncSkippedNoDuration = 0;
        if (log.isDebugEnabled()) log.debug("sync with flag={}", flag);
        /*
        if ((flag & FLAG_SYNC_NOW) != 0)
            removeListener();
         */
        // When the network listener is registered we still want to allow sync.
        // Only block when explicitly waiting or offline.
        if (mWaitBeforeSync || !mNetworkState.isConnected())
            return handleSyncStatus(Trakt.Status.ERROR_NETWORK, flag, null);

        if (flag == FLAG_SYNC_AUTO) {
            // AUTO sync should also include any pending retry flags
            flag |= Trakt.getFlagSyncPreference(mPreferences);
        } else if (flag == 0) {
            // If no flag provided, reuse any pending sync flags from preferences (e.g. retry after network errors)
            flag = Trakt.getFlagSyncPreference(mPreferences);
        }

        // Disable collection sync (to avoid hitting Trakt library limits)
        flag &= ~FLAG_SYNC_TO_DB_COLLECTION;
        flag &= ~FLAG_SYNC_TO_TRAKT_COLLECTION;

        if (mForcePush) {
            if (log.isDebugEnabled()) log.debug("sync: force push requested");
            flag |= FLAG_SYNC_TO_TRAKT_WATCHED | FLAG_SYNC_MOVIES | FLAG_SYNC_SHOWS;
        }
        if (mForcePull) {
            if (log.isDebugEnabled()) log.debug("sync: force pull requested");
            flag |= FLAG_SYNC_TO_DB_WATCHED | FLAG_SYNC_MOVIES | FLAG_SYNC_SHOWS | FLAG_SYNC_PROGRESS;
        }

        long movieTime = Trakt.getLastTimeMovieWatched(mPreferences);
        long showTime = Trakt.getLastTimeShowWatched(mPreferences);

        if (log.isDebugEnabled()) log.debug("sync: last sync time is movieTime={}, showTime={}", movieTime, showTime);

        final boolean isFirstSync = showTime == 0 && movieTime == 0 && !Trakt.isFirstSyncDone(mPreferences);
        if (isFirstSync) {
            if (log.isDebugEnabled()) log.debug("sync: first time syncing: full sync");
            flag |= FLAG_SYNC_FULL;
        }

        if ((flag & FLAG_SYNC_LAST_ACTIVITY_VETO) == 0 && (flag & FLAG_SYNC_TO_DB_WATCHED) == 0) {
            // if we don't sync from trakt to db, get last activity to check if we have to.
            if (log.isDebugEnabled()) log.debug("get lastactivity");

            Trakt.Result result = mTrakt.getLastActivity();
            if (result.status == Trakt.Status.ERROR_NETWORK || result.status == Trakt.Status.ERROR_ACCOUNT_LOCKED || result.status == Trakt.Status.ERROR_AUTH)
                return handleSyncStatus(result.status, flag, "lastActivities");
            flag |= getFlagsFromTraktLastActivity(result, movieTime, showTime);
        }
        /*
            Last activity will be used to know if we have something to sync FROM trakt to DB, not from DB to trakt
        */
        if (!Trakt.getSyncCollection(mPreferences)) {
            flag &= ~FLAG_SYNC_TO_DB_COLLECTION;
            flag &= ~FLAG_SYNC_TO_TRAKT_COLLECTION;
        }

        String libraries[] = null;

        if ((flag & FLAG_SYNC_TO_TRAKT_WATCHED) != 0 && (flag & FLAG_SYNC_TO_TRAKT_COLLECTION) != 0)
            libraries = Trakt.LIBRARIES;
        else
            libraries = new String[] {(flag & FLAG_SYNC_TO_TRAKT_COLLECTION) != 0 ?
                    Trakt.LIBRARY_COLLECTION : Trakt.LIBRARY_WATCHED};

        if (libraries != null) {
            for (String library : libraries) {
                if (Trakt.getAction(library) == null)
                    continue;
                // for markAs and unMarkAs
                for (boolean toMark : new boolean[]{ true, false } ) {
                    long maxPushedTime = -1;
                    if (log.isDebugEnabled()) log.debug("syncing movies({}) {} from DB to trakt.tv", toMark, library);
                    Trakt.Status status = syncMoviesToTrakt(library, toMark);
                    if (status == Trakt.Status.ERROR_NETWORK || status == Trakt.Status.ERROR_ACCOUNT_LOCKED || status == Trakt.Status.ERROR_AUTH)
                        return handleSyncStatus(status, flag, "syncMoviesToTrakt");
                    maxPushedTime = Math.max(maxPushedTime, Trakt.getLastPushedWatchedTime());
                    if (log.isDebugEnabled()) log.debug("syncing shows({}) {} from DB to trakt.tv", toMark, library);
                    status = syncShowsToTrakt(library, toMark);
                    if (status == Trakt.Status.ERROR_NETWORK || status == Trakt.Status.ERROR_ACCOUNT_LOCKED || status == Trakt.Status.ERROR_AUTH)
                        return handleSyncStatus(status, flag, "syncShowsToTrakt");
                    maxPushedTime = Math.max(maxPushedTime, Trakt.getLastPushedWatchedTime());
                    if (maxPushedTime > 0 && toMark && library.equals(Trakt.LIBRARY_WATCHED)) {
                        Trakt.setLastTimeWatchedSync(mPreferences, maxPushedTime);
                    }
                }
            }
        }

        // if we know we have something to sync from last activity
        final boolean syncShowsFromTrakt = (flag & FLAG_SYNC_SHOWS) != 0;
        final boolean syncMoviesFromTrakt = (flag & FLAG_SYNC_MOVIES) != 0;
        
        // HYBRID APPROACH: Use new method that preserves original logic with new architecture
        if(Trakt.getSyncPlaybackPreference(mPreferences)) {
            if (log.isDebugEnabled()) log.debug("sync: using hybrid playback sync (preserves original logic + new improvements)");
            Trakt.Status hybridStatus = syncPlaybackStatusHybrid();
            if (hybridStatus == Trakt.Status.ERROR_NETWORK || hybridStatus == Trakt.Status.ERROR_AUTH) {
                return handleSyncStatus(hybridStatus, flag, "syncPlaybackStatusHybrid");
            }
        }
        syncLists();

        if (!syncShowsFromTrakt && !syncMoviesFromTrakt) {
            if (log.isDebugEnabled()) log.debug("sync: no movie/show flag, abort");
            return handleSyncStatus(Trakt.Status.SUCCESS, flag, null);
        }

        libraries = null;

        // mark as read and add to library all movies/shows from trakt.tv to DB

        if ((flag & FLAG_SYNC_TO_DB) != 0) {
            if ((flag & FLAG_SYNC_TO_DB_WATCHED) != 0 && (flag & FLAG_SYNC_TO_DB_COLLECTION) != 0)
                libraries = Trakt.LIBRARIES;
            else
                libraries = new String[] {(flag & FLAG_SYNC_TO_DB_WATCHED) != 0 ?
                        Trakt.LIBRARY_WATCHED : Trakt.LIBRARY_COLLECTION};
        }

        if (libraries != null) {
            for (String library : libraries) {

                if (syncMoviesFromTrakt) {
                    if (log.isDebugEnabled()) log.debug("syncing movies {} from trakt.tv to DB", library);
                    Trakt.Status status = syncMoviesToDb(library);
                    if (log.isDebugEnabled()) log.debug("syncing movies {} from trakt.tv to DB finished : {}", library, status);
                    if (status == Trakt.Status.ERROR_NETWORK || status == Trakt.Status.ERROR_ACCOUNT_LOCKED || status == Trakt.Status.ERROR_AUTH)
                        return handleSyncStatus(status, flag, "syncMoviesToDb");
                }
                if (syncShowsFromTrakt) {
                    if (log.isDebugEnabled()) log.debug("syncing shows {} from trakt.tv to DB", library);
                    Trakt.Status status = syncShowsToDb(library);
                    if (log.isDebugEnabled()) log.debug("syncing shows {} from trakt.tv to DB finished : {}", library, status);
                    if (status == Trakt.Status.ERROR_NETWORK || status == Trakt.Status.ERROR_ACCOUNT_LOCKED || status == Trakt.Status.ERROR_AUTH)
                        return handleSyncStatus(status, flag, "syncShowsToDb");
                }
            }
        }


        Trakt.Result finalResult = handleSyncStatus(Trakt.Status.SUCCESS, flag, null);
        if (finalResult.status == Trakt.Status.SUCCESS && isFirstSync) {
            Trakt.setFirstSyncDone(mPreferences, true);
        }
        return finalResult;
    }

    private void syncLists() {
        if (log.isDebugEnabled()) log.debug("syncLists");
        Cursor cursor = getContentResolver().query(VideoStore.List.LIST_CONTENT_URI,VideoStore.List.Columns.COLUMNS, null, null, null);
        List<VideoStore.List.ListObj> localLists = new ArrayList<>();
        if(cursor.getCount() > 0){
            int titleCol = cursor.getColumnIndex(VideoStore.List.Columns.TITLE);
            int traktIdCol = cursor.getColumnIndex(VideoStore.List.Columns.TRAKT_ID);
            int syncStatusCol = cursor.getColumnIndex(VideoStore.List.Columns.SYNC_STATUS);
            int idCol = cursor.getColumnIndex(VideoStore.List.Columns.ID);
            while(cursor.moveToNext()){
                localLists.add(new VideoStore.List.ListObj(cursor.getInt(idCol), cursor.getString(titleCol), cursor.getInt(traktIdCol), cursor.getInt(syncStatusCol)));
            }
        }
        cursor.close();
        List<VideoStore.List.ListObj> originalLocalLists = new ArrayList<>(localLists);

        Trakt.Result result = mTrakt.getLists(0);
        List<TraktList> listLists;
        if(result.status == Status.SUCCESS){
            listLists= (List<TraktList>) result.obj;
        }else return;

        //trakt to DB
        for(TraktList list : listLists) {
            boolean isIn = false;
            boolean needsRenaming = false;
            boolean wasLocallyDeleted = false;
            for (VideoStore.List.ListObj localList : originalLocalLists) {
                if (localList.traktId == list.ids.trakt) {
                    isIn = true;
                    wasLocallyDeleted = localList.syncStatus == VideoStore.List.SyncStatus.STATUS_DELETED;
                    if (!localList.title.equals(list.name)) {
                        needsRenaming = true;
                    }
                    break;
                }
            }

            if(!isIn){
                //add to DB
                if (log.isDebugEnabled()) log.debug("add new list to DB {}", list.name);
                VideoStore.List.ListObj item = new VideoStore.List.ListObj(list.name, list.ids.trakt, VideoStore.List.SyncStatus.STATUS_OK);
                localLists.add(item);
                getContentResolver().insert(VideoStore.List.LIST_CONTENT_URI, item.toContentValues());
            }

            if(needsRenaming){
                //update DB
            }

            if(wasLocallyDeleted){
                //delete remote + remove from DB
                Trakt.Result result1 = mTrakt.deleteList(0, String.valueOf(list.ids.trakt));
                if (log.isDebugEnabled()) log.debug("delete remote list {}", list.name);
                if(result1.status == Status.SUCCESS){
                    getContentResolver().delete(VideoStore.List.LIST_CONTENT_URI, VideoStore.List.Columns.TRAKT_ID +"= ?", new String[]{String.valueOf(list.ids.trakt)});
                }
            }
        }

        // save last sync time
        PreferenceManager.getDefaultSharedPreferences(TraktService.this).edit().putLong(PREFERENCE_TRAKT_LAST_TIME_SYNC_TO_DB_LIST, System.currentTimeMillis() / 1000L).apply();

        //DB to trakt
        for (VideoStore.List.ListObj localList : originalLocalLists) {
            boolean isIn = false;
            for(TraktList list : listLists) {
                if (localList.traktId == list.ids.trakt) {
                    isIn = true;
                    break;
                }
            }
            if(!isIn) {
                //might not have been deleted, just new
                if(localList.syncStatus==VideoStore.List.SyncStatus.STATUS_NOT_SYNC){
                    //add to trakt
                    if (log.isDebugEnabled()) log.debug(" add List To Trakt {}", localList.title);
                    Trakt.Result result1 = mTrakt.addList(0,localList.title);
                    if(result1.status==Status.SUCCESS){
                        if (log.isDebugEnabled()) log.debug(" add List To Trakt Status.SUCCESS");

                        TraktList list = (TraktList) result1.obj;
                        localList.syncStatus = VideoStore.List.SyncStatus.STATUS_OK;
                        localList.traktId = list.ids.trakt;
                        getContentResolver().update(VideoStore.List.LIST_CONTENT_URI, localList.toContentValues(), VideoStore.List.Columns.ID +"= ?", new String[]{String.valueOf(localList.id)});
                    }
                } else{
                    //was deleted
                    if (log.isDebugEnabled()) log.debug(" deleting list from DB {}", localList.title);
                    getContentResolver().delete(VideoStore.List.LIST_CONTENT_URI, VideoStore.List.Columns.TRAKT_ID +"= ?", new String[]{String.valueOf(localList.traktId)});
                }
            }
        }

        // save last sync time
        PreferenceManager.getDefaultSharedPreferences(TraktService.this).edit().putLong(PREFERENCE_TRAKT_LAST_TIME_SYNC_LIST, System.currentTimeMillis() / 1000L).apply();

        //Sync videos
        //reload local and distant
        cursor = getContentResolver().query(VideoStore.List.LIST_CONTENT_URI,VideoStore.List.Columns.COLUMNS, null, null, null);
        localLists = new ArrayList<>();
        if(cursor.getCount() > 0){
            int titleCol = cursor.getColumnIndex(VideoStore.List.Columns.TITLE);
            int traktIdCol = cursor.getColumnIndex(VideoStore.List.Columns.TRAKT_ID);
            int listIdCol = cursor.getColumnIndex(VideoStore.List.Columns.ID);
            int syncStatusCol = cursor.getColumnIndex(VideoStore.List.Columns.SYNC_STATUS);
            while(cursor.moveToNext()){
                int traktId = cursor.getInt(traktIdCol);
                int localId = cursor.getInt(listIdCol);
                if(traktId>0){
                    Uri listUri = VideoStore.List.getListUri(localId);
                    //sync list content
                    Trakt.Result result1 = mTrakt.getListContent(0, traktId);
                    if(result1.status == Status.SUCCESS){
                        List<ListEntry> traktListItems = (List<ListEntry>) result1.obj;
                        ArrayList<VideoStore.VideoList.VideoItem> localListItems = new ArrayList<>();

                        Cursor cursorVideos = getContentResolver().query(VideoStore.List.getListUri(localId), VideoStore.VideoList.Columns.COLUMNS, null, null, null);
                        if(cursorVideos.getCount() > 0) {
                            int movieIdCol = cursorVideos.getColumnIndex(VideoStore.VideoList.Columns.M_ONLINE_ID);
                            int episodeIdCol = cursorVideos.getColumnIndex(VideoStore.VideoList.Columns.E_ONLINE_ID);
                            int syncStatusCol2 = cursorVideos.getColumnIndex(VideoStore.List.Columns.SYNC_STATUS);
                            while (cursorVideos.moveToNext()) {
                                VideoStore.VideoList.VideoItem videoItem = new VideoStore.VideoList.VideoItem(localId,
                                        cursorVideos.getInt(movieIdCol),
                                        cursorVideos.getInt(episodeIdCol),
                                        cursorVideos.getInt(syncStatusCol2));
                                boolean isIn = false;
                                for(ListEntry onlineItem : traktListItems){
                                    if(videoItem.episodeId > 0
                                            && onlineItem.episode!=null
                                            && onlineItem.episode.ids != null
                                            && onlineItem.episode.ids.tmdb != null
                                            && onlineItem.episode.ids.tmdb.equals(videoItem.episodeId)
                                            || videoItem.movieId>0
                                            && onlineItem.movie!=null
                                            && onlineItem.movie.ids != null
                                            && onlineItem.movie.ids.tmdb != null
                                            && onlineItem.movie.ids.tmdb.equals(videoItem.movieId)){
                                        isIn = true;
                                        break;
                                    }
                                }

                                if(!isIn){
                                    //check status
                                    if(videoItem.syncStatus == VideoStore.List.SyncStatus.STATUS_NOT_SYNC){
                                        //send
                                        Trakt.Result r = mTrakt.addVideoToList(0, traktId, videoItem);
                                        if(r.status == Status.SUCCESS) {
                                            videoItem.syncStatus = VideoStore.List.SyncStatus.STATUS_OK;
                                            getContentResolver().update(listUri, videoItem.toContentValues(), videoItem.getDBWhereString(), videoItem.getDBWhereArgs());
                                        }
                                        localListItems.add(videoItem);
                                    }
                                    else {
                                        videoItem.deleteFromDb(this);
                                    }
                                }else
                                    localListItems.add(videoItem);
                            }
                        }

                        cursorVideos.close();
                        for(ListEntry onlineItem : traktListItems){
                            if(onlineItem.episode == null && onlineItem.movie == null)
                                continue; //skip everything that is not movies or episodes
                            // skip entries without valid TMDb IDs
                            if (onlineItem.movie != null && (onlineItem.movie.ids == null || onlineItem.movie.ids.tmdb == null))
                                continue;
                            if (onlineItem.episode != null && (onlineItem.episode.ids == null || onlineItem.episode.ids.tmdb == null))
                                continue;
                            boolean isIn = false;
                            for(VideoStore.VideoList.VideoItem videoItem : localListItems){
                                if(videoItem.episodeId > 0
                                        && onlineItem.episode!=null
                                        && onlineItem.episode.ids != null
                                        && onlineItem.episode.ids.tmdb != null
                                        && onlineItem.episode.ids.tmdb.equals(videoItem.episodeId)
                                        || videoItem.movieId>0
                                        && onlineItem.movie!=null
                                        && onlineItem.movie.ids != null
                                        && onlineItem.movie.ids.tmdb != null
                                        && onlineItem.movie.ids.tmdb.equals(videoItem.movieId)) {
                                    isIn = true;
                                    if(videoItem.syncStatus == VideoStore.List.SyncStatus.STATUS_DELETED) {
                                        //delete from trakt and DB
                                        mTrakt.removeVideoFromList(0, traktId, onlineItem);
                                        videoItem.deleteFromDb(this);
                                    }
                                    break;
                                }
                            }
                            if(!isIn){
                                //add to DB
                                boolean isEpisode = onlineItem.episode != null;
                                int tmdbMovieId = (onlineItem.movie != null && onlineItem.movie.ids != null && onlineItem.movie.ids.tmdb != null) ? onlineItem.movie.ids.tmdb : -1;
                                int tmdbEpisodeId = (onlineItem.episode != null && onlineItem.episode.ids != null && onlineItem.episode.ids.tmdb != null) ? onlineItem.episode.ids.tmdb : -1;
                                VideoStore.VideoList.VideoItem videoItem =
                                new VideoStore.VideoList.VideoItem(localId, !isEpisode ? tmdbMovieId : -1, isEpisode ? tmdbEpisodeId : -1, VideoStore.List.SyncStatus.STATUS_OK);

                                getContentResolver().insert(listUri, videoItem.toContentValues());
                            }
                        }
                    }
                }
            }
        }
        cursor.close();
    }

    private void showToast(final CharSequence text) {
        if (text != null) {
            mUiHandler.removeCallbacksAndMessages(null);
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mToast != null) {
                        mToast.cancel();
                        mToast = null;
                    }
                    mToast = Toast.makeText(getApplicationContext(), "trakt.tv: " + text, Toast.LENGTH_SHORT);
                    mToast.show();
                }
            });
        }
    }

    @Override
    public void onCreate() {
        // Register as a lifecycle observer
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
        mNetworkState = NetworkState.instance(this);
        mBackgroundHandlerThread  = new HandlerThread(TAG);
        mBackgroundHandlerThread.start();
        mBackgroundHandler = new TraktHandler(mBackgroundHandlerThread.getLooper(), this);
        mUiHandler = new Handler(Looper.getMainLooper());
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        if (log.isDebugEnabled()) log.debug("onDestroy");
        cleanup();
        super.onDestroy();
    }

    private void cleanup() {
        removeListener();
        if (mBackgroundHandler != null) {
            mBackgroundHandler.removeCallbacksAndMessages(null);
        }
        if (mBackgroundHandlerThread != null) {
            if (mBackgroundHandlerThread.quit()) {
                try {
                    mBackgroundHandlerThread.join();
                } catch (InterruptedException e) {
                    log.error("InterruptedException while joining mBackgroundHandlerThread", e);
                }
            }
        }
        if (mUiHandler != null) {
            mUiHandler.removeCallbacksAndMessages(null); // Clear any pending messages
        }
        if (mToast != null) {
            mToast.cancel();
        }
        mNetworkState = null;
        mTrakt = null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (log.isDebugEnabled()) log.debug("Received start id {}: {}", startId, intent);
        networkState = NetworkState.instance(getApplicationContext());
        if (propertyChangeListener == null)
            propertyChangeListener = new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    if (evt.getOldValue() != evt.getNewValue()) {
                        if (log.isDebugEnabled()) log.debug("NetworkState for {} changed:{} -> {}", evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
                        if (mNetworkState == null) mNetworkState = NetworkState.instance(getApplicationContext());
                        if (mNetworkState.isConnected()) { // network state has changed and we are connected now
                            // anti flood mechanism
                            if (mLastTimeSync != -1 || System.currentTimeMillis() - mLastTimeSync > NETWORK_NETWORK_ON_DELAY) {
                                // sync was never done or long ago
                                mLastTimeSync = System.currentTimeMillis();
                                mBackgroundHandler.removeMessages(MSG_NETWORK_ON);
                                mBackgroundHandler.sendEmptyMessage(MSG_NETWORK_ON);
                                mWaitBeforeSync = false;
                            } else {
                                mWaitBeforeSync = true;
                            }
                        }
                    }
                }
            };
        String action = intent != null ? intent.getAction() : null;
        if (mBackgroundHandler != null && action != null)
            mBackgroundHandler.sendMessage(mBackgroundHandler.obtainMessage(MSG_INTENT, intent));
        return START_STICKY;
    }

    public static class Client {
        private final Context mContext;
        private final Listener mListener;
        private final Handler mHandler;
        private final Messenger mMessenger;
        private final boolean mNotify;
        public interface Listener {
            void onResult(Bundle result);
        }

        public Client(Context context, Listener listener, boolean notify) {
            mContext = context;
            mNotify = notify;
            if (listener != null) {
                mListener = listener;
                mHandler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
                    @Override
                    public boolean handleMessage(Message msg) {
                        if (msg.what == MSG_RESULT) {
                            mListener.onResult((Bundle) msg.obj);
                        }
                        return false;
                    }
                });
                mMessenger = new Messenger(mHandler);
            } else {
                mMessenger = null;
                mListener = null;
                mHandler = null;
            }
        }

        private Intent prepareIntent(String action, VideoDbInfo videoInfo, float progress, String traktAction) {
            Intent intent = new Intent(mContext, TraktService.class);
            intent.setAction(action);
            intent.putExtra("notify", mNotify);
            if (mNotify)
                intent.putExtra("notify_time", System.currentTimeMillis());
            if (videoInfo != null)
                intent.putExtra("video_info", videoInfo);
            if (traktAction != null)
                intent.putExtra("action", traktAction);
            if (progress != -1)
                intent.putExtra("progress", progress);
            if (mMessenger != null)
                intent.putExtra("messenger", mMessenger);
            return intent;
        }
        private Intent prepareIntent(String action, long videoID, float progress, String traktAction){
            Intent intent = new Intent(mContext, TraktService.class);
            intent.setAction(action);
            intent.putExtra("notify", mNotify);
            if (mNotify)
                intent.putExtra("notify_time", System.currentTimeMillis());
            if (videoID != -1)
                intent.putExtra("video_id", videoID);
            if (traktAction != null)
                intent.putExtra("action", traktAction);
            if (progress != -1)
                intent.putExtra("progress", progress);
            if (mMessenger != null)
                intent.putExtra("messenger", mMessenger);
            return intent;
        }
        public void watching(long videoID, float progress) {
            if (log.isDebugEnabled()) log.debug("watching: send INTENT_ACTION_WATCHING");
            Intent intent = prepareIntent(INTENT_ACTION_WATCHING, videoID, progress, null);
            if (isForeground) mContext.startService(intent);
        }
        public void watchingStop(long videoID, float progress) {
            if (log.isDebugEnabled()) log.debug("watchingStop: send INTENT_ACTION_WATCHING_STOP");
            Intent intent = prepareIntent(INTENT_ACTION_WATCHING_STOP, videoID, progress, null);
            // Should not check if isForeGround in this specific case in order to allow posting watch status when exiting
            // video playback with home button to save state but with Android restrictions, do not do it because foreground services banned
            if (isForeground) mContext.startService(intent);
        }
        public void watchingPause(long videoID, float progress) {
            if (log.isDebugEnabled()) log.debug("watchingPause: send INTENT_ACTION_WATCHING_PAUSE");
            Intent intent = prepareIntent(INTENT_ACTION_WATCHING_PAUSE, videoID, progress, null);
            if (isForeground) mContext.startService(intent);
        }
        public void watching(VideoDbInfo videoInfo, float progress) {
            if (log.isDebugEnabled()) log.debug("watching: send INTENT_ACTION_WATCHING");
            Intent intent = prepareIntent(INTENT_ACTION_WATCHING, videoInfo, progress, null);
            if (isForeground) mContext.startService(intent);
        }
        public void watchingStop(VideoDbInfo videoInfo, float progress) {
            if (log.isDebugEnabled()) log.debug("watchingStop: send INTENT_ACTION_WATCHING_STOP");
            Intent intent = prepareIntent(INTENT_ACTION_WATCHING_STOP, videoInfo, progress, null);
            // do not check if isForeGround in this specific case in order to allow posting watch status when exiting
            // video playback with home button to save state
            //if (isForeground) mContext.startService(intent);
            mContext.startService(intent);
        }
        public void watchingPause(VideoDbInfo videoInfo, float progress) {
            if (log.isDebugEnabled()) log.debug("watchingPause: send INTENT_ACTION_WATCHING_PAUSE");
            Intent intent = prepareIntent(INTENT_ACTION_WATCHING_PAUSE, videoInfo, progress, null);
            if (isForeground) mContext.startService(intent);
        }
        public void markAs(VideoDbInfo videoInfo, String traktAction) {
            if (log.isDebugEnabled()) log.debug("markAs: send INTENT_ACTION_MARK_AS");
            Intent intent = prepareIntent(INTENT_ACTION_MARK_AS, videoInfo, -1, traktAction);
            if (isForeground) mContext.startService(intent);
        }
        public void wipe() {
            if (log.isDebugEnabled()) log.debug("wipe: send INTENT_ACTION_WIPE");
            Intent intent = prepareIntent(INTENT_ACTION_WIPE, null, -1, null);
            if (isForeground) mContext.startService(intent);
        }
        public void wipeCollection() {
            if (log.isDebugEnabled()) log.debug("wipeCollection: send INTENT_ACTION_WIPE_COLLECTION");
            Intent intent = prepareIntent(INTENT_ACTION_WIPE_COLLECTION, null, -1, null);
            if (isForeground) mContext.startService(intent);
        }
        public void fullSync() {
            if (log.isDebugEnabled()) log.debug("fullSync: send INTENT_ACTION_SYNC");
            Intent intent = prepareIntent(INTENT_ACTION_SYNC, null, -1, null);
            intent.putExtra("flag_sync", FLAG_SYNC_FULL);
            if (isForeground) mContext.startService(intent);
        }
        public void sync(int flag) {
            if (log.isDebugEnabled()) log.debug("sync: send INTENT_ACTION_SYNC");
            Intent intent = prepareIntent(INTENT_ACTION_SYNC, null, -1, null);
            intent.putExtra("flag_sync", flag);
            if (isForeground) mContext.startService(intent);
        }

        public void forcePush() {
            if (log.isDebugEnabled()) log.debug("forcePush: send INTENT_ACTION_FORCE_PUSH");
            Intent intent = prepareIntent(INTENT_ACTION_FORCE_PUSH, null, -1, null);
            if (isForeground) mContext.startService(intent);
        }

        public void forcePull() {
            if (log.isDebugEnabled()) log.debug("forcePull: send INTENT_ACTION_FORCE_PULL");
            Intent intent = prepareIntent(INTENT_ACTION_FORCE_PULL, null, -1, null);
            if (isForeground) mContext.startService(intent);
        }
    }

    public static void sync(Context context, int flag) {
        if (Trakt.isTraktV2Enabled(context, PreferenceManager.getDefaultSharedPreferences(context)))
            new Client(context, null, false).sync(flag);
    }

    // WARNING: this triggers a full sync thus do not use at each new video addition...
    // TODO: improvement, add a method to add a single new video
    public static void onNewVideo(Context context) {
        if (Trakt.isTraktV2Enabled(context, PreferenceManager.getDefaultSharedPreferences(context)))
            new Client(context, null, false).sync(FLAG_SYNC_TO_DB_WATCHED|FLAG_SYNC_TO_TRAKT|FLAG_SYNC_MOVIES|FLAG_SYNC_SHOWS);
    }

    public static void syncAtStart(Context context) {
        if (Trakt.isTraktV2Enabled(context, PreferenceManager.getDefaultSharedPreferences(context)))
            new Client(context, null, false).sync(FLAG_SYNC_TO_DB_WATCHED|FLAG_SYNC_MOVIES|FLAG_SYNC_SHOWS|FLAG_SYNC_PROGRESS);
    }

    private void addListener() {
        if (networkState == null) networkState = NetworkState.instance(getApplicationContext());
        if (!mNetworkStateListenerAdded && propertyChangeListener != null) {
            if (log.isDebugEnabled()) log.debug("addNetworkListener: networkState.addPropertyChangeListener");
            networkState.addPropertyChangeListener(propertyChangeListener);
            mNetworkStateListenerAdded = true;
        }
    }

    private void removeListener() {
        if (networkState == null) networkState = NetworkState.instance(getApplicationContext());
        if (mNetworkStateListenerAdded && propertyChangeListener != null) {
            if (log.isDebugEnabled()) log.debug("removeListener: networkState.removePropertyChangeListener");
            networkState.removePropertyChangeListener(propertyChangeListener);
            mNetworkStateListenerAdded = false;
        }
    }

    @Override
    public void onStop(LifecycleOwner owner) {
        // App in background
        if (log.isDebugEnabled()) log.debug("onStop: LifecycleOwner App in background");
        isForeground = false;
        cleanup();
        stopSelf();
    }

    @Override
    public void onStart(LifecycleOwner owner) {
        // App in foreground
        if (log.isDebugEnabled()) log.debug("onStart: LifecycleOwner App in foreground");
        isForeground = true;
        if (NetworkState.isNetworkConnected(getApplicationContext()))
            TraktService.sync(getApplicationContext(), TraktService.FLAG_SYNC_AUTO);
        addListener();
    }

}
