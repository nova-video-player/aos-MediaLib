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
    // Matches "1-Foo" to be used with movies only because clashes with 24-s01e01 check with find . -type f -regex '.*/[0-9]+-[^/]*'
    private static final Pattern LEADING_NUMBERING_DASH = Pattern.compile("^(\\d+([-]|\\s+\\p{Punct}[\\p{Punct}\\s]*))*");

    /** besides the plain ' there is the typographic ’ and ‘ which is actually not an apostrophe */
    private static final char[] ALTERNATE_APOSTROPHES = new char[] {
        '’', '‘'
    };

    public static final Pattern BRACKETS = Pattern.compile("[<({\\[].+?[>)}\\]]");

    // matches "[space or punctuation/brackets etc]year", year is group 1
    private static final Pattern YEAR_PATTERN = Pattern.compile("(.*)[\\s\\p{Punct}]((?:19|20)\\d{2})(?!\\d)");
    private static final Pattern YEAR_PATTERN_END_STRING = Pattern.compile("(.*)[\\s\\p{Punct}]((?:19|20)\\d{2})(?!\\d)$");
    private static final Pattern PARENTHESIS_YEAR_PATTERN = Pattern.compile("(.*)[\\s\\p{Punct}]+\\(((?:19|20)\\d{2})\\)");

    // Strip out everything after empty parenthesis (after year pattern removal)
    // i.e. movieName (1969) garbage -> movieName () garbage -> movieName
    private static final Pattern EMPTY_PARENTHESIS_PATTERN = Pattern.compile("(.*)[\\s\\p{Punct}]+([(][)])");

    // full list of possible countries of origin is available here https://api.themoviedb.org/3/configuration/countries?api_key=051012651ba326cf5b1e2f482342eaa2
    private static final Pattern COUNTRY_OF_ORIGIN = Pattern.compile("(.*)[\\s\\p{Punct}]+\\(((US|UK|FR))\\)");

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
    public static String removeAfterEmptyParenthesis2(String input) {
        Pair<String, String> result = twoPatternExtractor2(input, EMPTY_PARENTHESIS_PATTERN);
        log.debug("removeAfterEmptyParenthesis input: " + input + " output " + result.first);
        return result.first;
    }

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
        return twoPatternExtractor2(input, YEAR_PATTERN);
    }

    public static Pair<String, String> yearExtractorEndString(String input) {
        log.debug("yearExtractor input: " + input);
        return twoPatternExtractor2(input, YEAR_PATTERN_END_STRING);
    }

    // matches "[space or punctuation/brackets etc](year)", year is group 1
    public static Pair<String, String> parenthesisYearExtractor(String input) {
        log.debug("parenthesisYearExtractor input: " + input);
        return twoPatternExtractor2(input, PARENTHESIS_YEAR_PATTERN);
    }

    // matches country of origin ((US|UK|FR)), country is group 1
    public static Pair<String, String> getCountryOfOrigin(String input) {
        String countryOfOrigin = null;
        log.debug("getCountryOfOrigin input: " + input);
        return twoPatternExtractor2(input, COUNTRY_OF_ORIGIN);
    }

    public static Pair<String, String> twoPatternExtractor2(String input, Pattern pattern) {
        log.debug("twoPatternExtractor2 input: " + input);
        String isolated = null;
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            input =  matcher.group(1);
            isolated = matcher.group(2);
        }
        log.debug("twoPatternExtractor output: " + input + " isolated: " + isolated);
        return new Pair<>(input, isolated);
    }

}
