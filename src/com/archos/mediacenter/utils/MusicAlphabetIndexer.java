/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.archos.mediacenter.utils;

import android.database.Cursor;
import android.widget.AlphabetIndexer;

/**
 * Handles comparisons in a different way because the Album, Song and Artist name
 * are stripped of some prefixes such as "a", "an", "the" and some symbols.
 *
 */
public class MusicAlphabetIndexer extends AlphabetIndexer {

    public MusicAlphabetIndexer(Cursor cursor, int sortedColumnIndex, CharSequence alphabet) {
        super(cursor, sortedColumnIndex, alphabet);
    }

    @Override
    protected int compare(String word, String letter) {
        String strippedWord = removePrefixesAndSymbols( word );
        String firstLetter;
        if (strippedWord.length() == 0) {
            firstLetter = " ";
        } else {
            firstLetter = strippedWord.toUpperCase().substring(0, 1);
        }
        return firstLetter.compareTo(letter);
    }

    private static String removePrefixesAndSymbols(String name) {
        name = name.trim().toLowerCase();
        if (name.startsWith("the ")) {
            name = name.substring(4);
        }
        if (name.startsWith("an ")) {
            name = name.substring(3);
        }
        if (name.startsWith("a ")) {
            name = name.substring(2);
        }
        if (name.endsWith(", the") || name.endsWith(",the") ||
                name.endsWith(", an") || name.endsWith(",an") ||
                name.endsWith(", a") || name.endsWith(",a")) {
            name = name.substring(0, name.lastIndexOf(','));
        }
        name = name.replaceAll("[\\[\\]\\(\\)\"'.,?!]", "").trim();
        return name;
    }
}
