// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.medialib;

/** Provenance values shared with AVOS sub_language.h; scores are not calibrated probabilities. */
public final class SubtitleLanguage {
    public static final int UNKNOWN = 0;
    public static final int METADATA = 1;
    public static final int DESCRIPTION = 2;
    public static final int FASTTEXT = 3;

    private SubtitleLanguage() { }

    public static String annotate(String label, int source, int confidence) {
        if (source != FASTTEXT) return label;
        return label + " (AI " + Math.max(0, Math.min(100, confidence)) + "%)";
    }
}
