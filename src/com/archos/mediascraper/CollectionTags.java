// Copyright 202 Courville Software
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
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.Log;

import com.archos.mediaprovider.video.ScraperStore;
import com.archos.mediascraper.themoviedb3.ImageConfiguration;

import java.io.File;
import java.util.ArrayList;

public class CollectionTags implements Parcelable {
    private static final String TAG = "CollectionTags";
    private final static boolean DBG = true;

    protected long mId;
    protected String mTitle;
    protected String mPlot;
    protected String mPosterPath = null;
    protected String mPosterLargeUrl = null;
    protected String mPosterLargeFile = null;
    protected String mPosterThumbUrl = null;
    protected String mPosterThumbFile = null;
    protected String mBackdropPath = null;
    protected String mBackdropLargeUrl = null;
    protected String mBackdropLargeFile = null;
    protected String mBackdropThumbUrl = null;
    protected String mBackdropThumbFile = null;
    protected ScraperImage mPoster, mBackdrop;

    public CollectionTags() {
    }

    public CollectionTags(Parcel in) {
        this();
        readFromParcel(in);
    }

    public static final Creator<CollectionTags> CREATOR = new Creator<CollectionTags>() {
        @Override
        public CollectionTags createFromParcel(Parcel in) {
            return new CollectionTags(in);
        }

        @Override
        public CollectionTags[] newArray(int size) {
            return new CollectionTags[size];
        }
    };

    public int describeContents() {
        return 0;
    }

    public long getId() { return mId; }
    public String getPlot() { return mPlot; }
    public String getTitle() { return mTitle; }
    public ScraperImage getPosterImage() { return mPoster; }
    public ScraperImage getBackdropImage() { return mBackdrop; }
    public void setId(long id) { mId = id; }
    public void setPlot(String plot) { mPlot = plot; }
    public void setTitle(String title) { mTitle = title; }
    public void setPoster(ScraperImage scraperImage) { mPoster = scraperImage; };
    public void setBackdrop(ScraperImage scraperImage) { mBackdrop = scraperImage; }
    public void setPosterLargeUrl(String posterLargeUrl) { mPosterLargeUrl = posterLargeUrl; }
    public void setPosterPath(String posterPath) { mPosterPath = posterPath; }
    public String getPosterPath() { return mPosterPath; }
    public String getPosterLargeUrl() { return mPosterLargeUrl; }
    public void setPosterLargeFile(String posterLargeFile) { mPosterLargeFile = posterLargeFile; }
    public String getPosterLargeFile() { return mPosterLargeFile; }
    public void setPosterThumbUrl(String posterThumbUrl) { mPosterThumbUrl = posterThumbUrl; }
    public String getPosterThumbUrl() { return mPosterThumbUrl; }
    public void setPosterThumbFile(String posterThumbFile) { mPosterThumbFile = posterThumbFile; }
    public String getPosterThumbFile() { return mPosterThumbFile; }
    public void setBackdropPath(String backdropPath) { mBackdropPath = backdropPath; }
    public String getBackdropPath() { return mBackdropPath; }
    public void setBackdropLargeUrl(String backdropLargeUrl) { mBackdropLargeUrl = backdropLargeUrl; }
    public String getBackdropLargeUrl() { return mBackdropLargeUrl; }
    public void setBackdropLargeFile(String backdropLargeFile) { mBackdropLargeFile = backdropLargeFile; }
    public String getBackdropLargeFile() { return mBackdropLargeFile; }
    public void setBackdropThumbUrl(String backdropThumbUrl) { mBackdropThumbUrl = backdropThumbUrl; }
    public String getBackdropThumbUrl() { return mBackdropThumbUrl; }
    public void setBackdropThumbFile(String backdropThumbFile) { mBackdropThumbFile = backdropThumbFile; }
    public String getBackdropThumbFile() { return mBackdropThumbFile; }

    public File getCover() {
        ScraperImage image = mPoster;
        if (image != null)
            return image.getLargeFileF();
        return null;
    }

    public File getBackdrop() {
        ScraperImage image = mBackdrop;
        if (image != null)
            return image.getLargeFileF();
        return null;
    }

    public void downloadPoster(Context context) {
        ScraperImage image = mPoster;
        if (image != null)
            image.download(context);
        else
            if (DBG) Log.d(TAG, "downloadPoster: image is null for " + mTitle + ", url " + image.getLargeUrl());
    }

    public void downloadBackdrop(Context context) {
        ScraperImage image = mBackdrop;
        if (image != null)
            image.download(context);
        else
            if (DBG) Log.d(TAG, "downloadBackdrop: image is null for " + mTitle + ", url " + image.getLargeUrl());
    }

    public final void downloadAllImages(Context context) {
        downloadPoster(context);
        downloadBackdrop(context);
    }

    private static final String[] PROJECTION_ID = new String[] { ScraperStore.MovieCollections.ID };
    private static final String WHERE_ID = ScraperStore.MovieCollections.ID + "=?";

    /**
     * Saves the information. Blocking!
     * First queries the db to find the ID, don't use this if you can avoid it.
     * @param context Context to be used
     * @return id of item in the database
     */
    public final long save(Context context, boolean forceUpdate) {

        if (DBG) Log.d(TAG, "save: collection " + mId +
                ", title " + mTitle +
                ", forceUpdate " + forceUpdate);

        ContentResolver cr = context.getContentResolver();
        ContentProviderOperation.Builder cop = null;
        if (forceUpdate) { // we know that the entry exists, this is an update
            cop = ContentProviderOperation.newUpdate(ScraperStore.MovieCollections.URI.BASE).withSelection(WHERE_ID, new String[]{String.valueOf(mId)});
        } else { // let's see if this is an update or new entry
            Cursor c = cr.query(ScraperStore.MovieCollections.URI.BASE, PROJECTION_ID, WHERE_ID,
                    new String[]{String.valueOf(mId)}, null);
            if (c != null) { // entry exists this is an update
                cop = ContentProviderOperation.newUpdate(ScraperStore.MovieCollections.URI.BASE).withSelection(WHERE_ID, new String[]{String.valueOf(mId)});
                c.close();
            } else { // entry does not exist this is an insert
                cop = ContentProviderOperation.newInsert(ScraperStore.MovieCollections.URI.BASE);
                cop.withValue(ScraperStore.MovieCollections.ID, mId);
            }
        }

        cop.withValue(ScraperStore.MovieCollections.NAME, mTitle);
        cop.withValue(ScraperStore.MovieCollections.DESCRIPTION, mPlot);
        cop.withValue(ScraperStore.MovieCollections.POSTER_LARGE_URL, mPosterLargeUrl);
        cop.withValue(ScraperStore.MovieCollections.POSTER_LARGE_FILE, mPosterLargeFile);
        cop.withValue(ScraperStore.MovieCollections.POSTER_THUMB_URL, mPosterThumbUrl);
        cop.withValue(ScraperStore.MovieCollections.POSTER_THUMB_FILE, mPosterThumbFile);
        cop.withValue(ScraperStore.MovieCollections.BACKDROP_LARGE_URL, mBackdropLargeUrl);
        cop.withValue(ScraperStore.MovieCollections.BACKDROP_LARGE_FILE, mBackdropLargeFile);
        cop.withValue(ScraperStore.MovieCollections.BACKDROP_THUMB_URL, mBackdropThumbUrl);
        cop.withValue(ScraperStore.MovieCollections.BACKDROP_THUMB_FILE, mBackdropThumbFile);
        ArrayList<ContentProviderOperation> allOperations = new ArrayList<>();
        allOperations.add(cop.build());

        long result = -1;
        try {
            ContentProviderResult[] results = cr.applyBatch(ScraperStore.AUTHORITY, allOperations);
            result = (results != null && results.length > 0) ? mId : -1;
        } catch (RemoteException e) {
            Log.d(TAG, "Exception :" + e, e);
        } catch (OperationApplicationException e) {
            Log.d(TAG, "Exception :" + e, e);
        }
        return result;
    }

    @Override
    public String toString() {
        return " TITLE=" + mTitle + " / PLOT=" + mPlot + " / COVER=" + getCover();
    }

    private void readFromParcel(Parcel in) {
        mId = in.readLong();
        mTitle = in.readString();
        mPlot = in.readString();
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeLong(mId);
        out.writeString(nonNull(mTitle));
        out.writeString(nonNull(mPlot));
    }

    protected final static String nonNull(String source) {
        return (source == null ? "" : source);
    }

    // generates the various posters/backdrops based on URL
    public void downloadImage(Context context) {
        downloadCollectionImage(ImageConfiguration.PosterSize.W342,     // large poster
                ImageConfiguration.PosterSize.W92,                      // thumb poster
                ImageConfiguration.BackdropSize.W1280,                  // large bd
                ImageConfiguration.BackdropSize.W300,                   // thumb bd
                mTitle, context);
    }

    public void downloadCollectionImage(ImageConfiguration.PosterSize posterFullSize,
                                               ImageConfiguration.PosterSize posterThumbSize,
                                               ImageConfiguration.BackdropSize backdropFullSize,
                                               ImageConfiguration.BackdropSize backdropThumbSize,
                                               String nameSeed, Context context) {
        if (getId() != -1) {
            String path = getPosterPath();
            if (DBG) Log.d(TAG, "downloadCollectionImage: treating collection poster " + path);
            String fullUrl, thumbUrl;
            ScraperImage image;
            if (path != null) {
                fullUrl = ImageConfiguration.getUrl(path, posterFullSize);
                thumbUrl = ImageConfiguration.getUrl(path, posterThumbSize);
                image = new ScraperImage(ScraperImage.Type.COLLECTION_POSTER, nameSeed);
                image.setLargeUrl(fullUrl);
                image.setThumbUrl(thumbUrl);
                image.generateFileNames(context);
                image.download(context);
                setPosterLargeFile(image.getLargeFile());
                setPosterLargeUrl(fullUrl);
                setPosterThumbFile(image.getThumbFile());
                setPosterThumbUrl(thumbUrl);
            }

            path = getBackdropPath();
            if (DBG) Log.d(TAG, "downloadCollectionImage: treating collection backdrop " + path);
            if (path != null) {
                fullUrl = ImageConfiguration.getUrl(path, backdropFullSize);
                thumbUrl = ImageConfiguration.getUrl(path, backdropThumbSize);
                image = new ScraperImage(ScraperImage.Type.COLLECTION_BACKDROP, nameSeed);
                image.setLargeUrl(fullUrl);
                image.setThumbUrl(thumbUrl);
                image.generateFileNames(context);
                image.download(context);
                setBackdropLargeFile(image.getLargeFile());
                setBackdropLargeUrl(fullUrl);
                setBackdropThumbFile(image.getThumbFile());
                setBackdropThumbUrl(thumbUrl);
            }
        }
    }

}
