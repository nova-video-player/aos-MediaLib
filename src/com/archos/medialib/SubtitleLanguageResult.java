// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.medialib;

/** Compact AI results in subtitle track order. Unknown/non-AI entries are und with score zero. */
public final class SubtitleLanguageResult {
    private final String[] languages;
    private final int[] confidences;

    public SubtitleLanguageResult(String[] languages, int[] confidences) {
        if (languages.length != confidences.length)
            throw new IllegalArgumentException("Subtitle language and confidence counts differ");
        this.languages = languages.clone();
        this.confidences = confidences.clone();
        for (int i = 0; i < languages.length; i++) {
            if (languages[i] == null || !languages[i].matches("[a-z]{2,3}")
                    || confidences[i] < 0 || confidences[i] > 100
                    || ("und".equals(languages[i]) && confidences[i] != 0))
                throw new IllegalArgumentException("Invalid subtitle language result");
        }
    }

    public static SubtitleLanguageResult fromCsv(String languages, String confidences) {
        String[] codes = languages.isEmpty() ? new String[0] : languages.split(",", -1);
        String[] scores = confidences.isEmpty() ? new String[0] : confidences.split(",", -1);
        int[] values = new int[scores.length];
        for (int i = 0; i < scores.length; i++) values[i] = Integer.parseInt(scores[i]);
        return new SubtitleLanguageResult(codes, values);
    }

    public int size() { return languages.length; }
    public String language(int index) { return languages[index]; }
    public int confidence(int index) { return confidences[index]; }
    public boolean isIdentified(int index) {
        return !"und".equals(languages[index]) && confidences[index] >= 85;
    }
    SubtitleLanguageResult withoutTracks(java.util.BitSet removed) {
        String[] codes = languages.clone();
        int[] scores = confidences.clone();
        for (int i = 0; i < size(); i++) if (removed.get(i)) {
            codes[i] = "und";
            scores[i] = 0;
        }
        return new SubtitleLanguageResult(codes, scores);
    }
    boolean hasIdentifiedTracks() {
        for (int i = 0; i < size(); i++) if (isIdentified(i)) return true;
        return false;
    }
    public String languagesCsv() { return android.text.TextUtils.join(",", languages); }
    public String confidencesCsv() {
        StringBuilder result = new StringBuilder();
        for (int value : confidences) {
            if (result.length() > 0) result.append(',');
            result.append(value);
        }
        return result.toString();
    }
}
