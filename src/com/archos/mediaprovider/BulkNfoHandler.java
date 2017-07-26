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


package com.archos.mediaprovider;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;

import com.archos.filecorelibrary.MetaFile;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.NfoParser;
import com.archos.mediascraper.NfoParser.NfoFile;

import java.util.ArrayList;

public class BulkNfoHandler {
    private static final String TAG = ArchosMediaCommon.TAG_PREFIX
            + BulkNfoHandler.class.getSimpleName();

    private static final boolean DBG = false;

    private final Context mContext;
    private final ContentResolver mCr;
    private final NfoParser.ImportContext mNfoContext;
    private final ArrayList<NfoFile> mNfoList;
    private final int mLimit;
    private int mHandledCount;

    public BulkNfoHandler(Context context, int limit) {
        mContext = context;
        mCr = context.getContentResolver();

        mNfoList = new ArrayList<NfoFile>(limit);
        mLimit = limit;

        mNfoContext = new NfoParser.ImportContext();
    }

    public void add(NfoFile nfoFile, BulkInserter flushIfExecute) {
        if (mNfoList.size() >= mLimit) {
            if (DBG) Log.d(TAG, "execute() at " + mNfoList.size() + "/" + mLimit);
            execute(flushIfExecute);
        }
        mNfoList.add(nfoFile);
    }

    public int execute(BulkInserter flushIfExecute) {
        if (mNfoList.size() <= 0)
            return mHandledCount;

        if (flushIfExecute != null) {
            flushIfExecute.execute();
        }

        handleNfoFiles();

        // got to clear the list.
        mNfoList.clear();

        return mHandledCount;
    }

    public int getHandledCount() {
        return mHandledCount;
    }

    private void handleNfoFiles() {
        for (NfoParser.NfoFile nfo : mNfoList) {
            // skip parsing if file has info already
            if (!hasScraperInfo(nfo, mCr)) {
                BaseTags tag = NfoParser.getTagForFile(nfo, mContext, mNfoContext);
                if (tag != null) {
                    if (nfo.hasDbId)
                        tag.save(mContext, nfo.dbId);
                    else
                        tag.save(mContext, nfo.videoFile);
                    mHandledCount++;
                }
            }
        }
    }

    private static boolean hasScraperInfo(NfoFile nfo, ContentResolver cr) {
        if (nfo.hasDbId)
            return hasScraperInfo(nfo.dbId, cr);
        return hasScraperInfo(nfo.videoFile, cr);
    }

    private static final String WHERE_FILE = VideoStore.MediaColumns.DATA + "=?";

    private static boolean hasScraperInfo(Uri video, ContentResolver cr) {
        String[] selectionArgs = {
                video.toString()
        };
        return hasScraperInfo(WHERE_FILE, selectionArgs, cr);
    }

    private static final String WHERE_ID = BaseColumns._ID + "=?";

    private static boolean hasScraperInfo(long videoId, ContentResolver cr) {
        String[] selectionArgs = {
            String.valueOf(videoId)
        };
        return hasScraperInfo(WHERE_ID, selectionArgs, cr);
    }

    private static final String[] PROJECT_SCRAPER_ID = {
        VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID
    };

    private static boolean hasScraperInfo(String selection, String[] selectionArgs,
            ContentResolver cr) {
        Cursor c = cr.query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, PROJECT_SCRAPER_ID,
                selection, selectionArgs, null);
        boolean result = false;
        if (c != null) {
            if (c.moveToFirst()) {
                // if ScraperID > 0
                if (c.getInt(0) > 0)
                    result = true;
            }
            c.close();
        }
        return result;
    }

}
