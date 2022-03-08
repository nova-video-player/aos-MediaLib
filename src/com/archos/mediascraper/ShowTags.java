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
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.ContentProviderOperation.Builder;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.text.TextUtils;

import com.archos.mediaprovider.video.ScraperStore;
import com.archos.mediascraper.ScraperImage.Type;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class ShowTags extends VideoTags {
    private static final Logger log = LoggerFactory.getLogger(ShowTags.class);
    private static final SimpleDateFormat sDateFormatter = new SimpleDateFormat("yyyy-MM-dd");
    protected Date mPremiered;

    @SuppressWarnings("hiding") // this has to be defined for every parcelable this way
    public static final Parcelable.Creator<ShowTags> CREATOR = new Parcelable.Creator<ShowTags>() { 
        public ShowTags createFromParcel(Parcel in) {
            return new ShowTags(in);
        }

        public ShowTags[] newArray(int size) { 
            return new ShowTags[size];
        }
    };

    public ShowTags() {
        super();
    }

    public ShowTags(Parcel in) {
        super(in);
        readFromParcel(in);
    }

    @Override
    public void setCover(File file) {
        if (file == null) return;
        if (getPosters() == null) {
            setPosters(ScraperImage.fromExistingCover(file.getPath(), Type.SHOW_POSTER).asList());
        }
    }

    public Date getPremiered() { return mPremiered; }

    private static final String[] BASE_PROJECTION = {
        ScraperStore.Show.ID,              // 0
        ScraperStore.Show.COVER,           // 1
        ScraperStore.Show.RATING,          // 2
        ScraperStore.Show.CONTENT_RATING,  // 3
        ScraperStore.Show.BACKDROP,        // 4
        ScraperStore.Show.IMDB_ID,         // 5
        ScraperStore.Show.ONLINE_ID,       // 6
        ScraperStore.Show.POSTER_ID,       // 7
        ScraperStore.Show.BACKDROP_ID      // 8
    };

    private static final String NAME_SELECTION = ScraperStore.Show.NAME + "=?";
    private static final String ONLINEID_SELECTION = ScraperStore.Show.ONLINE_ID + "=?";
    private static final String NAME_ONLINEID_SELECTION = ScraperStore.Show.NAME + "=? AND " +
            ScraperStore.Show.ONLINE_ID + "=?";

    boolean showFound = false;
    boolean baseInfoChanged = false;
    boolean updateCover = false;
    boolean updateBackdrop = false;
    boolean postersChanged = false;
    boolean backdropsChanged = false;
    long showId = -1;
    String newCover, newBackdrop;

    @Override
    public long save(Context context, long videoId) {

        // prepare cover / backdrop info
        ScraperImage cover = getDefaultPoster();
        newCover = cover == null ? null : cover.getLargeFile();
        ScraperImage backdrop = getDefaultBackdrop();
        newBackdrop = backdrop == null ? null : backdrop.getLargeFile();
        String newBackdropUrl = backdrop == null ? null : backdrop.getLargeUrl();
        String finalTitle;

        ContentResolver cr = context.getContentResolver();

        // Check if this TV show is already referenced in the scraperDB

        String mTitleDate = "";
        boolean isPremieredYearAvailable = false;
        int mPremieredYear = getPremieredYear();
        if (mPremieredYear > 1) {
            mTitleDate = mTitle + " " + mPremieredYear;
            isPremieredYearAvailable = true;
        }
        log.debug("save: mTitleDate " + mTitleDate);

        log.debug("save: called for show mTitle=" + mTitle + " mId=" + mId +
                " mOnlineId=" + mOnlineId + " mTitleDate=" + mTitleDate +
                " mPremieredYear=" + mPremieredYear);

        // global logic to avoid creating already existing title conflicting with UNIQUE db requirement
        if (isShowNameOnlineIdAlreadyKnown(mTitle, mOnlineId, context)) {
            // update mTitle (UNIQUE)
            log.debug("save: mTitle&mOnlineId known: updating mTitle entry");
            showFound = true;
            finalTitle = mTitle;
        } else {
            if (isPremieredYearAvailable && isShowNameOnlineIdAlreadyKnown(mTitleDate, mOnlineId, context)) {
                // update mTitleDate (UNIQUE)
                log.debug("save: mTitle+mOnlineId not known but mTitleDate&mOnlineId known: updating mTitleDate entry");
                showFound = true;
                finalTitle = mTitleDate;
            } else {
                if (isShowNameAlreadyKnown(mTitle, context)) {
                    if (isPremieredYearAvailable) {
                        if (isShowNameAlreadyKnown(mTitleDate, context)) {
                            // update mTitleDate (UNIQUE)
                            log.debug("save: (mTitle|mTitleDate)&mOnlineId not known but mTitle&mTitleDate known: updating mTitleDate entry");
                            showFound = true;
                            finalTitle = mTitleDate;
                        } else {
                            // create mTitleDate (UNIQUE)
                            log.debug("save: (mTitle|mTitleDate)&mOnlineId not known and mTitle known & mTitleDate not known: creating mTitleDate entry");
                            showFound = false;
                            finalTitle = mTitleDate;
                        }
                    } else {
                        log.debug("save: (mTitle|mTitleDate)&mOnlineId not known and mTitle known and premieredYear not available: updating mTitle entry");
                        // update mTitle (UNIQUE) --> can raise an issue if 2 shows with different onlineID and no premiered date
                        showFound = true;
                        finalTitle = mTitle;
                    }
                } else {
                    // create mTitle (UNIQUE)
                    log.debug("save: (mTitle|mTitleDate)&mOnlineId not known and mTitle not known: creating mTitle entry");
                    showFound = false;
                    finalTitle = mTitle;
                }
            }
        }

        // NAME_SELECTION new String[] { mTitle }
        // ONLINEID_SELECTION new String[] { String.valueOf(mOnlineId) }
        // NAME_ONLINEID_SELECTION new String[] { mTitle, String.valueOf(mOnlineId) }

        // only update info based on onlineId since it is the source of truth (not the name)
        updateInfo(ONLINEID_SELECTION, new String[] { String.valueOf(mOnlineId)  }, cr);

        if (!showFound || baseInfoChanged) {
            log.debug("save: show not found in db or baseInfo changed");
            // got to insert or update the baseinfo.
            ContentValues values = new ContentValues();
            values.put(ScraperStore.Show.NAME, finalTitle);
            values.put(ScraperStore.Show.ONLINE_ID, Long.valueOf(mOnlineId));
            values.put(ScraperStore.Show.IMDB_ID, mImdbId);
            values.put(ScraperStore.Show.CONTENT_RATING, mContentRating);
            values.put(ScraperStore.Show.PREMIERED, mPremiered == null ? null : Long.valueOf(mPremiered.getTime()));
            values.put(ScraperStore.Show.RATING, Float.valueOf(mRating));
            values.put(ScraperStore.Show.PLOT, mPlot);

            values.put(ScraperStore.Show.ACTORS_FORMATTED, getActorsFormatted());
            values.put(ScraperStore.Show.DIRECTORS_FORMATTED, getDirectorsFormatted());
            values.put(ScraperStore.Show.WRITERS_FORMATTED, getWritersFormatted());
            values.put(ScraperStore.Show.GERNES_FORMATTED, getGenresFormatted());
            values.put(ScraperStore.Show.STUDIOS_FORMATTED, getStudiosFormatted());

            if (!showFound || updateCover) {
                values.put(ScraperStore.Show.COVER, newCover);
            }
            if (!showFound || updateBackdrop) {
                values.put(ScraperStore.Show.BACKDROP, newBackdrop);
                values.put(ScraperStore.Show.BACKDROP_URL, newBackdropUrl);
            }
            if (showFound) {
                // update if it is already there
                log.debug("Updating show base info: mTitle " + mTitle + " -> finalTitle " + finalTitle + " showId " + showId);
                Uri uri = ContentUris.withAppendedId(ScraperStore.Show.URI.ID, showId);
                int update = cr.update(uri, values, null, null);
                if (update != 1) {
                    log.error("update Show id " + showId + " failed");
                    return -1;
                }
            } else {
                // insert if not in db otherwise crash with UNIQUE name constraint
                log.debug("Inserting new show: mTitle " + mTitle + " -> finalTitle " + finalTitle);
                Uri uri = ScraperStore.Show.URI.BASE;
                Uri inserted = cr.insert(uri, values);
                long result = inserted == null ? -1 : ContentUris.parseId(inserted);
                if (result == -1) {
                    log.error("insert Show failed");
                    return -1;
                }
                showId = result;
            }
        }

        String showIdString = String.valueOf(showId);
        Builder cop = null;
        ArrayList<ContentProviderOperation> allOperations = new ArrayList<ContentProviderOperation>();

        // if show did not exist insert all the actors etc, updates here are not done.
        if (!showFound) {
            // We know our ID now so we can put everything into a single transaction
            log.debug("Inserting studios, directors, writers, actors, genres.");

            for (String studio : mStudios) {
                cop = ContentProviderOperation.newInsert(ScraperStore.Studio.URI.SHOW);
                cop.withValue(ScraperStore.Show.Studio.NAME, studio);
                cop.withValue(ScraperStore.Show.Studio.SHOW, showIdString);
                allOperations.add(cop.build());
            }

            for (String director : mDirectors) {
                cop = ContentProviderOperation.newInsert(ScraperStore.Director.URI.SHOW);
                cop.withValue(ScraperStore.Show.Director.NAME, director);
                cop.withValue(ScraperStore.Show.Director.SHOW, showIdString);
                allOperations.add(cop.build());
            }

            for (String writer : mWriters) {
                cop = ContentProviderOperation.newInsert(ScraperStore.Writer.URI.SHOW);
                cop.withValue(ScraperStore.Show.Writer.NAME, writer);
                cop.withValue(ScraperStore.Show.Writer.SHOW, showIdString);
                allOperations.add(cop.build());
            }

            for (String actorName : mActors.keySet()) {
                cop = ContentProviderOperation.newInsert(ScraperStore.Actor.URI.SHOW);
                cop.withValue(ScraperStore.Show.Actor.NAME, actorName);
                cop.withValue(ScraperStore.Show.Actor.SHOW, showIdString);
                cop.withValue(ScraperStore.Show.Actor.ROLE, mActors.get(actorName));
                allOperations.add(cop.build());
            }

            for (String genre : mGenres) {
                cop = ContentProviderOperation.newInsert(ScraperStore.Genre.URI.SHOW);
                cop.withValue(ScraperStore.Show.Genre.NAME, genre);
                cop.withValue(ScraperStore.Show.Genre.SHOW, showIdString);
                allOperations.add(cop.build());
            }
        }
        // backreferences to first poster / backdrop insert operation
        int posterId = -1;
        int backdropId = -1;

        // if new show or posters changed
        if (!showFound || postersChanged) {
            log.debug("Inserting posters.");
            for (ScraperImage image : safeList(mPosters)) {
                if (posterId == -1)
                    posterId = allOperations.size();
                // insert ignores posters that are already present
                allOperations.add(image.getSaveOperation(showId));
            }
        }
        // if new show or backdrops changed
        if (!showFound || backdropsChanged) {
            log.debug("Inserting backdrops.");
            for (ScraperImage image : safeList(mBackdrops)) {
                if (backdropId == -1)
                    backdropId = allOperations.size();
                // insert ignores backdrops that are already present
                allOperations.add(image.getSaveOperation(showId));
            }
        }

        ContentValues backRef = null;
        if (posterId != -1) {
            backRef = new ContentValues();
            backRef.put(ScraperStore.Show.POSTER_ID, Integer.valueOf(posterId));
        }
        if (backdropId != -1) {
            if (backRef == null) backRef = new ContentValues();
            backRef.put(ScraperStore.Show.BACKDROP_ID, Integer.valueOf(backdropId));
        }

        // set a default poster id if there was none
        if (!showFound && backRef != null) {
            log.debug("Updating poster/backdrop id.");
            allOperations.add(
                    ContentProviderOperation.newUpdate(ScraperStore.Show.URI.BASE)
                    .withValueBackReferences(backRef)
                    .withSelection(ScraperStore.Show.ID + "=?", new String[] { showIdString })
                    .build()
                    );
        }

        // finally push stuff to database.
        if (!allOperations.isEmpty()) {
            log.debug("Performing " + allOperations.size() + " db operations.");
            try {
                cr.applyBatch(ScraperStore.AUTHORITY, allOperations);
            } catch (RemoteException e) {
                log.error("Exception :" + e, e);
            } catch (OperationApplicationException e) {
                log.error("Exception :" + e, e);
            }
        } else
            log.debug("Nothing to be done for this show.");

        return showId;
    }

    private static final boolean newStringIsBetter(String oldString, String newString) {
        // newString == null or empty can't be better
        if (newString == null || newString.isEmpty())
            return false;
        // newString not empty now. It's obviously better then any empty old string
        if (oldString == null || oldString.isEmpty())
            return true;
        // now both have values. same = not better
        if (oldString.equals(newString))
            return false;
        // different strings -> new is always better
        return true;
    }
    private static final boolean newStringIsNotEmpty(String oldString, String newString) {
        // newString == null or empty can't be better
        if (newString == null || newString.isEmpty())
            return false;
        // newString not empty now. It's obviously better then any empty old string
        if (oldString == null || oldString.isEmpty())
            return true;
        // anything else would be a change in values so it's not better
        return false;
    }
    private static final boolean newFloatIsBetter(float oldValue, float newValue) {
        // same as the String thing
        return newValue > 0f && (oldValue <= 0f || oldValue != newValue);
    }
    private static final boolean newLongIsBetter(long oldValue, long newValue) {
        // same as the String thing
        return newValue > 0 && (oldValue <= 0 || oldValue != newValue);
    }
    public void setPremiered(Date prem) { mPremiered = prem; }
    public void setPremiered(long prem) { mPremiered = new Date(prem); }

    @Override
    public String toString() {
        return super.toString() + " / PREMIERED=" + mPremiered;
    }

    private void readFromParcel(Parcel in) {
        mPremiered = readDate(in.readLong());
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        super.writeToParcel(out, flags);
        out.writeLong(nonNull(mPremiered));
    }

    public void setPremiered(String string) {
        if (TextUtils.isEmpty(string)) {
            mPremiered = new Date(0);
            return;
        }
        try {
            mPremiered = sDateFormatter.parse(string);
        } catch (ParseException e) {
            log.error("Illegal Date format [" + string + "]");
            mPremiered = new Date(0);
        }
    }

    @Override
    public List<ScraperImage> getAllPostersInDb(Context context) {
        return getAllPostersInDb(context, -1, false);
    }

    @Override
    public List<ScraperTrailer> getAllTrailersInDb(Context context) {
        return new ArrayList<>();
    }

    private static final String SELECTION_SEASON =
            ScraperStore.ShowPosters.SHOW_ID + "=? AND (" +
            ScraperStore.ShowPosters.SEASON + "=? OR " +
            ScraperStore.ShowPosters.SEASON + "= -1)";
    private static final String SELECTION_NO_SEASON =
            ScraperStore.ShowPosters.SHOW_ID + "=? AND " +
            ScraperStore.ShowPosters.SEASON + "= -1";
    private static final String SELECTION_ALL =
            ScraperStore.ShowPosters.SHOW_ID + "=?";
    // larger (= with real season number) first, then by id
    private static final String ORDER_SEASON = ScraperStore.ShowPosters.SEASON + " DESC," + ScraperStore.ShowPosters.ID;
    // simply ordered by season
    private static final String ORDER_ALL = ScraperStore.ShowPosters.SEASON + "," + ScraperStore.ShowPosters.ID;
    /**
     * Returns all posters in the db for this show with a certain season number.<br>
     * If season = -1 then only posters without a season are returned, otherwise both
     * posters that match season (sorted first) and posters that have no season are returned.<br>
     * season -1 also means that images are type SHOW_POSTER instead of EPISODE_POSTER
     * if allSeasons is true, the result contains every poster, season posters first
     */
    public List<ScraperImage> getAllPostersInDb(Context context, int season, boolean allSeasons) {
        ContentResolver cr = context.getContentResolver();
        Uri uri = ScraperStore.ShowPosters.URI.BASE;
        String selection;
        String[] selectionArgs;
        String order;
        ScraperImage.Type type;
        ScraperImage.Type typeNoSeason = null;
        if (allSeasons) {
            selection = SELECTION_ALL;
            selectionArgs = new String[] {
                    String.valueOf(mId),
            };
            order = ORDER_ALL;
            type = ScraperImage.Type.EPISODE_POSTER;
            typeNoSeason = ScraperImage.Type.SHOW_POSTER;
        } else
        if (season < 0) {
            selection = SELECTION_NO_SEASON;
            selectionArgs = new String[] {
                    String.valueOf(mId),
            };
            order = null;
            type = ScraperImage.Type.SHOW_POSTER;
        } else {
            selection = SELECTION_SEASON;
            selectionArgs = new String[] {
                    String.valueOf(mId),
                    String.valueOf(season)
            };
            order = ORDER_SEASON;
            type = ScraperImage.Type.EPISODE_POSTER;
        }
        Cursor cursor = cr.query(uri, null, selection, selectionArgs, order);
        List<ScraperImage> result = null;
        if (cursor != null) {
            result = new ArrayList<ScraperImage>(cursor.getCount());
            while (cursor.moveToNext()) {
                result.add(ScraperImage.fromCursor(cursor, type, typeNoSeason));
            }
            cursor.close();
        }
        return result;
    }

    @Override
    public List<ScraperImage> getAllBackdropsInDb(Context context) {
        ContentResolver cr = context.getContentResolver();
        Uri uri = ContentUris.withAppendedId(ScraperStore.ShowBackdrops.URI.BY_SHOW_ID, mId);
        Cursor cursor = cr.query(uri, null, null, null, null);
        List<ScraperImage> result = null;
        if (cursor != null) {
            result = new ArrayList<ScraperImage>(cursor.getCount());
            while (cursor.moveToNext()) {
                result.add(ScraperImage.fromCursor(cursor, Type.SHOW_BACKDROP));
            }
            cursor.close();
        }
        return result;
    }

    private static int storedPosterCount(long showId, ContentResolver cr) {
        return getCount(cr, ScraperStore.ShowPosters.URI.BY_SHOW_ID, showId);
    }

    private static int storedBackdropCount(long showId, ContentResolver cr) {
        return getCount(cr, ScraperStore.ShowBackdrops.URI.BY_SHOW_ID, showId);
    }

    private static final String[] PROJECTION_COUNT = { "count(*)" };
    private static int getCount(ContentResolver cr, Uri baseUri, long appendedId) {
        Uri uri = ContentUris.withAppendedId(baseUri, appendedId);
        int result = 0;
        Cursor cursor = cr.query(uri, PROJECTION_COUNT, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                result = cursor.getInt(0);
            }
            cursor.close();
        }
        return result;
    }

    /** Add this (local) image as the default show poster */
    public void addDefaultPoster(Context context, Uri localImage) {
        ScraperImage image = new ScraperImage(ScraperImage.Type.SHOW_POSTER, mTitle);
        String imageUrl = localImage.toString();
        image.setLargeUrl(imageUrl);
        image.setThumbUrl(imageUrl);
        image.generateFileNames(context);
        addDefaultPoster(image);
    }

    /** Add this (local) image as the default show backdrop */
    public void addDefaultBackdrop(Context context, Uri localImage) {
        ScraperImage image = new ScraperImage(ScraperImage.Type.SHOW_BACKDROP, mTitle);
        String imageUrl = localImage.toString();
        image.setLargeUrl(imageUrl);
        image.setThumbUrl(imageUrl);
        image.generateFileNames(context);
        addDefaultBackdrop(image);
    }

    /** Add this url as the default show poster */
    public void addDefaultPosterTMDB(Context context, String path) {
        ScraperImage image = new ScraperImage(ScraperImage.Type.SHOW_POSTER, mTitle);
        image.setLargeUrl(ScraperImage.TMPL + path);
        image.setThumbUrl(ScraperImage.TMPT + path);
        image.generateFileNames(context);
        addDefaultPoster(image);
    }

    /** Add this url image as the default show backdrop */
    public void addDefaultBackdropTMDB(Context context, String path) {
        ScraperImage image = new ScraperImage(ScraperImage.Type.SHOW_BACKDROP, mTitle);
        image.setLargeUrl(ScraperImage.TMBL + path);
        image.setThumbUrl(ScraperImage.TMBT + path);
        image.generateFileNames(context);
        addDefaultBackdrop(image);
    }

    private boolean isKnown(String [] projection, String selection, String [] selectionArgs, Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor = contentResolver.query(
                ScraperStore.Show.URI.ALL,
                projection, selection, selectionArgs, null);
        boolean isKnown = false;
        if (cursor != null) {
            isKnown = cursor.moveToFirst();
            cursor.close();
        }
        return isKnown;
    }

    private boolean isShowNameAlreadyKnown(String showName, Context context) {
        boolean isKnown = isKnown(new String[] {ScraperStore.Show.ID}, NAME_SELECTION, new String[] { showName }, context);
        log.debug("isShowNameAlreadyKnown: " + showName + " " + isKnown);
        return isKnown;
    }

    private boolean isShowOnlineIdAlreadyKnown(long onlineId, Context context) {
        boolean isKnown = isKnown(new String[] {ScraperStore.Show.ID}, ONLINEID_SELECTION, new String[] { String.valueOf(onlineId) }, context);
        log.debug("isShowIdAlreadyKnown: " + onlineId + " " + isKnown);
        return isKnown;
    }

    private boolean isShowNameOnlineIdAlreadyKnown(String showName, long onlineId, Context context) {
        boolean isKnown = isKnown(new String[] {ScraperStore.Show.ID, ScraperStore.Show.ONLINE_ID}, NAME_ONLINEID_SELECTION, new String[] { showName, String.valueOf(onlineId) }, context);
        log.debug("isShowNameAlreadyKnown: " + showName + " " + isKnown);
        return isKnown;
    }

    public int getPremieredYear() {
        Calendar cal = Calendar.getInstance();
        cal.setTime(mPremiered);
        return cal.get(Calendar.YEAR);
    }

    private void updateInfo(String selection, String[] selectionArgs, ContentResolver contentResolver) {

        log.debug("updateInfo: " + selection + ", selectionArgs " + selectionArgs);
        Cursor cursor = contentResolver.query(ScraperStore.Show.URI.ALL, BASE_PROJECTION,
                selection, selectionArgs, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                log.debug("save: show found in db " + DatabaseUtils.dumpCursorToString(cursor));
                showFound = true;
                // The show was found in the database -> get stored infos
                showId = cursor.getLong(0);
                String storedCover = cursor.getString(1);
                float storedRating = cursor.getFloat(2);
                String storedCRating = cursor.getString(3);
                String storedBD = cursor.getString(4);
                String storedImdb = cursor.getString(5);
                long storedOnlineId = cursor.getLong(6);

                updateCover = newStringIsNotEmpty(storedCover, newCover);
                updateBackdrop = newStringIsNotEmpty(storedBD, newBackdrop);

                log.debug("updateInfo: show found in db: storedCover " + storedCover + ", newCover " + newCover);
                log.debug("updateInfo: show found in db: storedBD " + storedBD + ", newBackdrop " + newBackdrop);
                log.debug("updateInfo: show found in db: storedRating " + storedRating + ", mRating " + mRating);
                log.debug("updateInfo: show found in db: storedCRating " + storedCRating + ", mContentRating " + mContentRating);
                log.debug("updateInfo: show found in db: storedImdb " + storedImdb + ", mImdbId " + mImdbId);
                log.debug("updateInfo: show found in db: storedOnlineId " + storedOnlineId + ", mOnlineId " + mOnlineId);
                // compare old vs new
                baseInfoChanged =
                        updateCover || updateBackdrop ||
                                newFloatIsBetter(storedRating, mRating) ||
                                newStringIsBetter(storedCRating, mContentRating) ||
                                newStringIsBetter(storedImdb, mImdbId) ||
                                newLongIsBetter(storedOnlineId, mOnlineId);

                log.debug("updateInfo: show found in db: updateCover " + updateCover + ", updateBackdrop " + updateBackdrop + " baseInfoChanged " + baseInfoChanged);

                // since show exists check other data for changes too
                int storedPosterCount = storedPosterCount(showId, contentResolver);
                int newPosterCount = mPosters == null ? 0 : mPosters.size();
                postersChanged = storedPosterCount != newPosterCount;

                int storedBackdropCount = storedBackdropCount(showId, contentResolver);
                int newBackdropCount = mBackdrops == null ? 0 : mBackdrops.size();
                backdropsChanged = storedBackdropCount != newBackdropCount;
            }
            cursor.close();
        }
    }
}
