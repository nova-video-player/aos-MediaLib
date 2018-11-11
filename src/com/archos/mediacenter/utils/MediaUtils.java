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


package com.archos.mediacenter.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AbsListView;
import android.widget.GridView;
import android.widget.ListView;

import com.archos.filecorelibrary.FileEditorFactory;
import com.archos.medialib.R;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Formatter;
import java.util.Locale;

public class MediaUtils {
    private final static String TAG = "Utils";
    private final static int SUBS_LIMIT = 100;

    // Common paths
    public static final String STORAGE_PATH = Environment.getExternalStorageDirectory().getPath();

    // Common name for all MediaCenter shared preferences
    public final static String SHARED_PREFERENCES_NAME = "MediaCenter";

    // Available keys for the MediaCenter shared preferences
    public static final String PREFS_NUM_WEEKS_KEY = "NumwWeeks";
    public static final String PREFS_SHOW_SUGGESTION_DIALOG_KEY = "ShowSearchSuggestionDialog";
    public static final String PREFS_SETTINGS_COVER_ROLL_CONTENT_KEY = "CoverRollContent";
    public static final String PREFS_SETTINGS_COVER_ROLL_3D_MUSIC_CONTENT_KEY = "CoverRoll3DMusicContent";
    public static final String PREFS_SETTINGS_COVER_ROLL_3D_VIDEO_CONTENT_KEY = "CoverRollContent";

    // Thresholds for the joystick zones
    public static final float JOYSTICK_DEAD_ZONE_THRESHOLD_PERCENT = 0.25f;
    public static final float JOYSTICK_FAR_ZONE_THRESHOLD_PERCENT = 0.70f;

    // Joystick zones
    public final static int JOYSTICK_ZONE_FAR_LEFT = 1;
    public final static int JOYSTICK_ZONE_LEFT = 2;
    public final static int JOYSTICK_ZONE_CENTER = 3;
    public final static int JOYSTICK_ZONE_RIGHT = 4;
    public final static int JOYSTICK_ZONE_FAR_RIGHT = 5;

    public static int getJoystickZone(MotionEvent event) {
        int joystickZone = JOYSTICK_ZONE_CENTER;

        InputDevice.MotionRange range = event.getDevice().getMotionRange(MotionEvent.AXIS_X, event.getSource());
        if (range != null) {
            float valueX = event.getAxisValue(MotionEvent.AXIS_X);
            float deadZoneThreshold = range.getRange() * JOYSTICK_DEAD_ZONE_THRESHOLD_PERCENT / 2;
            float farZoneThreshold = range.getRange() * JOYSTICK_FAR_ZONE_THRESHOLD_PERCENT / 2;

            // Get the current joystick state depending on the position
            if (valueX <= -farZoneThreshold) {
                joystickZone = MediaUtils.JOYSTICK_ZONE_FAR_LEFT;
            }
            else if (valueX <= -deadZoneThreshold) {
                joystickZone = MediaUtils.JOYSTICK_ZONE_LEFT;
            }
            else if (valueX < deadZoneThreshold) {
                joystickZone = MediaUtils.JOYSTICK_ZONE_CENTER;
            }
            else if (valueX < farZoneThreshold) {
                joystickZone = MediaUtils.JOYSTICK_ZONE_RIGHT;
            }
            else {
                joystickZone = MediaUtils.JOYSTICK_ZONE_FAR_RIGHT;
            }
        }

        return joystickZone;
    }

    public static boolean getSuggestionDialogPref(Context context, boolean def) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_WORLD_READABLE | Context.MODE_WORLD_WRITEABLE);
        return prefs.getBoolean(PREFS_SHOW_SUGGESTION_DIALOG_KEY, def);
    }

    public static void setSuggestionDialogPref(Context context, boolean value) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_WORLD_READABLE | Context.MODE_WORLD_WRITEABLE);
        Editor ed = prefs.edit();
        ed.putBoolean(PREFS_SHOW_SUGGESTION_DIALOG_KEY, value);
        ed.commit();
    }

    public static int getPositionForSection(int sectionIndex, SparseArray<String> indexer, int fileListSize, String[] sections) {
        // The Android documentation says that sectionIndex might be out of range
        int size = indexer.size();
        if (sectionIndex < 0) {
            return 0;
        }
        if (sectionIndex >= size) {
            return fileListSize;
        }

        String letter = sections[sectionIndex];
        int index = indexer.indexOfValue(letter);
        return indexer.keyAt(index);
    }

    public static int getSectionForPosition(int position, SparseArray<String> indexer, int fileListSize) {
        // The Android documentation says that position might be out of range
        if (position < 0) {
            return 0;
        }
        if (position >= fileListSize) {
            return indexer.size();
        }

        // Check if the position belongs to one of the first (sectionCount - 1) sections
        int section = 0;
        int sectionCount = indexer.size();
        while (section < sectionCount - 1) {
            if (position >= indexer.keyAt(section) && position < indexer.keyAt(section + 1)) {
                return section;
            }
            section++;
        }

        // The position belongs to the last section
        return sectionCount - 1;
    }


public static int restoreBestPosition(GridView view, int selectedPosition,
            int firstVisiblePosition, int lastPosition) {
	return restoreBestPositionWithScroll(view, selectedPosition, firstVisiblePosition,  0);
}

    /**
     * Restore the best position possible in the list.
     *
     * @return the new last position
     */
    public static int restoreBestPositionWithScroll(final AbsListView listView, int selectedPosition,
            int firstVisiblePosition,  final int scroll) {
    	if(listView.getFirstVisiblePosition() <= selectedPosition && listView.getLastVisiblePosition()>=selectedPosition)
    		return selectedPosition;
        final int newPositionToSelect;
        newPositionToSelect = selectedPosition>=0?selectedPosition:0;
        if (newPositionToSelect < listView.getCount()) {
            //we select next enabled view
            for(int i = newPositionToSelect; i<listView.getCount(); i++){      
                if(i<listView.getAdapter().getCount()&&listView.getAdapter().isEnabled(i)){
                     //we don't want to loose scroll
                        final int pos = i;  
                        if(listView instanceof GridView){
                        	listView.setSelection(pos);
                            listView.smoothScrollToPositionFromTop(pos, scroll);
                        }
                        else if(listView instanceof ListView)
                            ((ListView)listView).setSelectionFromTop(pos, scroll);
                      
                
                    break;       
                }     
            }
            
            // Make sure the album list/grid has still the focus after changing the
            // view mode
            listView.postDelayed(new Runnable() {
				@Override
				public void run() {
					listView.requestFocus();		
				}
			}, 200);
        }
        return newPositionToSelect;
    }
   
    public static File getOldSubsDir(Context context){
        StringBuilder sb = new StringBuilder();
        sb.append(STORAGE_PATH).append("/Android/data/").append(context.getPackageName()).append("/subtitles");
        File subsDir = new File(sb.toString());
        return subsDir;
    }
    public static void clearOldSubDir(Context context){
        
        try {
            File subdir = getOldSubsDir(context);

            if (subdir.exists()) {
                FileEditorFactory.getFileEditorForUrl(Uri.fromFile(subdir), context).delete();
            }
        }catch (Exception e){e.printStackTrace();}
    }
    /*
  * returns the path of local subtitles directory
  */
    public static File getSubsDir(Context context){
        StringBuilder sb = new StringBuilder();
        if(context.getExternalCacheDir()!=null) //seems that some devices haven't external cache
            sb.append(context.getExternalCacheDir().getAbsolutePath()).append("/subtitles");
        else
            sb.append(STORAGE_PATH).append("/Android/data/").append(context.getPackageName()).append("/cache/subtitles");
        File subsDir = new File(sb.toString());
        if (!subsDir.exists())
            subsDir.mkdirs();
        return subsDir;
    }

 // nice way to close things that might be null
    public static void closeSilently(Closeable closeme) {
        if (closeme == null) return;
        try {
            closeme.close();
        } catch (IOException e) {
            // silence
        }
    }
    /*
     * Remove the oldest subs files if SUBS_LIMIT number is exceeded or clearAll is true
     */
    public static void removeLastSubs(Context context){
        File subsFolder = getSubsDir(context);
        File[] subsList = subsFolder.listFiles();
        if (subsList==null||subsList.length <= SUBS_LIMIT){
            subsList = null;
            return;
        }
        // sort files
        Arrays.sort(subsList, new Comparator<File>(){
            public int compare(File f1, File f2)
            {
                return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
            } });
        int numberOfSubsToRemove = subsList.length - SUBS_LIMIT;
        for (int i = 0 ; i< numberOfSubsToRemove ; ++i){
            subsList[i].delete();
        }
        subsList = null;
    }

    /*
     * set a greyed and blured bitmap as background of a view
     */
    public static void setBackground(View v, Bitmap bm) {

        if (bm == null) {
            v.setBackgroundResource(0);
            return;
        }

        int vwidth = v.getWidth()/4;
        int vheight = v.getHeight()/4;
        int bwidth = bm.getWidth();
        int bheight = bm.getHeight();
        float scalex = (float) vwidth / bwidth;
        float scaley = (float) vheight / bheight;
        float scale = Math.max(scalex, scaley) * 1.3f;

        Bitmap.Config config = Bitmap.Config.ARGB_8888;
        Bitmap bg = Bitmap.createBitmap(vwidth, vheight, config);
        Canvas c = new Canvas(bg);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        ColorMatrix greymatrix = new ColorMatrix();
        greymatrix.setSaturation(0);
        ColorMatrix darkmatrix = new ColorMatrix();
        darkmatrix.setScale(1f, 1f, 1f, .1f);
        greymatrix.postConcat(darkmatrix);
        ColorFilter filter = new ColorMatrixColorFilter(greymatrix);
        paint.setColorFilter(filter);
        Matrix matrix = new Matrix();
        matrix.setTranslate(-bwidth/2, -bheight/2); // move bitmap center to origin
        matrix.postRotate(10);
        matrix.postScale(scale, scale);
        matrix.postTranslate(vwidth/2, vheight/2);  // Move bitmap center to view center
        c.drawBitmap(bm, matrix, paint);
        v.setBackgroundDrawable(new BitmapDrawable(bg));
    }

    public static void setStreamMusicMute(AudioManager audioManager, boolean mute) {
        Method setStreamMuteSystemNoUI;
        try {
            setStreamMuteSystemNoUI = AudioManager.class.getMethod("setStreamMuteSystemNoUI", int.class, boolean.class);
            setStreamMuteSystemNoUI.invoke(audioManager, AudioManager.STREAM_MUSIC, mute);
        } catch (Exception e) {
            Log.d(TAG, "setStreamMusicMute failed!", e);
            audioManager.setStreamMute(AudioManager.STREAM_MUSIC, mute);
        }
    }

    /*  Try to use String.format() as little as possible, because it creates a
     *  new Formatter every time you call it, which is very inefficient.
     *  Reusing an existing Formatter more than tripled the speed of
     *  makeTimeString().
     *  This Formatter/StringBuilder are also used by makeAlbumSongsLabel()
     */
    /** Formatting optimization to avoid creating many temporary objects. */
    private static StringBuilder sFormatBuilder = new StringBuilder();
    /** Formatting optimization to avoid creating many temporary objects. */
    private static Formatter sFormatter = new Formatter( sFormatBuilder, Locale.getDefault() );
    /** Formatting optimization to avoid creating many temporary objects. */
    private static final Object[] sTimeArgs = new Object[5];

    public static String makeTimeString(Context context, long secs) {
        String durationformat = context.getString(
                secs < 3600 ? R.string.durationformatshort : R.string.durationformatlong);

        /* Provide multiple arguments so the format can be changed easily
         * by modifying the xml.
         */
        sFormatBuilder.setLength(0);

        final Object[] timeArgs = sTimeArgs;
        timeArgs[0] = secs / 3600;
        timeArgs[1] = secs / 60;
        timeArgs[2] = (secs / 60) % 60;
        timeArgs[3] = secs;
        timeArgs[4] = secs % 60;

        return sFormatter.format(durationformat, timeArgs).toString();
    }

    public static String makeDurationString( Context context, long millisecs, boolean separator ) {
        long secs = millisecs / 1000;
        if( secs == 0 ) {
            return "";
        } else {
            String duration = separator ? " - " : "";
            return duration + makeTimeString( context, secs );
        }
    }

    static public String formatTime(long ms) {
        String res;
        if (ms <= 0)
            res = "";
        else {
            // h = ms / 3600000;
            // m = (ms % 3600000) / 60000;
            // s = (ms % 60000) / 1000;
            long sec = ms / 1000;
            if (sec >= 3600) {
                long m = (sec % 3600) / 60;
                // %Hh%m'
                res = String.valueOf(sec / 3600) + "h" + (m < 10 ? "0" + String.valueOf(m) : String.valueOf(m)) + "'";
            }
            else if (ms < 60)
                // %s''
                res = String.valueOf(sec % 60) + "''";
            else {
                long s = sec % 60;
                // %m'%s''
                res = String.valueOf((sec % 3600) / 60) + "'" + (s < 10 ? "0" + String.valueOf(s) : String.valueOf(s)) + "''";
            }
        }
        return res;
    }

    private static String mLastSdStatus;

    // Simplified version of MusicUtils.displayDatabaseError
    // Just returns the message
    public static String getDatabaseErrorMessage(Context c) {
        String status = Environment.getExternalStorageState();
        int title = R.string.storage_error_title;
        int message = R.string.storage_error_message;

        if (status.equals(Environment.MEDIA_SHARED) ||
            status.equals(Environment.MEDIA_UNMOUNTED)) {
            title = R.string.storage_busy_title;
            message = R.string.storage_busy_message;
        } else if (status.equals(Environment.MEDIA_REMOVED)) {
            title = R.string.storage_missing_title;
            message = R.string.storage_missing_message;
        } else if (status.equals(Environment.MEDIA_MOUNTED)){
            // The card is mounted, but we didn't get a valid cursor.
            // This probably means the mediascanner hasn't started scanning the
            // card yet (there is a small window of time during boot where this
            // will happen).
           // DON'T KNOW WHAT TO DO IN THAT CASE...
        } else if (!TextUtils.equals(mLastSdStatus, status)) {
            mLastSdStatus = status;
            Log.d(TAG, "sd card: " + status);
        }
        // returns only "message" (ditch title)
        return c.getResources().getString(message);
    }

}
