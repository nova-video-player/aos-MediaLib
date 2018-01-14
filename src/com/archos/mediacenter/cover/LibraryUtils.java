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

package com.archos.mediacenter.cover;

import com.archos.mediaprovider.video.VideoStore;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.BaseColumns;


public class LibraryUtils {

	private final static String TAG = "LibraryUtils";
	private final static String AND = " AND ";
	private final static String DESC = " DESC";
	private final static String LIMIT = " LIMIT ";

	public final static String[] VIDEO_COLS = {
		VideoStore.Video.VideoColumns._ID,
		VideoStore.Video.VideoColumns.DATA,
		VideoStore.Video.VideoColumns.TITLE,
		VideoStore.Video.VideoColumns.MIME_TYPE,
		VideoStore.Video.VideoColumns.DURATION,
		VideoStore.Video.VideoColumns.BOOKMARK,
		VideoStore.Video.VideoColumns.ARCHOS_BOOKMARK,
		VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE,
		VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID
	};

    public static final String TVSHOW_EPISODE_COUNT_COLUMN = "episode_count";
    public static final String TVSHOW_SEASON_COUNT_COLUMN = "season_count";
    private static final String[] TVSHOW_COLS = {
        VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID, // id
        VideoStore.Video.VideoColumns.SCRAPER_S_NAME,  // title
        VideoStore.Video.VideoColumns.SCRAPER_S_COVER, // show cover
        VideoStore.Video.VideoColumns.SCRAPER_E_SEASON, // season number for single season case
        VideoStore.Video.VideoColumns.DATA, // file path of any episode of show
        "count(*) AS " + TVSHOW_EPISODE_COUNT_COLUMN, // number of episodes total
        "count(DISTINCT " + VideoStore.Video.VideoColumns.SCRAPER_E_SEASON +
            ") AS " + TVSHOW_SEASON_COUNT_COLUMN, // number of seasons
    };
    private final static String SELECTION_NON_HIDDEN = VideoStore.Video.VideoColumns.ARCHOS_HIDE_FILE + "=0";

    private static final String TVSHOW_SELECT = VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID +
            " NOT NULL AND " + SELECTION_NON_HIDDEN + ") GROUP BY ( " + VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID;


    public static CursorLoader getAllVideosCursorLoader(Context context, int max) {
        String sortOrder = VideoStore.Video.Media.DEFAULT_SORT_ORDER + LIMIT + max;
        return new CursorLoader(context,VideoStore.Video.Media.EXTERNAL_CONTENT_URI, VIDEO_COLS, SELECTION_NON_HIDDEN, null, sortOrder);
    }

	public static CursorLoader getRecentlyAddedVideosCursorLoader(Context context, int days, int max) {
	        String sortOrder = VideoStore.MediaColumns.DATE_ADDED + DESC + LIMIT + max;
	        long xDaysFromNow = (System.currentTimeMillis()/1000) - (days*24*60*60);
	        String selection = SELECTION_NON_HIDDEN + " AND " + VideoStore.MediaColumns.DATE_ADDED + ">?";
	        String[] selectionArgs = new String[] {String.valueOf(xDaysFromNow)};
	        return new CursorLoader(context, VideoStore.Video.Media.EXTERNAL_CONTENT_URI, VIDEO_COLS, selection, selectionArgs, sortOrder);
	    }

	public static CursorLoader getAllMoviesCursorLoader(Context context, int max) {
        String sortOrder = VideoStore.MediaColumns.DATE_ADDED + DESC + LIMIT + max;
        StringBuilder where = new StringBuilder();
        where.append(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID).append(" > '0'"); // valid scraper IDs are >0
        where.append(AND).append(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE).append(" = ").append(com.archos.mediascraper.BaseTags.MOVIE ); // movies only, no tv shows
        return new CursorLoader(context,VideoStore.Video.Media.EXTERNAL_CONTENT_URI, VIDEO_COLS, where.toString(), null, sortOrder);
    }

    public static CursorLoader getAllTVShowsCursorLoader(Context context) {
        Uri uri = VideoStore.Video.Media.EXTERNAL_CONTENT_URI;
        String sortOrder = VideoStore.Video.VideoColumns.SCRAPER_S_NAME;
        return new CursorLoader(context, uri, TVSHOW_COLS, TVSHOW_SELECT, null, sortOrder);
    }

	private static Cursor query(Context context, Uri uri, String[] projection,
			String selection, String[] selectionArgs, String sortOrder) {
		try {
			ContentResolver resolver = context.getContentResolver();
			if (resolver == null) {
				return null;
			}
			return resolver.query(uri, projection, selection, selectionArgs, sortOrder);
		} catch (UnsupportedOperationException ex) {
			return null;
		}
	}

    public static Cursor getScraperCursorFromPath(ContentResolver resolver, String filePath) {
        StringBuilder where = new StringBuilder();
        where.append(VideoStore.Video.VideoColumns.DATA).append("= ?");
        where.append(" AND ").append(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID).append(" > '0'");
        String[] selectionArgs = new String[1];
        selectionArgs[0] = filePath;
        return resolver.query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, VIDEO_COLS, where.toString(), selectionArgs, null);
    }

    /**
     * get Resume and Bookmark values for a video, identified by it's MediaDB id.
     * @param context
     * @param videoId
     * @param resumeAndBookmark: out. resumeAndBookmark[0] is resume ; resumeAndBookmark[1] is bookmark, must be != null at call
     *  Returns zero and zero if the file is not found
     */
    public static void getVideoResumeAndBookmark(Context context, long videoId, int[] resumeAndBookmark) {
    	Cursor c = query(context, VideoStore.Video.Media.EXTERNAL_CONTENT_URI, VIDEO_COLS,
    			VideoStore.Video.Media._ID + " = '" + videoId + "'", null, null);
    	if ((c!=null) && (c.getCount()==1)) {
    		c.moveToFirst();
    		resumeAndBookmark[0] = c.getInt(c.getColumnIndexOrThrow(VideoStore.Video.VideoColumns.BOOKMARK));
    		resumeAndBookmark[1] = c.getInt(c.getColumnIndexOrThrow(VideoStore.Video.VideoColumns.ARCHOS_BOOKMARK));
    	}
    	else {
    		resumeAndBookmark[0] = resumeAndBookmark[1] = 0;
    	}
    }

    // Common name for all MediaCenter shared preferences
    public final static String SHARED_PREFERENCES_NAME = "MediaCenter";

    public static String getStringPref(Context context, String name, String def) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        return prefs.getString(name, def);
    }

    public static void setStringPref(Context context, String name, String value) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        Editor ed = prefs.edit();
        ed.putString(name, value);
        ed.commit();
    }

	public static boolean hasStorage() {
        String storageState = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(storageState)) {
            return true;
        }
        return false;
    }

}
