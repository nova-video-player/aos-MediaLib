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

import com.archos.mediaprovider.video.ScraperStore;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.ScraperImage.Type;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MovieTags extends VideoTags {
    private static final Logger log = LoggerFactory.getLogger(MovieTags.class);

    protected int mYear;

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
    public long save(Context context, long videoId) {
        ContentResolver cr = context.getContentResolver();
        //------------------------------------------------------
        // Create a new entry for this movie in the database.
        //------------------------------------------------------
        ContentValues values = new ContentValues();
        values.put(ScraperStore.Movie.VIDEO_ID, Long.valueOf(videoId));
        values.put(ScraperStore.Movie.NAME, mTitle);
        values.put(ScraperStore.Movie.YEAR, Integer.valueOf(mYear));
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

        ScraperImage actorphoto = getDefaultActorPhoto();
        if (actorphoto != null) {
            values.put(ScraperStore.Movie.ACTORPHOTO, actorphoto.getLargeFile());
            values.put(ScraperStore.Movie.ACTORPHOTO, actorphoto.getLargeUrl());
        }

        ScraperImage studiologo = getDefaultStudioLogo();
        if (studiologo != null) {
            values.put(ScraperStore.Movie.STUDIOLOGO, studiologo.getLargeFile());
            values.put(ScraperStore.Movie.STUDIOLOGO, studiologo.getLargeUrl());
        }

        ScraperImage clearlogo = getDefaultClearLogo();
        if (clearlogo != null) {
            values.put(ScraperStore.Movie.CLEARLOGO, clearlogo.getLargeFile());
            values.put(ScraperStore.Movie.CLEARLOGO, clearlogo.getLargeUrl());
        }

        values.put(ScraperStore.Movie.ACTORS_FORMATTED, getActorsFormatted());
        values.put(ScraperStore.Movie.DIRECTORS_FORMATTED, getDirectorsFormatted());
        values.put(ScraperStore.Movie.WRITERS_FORMATTED, getWritersFormatted());
        values.put(ScraperStore.Movie.TAGLINES_FORMATTED, getTaglinesFormatted());
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

        for(String tagline: mTaglines) {
            cop = ContentProviderOperation.newInsert(ScraperStore.Tagline.URI.MOVIE);
            cop.withValue(ScraperStore.Movie.Tagline.NAME, tagline);
            cop.withValueBackReference(ScraperStore.Movie.Tagline.MOVIE, 0);
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

        if (mCollectionId != -1) {
            // Check if this Movie Collection is already referenced in the scraperDB
            if (! isCollectionAlreadyKnown(mCollectionId, context)) {
                log.debug("save: collection " + mCollectionId + " does not exist, saving it");
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
                log.debug("save: collection " + mCollectionId + " already exists, skipping insert");
            }
        }

        // backreferences to first poster / backdrop. Set to the position of
        // the operation in "allOperations"
        int posterId = -1;
        int backdropId = -1;
        int actorphotoId = -1;
        int studiologoId = -1;
        int clearlogoId = -1;
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
        for (ScraperImage image : safeList(mActorPhotos)) {
            if (actorphotoId == -1)
                actorphotoId = allOperations.size();
            allOperations.add(image.getSaveOperationBackreferenced(0));
        }

        for (ScraperImage image : safeList(mStudioLogos)) {
            if (studiologoId == -1)
                studiologoId = allOperations.size();
            allOperations.add(image.getSaveOperationBackreferenced(0));
        }

        for (ScraperImage image : safeList(mClearLogos)) {
            if (clearlogoId == -1)
                clearlogoId = allOperations.size();
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
        if (actorphotoId != -1) {
            if (backRef == null) backRef = new ContentValues();
            backRef.put(ScraperStore.Movie.ACTORPHOTO_ID, Integer.valueOf(actorphotoId));
        }
        if (studiologoId != -1) {
            if (backRef == null) backRef = new ContentValues();
            backRef.put(ScraperStore.Movie.STUDIOLOGO_ID, Integer.valueOf(studiologoId));
        }
        if (clearlogoId != -1) {
            if (backRef == null) backRef = new ContentValues();
            backRef.put(ScraperStore.Movie.CLEARLOGO_ID, Integer.valueOf(clearlogoId));
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
        } catch (RemoteException e) {
            log.error("Exception :" + e, e);
        } catch (OperationApplicationException e) {
            log.error("Exception :" + e, e);
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

    @Override
    public List<ScraperImage> getAllNetworkLogosInDb(Context context) {
        ContentResolver cr = context.getContentResolver();
        List<ScraperImage> result = null;
        return result;
    }

    @Override
    public List<ScraperImage> getAllActorPhotosInDb(Context context) {
        ContentResolver cr = context.getContentResolver();
        Uri uri = ContentUris.withAppendedId(ScraperStore.MovieActorPhotos.URI.BY_MOVIE_ID, mId);
        Cursor cursor = cr.query(uri, null, null, null, null);
        List<ScraperImage> result = null;
        if (cursor != null) {
            result = new ArrayList<ScraperImage>(cursor.getCount());
            while (cursor.moveToNext()) {
                result.add(ScraperImage.fromCursor(cursor, Type.MOVIE_ACTORPHOTO));
            }
            cursor.close();
        }
        return result;
    }

    @Override
    public List<ScraperImage> getAllClearLogosInDb(Context context) {
        ContentResolver cr = context.getContentResolver();
        Uri uri = ContentUris.withAppendedId(ScraperStore.MovieClearLogos.URI.BY_MOVIE_ID, mId);
        Cursor cursor = cr.query(uri, null, null, null, null);
        List<ScraperImage> result = null;
        if (cursor != null) {
            result = new ArrayList<ScraperImage>(cursor.getCount());
            while (cursor.moveToNext()) {
                result.add(ScraperImage.fromCursor(cursor, Type.MOVIE_CLEARLOGO));
            }
            cursor.close();
        }
        return result;
    }

    @Override
    public List<ScraperImage> getAllStudioLogosInDb(Context context) {
        ContentResolver cr = context.getContentResolver();
        Uri uri = ContentUris.withAppendedId(ScraperStore.MovieStudioLogos.URI.BY_MOVIE_ID, mId);
        Cursor cursor = cr.query(uri, null, null, null, null);
        List<ScraperImage> result = null;
        if (cursor != null) {
            result = new ArrayList<ScraperImage>(cursor.getCount());
            while (cursor.moveToNext()) {
                result.add(ScraperImage.fromCursor(cursor, Type.MOVIE_STUDIOLOGO));
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
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        super.writeToParcel(out, flags);
        out.writeInt(mYear);
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

    /** Add this (local) image as the default ActorPhoto */
    public void addDefaultActorPhoto(Context context, Uri localImage, Uri videoFile) {
        ScraperImage image = new ScraperImage(ScraperImage.Type.MOVIE_ACTORPHOTO, videoFile.toString());
        String imageUrl = localImage.toString();
        image.setLargeUrl(imageUrl);
        image.setThumbUrl(imageUrl);
        image.generateFileNames(context);
        addDefaultActorPhoto(image);
    }

    /** Add this (local) image as the default StudioLogo */
    public void addDefaultStudioLogo(Context context, Uri localImage, Uri videoFile) {
        ScraperImage image = new ScraperImage(ScraperImage.Type.MOVIE_STUDIOLOGO, videoFile.toString());
        String imageUrl = localImage.toString();
        image.setLargeUrl(imageUrl);
        image.setThumbUrl(imageUrl);
        image.generateFileNames(context);
        addDefaultStudioLogo(image);
    }

    /** Add this (local) image as the default ClearLogo */
    public void addDefaultClearLogo(Context context, Uri localImage, Uri videoFile) {
        ScraperImage image = new ScraperImage(ScraperImage.Type.MOVIE_CLEARLOGO, videoFile.toString());
        String imageUrl = localImage.toString();
        image.setLargeUrl(imageUrl);
        image.setThumbUrl(imageUrl);
        image.generateFileNames(context);
        addDefaultClearLogo(image);
    }

    public static boolean isCollectionAlreadyKnown(Integer collectionId, Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        String[] selectionArgs = {String.valueOf(collectionId)};
        String[] baseProjection = {ScraperStore.MovieCollections.ID};
        String nameSelection = ScraperStore.MovieCollections.ID + "=?";
        Cursor cursor = contentResolver.query(ScraperStore.MovieCollections.URI.BASE, baseProjection,
                nameSelection, selectionArgs, null);
        Boolean isKnown = false;
        if (cursor != null) isKnown = cursor.moveToFirst();
        cursor.close();
        return isKnown;
    }

    public void setTrailers(List<ScraperTrailer> trailers) {
        this.trailers = trailers;
    }

    /** Add this url as the default show poster */
    public void addDefaultPosterTMDB(Context context, String path) {
        log.debug("addDefaultPosterTMDB: poster " + ScraperImage.TMPL + path);
        ScraperImage image = new ScraperImage(ScraperImage.Type.MOVIE_POSTER, mTitle);
        image.setLargeUrl(ScraperImage.TMPL + path);
        image.setThumbUrl(ScraperImage.TMPT + path);
        image.generateFileNames(context);
        addDefaultPoster(image);
    }

    /** Add this url image as the default show backdrop */
    public void addDefaultBackdropTMDB(Context context, String path) {
        log.debug("addDefaultPosterTMDB: backdrop " + ScraperImage.TMBL + path);
        ScraperImage image = new ScraperImage(ScraperImage.Type.MOVIE_BACKDROP, mTitle);
        image.setLargeUrl(ScraperImage.TMBL + path);
        image.setThumbUrl(ScraperImage.TMBT + path);
        image.generateFileNames(context);
        addDefaultBackdrop(image);
    }

    /** Add this url image as the default movie actor photos */
    public void addDefaultActorPhotoTMDB(Context context, String path) {
        log.debug("addDefaultActorPhotoTMDB: actorphoto " + ScraperImage.AP + path);
        ScraperImage image = new ScraperImage(ScraperImage.Type.MOVIE_ACTORPHOTO, mTitle);
        image.setLargeUrl(ScraperImage.AP + path);
        image.setThumbUrl(ScraperImage.AP + path);
        image.generateFileNames(context);
        addDefaultActorPhoto(image);
    }

    /** Add this url image as the default movie studiologos */
    public void addDefaultStudioLogoGITHUB(Context context, String path) {
        log.debug("addDefaultStudioLogoGITHUB: studiologo " + ScraperImage.GSNL + path);
        ScraperImage image = new ScraperImage(ScraperImage.Type.MOVIE_STUDIOLOGO, mTitle);
        image.setLargeUrl(ScraperImage.GSNL + path);
        image.setThumbUrl(ScraperImage.GSNL + path);
        image.generateFileNames(context);
        addDefaultStudioLogo(image);
    }

    /** Add this url image as the default movie ClearLogos */
    public void addDefaultClearLogoFTV(Context context, String path) {
        ScraperImage image = new ScraperImage(ScraperImage.Type.MOVIE_CLEARLOGO, mTitle);
        image.setLargeUrl(path);
        image.setThumbUrl(path);
        image.generateFileNames(context);
        addDefaultClearLogo(image);
    }
}
