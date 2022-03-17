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
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;
import android.util.LongSparseArray;

import com.archos.mediaprovider.video.ScraperStore;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediaprovider.video.VideoStore.MediaColumns;
import com.archos.mediaprovider.video.VideoStore.Video.VideoColumns;
import com.archos.mediascraper.ScraperImage.Type;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TagsFactory {
    final private static String TAG = "TagsFactory";
    final private static boolean DBG = false;

    private TagsFactory() {    }

    private static String getStringCol(Cursor cur, String col) {
        int id = cur.getColumnIndex(col);
        if(id < 0) 
            return null;
        return cur.getString(id);
    }
    private static String getStringCol(Cursor cur, int id) {
        if(id < 0)
            return null;
        return cur.getString(id);
    }

    private static int getIntCol(Cursor cur, String col) {
        int id = cur.getColumnIndex(col);
        if(id < 0) 
            return -1;
        return cur.getInt(id);
    }
    private static int getIntCol(Cursor cur, int id) {
        if(id < 0)
            return -1;
        return cur.getInt(id);
    }

    private static long getLongCol(Cursor cur, String col) {
        int id = cur.getColumnIndex(col);
        if(id < 0) 
            return -1L;
        return cur.getLong(id);
    }
    private static long getLongCol(Cursor cur, int id) {
        if(id < 0)
            return -1L;
        return cur.getLong(id);
    }

    private static float getFloatCol(Cursor cur, String col) {
        int id = cur.getColumnIndex(col);
        if(id < 0) 
            return -1f;
        return cur.getFloat(id);
    }
    private static float getFloatCol(Cursor cur, int id) {
        if(id < 0)
            return -1f;
        return cur.getFloat(id);
    }

    private static class Columns {
        public Columns(Cursor c) {
            id = getCol(c, BaseColumns._ID);
            data = getCol(c, MediaColumns.DATA);
            scraperId = getCol(c, VideoColumns.ARCHOS_MEDIA_SCRAPER_ID);
            scraperType = getCol(c, VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE);
            //title = getCol(c, MediaColumns.TITLE);
            titleMS = getCol(c, VideoColumns.SCRAPER_TITLE);
            titleE = getCol(c, VideoColumns.SCRAPER_E_NAME);
            ratingME = getCol(c, VideoColumns.SCRAPER_RATING);
            ratingS = getCol(c, VideoColumns.SCRAPER_S_RATING);
            yearM = getCol(c, VideoColumns.SCRAPER_M_YEAR);
            airedE = getCol(c, VideoColumns.SCRAPER_E_AIRED);
            premieredS = getCol(c, VideoColumns.SCRAPER_S_PREMIERED);
            onlineIdMS = getCol(c, VideoColumns.SCRAPER_ONLINE_ID);
            onlineIdE = getCol(c, VideoColumns.SCRAPER_E_ONLINE_ID);
            contentRatingMS = getCol(c, VideoColumns.SCRAPER_CONTENT_RATING);
            imdbIdMS = getCol(c, VideoColumns.SCRAPER_IMDB_ID);
            imdbIdE = getCol(c, VideoColumns.SCRAPER_E_IMDB_ID);
            plotME = getCol(c, VideoColumns.SCRAPER_PLOT);
            plotS = getCol(c, VideoColumns.SCRAPER_S_PLOT);
            coverME = getCol(c, VideoColumns.SCRAPER_COVER);
            coverS = getCol(c, VideoColumns.SCRAPER_S_COVER);
            backdropUrlMS = getCol(c, VideoColumns.SCRAPER_BACKDROP_URL);
            actorsMS = getCol(c, VideoColumns.SCRAPER_ACTORS);
            actorsE = getCol(c, VideoColumns.SCRAPER_E_ACTORS);
            directorsME = getCol(c, VideoColumns.SCRAPER_DIRECTORS);
            writersME = getCol(c, VideoColumns.SCRAPER_WRITERS);
            taglinesME = getCol(c, VideoColumns.SCRAPER_TAGLINES);
            // unused?
            directorsS = getCol(c, VideoColumns.SCRAPER_S_DIRECTORS);
            writersS = getCol(c, VideoColumns.SCRAPER_S_WRITERS);
            taglinesS = getCol(c, VideoColumns.SCRAPER_S_TAGLINES);
            seasonplotsS = getCol(c, VideoColumns.SCRAPER_S_SEASONPLOTS);
            genresMS = getCol(c, VideoColumns.SCRAPER_GENRES);
            studiosMS = getCol(c, VideoColumns.SCRAPER_STUDIOS);
            seasonE = getCol(c, VideoColumns.SCRAPER_E_SEASON);
            episodeE = getCol(c, VideoColumns.SCRAPER_E_EPISODE);
            showId = getCol(c, VideoColumns.SCRAPER_SHOW_ID);
            posterLFile = getCol(c, VideoColumns.SCRAPER_POSTER_LARGE_FILE);
            posterLUrl = getCol(c, VideoColumns.SCRAPER_POSTER_LARGE_URL);
            posterTFile = getCol(c, VideoColumns.SCRAPER_POSTER_THUMB_FILE);
            posterTUrl = getCol(c, VideoColumns.SCRAPER_POSTER_THUMB_URL);
            posterId = getCol(c, VideoColumns.SCRAPER_POSTER_ID);
            posterSLFile = getCol(c, VideoColumns.SCRAPER_S_POSTER_LARGE_FILE);
            posterSLUrl = getCol(c, VideoColumns.SCRAPER_S_POSTER_LARGE_URL);
            posterSTFile = getCol(c, VideoColumns.SCRAPER_S_POSTER_THUMB_FILE);
            posterSTUrl = getCol(c, VideoColumns.SCRAPER_S_POSTER_THUMB_URL);
            posterSId = getCol(c, VideoColumns.SCRAPER_S_POSTER_ID);
            backdropLFile = getCol(c, VideoColumns.SCRAPER_BACKDROP_LARGE_FILE);
            backdropLUrl = getCol(c, VideoColumns.SCRAPER_BACKDROP_LARGE_URL);
            backdropTFile = getCol(c, VideoColumns.SCRAPER_BACKDROP_THUMB_FILE);
            backdropTUrl = getCol(c, VideoColumns.SCRAPER_BACKDROP_THUMB_URL);
            backdropId = getCol(c, VideoColumns.SCRAPER_BACKDROP_ID);

            networklogoSLFile = getCol(c, VideoColumns.SCRAPER_S_NETWORKLOGO_FILE);
            networklogoSLUrl = getCol(c, VideoColumns.SCRAPER_S_NETWORKLOGO_URL);
            networklogoSTFile = getCol(c, VideoColumns.SCRAPER_S_NETWORKLOGO_FILE);
            networklogoSTUrl = getCol(c, VideoColumns.SCRAPER_S_NETWORKLOGO_URL);
            networklogoSId = getCol(c, VideoColumns.SCRAPER_S_NETWORKLOGO_ID);

            actorphotoSLFile = getCol(c, VideoColumns.SCRAPER_S_ACTORPHOTO_FILE);
            actorphotoSLUrl = getCol(c, VideoColumns.SCRAPER_S_ACTORPHOTO_URL);
            actorphotoSTFile = getCol(c, VideoColumns.SCRAPER_S_ACTORPHOTO_FILE);
            actorphotoSTUrl = getCol(c, VideoColumns.SCRAPER_S_ACTORPHOTO_URL);
            actorphotoSId = getCol(c, VideoColumns.SCRAPER_S_ACTORPHOTO_ID);

            actorphotoMLFile = getCol(c, VideoColumns.SCRAPER_M_ACTORPHOTO_FILE);
            actorphotoMLUrl = getCol(c, VideoColumns.SCRAPER_M_ACTORPHOTO_URL);
            actorphotoMTFile = getCol(c, VideoColumns.SCRAPER_M_ACTORPHOTO_FILE);
            actorphotoMTUrl = getCol(c, VideoColumns.SCRAPER_M_ACTORPHOTO_URL);
            actorphotoMId = getCol(c, VideoColumns.SCRAPER_M_ACTORPHOTO_ID);

            clearlogoSLFile = getCol(c, VideoColumns.SCRAPER_S_CLEARLOGO_FILE);
            clearlogoSLUrl = getCol(c, VideoColumns.SCRAPER_S_CLEARLOGO_URL);
            clearlogoSTFile = getCol(c, VideoColumns.SCRAPER_S_CLEARLOGO_FILE);
            clearlogoSTUrl = getCol(c, VideoColumns.SCRAPER_S_CLEARLOGO_URL);
            clearlogoSId = getCol(c, VideoColumns.SCRAPER_S_CLEARLOGO_ID);

            clearlogoMLFile = getCol(c, VideoColumns.SCRAPER_M_CLEARLOGO_FILE);
            clearlogoMLUrl = getCol(c, VideoColumns.SCRAPER_M_CLEARLOGO_URL);
            clearlogoMTFile = getCol(c, VideoColumns.SCRAPER_M_CLEARLOGO_FILE);
            clearlogoMTUrl = getCol(c, VideoColumns.SCRAPER_M_CLEARLOGO_URL);
            clearlogoMId = getCol(c, VideoColumns.SCRAPER_M_CLEARLOGO_ID);

            studiologoSLFile = getCol(c, VideoColumns.SCRAPER_S_STUDIOLOGO_FILE);
            studiologoSLUrl = getCol(c, VideoColumns.SCRAPER_S_STUDIOLOGO_URL);
            studiologoSTFile = getCol(c, VideoColumns.SCRAPER_S_STUDIOLOGO_FILE);
            studiologoSTUrl = getCol(c, VideoColumns.SCRAPER_S_STUDIOLOGO_URL);
            studiologoSId = getCol(c, VideoColumns.SCRAPER_S_STUDIOLOGO_ID);

            studiologoMLFile = getCol(c, VideoColumns.SCRAPER_M_STUDIOLOGO_FILE);
            studiologoMLUrl = getCol(c, VideoColumns.SCRAPER_M_STUDIOLOGO_URL);
            studiologoMTFile = getCol(c, VideoColumns.SCRAPER_M_STUDIOLOGO_FILE);
            studiologoMTUrl = getCol(c, VideoColumns.SCRAPER_M_STUDIOLOGO_URL);
            studiologoMId = getCol(c, VideoColumns.SCRAPER_M_STUDIOLOGO_ID);

            collectionId = getCol(c, VideoColumns.SCRAPER_C_ID);
            collectionName = getCol(c, VideoColumns.SCRAPER_C_NAME);
            collectionDescription = getCol(c, VideoColumns.SCRAPER_C_DESCRIPTION);
            posterCLFile = getCol(c, VideoColumns.SCRAPER_C_POSTER_LARGE_FILE);
            posterCLUrl = getCol(c, VideoColumns.SCRAPER_C_POSTER_LARGE_URL);
            posterCTFile = getCol(c, VideoColumns.SCRAPER_C_POSTER_THUMB_FILE);
            posterCTUrl = getCol(c, VideoColumns.SCRAPER_C_POSTER_THUMB_URL);
            backdropCLFile = getCol(c, VideoColumns.SCRAPER_C_BACKDROP_LARGE_FILE);
            backdropCLUrl = getCol(c, VideoColumns.SCRAPER_C_BACKDROP_LARGE_URL);
            backdropCTFile = getCol(c, VideoColumns.SCRAPER_C_BACKDROP_THUMB_FILE);
            backdropCTUrl = getCol(c, VideoColumns.SCRAPER_C_BACKDROP_THUMB_URL);
        }

        private static int getCol(Cursor c, String name) {
            if (c == null)
                return -1;
            int index = c.getColumnIndex(name);
            if (index < 0)
                Log.d(TAG, "Cursor misses column:" + name);
            return index;
        }

        public final int id;
        public final int data;
        public final int scraperId;
        public final int scraperType;
        //public final int title;
        public final int titleMS;
        public final int titleE;
        public final int ratingME;
        public final int ratingS;
        public final int onlineIdMS;
        public final int onlineIdE;
        public final int contentRatingMS;
        public final int imdbIdMS;
        public final int imdbIdE;
        public final int yearM;
        public final int airedE;
        public final int premieredS;
        public final int plotME;
        public final int plotS;
        public final int coverME;
        public final int coverS;
        public final int backdropUrlMS;
        public final int actorsMS;
        public final int actorsE;
        public final int directorsME;
        public final int directorsS;
        public final int writersME;
        public final int taglinesME;
        public final int writersS;
        public final int taglinesS;
        public final int seasonplotsS;
        public final int genresMS;
        public final int studiosMS;
        public final int seasonE;
        public final int episodeE;
        public final int showId;
        public final int backdropLFile;
        public final int backdropLUrl;
        public final int backdropTFile;
        public final int backdropTUrl;
        public final int backdropId;

        public final int networklogoSLFile;
        public final int networklogoSLUrl;
        public final int networklogoSTFile;
        public final int networklogoSTUrl;
        public final int networklogoSId;

        public final int actorphotoSLFile;
        public final int actorphotoSLUrl;
        public final int actorphotoSTFile;
        public final int actorphotoSTUrl;
        public final int actorphotoSId;

        public final int actorphotoMLFile;
        public final int actorphotoMLUrl;
        public final int actorphotoMTFile;
        public final int actorphotoMTUrl;
        public final int actorphotoMId;

        public final int clearlogoSLFile;
        public final int clearlogoSLUrl;
        public final int clearlogoSTFile;
        public final int clearlogoSTUrl;
        public final int clearlogoSId;

        public final int clearlogoMLFile;
        public final int clearlogoMLUrl;
        public final int clearlogoMTFile;
        public final int clearlogoMTUrl;
        public final int clearlogoMId;

        public final int studiologoSLFile;
        public final int studiologoSLUrl;
        public final int studiologoSTFile;
        public final int studiologoSTUrl;
        public final int studiologoSId;

        public final int studiologoMLFile;
        public final int studiologoMLUrl;
        public final int studiologoMTFile;
        public final int studiologoMTUrl;
        public final int studiologoMId;

        public final int posterLFile;
        public final int posterLUrl;
        public final int posterTFile;
        public final int posterTUrl;
        public final int posterId;
        public final int posterSLFile;
        public final int posterSLUrl;
        public final int posterSTFile;
        public final int posterSTUrl;
        public final int posterSId;
        public final int collectionId;
        public final int collectionName;
        public final int collectionDescription;
        public final int posterCLFile;
        public final int posterCLUrl;
        public final int posterCTFile;
        public final int posterCTUrl;
        public final int backdropCLFile;
        public final int backdropCLUrl;
        public final int backdropCTFile;
        public final int backdropCTUrl;
    }

    public static final String[] VIDEO_COLUMNS = {
        BaseColumns._ID,
        MediaColumns.DATA,
        VideoColumns.ARCHOS_MEDIA_SCRAPER_ID,
        VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE,
        VideoColumns.SCRAPER_TITLE,
        VideoColumns.SCRAPER_E_NAME,
        VideoColumns.SCRAPER_RATING,
        VideoColumns.SCRAPER_S_RATING,
        VideoColumns.SCRAPER_M_YEAR,
        VideoColumns.SCRAPER_E_AIRED,
        VideoColumns.SCRAPER_S_PREMIERED,
        VideoColumns.SCRAPER_ONLINE_ID,
        VideoColumns.SCRAPER_E_ONLINE_ID,
        VideoColumns.SCRAPER_CONTENT_RATING,
        VideoColumns.SCRAPER_IMDB_ID,
        VideoColumns.SCRAPER_E_IMDB_ID,
        VideoColumns.SCRAPER_PLOT,
        VideoColumns.SCRAPER_S_PLOT,
        VideoColumns.SCRAPER_COVER,
        VideoColumns.SCRAPER_S_COVER,
        VideoColumns.SCRAPER_BACKDROP_URL,
        VideoColumns.SCRAPER_ACTORS,
        VideoColumns.SCRAPER_E_ACTORS,
        VideoColumns.SCRAPER_DIRECTORS,
        VideoColumns.SCRAPER_WRITERS,
            VideoColumns.SCRAPER_TAGLINES,
            VideoColumns.SCRAPER_SEASONPLOTS,
        VideoColumns.SCRAPER_S_DIRECTORS,
        VideoColumns.SCRAPER_S_WRITERS,
            VideoColumns.SCRAPER_S_TAGLINES,
            VideoColumns.SCRAPER_S_SEASONPLOTS,
        VideoColumns.SCRAPER_GENRES,
        VideoColumns.SCRAPER_STUDIOS,
        VideoColumns.SCRAPER_E_SEASON,
        VideoColumns.SCRAPER_E_EPISODE,
        VideoColumns.SCRAPER_SHOW_ID,
        VideoColumns.SCRAPER_POSTER_LARGE_FILE,
        VideoColumns.SCRAPER_POSTER_LARGE_URL,
        VideoColumns.SCRAPER_POSTER_THUMB_FILE,
        VideoColumns.SCRAPER_POSTER_THUMB_URL,
        VideoColumns.SCRAPER_POSTER_ID,
        VideoColumns.SCRAPER_S_POSTER_LARGE_FILE,
        VideoColumns.SCRAPER_S_POSTER_LARGE_URL,
        VideoColumns.SCRAPER_S_POSTER_THUMB_FILE,
        VideoColumns.SCRAPER_S_POSTER_THUMB_URL,
        VideoColumns.SCRAPER_S_POSTER_ID,
        VideoColumns.SCRAPER_BACKDROP_LARGE_FILE,
        VideoColumns.SCRAPER_BACKDROP_LARGE_URL,
        VideoColumns.SCRAPER_BACKDROP_THUMB_FILE,
        VideoColumns.SCRAPER_BACKDROP_THUMB_URL,
        VideoColumns.SCRAPER_BACKDROP_ID,
        VideoColumns.SCRAPER_C_ID,
        VideoColumns.SCRAPER_C_NAME,
        VideoColumns.SCRAPER_C_DESCRIPTION,
        VideoColumns.SCRAPER_C_POSTER_LARGE_FILE,
        VideoColumns.SCRAPER_C_POSTER_LARGE_URL,
        VideoColumns.SCRAPER_C_POSTER_THUMB_FILE,
        VideoColumns.SCRAPER_C_POSTER_THUMB_URL,
        VideoColumns.SCRAPER_C_BACKDROP_LARGE_FILE,
        VideoColumns.SCRAPER_C_BACKDROP_LARGE_URL,
        VideoColumns.SCRAPER_C_BACKDROP_THUMB_FILE,
        VideoColumns.SCRAPER_C_BACKDROP_THUMB_URL
    };

    /**
     * use: {@link #VIDEO_COLUMNS} or those below
     * Won't break if you leave out some of them, basically that info
     * won't be set in the resulting Tags.
     * <p> The result has one item per row in the cursor. Either a {@link MovieTags}
     * or {@link ShowTags} if the row has scraper infos or <b>null</b>.
     * <p> Result is never <b>null</b>. List of size() 0 instead.
    <pre>
    BaseColumns._ID,
    MediaColumns.DATA,
    VideoColumns.ARCHOS_MEDIA_SCRAPER_ID,
    VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE,
    VideoColumns.SCRAPER_TITLE,
    VideoColumns.SCRAPER_E_NAME,
    VideoColumns.SCRAPER_RATING,
    VideoColumns.SCRAPER_S_RATING,
    VideoColumns.SCRAPER_M_YEAR,
    VideoColumns.SCRAPER_E_AIRED,
    VideoColumns.SCRAPER_S_PREMIERED,
    VideoColumns.SCRAPER_ONLINE_ID,
    VideoColumns.SCRAPER_E_ONLINE_ID,
    VideoColumns.SCRAPER_CONTENT_RATING,
    VideoColumns.SCRAPER_IMDB_ID,
    VideoColumns.SCRAPER_E_IMDB_ID,
    VideoColumns.SCRAPER_PLOT,
    VideoColumns.SCRAPER_S_PLOT,
    VideoColumns.SCRAPER_COVER,
    VideoColumns.SCRAPER_S_COVER,
    VideoColumns.SCRAPER_BACKDROP_URL,
    VideoColumns.SCRAPER_ACTORS,
    VideoColumns.SCRAPER_E_ACTORS,
    VideoColumns.SCRAPER_DIRECTORS,
    VideoColumns.SCRAPER_WRITERS,
     VideoColumns.SCRAPER_TAGLINES,
    VideoColumns.SCRAPER_S_DIRECTORS,
    VideoColumns.SCRAPER_S_WRITERS,
     VideoColumns.SCRAPER_S_TAGLINES,
    VideoColumns.SCRAPER_GENRES,
    VideoColumns.SCRAPER_STUDIOS,
    VideoColumns.SCRAPER_E_SEASON,
    VideoColumns.SCRAPER_E_EPISODE,
    VideoColumns.SCRAPER_SHOW_ID,
    VideoColumns.SCRAPER_POSTER_LARGE_FILE,
    VideoColumns.SCRAPER_POSTER_LARGE_URL,
    VideoColumns.SCRAPER_POSTER_THUMB_FILE,
    VideoColumns.SCRAPER_POSTER_THUMB_URL,
    VideoColumns.SCRAPER_POSTER_ID,
    VideoColumns.SCRAPER_S_POSTER_LARGE_FILE,
    VideoColumns.SCRAPER_S_POSTER_LARGE_URL,
    VideoColumns.SCRAPER_S_POSTER_THUMB_FILE,
    VideoColumns.SCRAPER_S_POSTER_THUMB_URL,
    VideoColumns.SCRAPER_S_POSTER_ID,
    VideoColumns.SCRAPER_BACKDROP_LARGE_FILE,
    VideoColumns.SCRAPER_BACKDROP_LARGE_URL,
    VideoColumns.SCRAPER_BACKDROP_THUMB_FILE,
    VideoColumns.SCRAPER_BACKDROP_THUMB_URL,
    VideoColumns.SCRAPER_BACKDROP_ID
    </pre>
    */

	public static List<BaseTags> buildTagsFromVideoCursor(Cursor cur) {
        int count = cur == null ? 0 : cur.getCount();
        List<BaseTags> resultList = new ArrayList<BaseTags>(count);
        LongSparseArray<ShowTags> tags = new LongSparseArray<ShowTags>();

        if (count == 0)
            return resultList;
        int initialPosition = cur.getPosition();
        cur.moveToPosition(-1);
        Columns cols = new Columns(cur);
        while (cur.moveToNext()) {
            long videoId = getLongCol(cur, cols.id);
            String data = getStringCol(cur, cols.data);
            long scraperId = getLongCol(cur, cols.scraperId);
            int scraperType = getIntCol(cur, cols.scraperType);
            String titleMS = getStringCol(cur, cols.titleMS);
            float ratingME = getFloatCol(cur, cols.ratingME);
            long onlineIdMS = getLongCol(cur, cols.onlineIdMS);
            String contentRatingMS = getStringCol(cur, cols.contentRatingMS);
            String imdbIdMS = getStringCol(cur, cols.imdbIdMS);
            String plotME = getStringCol(cur, cols.plotME);
            String coverME = getStringCol(cur, cols.coverME);
            String backdropUrlMS = getStringCol(cur, cols.backdropUrlMS);
            String actorsMS = getStringCol(cur, cols.actorsMS);
            String directorsME = getStringCol(cur, cols.directorsME);
            String writersME = getStringCol(cur, cols.writersME);
            String taglinesME = getStringCol(cur, cols.taglinesME);
            String genresMS = getStringCol(cur, cols.genresMS);
            String studiosMS = getStringCol(cur, cols.studiosMS);
            long backdropId = getLongCol(cur, cols.backdropId);
            String backdropLFile = getStringCol(cur, cols.backdropLFile);
            String backdropLUrl = getStringCol(cur, cols.backdropLUrl);
            String backdropTFile = getStringCol(cur, cols.backdropTFile);
            String backdropTUrl = getStringCol(cur, cols.backdropTUrl);

            long networklogoSId = getLongCol(cur, cols.networklogoSId);
            String networklogoSLFile = getStringCol(cur, cols.networklogoSLFile);
            String networklogoSLUrl = getStringCol(cur, cols.networklogoSLUrl);
            String networklogoSTFile = getStringCol(cur, cols.networklogoSTFile);
            String networklogoSTUrl = getStringCol(cur, cols.networklogoSTUrl);

            long actorphotoSId = getLongCol(cur, cols.actorphotoSId);
            String actorphotoSLFile = getStringCol(cur, cols.actorphotoSLFile);
            String actorphotoSLUrl = getStringCol(cur, cols.actorphotoSLUrl);
            String actorphotoSTFile = getStringCol(cur, cols.actorphotoSTFile);
            String actorphotoSTUrl = getStringCol(cur, cols.actorphotoSTUrl);

            long actorphotoMId = getLongCol(cur, cols.actorphotoMId);
            String actorphotoMLFile = getStringCol(cur, cols.actorphotoMLFile);
            String actorphotoMLUrl = getStringCol(cur, cols.actorphotoMLUrl);
            String actorphotoMTFile = getStringCol(cur, cols.actorphotoMTFile);
            String actorphotoMTUrl = getStringCol(cur, cols.actorphotoMTUrl);

            long clearlogoSId = getLongCol(cur, cols.clearlogoSId);
            String clearlogoSLFile = getStringCol(cur, cols.clearlogoSLFile);
            String clearlogoSLUrl = getStringCol(cur, cols.clearlogoSLUrl);
            String clearlogoSTFile = getStringCol(cur, cols.clearlogoSTFile);
            String clearlogoSTUrl = getStringCol(cur, cols.clearlogoSTUrl);

            long clearlogoMId = getLongCol(cur, cols.clearlogoMId);
            String clearlogoMLFile = getStringCol(cur, cols.clearlogoMLFile);
            String clearlogoMLUrl = getStringCol(cur, cols.clearlogoMLUrl);
            String clearlogoMTFile = getStringCol(cur, cols.clearlogoMTFile);
            String clearlogoMTUrl = getStringCol(cur, cols.clearlogoMTUrl);

            long studiologoSId = getLongCol(cur, cols.studiologoSId);
            String studiologoSLFile = getStringCol(cur, cols.studiologoSLFile);
            String studiologoSLUrl = getStringCol(cur, cols.studiologoSLUrl);
            String studiologoSTFile = getStringCol(cur, cols.studiologoSTFile);
            String studiologoSTUrl = getStringCol(cur, cols.studiologoSTUrl);

            long studiologoMId = getLongCol(cur, cols.studiologoMId);
            String studiologoMLFile = getStringCol(cur, cols.studiologoMLFile);
            String studiologoMLUrl = getStringCol(cur, cols.studiologoMLUrl);
            String studiologoMTFile = getStringCol(cur, cols.studiologoMTFile);
            String studiologoMTUrl = getStringCol(cur, cols.studiologoMTUrl);

            long posterId = getLongCol(cur, cols.posterId);
            String posterLFile = getStringCol(cur, cols.posterLFile);
            String posterLUrl = getStringCol(cur, cols.posterLUrl);
            String posterTFile = getStringCol(cur, cols.posterTFile);
            String posterTUrl = getStringCol(cur, cols.posterTUrl);
            int collectionId = getIntCol(cur, cols.collectionId);
            String collectionName = getStringCol(cur, cols.collectionName);
            String collectionDescription = getStringCol(cur, cols.collectionDescription);
            String backdropCLFile = getStringCol(cur, cols.backdropCLFile);
            String backdropCLUrl = getStringCol(cur, cols.backdropCLUrl);
            String backdropCTFile = getStringCol(cur, cols.backdropCTFile);
            String backdropCTUrl = getStringCol(cur, cols.backdropCTUrl);
            String posterCLFile = getStringCol(cur, cols.posterCLFile);
            String posterCLUrl = getStringCol(cur, cols.posterCLUrl);
            String posterCTFile = getStringCol(cur, cols.posterCTFile);
            String posterCTUrl = getStringCol(cur, cols.posterCTUrl);
            if (scraperType == ScraperStore.SCRAPER_TYPE_MOVIE) {
                MovieTags tag = new MovieTags();
                tag.setId(scraperId);
                tag.setVideoId(videoId);
                if (data != null)
                    tag.setFile(Uri.parse(data));
                tag.setTitle(titleMS);

                if(ratingME >= 0)
                    tag.setRating(ratingME);
                tag.setOnlineId(onlineIdMS);
                tag.setImdbId(imdbIdMS);
                tag.setContentRating(contentRatingMS);
                int year = getIntCol(cur, cols.yearM);
                if(year >= 0)
                    tag.setYear(year);
                tag.setPlot(plotME);
                tag.setActorsFormatted(actorsMS);
                tag.setDirectorsFormatted(directorsME);
                tag.setWritersFormatted(writersME);
                tag.setTaglinesFormatted(taglinesME);
                tag.setGenresFormatted(genresMS);
                tag.setStudiosFormatted(studiosMS);

                if(coverME != null && posterId <= 0)
                    tag.setCover(new File(coverME));
                if (backdropUrlMS != null && backdropId <= 0) {
                    Log.w(TAG, "No Backdrop due to missing paths in database");
                }

                if (backdropId > 0) {
                    ScraperImage image = new ScraperImage(ScraperImage.Type.MOVIE_BACKDROP, data);
                    image.setLargeFile(backdropLFile);
                    image.setLargeUrl(backdropLUrl);
                    image.setThumbFile(backdropTFile);
                    image.setThumbUrl(backdropTUrl);
                    image.setId(backdropId);
                    image.setRemoteId(scraperId);
                    tag.setBackdrops(image.asList());
                }

                if (actorphotoMId > 0) {
                    ScraperImage image = new ScraperImage(ScraperImage.Type.MOVIE_ACTORPHOTO, data);
                    image.setLargeFile(actorphotoMLFile);
                    image.setLargeUrl(actorphotoMLUrl);
                    image.setThumbFile(actorphotoMTFile);
                    image.setThumbUrl(actorphotoMTUrl);
                    image.setId(actorphotoMId);
                    image.setRemoteId(scraperId);
                    tag.setActorPhotos(image.asList());
                }

                if (studiologoMId > 0) {
                    ScraperImage image = new ScraperImage(ScraperImage.Type.MOVIE_STUDIOLOGO, data);
                    image.setLargeFile(studiologoMLFile);
                    image.setLargeUrl(studiologoMLUrl);
                    image.setThumbFile(studiologoMTFile);
                    image.setThumbUrl(studiologoMTUrl);
                    image.setId(studiologoMId);
                    image.setRemoteId(scraperId);
                    tag.setStudioLogos(image.asList());
                }

                if (clearlogoMId > 0) {
                    ScraperImage image = new ScraperImage(ScraperImage.Type.MOVIE_CLEARLOGO, data);
                    image.setLargeFile(clearlogoMLFile);
                    image.setLargeUrl(clearlogoMLUrl);
                    image.setThumbFile(clearlogoMTFile);
                    image.setThumbUrl(clearlogoMTUrl);
                    image.setId(clearlogoMId);
                    image.setRemoteId(scraperId);
                    tag.setClearLogos(image.asList());
                }

                if (posterId > 0) {
                    ScraperImage image = new ScraperImage(ScraperImage.Type.MOVIE_POSTER, data);
                    image.setLargeFile(posterLFile);
                    image.setLargeUrl(posterLUrl);
                    image.setThumbFile(posterTFile);
                    image.setThumbUrl(posterTUrl);
                    image.setId(posterId);
                    image.setRemoteId(scraperId);
                    tag.setPosters(image.asList());
                }

                if (collectionId > 0) {
                    tag.setCollectionId(collectionId);
                    tag.setCollectionName(collectionName);
                    tag.setCollectionDescription(collectionDescription);
                    tag.setCollectionPosterLargeFile(posterCLFile);
                    tag.setCollectionPosterLargeUrl(posterCLUrl);
                    tag.setCollectionPosterThumbFile(posterCTFile);
                    tag.setCollectionPosterThumbUrl(posterCTUrl);
                    tag.setCollectionBackdropLargeFile(backdropCLFile);
                    tag.setCollectionBackdropLargeUrl(backdropCLUrl);
                    tag.setCollectionBackdropThumbFile(backdropCTFile);
                    tag.setCollectionBackdropThumbUrl(backdropCTUrl);
                }

                resultList.add(tag);
            } else if (scraperType == ScraperStore.SCRAPER_TYPE_SHOW) {
                EpisodeTags epTag = new EpisodeTags();
                epTag.setId(scraperId);
                epTag.setVideoId(videoId);
                if (data != null)
                    epTag.setFile(Uri.parse(data));
                String titleE = getStringCol(cur, cols.titleE);
                epTag.setTitle(titleE);

                if(ratingME >= 0)
                    epTag.setRating(ratingME);
                String imdbIdE = getStringCol(cur, cols.imdbIdE);
                epTag.setImdbId(imdbIdE);
                long onlineIdE = getLongCol(cur, cols.onlineIdE);
                epTag.setOnlineId(onlineIdE);
                long airedE = getLongCol(cur, cols.airedE);
                if(airedE >= 0)
                    epTag.setAired(airedE);
                epTag.setPlot(plotME);
                int season = getIntCol(cur, cols.seasonE);
                epTag.setSeason(season);
                int episode = getIntCol(cur, cols.episodeE);
                epTag.setEpisode(episode);
                long showId = getLongCol(cur, cols.showId);
                epTag.setShowId(showId);
                String actorsE = getStringCol(cur, cols.actorsE);
                epTag.setActorsFormatted(actorsE);
                epTag.setDirectorsFormatted(directorsME);
                epTag.setWritersFormatted(writersME);
                epTag.setTaglinesFormatted(taglinesME);

                if(coverME != null && posterId <= 0)
                    epTag.setCover(new File(coverME));

                if (posterId >0) {
                    ScraperImage image = new ScraperImage(ScraperImage.Type.EPISODE_POSTER, titleMS);
                    image.setLargeFile(posterLFile);
                    image.setLargeUrl(posterLUrl);
                    image.setThumbFile(posterTFile);
                    image.setThumbUrl(posterTUrl);
                    image.setId(posterId);
                    image.setRemoteId(showId);
                    image.setSeason(season);
                    epTag.setPosters(image.asList());
                }

                ShowTags sTag = tags.get(showId);
                if(sTag == null) {
                    sTag = new ShowTags();
                    tags.put(showId, sTag);

                    sTag.setId(showId);
                    sTag.setTitle(titleMS);

                    float ratingS = getFloatCol(cur, cols.ratingS);
                    if(ratingS >= 0)
                        sTag.setRating(ratingS);
                    sTag.setOnlineId(onlineIdMS);
                    sTag.setImdbId(imdbIdMS);
                    sTag.setContentRating(contentRatingMS);
                    long premieredS = getLongCol(cur, cols.premieredS);
                    if(premieredS >= 0)
                        sTag.setPremiered(premieredS);
                    String plotS = getStringCol(cur, cols.plotS);
                    sTag.setPlot(plotS);
                    sTag.setActorsFormatted(actorsMS);
                    String directorsS =getStringCol(cur, cols.directorsS);
                    String writersS =getStringCol(cur, cols.writersS);
                    String taglinesS =getStringCol(cur, cols.taglinesS);
                    String seasonplotsS =getStringCol(cur, cols.seasonplotsS);
                    sTag.setDirectorsFormatted(directorsS);
                    sTag.setSeasonPlotsFormatted(seasonplotsS);
                    sTag.setGenresFormatted(genresMS);
                    sTag.setStudiosFormatted(studiosMS);

                    String coverS =getStringCol(cur, cols.coverS);
                    long posterSId = getLongCol(cur, cols.posterSId);
                    String posterSLFile = getStringCol(cur, cols.posterSLFile);
                    String posterSLUrl = getStringCol(cur, cols.posterSLUrl);
                    String posterSTFile = getStringCol(cur, cols.posterSTFile);
                    String posterSTUrl = getStringCol(cur, cols.posterSTUrl);

                    if(coverS != null && posterSId <= 0)
                        sTag.setCover(new File(coverS));
                    if (backdropUrlMS != null && backdropId <= 0) {
                        Log.w(TAG, "No Backdrop due to missing paths in database");
                    }

                    if (backdropId > 0) {
                        ScraperImage image = new ScraperImage(ScraperImage.Type.SHOW_BACKDROP, titleMS);
                        image.setLargeFile(backdropLFile);
                        image.setLargeUrl(backdropLUrl);
                        image.setThumbFile(backdropTFile);
                        image.setThumbUrl(backdropTUrl);
                        image.setId(backdropId);
                        image.setRemoteId(showId);
                        sTag.setBackdrops(image.asList());
                    }

                    if (networklogoSId > 0) {
                        ScraperImage image = new ScraperImage(ScraperImage.Type.SHOW_NETWORK, titleMS);
                        image.setLargeFile(networklogoSLFile);
                        image.setLargeUrl(networklogoSLUrl);
                        image.setThumbFile(networklogoSTFile);
                        image.setThumbUrl(networklogoSTUrl);
                        image.setId(networklogoSId);
                        image.setRemoteId(showId);
                        sTag.setNetworkLogos(image.asList());
                    }

                    if (actorphotoSId > 0) {
                        ScraperImage image = new ScraperImage(ScraperImage.Type.SHOW_ACTOR_PHOTO, titleMS);
                        image.setLargeFile(actorphotoSLFile);
                        image.setLargeUrl(actorphotoSLUrl);
                        image.setThumbFile(actorphotoSTFile);
                        image.setThumbUrl(actorphotoSTUrl);
                        image.setId(actorphotoSId);
                        image.setRemoteId(showId);
                        sTag.setActorPhotos(image.asList());
                    }

                    if (clearlogoSId > 0) {
                        ScraperImage image = new ScraperImage(ScraperImage.Type.SHOW_TITLE_CLEARLOGO, titleMS);
                        image.setLargeFile(clearlogoSLFile);
                        image.setLargeUrl(clearlogoSLUrl);
                        image.setThumbFile(clearlogoSTFile);
                        image.setThumbUrl(clearlogoSTUrl);
                        image.setId(clearlogoSId);
                        image.setRemoteId(showId);
                        sTag.setClearLogos(image.asList());
                    }

                    if (studiologoSId > 0) {
                        ScraperImage image = new ScraperImage(ScraperImage.Type.SHOW_STUDIOLOGO, titleMS);
                        image.setLargeFile(studiologoSLFile);
                        image.setLargeUrl(studiologoSLUrl);
                        image.setThumbFile(studiologoSTFile);
                        image.setThumbUrl(studiologoSTUrl);
                        image.setId(studiologoSId);
                        image.setRemoteId(showId);
                        sTag.setStudioLogos(image.asList());
                    }

                    if (posterSId >0) {
                        ScraperImage image = new ScraperImage(ScraperImage.Type.SHOW_POSTER, titleMS);
                        image.setLargeFile(posterSLFile);
                        image.setLargeUrl(posterSLUrl);
                        image.setThumbFile(posterSTFile);
                        image.setThumbUrl(posterSTUrl);
                        image.setId(posterSId);
                        image.setRemoteId(showId);
                        sTag.setPosters(image.asList());
                    }

                }
                epTag.setShowTags(sTag);
                resultList.add(epTag);
            } else {
                Log.d(TAG, "Not a Show / Movie");
                resultList.add(null);
            }
        }
        // leave cursor the way it was.
        cur.moveToPosition(initialPosition);
        return resultList;
    }

    public static List<MovieTags> buildMovieFromCursor(Cursor cur) {
        Hashtable<Long, MovieTags> tags = new Hashtable<Long, MovieTags>();
        if(!cur.moveToFirst())
            return null;
        do {
            Long id = Long.valueOf(getLongCol(cur, ScraperStore.Movie.ID));
            String name = getStringCol(cur, ScraperStore.Movie.NAME);
            float rating = getFloatCol(cur, ScraperStore.Movie.RATING);
            int year = getIntCol(cur, ScraperStore.Movie.YEAR);
            String plot = getStringCol(cur, ScraperStore.Movie.PLOT);
            String cover = getStringCol(cur, ScraperStore.Movie.COVER);
            String actorName = getStringCol(cur, ScraperStore.Movie.Actor.NAME);
            String role = getStringCol(cur, ScraperStore.Movie.Actor.ROLE);
            String director = getStringCol(cur, ScraperStore.Movie.Director.NAME);
            String writer = getStringCol(cur, ScraperStore.Movie.Writer.NAME);
            String tagline = getStringCol(cur, ScraperStore.Movie.Tagline.NAME);
            String genre = getStringCol(cur, ScraperStore.Movie.Genre.NAME);
            String studio = getStringCol(cur, ScraperStore.Movie.Studio.NAME);

            String backdropUrl = getStringCol(cur, ScraperStore.Movie.BACKDROP_URL);
            String backdropPath = getStringCol(cur, ScraperStore.Movie.BACKDROP);

            String actorphotoMUrl = getStringCol(cur, ScraperStore.Movie.ACTORPHOTO_URL);
            String actorphotoMPath = getStringCol(cur, ScraperStore.Movie.ACTORPHOTO);

            String studiologoMUrl = getStringCol(cur, ScraperStore.Movie.STUDIOLOGO_URL);
            String studiologoMPath = getStringCol(cur, ScraperStore.Movie.STUDIOLOGO);

            String clearlogoMUrl = getStringCol(cur, ScraperStore.Movie.CLEARLOGO_URL);
            String clearlogoMPath = getStringCol(cur, ScraperStore.Movie.CLEARLOGO);

            Integer collectionId = getIntCol(cur, ScraperStore.Movie.COLLECTION_ID);

            MovieTags tag = tags.get(id);
            if(tag == null) {
                tag = new MovieTags();
                tags.put(id, tag);
            }
            tag.setId(id.longValue());
            tag.setTitle(name);
            if(rating >= 0)
                tag.setRating(rating);
            if(year >= 0)
                tag.setYear(year);
            tag.setPlot(plot);

            if(cover != null)
                tag.setCover(new File(cover));

            tag.addActorIfAbsent(actorName, role);
            tag.addDirectorIfAbsent(director);
            tag.addWriterIfAbsent(writer);
            tag.addTaglineIfAbsent(tagline);
            tag.addGenreIfAbsent(genre);
            tag.addStudioIfAbsent(studio);

            if(backdropUrl != null && backdropPath != null) {
                ScraperImage image = new ScraperImage(Type.MOVIE_BACKDROP, null);
                image.setLargeUrl(backdropUrl);
                image.setLargeFile(backdropPath);
                tag.setBackdrops(image.asList());
            }

            if(actorphotoMUrl != null && actorphotoMPath != null) {
                ScraperImage image = new ScraperImage(Type.MOVIE_ACTORPHOTO, null);
                image.setLargeUrl(actorphotoMUrl);
                image.setLargeFile(actorphotoMPath);
                tag.setActorPhotos(image.asList());
            }

            if(studiologoMUrl != null && studiologoMPath != null) {
                ScraperImage image = new ScraperImage(Type.MOVIE_STUDIOLOGO, null);
                image.setLargeUrl(studiologoMUrl);
                image.setLargeFile(studiologoMPath);
                tag.setStudioLogos(image.asList());
            }

            if(clearlogoMUrl != null && clearlogoMPath != null) {
                ScraperImage image = new ScraperImage(Type.MOVIE_CLEARLOGO, null);
                image.setLargeUrl(clearlogoMUrl);
                image.setLargeFile(clearlogoMPath);
                tag.setClearLogos(image.asList());
            }

            if (collectionId > 0)
                tag.setCollectionId(collectionId);

        } while(cur.moveToNext());
        return new ArrayList<MovieTags>(tags.values());
    }

    public static List<ShowTags> buildShowFromCursor(Cursor cur) {
        Hashtable<Long, ShowTags> tags = new Hashtable<Long, ShowTags>();
        if(DBG) Log.d(TAG, "Building ShowTags from Cursor");
        if(!cur.moveToFirst())
            return null;
        do {
            Long id = Long.valueOf(getLongCol(cur, ScraperStore.Show.ID));
            String name = getStringCol(cur, ScraperStore.Show.NAME);
            float rating = getFloatCol(cur, ScraperStore.Show.RATING);
            long premiered = getLongCol(cur, ScraperStore.Show.PREMIERED);
            String plot = getStringCol(cur, ScraperStore.Show.PLOT);
            String cover = getStringCol(cur, ScraperStore.Show.COVER);
            String actorName = getStringCol(cur, ScraperStore.Show.Actor.NAME);
            String role = getStringCol(cur, ScraperStore.Show.Actor.ROLE);
            String director = getStringCol(cur, ScraperStore.Show.Director.NAME);
            String writer = getStringCol(cur, ScraperStore.Show.Writer.NAME);
            String tagline = getStringCol(cur, ScraperStore.Show.Tagline.NAME);
            String seasonplot = getStringCol(cur, ScraperStore.Show.SeasonPlot.NAME);
            String genre = getStringCol(cur, ScraperStore.Show.Genre.NAME);
            String studio = getStringCol(cur, ScraperStore.Show.Studio.NAME);

            String backdropUrl = getStringCol(cur, ScraperStore.Show.BACKDROP_URL);
            String backdropPath = getStringCol(cur, ScraperStore.Show.BACKDROP);

            String networklogoUrl = getStringCol(cur, ScraperStore.Show.NETWORKLOGO_URL);
            String networklogoPath = getStringCol(cur, ScraperStore.Show.NETWORKLOGO);

            String actorphotoSUrl = getStringCol(cur, ScraperStore.Show.ACTORPHOTO_URL);
            String actorphotoSPath = getStringCol(cur, ScraperStore.Show.ACTORPHOTO);

            String clearlogoSUrl = getStringCol(cur, ScraperStore.Show.CLEARLOGO_URL);
            String clearlogoSPath = getStringCol(cur, ScraperStore.Show.CLEARLOGO);

            String studiologoSUrl = getStringCol(cur, ScraperStore.Show.STUDIOLOGO_URL);
            String studiologoSPath = getStringCol(cur, ScraperStore.Show.STUDIOLOGO);

            ShowTags tag = tags.get(id);
            if(tag == null) {
                tag = new ShowTags();
                tags.put(id, tag);
            }
            tag.setTitle(name);
            tag.setId(id.longValue());
            if(rating >= 0)
                tag.setRating(rating);
            if(premiered >= 0)
                tag.setPremiered(premiered);
            tag.setPlot(plot);

            if(cover != null)
                tag.setCover(new File(cover));

            tag.addActorIfAbsent(actorName, role);
            tag.addDirectorIfAbsent(director);
            tag.addWriterIfAbsent(writer);
            tag.addTaglineIfAbsent(tagline);
            tag.addSeasonPlotIfAbsent(seasonplot);
            tag.addGenreIfAbsent(genre);
            tag.addStudioIfAbsent(studio);

            if(backdropUrl != null && backdropPath != null) {
                ScraperImage image = new ScraperImage(Type.SHOW_BACKDROP, null);
                image.setLargeUrl(backdropUrl);
                image.setLargeFile(backdropPath);
                tag.setBackdrops(image.asList());
            }

            if(networklogoUrl != null && networklogoPath != null) {
                ScraperImage image = new ScraperImage(Type.SHOW_NETWORK, null);
                image.setLargeUrl(networklogoUrl);
                image.setLargeFile(networklogoPath);
                tag.setNetworkLogos(image.asList());
            }
            if(actorphotoSUrl != null && actorphotoSPath != null) {
                ScraperImage image = new ScraperImage(Type.SHOW_ACTOR_PHOTO, null);
                image.setLargeUrl(actorphotoSUrl);
                image.setLargeFile(actorphotoSPath);
                tag.setActorPhotos(image.asList());
            }
            if(clearlogoSUrl != null && clearlogoSPath != null) {
                ScraperImage image = new ScraperImage(Type.SHOW_TITLE_CLEARLOGO, null);
                image.setLargeUrl(clearlogoSUrl);
                image.setLargeFile(clearlogoSPath);
                tag.setClearLogos(image.asList());
            }
            if(studiologoSUrl != null && studiologoSPath != null) {
                ScraperImage image = new ScraperImage(Type.SHOW_STUDIOLOGO, null);
                image.setLargeUrl(studiologoSUrl);
                image.setLargeFile(studiologoSPath);
                tag.setStudioLogos(image.asList());
            }

        } while(cur.moveToNext());
        return new ArrayList<ShowTags>(tags.values());
    }

    public static List<EpisodeTags> buildEpisodeFromCursor(Cursor cur) {
        Hashtable<Long, EpisodeTags> tags = new Hashtable<Long, EpisodeTags>();
        if(DBG) Log.d(TAG, "Building MovieTags from Cursor");
        if(!cur.moveToFirst())
            return null;
        do {
            Long id = Long.valueOf(getLongCol(cur, ScraperStore.Episode.ID));
            String name = getStringCol(cur, ScraperStore.Episode.NAME);
            float rating = getFloatCol(cur, ScraperStore.Episode.RATING);
            long aired = getLongCol(cur, ScraperStore.Episode.AIRED);
            String plot = getStringCol(cur, ScraperStore.Episode.PLOT);
            int season = getIntCol(cur, ScraperStore.Episode.SEASON);
            int number = getIntCol(cur, ScraperStore.Episode.NUMBER);
            long show = getLongCol(cur, ScraperStore.Episode.SHOW);
            String actorName = getStringCol(cur, ScraperStore.Episode.Actor.NAME);
            String role = getStringCol(cur, ScraperStore.Episode.Actor.ROLE);
            String director = getStringCol(cur, ScraperStore.Episode.Director.NAME);
            String writer = getStringCol(cur, ScraperStore.Episode.Writer.NAME);
            String tagline = getStringCol(cur, ScraperStore.Episode.Tagline.NAME);
            String cover = getStringCol(cur, ScraperStore.Episode.COVER);

            EpisodeTags tag = tags.get(id);
            if(tag == null) {
                tag = new EpisodeTags();
                tags.put(id, tag);
            }
            tag.setId(id.longValue());
            tag.setTitle(name);
            if(rating >= 0)
                tag.setRating(rating);
            if(aired >= 0)
                tag.setAired(aired);
            tag.setPlot(plot);
            tag.setSeason(season);
            tag.setEpisode(number);
            tag.setShowId(show);
            tag.addActorIfAbsent(actorName, role);
            tag.addDirectorIfAbsent(director);
            tag.addWriterIfAbsent(writer);
            tag.addTaglineIfAbsent(tagline);

            if(cover != null)
                tag.setCover(new File(cover));

        } while(cur.moveToNext());
        return new ArrayList<EpisodeTags>(tags.values());
    }

    public static MovieTags buildMovieTags(Context context, long movieId) {
        if (DBG) Log.d(TAG, "buildMovieTags: movieId=" + movieId);
        MovieTags result = null;
        ContentResolver cr = context.getContentResolver();
        Cursor c = cr.query(
                VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
                new String[] {
                        VideoColumns.SCRAPER_M_NAME,            // 0
                        VideoColumns.SCRAPER_M_YEAR,            // 1
                        VideoColumns.SCRAPER_M_RATING,          // 2
                        VideoColumns.SCRAPER_M_CONTENT_RATING,  // 3
                        VideoColumns.SCRAPER_M_PLOT,            // 4
                        VideoColumns.SCRAPER_M_ONLINE_ID,       // 5
                        VideoColumns.SCRAPER_M_IMDB_ID,         // 6
                        VideoColumns.SCRAPER_POSTER_ID,         // 7
                        VideoColumns.SCRAPER_BACKDROP_ID,       // 8
                        VideoColumns.BOOKMARK,                  // 9
                        VideoColumns.ARCHOS_BOOKMARK,           // 10
                        VideoColumns.DURATION,                  // 11
                        VideoColumns.ARCHOS_LAST_TIME_PLAYED,   // 12
                        MediaColumns.DATA,                      // 13
                        BaseColumns._ID,                        // 14
                        VideoColumns.SCRAPER_C_ID,                  // 15
                        VideoColumns.SCRAPER_C_NAME,                // 16
                        VideoColumns.SCRAPER_C_DESCRIPTION,         // 17
                        VideoColumns.SCRAPER_C_POSTER_LARGE_FILE,   // 18
                        VideoColumns.SCRAPER_C_POSTER_LARGE_URL,    // 19
                        VideoColumns.SCRAPER_C_POSTER_THUMB_FILE,   // 20
                        VideoColumns.SCRAPER_C_POSTER_THUMB_URL,    // 21
                        VideoColumns.SCRAPER_C_BACKDROP_LARGE_FILE, // 22
                        VideoColumns.SCRAPER_C_BACKDROP_LARGE_URL,  // 23
                        VideoColumns.SCRAPER_C_BACKDROP_THUMB_FILE, // 24
                        VideoColumns.SCRAPER_C_BACKDROP_THUMB_URL,   // 25
                        VideoColumns.SCRAPER_M_ACTORPHOTO_ID,       // 26
                        VideoColumns.SCRAPER_M_STUDIOLOGO_ID,       // 27
                        VideoColumns.SCRAPER_M_CLEARLOGO_ID       // 28
                },
                VideoStore.Video.VideoColumns.SCRAPER_MOVIE_ID + "=?",
                new String[] { String.valueOf(movieId) },
                null);
        long posterId = -1;
        long backdropId = -1;
        long actorphotoId = -1;
        long studiologoId = -1;
        long clearlogoId = -1;
        if (c != null) {
            if (c.moveToFirst()) {
                result = new MovieTags();
                result.setId(movieId);
                result.setTitle(c.getString(0));
                result.setYear(c.getInt(1));
                result.setRating(c.getFloat(2));
                result.setContentRating(c.getString(3));
                result.setPlot(c.getString(4));
                result.setOnlineId(c.getLong(5));
                result.setImdbId(c.getString(6));
                posterId = c.getLong(7);
                backdropId = c.getLong(8);
                result.setResume(c.getLong(9));
                result.setBookmark(c.getLong(10));
                result.setRuntime(c.getLong(11), TimeUnit.MILLISECONDS);
                result.setLastPlayed(c.getLong(12), TimeUnit.SECONDS);
                result.setFile(Uri.parse(c.getString(13)));
                result.setVideoId(c.getLong(14));
                result.setCollectionId(c.getInt(15));
                result.setCollectionName(c.getString(16));
                result.setCollectionDescription(c.getString(17));
                result.setCollectionPosterLargeFile(c.getString(18));
                result.setCollectionPosterLargeUrl(c.getString(19));
                result.setCollectionPosterThumbFile(c.getString(20));
                result.setCollectionPosterThumbUrl(c.getString(21));
                result.setCollectionBackdropLargeFile(c.getString(22));
                result.setCollectionBackdropLargeUrl(c.getString(23));
                result.setCollectionBackdropThumbFile(c.getString(24));
                result.setCollectionBackdropThumbUrl(c.getString(25));
                actorphotoId = c.getLong(26);
                studiologoId = c.getLong(27);
                clearlogoId = c.getLong(28);
            }
            c.close();
        }
        if (result != null) {
            // Actors
            c = cr.query(
                    ContentUris.withAppendedId(ScraperStore.Actor.URI.MOVIE, movieId),
                    new String[] {
                            ScraperStore.Movie.Actor.NAME,            // 0
                            ScraperStore.Movie.Actor.ROLE,            // 1
                    }, null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    String actor = c.getString(0);
                    String role = c.getString(1);
                    result.addActorIfAbsent(actor, role);
                }
                c.close();
            }
            // Directors
            c = cr.query(
                    ContentUris.withAppendedId(ScraperStore.Director.URI.MOVIE, movieId),
                    new String[] {
                            ScraperStore.Movie.Director.NAME,            // 0
                    }, null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    result.addDirectorIfAbsent(c.getString(0));
                }
                c.close();
            }
            // Writers
            c = cr.query(
                    ContentUris.withAppendedId(ScraperStore.Writer.URI.MOVIE, movieId),
                    new String[] {
                            ScraperStore.Movie.Writer.NAME,            // 0
                    }, null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    result.addWriterIfAbsent(c.getString(0));
                }
                c.close();
            }
            // Taglines
            c = cr.query(
                    ContentUris.withAppendedId(ScraperStore.Tagline.URI.MOVIE, movieId),
                    new String[] {
                            ScraperStore.Movie.Tagline.NAME,            // 0
                    }, null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    result.addTaglineIfAbsent(c.getString(0));
                }
                c.close();
            }
            // Genres
            c = cr.query(
                    ContentUris.withAppendedId(ScraperStore.Genre.URI.MOVIE, movieId),
                    new String[] {
                            ScraperStore.Movie.Genre.NAME,            // 0
                    }, null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    result.addGenreIfAbsent(c.getString(0));
                }
                c.close();
            }
            // Studios
            c = cr.query(
                    ContentUris.withAppendedId(ScraperStore.Studio.URI.MOVIE, movieId),
                    new String[] {
                            ScraperStore.Movie.Studio.NAME,            // 0
                    }, null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    result.addStudioIfAbsent(c.getString(0));
                }
                c.close();
            }
            // posters
            List<ScraperImage> allPostersInDb = result.getAllPostersInDb(context);
            if (allPostersInDb == null)
                allPostersInDb = Collections.emptyList();
            LinkedList<ScraperImage> allPostersSorted = new LinkedList<ScraperImage>();
            // find the selected poster and make it first in the list
            for (ScraperImage image : allPostersInDb) {
                if (image.getId() == posterId) {
                    allPostersSorted.addFirst(image);
                } else {
                    allPostersSorted.addLast(image);
                }
            }
            result.setPosters(allPostersSorted);

            // backdrops
            List<ScraperImage> allBackdropsInDb = result.getAllBackdropsInDb(context);
            if (allBackdropsInDb == null)
                allBackdropsInDb = Collections.emptyList();
            LinkedList<ScraperImage> allBackdropsSorted = new LinkedList<ScraperImage>();
            // find the selected backdrop and make it first in the list
            for (ScraperImage image : allBackdropsInDb) {
                if (image.getId() == backdropId)
                    allBackdropsSorted.addFirst(image);
                else
                    allBackdropsSorted.addLast(image);
            }
            result.setBackdrops(allBackdropsSorted);

            // actorphotos
            List<ScraperImage> allActorPhotosInDb = result.getAllActorPhotosInDb(context);
            if (allActorPhotosInDb == null)
                allActorPhotosInDb = Collections.emptyList();
            LinkedList<ScraperImage> allActorPhotosSorted = new LinkedList<ScraperImage>();
            // find the selected actorphoto and make it first in the list
            for (ScraperImage image : allActorPhotosInDb) {
                if (image.getId() == actorphotoId)
                    allActorPhotosSorted.addFirst(image);
                else
                    allActorPhotosSorted.addLast(image);
            }
            result.setActorPhotos(allActorPhotosSorted);

            // studiologos
            List<ScraperImage> allStudioLogosInDb = result.getAllStudioLogosInDb(context);
            if (allStudioLogosInDb == null)
                allStudioLogosInDb = Collections.emptyList();
            LinkedList<ScraperImage> allStudioLogosSorted = new LinkedList<ScraperImage>();
            // find the selected studiologo and make it first in the list
            for (ScraperImage image : allStudioLogosInDb) {
                if (image.getId() == studiologoId)
                    allStudioLogosSorted.addFirst(image);
                else
                    allStudioLogosSorted.addLast(image);
            }
            result.setStudioLogos(allStudioLogosSorted);

            // clearlogos
            List<ScraperImage> allClearLogosInDb = result.getAllClearLogosInDb(context);
            if (allClearLogosInDb == null)
                allClearLogosInDb = Collections.emptyList();
            LinkedList<ScraperImage> allClearLogosSorted = new LinkedList<ScraperImage>();
            // find the selected clearlogo and make it first in the list
            for (ScraperImage image : allClearLogosInDb) {
                if (image.getId() == clearlogoId)
                    allClearLogosSorted.addFirst(image);
                else
                    allClearLogosSorted.addLast(image);
            }
            result.setClearLogos(allClearLogosSorted);

        }
        return result;
    }

    public static EpisodeTags buildEpisodeTags(Context context, long episodeId) {
        EpisodeTags result = null;
        ContentResolver cr = context.getContentResolver();
        Cursor c = cr.query(
                VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
                new String[] {
                        VideoColumns.SCRAPER_E_NAME,            // 0
                        VideoColumns.SCRAPER_E_AIRED,           // 1
                        VideoColumns.SCRAPER_E_RATING,          // 2
                        VideoColumns.SCRAPER_S_CONTENT_RATING,  // 3
                        VideoColumns.SCRAPER_E_PLOT,            // 4
                        VideoColumns.SCRAPER_E_ONLINE_ID,       // 5
                        VideoColumns.SCRAPER_E_IMDB_ID,         // 6
                        VideoColumns.SCRAPER_POSTER_ID,         // 7
                        VideoColumns.SCRAPER_BACKDROP_ID,       // 8
                        VideoColumns.BOOKMARK,                  // 9
                        VideoColumns.ARCHOS_BOOKMARK,           // 10
                        VideoColumns.DURATION,                  // 11
                        VideoColumns.ARCHOS_LAST_TIME_PLAYED,   // 12
                        MediaColumns.DATA,                      // 13
                        BaseColumns._ID,                        // 14
                        VideoColumns.SCRAPER_E_EPISODE,         // 15
                        VideoColumns.SCRAPER_E_SEASON,          // 16
                        VideoColumns.SCRAPER_SHOW_ID,           // 17
                },
                VideoStore.Video.VideoColumns.SCRAPER_EPISODE_ID + "=?",
                new String[] { String.valueOf(episodeId) },
                null);
        long posterId = -1;
        long backdropId = -1;
        if (c != null) {
            if (c.moveToFirst()) {
                result = new EpisodeTags();
                result.setId(episodeId);
                result.setTitle(c.getString(0));
                result.setAired(c.getLong(1));
                result.setRating(c.getFloat(2));
                result.setContentRating(c.getString(3));
                result.setPlot(c.getString(4));
                result.setOnlineId(c.getLong(5));
                result.setImdbId(c.getString(6));
                posterId = c.getLong(7);
                backdropId = c.getLong(8);
                result.setResume(c.getLong(9));
                result.setBookmark(c.getLong(10));
                result.setRuntime(c.getLong(11), TimeUnit.MILLISECONDS);
                result.setLastPlayed(c.getLong(12), TimeUnit.SECONDS);
                result.setFile(Uri.parse(c.getString(13)));
                result.setVideoId(c.getLong(14));
                result.setEpisode(c.getInt(15));
                result.setSeason(c.getInt(16));
                result.setShowId(c.getLong(17));
            }
            c.close();
        }
        if (result != null) {
            // Actors
            c = cr.query(
                    ContentUris.withAppendedId(ScraperStore.Actor.URI.EPISODE, episodeId),
                    new String[] {
                            ScraperStore.Episode.Actor.NAME,            // 0
                            ScraperStore.Episode.Actor.ROLE,            // 1
                    }, null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    String actor = c.getString(0);
                    String role = c.getString(1);
                    result.addActorIfAbsent(actor, role);
                }
                c.close();
            }
            // Directors
            c = cr.query(
                    ContentUris.withAppendedId(ScraperStore.Director.URI.EPISODE, episodeId),
                    new String[] {
                            ScraperStore.Episode.Director.NAME,            // 0
                    }, null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    result.addDirectorIfAbsent(c.getString(0));
                }
                c.close();
            }
            // Writers
            c = cr.query(
                    ContentUris.withAppendedId(ScraperStore.Writer.URI.EPISODE, episodeId),
                    new String[] {
                            ScraperStore.Episode.Writer.NAME,            // 0
                    }, null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    result.addWriterIfAbsent(c.getString(0));
                }
                c.close();
            }
            // Taglines
            c = cr.query(
                    ContentUris.withAppendedId(ScraperStore.Tagline.URI.EPISODE, episodeId),
                    new String[] {
                            ScraperStore.Episode.Tagline.NAME,            // 0
                    }, null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    result.addTaglineIfAbsent(c.getString(0));
                }
                c.close();
            }
            ShowTags showTags = buildShowTags(context, result.getShowId());
            result.setShowTags(showTags);
            // posters -- need ShowTags
            List<ScraperImage> allPostersInDb = result.getAllPostersInDb(context);
            if (allPostersInDb == null)
                allPostersInDb = Collections.emptyList();
            LinkedList<ScraperImage> allPostersSorted = new LinkedList<ScraperImage>();
            // find the selected poster and make it first in the list
            for (ScraperImage image : allPostersInDb) {
                if (image.getId() == posterId)
                    allPostersSorted.addFirst(image);
                else
                    allPostersSorted.addLast(image);
            }
            result.setPosters(allPostersSorted);
        }
        return result;
    }

    public static ShowTags buildShowTags(Context context, long showId) {
        ContentResolver cr = context.getContentResolver();
        Cursor c = cr.query(
                ContentUris.withAppendedId(ScraperStore.Show.URI.ID, showId),
                new String[] {
                        ScraperStore.Show.NAME,                 // 0
                        ScraperStore.Show.PREMIERED,            // 1
                        ScraperStore.Show.RATING,               // 2
                        ScraperStore.Show.CONTENT_RATING,       // 3
                        ScraperStore.Show.PLOT,                 // 4
                        ScraperStore.Show.ONLINE_ID,            // 5
                        ScraperStore.Show.IMDB_ID,              // 6
                        ScraperStore.Show.POSTER_ID,            // 7
                        ScraperStore.Show.BACKDROP_ID,          // 8
                        ScraperStore.Show.NETWORKLOGO_ID,          // 9
                        ScraperStore.Show.ACTORPHOTO_ID,          // 10
                        ScraperStore.Show.CLEARLOGO_ID,          // 11
                        ScraperStore.Show.STUDIOLOGO_ID,          // 12
                }, null, null, null);
        return buildShowTagsFromCursor(context, c, showId);
    }

    public static ShowTags buildShowTagsOnlineId(Context context, long onlineId) {
        long showId = -1;
        ContentResolver cr = context.getContentResolver();
        Cursor c = cr.query(
                ContentUris.withAppendedId(ScraperStore.Show.URI.ONLINE_ID, onlineId),
                new String[] {
                        ScraperStore.Show.NAME,                 // 0
                        ScraperStore.Show.PREMIERED,            // 1
                        ScraperStore.Show.RATING,               // 2
                        ScraperStore.Show.CONTENT_RATING,       // 3
                        ScraperStore.Show.PLOT,                 // 4
                        ScraperStore.Show.ONLINE_ID,            // 5
                        ScraperStore.Show.IMDB_ID,              // 6
                        ScraperStore.Show.POSTER_ID,            // 7
                        ScraperStore.Show.BACKDROP_ID,          // 8
                        ScraperStore.Show.NETWORKLOGO_ID,          // 8
                        ScraperStore.Show.ACTORPHOTO_ID,          // 9
                        ScraperStore.Show.CLEARLOGO_ID,          // 10
                        ScraperStore.Show.STUDIOLOGO_ID,          // 11
                        ScraperStore.Show.ID,                   // 12
                }, null, null, null);
        if (c != null && c.moveToFirst())
            showId = c.getLong(9);
        return buildShowTagsFromCursor(context, c, showId);
    }

    public static ShowTags buildShowTagsFromCursor(Context context, Cursor c, long showId) {
        ShowTags showTags = null;
        long posterId = -1;
        long backdropId = -1;
        long networklogoId = -1;
        long actorphotoId = -1;
        long clearlogoId = -1;
        long studiologoId = -1;
        if (c != null) {
            if (c.moveToFirst()) {
                showTags = new ShowTags();
                showTags.setId(showId);
                showTags.setTitle(c.getString(0));
                showTags.setPremiered(c.getLong(1));
                showTags.setRating(c.getFloat(2));
                showTags.setContentRating(c.getString(3));
                showTags.setPlot(c.getString(4));
                showTags.setOnlineId(c.getLong(5));
                showTags.setImdbId(c.getString(6));
                posterId = c.getLong(7);
                backdropId = c.getLong(8);
                networklogoId = c.getLong(9);
                actorphotoId = c.getLong(10);
                clearlogoId = c.getLong(11);
                studiologoId = c.getLong(12);
            }
            c.close();
        }
        ContentResolver cr = context.getContentResolver();
        if (showTags != null) {
            // Actors
            c = cr.query(
                    ContentUris.withAppendedId(ScraperStore.Actor.URI.SHOW, showId),
                    new String[] {
                            ScraperStore.Show.Actor.NAME,            // 0
                            ScraperStore.Show.Actor.ROLE,            // 1
                    }, null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    String actor = c.getString(0);
                    String role = c.getString(1);
                    showTags.addActorIfAbsent(actor, role);
                }
                c.close();
            }
            // Directors
            c = cr.query(
                    ContentUris.withAppendedId(ScraperStore.Director.URI.SHOW, showId),
                    new String[] {
                            ScraperStore.Show.Director.NAME,            // 0
                    }, null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    showTags.addDirectorIfAbsent(c.getString(0));
                }
                c.close();
            }
            // Writers
            c = cr.query(
                    ContentUris.withAppendedId(ScraperStore.Writer.URI.SHOW, showId),
                    new String[] {
                            ScraperStore.Show.Writer.NAME,            // 0
                    }, null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    showTags.addWriterIfAbsent(c.getString(0));
                }
                c.close();
            }
            // Taglines
            c = cr.query(
                    ContentUris.withAppendedId(ScraperStore.Tagline.URI.SHOW, showId),
                    new String[] {
                            ScraperStore.Show.Tagline.NAME,            // 0
                    }, null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    showTags.addTaglineIfAbsent(c.getString(0));
                }
                c.close();
            }
            // SeasonPlots
            c = cr.query(
                    ContentUris.withAppendedId(ScraperStore.SeasonPlot.URI.SHOW, showId),
                    new String[] {
                            ScraperStore.Show.SeasonPlot.NAME,            // 0
                    }, null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    showTags.addSeasonPlotIfAbsent(c.getString(0));
                }
                c.close();
            }
            // Genres
            c = cr.query(
                    ContentUris.withAppendedId(ScraperStore.Genre.URI.SHOW, showId),
                    new String[] {
                            ScraperStore.Show.Genre.NAME,            // 0
                    }, null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    showTags.addGenreIfAbsent(c.getString(0));
                }
                c.close();
            }
            // Studios
            c = cr.query(
                    ContentUris.withAppendedId(ScraperStore.Studio.URI.SHOW, showId),
                    new String[] {
                            ScraperStore.Show.Studio.NAME,            // 0
                    }, null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    showTags.addStudioIfAbsent(c.getString(0));
                }
                c.close();
            }
            // this fetches all posters, not just show posters
            List<ScraperImage> allPostersInDb = showTags.getAllPostersInDb(context, -1, true);
            if (allPostersInDb == null)
                allPostersInDb = Collections.emptyList();
            LinkedList<ScraperImage> allPostersSorted = new LinkedList<ScraperImage>();
            // find the selected poster and make it first in the list
            for (ScraperImage image : allPostersInDb) {
                if (image.getId() == posterId)
                    allPostersSorted.addFirst(image);
                else
                    allPostersSorted.addLast(image);
            }
            showTags.setPosters(allPostersSorted);

            // backdrops
            List<ScraperImage> allBackdropsInDb = showTags.getAllBackdropsInDb(context);
            if (allBackdropsInDb == null)
                allBackdropsInDb = Collections.emptyList();
            LinkedList<ScraperImage> allBackdropsSorted = new LinkedList<ScraperImage>();
            // find the selected backdrop and make it first in the list
            for (ScraperImage image : allBackdropsInDb) {
                if (image.getId() == backdropId)
                    allBackdropsSorted.addFirst(image);
                else
                    allBackdropsSorted.addLast(image);
            }
            showTags.setBackdrops(allBackdropsSorted);


            // networklogos
            List<ScraperImage> allNetworkLogosInDb = showTags.getAllNetworkLogosInDb(context);
            if (allNetworkLogosInDb == null)
                allNetworkLogosInDb = Collections.emptyList();
            LinkedList<ScraperImage> allNetworkLogosSorted = new LinkedList<ScraperImage>();
            // find the selected networklogo and make it first in the list
            for (ScraperImage image : allNetworkLogosInDb) {
                if (image.getId() == networklogoId)
                    allNetworkLogosSorted.addFirst(image);
                else
                    allNetworkLogosSorted.addLast(image);
            }
            showTags.setNetworkLogos(allNetworkLogosSorted);

            // actorphotos
            List<ScraperImage> allActorPhotosInDb = showTags.getAllActorPhotosInDb(context);
            if (allActorPhotosInDb == null)
                allActorPhotosInDb = Collections.emptyList();
            LinkedList<ScraperImage> allActorPhotosSorted = new LinkedList<ScraperImage>();
            // find the selected actorphoto and make it first in the list
            for (ScraperImage image : allActorPhotosInDb) {
                if (image.getId() == actorphotoId)
                    allActorPhotosSorted.addFirst(image);
                else
                    allActorPhotosSorted.addLast(image);
            }
            showTags.setActorPhotos(allActorPhotosSorted);

            // clearlogos
            List<ScraperImage> allClearLogosInDb = showTags.getAllClearLogosInDb(context);
            if (allClearLogosInDb == null)
                allClearLogosInDb = Collections.emptyList();
            LinkedList<ScraperImage> allClearLogosSorted = new LinkedList<ScraperImage>();
            // find the selected clearlogo and make it first in the list
            for (ScraperImage image : allClearLogosInDb) {
                if (image.getId() == clearlogoId)
                    allClearLogosSorted.addFirst(image);
                else
                    allClearLogosSorted.addLast(image);
            }
            showTags.setClearLogos(allClearLogosSorted);

            // studiologos
            List<ScraperImage> allStudioLogosInDb = showTags.getAllStudioLogosInDb(context);
            if (allStudioLogosInDb == null)
                allStudioLogosInDb = Collections.emptyList();
            LinkedList<ScraperImage> allStudioLogosSorted = new LinkedList<ScraperImage>();
            // find the selected studiologo and make it first in the list
            for (ScraperImage image : allStudioLogosInDb) {
                if (image.getId() == studiologoId)
                    allStudioLogosSorted.addFirst(image);
                else
                    allStudioLogosSorted.addLast(image);
            }
            showTags.setStudioLogos(allStudioLogosSorted);
        }
        return showTags;
    }

}
