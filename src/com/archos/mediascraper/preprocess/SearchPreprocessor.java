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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SearchPreprocessor {
    private static final Logger log = LoggerFactory.getLogger(SearchPreprocessor.class);

    private SearchPreprocessor() {
        // simple singleton
    }

    private static final SearchPreprocessor INSTANCE = new SearchPreprocessor();

    public static SearchPreprocessor instance() {
        return INSTANCE;
    }

    private static final List<InputMatcher> PARSERS =
            new ArrayList<InputMatcher>();
    static {
        // 1st priority is tv shows
        PARSERS.add(TvShowMatcher.instance());
        PARSERS.add(TvShowFolderMatcher.instance());
        PARSERS.add(TvShowPathMatcher.instance());
        // then movies
        PARSERS.add(MovieVerbatimMatcher.instance());
        PARSERS.add(MovieDVDMatcher.instance());
        PARSERS.add(MoviePathMatcher.instance());
        PARSERS.add(MovieSceneMatcher.instance());
        // fallback to default that matches everything
        PARSERS.add(MovieDefaultMatcher.instance());
    }

    /**
     * Parses movie name and other information based on the file
     * @param uri must not be null
     * @return Either {@link MovieSearchInfo} or {@link TvShowSearchInfo}
     */
    public SearchInfo parseFileBased(Uri uri, Uri simplifiedUri) {
        for (InputMatcher matcher : PARSERS) {
            if (matcher.matchesFileInput(uri, simplifiedUri)) {
                SearchInfo result = matcher.getFileInputMatch(uri, simplifiedUri);
                if (result == null) {
                    throw new AssertionError("Matcher:" + matcher.getMatcherName() + " returned null file:" + uri.toString());
                }
                log.debug("result from" + matcher.getMatcherName());
                return reParseInfo(result);
            }
        }
        // default to something - should not happen
        log.error("parse error, no matcher");
        return new MovieSearchInfo(uri, FileUtils.getFileNameWithoutExtension(uri), null);
    }

    /**
     * Not for public consumption, used inside Scraper
     * <p>
     * Checks if info needs to be re-parsed because user-input changed the
     * suggestion or the original parser want's it to be parsed again
     * @param info
     * @return either original info or a newly created one
     */
    public SearchInfo reParseInfo(SearchInfo info) {
        if (info.needsReParse()) {
            Uri file = info.getFile();
            String userInput = info.getUserInput();
            if (userInput == null) {
                userInput = info.getSearchSuggestion();
            }
            for (InputMatcher matcher : PARSERS) {
                if (matcher.matchesUserInput(userInput)) {
                    SearchInfo result = matcher.getUserInputMatch(userInput, file);
                    if (result == null) {
                        throw new AssertionError("Matcher:" + matcher + " returned null userinput:" + userInput);
                    }
                    log.debug("re-parse result from" + matcher.getMatcherName());
                    return reParseInfo(result);
                }
            }
            // default to something - should not happen
            log.error("re-parse error, no matcher");
            return new MovieSearchInfo(file, userInput, null);
        }
        // if not modified return original input
        log.debug("re-parse no-op");
        return info;
    }
}
