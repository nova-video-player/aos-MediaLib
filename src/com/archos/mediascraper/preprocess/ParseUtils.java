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

import com.archos.mediascraper.StringUtils;

import java.util.regex.Pattern;

public class ParseUtils {

    /* ( whitespace | punctuation)+, matches dots, spaces, brackets etc */
    private static final Pattern MULTI_NON_CHARACTER_PATTERN = Pattern.compile("[\\s\\p{Punct}&&[^']]+");
    /*
     * Matches dots in between Uppercase letters e.g. in "E.T.", "S.H.I.E.L.D." but not a "a.b.c."
     * Last dot is kept "a.F.O.O.is.foo" => "a.FOO.is.foo"
     **/
    private static final Pattern ACRONYM_DOTS = Pattern.compile("(?<=(\\b|[._])\\p{Lu})[.](?=\\p{Lu}([.]|$))");

    /* Matches "1. ", "1) ", "1 - ", "1.-.", "1._"... but not "1.Foo" or "1-Foo" ..*/
    private static final Pattern LEADING_NUMBERING = Pattern.compile("^(\\d+([.)][\\s\\p{Punct}]+|\\s+\\p{Punct}[\\p{Punct}\\s]*))*");
    /** besides the plain ' there is the typographic ’ and ‘ which is actually not an apostrophe */
    private static final char[] ALTERNATE_APOSTROPHES = new char[] {
        '’', '‘'
    };

    /**
     * Removes leading numbering like "1. A Movie" => "A Movie",
     * does not replace numbers if they are not separated like in
     * "13.Years.Of.School"
     **/
    public static String removeNumbering(String input) {
        return StringUtils.replaceAll(input, "", LEADING_NUMBERING);
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

    private ParseUtils() {
        // static utilities
    }

}
