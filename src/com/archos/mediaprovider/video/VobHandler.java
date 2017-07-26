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

package com.archos.mediaprovider.video;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.provider.BaseColumns;
import android.util.Log;

import com.archos.mediaprovider.ArchosMediaCommon;
import com.archos.mediaprovider.ArchosMediaFile;
import com.archos.mediaprovider.video.VideoStore.MediaColumns;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles inserted / updated / deleted DVD content and
 * updates the database accordingly.<br>
 * Goal is to have just one .vob visible in a folder. Also Title in the database
 * is set based on directory structure if it seems to be a valid and complete DVD.
 */
public class VobHandler implements Handler.Callback {
    private static final String TAG =  ArchosMediaCommon.TAG_PREFIX + "VobHandler";
    private static final boolean LOCAL_DBG = true;
    private static final boolean DBG = ArchosMediaCommon.PACKAGE_DBG & LOCAL_DBG;

    /** delay before processing starts */
    // mediascanner processing can take above 500 msec per file so wait a little longer to be sure
    // we dont' repeat the processing for every inserted file.
    private static final long DELAY = 1500;

    private final Handler mHandler;
    private final Context mContext;

    // volatile since accessed from different threads.
    private volatile boolean mInTransaction;

    // a place to store the requests while still in transaction.
    private final HashMap<Integer, String> mTransactionQueue;

    public VobHandler(Context context) {
        mInTransaction = false;
        mTransactionQueue = new HashMap<Integer, String>();
        mContext = context;
        HandlerThread ht = new HandlerThread("ArchosVobHandler");
        ht.start();
        Looper l = ht.getLooper();
        mHandler = new Handler(l, this);
    }

    /**
     * entrypoint to trigger processing of a folder.
     */
    public void handleVob(String bucket_id) {
        if (DBG) {
            DateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
            String now = sdf.format(new Date());
            Log.d(TAG, "handleVob called at " + now + " bucket:" + bucket_id);
        }
        if (bucket_id == null) return;
        // use bucket_id as message id so we can handle
        // different buckets (folders) at the same time
        int msg_id;
        try {
            msg_id = Integer.parseInt(bucket_id);
        } catch (NumberFormatException e) {
            Log.w(TAG, "bad bucket_id:" + bucket_id);
            return;
        }
        // if we are in a transaction then we must store the requests until the transaction
        // ends otherwise the db query could be done before the transaction ends and we
        // would not find any parts. They are not visible until committed at transaction end.
        if (mInTransaction) {
            // synchronized to avoid enqueue / dequeue issues.
            synchronized (mTransactionQueue) {
                // that double checked locking works since mInTransaction is volatile
                if (mInTransaction) {
                    if (DBG) Log.d(TAG, "in Transaction, enqueuing Message");
                    Integer key = Integer.valueOf(msg_id);
                    mTransactionQueue.put(key, bucket_id);
                    return;
                }
                // else: inTransaction changed in the meantime but that does not matter.
            }
        }
        // if we get here we are not in a transaction and can proceed normally
        // delay the processing a bit so we don't need to do this every
        // time a vob is inserted.
        mHandler.removeMessages(msg_id);
        Message msg = mHandler.obtainMessage(msg_id, bucket_id);
        mHandler.sendMessageDelayed(msg, DELAY);
    }

    public void onBeginTransaction() {
        if (DBG) Log.d(TAG, "onBeginTransaction()");
        mInTransaction = true;
    }

    public void onEndTransaction() {
        if (DBG) Log.d(TAG, "onEndTransaction()");
        synchronized (mTransactionQueue) {
            if (DBG) Log.d(TAG, "sending " + mTransactionQueue.size() + " queued messages.");
            for (Integer key : mTransactionQueue.keySet()) {
                int msg_id = key.intValue();
                mHandler.removeMessages(msg_id);
                Message msg = mHandler.obtainMessage(msg_id, mTransactionQueue.get(key));
                // we don't need a delay here. Transactions impose their own delay.
                mHandler.sendMessage(msg);
            }
            mTransactionQueue.clear();
            mInTransaction = false;
        }
    }

    /** don't call this directly */
    public boolean handleMessage(Message msg) {
        if (msg.obj != null && msg.obj instanceof String) {
            handleInBackground((String) msg.obj);
        }
        return true;
    }

    // columns
    private static final String[] PROJECTION = new String[] {
        BaseColumns._ID,   // 0
        MediaColumns.DATA,  // 1
        MediaColumns.TITLE, // 2
        VideoStore.Video.VideoColumns.ARCHOS_HIDE_FILE, // 3
        VideoStore.Video.VideoColumns.ARCHOS_TITLE // 4
    };
    private static final int COLUMN_ID = 0;
    private static final int COLUMN_DATA = 1;
    private static final int COLUMN_TITLE = 2;
    private static final int COLUMN_HIDE_FILE = 3;
    private static final int COLUMN_ARCHOS_TITLE = 4;

    // where
    private static final String SELECTION = VideoStore.Video.VideoColumns.BUCKET_ID + "=? AND (" +
            MediaColumns.DATA + " LIKE '%/vts!___!__.vob' ESCAPE '!' OR " +
            MediaColumns.DATA + " LIKE '%/video!_ts.vob' ESCAPE '!')";

    // sort by
    private static final String SORTORDER = MediaColumns.DATA;

    /** processing takes place here. Called from handler in a background thread.*/
    private void handleInBackground(String bucket_id) {
        if (DBG) {
            DateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
            String now = sdf.format(new Date());
            Log.d(TAG, "starts processing at " + now + " bucket:" + bucket_id);
        }

        // first get all vobs in the directory from the database
        ContentResolver cr = mContext.getContentResolver();
        String[] selectionArgs = new String[] { bucket_id };
        ArrayList<VobFile> fileList = new ArrayList<VobFile>();
        Cursor c = cr.query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, PROJECTION, SELECTION, selectionArgs, SORTORDER);
        if (c != null) {
            while (c.moveToNext()) {
                // put them all in a list
                long id = c.getLong(COLUMN_ID);
                String file = c.getString(COLUMN_DATA);
                String title = c.getString(COLUMN_TITLE);
                String archosTitle = c.getString(COLUMN_ARCHOS_TITLE);
                int hidden = c.getInt(COLUMN_HIDE_FILE);
                VobFile vf = new VobFile(id, file, title, hidden, archosTitle);
                // since the database could return a file named VTS_AB_C.VOB,
                // check here that it matches the numbering schema.
                if (vf.validFile()) {
                    fileList.add(vf);
                } else {
                    if (DBG) Log.d(TAG, "Rejected file:" + vf.file);
                }
            }
            c.close();
        }
        // no files = nothing to do.
        if (fileList.size() == 0)
            return;
        // generate a new Title based on folder structure
        String newTitle = generateTitle(fileList.get(0).file);
        // create chains of vob files if they belong together
        linkFiles(fileList);
        VobFile longest = getLongest(fileList);
        boolean hideEverything = false;
        if (longest != null && longest.dvdPart == 1) {
            // if the longest is "vts_xx_1.vob" then we set the title
            // to the one we got from the directory structure
            // also everything but this title is hidden
            longest.newTitle = newTitle;
            hideEverything = true;
        }
        if (hideEverything) {
            for (VobFile vf : fileList) {
                // hide everything but the head of the longest vob chain
                if (vf != longest)
                    vf.newHide = true;
            }
        } else {
            // this seems to be some incomplete dvd so just hide subsequent
            // parts that are of no use to the user.
            for (VobFile vf : fileList) {
                vf.hideNextOrSelf();
            }
        }
        // save everything into db (if required)
        for (VobFile vf : fileList) {
            vf.updateDatabase(cr);
        }

        if (DBG) {
            // print some state info
            Log.d(TAG, "I found the following parts..");
            for (VobFile vf : fileList) {
                Log.d(TAG, vf.toString());
            }
        }
    }

    /**
     * @return same Title that MediaProvider would assign to a file
     */
    protected static String defaultTitle(File vob) {
        if (vob == null) return null;
        return ArchosMediaFile.getFileTitle(vob.getPath());
    }

    /**
     * returns a title generated from the directory structure.
     * Usually the name of the parent folder.<br>
     * <p>"/mnt/storage/The Matrix/video_ts.vob" -> "The Matrix"</p>
     * <p>skips the parent folder and takes the parent thereof if it is "VIDEO_TS"</p>
     * returns <b>null</b> if the result would be either the root of the filesystem
     * (e.g. /mnt/storage/[VIDEO_TS/]video_ts.vob -> null) or the "Video" folder
     * (/mnt/storage/Video/[VIDEO_TS/]vts_01_2.vob -> null)
     */
    private static String generateTitle (File vob) {
        // TODO: should we use something like "DVD" when there is no useful title?
        if (vob == null) return null;
        String path = vob.getPath();
        int slashCount = slashCount(path);
        // since files have to be at least
        // in "/mnt/storage|sdcard/dvd_folder/file" there need to be at
        // least four '/'
        if (slashCount < 4)
            return null;
        File parentFile = vob.getParentFile();
        File movieDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        // if the vob is in the Video directory ignore that name too
        if (parentFile.equals(movieDirectory)) {
            return null;
        }
        String parentDir = parentFile.getName();
        if (!"VIDEO_TS".equalsIgnoreCase(parentDir))
            return parentDir;
        // if "/mnt/storage/dvd_folder/VIDEO_TS/filename"
        if (slashCount > 4) {
            File parentParentFile = parentFile.getParentFile();
            // again not the "Video" directory
            if (!parentParentFile.equals(movieDirectory))
                return parentParentFile.getName();
        }
        // no new Title for that DVD
        return null;
    }

    /**
     * counts the amount of '/' in a String
     */
    private static int slashCount(String path) {
        if (path == null) return 0;
        int ret = 0;
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == File.separatorChar) {
                ret ++;
            }
        }
        return ret;
    }

    /**
     * returns a specified part from the list. <b>null</b> if that part does not
     * exist.
     */
    private static VobFile findPart(ArrayList<VobFile> list, int title, int part) {
        for (VobFile vf : list) {
            if (vf.dvdTitle == title && vf.dvdPart == part)
                return vf;
        }
        return null;
    }

    /**
     * sets each <b>next</b> field to the next part from the list. vts_xx_(n) -> vts_xx_(n+1).
     * remains <b>null</b> if that part is missing
     */
    private static void linkFiles(ArrayList<VobFile> list) {
        for (VobFile vf : list) {
            vf.next = findPart(list, vf.dvdTitle, vf.dvdPart + 1);
        }
    }

    /**
     * find the longest chain (>= 1) in the list and returns the head of that
     */
    private static VobFile getLongest(ArrayList<VobFile> list) {
        VobFile longest = null;
        int maxLength = 0;
        int currentLength;
        for (VobFile vf : list) {
            if ((currentLength = vf.linkLength()) > maxLength) {
                longest = vf;
                maxLength = currentLength;
            }
        }
        return longest;
    }

    /**
     * little helper class representing a vob file.
     */
    private static class VobFile {
        private static final Pattern VOB_PATTERN = Pattern.compile("vts_(\\d\\d)_(\\d).vob", Pattern.CASE_INSENSITIVE);
        // public fields for faster access & less code
        public final long id;
        public final File file;
        public final String title;
        public final String archosTitle;
        public final boolean hidden;
        public final int dvdTitle;
        public final int dvdPart;
        // next ones are determined afterwards
        public VobFile next = null;
        public boolean newHide = false;
        public String newTitle = null;

        public VobFile (long id, String file, String title, int hidden, String archosTitle) {
            this.id = id;
            this.file = new File(file);
            this.title = title;
            this.archosTitle = archosTitle;
            this.hidden = hidden != 0;
            if (file == null) {
                dvdTitle = -1;
                dvdPart = -1;
                return;
            }
            String name = this.file.getName();
            if ("video_ts.vob".equalsIgnoreCase(name)) {
                dvdTitle = 0;
                dvdPart = 0;
                return;
            }
            int dTitle = 0;
            int dPart = 0;
            Matcher m = VOB_PATTERN.matcher(name);
            if (m.matches()) {
                try {
                    dTitle = Integer.parseInt(m.group(1));
                    dPart = Integer.parseInt(m.group(2));
                } catch (NumberFormatException e) {
                    Log.w(TAG, "bad number in " + name);
                    dTitle = 0;
                    dPart = 0;
                }
            }
            dvdTitle = dTitle;
            dvdPart = dPart;
        }

        /**
         * checks if the file matches the required patterns.
         */
        public boolean validFile() {
            if (file == null) return false;
            String name = file.getName();
            if ("video_ts.vob".equalsIgnoreCase(name))
                return true;
            if (VOB_PATTERN.matcher(name).matches())
                return true;
            return false;
        }

        /**
         * @return length of the chain. vts_xx_n.vob -> vts_xx_n+1.vob ... _0.vob
         * has length 0.
         */
        public int linkLength() {
            // the _0 parts are never part of the movie
            if (dvdPart == 0) {
                return 0;
            }
            // the rest is at least of length 1
            int ret = 1;
            VobFile current = this;
            while (current.next != null) {
                current = current.next;
                ret++;
            }
            return ret;
        }

        /**
         * call this on every vob to have subsequent parts of a chain hidden.
         * Will just hide the next part or this part in case it's a _0.vob
         */
        public void hideNextOrSelf() {
            if (dvdTitle == 0) {
                // it's the video_ts.vob. hide self.
                newHide = true;
                return;
            }
            // hide the next in chain
            if (next != null) {
                // special handling of the _0 part.
                if (dvdPart == 0) {
                    // that _0 part is not part of the movie, hide self
                    newHide = true;
                } else {
                    // usual case, next in chain getting hidden.
                    next.newHide = true;
                }
            }
        }

        /**
         * Updates the database if that is required (title and/or hide)
         */
        public void updateDatabase(ContentResolver cr) {
            boolean updateTitle = false;
            if (dvdPart == 1) { // we only ever change the name of the _1.vob
                if (newTitle == null) {
                    // reset title back to null if it is not.
                    updateTitle =  archosTitle != null;
                } else if (!newTitle.equals(archosTitle)) {
                    // change title to something new.
                    updateTitle = true;
                }
            }
            if (updateTitle || hidden != newHide) {
                ContentValues cv = new ContentValues();
                if (updateTitle) {
                    cv.put(VideoStore.Video.VideoColumns.ARCHOS_TITLE, newTitle);
                    if (DBG) Log.d(TAG, "update " + file + " title: " + newTitle);
                }
                if (hidden != newHide) {
                    cv.put(VideoStore.Video.VideoColumns.ARCHOS_HIDE_FILE, newHide ? "1" : "0");
                    if (DBG) Log.d(TAG, "update " + file + " hide: " + (newHide ? "1" : "0"));
                }
                Uri uri = ContentUris.withAppendedId(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                cr.update(uri, cv, null, null);
            }
        }

        /** Printing state information about this vob file */
        @Override
        public String toString() {
            return "VobFile " + file.getPath() + " DB title:" + title + " DB id:" + id +
                    " dvdTitle:" + dvdTitle + " dvdPartOfTitle:" + dvdPart + " hasNext:" + (next != null) +
                    " hide:" + newHide + " length:" + linkLength() + " newTitle:" + newTitle;
        }
    }
}
