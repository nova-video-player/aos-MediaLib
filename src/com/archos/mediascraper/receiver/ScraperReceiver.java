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

package com.archos.mediascraper.receiver;

import com.archos.environment.ArchosIntents;
import com.archos.mediascraper.MediaScraper;
import com.archos.mediascraper.Scraper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;

import java.io.File;

public class ScraperReceiver extends BroadcastReceiver {
    private final static String TAG = "ScraperReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        if (action.equals(ArchosIntents.MEDIA_LIBRARY_FLUSH)) {
            deleteDatabases(context);
            deleteAllExternalFiles(context);
        } else if (ArchosIntents.MEDIASCANNER_MEDIASCRAPER_RESET.equals(action)) {
            resetDefaultScraperData(context);
        }
    }

    private static void deleteDatabases(Context context) {
        String[] dbList = context.databaseList();
        if (dbList != null) {
            for (String file : dbList) {
                if (!context.deleteDatabase(file)) {
                    Log.e(TAG, "Unable to delete database " + file + ".");
                }
            }
        }
    }

    private static void deleteAllExternalFiles(Context context) {
        deleteDirectory(MediaScraper.getPosterDirectory(context));
        deleteDirectory(MediaScraper.getImageCacheDirectory(context));
        deleteDirectory(MediaScraper.getXmlCacheDirectory(context));
        deleteDirectory(MediaScraper.getBackdropDirectory(context));
        deleteDirectory(MediaScraper.getBackdropCacheDirectory(context));
    }

    private final static void deleteDirectory (File directory) {
        try {
            if (directory.isDirectory()) {
                File[] files = directory.listFiles();
                if (files != null) {
                    for (File file: files) {
                        if (!file.delete()) {
                            Log.e(TAG, "Unable to delete file " + file.getName() + ".");
                        }
                    }
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Unable to delete the file / directory due to a security exception.", e);
        }
    }

    private static void resetDefaultScraperData(Context context) {
        final Context localContext = context;

        // in background since that is blocking for a short time
        AsyncTask.execute(new Runnable() {
            public void run() {
                Scraper scraper = new Scraper(localContext);
                scraper.setupDefaultContent(true);
            }
        });
    }

}
