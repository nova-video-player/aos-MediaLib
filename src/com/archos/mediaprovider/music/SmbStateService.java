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


package com.archos.mediaprovider.music;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;

import com.archos.filecorelibrary.MetaFile;
import com.archos.mediaprovider.ArchosMediaCommon;
import com.archos.mediaprovider.NetworkState;
import com.archos.mediaprovider.music.MusicStore.MediaColumns;

/** handles visibility updates of smb://server/share type servers in the database */
public class SmbStateService extends IntentService {
    private static final String TAG = ArchosMediaCommon.TAG_PREFIX + SmbStateService.class.getSimpleName();
    private static final boolean DBG = false;

    private static final Uri NOTIFY_URI = MusicStore.ALL_CONTENT_URI;
    private static final Uri SERVER_URI = MusicStore.SmbServer.getContentUri();

    private static final String[] PROJECTION_SERVERS = new String[] {
            BaseColumns._ID,
            MediaColumns.DATA,
            MusicStore.SmbServer.SmbServerColumns.ACTIVE
    };
    private static final int COLUMN_ID = 0;
    private static final int COLUMN_DATA = 1;
    private static final int COLUMN_ACTIVE = 2;

    private static final String SELECTION_ID = BaseColumns._ID + "=?";
    private static final String SELECTION_SMB = MediaColumns.DATA + " LIKE 'smb://%'";

    public static final String ACTION_CHECK_SMB = "archos.intent.action.CHECK_SMB";

    public SmbStateService() {
        super(SmbStateService.class.getSimpleName());
        if (DBG) Log.d(TAG, "SmbStateService CTOR");
        setIntentRedelivery(true);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (DBG) Log.d(TAG, "onHandleIntent " + intent);
        if (ACTION_CHECK_SMB.equals(intent.getAction())) {
            NetworkState state = NetworkState.instance(this);
            handleDb(this, state.hasLocalConnection());
        }
    }

    /** use to issue a check of the smb:// state */
    public static void start(Context context) {
        Intent intent = new Intent(context, SmbStateService.class);
        intent.setAction(ACTION_CHECK_SMB);
        context.startService(intent);
    }

    protected static void handleDb(Context context, boolean hasLocalConnection) {
        ContentResolver cr = context.getContentResolver();
        if (hasLocalConnection) {
            long now = System.currentTimeMillis() / 1000;
            // list all servers in the db
            Cursor c = cr.query(SERVER_URI, PROJECTION_SERVERS, SELECTION_SMB, null, null);
            if (c != null) {
                if (DBG) Log.d(TAG, "found " + c.getCount() + " servers");
                while (c.moveToNext()) {
                    long id = c.getLong(COLUMN_ID);
                    String server = c.getString(COLUMN_DATA);
                    int active = c.getInt(COLUMN_ACTIVE);
                    MetaFile serverFile = MetaFile.from(server);
                    if (serverFile == null) {
                        Log.d(TAG, "bad server [" + server + "]");
                        continue;
                    }
                    if (serverFile.exists()) {
                        if (DBG) Log.d(TAG, "server exists: " + serverFile.getDisplayPath());
                        updateServerDb(id, cr, active, 1, now);
                    } else {
                        if (DBG) Log.d(TAG, "server does not exist: " + serverFile.getDisplayPath());
                        updateServerDb(id, cr, active, 0, now);
                    }
                }
                c.close();
                // notify about a change in the db
                cr.notifyChange(NOTIFY_URI, null);
            } else if (DBG) {
                Log.d(TAG, "server query returned NULL");
            }
        } else {
            if (DBG) Log.d(TAG, "setting all smb servers inactive");
            // no more smb servers.
            ContentValues cv = new ContentValues(1);
            cv.put(MusicStore.SmbServer.SmbServerColumns.ACTIVE, "0");
            // update everything with inactive.
            cr.update(SERVER_URI, cv, SELECTION_SMB, null);
            // and tell the world
            cr.notifyChange(NOTIFY_URI, null);
        }
    }

    private final static void updateServerDb(long id, ContentResolver cr, int oldState,
            int newState, long time) {
        if (oldState == newState) return;
        ContentValues cv = new ContentValues();
        cv.put(MusicStore.SmbServer.SmbServerColumns.ACTIVE, String.valueOf(newState));
        if (newState != 0) {
            // update last seen only if it's active now
            cv.put(MusicStore.SmbServer.SmbServerColumns.LAST_SEEN, String.valueOf(time));
        }
        String[] selectionArgs = new String[] {
            String.valueOf(id)
        };
        if (DBG) Log.d(TAG, "DB: update server: " + id + " values:" + cv);
        cr.update(SERVER_URI, cv, SELECTION_ID, selectionArgs);
    }
}
