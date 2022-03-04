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

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.BaseColumns;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.Pair;

import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediaprovider.video.VideoStore.MediaColumns;
import com.uwetrottmann.tmdb2.entities.Image;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

public abstract class BaseTags implements Parcelable {

    private static final Logger log = LoggerFactory.getLogger(BaseTags.class);

    public static final int GENERIC = 0;

    public static final int VIDEO = 1;
    public static final int MOVIE = 11;
    public static final int TV_SHOW = 12;

    public static final int MUSIC = 2;

    protected long mId;

    protected long mOnlineId;
    protected String mContentRating;
    protected String mImdbId;

    protected String mTitle;
    protected float mRating;
    protected String mPlot;
    protected Map<String, String> mActors;
    protected Map<String, String> mSet; // for collection
    protected String mActorsFormatted;
    protected SpannableString mSpannableActorsFormatted;
    protected List<String> mDirectors;
    protected List<String> mWriters;
    protected List<String> mSeasonPlots;
    protected String mDirectorsFormatted;
    protected String mWritersFormatted;
    protected String mSeasonPlotsFormatted;
    protected Uri mFile;
    protected long mVideoId;
    protected List<ScraperImage> mPosters;
    protected List<ScraperImage> mBackdrops;
    protected List<ScraperImage> mNetworkLogos;
    protected List<ScraperImage> mActorPhotos;
    protected long mRuntimeMs;
    protected long mLastPlayedMs;
    protected long mBookmark;
    protected long mResume;

    public BaseTags() {
        mDirectors = new LinkedList<String>();
        mWriters = new LinkedList<String>();
        mSeasonPlots = new LinkedList<String>();
        mActors = new LinkedHashMap<String, String>();
    }

    public BaseTags(Parcel in) {
        this();
        readFromParcel(in);
    }

    public boolean actorExists(String name) {
        if(name != null)
            return mActors.containsKey(name);
        return false;
    }

    public boolean directorExists(String name) {
        return mDirectors.contains(name);
    }
    public boolean writerExists(String name) {
        return mWriters.contains(name);
    }
    public boolean seasonplotExists(String seasonplot) {
        return mSeasonPlots.contains(seasonplot);
    }

    public Map<String, String> getActors() { return java.util.Collections.unmodifiableMap(mActors); }
    public Map<String, String> getSet() { return mSet; }
    public List<String> getDirectors() { return mDirectors; }
    public List<String> getWriters() { return mWriters; }
    public List<String> getSeasonPlots() { return mSeasonPlots; }
    public Uri getFile() { return mFile; }
    public long getId() { return mId; }
    public long getVideoId() { return mVideoId; }
    public String getPlot() { return mPlot; }

    public float getRating() { return mRating; }
    public String getTitle() { return mTitle; }
    public String getStorageName() { return mTitle; }
    public List<ScraperImage> getPosters() { return mPosters; }
    public ScraperImage getDefaultPoster() { return getFirst(mPosters); }
    public List<ScraperImage> getBackdrops() { return mBackdrops; }
    public ScraperImage getDefaultBackdrop() { return getFirst(mBackdrops); }
    public List<ScraperImage> getNetworkLogos() { return mNetworkLogos; }
    public ScraperImage getDefaultNetworkLogo() { return getFirst(mNetworkLogos); }
    public List<ScraperImage> getActorPhotos() { return mActorPhotos; }
    public ScraperImage getDefaultActorPhoto() { return getFirst(mActorPhotos); }
    public String getContentRating() { return mContentRating; }
    public String getImdbId() { return mImdbId; }
    public long getOnlineId() { return mOnlineId; }
    public long getRuntime(TimeUnit unit) { return unit.convert(mRuntimeMs, TimeUnit.MILLISECONDS); }
    public long getResume() { return mResume; }
    public long getBookmark() { return mBookmark; }
    public long getLastPlayed(TimeUnit unit) { return unit.convert(mLastPlayedMs, TimeUnit.MILLISECONDS); }

    public abstract List<ScraperImage> getAllBackdropsInDb(Context context);
    public abstract List<ScraperImage> getAllPostersInDb(Context context);
    public abstract List<ScraperImage> getAllNetworkLogosInDb(Context context);
    public abstract List<ScraperImage> getAllActorPhotosInDb(Context context);
    public abstract List<ScraperTrailer> getAllTrailersInDb(Context context);
    public String getDirectorsFormatted() {
        ensureFormattedDirectors();
        return mDirectorsFormatted;
    }
    public String getWritersFormatted() {
        ensureFormattedWriters();
        return mWritersFormatted;
    }

    public String getSeasonPlotsFormatted() {
        ensureFormattedSeasonPlots();
        return mSeasonPlotsFormatted;
    }

    public String getActorsFormatted() {
        ensureFormattedActors();
        return mActorsFormatted;
    }

    /** does nothing if mActorsFormatted is already set, otherwise builds from mActors */
    private void ensureFormattedActors() {
        if (mActorsFormatted == null && mActors != null && !mActors.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            boolean firstTime = true;
            for (Entry<String, String> item : mActors.entrySet()) {
                if (firstTime) {
                    firstTime = false;
                } else {
                    sb.append(", ");
                }
                String actor = item.getKey();
                String role = item.getValue();
                sb.append(actor);
                if (role != null && !role.isEmpty()) {
                    sb.append(" (");
                    sb.append(role);
                    sb.append(')');
                }
            }
            mActorsFormatted = sb.toString();
        }
    }
    
    public SpannableString getSpannableActorsFormatted() {
        ensureSpannableFormattedActors();
        return mSpannableActorsFormatted;
    }

    private void ensureSpannableFormattedActors() {
        if (mSpannableActorsFormatted == null && mActors != null && !mActors.isEmpty()) {
            SpannableStringBuilder sb = new SpannableStringBuilder();
            boolean firstTime = true;
            for (Entry<String, String> item : mActors.entrySet()) {
                if (firstTime) {
                    firstTime = false;
                } else {
                    sb.append(", ");
                }
                String actor = item.getKey();
                String role = item.getValue();
                sb.append(actor);
                if (role != null && !role.isEmpty()) {
                    role = role.replaceAll("\\s*\\(.+\\)\\s*", "");
                    String[] roles = role.split("(?<=[^\\/\\|])\\s*(?:\\/|\\|)\\s*(?=[^\\/\\|])");
                    int color = Color.argb(164, 255, 255, 255);

                    sb.append(" (");

                    for (int i = 0; i < roles.length; i++) {
                        sb.append(roles[i], new ForegroundColorSpan(color), 0);

                        if (i != roles.length - 1)
                            sb.append(" / ");
                    }

                    sb.append(')');
                }
            }
            mSpannableActorsFormatted = SpannableString.valueOf(sb);
        }
    }

    /** does nothing if mDirectorsFormatted is already set, otherwise builds from mDirectors */
    private void ensureFormattedDirectors() {
        if (mDirectorsFormatted == null && mDirectors != null && !mDirectors.isEmpty()) {
            mDirectorsFormatted = TextUtils.join(", ", mDirectors);
        }
    }

    /** does nothing if mWritersFormatted is already set, otherwise builds from mWriters */
    private void ensureFormattedWriters() {
        if (mWritersFormatted == null && mWriters != null && !mWriters.isEmpty()) {
            mWritersFormatted = TextUtils.join(", ", mWriters);
        }
    }

    /** does nothing if mSeasonPlotsFormatted is already set, otherwise builds from mSeasonPlots */
    private void ensureFormattedSeasonPlots() {
        if (mSeasonPlotsFormatted == null && mSeasonPlots != null && !mSeasonPlots.isEmpty()) {
            mSeasonPlotsFormatted = TextUtils.join(", ", mSeasonPlots);
        }
    }

    public File getCover() {
        ScraperImage image = getDefaultPoster();
        if (image != null)
            return image.getLargeFileF();
        return null;
    }

    public File getBackdrop() {
        ScraperImage image = getDefaultBackdrop();
        if (image != null)
            return image.getLargeFileF();
        return null;
    }

    public File getNetworkLogo() {
        ScraperImage image = getDefaultNetworkLogo();
        if (image != null)
            return image.getLargeFileF();
        return null;
    }

    public File getActorPhoto() {
        ScraperImage image = getDefaultActorPhoto();
        if (image != null)
            return image.getLargeFileF();
        return null;
    }

    public List<File> getNetworkLogosLargeFileF() {
        List<File> files = new ArrayList<>();
        for (int i = 0; i < getNetworkLogos().size(); i++) {
            ScraperImage file = getNetworkLogos().get(i);
            File mfile = file.getLargeFileF();
            files.add(mfile);
        }
        return files;
    }

    public List<File> getActorPhotosLargeFileF() {
        List<File> files = new ArrayList<>();
        for (int i = 0; i < getActorPhotos().size(); i++) {
            ScraperImage file = getActorPhotos().get(i);
            File mfile = file.getLargeFileF();
            files.add(mfile);
        }
        return files;
    }

    public File downloadGetDefaultPosterFile(Context context) {
        ScraperImage image = getDefaultPoster();
        return downloadGetImage(image, context);
    }
    public File downloadGetDefaultNetworkLogoFile(Context context) {
        ScraperImage image = getDefaultNetworkLogo();
        return downloadGetImage(image, context);
    }
    public File downloadGetDefaultActorPhotoFile(Context context) {
        ScraperImage image = getDefaultActorPhoto();
        return downloadGetImage(image, context);
    }
    public File downloadGetDefaultBackdropFile(Context context) {
        ScraperImage image = getDefaultBackdrop();
        return downloadGetImage(image, context);
    }
    public static File downloadGetImage(ScraperImage image, Context context) {
        if (image != null) {
            image.download(context);
            return image.getLargeFileF();
        }
        return null;
    }

    private static final <E> E getFirst(List<E> list) {
        return (list != null && list.size() > 0) ? list.get(0) : null;
    }

    public void downloadPoster(Context context) {
        ScraperImage image = getDefaultPoster();
        if (image != null) {
            log.debug("downloadPoster: " + mTitle + ", url " + image.getLargeUrl());
            image.download(context);
        } else
            log.warn("downloadPoster: image is null for " + mTitle);
    }

    // normally not used since huge footprint -> downloaded when browsing
    public void downloadPosters(Context context) {
        if (mPosters != null)
            for (ScraperImage poster : mPosters) {
                log.debug("downloadPosters: " + mTitle + ", url " + poster.getLargeUrl());
                poster.download(context);
            } else
                log.warn("downloadPosters: mPosters is null for " + mTitle);
    }

    // normally not used since huge footprint -> downloaded when browsing
    public void downloadBackdrops(Context context) {
        if (mBackdrops != null)
            for (ScraperImage backdrop : mBackdrops) {
                log.debug("downloadBackdrops: " + mTitle + ", url " + backdrop.getLargeUrl());
                backdrop.download(context);
            } else
            log.warn("downloadBackdrops: mBackdrops is null for " + mTitle);
    }

    // normally not used since huge footprint -> downloaded when browsing
    public void downloadNetworkLogos(Context context) {
        if (mNetworkLogos != null)
            for (ScraperImage networklogo : mNetworkLogos) {
                log.debug("downloadNetworkLogos: " + mTitle + ", url " + networklogo.getLargeUrl());
                networklogo.download(context);
            } else
            log.warn("downloadNetworkLogos: mNetworkLogos is null for " + mTitle);
    }

    // normally not used since huge footprint -> downloaded when browsing
    public void downloadActorPhotos(Context context) {
        if (mActorPhotos != null)
            for (ScraperImage actorphoto : mActorPhotos) {
                log.debug("downloadActorPhotos: " + mTitle + ", url " + actorphoto.getLargeUrl());
                actorphoto.download(context);
            } else
            log.warn("downloadActorPhotos: mActorPhotos is null for " + mTitle);
    }

    public void downloadBackdrop(Context context) {
        ScraperImage image = getDefaultBackdrop();
        if (image != null) {
            log.debug("downloadBackdrop: " + mTitle + ", url " + image.getLargeUrl());
            image.download(context);
        } else
            log.warn("downloadBackdrop: image is null for " + mTitle);
    }

    public void downloadNetworkLogo(Context context) {
        ScraperImage image = getDefaultNetworkLogo();
        if (image != null) {
            log.debug("downloadNetworkLogo: " + mTitle + ", url " + image.getLargeUrl());
            image.download(context);
        } else
            log.warn("downloadNetworkLogo: image is null for " + mTitle);
    }

    public void downloadActorPhoto(Context context) {
        ScraperImage image = getDefaultActorPhoto();
        if (image != null) {
            log.debug("downloadActorPhoto: " + mTitle + ", url " + image.getLargeUrl());
            image.download(context);
        } else
            log.warn("downloadActorPhoto: image is null for " + mTitle);
    }

    public final void downloadAllImages(Context context) {
        downloadPoster(context);
        downloadBackdrop(context);
        downloadNetworkLogo(context);
        downloadActorPhoto(context);
    }

    /**
     * Saves the information. Blocking!
     * @param context Context to be used
     * @param videoId _id of the video in media db
     * @return id of item in the database
     */
    public abstract long save(Context context, long videoId);

    private static final String[] PROJECTION_ID = new String[] { BaseColumns._ID };
    private static final String WHERE_ID = MediaColumns.DATA + "=?";

    /**
     * Saves the information. Blocking!
     * First queries the db to find the ID, don't use this if you can avoid it.
     * @param context Context to be used
     * @return id of item in the database
     */
    public final long save(Context context, Uri video) {
        ContentResolver cr = context.getContentResolver();
        Cursor c = cr.query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, PROJECTION_ID, WHERE_ID,
                new String[] { video.toString() }, null);
        long videoId = -1;
        if (c != null) {
            if (c.moveToFirst()) {
                videoId = c.getLong(0);
            }
            c.close();
        }
        if (videoId > 0)
            return save(context, videoId);
        return -1;
    }

    /**
     * Saves the information asynchronously
     * @param context Context that will get used
     * @param videoId _id of the video in media db
     */
    public void saveAsync(final Context context, final long videoId) {
        AsyncTask.execute(new Runnable() {

            public void run() {
                save(context, videoId);
            }
        });
    }

    /** all strings are trimmed, does not put empty actor, does not replace entries */
    public void addActorIfAbsent(Map<String, String> actorRoleMap) {
        if (actorRoleMap != null) {
            for (Entry<String, String> entry : actorRoleMap.entrySet()) {
                addActorIfAbsent(entry.getKey(), entry.getValue());
            }
        }
    }

    /** all strings are trimmed, does not put empty actor, does not replace entries */
    public void addActorIfAbsent(String actor, String role) {
        // skip empty actor
        if (actor != null && !actor.isEmpty()) {
            String key = actor.trim();
            // make sure it's not empty after trim
            if (!key.isEmpty()) {
                // replace role by trimmed value or empty string if null
                String value = role != null ? role.trim() : "";
                addIfAbsentOrBetter(key, value, mActors);
            }
        }
    }

    /**
     * all strings are trimmed, does not put empty values, does not replace entries.
     * optional splitCharacters to specify how to split the string
     **/
    public void addActorIfAbsent(String actor, char... splitCharacters) {
        addIfAbsentSplitNTrim(actor, mActors, splitCharacters);
    }

    public void setActors(List<String> actors) {
        mActors = new LinkedHashMap<>();
        for(String actor : actors)
            mActors.put(actor, "");
    }

    /**
     * all strings are trimmed, does not put empty values, does not replace entries.
     * optional splitCharacters to specify how to split the string
     **/
    public void addDirectorIfAbsent(String director, char... splitCharacters) {
        addIfAbsentSplitNTrim(director, mDirectors, splitCharacters);
    }
    public void addWriterIfAbsent(String writer, char... splitCharacters) {
        addIfAbsentSplitNTrim(writer, mWriters, splitCharacters);
    }
    public void addSeasonPlotIfAbsent(String seasonplot, char... splitCharacters) {
        addIfAbsentSplitNTrim(seasonplot, mSeasonPlots, splitCharacters);
    }

    public void setDirectors(List<String> directors) { mDirectors = directors; }
    public void setWriters(List<String> writers) { mWriters = writers; }
    public void setSeasonPlots(List<String> seasonplots) { mSeasonPlots = seasonplots; }

    public abstract void setCover(File file);

    public void setFile(Uri searchFile) { mFile = searchFile; }
    public void setId(long id) { mId = id; }
    public void setVideoId(long id) { mVideoId = id; }
    public void setPlot(String plot) { mPlot = plot; }
    public void setRating(float rating) { mRating = rating; }
    public void setTitle(String title) { mTitle = title; }
    public void setPosters(List<ScraperImage> list) { mPosters = list; }
    public void setBackdrops(List<ScraperImage> list) { mBackdrops = list; }
    public void setNetworkLogos(List<ScraperImage> list) { mNetworkLogos = list; }
    public void setActorPhotos(List<ScraperImage> list) { mActorPhotos = list; }
    public void setActorsFormatted(String actors) { mActorsFormatted = actors; }
    public void setDirectorsFormatted(String directors) { mDirectorsFormatted = directors; }
    public void setWritersFormatted(String writers) { mWritersFormatted = writers; }
    public void setSeasonPlotsFormatted(String seasonplots) { mSeasonPlotsFormatted = seasonplots; }
    public void setContentRating(String contentRating) { mContentRating = contentRating; }
    public void setImdbId(String imdbId) { mImdbId = imdbId; }
    public void setOnlineId(long onlineId) { mOnlineId = onlineId; }
    public void setRuntime(long time, TimeUnit unit) { mRuntimeMs = TimeUnit.MILLISECONDS.convert(time, unit); }
    public void setBookmark(long bookmark) { mBookmark = bookmark; }
    public void setResume(long resume) { mResume = resume; }
    public void setLastPlayed(long time, TimeUnit unit) { mLastPlayedMs = TimeUnit.MILLISECONDS.convert(time, unit); }

    /** Adds this image as first element to the list of backdrops */
    public void addDefaultBackdrop(ScraperImage image) {
        mBackdrops = addAsFirstItem(mBackdrops, image);
    }

    /** Adds this image as first element to the list of posters */
    public void addDefaultPoster(ScraperImage image) {
        mPosters = addAsFirstItem(mPosters, image);
    }

    /** Adds this image as first element to the list of network logos */
    public void addDefaultNetworkLogo(ScraperImage image) {
        mNetworkLogos = addAsFirstItem(mNetworkLogos, image);
    }

    /** Adds this image as first element to the list of actor photos */
    public void addDefaultActorPhoto(ScraperImage image) {
        mActorPhotos = addAsFirstItem(mActorPhotos, image);
    }

    @Override
    public String toString() {
        return " TITLE=" + mTitle + " / RATING=" + mRating + " / DIRECTORS=" + mDirectors + " / WRITERS=" + mWriters + " / PLOT=" + mPlot + " / SEASONPLOTS=" + mSeasonPlots +
            " / ACTORS=" + mActors + " / COVER=" + getCover();
    }

    public int describeContents() {
        return 0;
    }

    private void readFromParcel(Parcel in) {
        mId = in.readLong();
        mTitle = in.readString();
        mRating = in.readFloat();
        mPlot = in.readString();
        in.readMap(mActors, LinkedHashMap.class.getClassLoader());
        in.readStringList(mDirectors);
        in.readStringList(mWriters);
        in.readStringList(mSeasonPlots);
        mFile = Uri.parse(in.readString());
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeLong(mId);
        out.writeString(nonNull(mTitle));
        out.writeFloat(mRating);
        out.writeString(nonNull(mPlot));
        out.writeMap(mActors);
        out.writeStringList(mDirectors);
        out.writeStringList(mWriters);
        out.writeStringList(mSeasonPlots);
        out.writeString(mFile!=null?mFile.toString():"");
    }

    /**
     * Adds items if they are not empty after trimming, optional splitting of strings
     * @param string Either containing a single item or multiple items separated by splitCharacters
     * @param map where to add, string is used as key, value is empty string
     * @param splitCharacters if present used to split string
     */
    protected static void addIfAbsentSplitNTrim(String string, Map<String, String> map, char... splitCharacters) {
        if (string != null && !string.isEmpty()) {
            if (splitCharacters != null && splitCharacters.length > 0) {
                List<String> splitted = StringUtils.split(string, splitCharacters);
                // splitted is already trimmed and does not contain empty strings
                addIfAbsent(splitted, map);
            } else {
                String toAdd = string.trim();
                if (!toAdd.isEmpty())
                    addIfAbsentOrBetter(toAdd, "", map);
            }
        }
    }

    /**
     * Adds items if they are not empty after trimming, optional splitting of strings
     * @param string Either containing a single item or multiple items separated by splitCharacters
     * @param collection where to add
     * @param splitCharacters if present used to split string
     */
    protected static void addIfAbsentSplitNTrim(String string, Collection<String> collection, char... splitCharacters) {
        if (string != null && !string.isEmpty()) {
            if (splitCharacters != null && splitCharacters.length > 0) {
                List<String> splitted = StringUtils.split(string, splitCharacters);
                // splitted is already trimmed and does not contain empty strings
                addIfAbsent(splitted, collection);
            } else {
                String toAdd = string.trim();
                if (!toAdd.isEmpty())
                    addIfAbsent(toAdd, collection);
            }
        }
    }

    /** no checks, just puts unmodified string */
    private final static void addIfAbsent(List<String> splitted, Collection<String> collection) {
        for (String string : splitted) {
            addIfAbsent(string, collection);
        }
    }

    /** no checks, just puts unmodified string, list = keys, values = empty string */
    private final static void addIfAbsent(List<String> splitted, Map<String, String> map) {
        for (String string : splitted) {
            addIfAbsentOrBetter(string, "", map);
        }
    }

    /** no checks, just puts unmodified string */
    private final static void addIfAbsent(String source, Collection<String> target) {
        if (!target.contains(source))
            target.add(source);
    }

    /** puts unmodified strings IF key is not present OR mapped value was empty */
    private final static void addIfAbsentOrBetter(String key, String value, Map<String, String> map) {
        String oldValue = map.get(key);
        if (oldValue == null || oldValue.isEmpty())
            map.put(key, value);
    }

    protected final static String nonNull(String source) {
        return (source == null ? "" : source);
    }

    protected final static long nonNull(Date source) {
        return (source == null ? 0L : source.getTime());
    }
    protected final static Date readDate(long source) {
        return (source == 0 ? null : new Date(source));
    }

    protected static final <T> List<T> safeList(List<T> list) {
        return list == null ? Collections.<T>emptyList() : list;
    }

    /**
     * Adds an element to this list as it's first element.<p>
     * if item is null, list is returned unaltered<p>
     * if list is null, returns a new list<p>
     * if list is not modifiable, returns a copy of the list
     */
    protected static final <T> List<T> addAsFirstItem(List<T> list, T item) {
        if (item == null)
            return list;

        // try to add at location 0
        if (list != null) {
            try {
                list.add(0, item);
                return list;
            } catch (UnsupportedOperationException e) {
                // this list does not support adding
            }
        }

        // fallback to creating a copy / new list
        List<T> result = new ArrayList<T>(list != null ? list.size() + 1 : 1);
        result.add(item);
        if (list != null) result.addAll(list);
        return result;
    }

}
