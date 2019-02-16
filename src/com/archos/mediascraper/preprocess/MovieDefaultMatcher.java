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

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Matches everything. Tries to strip away all junk, not very reliable.
 * <p>
 * Process is as follows:
 * <ul>
 * <li> Start with filename without extension: "100. [DVD]Starship_Troopers_1995.-HDrip--IT"
 * <li> Remove potential starting numbering of collections "[DVD]Starship_Troopers_1995.-HDrip--IT"
 * <li> Extract last year if any: "[DVD]Starship_Troopers_.-HDrip--IT"
 * <li> Remove anything in brackets: "Starship_Troopers_.-HDrip--IT"
 * <li> Assume from here on that the title is first followed by junk
 * <li> Trim CasE sensitive junk: "Starship_Troopers_.-HDrip" ("it" could be part of the movie name, "IT" probably not)
 * <li> Remove separators: "Starship Troopers HDrip"
 * <li> Trim junk case insensitive: "Starship Troopers"
 * </ul>
 */
class MovieDefaultMatcher implements InputMatcher {

    public static MovieDefaultMatcher instance() {
        return INSTANCE;
    }

    private static final MovieDefaultMatcher INSTANCE =
            new MovieDefaultMatcher();

    private MovieDefaultMatcher() {
        // singleton
    }

    @Override
    public boolean matchesFileInput(Uri fileInput, Uri simplifiedUri) {
        // this is the fallback matcher that matches everything
        return true;
    }

    @Override
    public boolean matchesUserInput(String userInput) {
        // this is the fallback matcher that matches everything
        return true;
    }

    @Override
    public SearchInfo getFileInputMatch(Uri file, Uri simplifiedUri) {
        if(simplifiedUri!=null)
            file = simplifiedUri;
        return getMatch(FileUtils.getFileNameWithoutExtension(file), file);
    }

    @Override
    public SearchInfo getUserInputMatch(String userInput, Uri file) {
        return getMatch(userInput, file);
    }

    private static SearchInfo getMatch(String input, Uri file) {
        String name = input;
        String year = null;

        // extract the last year from the string
        Matcher matcher = YEAR_PATTERN.matcher(name);
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
            year = name.substring(start, stop);
            name = name.substring(0, start) + name.substring(stop);
        }

        // Strip out starting numbering for collections
        name = ParseUtils.removeNumbering(name);

        // Strip out everything else in brackets <[{( .. )})>, most of the time teams names, etc
        name = StringUtils.replaceAll(name, "", BRACKETS);

        // strip away known case sensitive garbage
        name = cutOffBeforeFirstMatch(name, GARBAGE_CASESENSITIVE_PATTERNS);

        // replace all remaining whitespace & punctuation with a single space
        name = ParseUtils.removeInnerAndOutterSeparatorJunk(name);

        // append a " " to aid next step
        // > "Foo bar 1080p AC3 " to find e.g. " AC3 "
        name = name + " ";

        // try to remove more garbage, this time " garbage " syntax
        // method will compare with lowercase name automatically
        name = cutOffBeforeFirstMatch(name, GARBAGE_LOWERCASE);

        name = name.trim();
        return new MovieSearchInfo(file, name, year);
    }

    @Override
    public String getMatcherName() {
        return "MovieDefault";
    }

    // Common garbage in movies names to determine where the garbage starts in the name
    // tested against strings like "real movie name dvdrip 1080p power "
    private static final String[] GARBAGE_LOWERCASE = {
            " dvdrip ", " dvd rip ", " dvdscr ", " dvd scr ",
            " brrip ", " br rip ", " bdrip", " bd rip ", " blu ray ", " bluray ",
            " hddvd ", " hd dvd ", " hdrip ", " hd rip ", " hdlight ", " minibdrip ",
            " webrip ", " web rip ",
            " 720p ", " 1080p ", " 1080i ", " 720 ", " 1080 ", " 480i ", " 2160p ", " 4k ",
            " hdtv ", " sdtv ", " m hd ", " ultrahd ",
            " h264 ", " x264 ", " aac ", " ac3 ", " ogm ", " dts ", " hevc ", " x265 ",
            " avi ", " mkv ", " xvid ", " divx ", " wmv ", " mpg ", " mpeg ", " flv ", " f4v ",
            " asf ", " vob ", " mp4 ", " mov ",
            " directors cut ", " dircut ", " readnfo ", " read nfo ", " repack ", " rerip ", " multi ", " remastered ",
            " truefrench ", " srt ", " extended cut ",
            " sbs ", " hsbs ", " side by side ", " sidebyside ", /* Side-By-Side 3d stuff */
            " 3d ", " h sbs ", " h tb " , " tb ", " htb ", " top bot ", " topbot ", " top bottom ", " topbottom ", " tab ", " htab ", /* Top-Bottom 3d stuff */
            " anaglyph ", " anaglyphe ", /* Anaglyph 3d stuff */
    };

    // stuff that could be present in real names is matched with tight case sensitive syntax
    // strings here will only match if separated by any of " .-_"
    private static final String[] GARBAGE_CASESENSITIVE = {
            "FRENCH", "TRUEFRENCH", "DUAL", "MULTI", "MULTi", "SUBFORCED", "SUBFORCES", "UNRATED", "UNRATED[ ._-]DC", "EXTENDED", "IMAX",
            "COMPLETE", "PROPER", "iNTERNAL", "INTERNAL",
            "SUBBED", "ANiME", "LIMITED", "REMUX", "DCPRip",
            "TS", "TC", "REAL", "HD", "DDR", "WEB",
            "EN", "ENG", "DE", "FR", "ES", "IT", "NL", "VFQ", "VF", "VO", "VOSTFR", "Eng",
            "VFF", "VF2", "VFI", "VFSTFR",
    };

    private static final Pattern[] GARBAGE_CASESENSITIVE_PATTERNS = new Pattern[GARBAGE_CASESENSITIVE.length];
    static {
        for (int i = 0; i < GARBAGE_CASESENSITIVE.length; i++) {
            // case sensitive string wrapped in "space or . or _ or -", in the end either separator or end of line
            // end of line is important since .foo.bar. could be stripped to .foo and that would no longer match .foo.
            GARBAGE_CASESENSITIVE_PATTERNS[i] = Pattern.compile("[ ._-]" + GARBAGE_CASESENSITIVE[i] + "(?:[ ._-]|$)");
        }
    }

    // matches "[space or punctuation/brackets etc]year", year is group 1
    private static final Pattern YEAR_PATTERN =
            Pattern.compile("[\\s\\p{Punct}]((?:19|20)\\d{2})(?!\\d)");

    private static final Pattern BRACKETS = Pattern.compile("[<({\\[].+?[>)}\\]]");

    /**
     * assumes title is always first
     * @return substring from start to first finding of any garbage pattern
     */
    private static String cutOffBeforeFirstMatch(String input, Pattern[] patterns) {
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
    public static final String cutOffBeforeFirstMatch(String input, String[] garbageStrings) {
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

}
