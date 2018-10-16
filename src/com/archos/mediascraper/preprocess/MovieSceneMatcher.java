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

import com.archos.filecorelibrary.FileUtils;
import com.archos.mediascraper.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Matches typical scene rls names, e.g. "The.Movie.1996.x264-GRP"
 */
class MovieSceneMatcher implements InputMatcher {

    public static MovieSceneMatcher instance() {
        return INSTANCE;
    }

    private static final MovieSceneMatcher INSTANCE =
            new MovieSceneMatcher();

    private MovieSceneMatcher() {
        // singleton
    }

    @Override
    public boolean matchesFileInput(Uri fileInput, Uri simplifiedUri) {
        if(simplifiedUri!=null)
            fileInput = simplifiedUri;
        return matches(FileUtils.getFileNameWithoutExtension(fileInput));
    }

    @Override
    public boolean matchesUserInput(String userInput) {
        return matches(userInput);
    }

    private static boolean matches(String matchString) {
        if (matchString == null || matchString.isEmpty())
            return false;
        return NAME_YEAR_SCENE_PATTERN.matcher(matchString).matches();
    }

    @Override
    public SearchInfo getFileInputMatch(Uri file, Uri simplifiedUri) {
        if(simplifiedUri!=null)
            file = simplifiedUri;
        return getSearchInfo(FileUtils.getFileNameWithoutExtension(file), file);
    }

    @Override
    public SearchInfo getUserInputMatch(String userInput, Uri file) {
        return getSearchInfo(userInput, file);
    }

    private static SearchInfo getSearchInfo(String matchString, Uri file) {
        Matcher m = NAME_YEAR_SCENE_PATTERN.matcher(matchString);
        if (m.matches()) {
            String name = StringUtils.replaceAll(m.group(1), " ", JUNK_PATTERN);
            name = ParseUtils.removeInnerAndOutterSeparatorJunk(name);
            String year = m.group(2);
            return new MovieSearchInfo(file, name, year);
        }
        return null;
    }

    @Override
    public String getMatcherName() {
        return "MovieScene";
    }

    // some junk that sometimes appears before the year
    private static final String JUNK =
            "(?i)(?:(?:DIR(?:ECTORS)?|EXTENDED)[\\s\\p{Punct}]?CUT|UNRATED|THEATRICAL[\\s\\p{Punct}]?EDITION)";
    private static final Pattern JUNK_PATTERN = Pattern.compile(JUNK);

    // NOT ( whitespace | punctuation), matches A-Z, 0-9, localized characters etc
    private static final String CHARACTER = "[^\\s\\p{Punct}]";
    // ( whitespace | punctuation), matches dots, spaces, brackets etc
    private static final String NON_CHARACTER = "[\\s\\p{Punct}]";
    // matches "word"
    private static final String CHARACTER_GROUP = CHARACTER + "+";
    // matches shortest "word.word.word."
    private static final String SEPARATED_CHARTER_GROUPS = "(?:" + CHARACTER_GROUP + NON_CHARACTER + ")+?";
    // matches "19XX" and "20XX" - capture group
    private static final String YEAR_GROUP = "((?:19|20)\\d{2})";
    // matches "word.word.word." - capture group 
    private static final String MOVIENAME_GROUP = "(" + SEPARATED_CHARTER_GROUPS + ")";
    // matches ".junk.junk.junk"
    private static final String REMAINING_JUNK = "(?:" + NON_CHARACTER + CHARACTER_GROUP + ")+";
    // matches "Movie.Name.2011.JUNK.JUNK.avi"
    private static final String MOVIENAME_YEAR_JUNK = MOVIENAME_GROUP + YEAR_GROUP + REMAINING_JUNK;
    private static final Pattern NAME_YEAR_SCENE_PATTERN = Pattern.compile(MOVIENAME_YEAR_JUNK);

}
