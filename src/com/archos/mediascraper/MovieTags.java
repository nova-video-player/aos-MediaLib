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
import android.content.ContentProviderOperation.Builder;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.provider.BaseColumns;
import android.text.TextUtils;

import com.archos.mediaprovider.video.ScraperStore;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediacenter.utils.ISO639codes;
import com.archos.mediascraper.ScraperImage.Type;


import com.archos.mediascraper.themoviedb3.SearchMovieResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

public class MovieTags extends VideoTags {
    private static final Logger log = LoggerFactory.getLogger(MovieTags.class);

    protected int mYear;
    protected String mReleaseDate;
    private String mOriginalLanguage = "und";
    private String mOriginalTitle = "";
    private String mSpokenLanguages = "";
    private String mTitleLanguage = "und";

    @SuppressWarnings("hiding") // this has to be defined for every parcelable this way
    public static final Parcelable.Creator<MovieTags> CREATOR = new Parcelable.Creator<MovieTags>() { 
        public MovieTags createFromParcel(Parcel in) {
            return new MovieTags(in);
        }

        public MovieTags[] newArray(int size) { 
            return new MovieTags[size]; 
        }
    };
    private List<ScraperTrailer> trailers;

    public MovieTags() {
        super();
    }

    public MovieTags(Parcel in) {
        super(in);
        readFromParcel(in);
    }

    public int getYear() { return mYear; }

    public void setReleaseDate(String releaseDate) { mReleaseDate = releaseDate; }
    public String getReleaseDate() { return mReleaseDate; }

    /**
     * Stores a language identifier, never a localized display name. TMDb supplies ISO 639-1;
     * accepts ISO 639-2/3 input too and persists its ISO 639-1 equivalent when known.
     */
    public void setOriginalLanguage(String originalLanguage) {
        if (originalLanguage == null) {
            mOriginalLanguage = "und";
            return;
        }
        String normalized = ISO639codes.getISO6391ForLetterCode(originalLanguage);
        mOriginalLanguage = normalized.isEmpty() ? "und" : normalized;
    }

    /** ISO 639-1 original language, or ISO 639-2 {@code und} when unknown. */
    public String getOriginalLanguage() { return mOriginalLanguage; }

    public void setOriginalTitle(String originalTitle) {
        mOriginalTitle = originalTitle == null ? "" : originalTitle;
    }

    /** Original (non-localized) TMDb title, or an empty string when unavailable. */
    public String getOriginalTitle() { return mOriginalTitle; }

    /**
     * Normalizes spoken-language codes and stores them as a stable comma-separated ISO 639-1 list.
     * Empty means TMDb supplied no usable spoken-language metadata.
     */
    public void setSpokenLanguages(List<String> spokenLanguages) {
        LinkedHashSet<String> normalized = new LinkedHashSet<String>();
        if (spokenLanguages != null) {
            for (String language : spokenLanguages) {
                String code = ISO639codes.getISO6391ForLetterCode(language);
                if (!code.isEmpty()) normalized.add(code);
            }
        }
        mSpokenLanguages = TextUtils.join(",", normalized);
    }

    /** Comma-separated ISO 639-1 spoken-language codes, or an empty string when unknown. */
    public String getSpokenLanguages() { return mSpokenLanguages; }

    /** Language of the localized title returned by TMDb, ISO 639-1 or {@code und}. */
    public void setTitleLanguage(String titleLanguage) {
        String normalized = titleLanguage == null ? "" : ISO639codes.getISO6391ForLetterCode(titleLanguage);
        mTitleLanguage = normalized.isEmpty() ? "und" : normalized;
    }

    public String getTitleLanguage() { return mTitleLanguage; }

    protected int mCollectionId = -1;
    public void setCollectionId(int collectionId) { mCollectionId = collectionId; }
    public int getCollectionId() { return mCollectionId; }

    protected String mCollectionName = null;
    public void setCollectionName(String collectionName) { mCollectionName = collectionName; }
    public String getCollectionName() { return mCollectionName; }

    protected String mCollectionDescription = null;
    public void setCollectionDescription(String collectionDescription) { mCollectionDescription = collectionDescription; }
    public String getCollectionDescription() { return mCollectionDescription; }

    protected String mCollectionPosterPath = null;
    public void setCollectionPosterPath(String collectionPosterUrl) { mCollectionPosterPath = collectionPosterUrl; }
    public String getCollectionPosterPath() { return mCollectionPosterPath; }

    protected String mCollectionPosterLargeUrl = null;
    public void setCollectionPosterLargeUrl(String collectionPosterLargeUrl) { mCollectionPosterLargeUrl = collectionPosterLargeUrl; }
    public String getCollectionPosterLargeUrl() { return mCollectionPosterLargeUrl; }
    protected String mCollectionPosterLargeFile = null;
    public void setCollectionPosterLargeFile(String collectionPosterLargeFile) { mCollectionPosterLargeFile = collectionPosterLargeFile; }
    public String getCollectionPosterLargeFile() { return mCollectionPosterLargeFile; }

    protected String mCollectionPosterThumbUrl = null;
    public void setCollectionPosterThumbUrl(String collectionPosterThumbUrl) { mCollectionPosterThumbUrl = collectionPosterThumbUrl; }
    public String getCollectionPosterThumbUrl() { return mCollectionPosterThumbUrl; }
    protected String mCollectionPosterThumbFile = null;
    public void setCollectionPosterThumbFile(String collectionPosterThumbFile) { mCollectionPosterThumbFile = collectionPosterThumbFile; }
    public String getCollectionPosterThumbFile() { return mCollectionPosterThumbFile; }

    protected String mCollectionBackdropPath = null;
    public void setCollectionBackdropPath(String collectionBackdropPath) { mCollectionBackdropPath = collectionBackdropPath; }
    public String getCollectionBackdropPath() { return mCollectionBackdropPath; }

    protected String mCollectionBackdropLargeUrl = null;
    public void setCollectionBackdropLargeUrl(String collectionBackdropLargeUrl) { mCollectionBackdropLargeUrl = collectionBackdropLargeUrl; }
    public String getCollectionBackdropLargeUrl() { return mCollectionBackdropLargeUrl; }
    protected String mCollectionBackdropLargeFile = null;
    public void setCollectionBackdropLargeFile(String collectionBackdropLargeFile) { mCollectionBackdropLargeFile = collectionBackdropLargeFile; }
    public String getCollectionBackdropLargeFile() { return mCollectionBackdropLargeFile; }

    protected String mCollectionBackdropThumbUrl = null;
    public void setCollectionBackdropThumbUrl(String collectionBackdropThumbUrl) { mCollectionBackdropThumbUrl = collectionBackdropThumbUrl; }
    public String getCollectionBackdropThumbUrl() { return mCollectionBackdropThumbUrl; }
    protected String mCollectionBackdropThumbFile = null;
    public void setCollectionBackdropThumbFile(String collectionBackdropThumbFile) { mCollectionBackdropThumbFile = collectionBackdropThumbFile; }
    public String getCollectionBackdropThumbFile() { return mCollectionBackdropThumbFile; }

    @Override
    public void setCover(File file) {
        if (file == null) return;
        if (getPosters() == null) {
            setPosters(ScraperImage.fromExistingCover(file.getPath(), Type.MOVIE_POSTER).asList());
        }
    }

    @Override
    public void setBackdrop(File file) {
        if (file == null) return;
        if (getBackdrops() == null || getBackdrops().isEmpty()) {
            setBackdrops(ScraperImage.fromExistingCover(file.getPath(), Type.MOVIE_BACKDROP).asList());
        }
    }

    @Override
    public long save(Context context, long videoId) {
        ContentResolver cr = context.getContentResolver();
        //------------------------------------------------------
        // Create a new entry for this movie in the database.
        //------------------------------------------------------
        ContentValues values = new ContentValues();
        values.put(ScraperStore.Movie.VIDEO_ID, Long.valueOf(videoId));
        values.put(ScraperStore.Movie.NAME, mTitle);
        values.put(ScraperStore.Movie.YEAR, Integer.valueOf(mYear));
        values.put(ScraperStore.Movie.RELEASE_DATE, mReleaseDate);
        values.put(ScraperStore.Movie.ORIGINAL_LANGUAGE, mOriginalLanguage);
        values.put(ScraperStore.Movie.ORIGINAL_TITLE, mOriginalTitle);
        values.put(ScraperStore.Movie.SPOKEN_LANGUAGES, mSpokenLanguages);
        values.put(ScraperStore.Movie.TITLE_LANGUAGE, mTitleLanguage);
        values.put(ScraperStore.Movie.RATING, Float.valueOf(mRating));
        values.put(ScraperStore.Movie.COLLECTION_ID, Integer.valueOf(mCollectionId));

        /*if(trailer!=null)
            values.put(ScraperStore.Movie.TRAILER_KEY, Float.valueOf(trailer.getKey()));*/
                    values.put(ScraperStore.Movie.PLOT, mPlot);
        values.put(ScraperStore.Movie.ONLINE_ID, Long.valueOf(mOnlineId));
        values.put(ScraperStore.Movie.IMDB_ID, mImdbId);
        values.put(ScraperStore.Movie.CONTENT_RATING, mContentRating);
        File cover = getCover();
        if(cover != null)
            values.put(ScraperStore.Movie.COVER, cover.getPath());

        ScraperImage backdrop = getDefaultBackdrop();
        if (backdrop != null) {
            values.put(ScraperStore.Movie.BACKDROP, backdrop.getLargeFile());
            values.put(ScraperStore.Movie.BACKDROP_URL, backdrop.getLargeUrl());
        }

        values.put(ScraperStore.Movie.ACTORS_FORMATTED, getActorsFormatted());
        values.put(ScraperStore.Movie.DIRECTORS_FORMATTED, getDirectorsFormatted());
        values.put(ScraperStore.Movie.WRITERS_FORMATTED, getWritersFormatted());
        values.put(ScraperStore.Movie.GERNES_FORMATTED, getGenresFormatted());
        values.put(ScraperStore.Movie.STUDIOS_FORMATTED, getStudiosFormatted());

        // build list of operations
        Builder cop = null;
        ArrayList<ContentProviderOperation> allOperations = new ArrayList<ContentProviderOperation>();

        // first insert the movie base info - item 0 for backreferences
        cop = ContentProviderOperation.newInsert(ScraperStore.Movie.URI.BASE);
        cop.withValues(values);
        allOperations.add(cop.build());

        for(String studio: mStudios) {
            cop = ContentProviderOperation.newInsert(ScraperStore.Studio.URI.MOVIE);
            cop.withValue(ScraperStore.Movie.Studio.NAME, studio);
            cop.withValueBackReference(ScraperStore.Movie.Studio.MOVIE, 0);
            allOperations.add(cop.build());
        }

        for(String director: mDirectors) {
            cop = ContentProviderOperation.newInsert(ScraperStore.Director.URI.MOVIE);
            cop.withValue(ScraperStore.Movie.Director.NAME, director);
            cop.withValueBackReference(ScraperStore.Movie.Director.MOVIE, 0);
            allOperations.add(cop.build());
        }

        for(String writer: mWriters) {
            cop = ContentProviderOperation.newInsert(ScraperStore.Writer.URI.MOVIE);
            cop.withValue(ScraperStore.Movie.Writer.NAME, writer);
            cop.withValueBackReference(ScraperStore.Movie.Writer.MOVIE, 0);
            allOperations.add(cop.build());
        }

        for(String actorName: mActors.keySet()) {
            cop = ContentProviderOperation.newInsert(ScraperStore.Actor.URI.MOVIE);
            cop.withValue(ScraperStore.Movie.Actor.NAME, actorName);
            cop.withValueBackReference(ScraperStore.Movie.Actor.MOVIE, 0);
            cop.withValue(ScraperStore.Movie.Actor.ROLE, mActors.get(actorName));
            allOperations.add(cop.build());
        }

        for(String genre: mGenres) {
            cop = ContentProviderOperation.newInsert(ScraperStore.Genre.URI.MOVIE);
            cop.withValue(ScraperStore.Movie.Genre.NAME, genre);
            cop.withValueBackReference(ScraperStore.Movie.Genre.MOVIE, 0);
            allOperations.add(cop.build());
        }

        if (mCollectionId > 0) {
            // Check if this Movie Collection is already referenced in the scraperDB
            if (! isCollectionAlreadyKnown(mCollectionId, context)) {
                if (log.isDebugEnabled()) log.debug("save: collection {} does not exist, saving it", mCollectionId);
                cop = ContentProviderOperation.newInsert(ScraperStore.MovieCollections.URI.BASE);
                cop.withValue(ScraperStore.MovieCollections.ID, mCollectionId);
                cop.withValue(ScraperStore.MovieCollections.NAME, mCollectionName);
                cop.withValue(ScraperStore.MovieCollections.DESCRIPTION, mCollectionDescription);
                cop.withValue(ScraperStore.MovieCollections.POSTER_LARGE_URL, mCollectionPosterLargeUrl);
                cop.withValue(ScraperStore.MovieCollections.POSTER_LARGE_FILE, mCollectionPosterLargeFile);
                cop.withValue(ScraperStore.MovieCollections.POSTER_THUMB_URL, mCollectionPosterThumbUrl);
                cop.withValue(ScraperStore.MovieCollections.POSTER_THUMB_FILE, mCollectionPosterThumbFile);
                cop.withValue(ScraperStore.MovieCollections.BACKDROP_LARGE_URL, mCollectionBackdropLargeUrl);
                cop.withValue(ScraperStore.MovieCollections.BACKDROP_LARGE_FILE, mCollectionBackdropLargeFile);
                cop.withValue(ScraperStore.MovieCollections.BACKDROP_THUMB_URL, mCollectionBackdropThumbUrl);
                cop.withValue(ScraperStore.MovieCollections.BACKDROP_THUMB_FILE, mCollectionBackdropThumbFile);
                allOperations.add(cop.build());
            } else {
                if (log.isDebugEnabled()) log.debug("save: collection {} already exists, updating only non-null fields", mCollectionId);
                cop = ContentProviderOperation.newUpdate(ScraperStore.MovieCollections.URI.BASE)
                        .withSelection(ScraperStore.MovieCollections.ID + "=?", new String[]{String.valueOf(mCollectionId)});
                
                // Always update name and description if available
                if (mCollectionName != null) {
                    cop.withValue(ScraperStore.MovieCollections.NAME, mCollectionName);
                }
                if (mCollectionDescription != null) {
                    cop.withValue(ScraperStore.MovieCollections.DESCRIPTION, mCollectionDescription);
                }
                
                // Only update poster fields if this movie has poster data
                if (mCollectionPosterLargeUrl != null) {
                    cop.withValue(ScraperStore.MovieCollections.POSTER_LARGE_URL, mCollectionPosterLargeUrl);
                }
                if (mCollectionPosterLargeFile != null) {
                    cop.withValue(ScraperStore.MovieCollections.POSTER_LARGE_FILE, mCollectionPosterLargeFile);
                }
                if (mCollectionPosterThumbUrl != null) {
                    cop.withValue(ScraperStore.MovieCollections.POSTER_THUMB_URL, mCollectionPosterThumbUrl);
                }
                if (mCollectionPosterThumbFile != null) {
                    cop.withValue(ScraperStore.MovieCollections.POSTER_THUMB_FILE, mCollectionPosterThumbFile);
                }
                
                // Only update backdrop fields if this movie has backdrop data
                if (mCollectionBackdropLargeUrl != null) {
                    cop.withValue(ScraperStore.MovieCollections.BACKDROP_LARGE_URL, mCollectionBackdropLargeUrl);
                }
                if (mCollectionBackdropLargeFile != null) {
                    cop.withValue(ScraperStore.MovieCollections.BACKDROP_LARGE_FILE, mCollectionBackdropLargeFile);
                }
                if (mCollectionBackdropThumbUrl != null) {
                    cop.withValue(ScraperStore.MovieCollections.BACKDROP_THUMB_URL, mCollectionBackdropThumbUrl);
                }
                if (mCollectionBackdropThumbFile != null) {
                    cop.withValue(ScraperStore.MovieCollections.BACKDROP_THUMB_FILE, mCollectionBackdropThumbFile);
                }
                
                allOperations.add(cop.build());
            }
        }

        // backreferences to first poster / backdrop. Set to the position of
        // the operation in "allOperations"
        int posterId = -1;
        int backdropId = -1;
        for (ScraperImage image : safeList(mPosters)) {
            if (posterId == -1)
                posterId = allOperations.size();
            allOperations.add(image.getSaveOperationBackreferenced(0));
        }
        for (ScraperImage image : safeList(mBackdrops)) {
            if (backdropId == -1)
                backdropId = allOperations.size();
            allOperations.add(image.getSaveOperationBackreferenced(0));
        }

        for (ScraperTrailer trailer : safeList(trailers)) {
            allOperations.add(trailer.getSaveOperationBackreferenced(0));
        }

        ContentValues backRef = null;
        if (posterId != -1) {
            backRef = new ContentValues();
            backRef.put(ScraperStore.Movie.POSTER_ID, Integer.valueOf(posterId));
        }
        if (backdropId != -1) {
            if (backRef == null) backRef = new ContentValues();
            backRef.put(ScraperStore.Movie.BACKDROP_ID, Integer.valueOf(backdropId));
        }
        if (backRef != null) {
            allOperations.add(
                    ContentProviderOperation.newUpdate(ScraperStore.Movie.URI.BASE)
                    .withValueBackReferences(backRef)// will replace posterID with ID returned by execution of allOperations
                    .withSelection(ScraperStore.Movie.ID + "=?", new String[] { "-1" })
                            .withSelectionBackReference(0, 0)
                    .build()
                    );
        }
        // update runtime in video db, it's not part of the scraper db part
        if (mRuntimeMs > 0) {
            allOperations.add(
                    ContentProviderOperation.newUpdate(VideoStore.Video.Media.EXTERNAL_CONTENT_URI)
                    .withSelection(
                            BaseColumns._ID + "=? AND IFNULL(" + VideoStore.Video.VideoColumns.DURATION + ", 0) <= 0",
                            new String[] { String.valueOf(videoId) }
                            )
                    .withValue(VideoStore.Video.VideoColumns.DURATION, Long.valueOf(mRuntimeMs))
                    .build()
                    );
        }
        long result = -1;
        try {
            ContentProviderResult[] results = cr.applyBatch(ScraperStore.AUTHORITY, allOperations);
            if (results != null && results.length > 0) {
                result = ContentUris.parseId(results[0].uri);
            }
        } catch (RemoteException | OperationApplicationException e) {
            log.error("Exception", e);
        }
        return result;
    }

    @Override
    public List<ScraperImage> getAllPostersInDb(Context context) {
        ContentResolver cr = context.getContentResolver();
        Uri uri = ContentUris.withAppendedId(ScraperStore.MoviePosters.URI.BY_MOVIE_ID, mId);
        Cursor cursor = cr.query(uri, null, null, null, null);
        List<ScraperImage> result = null;
        if (cursor != null) {
            result = new ArrayList<ScraperImage>(cursor.getCount());
            while (cursor.moveToNext()) {
                result.add(ScraperImage.fromCursor(cursor, Type.MOVIE_POSTER));
            }
            cursor.close();
        }
        return result;
    }

    public List<ScraperTrailer> getAllTrailersInDb(Context context) {
        ContentResolver cr = context.getContentResolver();
        Uri uri = ContentUris.withAppendedId(ScraperStore.MovieTrailers.URI.BY_MOVIE_ID, mId);
        Cursor cursor = cr.query(uri, null, null, null, null);
        List<ScraperTrailer> result = null;
        if (cursor != null) {
            result = new ArrayList<>(cursor.getCount());
            while (cursor.moveToNext()) {
                result.add(ScraperTrailer.fromCursor(cursor, ScraperTrailer.Type.MOVIE_TRAILER));
            }
            cursor.close();
        }
        return result;
    }

    @Override
    public List<ScraperImage> getAllBackdropsInDb(Context context) {
        ContentResolver cr = context.getContentResolver();
        Uri uri = ContentUris.withAppendedId(ScraperStore.MovieBackdrops.URI.BY_MOVIE_ID, mId);
        Cursor cursor = cr.query(uri, null, null, null, null);
        List<ScraperImage> result = null;
        if (cursor != null) {
            result = new ArrayList<ScraperImage>(cursor.getCount());
            while (cursor.moveToNext()) {
                result.add(ScraperImage.fromCursor(cursor, Type.MOVIE_BACKDROP));
            }
            cursor.close();
        }
        return result;
    }

    public void setYear(int year) { mYear = year; }

    @Override
    public String toString() {
        return super.toString() + ", " + mYear;
    }

    private void readFromParcel(Parcel in) {
        mYear = in.readInt();
        mReleaseDate = in.readString();
        setOriginalLanguage(in.readString());
        setOriginalTitle(in.readString());
        mSpokenLanguages = in.readString();
        if (mSpokenLanguages == null) mSpokenLanguages = "";
        setTitleLanguage(in.readString());
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        super.writeToParcel(out, flags);
        out.writeInt(mYear);
        out.writeString(mReleaseDate);
        out.writeString(mOriginalLanguage);
        out.writeString(mOriginalTitle);
        out.writeString(mSpokenLanguages);
        out.writeString(mTitleLanguage);
    }

    /** Add this (local) image as the default poster */
    public void addDefaultPoster(Context context, Uri localImage, Uri videoFile) {
        ScraperImage image = new ScraperImage(ScraperImage.Type.MOVIE_POSTER, videoFile.toString());
        String imageUrl = localImage.toString();
        image.setLargeUrl(imageUrl);
        image.setThumbUrl(imageUrl);
        image.generateFileNames(context);
        addDefaultPoster(image);
    }

    /** Add this (local) image as the default backdrop */
    public void addDefaultBackdrop(Context context, Uri localImage, Uri videoFile) {
        ScraperImage image = new ScraperImage(ScraperImage.Type.MOVIE_BACKDROP, videoFile.toString());
        String imageUrl = localImage.toString();
        image.setLargeUrl(imageUrl);
        image.setThumbUrl(imageUrl);
        image.generateFileNames(context);
        addDefaultBackdrop(image);
    }

    public static boolean isCollectionAlreadyKnown(Integer collectionId, Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        String[] selectionArgs = {String.valueOf(collectionId)};
        String[] baseProjection = {ScraperStore.MovieCollections.ID};
        String nameSelection = ScraperStore.MovieCollections.ID + "=?";
        Cursor cursor = contentResolver.query(ScraperStore.MovieCollections.URI.BASE, baseProjection,
                nameSelection, selectionArgs, null);
        boolean isKnown = false;
        if (cursor != null) {
            try {
                isKnown = cursor.moveToFirst();
            } finally {
                cursor.close();
            }
        }
        return isKnown;
    }

    public void setTrailers(List<ScraperTrailer> trailers) {
        this.trailers = trailers;
    }

    public List<ScraperTrailer> getTrailers() {
        return trailers;
    }

    /** Add this url as the default show poster */
    public void addDefaultPosterTMDB(Context context, String path) {
        if (log.isDebugEnabled()) log.debug("addDefaultPosterTMDB: poster {}{}", ScraperImage.TMPL, path);
        ScraperImage image = new ScraperImage(ScraperImage.Type.MOVIE_POSTER, mTitle);
        image.setLargeUrl(ScraperImage.TMPL + path);
        image.setThumbUrl(ScraperImage.TMPT + path);
        image.generateFileNames(context);
        addDefaultPoster(image);
    }

    /** Add this url image as the default show backdrop */
    public void addDefaultBackdropTMDB(Context context, String path) {
        if (log.isDebugEnabled()) log.debug("addDefaultPosterTMDB: backdrop {}{}", ScraperImage.TMBL, path);
        ScraperImage image = new ScraperImage(ScraperImage.Type.MOVIE_BACKDROP, mTitle);
        image.setLargeUrl(ScraperImage.TMBL + path);
        image.setThumbUrl(ScraperImage.TMBT + path);
        image.generateFileNames(context);
        addDefaultBackdrop(image);
    }

    public static SearchMovieResult getMovieResultIfAlreadyKnown(Context context, String searchQuery, String year, Uri fileUri) {
        ContentResolver contentResolver = context.getContentResolver();
        String[] baseProjection = {ScraperStore.Movie.ID, ScraperStore.Movie.NAME}  ;

        //Query the databbase.
        if (year == null) year = "";
        Cursor cursor = contentResolver.query(ScraperStore.Movie.URI.ALL ,
                baseProjection,  "TRIM(REPLACE(REPLACE(REPLACE(REPLACE(name_movie, ':', ''), '-', ' '), '.', ' '), '& ', '')) LIKE ? AND " + ScraperStore.Movie.YEAR + " LIKE ?" , new String[] { "%" + searchQuery.trim() + "%", year + "%" }, null);

        //Check cursor count and return if positive.
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    //Get the columns, so we can grab the data.
                    int idxVideoId = cursor.getColumnIndexOrThrow(ScraperStore.Movie.ID);
                    int idxName = cursor.getColumnIndexOrThrow(ScraperStore.Movie.NAME);

                    //Create a Search Result and populate it.
                    SearchMovieResult myResult = new SearchMovieResult();
                    SearchResult result = new SearchResult();
                    result.setMovie();
                    result.setFile(fileUri);
                    result.setId((int) cursor.getLong(idxVideoId));
                    result.setOriginalTitle( cursor.getString(idxName));
                    result.setTitle(cursor.getString(idxName));
                    result.setYear(year);
                    result.fromDB = true;

                    //Add the resut to a list, to pass back.
                    myResult.result = new LinkedList<>();
                    myResult.result.add(result);
                    myResult.status = ScrapeStatus.OKAY;
                    return myResult;
                }
            } finally {
                cursor.close();
            }
        }
        return null;
    }
}
