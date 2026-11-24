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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent SQLite storage for pending SIMKL watch status actions.
 * Guarantees that actions taken offline survive application restarts,
 * are isolated per user account, and can be retried independently from
 * other watch trackers (e.g., Trakt).
 */
public class SimklSyncQueue extends SQLiteOpenHelper {

    private static final Logger log = LoggerFactory.getLogger(SimklSyncQueue.class);

    private static final String DATABASE_NAME = "simkl_sync.db";
    private static final int DATABASE_VERSION = 3;

    public static final String TABLE_PENDING = "pending_actions";

    public static final String COL_ID = "_id";
    public static final String COL_ACCOUNT = "account";         // SIMKL username or account identifier
    public static final String COL_ITEM_TYPE = "item_type";     // "movie", "show", "season", "episode"
    public static final String COL_TMDB_ID = "tmdb_id";
    public static final String COL_SEASON = "season";           // -1 for movies/shows
    public static final String COL_EPISODES = "episodes";       // comma-separated or single episode
    public static final String COL_WATCHED = "watched";         // 1: watched, 0: unwatched
    public static final String COL_TIMESTAMP = "timestamp";

    public static final String TYPE_MOVIE = "movie";
    public static final String TYPE_SHOW = "show";
    public static final String TYPE_SEASON = "season";
    public static final String TYPE_EPISODE = "episode";

    private static SimklSyncQueue sInstance;

    public static class PendingAction {
        public long id;
        public String account;
        public String itemType;
        public long tmdbId;
        public int season;
        public String episodes;
        public boolean watched;
        public long timestamp;

        public PendingAction(long id, String account, String itemType, long tmdbId, int season, String episodes, boolean watched, long timestamp) {
            this.id = id;
            this.account = account;
            this.itemType = itemType;
            this.tmdbId = tmdbId;
            this.season = season;
            this.episodes = episodes;
            this.watched = watched;
            this.timestamp = timestamp;
        }
    }

    public static synchronized SimklSyncQueue getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new SimklSyncQueue(context.getApplicationContext());
        }
        return sInstance;
    }

    private SimklSyncQueue(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_PENDING + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_ACCOUNT + " TEXT NOT NULL, "
                + COL_ITEM_TYPE + " TEXT NOT NULL, "
                + COL_TMDB_ID + " INTEGER NOT NULL, "
                + COL_SEASON + " INTEGER DEFAULT -1, "
                + COL_EPISODES + " TEXT NOT NULL DEFAULT '', "
                + COL_WATCHED + " INTEGER NOT NULL, "
                + COL_TIMESTAMP + " INTEGER NOT NULL, "
                + "UNIQUE(" + COL_ACCOUNT + ", " + COL_ITEM_TYPE + ", " + COL_TMDB_ID + ", " + COL_SEASON + ", " + COL_EPISODES + ")"
                + ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_PENDING + " ADD COLUMN " + COL_ACCOUNT + " TEXT NOT NULL DEFAULT '';");
            } catch (Exception e) {
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_PENDING);
                onCreate(db);
                return;
            }
        }
        if (oldVersion < 3) {
            // Deduplicate keeping the latest timestamp per (account, item_type, tmdb_id, season, episodes)
            // before enforcing the UNIQUE constraint.
            try {
                db.execSQL("UPDATE " + TABLE_PENDING + " SET " + COL_EPISODES + " = '' WHERE " + COL_EPISODES + " IS NULL;");
                db.execSQL("DELETE FROM " + TABLE_PENDING + " WHERE " + COL_ID + " NOT IN ("
                        + "SELECT MAX(" + COL_ID + ") FROM " + TABLE_PENDING
                        + " GROUP BY " + COL_ACCOUNT + ", " + COL_ITEM_TYPE + ", " + COL_TMDB_ID + ", " + COL_SEASON + ", " + COL_EPISODES + ");");
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_pending_unique ON " + TABLE_PENDING
                        + " (" + COL_ACCOUNT + ", " + COL_ITEM_TYPE + ", " + COL_TMDB_ID + ", " + COL_SEASON + ", " + COL_EPISODES + ");");
            } catch (Exception e) {
                log.error("onUpgrade v3 failed, recreating table", e);
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_PENDING);
                onCreate(db);
            }
        }
    }

    private static String normalizeAccount(String account) {
        if (account == null) return null;
        String trimmed = account.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Enqueue (or replace) a pending action isolated by account with an atomic SQLite transaction.
     * An older timestamp will not overwrite a newer queued action for the same item key.
     */
    private synchronized void upsertAction(String account, String itemType, long tmdbId, int season, String episodes, boolean watched, long timestamp) {
        final String acc = normalizeAccount(account);
        if (acc == null || tmdbId <= 0) return;
        final String epVal = episodes != null ? episodes : "";
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            // Do not overwrite a newer action for the same key.
            try (Cursor c = db.query(TABLE_PENDING, new String[]{COL_TIMESTAMP},
                    COL_ACCOUNT + "=? AND " + COL_ITEM_TYPE + "=? AND " + COL_TMDB_ID + "=? AND " + COL_SEASON + "=? AND " + COL_EPISODES + "=?",
                    new String[]{acc, itemType, String.valueOf(tmdbId), String.valueOf(season), epVal},
                    null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    long existingTs = c.getLong(0);
                    if (existingTs > timestamp) {
                        db.setTransactionSuccessful();
                        return;
                    }
                }
            }

            ContentValues values = new ContentValues();
            values.put(COL_ACCOUNT, acc);
            values.put(COL_ITEM_TYPE, itemType);
            values.put(COL_TMDB_ID, tmdbId);
            values.put(COL_SEASON, season);
            values.put(COL_EPISODES, epVal);
            values.put(COL_WATCHED, watched ? 1 : 0);
            values.put(COL_TIMESTAMP, timestamp);
            long rowId = db.insertWithOnConflict(TABLE_PENDING, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            if (rowId != -1) {
                db.setTransactionSuccessful();
                if (log.isDebugEnabled()) log.debug("upsertAction: queued account={}, type={}, tmdbId={}, season={}, eps={}, watched={}, ts={}",
                        acc, itemType, tmdbId, season, epVal, watched, timestamp);
            } else {
                log.error("upsertAction: insert failed, transaction rolled back for tmdbId={}", tmdbId);
            }
        } catch (Exception e) {
            log.error("upsertAction failed", e);
        } finally {
            db.endTransaction();
        }
    }

    public synchronized void enqueueMovie(String account, long tmdbId, boolean watched) {
        enqueueMovie(account, tmdbId, watched, System.currentTimeMillis());
    }

    public synchronized void enqueueMovie(String account, long tmdbId, boolean watched, long timestamp) {
        upsertAction(account, TYPE_MOVIE, tmdbId, -1, "", watched, timestamp);
    }

    public synchronized void enqueueShow(String account, long showTmdbId, boolean watched) {
        enqueueShow(account, showTmdbId, watched, System.currentTimeMillis());
    }

    public synchronized void enqueueShow(String account, long showTmdbId, boolean watched, long timestamp) {
        upsertAction(account, TYPE_SHOW, showTmdbId, -1, "", watched, timestamp);
    }

    public synchronized void enqueueSeasonEpisodes(String account, long showTmdbId, int seasonNumber, String episodesCommaSeparated, boolean watched) {
        enqueueSeasonEpisodes(account, showTmdbId, seasonNumber, episodesCommaSeparated, watched, System.currentTimeMillis());
    }

    public synchronized void enqueueSeasonEpisodes(String account, long showTmdbId, int seasonNumber, String episodesCommaSeparated, boolean watched, long timestamp) {
        if (seasonNumber < 0) return;
        upsertAction(account, TYPE_SEASON, showTmdbId, seasonNumber, episodesCommaSeparated, watched, timestamp);
    }

    public synchronized void enqueueEpisode(String account, long showTmdbId, int seasonNumber, int episodeNumber, boolean watched) {
        enqueueEpisode(account, showTmdbId, seasonNumber, episodeNumber, watched, System.currentTimeMillis());
    }

    public synchronized void enqueueEpisode(String account, long showTmdbId, int seasonNumber, int episodeNumber, boolean watched, long timestamp) {
        if (seasonNumber < 0 || episodeNumber < 0) return;
        upsertAction(account, TYPE_EPISODE, showTmdbId, seasonNumber, String.valueOf(episodeNumber), watched, timestamp);
    }

    /**
     * Retrieve pending actions for a specific account ordered chronologically.
     */
    public synchronized List<PendingAction> getPendingActionsForAccount(String account) {
        List<PendingAction> list = new ArrayList<>();
        final String acc = normalizeAccount(account);
        if (acc == null) return list;
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_PENDING, null, COL_ACCOUNT + "=?", new String[]{acc}, null, null, COL_TIMESTAMP + " ASC");
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID));
                    String itemAccount = cursor.getString(cursor.getColumnIndexOrThrow(COL_ACCOUNT));
                    String itemType = cursor.getString(cursor.getColumnIndexOrThrow(COL_ITEM_TYPE));
                    long tmdbId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TMDB_ID));
                    int season = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SEASON));
                    String episodes = cursor.getString(cursor.getColumnIndexOrThrow(COL_EPISODES));
                    boolean watched = cursor.getInt(cursor.getColumnIndexOrThrow(COL_WATCHED)) == 1;
                    long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP));

                    list.add(new PendingAction(id, itemAccount, itemType, tmdbId, season, episodes, watched, timestamp));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            log.error("getPendingActionsForAccount failed", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    /**
     * Remove an action after successful sync with SIMKL.
     */
    public synchronized void removeAction(long id) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            db.delete(TABLE_PENDING, COL_ID + "=?", new String[]{String.valueOf(id)});
        } catch (Exception e) {
            log.error("removeAction failed id=" + id, e);
        }
    }

    /**
     * Remove a flushed action only if its timestamp has not been superseded by a newer action.
     */
    public synchronized void removeActionIfUnchanged(long id, long expectedTimestamp) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            db.delete(TABLE_PENDING, COL_ID + "=? AND " + COL_TIMESTAMP + "=?",
                    new String[]{String.valueOf(id), String.valueOf(expectedTimestamp)});
        } catch (Exception e) {
            log.error("removeActionIfUnchanged failed id=" + id, e);
        }
    }

    /**
     * Remove any queued action for the same item key whose timestamp is older than or equal to maxTimestamp.
     * Used when a newer online action succeeds directly so stale offline actions are never re-sent.
     */
    public synchronized void removeActionIfOlderOrEqual(String account, String itemType, long tmdbId, int season, String episodes, long maxTimestamp) {
        final String acc = normalizeAccount(account);
        if (acc == null || tmdbId <= 0) return;
        final String epVal = episodes != null ? episodes : "";
        try {
            SQLiteDatabase db = getWritableDatabase();
            db.delete(TABLE_PENDING,
                    COL_ACCOUNT + "=? AND " + COL_ITEM_TYPE + "=? AND " + COL_TMDB_ID + "=? AND " + COL_SEASON + "=? AND " + COL_EPISODES + "=? AND " + COL_TIMESTAMP + "<=?",
                    new String[]{acc, itemType, String.valueOf(tmdbId), String.valueOf(season), epVal, String.valueOf(maxTimestamp)});
        } catch (Exception e) {
            log.error("removeActionIfOlderOrEqual failed", e);
        }
    }

    /**
     * Remove any queued episode or season actions for a show whose timestamp is older than or equal to maxTimestamp.
     */
    public synchronized void removeShowEpisodesIfOlderOrEqual(String account, long showTmdbId, long maxTimestamp) {
        final String acc = normalizeAccount(account);
        if (acc == null || showTmdbId <= 0) return;
        try {
            SQLiteDatabase db = getWritableDatabase();
            db.delete(TABLE_PENDING,
                    COL_ACCOUNT + "=? AND (" + COL_ITEM_TYPE + "=? OR " + COL_ITEM_TYPE + "=?) AND " + COL_TMDB_ID + "=? AND " + COL_TIMESTAMP + "<=?",
                    new String[]{acc, TYPE_EPISODE, TYPE_SEASON, String.valueOf(showTmdbId), String.valueOf(maxTimestamp)});
        } catch (Exception e) {
            log.error("removeShowEpisodesIfOlderOrEqual failed", e);
        }
    }

    /**
     * Reassign pending actions from a temporary pending account key to the resolved username.
     */
    public synchronized void reassignAccount(String fromAccount, String toAccount) {
        final String from = normalizeAccount(fromAccount);
        final String to = normalizeAccount(toAccount);
        if (from == null || to == null || from.equals(to)) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            try (Cursor c = db.query(TABLE_PENDING, null, COL_ACCOUNT + "=?", new String[]{from}, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    do {
                        String itemType = c.getString(c.getColumnIndexOrThrow(COL_ITEM_TYPE));
                        long tmdbId = c.getLong(c.getColumnIndexOrThrow(COL_TMDB_ID));
                        int season = c.getInt(c.getColumnIndexOrThrow(COL_SEASON));
                        String episodes = c.getString(c.getColumnIndexOrThrow(COL_EPISODES));
                        boolean watched = c.getInt(c.getColumnIndexOrThrow(COL_WATCHED)) == 1;
                        long ts = c.getLong(c.getColumnIndexOrThrow(COL_TIMESTAMP));
                        upsertAction(to, itemType, tmdbId, season, episodes, watched, ts);
                    } while (c.moveToNext());
                }
            }
            db.delete(TABLE_PENDING, COL_ACCOUNT + "=?", new String[]{from});
            db.setTransactionSuccessful();
        } catch (Exception e) {
            log.error("reassignAccount failed", e);
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Clear pending actions for a specific account (e.g. on user wipe/logout).
     */
    public synchronized void clearAccount(String account) {
        try {
            String acc = normalizeAccount(account);
            if (acc == null) return;
            SQLiteDatabase db = getWritableDatabase();
            db.delete(TABLE_PENDING, COL_ACCOUNT + "=?", new String[]{acc});
        } catch (Exception e) {
            log.error("clearAccount failed", e);
        }
    }

    /**
     * Clear all pending actions.
     */
    public synchronized void clear() {
        try {
            SQLiteDatabase db = getWritableDatabase();
            db.delete(TABLE_PENDING, null, null);
        } catch (Exception e) {
            log.error("clear failed", e);
        }
    }
}
