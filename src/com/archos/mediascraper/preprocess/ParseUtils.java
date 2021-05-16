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
    private static final Pattern LEADING_NUMBERING = Pattern.compile("^(\\d+([.)][\\s\\p{Punct}]+|\\s+\\p{Punct}[\\p{Punct}\\s]*))*");
    /* Matches "1-Foo" */
    private static final Pattern LEADING_NUMBERING_DASH = Pattern.compile("^(\\d+([-]|\\s+\\p{Punct}[\\p{Punct}\\s]*))*");

    /** besides the plain ' there is the typographic ’ and ‘ which is actually not an apostrophe */
    private static final char[] ALTERNATE_APOSTROPHES = new char[] {
        '’', '‘'
    };

    public static final Pattern BRACKETS = Pattern.compile("[<({\\[].+?[>)}\\]]");

    // matches "[space or punctuation/brackets etc]year", year is group 1
    private static final Pattern YEAR_PATTERN = Pattern.compile("[\\s\\p{Punct}]((?:19|20)\\d{2})(?!\\d)");

    private static final Pattern PARENTHESIS_YEAR_PATTERN = Pattern.compile("[\\s\\p{Punct}]\\(((?:19|20)\\d{2})\\)(?!\\d)");

    // Strip out everything after empty parenthesis (after year pattern removal)
    // i.e. movieName (1969) garbage -> movieName () garbage -> movieName
    private static final Pattern EMPTY_PARENTHESIS_PATTERN = Pattern.compile("[\\s\\p{Punct}]([(][)])[\\s\\p{Punct}]*");

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

    // remove all what is after empty parenthesis
    // only apply to movieName (1928) junk -> movieName () junk -> movieName, junk can be null
    public static String removeAfterEmptyParenthesis(String input) {
        log.debug("removeAfterEmptyParenthesis input: " + input);
        Matcher matcher = EMPTY_PARENTHESIS_PATTERN.matcher(input);
        int start = 0;
        int stop = 0;
        boolean found = false;
        while (matcher.find()) {
            log.debug("removeAfterEmptyParenthesis: pattern found");
            found = true;
            start = matcher.start(1);
        }
        // get the first match and extract it from the string
        if (found)
            input = input.substring(0, start);
        log.debug("removeAfterEmptyParenthesis remove junk after (): " + input);
        return input;
    }

    private ParseUtils() {
        // static utilities
    }

    // matches "[space or punctuation/brackets etc]year", year is group 1
    // "[\\s\\p{Punct}]((?:19|20)\\d{2})(?!\\d)"
    public static Pair<String, String> yearExtractor(String input) {
        log.debug("yearExtractor input: " + input);
        return twoPatternExtractor(input, YEAR_PATTERN);
    }

    // matches "[space or punctuation/brackets etc](year)", year is group 1
    public static Pair<String, String> parenthesisYearExtractor(String input) {
        log.debug("parenthesisYearExtractor input: " + input);
        return twoPatternExtractor(input, PARENTHESIS_YEAR_PATTERN);
    }

    public static Pair<String, String> twoPatternExtractor(String input, Pattern pattern) {
        log.debug("twoPatternExtractor input: " + input);
        String isolated = null;
        Matcher matcher = pattern.matcher(input);
        int start = 0;
        int stop = 0;
        boolean found = false;
        while (matcher.find()) {
            found = true;
            start = matcher.start(1);
            stop = matcher.end(1);
        }
        // get the last match and extract it from the string
        if (found) {
            isolated = input.substring(start, stop);
            input = input.substring(0, start) + input.substring(stop);
        }
        log.debug("yearExtractor release year: " + input + " isolated: " + isolated);
        return new Pair<>(input, isolated);
    }

}
