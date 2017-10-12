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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Matches "" enclosed strings as verbatim movie title, user input only
 */
class MovieVerbatimMatcher implements InputMatcher {

    public static MovieVerbatimMatcher instance() {
        return INSTANCE;
    }

    private static final MovieVerbatimMatcher INSTANCE =
            new MovieVerbatimMatcher();

    private MovieVerbatimMatcher() {
        // singleton
    }

    // matches if stuff is enclosed in "" with only spaces or nothing around it
    private static final String VERBATIM_GROUP = "\\s*\"(.*)\"\\s*";
    private static final Pattern VERBATIM_GROUP_PATTERN = Pattern.compile(VERBATIM_GROUP);

    @Override
    public boolean matchesFileInput(Uri fileInput, Uri simplifiedUri) {
        // does not match file intput, verbatim may come only from user input
        return false;
    }

    @Override
    public boolean matchesUserInput(String userInput) {
        return VERBATIM_GROUP_PATTERN.matcher(userInput).matches();
    }

    @Override
    public SearchInfo getFileInputMatch(Uri file, Uri simplifiedUri) {
        // does not match file input, user input only
        return null;
    }

    @Override
    public SearchInfo getUserInputMatch(String userInput, Uri file) {
        Matcher matcher = VERBATIM_GROUP_PATTERN.matcher(userInput);
        if (matcher.matches()) {
            return new MovieSearchInfo(file, matcher.group(1), null);
        }
        return null;
    }

    @Override
    public String getMatcherName() {
        return "MovieVerbatim";
    }

}

