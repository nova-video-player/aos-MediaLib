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
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import androidx.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.widget.Toast;

import com.archos.mediacenter.utils.AppState;
import com.archos.mediacenter.utils.trakt.Trakt.Status;
import com.archos.mediacenter.utils.videodb.IndexHelper;
import com.archos.mediacenter.utils.videodb.VideoDbInfo;
import com.archos.medialib.R;
import com.archos.environment.NetworkState;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.ScrapeStatus;
import com.uwetrottmann.trakt5.entities.BaseEpisode;
import com.uwetrottmann.trakt5.entities.BaseMovie;
import com.uwetrottmann.trakt5.entities.BaseSeason;
import com.uwetrottmann.trakt5.entities.BaseShow;
import com.uwetrottmann.trakt5.entities.EpisodeIds;
import com.uwetrottmann.trakt5.entities.GenericProgress;
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
import org.threeten.bp.DayOfWeek;
import org.threeten.bp.Instant;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.OffsetDateTime;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZoneOffset;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.temporal.TemporalAdjusters;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class TraktService extends Service {

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
    private IndexHelper mIndexHelper = null;
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

    public static final int FLAG_SYNC_AUTO =                0x001;
    public static final int FLAG_SYNC_LAST_ACTIVITY_VETO =  0x002;
    public static final int FLAG_SYNC_MOVIES =              0x004;
    public static final int FLAG_SYNC_SHOWS =               0x008;
    public static final int FLAG_SYNC_TO_DB_WATCHED =       0x010;
    public static final int FLAG_SYNC_TO_DB_COLLECTION =    0x020;
    public static final int FLAG_SYNC_TO_TRAKT_WATCHED =    0x040;
    public static final int FLAG_SYNC_TO_TRAKT_COLLECTION = 0x080;
    public static final int FLAG_SYNC_NOW =                 0x100;
    public static final int FLAG_SYNC_PROGRESS =       0x200;
    public static final int FLAG_SYNC_TO_DB = FLAG_SYNC_TO_DB_WATCHED | FLAG_SYNC_TO_DB_COLLECTION;
    public static final int FLAG_SYNC_TO_TRAKT = FLAG_SYNC_TO_TRAKT_WATCHED | FLAG_SYNC_TO_TRAKT_COLLECTION;
    public static final int FLAG_SYNC_FULL = FLAG_SYNC_TO_DB | FLAG_SYNC_TO_TRAKT | FLAG_SYNC_MOVIES | FLAG_SYNC_SHOWS;

    private static final long TRAKT_SYNC_DELAY = 30; // in sec

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
            log.debug("handleMessage msg=" + msg.toString());
            if (msg.what == MSG_INTENT) {
                Intent intent = (Intent) msg.obj;
                String action = intent.getAction();

                VideoDbInfo videoInfo = intent.getParcelableExtra("video_info");
                final long videoID = intent.getLongExtra("video_id", -1);
                final Messenger messenger = intent.getParcelableExtra("messenger");
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
                        log.debug("postWatching progress=" + progress);
                        if (videoInfo == null && videoID >= 0)
                            videoInfo = VideoDbInfo.fromId(getContentResolver(), videoID);
                        if (videoInfo != null) {
                            final float finalProgress = progress >= 0 ? progress : -progress;
                            result = mTrakt.postWatching(videoInfo, finalProgress);
                            if (videoInfo.traktResume < 0 && (result.status == Trakt.Status.SUCCESS || result.status == Trakt.Status.SUCCESS_ALREADY)) {
                                videoInfo.traktResume = Math.abs(videoInfo.traktResume);
                                ContentValues values = new ContentValues();
                                values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_RESUME, videoInfo.traktResume);
                                getContentResolver().update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
                                        values, VideoStore.Video.VideoColumns._ID + " = " + videoInfo.id, null);

                            }
                            log.debug("postWatching, result: " + result.status);
                            if (result.status != Status.ERROR) {
                                // continue the service even in case of network error
                                mBusy = true;
                            }
                        }
                    } else if (action.equals(INTENT_ACTION_WATCHING_STOP)) {
                        final float progress = intent.getFloatExtra("progress", -1);
                        log.debug("postWatchingStop progress=" + progress);
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
                            log.debug("postWatchingStop, result: " + result.status);
                            mBusy = false;
                        }
                    } else if (action.equals(INTENT_ACTION_WATCHING_PAUSE)) {
                        final float progress = intent.getFloatExtra("progress", -1);
                        log.debug("postWatchingPause progress=" + progress);
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
                            log.debug("postWatchingPause, result: " + result.status);
                            mBusy = false;
                        }
                    } else if (action.equals(INTENT_ACTION_MARK_AS)) {
                        // get last activity before doing some activity on trakt
                        traktAction = intent.getStringExtra("action");
                        log.debug("markAs: " + traktAction);

                        if (traktAction != null && videoInfo != null) {
                            final boolean wasBusy = mBusy;
                            mBusy = true;
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
                        // store last successful trakt sync time
                        if (result.status == Status.SUCCESS) mPreferences.edit().putLong("trakt_last_sync", System.currentTimeMillis() / 1000L).apply();
                    }
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
                    log.debug("markAs: Trakt.Status: " + status + ", scrapeStatus: " + videoInfo.scrapeStatus);
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
                    log.debug("MSG_NETWORK_ON: sync");
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
            log.debug("saveTraktStatus, id: "+videoInfo.id);
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

    private static final String getVideoToMarkSelection(String library, int scraperType, boolean toMark) {
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

    private Trakt.Status syncFlushEpisodeList(String library, TraktAPI.EpisodeListParam param,
                                              ArrayList<TraktAPI.Episode> episodeList, ContentResolver cr, String selection, boolean mark) {
        if (episodeList.size() > 0) {
            param.episodes = new TraktAPI.Episode[episodeList.size()];
            param.episodes = episodeList.toArray(param.episodes);
            ArrayList<SyncEpisode> eps = new ArrayList<SyncEpisode>();
            for (TraktAPI.Episode ep : param.episodes){
                SyncEpisode se = new SyncEpisode();
                EpisodeIds ids = new EpisodeIds();
                ids.tmdb = ep.tmdb ;
                se.id(ids);
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

    // only sync playback status on last 50 entries i.e. Trakt.PLAYBACK_HISTORY_SIZE
    private Trakt.Status syncPlaybackStatus(){
        log.debug("syncPlaybackStatus start");

        final ContentResolver cr = getContentResolver();
        Trakt.Result resultTrakt = mTrakt.getPlaybackStatus();
        java.util.List<PlaybackResponse> videos = null;
        // MOVIES here is either MOVIE or EPISODE
        if (resultTrakt.status == Trakt.Status.SUCCESS &&
                resultTrakt.objType == Trakt.Result.ObjectType.MOVIES) {
            videos = (java.util.List<PlaybackResponse>) resultTrakt.obj;
        }
        log.debug("syncPlaybackStatus: processing batch of " + videos.size());
        //from db to trakt

        Cursor c1= cr.query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, VideoDbInfo.COLUMNS, VideoStore.Video.VideoColumns.ARCHOS_TRAKT_RESUME +" < 0", null, null);
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
                            for (PlaybackResponse video : videos) {
                                // this value hasn't been sync yet
                                // we should check if videoInfo.watched_at more recent
                                if ((video.movie != null
                                        && video.movie.ids != null
                                        && videoInfo.scraperMovieId != null
                                        && video.movie.ids.tmdb == Integer.valueOf(videoInfo.scraperMovieId)
                                        && video.progress > -videoInfo.traktResume) || // negative traktResume means set but not yet synced
                                        (video.episode != null
                                                && video.episode.ids != null
                                                && videoInfo.scraperEpisodeId != null
                                                && video.episode.ids.tmdb == Integer.valueOf(videoInfo.scraperEpisodeId)
                                                && video.progress > -videoInfo.traktResume)) {
                                    //trakt mark is more advanced, we don't send anything
                                    send = false;
                                    gprog = video;
                                    break;
                                }
                            }
                        if (send) {
                            log.debug("syncPlaybackStatus: db->trakt " + videoInfo.scraperTitle + (videoInfo.isShow ? ", s" + videoInfo.scraperSeasonNr + "e" + videoInfo.scraperEpisodeNr : ""));
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

        //from trakt to db

        if (videos != null && videos.size() > 0) {
            String whereR;
            for (PlaybackResponse video : videos){
                if (video.movie != null) { // it is a movie
                    whereR = VideoStore.Video.VideoColumns._ID+" IN ("+
                            "SELECT video_id FROM movie where " + VideoStore.Video.VideoColumns.SCRAPER_M_ONLINE_ID + "= " + video.movie.ids.tmdb+")";
                } else { // it is an episode
                    whereR = VideoStore.Video.VideoColumns._ID+" IN ("+
                            "SELECT video_id FROM episode where " + VideoStore.Video.VideoColumns.SCRAPER_E_ONLINE_ID + "= " + video.episode.ids.tmdb+")";
                }
                Cursor c = cr.query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, SYNC_PROGRESS_PROJECTION, whereR, null, null);
                if (c == null) log.debug("syncPlaybackStatus: trakt->db cursor null!");
                if (c != null) {
                    if (c.getCount() > 0) {
                        final int idIdx = c.getColumnIndex(BaseColumns._ID);

                        while (c.moveToNext()) {
                            int id = c.getInt(idIdx);
                            VideoDbInfo i = VideoDbInfo.fromId(cr, id);
                            if (i != null) {
                                int newResumePercent = (int) Math.round(video.progress);
                                int newResume = (int) (video.progress/100.0*i.duration);
                                long lastWatched = 1; // 1st second of 1970 by default
                                OffsetDateTime lastWatchedOffsetDateTime = video.paused_at;
                                String lastPlayedDateString = "1";
                                if (lastWatchedOffsetDateTime != null) {
                                    lastWatched = lastWatchedOffsetDateTime.toEpochSecond();
                                    lastPlayedDateString = LocalDateTime.ofEpochSecond(lastWatched, 0, ZoneOffset.UTC).toString();
                                }
                                log.debug("syncPlaybackStatus: trakt->db db traktResume=" + i.scraperTitle +
                                        (i.isShow ? "s" + i.scraperSeasonNr + "e" + i.scraperEpisodeNr : "") +
                                        i.traktResume + "%" + ", traktSeen=" + i.traktSeen +
                                        ", resume " + i.resume + ", lastTimePlayed " + i.lastTimePlayed +
                                        "; trakt resume=" + newResumePercent + "%" +
                                        ", resume " + newResume + ", lastTimePlayed " + lastWatched +
                                        ", lastWatched=" + lastPlayedDateString);
                                boolean toConsider = false;
                                ContentValues values = new ContentValues();
                                if (i.lastTimePlayed < lastWatched && newResumePercent > 0) {
                                    // trakt lastTimePlayed > db lastTimePlayed: in this case update archos last time played since trakt was the latest compared to db
                                    // exclude null newResumePercent since some other players use this to store library (e.g. infuse) and avoid pollution
                                    log.debug("syncPlaybackStatus: trakt->db update Archos last time played by trakt which is the latest " + lastPlayedDateString);
                                    toConsider = true;
                                    values.put(VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED, lastWatched);
                                }
                                if (Math.abs(i.traktResume) != newResumePercent && // trakt resume % != db resume %
                                                i.traktSeen != 1 && // marked not watched on trakt (even if replayed)
                                                newResume > i.resume && // trakt resume time > db resume time
                                                i.resume != -2) { //not end of file (i.resume = -2 is file end)
                                    // trakt resume time is ahead of device one: only update device one in this case
                                    log.debug("syncPlaybackStatus: trakt->db trakt has the latest bookmark " + newResumePercent + "%, use this one");
                                    toConsider = true;
                                    values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_RESUME, newResumePercent);
                                    values.put(VideoStore.Video.VideoColumns.BOOKMARK, newResume);
                                    if (newResumePercent > Trakt.SCROBBLE_THRESHOLD) { // we are at end of file
                                        log.debug("syncPlaybackStatus: trakt->db trakt video has been completed on trakt, mark it viewed");
                                        values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_RESUME, 99); // resume%
                                        values.put(VideoStore.Video.VideoColumns.BOOKMARK, -2); // file end
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
                }
            }
        }
        log.debug("syncPlaybackStatus complete");
        return Trakt.Status.SUCCESS;
    }

    private Trakt.Status syncMoviesToDb(String library) {
        final ContentResolver cr = getContentResolver();

        Trakt.Result result = mTrakt.getAllMovies(library, true);
        if (result.status == Trakt.Status.ERROR_NETWORK)
            return Trakt.Status.ERROR_NETWORK;
        if (result.status == Trakt.Status.SUCCESS &&
                result.objType == Trakt.Result.ObjectType.MOVIES) {
            java.util.List<BaseMovie> movies = (java.util.List<BaseMovie>) result.obj;

            if (movies.size() > 0) {
                InBuilder inBuilder = new InBuilder(VideoStore.Video.VideoColumns.SCRAPER_M_ONLINE_ID);
                for (BaseMovie movie : movies){
                    inBuilder.addParam(movie.movie.ids.tmdb);
                    log.debug("syncMoviesToDb: marking " + movie.movie.title);
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
        final ContentResolver cr = getContentResolver();
        Trakt.Result result = mTrakt.getAllShows(library);
        if (result.status == Trakt.Status.ERROR_NETWORK)
            return Trakt.Status.ERROR_NETWORK;
        if (result.status == Trakt.Status.SUCCESS &&
                result.objType == Trakt.Result.ObjectType.SHOWS_PER_SEASON) {
            java.util.List<BaseShow> shows = (java.util.List<BaseShow> ) result.obj;
            if (shows.size() > 0) {
                for (BaseShow show : shows) {
                    for (BaseSeason season : show.seasons) {
                        InBuilder inBuilder = new InBuilder("number_episode");
                        for (BaseEpisode episode : season.episodes) {
                            log.debug("syncShowsToDb: marking " + show.show.title + " s" + season.number + "e" + episode.number);
                            inBuilder.addParam(episode.number);
                        }
                        final String inSelection = inBuilder.get();
                        if (inSelection != null) {
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

        Cursor c = cr.query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
                MOVIE_ONLINE_ID_PROJECTION,
                getVideoToMarkSelection(library, com.archos.mediascraper.BaseTags.MOVIE, toMark),
                null,
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
                    if (mOnlineId != null) {
                        TraktAPI.Movie movie = new TraktAPI.Movie();
                        movie.tmdb_id = mOnlineId;
                        movie.last_played = Trakt.getDateFormat(lastTimePlayed);
                        movieList.add(movie);
                        inBuilder.addParam(id);
                    }
                }
                if (movieList.size() > 0) {
                    param.movies = new TraktAPI.Movie[movieList.size()];
                    param.movies = movieList.toArray(param.movies);
                    ArrayList<SyncMovie> eps = new ArrayList<SyncMovie>();
                    for (TraktAPI.Movie m : param.movies){
                        SyncMovie se = new SyncMovie();
                        MovieIds ids = new MovieIds();
                        ids.tmdb =  Integer.valueOf(m.tmdb_id);
                        se.id(ids);
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

        Cursor c = cr.query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
                SHOW_ONLINE_ID_PROJECTION,
                getVideoToMarkSelection(library, com.archos.mediascraper.BaseTags.TV_SHOW, toMark),
                null,
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

                    if (sOnlineId != null && eSeasonNr >= 0 && eEpisodeNr >= 0) {
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

    private int getFlagsFromTraktLastActivity(Trakt.Result result, long movieTime, long showTime) {
        int flag = 0;
        log.debug("getFlagsFromTraktLastActivity: getLastActivity input is flag=" + flag + ", movieTime=" + movieTime + ", showTime=" + showTime);
        if (result != null && result.status == Trakt.Status.SUCCESS &&
                result.objType == Trakt.Result.ObjectType.LAST_ACTIVITY) {
            LastActivities lastActivity = (LastActivities) result.obj;
            log.debug("lastActivity: movie: " + lastActivity.movies.watched_at.toEpochSecond() + " vs " + movieTime);
            if (lastActivity.movies.watched_at.toEpochSecond()> movieTime) {
                log.debug("getFlagsFromTraktLastActivity: new activity watched on movies on trakt side detected");
                flag |= FLAG_SYNC_TO_DB_WATCHED | FLAG_SYNC_MOVIES;
            }
            log.debug("lastActivity: show: " + lastActivity.episodes.watched_at.toEpochSecond() + " vs " + showTime);
            if (lastActivity.episodes.watched_at.toEpochSecond()>showTime) {
                log.debug("getFlagsFromTraktLastActivity: new activity watched on shows on trakt side detected");
                flag |= FLAG_SYNC_TO_DB_WATCHED | FLAG_SYNC_SHOWS;
            }
            if (lastActivity.movies.paused_at.toEpochSecond()>movieTime||lastActivity.episodes.paused_at.toEpochSecond()>showTime) {
                log.debug("getFlagsFromTraktLastActivity: new activity on progress on trakt side detected either for movie or show");
                flag |= FLAG_SYNC_PROGRESS;
            }
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
                break;
            case SUCCESS:
                if ((flag & FLAG_SYNC_TO_TRAKT_WATCHED) != 0 || (flag & FLAG_SYNC_TO_DB_WATCHED) != 0) {
                    final long time = getCurrentTraktTime();
                    if ((flag & FLAG_SYNC_MOVIES) != 0)
                        Trakt.setLastTimeMovieWatched(mPreferences, time);
                    if ((flag & FLAG_SYNC_SHOWS) != 0)
                        Trakt.setLastTimeShowWatched(mPreferences, time);
                }
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

        log.debug("sync with flag=" + flag);
        /*
        if ((flag & FLAG_SYNC_NOW) != 0)
            removeListener();
         */
        if (mNetworkStateListenerAdded || mWaitBeforeSync || !mNetworkState.isConnected())
            return handleSyncStatus(Trakt.Status.ERROR_NETWORK, flag, null);

        if (flag == 0 || flag == FLAG_SYNC_AUTO)
            flag = Trakt.getFlagSyncPreference(mPreferences);

        long movieTime = Trakt.getLastTimeMovieWatched(mPreferences);
        long showTime = Trakt.getLastTimeShowWatched(mPreferences);

        log.debug("sync: last sync time is movieTime=" + movieTime + ", showTime=" + showTime);

        if (showTime == 0 && movieTime == 0) {
            log.debug("sync: first time syncing: full sync");
            flag |= FLAG_SYNC_FULL;
        }

        if ((flag & FLAG_SYNC_LAST_ACTIVITY_VETO) == 0 && (flag & FLAG_SYNC_TO_DB_WATCHED) == 0) {
            // if we don't sync from trakt to db, get last activity to check if we have to.
            log.debug("get lastactivity");

            Trakt.Result result = mTrakt.getLastActivity();
            if (result.status == Trakt.Status.ERROR_NETWORK)
                return handleSyncStatus(Trakt.Status.ERROR_NETWORK, flag, "lastActivities");
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
                    log.debug("syncing movies("+toMark+") " + library + " from DB to trakt.tv");
                    Trakt.Status status = syncMoviesToTrakt(library, toMark);
                    if (status == Trakt.Status.ERROR_NETWORK)
                        return handleSyncStatus(status, flag, "syncMoviesToTrakt");
                    log.debug("syncing shows("+toMark+") " + library + " from DB to trakt.tv");
                    status = syncShowsToTrakt(library, toMark);
                    if (status == Trakt.Status.ERROR_NETWORK)
                        return handleSyncStatus(status, flag, "syncShowsToTrakt");
                }
            }
        }

        // if we know we have something to sync from last activity
        final boolean syncShowsFromTrakt = (flag & FLAG_SYNC_SHOWS) != 0;
        final boolean syncMoviesFromTrakt = (flag & FLAG_SYNC_MOVIES) != 0;
        if(Trakt.getSyncPlaybackPreference(mPreferences)){
            syncPlaybackStatus();
        }
        syncLists();

        if (!syncShowsFromTrakt && !syncMoviesFromTrakt) {
            log.debug("sync: no movie/show flag, abort");
            return Trakt.Result.getSuccess();
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
                    log.debug("syncing movies " + library + " from trakt.tv to DB");
                    Trakt.Status status = syncMoviesToDb(library);
                    log.debug("syncing movies " + library + " from trakt.tv to DB finished : "+status);
                    if (status == Trakt.Status.ERROR_NETWORK)
                        return handleSyncStatus(status, flag, "syncMoviesToDb");
                }
                if (syncShowsFromTrakt) {
                    log.debug("syncing shows " + library + " from trakt.tv to DB");
                    Trakt.Status status = syncShowsToDb(library);
                    log.debug("syncing shows " + library + " from trakt.tv to DB finished : "+status);
                    if (status == Trakt.Status.ERROR_NETWORK)
                        return handleSyncStatus(status, flag, "syncShowsToDb");
                }
            }
        }


        return handleSyncStatus(Trakt.Status.SUCCESS, flag, null);
    }

    private void syncLists() {
        log.debug("syncLists");
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
                log.debug("add new list to DB "+list.name);
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
                log.debug("delete remote list "+list.name);
                if(result1.status == Status.SUCCESS){
                    getContentResolver().delete(VideoStore.List.LIST_CONTENT_URI, VideoStore.List.Columns.TRAKT_ID +"= ?", new String[]{String.valueOf(list.ids.trakt)});
                }
            }
        }

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
                    log.debug(" add List To Trakt "+localList.title);
                    Trakt.Result result1 = mTrakt.addList(0,localList.title);
                    if(result1.status==Status.SUCCESS){
                        log.debug(" add List To Trakt Status.SUCCESS");

                        TraktList list = (TraktList) result1.obj;
                        localList.syncStatus = VideoStore.List.SyncStatus.STATUS_OK;
                        localList.traktId = list.ids.trakt;
                        getContentResolver().update(VideoStore.List.LIST_CONTENT_URI, localList.toContentValues(), VideoStore.List.Columns.ID +"= ?", new String[]{String.valueOf(localList.id)});
                    }
                } else{
                    //was deleted
                    log.debug(" deleting list from DB "+localList.title);
                    getContentResolver().delete(VideoStore.List.LIST_CONTENT_URI, VideoStore.List.Columns.TRAKT_ID +"= ?", new String[]{String.valueOf(localList.traktId)});
                }
            }
        }

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
                                            && onlineItem.episode.ids.tmdb.equals(videoItem.episodeId)
                                            || videoItem.movieId>0
                                            && onlineItem.movie!=null
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
                            boolean isIn = false;
                            for(VideoStore.VideoList.VideoItem videoItem : localListItems){
                                if(videoItem.episodeId > 0
                                        && onlineItem.episode!=null
                                        && onlineItem.episode.ids.tmdb.equals(videoItem.episodeId)
                                        || videoItem.movieId>0
                                        && onlineItem.movie!=null
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
                                VideoStore.VideoList.VideoItem videoItem  =
                                        new VideoStore.VideoList.VideoItem(localId,!isEpisode?onlineItem.movie.ids.tmdb:-1, isEpisode?onlineItem.episode.ids.tmdb:-1, VideoStore.List.SyncStatus.STATUS_OK);

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
        mNetworkState = NetworkState.instance(this);
        mBackgroundHandlerThread  = new HandlerThread(TAG);
        mBackgroundHandlerThread.start();
        mBackgroundHandler = new TraktHandler(mBackgroundHandlerThread.getLooper(), this);
        mUiHandler = new Handler();
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        // handles foregroud/background for networkState add/remove Listener
        AppState.addOnForeGroundListener(mForeGroundListener);
        handleForeGround(AppState.isForeGround());
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        log.debug("onDestroy");
        removeListener();
        mBackgroundHandler.removeCallbacksAndMessages(null);
        if (mBackgroundHandlerThread.quit()) {
            try {
                mBackgroundHandlerThread.join();
            } catch (InterruptedException e) {
            }
        }
        mBackgroundHandlerThread = null;
        mNetworkState = null;
        mTrakt = null;
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log.debug("Received start id " + startId + ": " + intent);
        networkState = NetworkState.instance(getApplicationContext());
        if (propertyChangeListener == null)
            propertyChangeListener = new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    if (evt.getOldValue() != evt.getNewValue()) {
                        log.debug("NetworkState for " + evt.getPropertyName() + " changed:" + evt.getOldValue() + " -> " + evt.getNewValue());
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
        if (action != null)
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
            log.debug("watching: send INTENT_ACTION_WATCHING");
            Intent intent = prepareIntent(INTENT_ACTION_WATCHING, videoID, progress, null);
            if (AppState.isForeGround()) mContext.startService(intent);
        }
        public void watchingStop(long videoID, float progress) {
            log.debug("watchingStop: send INTENT_ACTION_WATCHING_STOP");
            Intent intent = prepareIntent(INTENT_ACTION_WATCHING_STOP, videoID, progress, null);
            // do not check if isForeGround in this specific case in order to allow posting watch status when exiting
            // video playback with home button to save state
            //if (AppState.isForeGround()) mContext.startService(intent);
            mContext.startService(intent);
        }
        public void watchingPause(long videoID, float progress) {
            log.debug("watchingPause: send INTENT_ACTION_WATCHING_PAUSE");
            Intent intent = prepareIntent(INTENT_ACTION_WATCHING_PAUSE, videoID, progress, null);
            if (AppState.isForeGround()) mContext.startService(intent);
        }
        public void watching(VideoDbInfo videoInfo, float progress) {
            log.debug("watching: send INTENT_ACTION_WATCHING");
            Intent intent = prepareIntent(INTENT_ACTION_WATCHING, videoInfo, progress, null);
            if (AppState.isForeGround()) mContext.startService(intent);
        }
        public void watchingStop(VideoDbInfo videoInfo, float progress) {
            log.debug("watchingStop: send INTENT_ACTION_WATCHING_STOP");
            Intent intent = prepareIntent(INTENT_ACTION_WATCHING_STOP, videoInfo, progress, null);
            // do not check if isForeGround in this specific case in order to allow posting watch status when exiting
            // video playback with home button to save state
            //if (AppState.isForeGround()) mContext.startService(intent);
            mContext.startService(intent);
        }
        public void watchingPause(VideoDbInfo videoInfo, float progress) {
            log.debug("watchingPause: send INTENT_ACTION_WATCHING_PAUSE");
            Intent intent = prepareIntent(INTENT_ACTION_WATCHING_PAUSE, videoInfo, progress, null);
            if (AppState.isForeGround()) mContext.startService(intent);
        }
        public void markAs(VideoDbInfo videoInfo, String traktAction) {
            log.debug("markAs: send INTENT_ACTION_MARK_AS");
            Intent intent = prepareIntent(INTENT_ACTION_MARK_AS, videoInfo, -1, traktAction);
            if (AppState.isForeGround()) mContext.startService(intent);
        }
        public void wipe() {
            log.debug("wipe: send INTENT_ACTION_WIPE");
            Intent intent = prepareIntent(INTENT_ACTION_WIPE, null, -1, null);
            if (AppState.isForeGround()) mContext.startService(intent);
        }
        public void wipeCollection() {
            log.debug("wipeCollection: send INTENT_ACTION_WIPE_COLLECTION");
            Intent intent = prepareIntent(INTENT_ACTION_WIPE_COLLECTION, null, -1, null);
            if (AppState.isForeGround()) mContext.startService(intent);
        }
        public void fullSync() {
            log.debug("fullSync: send INTENT_ACTION_SYNC");
            Intent intent = prepareIntent(INTENT_ACTION_SYNC, null, -1, null);
            intent.putExtra("flag_sync", FLAG_SYNC_FULL);
            if (AppState.isForeGround()) mContext.startService(intent);
        }
        public void sync(int flag) {
            log.debug("sync: send INTENT_ACTION_SYNC");
            Intent intent = prepareIntent(INTENT_ACTION_SYNC, null, -1, null);
            intent.putExtra("flag_sync", flag);
            if (AppState.isForeGround()) mContext.startService(intent);
        }
    }

    public static void sync(Context context, int flag) {
        if (Trakt.isTraktV2Enabled(context, PreferenceManager.getDefaultSharedPreferences(context)))
            new Client(context, null, false).sync(flag);
    }

    public static void onNewVideo(Context context) {
        if (Trakt.isTraktV2Enabled(context, PreferenceManager.getDefaultSharedPreferences(context)))
            new Client(context, null, false).sync(FLAG_SYNC_TO_DB_WATCHED|FLAG_SYNC_TO_TRAKT|FLAG_SYNC_MOVIES|FLAG_SYNC_SHOWS);
    }

    public static void init() {
        AppState.addOnForeGroundListener(sForeGroundListener);
    }

    // this one is static for CustomApplication init
    private static final AppState.OnForeGroundListener sForeGroundListener = new AppState.OnForeGroundListener() {
        @Override
        public void onForeGroundState(Context applicationContext, boolean foreground) {
            if (foreground && NetworkState.isNetworkConnected(applicationContext))
                TraktService.sync(applicationContext, TraktService.FLAG_SYNC_AUTO);
        }
    };

    // this one is non static for service use
    private final AppState.OnForeGroundListener mForeGroundListener = new AppState.OnForeGroundListener() {
        @Override
        public void onForeGroundState(Context applicationContext, boolean foreground) {
            if(foreground) {
                log.debug("mForeGroundListener: foreground");
            }  else {
                log.debug("mForeGroundListener: background");
            }
            handleForeGround(foreground);
        }
    };

    protected void handleForeGround(boolean foreground) {
        if (foreground) {
            log.debug("handleForeGround: app now in ForeGround");
            //addListener(false);
            addListener();
        } else {
            log.debug("handleForeGround: app now in BackGround");
            removeListener();
        }
    }

    private void addListener() {
        if (networkState == null) networkState = NetworkState.instance(getApplicationContext());
        if (!mNetworkStateListenerAdded && propertyChangeListener != null) {
            log.debug("addNetworkListener: networkState.addPropertyChangeListener");
            networkState.addPropertyChangeListener(propertyChangeListener);
            mNetworkStateListenerAdded = true;
        }
    }

    private void removeListener() {
        if (networkState == null) networkState = NetworkState.instance(getApplicationContext());
        if (mNetworkStateListenerAdded && propertyChangeListener != null) {
            log.debug("removeListener: networkState.removePropertyChangeListener");
            networkState.removePropertyChangeListener(propertyChangeListener);
            mNetworkStateListenerAdded = false;
        }
    }

}
