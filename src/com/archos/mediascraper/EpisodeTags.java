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
import android.util.Log;

import com.archos.filecorelibrary.MetaFile;
import com.archos.mediaprovider.video.ScraperStore;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.ScraperImage.Type;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class EpisodeTags extends BaseTags {
    private final static String TAG = "EpisodeTags";
    private ShowTags mShowTags;
    private String mShowTitle;
    private long mShowId;
    private int mSeason;
    private int mEpisode;
    private Date mAired;
    private final static SimpleDateFormat sDateFormatter = new SimpleDateFormat("yyyy-MM-dd");
    private final static Date DEFAULT_DATE = new Date(0);

    @SuppressWarnings("hiding") // this has to be defined for every parcelable this way
    public static final Parcelable.Creator<EpisodeTags> CREATOR = new Parcelable.Creator<EpisodeTags>() {

        public EpisodeTags createFromParcel(Parcel in) {
            return new EpisodeTags(in);
        }

        public EpisodeTags[] newArray(int size) {
            return new EpisodeTags[size];
        }
    };
    private ScraperImage mEpisodePicture;

    public EpisodeTags(Parcel in) {
        super(in);
        readFromParcel(in);
    }

    public EpisodeTags() {
        super();
    }

    public Date getAired() { return mAired; }
    public int getEpisode() { return mEpisode; }
    public int getSeason() { return mSeason; }
    public long getShowId() { return mShowId; }
    public ShowTags getShowTags() { return mShowTags; }
    public String getShowTitle() {
        if (mShowTags != null)
            return mShowTags.getTitle();
        return mShowTitle;
    }

    @Override
    public ScraperImage getDefaultBackdrop() {
        return mShowTags == null ? null :
            mShowTags.getDefaultBackdrop();
    }
    @Override
    public List<ScraperImage> getBackdrops() {
        return mShowTags == null ? null :
            mShowTags.getBackdrops();
    }

    @Override
    public String getStorageName() {
        return mTitle + "S" + mSeason + "E" + mEpisode;
    }

    private static final String[] ID_PROJECTION = {
        BaseColumns._ID
    };
    private static final String WHERE_LARGEFILE =
            ScraperStore.ShowPosters.LARGE_FILE + "=?";
    @Override
    public long save(Context context, long videoId) {
        // save the video id in this tag
        setVideoId(videoId);

        ContentResolver cr = context.getContentResolver();

        //---------------------------------------------------
        // Save or update the data for the TV show if needed
        //---------------------------------------------------
        long showId = mShowTags.save(context, videoId);

        //---------------------------------------------------------------------------------------
        // Create a new entry for this episode in the database.
        //---------------------------------------------------------------------------------------
        ContentValues values = new ContentValues();
        values.put(ScraperStore.Episode.VIDEO_ID, Long.valueOf(videoId));
        values.put(ScraperStore.Episode.NAME, mTitle);
        if(mAired != null) {
            values.put(ScraperStore.Episode.AIRED, Long.valueOf(mAired.getTime()));
        }
        values.put(ScraperStore.Episode.RATING, Float.valueOf(mRating));
        values.put(ScraperStore.Episode.PLOT, mPlot);
        values.put(ScraperStore.Episode.SEASON, Integer.valueOf(mSeason));
        values.put(ScraperStore.Episode.NUMBER, Integer.valueOf(mEpisode));
        values.put(ScraperStore.Episode.SHOW, Long.valueOf(showId));
        values.put(ScraperStore.Episode.IMDB_ID, mImdbId);
        values.put(ScraperStore.Episode.ONLINE_ID, Long.valueOf(mOnlineId));

        values.put(ScraperStore.Episode.ACTORS_FORMATTED, getActorsFormatted());
        values.put(ScraperStore.Episode.DIRECTORS_FORMATTED, getDirectorsFormatted());

        File cover = getCover();
        String coverPath = (cover != null) ? cover.getPath() : null;
        if (coverPath != null && !coverPath.isEmpty())
            values.put(ScraperStore.Episode.COVER, coverPath);
        ScraperImage pic = getEpisodePicture();
        if(pic!=null && pic.getLargeFile()!=null)
            values.put(ScraperStore.Episode.PICTURE, pic.getLargeFile());

        // need to find the poster id in the database
        ScraperImage poster = getDefaultPoster();
        long posterId = -1;

        if (poster != null) {
            // get or save this poster
            posterId = poster.save(context, showId);
        }
        if (posterId != -1) {
            values.put(ScraperStore.Episode.POSTER_ID, posterId);
        }

        // build list of operations
        Builder cop = null;
        ArrayList<ContentProviderOperation> allOperations = new ArrayList<ContentProviderOperation>();

        // first insert the episode base info - item 0 for backreferences
        cop = ContentProviderOperation.newInsert(ScraperStore.Episode.URI.BASE);
        cop.withValues(values);
        allOperations.add(cop.build());

        // then directors etc
        for(String director: mDirectors) {
            cop = ContentProviderOperation.newInsert(ScraperStore.Director.URI.EPISODE);
            cop.withValue(ScraperStore.Episode.Director.NAME, director);
            cop.withValueBackReference(ScraperStore.Episode.Director.EPISODE, 0);
            allOperations.add(cop.build());
        }

        for(String actorName: mActors.keySet()) {
            cop = ContentProviderOperation.newInsert(ScraperStore.Actor.URI.EPISODE);
            cop.withValue(ScraperStore.Episode.Actor.NAME, actorName);
            cop.withValueBackReference(ScraperStore.Episode.Actor.EPISODE, 0);
            cop.withValue(ScraperStore.Episode.Actor.ROLE, mActors.get(actorName));
            allOperations.add(cop.build());
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

        long returnValue = -1;
        try {
            ContentProviderResult[] results = cr.applyBatch(ScraperStore.AUTHORITY, allOperations);
            if (results != null && results.length > 0) {
                returnValue = ContentUris.parseId(results[0].uri);
            }
        } catch (RemoteException e) {
            Log.d(TAG, "Exception :" + e, e);
        } catch (OperationApplicationException e) {
            Log.d(TAG, "Exception :" + e, e);
        }
        return returnValue;
    }

    public void addSaveOperation(ArrayList<ContentProviderOperation> list, Map<String, Long> poster2IdMap) {
        if (list == null) return;

        // create ContentValues for this episode
        ContentValues values = new ContentValues();
        values.put(ScraperStore.Episode.VIDEO_ID, Long.valueOf(mVideoId));
        values.put(ScraperStore.Episode.NAME, mTitle);
        if(mAired != null) {
            values.put(ScraperStore.Episode.AIRED, Long.valueOf(mAired.getTime()));
        }
        values.put(ScraperStore.Episode.RATING, Float.valueOf(mRating));
        values.put(ScraperStore.Episode.PLOT, mPlot);
        values.put(ScraperStore.Episode.SEASON, Integer.valueOf(mSeason));
        values.put(ScraperStore.Episode.NUMBER, Integer.valueOf(mEpisode));
        values.put(ScraperStore.Episode.SHOW, Long.valueOf(mShowId));
        values.put(ScraperStore.Episode.IMDB_ID, mImdbId);
        values.put(ScraperStore.Episode.ONLINE_ID, Long.valueOf(mOnlineId));

        values.put(ScraperStore.Episode.ACTORS_FORMATTED, getActorsFormatted());
        values.put(ScraperStore.Episode.DIRECTORS_FORMATTED, getDirectorsFormatted());

        ScraperImage pic = getEpisodePicture();
        if(pic!=null && pic.getLargeFile()!=null)
            values.put(ScraperStore.Episode.PICTURE, pic.getLargeFile());
        
        File cover = getCover();
        String coverPath = (cover != null) ? cover.getPath() : null;
        if (coverPath != null && !coverPath.isEmpty())
            values.put(ScraperStore.Episode.COVER, coverPath);

        // need to find the poster id in the database
        ScraperImage poster = getDefaultPoster();

        if (poster != null) {
            Long posterId = poster2IdMap.get(poster.getLargeFile());
            values.put(ScraperStore.Episode.POSTER_ID, posterId);
        }

        // build list of operations
        Builder cop = null;

        int firstIndex = list.size();
        // first insert the episode base info - item 0 for backreferences
        cop = ContentProviderOperation.newInsert(ScraperStore.Episode.URI.BASE);
        cop.withValues(values);
        list.add(cop.build());

        // then directors etc
        for(String director: mDirectors) {
            cop = ContentProviderOperation.newInsert(ScraperStore.Director.URI.EPISODE);
            cop.withValue(ScraperStore.Episode.Director.NAME, director);
            cop.withValueBackReference(ScraperStore.Episode.Director.EPISODE, firstIndex);
            list.add(cop.build());
        }

        for(String actorName: mActors.keySet()) {
            cop = ContentProviderOperation.newInsert(ScraperStore.Actor.URI.EPISODE);
            cop.withValue(ScraperStore.Episode.Actor.NAME, actorName);
            cop.withValueBackReference(ScraperStore.Episode.Actor.EPISODE, firstIndex);
            cop.withValue(ScraperStore.Episode.Actor.ROLE, mActors.get(actorName));
            list.add(cop.build());
        }
    }

    public void setAired(Date aired) { mAired = aired; }
    public void setAired(long aired) { mAired = new Date(aired); }
    public void setEpisode(int ep) { mEpisode = ep; }
    public void setSeason(int season) { mSeason = season; }
    public void setShowId(long show) { mShowId = show; }
    public void setShowTags(ShowTags show) { mShowTags = show; }
    /** Does NOT set the title in ShowTags, just locally */
    public void setShowTitle(String title) { mShowTitle = title; }

    @Override
    public String toString() {
        return super.toString() + " / SEASON=" + mSeason + " / EPISODE=" + mEpisode + " / AIRED=" +
            mAired + " / SHOW ID=" + mShowId + " / SHOW TAGS=" + mShowTags;
    }

    public void readFromParcel(Parcel in) {
        mShowTags = in.readParcelable(ShowTags.class.getClassLoader());
        mShowId = in.readLong();
        mSeason = in.readInt();
        mEpisode = in.readInt();
        mAired = readDate(in.readLong());
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        super.writeToParcel(out, flags);
        out.writeParcelable(mShowTags, flags);
        out.writeLong(mShowId);
        out.writeInt(mSeason);
        out.writeInt(mEpisode);
        out.writeLong(nonNull(mAired));
    }

    public void setAired(String string) {
        if (TextUtils.isEmpty(string)) {
            mAired = DEFAULT_DATE;
            return;
        }
        try {
            mAired = sDateFormatter.parse(string);
        } catch (ParseException e) {
            Log.d(TAG, "Illegal Date format [" + string + "]");
            mAired = DEFAULT_DATE;
        }
    }

    @Override
    public File downloadGetDefaultBackdropFile(Context context) {
        if (mShowTags != null)
            return mShowTags.downloadGetDefaultBackdropFile(context);
        return null;
    }

    @Override
    public void downloadPoster(Context context) {
        // downloads the episode cover
        super.downloadPoster(context);
        // also download the showtags cover
        if (mShowTags != null)
            mShowTags.downloadPoster(context);
    }

    @Override
    public final void downloadBackdrop(Context context) {
        // downloads the episode backdrop
        // (which does not exist but maybe in future)
        super.downloadBackdrop(context);
        // also download the showtags backdrop
        if (mShowTags != null)
            mShowTags.downloadBackdrop(context);
    }

    @Override
    public void setCover(File file) {
        if (file == null) return;
        if (getPosters() == null) {
            setPosters(ScraperImage.fromExistingCover(file.getPath(), Type.EPISODE_POSTER).asList());
        }
    }

    @Override
    public File getCover() {
        File cover = super.getCover();
        if (cover != null && cover.exists())
            return cover;

        // fallback to show cover
        if (mShowTags != null) {
            cover  = mShowTags.getCover();
            if (cover != null && cover.exists()) {
                return cover;
            }
        }

        return null;
    }

    @Override
    public File getBackdrop() {
        // backdrops are per show
        if (mShowTags != null) {
            File backdrop = mShowTags.getBackdrop();
            if (backdrop != null && backdrop.exists()) {
                return backdrop;
            }
        }
        return null;
    }

    @Override
    public List<ScraperImage> getAllPostersInDb(Context context) {
        if (mShowTags != null)
            return mShowTags.getAllPostersInDb(context, mSeason, false);
        return null;
    }

    @Override
    public List<ScraperTrailer> getAllTrailersInDb(Context context) {
        return new ArrayList<>();
    }

    @Override
    public List<ScraperImage> getAllBackdropsInDb(Context context) {
        if (mShowTags != null)
            return mShowTags.getAllBackdropsInDb(context);
        return null;
    }

    /** Add this (local) image as the default season poster */
    public void addDefaultPoster(Context context, Uri localImage, String showTitle) {
        ScraperImage image = new ScraperImage(ScraperImage.Type.EPISODE_POSTER, showTitle);
        String imageUrl = localImage.toString();
        image.setSeason(mSeason);
        image.setLargeUrl(imageUrl);
        image.setThumbUrl(imageUrl);
        image.generateFileNames(context);
        addDefaultPoster(image);
    }

    public ScraperImage getEpisodePicture() {
        return mEpisodePicture;
    }

    public void setEpisodePicture(String string, Context ct) {
        ScraperImage image = new ScraperImage(Type.EPISODE_POSTER, "");
        image.setLargeUrl("https://www.thetvdb.com/banners/" + string);
        image.setThumbUrl("https://www.thetvdb.com/banners/" + string);
        image.setLanguage("en");
        image.setSeason(-1);
        image.generateFileNames(ct);
        mEpisodePicture = image;
    }

    public void downloadPicture(Context mContext) {
        if(mEpisodePicture!=null)
            mEpisodePicture.download(mContext);
    }
}
