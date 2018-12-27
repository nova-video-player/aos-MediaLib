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

package com.archos.mediaprovider.music;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.archos.mediaprovider.ArchosMediaCommon;
import com.archos.mediaprovider.CustomCursorFactory;
import com.archos.mediaprovider.SQLiteUtils;
import com.archos.mediaprovider.DeleteOnDowngradeSQLiteOpenHelper;

/**
 * Creates the music database
 */
public class MusicOpenHelper extends DeleteOnDowngradeSQLiteOpenHelper {
    private static final String TAG = ArchosMediaCommon.TAG_PREFIX + "MusicOpenHelper";
    private static final boolean LOCAL_DBG = false;
    private static final boolean DBG = ArchosMediaCommon.PACKAGE_DBG & LOCAL_DBG;

    // that is what onCreate creates
    private static final int DATABASE_CREATE_VERSION = 10;
    // that is the current version
    private static final int DATABASE_VERSION = 17;
    private static final String DATABASE_NAME = "media.db";

    // (Integer.MAX_VALUE / 2) rounded to human readable form
    /* package */ static final long SCANNED_ID_OFFSET = ArchosMediaCommon.SCANNED_ID_OFFSET;

    /* ---------------------------------------------------------------------- */
    /* --                 GENERAL files database part                         */
    /* ---------------------------------------------------------------------- */

    // files table defined later
    public static final String FILES_TABLE_NAME = "files";

    // ------------- ---##[ Imported Files       ]## ---------------------------
    // files_import table holds the imported data, but updates data in files table
    public static final String FILES_IMPORT_TABLE_NAME = "files_import";
    private static final String CREATE_FILES_IMPORT_TABLE =
            "CREATE TABLE " + FILES_IMPORT_TABLE_NAME + " (\n" +
            "    local_id            INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
            "    inserted            INTEGER DEFAULT ( strftime( '%s', 'now' )  ),\n" +
            // ON CONFLICT REPLACE is the magic that allows to simply overwrite data
            "    _id                 INTEGER UNIQUE ON CONFLICT REPLACE,\n" +
            "    _data               TEXT,\n" +
            "    _display_name       TEXT,\n" +
            "    _size               INTEGER,\n" +
            "    date_added          INTEGER,\n" +
            "    date_modified       INTEGER,\n" +
            "    bucket_id           TEXT,\n" +
            "    bucket_display_name TEXT,\n" +
            "    format              INTEGER,\n" +
            "    parent              INTEGER,\n" +
            "    storage_id          INTEGER \n" +
            ")";
    // trigger to insert + update the corresponding entry in files after inserting
    // or replacing data in files_import
    private static final String CREATE_FILES_IMPORT_TRIGGER_INSERT =
            "CREATE TRIGGER IF NOT EXISTS after_insert_files_import " +
            "AFTER INSERT ON " + FILES_IMPORT_TABLE_NAME + " " +
            "BEGIN "+
                // insert or ignore so entry exists
                "INSERT INTO " + FILES_TABLE_NAME + "(remote_id) VALUES (NEW._id);" +
                // then update inserted data in files table
                "UPDATE " + FILES_TABLE_NAME + " SET\n" +
                "_id = NEW._id , \n" +
                "_data = NEW._data , \n" +
                "_display_name = NEW._display_name , \n" +
                "_size = NEW._size , \n" +
                "date_added = NEW.date_added , \n" +
                "date_modified = NEW.date_modified , \n" +
                "bucket_id = NEW.bucket_id , \n" +
                "bucket_display_name = NEW.bucket_display_name , \n" +
                "format = NEW.format , \n" +
                "parent = NEW.parent , \n" +
                "storage_id = NEW.storage_id , \n" +
                "Archos_smbserver = 0\n" +
                "WHERE remote_id=NEW._id;" +
             "END";
    // trigger to delete from files table if the corresponding id was deleted in files_import
    private static final String CREATE_FILES_IMPORT_TRIGGER_DELETE =
            "CREATE TRIGGER IF NOT EXISTS after_delete_files_import " +
            "AFTER DELETE ON " + FILES_IMPORT_TABLE_NAME + " " +
            "BEGIN "+
                "DELETE FROM " + FILES_TABLE_NAME + " WHERE remote_id=OLD._id; "+
             "END";
    // ------------- ---##[ Scanned Files (SMB)  ]## ---------------------------
    // files_scanned holds data for network scanned files, but updates data in files table
    public static final String FILES_SCANNED_TABLE_NAME = "files_scanned";
    private static final String CREATE_FILES_SCANNED_TABLE =
            "CREATE TABLE " + FILES_SCANNED_TABLE_NAME + " (\n" +
            // debugging
            "    inserted            INTEGER DEFAULT ( strftime( '%s', 'now' ) ),\n" +
            // data, _id + magic number used to avoid conflicts in files table
            "    _id                 INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
            "    _data               TEXT    UNIQUE,\n" +
            "    _display_name       TEXT,\n" +
            "    _size               INTEGER,\n" +
            "    date_added          INTEGER DEFAULT ( strftime( '%s', 'now' )  ),\n" +
            "    date_modified       INTEGER,\n" +
            "    mime_type           TEXT,\n" + // ++
            "    title               TEXT,\n" + // ++
            "    media_type          INTEGER,\n" + // ++
            "    bucket_id           TEXT,\n" +
            "    bucket_display_name TEXT,\n" +
            "    format              INTEGER,\n" +
            "    parent              INTEGER DEFAULT ( -1 ),\n" +
            "    storage_id          INTEGER,\n" +
            "    Archos_smbserver    INTEGER DEFAULT ( 0 ) \n" +
            ")";
    // V16 drops the UNIQUE contraint on _data
    private static final String CREATE_FILES_SCANNED_TABLE_V16 =
            "CREATE TABLE " + FILES_SCANNED_TABLE_NAME + " (\n" +
            // debugging
            "    inserted            INTEGER DEFAULT ( strftime( '%s', 'now' ) ),\n" +
            // data, _id + magic number used to avoid conflicts in files table
            "    _id                 INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
            "    _data               TEXT,\n" +
            "    _display_name       TEXT,\n" +
            "    _size               INTEGER,\n" +
            "    date_added          INTEGER DEFAULT ( strftime( '%s', 'now' )  ),\n" +
            "    date_modified       INTEGER,\n" +
            "    mime_type           TEXT,\n" + // ++
            "    title               TEXT,\n" + // ++
            "    media_type          INTEGER,\n" + // ++
            "    bucket_id           TEXT,\n" +
            "    bucket_display_name TEXT,\n" +
            "    format              INTEGER,\n" +
            "    parent              INTEGER DEFAULT ( -1 ),\n" +
            "    storage_id          INTEGER,\n" +
            "    Archos_smbserver    INTEGER DEFAULT ( 0 ) \n" +
            ")";
    // trigger to insert + update the corresponding entry in files after inserting
    // or replacing data in files_scanned
    private static final String CREATE_FILES_SCANNED_TRIGGER_INSERT =
            "CREATE TRIGGER IF NOT EXISTS after_insert_files_scanned " +
            "AFTER INSERT ON " + FILES_SCANNED_TABLE_NAME + " " +
            "BEGIN " +
                // new entry with id + magic number, does nothing if entry exists
                "INSERT INTO " + FILES_TABLE_NAME + "(remote_id) VALUES (NEW._id + " + SCANNED_ID_OFFSET + ");" +
                // update files with data just inserted
                "UPDATE " + FILES_TABLE_NAME + " SET\n" +
                "_id = (NEW._id + " + SCANNED_ID_OFFSET + "), \n" +
                "_data = NEW._data , \n" +
                "_display_name = NEW._display_name , \n" +
                "_size = NEW._size , \n" +
                "date_added = NEW.date_added , \n" +
                "date_modified = NEW.date_modified , \n" +
                "mime_type = NEW.mime_type , \n" +
                "title = NEW.title , \n" +
                "media_type = NEW.media_type , \n" +
                "bucket_id = NEW.bucket_id , \n" +
                "bucket_display_name = NEW.bucket_display_name , \n" +
                "format = NEW.format , \n" +
                "parent = NEW.parent , \n" +
                "storage_id = NEW.storage_id , \n" +
                "Archos_smbserver = NEW.Archos_smbserver\n" +
                "WHERE remote_id=(NEW._id + " + SCANNED_ID_OFFSET + ");" +
            "END";
    // trigger to delete from files_extra if the corresponding id was deleted in files_scanned
    private static final String CREATE_FILES_SCANNED_TRIGGER_DELETE =
            "CREATE TRIGGER IF NOT EXISTS after_delete_files_scanned " +
            "AFTER DELETE ON " + FILES_SCANNED_TABLE_NAME + " " +
            "BEGIN " +
                "DELETE FROM " + FILES_TABLE_NAME + " WHERE remote_id=(OLD._id + " + SCANNED_ID_OFFSET + ");" +
            "END";


    // ------------- ---##[ All Files            ]## ---------------------------
    // files table holds all data that for each file that
    // is determined after scanning / importing
    private static final String CREATE_FILES_TABLE =
            "CREATE TABLE " + FILES_TABLE_NAME + " ( \n" +
            // for debugging purposes mainly
            "    local_id                        INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
            "    inserted                        INTEGER DEFAULT ( strftime( '%s', 'now' ) ),\n" +
            // updated if inserted into files_import / files_scanned
            "    _id                             INTEGER UNIQUE,\n" +
            "    _data                           TEXT    UNIQUE,\n" +
            "    _display_name                   TEXT,\n" +
            "    title                           TEXT,\n" +
            "    _size                           INTEGER,\n" +
            "    date_added                      INTEGER,\n" +
            "    date_modified                   INTEGER,\n" +
            "    bucket_id                       TEXT,\n" +
            "    bucket_display_name             TEXT,\n" +
            "    format                          INTEGER,\n" +
            "    parent                          INTEGER DEFAULT ( -1 ),\n" +
            "    storage_id                      INTEGER,\n" +
            "    Archos_smbserver                INTEGER DEFAULT ( 0 ), \n" +
            // updated from scanner etc
            // ON CONFLICT IGNORE - inserts from files_scanned / _imported only once
            "    remote_id                       INTEGER UNIQUE ON CONFLICT IGNORE,\n" +
            "    scan_state                      INTEGER DEFAULT ( 0 ),\n" +
            "    mime_type                       TEXT,\n" +
            "    media_type                      INTEGER,\n" +
            "    is_drm                          INTEGER,\n" +
            "    title_key                       TEXT,\n" +
            "    artist_id                       INTEGER,\n" +
            "    composer                        TEXT,\n" +
            "    album_id                        INTEGER,\n" +
            "    artist                          TEXT,\n" +
            "    album                           TEXT,\n" +
            "    track                           INTEGER,\n" +
            "    number_of_tracks                INTEGER DEFAULT 1,\n" +
            "    year                            INTEGER CHECK ( year != 0 ),\n" +
            "    is_ringtone                     INTEGER,\n" +
            "    is_music                        INTEGER,\n" +
            "    is_alarm                        INTEGER,\n" +
            "    is_notification                 INTEGER,\n" +
            "    is_podcast                      INTEGER,\n" +
            "    album_artist                    TEXT,\n" +
            "    duration                        INTEGER,\n" +
            "    width                           INTEGER,\n" +
            "    height                          INTEGER,\n" +
            "    mini_thumb_data                 TEXT,\n" +
            "    mini_thumb_magic                INTEGER,\n" +
            "    bookmark                        INTEGER,\n" +
            "    Archos_favorite_track           INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_bookmark                 INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_lastTimePlayed           INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_playerParams             INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_playerSubtitleDelay      INTEGER DEFAULT ( 0 ),\n" +
            "    ArchosMediaScraper_id           INTEGER DEFAULT ( 0 ),\n" +
            "    ArchosMediaScraper_type         INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_numberOfSubtitleTracks   INTEGER DEFAULT ( -1 ),\n" +
            "    Archos_numberOfAudioTracks      INTEGER DEFAULT ( -1 ),\n" +
            "    Archos_sampleRate               INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_numberOfChannels         INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_audioWaveCodec           INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_audioBitRate             INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_videoFourCCCodec         INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_videoBitRate             INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_framesPerThousandSeconds INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_encodingProfile          TEXT    DEFAULT ( NULL ),\n" +
            "    Archos_playerSubtitleRatio      INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_thumbTry                 INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_hideFile                 INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_title                    TEXT    DEFAULT ( NULL ) \n" +
            ")";
    private static final String CREATE_FILES_TABLE_V16 =
            "CREATE TABLE " + FILES_TABLE_NAME + " ( \n" +
            "    local_id                        INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
            "    inserted                        INTEGER DEFAULT ( strftime( '%s', 'now' ) ),\n" +
            // _id has now ON CONFLICT REPLACE
            "    _id                             INTEGER UNIQUE ON CONFLICT REPLACE,\n" +
            // _data is no longer UNIQUE
            "    _data                           TEXT,\n" +
            "    _display_name                   TEXT,\n" +
            "    title                           TEXT,\n" +
            "    _size                           INTEGER,\n" +
            "    date_added                      INTEGER,\n" +
            "    date_modified                   INTEGER,\n" +
            "    bucket_id                       TEXT,\n" +
            "    bucket_display_name             TEXT,\n" +
            "    format                          INTEGER,\n" +
            "    parent                          INTEGER DEFAULT ( -1 ),\n" +
            "    storage_id                      INTEGER,\n" +
            "    Archos_smbserver                INTEGER DEFAULT ( 0 ), \n" +
            "    remote_id                       INTEGER UNIQUE ON CONFLICT IGNORE,\n" +
            "    scan_state                      INTEGER DEFAULT ( 0 ),\n" +
            "    mime_type                       TEXT,\n" +
            "    media_type                      INTEGER,\n" +
            "    is_drm                          INTEGER,\n" +
            "    title_key                       TEXT,\n" +
            "    artist_id                       INTEGER,\n" +
            "    composer                        TEXT,\n" +
            "    album_id                        INTEGER,\n" +
            "    artist                          TEXT,\n" +
            "    album                           TEXT,\n" +
            "    track                           INTEGER,\n" +
            "    number_of_tracks                INTEGER DEFAULT 1,\n" +
            // stupid CHECK ( year != 0 ) is gone for year
            "    year                            INTEGER,\n" +
            "    is_ringtone                     INTEGER,\n" +
            "    is_music                        INTEGER,\n" +
            "    is_alarm                        INTEGER,\n" +
            "    is_notification                 INTEGER,\n" +
            "    is_podcast                      INTEGER,\n" +
            "    album_artist                    TEXT,\n" +
            "    duration                        INTEGER,\n" +
            "    width                           INTEGER,\n" +
            "    height                          INTEGER,\n" +
            "    mini_thumb_data                 TEXT,\n" +
            "    mini_thumb_magic                INTEGER,\n" +
            "    bookmark                        INTEGER,\n" +
            "    Archos_favorite_track           INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_bookmark                 INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_lastTimePlayed           INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_playerParams             INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_playerSubtitleDelay      INTEGER DEFAULT ( 0 ),\n" +
            "    ArchosMediaScraper_id           INTEGER DEFAULT ( 0 ),\n" +
            "    ArchosMediaScraper_type         INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_numberOfSubtitleTracks   INTEGER DEFAULT ( -1 ),\n" +
            "    Archos_numberOfAudioTracks      INTEGER DEFAULT ( -1 ),\n" +
            "    Archos_sampleRate               INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_numberOfChannels         INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_audioWaveCodec           INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_audioBitRate             INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_videoFourCCCodec         INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_videoBitRate             INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_framesPerThousandSeconds INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_encodingProfile          TEXT    DEFAULT ( NULL ),\n" +
            "    Archos_playerSubtitleRatio      INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_thumbTry                 INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_hideFile                 INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_title                    TEXT    DEFAULT ( NULL ) \n" +
            ")";
    // trigger to delete genre / playlists
    private static final String CREATE_FILES_TRIGGER_AUDIO_CLEANUP =
            "CREATE TRIGGER audio_meta_cleanup\n" +
            "        DELETE ON " + FILES_TABLE_NAME + "\n" +
            "       WHEN old.media_type = 2\n" +
            "BEGIN\n" +
            "    DELETE\n" +
            "      FROM audio_genres_map\n" +
            "     WHERE audio_id = old.remote_id;\n" +
            "    DELETE\n" +
            "      FROM audio_playlists_map\n" +
            "     WHERE audio_id = old.remote_id;\n" +
            "END";
    // indices TODO: test how these affect performance, just copied from android db.
    private static final String CREATE_FILES_IDX_ALBUM_ID =
            "CREATE INDEX album_id_idx ON " + FILES_TABLE_NAME + " (album_id)";
    private static final String CREATE_FILES_IDX_ARTIST_ID =
            "CREATE INDEX artist_id_idx ON " + FILES_TABLE_NAME + " (artist_id)";
    private static final String CREATE_FILES_IDX_MEDIA_TYPE =
            "CREATE INDEX media_type_index ON " + FILES_TABLE_NAME + " (media_type)";
    private static final String CREATE_FILES_IDX_TITLE =
            "CREATE INDEX title_idx ON " + FILES_TABLE_NAME + " (title)";
    private static final String CREATE_FILES_IDX_TITLE_KEY =
            "CREATE INDEX titlekey_index ON " + FILES_TABLE_NAME + " (title_key)";
    private static final String CREATE_FILES_IDX_PARENT =
            "CREATE INDEX parent_index ON " + FILES_TABLE_NAME + " (parent)";
    private static final String CREATE_FILES_IDX_BUCKET_NAME =
            "CREATE INDEX bucket_name ON " + FILES_TABLE_NAME + " (bucket_id, media_type, bucket_display_name)";
    private static final String CREATE_FILES_IDX_BUCKET_INDEX =
            "CREATE INDEX bucket_index ON " + FILES_TABLE_NAME + " (bucket_id, media_type" /*TODO ?? + ", datetaken"*/ + ", _id)";
    private static final String CREATE_FILES_IDX_PATH =
            "CREATE INDEX path_index ON " + FILES_TABLE_NAME + "(_data)";

    // ------------- ---##[ SMB Server mechanism ]## ---------------------------
    // smb_server table holds server identifier and active state
    // used to hide content from servers that are inactive
    public static final String SMB_SERVER_TABLE_NAME = "smb_server";
    private static final String CREATE_SMB_SERVER_TABLE =
            "CREATE TABLE " + SMB_SERVER_TABLE_NAME + "(" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL DEFAULT 1," +
                    "_data TEXT UNIQUE NOT NULL," +
                    "last_seen INTEGER NOT NULL DEFAULT 0," +
                    "active INTEGER NOT NULL DEFAULT 0" +
                    ")";

    // smb_server_acitve view shows ids of active servers
    public static final String SMB_SERVER_ACTIVE_VIEW_NAME = "smb_server_acitve";
    private static final String CREATE_SMB_SERVER_ACTIVE_VIEW =
            "CREATE VIEW " + SMB_SERVER_ACTIVE_VIEW_NAME + " AS " +
            "SELECT _id AS Archos_smbserver FROM smb_server WHERE active != 0";

    /* ---------------------------------------------------------------------- */
    /* --               STUFF TO DROP FROM VIDEO/SCRAPER DB                   */
    /* ---------------------------------------------------------------------- */

    public static String[] DROP_TABLES = {
        "actor",
        "belongs_movie",
        "belongs_show",
        "director",
        "episode",
        "films_episode",
        "films_movie",
        "films_show",
        "genre",
        "guests",
        "movie",
        "movie_backdrops",
        "movie_posters",
        "plays_movie",
        "plays_show",
        "produces_movie",
        "produces_show",
        "show",
        "show_backdrops",
        "show_posters",
        "studio",
        "videothumbnails",
    };
    public static String[] DROP_INDEXES = {
        "format_index",
    };
    public static String[] DROP_TRIGGERS = {
        "scraper_episode_cleanup",
        "scraper_movie_cleanup",
        "vob_update_import",
        "vob_delete_import",
        "vob_insert_import",
    };
    public static String[] DROP_VIEWS = {
        "V_BELONGS_MOVIE",
        "V_BELONGS_SHOW",
        "V_FILMS_EPISODE",
        "V_FILMS_MOVIE",
        "V_FILMS_SHOW",
        "V_GUESTS",
        "V_PLAYS_MOVIE",
        "V_PLAYS_SHOW",
        "V_PRODUCES_MOVIE",
        "V_PRODUCES_SHOW",
        "v_actor_deletable",
        "v_all_videos",
        "v_director_deletable",
        "v_episode_actors",
        "v_episode_directors",
        "v_genre_deletable",
        "v_movie_actors",
        "v_movie_directors",
        "v_movie_genres",
        "v_movie_studios",
        "v_seasons",
        "v_show_actors",
        "v_show_directors",
        "v_show_genres",
        "v_show_studios",
        "v_studio_deletable",
        "video",
    };
    public static void dropOldStuff(SQLiteDatabase db) {
        for (String drop : DROP_TABLES) {
            SQLiteUtils.dropTable(db, drop);
        }
        for (String drop : DROP_INDEXES) {
            SQLiteUtils.dropIndex(db, drop);
        }
        for (String drop : DROP_TRIGGERS) {
            SQLiteUtils.dropTrigger(db, drop);
        }
        for (String drop : DROP_VIEWS) {
            SQLiteUtils.dropView(db, drop);
        }
    }

    /* ---------------------------------------------------------------------- */
    /* --                       AUDIO database                                */
    /* ---------------------------------------------------------------------- */

    // ------------- ---##[ Audio Files          ]## ---------------------------
    public static final String AUDIO_META_VIEW_NAME = "audio_meta";
    private static final String CREATE_AUDIO_META_VIEW_V0 =
    "CREATE VIEW " + AUDIO_META_VIEW_NAME + " AS\n" +
    "SELECT _id,\n" +
    "       _data,\n" +
    "       _display_name,\n" +
    "       _size,\n" +
    "       mime_type,\n" +
    "       date_added,\n" +
    "       is_drm,\n" +
    "       date_modified,\n" +
    "       title,\n" +
    "       title_key,\n" +
    "       duration,\n" +
    "       artist_id,\n" +
    "       composer,\n" +
    "       album_id,\n" +
    "       track,\n" +
    "       number_of_tracks,\n" +
    "       year,\n" +
    "       is_ringtone,\n" +
    "       is_music,\n" +
    "       is_alarm,\n" +
    "       is_notification,\n" +
    "       is_podcast,\n" +
    "       bookmark,\n" +
    "       album_artist,\n" +
    "       Archos_favorite_track,\n" +
    "       Archos_bookmark,\n" +
    "       Archos_lastTimePlayed,\n" +
    "       Archos_sampleRate,\n" +
    "       Archos_numberOfChannels,\n" +
    "       Archos_audioBitRate,\n" +
    "       Archos_audioWaveCodec\n" +
    "  FROM " + FILES_TABLE_NAME +
    " WHERE media_type = 2 \n" +
    "       AND\n" +
    "        ( Archos_smbserver = 0 \n" +
    "           OR\n" +
    "       Archos_smbserver IN smb_server_acitve )";

    private static final String CREATE_AUDIO_META_VIEW_V14 =
    "CREATE VIEW " + AUDIO_META_VIEW_NAME + " AS\n" +
    "SELECT _id,\n" +
    "       _data,\n" +
    "       _display_name,\n" +
    "       _size,\n" +
    "       mime_type,\n" +
    "       date_added,\n" +
    "       is_drm,\n" +
    "       date_modified,\n" +
    "       title,\n" +
    "       title_key,\n" +
    "       duration,\n" +
    "       artist_id,\n" +
    "       composer,\n" +
    "       album_id,\n" +
    "       track,\n" +
    "       number_of_tracks,\n" +
    "       year,\n" +
    "       is_ringtone,\n" +
    "       is_music,\n" +
    "       is_alarm,\n" +
    "       is_notification,\n" +
    "       is_podcast,\n" +
    "       bookmark,\n" +
    "       album_artist,\n" +
    "       Archos_favorite_track,\n" +
    "       Archos_bookmark,\n" +
    "       Archos_lastTimePlayed,\n" +
    "       Archos_sampleRate,\n" +
    "       Archos_numberOfChannels,\n" +
    "       Archos_audioBitRate,\n" +
    "       Archos_audioWaveCodec\n" +
    "  FROM " + FILES_TABLE_NAME +
    " WHERE\n" +
    "    media_type=2 AND\n" +
    "    (Archos_smbserver=0 OR\n" +
    "    Archos_smbserver IN (SELECT _id FROM smb_server WHERE active=1))";

    // ------------- ---##[ Artists              ]## ---------------------------
    public static final String ARTISTS_TABLE_NAME = "artists";
    private static final String CREATE_ARTISTS_TABLE =
            "CREATE TABLE " + ARTISTS_TABLE_NAME + " ( \n" +
            "    artist_id          INTEGER PRIMARY KEY,\n" +
            "    artist_key         TEXT    NOT NULL\n" +
            "                               UNIQUE,\n" +
            "    artist             TEXT    NOT NULL,\n" +
            "    Archos_favorite_artist INTEGER DEFAULT ( 0 ) \n" +
            ")";
    private static final String CREATE_ARTISTS_IDX_ARTIST =
            "CREATE INDEX artist_idx on artists(artist)";
    private static final String CREATE_ARTISTS_IDX_ARTIST_KEY =
            "CREATE INDEX artistkey_index on artists(artist_key)";

    // ------------- ---##[ Albums               ]## ---------------------------
    public static final String ALBUMS_TABLE_NAME = "albums";
    private static final String CREATE_ALBUMS_TABLE =
            "CREATE TABLE " + ALBUMS_TABLE_NAME + " ( \n" +
            "    album_id           INTEGER PRIMARY KEY,\n" +
            "    album_key          TEXT    NOT NULL\n" +
            "                               UNIQUE,\n" +
            "    album              TEXT    NOT NULL,\n" +
            "    Archos_favorite_album INTEGER DEFAULT ( 0 ) \n" +
            ")";
    private static final String CREATE_ALBUMS_TRIGGER_CLEANUP =
            "CREATE TRIGGER albumart_cleanup1 " +
            "DELETE ON albums BEGIN " +
            "DELETE FROM album_art WHERE album_id = old.album_id;" +
            "END";
    private static final String CREATE_ALBUMS_IDX_ALBUM =
            "CREATE INDEX album_idx on albums(album)";
    private static final String CREATE_ALBUMS_IDX_ALBUMKEY =
            "CREATE INDEX albumkey_index on albums(album_key)";

    // ------------- ---##[ Album Art            ]## ---------------------------
    public static final String ALBUM_ART_TABLE_NAME = "album_art";
    private static final String CREATE_ALBUM_ART_TABLE =
            "CREATE TABLE " + ALBUM_ART_TABLE_NAME + " ( \n" +
            "    album_id INTEGER PRIMARY KEY,\n" +
            "    _data    TEXT \n" +
            ")";
    private static final String CREATE_ALBUM_ART_TRIGGER_CLEANUP =
            "CREATE TRIGGER albumart_cleanup2 " +
            "DELETE ON album_art BEGIN " +
            "SELECT _DELETE_FILE_J(old._data);" +
            "END";
    private static final String DROP_ALBUM_ART_TRIGGER_CLEANUP =
            "DROP TRIGGER IF EXISTS albumart_cleanup2";
    // ------------- ---##[ Audio Genres         ]## ---------------------------
    public static final String AUDIO_GENRES_TABLE_NAME = "audio_genres";
    private static final String CREATE_AUDIO_GENRES_TABLE =
            "CREATE TABLE " + AUDIO_GENRES_TABLE_NAME + " ( \n" +
            "    _id  INTEGER PRIMARY KEY,\n" +
            "    name TEXT    NOT NULL \n" +
            ")";
    private static final String CREATE_AUDIO_GENRES_TRIGGER_CLEANUP =
            "CREATE TRIGGER audio_genres_cleanup " +
            "DELETE ON audio_genres BEGIN " +
            "DELETE FROM audio_genres_map WHERE genre_id = old._id;" +
            "END";
    public static final String AUDIO_GENRES_MAP_TABLE_NAME = "audio_genres_map";
    private static final String CREATE_AUDIO_GENRES_MAP_TABLE =
            "CREATE TABLE " + AUDIO_GENRES_MAP_TABLE_NAME + " ( \n" +
            "    _id      INTEGER PRIMARY KEY,\n" +
            "    audio_id INTEGER NOT NULL,\n" +
            "    genre_id INTEGER NOT NULL \n" +
            ")";
    public static final String AUDIO_GENRES_MAP_NOID_VIEW_NAME = "audio_genres_map_noid";
    private static final String CREATE_AUDIO_GENRES_MAP_NOID_VIEW =
            "CREATE VIEW " + AUDIO_GENRES_MAP_NOID_VIEW_NAME + " AS\n" +
            "SELECT audio_id,\n" +
            "       genre_id\n" +
            "  FROM " + AUDIO_GENRES_MAP_TABLE_NAME;
    // ------------- ---##[ Audio Playlists      ]## ---------------------------
    public static final String AUDIO_PLAYLISTS_TABLE_NAME = "audio_playlists";
    public static final String AUDIO_PLAYLISTS_MAP_TABLE_NAME = "audio_playlists_map";

    private static final String CREATE_AUDIO_PLAYLISTS_TABLE =
            "CREATE TABLE " + AUDIO_PLAYLISTS_TABLE_NAME + " ( \n" +
            "    _id           INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
            "    _data         TEXT,\n" +
            "    name          TEXT,\n" +
            "    date_added    INTEGER,\n" +
            "    date_modified INTEGER \n" +
            ")";
    private static final String CREATE_AUDIO_PLAYLISTS_TRIGGER_CLEANUP =
            "CREATE TRIGGER audio_playlists_cleanup " +
            "DELETE ON " + AUDIO_PLAYLISTS_TABLE_NAME + " BEGIN " +
            "DELETE FROM " + AUDIO_PLAYLISTS_MAP_TABLE_NAME + " WHERE playlist_id = old._id;" +
            "END";

    private static final String CREATE_AUDIO_PLAYLISTS_MAP_TABLE =
            "CREATE TABLE " + AUDIO_PLAYLISTS_MAP_TABLE_NAME + " ( \n" +
            "    _id         INTEGER PRIMARY KEY,\n" +
            "    audio_id    INTEGER NOT NULL,\n" +
            "    playlist_id INTEGER NOT NULL,\n" +
            "    play_order  INTEGER NOT NULL \n" +
            ")";
    // ------------- ---##[ Audio views          ]## ---------------------------
    // audio view as in Android's media db - joins album / artist info onto track data
    public static final String AUDIO_VIEW_NAME = "audio";
    private static final String CREATE_AUDIO_VIEW =
            "CREATE VIEW " + AUDIO_VIEW_NAME + " AS\n" +
            "SELECT *\n" +
            "  FROM " + AUDIO_META_VIEW_NAME + "\n" +
            "       NATURAL LEFT JOIN " + ARTISTS_TABLE_NAME + "\n" +
            "       NATURAL LEFT JOIN " + ALBUMS_TABLE_NAME;
    // album_info groups audio info by album
    public static final String ALBUM_INFO_VIEW_NAME = "album_info";
    private static final String CREATE_ALBUM_INFO_VIEW =
            "CREATE VIEW " + ALBUM_INFO_VIEW_NAME + " AS\n" +
            "SELECT audio.album_id AS _id,\n" +
            "      album,\n" +
            "      album_key,\n" +
            "      MIN( year ) AS minyear,\n" +
            "      MAX( year ) AS maxyear,\n" +
            "      artist,\n" +
            "      artist_id,\n" +
            "      artist_key,\n" +
            "      count( * ) AS numsongs,\n" +
            "      album_art._data AS album_art,\n" +
            "      Archos_favorite_album\n" +
            " FROM " + AUDIO_VIEW_NAME + "\n" +
            "      LEFT OUTER JOIN " + ALBUM_ART_TABLE_NAME + "\n" +
            "                   ON audio.album_id = album_art.album_id\n" +
            "WHERE is_music = 1\n" +
            "GROUP BY audio.album_id";
    public static final String ARTIST_INFO_VIEW_NAME = "artist_info";
    private static final String CREATE_ARTIST_INFO_VIEW =
            "CREATE VIEW " + ARTIST_INFO_VIEW_NAME + " AS\n" +
            "SELECT artist_id AS _id,\n" +
            "       artist,\n" +
            "       artist_key,\n" +
            "       COUNT( DISTINCT album_key ) AS number_of_albums,\n" +
            "       COUNT( * ) AS number_of_tracks,\n" +
            "       Archos_favorite_artist\n" +
            "  FROM " + AUDIO_VIEW_NAME + "\n" +
            " WHERE is_music = 1\n" +
            " GROUP BY artist_key";
    public static final String ARTISTS_ALBUMS_MAP_VIEW_NAME = "artists_albums_map";
    private static final String CREATE_ARTISTS_ALBUMS_MAP =
            "CREATE VIEW " + ARTISTS_ALBUMS_MAP_VIEW_NAME + " AS\n" +
            "SELECT DISTINCT artist_id,\n" +
            "                album_id\n" +
            "           FROM " + AUDIO_META_VIEW_NAME;

    // ------------- ---##[ Audio search views   ]## ---------------------------
    public static final String SEARCHHELPERTITLE_VIEW_NAME = "searchhelpertitle";
    private static final String CREATE_SEARCHHELPERTITLE_VIEW =
            "CREATE VIEW " + SEARCHHELPERTITLE_VIEW_NAME + " AS\n" +
            "SELECT *\n" +
            "  FROM " + AUDIO_VIEW_NAME + "\n" +
            " ORDER BY title_key";
    public static final String SEARCH_VIEW_NAME = "search";
    private static final String CREATE_SEARCH_VIEW =
            "CREATE VIEW " + SEARCH_VIEW_NAME + " AS\n" +
            "SELECT _id,\n" +
            "       'artist' AS mime_type,\n" +
            "       artist,\n" +
            "       NULL AS album,\n" +
            "       NULL AS title,\n" +
            "       artist AS text1,\n" +
            "       NULL AS text2,\n" +
            "       number_of_albums AS data1,\n" +
            "       number_of_tracks AS data2,\n" +
            "       artist_key AS [MATCH],\n" +
            "       '" + MusicStore.Audio.Artists.CONTENT_URI_SLASH + "' || _id AS suggest_intent_data,\n" +
            "       1 AS grouporder\n" +
            "  FROM " + ARTIST_INFO_VIEW_NAME + "\n" +
            " WHERE ( artist != '<unknown>' ) \n" +
            "UNION ALL\n" +
            "SELECT _id,\n" +
            "       'album' AS mime_type,\n" +
            "       artist,\n" +
            "       album,\n" +
            "       NULL AS title,\n" +
            "       album AS text1,\n" +
            "       artist AS text2,\n" +
            "       NULL AS data1,\n" +
            "       NULL AS data2,\n" +
            "       artist_key || ' ' || album_key AS [MATCH],\n" +
            "       '" + MusicStore.Audio.Albums.CONTENT_URI_SLASH + "' || _id AS suggest_intent_data,\n" +
            "       2 AS grouporder\n" +
            "  FROM " + ALBUM_INFO_VIEW_NAME + "\n" +
            " WHERE ( album != '<unknown>' ) \n" +
            "UNION ALL\n" +
            "SELECT searchhelpertitle._id AS _id,\n" +
            "       mime_type,\n" +
            "       artist,\n" +
            "       album,\n" +
            "       title,\n" +
            "       title AS text1,\n" +
            "       artist AS text2,\n" +
            "       NULL AS data1,\n" +
            "       NULL AS data2,\n" +
            "       artist_key || ' ' || album_key || ' ' || title_key AS [MATCH],\n" +
            "       '" + MusicStore.Audio.Media.CONTENT_URI_SLASH + "' || searchhelpertitle._id AS suggest_intent_data,\n" +
            "       3 AS grouporder\n" +
            "  FROM " + SEARCHHELPERTITLE_VIEW_NAME + "\n" +
            " WHERE ( title != '' )";
    public static final String SEARCH_ARCHOS_VIEW_NAME = "search_archos";
    private static final String CREATE_SEARCH_ARCHOS_VIEW =
            "CREATE VIEW " + SEARCH_ARCHOS_VIEW_NAME + " AS\n" +
            "SELECT _id,\n" +
            "       'artist' AS mime_type,\n" +
            "       artist,\n" +
            "       NULL AS album,\n" +
            "       NULL AS title,\n" +
            "       artist AS text1,\n" +
            "       NULL AS text2,\n" +
            "       number_of_albums AS data1,\n" +
            "       number_of_tracks AS data2,\n" +
            "       artist_key AS [MATCH],\n" +
            "       '" + MusicStore.Audio.Artists.CONTENT_URI_SLASH + "' || _id AS suggest_intent_data,\n" +
            "       1 AS grouporder,\n" +
            "       NULL AS suggest_icon_1\n" +
            "  FROM " + ARTIST_INFO_VIEW_NAME + "\n" +
            " WHERE ( artist != '<unknown>' ) \n" +
            "UNION ALL\n" +
            "SELECT _id,\n" +
            "       'album' AS mime_type,\n" +
            "       artist,\n" +
            "       album,\n" +
            "       NULL AS title,\n" +
            "       album AS text1,\n" +
            "       artist AS text2,\n" +
            "       NULL AS data1,\n" +
            "       NULL AS data2,\n" +
            "       artist_key || ' ' || album_key AS [MATCH],\n" +
            "       '" + MusicStore.Audio.Albums.CONTENT_URI_SLASH + "' || _id AS suggest_intent_data,\n" +
            "       2 AS grouporder,\n" +
            "       'file://' || album_art AS suggest_icon_1\n" +
            "  FROM " + ALBUM_INFO_VIEW_NAME + "\n" +
            " WHERE ( album != '<unknown>' ) \n" +
            "UNION ALL\n" +
            "SELECT searchhelpertitle._id AS _id,\n" +
            "       searchhelpertitle.mime_type,\n" +
            "       searchhelpertitle.artist,\n" +
            "       searchhelpertitle.album,\n" +
            "       searchhelpertitle.title,\n" +
            "       searchhelpertitle.title AS text1,\n" +
            "       searchhelpertitle.artist AS text2,\n" +
            "       NULL AS data1,\n" +
            "       NULL AS data2,\n" +
            "       searchhelpertitle.artist_key || ' ' || searchhelpertitle.album_key || ' ' || searchhelpertitle.title_key AS [MATCH],\n" +
            "       '" + MusicStore.Audio.Media.CONTENT_URI_SLASH + "' || searchhelpertitle._id AS suggest_intent_data,\n" +
            "       3 AS grouporder,\n" +
            "       'file://' || album_art._data AS suggest_icon_1\n" +
            "  FROM " + SEARCHHELPERTITLE_VIEW_NAME + "\n" +
            "       LEFT JOIN " + ALBUM_ART_TABLE_NAME + "\n" +
            "              ON searchhelpertitle.album_id = album_art.album_id\n" +
            " WHERE ( title != '' )";


    public MusicOpenHelper(Context context) {
        super(context, DATABASE_NAME, new CustomCursorFactory(), DATABASE_VERSION);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        // Turn on WAL optimization
        // TODO: test if that is good for us or not.
        db.enableWriteAheadLogging();
        // turn on foreign key support used in scraper tables
        db.execSQL("PRAGMA foreign_keys = ON");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(TAG, "Creating Database at version " + DATABASE_CREATE_VERSION);
        // create table for imported files
        db.execSQL(CREATE_FILES_IMPORT_TABLE);
        db.execSQL(CREATE_FILES_IMPORT_TRIGGER_INSERT);
        db.execSQL(CREATE_FILES_IMPORT_TRIGGER_DELETE);
        // create table for files scanned locally
        db.execSQL(CREATE_FILES_SCANNED_TABLE);
        db.execSQL(CREATE_FILES_SCANNED_TRIGGER_INSERT);
        db.execSQL(CREATE_FILES_SCANNED_TRIGGER_DELETE);
        // create table that holds scanned/imported + added information about imported & scanned files
        db.execSQL(CREATE_FILES_TABLE);
        db.execSQL(CREATE_FILES_TRIGGER_AUDIO_CLEANUP);

        // indices for files extra
        db.execSQL(CREATE_FILES_IDX_ALBUM_ID);
        db.execSQL(CREATE_FILES_IDX_ARTIST_ID);
        db.execSQL(CREATE_FILES_IDX_MEDIA_TYPE);
        db.execSQL(CREATE_FILES_IDX_TITLE);
        db.execSQL(CREATE_FILES_IDX_TITLE_KEY);
        db.execSQL(CREATE_FILES_IDX_BUCKET_INDEX);
        db.execSQL(CREATE_FILES_IDX_BUCKET_NAME);
        db.execSQL(CREATE_FILES_IDX_PARENT);
        db.execSQL(CREATE_FILES_IDX_PATH);

        // create table that has info about smb servers
        db.execSQL(CREATE_SMB_SERVER_TABLE);
        db.execSQL(CREATE_SMB_SERVER_ACTIVE_VIEW);

        // add audio views & tables
        db.execSQL(CREATE_AUDIO_META_VIEW_V0);

        db.execSQL(CREATE_ARTISTS_TABLE);
        db.execSQL(CREATE_ARTISTS_IDX_ARTIST);
        db.execSQL(CREATE_ARTISTS_IDX_ARTIST_KEY);

        db.execSQL(CREATE_ALBUMS_TABLE);
        db.execSQL(CREATE_ALBUMS_IDX_ALBUM);
        db.execSQL(CREATE_ALBUMS_IDX_ALBUMKEY);
        db.execSQL(CREATE_ALBUMS_TRIGGER_CLEANUP);

        db.execSQL(CREATE_AUDIO_GENRES_TABLE);
        db.execSQL(CREATE_AUDIO_GENRES_TRIGGER_CLEANUP);
        db.execSQL(CREATE_AUDIO_GENRES_MAP_TABLE);
        db.execSQL(CREATE_AUDIO_GENRES_MAP_NOID_VIEW);

        db.execSQL(CREATE_ALBUM_ART_TABLE);
        db.execSQL(CREATE_ALBUM_ART_TRIGGER_CLEANUP);

        db.execSQL(CREATE_AUDIO_PLAYLISTS_TABLE);
        db.execSQL(CREATE_AUDIO_PLAYLISTS_TRIGGER_CLEANUP);

        db.execSQL(CREATE_AUDIO_PLAYLISTS_MAP_TABLE);

        db.execSQL(CREATE_AUDIO_VIEW);
        db.execSQL(CREATE_ALBUM_INFO_VIEW);
        db.execSQL(CREATE_ARTIST_INFO_VIEW);
        db.execSQL(CREATE_ARTISTS_ALBUMS_MAP);

        db.execSQL(CREATE_SEARCHHELPERTITLE_VIEW);
        db.execSQL(CREATE_SEARCH_VIEW);
        db.execSQL(CREATE_SEARCH_ARCHOS_VIEW);

        onUpgrade(db, DATABASE_CREATE_VERSION, DATABASE_VERSION);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "Upgrading Database from " + oldVersion + " to " + newVersion);
        if (oldVersion < DATABASE_CREATE_VERSION) {
            Log.d(TAG, "Upgrade not supported for version " + oldVersion + ", recreating the database.");
            // triggers database deletion
            deleteDatabase();
        }
        if (oldVersion < 11) { // do something..
            // skipped, was video
        }
        // update triggers _DELETE_FILE -> _DELETE_FILE_J
        if (oldVersion < 12) {
            db.execSQL(DROP_ALBUM_ART_TRIGGER_CLEANUP);
            db.execSQL(CREATE_ALBUM_ART_TRIGGER_CLEANUP);
        }
        if (oldVersion < 13) {
            // dropped
            db.execSQL("DROP VIEW IF EXISTS " + SMB_SERVER_ACTIVE_VIEW_NAME);
            // also create an index for smb_server to avoid auto index of the same
            db.execSQL("CREATE INDEX smb_server_active_idx ON smb_server (active)");
        }
        // narf, forgot to update audio_meta not to use SMB_SERVER_ACTIVE_VIEW_NAME
        if (oldVersion < 14) {
            db.execSQL("DROP VIEW IF EXISTS " + AUDIO_META_VIEW_NAME);
            db.execSQL(CREATE_AUDIO_META_VIEW_V14);
        }
        if (oldVersion < 15) {
            // hmm, this one is empty for some reason.
        }
        if (oldVersion < 16) {
            SQLiteUtils.alterTable(db, FILES_TABLE_NAME, CREATE_FILES_TABLE_V16);
            // recreate indices and triggers
            db.execSQL(CREATE_FILES_TRIGGER_AUDIO_CLEANUP);
            db.execSQL(CREATE_FILES_IDX_ALBUM_ID);
            db.execSQL(CREATE_FILES_IDX_ARTIST_ID);
            db.execSQL(CREATE_FILES_IDX_MEDIA_TYPE);
            db.execSQL(CREATE_FILES_IDX_TITLE);
            db.execSQL(CREATE_FILES_IDX_TITLE_KEY);
            db.execSQL(CREATE_FILES_IDX_BUCKET_INDEX);
            db.execSQL(CREATE_FILES_IDX_BUCKET_NAME);
            db.execSQL(CREATE_FILES_IDX_PARENT);
            db.execSQL(CREATE_FILES_IDX_PATH);

            SQLiteUtils.alterTable(db, FILES_SCANNED_TABLE_NAME, CREATE_FILES_SCANNED_TABLE_V16);
            db.execSQL(CREATE_FILES_SCANNED_TRIGGER_INSERT);
            db.execSQL(CREATE_FILES_SCANNED_TRIGGER_DELETE);
        }
        if (oldVersion < 17) {
            SQLiteUtils.removeCredentials(db, FILES_SCANNED_TABLE_NAME, true);
            SQLiteUtils.removeCredentials(db, FILES_TABLE_NAME, true);
            SQLiteUtils.removeCredentials(db, SMB_SERVER_TABLE_NAME, false);
        }
    }
}
