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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.archos.mediaprovider.SQLiteUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ScraperTables {

    private static final Logger log = LoggerFactory.getLogger(ScraperTables.class);

    private ScraperTables() { /* empty */ }

    /*
     * List of tables available
     */
    public static final String MOVIE_TABLE_NAME = "MOVIE";
    public static final String SHOW_TABLE_NAME = "SHOW";
    public static final String EPISODE_TABLE_NAME = "EPISODE";
    public static final String ACTORS_TABLE_NAME = "ACTOR";
    public static final String GENRES_TABLE_NAME = "GENRE";
    public static final String DIRECTORS_TABLE_NAME = "DIRECTOR";
    public static final String WRITERS_TABLE_NAME = "WRITER";
    public static final String STUDIOS_TABLE_NAME = "STUDIO";
    public static final String FILMS_MOVIE_TABLE_NAME = "FILMS_MOVIE";
    public static final String WRITERS_MOVIE_TABLE_NAME = "WRITERS_MOVIE";
    public static final String FILMS_SHOW_TABLE_NAME = "FILMS_SHOW";
    public static final String WRITERS_SHOW_TABLE_NAME = "WRITERS_SHOW";
    public static final String FILMS_EPISODE_TABLE_NAME = "FILMS_EPISODE";
    public static final String WRITERS_EPISODE_TABLE_NAME = "WRITERS_EPISODE";
    public static final String GUESTS_TABLE_NAME = "GUESTS";
    public static final String PRODUCES_MOVIE_TABLE_NAME = "PRODUCES_MOVIE";
    public static final String PRODUCES_SHOW_TABLE_NAME = "PRODUCES_SHOW";
    public static final String PLAYS_MOVIE_TABLE_NAME = "PLAYS_MOVIE";
    public static final String PLAYS_SHOW_TABLE_NAME = "PLAYS_SHOW";
    public static final String BELONGS_MOVIE_TABLE_NAME = "BELONGS_MOVIE";
    public static final String BELONGS_SHOW_TABLE_NAME = "BELONGS_SHOW";

    /*
     * List of views in the db
     */
    public static final String GUESTS_VIEW_NAME = "V_GUESTS";
    public static final String PLAYS_SHOW_VIEW_NAME = "V_PLAYS_SHOW";
    public static final String PLAYS_MOVIE_VIEW_NAME = "V_PLAYS_MOVIE";
    public static final String FILMS_MOVIE_VIEW_NAME = "V_FILMS_MOVIE";
    public static final String FILMS_SHOW_VIEW_NAME = "V_FILMS_SHOW";
    public static final String FILMS_EPISODE_VIEW_NAME = "V_FILMS_EPISODE";

    public static final String WRITERS_MOVIE_VIEW_NAME = "V_WRITERS_MOVIE";
    public static final String WRITERS_SHOW_VIEW_NAME = "V_WRITERS_SHOW";
    public static final String WRITERS_EPISODE_VIEW_NAME = "V_WRITERS_EPISODE";

    public static final String PRODUCES_MOVIE_VIEW_NAME = "V_PRODUCES_MOVIE";
    public static final String PRODUCES_SHOW_VIEW_NAME = "V_PRODUCES_SHOW";
    public static final String BELONGS_MOVIE_VIEW_NAME = "V_BELONGS_MOVIE";
    public static final String BELONGS_SHOW_VIEW_NAME = "V_BELONGS_SHOW";
    public static final String ALL_VIDEOS_VIEW_NAME = "v_all_videos";
    public static final String SEASONS_VIEW_NAME = "v_seasons";
    // these help deleting, only used internal
    public static final String ACTOR_DELETABLE_VIEW_NAME = "v_actor_deletable";
    public static final String DIRECTOR_DELETABLE_VIEW_NAME = "v_director_deletable";
    public static final String WRITER_DELETABLE_VIEW_NAME = "v_writer_deletable";
    public static final String GENRE_DELETABLE_VIEW_NAME = "v_genre_deletable";
    public static final String STUDIO_DELETABLE_VIEW_NAME = "v_studio_deletable";
    
    // Performance indexes for scraper tables to optimize joins in video view
    private static final String CREATE_EPISODE_SEASON_EPISODE_IDX =
            "CREATE INDEX IF NOT EXISTS idx_episode_season_episode ON " + EPISODE_TABLE_NAME + 
            "(show_episode, season_episode, number_episode)";
    private static final String CREATE_MOVIE_YEAR_IDX =
            "CREATE INDEX IF NOT EXISTS idx_movie_year ON " + MOVIE_TABLE_NAME + 
            "(" + ScraperStore.Movie.YEAR + ")";
    private static final String CREATE_MOVIE_RATING_IDX =
            "CREATE INDEX IF NOT EXISTS idx_movie_rating ON " + MOVIE_TABLE_NAME + 
            "(" + ScraperStore.Movie.RATING + ")";
    private static final String CREATE_EPISODE_VIDEO_ID_IDX =
            "CREATE INDEX IF NOT EXISTS idx_episode_video_id ON " + EPISODE_TABLE_NAME + 
            "(" + ScraperStore.Episode.VIDEO_ID + ")";
    private static final String CREATE_MOVIE_VIDEO_ID_IDX =
            "CREATE INDEX IF NOT EXISTS idx_movie_video_id ON " + MOVIE_TABLE_NAME + 
            "(" + ScraperStore.Movie.VIDEO_ID + ")";
    
    // Additional performance indexes for ScraperTables
    private static final String CREATE_SHOW_RATING_IDX =
            "CREATE INDEX IF NOT EXISTS idx_show_rating ON " + SHOW_TABLE_NAME + 
            "(" + ScraperStore.Show.RATING + ")";
    private static final String CREATE_EPISODE_AIRED_IDX =
            "CREATE INDEX IF NOT EXISTS idx_episode_aired ON " + EPISODE_TABLE_NAME + 
            "(" + ScraperStore.Episode.AIRED + ") WHERE " + ScraperStore.Episode.AIRED + " > 0";
    private static final String CREATE_MOVIE_COLLECTION_IDX =
            "CREATE INDEX IF NOT EXISTS idx_movie_collection ON " + MOVIE_TABLE_NAME + 
            "(m_coll_id) WHERE m_coll_id > 0";

    // WatchingUpNextLoader performance indexes for version 47
    private static final String CREATE_EPISODE_SERIES_ORDERING_IDX =
            "CREATE INDEX IF NOT EXISTS idx_episode_series_ordering ON " + EPISODE_TABLE_NAME + 
            "(show_episode, season_episode, number_episode)";
    private static final String CREATE_MOVIE_COLLECTION_YEAR_IDX =
            "CREATE INDEX IF NOT EXISTS idx_movie_collection_year ON " + MOVIE_TABLE_NAME + 
            "(m_coll_id, year_movie) WHERE m_coll_id IS NOT NULL";
    
    // Additional indexes for getSortOrder() subqueries in WatchingUpNextLoader
    private static final String CREATE_EPISODE_WATCHED_ORDERING_IDX =
            "CREATE INDEX IF NOT EXISTS idx_episode_watched_ordering ON " + EPISODE_TABLE_NAME + 
            "(show_episode, season_episode DESC, number_episode DESC)";
    private static final String CREATE_MOVIE_WATCHED_ORDERING_IDX =
            "CREATE INDEX IF NOT EXISTS idx_movie_watched_ordering ON " + MOVIE_TABLE_NAME + 
            "(m_coll_id, year_movie DESC) WHERE m_coll_id IS NOT NULL";

    /*
     * Columns names that we need and are not to be exposed.
     * Public ones are in the ScraperStore class.
     */
    private static final String FILMS_MOVIE_ID_MOVIE = "movie_films";
    private static final String FILMS_MOVIE_ID_DIRECTOR = "director_films";

    private static final String WRITERS_MOVIE_ID_MOVIE = "movie_writers";
    private static final String WRITERS_MOVIE_ID_WRITER = "writer_writers";

    private static final String FILMS_SHOW_ID_SHOW = "show_films";
    private static final String FILMS_SHOW_ID_DIRECTOR = "director_films";

    private static final String WRITERS_SHOW_ID_SHOW = "show_writers";
    private static final String WRITERS_SHOW_ID_WRITER = "writer_writers";

    private static final String FILMS_EPISODE_ID_DIRECTOR = "director_films";
    private static final String FILMS_EPISODE_ID_EPISODE = "episode_films";

    private static final String WRITERS_EPISODE_ID_WRITER = "writer_writers";
    private static final String WRITERS_EPISODE_ID_EPISODE = "episode_writers";

    private static final String PRODUCES_MOVIE_ID_MOVIE = "movie_produces";
    private static final String PRODUCES_MOVIE_ID_STUDIO = "studio_produces";

    private static final String PRODUCES_SHOW_ID_SHOW = "show_produces";
    private static final String PRODUCES_SHOW_ID_STUDIO = "studio_produces";

    private static final String PLAYS_MOVIE_ID_ACTOR = "actor_plays";
    private static final String PLAYS_MOVIE_ROLE = "role_plays";
    private static final String PLAYS_MOVIE_ID_MOVIE = "movie_plays";

    private static final String PLAYS_SHOW_ID_SHOW = "show_plays";
    private static final String PLAYS_SHOW_ROLE = "role_plays";
    private static final String PLAYS_SHOW_ID_ACTOR = "actor_plays";

    private static final String BELONGS_MOVIE_ID_GENRE = "genre_belongs";
    private static final String BELONGS_MOVIE_ID_MOVIE = "movie_belongs";

    private static final String GUESTS_ID_EPISODE = "episode_guests";
    private static final String GUESTS_ID_ACTOR = "actor_guests";
    private static final String GUESTS_ROLE = "role_guests";

    private static final String BELONGS_SHOW_ID_SHOW = "show_belongs";
    private static final String BELONGS_SHOW_ID_GENRE = "genre_belongs";

    /*
     * List of requests to create the tables, views and triggers.
     */
    private static final String MOVIE_TABLE_CREATE =
        "CREATE TABLE " + MOVIE_TABLE_NAME + " (" +
        ScraperStore.Movie.ID + " INTEGER PRIMARY KEY NOT NULL," +
        ScraperStore.Movie.VIDEO_ID + " INTEGER NOT NULL UNIQUE ON CONFLICT REPLACE REFERENCES " +
                VideoOpenHelper.FILES_TABLE_NAME + "(remote_id) ON DELETE CASCADE ON UPDATE CASCADE," +
        ScraperStore.Movie.NAME + " TEXT," +
        ScraperStore.Movie.YEAR + " INTEGER," +
        ScraperStore.Movie.RATING + " FLOAT," +
        ScraperStore.Movie.PLOT + " TEXT," +
        ScraperStore.Movie.COVER + " TEXT," +
        "overview_movie TEXT," +
        ScraperStore.Movie.BACKDROP_URL + " TEXT," +
        ScraperStore.Movie.BACKDROP + " TEXT," +
        "m_backdrop_id INTEGER,"  + // movie has backdrop + poster
        "m_poster_id INTEGER," +
        "m_online_id INTEGER," + // also the id in the online db "1858" - http://www.themoviedb.org/movie/1858
        "m_imdb_id TEXT," + // and the imdb id e.g. "tt0285331" - http://www.imdb.com/title/tt0285331
        "m_content_rating TEXT," + // also content rating e.g. "PG-13"
        ScraperStore.Movie.ACTORS_FORMATTED + " TEXT," +
        ScraperStore.Movie.DIRECTORS_FORMATTED + " TEXT," +
        ScraperStore.Movie.GERNES_FORMATTED + " TEXT," +
        ScraperStore.Movie.STUDIOS_FORMATTED + " TEXT" +
        ")";

    private static final String SHOW_TABLE_CREATE =
        "CREATE TABLE " + SHOW_TABLE_NAME + " (" +
        ScraperStore.Show.ID + " INTEGER PRIMARY KEY NOT NULL," +
        ScraperStore.Show.NAME + " TEXT UNIQUE," + // Remark: this should not be unique, online_id should
        ScraperStore.Show.COVER + " TEXT," +
        ScraperStore.Show.PREMIERED + " INTEGER," +
        ScraperStore.Show.RATING + " FLOAT," +
        ScraperStore.Show.PLOT + " TEXT," +
        ScraperStore.Show.BACKDROP_URL + " TEXT," +
        ScraperStore.Show.BACKDROP + " TEXT," +
        "s_backdrop_id INTEGER," + // show has backdrop + poster
        "s_poster_id INTEGER," +
        "s_online_id INTEGER," + // also the id in the online db "73255" - http://thetvdb.com/?tab=series&id=73255
        "s_imdb_id TEXT," + // and the imdb id e.g. "tt0285331" - http://www.imdb.com/title/tt0285331
        "s_content_rating TEXT," + // also content rating e.g. "TV-14"
        ScraperStore.Show.ACTORS_FORMATTED + " TEXT," +
        ScraperStore.Show.DIRECTORS_FORMATTED + " TEXT," +
        ScraperStore.Show.GERNES_FORMATTED + " TEXT," +
        ScraperStore.Show.STUDIOS_FORMATTED + " TEXT" +
        ")";

    private static final String EPISODE_TABLE_CREATE =
        "CREATE TABLE " + EPISODE_TABLE_NAME + " (" +
        ScraperStore.Episode.ID + " INTEGER PRIMARY KEY NOT NULL," +
        ScraperStore.Episode.VIDEO_ID + " INTEGER NOT NULL UNIQUE ON CONFLICT REPLACE REFERENCES " +
                VideoOpenHelper.FILES_TABLE_NAME + "(remote_id) ON DELETE CASCADE ON UPDATE CASCADE," +
        ScraperStore.Episode.SHOW + " INTEGER REFERENCES " + SHOW_TABLE_NAME +
                " ON DELETE RESTRICT ON UPDATE CASCADE," +
        ScraperStore.Episode.NAME + " TEXT," +
        ScraperStore.Episode.AIRED + " INTEGER," +
        ScraperStore.Episode.RATING + " FLOAT," +
        ScraperStore.Episode.PLOT + " TEXT," +
        ScraperStore.Episode.NUMBER + " INTEGER," +
        ScraperStore.Episode.SEASON + " INTEGER," +
        ScraperStore.Episode.COVER + " TEXT," +
        "e_poster_id INTEGER," + // episode has a poster too
        "e_online_id INTEGER," + // also the id in the online db "306192" - http://thetvdb.com/?tab=episode&seriesid=73255&id=306192
        "e_imdb_id TEXT," + // and the imdb id e.g. "tt0285331" - http://www.imdb.com/title/tt0285331
        ScraperStore.Episode.ACTORS_FORMATTED + " TEXT," +
        ScraperStore.Episode.DIRECTORS_FORMATTED + " TEXT," +
        ScraperStore.Episode.PICTURE + " TEXT" +
        ")";

    private static final String ACTORS_TABLE_CREATE =
        "CREATE TABLE " + ACTORS_TABLE_NAME + " (" +
        ScraperStore.Actor.ID + " INTEGER PRIMARY KEY NOT NULL," +
        ScraperStore.Actor.NAME + " TEXT UNIQUE," +
        ScraperStore.Actor.COUNT + " INTEGER)";

    private static final String GENRES_TABLE_CREATE =
        "CREATE TABLE " + GENRES_TABLE_NAME + " (" +
        ScraperStore.Genre.ID + " INTEGER PRIMARY KEY NOT NULL," +
        ScraperStore.Genre.NAME + " TEXT UNIQUE," +
        ScraperStore.Genre.COUNT + " INTEGER)";

    private static final String DIRECTORS_TABLE_CREATE =
        "CREATE TABLE " + DIRECTORS_TABLE_NAME + " (" +
        ScraperStore.Director.ID + " INTEGER PRIMARY KEY NOT NULL," +
        ScraperStore.Director.NAME + " TEXT UNIQUE," +
        ScraperStore.Director.COUNT + " INTEGER)";

    private static final String WRITERS_TABLE_CREATE =
            "CREATE TABLE " + WRITERS_TABLE_NAME + " (" +
                    ScraperStore.Writer.ID + " INTEGER PRIMARY KEY NOT NULL," +
                    ScraperStore.Writer.NAME + " TEXT UNIQUE," +
                    ScraperStore.Writer.COUNT + " INTEGER)";

    private static final String STUDIOS_TABLE_CREATE =
        "CREATE TABLE " + STUDIOS_TABLE_NAME + " (" +
        ScraperStore.Studio.ID + " INTEGER PRIMARY KEY NOT NULL," +
        ScraperStore.Studio.NAME + " TEXT UNIQUE," +
        ScraperStore.Studio.COUNT + " INTEGER)";

    /*
     *  Tables associating movie, show and episode tables with directors
     */
    private static final String FILMS_MOVIE_TABLE_CREATE =
        "CREATE TABLE " + FILMS_MOVIE_TABLE_NAME + " (" +
        FILMS_MOVIE_ID_MOVIE + " INTEGER REFERENCES " + MOVIE_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
        FILMS_MOVIE_ID_DIRECTOR + " INTEGER REFERENCES " + DIRECTORS_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
        "PRIMARY KEY(" + FILMS_MOVIE_ID_MOVIE + "," +
        FILMS_MOVIE_ID_DIRECTOR + "))";

    private static final String FILMS_SHOW_TABLE_CREATE =
        "CREATE TABLE " + FILMS_SHOW_TABLE_NAME + " (" +
        FILMS_SHOW_ID_SHOW + " INTEGER REFERENCES " + SHOW_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
        FILMS_SHOW_ID_DIRECTOR + " INTEGER REFERENCES " + DIRECTORS_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
        "PRIMARY KEY(" + FILMS_SHOW_ID_SHOW + "," +
        FILMS_SHOW_ID_DIRECTOR + "))";

    private static final String FILMS_EPISODE_TABLE_CREATE =
        "CREATE TABLE " + FILMS_EPISODE_TABLE_NAME + " (" +
        FILMS_EPISODE_ID_EPISODE + " INTEGER REFERENCES " + EPISODE_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
        FILMS_EPISODE_ID_DIRECTOR + " INTEGER REFERENCES " + DIRECTORS_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
        "PRIMARY KEY(" + FILMS_EPISODE_ID_EPISODE + "," +
        FILMS_EPISODE_ID_DIRECTOR + "))";


    /*
     *  Tables associating movie, show and episode tables with writers
     */
    private static final String WRITERS_MOVIE_TABLE_CREATE =
            "CREATE TABLE " + WRITERS_MOVIE_TABLE_NAME + " (" +
                    WRITERS_MOVIE_ID_MOVIE + " INTEGER REFERENCES " + MOVIE_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
                    WRITERS_MOVIE_ID_WRITER + " INTEGER REFERENCES " + WRITERS_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
                    "PRIMARY KEY(" + WRITERS_MOVIE_ID_MOVIE + "," +
                    WRITERS_MOVIE_ID_WRITER + "))";

    private static final String WRITERS_SHOW_TABLE_CREATE =
            "CREATE TABLE " + WRITERS_SHOW_TABLE_NAME + " (" +
                    WRITERS_SHOW_ID_SHOW + " INTEGER REFERENCES " + SHOW_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
                    WRITERS_SHOW_ID_WRITER + " INTEGER REFERENCES " + WRITERS_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
                    "PRIMARY KEY(" + WRITERS_SHOW_ID_SHOW + "," +
                    WRITERS_SHOW_ID_WRITER + "))";

    private static final String WRITERS_EPISODE_TABLE_CREATE =
            "CREATE TABLE " + WRITERS_EPISODE_TABLE_NAME + " (" +
                    WRITERS_EPISODE_ID_EPISODE + " INTEGER REFERENCES " + EPISODE_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
                    WRITERS_EPISODE_ID_WRITER + " INTEGER REFERENCES " + WRITERS_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
                    "PRIMARY KEY(" + WRITERS_EPISODE_ID_EPISODE + "," +
                    WRITERS_EPISODE_ID_WRITER + "))";

    /*
     * Tables associating movie and show tables to studios
     */
    private static final String PRODUCES_MOVIE_TABLE_CREATE =
        "CREATE TABLE " + PRODUCES_MOVIE_TABLE_NAME + " (" +
        PRODUCES_MOVIE_ID_MOVIE + " INTEGER REFERENCES " + MOVIE_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
        PRODUCES_MOVIE_ID_STUDIO + " INTEGER REFERENCES " + STUDIOS_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
        "PRIMARY KEY(" + PRODUCES_MOVIE_ID_MOVIE + "," +
        PRODUCES_MOVIE_ID_STUDIO + "))";

    private static final String PRODUCES_SHOW_TABLE_CREATE =
        "CREATE TABLE " + PRODUCES_SHOW_TABLE_NAME + " (" +
        PRODUCES_SHOW_ID_SHOW + " INTEGER REFERENCES " + SHOW_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
        PRODUCES_SHOW_ID_STUDIO + " INTEGER REFERENCES " + STUDIOS_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
        "PRIMARY KEY(" + PRODUCES_SHOW_ID_SHOW + "," +
        PRODUCES_SHOW_ID_STUDIO + "))";

    /*
     *  Tables associating movie, show and episode tables with actors
     */
    private static final String PLAYS_MOVIE_TABLE_CREATE =
        "CREATE TABLE " + PLAYS_MOVIE_TABLE_NAME + " (" +
        PLAYS_MOVIE_ID_MOVIE + " INTEGER REFERENCES " + MOVIE_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
        PLAYS_MOVIE_ID_ACTOR + " INTEGER REFERENCES " + ACTORS_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
        PLAYS_MOVIE_ROLE + " TEXT," +
        "PRIMARY KEY(" + PLAYS_MOVIE_ID_MOVIE + "," +
        PLAYS_MOVIE_ID_ACTOR + "))";

    private static final String PLAYS_SHOW_TABLE_CREATE =
        "CREATE TABLE " + PLAYS_SHOW_TABLE_NAME + " (" +
        PLAYS_SHOW_ID_SHOW + " INTEGER REFERENCES " + SHOW_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
        PLAYS_SHOW_ID_ACTOR + " INTEGER REFERENCES " + ACTORS_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
        PLAYS_SHOW_ROLE + " TEXT," +
        "PRIMARY KEY(" + PLAYS_SHOW_ID_SHOW + "," +
        PLAYS_SHOW_ID_ACTOR + "))";

    private static final String GUESTS_TABLE_CREATE =
        "CREATE TABLE " + GUESTS_TABLE_NAME + " (" +
        GUESTS_ID_EPISODE + " INTEGER REFERENCES " + EPISODE_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
        GUESTS_ID_ACTOR + " INTEGER REFERENCES " + ACTORS_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
        GUESTS_ROLE + " TEXT," +
        "PRIMARY KEY(" + GUESTS_ID_EPISODE + "," +
        GUESTS_ID_ACTOR + "))";

    /*
     *  Tables associating movie and show tables with genres
     */
    private static final String BELONGS_MOVIE_TABLE_CREATE =
        "CREATE TABLE " + BELONGS_MOVIE_TABLE_NAME + " (" +
        BELONGS_MOVIE_ID_MOVIE + " INTEGER REFERENCES " + MOVIE_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
        BELONGS_MOVIE_ID_GENRE + " INTEGER REFERENCES " + GENRES_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
        "PRIMARY KEY(" + BELONGS_MOVIE_ID_MOVIE + "," +
        BELONGS_MOVIE_ID_GENRE + "))";

    private static final String BELONGS_SHOW_TABLE_CREATE =
        "CREATE TABLE " + BELONGS_SHOW_TABLE_NAME + " (" +
        BELONGS_SHOW_ID_SHOW + " INTEGER REFERENCES " + SHOW_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
        BELONGS_SHOW_ID_GENRE + " INTEGER REFERENCES " + GENRES_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
        "PRIMARY KEY(" + BELONGS_SHOW_ID_SHOW + "," +
        BELONGS_SHOW_ID_GENRE + "))";

    /*
     * Views joining the above tables, and insert triggers to allow insertion on
     * views. These views are exposed and used by the ContentProvider, as it
     * avoids doing manual joints every time and multiple insertions.
     */
    private static final String GUESTS_VIEW_CREATE =
        "CREATE VIEW " + GUESTS_VIEW_NAME + " AS SELECT " +
        GUESTS_TABLE_NAME + "." +
        GUESTS_ID_EPISODE + " AS " + ScraperStore.Episode.Actor.EPISODE + ", " +
        ACTORS_TABLE_NAME + "." +
        ScraperStore.Actor.ID + " AS " + ScraperStore.Episode.Actor.ACTOR + ", " +
        ACTORS_TABLE_NAME + "." +
        ScraperStore.Actor.NAME + " AS " + ScraperStore.Episode.Actor.NAME + ", " +
        GUESTS_TABLE_NAME + "." +
        GUESTS_ROLE + " AS " + ScraperStore.Episode.Actor.ROLE + " FROM " +
        GUESTS_TABLE_NAME + " LEFT JOIN " + ACTORS_TABLE_NAME +
        " ON (" + GUESTS_TABLE_NAME + "." + GUESTS_ID_ACTOR +
        " = " + ACTORS_TABLE_NAME + "." + ScraperStore.Actor.ID + ")";

    private static final String GUESTS_VIEW_INSERT_TRIGGER =
        "CREATE TRIGGER insert_guests INSTEAD OF INSERT ON " + GUESTS_VIEW_NAME +
        " BEGIN " +
        "INSERT OR IGNORE INTO " + ACTORS_TABLE_NAME + " ( " + ScraperStore.Actor.NAME + " ) " +
        "VALUES (NEW." + ScraperStore.Episode.Actor.NAME + "); " +
        "INSERT INTO " + GUESTS_TABLE_NAME +
            " ( " + GUESTS_ID_EPISODE + ", " + GUESTS_ROLE +
            ", " + GUESTS_ID_ACTOR + " ) " +
        "SELECT NEW." + ScraperStore.Episode.Actor.EPISODE + ", " + "NEW." + ScraperStore.Episode.Actor.ROLE + ", " +
            ACTORS_TABLE_NAME + "." + ScraperStore.Actor.ID + " " +
        " FROM " + ACTORS_TABLE_NAME +
        " WHERE " + ScraperStore.Actor.NAME + " = NEW." + ScraperStore.Episode.Actor.NAME + "; " +
        "END";

    private static final String PLAYS_SHOW_VIEW_CREATE =
        "CREATE VIEW " + PLAYS_SHOW_VIEW_NAME + " AS SELECT " +
        PLAYS_SHOW_TABLE_NAME + "." +
        PLAYS_SHOW_ID_SHOW + " AS " + ScraperStore.Show.Actor.SHOW + ", " +
        ACTORS_TABLE_NAME + "." +
        ScraperStore.Actor.ID + " AS " + ScraperStore.Show.Actor.ACTOR + ", " +
        ACTORS_TABLE_NAME + "." +
        ScraperStore.Actor.NAME + " AS " + ScraperStore.Show.Actor.NAME + ", " +
        PLAYS_SHOW_TABLE_NAME + "." +
        PLAYS_SHOW_ROLE + " AS " + ScraperStore.Show.Actor.ROLE + " FROM " +
        PLAYS_SHOW_TABLE_NAME + " LEFT JOIN " + ACTORS_TABLE_NAME +
        " ON (" + PLAYS_SHOW_TABLE_NAME + "." + PLAYS_SHOW_ID_ACTOR +
        " = " + ACTORS_TABLE_NAME + "." + ScraperStore.Actor.ID + ")";

    private static final String PLAYS_SHOW_VIEW_INSERT_TRIGGER_NAME = "insert_plays_show";
    private static final String PLAYS_SHOW_VIEW_INSERT_TRIGGER =
        "CREATE TRIGGER " + PLAYS_SHOW_VIEW_INSERT_TRIGGER_NAME + " INSTEAD OF INSERT ON " + PLAYS_SHOW_VIEW_NAME +
        " BEGIN " +
        "INSERT OR IGNORE INTO " + ACTORS_TABLE_NAME + " ( " + ScraperStore.Actor.NAME + " ) " +
        "VALUES (NEW." + ScraperStore.Show.Actor.NAME + "); " +
        "INSERT OR REPLACE INTO " + PLAYS_SHOW_TABLE_NAME +
            " ( " + PLAYS_SHOW_ID_SHOW + ", " + PLAYS_SHOW_ROLE +
            ", " + PLAYS_SHOW_ID_ACTOR + " ) " +
        "SELECT NEW." + ScraperStore.Show.Actor.SHOW + ", " + "NEW." + ScraperStore.Show.Actor.ROLE + ", " +
            ACTORS_TABLE_NAME + "." + ScraperStore.Actor.ID + " " +
        " FROM " + ACTORS_TABLE_NAME +
        " WHERE " + ScraperStore.Actor.NAME + " = NEW." + ScraperStore.Show.Actor.NAME + "; " +
        "END";

    private static final String PLAYS_MOVIE_VIEW_CREATE =
        "CREATE VIEW " + PLAYS_MOVIE_VIEW_NAME + " AS SELECT " +
        PLAYS_MOVIE_TABLE_NAME + "." +
        PLAYS_MOVIE_ID_MOVIE + " AS " + ScraperStore.Movie.Actor.MOVIE + ", " +
        ACTORS_TABLE_NAME + "." +
        ScraperStore.Actor.ID + " AS " + ScraperStore.Movie.Actor.ACTOR + ", " +
        ACTORS_TABLE_NAME + "." +
        ScraperStore.Actor.NAME + " AS " + ScraperStore.Movie.Actor.NAME + ", " +
        PLAYS_MOVIE_TABLE_NAME + "." +
        PLAYS_MOVIE_ROLE + " AS " + ScraperStore.Movie.Actor.ROLE + " FROM " +
        PLAYS_MOVIE_TABLE_NAME + " LEFT JOIN " + ACTORS_TABLE_NAME +
        " ON (" + PLAYS_MOVIE_TABLE_NAME + "." + PLAYS_MOVIE_ID_ACTOR +
        " = " + ACTORS_TABLE_NAME + "." + ScraperStore.Actor.ID + ")";

    private static final String PLAYS_MOVIE_VIEW_INSERT_TRIGGER =
        "CREATE TRIGGER insert_plays_movie INSTEAD OF INSERT ON " + PLAYS_MOVIE_VIEW_NAME +
        " BEGIN " +
        "INSERT OR IGNORE INTO " + ACTORS_TABLE_NAME + " ( " + ScraperStore.Actor.NAME + " ) " +
        "VALUES (NEW." + ScraperStore.Movie.Actor.NAME + "); " +
        "INSERT INTO " + PLAYS_MOVIE_TABLE_NAME +
            " ( " + PLAYS_MOVIE_ID_MOVIE + ", " + PLAYS_MOVIE_ROLE +
            ", " + PLAYS_MOVIE_ID_ACTOR + " ) " +
        "SELECT NEW." + ScraperStore.Movie.Actor.MOVIE + ", " + "NEW." + ScraperStore.Movie.Actor.ROLE + ", " +
            ACTORS_TABLE_NAME + "." + ScraperStore.Actor.ID + " " +
        " FROM " + ACTORS_TABLE_NAME +
        " WHERE " + ScraperStore.Actor.NAME + " = NEW." + ScraperStore.Movie.Actor.NAME + "; " +
        "END";

    private static final String FILMS_MOVIE_VIEW_CREATE =
        "CREATE VIEW " + FILMS_MOVIE_VIEW_NAME + " AS SELECT " +
        FILMS_MOVIE_TABLE_NAME + "." +
        FILMS_MOVIE_ID_MOVIE + " AS " + ScraperStore.Movie.Director.MOVIE + ", " +
        DIRECTORS_TABLE_NAME + "." +
        ScraperStore.Director.NAME + " AS " + ScraperStore.Movie.Director.NAME + ", " +
        DIRECTORS_TABLE_NAME + "." +
        ScraperStore.Director.ID + " AS " + ScraperStore.Movie.Director.DIRECTOR + " FROM " +
        FILMS_MOVIE_TABLE_NAME + " LEFT JOIN " + DIRECTORS_TABLE_NAME +
        " ON (" + FILMS_MOVIE_TABLE_NAME + "." + FILMS_MOVIE_ID_DIRECTOR +
        " = " + DIRECTORS_TABLE_NAME + "." + ScraperStore.Director.ID + ")";

    private static final String FILMS_MOVIE_VIEW_INSERT_TRIGGER =
        "CREATE TRIGGER insert_films_movie INSTEAD OF INSERT ON " + FILMS_MOVIE_VIEW_NAME +
        " BEGIN " +
        "INSERT OR IGNORE INTO " + DIRECTORS_TABLE_NAME + " ( " + ScraperStore.Director.NAME + " ) " +
        "VALUES (NEW." + ScraperStore.Movie.Director.NAME + "); " +
        "INSERT INTO " + FILMS_MOVIE_TABLE_NAME +
            " ( " + FILMS_MOVIE_ID_MOVIE + "," + FILMS_MOVIE_ID_DIRECTOR + " ) " +
        "SELECT NEW." + ScraperStore.Movie.Director.MOVIE + ", " +
            DIRECTORS_TABLE_NAME + "." + ScraperStore.Director.ID + " " +
        " FROM " + DIRECTORS_TABLE_NAME +
        " WHERE " + ScraperStore.Director.NAME + " = NEW." + ScraperStore.Movie.Director.NAME + "; " +
        "END";

    private static final String WRITERS_MOVIE_VIEW_CREATE =
            "CREATE VIEW " + WRITERS_MOVIE_VIEW_NAME + " AS SELECT " +
                    WRITERS_MOVIE_TABLE_NAME + "." +
                    WRITERS_MOVIE_ID_MOVIE + " AS " + ScraperStore.Movie.Writer.MOVIE + ", " +
                    WRITERS_TABLE_NAME + "." +
                    ScraperStore.Writer.NAME + " AS " + ScraperStore.Movie.Writer.NAME + ", " +
                    WRITERS_TABLE_NAME + "." +
                    ScraperStore.Writer.ID + " AS " + ScraperStore.Movie.Writer.WRITER + " FROM " +
                    WRITERS_MOVIE_TABLE_NAME + " LEFT JOIN " + WRITERS_TABLE_NAME +
                    " ON (" + WRITERS_MOVIE_TABLE_NAME + "." + WRITERS_MOVIE_ID_WRITER +
                    " = " + WRITERS_TABLE_NAME + "." + ScraperStore.Writer.ID + ")";

    private static final String WRITERS_MOVIE_VIEW_INSERT_TRIGGER =
            "CREATE TRIGGER insert_writers_movie INSTEAD OF INSERT ON " + WRITERS_MOVIE_VIEW_NAME +
                    " BEGIN " +
                    "INSERT OR IGNORE INTO " + WRITERS_TABLE_NAME + " ( " + ScraperStore.Writer.NAME + " ) " +
                    "VALUES (NEW." + ScraperStore.Movie.Writer.NAME + "); " +
                    "INSERT INTO " + WRITERS_MOVIE_TABLE_NAME +
                    " ( " + WRITERS_MOVIE_ID_MOVIE + "," + WRITERS_MOVIE_ID_WRITER + " ) " +
                    "SELECT NEW." + ScraperStore.Movie.Writer.MOVIE + ", " +
                    WRITERS_TABLE_NAME + "." + ScraperStore.Writer.ID + " " +
                    " FROM " + WRITERS_TABLE_NAME +
                    " WHERE " + ScraperStore.Writer.NAME + " = NEW." + ScraperStore.Movie.Writer.NAME + "; " +
                    "END";

    private static final String FILMS_SHOW_VIEW_CREATE =
        "CREATE VIEW " + FILMS_SHOW_VIEW_NAME + " AS SELECT " +
        FILMS_SHOW_TABLE_NAME + "." +
        FILMS_SHOW_ID_SHOW + " AS " + ScraperStore.Show.Director.SHOW + ", " +
        DIRECTORS_TABLE_NAME + "." +
        ScraperStore.Director.NAME + " AS " + ScraperStore.Show.Director.NAME + ", " +
        DIRECTORS_TABLE_NAME + "." +
        ScraperStore.Director.ID + " AS " + ScraperStore.Show.Director.DIRECTOR + " FROM " +
        FILMS_SHOW_TABLE_NAME + " LEFT JOIN " + DIRECTORS_TABLE_NAME +
        " ON (" + FILMS_SHOW_TABLE_NAME + "." + FILMS_SHOW_ID_DIRECTOR +
        " = " + DIRECTORS_TABLE_NAME + "." + ScraperStore.Director.ID + ")";

    private static final String FILMS_SHOW_VIEW_INSERT_TRIGGER =
        "CREATE TRIGGER insert_films_show INSTEAD OF INSERT ON " + FILMS_SHOW_VIEW_NAME +
        " BEGIN " +
        "INSERT OR IGNORE INTO " + DIRECTORS_TABLE_NAME + " ( " + ScraperStore.Director.NAME + " ) " +
        "VALUES (NEW." + ScraperStore.Show.Director.NAME + "); " +
        "INSERT INTO " + FILMS_SHOW_TABLE_NAME +
            " ( " + FILMS_SHOW_ID_SHOW + "," + FILMS_SHOW_ID_DIRECTOR + " ) " +
        "SELECT NEW." + ScraperStore.Show.Director.SHOW + ", " +
            DIRECTORS_TABLE_NAME + "." + ScraperStore.Director.ID + " " +
        " FROM " + DIRECTORS_TABLE_NAME +
        " WHERE " + ScraperStore.Director.NAME + " = NEW." + ScraperStore.Show.Director.NAME + "; " +
        "END";

    private static final String FILMS_EPISODE_VIEW_CREATE =
        "CREATE VIEW " + FILMS_EPISODE_VIEW_NAME + " AS SELECT " +
        FILMS_EPISODE_TABLE_NAME + "." +
        FILMS_EPISODE_ID_EPISODE + " AS " + ScraperStore.Episode.Director.EPISODE + ", " +
        DIRECTORS_TABLE_NAME + "." +
        ScraperStore.Director.NAME + " AS " + ScraperStore.Episode.Director.NAME + ", " +
        DIRECTORS_TABLE_NAME + "." +
        ScraperStore.Director.ID + " AS " + ScraperStore.Episode.Director.DIRECTOR + " FROM " +
        FILMS_EPISODE_TABLE_NAME + " LEFT JOIN " + DIRECTORS_TABLE_NAME +
        " ON (" + FILMS_EPISODE_TABLE_NAME + "." + FILMS_EPISODE_ID_DIRECTOR +
        " = " + DIRECTORS_TABLE_NAME + "." + ScraperStore.Director.ID + ")";

    private static final String FILMS_EPISODE_VIEW_INSERT_TRIGGER =
        "CREATE TRIGGER insert_films_episode INSTEAD OF INSERT ON " + FILMS_EPISODE_VIEW_NAME +
        " BEGIN " +
        "INSERT OR IGNORE INTO " + DIRECTORS_TABLE_NAME + " ( " + ScraperStore.Director.NAME + " ) " +
        "VALUES (NEW." + ScraperStore.Episode.Director.NAME + "); " +
        "INSERT INTO " + FILMS_EPISODE_TABLE_NAME +
            " ( " + FILMS_EPISODE_ID_EPISODE + "," + FILMS_EPISODE_ID_DIRECTOR + " ) " +
        "SELECT NEW." + ScraperStore.Episode.Director.EPISODE + ", " +
            DIRECTORS_TABLE_NAME + "." + ScraperStore.Director.ID + " " +
        " FROM " + DIRECTORS_TABLE_NAME +
        " WHERE " + ScraperStore.Director.NAME + " = NEW." + ScraperStore.Episode.Director.NAME + "; " +
        "END";

    private static final String WRITERS_SHOW_VIEW_CREATE =
            "CREATE VIEW " + WRITERS_SHOW_VIEW_NAME + " AS SELECT " +
                    WRITERS_SHOW_TABLE_NAME + "." +
                    WRITERS_SHOW_ID_SHOW + " AS " + ScraperStore.Show.Writer.SHOW + ", " +
                    WRITERS_TABLE_NAME + "." +
                    ScraperStore.Writer.NAME + " AS " + ScraperStore.Show.Writer.NAME + ", " +
                    WRITERS_TABLE_NAME + "." +
                    ScraperStore.Writer.ID + " AS " + ScraperStore.Show.Writer.WRITER + " FROM " +
                    WRITERS_SHOW_TABLE_NAME + " LEFT JOIN " + WRITERS_TABLE_NAME +
                    " ON (" + WRITERS_SHOW_TABLE_NAME + "." + WRITERS_SHOW_ID_WRITER +
                    " = " + WRITERS_TABLE_NAME + "." + ScraperStore.Writer.ID + ")";

    private static final String WRITERS_SHOW_VIEW_INSERT_TRIGGER =
            "CREATE TRIGGER insert_writers_show INSTEAD OF INSERT ON " + WRITERS_SHOW_VIEW_NAME +
                    " BEGIN " +
                    "INSERT OR IGNORE INTO " + WRITERS_TABLE_NAME + " ( " + ScraperStore.Writer.NAME + " ) " +
                    "VALUES (NEW." + ScraperStore.Show.Writer.NAME + "); " +
                    "INSERT INTO " + WRITERS_SHOW_TABLE_NAME +
                    " ( " + WRITERS_SHOW_ID_SHOW + "," + WRITERS_SHOW_ID_WRITER + " ) " +
                    "SELECT NEW." + ScraperStore.Show.Writer.SHOW + ", " +
                    WRITERS_TABLE_NAME + "." + ScraperStore.Writer.ID + " " +
                    " FROM " + WRITERS_TABLE_NAME +
                    " WHERE " + ScraperStore.Writer.NAME + " = NEW." + ScraperStore.Show.Writer.NAME + "; " +
                    "END";

    private static final String WRITERS_EPISODE_VIEW_CREATE =
            "CREATE VIEW " + WRITERS_EPISODE_VIEW_NAME + " AS SELECT " +
                    WRITERS_EPISODE_TABLE_NAME + "." +
                    WRITERS_EPISODE_ID_EPISODE + " AS " + ScraperStore.Episode.Writer.EPISODE + ", " +
                    WRITERS_TABLE_NAME + "." +
                    ScraperStore.Writer.NAME + " AS " + ScraperStore.Episode.Writer.NAME + ", " +
                    WRITERS_TABLE_NAME + "." +
                    ScraperStore.Writer.ID + " AS " + ScraperStore.Episode.Writer.WRITER + " FROM " +
                    WRITERS_EPISODE_TABLE_NAME + " LEFT JOIN " + WRITERS_TABLE_NAME +
                    " ON (" + WRITERS_EPISODE_TABLE_NAME + "." + WRITERS_EPISODE_ID_WRITER +
                    " = " + WRITERS_TABLE_NAME + "." + ScraperStore.Writer.ID + ")";

    private static final String WRITERS_EPISODE_VIEW_INSERT_TRIGGER =
            "CREATE TRIGGER insert_writers_episode INSTEAD OF INSERT ON " + WRITERS_EPISODE_VIEW_NAME +
                    " BEGIN " +
                    "INSERT OR IGNORE INTO " + WRITERS_TABLE_NAME + " ( " + ScraperStore.Writer.NAME + " ) " +
                    "VALUES (NEW." + ScraperStore.Episode.Writer.NAME + "); " +
                    "INSERT INTO " + WRITERS_EPISODE_TABLE_NAME +
                    " ( " + WRITERS_EPISODE_ID_EPISODE + "," + WRITERS_EPISODE_ID_WRITER + " ) " +
                    "SELECT NEW." + ScraperStore.Episode.Writer.EPISODE + ", " +
                    WRITERS_TABLE_NAME + "." + ScraperStore.Writer.ID + " " +
                    " FROM " + WRITERS_TABLE_NAME +
                    " WHERE " + ScraperStore.Writer.NAME + " = NEW." + ScraperStore.Episode.Writer.NAME + "; " +
                    "END";





    private static final String PRODUCES_MOVIE_VIEW_CREATE =
        "CREATE VIEW " + PRODUCES_MOVIE_VIEW_NAME + " AS SELECT " +
        PRODUCES_MOVIE_TABLE_NAME + "." +
        PRODUCES_MOVIE_ID_MOVIE + " AS " + ScraperStore.Movie.Studio.MOVIE + ", " +
        STUDIOS_TABLE_NAME + "." +
        ScraperStore.Studio.NAME + " AS " + ScraperStore.Movie.Studio.NAME + ", " +
        STUDIOS_TABLE_NAME + "." +
        ScraperStore.Studio.ID + " AS " + ScraperStore.Movie.Studio.STUDIO + " FROM " +
        PRODUCES_MOVIE_TABLE_NAME + " LEFT JOIN " + STUDIOS_TABLE_NAME +
        " ON (" + PRODUCES_MOVIE_TABLE_NAME + "." + PRODUCES_MOVIE_ID_STUDIO +
        " = " + STUDIOS_TABLE_NAME + "." + ScraperStore.Studio.ID + ")";

    private static final String PRODUCES_MOVIE_VIEW_INSERT_TRIGGER =
        "CREATE TRIGGER insert_produces_movie INSTEAD OF INSERT ON " + PRODUCES_MOVIE_VIEW_NAME +
        " BEGIN " +
        "INSERT OR IGNORE INTO " + STUDIOS_TABLE_NAME + " ( " + ScraperStore.Studio.NAME + " ) " +
        "VALUES (NEW." + ScraperStore.Movie.Studio.NAME + "); " +
        "INSERT INTO " + PRODUCES_MOVIE_TABLE_NAME +
            " ( " + PRODUCES_MOVIE_ID_MOVIE + "," + PRODUCES_MOVIE_ID_STUDIO + " ) " +
        "SELECT NEW." + ScraperStore.Movie.Studio.MOVIE + ", " +
            STUDIOS_TABLE_NAME + "." + ScraperStore.Studio.ID + " " +
        " FROM " + STUDIOS_TABLE_NAME +
        " WHERE " + ScraperStore.Studio.NAME + " = NEW." + ScraperStore.Movie.Studio.NAME + "; " +
        "END";

    private static final String PRODUCES_SHOW_VIEW_CREATE =
        "CREATE VIEW " + PRODUCES_SHOW_VIEW_NAME + " AS SELECT " +
        PRODUCES_SHOW_TABLE_NAME + "." +
        PRODUCES_SHOW_ID_SHOW + " AS " + ScraperStore.Show.Studio.SHOW + ", " +
        STUDIOS_TABLE_NAME + "." +
        ScraperStore.Studio.NAME + " AS " + ScraperStore.Show.Studio.NAME + ", " +
        STUDIOS_TABLE_NAME + "." +
        ScraperStore.Studio.ID + " AS " + ScraperStore.Show.Studio.STUDIO + " FROM " +
        PRODUCES_SHOW_TABLE_NAME + " LEFT JOIN " + STUDIOS_TABLE_NAME +
        " ON (" + PRODUCES_SHOW_TABLE_NAME + "." + PRODUCES_SHOW_ID_SHOW +
        " = " + STUDIOS_TABLE_NAME + "." + ScraperStore.Studio.ID + ")";

    private static final String PRODUCES_SHOW_VIEW_INSERT_TRIGGER =
        "CREATE TRIGGER insert_produces_show INSTEAD OF INSERT ON " + PRODUCES_SHOW_VIEW_NAME +
        " BEGIN " +
        "INSERT OR IGNORE INTO " + STUDIOS_TABLE_NAME + " ( " + ScraperStore.Studio.NAME + " ) " +
        "VALUES (NEW." + ScraperStore.Show.Studio.NAME + "); " +
        "INSERT INTO " + PRODUCES_SHOW_TABLE_NAME +
            " ( " + PRODUCES_SHOW_ID_SHOW + "," + PRODUCES_SHOW_ID_STUDIO + " ) " +
        "SELECT NEW." + ScraperStore.Show.Studio.SHOW + ", " +
            STUDIOS_TABLE_NAME + "." + ScraperStore.Studio.ID + " " +
        " FROM " + STUDIOS_TABLE_NAME +
        " WHERE " + ScraperStore.Studio.NAME + " = NEW." + ScraperStore.Show.Studio.NAME + "; " +
        "END";

    private static final String BELONGS_MOVIE_VIEW_CREATE =
        "CREATE VIEW " + BELONGS_MOVIE_VIEW_NAME + " AS SELECT " +
        BELONGS_MOVIE_TABLE_NAME + "." +
        BELONGS_MOVIE_ID_MOVIE + " AS " + ScraperStore.Movie.Genre.MOVIE + ", " +
        GENRES_TABLE_NAME + "." +
        ScraperStore.Genre.NAME + " AS " + ScraperStore.Movie.Genre.NAME + ", " +
        GENRES_TABLE_NAME + "." +
        ScraperStore.Genre.ID + " AS " + ScraperStore.Movie.Genre.GENRE + " FROM " +
        BELONGS_MOVIE_TABLE_NAME + " LEFT JOIN " + GENRES_TABLE_NAME +
        " ON (" + BELONGS_MOVIE_TABLE_NAME + "." + BELONGS_MOVIE_ID_GENRE +
        " = " + GENRES_TABLE_NAME + "." + ScraperStore.Genre.ID + ")";

    private static final String BELONGS_MOVIE_VIEW_INSERT_TRIGGER =
        "CREATE TRIGGER insert_belongs_movie INSTEAD OF INSERT ON " + BELONGS_MOVIE_VIEW_NAME +
        " BEGIN " +
        "INSERT OR IGNORE INTO " + GENRES_TABLE_NAME + " ( " + ScraperStore.Genre.NAME + " ) " +
        "VALUES (NEW." + ScraperStore.Movie.Genre.NAME +"); " +
        "INSERT INTO " + BELONGS_MOVIE_TABLE_NAME +
            " ( " + BELONGS_MOVIE_ID_MOVIE + "," + BELONGS_MOVIE_ID_GENRE + " ) " +
        "SELECT NEW." + ScraperStore.Movie.Genre.MOVIE + ", " +
            GENRES_TABLE_NAME + "." + ScraperStore.Genre.ID + " " +
        " FROM " + GENRES_TABLE_NAME +
        " WHERE " + ScraperStore.Genre.NAME + " = NEW." + ScraperStore.Movie.Genre.NAME + "; " +
        "END";

    private static final String BELONGS_SHOW_VIEW_CREATE =
        "CREATE VIEW " + BELONGS_SHOW_VIEW_NAME + " AS SELECT " +
        BELONGS_SHOW_TABLE_NAME + "." +
        BELONGS_SHOW_ID_SHOW + " AS " + ScraperStore.Show.Genre.SHOW + ", " +
        GENRES_TABLE_NAME + "." +
        ScraperStore.Genre.NAME + " AS " + ScraperStore.Show.Genre.NAME + ", " +
        GENRES_TABLE_NAME + "." +
        ScraperStore.Genre.ID + " AS " + ScraperStore.Show.Genre.GENRE + " FROM " +
        BELONGS_SHOW_TABLE_NAME + " LEFT JOIN " + GENRES_TABLE_NAME +
        " ON (" + BELONGS_SHOW_TABLE_NAME + "." + BELONGS_SHOW_ID_GENRE +
        " = " + GENRES_TABLE_NAME + "." + ScraperStore.Genre.ID + ")";

    private static final String BELONGS_SHOW_VIEW_INSERT_TRIGGER =
        "CREATE TRIGGER insert_belongs_show INSTEAD OF INSERT ON " + BELONGS_SHOW_VIEW_NAME +
        " BEGIN " +
        "INSERT OR IGNORE INTO " + GENRES_TABLE_NAME + " ( " + ScraperStore.Genre.NAME + " ) " +
        "VALUES (NEW." + ScraperStore.Show.Genre.NAME + "); " +
        "INSERT INTO " + BELONGS_SHOW_TABLE_NAME +
            " ( " + BELONGS_SHOW_ID_SHOW + "," + BELONGS_SHOW_ID_GENRE + " ) " +
        "SELECT NEW." + ScraperStore.Show.Genre.SHOW + ", " +
            GENRES_TABLE_NAME + "." + ScraperStore.Genre.ID + " " +
        " FROM " + GENRES_TABLE_NAME +
        " WHERE " + ScraperStore.Genre.NAME + " = NEW." + ScraperStore.Show.Genre.NAME + "; " +
        "END";

    private static final String ACTOR_DELETABLE_VIEW_CREATE =
            "CREATE VIEW v_actor_deletable AS " +
            "SELECT _id FROM actor " +
            "LEFT JOIN plays_movie ON plays_movie.actor_plays=actor._id " +
            "LEFT JOIN plays_show ON plays_show.actor_plays=actor._id " +
            "LEFT JOIN guests ON  guests.actor_guests = actor._id " +
            "WHERE coalesce(movie_plays, show_plays, episode_guests) IS NULL";
    private static final String DIRECTOR_DELETABLE_VIEW_CREATE =
            "CREATE VIEW v_director_deletable AS " +
            "SELECT _id FROM director " +
            "LEFT JOIN films_movie ON films_movie.director_films=director._id " +
            "LEFT JOIN films_show ON films_show.director_films=director._id " +
            "LEFT JOIN films_episode ON films_episode.director_films=director._id " +
            "WHERE coalesce(movie_films, show_films, episode_films) IS NULL";

    private static final String WRITER_DELETABLE_VIEW_CREATE =
            "CREATE VIEW v_writer_deletable AS " +
                    "SELECT _id FROM writer " +
                    "LEFT JOIN writers_movie ON writers_movie.writer_writers=writer._id " +
                    "LEFT JOIN writers_show ON writers_show.writer_writers=writer._id " +
                    "LEFT JOIN writers_episode ON writers_episode.writer_writers=writer._id " +
                    "WHERE coalesce(movie_writers, show_writers, episode_writers) IS NULL";

    private static final String GENRE_DELETABLE_VIEW_CREATE =
            "CREATE VIEW v_genre_deletable AS " +
            "SELECT _id FROM genre " +
            "LEFT JOIN belongs_show  ON belongs_show.genre_belongs=genre._id " +
            "LEFT JOIN belongs_movie ON belongs_movie.genre_belongs=genre._id " +
            "WHERE coalesce(movie_belongs, show_belongs) IS NULL";
    private static final String STUDIO_DELETABLE_VIEW_CREATE =
            "CREATE VIEW v_studio_deletable AS " +
            "SELECT _id FROM studio " +
            "LEFT JOIN produces_movie ON produces_movie.studio_produces=studio._id " +
            "LEFT JOIN produces_show ON produces_show.studio_produces=studio._id " +
            "WHERE coalesce(movie_produces, show_produces) IS NULL";
    private static final String EPISODE_DELETE_TRIGGER_DROP = "DROP TRIGGER IF EXISTS episode_delete";
    private static final String EPISODE_DELETE_TRIGGER_CREATE =
            "CREATE TRIGGER episode_delete AFTER DELETE ON episode " +
            "BEGIN " +
            "delete from actor where _id in (select _id from v_actor_deletable); " +
            "delete from director where _id in (select _id from v_director_deletable); " +
            "delete from studio where _id in (select _id from v_studio_deletable); " +
            "delete from genre where _id in (select _id from v_genre_deletable); " +
            "DELETE FROM SHOW WHERE SHOW._id = OLD.show_episode AND NOT EXISTS (SELECT 1 FROM EPISODE WHERE show_episode = OLD.show_episode LIMIT 1); " +
            // set scraper type / id to 0 if something is refering this episode
            "UPDATE " + VideoOpenHelper.FILES_TABLE_NAME + " SET ArchosMediaScraper_id=0, ArchosMediaScraper_type=0 " +
            "WHERE ArchosMediaScraper_id = OLD._id AND ArchosMediaScraper_type = " + ScraperStore.SCRAPER_TYPE_SHOW + ";" +
            "END";
    private static final String EPISODE_DELETE_TRIGGER_CREATE_v2 =
            "CREATE TRIGGER episode_delete AFTER DELETE ON episode " +
                    "BEGIN " +
                    "DELETE FROM SHOW WHERE SHOW._id = OLD.show_episode AND NOT EXISTS (SELECT 1 FROM EPISODE WHERE show_episode = OLD.show_episode LIMIT 1); " +
                    // set scraper type / id to 0 if something is refering this episode
                    "UPDATE " + VideoOpenHelper.FILES_TABLE_NAME + " SET ArchosMediaScraper_id=0, ArchosMediaScraper_type=0 " +
                    "WHERE ArchosMediaScraper_id = OLD._id AND ArchosMediaScraper_type = " + ScraperStore.SCRAPER_TYPE_SHOW + ";" +
                    "END";
    private static final String MOVIE_DELETE_TRIGGER_DROP = "DROP TRIGGER IF EXISTS movie_delete";
    private static final String MOVIE_DELETE_TRIGGER_CREATE =
            "CREATE TRIGGER movie_delete AFTER DELETE ON movie " +
            "BEGIN " +
            "delete from actor where _id in (select _id from v_actor_deletable); " +
            "delete from director where _id in (select _id from v_director_deletable); " +
            "delete from genre where _id in (select _id from v_genre_deletable); " +
            // set scraper type / id to 0 if something is refering this episode
            "UPDATE " + VideoOpenHelper.FILES_TABLE_NAME + " SET ArchosMediaScraper_id=0, ArchosMediaScraper_type=0 " +
            "WHERE ArchosMediaScraper_id = OLD._id AND ArchosMediaScraper_type = " + ScraperStore.SCRAPER_TYPE_MOVIE + ";" +
            "INSERT INTO delete_files(name,use_count) VALUES(OLD.cover_movie, (SELECT COUNT("
            + ScraperStore.Movie.COVER + ") FROM " + MOVIE_TABLE_NAME + "  WHERE " + ScraperStore.Movie.COVER
            + " = OLD.cover_movie));" +
            "END";
    private static final String MOVIE_DELETE_TRIGGER_CREATE_v2 =
            "CREATE TRIGGER movie_delete AFTER DELETE ON movie " +
                    "BEGIN " +
                    // set scraper type / id to 0 if something is refering this episode
                    "UPDATE " + VideoOpenHelper.FILES_TABLE_NAME + " SET ArchosMediaScraper_id=0, ArchosMediaScraper_type=0 " +
                    "WHERE ArchosMediaScraper_id = OLD._id AND ArchosMediaScraper_type = " + ScraperStore.SCRAPER_TYPE_MOVIE + ";" +
                    "INSERT INTO delete_files(name,use_count) VALUES(OLD.cover_movie, (SELECT COUNT("
                    + ScraperStore.Movie.COVER + ") FROM " + MOVIE_TABLE_NAME + "  WHERE " + ScraperStore.Movie.COVER
                    + " = OLD.cover_movie));" +
                    "END";
    private static final String SHOW_DELETE_TRIGGER_DROP = "DROP TRIGGER IF EXISTS show_delete";
    private static final String SHOW_DELETE_TRIGGER_CREATE =
            "CREATE TRIGGER show_delete AFTER DELETE ON show " +
            "BEGIN " +
            "delete from actor where _id in (select _id from v_actor_deletable); " +
            "delete from director where _id in (select _id from v_director_deletable); " +
            "delete from studio where _id in (select _id from v_studio_deletable); " +
            "delete from genre where _id in (select _id from v_genre_deletable); " +
            "INSERT INTO delete_files(name) VALUES(OLD.cover_show);" +
            "END";
    private static final String SHOW_DELETE_TRIGGER_CREATE_v2 =
            "CREATE TRIGGER show_delete AFTER DELETE ON show " +
                    "BEGIN " +
                    "INSERT INTO delete_files(name) VALUES(OLD.cover_show);" +
                    "END";
    private static final String MOVIE_INSERT_TRIGGER_DROP = "DROP TRIGGER IF EXISTS movie_insert";
    private static final String MOVIE_INSERT_TRIGGER_CREATE =
            "CREATE TRIGGER movie_insert AFTER INSERT ON movie " +
            "BEGIN " +
            "UPDATE " + VideoOpenHelper.FILES_TABLE_NAME + " SET ArchosMediaScraper_id=NEW._id, ArchosMediaScraper_type=" + ScraperStore.SCRAPER_TYPE_MOVIE +
            " WHERE remote_id=NEW.video_id;" +
            "END";
    private static final String EPISODE_INSERT_TRIGGER_DROP = "DROP TRIGGER IF EXISTS episode_insert";
    private static final String EPISODE_INSERT_TRIGGER_CREATE =
            "CREATE TRIGGER episode_insert AFTER INSERT ON episode " +
            "BEGIN " +
            "UPDATE " + VideoOpenHelper.FILES_TABLE_NAME + " SET ArchosMediaScraper_id=NEW._id, ArchosMediaScraper_type=" + ScraperStore.SCRAPER_TYPE_SHOW +
            " WHERE remote_id=NEW.video_id;" +
            "END";
    // uses cover_episode instead of cover_show if != null
    // also includes backdrop and backdrop_url
    private static final String ALL_VIDEOS_VIEW_CREATE_v24 =
            "CREATE VIEW " + ALL_VIDEOS_VIEW_NAME + " AS SELECT " +
            "'" + ScraperStore.SCRAPER_TYPE_MOVIE + "' AS " + ScraperStore.AllVideos.SCRAPER_TYPE + ", " +
            "_id AS " + ScraperStore.AllVideos.SCRAPER_ID + ", " +
            "name_movie AS " + ScraperStore.AllVideos.MOVIE_OR_SHOW_NAME + ", " +
            "NULL AS " + ScraperStore.AllVideos.EPISODE_NAME + ", " +
            "NULL AS " + ScraperStore.AllVideos.EPISODE_NUMBER + ", " +
            "NULL AS " + ScraperStore.AllVideos.EPISODE_SEASON_NUMBER + ", " +
            "year_movie AS " + ScraperStore.AllVideos.MOVIE_YEAR + ", " +
            "NULL AS " + ScraperStore.AllVideos.SHOW_PREMIERED + ", " +
            "NULL AS " + ScraperStore.AllVideos.EPISODE_AIRED + ", " +
            "rating_movie AS " + ScraperStore.AllVideos.MOVIE_OR_SHOW_RATING + ", " +
            "NULL AS " + ScraperStore.AllVideos.EPISODE_RATING + ", " +
            "cover_movie AS " + ScraperStore.AllVideos.MOVIE_OR_SHOW_COVER + ", " +
            "backdrop_movie AS " + ScraperStore.AllVideos.MOVIE_OR_SHOW_BACKDROP + ", " +
            "backdrop_url_movie AS " + ScraperStore.AllVideos.MOVIE_OR_SHOW_BACKDROP_URL + ", " +
            "plot_movie AS " + ScraperStore.AllVideos.MOVIE_OR_SHOW_PLOT + ", " +
            "NULL AS " + ScraperStore.AllVideos.EPISODE_PLOT + " " +
            "FROM movie UNION SELECT " +
            "'" + ScraperStore.SCRAPER_TYPE_SHOW + "' AS " + ScraperStore.AllVideos.SCRAPER_TYPE + ", " +
            "episode._id AS " + ScraperStore.AllVideos.SCRAPER_ID + ", " +
            "name_show AS " + ScraperStore.AllVideos.MOVIE_OR_SHOW_NAME + ", " +
            "name_episode AS " + ScraperStore.AllVideos.EPISODE_NAME + ", " +
            "number_episode AS " + ScraperStore.AllVideos.EPISODE_NUMBER + ", " +
            "season_episode AS " + ScraperStore.AllVideos.EPISODE_SEASON_NUMBER + ", " +
            "NULL AS " + ScraperStore.AllVideos.MOVIE_YEAR + ", " +
            "premiered_show AS " + ScraperStore.AllVideos.SHOW_PREMIERED + ", " +
            "aired_episode AS " + ScraperStore.AllVideos.EPISODE_AIRED + ", " +
            "rating_show AS " + ScraperStore.AllVideos.MOVIE_OR_SHOW_RATING + ", " +
            "rating_episode AS " + ScraperStore.AllVideos.EPISODE_RATING + ", " +
            "coalesce(cover_episode, cover_show) AS " + ScraperStore.AllVideos.MOVIE_OR_SHOW_COVER + ", " +
            "backdrop_show AS " + ScraperStore.AllVideos.MOVIE_OR_SHOW_BACKDROP + ", " +
            "backdrop_url_show AS " + ScraperStore.AllVideos.MOVIE_OR_SHOW_BACKDROP_URL + ", " +
            "plot_show AS " + ScraperStore.AllVideos.MOVIE_OR_SHOW_PLOT + ", " +
            "plot_episode AS " + ScraperStore.AllVideos.EPISODE_PLOT + " " +
            "FROM show LEFT JOIN episode ON show_episode = show._id";

    public static final String VIEW_SEASONS_CREATE =
            "CREATE VIEW " + SEASONS_VIEW_NAME + " AS\n" +
            "SELECT show_id, season, group_concat( episode_number ) AS episode_numbers,\n" +
            "                  group_concat( episode_id ) AS episode_ids, group_concat( video_id ) AS video_ids,\n" +
            "          count( video_id ) AS episode_count\n" +
            "  FROM  ( \n" +
            "    SELECT show_episode AS show_id, season_episode AS season, number_episode AS episode_number, _id AS episode_id, video_id\n" +
            "      FROM episode\n" +
            "     ORDER BY show_id, season, episode_number \n" +
            ") \n" +
            " GROUP BY show_id, season\n" +
            " ORDER BY show_id, season";

    // Views that combine lists of actors, genres, etc into formatted strings

    // genres, show / movie
    public static final String VIEW_SHOW_GENRES = "v_show_genres";
    private static final String CREATE_VIEW_SHOW_GENRES =
            "CREATE VIEW " + VIEW_SHOW_GENRES + " AS\n" +
            " SELECT _id, group_concat( name_genre, ', ' ) AS genres\n" +
            "  FROM  ( \n" +
            "    SELECT show_belongs AS _id, name_genre\n" +
            "      FROM belongs_show\n" +
            "           LEFT JOIN genre\n" +
            "                  ON ( genre_belongs = _id ) \n" +
            "     ORDER BY belongs_show.ROWID \n" +
            ") \n" +
            " GROUP BY _id";
    public static final String VIEW_MOVIE_GENRES = "v_movie_genres";
    private static final String CREATE_VIEW_MOVIE_GENRES =
            "CREATE VIEW " + VIEW_MOVIE_GENRES + " AS\n" +
            " SELECT _id, group_concat( name_genre, ', ' ) AS genres\n" +
            "  FROM  ( \n" +
            "    SELECT movie_belongs AS _id, name_genre\n" +
            "      FROM belongs_movie\n" +
            "           LEFT JOIN genre\n" +
            "                  ON ( genre_belongs = _id ) \n" +
            "     ORDER BY belongs_movie.ROWID \n" +
            ") \n" +
            "GROUP BY _id";

    // directors, show / episode / movie (show directors might be unused though)
    public static final String VIEW_SHOW_DIRECTORS = "v_show_directors";
    private static final String CREATE_VIEW_SHOW_DIRECTORS =
            "CREATE VIEW " + VIEW_SHOW_DIRECTORS + " AS\n" +
            "SELECT _id, group_concat( name_director, ', ' ) AS directors\n" +
            "  FROM  ( \n" +
            "    SELECT show_films AS _id, name_director\n" +
            "      FROM films_show\n" +
            "           LEFT JOIN director\n" +
            "                  ON ( director_films = _id ) \n" +
            "     ORDER BY films_show.ROWID \n" +
            ") \n" +
            " GROUP BY _id";
    public static final String VIEW_EPISODE_DIRECTORS = "v_episode_directors";
    private static final String CREATE_VIEW_EPISODE_DIRECTORS =
            "CREATE VIEW " + VIEW_EPISODE_DIRECTORS + " AS\n" +
            "SELECT _id, group_concat( name_director, ', ' ) AS directors\n" +
            "  FROM  ( \n" +
            "    SELECT episode_films AS _id, name_director\n" +
            "      FROM films_episode\n" +
            "           LEFT JOIN director\n" +
            "                  ON ( director_films = _id ) \n" +
            "     ORDER BY films_episode.ROWID \n" +
            ") \n" +
            " GROUP BY _id";
    public static final String VIEW_MOVIE_DIRECTORS = "v_movie_directors";
    private static final String CREATE_VIEW_MOVIE_DIRECTORS =
            "CREATE VIEW " + VIEW_MOVIE_DIRECTORS + " AS\n" +
            "SELECT _id, group_concat( name_director, ', ' ) AS directors\n" +
            "  FROM  ( \n" +
            "    SELECT movie_films AS _id, name_director\n" +
            "      FROM films_movie\n" +
            "           LEFT JOIN director\n" +
            "                  ON ( director_films = _id ) \n" +
            "     ORDER BY films_movie.ROWID \n" +
            ") \n" +
            " GROUP BY _id";

    // writers, show / episode / movie (show writers might be unused though)
    public static final String VIEW_SHOW_WRITERS = "v_show_writers";
    private static final String CREATE_VIEW_SHOW_WRITERS =
            "CREATE VIEW " + VIEW_SHOW_WRITERS + " AS\n" +
                    "SELECT _id, group_concat( name_writer, ', ' ) AS writers\n" +
                    "  FROM  ( \n" +
                    "    SELECT show_writers AS _id, name_writer\n" +
                    "      FROM writers_show\n" +
                    "           LEFT JOIN writer\n" +
                    "                  ON ( writer_writers = _id ) \n" +
                    "     ORDER BY writers_show.ROWID \n" +
                    ") \n" +
                    " GROUP BY _id";
    public static final String VIEW_EPISODE_WRITERS = "v_episode_writers";
    private static final String CREATE_VIEW_EPISODE_WRITERS =
            "CREATE VIEW " + VIEW_EPISODE_WRITERS + " AS\n" +
                    "SELECT _id, group_concat( name_writer, ', ' ) AS writers\n" +
                    "  FROM  ( \n" +
                    "    SELECT episode_writers AS _id, name_writer\n" +
                    "      FROM writers_episode\n" +
                    "           LEFT JOIN writer\n" +
                    "                  ON ( writer_writers = _id ) \n" +
                    "     ORDER BY writers_episode.ROWID \n" +
                    ") \n" +
                    " GROUP BY _id";
    public static final String VIEW_MOVIE_WRITERS = "v_movie_writers";
    private static final String CREATE_VIEW_MOVIE_WRITERS =
            "CREATE VIEW " + VIEW_MOVIE_WRITERS + " AS\n" +
                    "SELECT _id, group_concat( name_writer, ', ' ) AS writers\n" +
                    "  FROM  ( \n" +
                    "    SELECT movie_writers AS _id, name_writer\n" +
                    "      FROM writers_movie\n" +
                    "           LEFT JOIN writer\n" +
                    "                  ON ( writer_writers = _id ) \n" +
                    "     ORDER BY writers_movie.ROWID \n" +
                    ") \n" +
                    " GROUP BY _id";

    // Actors for show / episode (guests) / movie
    public static final String VIEW_SHOW_ACTORS = "v_show_actors";
    private static final String CREATE_VIEW_SHOW_ACTORS =
            "CREATE VIEW " + VIEW_SHOW_ACTORS + " AS\n" +
            "SELECT _id, group_concat( actor_role, ', ' ) AS actors\n" +
            "  FROM  ( \n" +
            "    SELECT show_plays AS _id, CASE\n" +
            "                WHEN role_plays IS NULL \n" +
            "           OR\n" +
            "           role_plays = '' THEN name_actor \n" +
            "                ELSE name_actor || ' (' || role_plays || ')' \n" +
            "           END AS actor_role\n" +
            "      FROM plays_show\n" +
            "           LEFT JOIN actor\n" +
            "                  ON ( actor_plays = _id ) \n" +
            "     ORDER BY plays_show.ROWID \n" +
            ") \n" +
            " GROUP BY _id";
    public static final String VIEW_EPISODE_ACTORS = "v_episode_actors";
    private static final String CREATE_VIEW_EPISODE_ACTORS =
            "CREATE VIEW " + VIEW_EPISODE_ACTORS + " AS\n" +
            "SELECT _id, group_concat( actor_role, ', ' ) AS guests\n" +
            "  FROM  ( \n" +
            "    SELECT episode_guests AS _id, CASE\n" +
            "                WHEN role_guests IS NULL \n" +
            "           OR\n" +
            "           role_guests = '' THEN name_actor \n" +
            "                ELSE name_actor || ' (' || role_guests || ')' \n" +
            "           END AS actor_role\n" +
            "      FROM guests\n" +
            "           LEFT JOIN actor\n" +
            "                  ON ( actor_guests = _id ) \n" +
            "     ORDER BY guests.ROWID \n" +
            ") \n" +
            " GROUP BY _id";
    public static final String VIEW_MOVIE_ACTORS = "v_movie_actors";
    private static final String CREATE_VIEW_MOVIE_ACTORS =
            "CREATE VIEW " + VIEW_MOVIE_ACTORS + " AS\n" +
            "SELECT _id, group_concat( actor_role, ', ' ) AS actors\n" +
            "  FROM  ( \n" +
            "    SELECT movie_plays AS _id, CASE\n" +
            "                WHEN role_plays IS NULL \n" +
            "           OR\n" +
            "           role_plays = '' THEN name_actor \n" +
            "                ELSE name_actor || ' (' || role_plays || ')' \n" +
            "           END AS actor_role\n" +
            "      FROM plays_movie\n" +
            "           LEFT JOIN actor\n" +
            "                  ON ( actor_plays = _id ) \n" +
            "     ORDER BY plays_movie.ROWID \n" +
            ") \n" +
            " GROUP BY _id";

    // studios for show / movie
    public static final String VIEW_SHOW_STUDIOS = "v_show_studios";
    private static final String CREATE_VIEW_SHOW_STUDIOS =
            "CREATE VIEW " + VIEW_SHOW_STUDIOS + " AS\n" +
            "SELECT _id, group_concat( name_studio, ', ' ) AS studios\n" +
            "  FROM  ( \n" +
            "    SELECT show_produces AS _id, name_studio\n" +
            "      FROM produces_show\n" +
            "           LEFT JOIN studio\n" +
            "                  ON ( studio_produces = _id ) \n" +
            "     ORDER BY produces_show.ROWID \n" +
            ") \n" +
            " GROUP BY _id";
    public static final String VIEW_MOVIE_STUDIOS = "v_movie_studios";
    private static final String CREATE_VIEW_MOVIE_STUDIOS =
            "CREATE VIEW " + VIEW_MOVIE_STUDIOS + " AS\n" +
            "SELECT _id, group_concat( name_studio, ', ' ) AS studios\n" +
            "  FROM  ( \n" +
            "    SELECT movie_produces AS _id, name_studio\n" +
            "      FROM produces_movie\n" +
            "           LEFT JOIN studio\n" +
            "                  ON ( studio_produces = _id ) \n" +
            "     ORDER BY produces_movie.ROWID \n" +
            ") \n" +
            " GROUP BY _id";

    /* Version 11 */
    // additions for posters backdrops
    public static final String MOVIE_POSTERS_TABLE_NAME = "movie_posters";
    private static final String CREATE_MOVIE_POSTERS_TABLE =
            "CREATE TABLE " + MOVIE_POSTERS_TABLE_NAME + " ( \n" +
            "    _id             INTEGER PRIMARY KEY,\n" +
            "    movie_id        INTEGER REFERENCES movie ( _id ) ON DELETE CASCADE\n" +
            "                                                     ON UPDATE CASCADE,\n" +
            "    m_po_thumb_url  TEXT,\n" +
            "    m_po_thumb_file TEXT,\n" +
            "    m_po_large_url  TEXT,\n" +
            "    m_po_large_file TEXT\n" +
            ")";

    public static final String MOVIE_TRAILERS_TABLE_NAME = "movie_trailers";
    private static final String CREATE_MOVIE_TRAILERS_TABLE =
            "CREATE TABLE " + MOVIE_TRAILERS_TABLE_NAME + " ( \n" +
                    "    _id             INTEGER PRIMARY KEY,\n" +
                    "    "+ ScraperStore.MovieTrailers.MOVIE_ID+"        INTEGER REFERENCES movie ( _id ) ON DELETE CASCADE\n" +
                    "                                                     ON UPDATE CASCADE,\n" +
                    "    "+ ScraperStore.MovieTrailers.VIDEO_KEY+"  TEXT,\n" +
                    "    "+ ScraperStore.MovieTrailers.SITE+" TEXT,\n" +
                    "    "+ ScraperStore.MovieTrailers.NAME+"  TEXT,\n" +
                    "    "+ ScraperStore.MovieTrailers.LANG+" TEXT\n" +
                    ")";
    private static final String CREATE_MOVIE_POSTERS_DELETE_TRIGGER =
            "CREATE TRIGGER " + MOVIE_POSTERS_TABLE_NAME + "_delete\n" +
                    "       BEFORE DELETE ON " + MOVIE_POSTERS_TABLE_NAME + "\n" +
                    "BEGIN\n" +
                    "    INSERT INTO delete_files(name,use_count) VALUES(OLD.m_po_large_file,(SELECT COUNT("
                    + ScraperStore.Movie.COVER + ") FROM " + MOVIE_TABLE_NAME + "  WHERE " + ScraperStore.Movie.COVER
                    + " = OLD.m_po_large_file));\n" + "    INSERT INTO delete_files(name) VALUES(OLD.m_po_thumb_file);\n"
                    +
                    "END";
    private static final String DROP_MOVIE_POSTERS_DELETE_TRIGGER =
            "DROP TRIGGER IF EXISTS " + MOVIE_POSTERS_TABLE_NAME + "_delete";

    public static final String MOVIE_BACKDROPS_TABLE_NAME = "movie_backdrops";

    private static final String CREATE_MOVIE_BACKDROPS_TABLE =
            "CREATE TABLE " + MOVIE_BACKDROPS_TABLE_NAME + " ( \n" +
            "    _id             INTEGER PRIMARY KEY,\n" +
            "    movie_id        INTEGER REFERENCES movie ( _id ) ON DELETE CASCADE\n" +
            "                                                     ON UPDATE CASCADE,\n" +
            "    m_bd_thumb_url  TEXT,\n" +
            "    m_bd_thumb_file TEXT,\n" +
            "    m_bd_large_url  TEXT,\n" +
            "    m_bd_large_file TEXT\n" +
            ")";
    private static final String CREATE_MOVIE_BACKDROPS_DELETE_TRIGGER =
            "CREATE TRIGGER " + MOVIE_BACKDROPS_TABLE_NAME + "_delete\n" +
            "       AFTER DELETE ON " + MOVIE_BACKDROPS_TABLE_NAME + "\n" +
            "BEGIN\n" +
            "    INSERT INTO delete_files(name) VALUES ( OLD.m_bd_large_file );\n"
            + "    INSERT INTO delete_files(name) VALUES ( OLD.m_bd_thumb_file );\n" +
            "END";
    private static final String DROP_MOVIE_BACKDROPS_DELETE_TRIGGER =
            "DROP TRIGGER IF EXISTS " + MOVIE_BACKDROPS_TABLE_NAME + "_delete";
    public static final String SHOW_POSTERS_TABLE_NAME = "show_posters";
    private static final String CREATE_SHOW_POSTERS_TABLE =
            "CREATE TABLE " + SHOW_POSTERS_TABLE_NAME + " ( \n" +
            "    _id             INTEGER PRIMARY KEY,\n" +
            "    show_id         INTEGER REFERENCES show ( _id ) ON DELETE CASCADE\n" +
            "                                                    ON UPDATE CASCADE,\n" +
            "    s_po_thumb_url  TEXT,\n" +
            "    s_po_thumb_file TEXT UNIQUE ON CONFLICT IGNORE,\n" +
            "    s_po_large_url  TEXT,\n" +
            "    s_po_large_file TEXT UNIQUE ON CONFLICT IGNORE,\n" +
            "    s_po_season     INTEGER DEFAULT ( -1 )\n" +
            ")";
    private static final String CREATE_SHOW_POSTERS_DELETE_TRIGGER =
            "CREATE TRIGGER " + SHOW_POSTERS_TABLE_NAME + "_delete\n" +
            "       AFTER DELETE ON " + SHOW_POSTERS_TABLE_NAME + "\n" +
            "BEGIN\n" +
            "    INSERT INTO delete_files(name) VALUES ( OLD.s_po_large_file );\n"
            + "    INSERT INTO delete_files(name) VALUES ( OLD.s_po_thumb_file );\n" +
            "END";
    private static final String DROP_SHOW_POSTERS_DELETE_TRIGGER =
            "DROP TRIGGER IF EXISTS " + SHOW_POSTERS_TABLE_NAME + "_delete";
    public static final String SHOW_BACKDROPS_TABLE_NAME = "show_backdrops";
    private static final String CREATE_SHOW_BACKDROPS_TABLE =
            "CREATE TABLE " + SHOW_BACKDROPS_TABLE_NAME + " ( \n" +
            "    _id             INTEGER PRIMARY KEY,\n" +
            "    show_id         INTEGER REFERENCES show ( _id ) ON DELETE CASCADE\n" +
            "                                                    ON UPDATE CASCADE,\n" +
            "    s_bd_thumb_url  TEXT,\n" +
            "    s_bd_thumb_file TEXT UNIQUE ON CONFLICT IGNORE,\n" +
            "    s_bd_large_url  TEXT,\n" +
            "    s_bd_large_file TEXT UNIQUE ON CONFLICT IGNORE\n" +
            ")";
    private static final String CREATE_SHOW_BACKDROPS_DELETE_TRIGGER =
            "CREATE TRIGGER " + SHOW_BACKDROPS_TABLE_NAME + "_delete\n" +
            "       AFTER DELETE ON " + SHOW_BACKDROPS_TABLE_NAME + "\n" +
            "BEGIN\n" +
            "    INSERT INTO delete_files(name) VALUES ( OLD.s_bd_large_file );\n"
            + "    INSERT INTO delete_files(name) VALUES ( OLD.s_bd_thumb_file );\n" +
            "END";
    private static final String DROP_SHOW_BACKDROPS_DELETE_TRIGGER =
            "DROP TRIGGER IF EXISTS " + SHOW_BACKDROPS_TABLE_NAME + "_delete";

    public static final String MOVIE_COLLECTION_TABLE_NAME = "movie_collection";
    private static final String CREATE_MOVIE_COLLECTION_TABLE =
            "CREATE TABLE " + MOVIE_COLLECTION_TABLE_NAME + " ( \n" +
                    ScraperStore.MovieCollections.ID + " INTEGER PRIMARY KEY NOT NULL,\n" +
                    ScraperStore.MovieCollections.NAME + " TEXT,\n" +
                    ScraperStore.MovieCollections.DESCRIPTION + " TEXT,\n" +
                    ScraperStore.MovieCollections.POSTER_LARGE_URL + " TEXT,\n" +
                    ScraperStore.MovieCollections.POSTER_LARGE_FILE + " TEXT UNIQUE ON CONFLICT IGNORE,\n" +
                    ScraperStore.MovieCollections.BACKDROP_LARGE_URL + " TEXT,\n" +
                    ScraperStore.MovieCollections.BACKDROP_LARGE_FILE + " TEXT UNIQUE ON CONFLICT IGNORE,\n" +
                    ScraperStore.MovieCollections.POSTER_THUMB_URL + " TEXT,\n" +
                    ScraperStore.MovieCollections.POSTER_THUMB_FILE + " TEXT UNIQUE ON CONFLICT IGNORE,\n" +
                    ScraperStore.MovieCollections.BACKDROP_THUMB_URL + " TEXT,\n" +
                    ScraperStore.MovieCollections.BACKDROP_THUMB_FILE + " TEXT UNIQUE ON CONFLICT IGNORE\n" +
                    ")";

    public static void create(SQLiteDatabase db) {
        db.execSQL(MOVIE_TABLE_CREATE);
        db.execSQL(ACTORS_TABLE_CREATE);
        db.execSQL(DIRECTORS_TABLE_CREATE);
        db.execSQL(GENRES_TABLE_CREATE);
        db.execSQL(STUDIOS_TABLE_CREATE);
        db.execSQL(SHOW_TABLE_CREATE);
        db.execSQL(EPISODE_TABLE_CREATE);
        db.execSQL(GUESTS_TABLE_CREATE);

        db.execSQL(FILMS_MOVIE_TABLE_CREATE);
        db.execSQL(BELONGS_MOVIE_TABLE_CREATE);
        db.execSQL(PLAYS_MOVIE_TABLE_CREATE);
        db.execSQL(PRODUCES_MOVIE_TABLE_CREATE);

        db.execSQL(FILMS_EPISODE_TABLE_CREATE);
        db.execSQL(FILMS_SHOW_TABLE_CREATE);
        db.execSQL(BELONGS_SHOW_TABLE_CREATE);
        db.execSQL(PLAYS_SHOW_TABLE_CREATE);
        db.execSQL(PRODUCES_SHOW_TABLE_CREATE);

        db.execSQL(GUESTS_VIEW_CREATE);
        db.execSQL(PLAYS_SHOW_VIEW_CREATE);
        db.execSQL(PLAYS_MOVIE_VIEW_CREATE);
        db.execSQL(FILMS_MOVIE_VIEW_CREATE);
        db.execSQL(FILMS_SHOW_VIEW_CREATE);
        db.execSQL(FILMS_EPISODE_VIEW_CREATE);
        db.execSQL(PRODUCES_MOVIE_VIEW_CREATE);
        db.execSQL(PRODUCES_SHOW_VIEW_CREATE);
        db.execSQL(BELONGS_MOVIE_VIEW_CREATE);
        db.execSQL(BELONGS_SHOW_VIEW_CREATE);
        db.execSQL(ALL_VIDEOS_VIEW_CREATE_v24);

        db.execSQL(GUESTS_VIEW_INSERT_TRIGGER);
        db.execSQL(PLAYS_MOVIE_VIEW_INSERT_TRIGGER);
        db.execSQL(PLAYS_SHOW_VIEW_INSERT_TRIGGER);
        db.execSQL(FILMS_MOVIE_VIEW_INSERT_TRIGGER);
        db.execSQL(FILMS_SHOW_VIEW_INSERT_TRIGGER);
        db.execSQL(FILMS_EPISODE_VIEW_INSERT_TRIGGER);
        db.execSQL(PRODUCES_MOVIE_VIEW_INSERT_TRIGGER);
        db.execSQL(PRODUCES_SHOW_VIEW_INSERT_TRIGGER);
        db.execSQL(BELONGS_MOVIE_VIEW_INSERT_TRIGGER);
        db.execSQL(BELONGS_SHOW_VIEW_INSERT_TRIGGER);

        db.execSQL(ACTOR_DELETABLE_VIEW_CREATE);
        db.execSQL(DIRECTOR_DELETABLE_VIEW_CREATE);
        db.execSQL(GENRE_DELETABLE_VIEW_CREATE);
        db.execSQL(STUDIO_DELETABLE_VIEW_CREATE);

        db.execSQL(EPISODE_DELETE_TRIGGER_CREATE);
        db.execSQL(SHOW_DELETE_TRIGGER_CREATE);
        db.execSQL(MOVIE_DELETE_TRIGGER_CREATE);
        db.execSQL(EPISODE_INSERT_TRIGGER_CREATE);
        db.execSQL(MOVIE_INSERT_TRIGGER_CREATE);

        // create views that format everything
        db.execSQL(CREATE_VIEW_SHOW_GENRES);
        db.execSQL(CREATE_VIEW_MOVIE_GENRES);

        db.execSQL(CREATE_VIEW_SHOW_DIRECTORS);
        db.execSQL(CREATE_VIEW_EPISODE_DIRECTORS);
        db.execSQL(CREATE_VIEW_MOVIE_DIRECTORS);

        db.execSQL(CREATE_VIEW_SHOW_ACTORS);
        db.execSQL(CREATE_VIEW_EPISODE_ACTORS);
        db.execSQL(CREATE_VIEW_MOVIE_ACTORS);

        db.execSQL(CREATE_VIEW_SHOW_STUDIOS);
        db.execSQL(CREATE_VIEW_MOVIE_STUDIOS);

        // V11
        db.execSQL(CREATE_MOVIE_POSTERS_TABLE);
        db.execSQL(CREATE_MOVIE_POSTERS_DELETE_TRIGGER);
        db.execSQL(CREATE_MOVIE_BACKDROPS_TABLE);
        db.execSQL(CREATE_MOVIE_BACKDROPS_DELETE_TRIGGER);
        db.execSQL(CREATE_SHOW_POSTERS_TABLE);
        db.execSQL(CREATE_SHOW_POSTERS_DELETE_TRIGGER);
        db.execSQL(CREATE_SHOW_BACKDROPS_TABLE);
        db.execSQL(CREATE_SHOW_BACKDROPS_DELETE_TRIGGER);

        // V28
        db.execSQL(CREATE_MOVIE_TRAILERS_TABLE);
    }

    public static void upgradeTo(SQLiteDatabase db, int toVersion) {
        if (toVersion == 37) {
            if (log.isDebugEnabled()) log.debug("upgradeTo: {}", toVersion);
            db.execSQL("ALTER TABLE " + MOVIE_TABLE_NAME + " ADD COLUMN " + VideoStore.Video.VideoColumns.NOVA_PINNED + " INTEGER DEFAULT (0)");
            db.execSQL("ALTER TABLE " + SHOW_TABLE_NAME + " ADD COLUMN " + VideoStore.Video.VideoColumns.NOVA_PINNED + " INTEGER DEFAULT (0)");
        }
        if (toVersion == 38) {
            if (log.isDebugEnabled()) log.debug("upgradeTo: {}", toVersion);
            db.execSQL("ALTER TABLE " + MOVIE_TABLE_NAME + " ADD COLUMN " + VideoStore.Video.VideoColumns.SCRAPER_C_ID + " INTEGER DEFAULT (-1)");
            db.execSQL(CREATE_MOVIE_COLLECTION_TABLE);
        }
        if (toVersion == 39) {
            if (log.isDebugEnabled()) log.debug("upgradeTo: {}", toVersion);
            // create indexes to every non foreign keys with delete to speed up huge batch of delete in files_scanned during directory moves on network shares
            // performance hit comes from the cascade of triggers
            // without index, each delete from master table requires search through entire child table for foreign key'd items in O(N)
            // with index it is much lower (O(1) or whatever the index achieves)
            if (log.isDebugEnabled()) log.debug("upgradeTo: creating indexes");
            db.execSQL("CREATE INDEX subtitles_idx ON subtitles(file_id)");
            db.execSQL("CREATE INDEX movie_trailers_idx ON movie_trailers(movie_id)");
            db.execSQL("CREATE INDEX movie_backdrops_idx ON movie_backdrops(movie_id)");
            db.execSQL("CREATE INDEX movie_posters_idx ON movie_posters(movie_id)");
            db.execSQL("CREATE INDEX show_backdrops_idx ON show_backdrops(show_id)");
            db.execSQL("CREATE INDEX show_posters_idx ON show_posters(show_id)");
            db.execSQL("CREATE INDEX EPISODE_files_idx ON EPISODE(video_id)");
            db.execSQL("CREATE INDEX EPISODE_show_idx ON EPISODE(show_episode)");
            db.execSQL("CREATE INDEX MOVIE_idx ON MOVIE(video_id)");
            db.execSQL("CREATE INDEX GUESTS_idx ON GUESTS(actor_guests)");
            db.execSQL("CREATE INDEX FILMS_MOVIE_idx ON FILMS_MOVIE(director_films)");
            db.execSQL("CREATE INDEX BELONGS_MOVIE_idx ON BELONGS_MOVIE(genre_belongs)");
            db.execSQL("CREATE INDEX PLAYS_MOVIE_idx ON PLAYS_MOVIE(actor_plays)");
            db.execSQL("CREATE INDEX PRODUCES_MOVIE_idx ON PRODUCES_MOVIE(studio_produces)");
            db.execSQL("CREATE INDEX FILMS_EPISODE_idx ON FILMS_EPISODE(director_films)");
            db.execSQL("CREATE INDEX FILMS_SHOW_idx ON FILMS_SHOW(director_films)");
            db.execSQL("CREATE INDEX BELONGS_SHOW_idx ON BELONGS_SHOW(genre_belongs)");
            db.execSQL("CREATE INDEX PLAYS_SHOW_idx ON PLAYS_SHOW(actor_plays)");
            db.execSQL("CREATE INDEX PRODUCES_SHOW_idx ON PRODUCES_SHOW(studio_produces)");
            db.execSQL("CREATE INDEX files_scraper_idx ON files(ArchosMediaScraper_id, ArchosMediaScraper_type)");
            db.execSQL("CREATE INDEX MOVIE_cover_idx ON MOVIE(cover_movie)");
            // Replace the deletion triggers without the per-delete cleanup of v_.*_deletable views.
            SQLiteUtils.replaceTriggersCompat(db,
                    new String[] {"episode_delete", "show_delete", "movie_delete"},
                    EPISODE_DELETE_TRIGGER_CREATE_v2,
                    SHOW_DELETE_TRIGGER_CREATE_v2,
                    MOVIE_DELETE_TRIGGER_CREATE_v2);
            if (log.isDebugEnabled()) log.debug("upgradeTo: all good");
        }
        if (toVersion == 40) {
            if (log.isDebugEnabled()) log.debug("upgradeTo: {}", toVersion);
            db.execSQL("ALTER TABLE " + MOVIE_TABLE_NAME + " ADD COLUMN " + ScraperStore.Movie.WRITERS_FORMATTED + " TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE " + SHOW_TABLE_NAME + " ADD COLUMN " + ScraperStore.Show.WRITERS_FORMATTED + " TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE " + EPISODE_TABLE_NAME + " ADD COLUMN " + ScraperStore.Episode.WRITERS_FORMATTED + " TEXT DEFAULT ''");
            db.execSQL(WRITERS_TABLE_CREATE);
            db.execSQL(WRITERS_MOVIE_TABLE_CREATE);
            db.execSQL(WRITERS_EPISODE_TABLE_CREATE);
            db.execSQL(WRITERS_SHOW_TABLE_CREATE);
            db.execSQL(WRITERS_MOVIE_VIEW_CREATE);
            db.execSQL(WRITERS_SHOW_VIEW_CREATE);
            db.execSQL(WRITERS_EPISODE_VIEW_CREATE);
            db.execSQL(WRITERS_MOVIE_VIEW_INSERT_TRIGGER);
            db.execSQL(WRITERS_SHOW_VIEW_INSERT_TRIGGER);
            db.execSQL(WRITERS_EPISODE_VIEW_INSERT_TRIGGER);
            db.execSQL(WRITER_DELETABLE_VIEW_CREATE);
            db.execSQL(CREATE_VIEW_SHOW_WRITERS);
            db.execSQL(CREATE_VIEW_EPISODE_WRITERS);
            db.execSQL(CREATE_VIEW_MOVIE_WRITERS);
            if (log.isDebugEnabled()) log.debug("upgradeTo: creating indexes");
            // cf. v39 migration create indexes to speed up rescan in case of delete/renames
            db.execSQL("CREATE INDEX WRITERS_MOVIE_idx ON WRITERS_MOVIE(writer_writers)");
            db.execSQL("CREATE INDEX WRITERS_EPISODE_idx ON WRITERS_EPISODE(writer_writers)");
            db.execSQL("CREATE INDEX WRITERS_SHOW_idx ON WRITERS_SHOW(writer_writers)");
        }
        if (toVersion == 45) {
            if (log.isDebugEnabled()) log.debug("upgradeTo: {} - adding performance indexes for scraper data", toVersion);
            db.execSQL(CREATE_EPISODE_SEASON_EPISODE_IDX);
            db.execSQL(CREATE_MOVIE_YEAR_IDX);
            db.execSQL(CREATE_MOVIE_RATING_IDX);
            db.execSQL(CREATE_EPISODE_VIDEO_ID_IDX);
            db.execSQL(CREATE_MOVIE_VIDEO_ID_IDX);
        }
        if (toVersion == 46) {
            if (log.isDebugEnabled()) log.debug("upgradeTo: {} - adding additional performance indexes for scraper data", toVersion);
            db.execSQL(CREATE_SHOW_RATING_IDX);
            db.execSQL(CREATE_EPISODE_AIRED_IDX);
            db.execSQL(CREATE_MOVIE_COLLECTION_IDX);
        }
        if (toVersion == 47) {
            if (log.isDebugEnabled()) log.debug("upgradeTo: {} - adding WatchingUpNextLoader performance indexes", toVersion);
            db.execSQL(CREATE_EPISODE_SERIES_ORDERING_IDX);
            db.execSQL(CREATE_MOVIE_COLLECTION_YEAR_IDX);
            db.execSQL(CREATE_EPISODE_WATCHED_ORDERING_IDX);
            db.execSQL(CREATE_MOVIE_WATCHED_ORDERING_IDX);
        }
        if (toVersion == 48) {
            if (log.isDebugEnabled()) log.debug("upgradeTo: {} - no scraper changes (handled in VideoOpenHelper)", toVersion);
            // Version 48 changes are handled in VideoOpenHelper, not in ScraperTables
        }
        if (toVersion == 49) {
            if (log.isDebugEnabled()) log.debug("upgradeTo: {} - adding movie release_date column and populating from year", toVersion);
            db.execSQL("ALTER TABLE " + MOVIE_TABLE_NAME + " ADD COLUMN " + ScraperStore.Movie.RELEASE_DATE + " TEXT");
            // Infer release_date from year_movie: format as YYYY-01-01 (January 1st of release year)
            db.execSQL("UPDATE " + MOVIE_TABLE_NAME + " SET " + ScraperStore.Movie.RELEASE_DATE + " = " +
                    "printf('%04d-01-01', " + ScraperStore.Movie.YEAR + ") WHERE " + ScraperStore.Movie.YEAR + " > 0");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_movie_release_date ON " + MOVIE_TABLE_NAME + "(" + ScraperStore.Movie.RELEASE_DATE + ")");
        }
        if (toVersion == 51) {
            if (log.isDebugEnabled()) log.debug("upgradeTo: {} - adding UNIQUE constraints to movie poster/backdrop tables to prevent duplicates", toVersion);

            // Movie Posters - recreate with UNIQUE constraints
            if (log.isDebugEnabled()) log.debug("upgradeTo: recreating movie_posters table with UNIQUE constraints");
            db.execSQL("CREATE TABLE movie_posters_new ( \n" +
                    "    _id             INTEGER PRIMARY KEY,\n" +
                    "    movie_id        INTEGER REFERENCES movie ( _id ) ON DELETE CASCADE\n" +
                    "                                                     ON UPDATE CASCADE,\n" +
                    "    m_po_thumb_url  TEXT,\n" +
                    "    m_po_thumb_file TEXT UNIQUE ON CONFLICT IGNORE,\n" +
                    "    m_po_large_url  TEXT,\n" +
                    "    m_po_large_file TEXT UNIQUE ON CONFLICT IGNORE\n" +
                    ")");

            // Copy data, automatically deduplicating via UNIQUE constraint
            db.execSQL("INSERT OR IGNORE INTO movie_posters_new SELECT * FROM movie_posters");

            // Drop old table and triggers
            db.execSQL(DROP_MOVIE_POSTERS_DELETE_TRIGGER);
            db.execSQL("DROP TABLE movie_posters");

            // Rename new table
            db.execSQL("ALTER TABLE movie_posters_new RENAME TO movie_posters");

            // Recreate trigger and index
            db.execSQL(CREATE_MOVIE_POSTERS_DELETE_TRIGGER);
            db.execSQL("CREATE INDEX movie_posters_idx ON movie_posters(movie_id)");

            // Movie Backdrops - same process
            if (log.isDebugEnabled()) log.debug("upgradeTo: recreating movie_backdrops table with UNIQUE constraints");
            db.execSQL("CREATE TABLE movie_backdrops_new ( \n" +
                    "    _id             INTEGER PRIMARY KEY,\n" +
                    "    movie_id        INTEGER REFERENCES movie ( _id ) ON DELETE CASCADE\n" +
                    "                                                     ON UPDATE CASCADE,\n" +
                    "    m_bd_thumb_url  TEXT,\n" +
                    "    m_bd_thumb_file TEXT UNIQUE ON CONFLICT IGNORE,\n" +
                    "    m_bd_large_url  TEXT,\n" +
                    "    m_bd_large_file TEXT UNIQUE ON CONFLICT IGNORE\n" +
                    ")");

            db.execSQL("INSERT OR IGNORE INTO movie_backdrops_new SELECT * FROM movie_backdrops");
            db.execSQL(DROP_MOVIE_BACKDROPS_DELETE_TRIGGER);
            db.execSQL("DROP TABLE movie_backdrops");
            db.execSQL("ALTER TABLE movie_backdrops_new RENAME TO movie_backdrops");
            db.execSQL(CREATE_MOVIE_BACKDROPS_DELETE_TRIGGER);
            db.execSQL("CREATE INDEX movie_backdrops_idx ON movie_backdrops(movie_id)");

            if (log.isDebugEnabled()) log.debug("upgradeTo: {} - movie poster/backdrop tables successfully migrated", toVersion);
        }
        if (toVersion == 54) {
            if (log.isDebugEnabled()) log.debug("upgradeTo: {} - adding performance indexes for metadata protection", toVersion);
            db.execSQL("CREATE INDEX IF NOT EXISTS MOVIE_backdrop_idx ON " + MOVIE_TABLE_NAME + "(" + ScraperStore.Movie.BACKDROP + ")");
            db.execSQL("CREATE INDEX IF NOT EXISTS SHOW_cover_idx ON " + SHOW_TABLE_NAME + "(" + ScraperStore.Show.COVER + ")");
            db.execSQL("CREATE INDEX IF NOT EXISTS SHOW_backdrop_idx ON " + SHOW_TABLE_NAME + "(" + ScraperStore.Show.BACKDROP + ")");
            db.execSQL("CREATE INDEX IF NOT EXISTS EPISODE_cover_idx ON " + EPISODE_TABLE_NAME + "(" + ScraperStore.Episode.COVER + ")");
            db.execSQL("CREATE INDEX IF NOT EXISTS EPISODE_picture_idx ON " + EPISODE_TABLE_NAME + "(" + ScraperStore.Episode.PICTURE + ")");

            // Indexes for auxiliary tables used in the protection query
            db.execSQL("CREATE INDEX IF NOT EXISTS movie_posters_large_file_idx ON movie_posters(m_po_large_file)");
            db.execSQL("CREATE INDEX IF NOT EXISTS movie_posters_thumb_file_idx ON movie_posters(m_po_thumb_file)");
            db.execSQL("CREATE INDEX IF NOT EXISTS movie_backdrops_large_file_idx ON movie_backdrops(m_bd_large_file)");
            db.execSQL("CREATE INDEX IF NOT EXISTS movie_backdrops_thumb_file_idx ON movie_backdrops(m_bd_thumb_file)");
            db.execSQL("CREATE INDEX IF NOT EXISTS show_posters_large_file_idx ON show_posters(s_po_large_file)");
            db.execSQL("CREATE INDEX IF NOT EXISTS show_posters_thumb_file_idx ON show_posters(s_po_thumb_file)");
            db.execSQL("CREATE INDEX IF NOT EXISTS show_backdrops_large_file_idx ON show_backdrops(s_bd_large_file)");
            db.execSQL("CREATE INDEX IF NOT EXISTS show_backdrops_thumb_file_idx ON show_backdrops(s_bd_thumb_file)");
        }
        if (toVersion == 55) {
            if (log.isDebugEnabled()) log.debug("upgradeTo: {} - recreating movie/show/episode deletion triggers with 0/0 reset", toVersion);
            // Keep the v39 performance optimization while applying the -1 -> 0 reset.
            SQLiteUtils.replaceTriggersCompat(db,
                    new String[] {"episode_delete", "show_delete", "movie_delete"},
                    EPISODE_DELETE_TRIGGER_CREATE_v2,
                    SHOW_DELETE_TRIGGER_CREATE_v2,
                    MOVIE_DELETE_TRIGGER_CREATE_v2);
        }
        if (toVersion == 56) {
            if (log.isDebugEnabled()) log.debug("upgradeTo: {} - making artwork rows owner-specific", toVersion);
            upgradeArtworkOwnershipTo56(db);
        }
        if (toVersion == 58) {
            if (log.isDebugEnabled()) log.debug("upgradeTo: {} - adding original language and title metadata", toVersion);
            db.execSQL("ALTER TABLE " + MOVIE_TABLE_NAME + " ADD COLUMN " +
                    ScraperStore.Movie.ORIGINAL_LANGUAGE + " TEXT NOT NULL DEFAULT 'und'");
            db.execSQL("ALTER TABLE " + MOVIE_TABLE_NAME + " ADD COLUMN " +
                    ScraperStore.Movie.ORIGINAL_TITLE + " TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE " + MOVIE_TABLE_NAME + " ADD COLUMN " +
                    ScraperStore.Movie.SPOKEN_LANGUAGES + " TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE " + SHOW_TABLE_NAME + " ADD COLUMN " +
                    ScraperStore.Show.ORIGINAL_LANGUAGE + " TEXT NOT NULL DEFAULT 'und'");
            db.execSQL("ALTER TABLE " + SHOW_TABLE_NAME + " ADD COLUMN " +
                    ScraperStore.Show.ORIGINAL_TITLE + " TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE " + SHOW_TABLE_NAME + " ADD COLUMN " +
                    ScraperStore.Show.SPOKEN_LANGUAGES + " TEXT NOT NULL DEFAULT ''");
            // Explicitly normalize rows from partially upgraded or externally restored databases.
            db.execSQL("UPDATE " + MOVIE_TABLE_NAME + " SET " + ScraperStore.Movie.ORIGINAL_LANGUAGE +
                    " = 'und' WHERE " + ScraperStore.Movie.ORIGINAL_LANGUAGE + " IS NULL OR " +
                    "trim(" + ScraperStore.Movie.ORIGINAL_LANGUAGE + ") = ''");
            db.execSQL("UPDATE " + MOVIE_TABLE_NAME + " SET " + ScraperStore.Movie.ORIGINAL_TITLE +
                    " = '' WHERE " + ScraperStore.Movie.ORIGINAL_TITLE + " IS NULL");
            db.execSQL("UPDATE " + MOVIE_TABLE_NAME + " SET " + ScraperStore.Movie.SPOKEN_LANGUAGES +
                    " = '' WHERE " + ScraperStore.Movie.SPOKEN_LANGUAGES + " IS NULL");
            db.execSQL("UPDATE " + SHOW_TABLE_NAME + " SET " + ScraperStore.Show.ORIGINAL_LANGUAGE +
                    " = 'und' WHERE " + ScraperStore.Show.ORIGINAL_LANGUAGE + " IS NULL OR " +
                    "trim(" + ScraperStore.Show.ORIGINAL_LANGUAGE + ") = ''");
            db.execSQL("UPDATE " + SHOW_TABLE_NAME + " SET " + ScraperStore.Show.ORIGINAL_TITLE +
                    " = '' WHERE " + ScraperStore.Show.ORIGINAL_TITLE + " IS NULL");
            db.execSQL("UPDATE " + SHOW_TABLE_NAME + " SET " + ScraperStore.Show.SPOKEN_LANGUAGES +
                    " = '' WHERE " + ScraperStore.Show.SPOKEN_LANGUAGES + " IS NULL");
        }
    }

    /**
     * Rebuilds artwork tables so the same physical image may be referenced by
     * multiple metadata owners. Only rows are duplicated; image files remain
     * shared and are protected by VideoStoreImportService before disk deletion.
     *
     * Keep this migration compatible with SQLite 3.8 (Android API 23): do not use
     * UPSERT, window functions, RETURNING, generated columns, or modern ALTER
     * TABLE extensions here.
     */
    private static void upgradeArtworkOwnershipTo56(SQLiteDatabase db) {
        rebuildMoviePostersTo56(db);
        rebuildMovieBackdropsTo56(db);
        rebuildShowPostersTo56(db);
        rebuildShowBackdropsTo56(db);

        // Owner lookup indexes used by scraper queries and foreign-key cascades.
        db.execSQL("CREATE INDEX movie_posters_idx ON movie_posters(movie_id)");
        db.execSQL("CREATE INDEX movie_backdrops_idx ON movie_backdrops(movie_id)");
        db.execSQL("CREATE INDEX show_posters_idx ON show_posters(show_id)");
        db.execSQL("CREATE INDEX show_backdrops_idx ON show_backdrops(show_id)");

        // Recreate the v54 protection indexes removed with the old tables.
        db.execSQL("CREATE INDEX movie_posters_large_file_idx ON movie_posters(m_po_large_file)");
        db.execSQL("CREATE INDEX movie_posters_thumb_file_idx ON movie_posters(m_po_thumb_file)");
        db.execSQL("CREATE INDEX movie_backdrops_large_file_idx ON movie_backdrops(m_bd_large_file)");
        db.execSQL("CREATE INDEX movie_backdrops_thumb_file_idx ON movie_backdrops(m_bd_thumb_file)");
        db.execSQL("CREATE INDEX show_posters_large_file_idx ON show_posters(s_po_large_file)");
        db.execSQL("CREATE INDEX show_posters_thumb_file_idx ON show_posters(s_po_thumb_file)");
        db.execSQL("CREATE INDEX show_backdrops_large_file_idx ON show_backdrops(s_bd_large_file)");
        db.execSQL("CREATE INDEX show_backdrops_thumb_file_idx ON show_backdrops(s_bd_thumb_file)");
    }

    private static void rebuildMoviePostersTo56(SQLiteDatabase db) {
        db.execSQL(DROP_MOVIE_POSTERS_DELETE_TRIGGER);
        db.execSQL("DROP TABLE IF EXISTS movie_posters_v56");
        db.execSQL("CREATE TABLE movie_posters_v56 (" +
                "_id INTEGER PRIMARY KEY," +
                "movie_id INTEGER REFERENCES movie(_id) ON DELETE CASCADE ON UPDATE CASCADE," +
                "m_po_thumb_url TEXT," +
                "m_po_thumb_file TEXT," +
                "m_po_large_url TEXT," +
                "m_po_large_file TEXT," +
                "UNIQUE(movie_id,m_po_thumb_file) ON CONFLICT IGNORE," +
                "UNIQUE(movie_id,m_po_large_file) ON CONFLICT IGNORE)");
        db.execSQL("INSERT OR IGNORE INTO movie_posters_v56 " +
                "SELECT * FROM movie_posters ORDER BY _id");

        // Restore all artwork choices for duplicate files scraped as the same
        // online movie. V51 could retain the rows for only one of those owners.
        db.execSQL("INSERT OR IGNORE INTO movie_posters_v56 " +
                "(movie_id,m_po_thumb_url,m_po_thumb_file,m_po_large_url,m_po_large_file) " +
                "SELECT target._id,p.m_po_thumb_url,p.m_po_thumb_file,p.m_po_large_url,p.m_po_large_file " +
                "FROM MOVIE target JOIN MOVIE source ON source.m_online_id=target.m_online_id " +
                "JOIN movie_posters p ON p.movie_id=source._id " +
                "WHERE target.m_online_id IS NOT NULL AND target.m_online_id>0");
        // Clone the selected row for its actual owner. This repairs v51-v55
        // databases where a global conflict returned another movie's row id.
        db.execSQL("INSERT OR IGNORE INTO movie_posters_v56 " +
                "(movie_id,m_po_thumb_url,m_po_thumb_file,m_po_large_url,m_po_large_file) " +
                "SELECT MOVIE._id,p.m_po_thumb_url,p.m_po_thumb_file,p.m_po_large_url,p.m_po_large_file " +
                "FROM MOVIE JOIN movie_posters p ON p._id=MOVIE.m_poster_id");
        // A dangling selected id can still be recovered from the direct path.
        db.execSQL("INSERT OR IGNORE INTO movie_posters_v56(movie_id,m_po_large_file) " +
                "SELECT _id,cover_movie FROM MOVIE WHERE cover_movie IS NOT NULL");

        remapSelectedArtwork(db, "MOVIE", "_id", "_id", "m_poster_id", "cover_movie",
                "movie_posters", "movie_posters_v56", "movie_id",
                "m_po_large_file", "m_po_thumb_file");

        db.execSQL("DROP TABLE movie_posters");
        db.execSQL("ALTER TABLE movie_posters_v56 RENAME TO movie_posters");
        db.execSQL(CREATE_MOVIE_POSTERS_DELETE_TRIGGER);
    }

    private static void rebuildMovieBackdropsTo56(SQLiteDatabase db) {
        db.execSQL(DROP_MOVIE_BACKDROPS_DELETE_TRIGGER);
        db.execSQL("DROP TABLE IF EXISTS movie_backdrops_v56");
        db.execSQL("CREATE TABLE movie_backdrops_v56 (" +
                "_id INTEGER PRIMARY KEY," +
                "movie_id INTEGER REFERENCES movie(_id) ON DELETE CASCADE ON UPDATE CASCADE," +
                "m_bd_thumb_url TEXT," +
                "m_bd_thumb_file TEXT," +
                "m_bd_large_url TEXT," +
                "m_bd_large_file TEXT," +
                "UNIQUE(movie_id,m_bd_thumb_file) ON CONFLICT IGNORE," +
                "UNIQUE(movie_id,m_bd_large_file) ON CONFLICT IGNORE)");
        db.execSQL("INSERT OR IGNORE INTO movie_backdrops_v56 " +
                "SELECT * FROM movie_backdrops ORDER BY _id");
        db.execSQL("INSERT OR IGNORE INTO movie_backdrops_v56 " +
                "(movie_id,m_bd_thumb_url,m_bd_thumb_file,m_bd_large_url,m_bd_large_file) " +
                "SELECT target._id,b.m_bd_thumb_url,b.m_bd_thumb_file,b.m_bd_large_url,b.m_bd_large_file " +
                "FROM MOVIE target JOIN MOVIE source ON source.m_online_id=target.m_online_id " +
                "JOIN movie_backdrops b ON b.movie_id=source._id " +
                "WHERE target.m_online_id IS NOT NULL AND target.m_online_id>0");
        db.execSQL("INSERT OR IGNORE INTO movie_backdrops_v56 " +
                "(movie_id,m_bd_thumb_url,m_bd_thumb_file,m_bd_large_url,m_bd_large_file) " +
                "SELECT MOVIE._id,b.m_bd_thumb_url,b.m_bd_thumb_file,b.m_bd_large_url,b.m_bd_large_file " +
                "FROM MOVIE JOIN movie_backdrops b ON b._id=MOVIE.m_backdrop_id");
        db.execSQL("INSERT OR IGNORE INTO movie_backdrops_v56(movie_id,m_bd_large_file) " +
                "SELECT _id,backdrop_movie FROM MOVIE WHERE backdrop_movie IS NOT NULL");

        remapSelectedArtwork(db, "MOVIE", "_id", "_id", "m_backdrop_id", "backdrop_movie",
                "movie_backdrops", "movie_backdrops_v56", "movie_id",
                "m_bd_large_file", "m_bd_thumb_file");

        db.execSQL("DROP TABLE movie_backdrops");
        db.execSQL("ALTER TABLE movie_backdrops_v56 RENAME TO movie_backdrops");
        db.execSQL(CREATE_MOVIE_BACKDROPS_DELETE_TRIGGER);
    }

    private static void rebuildShowPostersTo56(SQLiteDatabase db) {
        db.execSQL(DROP_SHOW_POSTERS_DELETE_TRIGGER);
        db.execSQL("DROP TABLE IF EXISTS show_posters_v56");
        db.execSQL("CREATE TABLE show_posters_v56 (" +
                "_id INTEGER PRIMARY KEY," +
                "show_id INTEGER REFERENCES show(_id) ON DELETE CASCADE ON UPDATE CASCADE," +
                "s_po_thumb_url TEXT," +
                "s_po_thumb_file TEXT," +
                "s_po_large_url TEXT," +
                "s_po_large_file TEXT," +
                "s_po_season INTEGER DEFAULT(-1)," +
                "UNIQUE(show_id,s_po_thumb_file) ON CONFLICT IGNORE," +
                "UNIQUE(show_id,s_po_large_file) ON CONFLICT IGNORE)");
        db.execSQL("INSERT OR IGNORE INTO show_posters_v56 " +
                "SELECT * FROM show_posters ORDER BY _id");
        db.execSQL("INSERT OR IGNORE INTO show_posters_v56 " +
                "(show_id,s_po_thumb_url,s_po_thumb_file,s_po_large_url,s_po_large_file,s_po_season) " +
                "SELECT target._id,p.s_po_thumb_url,p.s_po_thumb_file,p.s_po_large_url,p.s_po_large_file,p.s_po_season " +
                "FROM SHOW target JOIN SHOW source ON source.s_online_id=target.s_online_id " +
                "JOIN show_posters p ON p.show_id=source._id " +
                "WHERE target.s_online_id IS NOT NULL AND target.s_online_id>0");
        db.execSQL("INSERT OR IGNORE INTO show_posters_v56 " +
                "(show_id,s_po_thumb_url,s_po_thumb_file,s_po_large_url,s_po_large_file,s_po_season) " +
                "SELECT SHOW._id,p.s_po_thumb_url,p.s_po_thumb_file,p.s_po_large_url,p.s_po_large_file,p.s_po_season " +
                "FROM SHOW JOIN show_posters p ON p._id=SHOW.s_poster_id");
        db.execSQL("INSERT OR IGNORE INTO show_posters_v56 " +
                "(show_id,s_po_thumb_url,s_po_thumb_file,s_po_large_url,s_po_large_file,s_po_season) " +
                "SELECT EPISODE.show_episode,p.s_po_thumb_url,p.s_po_thumb_file,p.s_po_large_url,p.s_po_large_file,p.s_po_season " +
                "FROM EPISODE JOIN show_posters p ON p._id=EPISODE.e_poster_id " +
                "WHERE EPISODE.show_episode IS NOT NULL");
        db.execSQL("INSERT OR IGNORE INTO show_posters_v56(show_id,s_po_large_file) " +
                "SELECT _id,cover_show FROM SHOW WHERE cover_show IS NOT NULL");
        db.execSQL("INSERT OR IGNORE INTO show_posters_v56(show_id,s_po_large_file) " +
                "SELECT show_episode,cover_episode FROM EPISODE " +
                "WHERE show_episode IS NOT NULL AND cover_episode IS NOT NULL");

        remapSelectedArtwork(db, "SHOW", "_id", "_id", "s_poster_id", "cover_show",
                "show_posters", "show_posters_v56", "show_id",
                "s_po_large_file", "s_po_thumb_file");
        remapSelectedArtwork(db, "EPISODE", "_id", "show_episode", "e_poster_id", "cover_episode",
                "show_posters", "show_posters_v56", "show_id",
                "s_po_large_file", "s_po_thumb_file");

        db.execSQL("DROP TABLE show_posters");
        db.execSQL("ALTER TABLE show_posters_v56 RENAME TO show_posters");
        db.execSQL(CREATE_SHOW_POSTERS_DELETE_TRIGGER);
    }

    private static void rebuildShowBackdropsTo56(SQLiteDatabase db) {
        db.execSQL(DROP_SHOW_BACKDROPS_DELETE_TRIGGER);
        db.execSQL("DROP TABLE IF EXISTS show_backdrops_v56");
        db.execSQL("CREATE TABLE show_backdrops_v56 (" +
                "_id INTEGER PRIMARY KEY," +
                "show_id INTEGER REFERENCES show(_id) ON DELETE CASCADE ON UPDATE CASCADE," +
                "s_bd_thumb_url TEXT," +
                "s_bd_thumb_file TEXT," +
                "s_bd_large_url TEXT," +
                "s_bd_large_file TEXT," +
                "UNIQUE(show_id,s_bd_thumb_file) ON CONFLICT IGNORE," +
                "UNIQUE(show_id,s_bd_large_file) ON CONFLICT IGNORE)");
        db.execSQL("INSERT OR IGNORE INTO show_backdrops_v56 " +
                "SELECT * FROM show_backdrops ORDER BY _id");
        db.execSQL("INSERT OR IGNORE INTO show_backdrops_v56 " +
                "(show_id,s_bd_thumb_url,s_bd_thumb_file,s_bd_large_url,s_bd_large_file) " +
                "SELECT target._id,b.s_bd_thumb_url,b.s_bd_thumb_file,b.s_bd_large_url,b.s_bd_large_file " +
                "FROM SHOW target JOIN SHOW source ON source.s_online_id=target.s_online_id " +
                "JOIN show_backdrops b ON b.show_id=source._id " +
                "WHERE target.s_online_id IS NOT NULL AND target.s_online_id>0");
        db.execSQL("INSERT OR IGNORE INTO show_backdrops_v56 " +
                "(show_id,s_bd_thumb_url,s_bd_thumb_file,s_bd_large_url,s_bd_large_file) " +
                "SELECT SHOW._id,b.s_bd_thumb_url,b.s_bd_thumb_file,b.s_bd_large_url,b.s_bd_large_file " +
                "FROM SHOW JOIN show_backdrops b ON b._id=SHOW.s_backdrop_id");
        db.execSQL("INSERT OR IGNORE INTO show_backdrops_v56(show_id,s_bd_large_file) " +
                "SELECT _id,backdrop_show FROM SHOW WHERE backdrop_show IS NOT NULL");

        remapSelectedArtwork(db, "SHOW", "_id", "_id", "s_backdrop_id", "backdrop_show",
                "show_backdrops", "show_backdrops_v56", "show_id",
                "s_bd_large_file", "s_bd_thumb_file");

        db.execSQL("DROP TABLE show_backdrops");
        db.execSQL("ALTER TABLE show_backdrops_v56 RENAME TO show_backdrops");
        db.execSQL(CREATE_SHOW_BACKDROPS_DELETE_TRIGGER);
    }

    /**
     * Remap a selected image id without correlated UPDATE syntax. Some Android
     * SQLite releases do not resolve the target table from nested JOIN clauses.
     */
    private static void remapSelectedArtwork(SQLiteDatabase db, String ownerTable,
            String rowIdColumn, String imageOwnerSourceColumn, String selectedIdColumn,
            String directFileColumn, String oldImageTable, String newImageTable,
            String imageOwnerColumn, String largeFileColumn, String thumbFileColumn) {
        String[] projection = {
                rowIdColumn, imageOwnerSourceColumn, selectedIdColumn, directFileColumn
        };
        String selection = selectedIdColumn + " IS NOT NULL OR " + directFileColumn + " IS NOT NULL";
        try (Cursor owners = db.query(ownerTable, projection, selection,
                null, null, null, null)) {
            while (owners.moveToNext()) {
                long rowId = owners.getLong(0);
                Long imageOwnerId = owners.isNull(1) ? null : owners.getLong(1);
                Long selectedId = owners.isNull(2) ? null : owners.getLong(2);
                String directFile = owners.getString(3);
                Long remappedId = findOwnedArtworkId(db, oldImageTable, newImageTable,
                        imageOwnerColumn, largeFileColumn, thumbFileColumn,
                        imageOwnerId, selectedId, directFile);

                ContentValues update = new ContentValues();
                if (remappedId == null) {
                    update.putNull(selectedIdColumn);
                } else {
                    update.put(selectedIdColumn, remappedId);
                }
                db.update(ownerTable, update, rowIdColumn + "=?",
                        new String[] { String.valueOf(rowId) });
            }
        }
    }

    private static Long findOwnedArtworkId(SQLiteDatabase db, String oldImageTable,
            String newImageTable, String imageOwnerColumn, String largeFileColumn,
            String thumbFileColumn, Long ownerId, Long selectedId, String directFile) {
        if (ownerId == null) {
            return null;
        }

        String selectedLargeFile = null;
        String selectedThumbFile = null;
        if (selectedId != null) {
            try (Cursor selected = db.query(oldImageTable,
                    new String[] { largeFileColumn, thumbFileColumn }, "_id=?",
                    new String[] { String.valueOf(selectedId) }, null, null, null)) {
                if (selected.moveToFirst()) {
                    selectedLargeFile = selected.getString(0);
                    selectedThumbFile = selected.getString(1);
                }
            }
        }

        Long result = queryOwnedArtworkId(db, newImageTable, imageOwnerColumn,
                largeFileColumn, ownerId, selectedLargeFile);
        if (result == null) {
            result = queryOwnedArtworkId(db, newImageTable, imageOwnerColumn,
                    thumbFileColumn, ownerId, selectedThumbFile);
        }
        if (result == null && selectedId != null) {
            try (Cursor sameId = db.query(newImageTable, new String[] { "_id" },
                    "_id=? AND " + imageOwnerColumn + "=?",
                    new String[] { String.valueOf(selectedId), String.valueOf(ownerId) },
                    null, null, null)) {
                if (sameId.moveToFirst()) {
                    result = sameId.getLong(0);
                }
            }
        }
        if (result == null) {
            result = queryOwnedArtworkId(db, newImageTable, imageOwnerColumn,
                    largeFileColumn, ownerId, directFile);
        }
        return result;
    }

    private static Long queryOwnedArtworkId(SQLiteDatabase db, String imageTable,
            String imageOwnerColumn, String fileColumn, long ownerId, String file) {
        if (file == null) {
            return null;
        }
        try (Cursor cursor = db.query(imageTable, new String[] { "_id" },
                imageOwnerColumn + "=? AND " + fileColumn + "=?",
                new String[] { String.valueOf(ownerId), file },
                null, null, "_id", "1")) {
            return cursor.moveToFirst() ? cursor.getLong(0) : null;
        }
    }
}
