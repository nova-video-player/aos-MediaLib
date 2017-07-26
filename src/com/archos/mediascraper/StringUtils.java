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

package com.archos.mediascraper;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class StringUtils {

    /**
     * Splits Strings upon finding a character<p>
     * Parts are trimmed<p>
     * Parts are only added to the result if they are not empty after trimming
     * <pre>
     * split("  ,  ", ',') = empty list
     * split(" hello ", ',') = ["hello"]
     * split("|hi|world|", '|') = ["hi","world"]
     * split("|hi,world|", '|', ',') = ["hi","world"]
     * </pre>
     * @return never null list
     */
    public static List<String> split(String src, char... splitCharacters) {
        List<String> result = new ArrayList<String>();
        if (src != null && !src.isEmpty()) {
            boolean inString = true;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < src.length(); i++) {
                char charInSrc = src.charAt(i);
                if (contains(splitCharacters, charInSrc)) {
                    // only act when switching from inString to !inString
                    if (inString) {
                        inString = false;
                        if (sb.length() > 0) {
                            String string = sb.toString().trim();
                            sb.setLength(0);
                            if (!string.isEmpty())
                                result.add(string);
                        }
                    }
                } else {
                    if (!inString)
                        inString = true;
                    sb.append(charInSrc);
                }
            }
            // maybe add remainder to list
            if (inString && sb.length() > 0) {
                String string = sb.toString().trim();
                if (!string.isEmpty())
                    result.add(string);
            }
        }
        return result;
    }

    private static boolean contains(char[] array, char c) {
        for (char test : array)
            if (test == c)
                return true;
        return false;
    }
    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();
    public static final Charset CHARSET_UTF8 = Charset.forName("UTF-8");
    /** Index of a component which was not found. */
    private final static int NOT_FOUND = -1;

    /**
     * Encodes characters in the given string as '%'-escaped octets using the
     * UTF-8 scheme. Leaves characters that are allowed in typical filesystems
     * intact. Assumes Unicode capability! Encodes all other characters.
     * Result is trimmed and if empty replaced with "unknown", if starting with
     * a dot (would be hidden file) that dot is replaced with an 'X'
     * Also limits length to 200 characters
     *
     * @param s string to encode
     * @param allow set of additional characters to allow in the encoded form,
     *            null if no characters should be skipped
     * @return an encoded version of s suitable for use as a filename, or null
     *         if s is null
     */
    public static String fileSystemEncode(String s) {
        // 99% copied from android's Uri.encode()
        if (s == null) {
            return null;
        }

        // Lazily-initialized buffers.
        StringBuilder encoded = null;

        int oldLength = s.length();

        // This loop alternates between copying over allowed characters and
        // encoding in chunks. This results in fewer method calls and
        // allocations than encoding one character at a time.
        int current = 0;
        while (current < oldLength) {
            // Start in "copying" mode where we copy over allowed chars.

            // Find the next character which needs to be encoded.
            int nextToEncode = current;
            while (nextToEncode < oldLength
                    && isAllowedInFileSystem(s.charAt(nextToEncode))) {
                nextToEncode++;
            }

            // If there's nothing more to encode...
            if (nextToEncode == oldLength) {
                if (current == 0) {
                    // We didn't need to encode anything!
                    return finalizeSanitation(s);
                } else {
                    // Presumably, we've already done some encoding.
                    encoded.append(s, current, oldLength);
                    return finalizeSanitation(encoded.toString());
                }
            }

            if (encoded == null) {
                encoded = new StringBuilder();
            }

            if (nextToEncode > current) {
                // Append allowed characters leading up to this point.
                encoded.append(s, current, nextToEncode);
            } else {
                // assert nextToEncode == current
            }

            // Switch to "encoding" mode.

            // Find the next allowed character.
            current = nextToEncode;
            int nextAllowed = current + 1;
            while (nextAllowed < oldLength
                    && !isAllowedInFileSystem(s.charAt(nextAllowed))) {
                nextAllowed++;
            }

            // Convert the substring to bytes and encode the bytes as
            // '%'-escaped octets.
            String toEncode = s.substring(current, nextAllowed);
            byte[] bytes = toEncode.getBytes(CHARSET_UTF8);
            int bytesLength = bytes.length;
            for (int i = 0; i < bytesLength; i++) {
                encoded.append('%');
                encoded.append(HEX_DIGITS[(bytes[i] & 0xf0) >> 4]);
                encoded.append(HEX_DIGITS[bytes[i] & 0xf]);
            }

            current = nextAllowed;
        }

        // Encoded could still be null at this point if s is empty.
        return finalizeSanitation(encoded == null ? s : encoded.toString());
    }

    private static String finalizeSanitation(String result) {
        if (result != null) {
            result = result.trim();
            if (result.isEmpty())
                result = "unknown";
            else if (result.charAt(0) == '.')
                result = "X" + result.substring(1);
            // limit to 200 characters, enough space to add an extension,
            // windows seems to be limited to 234 characters
            if (result.length() > 200)
                result = result.substring(0, 200);
        }
        return result;
    }

    /**
     * Returns true if the given character is allowed in typical filesystem.
     * Assumes Unicode capability
     *
     * @param c character to check
     * @param allow characters to allow
     * @return true if the character is allowed or false if it should be encoded
     */
    private static boolean isAllowedInFileSystem(char c) {
        return Character.isLetter(c)
                || Character.isDigit(c)
                // Windows does not allow / \ : * ? " < > |
                // see
                // http://en.wikipedia.org/wiki/Filename#Reserved_characters_and_words
                // but we keep dots and spaces
                // also don't allow % since that's our encode character
                // below special characters are ok
                || " .-`'~!@#$^&_=+[]{}();,".indexOf(c) != NOT_FOUND;
    }

    /** tries to parse String > int, errorValue if something prevents parsing (null String / wrong format) */
    public static int parseInt(String string, int errorValue) {
        if (string != null) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException e) {
                // error
            }
        }
        return errorValue;
    }

    /** tries to parse String > long, errorValue if something prevents parsing (null String / wrong format) */
    public static long parseLong(String string, long errorValue) {
        if (string != null) {
            try {
                return Long.parseLong(string);
            } catch (NumberFormatException e) {
                // error
            }
        }
        return errorValue;
    }

    /** tries to parse String > float, errorValue if something prevents parsing (null String / wrong format) */
    public static float parseFloat(String string, float errorValue) {
        if (string != null) {
            try {
                return Float.parseFloat(string);
            } catch (NumberFormatException e) {
                // error
            }
        }
        return errorValue;
    }

    /** tries to parse String > double, errorValue if something prevents parsing (null String / wrong format) */
    public static double parseDouble(String string, double errorValue) {
        if (string != null) {
            try {
                return Double.parseDouble(string);
            } catch (NumberFormatException e) {
                // error
            }
        }
        return errorValue;
    }

    /** tries to parse String > boolean, errorValue if something prevents parsing (null String / wrong format) */
    public static boolean parseBoolean(String string, boolean errorValue) {
        if (string != null) {
            return Boolean.parseBoolean(string);
        }
        return errorValue;
    }

    /** Like String.replaceAll but with re-usable Pattern */
    public static String replaceAll(String input, String replacement, Pattern pattern) {
        return pattern.matcher(input).replaceAll(replacement);
    }

    public static String replaceAllChars(String input, char[] badChars, char newChar) {
        if (badChars == null || badChars.length == 0)
            return input;
        int inputLength = input.length();
        int replacementLenght = badChars.length;
        boolean modified = false;
        char[] buffer = new char[inputLength];
        input.getChars(0, inputLength, buffer, 0);
        for (int inputIdx = 0; inputIdx < inputLength; inputIdx++) {
            char current = buffer[inputIdx];
            for (int replacementIdx = 0; replacementIdx < replacementLenght; replacementIdx++) {
                if (current == badChars[replacementIdx]) {
                    buffer[inputIdx] = newChar;
                    modified = true;
                    break;
                }
            }
        }
        return modified ? new String(buffer) : input;
    }

    private StringUtils() {
        // all static
    }
}
