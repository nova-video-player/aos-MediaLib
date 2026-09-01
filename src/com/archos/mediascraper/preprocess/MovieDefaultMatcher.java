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
import android.util.Pair;

import com.archos.filecorelibrary.FileUtils;
import com.archos.mediascraper.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.regex.Pattern;

import static com.archos.mediascraper.preprocess.ParseUtils.BRACKETS;
import static com.archos.mediascraper.preprocess.ParseUtils.extractYearAnywhere;
import static com.archos.mediascraper.preprocess.ParseUtils.isPlausibleYear;
import static com.archos.mediascraper.preprocess.ParseUtils.removeAfterEmptyParenthesis;

/**
 * Matches Movies
 * <p>
 * Title & Year specifically
 */
class MovieDefaultMatcher implements InputMatcher {
    private static final Logger log = LoggerFactory.getLogger(MovieDefaultMatcher.class);

    private static final MovieDefaultMatcher INSTANCE = new MovieDefaultMatcher();

    public static MovieDefaultMatcher instance() {
        return INSTANCE;
    }

    private MovieDefaultMatcher() {
        // singleton
    }

    @Override
    public boolean matchesFileInput(Uri fileInput, Uri simplifiedUri) {
        return true;
    }

    @Override
    public boolean matchesUserInput(String userInput) {
        return true;
    }

    @Override
    public SearchInfo getFileInputMatch(Uri file, Uri simplifiedUri) {
        if(simplifiedUri!=null)
            file = simplifiedUri;
        String input = FileUtils.getFileNameWithoutExtension(file);
        return getMatch(input, file);
    }

    @Override
    public SearchInfo getUserInputMatch(String userInput, Uri file) {
        return getMatch(userInput, file);
    }

    private static final Pattern KNOWN_EXTENSIONS = Pattern.compile("\\.(mkv|avi|mp4|mov|wmv|flv|mpg|mpeg|vob|asf|divx|f4v|ts|m2ts|m4v|webm|ogv|3gp)$", Pattern.CASE_INSENSITIVE);

    private static SearchInfo getMatch(String input, Uri file) {
        // Strip known video extensions (input may still have one from getUserInputMatch or tests)
        String name = KNOWN_EXTENSIONS.matcher(input).replaceFirst("");
        if (log.isDebugEnabled()) log.debug("getMatch input: {}", name);
        final int currentYear = Calendar.getInstance().get(Calendar.YEAR);

        // Capture original name after extension removal but before numbering/year stripping
        String originalName = name; 

        // Strip out starting numbering for collections "1. ", "1) ", "1 - "... restricted to 3 digits
        name = ParseUtils.removeNumbering(name);
        name = ParseUtils.removeNumberingDash(name);

        String year = null;
        boolean yearConfident = false;
        boolean yearAtStart = false;

        // Step 1: try parenthesisYearExtractor - if matches, it's a confident release year
        Pair<String, String> nameYear = ParseUtils.parenthesisYearExtractorTitleOnly(name);
        if (isPlausibleYear(nameYear.second, nameYear.first, currentYear)) {
            name = nameYear.first;
            year = nameYear.second;
            yearConfident = true;
        }

        // Step 2: remove everything in brackets
        if (year == null) {
            name = StringUtils.replaceAll(name, "", BRACKETS);
        } else {
            name = removeAfterEmptyParenthesis(name);
            name = StringUtils.replaceAll(name, "", BRACKETS);
        }

        // Step 3: Backwards-looping "Anywhere" extraction (Leeroy's logic)
        if (year == null || year.isEmpty()) {
            nameYear = extractYearAnywhere(name, currentYear);
            if (nameYear.second != null) {
                // Determine if year was at start
                int cutIndex = name.indexOf(nameYear.second);
                if (cutIndex == 0) {
                    // Identified at start - strip from 'name' but keep 'yearAtStart' for scraper logic
                    name = nameYear.first; // Remainder (cleaned)
                    year = nameYear.second;
                    yearAtStart = true;
                } else {
                    // Identified in middle/end - strip from 'name'
                    name = nameYear.first;
                    year = nameYear.second;
                }
            }
        }

        // strip away known case sensitive garbage
        name = ParseUtils.cutOffBeforeFirstMatch(name, ParseUtils.GARBAGE_CASESENSITIVE_PATTERNS);

        // replace all remaining whitespace & punctuation with a single space
        name = ParseUtils.removeInnerAndOutterSeparatorJunk(name);

        // append a " " to aid next step
        name = name + " ";

        // try to remove more garbage, this time " garbage " syntax
        name = ParseUtils.cutOffBeforeFirstMatch(name, ParseUtils.GARBAGE_LOWERCASE);

        name = name.trim();
        // Clean originalName through the same garbage pipeline so it can be used as a TMDB query
        // (year stays embedded for "unstripped" searches per SCRAPE.md)
        originalName = ParseUtils.cutOffBeforeFirstMatch(originalName, ParseUtils.GARBAGE_CASESENSITIVE_PATTERNS);
        originalName = ParseUtils.removeInnerAndOutterSeparatorJunk(originalName);
        originalName = ParseUtils.cutOffBeforeFirstMatch(originalName + " ", ParseUtils.GARBAGE_LOWERCASE).trim();
        return new MovieSearchInfo(file, name, year, originalName, yearConfident, yearAtStart);
    }

    @Override
    public String getMatcherName() {
        return "MovieDefault";
    }

}
