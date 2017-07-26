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

package com.archos.mediascraper.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Hashtable;

/*
 * Class containing all the settings for a given scraper, and all the necessary
 * logic to use them.
 */
public class ScraperSettings {
    private static final String TAG = ScraperSettings.class.getSimpleName();
    private static final boolean DEBUG = false;


    private SharedPreferences mPreferences;
    private Hashtable<String, ScraperSetting> mSettings;

    public ScraperSettings(Context context, String preferenceName) {
        mSettings = new Hashtable<String, ScraperSetting>();
        mPreferences = context.getSharedPreferences(preferenceName,
                Context.MODE_PRIVATE);
    }

    public void addAll(ScraperSettings settings) {
        if(settings == null)
            return;
        mSettings.putAll(settings.getSettings());
    }

    public void addSetting(String key, ScraperSetting setting) {
        mSettings.put(key, setting);
    }

    public ScraperSetting getSetting(String key) {
        return mSettings.get(key);
    }

    public Hashtable<String, ScraperSetting> getSettings() {
        return mSettings;
    }

    @Override
    public String toString() {
        return "Default Settings : " + mSettings;
    }

    public boolean getBoolean(String key) {
        ScraperSetting setting = mSettings.get(key);
        boolean value = mPreferences.getBoolean(key, setting.getBooleanDefault());
        if(DEBUG) Log.d(TAG, "Condition " + key + " asked, value is " + value);
        return value;
    }

    public String getString(String key) {
        if (key == null) {
            if (DEBUG) Log.d(TAG, "getString: key must not be null.");
            return null;
        }
        ScraperSetting setting = mSettings.get(key);
        if (setting == null) {
            Log.d(TAG, "Settings key [" + key + "] does not exist.");
            return null;
        }
        String def = setting.getStringDefault();
        if (def == null) {
            Log.d(TAG, "no default for [" + key + "]");
            return null;
        }
        return mPreferences.getString(key, def);
    }
}
