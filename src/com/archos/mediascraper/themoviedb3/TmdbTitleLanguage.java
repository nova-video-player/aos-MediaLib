// Copyright 2026 Courville Software
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.archos.mediascraper.themoviedb3;

import android.text.TextUtils;

import com.archos.mediacenter.utils.ISO639codes;
import com.uwetrottmann.tmdb2.entities.Translations;
import com.uwetrottmann.tmdb2.entities.Translations.Translation;

/** Derives the language of TMDb's localized title without a second API request. */
final class TmdbTitleLanguage {
    private TmdbTitleLanguage() { }

    static String forMovie(String title, String requestedLanguage, String originalTitle,
            String originalLanguage, Translations translations) {
        return find(title, requestedLanguage, originalTitle, originalLanguage, translations, false);
    }

    static String forShow(String name, String requestedLanguage, String originalName,
            String originalLanguage, Translations translations) {
        return find(name, requestedLanguage, originalName, originalLanguage, translations, true);
    }

    private static String find(String value, String requestedLanguage, String originalValue,
            String originalLanguage, Translations translations, boolean show) {
        if (TextUtils.isEmpty(value)) return "und";
        String requested = normalize(requestedLanguage);
        // A matching requested translation is unambiguous, including when its text equals the
        // original title (for example an English title requested in English).
        String match = findTranslation(value, requested, translations, show);
        if (match != null) return match;
        String original = normalize(originalLanguage);
        if (value.equals(originalValue) && !"und".equals(original)) return original;

        // TMDb can fall back to a different translation. Only accept it when the returned text
        // identifies exactly one language; otherwise preserve the explicit unknown value.
        String unique = null;
        if (translations != null && translations.translations != null) {
            for (Translation translation : translations.translations) {
                if (!matches(value, translation, show)) continue;
                String language = normalize(translation.iso_639_1);
                if ("und".equals(language)) continue;
                if (unique == null) unique = language;
                else if (!unique.equals(language)) return "und";
            }
        }
        return unique == null ? "und" : unique;
    }

    private static String findTranslation(String value, String requested, Translations translations,
            boolean show) {
        if ("und".equals(requested) || translations == null || translations.translations == null) return null;
        for (Translation translation : translations.translations) {
            if (requested.equals(normalize(translation.iso_639_1)) && matches(value, translation, show)) {
                return requested;
            }
        }
        return null;
    }

    private static boolean matches(String value, Translation translation, boolean show) {
        if (translation == null || translation.data == null) return false;
        String translated = show ? translation.data.name : translation.data.title;
        return value.equals(translated);
    }

    private static String normalize(String language) {
        String code = language == null ? "" : ISO639codes.getISO6391ForLetterCode(language);
        return code.isEmpty() ? "und" : code;
    }
}
