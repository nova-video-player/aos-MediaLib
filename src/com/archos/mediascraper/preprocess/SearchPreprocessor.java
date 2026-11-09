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
import java.util.Locale;
import java.util.regex.Pattern;

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
        return parseFileBased(uri, simplifiedUri, false);
    }

    private SearchInfo parseFileBased(Uri uri, Uri simplifiedUri, boolean obfuscatedName) {
        String candidate = FileUtils.getFileNameWithoutExtension(uri);
        for (InputMatcher matcher : PARSERS) {
            // UUID fragments such as "-e29" can look like episode markers. Still allow
            // release-folder/movie-folder detection, which does not depend on the basename.
            if (obfuscatedName && (matcher == TvShowMatcher.instance()
                    || matcher instanceof TvShowPathMatcher || matcher instanceof MovieSceneMatcher)) continue;
            if (log.isDebugEnabled()) log.debug("parseFileBased: trying parser {} for {} derived from uri {} and simplifiedUri {}",
                    matcher.getMatcherName(), candidate,
                    (uri != null) ? uri.getPath() : null,
                    (simplifiedUri != null) ? simplifiedUri.getPath() : null);
            if (matcher.matchesFileInput(uri, simplifiedUri)) {
                SearchInfo result = matcher.getFileInputMatch(uri, simplifiedUri);
                if (result == null) {
                    // Matcher claimed to match but returned null - log error and try next matcher
                    // This can happen with false positives (e.g., folder name looks like TV show but isn't)
                    log.error("parseFileBased: Matcher {} returned null despite matching file {}",
                            matcher.getMatcherName(), (uri != null) ? uri.toString() : null);
                    continue; // Try next matcher instead of crashing
                }
                if (log.isDebugEnabled()) log.debug("parseFileBased: result from {} for {} -> {}",
                        matcher.getMatcherName(), candidate, result.getSearchSuggestion());
                result = reParseInfo(result);
                result.setFallbackMatch(matcher instanceof MovieDefaultMatcher);
                if (ParseUtils.isNonScrapableFolder(uri) || ParseUtils.isNonScrapableFolder(simplifiedUri)) {
                    if (log.isDebugEnabled()) log.debug("parseFileBased: {} is in a bonus/extras folder, marking skipScraping", candidate);
                    result.skipScraping = true;
                }
                return result;
            }
        }
        // default to something - should not happen
        log.error("parseFileBased: parse error, no matcher for {}", candidate);
        MovieSearchInfo fallback = new MovieSearchInfo(uri, candidate, null);
        fallback.setFallbackMatch(true);
        return fallback;
    }

    /**
     * Parses a file preferring its real name/path (the release name) over the stored metadata
     * title, which may be a localized on-screen title rather than a release name (issue #1987).
     * <p>
     * A default movie match is not evidence of an unusable filename: ordinary movie names
     * also reach that matcher. Only recognizable obfuscated names may fall back to metadata.
     * UPNP object IDs and content URIs without a video filename use the title directly.
     *
     * @param fileUri       the real file URI, must not be null
     * @param metadataTitle the embedded/stored title, may be null or empty
     */
    public SearchInfo parseFileBasedWithTitleFallback(Uri fileUri, String metadataTitle) {
        if (fileUri == null) throw new IllegalArgumentException("fileUri must not be null");
        String title = metadataTitle == null ? "" : metadataTitle.trim();
        boolean hasTitle = !title.isEmpty() && !title.equalsIgnoreCase("null");
        String scheme = fileUri.getScheme();
        boolean content = "content".equalsIgnoreCase(scheme);
        boolean upnp = scheme != null && scheme.toLowerCase(Locale.ROOT).startsWith("upnp");
        String name = FileUtils.getName(fileUri);
        // A SAF document ID may contain an entire decoded path in its last URI segment.
        if (content) name = FileUtils.getName(name);
        boolean opaque = upnp || (content && !ParseUtils.hasVideoExtension(name));

        SearchInfo result;
        if (opaque) {
            result = parseTitle(fileUri, hasTitle ? title : name);
        } else {
            String stem = FileUtils.stripExtensionFromName(name);
            boolean obfuscatedName = isObfuscatedName(stem);
            // Do not feed provider IDs to the folder matchers, which expect filesystem paths.
            if (content && obfuscatedName) {
                result = MovieDefaultMatcher.instance().getUserInputMatch(stem, fileUri);
                result.setFallbackMatch(true);
            } else {
                result = content ? parseTitle(fileUri, stem)
                        : parseFileBased(fileUri, null, obfuscatedName);
            }
            if (hasTitle && result.isFallbackMatch() && obfuscatedName) {
                SearchInfo fallback = parseTitle(fileUri, title);
                if (!fallback.getSearchSuggestion().isEmpty()) result = fallback;
            }
        }
        result.setOriginalUri(fileUri);
        result.skipScraping |= ParseUtils.isNonScrapableFolder(fileUri);
        return result;
    }

    private static final Pattern OBFUSCATED_NAME = Pattern.compile(
            "(?i)(?:[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"
                    + "|(?=[a-f0-9]*[a-f])(?=[a-f0-9]*[0-9])[a-f0-9]{8,}"
                    + "|[a-f0-9]{16,})");

    private static boolean isObfuscatedName(String name) {
        // Intentionally conservative: short/numeric titles (e.g. 1917) and readable words win.
        return name.trim().isEmpty() || OBFUSCATED_NAME.matcher(name).matches();
    }

    private SearchInfo parseTitle(Uri fileUri, String title) {
        // Parse text directly: '/', '?', '#' and '%' must never become URI syntax, and
        // SearchInfo.getFile() must continue to identify the actual media file.
        for (InputMatcher matcher : PARSERS) {
            if (matcher instanceof MovieVerbatimMatcher || !matcher.matchesUserInput(title)) continue;
            SearchInfo result = matcher.getUserInputMatch(title, fileUri);
            if (result != null) {
                result.setFallbackMatch(matcher instanceof MovieDefaultMatcher);
                return result;
            }
        }
        throw new IllegalStateException("MovieDefaultMatcher must accept every title");
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

            //We need to make sure there are no NULL problems here especially for UPNP.
            String[] candidates = {
                    info.getUserInput(),
                    info.getSearchSuggestion()
            };

            //Check Search Suggestion, Name and fallback to filename.
            String searchQuery = FileUtils.getFileNameWithoutExtension(file);
            for (String candidate : candidates) {
                if (!(candidate == null || candidate.isBlank() || candidate.equalsIgnoreCase("null"))) {
                    searchQuery = candidate;
                    break; // first valid match wins, fallback to file name.
                }
            }

            for (InputMatcher matcher : PARSERS) {
                if (matcher.matchesUserInput(searchQuery)) {
                    SearchInfo result = matcher.getUserInputMatch(searchQuery, file);
                    if (result == null) {
                        // Matcher claimed to match but returned null - log error and try next matcher
                        log.error("reParseInfo: Matcher {} returned null for user input: {}",
                                matcher.getMatcherName(), searchQuery);
                        continue; // Try next matcher instead of crashing
                    }
                    if (log.isDebugEnabled()) log.debug("re-parse result from {}", matcher.getMatcherName());
                    result.skipScraping = info.skipScraping;
                    result.setOriginalUri(info.getOriginalUri());
                    result.setFallbackMatch(matcher instanceof MovieDefaultMatcher);
                    return result;
                }
            }
            // default to something - should not happen
            log.error("re-parse error, no matcher");
            return new MovieSearchInfo(file, searchQuery, null);
        }
        // if not modified return original input
        if (log.isDebugEnabled()) log.debug("re-parse no-op");
        return info;
    }
}
