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
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;
import android.util.LongSparseArray;

import com.archos.filecorelibrary.MetaFile;
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
            // unused?
            directorsS = getCol(c, VideoColumns.SCRAPER_S_DIRECTORS);
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
        VideoColumns.SCRAPER_S_DIRECTORS,
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
    VideoColumns.SCRAPER_S_DIRECTORS,
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
            String genresMS = getStringCol(cur, cols.genresMS);
            String studiosMS = getStringCol(cur, cols.studiosMS);
            long backdropId = getLongCol(cur, cols.backdropId);
            String backdropLFile = getStringCol(cur, cols.backdropLFile);
            String backdropLUrl = getStringCol(cur, cols.backdropLUrl);
            String backdropTFile = getStringCol(cur, cols.backdropTFile);
            String backdropTUrl = getStringCol(cur, cols.backdropTUrl);
            long posterId = getLongCol(cur, cols.posterId);
            String posterLFile = getStringCol(cur, cols.posterLFile);
            String posterLUrl = getStringCol(cur, cols.posterLUrl);
            String posterTFile = getStringCol(cur, cols.posterTFile);
            String posterTUrl = getStringCol(cur, cols.posterTUrl);
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
                    sTag.setDirectorsFormatted(directorsS);
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
        if(DBG) Log.d(TAG, "Building MovieTags from Cursor");
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
            String genre = getStringCol(cur, ScraperStore.Movie.Genre.NAME);
            String studio = getStringCol(cur, ScraperStore.Movie.Studio.NAME);

            String backdropUrl = getStringCol(cur, ScraperStore.Movie.BACKDROP_URL);
            String backdropPath = getStringCol(cur, ScraperStore.Movie.BACKDROP);

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
            tag.addGenreIfAbsent(genre);
            tag.addStudioIfAbsent(studio);

            if(backdropUrl != null && backdropPath != null) {
                ScraperImage image = new ScraperImage(Type.MOVIE_BACKDROP, null);
                image.setLargeUrl(backdropUrl);
                image.setLargeFile(backdropPath);
                tag.setBackdrops(image.asList());
            }

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
            String genre = getStringCol(cur, ScraperStore.Show.Genre.NAME);
            String studio = getStringCol(cur, ScraperStore.Show.Studio.NAME);

            String backdropUrl = getStringCol(cur, ScraperStore.Show.BACKDROP_URL);
            String backdropPath = getStringCol(cur, ScraperStore.Show.BACKDROP);

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
            tag.addGenreIfAbsent(genre);
            tag.addStudioIfAbsent(studio);

            if(backdropUrl != null && backdropPath != null) {
                ScraperImage image = new ScraperImage(Type.SHOW_BACKDROP, null);
                image.setLargeUrl(backdropUrl);
                image.setLargeFile(backdropPath);
                tag.setBackdrops(image.asList());
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

            if(cover != null)
                tag.setCover(new File(cover));

        } while(cur.moveToNext());
        return new ArrayList<EpisodeTags>(tags.values());
    }

    public static MovieTags buildMovieTags(Context context, long movieId) {
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
                },
                VideoStore.Video.VideoColumns.SCRAPER_MOVIE_ID + "=?",
                new String[] { String.valueOf(movieId) },
                null);
        long posterId = -1;
        long backdropId = -1;
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
            }
            c.close();
            c = null;
        }
        if (result != null) {
            // Actors
            c = cr.query(
                    ContentUris.withAppendedId(ScraperStore.Actor.URI.MOVIE, movieId),
                    new String[] {
                            ScraperStore.Movie.Actor.NAME,            // 0
                            ScraperStore.Movie.Actor.ROLE,            // 1
                    },
                    null,
                    null,
                    null);
            if (c != null) {
                while (c.moveToNext()) {
                    String actor = c.getString(0);
                    String role = c.getString(1);
                    result.addActorIfAbsent(actor, role);
                }
                c.close();
                c = null;
            }
            // Directors
            c = cr.query(
                    ContentUris.withAppendedId(ScraperStore.Director.URI.MOVIE, movieId),
                    new String[] {
                            ScraperStore.Movie.Director.NAME,            // 0
                    },
                    null,
                    null,
                    null);
            if (c != null) {
                while (c.moveToNext()) {
                    result.addDirectorIfAbsent(c.getString(0));
                }
                c.close();
                c = null;
            }
            // Genres
            c = cr.query(
                    ContentUris.withAppendedId(ScraperStore.Genre.URI.MOVIE, movieId),
                    new String[] {
                            ScraperStore.Movie.Genre.NAME,            // 0
                    },
                    null,
                    null,
                    null);
            if (c != null) {
                while (c.moveToNext()) {
                    result.addGenreIfAbsent(c.getString(0));
                }
                c.close();
                c = null;
            }
            // Studios
            c = cr.query(
                    ContentUris.withAppendedId(ScraperStore.Studio.URI.MOVIE, movieId),
                    new String[] {
                            ScraperStore.Movie.Studio.NAME,            // 0
                    },
                    null,
                    null,
                    null);
            if (c != null) {
                while (c.moveToNext()) {
                    result.addStudioIfAbsent(c.getString(0));
                }
                c.close();
                c = null;
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
            c = null;
        }
        if (result != null) {
            // Actors
            c = cr.query(
                    ContentUris.withAppendedId(ScraperStore.Actor.URI.EPISODE, episodeId),
                    new String[] {
                            ScraperStore.Episode.Actor.NAME,            // 0
                            ScraperStore.Episode.Actor.ROLE,            // 1
                    },
                    null,
                    null,
                    null);
            if (c != null) {
                while (c.moveToNext()) {
                    String actor = c.getString(0);
                    String role = c.getString(1);
                    result.addActorIfAbsent(actor, role);
                }
                c.close();
                c = null;
            }
            // Directors
            c = cr.query(
                    ContentUris.withAppendedId(ScraperStore.Director.URI.EPISODE, episodeId),
                    new String[] {
                            ScraperStore.Episode.Director.NAME,            // 0
                    },
                    null,
                    null,
                    null);
            if (c != null) {
                while (c.moveToNext()) {
                    result.addDirectorIfAbsent(c.getString(0));
                }
                c.close();
                c = null;
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
        ShowTags result = null;
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
                },
                null,
                null,
                null);
        long posterId = -1;
        long backdropId = -1;
        if (c != null) {
            if (c.moveToFirst()) {
                result = new ShowTags();
                result.setId(showId);
                result.setTitle(c.getString(0));
                result.setPremiered(c.getLong(1));
                result.setRating(c.getFloat(2));
                result.setContentRating(c.getString(3));
                result.setPlot(c.getString(4));
                result.setOnlineId(c.getLong(5));
                result.setImdbId(c.getString(6));
                posterId = c.getLong(7);
                backdropId = c.getLong(8);
            }
            c.close();
            c = null;
        }
        if (result != null) {
            // Actors
            c = cr.query(
                    ContentUris.withAppendedId(ScraperStore.Actor.URI.SHOW, showId),
                    new String[] {
                            ScraperStore.Show.Actor.NAME,            // 0
                            ScraperStore.Show.Actor.ROLE,            // 1
                    },
                    null,
                    null,
                    null);
            if (c != null) {
                while (c.moveToNext()) {
                    String actor = c.getString(0);
                    String role = c.getString(1);
                    result.addActorIfAbsent(actor, role);
                }
                c.close();
                c = null;
            }
            // Directors
            c = cr.query(
                    ContentUris.withAppendedId(ScraperStore.Director.URI.SHOW, showId),
                    new String[] {
                            ScraperStore.Show.Director.NAME,            // 0
                    },
                    null,
                    null,
                    null);
            if (c != null) {
                while (c.moveToNext()) {
                    result.addDirectorIfAbsent(c.getString(0));
                }
                c.close();
                c = null;
            }
            // Genres
            c = cr.query(
                    ContentUris.withAppendedId(ScraperStore.Genre.URI.SHOW, showId),
                    new String[] {
                            ScraperStore.Show.Genre.NAME,            // 0
                    },
                    null,
                    null,
                    null);
            if (c != null) {
                while (c.moveToNext()) {
                    result.addGenreIfAbsent(c.getString(0));
                }
                c.close();
                c = null;
            }
            // Studios
            c = cr.query(
                    ContentUris.withAppendedId(ScraperStore.Studio.URI.SHOW, showId),
                    new String[] {
                            ScraperStore.Show.Studio.NAME,            // 0
                    },
                    null,
                    null,
                    null);
            if (c != null) {
                while (c.moveToNext()) {
                    result.addStudioIfAbsent(c.getString(0));
                }
                c.close();
                c = null;
            }
            // this fetches all posters, not just show posters
            List<ScraperImage> allPostersInDb = result.getAllPostersInDb(context, -1, true);
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
        }
        return result;
    }

}
