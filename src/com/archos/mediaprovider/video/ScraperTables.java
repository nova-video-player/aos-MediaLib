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
    public static final String TAGLINES_TABLE_NAME = "TAGLINE";
    public static final String PRODUCERS_TABLE_NAME = "PRODUCER";
    public static final String SCREENPLAYS_TABLE_NAME = "SCREENPLAY";
    public static final String MUSICCOMPOSERS_TABLE_NAME = "MUSICCOMPOSER";
    public static final String COUNTRIES_TABLE_NAME = "COUNTRY";
    public static final String SEASONPLOTS_TABLE_NAME = "SEASONPLOT";
    public static final String STUDIOS_TABLE_NAME = "STUDIO";
    public static final String FILMS_MOVIE_TABLE_NAME = "FILMS_MOVIE";
    public static final String WRITERS_MOVIE_TABLE_NAME = "WRITERS_MOVIE";
    public static final String TAGLINES_MOVIE_TABLE_NAME = "TAGLINES_MOVIE";
    public static final String PRODUCERS_MOVIE_TABLE_NAME = "PRODUCERS_MOVIE";
    public static final String SCREENPLAYS_MOVIE_TABLE_NAME = "SCREENPLAYS_MOVIE";
    public static final String MUSICCOMPOSERS_MOVIE_TABLE_NAME = "MUSICCOMPOSERS_MOVIE";
    public static final String COUNTRIES_MOVIE_TABLE_NAME = "COUNTRIES_MOVIE";
    public static final String FILMS_SHOW_TABLE_NAME = "FILMS_SHOW";
    public static final String WRITERS_SHOW_TABLE_NAME = "WRITERS_SHOW";
    public static final String TAGLINES_SHOW_TABLE_NAME = "TAGLINES_SHOW";
    public static final String PRODUCERS_SHOW_TABLE_NAME = "PRODUCERS_SHOW";
    public static final String SCREENPLAYS_SHOW_TABLE_NAME = "SCREENPLAYS_SHOW";
    public static final String MUSICCOMPOSERS_SHOW_TABLE_NAME = "MUSICCOMPOSERS_SHOW";
    public static final String COUNTRIES_SHOW_TABLE_NAME = "COUNTRIES_SHOW";
    public static final String SEASONPLOTS_SHOW_TABLE_NAME = "SEASONPLOTS_SHOW";
    public static final String FILMS_EPISODE_TABLE_NAME = "FILMS_EPISODE";
    public static final String WRITERS_EPISODE_TABLE_NAME = "WRITERS_EPISODE";
    public static final String TAGLINES_EPISODE_TABLE_NAME = "TAGLINES_EPISODE";
    public static final String PRODUCERS_EPISODE_TABLE_NAME = "PRODUCERS_EPISODE";
    public static final String SCREENPLAYS_EPISODE_TABLE_NAME = "SCREENPLAYS_EPISODE";
    public static final String MUSICCOMPOSERS_EPISODE_TABLE_NAME = "MUSICCOMPOSERS_EPISODE";
    public static final String COUNTRIES_EPISODE_TABLE_NAME = "COUNTRIES_EPISODE";
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

    public static final String TAGLINES_MOVIE_VIEW_NAME = "V_TAGLINES_MOVIE";
    public static final String TAGLINES_SHOW_VIEW_NAME = "V_TAGLINES_SHOW";
    public static final String TAGLINES_EPISODE_VIEW_NAME = "V_TAGLINES_EPISODE";

    public static final String PRODUCERS_MOVIE_VIEW_NAME = "V_PRODUCERS_MOVIE";
    public static final String PRODUCERS_SHOW_VIEW_NAME = "V_PRODUCERS_SHOW";
    public static final String PRODUCERS_EPISODE_VIEW_NAME = "V_PRODUCERS_EPISODE";

    public static final String SCREENPLAYS_MOVIE_VIEW_NAME = "V_SCREENPLAYS_MOVIE";
    public static final String SCREENPLAYS_SHOW_VIEW_NAME = "V_SCREENPLAYS_SHOW";
    public static final String SCREENPLAYS_EPISODE_VIEW_NAME = "V_SCREENPLAYS_EPISODE";

    public static final String MUSICCOMPOSERS_MOVIE_VIEW_NAME = "V_MUSICCOMPOSERS_MOVIE";
    public static final String MUSICCOMPOSERS_SHOW_VIEW_NAME = "V_MUSICCOMPOSERS_SHOW";
    public static final String MUSICCOMPOSERS_EPISODE_VIEW_NAME = "V_MUSICCOMPOSERS_EPISODE";

    public static final String COUNTRIES_MOVIE_VIEW_NAME = "V_COUNTRIES_MOVIE";
    public static final String COUNTRIES_SHOW_VIEW_NAME = "V_COUNTRIES_SHOW";
    public static final String COUNTRIES_EPISODE_VIEW_NAME = "V_COUNTRIES_EPISODE";

    public static final String SEASONPLOTS_SHOW_VIEW_NAME = "V_SEASONPLOTS_SHOW";

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
    public static final String TAGLINE_DELETABLE_VIEW_NAME = "v_tagline_deletable";
    public static final String PRODUCER_DELETABLE_VIEW_NAME = "v_producer_deletable";
    public static final String SCREENPLAY_DELETABLE_VIEW_NAME = "v_screenplay_deletable";
    public static final String MUSICCOMPOSER_DELETABLE_VIEW_NAME = "v_musiccomposer_deletable";
    public static final String COUNTRY_DELETABLE_VIEW_NAME = "v_country_deletable";
    public static final String GENRE_DELETABLE_VIEW_NAME = "v_genre_deletable";
    public static final String STUDIO_DELETABLE_VIEW_NAME = "v_studio_deletable";
    /*
     * Columns names that we need and are not to be exposed.
     * Public ones are in the ScraperStore class.
     */
    private static final String FILMS_MOVIE_ID_MOVIE = "movie_films";
    private static final String FILMS_MOVIE_ID_DIRECTOR = "director_films";

    private static final String WRITERS_MOVIE_ID_MOVIE = "movie_writers";
    private static final String WRITERS_MOVIE_ID_WRITER = "writer_writers";

    private static final String TAGLINES_MOVIE_ID_MOVIE = "movie_taglines";
    private static final String TAGLINES_MOVIE_ID_TAGLINE = "tagline_taglines";

    private static final String PRODUCERS_MOVIE_ID_MOVIE = "movie_producers";
    private static final String PRODUCERS_MOVIE_ID_PRODUCER = "producer_producers";

    private static final String SCREENPLAYS_MOVIE_ID_MOVIE = "movie_screenplays";
    private static final String SCREENPLAYS_MOVIE_ID_SCREENPLAY = "screenplay_screenplays";

    private static final String MUSICCOMPOSERS_MOVIE_ID_MOVIE = "movie_musiccomposers";
    private static final String MUSICCOMPOSERS_MOVIE_ID_MUSICCOMPOSER = "musiccomposer_musiccomposers";

    private static final String COUNTRIES_MOVIE_ID_MOVIE = "movie_countries";
    private static final String COUNTRIES_MOVIE_ID_COUNTRY = "country_countries";

    private static final String FILMS_SHOW_ID_SHOW = "show_films";
    private static final String FILMS_SHOW_ID_DIRECTOR = "director_films";

    private static final String WRITERS_SHOW_ID_SHOW = "show_writers";
    private static final String WRITERS_SHOW_ID_WRITER = "writer_writers";

    private static final String TAGLINES_SHOW_ID_SHOW = "show_taglines";
    private static final String TAGLINES_SHOW_ID_TAGLINE = "tagline_taglines";

    private static final String PRODUCERS_SHOW_ID_SHOW = "show_producers";
    private static final String PRODUCERS_SHOW_ID_PRODUCER = "producer_producers";

    private static final String SCREENPLAYS_SHOW_ID_SHOW = "show_screenplays";
    private static final String SCREENPLAYS_SHOW_ID_SCREENPLAY = "screenplay_screenplays";

    private static final String MUSICCOMPOSERS_SHOW_ID_SHOW = "show_musiccomposers";
    private static final String MUSICCOMPOSERS_SHOW_ID_MUSICCOMPOSER = "musiccomposer_musiccomposers";

    private static final String COUNTRIES_SHOW_ID_SHOW = "show_countries";
    private static final String COUNTRIES_SHOW_ID_COUNTRY = "country_countries";

    private static final String SEASONPLOTS_SHOW_ID_SHOW = "show_seasonplots";
    private static final String SEASONPLOTS_SHOW_ID_SEASONPLOT = "seasonplot_seasonplots";

    private static final String FILMS_EPISODE_ID_DIRECTOR = "director_films";
    private static final String FILMS_EPISODE_ID_EPISODE = "episode_films";

    private static final String WRITERS_EPISODE_ID_WRITER = "writer_writers";
    private static final String WRITERS_EPISODE_ID_EPISODE = "episode_writers";

    private static final String TAGLINES_EPISODE_ID_TAGLINE = "tagline_taglines";
    private static final String TAGLINES_EPISODE_ID_EPISODE = "episode_taglines";

    private static final String PRODUCERS_EPISODE_ID_PRODUCER = "producer_producers";
    private static final String PRODUCERS_EPISODE_ID_EPISODE = "episode_producers";

    private static final String SCREENPLAYS_EPISODE_ID_SCREENPLAY = "screenplay_screenplays";
    private static final String SCREENPLAYS_EPISODE_ID_EPISODE = "episode_screenplays";

    private static final String MUSICCOMPOSERS_EPISODE_ID_MUSICCOMPOSER = "musiccomposer_musiccomposers";
    private static final String MUSICCOMPOSERS_EPISODE_ID_EPISODE = "episode_musiccomposers";

    private static final String COUNTRIES_EPISODE_ID_COUNTRY = "country_countries";
    private static final String COUNTRIES_EPISODE_ID_EPISODE = "episode_countries";

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
                ScraperStore.Movie.ACTORPHOTO_URL + " TEXT," +
                ScraperStore.Movie.ACTORPHOTO + " TEXT," +
                ScraperStore.Movie.STUDIOLOGO_URL + " TEXT," +
                ScraperStore.Movie.STUDIOLOGO + " TEXT," +
                ScraperStore.Movie.CLEARLOGO_URL + " TEXT," +
                ScraperStore.Movie.CLEARLOGO + " TEXT," +
        "m_backdrop_id INTEGER,"  + // movie has backdrop + poster
        "m_poster_id INTEGER," +
                "m_actorphoto_id INTEGER," +
                "m_studiologo_id INTEGER," +
                "m_clearlogo_id INTEGER," +
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
                ScraperStore.Show.NETWORKLOGO_URL + " TEXT," +
                ScraperStore.Show.NETWORKLOGO + " TEXT," +
                ScraperStore.Show.ACTORPHOTO_URL + " TEXT," +
                ScraperStore.Show.ACTORPHOTO + " TEXT," +
                ScraperStore.Show.CLEARLOGO_URL + " TEXT," +
                ScraperStore.Show.CLEARLOGO + " TEXT," +
                ScraperStore.Show.STUDIOLOGO_URL + " TEXT," +
                ScraperStore.Show.STUDIOLOGO + " TEXT," +
        "s_backdrop_id INTEGER," + // show has backdrop + poster
        "s_poster_id INTEGER," +
                "s_networklogo_id INTEGER," +
                "s_actorphoto_id INTEGER," +
                "s_clearlogo_id INTEGER," +
                "s_studiologo_id INTEGER," +
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

    private static final String TAGLINES_TABLE_CREATE =
            "CREATE TABLE " + TAGLINES_TABLE_NAME + " (" +
                    ScraperStore.Tagline.ID + " INTEGER PRIMARY KEY NOT NULL," +
                    ScraperStore.Tagline.NAME + " TEXT UNIQUE," +
                    ScraperStore.Tagline.COUNT + " INTEGER)";

    private static final String PRODUCERS_TABLE_CREATE =
            "CREATE TABLE " + PRODUCERS_TABLE_NAME + " (" +
                    ScraperStore.Producer.ID + " INTEGER PRIMARY KEY NOT NULL," +
                    ScraperStore.Producer.NAME + " TEXT UNIQUE," +
                    ScraperStore.Producer.COUNT + " INTEGER)";

    private static final String SCREENPLAYS_TABLE_CREATE =
            "CREATE TABLE " + SCREENPLAYS_TABLE_NAME + " (" +
                    ScraperStore.Screenplay.ID + " INTEGER PRIMARY KEY NOT NULL," +
                    ScraperStore.Screenplay.NAME + " TEXT UNIQUE," +
                    ScraperStore.Screenplay.COUNT + " INTEGER)";

    private static final String MUSICCOMPOSERS_TABLE_CREATE =
            "CREATE TABLE " + MUSICCOMPOSERS_TABLE_NAME + " (" +
                    ScraperStore.Musiccomposer.ID + " INTEGER PRIMARY KEY NOT NULL," +
                    ScraperStore.Musiccomposer.NAME + " TEXT UNIQUE," +
                    ScraperStore.Musiccomposer.COUNT + " INTEGER)";

    private static final String COUNTRIES_TABLE_CREATE =
            "CREATE TABLE " + COUNTRIES_TABLE_NAME + " (" +
                    ScraperStore.Country.ID + " INTEGER PRIMARY KEY NOT NULL," +
                    ScraperStore.Country.NAME + " TEXT UNIQUE," +
                    ScraperStore.Country.COUNT + " INTEGER)";

    private static final String SEASONPLOTS_TABLE_CREATE =
            "CREATE TABLE " + SEASONPLOTS_TABLE_NAME + " (" +
                    ScraperStore.SeasonPlot.ID + " INTEGER PRIMARY KEY NOT NULL," +
                    ScraperStore.SeasonPlot.NAME + " TEXT UNIQUE," +
                    ScraperStore.SeasonPlot.COUNT + " INTEGER)";

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

    private static final String SEASONPLOTS_SHOW_TABLE_CREATE =
            "CREATE TABLE " + SEASONPLOTS_SHOW_TABLE_NAME + " (" +
                    SEASONPLOTS_SHOW_ID_SHOW + " INTEGER REFERENCES " + SHOW_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
                    SEASONPLOTS_SHOW_ID_SEASONPLOT + " INTEGER REFERENCES " + SEASONPLOTS_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
                    "PRIMARY KEY(" + SEASONPLOTS_SHOW_ID_SHOW + "," +
                    SEASONPLOTS_SHOW_ID_SEASONPLOT + "))";

    private static final String WRITERS_EPISODE_TABLE_CREATE =
            "CREATE TABLE " + WRITERS_EPISODE_TABLE_NAME + " (" +
                    WRITERS_EPISODE_ID_EPISODE + " INTEGER REFERENCES " + EPISODE_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
                    WRITERS_EPISODE_ID_WRITER + " INTEGER REFERENCES " + WRITERS_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
                    "PRIMARY KEY(" + WRITERS_EPISODE_ID_EPISODE + "," +
                    WRITERS_EPISODE_ID_WRITER + "))";


    /*
     *  Tables associating movie, show and episode tables with taglines
     */
    private static final String TAGLINES_MOVIE_TABLE_CREATE =
            "CREATE TABLE " + TAGLINES_MOVIE_TABLE_NAME + " (" +
                    TAGLINES_MOVIE_ID_MOVIE + " INTEGER REFERENCES " + MOVIE_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
                    TAGLINES_MOVIE_ID_TAGLINE + " INTEGER REFERENCES " + TAGLINES_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
                    "PRIMARY KEY(" + TAGLINES_MOVIE_ID_MOVIE + "," +
                    TAGLINES_MOVIE_ID_TAGLINE + "))";

    private static final String TAGLINES_SHOW_TABLE_CREATE =
            "CREATE TABLE " + TAGLINES_SHOW_TABLE_NAME + " (" +
                    TAGLINES_SHOW_ID_SHOW + " INTEGER REFERENCES " + SHOW_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
                    TAGLINES_SHOW_ID_TAGLINE + " INTEGER REFERENCES " + TAGLINES_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
                    "PRIMARY KEY(" + TAGLINES_SHOW_ID_SHOW + "," +
                    TAGLINES_SHOW_ID_TAGLINE + "))";

    private static final String TAGLINES_EPISODE_TABLE_CREATE =
            "CREATE TABLE " + TAGLINES_EPISODE_TABLE_NAME + " (" +
                    TAGLINES_EPISODE_ID_EPISODE + " INTEGER REFERENCES " + EPISODE_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
                    TAGLINES_EPISODE_ID_TAGLINE + " INTEGER REFERENCES " + TAGLINES_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
                    "PRIMARY KEY(" + TAGLINES_EPISODE_ID_EPISODE + "," +
                    TAGLINES_EPISODE_ID_TAGLINE + "))";


    /*
     *  Tables associating movie, show and episode tables with producers
     */
    private static final String PRODUCERS_MOVIE_TABLE_CREATE =
            "CREATE TABLE " + PRODUCERS_MOVIE_TABLE_NAME + " (" +
                    PRODUCERS_MOVIE_ID_MOVIE + " INTEGER REFERENCES " + MOVIE_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
                    PRODUCERS_MOVIE_ID_PRODUCER + " INTEGER REFERENCES " + PRODUCERS_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
                    "PRIMARY KEY(" + PRODUCERS_MOVIE_ID_MOVIE + "," +
                    PRODUCERS_MOVIE_ID_PRODUCER + "))";

    private static final String PRODUCERS_SHOW_TABLE_CREATE =
            "CREATE TABLE " + PRODUCERS_SHOW_TABLE_NAME + " (" +
                    PRODUCERS_SHOW_ID_SHOW + " INTEGER REFERENCES " + SHOW_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
                    PRODUCERS_SHOW_ID_PRODUCER + " INTEGER REFERENCES " + PRODUCERS_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
                    "PRIMARY KEY(" + PRODUCERS_SHOW_ID_SHOW + "," +
                    PRODUCERS_SHOW_ID_PRODUCER + "))";

    private static final String PRODUCERS_EPISODE_TABLE_CREATE =
            "CREATE TABLE " + PRODUCERS_EPISODE_TABLE_NAME + " (" +
                    PRODUCERS_EPISODE_ID_EPISODE + " INTEGER REFERENCES " + EPISODE_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
                    PRODUCERS_EPISODE_ID_PRODUCER + " INTEGER REFERENCES " + PRODUCERS_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
                    "PRIMARY KEY(" + PRODUCERS_EPISODE_ID_EPISODE + "," +
                    PRODUCERS_EPISODE_ID_PRODUCER + "))";

    /*
     *  Tables associating movie, show and episode tables with screenplays
     */
    private static final String SCREENPLAYS_MOVIE_TABLE_CREATE =
            "CREATE TABLE " + SCREENPLAYS_MOVIE_TABLE_NAME + " (" +
                    SCREENPLAYS_MOVIE_ID_MOVIE + " INTEGER REFERENCES " + MOVIE_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
                    SCREENPLAYS_MOVIE_ID_SCREENPLAY + " INTEGER REFERENCES " + SCREENPLAYS_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
                    "PRIMARY KEY(" + SCREENPLAYS_MOVIE_ID_MOVIE + "," +
                    SCREENPLAYS_MOVIE_ID_SCREENPLAY + "))";

    private static final String SCREENPLAYS_SHOW_TABLE_CREATE =
            "CREATE TABLE " + SCREENPLAYS_SHOW_TABLE_NAME + " (" +
                    SCREENPLAYS_SHOW_ID_SHOW + " INTEGER REFERENCES " + SHOW_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
                    SCREENPLAYS_SHOW_ID_SCREENPLAY + " INTEGER REFERENCES " + SCREENPLAYS_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
                    "PRIMARY KEY(" + SCREENPLAYS_SHOW_ID_SHOW + "," +
                    SCREENPLAYS_SHOW_ID_SCREENPLAY + "))";

    private static final String SCREENPLAYS_EPISODE_TABLE_CREATE =
            "CREATE TABLE " + SCREENPLAYS_EPISODE_TABLE_NAME + " (" +
                    SCREENPLAYS_EPISODE_ID_EPISODE + " INTEGER REFERENCES " + EPISODE_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
                    SCREENPLAYS_EPISODE_ID_SCREENPLAY + " INTEGER REFERENCES " + SCREENPLAYS_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
                    "PRIMARY KEY(" + SCREENPLAYS_EPISODE_ID_EPISODE + "," +
                    SCREENPLAYS_EPISODE_ID_SCREENPLAY + "))";

    /*
     *  Tables associating movie, show and episode tables with musiccomposers
     */
    private static final String MUSICCOMPOSERS_MOVIE_TABLE_CREATE =
            "CREATE TABLE " + MUSICCOMPOSERS_MOVIE_TABLE_NAME + " (" +
                    MUSICCOMPOSERS_MOVIE_ID_MOVIE + " INTEGER REFERENCES " + MOVIE_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
                    MUSICCOMPOSERS_MOVIE_ID_MUSICCOMPOSER + " INTEGER REFERENCES " + MUSICCOMPOSERS_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
                    "PRIMARY KEY(" + MUSICCOMPOSERS_MOVIE_ID_MOVIE + "," +
                    MUSICCOMPOSERS_MOVIE_ID_MUSICCOMPOSER + "))";

    private static final String MUSICCOMPOSERS_SHOW_TABLE_CREATE =
            "CREATE TABLE " + MUSICCOMPOSERS_SHOW_TABLE_NAME + " (" +
                    MUSICCOMPOSERS_SHOW_ID_SHOW + " INTEGER REFERENCES " + SHOW_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
                    MUSICCOMPOSERS_SHOW_ID_MUSICCOMPOSER + " INTEGER REFERENCES " + MUSICCOMPOSERS_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
                    "PRIMARY KEY(" + MUSICCOMPOSERS_SHOW_ID_SHOW + "," +
                    MUSICCOMPOSERS_SHOW_ID_MUSICCOMPOSER + "))";

    private static final String MUSICCOMPOSERS_EPISODE_TABLE_CREATE =
            "CREATE TABLE " + MUSICCOMPOSERS_EPISODE_TABLE_NAME + " (" +
                    MUSICCOMPOSERS_EPISODE_ID_EPISODE + " INTEGER REFERENCES " + EPISODE_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
                    MUSICCOMPOSERS_EPISODE_ID_MUSICCOMPOSER + " INTEGER REFERENCES " + MUSICCOMPOSERS_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
                    "PRIMARY KEY(" + MUSICCOMPOSERS_EPISODE_ID_EPISODE + "," +
                    MUSICCOMPOSERS_EPISODE_ID_MUSICCOMPOSER + "))";

    /*
     *  Tables associating movie, show and episode tables with countries
     */
    private static final String COUNTRIES_MOVIE_TABLE_CREATE =
            "CREATE TABLE " + COUNTRIES_MOVIE_TABLE_NAME + " (" +
                    COUNTRIES_MOVIE_ID_MOVIE + " INTEGER REFERENCES " + MOVIE_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
                    COUNTRIES_MOVIE_ID_COUNTRY + " INTEGER REFERENCES " + COUNTRIES_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
                    "PRIMARY KEY(" + COUNTRIES_MOVIE_ID_MOVIE + "," +
                    COUNTRIES_MOVIE_ID_COUNTRY + "))";

    private static final String COUNTRIES_SHOW_TABLE_CREATE =
            "CREATE TABLE " + COUNTRIES_SHOW_TABLE_NAME + " (" +
                    COUNTRIES_SHOW_ID_SHOW + " INTEGER REFERENCES " + SHOW_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
                    COUNTRIES_SHOW_ID_COUNTRY + " INTEGER REFERENCES " + COUNTRIES_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
                    "PRIMARY KEY(" + COUNTRIES_SHOW_ID_SHOW + "," +
                    COUNTRIES_SHOW_ID_COUNTRY + "))";

    private static final String COUNTRIES_EPISODE_TABLE_CREATE =
            "CREATE TABLE " + COUNTRIES_EPISODE_TABLE_NAME + " (" +
                    COUNTRIES_EPISODE_ID_EPISODE + " INTEGER REFERENCES " + EPISODE_TABLE_NAME + " ON DELETE CASCADE ON UPDATE CASCADE," +
                    COUNTRIES_EPISODE_ID_COUNTRY + " INTEGER REFERENCES " + COUNTRIES_TABLE_NAME + " ON DELETE RESTRICT ON UPDATE CASCADE," +
                    "PRIMARY KEY(" + COUNTRIES_EPISODE_ID_EPISODE + "," +
                    COUNTRIES_EPISODE_ID_COUNTRY + "))";

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

    private static final String TAGLINES_MOVIE_VIEW_CREATE =
            "CREATE VIEW " + TAGLINES_MOVIE_VIEW_NAME + " AS SELECT " +
                    TAGLINES_MOVIE_TABLE_NAME + "." +
                    TAGLINES_MOVIE_ID_MOVIE + " AS " + ScraperStore.Movie.Tagline.MOVIE + ", " +
                    TAGLINES_TABLE_NAME + "." +
                    ScraperStore.Tagline.NAME + " AS " + ScraperStore.Movie.Tagline.NAME + ", " +
                    TAGLINES_TABLE_NAME + "." +
                    ScraperStore.Tagline.ID + " AS " + ScraperStore.Movie.Tagline.TAGLINE + " FROM " +
                    TAGLINES_MOVIE_TABLE_NAME + " LEFT JOIN " + TAGLINES_TABLE_NAME +
                    " ON (" + TAGLINES_MOVIE_TABLE_NAME + "." + TAGLINES_MOVIE_ID_TAGLINE +
                    " = " + TAGLINES_TABLE_NAME + "." + ScraperStore.Tagline.ID + ")";

    private static final String TAGLINES_MOVIE_VIEW_INSERT_TRIGGER =
            "CREATE TRIGGER insert_taglines_movie INSTEAD OF INSERT ON " + TAGLINES_MOVIE_VIEW_NAME +
                    " BEGIN " +
                    "INSERT OR IGNORE INTO " + TAGLINES_TABLE_NAME + " ( " + ScraperStore.Tagline.NAME + " ) " +
                    "VALUES (NEW." + ScraperStore.Movie.Tagline.NAME + "); " +
                    "INSERT INTO " + TAGLINES_MOVIE_TABLE_NAME +
                    " ( " + TAGLINES_MOVIE_ID_MOVIE + "," + TAGLINES_MOVIE_ID_TAGLINE + " ) " +
                    "SELECT NEW." + ScraperStore.Movie.Tagline.MOVIE + ", " +
                    TAGLINES_TABLE_NAME + "." + ScraperStore.Tagline.ID + " " +
                    " FROM " + TAGLINES_TABLE_NAME +
                    " WHERE " + ScraperStore.Tagline.NAME + " = NEW." + ScraperStore.Movie.Tagline.NAME + "; " +
                    "END";


    private static final String PRODUCERS_MOVIE_VIEW_CREATE =
            "CREATE VIEW " + PRODUCERS_MOVIE_VIEW_NAME + " AS SELECT " +
                    PRODUCERS_MOVIE_TABLE_NAME + "." +
                    PRODUCERS_MOVIE_ID_MOVIE + " AS " + ScraperStore.Movie.Producer.MOVIE + ", " +
                    PRODUCERS_TABLE_NAME + "." +
                    ScraperStore.Producer.NAME + " AS " + ScraperStore.Movie.Producer.NAME + ", " +
                    PRODUCERS_TABLE_NAME + "." +
                    ScraperStore.Producer.ID + " AS " + ScraperStore.Movie.Producer.PRODUCER + " FROM " +
                    PRODUCERS_MOVIE_TABLE_NAME + " LEFT JOIN " + PRODUCERS_TABLE_NAME +
                    " ON (" + PRODUCERS_MOVIE_TABLE_NAME + "." + PRODUCERS_MOVIE_ID_PRODUCER +
                    " = " + PRODUCERS_TABLE_NAME + "." + ScraperStore.Producer.ID + ")";

    private static final String PRODUCERS_MOVIE_VIEW_INSERT_TRIGGER =
            "CREATE TRIGGER insert_producers_movie INSTEAD OF INSERT ON " + PRODUCERS_MOVIE_VIEW_NAME +
                    " BEGIN " +
                    "INSERT OR IGNORE INTO " + PRODUCERS_TABLE_NAME + " ( " + ScraperStore.Producer.NAME + " ) " +
                    "VALUES (NEW." + ScraperStore.Movie.Producer.NAME + "); " +
                    "INSERT INTO " + PRODUCERS_MOVIE_TABLE_NAME +
                    " ( " + PRODUCERS_MOVIE_ID_MOVIE + "," + PRODUCERS_MOVIE_ID_PRODUCER + " ) " +
                    "SELECT NEW." + ScraperStore.Movie.Producer.MOVIE + ", " +
                    PRODUCERS_TABLE_NAME + "." + ScraperStore.Producer.ID + " " +
                    " FROM " + PRODUCERS_TABLE_NAME +
                    " WHERE " + ScraperStore.Producer.NAME + " = NEW." + ScraperStore.Movie.Producer.NAME + "; " +
                    "END";

    private static final String SCREENPLAYS_MOVIE_VIEW_CREATE =
            "CREATE VIEW " + SCREENPLAYS_MOVIE_VIEW_NAME + " AS SELECT " +
                    SCREENPLAYS_MOVIE_TABLE_NAME + "." +
                    SCREENPLAYS_MOVIE_ID_MOVIE + " AS " + ScraperStore.Movie.Screenplay.MOVIE + ", " +
                    SCREENPLAYS_TABLE_NAME + "." +
                    ScraperStore.Screenplay.NAME + " AS " + ScraperStore.Movie.Screenplay.NAME + ", " +
                    SCREENPLAYS_TABLE_NAME + "." +
                    ScraperStore.Screenplay.ID + " AS " + ScraperStore.Movie.Screenplay.SCREENPLAY + " FROM " +
                    SCREENPLAYS_MOVIE_TABLE_NAME + " LEFT JOIN " + SCREENPLAYS_TABLE_NAME +
                    " ON (" + SCREENPLAYS_MOVIE_TABLE_NAME + "." + SCREENPLAYS_MOVIE_ID_SCREENPLAY +
                    " = " + SCREENPLAYS_TABLE_NAME + "." + ScraperStore.Screenplay.ID + ")";

    private static final String SCREENPLAYS_MOVIE_VIEW_INSERT_TRIGGER =
            "CREATE TRIGGER insert_screenplays_movie INSTEAD OF INSERT ON " + SCREENPLAYS_MOVIE_VIEW_NAME +
                    " BEGIN " +
                    "INSERT OR IGNORE INTO " + SCREENPLAYS_TABLE_NAME + " ( " + ScraperStore.Screenplay.NAME + " ) " +
                    "VALUES (NEW." + ScraperStore.Movie.Screenplay.NAME + "); " +
                    "INSERT INTO " + SCREENPLAYS_MOVIE_TABLE_NAME +
                    " ( " + SCREENPLAYS_MOVIE_ID_MOVIE + "," + SCREENPLAYS_MOVIE_ID_SCREENPLAY + " ) " +
                    "SELECT NEW." + ScraperStore.Movie.Screenplay.MOVIE + ", " +
                    SCREENPLAYS_TABLE_NAME + "." + ScraperStore.Screenplay.ID + " " +
                    " FROM " + SCREENPLAYS_TABLE_NAME +
                    " WHERE " + ScraperStore.Screenplay.NAME + " = NEW." + ScraperStore.Movie.Screenplay.NAME + "; " +
                    "END";

    private static final String MUSICCOMPOSERS_MOVIE_VIEW_CREATE =
            "CREATE VIEW " + MUSICCOMPOSERS_MOVIE_VIEW_NAME + " AS SELECT " +
                    MUSICCOMPOSERS_MOVIE_TABLE_NAME + "." +
                    MUSICCOMPOSERS_MOVIE_ID_MOVIE + " AS " + ScraperStore.Movie.Musiccomposer.MOVIE + ", " +
                    MUSICCOMPOSERS_TABLE_NAME + "." +
                    ScraperStore.Musiccomposer.NAME + " AS " + ScraperStore.Movie.Musiccomposer.NAME + ", " +
                    MUSICCOMPOSERS_TABLE_NAME + "." +
                    ScraperStore.Musiccomposer.ID + " AS " + ScraperStore.Movie.Musiccomposer.MUSICCOMPOSER + " FROM " +
                    MUSICCOMPOSERS_MOVIE_TABLE_NAME + " LEFT JOIN " + MUSICCOMPOSERS_TABLE_NAME +
                    " ON (" + MUSICCOMPOSERS_MOVIE_TABLE_NAME + "." + MUSICCOMPOSERS_MOVIE_ID_MUSICCOMPOSER +
                    " = " + MUSICCOMPOSERS_TABLE_NAME + "." + ScraperStore.Musiccomposer.ID + ")";

    private static final String MUSICCOMPOSERS_MOVIE_VIEW_INSERT_TRIGGER =
            "CREATE TRIGGER insert_musiccomposers_movie INSTEAD OF INSERT ON " + MUSICCOMPOSERS_MOVIE_VIEW_NAME +
                    " BEGIN " +
                    "INSERT OR IGNORE INTO " + MUSICCOMPOSERS_TABLE_NAME + " ( " + ScraperStore.Musiccomposer.NAME + " ) " +
                    "VALUES (NEW." + ScraperStore.Movie.Musiccomposer.NAME + "); " +
                    "INSERT INTO " + MUSICCOMPOSERS_MOVIE_TABLE_NAME +
                    " ( " + MUSICCOMPOSERS_MOVIE_ID_MOVIE + "," + MUSICCOMPOSERS_MOVIE_ID_MUSICCOMPOSER + " ) " +
                    "SELECT NEW." + ScraperStore.Movie.Musiccomposer.MOVIE + ", " +
                    MUSICCOMPOSERS_TABLE_NAME + "." + ScraperStore.Musiccomposer.ID + " " +
                    " FROM " + MUSICCOMPOSERS_TABLE_NAME +
                    " WHERE " + ScraperStore.Musiccomposer.NAME + " = NEW." + ScraperStore.Movie.Musiccomposer.NAME + "; " +
                    "END";



    private static final String COUNTRIES_MOVIE_VIEW_CREATE =
            "CREATE VIEW " + COUNTRIES_MOVIE_VIEW_NAME + " AS SELECT " +
                    COUNTRIES_MOVIE_TABLE_NAME + "." +
                    COUNTRIES_MOVIE_ID_MOVIE + " AS " + ScraperStore.Movie.Country.MOVIE + ", " +
                    COUNTRIES_TABLE_NAME + "." +
                    ScraperStore.Country.NAME + " AS " + ScraperStore.Movie.Country.NAME + ", " +
                    COUNTRIES_TABLE_NAME + "." +
                    ScraperStore.Country.ID + " AS " + ScraperStore.Movie.Country.COUNTRY + " FROM " +
                    COUNTRIES_MOVIE_TABLE_NAME + " LEFT JOIN " + COUNTRIES_TABLE_NAME +
                    " ON (" + COUNTRIES_MOVIE_TABLE_NAME + "." + COUNTRIES_MOVIE_ID_COUNTRY +
                    " = " + COUNTRIES_TABLE_NAME + "." + ScraperStore.Country.ID + ")";

    private static final String COUNTRIES_MOVIE_VIEW_INSERT_TRIGGER =
            "CREATE TRIGGER insert_countries_movie INSTEAD OF INSERT ON " + COUNTRIES_MOVIE_VIEW_NAME +
                    " BEGIN " +
                    "INSERT OR IGNORE INTO " + COUNTRIES_TABLE_NAME + " ( " + ScraperStore.Country.NAME + " ) " +
                    "VALUES (NEW." + ScraperStore.Movie.Country.NAME + "); " +
                    "INSERT INTO " + COUNTRIES_MOVIE_TABLE_NAME +
                    " ( " + COUNTRIES_MOVIE_ID_MOVIE + "," + COUNTRIES_MOVIE_ID_COUNTRY + " ) " +
                    "SELECT NEW." + ScraperStore.Movie.Country.MOVIE + ", " +
                    COUNTRIES_TABLE_NAME + "." + ScraperStore.Country.ID + " " +
                    " FROM " + COUNTRIES_TABLE_NAME +
                    " WHERE " + ScraperStore.Country.NAME + " = NEW." + ScraperStore.Movie.Country.NAME + "; " +
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

    private static final String TAGLINES_SHOW_VIEW_CREATE =
            "CREATE VIEW " + TAGLINES_SHOW_VIEW_NAME + " AS SELECT " +
                    TAGLINES_SHOW_TABLE_NAME + "." +
                    TAGLINES_SHOW_ID_SHOW + " AS " + ScraperStore.Show.Tagline.SHOW + ", " +
                    TAGLINES_TABLE_NAME + "." +
                    ScraperStore.Tagline.NAME + " AS " + ScraperStore.Show.Tagline.NAME + ", " +
                    TAGLINES_TABLE_NAME + "." +
                    ScraperStore.Tagline.ID + " AS " + ScraperStore.Show.Tagline.TAGLINE + " FROM " +
                    TAGLINES_SHOW_TABLE_NAME + " LEFT JOIN " + TAGLINES_TABLE_NAME +
                    " ON (" + TAGLINES_SHOW_TABLE_NAME + "." + TAGLINES_SHOW_ID_TAGLINE +
                    " = " + TAGLINES_TABLE_NAME + "." + ScraperStore.Tagline.ID + ")";

    private static final String PRODUCERS_SHOW_VIEW_CREATE =
            "CREATE VIEW " + PRODUCERS_SHOW_VIEW_NAME + " AS SELECT " +
                    PRODUCERS_SHOW_TABLE_NAME + "." +
                    PRODUCERS_SHOW_ID_SHOW + " AS " + ScraperStore.Show.Producer.SHOW + ", " +
                    PRODUCERS_TABLE_NAME + "." +
                    ScraperStore.Producer.NAME + " AS " + ScraperStore.Show.Producer.NAME + ", " +
                    PRODUCERS_TABLE_NAME + "." +
                    ScraperStore.Producer.ID + " AS " + ScraperStore.Show.Producer.PRODUCER + " FROM " +
                    PRODUCERS_SHOW_TABLE_NAME + " LEFT JOIN " + PRODUCERS_TABLE_NAME +
                    " ON (" + PRODUCERS_SHOW_TABLE_NAME + "." + PRODUCERS_SHOW_ID_PRODUCER +
                    " = " + PRODUCERS_TABLE_NAME + "." + ScraperStore.Producer.ID + ")";

    private static final String SCREENPLAYS_SHOW_VIEW_CREATE =
            "CREATE VIEW " + SCREENPLAYS_SHOW_VIEW_NAME + " AS SELECT " +
                    SCREENPLAYS_SHOW_TABLE_NAME + "." +
                    SCREENPLAYS_SHOW_ID_SHOW + " AS " + ScraperStore.Show.Screenplay.SHOW + ", " +
                    SCREENPLAYS_TABLE_NAME + "." +
                    ScraperStore.Screenplay.NAME + " AS " + ScraperStore.Show.Screenplay.NAME + ", " +
                    SCREENPLAYS_TABLE_NAME + "." +
                    ScraperStore.Screenplay.ID + " AS " + ScraperStore.Show.Screenplay.SCREENPLAY + " FROM " +
                    SCREENPLAYS_SHOW_TABLE_NAME + " LEFT JOIN " + SCREENPLAYS_TABLE_NAME +
                    " ON (" + SCREENPLAYS_SHOW_TABLE_NAME + "." + SCREENPLAYS_SHOW_ID_SCREENPLAY +
                    " = " + SCREENPLAYS_TABLE_NAME + "." + ScraperStore.Screenplay.ID + ")";

    private static final String MUSICCOMPOSERS_SHOW_VIEW_CREATE =
            "CREATE VIEW " + MUSICCOMPOSERS_SHOW_VIEW_NAME + " AS SELECT " +
                    MUSICCOMPOSERS_SHOW_TABLE_NAME + "." +
                    MUSICCOMPOSERS_SHOW_ID_SHOW + " AS " + ScraperStore.Show.Musiccomposer.SHOW + ", " +
                    MUSICCOMPOSERS_TABLE_NAME + "." +
                    ScraperStore.Musiccomposer.NAME + " AS " + ScraperStore.Show.Musiccomposer.NAME + ", " +
                    MUSICCOMPOSERS_TABLE_NAME + "." +
                    ScraperStore.Musiccomposer.ID + " AS " + ScraperStore.Show.Musiccomposer.MUSICCOMPOSER + " FROM " +
                    MUSICCOMPOSERS_SHOW_TABLE_NAME + " LEFT JOIN " + MUSICCOMPOSERS_TABLE_NAME +
                    " ON (" + MUSICCOMPOSERS_SHOW_TABLE_NAME + "." + MUSICCOMPOSERS_SHOW_ID_MUSICCOMPOSER +
                    " = " + MUSICCOMPOSERS_TABLE_NAME + "." + ScraperStore.Musiccomposer.ID + ")";

    private static final String COUNTRIES_SHOW_VIEW_CREATE =
            "CREATE VIEW " + COUNTRIES_SHOW_VIEW_NAME + " AS SELECT " +
                    COUNTRIES_SHOW_TABLE_NAME + "." +
                    COUNTRIES_SHOW_ID_SHOW + " AS " + ScraperStore.Show.Country.SHOW + ", " +
                    COUNTRIES_TABLE_NAME + "." +
                    ScraperStore.Country.NAME + " AS " + ScraperStore.Show.Country.NAME + ", " +
                    COUNTRIES_TABLE_NAME + "." +
                    ScraperStore.Country.ID + " AS " + ScraperStore.Show.Country.COUNTRY + " FROM " +
                    COUNTRIES_SHOW_TABLE_NAME + " LEFT JOIN " + COUNTRIES_TABLE_NAME +
                    " ON (" + COUNTRIES_SHOW_TABLE_NAME + "." + COUNTRIES_SHOW_ID_COUNTRY +
                    " = " + COUNTRIES_TABLE_NAME + "." + ScraperStore.Country.ID + ")";

    private static final String SEASONPLOTS_SHOW_VIEW_CREATE =
            "CREATE VIEW " + SEASONPLOTS_SHOW_VIEW_NAME + " AS SELECT " +
                    SEASONPLOTS_SHOW_TABLE_NAME + "." +
                    SEASONPLOTS_SHOW_ID_SHOW + " AS " + ScraperStore.Show.SeasonPlot.SHOW + ", " +
                    SEASONPLOTS_TABLE_NAME + "." +
                    ScraperStore.SeasonPlot.NAME + " AS " + ScraperStore.Show.SeasonPlot.NAME + ", " +
                    SEASONPLOTS_TABLE_NAME + "." +
                    ScraperStore.SeasonPlot.ID + " AS " + ScraperStore.Show.SeasonPlot.SEASONPLOT + " FROM " +
                    SEASONPLOTS_SHOW_TABLE_NAME + " LEFT JOIN " + SEASONPLOTS_TABLE_NAME +
                    " ON (" + SEASONPLOTS_SHOW_TABLE_NAME + "." + SEASONPLOTS_SHOW_ID_SEASONPLOT +
                    " = " + SEASONPLOTS_TABLE_NAME + "." + ScraperStore.SeasonPlot.ID + ")";

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

    private static final String TAGLINES_SHOW_VIEW_INSERT_TRIGGER =
            "CREATE TRIGGER insert_taglines_show INSTEAD OF INSERT ON " + TAGLINES_SHOW_VIEW_NAME +
                    " BEGIN " +
                    "INSERT OR IGNORE INTO " + TAGLINES_TABLE_NAME + " ( " + ScraperStore.Tagline.NAME + " ) " +
                    "VALUES (NEW." + ScraperStore.Show.Tagline.NAME + "); " +
                    "INSERT INTO " + TAGLINES_SHOW_TABLE_NAME +
                    " ( " + TAGLINES_SHOW_ID_SHOW + "," + TAGLINES_SHOW_ID_TAGLINE + " ) " +
                    "SELECT NEW." + ScraperStore.Show.Tagline.SHOW + ", " +
                    TAGLINES_TABLE_NAME + "." + ScraperStore.Tagline.ID + " " +
                    " FROM " + TAGLINES_TABLE_NAME +
                    " WHERE " + ScraperStore.Tagline.NAME + " = NEW." + ScraperStore.Show.Tagline.NAME + "; " +
                    "END";

    private static final String PRODUCERS_SHOW_VIEW_INSERT_TRIGGER =
            "CREATE TRIGGER insert_producers_show INSTEAD OF INSERT ON " + PRODUCERS_SHOW_VIEW_NAME +
                    " BEGIN " +
                    "INSERT OR IGNORE INTO " + PRODUCERS_TABLE_NAME + " ( " + ScraperStore.Producer.NAME + " ) " +
                    "VALUES (NEW." + ScraperStore.Show.Producer.NAME + "); " +
                    "INSERT INTO " + PRODUCERS_SHOW_TABLE_NAME +
                    " ( " + PRODUCERS_SHOW_ID_SHOW + "," + PRODUCERS_SHOW_ID_PRODUCER + " ) " +
                    "SELECT NEW." + ScraperStore.Show.Producer.SHOW + ", " +
                    PRODUCERS_TABLE_NAME + "." + ScraperStore.Producer.ID + " " +
                    " FROM " + PRODUCERS_TABLE_NAME +
                    " WHERE " + ScraperStore.Producer.NAME + " = NEW." + ScraperStore.Show.Producer.NAME + "; " +
                    "END";

    private static final String SCREENPLAYS_SHOW_VIEW_INSERT_TRIGGER =
            "CREATE TRIGGER insert_screenplays_show INSTEAD OF INSERT ON " + SCREENPLAYS_SHOW_VIEW_NAME +
                    " BEGIN " +
                    "INSERT OR IGNORE INTO " + SCREENPLAYS_TABLE_NAME + " ( " + ScraperStore.Screenplay.NAME + " ) " +
                    "VALUES (NEW." + ScraperStore.Show.Screenplay.NAME + "); " +
                    "INSERT INTO " + SCREENPLAYS_SHOW_TABLE_NAME +
                    " ( " + SCREENPLAYS_SHOW_ID_SHOW + "," + SCREENPLAYS_SHOW_ID_SCREENPLAY + " ) " +
                    "SELECT NEW." + ScraperStore.Show.Screenplay.SHOW + ", " +
                    SCREENPLAYS_TABLE_NAME + "." + ScraperStore.Screenplay.ID + " " +
                    " FROM " + SCREENPLAYS_TABLE_NAME +
                    " WHERE " + ScraperStore.Screenplay.NAME + " = NEW." + ScraperStore.Show.Screenplay.NAME + "; " +
                    "END";

    private static final String MUSICCOMPOSERS_SHOW_VIEW_INSERT_TRIGGER =
            "CREATE TRIGGER insert_musiccomposers_show INSTEAD OF INSERT ON " + MUSICCOMPOSERS_SHOW_VIEW_NAME +
                    " BEGIN " +
                    "INSERT OR IGNORE INTO " + MUSICCOMPOSERS_TABLE_NAME + " ( " + ScraperStore.Musiccomposer.NAME + " ) " +
                    "VALUES (NEW." + ScraperStore.Show.Musiccomposer.NAME + "); " +
                    "INSERT INTO " + MUSICCOMPOSERS_SHOW_TABLE_NAME +
                    " ( " + MUSICCOMPOSERS_SHOW_ID_SHOW + "," + MUSICCOMPOSERS_SHOW_ID_MUSICCOMPOSER + " ) " +
                    "SELECT NEW." + ScraperStore.Show.Musiccomposer.SHOW + ", " +
                    MUSICCOMPOSERS_TABLE_NAME + "." + ScraperStore.Musiccomposer.ID + " " +
                    " FROM " + MUSICCOMPOSERS_TABLE_NAME +
                    " WHERE " + ScraperStore.Musiccomposer.NAME + " = NEW." + ScraperStore.Show.Musiccomposer.NAME + "; " +
                    "END";

    private static final String COUNTRIES_SHOW_VIEW_INSERT_TRIGGER =
            "CREATE TRIGGER insert_countries_show INSTEAD OF INSERT ON " + COUNTRIES_SHOW_VIEW_NAME +
                    " BEGIN " +
                    "INSERT OR IGNORE INTO " + COUNTRIES_TABLE_NAME + " ( " + ScraperStore.Country.NAME + " ) " +
                    "VALUES (NEW." + ScraperStore.Show.Country.NAME + "); " +
                    "INSERT INTO " + COUNTRIES_SHOW_TABLE_NAME +
                    " ( " + COUNTRIES_SHOW_ID_SHOW + "," + COUNTRIES_SHOW_ID_COUNTRY + " ) " +
                    "SELECT NEW." + ScraperStore.Show.Country.SHOW + ", " +
                    COUNTRIES_TABLE_NAME + "." + ScraperStore.Country.ID + " " +
                    " FROM " + COUNTRIES_TABLE_NAME +
                    " WHERE " + ScraperStore.Country.NAME + " = NEW." + ScraperStore.Show.Country.NAME + "; " +
                    "END";

    private static final String SEASONPLOTS_SHOW_VIEW_INSERT_TRIGGER =
            "CREATE TRIGGER insert_seasonplots_show INSTEAD OF INSERT ON " + SEASONPLOTS_SHOW_VIEW_NAME +
                    " BEGIN " +
                    "INSERT OR IGNORE INTO " + SEASONPLOTS_TABLE_NAME + " ( " + ScraperStore.SeasonPlot.NAME + " ) " +
                    "VALUES (NEW." + ScraperStore.Show.SeasonPlot.NAME + "); " +
                    "INSERT INTO " + SEASONPLOTS_SHOW_TABLE_NAME +
                    " ( " + SEASONPLOTS_SHOW_ID_SHOW + "," + SEASONPLOTS_SHOW_ID_SEASONPLOT + " ) " +
                    "SELECT NEW." + ScraperStore.Show.SeasonPlot.SHOW + ", " +
                    SEASONPLOTS_TABLE_NAME + "." + ScraperStore.SeasonPlot.ID + " " +
                    " FROM " + SEASONPLOTS_TABLE_NAME +
                    " WHERE " + ScraperStore.SeasonPlot.NAME + " = NEW." + ScraperStore.Show.SeasonPlot.NAME + "; " +
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


    private static final String TAGLINES_EPISODE_VIEW_CREATE =
            "CREATE VIEW " + TAGLINES_EPISODE_VIEW_NAME + " AS SELECT " +
                    TAGLINES_EPISODE_TABLE_NAME + "." +
                    TAGLINES_EPISODE_ID_EPISODE + " AS " + ScraperStore.Episode.Tagline.EPISODE + ", " +
                    TAGLINES_TABLE_NAME + "." +
                    ScraperStore.Tagline.NAME + " AS " + ScraperStore.Episode.Tagline.NAME + ", " +
                    TAGLINES_TABLE_NAME + "." +
                    ScraperStore.Tagline.ID + " AS " + ScraperStore.Episode.Tagline.TAGLINE + " FROM " +
                    TAGLINES_EPISODE_TABLE_NAME + " LEFT JOIN " + TAGLINES_TABLE_NAME +
                    " ON (" + TAGLINES_EPISODE_TABLE_NAME + "." + TAGLINES_EPISODE_ID_TAGLINE +
                    " = " + TAGLINES_TABLE_NAME + "." + ScraperStore.Tagline.ID + ")";

    private static final String TAGLINES_EPISODE_VIEW_INSERT_TRIGGER =
            "CREATE TRIGGER insert_taglines_episode INSTEAD OF INSERT ON " + TAGLINES_EPISODE_VIEW_NAME +
                    " BEGIN " +
                    "INSERT OR IGNORE INTO " + TAGLINES_TABLE_NAME + " ( " + ScraperStore.Tagline.NAME + " ) " +
                    "VALUES (NEW." + ScraperStore.Episode.Tagline.NAME + "); " +
                    "INSERT INTO " + TAGLINES_EPISODE_TABLE_NAME +
                    " ( " + TAGLINES_EPISODE_ID_EPISODE + "," + TAGLINES_EPISODE_ID_TAGLINE + " ) " +
                    "SELECT NEW." + ScraperStore.Episode.Tagline.EPISODE + ", " +
                    TAGLINES_TABLE_NAME + "." + ScraperStore.Tagline.ID + " " +
                    " FROM " + TAGLINES_TABLE_NAME +
                    " WHERE " + ScraperStore.Tagline.NAME + " = NEW." + ScraperStore.Episode.Tagline.NAME + "; " +
                    "END";

    private static final String PRODUCERS_EPISODE_VIEW_CREATE =
            "CREATE VIEW " + PRODUCERS_EPISODE_VIEW_NAME + " AS SELECT " +
                    PRODUCERS_EPISODE_TABLE_NAME + "." +
                    PRODUCERS_EPISODE_ID_EPISODE + " AS " + ScraperStore.Episode.Producer.EPISODE + ", " +
                    PRODUCERS_TABLE_NAME + "." +
                    ScraperStore.Producer.NAME + " AS " + ScraperStore.Episode.Producer.NAME + ", " +
                    PRODUCERS_TABLE_NAME + "." +
                    ScraperStore.Producer.ID + " AS " + ScraperStore.Episode.Producer.PRODUCER + " FROM " +
                    PRODUCERS_EPISODE_TABLE_NAME + " LEFT JOIN " + PRODUCERS_TABLE_NAME +
                    " ON (" + PRODUCERS_EPISODE_TABLE_NAME + "." + PRODUCERS_EPISODE_ID_PRODUCER +
                    " = " + PRODUCERS_TABLE_NAME + "." + ScraperStore.Producer.ID + ")";

    private static final String PRODUCERS_EPISODE_VIEW_INSERT_TRIGGER =
            "CREATE TRIGGER insert_producers_episode INSTEAD OF INSERT ON " + PRODUCERS_EPISODE_VIEW_NAME +
                    " BEGIN " +
                    "INSERT OR IGNORE INTO " + PRODUCERS_TABLE_NAME + " ( " + ScraperStore.Producer.NAME + " ) " +
                    "VALUES (NEW." + ScraperStore.Episode.Producer.NAME + "); " +
                    "INSERT INTO " + PRODUCERS_EPISODE_TABLE_NAME +
                    " ( " + PRODUCERS_EPISODE_ID_EPISODE + "," + PRODUCERS_EPISODE_ID_PRODUCER + " ) " +
                    "SELECT NEW." + ScraperStore.Episode.Producer.EPISODE + ", " +
                    PRODUCERS_TABLE_NAME + "." + ScraperStore.Producer.ID + " " +
                    " FROM " + PRODUCERS_TABLE_NAME +
                    " WHERE " + ScraperStore.Producer.NAME + " = NEW." + ScraperStore.Episode.Producer.NAME + "; " +
                    "END";

    private static final String SCREENPLAYS_EPISODE_VIEW_CREATE =
            "CREATE VIEW " + SCREENPLAYS_EPISODE_VIEW_NAME + " AS SELECT " +
                    SCREENPLAYS_EPISODE_TABLE_NAME + "." +
                    SCREENPLAYS_EPISODE_ID_EPISODE + " AS " + ScraperStore.Episode.Screenplay.EPISODE + ", " +
                    SCREENPLAYS_TABLE_NAME + "." +
                    ScraperStore.Screenplay.NAME + " AS " + ScraperStore.Episode.Screenplay.NAME + ", " +
                    SCREENPLAYS_TABLE_NAME + "." +
                    ScraperStore.Screenplay.ID + " AS " + ScraperStore.Episode.Screenplay.SCREENPLAY + " FROM " +
                    SCREENPLAYS_EPISODE_TABLE_NAME + " LEFT JOIN " + SCREENPLAYS_TABLE_NAME +
                    " ON (" + SCREENPLAYS_EPISODE_TABLE_NAME + "." + SCREENPLAYS_EPISODE_ID_SCREENPLAY +
                    " = " + SCREENPLAYS_TABLE_NAME + "." + ScraperStore.Screenplay.ID + ")";

    private static final String SCREENPLAYS_EPISODE_VIEW_INSERT_TRIGGER =
            "CREATE TRIGGER insert_screenplays_episode INSTEAD OF INSERT ON " + SCREENPLAYS_EPISODE_VIEW_NAME +
                    " BEGIN " +
                    "INSERT OR IGNORE INTO " + SCREENPLAYS_TABLE_NAME + " ( " + ScraperStore.Screenplay.NAME + " ) " +
                    "VALUES (NEW." + ScraperStore.Episode.Screenplay.NAME + "); " +
                    "INSERT INTO " + SCREENPLAYS_EPISODE_TABLE_NAME +
                    " ( " + SCREENPLAYS_EPISODE_ID_EPISODE + "," + SCREENPLAYS_EPISODE_ID_SCREENPLAY + " ) " +
                    "SELECT NEW." + ScraperStore.Episode.Screenplay.EPISODE + ", " +
                    SCREENPLAYS_TABLE_NAME + "." + ScraperStore.Screenplay.ID + " " +
                    " FROM " + SCREENPLAYS_TABLE_NAME +
                    " WHERE " + ScraperStore.Screenplay.NAME + " = NEW." + ScraperStore.Episode.Screenplay.NAME + "; " +
                    "END";

    private static final String MUSICCOMPOSERS_EPISODE_VIEW_CREATE =
            "CREATE VIEW " + MUSICCOMPOSERS_EPISODE_VIEW_NAME + " AS SELECT " +
                    MUSICCOMPOSERS_EPISODE_TABLE_NAME + "." +
                    MUSICCOMPOSERS_EPISODE_ID_EPISODE + " AS " + ScraperStore.Episode.Musiccomposer.EPISODE + ", " +
                    MUSICCOMPOSERS_TABLE_NAME + "." +
                    ScraperStore.Musiccomposer.NAME + " AS " + ScraperStore.Episode.Musiccomposer.NAME + ", " +
                    MUSICCOMPOSERS_TABLE_NAME + "." +
                    ScraperStore.Musiccomposer.ID + " AS " + ScraperStore.Episode.Musiccomposer.MUSICCOMPOSER + " FROM " +
                    MUSICCOMPOSERS_EPISODE_TABLE_NAME + " LEFT JOIN " + MUSICCOMPOSERS_TABLE_NAME +
                    " ON (" + MUSICCOMPOSERS_EPISODE_TABLE_NAME + "." + MUSICCOMPOSERS_EPISODE_ID_MUSICCOMPOSER +
                    " = " + MUSICCOMPOSERS_TABLE_NAME + "." + ScraperStore.Musiccomposer.ID + ")";

    private static final String MUSICCOMPOSERS_EPISODE_VIEW_INSERT_TRIGGER =
            "CREATE TRIGGER insert_musiccomposers_episode INSTEAD OF INSERT ON " + MUSICCOMPOSERS_EPISODE_VIEW_NAME +
                    " BEGIN " +
                    "INSERT OR IGNORE INTO " + MUSICCOMPOSERS_TABLE_NAME + " ( " + ScraperStore.Musiccomposer.NAME + " ) " +
                    "VALUES (NEW." + ScraperStore.Episode.Musiccomposer.NAME + "); " +
                    "INSERT INTO " + MUSICCOMPOSERS_EPISODE_TABLE_NAME +
                    " ( " + MUSICCOMPOSERS_EPISODE_ID_EPISODE + "," + MUSICCOMPOSERS_EPISODE_ID_MUSICCOMPOSER + " ) " +
                    "SELECT NEW." + ScraperStore.Episode.Musiccomposer.EPISODE + ", " +
                    MUSICCOMPOSERS_TABLE_NAME + "." + ScraperStore.Musiccomposer.ID + " " +
                    " FROM " + MUSICCOMPOSERS_TABLE_NAME +
                    " WHERE " + ScraperStore.Musiccomposer.NAME + " = NEW." + ScraperStore.Episode.Musiccomposer.NAME + "; " +
                    "END";

    private static final String COUNTRIES_EPISODE_VIEW_CREATE =
            "CREATE VIEW " + COUNTRIES_EPISODE_VIEW_NAME + " AS SELECT " +
                    COUNTRIES_EPISODE_TABLE_NAME + "." +
                    COUNTRIES_EPISODE_ID_EPISODE + " AS " + ScraperStore.Episode.Country.EPISODE + ", " +
                    COUNTRIES_TABLE_NAME + "." +
                    ScraperStore.Country.NAME + " AS " + ScraperStore.Episode.Country.NAME + ", " +
                    COUNTRIES_TABLE_NAME + "." +
                    ScraperStore.Country.ID + " AS " + ScraperStore.Episode.Country.COUNTRY + " FROM " +
                    COUNTRIES_EPISODE_TABLE_NAME + " LEFT JOIN " + COUNTRIES_TABLE_NAME +
                    " ON (" + COUNTRIES_EPISODE_TABLE_NAME + "." + COUNTRIES_EPISODE_ID_COUNTRY +
                    " = " + COUNTRIES_TABLE_NAME + "." + ScraperStore.Country.ID + ")";

    private static final String COUNTRIES_EPISODE_VIEW_INSERT_TRIGGER =
            "CREATE TRIGGER insert_countries_episode INSTEAD OF INSERT ON " + COUNTRIES_EPISODE_VIEW_NAME +
                    " BEGIN " +
                    "INSERT OR IGNORE INTO " + COUNTRIES_TABLE_NAME + " ( " + ScraperStore.Country.NAME + " ) " +
                    "VALUES (NEW." + ScraperStore.Episode.Country.NAME + "); " +
                    "INSERT INTO " + COUNTRIES_EPISODE_TABLE_NAME +
                    " ( " + COUNTRIES_EPISODE_ID_EPISODE + "," + COUNTRIES_EPISODE_ID_COUNTRY + " ) " +
                    "SELECT NEW." + ScraperStore.Episode.Country.EPISODE + ", " +
                    COUNTRIES_TABLE_NAME + "." + ScraperStore.Country.ID + " " +
                    " FROM " + COUNTRIES_TABLE_NAME +
                    " WHERE " + ScraperStore.Country.NAME + " = NEW." + ScraperStore.Episode.Country.NAME + "; " +
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

    private static final String TAGLINE_DELETABLE_VIEW_CREATE =
            "CREATE VIEW v_tagline_deletable AS " +
                    "SELECT _id FROM tagline " +
                    "LEFT JOIN taglines_movie ON taglines_movie.tagline_taglines=tagline._id " +
                    "LEFT JOIN taglines_show ON taglines_show.tagline_taglines=tagline._id " +
                    "LEFT JOIN taglines_episode ON taglines_episode.tagline_taglines=tagline._id " +
                    "WHERE coalesce(movie_taglines, show_taglines, episode_taglines) IS NULL";

    private static final String PRODUCER_DELETABLE_VIEW_CREATE =
            "CREATE VIEW v_producer_deletable AS " +
                    "SELECT _id FROM producer " +
                    "LEFT JOIN producers_movie ON producers_movie.producer_producers=producer._id " +
                    "LEFT JOIN producers_show ON producers_show.producer_producers=producer._id " +
                    "LEFT JOIN producers_episode ON producers_episode.producer_producers=producer._id " +
                    "WHERE coalesce(movie_producers, show_producers, episode_producers) IS NULL";

    private static final String SCREENPLAY_DELETABLE_VIEW_CREATE =
            "CREATE VIEW v_screenplay_deletable AS " +
                    "SELECT _id FROM screenplay " +
                    "LEFT JOIN screenplays_movie ON screenplays_movie.screenplay_screenplays=screenplay._id " +
                    "LEFT JOIN screenplays_show ON screenplays_show.screenplay_screenplays=screenplay._id " +
                    "LEFT JOIN screenplays_episode ON screenplays_episode.screenplay_screenplays=screenplay._id " +
                    "WHERE coalesce(movie_screenplays, show_screenplays, episode_screenplays) IS NULL";

    private static final String MUSICCOMPOSER_DELETABLE_VIEW_CREATE =
            "CREATE VIEW v_musiccomposer_deletable AS " +
                    "SELECT _id FROM musiccomposer " +
                    "LEFT JOIN musiccomposers_movie ON musiccomposers_movie.musiccomposer_musiccomposers=musiccomposer._id " +
                    "LEFT JOIN musiccomposers_show ON musiccomposers_show.musiccomposer_musiccomposers=musiccomposer._id " +
                    "LEFT JOIN musiccomposers_episode ON musiccomposers_episode.musiccomposer_musiccomposers=musiccomposer._id " +
                    "WHERE coalesce(movie_musiccomposers, show_musiccomposers, episode_musiccomposers) IS NULL";

    private static final String COUNTRY_DELETABLE_VIEW_CREATE =
            "CREATE VIEW v_country_deletable AS " +
                    "SELECT _id FROM country " +
                    "LEFT JOIN countries_movie ON countries_movie.country_countries=country._id " +
                    "LEFT JOIN countries_show ON countries_show.country_countries=country._id " +
                    "LEFT JOIN countries_episode ON countries_episode.country_countries=country._id " +
                    "WHERE coalesce(movie_countries, show_countries, episode_countries) IS NULL";

    private static final String SEASONPLOT_DELETABLE_VIEW_CREATE =
            "CREATE VIEW v_seasonplot_deletable AS " +
                    "SELECT _id FROM seasonplot " +
                    "LEFT JOIN seasonplots_show ON seasonplots_show.seasonplot_seasonplots=seasonplot._id " +
                    "WHERE coalesce(movie_seasonplots, show_seasonplots, episode_seasonplots) IS NULL";

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
    private static final String EPISODE_DELETE_TRIGGER_CREATE_v2 =
            "CREATE TRIGGER episode_delete AFTER DELETE ON episode " +
                    "BEGIN " +
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
            "delete from genre where _id in (select _id from v_genre_deletable); " +
            // set scraper type / id to -1 if something is refering this episode
            "UPDATE " + VideoOpenHelper.FILES_TABLE_NAME + " SET ArchosMediaScraper_id=-1, ArchosMediaScraper_type=-1 " +
            "WHERE ArchosMediaScraper_id = OLD._id AND ArchosMediaScraper_type = " + ScraperStore.SCRAPER_TYPE_MOVIE + ";" +
            "INSERT INTO delete_files(name,use_count) VALUES(OLD.cover_movie, (SELECT COUNT("
            + ScraperStore.Movie.COVER + ") FROM " + MOVIE_TABLE_NAME + "  WHERE " + ScraperStore.Movie.COVER
            + " = OLD.cover_movie));" +
            "END";
    private static final String MOVIE_DELETE_TRIGGER_CREATE_v2 =
            "CREATE TRIGGER movie_delete AFTER DELETE ON movie " +
                    "BEGIN " +
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
    public static final String VIEW_SHOW_TAGLINES = "v_show_taglines";
    private static final String CREATE_VIEW_SHOW_TAGLINES =
            "CREATE VIEW " + VIEW_SHOW_TAGLINES + " AS\n" +
                    "SELECT _id, group_concat( name_tagline, ', ' ) AS taglines\n" +
                    "  FROM  ( \n" +
                    "    SELECT show_taglines AS _id, name_tagline\n" +
                    "      FROM taglines_show\n" +
                    "           LEFT JOIN tagline\n" +
                    "                  ON ( tagline_taglines = _id ) \n" +
                    "     ORDER BY taglines_show.ROWID \n" +
                    ") \n" +
                    " GROUP BY _id";
    public static final String VIEW_SHOW_PRODUCERS = "v_show_producers";
    private static final String CREATE_VIEW_SHOW_PRODUCERS =
            "CREATE VIEW " + VIEW_SHOW_PRODUCERS + " AS\n" +
                    "SELECT _id, group_concat( name_producer, ', ' ) AS producers\n" +
                    "  FROM  ( \n" +
                    "    SELECT show_producers AS _id, name_producer\n" +
                    "      FROM producers_show\n" +
                    "           LEFT JOIN producer\n" +
                    "                  ON ( producer_producers = _id ) \n" +
                    "     ORDER BY producers_show.ROWID \n" +
                    ") \n" +
                    " GROUP BY _id";
    public static final String VIEW_SHOW_SCREENPLAYS = "v_show_screenplays";
    private static final String CREATE_VIEW_SHOW_SCREENPLAYS =
            "CREATE VIEW " + VIEW_SHOW_SCREENPLAYS + " AS\n" +
                    "SELECT _id, group_concat( name_screenplay, ', ' ) AS screenplays\n" +
                    "  FROM  ( \n" +
                    "    SELECT show_screenplays AS _id, name_screenplay\n" +
                    "      FROM screenplays_show\n" +
                    "           LEFT JOIN screenplay\n" +
                    "                  ON ( screenplay_screenplays = _id ) \n" +
                    "     ORDER BY screenplays_show.ROWID \n" +
                    ") \n" +
                    " GROUP BY _id";

    public static final String VIEW_SHOW_MUSICCOMPOSERS = "v_show_musiccomposers";
    private static final String CREATE_VIEW_SHOW_MUSICCOMPOSERS =
            "CREATE VIEW " + VIEW_SHOW_MUSICCOMPOSERS + " AS\n" +
                    "SELECT _id, group_concat( name_musiccomposer, ', ' ) AS musiccomposers\n" +
                    "  FROM  ( \n" +
                    "    SELECT show_musiccomposers AS _id, name_musiccomposer\n" +
                    "      FROM musiccomposers_show\n" +
                    "           LEFT JOIN musiccomposer\n" +
                    "                  ON ( musiccomposer_musiccomposers = _id ) \n" +
                    "     ORDER BY musiccomposers_show.ROWID \n" +
                    ") \n" +
                    " GROUP BY _id";

    public static final String VIEW_SHOW_COUNTRIES = "v_show_countries";
    private static final String CREATE_VIEW_SHOW_COUNTRIES =
            "CREATE VIEW " + VIEW_SHOW_COUNTRIES + " AS\n" +
                    "SELECT _id, group_concat( name_country, ', ' ) AS countries\n" +
                    "  FROM  ( \n" +
                    "    SELECT show_countries AS _id, name_country\n" +
                    "      FROM countries_show\n" +
                    "           LEFT JOIN country\n" +
                    "                  ON ( country_countries = _id ) \n" +
                    "     ORDER BY countries_show.ROWID \n" +
                    ") \n" +
                    " GROUP BY _id";

    public static final String VIEW_SHOW_SEASONPLOTS = "v_show_seasonplots";
    private static final String CREATE_VIEW_SHOW_SEASONPLOTS =
            "CREATE VIEW " + VIEW_SHOW_SEASONPLOTS + " AS\n" +
                    "SELECT _id, group_concat( name_seasonplot, ', ' ) AS seasonplots\n" +
                    "  FROM  ( \n" +
                    "    SELECT show_seasonplots AS _id, name_seasonplot\n" +
                    "      FROM seasonplots_show\n" +
                    "           LEFT JOIN seasonplot\n" +
                    "                  ON ( seasonplot_seasonplots = _id ) \n" +
                    "     ORDER BY seasonplots_show.ROWID \n" +
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


    public static final String VIEW_EPISODE_TAGLINES = "v_episode_taglines";
    private static final String CREATE_VIEW_EPISODE_TAGLINES =
            "CREATE VIEW " + VIEW_EPISODE_TAGLINES + " AS\n" +
                    "SELECT _id, group_concat( name_tagline, ', ' ) AS taglines\n" +
                    "  FROM  ( \n" +
                    "    SELECT episode_taglines AS _id, name_tagline\n" +
                    "      FROM taglines_episode\n" +
                    "           LEFT JOIN tagline\n" +
                    "                  ON ( tagline_taglines = _id ) \n" +
                    "     ORDER BY taglines_episode.ROWID \n" +
                    ") \n" +
                    " GROUP BY _id";
    public static final String VIEW_MOVIE_TAGLINES = "v_movie_taglines";
    private static final String CREATE_VIEW_MOVIE_TAGLINES =
            "CREATE VIEW " + VIEW_MOVIE_TAGLINES + " AS\n" +
                    "SELECT _id, group_concat( name_tagline, ', ' ) AS taglines\n" +
                    "  FROM  ( \n" +
                    "    SELECT movie_taglines AS _id, name_tagline\n" +
                    "      FROM taglines_movie\n" +
                    "           LEFT JOIN tagline\n" +
                    "                  ON ( tagline_taglines = _id ) \n" +
                    "     ORDER BY taglines_movie.ROWID \n" +
                    ") \n" +
                    " GROUP BY _id";


    public static final String VIEW_EPISODE_PRODUCERS = "v_episode_producers";
    private static final String CREATE_VIEW_EPISODE_PRODUCERS =
            "CREATE VIEW " + VIEW_EPISODE_PRODUCERS + " AS\n" +
                    "SELECT _id, group_concat( name_producer, ', ' ) AS producers\n" +
                    "  FROM  ( \n" +
                    "    SELECT episode_producers AS _id, name_producer\n" +
                    "      FROM producers_episode\n" +
                    "           LEFT JOIN producer\n" +
                    "                  ON ( producer_producers = _id ) \n" +
                    "     ORDER BY producers_episode.ROWID \n" +
                    ") \n" +
                    " GROUP BY _id";
    public static final String VIEW_MOVIE_PRODUCERS = "v_movie_producers";
    private static final String CREATE_VIEW_MOVIE_PRODUCERS =
            "CREATE VIEW " + VIEW_MOVIE_PRODUCERS + " AS\n" +
                    "SELECT _id, group_concat( name_producer, ', ' ) AS producers\n" +
                    "  FROM  ( \n" +
                    "    SELECT movie_producers AS _id, name_producer\n" +
                    "      FROM producers_movie\n" +
                    "           LEFT JOIN producer\n" +
                    "                  ON ( producer_producers = _id ) \n" +
                    "     ORDER BY producers_movie.ROWID \n" +
                    ") \n" +
                    " GROUP BY _id";

    public static final String VIEW_EPISODE_SCREENPLAYS = "v_episode_screenplays";
    private static final String CREATE_VIEW_EPISODE_SCREENPLAYS =
            "CREATE VIEW " + VIEW_EPISODE_SCREENPLAYS + " AS\n" +
                    "SELECT _id, group_concat( name_screenplay, ', ' ) AS screenplays\n" +
                    "  FROM  ( \n" +
                    "    SELECT episode_screenplays AS _id, name_screenplay\n" +
                    "      FROM screenplays_episode\n" +
                    "           LEFT JOIN screenplay\n" +
                    "                  ON ( screenplay_screenplays = _id ) \n" +
                    "     ORDER BY screenplays_episode.ROWID \n" +
                    ") \n" +
                    " GROUP BY _id";
    public static final String VIEW_MOVIE_SCREENPLAYS = "v_movie_screenplays";
    private static final String CREATE_VIEW_MOVIE_SCREENPLAYS =
            "CREATE VIEW " + VIEW_MOVIE_SCREENPLAYS + " AS\n" +
                    "SELECT _id, group_concat( name_screenplay, ', ' ) AS screenplays\n" +
                    "  FROM  ( \n" +
                    "    SELECT movie_screenplays AS _id, name_screenplay\n" +
                    "      FROM screenplays_movie\n" +
                    "           LEFT JOIN screenplay\n" +
                    "                  ON ( screenplay_screenplays = _id ) \n" +
                    "     ORDER BY screenplays_movie.ROWID \n" +
                    ") \n" +
                    " GROUP BY _id";

    public static final String VIEW_EPISODE_MUSICCOMPOSERS = "v_episode_musiccomposers";
    private static final String CREATE_VIEW_EPISODE_MUSICCOMPOSERS =
            "CREATE VIEW " + VIEW_EPISODE_MUSICCOMPOSERS + " AS\n" +
                    "SELECT _id, group_concat( name_musiccomposer, ', ' ) AS musiccomposers\n" +
                    "  FROM  ( \n" +
                    "    SELECT episode_musiccomposers AS _id, name_musiccomposer\n" +
                    "      FROM musiccomposers_episode\n" +
                    "           LEFT JOIN musiccomposer\n" +
                    "                  ON ( musiccomposer_musiccomposers = _id ) \n" +
                    "     ORDER BY musiccomposers_episode.ROWID \n" +
                    ") \n" +
                    " GROUP BY _id";
    public static final String VIEW_MOVIE_MUSICCOMPOSERS = "v_movie_musiccomposers";
    private static final String CREATE_VIEW_MOVIE_MUSICCOMPOSERS =
            "CREATE VIEW " + VIEW_MOVIE_MUSICCOMPOSERS + " AS\n" +
                    "SELECT _id, group_concat( name_musiccomposer, ', ' ) AS musiccomposers\n" +
                    "  FROM  ( \n" +
                    "    SELECT movie_musiccomposers AS _id, name_musiccomposer\n" +
                    "      FROM musiccomposers_movie\n" +
                    "           LEFT JOIN musiccomposer\n" +
                    "                  ON ( musiccomposer_musiccomposers = _id ) \n" +
                    "     ORDER BY musiccomposers_movie.ROWID \n" +
                    ") \n" +
                    " GROUP BY _id";

    public static final String VIEW_EPISODE_COUNTRIES = "v_episode_countries";
    private static final String CREATE_VIEW_EPISODE_COUNTRIES =
            "CREATE VIEW " + VIEW_EPISODE_COUNTRIES + " AS\n" +
                    "SELECT _id, group_concat( name_country, ', ' ) AS countries\n" +
                    "  FROM  ( \n" +
                    "    SELECT episode_countries AS _id, name_country\n" +
                    "      FROM countries_episode\n" +
                    "           LEFT JOIN country\n" +
                    "                  ON ( country_countries = _id ) \n" +
                    "     ORDER BY countries_episode.ROWID \n" +
                    ") \n" +
                    " GROUP BY _id";
    public static final String VIEW_MOVIE_COUNTRIES = "v_movie_countries";
    private static final String CREATE_VIEW_MOVIE_COUNTRIES =
            "CREATE VIEW " + VIEW_MOVIE_COUNTRIES + " AS\n" +
                    "SELECT _id, group_concat( name_country, ', ' ) AS countries\n" +
                    "  FROM  ( \n" +
                    "    SELECT movie_countries AS _id, name_country\n" +
                    "      FROM countries_movie\n" +
                    "           LEFT JOIN country\n" +
                    "                  ON ( country_countries = _id ) \n" +
                    "     ORDER BY countries_movie.ROWID \n" +
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
    public static final String SHOW_NETWORKLOGOS_TABLE_NAME = "show_networklogos";
    public static final String SHOW_ACTORPHOTOS_TABLE_NAME = "show_actorphotos";

    public static final String MOVIE_ACTORPHOTOS_TABLE_NAME = "movie_actorphotos";
    public static final String MOVIE_STUDIOLOGOS_TABLE_NAME = "movie_studiologos";
    public static final String MOVIE_CLEARLOGOS_TABLE_NAME = "movie_clearlogos";

    public static final String SHOW_CLEARLOGOS_TABLE_NAME = "show_clearlogos";
    public static final String SHOW_STUDIOLOGOS_TABLE_NAME = "show_studiologos";
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

    private static final String CREATE_SHOW_NETWORKLOGOS_TABLE =
            "CREATE TABLE " + SHOW_NETWORKLOGOS_TABLE_NAME + " ( \n" +
                    "    _id             INTEGER PRIMARY KEY,\n" +
                    "    show_id         INTEGER REFERENCES show ( _id ) ON DELETE CASCADE\n" +
                    "                                                    ON UPDATE CASCADE,\n" +
                    "    s_nl_thumb_url  TEXT,\n" +
                    "    s_nl_thumb_file TEXT,\n" +
                    "    s_nl_large_url  TEXT,\n" +
                    "    s_nl_large_file TEXT \n" +
                    ")";

    private static final String CREATE_SHOW_ACTORPHOTOS_TABLE =
            "CREATE TABLE " + SHOW_ACTORPHOTOS_TABLE_NAME + " ( \n" +
                    "    _id             INTEGER PRIMARY KEY,\n" +
                    "    show_id         INTEGER REFERENCES show ( _id ) ON DELETE CASCADE\n" +
                    "                                                    ON UPDATE CASCADE,\n" +
                    "    s_ap_thumb_url  TEXT,\n" +
                    "    s_ap_thumb_file TEXT,\n" +
                    "    s_ap_large_url  TEXT,\n" +
                    "    s_ap_large_file TEXT \n" +
                    ")";

    private static final String CREATE_MOVIE_ACTORPHOTOS_TABLE =
            "CREATE TABLE " + MOVIE_ACTORPHOTOS_TABLE_NAME + " ( \n" +
                    "    _id             INTEGER PRIMARY KEY,\n" +
                    "    movie_id         INTEGER REFERENCES movie ( _id ) ON DELETE CASCADE\n" +
                    "                                                    ON UPDATE CASCADE,\n" +
                    "    m_ap_thumb_url  TEXT,\n" +
                    "    m_ap_thumb_file TEXT,\n" +
                    "    m_ap_large_url  TEXT,\n" +
                    "    m_ap_large_file TEXT \n" +
                    ")";

    private static final String CREATE_MOVIE_STUDIOLOGOS_TABLE =
            "CREATE TABLE " + MOVIE_STUDIOLOGOS_TABLE_NAME + " ( \n" +
                    "    _id             INTEGER PRIMARY KEY,\n" +
                    "    movie_id         INTEGER REFERENCES movie ( _id ) ON DELETE CASCADE\n" +
                    "                                                    ON UPDATE CASCADE,\n" +
                    "    m_sl_thumb_url  TEXT,\n" +
                    "    m_sl_thumb_file TEXT,\n" +
                    "    m_sl_large_url  TEXT,\n" +
                    "    m_sl_large_file TEXT \n" +
                    ")";

    private static final String CREATE_MOVIE_CLEARLOGOS_TABLE =
            "CREATE TABLE " + MOVIE_CLEARLOGOS_TABLE_NAME + " ( \n" +
                    "    _id             INTEGER PRIMARY KEY,\n" +
                    "    movie_id         INTEGER REFERENCES movie ( _id ) ON DELETE CASCADE\n" +
                    "                                                    ON UPDATE CASCADE,\n" +
                    "    m_cl_thumb_url  TEXT,\n" +
                    "    m_cl_thumb_file TEXT,\n" +
                    "    m_cl_large_url  TEXT,\n" +
                    "    m_cl_large_file TEXT \n" +
                    ")";

    private static final String CREATE_SHOW_CLEARLOGOS_TABLE =
            "CREATE TABLE " + SHOW_CLEARLOGOS_TABLE_NAME + " ( \n" +
                    "    _id             INTEGER PRIMARY KEY,\n" +
                    "    show_id         INTEGER REFERENCES show ( _id ) ON DELETE CASCADE\n" +
                    "                                                    ON UPDATE CASCADE,\n" +
                    "    s_cl_thumb_url  TEXT,\n" +
                    "    s_cl_thumb_file TEXT,\n" +
                    "    s_cl_large_url  TEXT,\n" +
                    "    s_cl_large_file TEXT \n" +
                    ")";

    private static final String CREATE_SHOW_STUDIOLOGOS_TABLE =
            "CREATE TABLE " + SHOW_STUDIOLOGOS_TABLE_NAME + " ( \n" +
                    "    _id             INTEGER PRIMARY KEY,\n" +
                    "    show_id         INTEGER REFERENCES show ( _id ) ON DELETE CASCADE\n" +
                    "                                                    ON UPDATE CASCADE,\n" +
                    "    s_sl_thumb_url  TEXT,\n" +
                    "    s_sl_thumb_file TEXT,\n" +
                    "    s_sl_large_url  TEXT,\n" +
                    "    s_sl_large_file TEXT \n" +
                    ")";


    private static final String CREATE_SHOW_BACKDROPS_DELETE_TRIGGER =
            "CREATE TRIGGER " + SHOW_BACKDROPS_TABLE_NAME + "_delete\n" +
            "       AFTER DELETE ON " + SHOW_BACKDROPS_TABLE_NAME + "\n" +
            "BEGIN\n" +
            "    INSERT INTO delete_files(name) VALUES ( OLD.s_bd_large_file );\n"
            + "    INSERT INTO delete_files(name) VALUES ( OLD.s_bd_thumb_file );\n" +
            "END";


    private static final String CREATE_SHOW_NETWORKLOGOS_DELETE_TRIGGER =
            "CREATE TRIGGER " + SHOW_NETWORKLOGOS_TABLE_NAME + "_delete\n" +
                    "       AFTER DELETE ON " + SHOW_NETWORKLOGOS_TABLE_NAME + "\n" +
                    "BEGIN\n" +
                    "    INSERT INTO delete_files(name) VALUES ( OLD.s_nl_large_file );\n"
                    + "    INSERT INTO delete_files(name) VALUES ( OLD.s_nl_thumb_file );\n" +
                    "END";

    private static final String CREATE_SHOW_ACTORPHOTOS_DELETE_TRIGGER =
            "CREATE TRIGGER " + SHOW_ACTORPHOTOS_TABLE_NAME + "_delete\n" +
                    "       AFTER DELETE ON " + SHOW_ACTORPHOTOS_TABLE_NAME + "\n" +
                    "BEGIN\n" +
                    "    INSERT INTO delete_files(name) VALUES ( OLD.s_ap_large_file );\n"
                    + "    INSERT INTO delete_files(name) VALUES ( OLD.s_ap_thumb_file );\n" +
                    "END";

    private static final String CREATE_MOVIE_ACTORPHOTOS_DELETE_TRIGGER =
            "CREATE TRIGGER " + MOVIE_ACTORPHOTOS_TABLE_NAME + "_delete\n" +
                    "       AFTER DELETE ON " + MOVIE_ACTORPHOTOS_TABLE_NAME + "\n" +
                    "BEGIN\n" +
                    "    INSERT INTO delete_files(name) VALUES ( OLD.m_ap_large_file );\n"
                    + "    INSERT INTO delete_files(name) VALUES ( OLD.m_ap_thumb_file );\n" +
                    "END";

    private static final String CREATE_MOVIE_STUDIOLOGOS_DELETE_TRIGGER =
            "CREATE TRIGGER " + MOVIE_STUDIOLOGOS_TABLE_NAME + "_delete\n" +
                    "       AFTER DELETE ON " + MOVIE_STUDIOLOGOS_TABLE_NAME + "\n" +
                    "BEGIN\n" +
                    "    INSERT INTO delete_files(name) VALUES ( OLD.m_sl_large_file );\n"
                    + "    INSERT INTO delete_files(name) VALUES ( OLD.m_sl_thumb_file );\n" +
                    "END";

    private static final String CREATE_MOVIE_CLEARLOGOS_DELETE_TRIGGER =
            "CREATE TRIGGER " + MOVIE_CLEARLOGOS_TABLE_NAME + "_delete\n" +
                    "       AFTER DELETE ON " + MOVIE_CLEARLOGOS_TABLE_NAME + "\n" +
                    "BEGIN\n" +
                    "    INSERT INTO delete_files(name) VALUES ( OLD.m_cl_large_file );\n"
                    + "    INSERT INTO delete_files(name) VALUES ( OLD.m_cl_thumb_file );\n" +
                    "END";

    private static final String CREATE_SHOW_CLEARLOGOS_DELETE_TRIGGER =
            "CREATE TRIGGER " + SHOW_CLEARLOGOS_TABLE_NAME + "_delete\n" +
                    "       AFTER DELETE ON " + SHOW_CLEARLOGOS_TABLE_NAME + "\n" +
                    "BEGIN\n" +
                    "    INSERT INTO delete_files(name) VALUES ( OLD.s_cl_large_file );\n"
                    + "    INSERT INTO delete_files(name) VALUES ( OLD.s_cl_thumb_file );\n" +
                    "END";

    private static final String CREATE_SHOW_STUDIOLOGOS_DELETE_TRIGGER =
            "CREATE TRIGGER " + SHOW_STUDIOLOGOS_TABLE_NAME + "_delete\n" +
                    "       AFTER DELETE ON " + SHOW_STUDIOLOGOS_TABLE_NAME + "\n" +
                    "BEGIN\n" +
                    "    INSERT INTO delete_files(name) VALUES ( OLD.s_sl_large_file );\n"
                    + "    INSERT INTO delete_files(name) VALUES ( OLD.s_sl_thumb_file );\n" +
                    "END";

    private static final String DROP_SHOW_BACKDROPS_DELETE_TRIGGER =
            "DROP TRIGGER IF EXISTS " + SHOW_BACKDROPS_TABLE_NAME + "_delete";


    private static final String DROP_SHOW_NETWORKLOGOS_DELETE_TRIGGER =
            "DROP TRIGGER IF EXISTS " + SHOW_NETWORKLOGOS_TABLE_NAME + "_delete";

    private static final String DROP_SHOW_ACTORPHOTOS_DELETE_TRIGGER =
            "DROP TRIGGER IF EXISTS " + SHOW_ACTORPHOTOS_TABLE_NAME + "_delete";

    private static final String DROP_MOVIE_ACTORPHOTOS_DELETE_TRIGGER =
            "DROP TRIGGER IF EXISTS " + MOVIE_ACTORPHOTOS_TABLE_NAME + "_delete";

    private static final String DROP_MOVIE_STUDIOLOGOS_DELETE_TRIGGER =
            "DROP TRIGGER IF EXISTS " + MOVIE_STUDIOLOGOS_TABLE_NAME + "_delete";

    private static final String DROP_MOVIE_CLEARLOGOS_DELETE_TRIGGER =
            "DROP TRIGGER IF EXISTS " + MOVIE_CLEARLOGOS_TABLE_NAME + "_delete";

    private static final String DROP_SHOW_CLEARLOGOS_DELETE_TRIGGER =
            "DROP TRIGGER IF EXISTS " + SHOW_CLEARLOGOS_TABLE_NAME + "_delete";

    private static final String DROP_SHOW_STUDIOLOGOS_DELETE_TRIGGER =
            "DROP TRIGGER IF EXISTS " + SHOW_STUDIOLOGOS_TABLE_NAME + "_delete";


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
        db.execSQL(CREATE_SHOW_NETWORKLOGOS_TABLE);
        db.execSQL(CREATE_SHOW_NETWORKLOGOS_DELETE_TRIGGER);
        db.execSQL(CREATE_SHOW_ACTORPHOTOS_TABLE);
        db.execSQL(CREATE_SHOW_ACTORPHOTOS_DELETE_TRIGGER);
        db.execSQL(CREATE_MOVIE_ACTORPHOTOS_TABLE);
        db.execSQL(CREATE_MOVIE_ACTORPHOTOS_DELETE_TRIGGER);
        db.execSQL(CREATE_MOVIE_STUDIOLOGOS_TABLE);
        db.execSQL(CREATE_MOVIE_STUDIOLOGOS_DELETE_TRIGGER);
        db.execSQL(CREATE_MOVIE_CLEARLOGOS_TABLE);
        db.execSQL(CREATE_MOVIE_CLEARLOGOS_DELETE_TRIGGER);
        db.execSQL(CREATE_SHOW_CLEARLOGOS_TABLE);
        db.execSQL(CREATE_SHOW_CLEARLOGOS_DELETE_TRIGGER);
        db.execSQL(CREATE_SHOW_STUDIOLOGOS_TABLE);
        db.execSQL(CREATE_SHOW_STUDIOLOGOS_DELETE_TRIGGER);

        // V28
        db.execSQL(CREATE_MOVIE_TRAILERS_TABLE);
    }

    public static void upgradeTo(SQLiteDatabase db, int toVersion) {
        if (toVersion == 37) {
            log.debug("upgradeTo: " + toVersion);
            db.execSQL("ALTER TABLE " + MOVIE_TABLE_NAME + " ADD COLUMN " + VideoStore.Video.VideoColumns.NOVA_PINNED + " INTEGER DEFAULT (0)");
            db.execSQL("ALTER TABLE " + SHOW_TABLE_NAME + " ADD COLUMN " + VideoStore.Video.VideoColumns.NOVA_PINNED + " INTEGER DEFAULT (0)");
        }
        if (toVersion == 38) {
            log.debug("upgradeTo: " + toVersion);
            db.execSQL("ALTER TABLE " + MOVIE_TABLE_NAME + " ADD COLUMN " + VideoStore.Video.VideoColumns.SCRAPER_C_ID + " INTEGER DEFAULT (-1)");
            db.execSQL(CREATE_MOVIE_COLLECTION_TABLE);
        }
        if (toVersion == 39) {
            log.debug("upgradeTo: " + toVersion);
            // create indexes to every non foreign keys with delete to speed up huge batch of delete in files_scanned during directory moves on network shares
            // performance hit comes from the cascade of triggers
            // without index, each delete from master table requires search through entire child table for foreign key'd items in O(N)
            // with index it is much lower (O(1) or whatever the index achieves)
            log.debug("upgradeTo: creating indexes");
            db.execSQL("CREATE INDEX subtitles_idx ON subtitles(file_id)");
            db.execSQL("CREATE INDEX movie_trailers_idx ON movie_trailers(movie_id)");
            db.execSQL("CREATE INDEX movie_backdrops_idx ON movie_backdrops(movie_id)");
            db.execSQL("CREATE INDEX movie_posters_idx ON movie_posters(movie_id)");
            db.execSQL("CREATE INDEX show_backdrops_idx ON show_backdrops(show_id)");
            db.execSQL("CREATE INDEX show_networklogos_idx ON show_networklogos(show_id)");
            db.execSQL("CREATE INDEX show_actorphotos_idx ON show_actorphotos(show_id)");
            db.execSQL("CREATE INDEX movie_actorphotos_idx ON movie_actorphotos(movie_id)");
            db.execSQL("CREATE INDEX movie_studiologos_idx ON movie_studiologos(movie_id)");
            db.execSQL("CREATE INDEX movie_clearlogos_idx ON movie_clearlogos(movie_id)");
            db.execSQL("CREATE INDEX show_clearlogos_idx ON show_clearlogos(show_id)");
            db.execSQL("CREATE INDEX show_studiologos_idx ON show_studiologos(show_id)");
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
            // create new triggers that does not call each time a clean of v_.*_deletable tables: do it once at startup
            // for some reasons sometimes the triggers are not dropped, thus make sure it is deleted
            db.execSQL("pragma writable_schema = ON");
            log.debug("upgradeTo: removing trigger movie_delete");
            db.execSQL("delete from sqlite_master where name = 'movie_delete'");
            log.debug("upgradeTo: removing trigger show_delete");
            db.execSQL("delete from sqlite_master where name = 'show_delete'");
            log.debug("upgradeTo: removing trigger episode_delete");
            db.execSQL("delete from sqlite_master where name = 'episode_delete'");
            db.execSQL("pragma writable_schema = OFF");
            //db.execSQL(EPISODE_DELETE_TRIGGER_DROP);
            //db.execSQL(SHOW_DELETE_TRIGGER_DROP);
            //db.execSQL(MOVIE_DELETE_TRIGGER_DROP);
            log.debug("upgradeTo: creating episode_delete trigger " + EPISODE_DELETE_TRIGGER_CREATE_v2);
            db.execSQL(EPISODE_DELETE_TRIGGER_CREATE_v2);
            log.debug("upgradeTo: creating show_delete trigger " + SHOW_DELETE_TRIGGER_CREATE_v2);
            db.execSQL(SHOW_DELETE_TRIGGER_CREATE_v2);
            log.debug("upgradeTo: creating movie_delete trigger " + MOVIE_DELETE_TRIGGER_CREATE_v2);
            db.execSQL(MOVIE_DELETE_TRIGGER_CREATE_v2);
            log.debug("upgradeTo: all good");
        }
        if (toVersion == 40) {
            log.debug("upgradeTo: " + toVersion);
            db.execSQL("ALTER TABLE " + MOVIE_TABLE_NAME + " ADD COLUMN " + ScraperStore.Movie.WRITERS_FORMATTED + " TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE " + SHOW_TABLE_NAME + " ADD COLUMN " + ScraperStore.Show.WRITERS_FORMATTED + " TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE " + EPISODE_TABLE_NAME + " ADD COLUMN " + ScraperStore.Episode.WRITERS_FORMATTED + " TEXT DEFAULT ''");

            db.execSQL("ALTER TABLE " + MOVIE_TABLE_NAME + " ADD COLUMN " + ScraperStore.Movie.TAGLINES_FORMATTED + " TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE " + SHOW_TABLE_NAME + " ADD COLUMN " + ScraperStore.Show.TAGLINES_FORMATTED + " TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE " + EPISODE_TABLE_NAME + " ADD COLUMN " + ScraperStore.Episode.TAGLINES_FORMATTED + " TEXT DEFAULT ''");

            db.execSQL("ALTER TABLE " + MOVIE_TABLE_NAME + " ADD COLUMN " + ScraperStore.Movie.PRODUCERS_FORMATTED + " TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE " + SHOW_TABLE_NAME + " ADD COLUMN " + ScraperStore.Show.PRODUCERS_FORMATTED + " TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE " + EPISODE_TABLE_NAME + " ADD COLUMN " + ScraperStore.Episode.PRODUCERS_FORMATTED + " TEXT DEFAULT ''");

            db.execSQL("ALTER TABLE " + MOVIE_TABLE_NAME + " ADD COLUMN " + ScraperStore.Movie.SCREENPLAYS_FORMATTED + " TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE " + SHOW_TABLE_NAME + " ADD COLUMN " + ScraperStore.Show.SCREENPLAYS_FORMATTED + " TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE " + EPISODE_TABLE_NAME + " ADD COLUMN " + ScraperStore.Episode.SCREENPLAYS_FORMATTED + " TEXT DEFAULT ''");

            db.execSQL("ALTER TABLE " + MOVIE_TABLE_NAME + " ADD COLUMN " + ScraperStore.Movie.MUSICCOMPOSERS_FORMATTED + " TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE " + SHOW_TABLE_NAME + " ADD COLUMN " + ScraperStore.Show.MUSICCOMPOSERS_FORMATTED + " TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE " + EPISODE_TABLE_NAME + " ADD COLUMN " + ScraperStore.Episode.MUSICCOMPOSERS_FORMATTED + " TEXT DEFAULT ''");

            db.execSQL("ALTER TABLE " + MOVIE_TABLE_NAME + " ADD COLUMN " + ScraperStore.Movie.COUNTRIES_FORMATTED + " TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE " + SHOW_TABLE_NAME + " ADD COLUMN " + ScraperStore.Show.COUNTRIES_FORMATTED + " TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE " + EPISODE_TABLE_NAME + " ADD COLUMN " + ScraperStore.Episode.COUNTRIES_FORMATTED + " TEXT DEFAULT ''");

            db.execSQL("ALTER TABLE " + SHOW_TABLE_NAME + " ADD COLUMN " + ScraperStore.Show.SEASONPLOTS_FORMATTED + " TEXT DEFAULT ''");

            db.execSQL(WRITERS_TABLE_CREATE);
            db.execSQL(WRITERS_MOVIE_TABLE_CREATE);
            db.execSQL(WRITERS_EPISODE_TABLE_CREATE);
            db.execSQL(WRITERS_SHOW_TABLE_CREATE);

            db.execSQL(TAGLINES_TABLE_CREATE);
            db.execSQL(TAGLINES_MOVIE_TABLE_CREATE);
            db.execSQL(TAGLINES_EPISODE_TABLE_CREATE);
            db.execSQL(TAGLINES_SHOW_TABLE_CREATE);

            db.execSQL(PRODUCERS_TABLE_CREATE);
            db.execSQL(PRODUCERS_MOVIE_TABLE_CREATE);
            db.execSQL(PRODUCERS_EPISODE_TABLE_CREATE);
            db.execSQL(PRODUCERS_SHOW_TABLE_CREATE);

            db.execSQL(SCREENPLAYS_TABLE_CREATE);
            db.execSQL(SCREENPLAYS_MOVIE_TABLE_CREATE);
            db.execSQL(SCREENPLAYS_EPISODE_TABLE_CREATE);
            db.execSQL(SCREENPLAYS_SHOW_TABLE_CREATE);

            db.execSQL(MUSICCOMPOSERS_TABLE_CREATE);
            db.execSQL(MUSICCOMPOSERS_MOVIE_TABLE_CREATE);
            db.execSQL(MUSICCOMPOSERS_EPISODE_TABLE_CREATE);
            db.execSQL(MUSICCOMPOSERS_SHOW_TABLE_CREATE);

            db.execSQL(COUNTRIES_TABLE_CREATE);
            db.execSQL(COUNTRIES_MOVIE_TABLE_CREATE);
            db.execSQL(COUNTRIES_EPISODE_TABLE_CREATE);
            db.execSQL(COUNTRIES_SHOW_TABLE_CREATE);

            db.execSQL(SEASONPLOTS_TABLE_CREATE);
            db.execSQL(SEASONPLOTS_SHOW_TABLE_CREATE);
            db.execSQL(SEASONPLOTS_SHOW_VIEW_CREATE);
            db.execSQL(SEASONPLOTS_SHOW_VIEW_INSERT_TRIGGER);
            db.execSQL(SEASONPLOT_DELETABLE_VIEW_CREATE);
            db.execSQL(CREATE_VIEW_SHOW_SEASONPLOTS);

            db.execSQL(WRITERS_MOVIE_VIEW_CREATE);
            db.execSQL(WRITERS_SHOW_VIEW_CREATE);
            db.execSQL(WRITERS_EPISODE_VIEW_CREATE);
            db.execSQL(WRITERS_MOVIE_VIEW_INSERT_TRIGGER);
            db.execSQL(WRITERS_SHOW_VIEW_INSERT_TRIGGER);
            db.execSQL(WRITERS_EPISODE_VIEW_INSERT_TRIGGER);
            db.execSQL(WRITER_DELETABLE_VIEW_CREATE);

            db.execSQL(TAGLINES_MOVIE_VIEW_CREATE);
            db.execSQL(TAGLINES_SHOW_VIEW_CREATE);
            db.execSQL(TAGLINES_EPISODE_VIEW_CREATE);
            db.execSQL(TAGLINES_MOVIE_VIEW_INSERT_TRIGGER);
            db.execSQL(TAGLINES_SHOW_VIEW_INSERT_TRIGGER);
            db.execSQL(TAGLINES_EPISODE_VIEW_INSERT_TRIGGER);
            db.execSQL(TAGLINE_DELETABLE_VIEW_CREATE);

            db.execSQL(PRODUCERS_MOVIE_VIEW_CREATE);
            db.execSQL(PRODUCERS_SHOW_VIEW_CREATE);
            db.execSQL(PRODUCERS_EPISODE_VIEW_CREATE);
            db.execSQL(PRODUCERS_MOVIE_VIEW_INSERT_TRIGGER);
            db.execSQL(PRODUCERS_SHOW_VIEW_INSERT_TRIGGER);
            db.execSQL(PRODUCERS_EPISODE_VIEW_INSERT_TRIGGER);
            db.execSQL(PRODUCER_DELETABLE_VIEW_CREATE);

            db.execSQL(SCREENPLAYS_MOVIE_VIEW_CREATE);
            db.execSQL(SCREENPLAYS_SHOW_VIEW_CREATE);
            db.execSQL(SCREENPLAYS_EPISODE_VIEW_CREATE);
            db.execSQL(SCREENPLAYS_MOVIE_VIEW_INSERT_TRIGGER);
            db.execSQL(SCREENPLAYS_SHOW_VIEW_INSERT_TRIGGER);
            db.execSQL(SCREENPLAYS_EPISODE_VIEW_INSERT_TRIGGER);
            db.execSQL(SCREENPLAY_DELETABLE_VIEW_CREATE);

            db.execSQL(MUSICCOMPOSERS_MOVIE_VIEW_CREATE);
            db.execSQL(MUSICCOMPOSERS_SHOW_VIEW_CREATE);
            db.execSQL(MUSICCOMPOSERS_EPISODE_VIEW_CREATE);
            db.execSQL(MUSICCOMPOSERS_MOVIE_VIEW_INSERT_TRIGGER);
            db.execSQL(MUSICCOMPOSERS_SHOW_VIEW_INSERT_TRIGGER);
            db.execSQL(MUSICCOMPOSERS_EPISODE_VIEW_INSERT_TRIGGER);
            db.execSQL(MUSICCOMPOSER_DELETABLE_VIEW_CREATE);

            db.execSQL(COUNTRIES_MOVIE_VIEW_CREATE);
            db.execSQL(COUNTRIES_SHOW_VIEW_CREATE);
            db.execSQL(COUNTRIES_EPISODE_VIEW_CREATE);
            db.execSQL(COUNTRIES_MOVIE_VIEW_INSERT_TRIGGER);
            db.execSQL(COUNTRIES_SHOW_VIEW_INSERT_TRIGGER);
            db.execSQL(COUNTRIES_EPISODE_VIEW_INSERT_TRIGGER);
            db.execSQL(COUNTRY_DELETABLE_VIEW_CREATE);

            db.execSQL(CREATE_VIEW_SHOW_WRITERS);
            db.execSQL(CREATE_VIEW_EPISODE_WRITERS);
            db.execSQL(CREATE_VIEW_MOVIE_WRITERS);

            db.execSQL(CREATE_VIEW_SHOW_TAGLINES);
            db.execSQL(CREATE_VIEW_EPISODE_TAGLINES);
            db.execSQL(CREATE_VIEW_MOVIE_TAGLINES);

            db.execSQL(CREATE_VIEW_SHOW_PRODUCERS);
            db.execSQL(CREATE_VIEW_EPISODE_PRODUCERS);
            db.execSQL(CREATE_VIEW_MOVIE_PRODUCERS);

            db.execSQL(CREATE_VIEW_SHOW_SCREENPLAYS);
            db.execSQL(CREATE_VIEW_EPISODE_SCREENPLAYS);
            db.execSQL(CREATE_VIEW_MOVIE_SCREENPLAYS);

            db.execSQL(CREATE_VIEW_SHOW_MUSICCOMPOSERS);
            db.execSQL(CREATE_VIEW_EPISODE_MUSICCOMPOSERS);
            db.execSQL(CREATE_VIEW_MOVIE_MUSICCOMPOSERS);

            db.execSQL(CREATE_VIEW_SHOW_COUNTRIES);
            db.execSQL(CREATE_VIEW_EPISODE_COUNTRIES);
            db.execSQL(CREATE_VIEW_MOVIE_COUNTRIES);

            log.debug("upgradeTo: creating indexes");
            // cf. v39 migration create indexes to speed up rescan in case of delete/renames
            db.execSQL("CREATE INDEX WRITERS_MOVIE_idx ON WRITERS_MOVIE(writer_writers)");
            db.execSQL("CREATE INDEX WRITERS_EPISODE_idx ON WRITERS_EPISODE(writer_writers)");
            db.execSQL("CREATE INDEX WRITERS_SHOW_idx ON WRITERS_SHOW(writer_writers)");

            db.execSQL("CREATE INDEX TAGLINES_MOVIE_idx ON TAGLINES_MOVIE(tagline_taglines)");
            db.execSQL("CREATE INDEX TAGLINES_EPISODE_idx ON TAGLINES_EPISODE(tagline_taglines)");
            db.execSQL("CREATE INDEX TAGLINES_SHOW_idx ON TAGLINES_SHOW(tagline_taglines)");

            db.execSQL("CREATE INDEX PRODUCERS_MOVIE_idx ON PRODUCERS_MOVIE(producer_producers)");
            db.execSQL("CREATE INDEX PRODUCERS_EPISODE_idx ON PRODUCERS_EPISODE(producer_producers)");
            db.execSQL("CREATE INDEX PRODUCERS_SHOW_idx ON PRODUCERS_SHOW(producer_producers)");

            db.execSQL("CREATE INDEX SCREENPLAYS_MOVIE_idx ON SCREENPLAYS_MOVIE(screenplay_screenplays)");
            db.execSQL("CREATE INDEX SCREENPLAYS_EPISODE_idx ON SCREENPLAYS_EPISODE(screenplay_screenplays)");
            db.execSQL("CREATE INDEX SCREENPLAYS_SHOW_idx ON SCREENPLAYS_SHOW(screenplay_screenplays)");

            db.execSQL("CREATE INDEX MUSICCOMPOSERS_MOVIE_idx ON MUSICCOMPOSERS_MOVIE(musiccomposer_musiccomposers)");
            db.execSQL("CREATE INDEX MUSICCOMPOSERS_EPISODE_idx ON MUSICCOMPOSERS_EPISODE(musiccomposer_musiccomposers)");
            db.execSQL("CREATE INDEX MUSICCOMPOSERS_SHOW_idx ON MUSICCOMPOSERS_SHOW(musiccomposer_musiccomposers)");

            db.execSQL("CREATE INDEX COUNTRIES_MOVIE_idx ON COUNTRIES_MOVIE(country_countries)");
            db.execSQL("CREATE INDEX COUNTRIES_EPISODE_idx ON COUNTRIES_EPISODE(country_countries)");
            db.execSQL("CREATE INDEX COUNTRIES_SHOW_idx ON COUNTRIES_SHOW(country_countries)");

            db.execSQL("CREATE INDEX SEASONPLOTS_SHOW_idx ON SEASONPLOTS_SHOW(seasonplot_seasonplots)");
        }
    }
}
