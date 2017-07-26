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

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import com.archos.medialib.R;
import com.archos.medialib.IMediaMetadataRetriever;
import com.archos.medialib.MediaFactory;
import com.archos.mediaprovider.ArchosMediaCommon;
import com.archos.mediaprovider.DbHolder;
import com.archos.mediaprovider.MediaThumbnailService;
import com.archos.mediaprovider.music.MusicStore.Audio;
import com.archos.mediaprovider.music.MusicStore.MediaColumns;
import com.archos.mediaprovider.music.MusicStore.Audio.AlbumColumns;
import com.archos.mediaprovider.music.MusicStore.Audio.ArtistColumns;
import com.archos.mediaprovider.music.MusicStore.Audio.AudioColumns;
import com.archos.mediaprovider.music.MusicStore.Audio.GenresColumns;
import com.archos.mediaprovider.music.MusicStore.Audio.PlaylistsColumns;
import com.archos.mediaprovider.music.MusicStore.Files.FileColumns;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

public class MusicProvider extends ContentProvider {
    private static final String TAG =  ArchosMediaCommon.TAG_PREFIX + "MusicProvider";
    private static final boolean LOCAL_DBG = false;
    private static final boolean DBG = ArchosMediaCommon.PACKAGE_DBG & LOCAL_DBG;

    private DbHolder mDbHolder;
    private Handler mThumbHandler;

    private static final int ALBUM_THUMB = 1;
    private ContentResolver mCr;

    /** place for album thums */
    private String mAlbumThumbFolder;

    private static final String ALBUM_THUMB_FOLDER_NAME = "album_thumbs";

    private static final HashMap<String, String> sArtistAlbumsMap = new HashMap<String, String>();

    public MusicProvider() {
    }

    @Override
    public boolean onCreate() {
        if (DBG) Log.d(TAG, "onCreate");
        Context context = getContext();
        mAlbumThumbFolder = context.getDir(ALBUM_THUMB_FOLDER_NAME, Context.MODE_WORLD_READABLE).getPath();
        fillArtistAlbumsMap();
        mSearchColsBasic[SEARCH_COLUMN_BASIC_TEXT2] =
                mSearchColsBasic[SEARCH_COLUMN_BASIC_TEXT2].replaceAll(
                        "%1", context.getString(R.string.artist_label));
        mSearchColsArchos[SEARCH_COLUMN_BASIC_TEXT2] =
                mSearchColsArchos[SEARCH_COLUMN_BASIC_TEXT2].replaceAll(
                        "%1", context.getString(R.string.artist_label));
        mDbHolder = new DbHolder(new MusicOpenHelper(context));

        mCr = context.getContentResolver();

        MediaThumbnailService.bind(context);
        MusicStoreImportService.bind(context);

        HandlerThread ht = new HandlerThread("thumbs thread", Process.THREAD_PRIORITY_BACKGROUND);
        ht.start();
        mThumbHandler = new Handler(ht.getLooper()) {
            private static final int HDD_MEDIAPROVIDER_TIMEOUT = 25;
            private static final int HDD_MEDIAPROVIDER_DELAY = (HDD_MEDIAPROVIDER_TIMEOUT + 2) * 1000;

            @Override
            public void handleMessage(Message msg) {
                if (msg.what == ALBUM_THUMB) {
                    ThumbData d;
                    synchronized (mThumbRequestStack) {
                        d = mThumbRequestStack.pop();
                    }
                    makeThumbInternal(d);
                    synchronized (mPendingThumbs) {
                        mPendingThumbs.remove(d.path);
                    }
                }
            }
        };
        // initial check for smb state.
        SmbStateService.start(context);
        return true;
    }

    private static void fillArtistAlbumsMap() {
        sArtistAlbumsMap.put(BaseColumns._ID, "audio.album_id AS " +
                BaseColumns._ID);
        sArtistAlbumsMap.put(AlbumColumns.ALBUM, "album");
        sArtistAlbumsMap.put(AlbumColumns.ALBUM_KEY, "album_key");
        sArtistAlbumsMap.put(AlbumColumns.FIRST_YEAR, "MIN(year) AS " +
                AlbumColumns.FIRST_YEAR);
        sArtistAlbumsMap.put(AlbumColumns.LAST_YEAR, "MAX(year) AS " +
                AlbumColumns.LAST_YEAR);
        sArtistAlbumsMap.put(AudioColumns.ARTIST, "artist");
        sArtistAlbumsMap.put(AudioColumns.ARTIST_ID, "artist");
        sArtistAlbumsMap.put(AudioColumns.ARTIST_KEY, "artist_key");
        sArtistAlbumsMap.put(AlbumColumns.NUMBER_OF_SONGS, "count(*) AS " +
                AlbumColumns.NUMBER_OF_SONGS);
        sArtistAlbumsMap.put(AlbumColumns.ALBUM_ART, "album_art._data AS " +
                AlbumColumns.ALBUM_ART);
        sArtistAlbumsMap.put(AlbumColumns.IS_ARCHOS_FAVORITE, "audio.Archos_favorite_album AS " +
                AlbumColumns.IS_ARCHOS_FAVORITE);
    }

    @Override
    public Cursor query(Uri uri, String[] projectionIn, String selection, String[] selectionArgs,
            String sort) {
        if (DBG) Log.d(TAG, "QUERY " + uri);
        int table = URI_MATCHER.match(uri);

        String limit = uri.getQueryParameter("limit");
        String groupby = uri.getQueryParameter("group");
        String having = uri.getQueryParameter("having");

        SQLiteDatabase db = mDbHolder.get();

        // query our custom files tables directly
        if (table == RAW) {
            String tableName = uri.getLastPathSegment();
            return db.query(tableName, projectionIn, selection, selectionArgs, groupby, having, sort, limit);
        }

        List<String> prependArgs = new ArrayList<String>();

        String groupBy = null;

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        String filter = uri.getQueryParameter("filter");
        String [] keywords = null;
        if (filter != null) {
            filter = Uri.decode(filter).trim();
            if (!TextUtils.isEmpty(filter)) {
                String [] searchWords = filter.split(" ");
                keywords = new String[searchWords.length];
                Collator col = Collator.getInstance();
                col.setStrength(Collator.PRIMARY);
                for (int i = 0; i < searchWords.length; i++) {
                    String key = MediaStore.Audio.keyFor(searchWords[i]);
                    key = key.replace("\\", "\\\\");
                    key = key.replace("%", "\\%");
                    key = key.replace("_", "\\_");
                    keywords[i] = key;
                }
            }
        }
        if (uri.getQueryParameter("distinct") != null) {
            qb.setDistinct(true);
        }

        switch (table) {
            case AUDIO_MEDIA:
                if (projectionIn != null && projectionIn.length == 1 &&  selectionArgs == null
                        && (selection == null || selection.equalsIgnoreCase("is_music=1")
                          || selection.equalsIgnoreCase("is_podcast=1") )
                        && projectionIn[0].equalsIgnoreCase("count(*)")
                        && keywords != null) {
                    qb.setTables(MusicOpenHelper.AUDIO_META_VIEW_NAME);
                } else {
                    qb.setTables(MusicOpenHelper.AUDIO_VIEW_NAME);
                    for (int i = 0; keywords != null && i < keywords.length; i++) {
                        if (i > 0) {
                            qb.appendWhere(" AND ");
                        }
                        qb.appendWhere(AudioColumns.ARTIST_KEY +
                                "||" + AudioColumns.ALBUM_KEY +
                                "||" + AudioColumns.TITLE_KEY + " LIKE ? ESCAPE '\\'");
                        prependArgs.add("%" + keywords[i] + "%");
                    }
                }
                break;

            case AUDIO_MEDIA_ID:
                qb.setTables(MusicOpenHelper.AUDIO_VIEW_NAME);
                qb.appendWhere("_id=?");
                prependArgs.add(uri.getPathSegments().get(3));
                break;

            case AUDIO_MEDIA_ID_GENRES:
                qb.setTables(MusicOpenHelper.AUDIO_GENRES_TABLE_NAME);
                qb.appendWhere("_id IN (SELECT genre_id FROM " +
                        MusicOpenHelper.AUDIO_GENRES_MAP_TABLE_NAME + " WHERE audio_id=?)");
                prependArgs.add(uri.getPathSegments().get(3));
                break;

            case AUDIO_MEDIA_ID_GENRES_ID:
                qb.setTables(MusicOpenHelper.AUDIO_GENRES_TABLE_NAME);
                qb.appendWhere("_id=?");
                prependArgs.add(uri.getPathSegments().get(5));
                break;

            case AUDIO_MEDIA_ID_PLAYLISTS:
                qb.setTables(MusicOpenHelper.AUDIO_PLAYLISTS_TABLE_NAME);
                qb.appendWhere("_id IN (SELECT playlist_id FROM " +
                        MusicOpenHelper.AUDIO_PLAYLISTS_MAP_TABLE_NAME + " WHERE audio_id=?)");
                prependArgs.add(uri.getPathSegments().get(3));
                break;

            case AUDIO_MEDIA_ID_PLAYLISTS_ID:
                qb.setTables(MusicOpenHelper.AUDIO_PLAYLISTS_TABLE_NAME);
                qb.appendWhere("_id=?");
                prependArgs.add(uri.getPathSegments().get(5));
                break;

            case AUDIO_GENRES:
                qb.setTables(MusicOpenHelper.AUDIO_GENRES_TABLE_NAME);
                break;

            case AUDIO_GENRES_ID:
                qb.setTables(MusicOpenHelper.AUDIO_GENRES_TABLE_NAME);
                qb.appendWhere("_id=?");
                prependArgs.add(uri.getPathSegments().get(3));
                break;

            case AUDIO_GENRES_ALL_MEMBERS:
            case AUDIO_GENRES_ID_MEMBERS:
                {
                    // if simpleQuery is true, we can do a simpler query on just audio_genres_map
                    // we can do this if we have no keywords and our projection includes just columns
                    // from audio_genres_map
                    boolean simpleQuery = (keywords == null && projectionIn != null
                            && (selection == null || selection.equalsIgnoreCase("genre_id=?")));
                    if (projectionIn != null) {
                        for (int i = 0; i < projectionIn.length; i++) {
                            String p = projectionIn[i];
                            if (p.equals("_id")) {
                                // note, this is different from playlist below, because
                                // "_id" used to (wrongly) be the audio id in this query, not
                                // the row id of the entry in the map, and we preserve this
                                // behavior for backwards compatibility
                                simpleQuery = false;
                            }
                            if (simpleQuery && !(p.equals("audio_id") ||
                                    p.equals("genre_id"))) {
                                simpleQuery = false;
                            }
                        }
                    }
                    if (simpleQuery) {
                        qb.setTables(MusicOpenHelper.AUDIO_GENRES_MAP_NOID_VIEW_NAME);
                        if (table == AUDIO_GENRES_ID_MEMBERS) {
                            qb.appendWhere("genre_id=?");
                            prependArgs.add(uri.getPathSegments().get(3));
                        }
                    } else {
                        qb.setTables(MusicOpenHelper.AUDIO_GENRES_MAP_NOID_VIEW_NAME + "," +
                                MusicOpenHelper.AUDIO_VIEW_NAME);
                        qb.appendWhere("audio._id = audio_id");
                        if (table == AUDIO_GENRES_ID_MEMBERS) {
                            qb.appendWhere(" AND genre_id=?");
                            prependArgs.add(uri.getPathSegments().get(3));
                        }
                        for (int i = 0; keywords != null && i < keywords.length; i++) {
                            qb.appendWhere(" AND ");
                            qb.appendWhere(AudioColumns.ARTIST_KEY +
                                    "||" + AudioColumns.ALBUM_KEY +
                                    "||" + AudioColumns.TITLE_KEY +
                                    " LIKE ? ESCAPE '\\'");
                            prependArgs.add("%" + keywords[i] + "%");
                        }
                    }
                }
                break;

            case AUDIO_PLAYLISTS:
                qb.setTables(MusicOpenHelper.AUDIO_PLAYLISTS_TABLE_NAME);
                break;

            case AUDIO_PLAYLISTS_ID:
                qb.setTables(MusicOpenHelper.AUDIO_PLAYLISTS_TABLE_NAME);
                qb.appendWhere("_id=?");
                prependArgs.add(uri.getPathSegments().get(3));
                break;

            case AUDIO_PLAYLISTS_ID_MEMBERS_ID:
            case AUDIO_PLAYLISTS_ID_MEMBERS:
                // if simpleQuery is true, we can do a simpler query on just audio_playlists_map
                // we can do this if we have no keywords and our projection includes just columns
                // from audio_playlists_map
                boolean simpleQuery = (keywords == null && projectionIn != null
                        && (selection == null || selection.equalsIgnoreCase("playlist_id=?")));
                if (projectionIn != null) {
                    for (int i = 0; i < projectionIn.length; i++) {
                        String p = projectionIn[i];
                        if (simpleQuery && !(p.equals("audio_id") ||
                                p.equals("playlist_id") || p.equals("play_order"))) {
                            simpleQuery = false;
                        }
                        if (p.equals("_id")) {
                            projectionIn[i] = "audio_playlists_map._id AS _id";
                        }
                    }
                }
                if (simpleQuery) {
                    qb.setTables(MusicOpenHelper.AUDIO_PLAYLISTS_MAP_TABLE_NAME);
                    qb.appendWhere("playlist_id=?");
                    prependArgs.add(uri.getPathSegments().get(3));
                } else {
                    qb.setTables(MusicOpenHelper.AUDIO_PLAYLISTS_MAP_TABLE_NAME + "," +
                            MusicOpenHelper.AUDIO_VIEW_NAME);
                    qb.appendWhere("audio._id = audio_id AND playlist_id=?");
                    prependArgs.add(uri.getPathSegments().get(3));
                    for (int i = 0; keywords != null && i < keywords.length; i++) {
                        qb.appendWhere(" AND ");
                        qb.appendWhere(AudioColumns.ARTIST_KEY +
                                "||" + AudioColumns.ALBUM_KEY +
                                "||" + AudioColumns.TITLE_KEY +
                                " LIKE ? ESCAPE '\\'");
                        prependArgs.add("%" + keywords[i] + "%");
                    }
                }
                if (table == AUDIO_PLAYLISTS_ID_MEMBERS_ID) {
                    qb.appendWhere(" AND audio_playlists_map._id=?");
                    prependArgs.add(uri.getPathSegments().get(5));
                }
                break;
            case AUDIO_ARTISTS:
                if (projectionIn != null && projectionIn.length == 1 &&  selectionArgs == null
                        && (selection == null || selection.length() == 0)
                        && projectionIn[0].equalsIgnoreCase("count(*)")
                        && keywords != null) {
                    //Log.i("@@@@", "taking fast path for counting artists");
                    qb.setTables(MusicOpenHelper.AUDIO_META_VIEW_NAME);
                    projectionIn[0] = "count(distinct artist_id)";
                    qb.appendWhere("is_music=1");
                } else {
                    qb.setTables(MusicOpenHelper.ARTIST_INFO_VIEW_NAME);
                    for (int i = 0; keywords != null && i < keywords.length; i++) {
                        if (i > 0) {
                            qb.appendWhere(" AND ");
                        }
                        qb.appendWhere(AudioColumns.ARTIST_KEY +
                                " LIKE ? ESCAPE '\\'");
                        prependArgs.add("%" + keywords[i] + "%");
                    }
                }
                break;

            case AUDIO_ARTISTS_ID:
                qb.setTables(MusicOpenHelper.ARTIST_INFO_VIEW_NAME);
                qb.appendWhere("_id=?");
                prependArgs.add(uri.getPathSegments().get(3));
                break;

            case AUDIO_ARTISTS_ID_ALBUMS:
                String aid = uri.getPathSegments().get(3);
                qb.setTables(MusicOpenHelper.AUDIO_VIEW_NAME + " LEFT OUTER JOIN " +
                        MusicOpenHelper.ALBUM_ART_TABLE_NAME + " ON" +
                        " audio.album_id=album_art.album_id");
                qb.appendWhere("is_music=1 AND audio.album_id IN (SELECT album_id FROM " +
                        MusicOpenHelper.ARTISTS_ALBUMS_MAP_VIEW_NAME + " WHERE artist_id=?)");
                prependArgs.add(aid);
                for (int i = 0; keywords != null && i < keywords.length; i++) {
                    qb.appendWhere(" AND ");
                    qb.appendWhere(AudioColumns.ARTIST_KEY +
                            "||" + AudioColumns.ALBUM_KEY +
                            " LIKE ? ESCAPE '\\'");
                    prependArgs.add("%" + keywords[i] + "%");
                }
                groupBy = "audio.album_id";
                sArtistAlbumsMap.put(AlbumColumns.NUMBER_OF_SONGS_FOR_ARTIST,
                        "count(CASE WHEN artist_id==" + aid + " THEN 'foo' ELSE NULL END) AS " +
                                AlbumColumns.NUMBER_OF_SONGS_FOR_ARTIST);
                qb.setProjectionMap(sArtistAlbumsMap);
                break;

            case AUDIO_ALBUMS:
                if (projectionIn != null && projectionIn.length == 1 &&  selectionArgs == null
                        && (selection == null || selection.length() == 0)
                        && projectionIn[0].equalsIgnoreCase("count(*)")
                        && keywords != null) {
                    //Log.i("@@@@", "taking fast path for counting albums");
                    qb.setTables(MusicOpenHelper.AUDIO_META_VIEW_NAME);
                    projectionIn[0] = "count(distinct album_id)";
                    qb.appendWhere("is_music=1");
                } else {
                    qb.setTables(MusicOpenHelper.ALBUM_INFO_VIEW_NAME);
                    for (int i = 0; keywords != null && i < keywords.length; i++) {
                        if (i > 0) {
                            qb.appendWhere(" AND ");
                        }
                        qb.appendWhere(AudioColumns.ARTIST_KEY +
                                "||" + AudioColumns.ALBUM_KEY +
                                " LIKE ? ESCAPE '\\'");
                        prependArgs.add("%" + keywords[i] + "%");
                    }
                }
                break;

            case AUDIO_ALBUMS_ID:
                qb.setTables(MusicOpenHelper.ALBUM_INFO_VIEW_NAME);
                qb.appendWhere("_id=?");
                prependArgs.add(uri.getPathSegments().get(3));
                break;

            case AUDIO_ALBUMART_ID:
                qb.setTables(MusicOpenHelper.ALBUM_ART_TABLE_NAME);
                qb.appendWhere("album_id=?");
                prependArgs.add(uri.getPathSegments().get(3));
                break;

            case AUDIO_SEARCH_LEGACY:
                Log.w(TAG, "Legacy media search Uri used. Please update your code.");
                //$FALL-THROUGH$
            case AUDIO_SEARCH_FANCY:
            case AUDIO_SEARCH_BASIC:
            case AUDIO_SEARCH_ARCHOS:
                return doAudioSearch(db, qb, uri, projectionIn, selection,
                        combine(prependArgs, selectionArgs), sort, table, limit);
            case FILES_ID:
                qb.appendWhere("_id=?");
                prependArgs.add(uri.getLastPathSegment());
                //$FALL-THROUGH$
            case FILES:
                qb.setTables(MusicOpenHelper.FILES_TABLE_NAME);
                break;
            case ARCHOS_SMB_SERVER_ID:
                qb.appendWhere("_id=?");
                prependArgs.add(uri.getPathSegments().get(2));
                //$FALL-THROUGH$
            case ARCHOS_SMB_SERVER:
                qb.setTables(MusicOpenHelper.SMB_SERVER_TABLE_NAME);
                break;
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }

        Cursor c = qb.query(db, projectionIn, selection,
                combine(prependArgs, selectionArgs), groupBy, null, sort, limit);

        if (c != null) {
            c.setNotificationUri(mCr, uri);
        }

        return c;
    }

    private static String[] combine(List<String> prepend, String[] userArgs) {
        int presize = prepend.size();
        if (presize == 0) {
            return userArgs;
        }

        int usersize = (userArgs != null) ? userArgs.length : 0;
        String [] combined = new String[presize + usersize];
        for (int i = 0; i < presize; i++) {
            combined[i] = prepend.get(i);
        }
        for (int i = 0; i < usersize; i++) {
            combined[presize + i] = userArgs[i];
        }
        return combined;
    }

    private static final String[] MIME_TYPE_PROJECTION = new String[] {
        BaseColumns._ID, // 0
        MediaColumns.MIME_TYPE, // 1
    };
    @Override
    public String getType(Uri url) {
        if (DBG) Log.d(TAG, "getType" + url);

        // determine match
        int match = URI_MATCHER.match(url);

        // return what we can
        switch (match) {
            case AUDIO_MEDIA_ID:
            case AUDIO_PLAYLISTS_ID_MEMBERS_ID:
            case FILES_ID:
                Cursor c = null;
                try {
                    c = query(url, MIME_TYPE_PROJECTION, null, null, null);
                    if (c != null && c.getCount() == 1) {
                        c.moveToFirst();
                        String mimeType = c.getString(1);
                        c.deactivate();
                        return mimeType;
                    }
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }
                break;

            case AUDIO_ALBUMART_ID:
                return "image/jpeg";

            case AUDIO_MEDIA:
            case AUDIO_GENRES_ID_MEMBERS:
            case AUDIO_PLAYLISTS_ID_MEMBERS:
                return Audio.Media.CONTENT_TYPE;

            case AUDIO_GENRES:
            case AUDIO_MEDIA_ID_GENRES:
                return Audio.Genres.CONTENT_TYPE;
            case AUDIO_GENRES_ID:
            case AUDIO_MEDIA_ID_GENRES_ID:
                return Audio.Genres.ENTRY_CONTENT_TYPE;
            case AUDIO_PLAYLISTS:
            case AUDIO_MEDIA_ID_PLAYLISTS:
                return Audio.Playlists.CONTENT_TYPE;
            case AUDIO_PLAYLISTS_ID:
            case AUDIO_MEDIA_ID_PLAYLISTS_ID:
                return Audio.Playlists.ENTRY_CONTENT_TYPE;
            case AUDIO_ALBUMS_ID:
                return Audio.Albums.CONTENT_TYPE;
            case AUDIO_ARTISTS_ID:
                return Audio.Artists.CONTENT_TYPE;
        }
        throw new IllegalArgumentException("Unsupported URI: " + url);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (DBG) Log.d(TAG, "INSRT " + uri + " PID:" + Process.myPid() + " TID:" + Process.myTid());
        int match = URI_MATCHER.match(uri);

        SQLiteDatabase db = mDbHolder.get();

        // insert into our custom files tables.
        if (match == RAW) {
            String table = uri.getLastPathSegment();
            long rowId = db.insert(table, null, values);
            if (rowId > 0) {
                if (MusicOpenHelper.FILES_SCANNED_TABLE_NAME.equals(table)) {
                    handleScanned(values, rowId + MusicOpenHelper.SCANNED_ID_OFFSET);
                }
                Uri result = ContentUris.withAppendedId(uri, rowId);
                if (!db.inTransaction()) {
                    mCr.notifyChange(MusicStore.ALL_CONTENT_URI, null);
                }
                return result;
            }
            return null;
        }
        long rowId = -1;
        Uri newUri = null;
        switch (match) {
            case AUDIO_MEDIA_ID_PLAYLISTS: {
                Long audioId = Long.valueOf(uri.getPathSegments().get(2));
                values.put(Audio.Playlists.Members.AUDIO_ID, audioId);
                rowId = db.insert(MusicOpenHelper.AUDIO_PLAYLISTS_MAP_TABLE_NAME, "playlist_id",
                        values);
                if (rowId > 0) {
                    newUri = ContentUris.withAppendedId(uri, rowId);
                }
                break;
            }
            case AUDIO_PLAYLISTS: {
                String now = String.valueOf(System.currentTimeMillis() / 1000L);
                values.put(PlaylistsColumns.DATE_ADDED, now);
                values.put(PlaylistsColumns.DATE_MODIFIED, now);
                rowId = db.insert(MusicOpenHelper.AUDIO_PLAYLISTS_TABLE_NAME, "_id",
                        values);
                if (rowId > 0) {
                    newUri = ContentUris.withAppendedId(Audio.Playlists.getContentUri(uri.getPathSegments().get(0)), rowId);
                }
                break;
            }
            case AUDIO_PLAYLISTS_ID:
            case AUDIO_PLAYLISTS_ID_MEMBERS: {
                Long playlistId = Long.valueOf(uri.getPathSegments().get(3));
                values.put(Audio.Playlists.Members.PLAYLIST_ID, playlistId);
                rowId = db.insert(MusicOpenHelper.AUDIO_PLAYLISTS_MAP_TABLE_NAME, "playlist_id", values);
                if (rowId > 0) {
                    newUri = ContentUris.withAppendedId(uri, rowId);
                }
                break;
            }
            case AUDIO_ALBUMART: {
                try {
                    values = ensureFile(values, "", mAlbumThumbFolder);
                } catch (IllegalStateException ex) {
                    // probably no more room to store albumthumbs
                }
                rowId = db.insert(MusicOpenHelper.ALBUM_ART_TABLE_NAME, MediaColumns.DATA, values);
                if (rowId > 0) {
                    newUri = ContentUris.withAppendedId(uri, rowId);
                }
                break;
            }
            case ARCHOS_SMB_SERVER: {
                rowId = db.insert(MusicOpenHelper.SMB_SERVER_TABLE_NAME, BaseColumns._ID, values);
                if (rowId > 0) {
                    newUri = MusicStore.SmbServer.getContentUri(rowId);
                }
                break;
            }

            case AUDIO_MEDIA_ID_GENRES:
            case AUDIO_GENRES:
            case AUDIO_GENRES_ID_MEMBERS:
            case AUDIO_MEDIA:
            case FILES:
                //$FALL-THROUGH$
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
        if (newUri != null && !db.inTransaction()) {
            mCr.notifyChange(newUri, null);
        }
        return newUri;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {

        ParcelFileDescriptor pfd = null;
        int match = URI_MATCHER.match(uri);

        SQLiteDatabase db = mDbHolder.get();

        if (match == AUDIO_ALBUMART_FILE_ID) {
            // get album art for the specified media file
            SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
            int songid = Integer.parseInt(uri.getPathSegments().get(3));
            qb.setTables(MusicOpenHelper.AUDIO_META_VIEW_NAME);
            qb.appendWhere("_id=" + songid);
            Cursor c = qb.query(db,
                    new String [] {
                        MediaColumns.DATA,
                        AudioColumns.ALBUM_ID },
                    null, null, null, null, null);
            if (c.moveToFirst()) {
                String audiopath = c.getString(0);
                int albumid = c.getInt(1);
                // Try to get existing album art for this album first, which
                // could possibly have been obtained from a different file.
                // If that fails, try to get it from this specific file.
                Uri newUri = ContentUris.withAppendedId(MusicStore.Audio.Albums.ALBUM_ART_URI, albumid);
                try {
                    pfd = openFileHelper(newUri, mode);
                } catch (FileNotFoundException ex) {
                    // That didn't work, now try to get it from the specific file
                    if (DBG) Log.d(TAG, "openFile: trying to regenerate thumb for album:" + albumid);
                    pfd = getThumb(db, audiopath, albumid, null);
                }
            }
            c.close();
            return pfd;
        }

        try {
            pfd = openFileHelper(uri, mode);
        } catch (FileNotFoundException ex) {
            if (mode.contains("w")) {
                // if the file couldn't be created, we shouldn't extract album art
                throw ex;
            }

            if (match == AUDIO_ALBUMART_ID) {
                // Tried to open an album art file which does not exist. Regenerate.
                SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
                int albumid = Integer.parseInt(uri.getPathSegments().get(3));
                qb.setTables(MusicOpenHelper.AUDIO_META_VIEW_NAME);
                qb.appendWhere("album_id=" + albumid);
                Cursor c = qb.query(db,
                        new String [] {
                            MediaColumns.DATA },
                        null, null, null, null, AudioColumns.TRACK);
                if (c.moveToFirst()) {
                    String audiopath = c.getString(0);
                    if (DBG) Log.d(TAG, "openFile: trying to regenerate thumb for album:" + albumid);
                    pfd = getThumb(db, audiopath, albumid, uri);
                }
                c.close();
            }
            if (pfd == null) {
                throw ex;
            }
        }
        return pfd;
    }

    private ParcelFileDescriptor getThumb(SQLiteDatabase db, String path, long album_id,
            Uri albumart_uri) {
        if (path != null) {
            if (DBG) Log.d(TAG, "not regenerating album art for [" + path + "]");
            return null;
        }
        ThumbData d = new ThumbData();
        d.db = db;
        d.path = path;
        d.album_id = album_id;
        d.albumart_uri = albumart_uri;
        return makeThumbInternal(d);
    }

    private static ContentValues ensureFile(ContentValues initialValues,
            String preferredExtension, String directoryName) {
        ContentValues values;
        String file = initialValues.getAsString(MusicStore.MediaColumns.DATA);
        if (TextUtils.isEmpty(file)) {
            file = generateFileName(preferredExtension, directoryName);
            values = new ContentValues(initialValues);
            values.put(MusicStore.MediaColumns.DATA, file);
        } else {
            values = initialValues;
        }

        if (!ensureFileExists(file)) {
            throw new IllegalStateException("Unable to create new file: " + file);
        }
        return values;
    }

    private static boolean ensureFileExists(String path) {
        File file = new File(path);
        if (file.exists()) {
            return true;
        }
        // we will not attempt to create the first directory in the path
        // (for example, do not create /sdcard if the SD card is not mounted)
        int secondSlash = path.indexOf('/', 1);
        if (secondSlash < 1) return false;
        String directoryPath = path.substring(0, secondSlash);
        File directory = new File(directoryPath);
        if (!directory.exists())
            return false;
        // it's possible that we cannot create the directory
        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
            Log.e(TAG, "could not create " + file.getParent());
            return false;
        }
        try {
            boolean ret = file.createNewFile();
            // file needs  to be world readable, enforce that here.
            if (ret)
                file.setReadable(true, false);
            return ret;
        } catch(IOException ioe) {
            Log.e(TAG, "File creation failed", ioe);
        }
        return false;
    }

    private static String generateFileName(String preferredExtension, String directoryName) {
        // create a random file
        String name = String.valueOf(System.currentTimeMillis());
        return directoryName + "/" + name + preferredExtension;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        if (DBG) Log.d(TAG, "DELTE " + uri);
        int match = URI_MATCHER.match(uri);

        SQLiteDatabase db = mDbHolder.get();

        // direct access to raw tables
        if (match == RAW) {
            String tableName = uri.getLastPathSegment();
            int result = db.delete(tableName, selection, selectionArgs);
            if (result > 0 && !db.inTransaction()) {
                mCr.notifyChange(MusicStore.ALL_CONTENT_URI, null);
            }
            return result;
        }

        // the rest uses the usual way as in Android
        int count;
        synchronized (sGetTableAndWhereParam) {
            getTableAndWhere(uri, match, selection, sGetTableAndWhereParam);
            switch (match) {
                case FILES:
                case FILES_ID:
                case AUDIO_MEDIA:
                case AUDIO_MEDIA_ID:
                    return forwardDelete(db, sGetTableAndWhereParam.table, sGetTableAndWhereParam.where, selectionArgs);
                case AUDIO_GENRES_ID_MEMBERS:
                    count = db.delete(MusicOpenHelper.AUDIO_GENRES_MAP_TABLE_NAME,
                            sGetTableAndWhereParam.where, selectionArgs);
                    break;

                default:
                    count = db.delete(sGetTableAndWhereParam.table,
                            sGetTableAndWhereParam.where, selectionArgs);
                    break;
            }
        }
        // TODO reset Artist / Album cache?
        // since deletes may affect a lot of stuff notify everywhere.
        if (count > 0 && !db.inTransaction())
            mCr.notifyChange(MusicStore.ALL_CONTENT_URI, null);
        return count;
    }

    private static final String[] PROJECTION_IDS = {
        "group_concat(" + BaseColumns._ID + ")"
    };
    private int forwardDelete(SQLiteDatabase db, String table, String selection, String[] selectionArgs) {
        String deleteIds = null;
        Cursor c = db.query(table, PROJECTION_IDS, selection, selectionArgs, null, null, null);
        if (c != null) {
            if (c.moveToFirst()) {
                deleteIds = c.getString(0);
            }
            c.close();
        }
        if (deleteIds == null || deleteIds.isEmpty())
            return 0;

        String where = BaseColumns._ID + " IN (" + deleteIds + ")";
        return mCr.delete(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, where, null);
    }

    @Override
    public int update(Uri uri, ContentValues initialValues, String userWhere,
            String[] whereArgs) {
        if (DBG) Log.d(TAG, "UPDTE " + uri);
        int count;
        // Log.v(TAG, "update for uri="+uri+", initValues="+initialValues);
        int match = URI_MATCHER.match(uri);

        SQLiteDatabase db = mDbHolder.get();

        switch (match) {
            case RAW: {
                String tableName = uri.getLastPathSegment();
                if (MusicOpenHelper.FILES_TABLE_NAME.equals(tableName)) {
                    // if KEY_SCANNER is present that update was generated by our scanner
                    if (initialValues.containsKey(MusicStoreInternal.KEY_SCANNER)) {
                        initialValues.remove(MusicStoreInternal.KEY_SCANNER);

                        long id = initialValues.getAsLong(BaseColumns._ID).longValue();
                        String path = initialValues.getAsString(MediaColumns.DATA);
                        File f = new File(path);
                        String parentDir = f.getParent();

                        // remove genre & handle separately
                        String genre = initialValues.getAsString(Audio.AudioColumns.GENRE);
                        initialValues.remove(Audio.AudioColumns.GENRE);

                        int mediaType = initialValues.getAsInteger(FileColumns.MEDIA_TYPE).intValue();
                        if (mediaType == MusicStore.Files.FileColumns.MEDIA_TYPE_AUDIO) {
                            handleAlbum(initialValues, parentDir, path);
                            handleArtist(initialValues);
                            handleTitle(initialValues, path);
                            handleAudioType(initialValues, path);
                            if (genre != null)
                                updateGenre(id, genre);
                        }
                    }
                    initialValues.remove(AudioColumns.COMPILATION);
                    initialValues.remove(BaseColumns._ID);
                    initialValues.remove(MediaColumns.DATA);
                }
                int result = db.update(tableName, initialValues, userWhere, whereArgs);
                if (result > 0 && !db.inTransaction()) {
                    mCr.notifyChange(MusicStore.ALL_CONTENT_URI, null);
                }
                return result;
            }
            case AUDIO_MEDIA:
            case AUDIO_MEDIA_ID:
            case AUDIO_ALBUMS:
            case AUDIO_ALBUMS_ID:
            case AUDIO_ARTISTS:
            case AUDIO_ARTISTS_ID:
            case AUDIO_PLAYLISTS_ID_MEMBERS_ID:
            case ARCHOS_SMB_SERVER:
            case ARCHOS_SMB_SERVER_ID:
                break; // continue below
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }

        synchronized (sGetTableAndWhereParam) {
            getTableAndWhere(uri, match, userWhere, sGetTableAndWhereParam);

            switch (match) {
                case AUDIO_MEDIA:
                case AUDIO_MEDIA_ID:
                case AUDIO_ALBUMS:
                case AUDIO_ALBUMS_ID:
                case AUDIO_ARTISTS:
                case AUDIO_ARTISTS_ID:
                case AUDIO_PLAYLISTS_ID_MEMBERS_ID:
                {
                    ContentValues values = new ContentValues(initialValues);
                    // Don't allow imported stuff to be updated.
                    valuesRemove(values, BaseColumns._ID);
                    valuesRemove(values, MediaColumns.DATA);
                    valuesRemove(values, MediaColumns.DISPLAY_NAME);
                    valuesRemove(values, MediaColumns.SIZE);
                    valuesRemove(values, MediaColumns.DATE_ADDED);
                    valuesRemove(values, MediaColumns.DATE_MODIFIED);
                    valuesRemove(values, AudioColumns.BUCKET_ID);
                    valuesRemove(values, AudioColumns.BUCKET_DISPLAY_NAME);
                    valuesRemove(values, MusicStore.Files.FileColumns.FORMAT);
                    valuesRemove(values, MusicStore.Files.FileColumns.PARENT);
                    valuesRemove(values, MusicStore.Files.FileColumns.STORAGE_ID);
                    if (values.size() < 1) {
                        Log.e(TAG, "no more Values, aborting update.");
                        return 0;
                    }
                    count = db.update(sGetTableAndWhereParam.table, values,
                            sGetTableAndWhereParam.where, whereArgs);
                }
                break;
                // fall through
                default:
                    count = db.update(sGetTableAndWhereParam.table, initialValues,
                            sGetTableAndWhereParam.where, whereArgs);
                    break;
            }
        }
        // in a transaction, the code that began the transaction should be taking
        // care of notifications once it ends the transaction successfully
        if (count > 0 && !db.inTransaction()) {
            mCr.notifyChange(uri, null);
        }
        return count;
    }

    private static final String[] GENRE_LOOKUP_PROJECTION = new String[] {
        BaseColumns._ID, // 0
        GenresColumns.NAME, // 1
    };
    private void updateGenre(long rowId, String genre) {
        SQLiteDatabase db = mDbHolder.get();
        long id = -1;
        Cursor cursor = null;
        try {
            // see if the genre already exists
            cursor = db.query(MusicOpenHelper.AUDIO_GENRES_TABLE_NAME, GENRE_LOOKUP_PROJECTION, GenresColumns.NAME + "=?",
                            new String[] { genre }, null, null, null);
            if (cursor == null || cursor.getCount() == 0) {
                // genre does not exist, so create the genre in the genre table
                ContentValues values = new ContentValues();
                values.put(GenresColumns.NAME, genre);
                id = db.insert(MusicOpenHelper.AUDIO_GENRES_TABLE_NAME, BaseColumns._ID, values);
            } else {
                // genre already exists, so compute its Uri
                cursor.moveToNext();
                id = cursor.getLong(0);
            }
        } finally {
            // release the cursor if it exists
            if (cursor != null) {
                cursor.close();
            }
        }

        if (id != -1) {
            // add entry to audio_genre_map
            ContentValues values = new ContentValues();
            values.put(Audio.Genres.Members.AUDIO_ID, Long.valueOf(rowId));
            values.put(Audio.Genres.Members.GENRE_ID, Long.valueOf(id));
            db.insert(MusicOpenHelper.AUDIO_GENRES_MAP_TABLE_NAME, BaseColumns._ID, values);
        }
    }

    private static final String[] ALBUMS_PROJ = {
        AlbumColumns.ALBUM_ID,
        AlbumColumns.ALBUM_KEY
    };
    private static final String[] ALBUMS_ID_ALBUM_PROJ = {
        AlbumColumns.ALBUM_ID,
        AlbumColumns.ALBUM
    };
    private static final String WHERE_ALBUM_KEY = AlbumColumns.ALBUM_KEY + "=?";
    private HashMap<String, String> mAlbumCache = null;
    private void handleAlbum(ContentValues cv, String parentDir, String file) {
        String album = cv.getAsString(AudioColumns.ALBUM);
        String albumArtist = cv.getAsString(AudioColumns.ALBUM_ARTIST);
        int albumhash = 0;
        if (album == null) {
            album = new File(parentDir).getName();
        } else {
            album = album.trim();
            if (album.isEmpty())
                album = MusicStore.UNKNOWN_STRING;
        }
        cv.put(AudioColumns.ALBUM, album);
        if (albumArtist != null) {
            albumhash = albumArtist.hashCode();
        } else {
            albumhash = parentDir.hashCode();
        }

        SQLiteDatabase db = mDbHolder.get();

        // ensure we have an initial album cache to look up stuff
        if (mAlbumCache == null) {
            Cursor c = db.query(MusicOpenHelper.ALBUMS_TABLE_NAME, ALBUMS_PROJ, null, null, null, null, null);
            if (c != null) {
                // init size to something useful
                mAlbumCache = new HashMap<String, String>(c.getCount() + 10);
                while (c.moveToNext()) {
                    String id = c.getString(0);
                    String key = c.getString(1);
                    mAlbumCache.put(key, id);
                }
                c.close();
            }
            // init small cache for now
            if (mAlbumCache == null)
                mAlbumCache = new HashMap<String, String>(10);
        }
        String key = MusicStore.Audio.keyFor(album) + albumhash;
        String id = mAlbumCache.get(key);
        if (id == null) {
            ContentValues newAlbum = new ContentValues();
            newAlbum.put(AlbumColumns.ALBUM_KEY, key);
            newAlbum.put(AlbumColumns.ALBUM, album);
            // try inserting that album
            long newId = db.insert(MusicOpenHelper.ALBUMS_TABLE_NAME, AlbumColumns.ALBUM, newAlbum);
            // if that fails our cache is out of sync, try find that album in the db
            if (newId < 0) {
                Log.w(TAG, "album cache miss");
                String[] args = { key };
                Cursor c = db.query(MusicOpenHelper.ALBUMS_TABLE_NAME, ALBUMS_ID_ALBUM_PROJ, WHERE_ALBUM_KEY, args, null, null, null);
                if (c != null) {
                    if (c.moveToFirst()) {
                        newId = c.getLong(0);
                        album = c.getString(1); // use album name from db instead
                        // todo use getBestName stuff from MediaScanner
                    }
                    c.close();
                }
            } else {
                // new Album inserted, now do album art
                makeThumbAsync(db, file, newId);
            }
            if (newId < 0) {
                Log.e(TAG, "failed to insert album:" + album);
            }
            id = String.valueOf(newId);
        }
        cv.put(AudioColumns.ALBUM_ID, id);
        mAlbumCache.put(key, id);
    }

    private static final String[] ARTIST_PROJ = {
        AudioColumns.ARTIST_ID,
        ArtistColumns.ARTIST_KEY
    };
    private static final String[] ARTIST_ID_ARTIST_PROJ = {
        AudioColumns.ARTIST_ID,
        ArtistColumns.ARTIST
    };
    private static final String WHERE_ARTIST_KEY = ArtistColumns.ARTIST_KEY + "=?";
    private HashMap<String, String> mArtistCache = null;
    private void handleArtist(ContentValues cv) {
        String artist = cv.getAsString(AudioColumns.ARTIST);
        String album_artist = cv.getAsString(AudioColumns.ALBUM_ARTIST);
        if (TextUtils.isEmpty(artist)) {
            artist = album_artist;
        }
        if (TextUtils.isEmpty(artist)) {
            artist = MusicStore.UNKNOWN_STRING;
        } else {
            artist = artist.trim();
            if (artist.isEmpty())
                artist = MusicStore.UNKNOWN_STRING;
        }
        cv.put(AudioColumns.ARTIST, artist);

        SQLiteDatabase db = mDbHolder.get();

        // ensure we have an initial artist cache to look up stuff
        if (mArtistCache == null) {
            Cursor c = db.query(MusicOpenHelper.ARTISTS_TABLE_NAME, ARTIST_PROJ, null, null, null, null, null);
            if (c != null) {
                // init size to something useful
                mArtistCache = new HashMap<String, String>(c.getCount() + 10);
                while (c.moveToNext()) {
                    String id = c.getString(0);
                    String key = c.getString(1);
                    mArtistCache.put(key, id);
                }
                c.close();
            }
            // init small cache for now
            if (mArtistCache == null)
                mArtistCache = new HashMap<String, String>(10);
        }
        String key = MusicStore.Audio.keyFor(artist);
        String id = mArtistCache.get(key);
        if (id == null) {
            ContentValues newArtist = new ContentValues();
            newArtist.put(AudioColumns.ARTIST_KEY, key);
            newArtist.put(AudioColumns.ARTIST, artist);
            // try inserting that artist
            long newId = db.insert(MusicOpenHelper.ARTISTS_TABLE_NAME, AudioColumns.ARTIST, newArtist);
            // if that fails our cache is out of sync, try find that artist in the db
            if (newId < 0) {
                Log.w(TAG, "artist cache miss");
                String[] args = { key };
                Cursor c = db.query(MusicOpenHelper.ARTISTS_TABLE_NAME, ARTIST_ID_ARTIST_PROJ, WHERE_ARTIST_KEY, args, null, null, null);
                if (c != null) {
                    if (c.moveToFirst()) {
                        newId = c.getLong(0);
                        artist = c.getString(1); // use artist name from db instead
                        // todo use getBestName stuff from MediaScanner
                    }
                    c.close();
                }
            }
            if (newId < 0) {
                Log.e(TAG, "failed to insert artist:" + artist);
            }
            id = String.valueOf(newId);
        }
        cv.put(AudioColumns.ARTIST_ID, id);
        mArtistCache.put(key, id);
    }
    private static void handleTitle(ContentValues cv, String path) {
        String title = cv.getAsString(MediaColumns.TITLE);
        if (TextUtils.isEmpty(title)) {
            title = new File (path).getName();
            int idx = title.lastIndexOf('.');
            if (idx > 0) {
                title = title.substring(0, idx);
            }
            cv.put(MediaColumns.TITLE, title);
        }
        String titleKey = Audio.keyFor(title);
        cv.put(AudioColumns.TITLE_KEY, titleKey);
    }

    private static final String RINGTONES_DIR = ("/" + Environment.DIRECTORY_RINGTONES + "/").toLowerCase(Locale.US);
    private static final String NOTIFICATIONS_DIR = ("/" + Environment.DIRECTORY_NOTIFICATIONS + "/").toLowerCase(Locale.US);
    private static final String ALARMS_DIR = ("/" + Environment.DIRECTORY_ALARMS + "/").toLowerCase(Locale.US);
    private static final String MUSIC_DIR = ("/" + Environment.DIRECTORY_MUSIC + "/").toLowerCase(Locale.US);
    private static final String PODCAST_DIR = ("/" + Environment.DIRECTORY_PODCASTS + "/").toLowerCase(Locale.US);
    private static void handleAudioType(ContentValues cv, String path) {
        String lowpath = path.toLowerCase(Locale.US);
        boolean ringtones = (lowpath.indexOf(RINGTONES_DIR) > 0);
        boolean notifications = (lowpath.indexOf(NOTIFICATIONS_DIR) > 0);
        boolean alarms = (lowpath.indexOf(ALARMS_DIR) > 0);
        boolean podcasts = (lowpath.indexOf(PODCAST_DIR) > 0);
        boolean music = (lowpath.indexOf(MUSIC_DIR) > 0) ||
            (!ringtones && !notifications && !alarms && !podcasts);
        cv.put(AudioColumns.IS_RINGTONE, ringtones ? "1" : "0");
        cv.put(AudioColumns.IS_NOTIFICATION, notifications ? "1" : "0");
        cv.put(AudioColumns.IS_ALARM, alarms ? "1" : "0");
        cv.put(AudioColumns.IS_PODCAST, podcasts ? "1" : "0");
        cv.put(AudioColumns.IS_MUSIC, music ? "1" : "0");
    }

    private static final String WHERE_REMOTE_ID = "remote_id=?";
    private void handleScanned(ContentValues cv, long rowId) {
        String media_type = cv.getAsString(FileColumns.MEDIA_TYPE);
        if (Integer.parseInt(media_type) == FileColumns.MEDIA_TYPE_AUDIO) {
            cv.put(AudioColumns.IS_RINGTONE, "0");
            cv.put(AudioColumns.IS_NOTIFICATION, "0");
            cv.put(AudioColumns.IS_ALARM, "0");
            cv.put(AudioColumns.IS_PODCAST, "0");
            cv.put(AudioColumns.IS_MUSIC, "1");
            String path = cv.getAsString(MediaColumns.DATA);
            String parentDir = new File(path).getParent();
            handleAlbum(cv, parentDir, path);
            handleArtist(cv);
            handleTitle(cv, path);
            cv.put(MusicStoreInternal.FILES_EXTRA_COLUMN_SCAN_STATE, "1");
            mDbHolder.get().update(MusicOpenHelper.FILES_TABLE_NAME, cv, WHERE_REMOTE_ID, new String[] { String.valueOf(rowId) });
        }
    }

    private static class ThumbData {
        public ThumbData() { /* empty */ }
        SQLiteDatabase db;
        String path;
        long album_id;
        Uri albumart_uri;
    }

    // A HashSet of paths that are pending creation of album art thumbnails.
    protected final HashSet<String> mPendingThumbs = new HashSet<String>();
    // A Stack of outstanding thumbnail requests.
    protected final Stack<ThumbData> mThumbRequestStack = new Stack<ThumbData>();
    private void makeThumbAsync(SQLiteDatabase db, String path, long album_id) {
        synchronized (mPendingThumbs) {
            if (mPendingThumbs.contains(path)) {
                // There's already a request to make an album art thumbnail
                // for this audio file in the queue.
                return;
            }

            mPendingThumbs.add(path);
        }

        ThumbData d = new ThumbData();
        d.db = db;
        d.path = path;
        d.album_id = album_id;
        d.albumart_uri = ContentUris.withAppendedId(MusicStore.Audio.Albums.ALBUM_ART_URI, album_id);

        // Instead of processing thumbnail requests in the order they were
        // received we instead process them stack-based, i.e. LIFO.
        // The idea behind this is that the most recently requested thumbnails
        // are most likely the ones still in the user's view, whereas those
        // requested earlier may have already scrolled off.
        synchronized (mThumbRequestStack) {
            mThumbRequestStack.push(d);
        }

        // Trigger the handler.
        mThumbHandler.sendEmptyMessage(ALBUM_THUMB);
    }

    protected ParcelFileDescriptor makeThumbInternal(ThumbData d) {
        byte[] compressed = getCompressedAlbumArt(getContext(), d.path);

        if (compressed == null) {
            return null;
        }

        Bitmap bm = null;
        boolean need_to_recompress = true;

        try {
            // get the size of the bitmap
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            opts.inSampleSize = 1;
            BitmapFactory.decodeByteArray(compressed, 0, compressed.length, opts);
            // request a reasonably sized output image
            final Resources r = getContext().getResources();
            final int maximumThumbSize = r.getDimensionPixelSize(R.dimen.maximum_thumb_size);
            while (opts.outHeight > maximumThumbSize || opts.outWidth > maximumThumbSize) {
                opts.outHeight /= 2;
                opts.outWidth /= 2;
                opts.inSampleSize *= 2;
            }

            if (opts.inSampleSize == 1) {
                // The original album art was of proper size, we won't have to
                // recompress the bitmap later.
                need_to_recompress = false;
            } else {
                // get the image for real now
                opts.inJustDecodeBounds = false;
                opts.inPreferredConfig = Bitmap.Config.RGB_565;
                bm = BitmapFactory.decodeByteArray(compressed, 0, compressed.length, opts);

                if (bm != null && bm.getConfig() == null) {
                    Bitmap nbm = bm.copy(Bitmap.Config.RGB_565, false);
                    if (nbm != null && nbm != bm) {
                        bm.recycle();
                        bm = nbm;
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }


        if (need_to_recompress && bm == null) {
            return null;
        }

        if (d.albumart_uri == null) {
            // this one doesn't need to be saved (probably a song with an unknown album),
            // so stick it in a memory file and return that
            try {
                Log.d(TAG, "makeThumbInternal: does not need to be saved ?!");
                return openPipeHelper(null, null, null, compressed, new PipeByteWriter());
            } catch (IOException e) {
                // ignore
            }
        } else {
            // This one needs to actually be saved on the sd card.
            // This is wrapped in a transaction because there are various things
            // that could go wrong while generating the thumbnail, and we only want
            // to update the database when all steps succeeded.
            d.db.beginTransaction();
            try {
                Uri out = getAlbumArtOutputUri(d.db, d.album_id, d.albumart_uri);

                if (out != null) {
                    writeAlbumArt(need_to_recompress, out, compressed, bm);
                    getContext().getContentResolver().notifyChange(MusicStore.ALL_CONTENT_URI, null);
                    ParcelFileDescriptor pfd = openFileHelper(out, "r");
                    d.db.setTransactionSuccessful();
                    return pfd;
                }
            } catch (FileNotFoundException ex) {
                // do nothing, just return null below
            } catch (UnsupportedOperationException ex) {
                // do nothing, just return null below
            } finally {
                d.db.endTransaction();
                if (bm != null) {
                    bm.recycle();
                }
            }
        }
        return null;
    }

    static class PipeByteWriter implements PipeDataWriter<byte[]> {
        @Override
        public void writeDataToPipe(ParcelFileDescriptor output, Uri uri, String mimeType, Bundle opts, byte[] args) {
            FileOutputStream fout = new FileOutputStream(output.getFileDescriptor());
            try {
                fout.write(args);
            } catch (IOException e) {
                Log.w(TAG, e);
            } finally {
                try {
                    fout.close();
                } catch (IOException e) {
                    // ignored
                }
            }
        }
    }

    private static final HashMap<String, String> sFolderArtMap = new HashMap<String, String>();
    // Extract compressed image data from the audio file itself or, if that fails,
    // look for a file "AlbumArt.jpg" in the containing directory.
    private static byte[] getCompressedAlbumArt(Context context, String path) {
        byte[] compressed = null;
        if (path == null) return null;

        try {
            IMediaMetadataRetriever scanner = MediaFactory.createMetadataRetriever(context);
            scanner.setDataSource(path);
            compressed = scanner.getEmbeddedPicture();
            scanner.release();

            // If no embedded art exists, look for a suitable image file in the
            // same directory as the media file, except if that directory is
            // is the root directory of the sd card or the download directory.
            // We look for, in order of preference:
            // 0 AlbumArt.jpg
            // 1 AlbumArt*Large.jpg
            // 2 Any other jpg image with 'albumart' anywhere in the name
            // 3 Any other jpg image
            // 4 any other png image
            if (compressed == null) {
                int lastSlash = path.lastIndexOf('/');
                if (lastSlash > 0) {

                    String artPath = path.substring(0, lastSlash);
                    String sdroot = ArchosMediaCommon.EXTERNAL_STORAGE_PATH;
                    String dwndir = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();

                    String bestmatch = null;
                    synchronized (sFolderArtMap) {
                        if (sFolderArtMap.containsKey(artPath)) {
                            bestmatch = sFolderArtMap.get(artPath);
                        } else if (!artPath.equalsIgnoreCase(sdroot) &&
                                !artPath.equalsIgnoreCase(dwndir)) {
                            File dir = new File(artPath);
                            String [] entrynames = dir.list();
                            if (entrynames == null) {
                                return null;
                            }
                            int matchlevel = 1000;
                            for (int i = entrynames.length - 1; i >=0; i--) {
                                String entry = entrynames[i].toLowerCase();
                                if (entry.equals("albumart.jpg")) {
                                    bestmatch = entrynames[i];
                                    break;
                                } else if (entry.startsWith("albumart")
                                        && entry.endsWith("large.jpg")
                                        && matchlevel > 1) {
                                    bestmatch = entrynames[i];
                                    matchlevel = 1;
                                } else if (entry.contains("albumart")
                                        && entry.endsWith(".jpg")
                                        && matchlevel > 2) {
                                    bestmatch = entrynames[i];
                                    matchlevel = 2;
                                } else if (entry.endsWith(".jpg") && matchlevel > 3) {
                                    bestmatch = entrynames[i];
                                    matchlevel = 3;
                                } else if (entry.endsWith(".png") && matchlevel > 4) {
                                    bestmatch = entrynames[i];
                                    matchlevel = 4;
                                }
                            }
                            // note that this may insert null if no album art was found
                            sFolderArtMap.put(artPath, bestmatch);
                        }
                    }

                    if (bestmatch != null) {
                        File file = new File(artPath, bestmatch);
                        if (file.exists()) {
                            compressed = new byte[(int)file.length()];
                            FileInputStream stream = null;
                            try {
                                stream = new FileInputStream(file);
                                stream.read(compressed);
                            } catch (IOException ex) {
                                compressed = null;
                            } finally {
                                if (stream != null) {
                                    stream.close();
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            /* empty */
        }

        return compressed;
    }

    // Return a URI to write the album art to and update the database as necessary.
    Uri getAlbumArtOutputUri(SQLiteDatabase db, long album_id, Uri albumart_uri) {
        Uri out = null;
        // TODO: this could be done more efficiently with a call to db.replace(), which
        // replaces or inserts as needed, making it unnecessary to query() first.
        if (albumart_uri != null) {
            Cursor c = query(albumart_uri, new String [] { MusicStore.MediaColumns.DATA },
                    null, null, null);
            try {
                if (c != null && c.moveToFirst()) {
                    String albumart_path = c.getString(0);
                    if (ensureFileExists(albumart_path)) {
                        out = albumart_uri;
                    }
                } else {
                    albumart_uri = null;
                }
            } finally {
                if (c != null) {
                    c.close();
                }
            }
        }
        if (albumart_uri == null){
            ContentValues initialValues = new ContentValues();
            initialValues.put("album_id", Long.valueOf(album_id));
            try {
                ContentValues values = ensureFile(initialValues, "", mAlbumThumbFolder);
                long rowId = db.insert("album_art", MusicStore.MediaColumns.DATA, values);
                if (rowId > 0) {
                    out = ContentUris.withAppendedId(MusicStore.Audio.Albums.ALBUM_ART_URI, rowId);
                }
            } catch (IllegalStateException ex) {
                Log.e(TAG, "error creating album thumb file");
            }
        }
        return out;
    }
    // Write out the album art to the output URI, recompresses the given Bitmap
    // if necessary, otherwise writes the compressed data.
    private void writeAlbumArt(
            boolean need_to_recompress, Uri out, byte[] compressed, Bitmap bm) {
        boolean success = false;
        try {
            OutputStream outstream = mCr.openOutputStream(out);

            if (!need_to_recompress) {
                // No need to recompress here, just write out the original
                // compressed data here.
                outstream.write(compressed);
                success = true;
            } else {
                success = bm.compress(Bitmap.CompressFormat.JPEG, 85, outstream);
            }

            outstream.close();
        } catch (FileNotFoundException ex) {
            Log.e(TAG, "error creating file", ex);
        } catch (IOException ex) {
            Log.e(TAG, "error creating file", ex);
        }
        if (!success) {
            // the thumbnail was not written successfully, delete the entry that refers to it
            mCr.delete(out, null, null);
        }
    }

    private static void valuesRemove(ContentValues cv, String what) {
        if (cv.containsKey(what)) {
            Log.e(TAG, "Removing: " + what + " since that is not supported.");
            cv.remove(what);
        }
    }

    private static final class GetTableAndWhereOutParameter {
        public GetTableAndWhereOutParameter() { /* empty */ }
        public String table;
        public String where;
    }

    static final GetTableAndWhereOutParameter sGetTableAndWhereParam =
            new GetTableAndWhereOutParameter();

    private static void getTableAndWhere(Uri uri, int match, String userWhere,
            GetTableAndWhereOutParameter out) {
        String where = null;
        switch (match) {
            case AUDIO_MEDIA:
                out.table = MusicOpenHelper.FILES_TABLE_NAME;
                where = FileColumns.MEDIA_TYPE + "=" + FileColumns.MEDIA_TYPE_AUDIO;
                break;

            case AUDIO_MEDIA_ID:
                out.table = MusicOpenHelper.FILES_TABLE_NAME;
                where = "_id=" + uri.getPathSegments().get(3);
                break;

            case AUDIO_MEDIA_ID_GENRES:
                out.table = MusicOpenHelper.AUDIO_GENRES_TABLE_NAME;
                where = "audio_id=" + uri.getPathSegments().get(3);
                break;

            case AUDIO_MEDIA_ID_GENRES_ID:
                out.table = MusicOpenHelper.AUDIO_GENRES_TABLE_NAME;
                where = "audio_id=" + uri.getPathSegments().get(3) +
                        " AND genre_id=" + uri.getPathSegments().get(5);
               break;

            case AUDIO_MEDIA_ID_PLAYLISTS:
                out.table = MusicOpenHelper.AUDIO_PLAYLISTS_TABLE_NAME;
                where = "audio_id=" + uri.getPathSegments().get(3);
                break;

            case AUDIO_MEDIA_ID_PLAYLISTS_ID:
                out.table = MusicOpenHelper.AUDIO_PLAYLISTS_MAP_TABLE_NAME;
                where = "audio_id=" + uri.getPathSegments().get(3) +
                        " AND playlists_id=" + uri.getPathSegments().get(5);
                break;

            case AUDIO_GENRES:
                out.table = MusicOpenHelper.AUDIO_GENRES_TABLE_NAME;
                break;

            case AUDIO_GENRES_ID:
                out.table = MusicOpenHelper.AUDIO_GENRES_TABLE_NAME;
                where = "_id=" + uri.getPathSegments().get(3);
                break;

            case AUDIO_GENRES_ID_MEMBERS:
                out.table = MusicOpenHelper.AUDIO_GENRES_TABLE_NAME;
                where = "genre_id=" + uri.getPathSegments().get(3);
                break;

            case AUDIO_PLAYLISTS:
                out.table = MusicOpenHelper.AUDIO_PLAYLISTS_TABLE_NAME;
                break;

            case AUDIO_PLAYLISTS_ID:
                out.table = MusicOpenHelper.AUDIO_PLAYLISTS_TABLE_NAME;
                where = "_id=" + uri.getPathSegments().get(3);
                break;

            case AUDIO_PLAYLISTS_ID_MEMBERS:
                out.table = MusicOpenHelper.AUDIO_PLAYLISTS_MAP_TABLE_NAME;
                where = "playlist_id=" + uri.getPathSegments().get(3);
                break;

            case AUDIO_PLAYLISTS_ID_MEMBERS_ID:
                out.table = MusicOpenHelper.AUDIO_PLAYLISTS_MAP_TABLE_NAME;
                where = "playlist_id=" + uri.getPathSegments().get(3) +
                        " AND _id=" + uri.getPathSegments().get(5);
                break;

            case AUDIO_ALBUMART_ID:
                out.table = MusicOpenHelper.ALBUM_ART_TABLE_NAME;
                where = "album_id=" + uri.getPathSegments().get(3);
                break;

            case ARCHOS_SMB_SERVER_ID:
                where = "_id=" + uri.getLastPathSegment();
                //$FALL-THROUGH$
            case ARCHOS_SMB_SERVER:
                out.table = MusicOpenHelper.SMB_SERVER_TABLE_NAME;
                break;

            case FILES_ID:
            //case MTP_OBJECTS_ID:
                where = "_id=" + uri.getPathSegments().get(2);
                //$FALL-THROUGH$
            case FILES:
            //case MTP_OBJECTS:
                out.table = MusicOpenHelper.FILES_TABLE_NAME;
                break;

            case AUDIO_ALBUMS_ID:
                where = "album_id=" + uri.getPathSegments().get(3);
                //$FALL-THROUGH$
            case AUDIO_ALBUMS:
                out.table = MusicOpenHelper.ALBUMS_TABLE_NAME;
                break;

            case AUDIO_ARTISTS_ID:
                where = "artist_id=" + uri.getPathSegments().get(3);
                //$FALL-THROUGH$
            case AUDIO_ARTISTS:
                out.table = MusicOpenHelper.ARTISTS_TABLE_NAME;
                break;

            default:
                throw new UnsupportedOperationException(
                        "Unknown or unsupported URL: " + uri.toString());
        }

        // Add in the user requested WHERE clause, if needed
        if (!TextUtils.isEmpty(userWhere)) {
            if (!TextUtils.isEmpty(where)) {
                out.where = where + " AND (" + userWhere + ")";
            } else {
                out.where = userWhere;
            }
        } else {
            out.where = where;
        }
    }

    private static final UriMatcher URI_MATCHER =
            new UriMatcher(UriMatcher.NO_MATCH);

    private static final int AUDIO_MEDIA = 100;
    private static final int AUDIO_MEDIA_ID = 101;
    private static final int AUDIO_MEDIA_ID_GENRES = 102;
    private static final int AUDIO_MEDIA_ID_GENRES_ID = 103;
    private static final int AUDIO_MEDIA_ID_PLAYLISTS = 104;
    private static final int AUDIO_MEDIA_ID_PLAYLISTS_ID = 105;
    private static final int AUDIO_GENRES = 106;
    private static final int AUDIO_GENRES_ID = 107;
    private static final int AUDIO_GENRES_ID_MEMBERS = 108;
    private static final int AUDIO_GENRES_ALL_MEMBERS = 109;
    private static final int AUDIO_PLAYLISTS = 110;
    private static final int AUDIO_PLAYLISTS_ID = 111;
    private static final int AUDIO_PLAYLISTS_ID_MEMBERS = 112;
    private static final int AUDIO_PLAYLISTS_ID_MEMBERS_ID = 113;
    private static final int AUDIO_ARTISTS = 114;
    private static final int AUDIO_ARTISTS_ID = 115;
    private static final int AUDIO_ALBUMS = 116;
    private static final int AUDIO_ALBUMS_ID = 117;
    private static final int AUDIO_ARTISTS_ID_ALBUMS = 118;
    private static final int AUDIO_ALBUMART = 119;
    private static final int AUDIO_ALBUMART_ID = 120;
    private static final int AUDIO_ALBUMART_FILE_ID = 121;

    private static final int AUDIO_SEARCH_LEGACY = 400;
    private static final int AUDIO_SEARCH_BASIC = 401;
    private static final int AUDIO_SEARCH_FANCY = 402;
    private static final int AUDIO_SEARCH_ARCHOS = 403;

    private static final int FILES = 700;
    private static final int FILES_ID = 701;

    private static final int ARCHOS_SMB_SERVER = 803;
    private static final int ARCHOS_SMB_SERVER_ID = 804;

    private static final int RAW = 900;

    static {
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "raw/*", RAW);

        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/media", AUDIO_MEDIA);
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/media/#", AUDIO_MEDIA_ID);
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/media/#/genres", AUDIO_MEDIA_ID_GENRES);
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/media/#/genres/#", AUDIO_MEDIA_ID_GENRES_ID);
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/media/#/playlists", AUDIO_MEDIA_ID_PLAYLISTS);
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/media/#/playlists/#", AUDIO_MEDIA_ID_PLAYLISTS_ID);
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/genres", AUDIO_GENRES);
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/genres/#", AUDIO_GENRES_ID);
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/genres/#/members", AUDIO_GENRES_ID_MEMBERS);
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/genres/all/members", AUDIO_GENRES_ALL_MEMBERS);
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/playlists", AUDIO_PLAYLISTS);
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/playlists/#", AUDIO_PLAYLISTS_ID);
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/playlists/#/members", AUDIO_PLAYLISTS_ID_MEMBERS);
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/playlists/#/members/#", AUDIO_PLAYLISTS_ID_MEMBERS_ID);
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/artists", AUDIO_ARTISTS);
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/artists/#", AUDIO_ARTISTS_ID);
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/artists/#/albums", AUDIO_ARTISTS_ID_ALBUMS);
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/albums", AUDIO_ALBUMS);
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/albums/#", AUDIO_ALBUMS_ID);
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/albumart", AUDIO_ALBUMART);
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/albumart/#", AUDIO_ALBUMART_ID);
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/media/#/albumart", AUDIO_ALBUMART_FILE_ID);

        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/file", FILES);
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/file/#", FILES_ID);

        /**
         * @deprecated use the 'basic' or 'fancy' search Uris instead
         */
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/" + SearchManager.SUGGEST_URI_PATH_QUERY,
                AUDIO_SEARCH_LEGACY);
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/" + SearchManager.SUGGEST_URI_PATH_QUERY + "/*",
                AUDIO_SEARCH_LEGACY);

        // used for search suggestions
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/search/" + SearchManager.SUGGEST_URI_PATH_QUERY,
                AUDIO_SEARCH_BASIC);
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/search/" + SearchManager.SUGGEST_URI_PATH_QUERY +
                "/*", AUDIO_SEARCH_BASIC);

         // used for search suggestions - ARCHOS
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/search/archos/" + SearchManager.SUGGEST_URI_PATH_QUERY,
                AUDIO_SEARCH_ARCHOS);
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/search/archos/" + SearchManager.SUGGEST_URI_PATH_QUERY +
                "/*", AUDIO_SEARCH_ARCHOS);
        // used by the music app's search activity
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/search/fancy", AUDIO_SEARCH_FANCY);
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/audio/search/fancy/*", AUDIO_SEARCH_FANCY);

        // Archos additions
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/smb_server/#", ARCHOS_SMB_SERVER_ID);
        URI_MATCHER.addURI(MusicStore.AUTHORITY, "*/smb_server", ARCHOS_SMB_SERVER);
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        if (DBG) Log.d(TAG, "bulkInsert " + uri);
        int match = URI_MATCHER.match(uri);

        if (match != -1) {
            int result = 0;
            SQLiteDatabase db = mDbHolder.get();
            db.beginTransaction();
            try {
                result = super.bulkInsert(uri, values);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            if (result > 0)
                mCr.notifyChange(MusicStore.ALL_CONTENT_URI, null);
            return result;
        }
        return 0;
    }

    @Override
    public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations)
            throws OperationApplicationException {
        if (DBG) Log.d(TAG, "applyBatch");
        ContentProviderResult[] result = null;
        SQLiteDatabase db = mDbHolder.get();
        db.beginTransaction();
        try {
             result = super.applyBatch(operations);
             db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (result != null) {
            mCr.notifyChange(MusicStore.ALL_CONTENT_URI, null);
        }
        return result;
    }

    // For compatibility with the approximately 0 apps that used mediaprovider search in
    // releases 1.0, 1.1 or 1.5
    private String[] mSearchColsLegacy = new String[] {
            BaseColumns._ID,
            MediaColumns.MIME_TYPE,
            "(CASE WHEN grouporder=1 THEN " + R.drawable.ic_search_category_music_artist +
            " ELSE CASE WHEN grouporder=2 THEN " + R.drawable.ic_search_category_music_album +
            " ELSE " + R.drawable.ic_search_category_music_song + " END END" +
            ") AS " + SearchManager.SUGGEST_COLUMN_ICON_1,
            "0 AS " + SearchManager.SUGGEST_COLUMN_ICON_2,
            "text1 AS " + SearchManager.SUGGEST_COLUMN_TEXT_1,
            "text1 AS " + SearchManager.SUGGEST_COLUMN_QUERY,
            "CASE when grouporder=1 THEN data1 ELSE artist END AS data1",
            "CASE when grouporder=1 THEN data2 ELSE " +
                "CASE WHEN grouporder=2 THEN NULL ELSE album END END AS data2",
            "match as ar",
            SearchManager.SUGGEST_COLUMN_INTENT_DATA,
            "grouporder",
            "NULL AS itemorder" // We should be sorting by the artist/album/title keys, but that
                                // column is not available here, and the list is already sorted.
    };
    private String[] mSearchColsFancy = new String[] {
            BaseColumns._ID,
            MediaColumns.MIME_TYPE,
            ArtistColumns.ARTIST,
            AlbumColumns.ALBUM,
            MediaColumns.TITLE,
            "data1",
            "data2",
    };
    // If this array gets changed, please update the constant below to point to the correct item.
    private String[] mSearchColsBasic = new String[] {
            BaseColumns._ID,
            MediaColumns.MIME_TYPE,
            "(CASE WHEN grouporder=1 THEN " + R.drawable.ic_search_category_music_artist +
            " ELSE CASE WHEN grouporder=2 THEN " + R.drawable.ic_search_category_music_album +
            " ELSE " + R.drawable.ic_search_category_music_song + " END END" +
            ") AS " + SearchManager.SUGGEST_COLUMN_ICON_1,
            "text1 AS " + SearchManager.SUGGEST_COLUMN_TEXT_1,
            "text1 AS " + SearchManager.SUGGEST_COLUMN_QUERY,
            "(CASE WHEN grouporder=1 THEN '%1'" +  // %1 gets replaced with localized string.
            " ELSE CASE WHEN grouporder=3 THEN artist || ' - ' || album" +
            " ELSE CASE WHEN text2!='" + MediaStore.UNKNOWN_STRING + "' THEN text2" +
            " ELSE NULL END END END) AS " + SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA
    };
    // Position of the TEXT_2 item in the above array.
    private static final int SEARCH_COLUMN_BASIC_TEXT2 = 5;

    private String[] mSearchColsArchos = new String[] {
            android.provider.BaseColumns._ID,
            MediaColumns.MIME_TYPE,
            "coalesce("+SearchManager.SUGGEST_COLUMN_ICON_1+",(CASE WHEN grouporder=1 THEN " + R.drawable.ic_search_category_music_artist +
            " ELSE CASE WHEN grouporder=2 THEN " + R.drawable.ic_search_category_music_album +
            " ELSE " + R.drawable.ic_search_category_music_song + " END END" +
            ")) AS " + SearchManager.SUGGEST_COLUMN_ICON_1,
            "text1 AS " + SearchManager.SUGGEST_COLUMN_TEXT_1,
            "text1 AS " + SearchManager.SUGGEST_COLUMN_QUERY,
            "(CASE WHEN grouporder=1 THEN '%1'" +  // %1 gets replaced with localized string.
            " ELSE CASE WHEN grouporder=3 THEN artist || ' - ' || album" +
            " ELSE CASE WHEN text2!='" + MediaStore.UNKNOWN_STRING + "' THEN text2" +
            " ELSE NULL END END END) AS " + SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA
    };
    private Cursor doAudioSearch(SQLiteDatabase db, SQLiteQueryBuilder qb,
            Uri uri, String[] projectionIn, String selection,
            String[] selectionArgs, String sort, int mode,
            String limit) {

        String mSearchString = uri.getPath().endsWith("/") ? "" : uri.getLastPathSegment();
        mSearchString = mSearchString.replaceAll("  ", " ").trim().toLowerCase();

        String [] searchWords = mSearchString.length() > 0 ?
                mSearchString.split(" ") : new String[0];
        String [] wildcardWords = new String[searchWords.length];
        Collator col = Collator.getInstance();
        col.setStrength(Collator.PRIMARY);
        int len = searchWords.length;
        for (int i = 0; i < len; i++) {
            // Because we match on individual words here, we need to remove words
            // like 'a' and 'the' that aren't part of the keys.
            String key = MediaStore.Audio.keyFor(searchWords[i]);
            key = key.replace("\\", "\\\\");
            key = key.replace("%", "\\%");
            key = key.replace("_", "\\_");
            wildcardWords[i] =
                (searchWords[i].equals("a") || searchWords[i].equals("an") ||
                        searchWords[i].equals("the")) ? "%" : "%" + key + "%";
        }

        String where = "";
        for (int i = 0; i < searchWords.length; i++) {
            if (i == 0) {
                where = "match LIKE ? ESCAPE '\\'";
            } else {
                where += " AND match LIKE ? ESCAPE '\\'";
            }
        }

        qb.setTables("search");
        String [] cols;
        if (mode == AUDIO_SEARCH_FANCY) {
            cols = mSearchColsFancy;
        } else if (mode == AUDIO_SEARCH_BASIC) {
            cols = mSearchColsBasic;
        } else if (mode == AUDIO_SEARCH_ARCHOS) {
            cols = mSearchColsArchos;
            qb.setTables("search_archos");
        } else {
            cols = mSearchColsLegacy;
        }
        return qb.query(db, cols, where, wildcardWords, null, null, null, limit);
    }
}
