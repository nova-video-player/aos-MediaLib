// Copyright 2026 Courville Software
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
