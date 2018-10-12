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

import com.archos.medialib.R;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;

/*
 * Preference screen generator.
 * Generate on-the-fly preference screen for a scraper, relying on its GetSettings
 * function.
 */

public class ScraperPreferences extends AppCompatActivity {
    /*
     * Setup the Preference Screen.
     * Load the scraper based on the media type sent through the Intent's extras.
     * Load the Preference Manager, etc.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int type = getIntent().getIntExtra("media", 0);

        setContentView(R.layout.scraper_preferences_fragment);

        Bundle mBundle = new Bundle();
        mBundle.putString("type", String.valueOf(type));
        Fragment mScraperPreferencesFragment = getSupportFragmentManager().findFragmentByTag(ScraperPreferencesFragment.FRAGMENT_TAG);
        if (mScraperPreferencesFragment == null) {
            mScraperPreferencesFragment = new ScraperPreferencesFragment();
        }
        mScraperPreferencesFragment.setArguments(mBundle);

        FragmentTransaction mFragmentTransaction = getSupportFragmentManager().beginTransaction();
        mFragmentTransaction.replace(R.id.fragment_container, mScraperPreferencesFragment, ScraperPreferencesFragment.FRAGMENT_TAG);
        mFragmentTransaction.commit();
    }
}