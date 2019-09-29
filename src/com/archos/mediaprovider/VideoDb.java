package com.archos.mediaprovider;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.archos.mediaprovider.video.VideoOpenHelper;

public class VideoDb {
    private static volatile DbHolder instance;

    public static DbHolder getHolder(Context context) {
        DbHolder result = instance;
        if (result == null) {
            synchronized (VideoDb.class) {
                result = instance;
                if (result == null) {
                    result = instance = new DbHolder(new VideoOpenHelper(context.getApplicationContext()));
                }
            }
        }
        return result;
    }

    public static SQLiteDatabase get(Context context) {
        return getHolder(context).get();
    }

}
