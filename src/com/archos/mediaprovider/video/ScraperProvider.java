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

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.BaseColumns;
import android.util.Log;

import com.archos.medialib.R;
import com.archos.mediaprovider.DbHolder;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.ScraperImage;
import com.archos.mediascraper.ScraperImage.Type;
import com.archos.mediascraper.db.ScraperCursorFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Content provider for the scraper database
 */
public class ScraperProvider extends ContentProvider {
    private static final String TAG = "ScraperProvider";
    private static final boolean DBG = false;

    // using offset to avoid collision with mediaprovider's matcher
    public static final int SCRAPER_PROVIDER_OFFSET = 10000;

    private static final int MOVIE = SCRAPER_PROVIDER_OFFSET + 30;
    private static final int MOVIE_ID = SCRAPER_PROVIDER_OFFSET + 31;
    private static final int MOVIE_ALL_INFOS = SCRAPER_PROVIDER_OFFSET + 33;
    private static final int MOVIE_ALL = SCRAPER_PROVIDER_OFFSET + 34;

    private static final int SHOW = SCRAPER_PROVIDER_OFFSET + 40;
    private static final int SHOW_ID = SCRAPER_PROVIDER_OFFSET + 41;
    private static final int SHOW_NAME = SCRAPER_PROVIDER_OFFSET + 42;
    private static final int SHOW_ALL_INFOS = SCRAPER_PROVIDER_OFFSET + 43;
    private static final int SHOW_ALL = SCRAPER_PROVIDER_OFFSET + 44;
    private static final int SHOW_ONLINE_ID = SCRAPER_PROVIDER_OFFSET + 45;

    private static final int EPISODE = SCRAPER_PROVIDER_OFFSET + 50;
    private static final int EPISODE_ID = SCRAPER_PROVIDER_OFFSET + 51;
    private static final int EPISODE_ALL_INFOS = SCRAPER_PROVIDER_OFFSET + 53;
    private static final int EPISODE_ALL_SEASONS = SCRAPER_PROVIDER_OFFSET + 54;
    private static final int EPISODE_SHOW = SCRAPER_PROVIDER_OFFSET + 55;

    private static final int ACTOR = SCRAPER_PROVIDER_OFFSET + 60;
    private static final int ACTOR_ID = SCRAPER_PROVIDER_OFFSET + 61;
    private static final int ACTOR_ALL = SCRAPER_PROVIDER_OFFSET + 62;
    private static final int ACTOR_MOVIE = SCRAPER_PROVIDER_OFFSET + 63;
    private static final int ACTOR_SHOW = SCRAPER_PROVIDER_OFFSET + 64;
    private static final int ACTOR_EPISODE = SCRAPER_PROVIDER_OFFSET + 65;
    private static final int ACTOR_NAME = SCRAPER_PROVIDER_OFFSET + 66;

    private static final int GENRE = SCRAPER_PROVIDER_OFFSET + 70;
    private static final int GENRE_ID = SCRAPER_PROVIDER_OFFSET + 71;
    private static final int GENRE_ALL = SCRAPER_PROVIDER_OFFSET + 72;
    private static final int GENRE_MOVIE = SCRAPER_PROVIDER_OFFSET + 73;
    private static final int GENRE_SHOW = SCRAPER_PROVIDER_OFFSET + 74;
    private static final int GENRE_NAME = SCRAPER_PROVIDER_OFFSET + 75;

    private static final int DIRECTOR = SCRAPER_PROVIDER_OFFSET + 80;
    private static final int DIRECTOR_ID = SCRAPER_PROVIDER_OFFSET + 81;
    private static final int DIRECTOR_ALL = SCRAPER_PROVIDER_OFFSET + 82;
    private static final int DIRECTOR_MOVIE = SCRAPER_PROVIDER_OFFSET + 83;
    private static final int DIRECTOR_SHOW = SCRAPER_PROVIDER_OFFSET + 84;
    private static final int DIRECTOR_EPISODE = SCRAPER_PROVIDER_OFFSET + 85;
    private static final int DIRECTOR_NAME = SCRAPER_PROVIDER_OFFSET + 86;

    private static final int STUDIO = SCRAPER_PROVIDER_OFFSET + 90;
    private static final int STUDIO_ID = SCRAPER_PROVIDER_OFFSET + 91;
    private static final int STUDIO_ALL = SCRAPER_PROVIDER_OFFSET + 92;
    private static final int STUDIO_MOVIE = SCRAPER_PROVIDER_OFFSET + 93;
    private static final int STUDIO_SHOW = SCRAPER_PROVIDER_OFFSET + 94;
    private static final int STUDIO_NAME = SCRAPER_PROVIDER_OFFSET + 95;

    private static final int EPISODESHOWCOMBINED_ALL = SCRAPER_PROVIDER_OFFSET + 100;
    private static final int EPISODESHOWCOMBINED_ID = SCRAPER_PROVIDER_OFFSET + 101;

    private static final int ALL_VIDEOS_ALL = SCRAPER_PROVIDER_OFFSET + 110;

    private static final int SEASONS = SCRAPER_PROVIDER_OFFSET + 120;
    private static final int SEASONS_BYSHOWID = SCRAPER_PROVIDER_OFFSET + 121;

    private static final int MOVIE_POSTERS = SCRAPER_PROVIDER_OFFSET + 130;
    private static final int MOVIE_POSTERS_ID = SCRAPER_PROVIDER_OFFSET + 131;
    private static final int MOVIE_POSTERS_MOVIE_ID = SCRAPER_PROVIDER_OFFSET + 132;
    private static final int MOVIE_BACKDROPS = SCRAPER_PROVIDER_OFFSET + 133;
    private static final int MOVIE_BACKDROPS_ID = SCRAPER_PROVIDER_OFFSET + 134;
    private static final int MOVIE_BACKDROPS_MOVIE_ID = SCRAPER_PROVIDER_OFFSET + 135;
    private static final int SHOW_POSTERS = SCRAPER_PROVIDER_OFFSET + 136;
    private static final int SHOW_POSTERS_ID = SCRAPER_PROVIDER_OFFSET + 137;
    private static final int SHOW_POSTERS_SHOW_ID = SCRAPER_PROVIDER_OFFSET + 138;
    private static final int SHOW_BACKDROPS = SCRAPER_PROVIDER_OFFSET + 139;
    private static final int SHOW_BACKDROPS_ID = SCRAPER_PROVIDER_OFFSET + 140;
    private static final int SHOW_BACKDROPS_SHOW_ID = SCRAPER_PROVIDER_OFFSET + 141;
    private static final int MOVIE_TRAILERS = SCRAPER_PROVIDER_OFFSET + 142;
    private static final int MOVIE_TRAILERS_ID = SCRAPER_PROVIDER_OFFSET + 143;
    private static final int MOVIE_TRAILERS_MOVIE_ID = SCRAPER_PROVIDER_OFFSET + 144;


    private static final int MOVIE_COLLECTION = SCRAPER_PROVIDER_OFFSET + 150;
    private static final int MOVIE_COLLECTION_ID = SCRAPER_PROVIDER_OFFSET + 151;
    private static final int MOVIE_COLLECTION_NAME = SCRAPER_PROVIDER_OFFSET + 152;
    private static final int MOVIE_COLLECTION_POSTER_LARGE_URL = SCRAPER_PROVIDER_OFFSET + 153;
    private static final int MOVIE_COLLECTION_POSTER_LARGE_FILE = SCRAPER_PROVIDER_OFFSET + 154;
    private static final int MOVIE_COLLECTION_POSTER_THUMB_URL = SCRAPER_PROVIDER_OFFSET + 155;
    private static final int MOVIE_COLLECTION_POSTER_THUMB_FILE = SCRAPER_PROVIDER_OFFSET + 156;
    private static final int MOVIE_COLLECTION_BACKDROP_LARGE_URL = SCRAPER_PROVIDER_OFFSET + 157;
    private static final int MOVIE_COLLECTION_BACKDROP_LARGE_FILE = SCRAPER_PROVIDER_OFFSET + 158;
    private static final int MOVIE_COLLECTION_BACKDROP_THUMB_URL = SCRAPER_PROVIDER_OFFSET + 159;
    private static final int MOVIE_COLLECTION_BACKDROP_THUMB_FILE = SCRAPER_PROVIDER_OFFSET + 160;

    private static final int WRITER = SCRAPER_PROVIDER_OFFSET + 161;
    private static final int WRITER_ID = SCRAPER_PROVIDER_OFFSET + 162;
    private static final int WRITER_ALL = SCRAPER_PROVIDER_OFFSET + 163;
    private static final int WRITER_MOVIE = SCRAPER_PROVIDER_OFFSET + 164;
    private static final int WRITER_SHOW = SCRAPER_PROVIDER_OFFSET + 165;
    private static final int WRITER_EPISODE = SCRAPER_PROVIDER_OFFSET + 166;
    private static final int WRITER_NAME = SCRAPER_PROVIDER_OFFSET + 167;

    private static final int SHOW_NETWORKLOGOS = SCRAPER_PROVIDER_OFFSET + 168;
    private static final int SHOW_NETWORKLOGOS_ID = SCRAPER_PROVIDER_OFFSET + 169;
    private static final int SHOW_NETWORKLOGOS_SHOW_ID = SCRAPER_PROVIDER_OFFSET + 170;

    private static final int SEASONPLOT = SCRAPER_PROVIDER_OFFSET + 171;
    private static final int SEASONPLOT_ID = SCRAPER_PROVIDER_OFFSET + 172;
    private static final int SEASONPLOT_ALL = SCRAPER_PROVIDER_OFFSET + 173;
    private static final int SEASONPLOT_SHOW = SCRAPER_PROVIDER_OFFSET + 174;
    private static final int SEASONPLOT_NAME = SCRAPER_PROVIDER_OFFSET + 175;

    private static final int SHOW_ACTORPHOTOS = SCRAPER_PROVIDER_OFFSET + 176;
    private static final int SHOW_ACTORPHOTOS_ID = SCRAPER_PROVIDER_OFFSET + 177;
    private static final int SHOW_ACTORPHOTOS_SHOW_ID = SCRAPER_PROVIDER_OFFSET + 178;

    private static final int SHOW_CLEARLOGOS = SCRAPER_PROVIDER_OFFSET + 179;
    private static final int SHOW_CLEARLOGOS_ID = SCRAPER_PROVIDER_OFFSET + 180;
    private static final int SHOW_CLEARLOGOS_SHOW_ID = SCRAPER_PROVIDER_OFFSET + 181;

    private static final int TAGLINE = SCRAPER_PROVIDER_OFFSET + 182;
    private static final int TAGLINE_ID = SCRAPER_PROVIDER_OFFSET + 183;
    private static final int TAGLINE_ALL = SCRAPER_PROVIDER_OFFSET + 184;
    private static final int TAGLINE_MOVIE = SCRAPER_PROVIDER_OFFSET + 185;
    private static final int TAGLINE_SHOW = SCRAPER_PROVIDER_OFFSET + 186;
    private static final int TAGLINE_EPISODE = SCRAPER_PROVIDER_OFFSET + 187;
    private static final int TAGLINE_NAME = SCRAPER_PROVIDER_OFFSET + 188;

    private static final int SHOW_STUDIOLOGOS = SCRAPER_PROVIDER_OFFSET + 189;
    private static final int SHOW_STUDIOLOGOS_ID = SCRAPER_PROVIDER_OFFSET + 190;
    private static final int SHOW_STUDIOLOGOS_SHOW_ID = SCRAPER_PROVIDER_OFFSET + 191;

    private static final int MOVIE_ACTORPHOTOS = SCRAPER_PROVIDER_OFFSET + 192;
    private static final int MOVIE_ACTORPHOTOS_ID = SCRAPER_PROVIDER_OFFSET + 193;
    private static final int MOVIE_ACTORPHOTOS_MOVIE_ID = SCRAPER_PROVIDER_OFFSET + 194;

    private static final int MOVIE_STUDIOLOGOS = SCRAPER_PROVIDER_OFFSET + 195;
    private static final int MOVIE_STUDIOLOGOS_ID = SCRAPER_PROVIDER_OFFSET + 196;
    private static final int MOVIE_STUDIOLOGOS_MOVIE_ID = SCRAPER_PROVIDER_OFFSET + 197;

    private static final int MOVIE_CLEARLOGOS = SCRAPER_PROVIDER_OFFSET + 198;
    private static final int MOVIE_CLEARLOGOS_ID = SCRAPER_PROVIDER_OFFSET + 199;
    private static final int MOVIE_CLEARLOGOS_MOVIE_ID = SCRAPER_PROVIDER_OFFSET + 200;

    private static final int PRODUCER = SCRAPER_PROVIDER_OFFSET + 201;
    private static final int PRODUCER_ID = SCRAPER_PROVIDER_OFFSET + 202;
    private static final int PRODUCER_ALL = SCRAPER_PROVIDER_OFFSET + 203;
    private static final int PRODUCER_MOVIE = SCRAPER_PROVIDER_OFFSET + 204;
    private static final int PRODUCER_SHOW = SCRAPER_PROVIDER_OFFSET + 205;
    private static final int PRODUCER_EPISODE = SCRAPER_PROVIDER_OFFSET + 206;
    private static final int PRODUCER_NAME = SCRAPER_PROVIDER_OFFSET + 207;

    private static final int SCREENPLAY = SCRAPER_PROVIDER_OFFSET + 208;
    private static final int SCREENPLAY_ID = SCRAPER_PROVIDER_OFFSET + 209;
    private static final int SCREENPLAY_ALL = SCRAPER_PROVIDER_OFFSET + 210;
    private static final int SCREENPLAY_MOVIE = SCRAPER_PROVIDER_OFFSET + 211;
    private static final int SCREENPLAY_SHOW = SCRAPER_PROVIDER_OFFSET + 212;
    private static final int SCREENPLAY_EPISODE = SCRAPER_PROVIDER_OFFSET + 213;
    private static final int SCREENPLAY_NAME = SCRAPER_PROVIDER_OFFSET + 214;

    private static final int MUSICCOMPOSER = SCRAPER_PROVIDER_OFFSET + 215;
    private static final int MUSICCOMPOSER_ID = SCRAPER_PROVIDER_OFFSET + 216;
    private static final int MUSICCOMPOSER_ALL = SCRAPER_PROVIDER_OFFSET + 217;
    private static final int MUSICCOMPOSER_MOVIE = SCRAPER_PROVIDER_OFFSET + 218;
    private static final int MUSICCOMPOSER_SHOW = SCRAPER_PROVIDER_OFFSET + 219;
    private static final int MUSICCOMPOSER_EPISODE = SCRAPER_PROVIDER_OFFSET + 220;
    private static final int MUSICCOMPOSER_NAME = SCRAPER_PROVIDER_OFFSET + 221;

    private static final int COUNTRY = SCRAPER_PROVIDER_OFFSET + 222;
    private static final int COUNTRY_ID = SCRAPER_PROVIDER_OFFSET + 223;
    private static final int COUNTRY_ALL = SCRAPER_PROVIDER_OFFSET + 224;
    private static final int COUNTRY_MOVIE = SCRAPER_PROVIDER_OFFSET + 225;
    private static final int COUNTRY_SHOW = SCRAPER_PROVIDER_OFFSET + 226;
    private static final int COUNTRY_EPISODE = SCRAPER_PROVIDER_OFFSET + 227;
    private static final int COUNTRY_NAME = SCRAPER_PROVIDER_OFFSET + 228;

    private static final int SPOKENLANGUAGE = SCRAPER_PROVIDER_OFFSET + 229;
    private static final int SPOKENLANGUAGE_ID = SCRAPER_PROVIDER_OFFSET + 230;
    private static final int SPOKENLANGUAGE_ALL = SCRAPER_PROVIDER_OFFSET + 231;
    private static final int SPOKENLANGUAGE_MOVIE = SCRAPER_PROVIDER_OFFSET + 232;
    private static final int SPOKENLANGUAGE_SHOW = SCRAPER_PROVIDER_OFFSET + 233;
    private static final int SPOKENLANGUAGE_EPISODE = SCRAPER_PROVIDER_OFFSET + 234;
    private static final int SPOKENLANGUAGE_NAME = SCRAPER_PROVIDER_OFFSET + 235;

    private static UriMatcher sUriMatcher;

    /**
     * called from VideoProvider static context. Registers these URIs
     */
    public static void hookUriMatcher(UriMatcher hook) {
        sUriMatcher = hook;

        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Movie.URI.BASE),
                MOVIE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Movie.URI.ALL),
                MOVIE_ALL);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Movie.URI.ID) + "#",
                MOVIE_ID);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Movie.URI.ALL_INFOS) + "#",
                MOVIE_ALL_INFOS);

        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Show.URI.BASE),
                SHOW);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Show.URI.ALL),
                SHOW_ALL);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Show.URI.ID) + "#",
                SHOW_ID);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Show.URI.ALL_INFOS) + "#",
                SHOW_ALL_INFOS);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Show.URI.NAME) + "*",
                SHOW_NAME);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Show.URI.ONLINE_ID) + "#",
                SHOW_ONLINE_ID);

        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Episode.URI.BASE),
                EPISODE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Episode.URI.ID) + "#",
                EPISODE_ID);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Episode.URI.ALL_INFOS) + "#",
                EPISODE_ALL_INFOS);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Episode.URI.ALL_SEASONS) + "#",
                EPISODE_ALL_SEASONS);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Episode.URI.SHOW) + "#",
                EPISODE_SHOW);

        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Director.URI.BASE),
                DIRECTOR);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Director.URI.ALL),
                DIRECTOR_ALL);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Director.URI.MOVIE),
                DIRECTOR_MOVIE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Director.URI.MOVIE) + "#",
                DIRECTOR_MOVIE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Director.URI.SHOW),
                DIRECTOR_SHOW);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Director.URI.SHOW) + "#",
                DIRECTOR_SHOW);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Director.URI.EPISODE),
                DIRECTOR_EPISODE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Director.URI.EPISODE) + "#",
                DIRECTOR_EPISODE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Director.URI.NAME) + "*",
                DIRECTOR_NAME);

        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Writer.URI.BASE),
                WRITER);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Writer.URI.ALL),
                WRITER_ALL);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Writer.URI.MOVIE),
                WRITER_MOVIE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Writer.URI.MOVIE) + "#",
                WRITER_MOVIE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Writer.URI.SHOW),
                WRITER_SHOW);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Writer.URI.SHOW) + "#",
                WRITER_SHOW);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Writer.URI.EPISODE),
                WRITER_EPISODE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Writer.URI.EPISODE) + "#",
                WRITER_EPISODE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Writer.URI.NAME) + "*",
                WRITER_NAME);

        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Tagline.URI.BASE),
                TAGLINE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Tagline.URI.ALL),
                TAGLINE_ALL);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Tagline.URI.MOVIE),
                TAGLINE_MOVIE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Tagline.URI.MOVIE) + "#",
                TAGLINE_MOVIE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Tagline.URI.SHOW),
                TAGLINE_SHOW);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Tagline.URI.SHOW) + "#",
                TAGLINE_SHOW);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Tagline.URI.EPISODE),
                TAGLINE_EPISODE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Tagline.URI.EPISODE) + "#",
                TAGLINE_EPISODE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Tagline.URI.NAME) + "*",
                TAGLINE_NAME);

        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Producer.URI.BASE),
                PRODUCER);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Producer.URI.ALL),
                PRODUCER_ALL);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Producer.URI.MOVIE),
                PRODUCER_MOVIE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Producer.URI.MOVIE) + "#",
                PRODUCER_MOVIE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Producer.URI.SHOW),
                PRODUCER_SHOW);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Producer.URI.SHOW) + "#",
                PRODUCER_SHOW);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Producer.URI.EPISODE),
                PRODUCER_EPISODE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Producer.URI.EPISODE) + "#",
                PRODUCER_EPISODE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Producer.URI.NAME) + "*",
                PRODUCER_NAME);

        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Screenplay.URI.BASE),
                SCREENPLAY);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Screenplay.URI.ALL),
                SCREENPLAY_ALL);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Screenplay.URI.MOVIE),
                SCREENPLAY_MOVIE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Screenplay.URI.MOVIE) + "#",
                SCREENPLAY_MOVIE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Screenplay.URI.SHOW),
                SCREENPLAY_SHOW);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Screenplay.URI.SHOW) + "#",
                SCREENPLAY_SHOW);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Screenplay.URI.EPISODE),
                SCREENPLAY_EPISODE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Screenplay.URI.EPISODE) + "#",
                SCREENPLAY_EPISODE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Screenplay.URI.NAME) + "*",
                SCREENPLAY_NAME);

        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Musiccomposer.URI.BASE),
                MUSICCOMPOSER);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Musiccomposer.URI.ALL),
                MUSICCOMPOSER_ALL);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Musiccomposer.URI.MOVIE),
                MUSICCOMPOSER_MOVIE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Musiccomposer.URI.MOVIE) + "#",
                MUSICCOMPOSER_MOVIE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Musiccomposer.URI.SHOW),
                MUSICCOMPOSER_SHOW);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Musiccomposer.URI.SHOW) + "#",
                MUSICCOMPOSER_SHOW);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Musiccomposer.URI.EPISODE),
                MUSICCOMPOSER_EPISODE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Musiccomposer.URI.EPISODE) + "#",
                MUSICCOMPOSER_EPISODE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Musiccomposer.URI.NAME) + "*",
                MUSICCOMPOSER_NAME);

        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Spokenlanguage.URI.BASE),
                SPOKENLANGUAGE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Spokenlanguage.URI.ALL),
                SPOKENLANGUAGE_ALL);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Spokenlanguage.URI.MOVIE),
                SPOKENLANGUAGE_MOVIE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Spokenlanguage.URI.MOVIE) + "#",
                SPOKENLANGUAGE_MOVIE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Spokenlanguage.URI.SHOW),
                SPOKENLANGUAGE_SHOW);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Spokenlanguage.URI.SHOW) + "#",
                SPOKENLANGUAGE_SHOW);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Spokenlanguage.URI.EPISODE),
                SPOKENLANGUAGE_EPISODE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Spokenlanguage.URI.EPISODE) + "#",
                SPOKENLANGUAGE_EPISODE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Spokenlanguage.URI.NAME) + "*",
                SPOKENLANGUAGE_NAME);

        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Country.URI.BASE),
                COUNTRY);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Country.URI.ALL),
                COUNTRY_ALL);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Country.URI.MOVIE),
                COUNTRY_MOVIE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Country.URI.MOVIE) + "#",
                COUNTRY_MOVIE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Country.URI.SHOW),
                COUNTRY_SHOW);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Country.URI.SHOW) + "#",
                COUNTRY_SHOW);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Country.URI.EPISODE),
                COUNTRY_EPISODE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Country.URI.EPISODE) + "#",
                COUNTRY_EPISODE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Country.URI.NAME) + "*",
                COUNTRY_NAME);

        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.SeasonPlot.URI.BASE),
                SEASONPLOT);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.SeasonPlot.URI.ALL),
                SEASONPLOT_ALL);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.SeasonPlot.URI.SHOW),
                SEASONPLOT_SHOW);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.SeasonPlot.URI.SHOW) + "#",
                SEASONPLOT_SHOW);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.SeasonPlot.URI.NAME) + "*",
                SEASONPLOT_NAME);

        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Actor.URI.BASE),
                ACTOR);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Actor.URI.ALL),
                ACTOR_ALL);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Actor.URI.ID) + "#",
                ACTOR_ID);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Actor.URI.MOVIE),
                ACTOR_MOVIE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Actor.URI.MOVIE) + "#",
                ACTOR_MOVIE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Actor.URI.SHOW),
                ACTOR_SHOW);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Actor.URI.SHOW) + "#",
                ACTOR_SHOW);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Actor.URI.EPISODE),
                ACTOR_EPISODE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Actor.URI.EPISODE) + "#",
                ACTOR_EPISODE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Actor.URI.NAME) + "*",
                ACTOR_NAME);

        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Genre.URI.BASE),
                GENRE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Genre.URI.ALL),
                GENRE_ALL);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Genre.URI.ID) + "#",
                GENRE_ID);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Genre.URI.MOVIE),
                GENRE_MOVIE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Genre.URI.MOVIE) + "#",
                GENRE_MOVIE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Genre.URI.SHOW),
                GENRE_SHOW);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Genre.URI.SHOW) + "#",
                GENRE_SHOW);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Genre.URI.NAME) + "*",
                GENRE_NAME);

        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Studio.URI.BASE),
                STUDIO);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Studio.URI.ALL),
                STUDIO_ALL);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Studio.URI.MOVIE),
                STUDIO_MOVIE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Studio.URI.MOVIE) + "#",
                STUDIO_MOVIE);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Studio.URI.SHOW),
                STUDIO_SHOW);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Studio.URI.SHOW) + "#",
                STUDIO_SHOW);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Studio.URI.NAME) + "*",
                STUDIO_NAME);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.EpisodeShowCombined.URI.ALL),
                EPISODESHOWCOMBINED_ALL);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.EpisodeShowCombined.URI.ID) + "#",
                EPISODESHOWCOMBINED_ID);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.AllVideos.URI.ALL),
                ALL_VIDEOS_ALL);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Seasons.URI.ALL),
                SEASONS);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.Seasons.URI.ALL) + "/#",
                SEASONS_BYSHOWID);

        // movie posters
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.MoviePosters.URI.BASE),
                MOVIE_POSTERS);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.MoviePosters.URI.BASE) + "/#",
                MOVIE_POSTERS_ID);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.MoviePosters.URI.BASE) + "/#/#",
                MOVIE_POSTERS_ID);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.MoviePosters.URI.BY_MOVIE_ID) + "/#",
                MOVIE_POSTERS_MOVIE_ID);

        // movie backdrops
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.MovieBackdrops.URI.BASE),
                MOVIE_BACKDROPS);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.MovieBackdrops.URI.BASE) + "/#",
                MOVIE_BACKDROPS_ID);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.MovieBackdrops.URI.BY_MOVIE_ID) + "/#",
                MOVIE_BACKDROPS_MOVIE_ID);

        // show posters
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.ShowPosters.URI.BASE),
                SHOW_POSTERS);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.ShowPosters.URI.BASE) + "/#",
                SHOW_POSTERS_ID);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.ShowPosters.URI.BASE) + "/#/#",
                SHOW_POSTERS_ID);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.ShowPosters.URI.BY_SHOW_ID) + "/#",
                SHOW_POSTERS_SHOW_ID);

        // show backdrops
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.ShowBackdrops.URI.BASE),
                SHOW_BACKDROPS);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.ShowBackdrops.URI.BASE) + "/#",
                SHOW_BACKDROPS_ID);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.ShowBackdrops.URI.BY_SHOW_ID) + "/#",
                SHOW_BACKDROPS_SHOW_ID);

        // show networklogos
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.ShowNetworkLogos.URI.BASE),
                SHOW_NETWORKLOGOS);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.ShowNetworkLogos.URI.BASE) + "/#",
                SHOW_NETWORKLOGOS_ID);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.ShowNetworkLogos.URI.BY_SHOW_ID) + "/#",
                SHOW_NETWORKLOGOS_SHOW_ID);

        // show actorphotos
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.ShowActorPhotos.URI.BASE),
                SHOW_ACTORPHOTOS);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.ShowActorPhotos.URI.BASE) + "/#",
                SHOW_ACTORPHOTOS_ID);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.ShowActorPhotos.URI.BY_SHOW_ID) + "/#",
                SHOW_ACTORPHOTOS_SHOW_ID);

        // movie actorphotos
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.MovieActorPhotos.URI.BASE),
                MOVIE_ACTORPHOTOS);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.MovieActorPhotos.URI.BASE) + "/#",
                MOVIE_ACTORPHOTOS_ID);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.MovieActorPhotos.URI.BY_MOVIE_ID) + "/#",
                MOVIE_ACTORPHOTOS_MOVIE_ID);

        // movie studiologos
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.MovieStudioLogos.URI.BASE),
                MOVIE_STUDIOLOGOS);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.MovieStudioLogos.URI.BASE) + "/#",
                MOVIE_STUDIOLOGOS_ID);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.MovieStudioLogos.URI.BY_MOVIE_ID) + "/#",
                MOVIE_STUDIOLOGOS_MOVIE_ID);

        // movie clearlogos
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.MovieClearLogos.URI.BASE),
                MOVIE_CLEARLOGOS);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.MovieClearLogos.URI.BASE) + "/#",
                MOVIE_CLEARLOGOS_ID);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.MovieClearLogos.URI.BY_MOVIE_ID) + "/#",
                MOVIE_CLEARLOGOS_MOVIE_ID);

        // show clearlogos
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.ShowClearLogos.URI.BASE),
                SHOW_CLEARLOGOS);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.ShowClearLogos.URI.BASE) + "/#",
                SHOW_CLEARLOGOS_ID);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.ShowClearLogos.URI.BY_SHOW_ID) + "/#",
                SHOW_CLEARLOGOS_SHOW_ID);

        // show studiologos
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.ShowStudioLogos.URI.BASE),
                SHOW_STUDIOLOGOS);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.ShowStudioLogos.URI.BASE) + "/#",
                SHOW_STUDIOLOGOS_ID);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.ShowStudioLogos.URI.BY_SHOW_ID) + "/#",
                SHOW_STUDIOLOGOS_SHOW_ID);

        // movie trailers
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.MovieTrailers.URI.BASE),
                MOVIE_TRAILERS);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.MovieTrailers.URI.BASE) + "/#",
                MOVIE_TRAILERS_ID);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.MovieTrailers.URI.BY_MOVIE_ID) + "/#",
                MOVIE_TRAILERS_MOVIE_ID);

        // movie collection
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.MovieCollections.URI.BASE),
                MOVIE_COLLECTION);
        sUriMatcher.addURI(ScraperStore.AUTHORITY, getPath(ScraperStore.MovieCollections.URI.BY_COLLECTION_ID) + "/#",
                MOVIE_COLLECTION_ID);
    }

    // Uris that need to be notified when inserting a Show or Episode
    private static final Uri[] ADDITIONAL_SHOW = new Uri[]  {
        ScraperStore.AllVideos.URI.BASE,
        ScraperStore.EpisodeShowCombined.URI.BASE,
    };

    // Uris that need to be notified when inserting a Movie
    private static final Uri[] ADDITIONAL_MOVIE = new Uri[]  {
        ScraperStore.AllVideos.URI.BASE,
    };

    private final DbHolder mDbHolder;
    private final ContentResolver mCr;
    private final Context mContext;

    /* Create an instance of this class to use. Don't use it as a real provider.*/
    public ScraperProvider(Context context, DbHolder dbH) {
        mDbHolder = dbH;
        mContext = context;
        mCr = context.getContentResolver();
    }


    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int changed = internalDelete(uri, selection, selectionArgs);
        if (changed > 0) {
            // since deleting stuff affects actors, studios, shows etc notify a change for everything.
            Uri notifyUri = ScraperStore.ALL_CONTENT_URI;
            mCr.notifyChange(notifyUri, null);
        }
        return changed;
    }

    public int internalDelete(Uri uri, String selection, String[] selectionArgs) {
        String data = uri.getLastPathSegment();
        String whereClause;
        String[] whereArgs;
        SQLiteDatabase db = mDbHolder.get();
        if(DBG) Log.d(TAG, "Delete request with URI " + uri.toString());
        try {
            switch (sUriMatcher.match(uri)) {
                case MOVIE:
                    return db.delete(ScraperTables.MOVIE_TABLE_NAME, selection,
                            selectionArgs);
                case MOVIE_ID:
                    whereClause = ScraperStore.Movie.ID + "=?";
                    whereArgs = new String[] {data};
                    return db.delete(ScraperTables.MOVIE_TABLE_NAME, whereClause, whereArgs);
                case EPISODE:
                    return db.delete(ScraperTables.EPISODE_TABLE_NAME, selection,
                            selectionArgs);
                case EPISODE_ID:
                    whereClause = ScraperStore.Episode.ID + "=?";
                    whereArgs = new String[] {data};
                    return db.delete(ScraperTables.EPISODE_TABLE_NAME, whereClause, whereArgs);
                default:
                    throw new IllegalArgumentException("Unknown URI " + uri);
            }
        } catch (SQLiteConstraintException e) {
            Log.d (TAG, "Delete Failed selection:" + selection + " selectionArgs:" + Arrays.toString(selectionArgs));
            return 0;
        }
    }

    @Override
    public String getType(Uri uri) {
        throw new IllegalArgumentException("ScraperProvider#getType not supported " + uri);
    }

    private static String getPath(Uri uri) {
        int offset = 0;
        String path = uri.getPath();
        for(; path.startsWith("/", offset); offset++) {}
        return path.substring(offset);
    }

    @Override
    public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations)
            throws OperationApplicationException {
        SQLiteDatabase db = mDbHolder.get();
        db.beginTransaction();
        ContentProviderResult[] result = null;
        try {
            result = super.applyBatch(operations);
            db.setTransactionSuccessful();
            ContentResolver res = mCr;
            res.notifyChange(ScraperStore.ALL_CONTENT_URI, null);
            return result;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * returns that baseUri with appended Id and
     * if not in an transaction calls notifyChange for the baseUri and any additional Uri
     */
    private final static Uri createUriAndNotify(long rowId, SQLiteDatabase db,
            Uri baseUri, ContentResolver cr, Uri... additionalNotifications) {
        if (rowId < 0) return null;
        Uri returnValue = ContentUris.withAppendedId(baseUri, rowId);
        // only notify when not in an transaction
        if (!db.inTransaction()) {
            cr.notifyChange(baseUri, null);
            if (additionalNotifications != null)
                for (Uri additional : additionalNotifications)
                    cr.notifyChange(additional, null);
        }
        return returnValue;
    }

    private static final String[] ID_PROJ = { BaseColumns._ID };
    private final static long findScraperImage(SQLiteDatabase db, String table, ScraperImage.Type type, ContentValues cv) {
        String selection = type.largeFileColumn + "=?";
        String[] selectionArgs = { cv.getAsString(type.largeFileColumn) };
        long result = -1;
        Cursor cursor = db.query(table, ID_PROJ, selection, selectionArgs, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                result = cursor.getLong(0);
            }
            cursor.close();
        }
        return result;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if(DBG) Log.d(TAG, "Insert request with URI " + uri.toString() + " values:" +values.toString());

        long rowId = -1;
        Uri noteUri = null;
        ContentResolver cr = mCr;
        SQLiteDatabase db = mDbHolder.get();
        switch (sUriMatcher.match(uri)) {
            case MOVIE:
                rowId = db.insert(ScraperTables.MOVIE_TABLE_NAME,
                        ScraperStore.Movie.ID, values);
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Movie.URI.ID, cr, ADDITIONAL_MOVIE);
                break;
            case SHOW:
                rowId = db.insert(ScraperTables.SHOW_TABLE_NAME,
                        ScraperStore.Show.ID, values);
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Show.URI.ID, cr, ADDITIONAL_SHOW);
                break;
            case EPISODE:
                rowId = db.insert(ScraperTables.EPISODE_TABLE_NAME,
                        ScraperStore.Episode.ID, values);
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Episode.URI.ID, cr, ADDITIONAL_SHOW);
                break;
            case DIRECTOR:
                rowId = db.insert(ScraperTables.DIRECTORS_TABLE_NAME,
                        ScraperStore.Director.ID, values);
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Director.URI.ID, cr);
                break;
            case WRITER:
                rowId = db.insert(ScraperTables.WRITERS_TABLE_NAME,
                        ScraperStore.Writer.ID, values);
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Writer.URI.ID, cr);
                break;
            case TAGLINE:
                rowId = db.insert(ScraperTables.TAGLINES_TABLE_NAME,
                        ScraperStore.Tagline.ID, values);
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Tagline.URI.ID, cr);
                break;
            case PRODUCER:
                rowId = db.insert(ScraperTables.PRODUCERS_TABLE_NAME,
                        ScraperStore.Producer.ID, values);
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Producer.URI.ID, cr);
                break;
            case SCREENPLAY:
                rowId = db.insert(ScraperTables.SCREENPLAYS_TABLE_NAME,
                        ScraperStore.Screenplay.ID, values);
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Screenplay.URI.ID, cr);
                break;
            case MUSICCOMPOSER:
                rowId = db.insert(ScraperTables.MUSICCOMPOSERS_TABLE_NAME,
                        ScraperStore.Musiccomposer.ID, values);
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Musiccomposer.URI.ID, cr);
                break;
            case SPOKENLANGUAGE:
                rowId = db.insert(ScraperTables.SPOKENLANGUAGES_TABLE_NAME,
                        ScraperStore.Spokenlanguage.ID, values);
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Spokenlanguage.URI.ID, cr);
                break;
            case COUNTRY:
                rowId = db.insert(ScraperTables.COUNTRIES_TABLE_NAME,
                        ScraperStore.Country.ID, values);
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Country.URI.ID, cr);
                break;
            case SEASONPLOT:
                rowId = db.insert(ScraperTables.SEASONPLOTS_TABLE_NAME,
                        ScraperStore.SeasonPlot.ID, values);
                noteUri = createUriAndNotify(rowId, db, ScraperStore.SeasonPlot.URI.ID, cr);
                break;
            case ACTOR:
                rowId = db.insert(ScraperTables.ACTORS_TABLE_NAME,
                        ScraperStore.Actor.ID, values);
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Actor.URI.ID, cr);
                break;
            case GENRE:
                rowId = db.insert(ScraperTables.GENRES_TABLE_NAME,
                        ScraperStore.Genre.ID, values);
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Genre.URI.ID, cr);
                break;
            case STUDIO:
                rowId = db.insert(ScraperTables.STUDIOS_TABLE_NAME,
                        ScraperStore.Studio.ID, values);
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Studio.URI.ID, cr);
                break;
            case DIRECTOR_MOVIE:
                try {
                    rowId = db.insertOrThrow(ScraperTables.FILMS_MOVIE_VIEW_NAME,
                            ScraperStore.Movie.Director.MOVIE, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Director.URI.ID, cr);
                break;
            case WRITER_MOVIE:
                try {
                    rowId = db.insertOrThrow(ScraperTables.WRITERS_MOVIE_VIEW_NAME,
                            ScraperStore.Movie.Writer.MOVIE, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Writer.URI.ID, cr);
                break;
            case TAGLINE_MOVIE:
                try {
                    rowId = db.insertOrThrow(ScraperTables.TAGLINES_MOVIE_VIEW_NAME,
                            ScraperStore.Movie.Tagline.MOVIE, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Tagline.URI.ID, cr);
                break;
            case PRODUCER_MOVIE:
                try {
                    rowId = db.insertOrThrow(ScraperTables.PRODUCERS_MOVIE_VIEW_NAME,
                            ScraperStore.Movie.Producer.MOVIE, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Producer.URI.ID, cr);
                break;
            case SCREENPLAY_MOVIE:
                try {
                    rowId = db.insertOrThrow(ScraperTables.SCREENPLAYS_MOVIE_VIEW_NAME,
                            ScraperStore.Movie.Screenplay.MOVIE, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Screenplay.URI.ID, cr);
                break;
            case MUSICCOMPOSER_MOVIE:
                try {
                    rowId = db.insertOrThrow(ScraperTables.MUSICCOMPOSERS_MOVIE_VIEW_NAME,
                            ScraperStore.Movie.Musiccomposer.MOVIE, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Musiccomposer.URI.ID, cr);
                break;
            case SPOKENLANGUAGE_MOVIE:
                try {
                    rowId = db.insertOrThrow(ScraperTables.SPOKENLANGUAGES_MOVIE_VIEW_NAME,
                            ScraperStore.Movie.Spokenlanguage.MOVIE, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Spokenlanguage.URI.ID, cr);
                break;
            case COUNTRY_MOVIE:
                try {
                    rowId = db.insertOrThrow(ScraperTables.COUNTRIES_MOVIE_VIEW_NAME,
                            ScraperStore.Movie.Country.MOVIE, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Country.URI.ID, cr);
                break;
            case DIRECTOR_SHOW:
                try {
                    rowId = db.insertOrThrow(ScraperTables.FILMS_SHOW_VIEW_NAME,
                            ScraperStore.Show.Director.SHOW, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Director.URI.ID, cr);
                break;
            case WRITER_SHOW:
                try {
                    rowId = db.insertOrThrow(ScraperTables.WRITERS_SHOW_VIEW_NAME,
                            ScraperStore.Show.Writer.SHOW, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Writer.URI.ID, cr);
                break;
            case TAGLINE_SHOW:
                try {
                    rowId = db.insertOrThrow(ScraperTables.TAGLINES_SHOW_VIEW_NAME,
                            ScraperStore.Show.Tagline.SHOW, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Tagline.URI.ID, cr);
                break;
            case PRODUCER_SHOW:
                try {
                    rowId = db.insertOrThrow(ScraperTables.PRODUCERS_SHOW_VIEW_NAME,
                            ScraperStore.Show.Producer.SHOW, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Producer.URI.ID, cr);
                break;
            case SCREENPLAY_SHOW:
                try {
                    rowId = db.insertOrThrow(ScraperTables.SCREENPLAYS_SHOW_VIEW_NAME,
                            ScraperStore.Show.Screenplay.SHOW, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Screenplay.URI.ID, cr);
                break;
            case MUSICCOMPOSER_SHOW:
                try {
                    rowId = db.insertOrThrow(ScraperTables.MUSICCOMPOSERS_SHOW_VIEW_NAME,
                            ScraperStore.Show.Musiccomposer.SHOW, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Musiccomposer.URI.ID, cr);
                break;
            case SPOKENLANGUAGE_SHOW:
                try {
                    rowId = db.insertOrThrow(ScraperTables.SPOKENLANGUAGES_SHOW_VIEW_NAME,
                            ScraperStore.Show.Spokenlanguage.SHOW, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Spokenlanguage.URI.ID, cr);
                break;
            case COUNTRY_SHOW:
                try {
                    rowId = db.insertOrThrow(ScraperTables.COUNTRIES_SHOW_VIEW_NAME,
                            ScraperStore.Show.Country.SHOW, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Country.URI.ID, cr);
                break;
            case SEASONPLOT_SHOW:
                try {
                    rowId = db.insertOrThrow(ScraperTables.SEASONPLOTS_SHOW_VIEW_NAME,
                            ScraperStore.Show.SeasonPlot.SHOW, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.SeasonPlot.URI.ID, cr);
                break;
            case DIRECTOR_EPISODE:
                try {
                    rowId = db.insertOrThrow(ScraperTables.FILMS_EPISODE_VIEW_NAME,
                            ScraperStore.Episode.Director.EPISODE, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Director.URI.ID, cr);
                break;
            case WRITER_EPISODE:
                try {
                    rowId = db.insertOrThrow(ScraperTables.WRITERS_EPISODE_VIEW_NAME,
                            ScraperStore.Episode.Writer.EPISODE, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Writer.URI.ID, cr);
                break;
            case TAGLINE_EPISODE:
                try {
                    rowId = db.insertOrThrow(ScraperTables.TAGLINES_EPISODE_VIEW_NAME,
                            ScraperStore.Episode.Tagline.EPISODE, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Tagline.URI.ID, cr);
                break;
            case PRODUCER_EPISODE:
                try {
                    rowId = db.insertOrThrow(ScraperTables.PRODUCERS_EPISODE_VIEW_NAME,
                            ScraperStore.Episode.Producer.EPISODE, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Producer.URI.ID, cr);
                break;
            case SCREENPLAY_EPISODE:
                try {
                    rowId = db.insertOrThrow(ScraperTables.SCREENPLAYS_EPISODE_VIEW_NAME,
                            ScraperStore.Episode.Screenplay.EPISODE, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Screenplay.URI.ID, cr);
                break;
            case MUSICCOMPOSER_EPISODE:
                try {
                    rowId = db.insertOrThrow(ScraperTables.MUSICCOMPOSERS_EPISODE_VIEW_NAME,
                            ScraperStore.Episode.Musiccomposer.EPISODE, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Musiccomposer.URI.ID, cr);
                break;
            case SPOKENLANGUAGE_EPISODE:
                try {
                    rowId = db.insertOrThrow(ScraperTables.SPOKENLANGUAGES_EPISODE_VIEW_NAME,
                            ScraperStore.Episode.Spokenlanguage.EPISODE, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Spokenlanguage.URI.ID, cr);
                break;
            case COUNTRY_EPISODE:
                try {
                    rowId = db.insertOrThrow(ScraperTables.COUNTRIES_EPISODE_VIEW_NAME,
                            ScraperStore.Episode.Country.EPISODE, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Country.URI.ID, cr);
                break;
            case ACTOR_MOVIE:
                try {
                    rowId = db.insertOrThrow(ScraperTables.PLAYS_MOVIE_VIEW_NAME,
                            ScraperStore.Movie.Actor.MOVIE, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Actor.URI.ID, cr);
                break;
            case ACTOR_SHOW:
                try {
                    rowId = db.insertOrThrow(ScraperTables.PLAYS_SHOW_VIEW_NAME,
                            ScraperStore.Show.Actor.SHOW, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Actor.URI.ID, cr);
                break;
            case ACTOR_EPISODE:
                try {
                    rowId = db.insertOrThrow(ScraperTables.GUESTS_VIEW_NAME,
                            ScraperStore.Episode.Actor.EPISODE, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Actor.URI.ID, cr);
                break;
            case GENRE_MOVIE:
                try {
                    rowId = db.insertOrThrow(ScraperTables.BELONGS_MOVIE_VIEW_NAME,
                            ScraperStore.Movie.Genre.MOVIE, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Genre.URI.ID, cr);
                break;
            case GENRE_SHOW:
                try {
                    rowId = db.insertOrThrow(ScraperTables.BELONGS_SHOW_VIEW_NAME,
                            ScraperStore.Show.Genre.SHOW, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Genre.URI.ID, cr);
                break;
            case STUDIO_MOVIE:
                try {
                    rowId = db.insertOrThrow(ScraperTables.PRODUCES_MOVIE_VIEW_NAME,
                            ScraperStore.Movie.Studio.MOVIE, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Studio.URI.ID, cr);
                break;
            case STUDIO_SHOW:
                try {
                    rowId = db.insertOrThrow(ScraperTables.PRODUCES_SHOW_VIEW_NAME,
                            ScraperStore.Show.Studio.SHOW, values);
                    rowId = 1; // inserting into views will not return a row
                } catch (SQLException e) {
                    Log.d(TAG, "Exception: ", e);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.Studio.URI.ID, cr);
                break;
            case MOVIE_POSTERS:
                rowId = db.insert(ScraperTables.MOVIE_POSTERS_TABLE_NAME,
                        ScraperStore.MoviePosters.ID, values);
                // table does ON CONFLICT IGNORE which means we may get a -1 rowId here.
                // Since we insert via ContentProviderOperations which does not like
                // the resulting null Uri we search the image in the database so we can
                // return the real id.
                if (rowId < 0) {
                    rowId = findScraperImage(db, ScraperTables.MOVIE_POSTERS_TABLE_NAME,
                            ScraperImage.Type.MOVIE_POSTER, values);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.MoviePosters.URI.BASE, cr);
                break;

            case MOVIE_TRAILERS:
                rowId = db.insert(ScraperTables.MOVIE_TRAILERS_TABLE_NAME,
                        ScraperStore.MovieTrailers.ID, values);
                // table does ON CONFLICT IGNORE which means we may get a -1 rowId here.
                // Since we insert via ContentProviderOperations which does not like
                // the resulting null Uri we search the image in the database so we can
                // return the real id.
                if (rowId < 0) {

                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.MovieTrailers.URI.BASE, cr);
                break;
            case MOVIE_BACKDROPS:
                // see MOVIE_POSTERS
                rowId = db.insert(ScraperTables.MOVIE_BACKDROPS_TABLE_NAME,
                        ScraperStore.MovieBackdrops.ID, values);
                if (rowId < 0) {
                    rowId = findScraperImage(db, ScraperTables.MOVIE_BACKDROPS_TABLE_NAME,
                            ScraperImage.Type.MOVIE_BACKDROP, values);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.MovieBackdrops.URI.BASE, cr);
                break;
            case SHOW_POSTERS:
                // see MOVIE_POSTERS
                rowId = db.insert(ScraperTables.SHOW_POSTERS_TABLE_NAME,
                        ScraperStore.ShowPosters.ID, values);
                if (rowId < 0) {
                    rowId = findScraperImage(db, ScraperTables.SHOW_POSTERS_TABLE_NAME,
                            ScraperImage.Type.SHOW_POSTER, values);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.ShowPosters.URI.BASE, cr);
                break;
            case SHOW_BACKDROPS:
                // see MOVIE_POSTERS
                rowId = db.insert(ScraperTables.SHOW_BACKDROPS_TABLE_NAME,
                        ScraperStore.ShowBackdrops.ID, values);
                if (rowId < 0) {
                    rowId = findScraperImage(db, ScraperTables.SHOW_BACKDROPS_TABLE_NAME,
                            ScraperImage.Type.SHOW_BACKDROP, values);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.ShowBackdrops.URI.BASE, cr);
                break;
            case SHOW_NETWORKLOGOS:
                rowId = db.insert(ScraperTables.SHOW_NETWORKLOGOS_TABLE_NAME,
                        ScraperStore.ShowNetworkLogos.ID, values);
                if (rowId < 0) {
                    rowId = findScraperImage(db, ScraperTables.SHOW_NETWORKLOGOS_TABLE_NAME,
                            ScraperImage.Type.SHOW_NETWORK, values);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.ShowNetworkLogos.URI.BASE, cr);
                break;
            case SHOW_ACTORPHOTOS:
                rowId = db.insert(ScraperTables.SHOW_ACTORPHOTOS_TABLE_NAME,
                        ScraperStore.ShowActorPhotos.ID, values);
                if (rowId < 0) {
                    rowId = findScraperImage(db, ScraperTables.SHOW_ACTORPHOTOS_TABLE_NAME,
                            ScraperImage.Type.SHOW_ACTOR_PHOTO, values);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.ShowActorPhotos.URI.BASE, cr);
                break;
            case MOVIE_ACTORPHOTOS:
                rowId = db.insert(ScraperTables.MOVIE_ACTORPHOTOS_TABLE_NAME,
                        ScraperStore.MovieActorPhotos.ID, values);
                if (rowId < 0) {
                    rowId = findScraperImage(db, ScraperTables.MOVIE_ACTORPHOTOS_TABLE_NAME,
                            ScraperImage.Type.MOVIE_ACTORPHOTO, values);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.MovieActorPhotos.URI.BASE, cr);
                break;
            case MOVIE_STUDIOLOGOS:
                rowId = db.insert(ScraperTables.MOVIE_STUDIOLOGOS_TABLE_NAME,
                        ScraperStore.MovieStudioLogos.ID, values);
                if (rowId < 0) {
                    rowId = findScraperImage(db, ScraperTables.MOVIE_STUDIOLOGOS_TABLE_NAME,
                            ScraperImage.Type.MOVIE_STUDIOLOGO, values);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.MovieStudioLogos.URI.BASE, cr);
                break;
            case MOVIE_CLEARLOGOS:
                rowId = db.insert(ScraperTables.MOVIE_CLEARLOGOS_TABLE_NAME,
                        ScraperStore.MovieClearLogos.ID, values);
                if (rowId < 0) {
                    rowId = findScraperImage(db, ScraperTables.MOVIE_CLEARLOGOS_TABLE_NAME,
                            ScraperImage.Type.MOVIE_CLEARLOGO, values);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.MovieClearLogos.URI.BASE, cr);
                break;
            case SHOW_CLEARLOGOS:
                rowId = db.insert(ScraperTables.SHOW_CLEARLOGOS_TABLE_NAME,
                        ScraperStore.ShowClearLogos.ID, values);
                if (rowId < 0) {
                    rowId = findScraperImage(db, ScraperTables.SHOW_CLEARLOGOS_TABLE_NAME,
                            ScraperImage.Type.SHOW_TITLE_CLEARLOGO, values);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.ShowClearLogos.URI.BASE, cr);
                break;
            case SHOW_STUDIOLOGOS:
                rowId = db.insert(ScraperTables.SHOW_STUDIOLOGOS_TABLE_NAME,
                        ScraperStore.ShowStudioLogos.ID, values);
                if (rowId < 0) {
                    rowId = findScraperImage(db, ScraperTables.SHOW_STUDIOLOGOS_TABLE_NAME,
                            ScraperImage.Type.SHOW_STUDIOLOGO, values);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.ShowStudioLogos.URI.BASE, cr);
                break;
            case MOVIE_COLLECTION:
                // see MOVIE_POSTERS
                rowId = db.insert(ScraperTables.MOVIE_COLLECTION_TABLE_NAME,
                        ScraperStore.MovieCollections.ID, values);
                if (rowId < 0) {
                    rowId = findScraperImage(db, ScraperTables.MOVIE_COLLECTION_TABLE_NAME,
                            ScraperImage.Type.COLLECTION_BACKDROP, values);
                }
                noteUri = createUriAndNotify(rowId, db, ScraperStore.MovieCollections.URI.BASE, cr);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        return noteUri;
    }

    @Override
    public boolean onCreate() {
        throw new RuntimeException("ScraperProvider can't be created");
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        Bundle extras = new Bundle();
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        Cursor c;
        String data = uri.getLastPathSegment();
        qb.setCursorFactory(new ScraperCursorFactory());

        if(DBG) Log.d(TAG, "Query with URI " + uri.toString());
        boolean useId = false;
        switch (sUriMatcher.match(uri)) {
            case MOVIE_ALL:
                qb.setTables(ScraperTables.MOVIE_TABLE_NAME);
                break;

            case MOVIE_ID:
                qb.setTables(ScraperTables.MOVIE_TABLE_NAME);
                qb.appendWhere(ScraperStore.Movie.ID + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case MOVIE_ALL_INFOS:
                handleMovieFull(qb);
                qb.appendWhere(ScraperStore.Movie.ID + " = ");
                qb.appendWhereEscapeString(data);
                extras.putInt("type", BaseTags.MOVIE);
                break;

            case SHOW_ID:
                qb.setTables(ScraperTables.SHOW_TABLE_NAME);
                qb.appendWhere(ScraperStore.Show.ID + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case SHOW_ONLINE_ID:
                qb.setTables(ScraperTables.SHOW_TABLE_NAME);
                qb.appendWhere(ScraperStore.Show.ONLINE_ID + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case SHOW_NAME:
                handleShowFull(qb);
                qb.appendWhere(ScraperStore.Show.NAME + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case SHOW_ALL_INFOS:
                handleShowFull(qb);
                qb.appendWhere(ScraperStore.Show.ID + " = ");
                qb.appendWhereEscapeString(data);
                extras.putInt("type", BaseTags.TV_SHOW);
                break;

            case SHOW_ALL:
                qb.setTables(ScraperTables.SHOW_TABLE_NAME);
                break;

            case EPISODE_ID:
                qb.setTables(ScraperTables.EPISODE_TABLE_NAME);
                qb.appendWhere(ScraperStore.Episode.ID + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case EPISODE_ALL_INFOS:
                handleEpisodeFull(qb);
                qb.appendWhere(ScraperStore.Episode.ID + " = ");
                qb.appendWhereEscapeString(data);
                extras.putInt("type", BaseTags.TV_SHOW);
                break;

            case EPISODE_ALL_SEASONS:
                qb.setTables(ScraperTables.EPISODE_TABLE_NAME);
                qb.setDistinct(true);
                qb.appendWhere(ScraperStore.Episode.SHOW + " = ");
                qb.appendWhereEscapeString(data);
                // FIXME should we rly overwrite user supplied field?
                projection = new String[] {ScraperStore.Episode.SEASON};
                break;

            case EPISODE_SHOW:
                qb.setTables(ScraperTables.EPISODE_TABLE_NAME);
                qb.appendWhere(ScraperStore.Episode.SHOW + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case ACTOR_ID:
                qb.setTables(ScraperTables.ACTORS_TABLE_NAME);
                qb.appendWhere(ScraperStore.Actor.ID + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case ACTOR_ALL:
                qb.setTables(ScraperTables.ACTORS_TABLE_NAME);
                break;

            case ACTOR_MOVIE:
                qb.setTables(ScraperTables.PLAYS_MOVIE_VIEW_NAME);
                qb.appendWhere(ScraperStore.Movie.Actor.MOVIE + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case ACTOR_SHOW:
                qb.setTables(ScraperTables.PLAYS_SHOW_VIEW_NAME);
                qb.appendWhere(ScraperStore.Show.Actor.SHOW + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case ACTOR_EPISODE:
                qb.setTables(ScraperTables.GUESTS_VIEW_NAME);
                qb.appendWhere(ScraperStore.Episode.Actor.EPISODE + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case ACTOR_NAME:
                qb.setTables(ScraperTables.ACTORS_TABLE_NAME);
                qb.appendWhere(ScraperStore.Actor.NAME + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case GENRE_ID:
                qb.setTables(ScraperTables.GENRES_TABLE_NAME);
                qb.appendWhere(ScraperStore.Genre.ID + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case GENRE_ALL:
                qb.setTables(ScraperTables.GENRES_TABLE_NAME);
                break; 

            case GENRE_MOVIE:
                qb.setTables(ScraperTables.BELONGS_MOVIE_VIEW_NAME);
                qb.appendWhere(ScraperStore.Movie.Genre.MOVIE + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case GENRE_SHOW:
                qb.setTables(ScraperTables.BELONGS_SHOW_VIEW_NAME);
                qb.appendWhere(ScraperStore.Show.Genre.SHOW + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case GENRE_NAME:
                qb.setTables(ScraperTables.GENRES_TABLE_NAME);
                qb.appendWhere(ScraperStore.Genre.NAME + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case DIRECTOR_ID:
                qb.setTables(ScraperTables.DIRECTORS_TABLE_NAME);
                qb.appendWhere(ScraperStore.Director.ID + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case DIRECTOR_ALL:
                qb.setTables(ScraperTables.DIRECTORS_TABLE_NAME);
                break;

            case DIRECTOR_MOVIE:
                qb.setTables(ScraperTables.FILMS_MOVIE_VIEW_NAME);
                qb.appendWhere(ScraperStore.Movie.Director.MOVIE + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case DIRECTOR_SHOW:
                qb.setTables(ScraperTables.FILMS_SHOW_VIEW_NAME);
                qb.appendWhere(ScraperStore.Show.Director.SHOW + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case DIRECTOR_EPISODE:
                qb.setTables(ScraperTables.FILMS_EPISODE_VIEW_NAME);
                qb.appendWhere(ScraperStore.Episode.Director.EPISODE + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case DIRECTOR_NAME:
                qb.setTables(ScraperTables.DIRECTORS_TABLE_NAME);
                qb.appendWhere(ScraperStore.Director.NAME + " = ");
                qb.appendWhereEscapeString(data);
                break;


            case WRITER_ID:
                qb.setTables(ScraperTables.WRITERS_TABLE_NAME);
                qb.appendWhere(ScraperStore.Writer.ID + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case WRITER_ALL:
                qb.setTables(ScraperTables.WRITERS_TABLE_NAME);
                break;

            case WRITER_MOVIE:
                qb.setTables(ScraperTables.WRITERS_MOVIE_VIEW_NAME);
                qb.appendWhere(ScraperStore.Movie.Writer.MOVIE + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case WRITER_SHOW:
                qb.setTables(ScraperTables.WRITERS_SHOW_VIEW_NAME);
                qb.appendWhere(ScraperStore.Show.Writer.SHOW + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case WRITER_EPISODE:
                qb.setTables(ScraperTables.WRITERS_EPISODE_VIEW_NAME);
                qb.appendWhere(ScraperStore.Episode.Writer.EPISODE + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case WRITER_NAME:
                qb.setTables(ScraperTables.WRITERS_TABLE_NAME);
                qb.appendWhere(ScraperStore.Writer.NAME + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case TAGLINE_ID:
                qb.setTables(ScraperTables.TAGLINES_TABLE_NAME);
                qb.appendWhere(ScraperStore.Tagline.ID + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case TAGLINE_ALL:
                qb.setTables(ScraperTables.TAGLINES_TABLE_NAME);
                break;

            case TAGLINE_MOVIE:
                qb.setTables(ScraperTables.TAGLINES_MOVIE_VIEW_NAME);
                qb.appendWhere(ScraperStore.Movie.Tagline.MOVIE + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case TAGLINE_SHOW:
                qb.setTables(ScraperTables.TAGLINES_SHOW_VIEW_NAME);
                qb.appendWhere(ScraperStore.Show.Tagline.SHOW + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case TAGLINE_EPISODE:
                qb.setTables(ScraperTables.TAGLINES_EPISODE_VIEW_NAME);
                qb.appendWhere(ScraperStore.Episode.Tagline.EPISODE + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case TAGLINE_NAME:
                qb.setTables(ScraperTables.TAGLINES_TABLE_NAME);
                qb.appendWhere(ScraperStore.Tagline.NAME + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case PRODUCER_ID:
                qb.setTables(ScraperTables.PRODUCERS_TABLE_NAME);
                qb.appendWhere(ScraperStore.Producer.ID + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case PRODUCER_ALL:
                qb.setTables(ScraperTables.PRODUCERS_TABLE_NAME);
                break;

            case PRODUCER_MOVIE:
                qb.setTables(ScraperTables.PRODUCERS_MOVIE_VIEW_NAME);
                qb.appendWhere(ScraperStore.Movie.Producer.MOVIE + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case PRODUCER_SHOW:
                qb.setTables(ScraperTables.PRODUCERS_SHOW_VIEW_NAME);
                qb.appendWhere(ScraperStore.Show.Producer.SHOW + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case PRODUCER_EPISODE:
                qb.setTables(ScraperTables.PRODUCERS_EPISODE_VIEW_NAME);
                qb.appendWhere(ScraperStore.Episode.Producer.EPISODE + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case PRODUCER_NAME:
                qb.setTables(ScraperTables.PRODUCERS_TABLE_NAME);
                qb.appendWhere(ScraperStore.Producer.NAME + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case SCREENPLAY_ID:
                qb.setTables(ScraperTables.SCREENPLAYS_TABLE_NAME);
                qb.appendWhere(ScraperStore.Screenplay.ID + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case SCREENPLAY_ALL:
                qb.setTables(ScraperTables.SCREENPLAYS_TABLE_NAME);
                break;

            case SCREENPLAY_MOVIE:
                qb.setTables(ScraperTables.SCREENPLAYS_MOVIE_VIEW_NAME);
                qb.appendWhere(ScraperStore.Movie.Screenplay.MOVIE + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case SCREENPLAY_SHOW:
                qb.setTables(ScraperTables.SCREENPLAYS_SHOW_VIEW_NAME);
                qb.appendWhere(ScraperStore.Show.Screenplay.SHOW + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case SCREENPLAY_EPISODE:
                qb.setTables(ScraperTables.SCREENPLAYS_EPISODE_VIEW_NAME);
                qb.appendWhere(ScraperStore.Episode.Screenplay.EPISODE + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case SCREENPLAY_NAME:
                qb.setTables(ScraperTables.SCREENPLAYS_TABLE_NAME);
                qb.appendWhere(ScraperStore.Screenplay.NAME + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case MUSICCOMPOSER_ID:
                qb.setTables(ScraperTables.MUSICCOMPOSERS_TABLE_NAME);
                qb.appendWhere(ScraperStore.Musiccomposer.ID + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case MUSICCOMPOSER_ALL:
                qb.setTables(ScraperTables.MUSICCOMPOSERS_TABLE_NAME);
                break;

            case MUSICCOMPOSER_MOVIE:
                qb.setTables(ScraperTables.MUSICCOMPOSERS_MOVIE_VIEW_NAME);
                qb.appendWhere(ScraperStore.Movie.Musiccomposer.MOVIE + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case MUSICCOMPOSER_SHOW:
                qb.setTables(ScraperTables.MUSICCOMPOSERS_SHOW_VIEW_NAME);
                qb.appendWhere(ScraperStore.Show.Musiccomposer.SHOW + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case MUSICCOMPOSER_EPISODE:
                qb.setTables(ScraperTables.MUSICCOMPOSERS_EPISODE_VIEW_NAME);
                qb.appendWhere(ScraperStore.Episode.Musiccomposer.EPISODE + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case MUSICCOMPOSER_NAME:
                qb.setTables(ScraperTables.MUSICCOMPOSERS_TABLE_NAME);
                qb.appendWhere(ScraperStore.Musiccomposer.NAME + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case SPOKENLANGUAGE_ID:
                qb.setTables(ScraperTables.SPOKENLANGUAGES_TABLE_NAME);
                qb.appendWhere(ScraperStore.Spokenlanguage.ID + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case SPOKENLANGUAGE_ALL:
                qb.setTables(ScraperTables.SPOKENLANGUAGES_TABLE_NAME);
                break;

            case SPOKENLANGUAGE_MOVIE:
                qb.setTables(ScraperTables.SPOKENLANGUAGES_MOVIE_VIEW_NAME);
                qb.appendWhere(ScraperStore.Movie.Spokenlanguage.MOVIE + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case SPOKENLANGUAGE_SHOW:
                qb.setTables(ScraperTables.SPOKENLANGUAGES_SHOW_VIEW_NAME);
                qb.appendWhere(ScraperStore.Show.Spokenlanguage.SHOW + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case SPOKENLANGUAGE_EPISODE:
                qb.setTables(ScraperTables.SPOKENLANGUAGES_EPISODE_VIEW_NAME);
                qb.appendWhere(ScraperStore.Episode.Spokenlanguage.EPISODE + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case SPOKENLANGUAGE_NAME:
                qb.setTables(ScraperTables.SPOKENLANGUAGES_TABLE_NAME);
                qb.appendWhere(ScraperStore.Spokenlanguage.NAME + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case COUNTRY_ID:
                qb.setTables(ScraperTables.COUNTRIES_TABLE_NAME);
                qb.appendWhere(ScraperStore.Country.ID + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case COUNTRY_ALL:
                qb.setTables(ScraperTables.COUNTRIES_TABLE_NAME);
                break;

            case COUNTRY_MOVIE:
                qb.setTables(ScraperTables.COUNTRIES_MOVIE_VIEW_NAME);
                qb.appendWhere(ScraperStore.Movie.Country.MOVIE + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case COUNTRY_SHOW:
                qb.setTables(ScraperTables.COUNTRIES_SHOW_VIEW_NAME);
                qb.appendWhere(ScraperStore.Show.Country.SHOW + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case COUNTRY_EPISODE:
                qb.setTables(ScraperTables.COUNTRIES_EPISODE_VIEW_NAME);
                qb.appendWhere(ScraperStore.Episode.Country.EPISODE + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case COUNTRY_NAME:
                qb.setTables(ScraperTables.COUNTRIES_TABLE_NAME);
                qb.appendWhere(ScraperStore.Country.NAME + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case SEASONPLOT_ID:
                qb.setTables(ScraperTables.SEASONPLOTS_TABLE_NAME);
                qb.appendWhere(ScraperStore.SeasonPlot.ID + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case SEASONPLOT_ALL:
                qb.setTables(ScraperTables.SEASONPLOTS_TABLE_NAME);
                break;

            case SEASONPLOT_SHOW:
                qb.setTables(ScraperTables.SEASONPLOTS_SHOW_VIEW_NAME);
                qb.appendWhere(ScraperStore.Show.SeasonPlot.SHOW + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case SEASONPLOT_NAME:
                qb.setTables(ScraperTables.SEASONPLOTS_TABLE_NAME);
                qb.appendWhere(ScraperStore.SeasonPlot.NAME + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case STUDIO_ID:
                qb.setTables(ScraperTables.STUDIOS_TABLE_NAME);
                qb.appendWhere(ScraperStore.Studio.ID + "=");
                qb.appendWhereEscapeString(data);
                break;

            case STUDIO_ALL:
                qb.setTables(ScraperTables.STUDIOS_TABLE_NAME);
                break;

            case STUDIO_MOVIE:
                qb.setTables(ScraperTables.PRODUCES_MOVIE_VIEW_NAME);
                qb.appendWhere(ScraperStore.Movie.Studio.MOVIE + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case STUDIO_SHOW:
                qb.setTables(ScraperTables.PRODUCES_SHOW_VIEW_NAME);
                qb.appendWhere(ScraperStore.Show.Studio.SHOW + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case STUDIO_NAME:
                qb.setTables(ScraperTables.STUDIOS_TABLE_NAME);
                qb.appendWhere(ScraperStore.Studio.NAME + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case EPISODESHOWCOMBINED_ID:
                handleEpisodeShowCombined(qb);
                qb.appendWhere(ScraperStore.EpisodeShowCombined.SCRAPER_ID + " = ");
                qb.appendWhereEscapeString(data);
                break;

            case EPISODESHOWCOMBINED_ALL:
                handleEpisodeShowCombined(qb);
                break;

            case ALL_VIDEOS_ALL:
                qb.setTables(ScraperTables.ALL_VIDEOS_VIEW_NAME);
                break;

            case SEASONS_BYSHOWID:
                qb.appendWhere(ScraperStore.Seasons.SHOW_ID + "=");
                qb.appendWhereEscapeString(data);
                //$FALL-THROUGH$
            case SEASONS:
                qb.setTables(ScraperTables.SEASONS_VIEW_NAME);
                break;

            case MOVIE_POSTERS_ID:
                useId = true;
                //$FALL-THROUGH$
            case MOVIE_POSTERS_MOVIE_ID:
                qb.appendWhere(useId ? BaseColumns._ID  + "=" : ScraperStore.MoviePosters.MOVIE_ID + "=");
                qb.appendWhereEscapeString(data);
                //$FALL-THROUGH$
            case MOVIE_POSTERS:
                qb.setTables(ScraperTables.MOVIE_POSTERS_TABLE_NAME);
                break;

            case MOVIE_BACKDROPS_ID:
                useId = true;
                //$FALL-THROUGH$
            case MOVIE_BACKDROPS_MOVIE_ID:
                qb.appendWhere(useId ? BaseColumns._ID  + "=" : ScraperStore.MovieBackdrops.MOVIE_ID + "=");
                qb.appendWhereEscapeString(data);
                //$FALL-THROUGH$
            case MOVIE_BACKDROPS:
                qb.setTables(ScraperTables.MOVIE_BACKDROPS_TABLE_NAME);
                break;

            case SHOW_POSTERS_ID:
                useId = true;
                //$FALL-THROUGH$
            case SHOW_POSTERS_SHOW_ID:
                qb.appendWhere(useId ? BaseColumns._ID  + "=" : ScraperStore.ShowPosters.SHOW_ID + "=");
                qb.appendWhereEscapeString(data);
                //$FALL-THROUGH$
            case SHOW_POSTERS:
                qb.setTables(ScraperTables.SHOW_POSTERS_TABLE_NAME);
                break;

            case SHOW_BACKDROPS_ID:
                useId = true;
                //$FALL-THROUGH$
            case SHOW_BACKDROPS_SHOW_ID:
                qb.appendWhere(useId ? BaseColumns._ID  + "=" : ScraperStore.ShowBackdrops.SHOW_ID + "=");
                qb.appendWhereEscapeString(data);
                //$FALL-THROUGH$
            case SHOW_BACKDROPS:
                qb.setTables(ScraperTables.SHOW_BACKDROPS_TABLE_NAME);
                break;

            case SHOW_NETWORKLOGOS_ID:
                useId = true;
                //$FALL-THROUGH$
            case SHOW_NETWORKLOGOS_SHOW_ID:
                qb.appendWhere(useId ? BaseColumns._ID  + "=" : ScraperStore.ShowNetworkLogos.SHOW_ID + "=");
                qb.appendWhereEscapeString(data);
                //$FALL-THROUGH$
            case SHOW_NETWORKLOGOS:
                qb.setTables(ScraperTables.SHOW_NETWORKLOGOS_TABLE_NAME);
                break;
            case SHOW_ACTORPHOTOS_ID:
                useId = true;
                //$FALL-THROUGH$
            case SHOW_ACTORPHOTOS_SHOW_ID:
                qb.appendWhere(useId ? BaseColumns._ID  + "=" : ScraperStore.ShowActorPhotos.SHOW_ID + "=");
                qb.appendWhereEscapeString(data);
                //$FALL-THROUGH$
            case SHOW_ACTORPHOTOS:
                qb.setTables(ScraperTables.SHOW_ACTORPHOTOS_TABLE_NAME);
                break;
            case MOVIE_ACTORPHOTOS_ID:
                useId = true;
                //$FALL-THROUGH$
            case MOVIE_ACTORPHOTOS_MOVIE_ID:
                qb.appendWhere(useId ? BaseColumns._ID  + "=" : ScraperStore.MovieActorPhotos.MOVIE_ID + "=");
                qb.appendWhereEscapeString(data);
                //$FALL-THROUGH$
            case MOVIE_ACTORPHOTOS:
                qb.setTables(ScraperTables.MOVIE_ACTORPHOTOS_TABLE_NAME);
                break;
            case MOVIE_STUDIOLOGOS_ID:
                useId = true;
                //$FALL-THROUGH$
            case MOVIE_STUDIOLOGOS_MOVIE_ID:
                qb.appendWhere(useId ? BaseColumns._ID  + "=" : ScraperStore.MovieStudioLogos.MOVIE_ID + "=");
                qb.appendWhereEscapeString(data);
                //$FALL-THROUGH$
            case MOVIE_STUDIOLOGOS:
                qb.setTables(ScraperTables.MOVIE_STUDIOLOGOS_TABLE_NAME);
                break;
            case MOVIE_CLEARLOGOS_ID:
                useId = true;
                //$FALL-THROUGH$
            case MOVIE_CLEARLOGOS_MOVIE_ID:
                qb.appendWhere(useId ? BaseColumns._ID  + "=" : ScraperStore.MovieClearLogos.MOVIE_ID + "=");
                qb.appendWhereEscapeString(data);
                //$FALL-THROUGH$
            case MOVIE_CLEARLOGOS:
                qb.setTables(ScraperTables.MOVIE_CLEARLOGOS_TABLE_NAME);
                break;
            case SHOW_CLEARLOGOS_ID:
                useId = true;
                //$FALL-THROUGH$
            case SHOW_CLEARLOGOS_SHOW_ID:
                qb.appendWhere(useId ? BaseColumns._ID  + "=" : ScraperStore.ShowClearLogos.SHOW_ID + "=");
                qb.appendWhereEscapeString(data);
                //$FALL-THROUGH$
            case SHOW_CLEARLOGOS:
                qb.setTables(ScraperTables.SHOW_CLEARLOGOS_TABLE_NAME);
                break;
            case SHOW_STUDIOLOGOS_ID:
                useId = true;
                //$FALL-THROUGH$
            case SHOW_STUDIOLOGOS_SHOW_ID:
                qb.appendWhere(useId ? BaseColumns._ID  + "=" : ScraperStore.ShowStudioLogos.SHOW_ID + "=");
                qb.appendWhereEscapeString(data);
                //$FALL-THROUGH$
            case SHOW_STUDIOLOGOS:
                qb.setTables(ScraperTables.SHOW_STUDIOLOGOS_TABLE_NAME);
                break;
            case MOVIE_TRAILERS_ID:
                useId = true;
                //$FALL-THROUGH$
            case MOVIE_TRAILERS_MOVIE_ID:
                qb.appendWhere(useId ? BaseColumns._ID  + "=" : ScraperStore.MovieTrailers.MOVIE_ID + "=");
                qb.appendWhereEscapeString(data);
                //$FALL-THROUGH$
            case MOVIE_TRAILERS:
                qb.setTables(ScraperTables.MOVIE_TRAILERS_TABLE_NAME);
                break;

            case MOVIE_COLLECTION:
                qb.setTables(ScraperTables.MOVIE_COLLECTION_TABLE_NAME);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        if(DBG) Log.d(TAG, "Query handling ended.");
        SQLiteDatabase db = mDbHolder.get();
        // TODO: try/catch used but this needs fixing!
        try {
            c = qb.query(db, projection, selection, selectionArgs, null,
                    null, sortOrder);
        } catch (Throwable t) {
            Log.e(TAG, "query: qb.query failed with projection=" + projection + ", selection=" + selection + ", selectionArgs=" + selectionArgs, t);
            c = null;
        }
        if (c != null) {
            // Tell the cursor what uri to watch, so it knows when its source data changes
            c.setNotificationUri(mCr, uri);
            c.respond(extras);
        }
        return c;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (DBG) Log.d(TAG, "openFile " + uri + " mode:" + mode);
        int match = sUriMatcher.match(uri);
        boolean thumb = uri.getQueryParameter("thumb") != null;
        String table = null;
        String[] cols = new String[2];
        ScraperImage.Type type = null;
        switch (match) {
            case MOVIE_POSTERS_ID:
                table = ScraperTables.MOVIE_POSTERS_TABLE_NAME;
                cols[0] = thumb ? ScraperStore.MoviePosters.THUMB_URL : ScraperStore.MoviePosters.LARGE_URL;
                cols[1] = thumb ? ScraperStore.MoviePosters.THUMB_FILE : ScraperStore.MoviePosters.LARGE_FILE;
                type = Type.MOVIE_POSTER;
                break;
            case MOVIE_BACKDROPS_ID:
                table = ScraperTables.MOVIE_BACKDROPS_TABLE_NAME;
                cols[0] = thumb ? ScraperStore.MovieBackdrops.THUMB_URL : ScraperStore.MovieBackdrops.LARGE_URL;
                cols[1] = thumb ? ScraperStore.MovieBackdrops.THUMB_FILE : ScraperStore.MovieBackdrops.LARGE_FILE;
                type = Type.MOVIE_BACKDROP;
                break;
            case SHOW_POSTERS_ID:
                table = ScraperTables.SHOW_POSTERS_TABLE_NAME;
                cols[0] = thumb ? ScraperStore.ShowPosters.THUMB_URL : ScraperStore.ShowPosters.LARGE_URL;
                cols[1] = thumb ? ScraperStore.ShowPosters.THUMB_FILE : ScraperStore.ShowPosters.LARGE_FILE;
                type = Type.SHOW_POSTER;
                break;
            case SHOW_BACKDROPS_ID:
                table = ScraperTables.SHOW_BACKDROPS_TABLE_NAME;
                cols[0] = thumb ? ScraperStore.ShowBackdrops.THUMB_URL : ScraperStore.ShowBackdrops.LARGE_URL;
                cols[1] = thumb ? ScraperStore.ShowBackdrops.THUMB_FILE : ScraperStore.ShowBackdrops.LARGE_FILE;
                type = Type.SHOW_BACKDROP;
                break;
            case SHOW_NETWORKLOGOS_ID:
                table = ScraperTables.SHOW_NETWORKLOGOS_TABLE_NAME;
                cols[0] = thumb ? ScraperStore.ShowNetworkLogos.THUMB_URL : ScraperStore.ShowNetworkLogos.LARGE_URL;
                cols[1] = thumb ? ScraperStore.ShowNetworkLogos.THUMB_FILE : ScraperStore.ShowNetworkLogos.LARGE_FILE;
                type = Type.SHOW_NETWORK;
                break;
            case SHOW_ACTORPHOTOS_ID:
                table = ScraperTables.SHOW_ACTORPHOTOS_TABLE_NAME;
                cols[0] = thumb ? ScraperStore.ShowActorPhotos.THUMB_URL : ScraperStore.ShowActorPhotos.LARGE_URL;
                cols[1] = thumb ? ScraperStore.ShowActorPhotos.THUMB_FILE : ScraperStore.ShowActorPhotos.LARGE_FILE;
                type = Type.SHOW_ACTOR_PHOTO;
                break;
            case MOVIE_ACTORPHOTOS_ID:
                table = ScraperTables.MOVIE_ACTORPHOTOS_TABLE_NAME;
                cols[0] = thumb ? ScraperStore.MovieActorPhotos.THUMB_URL : ScraperStore.MovieActorPhotos.LARGE_URL;
                cols[1] = thumb ? ScraperStore.MovieActorPhotos.THUMB_FILE : ScraperStore.MovieActorPhotos.LARGE_FILE;
                type = Type.MOVIE_ACTORPHOTO;
                break;
            case MOVIE_STUDIOLOGOS_ID:
                table = ScraperTables.MOVIE_STUDIOLOGOS_TABLE_NAME;
                cols[0] = thumb ? ScraperStore.MovieStudioLogos.THUMB_URL : ScraperStore.MovieStudioLogos.LARGE_URL;
                cols[1] = thumb ? ScraperStore.MovieStudioLogos.THUMB_FILE : ScraperStore.MovieStudioLogos.LARGE_FILE;
                type = Type.MOVIE_STUDIOLOGO;
                break;
            case MOVIE_CLEARLOGOS_ID:
                table = ScraperTables.MOVIE_CLEARLOGOS_TABLE_NAME;
                cols[0] = thumb ? ScraperStore.MovieClearLogos.THUMB_URL : ScraperStore.MovieClearLogos.LARGE_URL;
                cols[1] = thumb ? ScraperStore.MovieClearLogos.THUMB_FILE : ScraperStore.MovieClearLogos.LARGE_FILE;
                type = Type.MOVIE_CLEARLOGO;
                break;
            case SHOW_CLEARLOGOS_ID:
                table = ScraperTables.SHOW_CLEARLOGOS_TABLE_NAME;
                cols[0] = thumb ? ScraperStore.ShowClearLogos.THUMB_URL : ScraperStore.ShowClearLogos.LARGE_URL;
                cols[1] = thumb ? ScraperStore.ShowClearLogos.THUMB_FILE : ScraperStore.ShowClearLogos.LARGE_FILE;
                type = Type.SHOW_TITLE_CLEARLOGO;
                break;
            case SHOW_STUDIOLOGOS_ID:
                table = ScraperTables.SHOW_STUDIOLOGOS_TABLE_NAME;
                cols[0] = thumb ? ScraperStore.ShowStudioLogos.THUMB_URL : ScraperStore.ShowStudioLogos.LARGE_URL;
                cols[1] = thumb ? ScraperStore.ShowStudioLogos.THUMB_FILE : ScraperStore.ShowStudioLogos.LARGE_FILE;
                type = Type.SHOW_STUDIOLOGO;
                break;
            default:
                throw new FileNotFoundException("No files supported by provider at " + uri);
        }
        SQLiteDatabase db = mDbHolder.get();
        Cursor c = db.query(table, cols, "_id=?", new String[] { uri.getLastPathSegment() }, null, null, "_id");

        String url = null;
        String file = null;

        if (c != null) {
            if (c.moveToFirst()) {
                url = c.getString(0);
                file = c.getString(1);
            }
            c.close();
        }

        int modeBits = modeToMode(uri, mode);

        // we need to know the filename
        if (file != null && !file.isEmpty()) {
            File f = new File(file);
            // if that file already exists return it
            if (f.exists()) {
                return ParcelFileDescriptor.open(f, modeBits);
            }
            // else fallback to downloading.
            if (url != null && !url.isEmpty() && type != null) {
                ScraperImage image = new ScraperImage(type, null);
                if (thumb) {
                    // no idea what size those should be / if they make sense.
                    int width = ScraperImage.POSTER_WIDTH;
                    int height = ScraperImage.POSTER_HEIGHT;
                    if (type == Type.MOVIE_BACKDROP || type == Type.SHOW_BACKDROP) {
                        width = mContext.getResources().getDimensionPixelSize(R.dimen.video_info_backdrop_chooser_image_width);
                        height = mContext.getResources().getDimensionPixelSize(R.dimen.video_info_backdrop_chooser_image_height);
                    }
                    image.setThumbFile(file);
                    image.setThumbUrl(url);
                    image.downloadThumb(mContext, width, height);
                } else {
                    image.setLargeFile(file);
                    image.setLargeUrl(url);
                    image.download(mContext);
                }

                // check again
                if (f.exists()) {
                    return ParcelFileDescriptor.open(f, modeBits);
                } // else fail

            }
        }
        throw new FileNotFoundException("Database does not contain file / url for " + uri);
    }

    static public int modeToMode(Uri uri, String mode) throws FileNotFoundException {
        int modeBits;
        if ("r".equals(mode)) {
            modeBits = ParcelFileDescriptor.MODE_READ_ONLY;
        } else if ("w".equals(mode) || "wt".equals(mode)) {
            modeBits = ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_CREATE
                    | ParcelFileDescriptor.MODE_TRUNCATE;
        } else if ("wa".equals(mode)) {
            modeBits = ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_CREATE
                    | ParcelFileDescriptor.MODE_APPEND;
        } else if ("rw".equals(mode)) {
            modeBits = ParcelFileDescriptor.MODE_READ_WRITE
                    | ParcelFileDescriptor.MODE_CREATE;
        } else if ("rwt".equals(mode)) {
            modeBits = ParcelFileDescriptor.MODE_READ_WRITE
                    | ParcelFileDescriptor.MODE_CREATE
                    | ParcelFileDescriptor.MODE_TRUNCATE;
        } else {
            throw new FileNotFoundException("Bad mode for " + uri + ": "
                    + mode);
        }
        return modeBits;
    }

    /*
     * Update a row in a content URI
     *
     * NOTE : assumes that the entry to update is identified by selection and
     *        selectionArgs (do not provide a name or id in the uri for instance)
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        String table;
        switch (sUriMatcher.match(uri)) {
            case EPISODE_ID:
                selection = "_id=?";
                selectionArgs = new String[] { uri.getLastPathSegment() };
                //$FALL-THROUGH$
            case EPISODE:
                table = ScraperTables.EPISODE_TABLE_NAME;
                break;

            case SHOW_ID:
                selection = "_id=?";
                selectionArgs = new String[] { uri.getLastPathSegment() };
                //$FALL-THROUGH$
            case SHOW:
            case SHOW_NAME:
                table = ScraperTables.SHOW_TABLE_NAME;
                break;

            case MOVIE_ID:
                selection = "_id=?";
                selectionArgs = new String[] { uri.getLastPathSegment() };
                //$FALL-THROUGH$
            case MOVIE:
                table = ScraperTables.MOVIE_TABLE_NAME;
                break;

            case MOVIE_BACKDROPS:
                table = ScraperTables.MOVIE_BACKDROPS_TABLE_NAME;
                break;
            case MOVIE_POSTERS:
                table = ScraperTables.MOVIE_POSTERS_TABLE_NAME;
                break;
            case SHOW_BACKDROPS:
                table = ScraperTables.SHOW_BACKDROPS_TABLE_NAME;
                break;
            case SHOW_NETWORKLOGOS:
                table = ScraperTables.SHOW_NETWORKLOGOS_TABLE_NAME;
                break;
            case SHOW_ACTORPHOTOS:
                table = ScraperTables.SHOW_ACTORPHOTOS_TABLE_NAME;
                break;
            case MOVIE_ACTORPHOTOS:
                table = ScraperTables.MOVIE_ACTORPHOTOS_TABLE_NAME;
                break;
            case MOVIE_STUDIOLOGOS:
                table = ScraperTables.MOVIE_STUDIOLOGOS_TABLE_NAME;
                break;
            case MOVIE_CLEARLOGOS:
                table = ScraperTables.MOVIE_CLEARLOGOS_TABLE_NAME;
                break;
            case SHOW_CLEARLOGOS:
                table = ScraperTables.SHOW_CLEARLOGOS_TABLE_NAME;
                break;
            case SHOW_STUDIOLOGOS:
                table = ScraperTables.SHOW_STUDIOLOGOS_TABLE_NAME;
                break;
            case SHOW_POSTERS:
                table = ScraperTables.SHOW_POSTERS_TABLE_NAME;
                break;
            case MOVIE_COLLECTION_ID:
                selection = "_id=?";
                selectionArgs = new String[] { uri.getLastPathSegment() };
                //$FALL-THROUGH$
            case MOVIE_COLLECTION:
                table = ScraperTables.MOVIE_COLLECTION_TABLE_NAME;
                break;

            default:
                throw new IllegalArgumentException("URI not supported in update(): " + uri);
        }
        SQLiteDatabase db = mDbHolder.get();
        int updated = db.update(table, values,
                selection, selectionArgs);
        if (updated > 0 && !db.inTransaction()) {
            mCr.notifyChange(uri, null);
            mCr.notifyChange(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, null);
        }
        return updated;
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        if (DBG) Log.d(TAG, "bulkInsert");
        int result = 0;
        SQLiteDatabase db = mDbHolder.get();
        db.beginTransaction();
        try {
            result = super.bulkInsert(uri, values);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return result;
    }

    private static void handleEpisodeFull(SQLiteQueryBuilder qb) {
    	if(DBG) Log.d(TAG, "File is a TV show.");

        qb.setTables(ScraperTables.EPISODE_TABLE_NAME +
                " LEFT JOIN " + ScraperTables.FILMS_EPISODE_VIEW_NAME + " ON (" +
                ScraperTables.EPISODE_TABLE_NAME + "." +
                ScraperStore.Episode.ID + " = " +
                ScraperTables.FILMS_EPISODE_VIEW_NAME + "." +
                ScraperStore.Episode.Director.EPISODE + ") " +
                " LEFT JOIN " + ScraperTables.WRITERS_EPISODE_VIEW_NAME + " ON (" +
                ScraperTables.EPISODE_TABLE_NAME + "." +
                ScraperStore.Episode.ID + " = " +
                ScraperTables.WRITERS_EPISODE_VIEW_NAME + "." +
                ScraperStore.Episode.Writer.EPISODE + ") " +
                " LEFT JOIN " + ScraperTables.TAGLINES_EPISODE_VIEW_NAME + " ON (" +
                ScraperTables.EPISODE_TABLE_NAME + "." +
                ScraperStore.Episode.ID + " = " +
                ScraperTables.TAGLINES_EPISODE_VIEW_NAME + "." +
                ScraperStore.Episode.Tagline.EPISODE + ") " +
                " LEFT JOIN " + ScraperTables.PRODUCERS_EPISODE_VIEW_NAME + " ON (" +
                ScraperTables.EPISODE_TABLE_NAME + "." +
                ScraperStore.Episode.ID + " = " +
                ScraperTables.PRODUCERS_EPISODE_VIEW_NAME + "." +
                ScraperStore.Episode.Producer.EPISODE + ") " +
                " LEFT JOIN " + ScraperTables.SCREENPLAYS_EPISODE_VIEW_NAME + " ON (" +
                ScraperTables.EPISODE_TABLE_NAME + "." +
                ScraperStore.Episode.ID + " = " +
                ScraperTables.SCREENPLAYS_EPISODE_VIEW_NAME + "." +
                ScraperStore.Episode.Screenplay.EPISODE + ") " +
                " LEFT JOIN " + ScraperTables.MUSICCOMPOSERS_EPISODE_VIEW_NAME + " ON (" +
                ScraperTables.EPISODE_TABLE_NAME + "." +
                ScraperStore.Episode.ID + " = " +
                ScraperTables.MUSICCOMPOSERS_EPISODE_VIEW_NAME + "." +
                ScraperStore.Episode.Musiccomposer.EPISODE + ") " +
                " LEFT JOIN " + ScraperTables.SPOKENLANGUAGES_EPISODE_VIEW_NAME + " ON (" +
                ScraperTables.EPISODE_TABLE_NAME + "." +
                ScraperStore.Episode.ID + " = " +
                ScraperTables.SPOKENLANGUAGES_EPISODE_VIEW_NAME + "." +
                ScraperStore.Episode.Spokenlanguage.EPISODE + ") " +
                " LEFT JOIN " + ScraperTables.COUNTRIES_EPISODE_VIEW_NAME + " ON (" +
                ScraperTables.EPISODE_TABLE_NAME + "." +
                ScraperStore.Episode.ID + " = " +
                ScraperTables.COUNTRIES_EPISODE_VIEW_NAME + "." +
                ScraperStore.Episode.Country.EPISODE + ") " +
                "LEFT JOIN " + ScraperTables.GUESTS_VIEW_NAME + " ON (" +
                ScraperTables.EPISODE_TABLE_NAME + "." +
                ScraperStore.Episode.ID + " = " + 
                ScraperTables.GUESTS_VIEW_NAME + "." +
                ScraperStore.Episode.Actor.EPISODE + ")");
    }

    private static void handleMovieFull(SQLiteQueryBuilder qb) {
    	if(DBG) Log.d(TAG, "File is a movie.");

        qb.setTables(ScraperTables.MOVIE_TABLE_NAME +
                " LEFT JOIN " + ScraperTables.FILMS_MOVIE_VIEW_NAME + " ON (" +
                ScraperTables.MOVIE_TABLE_NAME + "." +
                ScraperStore.Movie.ID + " = " +
                ScraperTables.FILMS_MOVIE_VIEW_NAME + "." +
                ScraperStore.Movie.Director.MOVIE + ") " +
                " LEFT JOIN " + ScraperTables.WRITERS_MOVIE_VIEW_NAME + " ON (" +
                ScraperTables.MOVIE_TABLE_NAME + "." +
                ScraperStore.Movie.ID + " = " +
                ScraperTables.WRITERS_MOVIE_VIEW_NAME + "." +
                ScraperStore.Movie.Writer.MOVIE + ") " +
                " LEFT JOIN " + ScraperTables.TAGLINES_MOVIE_VIEW_NAME + " ON (" +
                ScraperTables.MOVIE_TABLE_NAME + "." +
                ScraperStore.Movie.ID + " = " +
                ScraperTables.TAGLINES_MOVIE_VIEW_NAME + "." +
                ScraperStore.Movie.Tagline.MOVIE + ") " +
                " LEFT JOIN " + ScraperTables.PRODUCERS_MOVIE_VIEW_NAME + " ON (" +
                ScraperTables.MOVIE_TABLE_NAME + "." +
                ScraperStore.Movie.ID + " = " +
                ScraperTables.PRODUCERS_MOVIE_VIEW_NAME + "." +
                ScraperStore.Movie.Producer.MOVIE + ") " +
                " LEFT JOIN " + ScraperTables.SCREENPLAYS_MOVIE_VIEW_NAME + " ON (" +
                ScraperTables.MOVIE_TABLE_NAME + "." +
                ScraperStore.Movie.ID + " = " +
                ScraperTables.SCREENPLAYS_MOVIE_VIEW_NAME + "." +
                ScraperStore.Movie.Screenplay.MOVIE + ") " +
                " LEFT JOIN " + ScraperTables.MUSICCOMPOSERS_MOVIE_VIEW_NAME + " ON (" +
                ScraperTables.MOVIE_TABLE_NAME + "." +
                ScraperStore.Movie.ID + " = " +
                ScraperTables.MUSICCOMPOSERS_MOVIE_VIEW_NAME + "." +
                ScraperStore.Movie.Musiccomposer.MOVIE + ") " +
                " LEFT JOIN " + ScraperTables.SPOKENLANGUAGES_MOVIE_VIEW_NAME + " ON (" +
                ScraperTables.MOVIE_TABLE_NAME + "." +
                ScraperStore.Movie.ID + " = " +
                ScraperTables.SPOKENLANGUAGES_MOVIE_VIEW_NAME + "." +
                ScraperStore.Movie.Spokenlanguage.MOVIE + ") " +
                " LEFT JOIN " + ScraperTables.COUNTRIES_MOVIE_VIEW_NAME + " ON (" +
                ScraperTables.MOVIE_TABLE_NAME + "." +
                ScraperStore.Movie.ID + " = " +
                ScraperTables.COUNTRIES_MOVIE_VIEW_NAME + "." +
                ScraperStore.Movie.Country.MOVIE + ") " +
                "LEFT JOIN " + ScraperTables.PLAYS_MOVIE_VIEW_NAME + " ON (" +
                ScraperTables.MOVIE_TABLE_NAME + "." +
                ScraperStore.Movie.ID + " = " + 
                ScraperTables.PLAYS_MOVIE_VIEW_NAME + "." +
                ScraperStore.Movie.Actor.MOVIE + ") " +
                "LEFT JOIN " + ScraperTables.PRODUCES_MOVIE_VIEW_NAME + " ON (" +
                ScraperTables.MOVIE_TABLE_NAME + "." +
                ScraperStore.Movie.ID + " = " + 
                ScraperTables.PRODUCES_MOVIE_VIEW_NAME + "." +
                ScraperStore.Movie.Studio.MOVIE + ") " +
                "LEFT JOIN " + ScraperTables.BELONGS_MOVIE_VIEW_NAME + " ON (" +
                ScraperTables.MOVIE_TABLE_NAME + "." +
                ScraperStore.Movie.ID + " = " + 
                ScraperTables.BELONGS_MOVIE_VIEW_NAME + "." +
                ScraperStore.Movie.Genre.MOVIE + ")");
    }

    private static void handleShowFull(SQLiteQueryBuilder qb) {
    	if(DBG) Log.d(TAG, "File is a TV show.");

        qb.setTables(ScraperTables.SHOW_TABLE_NAME +
                " LEFT JOIN " +
                ScraperTables.FILMS_SHOW_VIEW_NAME + " ON (" +
                ScraperTables.SHOW_TABLE_NAME + "." +
                ScraperStore.Show.ID + " = " +
                ScraperTables.FILMS_SHOW_VIEW_NAME + "." +
                ScraperStore.Show.Director.SHOW + ") " +
                " LEFT JOIN " +
                ScraperTables.WRITERS_SHOW_VIEW_NAME + " ON (" +
                ScraperTables.SHOW_TABLE_NAME + "." +
                ScraperStore.Show.ID + " = " +
                ScraperTables.WRITERS_SHOW_VIEW_NAME + "." +
                ScraperStore.Show.Writer.SHOW + ") " +
                " LEFT JOIN " +
                ScraperTables.TAGLINES_SHOW_VIEW_NAME + " ON (" +
                ScraperTables.SHOW_TABLE_NAME + "." +
                ScraperStore.Show.ID + " = " +
                ScraperTables.TAGLINES_SHOW_VIEW_NAME + "." +
                ScraperStore.Show.Tagline.SHOW + ") " +
                " LEFT JOIN " +
                ScraperTables.PRODUCERS_SHOW_VIEW_NAME + " ON (" +
                ScraperTables.SHOW_TABLE_NAME + "." +
                ScraperStore.Show.ID + " = " +
                ScraperTables.PRODUCERS_SHOW_VIEW_NAME + "." +
                ScraperStore.Show.Producer.SHOW + ") " +
                " LEFT JOIN " +
                ScraperTables.SCREENPLAYS_SHOW_VIEW_NAME + " ON (" +
                ScraperTables.SHOW_TABLE_NAME + "." +
                ScraperStore.Show.ID + " = " +
                ScraperTables.SCREENPLAYS_SHOW_VIEW_NAME + "." +
                ScraperStore.Show.Screenplay.SHOW + ") " +
                " LEFT JOIN " +
                ScraperTables.MUSICCOMPOSERS_SHOW_VIEW_NAME + " ON (" +
                ScraperTables.SHOW_TABLE_NAME + "." +
                ScraperStore.Show.ID + " = " +
                ScraperTables.MUSICCOMPOSERS_SHOW_VIEW_NAME + "." +
                ScraperStore.Show.Musiccomposer.SHOW + ") " +
                " LEFT JOIN " +
                ScraperTables.SPOKENLANGUAGES_SHOW_VIEW_NAME + " ON (" +
                ScraperTables.SHOW_TABLE_NAME + "." +
                ScraperStore.Show.ID + " = " +
                ScraperTables.SPOKENLANGUAGES_SHOW_VIEW_NAME + "." +
                ScraperStore.Show.Spokenlanguage.SHOW + ") " +
                " LEFT JOIN " +
                ScraperTables.COUNTRIES_SHOW_VIEW_NAME + " ON (" +
                ScraperTables.SHOW_TABLE_NAME + "." +
                ScraperStore.Show.ID + " = " +
                ScraperTables.COUNTRIES_SHOW_VIEW_NAME + "." +
                ScraperStore.Show.Country.SHOW + ") " +
                " LEFT JOIN " +
                ScraperTables.SEASONPLOTS_SHOW_VIEW_NAME + " ON (" +
                ScraperTables.SHOW_TABLE_NAME + "." +
                ScraperStore.Show.ID + " = " +
                ScraperTables.SEASONPLOTS_SHOW_VIEW_NAME + "." +
                ScraperStore.Show.SeasonPlot.SHOW + ") " +
                "LEFT JOIN " + ScraperTables.PLAYS_SHOW_VIEW_NAME + " ON (" +
                ScraperTables.SHOW_TABLE_NAME + "." +
                ScraperStore.Show.ID + " = " + 
                ScraperTables.PLAYS_SHOW_VIEW_NAME + "." +
                ScraperStore.Show.Actor.SHOW + ") " +
                "LEFT JOIN " + ScraperTables.PRODUCES_SHOW_VIEW_NAME + " ON (" +
                ScraperTables.SHOW_TABLE_NAME + "." +
                ScraperStore.Show.ID + " = " + 
                ScraperTables.PRODUCES_SHOW_VIEW_NAME + "." +
                ScraperStore.Show.Studio.SHOW + ") " +
                "LEFT JOIN " + ScraperTables.BELONGS_SHOW_VIEW_NAME + " ON (" +
                ScraperTables.SHOW_TABLE_NAME + "." +
                ScraperStore.Show.ID + " = " + 
                ScraperTables.BELONGS_SHOW_VIEW_NAME + "." +
                ScraperStore.Show.Genre.SHOW + ")");
    }

    private static void handleEpisodeShowCombined(SQLiteQueryBuilder qb) {
        // select
        //   episode._id, show.name_show, show.cover_show, episode.name_episode, episode.number_episode, episode.season_episode
        // from
        //   show LEFT JOIN episode on show._id = episode.show_episode
        qb.setTables(ScraperTables.SHOW_TABLE_NAME + " LEFT JOIN " +
                ScraperTables.EPISODE_TABLE_NAME + " ON " +
                ScraperTables.SHOW_TABLE_NAME + "." + ScraperStore.Show.ID + "=" +
                ScraperTables.EPISODE_TABLE_NAME + "." + ScraperStore.Episode.SHOW
                );
    }

    /**
     * @return true if this implementation handles the match
     */
    public static boolean handles(int match) {
        // simple check is good enough
        return match > SCRAPER_PROVIDER_OFFSET;
    }
}