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

import android.util.Pair;

import com.archos.mediascraper.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParseUtils {

    private static final Logger log = LoggerFactory.getLogger(ParseUtils.class);

    /* ( whitespace | punctuation)+, matches dots, spaces, brackets etc */
    private static final Pattern MULTI_NON_CHARACTER_PATTERN = Pattern.compile("[\\s\\p{Punct}&&[^']]+");
    /*
     * Matches dots in between Uppercase letters e.g. in "E.T.", "S.H.I.E.L.D." but not a "a.b.c."
     * Last dot is kept "a.F.O.O.is.foo" => "a.FOO.is.foo"
     **/
    private static final Pattern ACRONYM_DOTS = Pattern.compile("(?<=(\\b|[._])\\p{Lu})[.](?=\\p{Lu}([.]|$))");

    /* Matches "1. ", "1) ", "1 - ", "1.-.", "1._"... but not "1.Foo" (could be a starting date with space) or "1-Foo" ..*/
    // Restrict to max 3 digits to avoid matching years at the start of filenames
    // Also restrict punctuation to avoid matching "300 (2007)" as numbering "300 ("
    private static final Pattern LEADING_NUMBERING = Pattern.compile("^(\\d{1,3}([.)][\\s\\p{Punct}]+|\\s+[\\p{Punct}&&[^\\[({]][\\p{Punct}\\s]*))*");
    // Matches "1-Foo" to be used with movies only because clashes with 24-s01e01 check with find . -type f -regex '.*/[0-9]+-[^/]*'
    private static final Pattern LEADING_NUMBERING_DASH = Pattern.compile("^(\\d{1,3}([-]|\\s+[\\p{Punct}&&[^\\[({]][\\p{Punct}\\s]*))*");

    /** besides the plain ' there is the typographic ’ and ‘ which is actually not an apostrophe */
    private static final char[] ALTERNATE_APOSTROPHES = new char[] {
        '’', '‘'
    };

    // Only match brackets preceded by whitespace or at start of string to preserve
    // parenthesized content attached to words (e.g. "OVNI(s)" keeps "(s)")
    public static final Pattern BRACKETS = Pattern.compile("(?<=\\s|^)[<({\\[].+?[>)}\\]]");

    // isolated 4-digit number. Avoid resolutions like 1920x1080 or codecs.
    private static final Pattern YEAR_ANYWHERE_PATTERN = Pattern.compile("(?<![\\d\\p{L}])(\\d{4})(?![\\d\\p{L}])");

    private static final Pattern YEAR_PATTERN_END_STRING = Pattern.compile("(.*)[\\s\\p{Punct}]((?:19|20)\\d{2})(?!\\d)$");

    // matches "(year)" — uses reluctant quantifier so the first parenthesized year wins
    private static final Pattern PARENTHESIS_YEAR_PATTERN = Pattern.compile("(.*?)[\\s\\p{Punct} ]*\\(((?:19|20)\\d{2})\\)(.*)");

    public static final int MIN_YEAR = 1906;

    // Strip out everything after empty parenthesis (after year pattern removal)
    // i.e. movieName (1969) garbage -> movieName () garbage -> movieName
    private static final Pattern EMPTY_PARENTHESIS_PATTERN = Pattern.compile("(.*)[\\s\\p{Punct}]+([(][)])(.*)");

    // full list of possible countries of origin is available here https://api.themoviedb.org/3/configuration/countries?api_key=051012651ba326cf5b1e2f482342eaa2
    private static final Pattern COUNTRY_OF_ORIGIN = Pattern.compile("(.*)[\\s\\p{Punct}]+\\(((US|UK|FR))\\)(.*)");


    /**
     * Removes leading numbering like "1. A Movie" => "A Movie",
     * does not replace numbers if they are not separated like in
     * "13.Years.Of.School"
     **/
    public static String removeNumbering(String input) {
        return StringUtils.replaceAll(input, "", LEADING_NUMBERING);
    }

    public static String removeNumberingDash(String input) {
        return StringUtils.replaceAll(input, "", LEADING_NUMBERING_DASH);
    }

    /** replaces "S.H.I.E.L.D." with "SHIELD", only uppercase letters */
    public static String replaceAcronyms(String input) {
        return StringUtils.replaceAll(input, "", ACRONYM_DOTS);
    }

    /** replaces alternative apostrophes with a simple ' */
    public static String unifyApostrophes(String input) {
        return StringUtils.replaceAllChars(input, ALTERNATE_APOSTROPHES, '\'');
    }

    /** removes all punctuation characters besides ' Also does apostrophe and Acronym replacement */
    public static String removeInnerAndOutterSeparatorJunk(String input) {
        // replace ’ and ‘ by ' - both could be used as apostrophes
        String result = unifyApostrophes(input);
        result = replaceAcronyms(result);
        return StringUtils.replaceAll(result, " ", MULTI_NON_CHARACTER_PATTERN).trim();
    }

    public static String removeAfterEmptyParenthesis(String input) {
        if (log.isDebugEnabled()) log.debug("removeAfterEmptyParenthesis input: {}", input);
        Matcher matcher = EMPTY_PARENTHESIS_PATTERN.matcher(input);
        if (matcher.find()) {
            if (log.isDebugEnabled()) log.debug("removeAfterEmptyParenthesis: pattern found");
            // Keep everything before the empty parenthesis (end of group 1 = the title part)
            input = matcher.group(1);
        }
        if (log.isDebugEnabled()) log.debug("removeAfterEmptyParenthesis remove junk after (): {}", input);
        return input;
    }

    private ParseUtils() {
        // static utilities
    }

    // Common garbage in movies names to determine where the garbage starts in the name
    // tested against strings like "real movie name dvdrip 1080p power "
    public static final String[] GARBAGE_LOWERCASE = {
            " dvdrip ", " dvd rip ", "dvdscreener ", " dvdscr ", " dvd scr ",
            " brrip ", " br rip ", " bdrip", " bd rip ", " blu ray ", " bluray ",
            " hddvd ", " hd dvd ", " hdrip ", " hd rip ", " hdlight ", " minibdrip ",
            " webrip ", " web rip ",
            " 720p ", " 1080p ", " 1080i ", " 720 ", " 1080 ", " 480i ", " 2160p ", " 4k ", " 480p ", " 576p ", " 576i ", " 240p ", " 360p ", " 4320p ", " 8k ",
            " 1920x1080 ", " 1280x720 ", " 3840x2160 ", " 1280x800 ", " 1024x768 ", " 720x480 ", " 720x576 ",
            " hdtv ", " sdtv ", " m hd ", " ultrahd ", " mhd ",
            " h264 ", " x264 ", " aac ", " ac3 ", " ogm ", " dts ", " hevc ", " x265 ", " av1 ",
            " avi ", " mkv ", " xvid ", " divx ", " wmv ", " mpg ", " mpeg ", " flv ", " f4v ",
            " asf ", " vob ", " mp4 ", " mov ",
            " directors cut ", " dircut ", " readnfo ", " read nfo ", " repack ", " rerip ", " multi ", " remastered ",
            " truefrench ", " srt ", " extended cut ",
            " sbs ", " hsbs ", " side by side ", " sidebyside ", /* Side-By-Side 3d stuff */
            " 3d ", " h sbs ", " h tb " , " tb ", " htb ", " top bot ", " topbot ", " top bottom ", " topbottom ", " tab ", " htab ", /* Top-Bottom 3d stuff */
            " anaglyph ", " anaglyphe ", /* Anaglyph 3d stuff */
            " truehd ", " atmos ", " uhd ", " hdr10+ ", " hdr10 ", " hdr ", " dolby ", " dts-x ", " dts-hd.ma ",
            " hfr ",
    };

    // stuff that could be present in real names is matched with tight case sensitive syntax
    // strings here will only match if separated by any of " .-_"
    public static final String[] GARBAGE_CASESENSITIVE = {
            "FRENCH", "TRUEFRENCH", "DUAL", "MULTISUBS", "MULTI", "MULTi", "SUBFORCED", "SUBFORCES", "UNRATED", "UNRATED[ ._-]DC", "EXTENDED", "IMAX",
            "COMPLETE", "PROPER", "iNTERNAL", "INTERNAL",
            "SUBBED", "ANiME", "LIMITED", "REMUX", "DCPRip",
            "TS", "TC", "REAL", "HD", "DDR", "WEB",
            "EN", "ENG", "FR", "ES", "IT", "NL", "VFQ", "VF", "VO", "VOF", "VOSTFR", "Eng",
            "VOST", "VFF", "VF2", "VFI", "VFSTFR",
    };

    public static final Pattern[] GARBAGE_CASESENSITIVE_PATTERNS = new Pattern[GARBAGE_CASESENSITIVE.length];
    static {
        for (int i = 0; i < GARBAGE_CASESENSITIVE.length; i++) {
            // case sensitive string wrapped in "space or . or _ or -", in the end either separator or end of line
            GARBAGE_CASESENSITIVE_PATTERNS[i] = Pattern.compile("[ ._-]" + GARBAGE_CASESENSITIVE[i] + "(?:[ ._-]|$)");
        }
    }

    /**
     * assumes title is always first
     * @return substring from start to first finding of any garbage pattern
     */
    public static String cutOffBeforeFirstMatch(String input, Pattern[] patterns) {
        String remaining = input;
        for (Pattern pattern : patterns) {
            if (remaining.isEmpty()) return "";

            Matcher matcher = pattern.matcher(remaining);
            if (matcher.find()) {
                remaining = remaining.substring(0, matcher.start());
            }
        }
        return remaining;
    }

    /**
     * assumes title is always first
     * @param garbageStrings lower case strings
     * @return substring from start to first finding of any garbage string
     */
    public static String cutOffBeforeFirstMatch(String input, String[] garbageStrings) {
        // lower case input to test against lowercase strings
        String inputLowerCased = input.toLowerCase(Locale.US);

        int firstGarbage = input.length();

        for (String garbage : garbageStrings) {
            int garbageIndex = inputLowerCased.indexOf(garbage);
            // if found, shrink to 0..index
            if (garbageIndex > -1 && garbageIndex < firstGarbage)
                firstGarbage = garbageIndex;
        }

        // return substring from input -> keep case
        return input.substring(0, firstGarbage);
    }

    /**
     * Cleans an extracted title fragment (e.g. episode title from filename) by applying
     * the standard garbage-stripping pipeline: case-sensitive patterns, separator cleanup,
     * then case-insensitive garbage words.
     */
    public static String cleanExtractedTitle(String raw) {
        // strip case-sensitive garbage (FRENCH, MULTI, WEB, etc.)
        String name = cutOffBeforeFirstMatch(raw, GARBAGE_CASESENSITIVE_PATTERNS);
        // dots/underscores → spaces, acronyms, apostrophes
        name = removeInnerAndOutterSeparatorJunk(name);
        // strip case-insensitive garbage (1080p, x264, bluray, etc.)
        name = cutOffBeforeFirstMatch(name + " ", GARBAGE_LOWERCASE).trim();
        return name;
    }

    /**
     * Removes all matches of the given garbage strings from the input without cutting off the remainder.
     * This is useful when you need to strip garbage but keep trailing information (like a year).
     */
    public static String removeGarbage(String input) {
        if (input == null || input.isEmpty()) return input;

        // Pad with spaces to allow easy matching of " garbage " strings
        String result = " " + input + " ";
        String resultLower = result.toLowerCase(Locale.US);

        // Remove case-insensitive garbage
        for (String rawGarbage : GARBAGE_LOWERCASE) {
            String garbage = rawGarbage.trim();
            if (garbage.isEmpty()) continue;

            // Check for garbage wrapped in common separators
            String[] formats = { "." + garbage + ".", " " + garbage + " ", "-" + garbage + "-", "_" + garbage + "_",
                                 "." + garbage + " ", " " + garbage + ".", "-" + garbage + " ", " " + garbage + "-" };
            for (String format : formats) {
                int index = resultLower.indexOf(format);
                while (index > -1) {
                    // Replace the exact length of the garbage string with a single space to preserve separation
                    String replacement = format.substring(0, 1) + " " + format.substring(format.length() - 1);
                    result = result.substring(0, index) + replacement + result.substring(index + format.length());
                    resultLower = result.toLowerCase(Locale.US);
                    index = resultLower.indexOf(format);
                }
            }
        }

        // Remove case-sensitive garbage
        for (Pattern p : GARBAGE_CASESENSITIVE_PATTERNS) {
            Matcher m = p.matcher(result);
            while (m.find()) {
                result = m.replaceAll(" ");
                m = p.matcher(result);
            }
        }

        return result.replaceAll("\\s+", " ").trim();
    }

    public static boolean isValidYear(String year, int currentYear) {
        if (year == null || year.isEmpty()) return false;
        try {
            int parsedYear = Integer.parseInt(year);
            return parsedYear >= MIN_YEAR && parsedYear <= currentYear + 1;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isValidYear(String year) {
        return isValidYear(year, Calendar.getInstance().get(Calendar.YEAR));
    }

    public static boolean isPlausibleYear(String year, String remainingName, int currentYear) {
        if (remainingName == null || remainingName.trim().length() < 2) return false;
        return isValidYear(year, currentYear);
    }

    public static boolean isPlausibleYear(String year, String remainingName) {
        return isPlausibleYear(year, remainingName, Calendar.getInstance().get(Calendar.YEAR));
    }

    public static Pair<String, String> yearExtractorEndString(String input) {
        if (log.isDebugEnabled()) log.debug("yearExtractorEndString input: {}", input);
        return twoPatternExtractor(input, YEAR_PATTERN_END_STRING, 3);
    }

    // matches "(year)" — uses reluctant quantifier so the first parenthesized year wins
    public static Pair<String, String> parenthesisYearExtractor(String input) {
        if (log.isDebugEnabled()) log.debug("parenthesisYearExtractor input: {}", input);
        return twoPatternExtractor(input, PARENTHESIS_YEAR_PATTERN, 3);
    }

    /**
     * Replaces common transliterations with their actual characters (e.g. German umlauts).
     * This is useful as a fallback candidate when searching for titles that might have been
     * transliterated in filenames to avoid encoding issues.
     */
    public static String transliterate(String input) {
        if (input == null || input.isEmpty()) return input;
        // German umlauts
        String result = input.replace("ae", "ä");
        result = result.replace("oe", "ö");
        result = result.replace("ue", "ü");
        result = result.replace("Ae", "Ä");
        result = result.replace("Oe", "Ö");
        result = result.replace("Ue", "Ü");
        return result;
    }

    // Movie preprocessing needs the title before the release year only.
    public static Pair<String, String> parenthesisYearExtractorTitleOnly(String input) {
        if (log.isDebugEnabled()) log.debug("parenthesisYearExtractorTitleOnly input: {}", input);
        return twoPatternExtractor(input, PARENTHESIS_YEAR_PATTERN, 0);
    }

    // matches country of origin ((US|UK|FR)), country is group 1
    // COUNTRY_OF_ORIGIN has an extra group for the inner capture, so "after" is group 4
    public static Pair<String, String> getCountryOfOrigin(String input) {
        if (log.isDebugEnabled()) log.debug("getCountryOfOrigin input: {}", input);
        return twoPatternExtractor(input, COUNTRY_OF_ORIGIN, 4);
    }

    public static Pair<String, String> extractYearAnywhere(String input, int currentYear) {
        if (log.isDebugEnabled()) log.debug("extractYearAnywhere input: {}", input);
        String reversed = new StringBuilder(input).reverse().toString();
        Matcher matcher = YEAR_ANYWHERE_PATTERN.matcher(reversed);

        while (matcher.find()) {
            String candidateYear = new StringBuilder(matcher.group(1)).reverse().toString();
            int cutIndex = input.length() - matcher.start() - 4;

            if (isValidYear(candidateYear, currentYear)) {
                // Heuristic: only strip if it leaves at least 2 characters.
                if (cutIndex >= 2) {
                    // Year is at middle/end
                    String name = input.substring(0, cutIndex).trim();
                    if (log.isDebugEnabled()) log.debug("extractYearAnywhere found year at middle/end: {}, name: {}", candidateYear, name);
                    return new Pair<>(name, candidateYear);
                } else if (cutIndex == 0) {
                    // Year is at start
                    String remainder = input.substring(4).trim();
                    // Clean up remainder (remove leading punctuation if any)
                    remainder = removeInnerAndOutterSeparatorJunk(remainder);
                    if (remainder.length() >= 2) {
                        if (log.isDebugEnabled()) log.debug("extractYearAnywhere found year at start: {}, remainder: {}", candidateYear, remainder);
                        return new Pair<>(remainder, candidateYear);
                    }
                }
                // Valid year but remainder too short (< 2) -> break per Leeroy's logic
                break;
            }
        }
        return new Pair<>(input, null);
    }

    /**
     * Extracts a value from a pattern with groups: (before)(value)(after).
     * @param input the string to match against
     * @param pattern regex with group 1 = before, group 2 = extracted value
     * @param afterGroup the group index containing text after the extracted value (0 if none)
     * @return Pair of (remaining input, extracted value) or (input, null) if no match
     */
    public static Pair<String, String> twoPatternExtractor(String input, Pattern pattern, int afterGroup) {
        if (log.isDebugEnabled()) log.debug("twoPatternExtractor input: {}", input);
        String isolated = null;
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            isolated = matcher.group(2);
            String before = matcher.group(1);
            String after = (afterGroup > 0 && matcher.groupCount() >= afterGroup) ? matcher.group(afterGroup) : "";
            input = (before != null ? before : "") + (after != null ? after : "");
        }
        if (log.isDebugEnabled()) log.debug("twoPatternExtractor output: {} isolated: {}", input, isolated);
        return new Pair<>(input, isolated);
    }

}
