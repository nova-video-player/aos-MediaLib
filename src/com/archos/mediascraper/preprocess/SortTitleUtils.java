// Copyright 2026 Courville Software
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

import android.text.TextUtils;

import com.archos.mediacenter.utils.ISO639codes;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes movie and TV show titles for alphabetical sorting by moving leading
 * grammatical articles to the end (e.g. "The Matrix" -> "Matrix, The").
 * Applies strict per-language matching when title language is known, and avoids
 * false positives by leaving titles unaltered when language is undetermined ("und").
 */
public final class SortTitleUtils {

    private SortTitleUtils() { }

    // Space-delimited articles per language (must be followed by whitespace \s+)
    private static final Pattern ENGLISH_SPACE_PATTERN = Pattern.compile("^(?i)(a|an|the|ye)\\s+(.+)$");
    private static final Pattern FRENCH_SPACE_PATTERN = Pattern.compile("^(?i)(le|la|les|un|une|des|du)\\s+(.+)$");
    private static final Pattern GERMAN_SPACE_PATTERN = Pattern.compile("^(?i)(der|die|das|dem|den|des|ein|eine|einer|einem|einen|eines)\\s+(.+)$");
    private static final Pattern SPANISH_SPACE_PATTERN = Pattern.compile("^(?i)(el|la|los|las|lo|un|una|unos|unas)\\s+(.+)$");
    private static final Pattern ITALIAN_SPACE_PATTERN = Pattern.compile("^(?i)(il|lo|la|i|gli|le|un|uno|una|dei|degli|delle)\\s+(.+)$");
    private static final Pattern PORTUGUESE_SPACE_PATTERN = Pattern.compile("^(?i)(o|a|os|as|um|uma|uns|umas)\\s+(.+)$");
    private static final Pattern DUTCH_SPACE_PATTERN = Pattern.compile("^(?i)(de|het|een|eene|eenen)\\s+(.+)$");

    // Apostrophe-delimited articles per language (attached directly to next word with standard or curly apostrophe)
    private static final Pattern FRENCH_APOSTROPHE_PATTERN = Pattern.compile("^(?i)(l|d)['’‘](.+)$");
    private static final Pattern ITALIAN_APOSTROPHE_PATTERN = Pattern.compile("^(?i)(l|un|d)['’‘](.+)$");
    private static final Pattern DUTCH_APOSTROPHE_PATTERN = Pattern.compile("^(?i)('t|'n|’t|’n)\\s+(.+)$");

    private static final Pattern ENGLISH_FALLBACK_PATTERN = Pattern.compile("^(?i)(an|the|ye)\\s+(.+)$");

    /**
     * Extracts the sort title for a given title and its language.
     * When title language is known and supported, applies that language's rules, falling back to English.
     * When language is unknown ("und", null, or unsupported), falls back to system locale rules then English.
     *
     * @param title Raw title to format
     * @param language ISO 639-1 language code of the title (e.g. "en", "fr", "de"), or "und"
     * @return Formatted sort title ("Title, Article") or raw title
     */
    public static String extractSortTitle(String title, String language) {
        if (TextUtils.isEmpty(title)) {
            return title == null ? "" : title;
        }

        String trimmed = title.trim();
        if (trimmed.isEmpty()) {
            return title;
        }

        String lang = normalizeLanguage(language);
        if ("und".equals(lang)) {
            // For legacy / untagged databases, try system default locale first, then fall back to English
            String defaultLocaleLang = normalizeLanguage(java.util.Locale.getDefault().getLanguage());
            if (!"und".equals(defaultLocaleLang)) {
                String localeResult = extractForLanguage(trimmed, defaultLocaleLang);
                if (!localeResult.equals(trimmed)) {
                    return localeResult;
                }
            }
            return formatMatch(trimmed, ENGLISH_SPACE_PATTERN.matcher(trimmed), false);
        }

        // Apply primary language rules
        String result = extractForLanguage(trimmed, lang);
        if (!result.equals(trimmed)) {
            return result;
        }

        // Fallback to English for hybrid titles (e.g. English franchise title with localized subtitle like "The Amazing Spider-Man : ...").
        // When falling back from a known non-English language, use unambiguous English articles ("the", "an", "ye")
        // to avoid misinterpreting non-English words like French preposition "À" in "À bout de souffle".
        if (!"en".equals(lang)) {
            return formatMatch(trimmed, ENGLISH_FALLBACK_PATTERN.matcher(trimmed), false);
        }

        return trimmed;
    }

    private static String extractForLanguage(String trimmed, String lang) {
        switch (lang) {
            case "en":
                return formatMatch(trimmed, ENGLISH_SPACE_PATTERN.matcher(trimmed), false);
            case "fr": {
                String apostropheResult = formatMatch(trimmed, FRENCH_APOSTROPHE_PATTERN.matcher(trimmed), true);
                if (!apostropheResult.equals(trimmed)) return apostropheResult;
                return formatMatch(trimmed, FRENCH_SPACE_PATTERN.matcher(trimmed), false);
            }
            case "de":
                return formatMatch(trimmed, GERMAN_SPACE_PATTERN.matcher(trimmed), false);
            case "es":
                return formatMatch(trimmed, SPANISH_SPACE_PATTERN.matcher(trimmed), false);
            case "it": {
                String apostropheResult = formatMatch(trimmed, ITALIAN_APOSTROPHE_PATTERN.matcher(trimmed), true);
                if (!apostropheResult.equals(trimmed)) return apostropheResult;
                return formatMatch(trimmed, ITALIAN_SPACE_PATTERN.matcher(trimmed), false);
            }
            case "pt":
                return formatMatch(trimmed, PORTUGUESE_SPACE_PATTERN.matcher(trimmed), false);
            case "nl": {
                String apostropheResult = formatDutchApostropheMatch(trimmed, DUTCH_APOSTROPHE_PATTERN.matcher(trimmed));
                if (!apostropheResult.equals(trimmed)) return apostropheResult;
                return formatMatch(trimmed, DUTCH_SPACE_PATTERN.matcher(trimmed), false);
            }
            default:
                return trimmed;
        }
    }

    private static String formatMatch(String original, Matcher matcher, boolean appendApostrophe) {
        if (matcher.matches()) {
            String article = matcher.group(1);
            String remainder = matcher.group(2);
            if (!TextUtils.isEmpty(remainder) && !remainder.trim().isEmpty()) {
                if (appendApostrophe) {
                    return remainder.trim() + ", " + article + "'";
                } else {
                    return remainder.trim() + ", " + article;
                }
            }
        }
        return original;
    }

    private static String formatDutchApostropheMatch(String original, Matcher matcher) {
        if (matcher.matches()) {
            String article = matcher.group(1);
            String remainder = matcher.group(2);
            if (!TextUtils.isEmpty(remainder) && !remainder.trim().isEmpty()) {
                String normalizedArticle = article.replace('’', '\'');
                return remainder.trim() + ", " + normalizedArticle;
            }
        }
        return original;
    }

    private static String normalizeLanguage(String language) {
        if (TextUtils.isEmpty(language)) return "und";
        String code = ISO639codes.getISO6391ForLetterCode(language);
        return code.isEmpty() ? "und" : code;
    }
}
