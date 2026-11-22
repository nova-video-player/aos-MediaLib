// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.medialib;
import static org.junit.Assert.*;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.archos.mediaprovider.video.VideoOpenHelper;
import java.util.Arrays;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {23, 33})
public class SubtitleLanguageStoreTest {
    @Before public void resetStoreForFreshApplication() {
        // Robolectric creates a fresh application/database directory for each test.
        com.archos.mediaprovider.video.VideoOpenHelper helper =
                org.robolectric.util.ReflectionHelpers.getStaticField(SubtitleLanguageStore.class, "helper");
        if (helper != null) helper.close();
        org.robolectric.util.ReflectionHelpers.setStaticField(SubtitleLanguageStore.class, "helper", null);
    }

    @Test public void uriKeyedResultsKeepOrderAndReplacePreviousAnalysis() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        String uri = "file:///store-test.mkv";
        SubtitleLanguageStore.save(context, uri, SubtitleLanguageResult.fromCsv("en,und,fra", "97,0,99"));
        SubtitleLanguageResult saved = SubtitleLanguageStore.load(context, uri);
        assertNotNull(saved);
        assertEquals("en,und,fra", saved.languagesCsv());
        assertEquals("97,0,99", saved.confidencesCsv());
        assertEquals(3, saved.size());
        assertFalse(saved.isIdentified(1));
        assertNull(SubtitleLanguageStore.load(context, "file:///another.mkv"));
        SubtitleLanguageStore.save(context, uri, SubtitleLanguageResult.fromCsv("de", "96"));
        assertEquals("de", SubtitleLanguageStore.load(context, uri).languagesCsv());
        try (VideoOpenHelper helper = new VideoOpenHelper(context)) {
            SQLiteDatabase db = helper.getReadableDatabase();
            try (Cursor row = db.query("subtitle_language_analysis", null, "uri=?", new String[]{uri}, null, null, null)) {
                assertEquals(1, row.getCount());
                assertArrayEquals(new String[]{"uri", "languages", "confidences"}, row.getColumnNames());
            }
        }
        SubtitleLanguageStore.invalidate(context, uri);
        assertNull(SubtitleLanguageStore.load(context, uri));
    }

    @Test public void compactListsValidateCountsCodesAndConfidenceRanges() {
        for (String[] invalid : new String[][]{{"en,fr", "97"}, {"en,", "97,99"}, {"eng_Latn", "99"},
                {"und", "99"}, {"en", "101"}, {"en", "-1"}, {"en", "bad"}}) {
            try {
                SubtitleLanguageResult.fromCsv(invalid[0], invalid[1]);
                fail("Invalid results must not shift or mislabel tracks");
            } catch (IllegalArgumentException expected) { }
        }
        assertEquals(0, SubtitleLanguageResult.fromCsv("", "").size());
        String[] codes = new String[38];
        int[] scores = new int[38];
        Arrays.fill(codes, "fra");
        Arrays.fill(scores, 99);
        SubtitleLanguageResult result = new SubtitleLanguageResult(codes, scores);
        codes[0] = "de";
        scores[0] = 1;
        assertEquals("fra", result.language(0));
        assertEquals(99, result.confidence(0));
        assertTrue(result.languagesCsv().length() + result.confidencesCsv().length() < 300);
    }

    @Test public void malformedRowsAreIgnoredAndJournalRemainsSeparate() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        String uri = "file:///malformed.mkv";
        try (VideoOpenHelper helper = new VideoOpenHelper(context)) {
            helper.getWritableDatabase().execSQL("INSERT OR REPLACE INTO subtitle_language_analysis VALUES (?, ?, ?)",
                    new Object[]{uri, "en,fr", "99"});
        }
        assertNull(SubtitleLanguageStore.load(context, uri));
        JSONObject journal = new JSONObject().put("languages", "en,und").put("confidences", "97,0");
        SubtitleLanguageStore.journal(context, uri, journal, "PENDING");
        assertEquals("PENDING", SubtitleLanguageStore.journal(context, uri).getString("state"));
        SubtitleLanguageStore.journal(context, uri, journal, "UNDONE");
        assertEquals("UNDONE", SubtitleLanguageStore.journal(context, uri).getString("state"));
    }
    @Test public void deletionClearsUnindexedFileAndItsJournal() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        String uri = "file:///not-indexed.mkv";
        SubtitleLanguageStore.save(context, uri, SubtitleLanguageResult.fromCsv("fr", "99"));
        SubtitleLanguageStore.journal(context, uri, new JSONObject(), "APPLIED");
        SubtitleLanguageStore.removeDeleted(context, android.net.Uri.parse("/not-indexed.mkv"), false);
        assertNull(SubtitleLanguageStore.load(context, uri));
        assertNull(SubtitleLanguageStore.journal(context, uri));
    }

    @Test public void folderDeletionUsesLiteralBoundaryAndRemovesJournalWithoutCachedResults() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        String folder = "sftp://server/show_%25";
        String child = folder + "/season/episode.mkv";
        String orphan = folder + "/saved.mkv";
        String sibling = folder + "2/episode.mkv";
        String wildcard = "sftp://server/showAB25/episode.mkv";
        for (String uri : new String[]{child, sibling, wildcard}) {
            SubtitleLanguageStore.save(context, uri, SubtitleLanguageResult.fromCsv("fr", "99"));
            SubtitleLanguageStore.journal(context, uri, new JSONObject(), "APPLIED");
        }
        SubtitleLanguageStore.journal(context, orphan, new JSONObject(), "APPLIED");
        SubtitleLanguageStore.removeDeleted(context, android.net.Uri.parse(folder), true);
        assertNull(SubtitleLanguageStore.load(context, child));
        assertNull(SubtitleLanguageStore.journal(context, child));
        assertNull(SubtitleLanguageStore.journal(context, orphan));
        for (String uri : new String[]{sibling, wildcard}) {
            assertNotNull(SubtitleLanguageStore.load(context, uri));
            assertNotNull(SubtitleLanguageStore.journal(context, uri));
        }
    }

}
