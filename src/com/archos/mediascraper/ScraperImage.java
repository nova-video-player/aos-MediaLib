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
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.BaseColumns;
import android.util.DisplayMetrics;
import android.util.Log;

import com.archos.mediaprovider.video.ScraperStore;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ScraperImage {

        private static final String TAG = "ScraperImage";
    private static final boolean DBG = false;
    private static final boolean DBG2 = false;

    public static int POSTER_WIDTH = 240;
    public static int POSTER_HEIGHT = 360;

    public String getLanguage() {
        return language;
    }

    public enum Type {
        MOVIE_BACKDROP(
                ScraperStore.MovieBackdrops.THUMB_URL, ScraperStore.MovieBackdrops.THUMB_FILE,
                ScraperStore.MovieBackdrops.LARGE_URL, ScraperStore.MovieBackdrops.LARGE_FILE,
                null, ScraperStore.MovieBackdrops.URI.BASE, ScraperStore.MovieBackdrops.MOVIE_ID,
                ImageScaler.Type.SCALE_OUTSIDE
                ),
        MOVIE_POSTER(
                ScraperStore.MoviePosters.THUMB_URL, ScraperStore.MoviePosters.THUMB_FILE,
                ScraperStore.MoviePosters.LARGE_URL, ScraperStore.MoviePosters.LARGE_FILE,
                null, ScraperStore.MoviePosters.URI.BASE, ScraperStore.MoviePosters.MOVIE_ID,
                ImageScaler.Type.SCALE_INSIDE
                ),
        SHOW_BACKDROP(
                ScraperStore.ShowBackdrops.THUMB_URL, ScraperStore.ShowBackdrops.THUMB_FILE,
                ScraperStore.ShowBackdrops.LARGE_URL, ScraperStore.ShowBackdrops.LARGE_FILE,
                null, ScraperStore.ShowBackdrops.URI.BASE, ScraperStore.ShowBackdrops.SHOW_ID,
                ImageScaler.Type.SCALE_OUTSIDE
                ),
        SHOW_POSTER(
                ScraperStore.ShowPosters.THUMB_URL, ScraperStore.ShowPosters.THUMB_FILE,
                ScraperStore.ShowPosters.LARGE_URL, ScraperStore.ShowPosters.LARGE_FILE,
                null, ScraperStore.ShowPosters.URI.BASE, ScraperStore.ShowPosters.SHOW_ID,
                ImageScaler.Type.SCALE_INSIDE
                ),
        EPISODE_POSTER(
                ScraperStore.ShowPosters.THUMB_URL, ScraperStore.ShowPosters.THUMB_FILE,
                ScraperStore.ShowPosters.LARGE_URL, ScraperStore.ShowPosters.LARGE_FILE,
                ScraperStore.ShowPosters.SEASON, ScraperStore.ShowPosters.URI.BASE, ScraperStore.ShowPosters.SHOW_ID,
                ImageScaler.Type.SCALE_INSIDE
                ),
        EPISODE_PICTURE(
                ScraperStore.ShowPosters.THUMB_URL, ScraperStore.ShowPosters.THUMB_FILE,
                ScraperStore.ShowPosters.LARGE_URL, ScraperStore.ShowPosters.LARGE_FILE,
                null, ScraperStore.ShowPosters.URI.BASE, ScraperStore.ShowPosters.SHOW_ID,
                ImageScaler.Type.SCALE_INSIDE
        );


        public final String thumbUrlColumn;
        public final String thumbFileColumn;
        public final String largeUrlColumn;
        public final String largeFileColumn;
        public final String seasonColumn;
        public final Uri baseUri;
        public final String remoteIdColumn;
        public final ImageScaler.Type scaleType;

        Type(String thumbUrl, String thumbFile, String largeUrl, String largeFile, String season, Uri uri, String remoteId, ImageScaler.Type scale) {
            thumbUrlColumn = thumbUrl;
            thumbFileColumn = thumbFile;
            largeUrlColumn = largeUrl;
            largeFileColumn = largeFile;
            seasonColumn = season;
            baseUri = uri;
            remoteIdColumn = remoteId;
            scaleType = scale;
        }
    }

    private String mThumbUrl;
    private String mThumbFile;
    private String mLargeUrl;
    private String mLargeFile;
    private int mSeason = -1;
    public String language;
    private long mId = -1;
    private long mRemoteId = -1;
    private long mOnlineID = -1;
    private final String mNameSeed;

    private final Type mType;

    public static void setGeneralPosterSize(int width, int height) {
        POSTER_WIDTH = width;
        POSTER_HEIGHT = height;
    }

    public ScraperImage(Type type, String nameSeed) {
        mType = type;
        mNameSeed = nameSeed;
    }
    public void setLanguage(String language){
        this.language = language;
    }
    public static ScraperImage fromExistingCover(String path, Type type) {
        ScraperImage image = new ScraperImage(type, null);
        image.mLargeFile = path;
        image.mThumbFile = path;
        return image;
    }

    public static ScraperImage fromCursor(Cursor cur, Type type) {
        return fromCursor(cur, type, null);
    }

    public static ScraperImage fromCursor(Cursor cur, Type type, Type typeNoSeason) {
        long imageId = cur.getLong(cur.getColumnIndexOrThrow(BaseColumns._ID));
        long remoteId = cur.getLong(cur.getColumnIndexOrThrow(type.remoteIdColumn));
        String lFile = cur.getString(cur.getColumnIndexOrThrow(type.largeFileColumn));
        String lUrl = cur.getString(cur.getColumnIndexOrThrow(type.largeUrlColumn));
        String tFile = cur.getString(cur.getColumnIndexOrThrow(type.thumbFileColumn));
        String tUrl = cur.getString(cur.getColumnIndexOrThrow(type.thumbUrlColumn));
        int season = -1;
        if (type.seasonColumn != null)
            season = cur.getInt(cur.getColumnIndexOrThrow(type.seasonColumn));
        if (season == -1 && typeNoSeason != null)
            type = typeNoSeason;
        ScraperImage image = new ScraperImage(type, null);
        image.setLargeFile(lFile);
        image.setLargeUrl(lUrl);
        image.setThumbFile(tFile);
        image.setThumbUrl(tUrl);
        image.setId(imageId);
        image.setRemoteId(remoteId);
        if (season != -1)
            image.setSeason(season);
        return image;
    }

    /**
     * @return true if this is the poster or the backdrop for a movie
     */
    public boolean isMovie() {
        return (mType==Type.MOVIE_POSTER || mType == Type.MOVIE_BACKDROP);
    }

    /**
     * Caution this is different than isShow()
     * @return true if this is the poster for an episode
     */
    public boolean isEpisode() {
        return (mType==Type.EPISODE_POSTER);
    }

    /**
     * Caution this is different than isEpisode()
     * @return true if this is the poster or the backdrop for a TV show
     */
    public boolean isShow() {
        return (mType==Type.SHOW_POSTER || mType==Type.SHOW_BACKDROP);
    }

    public long save(Context context, long remoteId) {
        ContentValues cv = toContentValues(remoteId);
        Uri insert = context.getContentResolver().insert(mType.baseUri, cv);
        long result = -1;
        if (insert != null)
            result = Long.parseLong(insert.getLastPathSegment());
        mId = result;
        return result;
    }

    public ContentProviderOperation getSaveOperation(long remoteId) {
        ContentValues cv = toContentValues(remoteId);
        return ContentProviderOperation.newInsert(mType.baseUri).withValues(cv).build();
    }

    public ContentProviderOperation getSaveOperationBackreferenced(int backref) {
        ContentValues cv = toContentValues(0); // some bogus value - value is overwritten
        return ContentProviderOperation.newInsert(mType.baseUri)
                .withValues(cv)
                .withValueBackReference(mType.remoteIdColumn, backref) // will replace movideId with ID returned by execution of allOperations
                .build();
    }

    public ContentValues toContentValues(long remoteId) {
        ContentValues cv = new ContentValues();
        cvPut(cv, mType.remoteIdColumn, String.valueOf(remoteId));
        cvPut(cv, mType.thumbUrlColumn, mThumbUrl);
        cvPut(cv, mType.thumbFileColumn, mThumbFile);
        cvPut(cv, mType.largeUrlColumn, mLargeUrl);
        cvPut(cv, mType.largeFileColumn, mLargeFile);
        cvPut(cv, mType.seasonColumn, String.valueOf(mSeason));
        return cv;
    }

    public List<ScraperImage> asList() {
        ArrayList<ScraperImage> list = new ArrayList<ScraperImage>(1);
        list.add(this);
        return list;
    }

    public String getThumbUrl() {
        return mThumbUrl;
    }
    public void setThumbUrl(String thumbUrl) {
        mThumbUrl = thumbUrl;
    }
    public String getThumbFile() {
        return mThumbFile;
    }
    public File getThumbFileF() {
        return mThumbFile != null ? new File(mThumbFile) : null;
    }
    public void setThumbFile(String thumbFile) {
        mThumbFile = thumbFile;
    }
    public void generateFileNames(Context context) {
        if (mThumbFile == null && mThumbUrl != null) {
            mThumbFile = getFilePath(mThumbUrl, true, context);
            if (DBG2) Log.d(TAG, "mThumbFile = " + mThumbFile);
        }
        if (mLargeFile == null && mLargeUrl != null) {
            mLargeFile = getFilePath(mLargeUrl, false, context);
            if (DBG2) Log.d(TAG, "mLargeFile = " + mLargeFile);
        }
    }
    public String getLargeUrl() {
        return mLargeUrl;
    }
    public void setLargeUrl(String largeUrl) {
        mLargeUrl = largeUrl;
    }
    public String getLargeFile() {
        return mLargeFile;
    }
    public File getLargeFileF() {
        return mLargeFile != null ? new File(mLargeFile) : null;
    }
    public void setLargeFile(String largeFile) {
        mLargeFile = largeFile;
    }
    public int getSeason() {
        if (mType == Type.EPISODE_POSTER)
            return mSeason;
        return -1;
    }
    public void setSeason(int season) {
        mSeason = season;
    }
    public long getId() {
        return mId;
    }
    public void setId(long imageId) {
        mId = imageId;
    }
    public long getRemoteId() {
        return mRemoteId;
    }
    public void setRemoteId(long itemId) {
        mRemoteId = itemId;
    }

    public void setOnlineId(long itemId) {
        mOnlineID = itemId;
    }
    private String getFilePath(String url, boolean thumb, Context context) {
        if (url == null) return null;
        return new File(getDir(mType, context), getFileName(url, mNameSeed, thumb)).getPath();
    }

    private static String getFileName(String url, String nameSeed, boolean thumb) {
        // goal is to generate a stable + unique filename
        int urlHash;
        int seedHash;

        if (url != null) {
            urlHash = url.hashCode();
        } else {
            Log.w(TAG, "getFileName: url is null!");
            urlHash = String.valueOf(System.currentTimeMillis()).hashCode();
        }

        if (nameSeed != null) {
            seedHash = nameSeed.hashCode();
        } else {
            Log.w(TAG, "getFileName: nameSeed is null!");
            seedHash = String.valueOf(System.currentTimeMillis()).hashCode();
        }

        String name = String.valueOf(seedHash) + String.valueOf(urlHash);
        return name + (thumb ? "t.jpg" : "l.jpg");
    }

    /** returns directory where image shall be saved, creates dir if necessary */
    private final static File getDir(Type type, Context context) {
        File ret;
        switch (type) {
            case EPISODE_POSTER:
            case MOVIE_POSTER:
            case SHOW_POSTER:
                ret =  MediaScraper.getPosterDirectory(context);
                break;
            case MOVIE_BACKDROP:
            case SHOW_BACKDROP:
                ret = MediaScraper.getBackdropDirectory(context);
                break;
            default:
                // that would be really bad, kind of impossible though
                Log.e(TAG, "could not determine Directory, fallback to public dir");
                ret = Environment.getExternalStorageDirectory();
                break;
        }
        // if dir does not exists, create it.
        if (!ret.exists())
            ret.mkdirs();
        // also try to make it readable
        ret.setReadable(true, false);
        return ret;
    }

    /** returns directory where image shall be downloaded to, creates dir if necessary */
    private final static File getCacheDir(Type type, Context context) {
        File ret;
        switch (type) {
            case EPISODE_POSTER:
            case MOVIE_POSTER:
            case SHOW_POSTER:
                ret = MediaScraper.getImageCacheDirectory(context);
                break;
            case MOVIE_BACKDROP:
            case SHOW_BACKDROP:
                ret = MediaScraper.getBackdropCacheDirectory(context);
                break;
            default:
                // that would be really bad, kind of impossible though
                Log.e(TAG, "could not determine Directory, fallback to public dir");
                ret = Environment.getExternalStorageDirectory();
                break;
        }
        // if dir does not exists, create it.
        if (!ret.exists())
            ret.mkdirs();
        // also try to make it readable
        ret.setReadable(true, false);
        return ret;
    }
    /** returns timeout for cache */
    private final static long getCacheTimeout(Type type) {
        switch (type) {
            case EPISODE_POSTER:
            case MOVIE_POSTER:
            case SHOW_POSTER:
                return MediaScraper.IMAGE_CACHE_TIMEOUT;
            case MOVIE_BACKDROP:
            case SHOW_BACKDROP:
                return MediaScraper.BACKDROP_CACHE_TIMEOUT;
            default:
                return 0;
        }
    }

    private static final void cvPut (ContentValues cv, String key, String value) {
        if (cv != null && key != null) cv.put(key, value);
    }

    private static final MultiLock<String> sLock = new MultiLock<String>();

    public final boolean download(Context context) {
       return download(context, false, 0, 0, false);
    }

    /**
     * Special version just for our demo content.
     * Don't use in regular code.
     */
    public final void downloadFake(Context context) {
        download(context, false, 0, 0, true);
    }

    public final void downloadThumb(Context context, int maxWidth, int maxHeight) {
        download(context, true /* thumb */, maxWidth, maxHeight, false /* fake */);
    }

    private boolean download(Context context, boolean thumb, int maxWidth, int maxHeight, boolean fake) {
        String file = mLargeFile;
        String url = mLargeUrl;
        boolean success = false ;
        if (thumb) {
            file = mThumbFile;
            url = mThumbUrl == null ? mLargeUrl : mThumbUrl;
        }

        String lockString = file == null ? "null" : file;
        sLock.lock(lockString);
        try {
            if (DBG) Log.d(TAG, "download " + mType.name());
            // maybe large file exists already
            if (fileIfExists(file) != null) {
                if (DBG) Log.d(TAG, "using existing file.");
                success = true;
            }

            else if (url == null) {
                Log.d(TAG, "There is no URL to download. Aborting.");
                success = false;
            }

            else if (file == null) {
                Log.d(TAG, "No Filename set. Aborting.");
                success = false;
            }
            else {
                // make sure directories exist.
                getCacheDir(mType, context);
                getDir(mType, context);
                // does not exist - so download it and update the database.
                success = saveSizedImage(context, url, file, mType, thumb, maxWidth, maxHeight, fake);
            }
        } finally {
            sLock.unlock(lockString);
            return success;
        }

    }

    private static File fileIfExists(String path) {
        if (path != null && !path.isEmpty()) {
            File f = new File(path);
            if (f.exists())
                return f;
        }
        return null;
    }

    private static boolean saveSizedImage(Context context, String url, String targetName, Type type,
            boolean thumb, int thumbWidth, int thumbHeight, boolean fake) {
        DebugTimer dbgTimer = null;
        if (DBG) dbgTimer  = new DebugTimer();

        // determine dir to cache, backdrops on external storage
        File cacheDir = getCacheDir(type, context);

        // determine size
        int maxWidth;
        int maxHeight;
        switch (type) {
            case EPISODE_POSTER:
            case MOVIE_POSTER:
            case SHOW_POSTER:
                // TODO those values should be taken from resources. Problem: this class is used by MediaCenter
                // and ArchosWidget and we have no common resouces :/
                maxWidth = POSTER_WIDTH;
                maxHeight = POSTER_HEIGHT;
                if (DBG) Log.d(TAG, "Target: Poster(" + maxWidth + "," + maxHeight + ")");
                break;
            case MOVIE_BACKDROP:
            case SHOW_BACKDROP:
                if (thumb) {
                    maxWidth = thumbWidth;
                    maxHeight = thumbHeight;
                } else {
                        
                    // TODO maybe use fixed values here. In case we are connected to a high res display
                    // we might get a wrong / temporary size here.
                    DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
                    maxHeight = displayMetrics.heightPixels;
                    maxWidth = displayMetrics.widthPixels;
                }
                if (DBG) Log.d(TAG, "Target: Backdrop(" + maxWidth + "," + maxHeight + ")");
                break;
            default:
                maxWidth = POSTER_WIDTH;
                maxHeight = POSTER_HEIGHT;
                Log.e(TAG, "Target: Unknown, fallback to (" + maxWidth + "," + maxHeight + ")");
                break;
        }

        File rawFile;
        // now download that file into the cache
        Uri imageSource = null;
        String testUrl = url != null ? url.toLowerCase(Locale.ROOT) : "";
        // try to use anything not http(s) via MetaFile
        if (!testUrl.startsWith("http")) {
            imageSource = Uri.parse(url);
        } else
        if (!fake) {
            long timeout = getCacheTimeout(type);
            HttpCache httpCache = HttpCache.getInstance(cacheDir, timeout,
                    MediaScraper.CACHE_FALLBACK_DIRECTORY,
                    MediaScraper.CACHE_OVERWRITE_DIRECTORY);
            File cached = httpCache.getFile(url, false);
            imageSource = (cached==null) ? null : Uri.fromFile(cached);
        } else {
            File cached = HttpCache.getStaticFile(url, MediaScraper.CACHE_FALLBACK_DIRECTORY, MediaScraper.CACHE_OVERWRITE_DIRECTORY);
            imageSource = (cached==null) ? null : Uri.fromFile(cached);
        }
        if (DBG) Log.d(TAG, "Downloading took " + dbgTimer.step());
        if (imageSource == null) {
            if (DBG) Log.d(TAG,  "Downloading failed for " + url);
            return false;
        }
        boolean saveOk = ImageScaler.scale(imageSource, targetName, maxWidth, maxHeight, type.scaleType);
        if (DBG) Log.d(TAG, dbgTimer.total() + "download() in total");
        return saveOk;
    }

    public boolean setAsDefault(Context context) {
        return setAsDefault(context, mSeason);
    }

    public boolean setAsDefault(Context context, int season) {
        if (mRemoteId <= 0) {
            Log.e(TAG, "setAsDefault - don't have remoteId, aborting.");
            return false;
        }
        if (mId <= 0) {
            Log.e(TAG, "setAsDefault - don't have id, aborting.");
            return false;
        }
        boolean success = false;
        Uri updateUri = null;
        String selection = null;
        String[] selectionArgs = null;
        ContentValues updateValues = new ContentValues();
        switch (mType) {
            case EPISODE_POSTER:
                updateUri = ScraperStore.Episode.URI.BASE;
                updateValues.put(ScraperStore.Episode.POSTER_ID, Long.valueOf(mId));
                updateValues.put(ScraperStore.Episode.COVER, mLargeFile);
                selection = ScraperStore.Episode.SHOW + "=? AND " +
                        ScraperStore.Episode.SEASON + "=?";
                selectionArgs = new String[] {
                        String.valueOf(mRemoteId),
                        String.valueOf(season)
                };
                break;
            case EPISODE_PICTURE:
                updateUri = ScraperStore.Episode.URI.BASE;
                updateValues.put(ScraperStore.Episode.PICTURE, mLargeFile);
                selection = ScraperStore.Episode.ID + "=? ";
                selectionArgs = new String[] {
                        String.valueOf(mRemoteId),

                };
                break;
            case SHOW_POSTER:
                updateUri = ContentUris.withAppendedId(ScraperStore.Show.URI.ID, mRemoteId);
                updateValues.put(ScraperStore.Show.POSTER_ID, Long.valueOf(mId));
                updateValues.put(ScraperStore.Show.COVER, mLargeFile);
                break;
            case SHOW_BACKDROP:
                updateUri = ContentUris.withAppendedId(ScraperStore.Show.URI.ID, mRemoteId);
                updateValues.put(ScraperStore.Show.BACKDROP_ID, Long.valueOf(mId));
                updateValues.put(ScraperStore.Show.BACKDROP_URL, mLargeUrl);
                updateValues.put(ScraperStore.Show.BACKDROP, mLargeFile);
                break;
            case MOVIE_POSTER:
                if(mOnlineID>0) {
                    updateUri = ScraperStore.Movie.URI.BASE;
                    selection  = ScraperStore.Movie.ONLINE_ID +" = ?";
                    selectionArgs = new String[] {
                            String.valueOf(mOnlineID),
                    };

                }else {
                    updateUri = ContentUris.withAppendedId(ScraperStore.Movie.URI.ID, mRemoteId);
                }
                updateValues.put(ScraperStore.Movie.POSTER_ID, Long.valueOf(mId));
                updateValues.put(ScraperStore.Movie.COVER, mLargeFile);
                break;
            case MOVIE_BACKDROP:
                updateUri = ContentUris.withAppendedId(ScraperStore.Movie.URI.ID, mRemoteId);
                updateValues.put(ScraperStore.Movie.BACKDROP_ID, Long.valueOf(mId));
                updateValues.put(ScraperStore.Movie.BACKDROP_URL, mLargeUrl);
                updateValues.put(ScraperStore.Movie.BACKDROP, mLargeFile);
                break;
            default:
                Log.e(TAG, "unknown type:" + mType);
                break;
        }
        if (updateUri != null) {
            success = context.getContentResolver().update(updateUri, updateValues, selection, selectionArgs) > 0;
        }
        return success;
    }

    public void setAsDefaultAndDownloadAsync(final Context context) {
        AsyncTask.execute(new Runnable() {
            public void run() {
                if (setAsDefault(context))
                    download(context);
            }
        });
    }

    /** true if this image source url is in the internets */
    public boolean isHttpImage() {
        return mLargeUrl != null && mLargeUrl.startsWith("http");
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ScraperImage [mThumbUrl=");
        builder.append(mThumbUrl);
        builder.append(", mThumbFile=");
        builder.append(mThumbFile);
        builder.append(", mLargeUrl=");
        builder.append(mLargeUrl);
        builder.append(", mLargeFile=");
        builder.append(mLargeFile);
        builder.append(", mSeason=");
        builder.append(mSeason);
        builder.append(", mId=");
        builder.append(mId);
        builder.append(", mRemoteId=");
        builder.append(mRemoteId);
        builder.append(", mNameSeed=");
        builder.append(mNameSeed);
        builder.append(", mType=");
        builder.append(mType.name());
        builder.append("]");
        return builder.toString();
    }
}
