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

package com.archos.mediascraper;

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;

import com.archos.mediaprovider.video.ScraperStore;

/**
 * Created by alexandre on 09/11/15.
 */
public class ScraperTrailer {

    public final String mName;
    public final String mVideoKey;
    public final String mSite;
    private final String mLang;



    public enum Type {
        MOVIE_TRAILER(
                ScraperStore.MovieTrailers.MOVIE_ID, ScraperStore.MovieTrailers.VIDEO_KEY,
                ScraperStore.MovieTrailers.SITE, ScraperStore.MovieTrailers.NAME,
        ScraperStore.MovieTrailers.LANG, ScraperStore.MovieTrailers.URI.BASE),
        SHOW_TRAILER(
                ScraperStore.MovieTrailers.MOVIE_ID, ScraperStore.MovieTrailers.VIDEO_KEY,
                ScraperStore.MovieTrailers.SITE, ScraperStore.MovieTrailers.NAME,
                ScraperStore.MovieTrailers.LANG, ScraperStore.MovieTrailers.URI.BASE);

        public final Uri baseUri;
        public final String movieIdColumn;
        public final String videoKeyColumn;
        public final String siteColumn;
        public final String nameColumn;
        public final String langColumn;

        Type(String movieId, String videoKey, String site, String name, String lang, Uri uri) {
            movieIdColumn = movieId;
            videoKeyColumn = videoKey;
            siteColumn = site;
            nameColumn = name;
            langColumn = lang;
            baseUri = uri;
        }
    }



    private final Type mType;



    public ScraperTrailer(Type type, String name, String videoKey, String site, String lang) {
        mType = type;
        mName = name;
        mVideoKey = videoKey;
        mSite = site;
        mLang = lang;
    }

    public ContentProviderOperation getSaveOperationBackreferenced(int backref) {
        ContentValues cv = toContentValues(0); // some bogus value - value is overwritten
        return ContentProviderOperation.newInsert(mType.baseUri)
                .withValues(cv)
                .withValueBackReference(mType.movieIdColumn, backref) // overwrite
                .build();
    }

    public ContentValues toContentValues(long remoteId) {
        ContentValues cv = new ContentValues();
        cvPut(cv, mType.movieIdColumn, String.valueOf(remoteId));
        cvPut(cv, mType.nameColumn, mName);
        cvPut(cv, mType.videoKeyColumn, mVideoKey);
        cvPut(cv, mType.siteColumn, mSite);
        cvPut(cv, mType.langColumn, mLang);
        return cv;
    }
    private static final void cvPut (ContentValues cv, String key, String value) {
        if (cv != null && key != null) cv.put(key, value);
    }

    public Uri getUrl() {
        String urlScheme = "";
        if(mSite.equals("YouTube")){
            urlScheme = "https://www.youtube.com/watch?v=%s";
        }
        Log.d("urldebug",  mVideoKey);
        Log.d("urldebug", String.format(urlScheme, mVideoKey));
        return Uri.parse(String.format(urlScheme, mVideoKey));
    }
    public static ScraperTrailer fromCursor(Cursor cur, Type type) {
        long imageId = cur.getLong(cur.getColumnIndexOrThrow(BaseColumns._ID));
        long remoteId = cur.getLong(cur.getColumnIndexOrThrow(type.movieIdColumn));
        String name = cur.getString(cur.getColumnIndexOrThrow(type.nameColumn));
        String videoKey = cur.getString(cur.getColumnIndexOrThrow(type.videoKeyColumn));
        String site = cur.getString(cur.getColumnIndexOrThrow(type.siteColumn));
        String lang = cur.getString(cur.getColumnIndexOrThrow(type.langColumn));

        ScraperTrailer image = new ScraperTrailer(type,  name, videoKey, site, lang);

        return image;
    }
}
