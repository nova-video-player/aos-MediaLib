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

package com.archos.mediaprovider.video;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.provider.MediaStore.Files.FileColumns;
import android.util.Log;

import com.archos.mediaprovider.ArchosMediaCommon;
import com.archos.mediaprovider.CustomCursorFactory;
import com.archos.mediaprovider.SQLiteUtils;
import com.archos.mediaprovider.DeleteFileCallback;
import com.archos.mediaprovider.DeleteOnDowngradeSQLiteOpenHelper;
import com.archos.mediaprovider.video.VideoStore.MediaColumns;
import com.archos.mediaprovider.video.VideoStore.Video.VideoColumns;
import com.archos.mediascraper.ScraperImage;
import com.archos.mediascraper.ScraperImage.Type;

import java.io.File;

/**
 * Creates the video database
 */
public class VideoOpenHelper extends DeleteOnDowngradeSQLiteOpenHelper {
    private static final String TAG = ArchosMediaCommon.TAG_PREFIX + "VideoOpenHelper";
    private static final boolean LOCAL_DBG = false;
    private static final boolean DBG = ArchosMediaCommon.PACKAGE_DBG & LOCAL_DBG;

    // that is what onCreate creates
    private static final int DATABASE_CREATE_VERSION = 36; // initial version for v1.0 of nova (archos was 10)
    // that is the current version
    private static final int DATABASE_VERSION = 37;
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
    private static final String CREATE_FILES_IMPORT_TABLE_V21 =
            "CREATE TABLE " + FILES_IMPORT_TABLE_NAME + " (\n" +
            "    local_id            INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
            "    inserted            INTEGER DEFAULT ( strftime( '%s', 'now' )  ),\n" +
            // ON CONFLICT REPLACE is the magic that allows to simply overwrite data
            "    _id                 INTEGER UNIQUE ON CONFLICT REPLACE,\n" +
            // ON CONFLICT REPLACE for _data as well since files can change id
            "    _data               TEXT UNIQUE ON CONFLICT REPLACE,\n" +
            "    _display_name       TEXT,\n" +
            "    _size               INTEGER,\n" +
            "    date_added          INTEGER,\n" +
            "    date_modified       INTEGER,\n" +
            "    bucket_id           TEXT,\n" +
            "    bucket_display_name TEXT,\n" +
            "    format              INTEGER,\n" +
            "    parent              INTEGER,\n" +
            "    storage_id          INTEGER,\n" +
            "    volume_hidden       INTEGER DEFAULT (0)\n" +
            ")";
    // trigger to insert + update the corresponding entry in files after inserting
    // or replacing data in files_import
    private static final String CREATE_FILES_IMPORT_TRIGGER_INSERT_V21 =
            "CREATE TRIGGER IF NOT EXISTS after_insert_files_import " +
            "AFTER INSERT ON " + FILES_IMPORT_TABLE_NAME + " " +
            "BEGIN \n"+
                "INSERT INTO " + FILES_TABLE_NAME + "(_data) VALUES (NEW._data);\n" +
                "UPDATE " + FILES_TABLE_NAME + " SET\n" +
                "_id = NEW._id , \n" +
                "remote_id = NEW._id , \n" +
                "_display_name = NEW._display_name , \n" +
                "_size = NEW._size , \n" +
                "date_added = NEW.date_added , \n" +
                "date_modified = NEW.date_modified , \n" +
                "bucket_id = NEW.bucket_id , \n" +
                "bucket_display_name = NEW.bucket_display_name , \n" +
                "format = NEW.format , \n" +
                "parent = NEW.parent , \n" +
                "storage_id = NEW.storage_id , \n" +
                "Archos_smbserver = 0 , \n" +
                "volume_hidden = 0\n" + // NEW - set hidden to false
                "WHERE _data=NEW._data;\n" +
             "END";
    // trigger to delete from files table if the corresponding id was deleted in files_import
    // trigger to delete from files table if the corresponding id was deleted in files_import
    private static final String CREATE_FILES_IMPORT_TRIGGER_DELETE_V20 =
            "CREATE TRIGGER IF NOT EXISTS after_delete_files_import " +
            "AFTER DELETE ON " + FILES_IMPORT_TABLE_NAME + " " +
            "BEGIN "+
                "DELETE FROM " + FILES_TABLE_NAME + " WHERE _data=OLD._data; "+
             "END";
    // forwards updates of volume hidden to files table
    private static final String CREATE_FILES_IMPORT_TRIGGER_UPDATE_V21 =
            "CREATE TRIGGER IF NOT EXISTS after_update_files_import " +
            "AFTER UPDATE OF volume_hidden ON " + FILES_IMPORT_TABLE_NAME + " " +
            "BEGIN "+
                "UPDATE " + FILES_TABLE_NAME + " SET volume_hidden = NEW.volume_hidden WHERE _id = OLD._id; "+
             "END";

    /**
     * View that when inserted a storage_id hides & deletes data for that volume.
     * Queries on that view lists all the storage_ids that exist in files_import.
     **/
    public static final String HIDE_VOLUMES_VIEW_NAME = "hide_volume_cmd";
    private static final String CREATE_HIDE_VOLUMES_VIEW =
            "CREATE VIEW " + HIDE_VOLUMES_VIEW_NAME + " AS SELECT DISTINCT storage_id FROM " + FILES_IMPORT_TABLE_NAME;
    private static final String CREATE_HIDE_VOLUMES_TRIGGER =
            "CREATE TRIGGER hide_volume_cmd_trigger INSTEAD OF INSERT ON " + HIDE_VOLUMES_VIEW_NAME + " \n" +
            "BEGIN\n" +
            // delete all files that are already hidden and have that state since at least a month
            "    DELETE FROM " + FILES_IMPORT_TABLE_NAME + " WHERE storage_id = NEW.storage_id AND volume_hidden > 0 AND volume_hidden < strftime('%s', 'now', '-1 month');\n" +
            // then set all visible files to hidden
            "    UPDATE " + FILES_IMPORT_TABLE_NAME + " SET volume_hidden = strftime('%s', 'now') WHERE volume_hidden == 0 AND storage_id == NEW.storage_id;\n" + 
            "END";
    // ------------- ---##[ Scanned Files (SMB)  ]## ---------------------------
    // files_scanned holds data for network scanned files, but updates data in files table
    public static final String FILES_SCANNED_TABLE_NAME = "files_scanned";
    // V18 drops the UNIQUE contraint on _data
    private static final String CREATE_FILES_SCANNED_TABLE_V32 =
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
            "    Archos_smbserver    INTEGER DEFAULT ( 0 ),\n" +
            "    Archos_videoStereo  INTEGER DEFAULT (0),\n" +
            "    Archos_videoDefinition INTEGER DEFAULT (0),\n" +
            VideoColumns.ARCHOS_UNIQUE_ID + " STRING DEFAULT (''),\n" +
            VideoColumns.ARCHOS_GUESSED_AUDIO_FORMAT + " STRING,\n" +
            VideoColumns.ARCHOS_GUESSED_VIDEO_FORMAT +" STRING\n" +
            ")";

    // trigger to insert + update the corresponding entry in files after inserting
    // or replacing data in files_scanned
    private static final String CREATE_FILES_SCANNED_TRIGGER_INSERT_V32 =
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
                "Archos_smbserver = NEW.Archos_smbserver , \n" +
                "Archos_videoStereo = NEW.Archos_videoStereo , \n" +
                "Archos_videoDefinition = NEW.Archos_videoDefinition, \n" +
                VideoColumns.ARCHOS_GUESSED_AUDIO_FORMAT+" = NEW."+VideoColumns.ARCHOS_GUESSED_AUDIO_FORMAT+", \n" + // new
                VideoColumns.ARCHOS_GUESSED_VIDEO_FORMAT+" = NEW."+VideoColumns.ARCHOS_GUESSED_VIDEO_FORMAT+"\n" + // new
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
    private static final String CREATE_FILES_TABLE_V32 =
            "CREATE TABLE " + FILES_TABLE_NAME + " ( \n" +
            "    local_id                        INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
            "    inserted                        INTEGER DEFAULT ( strftime( '%s', 'now' ) ),\n" +
            "    _id                             INTEGER UNIQUE ON CONFLICT REPLACE,\n" +
            // _data is now  UNIQUE ON CONFLICT IGNORE since that column is now used by
            // files_import to detect insert or updates of data
            "    _data                           TEXT UNIQUE ON CONFLICT IGNORE,\n" +
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
            "    Archos_title                    TEXT    DEFAULT ( NULL ),\n" +
            "    subtitle_count_ext INTEGER DEFAULT (0),\n" +
            "    autoscrape_status INTEGER DEFAULT (0)\n," +
            "    volume_hidden INTEGER DEFAULT (0)\n," +
            "    Archos_traktSeen INTEGER DEFAULT (0)\n," +
            "    Archos_traktLibrary INTEGER DEFAULT (0)\n," +
            "    Archos_videoStereo INTEGER DEFAULT (0)\n," +
            "    Archos_videoDefinition INTEGER DEFAULT (0),\n" +
            "    Archos_traktResume INTEGER DEFAULT (0),\n" +
            "    Archos_hiddenByUser INTEGER DEFAULT (0),\n" +
            VideoColumns.ARCHOS_CALCULATED_VIDEO_FORMAT + " STRING,\n" +
            VideoColumns.ARCHOS_CALCULATED_BEST_AUDIOTRACK_FORMAT + " STRING,\n" +
            VideoColumns.ARCHOS_GUESSED_AUDIO_FORMAT + " STRING,\n" +
            VideoColumns.ARCHOS_GUESSED_VIDEO_FORMAT +" STRING\n" +
            ")";


    private static final String CREATE_FILES_SCANNED_TRIGGER_UPDATE_URI =
            "CREATE TRIGGER after_update_uri_files_scanned " +
                    "AFTER UPDATE OF _data ON " + FILES_SCANNED_TABLE_NAME + " " +
                    "BEGIN " +
                    "UPDATE " + FILES_TABLE_NAME + " SET _data= NEW._data WHERE remote_id=(OLD._id + " + SCANNED_ID_OFFSET + ");" +
                    "END";

    private static final String CREATE_FILES_SCANNED_TRIGGER_STORAGE_ID =
            "CREATE TRIGGER after_update_storage_id_files_scanned " +
                    "AFTER UPDATE OF storage_id ON " + FILES_SCANNED_TABLE_NAME + " " +
                    "BEGIN " +
                    "UPDATE " + FILES_TABLE_NAME + " SET storage_id = NEW.storage_id;" +
                    "END";
    private static final String DROP_TRIGGER_STORAGE_ID =
            "DROP TRIGGER after_update_storage_id_files_scanned";

    // triggers to remove scraper data on scraper id change
    private static final String CREATE_FILES_TRIGGER_SCRAPER_MOVIE_CLEANUP =
            "CREATE TRIGGER scraper_movie_cleanup AFTER UPDATE OF ArchosMediaScraper_id ON " +
            FILES_TABLE_NAME + " WHEN OLD.ArchosMediaScraper_type=" + ScraperStore.SCRAPER_TYPE_MOVIE +
            " AND NEW.ArchosMediaScraper_id != OLD.ArchosMediaScraper_id " +
            "BEGIN " +
            "DELETE FROM movie WHERE _id = OLD.ArchosMediaScraper_id; " +
            "END";
    private static final String CREATE_FILES_TRIGGER_SCRAPER_EPISODE_CLEANUP =
            "CREATE TRIGGER scraper_episode_cleanup AFTER UPDATE OF ArchosMediaScraper_id ON " +
            FILES_TABLE_NAME + " WHEN OLD.ArchosMediaScraper_type=" + ScraperStore.SCRAPER_TYPE_SHOW +
            " AND NEW.ArchosMediaScraper_id != OLD.ArchosMediaScraper_id " +
            "BEGIN " +
            "DELETE FROM episode WHERE _id = OLD.ArchosMediaScraper_id; " +
            "END";
    /* VOB file detection to trigger code that hides unwanted vobs */
    // trigger to callback java VobHandler when a new vob is inserted
    private static final String CREATE_FILES_TRIGGER_VOB_INSERT =
            "CREATE TRIGGER vob_insert_import AFTER INSERT ON " + FILES_TABLE_NAME + " WHEN " +
            "NEW.bucket_id IS NOT NULL AND NEW.date_modified > 0 AND ( " +
            "NEW._data LIKE '%/vts!___!__.vob' ESCAPE '!' OR " +
            "NEW._data LIKE '%/video!_ts.vob' ESCAPE '!') " +
            "BEGIN INSERT INTO vob_insert(name) VALUES(NEW.bucket_id);END";
    private static final String DROP_FILES_TRIGGER_VOB_INSERT =
            "DROP TRIGGER IF EXISTS vob_insert_import";
    // trigger to callback java VobHandler when a vob is updated
    private static final String CREATE_FILES_TRIGGER_VOB_UPDATE =
            "CREATE TRIGGER vob_update_import AFTER UPDATE OF date_modified ON " + FILES_TABLE_NAME + " WHEN " +
            "NEW.date_modified > 0 AND (" +
            "NEW._data LIKE '%/vts!___!__.vob' ESCAPE '!' OR " +
            "NEW._data LIKE '%/video!_ts.vob' ESCAPE '!') " +
            "BEGIN INSERT INTO vob_insert(name) VALUES(NEW.bucket_id);END";
    private static final String DROP_FILES_TRIGGER_VOB_UPDATE =
            "DROP TRIGGER IF EXISTS vob_update_import";
    private static final String CREATE_FILES_TRIGGER_VOB_DELETE =
            "CREATE TRIGGER vob_delete_import AFTER DELETE ON " + FILES_TABLE_NAME + " WHEN " +
            "OLD._data LIKE '%/vts!___!__.vob' ESCAPE '!' OR " +
            "OLD._data LIKE '%/video!_ts.vob' ESCAPE '!' " +
            "BEGIN INSERT INTO vob_insert(name) VALUES(OLD.bucket_id);END";
    private static final String DROP_FILES_TRIGGER_VOB_DELETE =
            "DROP TRIGGER IF EXISTS vob_delete_import";
    // indices TODO: test how these affect performance, just copied from android db.
    private static final String CREATE_FILES_IDX_MEDIA_TYPE =
            "CREATE INDEX media_type_index ON " + FILES_TABLE_NAME + " (media_type)";
    private static final String CREATE_FILES_IDX_TITLE =
            "CREATE INDEX title_idx ON " + FILES_TABLE_NAME + " (title)";
    private static final String CREATE_FILES_IDX_PARENT =
            "CREATE INDEX parent_index ON " + FILES_TABLE_NAME + " (parent)";
    private static final String CREATE_FILES_IDX_BUCKET_NAME =
            "CREATE INDEX bucket_name ON " + FILES_TABLE_NAME + " (bucket_id, media_type, bucket_display_name)";
    private static final String CREATE_FILES_IDX_BUCKET_INDEX =
            "CREATE INDEX bucket_index ON " + FILES_TABLE_NAME + " (bucket_id, media_type" /*TODO ?? + ", datetaken"*/ + ", _id)";
    private static final String CREATE_FILES_IDX_PATH =
            "CREATE INDEX path_index ON " + FILES_TABLE_NAME + "(_data)";
    // should speed up most queries on Video that contain the typical Archos_hideFile = 0
    private static final String CREATE_FILES_HIDDEN_IDX =
            "CREATE INDEX files_hidden ON " + FILES_TABLE_NAME + " (volume_hidden, media_type, Archos_hideFile)";

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
    /* --                       VIDEO database part                           */
    /* ---------------------------------------------------------------------- */

    // ------------- ---##[ Video Files          ]## ---------------------------
    public static final String VIDEO_VIEW_NAME = "video";
    private static final String CREATE_VIDEO_VIEW_V32 =
            "CREATE VIEW " + VIDEO_VIEW_NAME + " AS SELECT \n" +
                    "    f._id,\n" +
                    "    _data,\n" +
                    "    _display_name,\n" +
                    "    _size,\n" +
                    "    mime_type,\n" +
                    "    date_added,\n" +
                    "    date_modified,\n" +
                    "    inserted,\n" +
                    "    coalesce( archos_title, title ) AS title,\n" +
                    "    title AS android_title,\n" +
                    "    archos_title,\n" +
                    "    duration,\n" +
                    "    artist,\n" +
                    "    album,\n" +
                    "    NULL AS resolution,\n" +
                    "    NULL AS description,\n" +
                    "    NULL AS isprivate,\n" +
                    "    NULL AS tags,\n" +
                    "    NULL AS category,\n" +
                    "    NULL AS language,\n" +
                    "    mini_thumb_data,\n" +
                    "    NULL AS latitude,\n" +
                    "    NULL AS longitude,\n" +
                    "    NULL AS datetaken,\n" +
                    "    mini_thumb_magic,\n" +
                    "    bucket_id,\n" +
                    "    bucket_display_name,\n" +
                    "    bookmark,\n" +
                    "    width,\n" +
                    "    height,\n" +
                    "    Archos_favorite_track,\n" +
                    "    Archos_bookmark,\n" +
                    "    Archos_lastTimePlayed,\n" +
                    "    Archos_playerParams,\n" +
                    "    Archos_playerSubtitleDelay,\n" +
                    "    ArchosMediaScraper_id,\n" +
                    "    ArchosMediaScraper_type,\n" +
                    "    Archos_numberOfSubtitleTracks,\n" +
                    "    subtitle_count_ext,\n" +
                    "    Archos_numberOfAudioTracks,\n" +
                    "    Archos_sampleRate,\n" +
                    "    Archos_numberOfChannels,\n" +
                    "    Archos_audioWaveCodec,\n" +
                    "    Archos_audioBitRate,\n" +
                    "    Archos_videoFourCCCodec,\n" +
                    "    Archos_videoBitRate,\n" +
                    "    Archos_framesPerThousandSeconds,\n" +
                    "    Archos_encodingProfile,\n" +
                    "    Archos_playerSubtitleRatio,\n" +
                    "    Archos_thumbTry,\n" +
                    "    Archos_hideFile,\n" +
                    "    Archos_hiddenByUser,\n" +  //NEW hidden by user feature
                    "    m._id AS m_id,\n" +
                    "    s._id AS s_id,\n" +
                    "    e._id AS e_id,\n" +
                    "    coalesce(name_movie, name_show) AS scraper_name,\n" +
                    "    name_movie AS m_name,\n" +
                    "    name_show AS s_name,\n" +
                    "    name_episode AS e_name,\n" +
                    "    season_episode AS e_season,\n" +
                    "    number_episode AS e_episode,\n" +
                    "    aired_episode AS e_aired,\n" +
                    "    premiered_show AS s_premiered,\n" +
                    "    year_movie AS m_year,\n" +
                    "    coalesce(rating_movie, rating_episode) AS rating,\n" +
                    "    rating_movie AS m_rating,\n" +
                    "    rating_episode AS e_rating,\n" +
                    "    rating_show AS s_rating,\n" +
                    "    coalesce(m_online_id, s_online_id) AS online_id,\n" +
                    "    coalesce(m_online_id, e_online_id) AS "+VideoStore.Video.VideoColumns.SCRAPER_VIDEO_ONLINE_ID+",\n" +
                    "    coalesce(m_imdb_id, s_imdb_id) AS imdb_id,\n" +
                    "    coalesce(m_content_rating, s_content_rating) AS content_rating,\n" +
                    "    m_online_id,\n" +
                    "    m_imdb_id,\n" +
                    "    m_content_rating,\n" +
                    "    s_online_id,\n" +
                    "    s_imdb_id,\n" +
                    "    s_content_rating,\n" +
                    "    e_online_id,\n" +
                    "    e_imdb_id,\n" +
                    "    coalesce(plot_movie, plot_episode) AS plot,\n" +
                    "    plot_movie AS m_plot,\n" +
                    "    plot_episode AS e_plot,\n" +
                    "    plot_show AS s_plot,\n" +
                    "    coalesce(m_actors, s_actors) AS actors,\n" +
                    "    m_actors,\n" +
                    "    s_actors,\n" +
                    "    e_actors,\n" +
                    "    coalesce(m_directors, e_directors) AS directors,\n" +
                    "    m_directors,\n" +
                    "    e_directors,\n" +
                    "    s_directors,\n" +
                    "    coalesce(m_genres, s_genres) AS genres,\n" +
                    "    m_genres,\n" +
                    "    s_genres,\n" +
                    "    coalesce(m_studios, s_studios) AS studios,\n" +
                    "    m_studios,\n" +
                    "    s_studios,\n" +
                    "    coalesce(mp.m_po_large_file, cover_movie, ep.s_po_large_file, sp.s_po_large_file, cover_episode, cover_show) AS cover,\n" +
                    "    coalesce(mp.m_po_large_file, cover_movie) AS m_cover,\n" +
                    "    coalesce(ep.s_po_large_file, cover_episode) AS e_cover,\n" +
                    "    coalesce(sp.s_po_large_file, cover_show) AS s_cover,\n" +
                    "    coalesce(mb.m_bd_large_url, backdrop_url_movie, sb.s_bd_large_url, backdrop_url_show) AS bd_url,\n" +
                    "    coalesce(mb.m_bd_large_url, backdrop_url_movie) AS m_bd_url,\n" +
                    "    coalesce(sb.s_bd_large_url, backdrop_url_show) AS s_bd_url,\n" +
                    "    coalesce(mb.m_bd_large_file, backdrop_movie, sb.s_bd_large_file, backdrop_show) AS bd_file,\n" +
                    "    coalesce(mb.m_bd_large_file, backdrop_movie) AS m_bd_file,\n" +
                    "    coalesce(sb.s_bd_large_file, backdrop_show) AS s_bd_file,\n" +
                    "    coalesce(mp._id, ep._id, sp._id) AS poster_id,\n" +
                    "    coalesce(mp.m_po_thumb_url, ep.s_po_thumb_url, sp.s_po_thumb_url) AS po_thumb_url,\n" +
                    "    coalesce(mp.m_po_thumb_file, ep.s_po_thumb_file, sp.s_po_thumb_file) AS po_thumb_file,\n" +
                    "    coalesce(mp.m_po_large_url, ep.s_po_large_url, sp.s_po_large_url) AS po_large_url,\n" +
                    "    coalesce(mp.m_po_large_file, ep.s_po_large_file, sp.s_po_large_file) AS po_large_file,\n" +
                    "    coalesce(mb._id, sb._id) AS backdrop_id,\n" +
                    "    coalesce(mb.m_bd_thumb_url, sb.s_bd_thumb_url) AS bd_thumb_url,\n" +
                    "    coalesce(mb.m_bd_thumb_file, sb.s_bd_thumb_file) AS bd_thumb_file,\n" +
                    "    coalesce(mb.m_bd_large_url, sb.s_bd_large_url) AS bd_large_url,\n" +
                    "    coalesce(mb.m_bd_large_file, sb.s_bd_large_file) AS bd_large_file,\n" +
                    "    ep._id AS e_poster_id,\n" +
                    "    ep.s_po_thumb_url AS e_po_thumb_url,\n" +
                    "    ep.s_po_thumb_file AS e_po_thumb_file,\n" +
                    "    ep.s_po_large_url AS e_po_large_url,\n" +
                    "    ep.s_po_large_file AS e_po_large_file,\n" +
                    "    ep.s_po_season AS e_po_season,\n" +
                    "    sp._id AS s_poster_id,\n" +
                    "    sp.s_po_thumb_url,\n" +
                    "    sp.s_po_thumb_file,\n" +
                    "    sp.s_po_large_url,\n" +
                    "    sp.s_po_large_file,\n" +
                    "    sb._id AS s_backdrop_id,\n" +
                    "    sb.s_bd_thumb_url,\n" +
                    "    sb.s_bd_thumb_file,\n" +
                    "    sb.s_bd_large_url,\n" +
                    "    sb.s_bd_large_file,\n" +
                    "    mp._id AS m_poster_id,\n" +
                    "    mp.m_po_thumb_url,\n" +
                    "    mp.m_po_thumb_file,\n" +
                    "    mp.m_po_large_url,\n" +
                    "    mp.m_po_large_file,\n" +
                    "    mb._id AS m_backdrop_id,\n" +
                    "    mb.m_bd_thumb_url,\n" +
                    "    mb.m_bd_thumb_file,\n" +
                    "    mb.m_bd_large_url,\n" +
                    "    mb.m_bd_large_file,\n" +
                    "    autoscrape_status,\n" +
                    "    Archos_traktSeen,\n" +
                    "    Archos_traktLibrary,\n" +
                    "    Archos_videoStereo,\n" +
                    "    Archos_videoDefinition,\n" +
                    "    Archos_traktResume,\n" +
                    "    "+VideoColumns.ARCHOS_CALCULATED_VIDEO_FORMAT +",\n" +
                    "    "+VideoColumns.ARCHOS_CALCULATED_BEST_AUDIOTRACK_FORMAT +",\n" +
                    "    "+VideoColumns.ARCHOS_GUESSED_VIDEO_FORMAT+",\n" +
                    "    "+VideoColumns.ARCHOS_GUESSED_AUDIO_FORMAT +",\n" +
                    "    "+ScraperStore.Episode.PICTURE+" AS "+ VideoColumns.SCRAPER_E_PICTURE+" \n"+
                    "FROM\n" +
                    "files AS f\n" +
                    "LEFT JOIN movie AS m ON (m.video_id = f._id)\n" +
                    "       LEFT JOIN movie_posters AS mp ON ( m.m_poster_id = mp._id ) \n" +
                    "       LEFT JOIN movie_backdrops AS mb ON ( m.m_backdrop_id = mb._id )\n" +
                    "LEFT JOIN episode AS e ON (e.video_id = f._id)\n" +
                    "       LEFT JOIN show_posters AS ep ON ( e.e_poster_id = ep._id )\n" +
                    "   LEFT JOIN show AS s on (e.show_episode = s._id)\n" +
                    "       LEFT JOIN show_posters AS sp ON ( s.s_poster_id = sp._id ) \n" +
                    "       LEFT JOIN show_backdrops AS sb ON ( s.s_backdrop_id = sb._id )\n" +
                    "WHERE\n" +
                    "    volume_hidden == 0 AND\n" +
                    "    media_type == 3 AND\n" +
                    "    (Archos_smbserver == 0 OR\n" +
                    "    Archos_smbserver IN (SELECT _id FROM smb_server WHERE active == 1))";

	private static final String CREATE_VIDEO_VIEW_V37 =
			"CREATE VIEW " + VIDEO_VIEW_NAME + " AS SELECT \n" +
					"    f._id,\n" +
					"    _data,\n" +
					"    _display_name,\n" +
					"    _size,\n" +
					"    mime_type,\n" +
					"    date_added,\n" +
					"    date_modified,\n" +
					"    inserted,\n" +
					"    coalesce( archos_title, title ) AS title,\n" +
					"    title AS android_title,\n" +
					"    archos_title,\n" +
					"    duration,\n" +
					"    artist,\n" +
					"    album,\n" +
					"    NULL AS resolution,\n" +
					"    NULL AS description,\n" +
					"    NULL AS isprivate,\n" +
					"    NULL AS tags,\n" +
					"    NULL AS category,\n" +
					"    NULL AS language,\n" +
					"    mini_thumb_data,\n" +
					"    NULL AS latitude,\n" +
					"    NULL AS longitude,\n" +
					"    NULL AS datetaken,\n" +
					"    mini_thumb_magic,\n" +
					"    bucket_id,\n" +
					"    bucket_display_name,\n" +
					"    bookmark,\n" +
					"    width,\n" +
					"    height,\n" +
					"    Archos_favorite_track,\n" +
					"    Archos_bookmark,\n" +
					"    Archos_lastTimePlayed,\n" +
					"    Archos_playerParams,\n" +
					"    Archos_playerSubtitleDelay,\n" +
					"    ArchosMediaScraper_id,\n" +
					"    ArchosMediaScraper_type,\n" +
					"    Archos_numberOfSubtitleTracks,\n" +
					"    subtitle_count_ext,\n" +
					"    Archos_numberOfAudioTracks,\n" +
					"    Archos_sampleRate,\n" +
					"    Archos_numberOfChannels,\n" +
					"    Archos_audioWaveCodec,\n" +
					"    Archos_audioBitRate,\n" +
					"    Archos_videoFourCCCodec,\n" +
					"    Archos_videoBitRate,\n" +
					"    Archos_framesPerThousandSeconds,\n" +
					"    Archos_encodingProfile,\n" +
					"    Archos_playerSubtitleRatio,\n" +
					"    Archos_thumbTry,\n" +
					"    Archos_hideFile,\n" +
					"    Archos_hiddenByUser,\n" +  //NEW hidden by user feature
					"    m._id AS m_id,\n" +
					"    s._id AS s_id,\n" +
					"    e._id AS e_id,\n" +
					"    coalesce(name_movie, name_show) AS scraper_name,\n" +
					"    name_movie AS m_name,\n" +
					"    name_show AS s_name,\n" +
					"    name_episode AS e_name,\n" +
					"    season_episode AS e_season,\n" +
					"    number_episode AS e_episode,\n" +
					"    aired_episode AS e_aired,\n" +
					"    premiered_show AS s_premiered,\n" +
					"    year_movie AS m_year,\n" +
					"    coalesce(rating_movie, rating_episode) AS rating,\n" +
					"    rating_movie AS m_rating,\n" +
					"    rating_episode AS e_rating,\n" +
					"    rating_show AS s_rating,\n" +
					"    coalesce(m_online_id, s_online_id) AS online_id,\n" +
					"    coalesce(m_online_id, e_online_id) AS "+VideoStore.Video.VideoColumns.SCRAPER_VIDEO_ONLINE_ID+",\n" +
					"    coalesce(m_imdb_id, s_imdb_id) AS imdb_id,\n" +
					"    coalesce(m_content_rating, s_content_rating) AS content_rating,\n" +
					"    m_online_id,\n" +
					"    m_imdb_id,\n" +
					"    m_content_rating,\n" +
					"    s_online_id,\n" +
					"    s_imdb_id,\n" +
					"    s_content_rating,\n" +
					"    e_online_id,\n" +
					"    e_imdb_id,\n" +
					"    coalesce(plot_movie, plot_episode) AS plot,\n" +
					"    plot_movie AS m_plot,\n" +
					"    plot_episode AS e_plot,\n" +
					"    plot_show AS s_plot,\n" +
					"    coalesce(m_actors, s_actors) AS actors,\n" +
					"    m_actors,\n" +
					"    s_actors,\n" +
					"    e_actors,\n" +
					"    coalesce(m_directors, e_directors) AS directors,\n" +
					"    m_directors,\n" +
					"    e_directors,\n" +
					"    s_directors,\n" +
					"    coalesce(m_genres, s_genres) AS genres,\n" +
					"    m_genres,\n" +
					"    s_genres,\n" +
					"    coalesce(m_studios, s_studios) AS studios,\n" +
					"    m_studios,\n" +
					"    s_studios,\n" +
					"    coalesce(mp.m_po_large_file, cover_movie, ep.s_po_large_file, sp.s_po_large_file, cover_episode, cover_show) AS cover,\n" +
					"    coalesce(mp.m_po_large_file, cover_movie) AS m_cover,\n" +
					"    coalesce(ep.s_po_large_file, cover_episode) AS e_cover,\n" +
					"    coalesce(sp.s_po_large_file, cover_show) AS s_cover,\n" +
					"    coalesce(mb.m_bd_large_url, backdrop_url_movie, sb.s_bd_large_url, backdrop_url_show) AS bd_url,\n" +
					"    coalesce(mb.m_bd_large_url, backdrop_url_movie) AS m_bd_url,\n" +
					"    coalesce(sb.s_bd_large_url, backdrop_url_show) AS s_bd_url,\n" +
					"    coalesce(mb.m_bd_large_file, backdrop_movie, sb.s_bd_large_file, backdrop_show) AS bd_file,\n" +
					"    coalesce(mb.m_bd_large_file, backdrop_movie) AS m_bd_file,\n" +
					"    coalesce(sb.s_bd_large_file, backdrop_show) AS s_bd_file,\n" +
					"    coalesce(mp._id, ep._id, sp._id) AS poster_id,\n" +
					"    coalesce(mp.m_po_thumb_url, ep.s_po_thumb_url, sp.s_po_thumb_url) AS po_thumb_url,\n" +
					"    coalesce(mp.m_po_thumb_file, ep.s_po_thumb_file, sp.s_po_thumb_file) AS po_thumb_file,\n" +
					"    coalesce(mp.m_po_large_url, ep.s_po_large_url, sp.s_po_large_url) AS po_large_url,\n" +
					"    coalesce(mp.m_po_large_file, ep.s_po_large_file, sp.s_po_large_file) AS po_large_file,\n" +
					"    coalesce(mb._id, sb._id) AS backdrop_id,\n" +
					"    coalesce(mb.m_bd_thumb_url, sb.s_bd_thumb_url) AS bd_thumb_url,\n" +
					"    coalesce(mb.m_bd_thumb_file, sb.s_bd_thumb_file) AS bd_thumb_file,\n" +
					"    coalesce(mb.m_bd_large_url, sb.s_bd_large_url) AS bd_large_url,\n" +
					"    coalesce(mb.m_bd_large_file, sb.s_bd_large_file) AS bd_large_file,\n" +
					"    ep._id AS e_poster_id,\n" +
					"    ep.s_po_thumb_url AS e_po_thumb_url,\n" +
					"    ep.s_po_thumb_file AS e_po_thumb_file,\n" +
					"    ep.s_po_large_url AS e_po_large_url,\n" +
					"    ep.s_po_large_file AS e_po_large_file,\n" +
					"    ep.s_po_season AS e_po_season,\n" +
					"    sp._id AS s_poster_id,\n" +
					"    sp.s_po_thumb_url,\n" +
					"    sp.s_po_thumb_file,\n" +
					"    sp.s_po_large_url,\n" +
					"    sp.s_po_large_file,\n" +
					"    sb._id AS s_backdrop_id,\n" +
					"    sb.s_bd_thumb_url,\n" +
					"    sb.s_bd_thumb_file,\n" +
					"    sb.s_bd_large_url,\n" +
					"    sb.s_bd_large_file,\n" +
					"    mp._id AS m_poster_id,\n" +
					"    mp.m_po_thumb_url,\n" +
					"    mp.m_po_thumb_file,\n" +
					"    mp.m_po_large_url,\n" +
					"    mp.m_po_large_file,\n" +
					"    mb._id AS m_backdrop_id,\n" +
					"    mb.m_bd_thumb_url,\n" +
					"    mb.m_bd_thumb_file,\n" +
					"    mb.m_bd_large_url,\n" +
					"    mb.m_bd_large_file,\n" +
					"    autoscrape_status,\n" +
					"    Archos_traktSeen,\n" +
					"    Archos_traktLibrary,\n" +
					"    Archos_videoStereo,\n" +
					"    Archos_videoDefinition,\n" +
					"    Archos_traktResume,\n" +
					"    "+VideoColumns.ARCHOS_CALCULATED_VIDEO_FORMAT +",\n" +
					"    "+VideoColumns.ARCHOS_CALCULATED_BEST_AUDIOTRACK_FORMAT +",\n" +
					"    "+VideoColumns.ARCHOS_GUESSED_VIDEO_FORMAT+",\n" +
					"    "+VideoColumns.ARCHOS_GUESSED_AUDIO_FORMAT +",\n" +
					"    "+ScraperStore.Episode.PICTURE+" AS "+ VideoColumns.SCRAPER_E_PICTURE+",\n"+
					"    coalesce(m." + VideoColumns.NOVA_PINNED + ", s." + VideoColumns.NOVA_PINNED + ") AS " + VideoColumns.NOVA_PINNED + " \n" +
					"FROM\n" +
					"files AS f\n" +
					"LEFT JOIN movie AS m ON (m.video_id = f._id)\n" +
					"       LEFT JOIN movie_posters AS mp ON ( m.m_poster_id = mp._id ) \n" +
					"       LEFT JOIN movie_backdrops AS mb ON ( m.m_backdrop_id = mb._id )\n" +
					"LEFT JOIN episode AS e ON (e.video_id = f._id)\n" +
					"       LEFT JOIN show_posters AS ep ON ( e.e_poster_id = ep._id )\n" +
					"   LEFT JOIN show AS s on (e.show_episode = s._id)\n" +
					"       LEFT JOIN show_posters AS sp ON ( s.s_poster_id = sp._id ) \n" +
					"       LEFT JOIN show_backdrops AS sb ON ( s.s_backdrop_id = sb._id )\n" +
					"WHERE\n" +
					"    volume_hidden == 0 AND\n" +
					"    media_type == 3 AND\n" +
					"    (Archos_smbserver == 0 OR\n" +
					"    Archos_smbserver IN (SELECT _id FROM smb_server WHERE active == 1))";


    // ------------- ---##[ Video Thumbnails     ]## ---------------------------
    public static final String VIDEOTHUMBNAIL_TABLE_NAME = "videothumbnails";
    private static final String CREATE_VIDEOTHUMBNAIL_TABLE =
            "CREATE TABLE " + VIDEOTHUMBNAIL_TABLE_NAME + " " +
            "(_id INTEGER PRIMARY KEY,_data TEXT,video_id INTEGER,kind INTEGER,width INTEGER,height INTEGER)";
    // trigger to delete thumb files when removing the db entry
    private static final String CREATE_VIDEOTHUMBNAIL_TRIGGER_CLEANUP =
            "CREATE TRIGGER videothumbnails_cleanup DELETE ON " + VIDEOTHUMBNAIL_TABLE_NAME + " " +
            "BEGIN SELECT _DELETE_FILE_J(old._data);END";
    private static final String DROP_VIDEOTHUMBNAIL_TRIGGER_CLEANUP =
            "DROP TRIGGER IF EXISTS videothumbnails_cleanup";
    private static final String CREATE_VIDEOTHUMBNAIL_IDX_VIDEO_ID =
            "CREATE INDEX video_id_index on videothumbnails(video_id)";

    public static final String SUBTITLES_TABLE_NAME = "subtitles";
    private static final String CREATE_SUBTITLES_TABLE_V17 =
            "CREATE TABLE " + SUBTITLES_TABLE_NAME + " (\n" +
            "    _id      INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
            "    _data    TEXT,\n" +
            "    lang     TEXT,\n" +
            "    _size     INTEGER,\n" +
            "    video_id INTEGER REFERENCES files ( _id ) ON DELETE CASCADE\n" +
            "                                              ON UPDATE CASCADE,\n" +
            "    file_id  INTEGER NOT NULL\n" +
            "                     REFERENCES files ( _id ) ON DELETE CASCADE\n" +
            "                                              ON UPDATE CASCADE,\n" +
            "    UNIQUE ( video_id, file_id )  ON CONFLICT IGNORE \n" +
            ")";
    private static final String CREATE_SUBTITLES_INSERT_TRIGGER =
            "CREATE TRIGGER subtitle_insert\n" +
            "       AFTER INSERT ON " + SUBTITLES_TABLE_NAME + "\n" +
            "       WHEN NEW.video_id > 0\n" +
            "BEGIN\n" +
            "    UPDATE files\n" +
            "       SET subtitle_count_ext = ( \n" +
            "               SELECT count( * )\n" +
            "                 FROM subtitles\n" +
            "                WHERE video_id = NEW.video_id \n" +
            "           )\n" +
            "     WHERE _id = NEW.video_id;\n" +
            "END";
    private static final String CREATE_SUBTITLES_DELETE_TRIGGER =
            "CREATE TRIGGER subtitle_delete\n" +
            "       AFTER DELETE ON " + SUBTITLES_TABLE_NAME + "\n" +
            "       WHEN OLD.video_id > 0\n" +
            "BEGIN\n" +
            "    UPDATE files\n" +
            "       SET subtitle_count_ext = ( \n" +
            "               SELECT count( * )\n" +
            "                 FROM subtitles\n" +
            "                WHERE video_id = OLD.video_id \n" +
            "           )\n" +
            "     WHERE _id = OLD.video_id;\n" +
            "END";

    private static final String CREATE_FILES_DELETE_TABLE =
            "CREATE TABLE IF NOT EXISTS delete_files (_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE ON CONFLICT REPLACE, use_count INTEGER);";

    private static final String CREATE_VOB_INSERT_TABLE =
            "CREATE TABLE IF NOT EXISTS vob_insert (_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE ON CONFLICT REPLACE);";

    /* ---------------------------------------------------------------------- */
    /* --                    STUFF TO DROP - AUDIO REMOVED                    */
    /* ---------------------------------------------------------------------- */
    public static String[] DROP_TABLES = {
        "album_art",
        "albums",
        "artists",
        "audio_genres",
        "audio_genres_map",
        "audio_playlists",
        "audio_playlists_map",
    };
    public static String[] DROP_INDEXES = {
        "format_index",
        "titlekey_index",
        "artist_id_idx",
        "album_id_idx",
    };
    public static String[] DROP_TRIGGERS = {
        "audio_meta_cleanup",
    };
    public static String[] DROP_VIEWS = {
        "album_info",
        "artist_info",
        "artists_albums_map",
        "audio",
        "audio_genres_map_noid",
        "audio_meta",
        "search",
        "search_archos",
        "searchhelpertitle",
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

    private final Context mContext;

    public VideoOpenHelper(Context context) {
        super(context, DATABASE_NAME, new CustomCursorFactory(), DATABASE_VERSION);
        mContext = context;
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        // this runs before onCreate/onUpgrade - must happen this early
        // Turn on WAL optimization
        // TODO: test if that is good for us or not.
        db.enableWriteAheadLogging();
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        // this runs after onCreate/onUpgrade - we don't want foreign keys enabled during that phase
        // turn on foreign key support used in scraper tables
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(TAG, "Creating Database at version " + DATABASE_CREATE_VERSION);
        // create table for imported files
        db.execSQL(CREATE_FILES_IMPORT_TABLE_V21);
        db.execSQL(CREATE_FILES_IMPORT_TRIGGER_INSERT_V21);
        db.execSQL(CREATE_FILES_IMPORT_TRIGGER_DELETE_V20);
        // create table for files scanned locally
        db.execSQL(CREATE_FILES_SCANNED_TABLE_V32);
        db.execSQL(CREATE_FILES_SCANNED_TRIGGER_INSERT_V32);
        db.execSQL(CREATE_FILES_SCANNED_TRIGGER_DELETE);
        // create table that holds scanned/imported + added information about imported & scanned files
        db.execSQL(CREATE_FILES_TABLE_V32);

        // these are new tables starting v35 and are required for the CREATE_FILES_TRIGGER_VOB_*
        db.execSQL(CREATE_FILES_DELETE_TABLE);
        db.execSQL(CREATE_VOB_INSERT_TABLE);

        db.execSQL(CREATE_FILES_TRIGGER_VOB_INSERT);
        db.execSQL(CREATE_FILES_TRIGGER_VOB_DELETE);
        db.execSQL(CREATE_FILES_TRIGGER_VOB_UPDATE);
        // add triggers that delete scraper info
        db.execSQL(CREATE_FILES_TRIGGER_SCRAPER_MOVIE_CLEANUP);
        db.execSQL(CREATE_FILES_TRIGGER_SCRAPER_EPISODE_CLEANUP);
        // trigger to update upnp data
        db.execSQL(CREATE_FILES_SCANNED_TRIGGER_UPDATE_URI);
        // indices for files extra
        db.execSQL(CREATE_FILES_IDX_MEDIA_TYPE);
        db.execSQL(CREATE_FILES_IDX_TITLE);
        db.execSQL(CREATE_FILES_IDX_BUCKET_INDEX);
        db.execSQL(CREATE_FILES_IDX_BUCKET_NAME);
        db.execSQL(CREATE_FILES_IDX_PARENT);
        db.execSQL(CREATE_FILES_IDX_PATH);

        // create table that has info about video thumbs
        db.execSQL(CREATE_VIDEOTHUMBNAIL_TABLE);
        db.execSQL(CREATE_VIDEOTHUMBNAIL_TRIGGER_CLEANUP);
        db.execSQL(CREATE_VIDEOTHUMBNAIL_IDX_VIDEO_ID);

        // create table that has info about smb servers
        db.execSQL(CREATE_SMB_SERVER_TABLE);
        db.execSQL(CREATE_SMB_SERVER_ACTIVE_VIEW);
        // also create an index for smb_server to avoid auto index of the same
        db.execSQL("CREATE INDEX smb_server_active_idx ON smb_server (active)");

        // also create all the scraper tables.
        ScraperTables.create(db);

        // video view that includes scraper data
        db.execSQL(CREATE_VIDEO_VIEW_V32);

        // no longer unique _data column, bucket_id added.
        db.execSQL(CREATE_SUBTITLES_TABLE_V17);
        // also recreate triggers
        db.execSQL(CREATE_SUBTITLES_INSERT_TRIGGER);
        db.execSQL(CREATE_SUBTITLES_DELETE_TRIGGER);

        // V17 also add trigger that forwards update of volume hidden from files_import to files
        db.execSQL(CREATE_FILES_IMPORT_TRIGGER_UPDATE_V21);

        // V21 set up a view that when inserting a storage_id deletes & update volume hidden
        db.execSQL(CREATE_HIDE_VOLUMES_VIEW);
        db.execSQL(CREATE_HIDE_VOLUMES_TRIGGER);
        // add in index that covers the hidden states that are typical for queries on Video
        db.execSQL(CREATE_FILES_HIDDEN_IDX);

        ListTables.upgradeTo(db, 34);

        db.execSQL(ScraperTables.VIEW_SEASONS_CREATE);

        onUpgrade(db, DATABASE_CREATE_VERSION, DATABASE_VERSION);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // create db sets initial version to 10
        // TODO: nova first release is has db version 34, reimport upgrade into create, do not forget mirror action on ScraperTables
        Log.d(TAG, "Upgrading Database from " + oldVersion + " to " + newVersion);
        if (oldVersion < DATABASE_CREATE_VERSION) {
            Log.d(TAG, "Upgrade not supported for version " + oldVersion + ", recreating the database.");
            // triggers database deletion
            deleteDatabase();
        }
        if (oldVersion < 37) {
			SQLiteUtils.dropView(db, VIDEO_VIEW_NAME);
			ScraperTables.upgradeTo(db, 37);
			db.execSQL(CREATE_VIDEO_VIEW_V37);
        }
    }

    private static final String[] PROJECTION = {
        MediaColumns.DATA,                      //0
        VideoColumns.ARCHOS_MEDIA_SCRAPER_ID,   //1
        VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE, //2
        VideoColumns.SCRAPER_S_NAME,            //3
        VideoColumns.SCRAPER_BACKDROP_URL,      //4
    };
    private static final String SELECTION = VideoColumns.SCRAPER_BACKDROP_URL + " IS NOT NULL AND " +
            VideoColumns.ARCHOS_MEDIA_SCRAPER_ID + " > 0";
    private static final String SELECTION_ID = BaseColumns._ID + "=?";
    private static final String SHOW_LARGE =
            "https://www.thetvdb.com/banners/fanart/original/";
    private static final String SHOW_THUMB =
            "https://www.thetvdb.com/banners/_cache/fanart/original/";
    private static final String MOVIE_LARGE =
            "https://cf2.imgobject.com/t/p/w1280/";
    private static final String MOVIE_THUMB =
            "https://cf2.imgobject.com/t/p/w300/";
    /** Converts all backdrop urls already in the db to the new format */
    private static void convertBackdrops(SQLiteDatabase db, Context context) {
        if (DBG) Log.d(TAG, "convertBackdrops");
        Cursor c = db.query(VIDEO_VIEW_NAME, PROJECTION, SELECTION, null, null, null, null);
        if (c != null) {
            if (DBG) Log.d(TAG, "convertBackdrops - found " + c.getCount());
            while (c.moveToNext()) {
                String data = c.getString(0);
                long id = c.getLong(1);
                int type = c.getInt(2);
                String sName = c.getString(3);
                String bdUrl = c.getString(4);
                if (type == ScraperStore.SCRAPER_TYPE_MOVIE) {
                    ScraperImage image = new ScraperImage(Type.MOVIE_BACKDROP, data);
                    image.setLargeUrl(bdUrl);
                    String bdTUrl = bdUrl;
                    if (bdTUrl.startsWith(MOVIE_LARGE)) {
                        bdTUrl = MOVIE_THUMB + bdTUrl.substring(MOVIE_LARGE.length());
                    }
                    image.setThumbUrl(bdTUrl);
                    image.generateFileNames(context);
                    ContentValues cv = image.toContentValues(id);
                    long imageId = db.insert(ScraperTables.MOVIE_BACKDROPS_TABLE_NAME,
                            BaseColumns._ID, cv);
                    if (DBG) Log.d(TAG, "convertBackdrops - " + image.toString() + " imageId:"  + imageId);
                    if (imageId > 0) {
                        ContentValues update = new ContentValues();
                        update.put(ScraperStore.Movie.BACKDROP_ID, Long.valueOf(imageId));
                        update.put(ScraperStore.Movie.BACKDROP, image.getLargeFile());
                        String[] whereArgs = { String.valueOf(id) };
                        int upd = db.update(ScraperTables.MOVIE_TABLE_NAME, update, SELECTION_ID, whereArgs);
                        if (DBG) Log.d(TAG, "convertBackdrops - update table result:"  + upd);
                    }
                } else if (type == ScraperStore.SCRAPER_TYPE_SHOW) {
                    ScraperImage image = new ScraperImage(Type.SHOW_BACKDROP, sName);
                    image.setLargeUrl(bdUrl);
                    String bdTUrl = bdUrl;
                    if (bdTUrl.startsWith(SHOW_LARGE)) {
                        bdTUrl = SHOW_THUMB + bdTUrl.substring(SHOW_LARGE.length());
                    }
                    image.setThumbUrl(bdTUrl);
                    image.generateFileNames(context);
                    ContentValues cv = image.toContentValues(id);
                    long imageId = db.insert(ScraperTables.SHOW_BACKDROPS_TABLE_NAME,
                            BaseColumns._ID, cv);
                    if (DBG) Log.d(TAG, "convertBackdrops - " + image.toString() + " imageId:"  + imageId);
                    if (imageId > 0) {
                        ContentValues update = new ContentValues();
                        update.put(ScraperStore.Show.BACKDROP_ID, Long.valueOf(imageId));
                        update.put(ScraperStore.Show.BACKDROP, image.getLargeFile());
                        String[] whereArgs = { String.valueOf(id) };
                        int upd = db.update(ScraperTables.SHOW_TABLE_NAME, update, SELECTION_ID, whereArgs);
                        if (DBG) Log.d(TAG, "convertBackdrops - update table result:"  + upd);
                    }
                }
            }
            c.close();
        }
    }

    private void processVideoNamesInDB(SQLiteDatabase db) {
        Cursor c = db.query(FILES_TABLE_NAME, new String[] {MediaColumns.DATA, "_id"}, "media_type like " + FileColumns.MEDIA_TYPE_VIDEO, null, null, null, null);
        if (c == null) {
            return;
        }

        while (c.moveToNext()) {
            String path = c.getString(0);
            long id = c.getLong(1);
            ContentValues cvExtra = VideoNameProcessor.extractValuesFromPath(path);
            db.update(FILES_TABLE_NAME, cvExtra, SELECTION_ID, new String[] { String.valueOf(id) });
        }

        if (c != null) {
            c.close();
        }
    }

    /**
     * To be used for debug only
     * @param context
     * @return the SQL DB file
     */
    static public File getDatabaseFile(Context context) {
        return context.getDatabasePath(DATABASE_NAME);
    }
}
