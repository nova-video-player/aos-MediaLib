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

package com.archos.mediacenter.utils.videodb;

import java.io.File;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.BaseColumns;
import android.util.Log;

import com.archos.filecorelibrary.MetaFile;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.EpisodeTags;
import com.archos.mediascraper.ScrapeDetailResult;
import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.ShowTags;

public class VideoDbInfo implements Parcelable {

    public VideoDbInfo() {}

    public VideoDbInfo(String path) {
        setPath(path);
    }


    public VideoDbInfo(Uri uri) {
        setFile(uri);
    }

    public void setPath(String path) {
            uri = Uri.parse(path);
    }

    public void setFile(Uri uri) {
        this.uri = uri;
    }
    public int scraperId = -1;
    public long id = -1;
    public Uri uri = null;
    public String title = null;
    public int duration = -1;
    public int resume = -1;
    public int bookmark = -1;
    public int audioTrack = -1;
    public int subtitleTrack = -1;
    public int subtitleDelay = 0;
    public int subtitleRatio = 0;
    public long lastTimePlayed = -1;
    public int nbSubtitles = 0;
    public boolean isScraped = false;
    public ScrapeStatus scrapeStatus = null;
    public boolean isShow = false;
    public String scraperTitle = null;
    public String scraperCover = null;
    public int scraperEpisodeNr = -1;
    public int scraperSeasonNr = -1;
    public String scraperEpisodeName = null;
    public String scraperMovieId = null;
    public String scraperShowId = null;
    public String scraperEpisodeId = null;
    public int traktSeen = -1;
    public int traktLibrary = -1;
    public int traktResume = 0; // a negative value means -> has been set but not synchronized with trakt
    public int videoStereo = -1;
    public int videoDefinition = -1;

    public static final Uri URI = VideoStore.Video.Media.EXTERNAL_CONTENT_URI;
    public static final String SELECTION_ID = BaseColumns._ID + "=?";
    public static final String SELECTION_PATH = VideoStore.MediaColumns.DATA + "=?";

    public static final String[] COLUMNS = {
        BaseColumns._ID,                                            //  0
        VideoStore.MediaColumns.DATA,                               //  1
        VideoStore.MediaColumns.TITLE,                              //  2
        VideoStore.Video.VideoColumns.DURATION,                     //  3
        VideoStore.Video.VideoColumns.BOOKMARK,                     //  4
        VideoStore.Video.VideoColumns.ARCHOS_BOOKMARK,              //  5
        VideoStore.Video.VideoColumns.ARCHOS_PLAYER_PARAMS,         //  6
        VideoStore.Video.VideoColumns.ARCHOS_PLAYER_SUBTITLE_DELAY, //  7
        VideoStore.Video.VideoColumns.ARCHOS_PLAYER_SUBTITLE_RATIO, //  8
        VideoStore.Video.VideoColumns.ARCHOS_NUMBER_OF_SUBTITLE_TRACKS, // 9
        VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED,      //  10
        VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN,            //  11
        VideoStore.Video.VideoColumns.ARCHOS_TRAKT_LIBRARY,         //  12
        VideoStore.Video.VideoColumns.ARCHOS_TRAKT_RESUME, 			//	13
        VideoStore.Video.VideoColumns.ARCHOS_VIDEO_STEREO,          //  14
        VideoStore.Video.VideoColumns.ARCHOS_VIDEO_DEFINITION,      //  15
        // scraper infos
        VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE,    // 16
        VideoStore.Video.VideoColumns.SCRAPER_TITLE,                // 17
        VideoStore.Video.VideoColumns.SCRAPER_COVER,                // 18
        VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE,            // 19
        VideoStore.Video.VideoColumns.SCRAPER_E_SEASON,             // 20
        VideoStore.Video.VideoColumns.SCRAPER_E_NAME,               // 21
        VideoStore.Video.VideoColumns.SCRAPER_M_ONLINE_ID,          // 22
        VideoStore.Video.VideoColumns.SCRAPER_S_ONLINE_ID,          // 23
        VideoStore.Video.VideoColumns.SCRAPER_E_ONLINE_ID,          // 24
        VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID,    // 25
    };

    public static final int IDX_ID =                     0;
    public static final int IDX_DATA =                   1;
    public static final int IDX_TITLE =                  2;
    public static final int IDX_DURATION =               3;
    public static final int IDX_BOOKMARK =               4;
    public static final int IDX_ARCHOS_BOOKMARK =        5;
    public static final int IDX_PLAYER_PARAMS =          6;
    public static final int IDX_SUBTITLE_DELAY =         7;
    public static final int IDX_SUBTITLE_RATIO =         8;
    public static final int IDX_NB_SUBTITLES =           9;
    public static final int IDX_LAST_TIME_PLAYED =      10;
    public static final int IDX_TRAKT_SEEN =            11;
    public static final int IDX_TRAKT_LIBRARY =         12;
    public static final int IDX_TRAKT_RESUME =			13;
    public static final int IDX_VIDEO_STEREO =          14;
    public static final int IDX_VIDEO_DEFINITION =      15;
    public static final int IDX_SCRAPER_TYPE =          16;
    public static final int IDX_SCRAPER_TITLE =         17;
    public static final int IDX_SCRAPER_COVER =         18;
    public static final int IDX_SCRAPER_EPISODE_NR =    19;
    public static final int IDX_SCRAPER_SEASON_NR =     20;
    public static final int IDX_SCRAPER_EPISODE_NAME =  21;
    public static final int IDX_SCRAPER_M_ONLINE_ID =   22;
    public static final int IDX_SCRAPER_S_ONLINE_ID =   23;
    public static final int IDX_SCRAPER_E_ONLINE_ID =   24;
    public static final int IDX_SCRAPER_ID =          25;
    public static VideoDbInfo fromId(ContentResolver cr, long id) {
        if (id >= 0) {
            String[] selectionArgs = { String.valueOf(id) };
            return fromArgs(cr, SELECTION_ID, selectionArgs);
        }
        return null;
    }

    public static VideoDbInfo fromUri(ContentResolver cr, Uri uri) {
        if (uri != null) {
            String[] selectionArgs = { uri.toString() };
            return fromArgs(cr, SELECTION_PATH, selectionArgs);
        }
        return null;
    }

    private static VideoDbInfo fromArgs(ContentResolver cr, String selection, String[] selectionArgs) {
        VideoDbInfo result = null;
        Cursor c = cr.query(URI, COLUMNS, selection, selectionArgs, null);
        if (c != null) {
            result = fromCursor(c,true);
            c.close();
        }
        return result;
    }

    public static VideoDbInfo fromParams(String path, int resume, int bookmark, int duration,
            int audioTrack, int subtitleTrack, int subtitleDelay, int subtitleRatio) {
        VideoDbInfo result = new VideoDbInfo(path);
        result.duration = duration;
        result.resume = resume;
        result.bookmark = bookmark;
        result.audioTrack = audioTrack;
        result.subtitleTrack = subtitleTrack;
        result.subtitleDelay = subtitleDelay;
        result.subtitleRatio = subtitleRatio;

        return result;
    }

    /** Note: does not close the cursor so it can be used with a Loader */
    public static VideoDbInfo fromCursor(Cursor c, boolean shouldMoveToNext) {
        VideoDbInfo result = null;
        boolean moveToNext = true;
        if(shouldMoveToNext)
        	moveToNext= c.moveToNext();
        if (c != null && moveToNext) {
            result = new VideoDbInfo(c.getString(IDX_DATA));
            result.id = c.getLong(IDX_ID);
            result.title = c.getString(IDX_TITLE);
            result.duration = c.getInt(IDX_DURATION);
            result.resume = c.getInt(IDX_BOOKMARK);
            result.bookmark = c.getInt(IDX_ARCHOS_BOOKMARK);
            int playerParams = c.getInt(IDX_PLAYER_PARAMS);
            result.audioTrack = VideoStore.paramsToAudioTrack(playerParams);
            result.subtitleTrack = VideoStore.paramsToSubtitleTrack(playerParams);
            result.subtitleDelay = c.getInt(IDX_SUBTITLE_DELAY);
            result.subtitleRatio = c.getInt(IDX_SUBTITLE_RATIO);
            result.nbSubtitles = c.getInt(IDX_NB_SUBTITLES);
            result.lastTimePlayed = c.getLong(IDX_LAST_TIME_PLAYED);
            result.traktSeen = c.getInt(IDX_TRAKT_SEEN);
            result.traktLibrary = c.getInt(IDX_TRAKT_LIBRARY);
            result.traktResume = c.getInt(IDX_TRAKT_RESUME);
            result.videoStereo = c.getInt(IDX_VIDEO_STEREO);
            result.videoDefinition = c.getInt(IDX_VIDEO_DEFINITION);
            int scraperType = c.getInt(IDX_SCRAPER_TYPE);
            result.scraperId = c.getInt(IDX_SCRAPER_ID);
            result.isScraped = scraperType > 0;
            if (result.isScraped)
                result.scrapeStatus = ScrapeStatus.OKAY;
            result.isShow = scraperType == BaseTags.TV_SHOW;
            result.scraperTitle = c.getString(IDX_SCRAPER_TITLE);
            result.scraperCover = c.getString(IDX_SCRAPER_COVER);
            result.scraperEpisodeNr = c.getInt(IDX_SCRAPER_EPISODE_NR);
            result.scraperSeasonNr = c.getInt(IDX_SCRAPER_SEASON_NR);
            result.scraperEpisodeName = c.getString(IDX_SCRAPER_EPISODE_NAME);
            result.scraperMovieId = c.getString(IDX_SCRAPER_M_ONLINE_ID);
            result.scraperShowId = c.getString(IDX_SCRAPER_S_ONLINE_ID);
            result.scraperEpisodeId = c.getString(IDX_SCRAPER_E_ONLINE_ID);
        }
        return result;
    }

    public void updateFromScraper(ScrapeDetailResult result) {
        if (result != null) {
            scrapeStatus = result.status;
            if (result.tag != null) {
                isScraped = true; // at least if you called tags.save()
                isShow = result.tag instanceof EpisodeTags; // there is not getType() or so..
                scraperTitle = result.tag.getTitle();       // for episodes, we may need to add show title
                File cover = result.tag.getCover(); // might work, not sure, covers was complicated
                if (cover != null)
                    scraperCover = cover.getPath();
                if (isShow) {
                    EpisodeTags etags = (EpisodeTags) result.tag;
                    scraperEpisodeNr = etags.getEpisode();
                    scraperSeasonNr = etags.getSeason();
                    // show title is inside showtags inside episode tags
                    ShowTags showTags = etags.getShowTags();
                    if (showTags != null) {
                        scraperTitle = showTags.getTitle();
                        scraperShowId = String.valueOf(showTags.getOnlineId());
                    }
                    scraperEpisodeName = etags.getTitle();
                    scraperEpisodeId = String.valueOf(etags.getOnlineId());
                } else {
                    scraperMovieId = String.valueOf(result.tag.getOnlineId());
                }
            }
        }
    }

    public ContentValues toContentValues() {
        ContentValues values = new ContentValues();

        if (bookmark != 1)
            values.put(VideoStore.Video.VideoColumns.ARCHOS_BOOKMARK, bookmark);

        if (resume != 1)
            values.put(VideoStore.Video.VideoColumns.BOOKMARK, resume);

        if (duration > 0)
            values.put(VideoStore.Video.VideoColumns.DURATION, duration);
        if (lastTimePlayed > 0)
            values.put(VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED, lastTimePlayed);

        if (subtitleTrack >= 0 && audioTrack >= 0) {
            int archosParams = VideoStore.paramsFromTracks(audioTrack, subtitleTrack);
            values.put(VideoStore.Video.VideoColumns.ARCHOS_PLAYER_PARAMS, archosParams);
        }

        if (subtitleDelay >= 0)
            values.put(VideoStore.Video.VideoColumns.ARCHOS_PLAYER_SUBTITLE_DELAY, subtitleDelay);

        if (subtitleRatio >= 0)
            values.put(VideoStore.Video.VideoColumns.ARCHOS_PLAYER_SUBTITLE_RATIO, subtitleRatio);

        if (nbSubtitles > 0)
            values.put(VideoStore.Video.VideoColumns.ARCHOS_NUMBER_OF_SUBTITLE_TRACKS, nbSubtitles);
        if (traktSeen != -1)
            values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN, traktSeen);
        if (traktLibrary != -1)
            values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_LIBRARY, traktLibrary);
        if (traktResume != -1)
            values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_LIBRARY, traktLibrary);

        if (videoStereo != -1)
            values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_RESUME, traktResume);

        if (videoDefinition != -1)
            values.put(VideoStore.Video.VideoColumns.ARCHOS_VIDEO_DEFINITION, videoDefinition);

        return values;
    }

    public VideoDbInfo(VideoDbInfo another) {
        id = another.id;
        uri = another.uri;
        title = another.title;
        duration = another.duration;
        resume = another.resume;
        bookmark = another.bookmark;
        audioTrack = another.audioTrack;
        subtitleTrack = another.subtitleTrack;
        subtitleDelay = another.subtitleDelay;
        subtitleRatio = another.subtitleRatio;
        lastTimePlayed = another.lastTimePlayed;
        nbSubtitles = another.nbSubtitles;
        isScraped = another.isScraped;
        scrapeStatus = another.scrapeStatus;
        isShow = another.isShow;
        scraperTitle = another.scraperTitle;
        scraperCover = another.scraperCover;
        scraperEpisodeNr = another.scraperEpisodeNr;
        scraperSeasonNr = another.scraperSeasonNr;
        scraperEpisodeName = another.scraperEpisodeName;
        scraperMovieId = another.scraperMovieId;
        scraperShowId = another.scraperShowId;
        scraperEpisodeId = another.scraperEpisodeId;
        traktSeen = another.traktSeen;
        traktLibrary = another.traktLibrary;
        traktResume = another.traktResume;
        videoStereo = another.videoStereo;
        videoDefinition = another.videoDefinition;
    }

    public VideoDbInfo(Parcel in) {
        id = in.readLong();
        uri = in.readParcelable(Uri.class.getClassLoader());
        title = in.readString();
        duration = in.readInt();
        resume = in.readInt();
        bookmark = in.readInt();
        audioTrack = in.readInt();
        subtitleTrack = in.readInt();
        subtitleDelay = in.readInt();
        subtitleRatio = in.readInt();
        lastTimePlayed = in.readLong();
        nbSubtitles = in.readInt();
        isScraped = in.readByte() != 0;
        int ordinal = in.readInt();
        scrapeStatus = ordinal >= 0 ? ScrapeStatus.values()[ordinal] : null;
        isShow = in.readByte() != 0;
        scraperTitle = in.readString();
        scraperCover = in.readString();
        scraperEpisodeNr = in.readInt();
        scraperSeasonNr = in.readInt();
        scraperEpisodeName = in.readString();
        scraperMovieId = in.readString();
        scraperShowId = in.readString();
        scraperEpisodeId = in.readString();
        traktSeen = in.readInt();
        traktLibrary = in.readInt();
        traktResume = in.readInt();
        videoStereo = in.readInt();
        videoDefinition = in.readInt();

    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeParcelable(uri, flags);
        dest.writeString(title);
        dest.writeInt(duration);
        dest.writeInt(resume);
        dest.writeInt(bookmark);
        dest.writeInt(audioTrack);
        dest.writeInt(subtitleTrack);
        dest.writeInt(subtitleDelay);
        dest.writeInt(subtitleRatio);
        dest.writeLong(lastTimePlayed);
        dest.writeInt(nbSubtitles);
        dest.writeByte((byte)(isScraped ? 1 : 0));
        dest.writeInt(scrapeStatus != null ? scrapeStatus.ordinal() : -1);
        dest.writeByte((byte)(isShow ? 1 : 0));
        dest.writeString(scraperTitle);
        dest.writeString(scraperCover);
        dest.writeInt(scraperEpisodeNr);
        dest.writeInt(scraperSeasonNr);
        dest.writeString(scraperEpisodeName);
        dest.writeString(scraperMovieId);
        dest.writeString(scraperShowId);
        dest.writeString(scraperEpisodeId);
        dest.writeInt(traktSeen);
        dest.writeInt(traktLibrary);
        dest.writeInt(traktResume);
        dest.writeInt(videoStereo);
        dest.writeInt(videoDefinition);
    }

    public static final Parcelable.Creator<VideoDbInfo> CREATOR = new Parcelable.Creator<VideoDbInfo>() {
        public VideoDbInfo createFromParcel(Parcel in) {
            return new VideoDbInfo(in);
        }
        public VideoDbInfo[] newArray(int size) {
            return new VideoDbInfo[size];
        }
    };
}
