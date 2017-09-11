package com.archos.mediaprovider.video;

import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * Created by alexandre on 12/05/17.
 */

public class ListTables {

    public static final String LIST_TABLE = "list_table";
    public static final String VIDEO_LIST_TABLE = "video_list_table";

    private static final String LIST_TABLE_CREATE =
            "CREATE TABLE " + LIST_TABLE + " (" +
                    VideoStore.List.Columns.ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    VideoStore.List.Columns.TITLE + " TEXT," +
                    VideoStore.List.Columns.TRAKT_ID + " INTEGER UNIQUE ON CONFLICT REPLACE," +
                    VideoStore.List.Columns.SYNC_STATUS + " INTEGER" +
                    ")";

    //table with links between lists and videos
    private static final String VIDEO_LIST_TABLE_CREATE =
            "CREATE TABLE " + VIDEO_LIST_TABLE + " (" +
                    VideoStore.VideoList.Columns.ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"+
                    VideoStore.VideoList.Columns.LIST_ID + " INTEGER REFERENCES " + LIST_TABLE + "(" + VideoStore.List.Columns.ID + ") ON DELETE CASCADE,"+
                    VideoStore.VideoList.Columns.M_ONLINE_ID+" INTEGER,"+
                    VideoStore.VideoList.Columns.E_ONLINE_ID+" INTEGER,"+
                    VideoStore.List.Columns.SYNC_STATUS + " INTEGER" + "," +
                    "UNIQUE("+VideoStore.VideoList.Columns.LIST_ID+","+VideoStore.VideoList.Columns.M_ONLINE_ID+","+VideoStore.VideoList.Columns.E_ONLINE_ID+"))";

    public static void upgradeTo(SQLiteDatabase db, int oldDbVersion) {
        db.execSQL(LIST_TABLE_CREATE);
        Log.d("create",LIST_TABLE_CREATE);
        db.execSQL(VIDEO_LIST_TABLE_CREATE);
    }
}
