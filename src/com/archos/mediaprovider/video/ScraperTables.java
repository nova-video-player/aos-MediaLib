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

import android.database.sqlite.SQLiteDatabase;


public final class ScraperTables {
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
    public static final String STUDIOS_TABLE_NAME = "STUDIO";
    public static final String FILMS_MOVIE_TABLE_NAME = "FILMS_MOVIE";
    public static final String FILMS_SHOW_TABLE_NAME = "FILMS_SHOW";
    public static final String FILMS_EPISODE_TABLE_NAME = "FILMS_EPISODE";
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
    public static final String PRODUCES_MOVIE_VIEW_NAME = "V_PRODUCES_MOVIE";
    public static final String PRODUCES_SHOW_VIEW_NAME = "V_PRODUCES_SHOW";
    public static final String BELONGS_MOVIE_VIEW_NAME = "V_BELONGS_MOVIE";
    public static final String BELONGS_SHOW_VIEW_NAME = "V_BELONGS_SHOW";
    public static final String ALL_VIDEOS_VIEW_NAME = "v_all_videos";
    public static final String SEASONS_VIEW_NAME = "v_seasons";
    // these help deleting, only used internal
    public static final String ACTOR_DELETABLE_VIEW_NAME = "v_actor_deletable";
    public static final String DIRECTOR_DELETABLE_VIEW_NAME = "v_director_deletable";
    public static final String GENRE_DELETABLE_VIEW_NAME = "v_genre_deletable";
    public static final String STUDIO_DELETABLE_VIEW_NAME = "v_studio_deletable";
    /*
     * Columns names that we need and are not to be exposed.
     * Public ones are in the ScraperStore class.
     */
    private static final String FILMS_MOVIE_ID_MOVIE = "movie_films";
    private static final String FILMS_MOVIE_ID_DIRECTOR = "director_films";

    private static final String FILMS_SHOW_ID_SHOW = "show_films";
    private static final String FILMS_SHOW_ID_DIRECTOR = "director_films";

    private static final String FILMS_EPISODE_ID_DIRECTOR = "director_films";
    private static final String FILMS_EPISODE_ID_EPISODE = "episode_films";

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
        ScraperStore.Movie.BACKDROP + " TEXT)";

    private static final String SHOW_TABLE_CREATE =
        "CREATE TABLE " + SHOW_TABLE_NAME + " (" +
        ScraperStore.Show.ID + " INTEGER PRIMARY KEY NOT NULL," +
        ScraperStore.Show.NAME + " TEXT UNIQUE," +
        ScraperStore.Show.COVER + " TEXT," +
        ScraperStore.Show.PREMIERED + " INTEGER," +
        ScraperStore.Show.RATING + " FLOAT," +
        ScraperStore.Show.PLOT + " TEXT," +
        ScraperStore.Show.BACKDROP_URL + " TEXT," +
        ScraperStore.Show.BACKDROP + " TEXT)";

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
        ScraperStore.Episode.COVER + ")";

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
            // set scraper type / id to -1 if something is refering this episode
            "UPDATE " + VideoOpenHelper.FILES_TABLE_NAME + " SET ArchosMediaScraper_id=-1, ArchosMediaScraper_type=-1 " +
            "WHERE ArchosMediaScraper_id = OLD._id AND ArchosMediaScraper_type = " + ScraperStore.SCRAPER_TYPE_SHOW + ";" +
            "END";
    private static final String MOVIE_DELETE_TRIGGER_DROP = "DROP TRIGGER IF EXISTS movie_delete";
    private static final String MOVIE_DELETE_TRIGGER_CREATE =
            "CREATE TRIGGER movie_delete AFTER DELETE ON movie " +
            "BEGIN " +
            "delete from actor where _id in (select _id from v_actor_deletable); " +
            "delete from director where _id in (select _id from v_director_deletable); " +
            "delete from studio where _id in (select _id from v_studio_deletable); " +
            "delete from genre where _id in (select _id from v_genre_deletable); " +
            // set scraper type / id to -1 if something is refering this episode
            "UPDATE " + VideoOpenHelper.FILES_TABLE_NAME + " SET ArchosMediaScraper_id=-1, ArchosMediaScraper_type=-1 " +
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

    // views that combine above information with show / episode / movie
    public static final String VIEW_SHOW_ALL = "v_show_all";
    private static final String CREATE_VIEW_SHOW_ALL_V0 =
            "CREATE VIEW " + VIEW_SHOW_ALL + " AS\n" +
            "SELECT *\n" +
            "  FROM show\n" +
            "       NATURAL LEFT JOIN v_show_actors\n" +
            "       NATURAL LEFT JOIN v_show_directors\n" +
            "       NATURAL LEFT JOIN v_show_genres\n" +
            "       NATURAL LEFT JOIN v_show_studios";
    public static final String VIEW_EPISODE_ALL = "v_episode_all";
    private static final String CREATE_VIEW_EPISODE_ALL_V0 =
            "CREATE VIEW " + VIEW_EPISODE_ALL + " AS\n" +
            "SELECT *\n" +
            "  FROM episode\n" +
            "       NATURAL LEFT JOIN v_episode_actors\n" +
            "       NATURAL LEFT JOIN v_episode_directors";
    public static final String VIEW_MOVIE_ALL = "v_movie_all";
    private static final String CREATE_VIEW_MOVIE_ALL_V0 =
            "CREATE VIEW " + VIEW_MOVIE_ALL + " AS\n" +
            "SELECT *\n" +
            "  FROM movie\n" +
            "       NATURAL LEFT JOIN v_movie_actors\n" +
            "       NATURAL LEFT JOIN v_movie_directors\n" +
            "       NATURAL LEFT JOIN v_movie_genres\n" +
            "       NATURAL LEFT JOIN v_movie_studios";

    public static final String VIEW_VIDEO_ALL = "v_video_all";
    private static final String CREATE_VIEW_VIDEO_ALL_V0 =
            "CREATE VIEW " + VIEW_VIDEO_ALL + " AS\n" +
            "SELECT\n" +
            "    e.video_id AS _id,\n" +
            "    NULL AS m_id,\n" +
            "    e.show_episode AS s_id,\n" +
            "    e._id AS e_id,\n" +
            "    s.name_show AS scraper_name,\n" +
            "    NULL AS m_name,\n" +
            "    s.name_show AS s_name,\n" +
            "    e.name_episode AS e_name,\n" +
            "    e.season_episode AS e_season,\n" +
            "    e.number_episode AS e_episode,\n" +
            "    e.aired_episode AS e_aired,\n" +
            "    s.premiered_show AS s_premiered,\n" +
            "    NULL AS m_year,\n" +
            "    e.rating_episode AS rating,\n" +
            "    NULL AS m_rating,\n" +
            "    e.rating_episode AS e_rating,\n" +
            "    s.rating_show AS s_rating,\n" +
            "    e.plot_episode AS plot,\n" +
            "    NULL AS m_plot,\n" +
            "    e.plot_episode AS e_plot,\n" +
            "    s.plot_show AS s_plot,\n" +
            "    s.actors AS actors,\n" +
            "    NULL AS m_actors,\n" +
            "    s.actors AS s_actors,\n" +
            "    e.guests AS e_actors,\n" +
            "    e.directors AS directors,\n" +
            "    NULL AS m_directors,\n" +
            "    e.directors AS e_directors,\n" +
            "    s.directors AS s_directors,\n" +
            "    s.genres AS genres,\n" +
            "    NULL AS m_genres,\n" +
            "    s.genres AS s_genres,\n" +
            "    s.studios AS studios,\n" +
            "    NULL AS m_studios,\n" +
            "    s.studios AS s_studios,\n" +
            "    coalesce(e.cover_episode, s.cover_show) AS cover,\n" +
            "    NULL AS m_cover,\n" +
            "    e.cover_episode AS e_cover,\n" +
            "    s.cover_show AS s_cover,\n" +
            "    s.backdrop_url_show AS bd_url,\n" +
            "    NULL AS m_bd_url,\n" +
            "    s.backdrop_url_show AS s_bd_url,\n" +
            "    s.backdrop_show AS bd_file,\n" +
            "    NULL AS m_bd_file,\n" +
            "    s.backdrop_show AS s_bd_file\n" +
            "FROM\n" +
            "    v_episode_all AS e\n" +
            "        LEFT JOIN\n" +
            "        v_show_all AS s\n" +
            "        ON\n" +
            "        (s_id = s._id)\n" +
            "UNION\n" +
            "SELECT\n" +
            "    m.video_id AS _id,\n" +
            "    m._id AS m_id,\n" +
            "    NULL AS s_id,\n" +
            "    NULL AS e_id,\n" +
            "    m.name_movie AS scraper_name,\n" +
            "    m.name_movie AS m_name,\n" +
            "    NULL AS s_name,\n" +
            "    NULL AS e_name,\n" +
            "    NULL AS e_season,\n" +
            "    NULL AS e_episode,\n" +
            "    NULL AS e_aired,\n" +
            "    NULL AS s_premiered,\n" +
            "    m.year_movie AS m_year,\n" +
            "    m.rating_movie AS rating,\n" +
            "    m.rating_movie AS m_rating,\n" +
            "    NULL AS e_rating,\n" +
            "    NULL AS s_rating,\n" +
            "    m.plot_movie AS plot,\n" +
            "    m.plot_movie AS m_plot,\n" +
            "    NULL AS e_plot,\n" +
            "    NULL AS s_plot,\n" +
            "    m.actors AS actors,\n" +
            "    m.actors AS m_actors,\n" +
            "    NULL AS s_actors,\n" +
            "    NULL AS e_actors,\n" +
            "    m.directors AS directors,\n" +
            "    m.directors AS m_directors,\n" +
            "    NULL AS e_directors,\n" +
            "    NULL AS s_directors,\n" +
            "    m.genres AS genres,\n" +
            "    m.genres AS m_genres,\n" +
            "    NULL AS s_genres,\n" +
            "    m.studios AS studios,\n" +
            "    m.studios AS m_studios,\n" +
            "    NULL AS s_studios,\n" +
            "    m.cover_movie AS cover,\n" +
            "    m.cover_movie AS m_cover,\n" +
            "    NULL AS e_cover,\n" +
            "    NULL AS s_cover,\n" +
            "    m.backdrop_url_movie AS bd_url,\n" +
            "    m.backdrop_url_movie AS m_bd_url,\n" +
            "    NULL AS s_bd_url,\n" +
            "    m.backdrop_movie AS bd_file,\n" +
            "    m.backdrop_movie AS m_bd_file,\n" +
            "    NULL AS s_bd_file\n" +
            "FROM\n" +
            "    v_movie_all AS m";

    /* Version 11 */
    // additions for posters backdrops
    public static final String MOVIE_POSTERS_TABLE_NAME = "movie_posters";
    private static final String CREATE_MOVIE_POSTERS_TABLE =
            "CREATE TABLE " + MOVIE_POSTERS_TABLE_NAME + " ( \n" +
            "    _id             INTEGER PRIMARY KEY,\n" +
            "    movie_id        INTEGER REFERENCES movie ( _id ) ON DELETE CASCADE\n" +
            "                                                     ON UPDATE CASCADE,\n" +
            "    m_po_thumb_url  TEXT,\n" +
            "    m_po_thumb_file TEXT UNIQUE ON CONFLICT IGNORE,\n" +
            "    m_po_large_url  TEXT,\n" +
            "    m_po_large_file TEXT UNIQUE ON CONFLICT IGNORE\n" +
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
            "    m_bd_thumb_file TEXT UNIQUE ON CONFLICT IGNORE,\n" +
            "    m_bd_large_url  TEXT,\n" +
            "    m_bd_large_file TEXT UNIQUE ON CONFLICT IGNORE\n" +
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
    private static final String CREATE_VIEW_MOVIE_ALL_V11 =
            "CREATE VIEW " + VIEW_MOVIE_ALL + " AS\n" +
            "SELECT movie.*,\n" +
            "       a.actors,\n" +
            "       d.directors,\n" +
            "       g.genres,\n" +
            "       s.studios,\n" +
            "       p.m_po_thumb_url,\n" +
            "       p.m_po_thumb_file,\n" +
            "       p.m_po_large_url,\n" +
            "       p.m_po_large_file,\n" +
            "       b.m_bd_thumb_url,\n" +
            "       b.m_bd_thumb_file,\n" +
            "       b.m_bd_large_url,\n" +
            "       b.m_bd_large_file\n" +
            "  FROM movie\n" +
            "       NATURAL LEFT JOIN v_movie_actors AS a\n" +
            "       NATURAL LEFT JOIN v_movie_directors AS d\n" +
            "       NATURAL LEFT JOIN v_movie_genres AS g\n" +
            "       NATURAL LEFT JOIN v_movie_studios AS s\n" +
            "       LEFT JOIN movie_posters AS p\n" +
            "              ON ( movie.m_poster_id = p._id ) \n" +
            "       LEFT JOIN movie_backdrops AS b\n" +
            "              ON ( movie.m_backdrop_id = b._id )";
    private static final String CREATE_VIEW_SHOW_ALL_V11 =
            "CREATE VIEW " + VIEW_SHOW_ALL + " AS\n" +
            "SELECT show.*,\n" +
            "       a.actors,\n" +
            "       d.directors,\n" +
            "       g.genres,\n" +
            "       s.studios,\n" +
            "       p.s_po_thumb_url,\n" +
            "       p.s_po_thumb_file,\n" +
            "       p.s_po_large_url,\n" +
            "       p.s_po_large_file,\n" +
            "       b.s_bd_thumb_url,\n" +
            "       b.s_bd_thumb_file,\n" +
            "       b.s_bd_large_url,\n" +
            "       b.s_bd_large_file\n" +
            "  FROM show\n" +
            "       NATURAL LEFT JOIN v_show_actors AS a\n" +
            "       NATURAL LEFT JOIN v_show_directors AS d\n" +
            "       NATURAL LEFT JOIN v_show_genres AS g\n" +
            "       NATURAL LEFT JOIN v_show_studios AS s\n" +
            "       LEFT JOIN show_posters AS p\n" +
            "              ON ( show.s_poster_id = p._id ) \n" +
            "       LEFT JOIN show_backdrops AS b\n" +
            "              ON ( show.s_backdrop_id = b._id )";
    private static final String CREATE_VIEW_EPISODE_ALL_V11 =
            "CREATE VIEW " + VIEW_EPISODE_ALL + " AS\n" +
            "SELECT episode.*,\n" +
            "       a.guests,\n" +
            "       d.directors,\n" +
            "       p.s_po_thumb_url AS e_po_thumb_url,\n" +
            "       p.s_po_thumb_file AS e_po_thumb_file,\n" +
            "       p.s_po_large_url AS e_po_large_url,\n" +
            "       p.s_po_large_file AS e_po_large_file,\n" +
            "       p.s_po_season AS e_po_season\n" +
            "  FROM episode\n" +
            "       NATURAL LEFT JOIN v_episode_actors AS a\n" +
            "       NATURAL LEFT JOIN v_episode_directors AS d\n" +
            "       LEFT JOIN show_posters AS p\n" +
            "              ON ( episode.e_poster_id = p._id )";
    private static final String CREATE_VIEW_VIDEO_ALL_V11 =
            "CREATE VIEW " + VIEW_VIDEO_ALL + " AS\n" +
            "SELECT e.video_id AS _id,\n" +
            "       NULL AS m_id,\n" +
            "       e.show_episode AS s_id,\n" +
            "       e._id AS e_id,\n" +
            "       s.name_show AS scraper_name,\n" +
            "       NULL AS m_name,\n" +
            "       s.name_show AS s_name,\n" +
            "       e.name_episode AS e_name,\n" +
            "       e.season_episode AS e_season,\n" +
            "       e.number_episode AS e_episode,\n" +
            "       e.aired_episode AS e_aired,\n" +
            "       s.premiered_show AS s_premiered,\n" +
            "       NULL AS m_year,\n" +
            "       e.rating_episode AS rating,\n" +
            "       NULL AS m_rating,\n" +
            "       e.rating_episode AS e_rating,\n" +
            "       s.rating_show AS s_rating,\n" +

            "       s_online_id AS online_id,\n" +
            "       s_imdb_id AS imdb_id,\n" +
            "       s_content_rating AS content_rating,\n" +
            "       NULL AS m_online_id,\n" +
            "       NULL AS m_imdb_id,\n" +
            "       NULL AS m_content_rating,\n" +
            "       s_online_id,\n" +
            "       s_imdb_id,\n" +
            "       s_content_rating,\n" +
            "       e_online_id,\n" +
            "       e_imdb_id,\n" +

            "       e.plot_episode AS plot,\n" +
            "       NULL AS m_plot,\n" +
            "       e.plot_episode AS e_plot,\n" +
            "       s.plot_show AS s_plot,\n" +
            "       s.actors AS actors,\n" +
            "       NULL AS m_actors,\n" +
            "       s.actors AS s_actors,\n" +
            "       e.guests AS e_actors,\n" +
            "       e.directors AS directors,\n" +
            "       NULL AS m_directors,\n" +
            "       e.directors AS e_directors,\n" +
            "       s.directors AS s_directors,\n" +
            "       s.genres AS genres,\n" +
            "       NULL AS m_genres,\n" +
            "       s.genres AS s_genres,\n" +
            "       s.studios AS studios,\n" +
            "       NULL AS m_studios,\n" +
            "       s.studios AS s_studios,\n" +
            "       coalesce( e_po_large_file, s_po_large_file, e.cover_episode, s.cover_show ) AS cover,\n" +
            "       NULL AS m_cover,\n" +
            "       coalesce(e_po_large_file, e.cover_episode) AS e_cover,\n" +
            "       coalesce(s_po_large_file, s.cover_show) AS s_cover,\n" +
            "       coalesce(s_bd_large_url, s.backdrop_url_show) AS bd_url,\n" +
            "       NULL AS m_bd_url,\n" +
            "       coalesce(s_bd_large_url, s.backdrop_url_show) AS s_bd_url,\n" +
            "       coalesce(s_bd_large_file, s.backdrop_show) AS bd_file,\n" +
            "       NULL AS m_bd_file,\n" +
            "       coalesce(s_bd_large_file, s.backdrop_show) AS s_bd_file,\n" +
            "       coalesce(e_poster_id, s_poster_id) AS poster_id,\n" +
            "       coalesce(e_po_thumb_url,  s_po_thumb_url)  AS po_thumb_url,\n" +
            "       coalesce(e_po_thumb_file, s_po_thumb_file) AS po_thumb_file,\n" +
            "       coalesce(e_po_large_url,  s_po_large_url)  AS po_large_url,\n" +
            "       coalesce(e_po_large_file, s_po_large_file)  AS po_large_file,\n" +
            "       s_backdrop_id AS backdrop_id,\n" +
            "       s_bd_thumb_url AS bd_thumb_url,\n" +
            "       s_bd_thumb_file AS bd_thumb_file,\n" +
            "       s_bd_large_url AS bd_large_url,\n" +
            "       s_bd_large_file AS bd_large_file,\n" +
            "       e_poster_id,\n" +
            "       e_po_thumb_url,\n" +
            "       e_po_thumb_file,\n" +
            "       e_po_large_url,\n" +
            "       e_po_large_file,\n" +
            "       e_po_season,\n" +
            "       s_poster_id,\n" +
            "       s_po_thumb_url,\n" +
            "       s_po_thumb_file,\n" +
            "       s_po_large_url,\n" +
            "       s_po_large_file,\n" +
            "       s_backdrop_id,\n" +
            "       s_bd_thumb_url,\n" +
            "       s_bd_thumb_file,\n" +
            "       s_bd_large_url,\n" +
            "       s_bd_large_file,\n" +
            "       NULL AS m_poster_id,\n" +
            "       NULL AS m_po_thumb_url,\n" +
            "       NULL AS m_po_thumb_file,\n" +
            "       NULL AS m_po_large_url,\n" +
            "       NULL AS m_po_large_file,\n" +
            "       NULL AS m_backdrop_id,\n" +
            "       NULL AS m_bd_thumb_url,\n" +
            "       NULL AS m_bd_thumb_file,\n" +
            "       NULL AS m_bd_large_url,\n" +
            "       NULL AS m_bd_large_file\n" +
            "  FROM v_episode_all AS e\n" +
            "       LEFT JOIN v_show_all AS s\n" +
            "              ON ( s_id = s._id ) \n" +
            "UNION\n" +
            "SELECT m.video_id AS _id,\n" +
            "       m._id AS m_id,\n" +
            "       NULL AS s_id,\n" +
            "       NULL AS e_id,\n" +
            "       m.name_movie AS scraper_name,\n" +
            "       m.name_movie AS m_name,\n" +
            "       NULL AS s_name,\n" +
            "       NULL AS e_name,\n" +
            "       NULL AS e_season,\n" +
            "       NULL AS e_episode,\n" +
            "       NULL AS e_aired,\n" +
            "       NULL AS s_premiered,\n" +
            "       m.year_movie AS m_year,\n" +
            "       m.rating_movie AS rating,\n" +
            "       m.rating_movie AS m_rating,\n" +
            "       NULL AS e_rating,\n" +
            "       NULL AS s_rating,\n" +

            "       m_online_id AS online_id,\n" +
            "       m_imdb_id AS imdb_id,\n" +
            "       m_content_rating AS content_rating,\n" +
            "       m_online_id,\n" +
            "       m_imdb_id,\n" +
            "       m_content_rating,\n" +
            "       NULL AS s_online_id,\n" +
            "       NULL AS s_imdb_id,\n" +
            "       NULL AS s_content_rating,\n" +
            "       NULL AS e_online_id,\n" +
            "       NULL AS e_imdb_id,\n" +

            "       m.plot_movie AS plot,\n" +
            "       m.plot_movie AS m_plot,\n" +
            "       NULL AS e_plot,\n" +
            "       NULL AS s_plot,\n" +
            "       m.actors AS actors,\n" +
            "       m.actors AS m_actors,\n" +
            "       NULL AS s_actors,\n" +
            "       NULL AS e_actors,\n" +
            "       m.directors AS directors,\n" +
            "       m.directors AS m_directors,\n" +
            "       NULL AS e_directors,\n" +
            "       NULL AS s_directors,\n" +
            "       m.genres AS genres,\n" +
            "       m.genres AS m_genres,\n" +
            "       NULL AS s_genres,\n" +
            "       m.studios AS studios,\n" +
            "       m.studios AS m_studios,\n" +
            "       NULL AS s_studios,\n" +
            "       coalesce(m_po_large_file, m.cover_movie) AS cover,\n" +
            "       coalesce(m_po_large_file, m.cover_movie) AS m_cover,\n" +
            "       NULL AS e_cover,\n" +
            "       NULL AS s_cover,\n" +
            "       coalesce(m_bd_large_url, m.backdrop_url_movie) AS bd_url,\n" +
            "       coalesce(m_bd_large_url, m.backdrop_url_movie) AS m_bd_url,\n" +
            "       NULL AS s_bd_url,\n" +
            "       coalesce(m_bd_large_file, m.backdrop_movie) AS bd_file,\n" +
            "       coalesce(m_bd_large_file, m.backdrop_movie) AS m_bd_file,\n" +
            "       NULL AS s_bd_file,\n" +
            "       m_poster_id AS poster_id,\n" +
            "       m_po_thumb_url AS po_thumb_url,\n" +
            "       m_po_thumb_file AS po_thumb_file,\n" +
            "       m_po_large_url AS po_large_url,\n" +
            "       m_po_large_file AS po_large_file,\n" +
            "       m_backdrop_id AS backdrop_id,\n" +
            "       m_bd_thumb_url AS bd_thumb_url,\n" +
            "       m_bd_thumb_file AS bd_thumb_file,\n" +
            "       m_bd_large_url AS bd_large_url,\n" +
            "       m_bd_large_file AS bd_large_file,\n" +
            "       NULL AS e_poster_id,\n" +
            "       NULL AS e_po_thumb_url,\n" +
            "       NULL AS e_po_thumb_file,\n" +
            "       NULL AS e_po_large_url,\n" +
            "       NULL AS e_po_large_file,\n" +
            "       NULL AS e_po_season,\n" +
            "       NULL AS s_poster_id,\n" +
            "       NULL AS s_po_thumb_url,\n" +
            "       NULL AS s_po_thumb_file,\n" +
            "       NULL AS s_po_large_url,\n" +
            "       NULL AS s_po_large_file,\n" +
            "       NULL AS s_backdrop_id,\n" +
            "       NULL AS s_bd_thumb_url,\n" +
            "       NULL AS s_bd_thumb_file,\n" +
            "       NULL AS s_bd_large_url,\n" +
            "       NULL AS s_bd_large_file,\n" +
            "       m_poster_id,\n" +
            "       m_po_thumb_url,\n" +
            "       m_po_thumb_file,\n" +
            "       m_po_large_url,\n" +
            "       m_po_large_file,\n" +
            "       m_backdrop_id,\n" +
            "       m_bd_thumb_url,\n" +
            "       m_bd_thumb_file,\n" +
            "       m_bd_large_url,\n" +
            "       m_bd_large_file\n" +
            "  FROM v_movie_all AS m";
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

        // create views that combine formatted with base info
        db.execSQL(CREATE_VIEW_SHOW_ALL_V0);
        db.execSQL(CREATE_VIEW_EPISODE_ALL_V0);
        db.execSQL(CREATE_VIEW_MOVIE_ALL_V0);

        // and combine all that into another view
        db.execSQL(CREATE_VIEW_VIDEO_ALL_V0);

    }

    public static void upgradeTo (SQLiteDatabase db, int toVersion) {
        if (toVersion == 11) {
            // drop those triggers
            db.execSQL(EPISODE_INSERT_TRIGGER_DROP);
            db.execSQL(EPISODE_DELETE_TRIGGER_DROP);
            db.execSQL(SHOW_DELETE_TRIGGER_DROP);
            db.execSQL(MOVIE_INSERT_TRIGGER_DROP);
            db.execSQL(MOVIE_DELETE_TRIGGER_DROP);

            // movie has backdrop + poster
            db.execSQL("ALTER TABLE movie ADD COLUMN m_backdrop_id INTEGER");
            db.execSQL("ALTER TABLE movie ADD COLUMN m_poster_id INTEGER");

            // also the id in the online db "1858" - http://www.themoviedb.org/movie/1858
            db.execSQL("ALTER TABLE movie ADD COLUMN m_online_id INTEGER");
            // and the imdb id e.g. "tt0285331" - http://www.imdb.com/title/tt0285331
            db.execSQL("ALTER TABLE movie ADD COLUMN m_imdb_id TEXT");
            // also content rating e.g. "PG-13"
            db.execSQL("ALTER TABLE movie ADD COLUMN m_content_rating TEXT");

            // show has backdrop + poster
            db.execSQL("ALTER TABLE show ADD COLUMN s_backdrop_id INTEGER");
            db.execSQL("ALTER TABLE show ADD COLUMN s_poster_id INTEGER");

            // also the id in the online db "73255" - http://thetvdb.com/?tab=series&id=73255
            db.execSQL("ALTER TABLE show ADD COLUMN s_online_id INTEGER");
            // and the imdb id e.g. "tt0285331" - http://www.imdb.com/title/tt0285331
            db.execSQL("ALTER TABLE show ADD COLUMN s_imdb_id TEXT");
            // also content rating e.g. "TV-14"
            db.execSQL("ALTER TABLE show ADD COLUMN s_content_rating TEXT");

            // episode has a poster too
            db.execSQL("ALTER TABLE episode ADD COLUMN e_poster_id INTEGER");
            // also the id in the online db "306192" - http://thetvdb.com/?tab=episode&seriesid=73255&id=306192
            db.execSQL("ALTER TABLE episode ADD COLUMN e_online_id INTEGER");
            // and the imdb id e.g. "tt0285331" - http://www.imdb.com/title/tt0285331
            db.execSQL("ALTER TABLE episode ADD COLUMN e_imdb_id TEXT");

            db.execSQL(CREATE_MOVIE_POSTERS_TABLE);
            db.execSQL(CREATE_MOVIE_POSTERS_DELETE_TRIGGER);
            db.execSQL(CREATE_MOVIE_BACKDROPS_TABLE);
            db.execSQL(CREATE_MOVIE_BACKDROPS_DELETE_TRIGGER);
            db.execSQL(CREATE_SHOW_POSTERS_TABLE);
            db.execSQL(CREATE_SHOW_POSTERS_DELETE_TRIGGER);
            db.execSQL(CREATE_SHOW_BACKDROPS_TABLE);
            db.execSQL(CREATE_SHOW_BACKDROPS_DELETE_TRIGGER);
            db.execSQL("DROP VIEW IF EXISTS " + VIEW_SHOW_ALL);
            db.execSQL("DROP VIEW IF EXISTS " + VIEW_EPISODE_ALL);
            db.execSQL("DROP VIEW IF EXISTS " + VIEW_MOVIE_ALL);
            db.execSQL("DROP VIEW IF EXISTS " + VIEW_VIDEO_ALL);
            db.execSQL(CREATE_VIEW_SHOW_ALL_V11);
            db.execSQL(CREATE_VIEW_EPISODE_ALL_V11);
            db.execSQL(CREATE_VIEW_MOVIE_ALL_V11);
            db.execSQL(CREATE_VIEW_VIDEO_ALL_V11);

            // recreate the triggers
            db.execSQL(EPISODE_INSERT_TRIGGER_CREATE);
            db.execSQL(EPISODE_DELETE_TRIGGER_CREATE);
            db.execSQL(MOVIE_INSERT_TRIGGER_CREATE);
            db.execSQL(MOVIE_DELETE_TRIGGER_CREATE);
            db.execSQL(SHOW_DELETE_TRIGGER_CREATE);
        }
        // recreate those _DELETE_FILE triggers with _DELETE_FILE_J
        if (toVersion == 12) {
            // movie / show backdrops + posters
            db.execSQL(DROP_MOVIE_POSTERS_DELETE_TRIGGER);
            db.execSQL(CREATE_MOVIE_POSTERS_DELETE_TRIGGER);

            db.execSQL(DROP_MOVIE_BACKDROPS_DELETE_TRIGGER);
            db.execSQL(CREATE_MOVIE_BACKDROPS_DELETE_TRIGGER);

            db.execSQL(DROP_SHOW_POSTERS_DELETE_TRIGGER);
            db.execSQL(CREATE_SHOW_POSTERS_DELETE_TRIGGER);

            db.execSQL(DROP_SHOW_BACKDROPS_DELETE_TRIGGER);
            db.execSQL(CREATE_SHOW_BACKDROPS_DELETE_TRIGGER);

            // "cover" on movie / show
            db.execSQL(MOVIE_DELETE_TRIGGER_DROP);
            db.execSQL(MOVIE_DELETE_TRIGGER_CREATE);

            db.execSQL(SHOW_DELETE_TRIGGER_DROP);
            db.execSQL(SHOW_DELETE_TRIGGER_CREATE);
        }
        // use hard data for formatted actors etc
        if (toVersion == 13) {
            // drop those triggers, sqlite breaks when those are not recreated
            db.execSQL(EPISODE_INSERT_TRIGGER_DROP);
            db.execSQL(EPISODE_DELETE_TRIGGER_DROP);
            db.execSQL(SHOW_DELETE_TRIGGER_DROP);
            db.execSQL(MOVIE_INSERT_TRIGGER_DROP);
            db.execSQL(MOVIE_DELETE_TRIGGER_DROP);

            // those views are no longer required, drop them
            db.execSQL("DROP VIEW IF EXISTS " + VIEW_MOVIE_ALL);
            db.execSQL("DROP VIEW IF EXISTS " + VIEW_SHOW_ALL);
            db.execSQL("DROP VIEW IF EXISTS " + VIEW_EPISODE_ALL);
            db.execSQL("DROP VIEW IF EXISTS " + VIEW_VIDEO_ALL);

            db.execSQL("ALTER TABLE " + MOVIE_TABLE_NAME + " ADD COLUMN " + ScraperStore.Movie.ACTORS_FORMATTED + " TEXT");
            db.execSQL("ALTER TABLE " + MOVIE_TABLE_NAME + " ADD COLUMN " + ScraperStore.Movie.DIRECTORS_FORMATTED + " TEXT");
            db.execSQL("ALTER TABLE " + MOVIE_TABLE_NAME + " ADD COLUMN " + ScraperStore.Movie.GERNES_FORMATTED + " TEXT");
            db.execSQL("ALTER TABLE " + MOVIE_TABLE_NAME + " ADD COLUMN " + ScraperStore.Movie.STUDIOS_FORMATTED + " TEXT");
            // insert existing data into those columns
            db.execSQL("UPDATE " + MOVIE_TABLE_NAME + " SET " + ScraperStore.Movie.ACTORS_FORMATTED + " = (SELECT " +
                    "actors FROM " + VIEW_MOVIE_ACTORS + " AS t WHERE t._id = " + MOVIE_TABLE_NAME + "._id)");
            db.execSQL("UPDATE " + MOVIE_TABLE_NAME + " SET " + ScraperStore.Movie.DIRECTORS_FORMATTED + " = (SELECT " +
                    "directors FROM " + VIEW_MOVIE_DIRECTORS + " AS t WHERE t._id = " + MOVIE_TABLE_NAME + "._id)");
            db.execSQL("UPDATE " + MOVIE_TABLE_NAME + " SET " + ScraperStore.Movie.GERNES_FORMATTED + " = (SELECT " +
                    "genres FROM " + VIEW_MOVIE_GENRES + " AS t WHERE t._id = " + MOVIE_TABLE_NAME + "._id)");
            db.execSQL("UPDATE " + MOVIE_TABLE_NAME + " SET " + ScraperStore.Movie.STUDIOS_FORMATTED + " = (SELECT " +
                    "studios FROM " + VIEW_MOVIE_STUDIOS + " AS t WHERE t._id = " + MOVIE_TABLE_NAME + "._id)");

            db.execSQL("ALTER TABLE " + SHOW_TABLE_NAME + " ADD COLUMN " + ScraperStore.Show.ACTORS_FORMATTED + " TEXT");
            db.execSQL("ALTER TABLE " + SHOW_TABLE_NAME + " ADD COLUMN " + ScraperStore.Show.DIRECTORS_FORMATTED + " TEXT");
            db.execSQL("ALTER TABLE " + SHOW_TABLE_NAME + " ADD COLUMN " + ScraperStore.Show.GERNES_FORMATTED + " TEXT");
            db.execSQL("ALTER TABLE " + SHOW_TABLE_NAME + " ADD COLUMN " + ScraperStore.Show.STUDIOS_FORMATTED + " TEXT");
            db.execSQL("UPDATE " + SHOW_TABLE_NAME + " SET " + ScraperStore.Show.ACTORS_FORMATTED + " = (SELECT " +
                    "actors FROM " + VIEW_SHOW_ACTORS + " AS t WHERE t._id = " + SHOW_TABLE_NAME + "._id)");
            db.execSQL("UPDATE " + SHOW_TABLE_NAME + " SET " + ScraperStore.Show.DIRECTORS_FORMATTED + " = (SELECT " +
                    "directors FROM " + VIEW_SHOW_DIRECTORS + " AS t WHERE t._id = " + SHOW_TABLE_NAME + "._id)");
            db.execSQL("UPDATE " + SHOW_TABLE_NAME + " SET " + ScraperStore.Show.GERNES_FORMATTED + " = (SELECT " +
                    "genres FROM " + VIEW_SHOW_GENRES + " AS t WHERE t._id = " + SHOW_TABLE_NAME + "._id)");
            db.execSQL("UPDATE " + SHOW_TABLE_NAME + " SET " + ScraperStore.Show.STUDIOS_FORMATTED + " = (SELECT " +
                    "studios FROM " + VIEW_SHOW_STUDIOS + " AS t WHERE t._id = " + SHOW_TABLE_NAME + "._id)");

            db.execSQL("ALTER TABLE " + EPISODE_TABLE_NAME + " ADD COLUMN " + ScraperStore.Episode.ACTORS_FORMATTED + " TEXT");
            db.execSQL("ALTER TABLE " + EPISODE_TABLE_NAME + " ADD COLUMN " + ScraperStore.Episode.DIRECTORS_FORMATTED + " TEXT");
            db.execSQL("UPDATE " + EPISODE_TABLE_NAME + " SET " + ScraperStore.Episode.ACTORS_FORMATTED + " = (SELECT " +
                    "guests FROM " + VIEW_EPISODE_ACTORS + " AS t WHERE t._id = " + EPISODE_TABLE_NAME + "._id)");
            db.execSQL("UPDATE " + EPISODE_TABLE_NAME + " SET " + ScraperStore.Episode.DIRECTORS_FORMATTED + " = (SELECT " +
                    "directors FROM " + VIEW_EPISODE_DIRECTORS + " AS t WHERE t._id = " + EPISODE_TABLE_NAME + "._id)");

            // recreate the triggers
            db.execSQL(EPISODE_INSERT_TRIGGER_CREATE);
            db.execSQL(EPISODE_DELETE_TRIGGER_CREATE);
            db.execSQL(MOVIE_INSERT_TRIGGER_CREATE);
            db.execSQL(MOVIE_DELETE_TRIGGER_CREATE);
            db.execSQL(SHOW_DELETE_TRIGGER_CREATE);
        }
        if(toVersion == 28){

            db.execSQL(CREATE_MOVIE_TRAILERS_TABLE);
        }
        if(toVersion==29)
            db.execSQL("ALTER TABLE " + EPISODE_TABLE_NAME + " ADD COLUMN " + ScraperStore.Episode.PICTURE + " TEXT");
        if(toVersion==33) {
            db.execSQL(DROP_MOVIE_POSTERS_DELETE_TRIGGER);
            db.execSQL(CREATE_MOVIE_POSTERS_DELETE_TRIGGER);
            db.execSQL(MOVIE_DELETE_TRIGGER_DROP);
            db.execSQL(MOVIE_DELETE_TRIGGER_CREATE);
        }
        if (toVersion == 36) {
            // tables touched MOVIE_DELETE_TRIGGER_CREATE HOW_DELETE_TRIGGER_CREATE CREATE_MOVIE_POSTERS_DELETE_TRIGGER CREATE_MOVIE_BACKDROPS_DELETE_TRIGGER CREATE_SHOW_POSTERS_DELETE_TRIGGER CREATE_SHOW_BACKDROPS_DELETE_TRIGGER
            db.execSQL(DROP_MOVIE_POSTERS_DELETE_TRIGGER);
            db.execSQL(CREATE_MOVIE_POSTERS_DELETE_TRIGGER);
            db.execSQL(DROP_MOVIE_BACKDROPS_DELETE_TRIGGER);
            db.execSQL(CREATE_MOVIE_BACKDROPS_DELETE_TRIGGER);
            db.execSQL(DROP_SHOW_POSTERS_DELETE_TRIGGER);
            db.execSQL(CREATE_SHOW_POSTERS_DELETE_TRIGGER);
            db.execSQL(DROP_SHOW_BACKDROPS_DELETE_TRIGGER);
            db.execSQL(CREATE_SHOW_BACKDROPS_DELETE_TRIGGER);
            db.execSQL(MOVIE_DELETE_TRIGGER_DROP);
            db.execSQL(MOVIE_DELETE_TRIGGER_CREATE);
            db.execSQL(SHOW_DELETE_TRIGGER_DROP);
            db.execSQL(SHOW_DELETE_TRIGGER_CREATE);
        }
    }
}
