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


package com.archos.mediascraper.preprocess;

import android.net.Uri;
import com.archos.mediascraper.Scraper;
import java.text.Normalizer;

/**
 * Holds search relevant information
 * if {@link #isTvShow()} returns true cast to {@link TvShowSearchInfo}
 * otherwise to {@link MovieSearchInfo}
 * <p>
 * Usage: get an instance of this via {@link SearchPreprocessor} then display
 * the information from {@link #getSearchSuggestion()}.
 * Before passing it back to {@link Scraper} call {@link #setUserInput(String)}
 * with whatever the user may have entered.
 * <p>
 * If user-input differs from search suggestion then scraper will parse the changed
 * input.
 */
public abstract class SearchInfo {

    private final Uri mUri;
    private String mSearchSuggestion;
    private String mUserInput;
    private boolean mForceReParse;

    /** package private so outside can't construct these */
    protected SearchInfo(Uri uri) {
        if (uri == null)
            throw new AssertionError("SearchInfo needs a file");
        mUri = uri;
    }

    /**
     * In case user can edit the search suggestion set the edited text here so scraper
     * can do the right thing
     */
    public final void setUserInput(String userInput) {
        mUserInput = userInput;

    }

    protected final String getUserInput() {
        return mUserInput;
    }

    public final Uri getFile() {
        return mUri;
    }

    protected abstract String createSearchSuggestion();

    /**
     * If user should be able to see what the search will search display this.
     */
    public final String getSearchSuggestion() {
        if (mSearchSuggestion == null) {
            mSearchSuggestion = createSearchSuggestion();
        }
        return mSearchSuggestion;
    }

    final void setForceReParse(boolean state) {
        mForceReParse = state;
    }

    final boolean needsReParse() {
        if (mForceReParse) {
            return true;
        }
        String userInput = getUserInput();
        if (userInput != null && !userInput.isEmpty()) {
            return !getSearchSuggestion().equals(userInput);
        }
        // in case there is no valid user input assume it has not changed
        return false;
    }

    public abstract boolean isTvShow();
}
