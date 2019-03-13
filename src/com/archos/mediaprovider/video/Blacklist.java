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

import com.archos.filecorelibrary.ExtStorageManager;
import com.archos.filecorelibrary.MetaFile;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.utils.BlacklistedDbAdapter;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Blacklist {
    protected static final String TAG = Blacklist.class.getSimpleName();

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
            "/Movies/Twitter",
    };

    public boolean isBlacklisted(Uri file) {
        if (file == null) return true;
        for (String blacklisted : BLACKLISTED_CAMERA) {
            if (FileUtils.isLocal(file) && file.getPath().startsWith(blacklisted)) return true;
        }
        for (String extPath: ExtStorageManager.getExtStorageManager().getExtSdcards()) {
            for (String blacklistedDir : BLACKLISTED_CAM_DIRS)  {
                if (FileUtils.isLocal(file) && file.getPath().startsWith(extPath+blacklistedDir)) return true;
            }
        }
        for (String blacklisted : getBlacklisteds()) {
            if (FileUtils.isLocal(file) && file.getPath().startsWith(blacklisted)) return true;
        }
        return isFilenameBlacklisted(file.getLastPathSegment());
    }

    private boolean isFilenameBlacklisted(String fileName) {
        if (fileName != null) {
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

        c.moveToFirst();
        while (!c.isAfterLast()) {
            String blacklistedPath = c.getString(pathColumn);
            if (blacklistedPath!=null) {
                blacklistedPath = Uri.parse(blacklistedPath).getPath();

                if(!blacklistedPath.endsWith("/"))
                    blacklistedPath += "/";

                blacklisteds.add(blacklistedPath);
            }
            c.moveToNext();
        }
        c.close();

        return blacklisteds;
    }
}
