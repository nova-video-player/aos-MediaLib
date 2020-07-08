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

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.archos.filecorelibrary.ExtStorageManager;

import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.utils.BlacklistedDbAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Blacklist {
    protected static final String TAG = Blacklist.class.getSimpleName();
    private static final boolean DBG = false;

    private static Blacklist DEFAULT_INSTANCE;

    private Context mContext;

    private Blacklist(Context context) {
        /*
         * TODO: this class shall be able to tell you if a file is blacklisted (or outside the
         * whitelist), this needs configuration of some sort (shared preferences?) and may also need to trigger an
         * update of the database to (un-)hide or rescan files.
         * For now it's only blacklisting files ending with "sample" or "trailer"
         */
        mContext = context;
    }

    public static Blacklist getInstance(Context context) {
        if (DEFAULT_INSTANCE == null)
            DEFAULT_INSTANCE = new Blacklist(context);
        
        return DEFAULT_INSTANCE;
    }

    // ends with sample/trailer and may have .ext
    private static final Pattern BLACKLISTED = Pattern.compile("^.*(?:sample|trailer)(?:\\.[^.]+)?$", Pattern.CASE_INSENSITIVE);

    private static final String[] BLACKLISTED_CAMERA = {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath(),
    };

    private static final String[] BLACKLISTED_CAM_DIRS = {
            "/DCIM", "/Camera",
            "/WhatsApp", "/GooglePlus",
            "/Allo", "/Pictures",
            "/Snapchat", "/Telegram",
            "/com.facebook.katana",
            "/com.facebook.orca",
            "/Movies/Instagram", "/Movies/Messenger",
            "/Movies/Twitter", "/tencent"
    };

    public boolean isBlacklisted(Uri file) {
        if (DBG) Log.d(TAG,"isBlacklisted: ExternalStorageDirectory=" + Environment.getExternalStorageDirectory().getPath() + ", ExtSdcards=" + ExtStorageManager.getExtStorageManager().getExtSdcards());
        if (file == null) return true;
        if (DBG) Log.d(TAG,"isBlacklisted: checking now on default camera dir " + BLACKLISTED_CAMERA);
        for (String blacklisted : BLACKLISTED_CAMERA) {
            if (DBG) Log.d(TAG,"isBlacklisted: " + file.getPath() + " starts with " + blacklisted);
            if (FileUtils.isLocal(file) && file.getPath().startsWith(blacklisted)) return true;
        }
        List<String> extPathList = ExtStorageManager.getExtStorageManager().getExtSdcards();
        extPathList.add(Environment.getExternalStorageDirectory().getPath());
        if (DBG) Log.d(TAG,"isBlacklisted: checking now on app blacklisted" + extPathList);
        for (String extPath: extPathList) {
            for (String blacklistedDir : BLACKLISTED_CAM_DIRS)  {
                if (DBG) Log.d(TAG,"isBlacklisted: " + file.getPath() + " starts with " + extPath+blacklistedDir);
                if (FileUtils.isLocal(file) && file.getPath().startsWith(extPath+blacklistedDir)) return true;
            }
        }
        for (String blacklisted : getBlacklisteds()) {
            if (DBG) Log.d(TAG,"isBlacklisted: " + file.getPath() + " starts with " + blacklisted);
            if (FileUtils.isLocal(file) && file.getPath().startsWith(blacklisted)) return true;
        }
        return isFilenameBlacklisted(file.getLastPathSegment());
    }

    private boolean isFilenameBlacklisted(String fileName) {
        if (fileName != null) {
            if (DBG) Log.d(TAG,"isFilenameBlacklisted: " + fileName + " contains sample|trailer");
            Matcher matcher = BLACKLISTED.matcher(fileName);
            if (matcher.matches())
                return true;
        }
        return false;
    }

    private ArrayList<String> getBlacklisteds() {
        ArrayList<String> blacklisteds = new ArrayList<>();
        Cursor c = BlacklistedDbAdapter.VIDEO.queryAllBlacklisteds(mContext);
        final int pathColumn = c.getColumnIndexOrThrow(BlacklistedDbAdapter.KEY_PATH);
        try {
            if (c.moveToFirst()) {
                while (!c.isAfterLast()) {
                    String blacklistedPath = c.getString(pathColumn);
                    if (blacklistedPath != null) {
                        blacklistedPath = Uri.parse(blacklistedPath).getPath();

                        if (!blacklistedPath.endsWith("/"))
                            blacklistedPath += "/";

                        blacklisteds.add(blacklistedPath);
                    }
                    c.moveToNext();
                }
            }
        } catch (Exception e) {
            // with c.close() we get with c.moveToFirst() IllegalStateException: Cannot perform this operation because the connection pool has been closed
            // without c.close() we get with c.moveToFirst() CursorWindowAllocationException
            Log.e(TAG, "getBlacklisteds: caught Exception", e);
        } finally {
            c.close();
        }
        return blacklisteds;
    }
}
