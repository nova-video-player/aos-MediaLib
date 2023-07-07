// Copyright 2018 Courville Software
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

import com.archos.mediacenter.utils.ISO639codes;
import com.archos.medialib.R;
import com.archos.mediascraper.xml.BaseScraper2;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Locale;
import java.util.List;

/*
 * Preference screen generator.
 * Generate on-the-fly preference screen for a scraper, relying on its GetSettings
 * function.
 */


public class ScraperPreferencesFragment extends PreferenceFragmentCompat {

    private PreferenceManager mPm;
    private BaseScraper2 mScraper;
    private ScraperSettings mSettings;
    int type;

    public static final String FRAGMENT_TAG = "mScraperPreferenceFragment";

    public ScraperPreferencesFragment() { }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        if (getArguments() != null) {
            type = Integer.parseInt(getArguments().getString("type"));
            mSettings = BaseScraper2.getSettings(type, getActivity());
            mScraper = BaseScraper2.getScraper(type, getActivity());
            generatePreferenceScreen();
        }
    }

    private void generatePreferenceScreen() {
        // We use a different preference file for each scraper, so we need to
        // change it.
        mPm = getPreferenceManager();
        mPm.setSharedPreferencesName(mScraper.getName());

        PreferenceScreen screen = createPreferenceHierarchy();
        if (screen != null) {
            setPreferenceScreen(screen);
        } else {
            getActivity().setContentView(R.layout.no_settings);
        }
    }

    private PreferenceScreen createPreferenceHierarchy() {

        if(mPm == null || mScraper == null)
            return null;

        PreferenceScreen root = mPm.createPreferenceScreen(getActivity());
        SharedPreferences preferences = mPm.getSharedPreferences();
        ScraperSettings settings = mSettings;

        if(settings == null)
            return null;

        // Get the settings available for this scraper.
        Hashtable<String, ScraperSetting> hashSettings = settings.getSettings();

        if(hashSettings == null || hashSettings.size() == 0)
            return null;

        /*
         * We iterate on the settings to generate an adequate object, ie. a
         * checkbox for a boolean, a text input for text, a list for an enum, etc.
         */
        for(Enumeration<String> e = hashSettings.keys(); e.hasMoreElements();) {
            String key = e.nextElement();
            ScraperSetting value = hashSettings.get(key);
            switch(value.getType()) {
                case ScraperSetting.TYPE_BOOL:
                    CheckBoxPreference togglePref = new CheckBoxPreference(getActivity());
                    togglePref.setKey(value.getId());
                    togglePref.setTitle(value.getLabel());
                    togglePref.setDefaultValue(preferences.getBoolean(value.getId(),
                            value.getBooleanDefault()));
                    root.addPreference(togglePref);
                    break;

                case ScraperSetting.TYPE_INTEGER:
                    EditTextPreference intPref = new EditTextPreference(getActivity());
                    intPref.setKey(value.getId());
                    intPref.setTitle(value.getLabel());
                    intPref.setDefaultValue(preferences.getInt(value.getId(),
                            value.getIntDefault()));
                    root.addPreference(intPref);
                    break;

                case ScraperSetting.TYPE_TEXT:
                    EditTextPreference textPref = new EditTextPreference(getActivity());
                    textPref.setKey(value.getId());
                    textPref.setTitle(value.getLabel());
                    textPref.setDefaultValue(preferences.getString(value.getId(),
                            value.getStringDefault()));
                    root.addPreference(textPref);
                    break;

                case ScraperSetting.TYPE_LABELENUM:
                    // this is the Language Setting.. the only one that is of type labelenum
                    List<String> items = value.getValues();
                    Hashtable<String, String> values = new Hashtable<String, String>();
                    String userLocale = Locale.getDefault().getLanguage();
                    String localeDefault = null;
                    for(String item: items) {
                        if (userLocale.equals(item)) {
                            localeDefault = item;
                        }
                        values.put(ISO639codes.getLanguageNameFor2LetterCode(item), item);
                    }
                    String[] keys = values.keySet().toArray(new String[items.size()]);
                    Arrays.sort(keys);
                    String[] display = new String[items.size()];
                    for(int i = 0; i < items.size(); i++)
                        display[i] = values.get(keys[i]);
                    ListPreference enumPref = new ListPreference(getActivity());
                    enumPref.setKey(value.getId());
                    enumPref.setTitle(value.getLabel());
                    enumPref.setEntries(keys);
                    enumPref.setEntryValues(display);
                    if (localeDefault != null) {
                        enumPref.setDefaultValue(preferences.getString(value.getId(),
                                localeDefault));
                    } else {
                        enumPref.setDefaultValue(preferences.getString(value.getId(),
                                value.getStringDefault()));
                    }
                    root.addPreference(enumPref);
                    break;
            }
        }

        return root;
    }
}
