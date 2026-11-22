// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.medialib;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.content.Context;
import android.net.Uri;
import android.os.CancellationSignal;
import android.util.Base64;
import com.archos.filecorelibrary.NetworkInitialization;
import com.archos.filecorelibrary.RandomAccessMediaFile;
import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {23, 33}, shadows = MatroskaLanguageSaveTest.Header.class)
public class MatroskaLanguageSaveTest {
    @Before public void resetStoreForFreshApplication() {
        // Robolectric creates a fresh application/database directory for each test.
        com.archos.mediaprovider.video.VideoOpenHelper helper =
                org.robolectric.util.ReflectionHelpers.getStaticField(SubtitleLanguageStore.class, "helper");
        if (helper != null) helper.close();
        org.robolectric.util.ReflectionHelpers.setStaticField(SubtitleLanguageStore.class, "helper", null);
    }

    @Implements(value = MatroskaHeader.class, isInAndroidSdk = false)
    public static class Header {
        static JSONObject current;
        static int reads;
        @Implementation protected static void __staticInitializer__() { }
        @Implementation protected static JSONObject read(Context context, Uri uri, CancellationSignal cancel) {
            reads++;
            return current;
        }
        @Implementation protected static String inspect(byte[] prefix, long length) { return "{}"; }
    }

    private static class MemoryFile extends RandomAccessMediaFile {
        final byte[] data = new byte[80];
        int writes;
        MemoryFile() throws Exception {
            Arrays.fill(data, (byte) 42);
            System.arraycopy("und".getBytes("US-ASCII"), 0, data, 20, 3);
            Header.reads = 0;
            Header.current = new JSONObject().put("size", data.length)
                    .put("before", Base64.encodeToString(data, Base64.NO_WRAP))
                    .put("checksums", new JSONArray())
                    .put("tracks", new JSONArray().put(new JSONObject().put("uid", "current-header-uid")
                            .put("offset", 20).put("length", 3).put("ietfOffset", 0).put("ietfLength", 0)));
        }
        public long length() { return data.length; }
        public int read(long at, byte[] output, int off, int len) {
            System.arraycopy(data, (int) at, output, off, len);
            return len;
        }
        public void write(long at, byte[] input, int off, int len) {
            writes++;
            System.arraycopy(input, off, data, (int) at, len);
        }
        public void flush() { }
        public void close() { }
    }

    @Test public void saveReadsCurrentHeaderAndRestoreUsesCompactJournalResults() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        Uri uri = Uri.parse("sftp://server/save-current.mkv");
        MemoryFile file = new MemoryFile();
        byte[] original = file.data.clone();
        SubtitleLanguageResult result = SubtitleLanguageResult.fromCsv("fr", "99");
        SubtitleLanguageStore.save(context, uri.toString(), result);
        try (MockedStatic<NetworkInitialization> network = mockStatic(NetworkInitialization.class);
             MockedStatic<RandomAccessMediaFile> files = mockStatic(RandomAccessMediaFile.class)) {
            files.when(() -> RandomAccessMediaFile.open(context, uri)).thenReturn(file);
            MatroskaLanguageWriter.save(context, uri, result);
            assertEquals(1, Header.reads);
            assertNull(SubtitleLanguageStore.load(context, uri.toString()));
            assertEquals("fre", new String(file.data, 20, 3, "US-ASCII"));
            JSONObject journal = SubtitleLanguageStore.journal(context, uri.toString());
            assertEquals("APPLIED", journal.getString("state"));
            assertEquals("fr", journal.getString("languages"));
            assertEquals("99", journal.getString("confidences"));
            assertFalse(journal.has("analysis"));
            assertFalse(journal.has("mkv"));
            assertFalse(journal.has("fingerprint"));
            // Simulate a partial write: restore must still accept recorded before/after bytes.
            file.data[20] = original[20];
            MatroskaLanguageWriter.restore(context, uri);
            assertArrayEquals(original, file.data);
            assertEquals("UNDONE", SubtitleLanguageStore.journal(context, uri.toString()).getString("state"));
            assertEquals("fr", SubtitleLanguageStore.load(context, uri.toString()).languagesCsv());
            assertFalse(MatroskaLanguageWriter.isPlaybackActive());
        }
    }

    @Test public void headerChangedDuringPreparationIsRefusedBeforeWriting() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        Uri uri = Uri.parse("smb://server/changed-during-save.mkv");
        MemoryFile file = new MemoryFile();
        file.data[70] ^= 1;
        SubtitleLanguageStore.save(context, uri.toString(), SubtitleLanguageResult.fromCsv("fr", "99"));
        try (MockedStatic<NetworkInitialization> network = mockStatic(NetworkInitialization.class);
             MockedStatic<RandomAccessMediaFile> files = mockStatic(RandomAccessMediaFile.class)) {
            files.when(() -> RandomAccessMediaFile.open(context, uri)).thenReturn(file);
            try {
                MatroskaLanguageWriter.save(context, uri, SubtitleLanguageResult.fromCsv("fr", "99"));
                fail("Changed bytes must not be written");
            } catch (SubtitleLanguageException expected) {
                assertEquals(SubtitleLanguageException.Code.FILE_CHANGED, SubtitleLanguageException.codeOf(expected));
            }
            assertEquals(0, file.writes);
            assertEquals("fr", SubtitleLanguageStore.load(context, uri.toString()).languagesCsv());
            assertNull(SubtitleLanguageStore.journal(context, uri.toString()));
        }
    }
    @Test public void partialSaveKeepsLowConfidenceAndUnwritableTracksInPlace() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        Uri uri = Uri.parse("smb://server/partial-save.mkv");
        MemoryFile file = new MemoryFile();
        System.arraycopy("undund".getBytes("US-ASCII"), 0, file.data, 30, 6);
        JSONArray tracks = Header.current.getJSONArray("tracks");
        tracks.put(new JSONObject().put("offset", 30).put("length", 3).put("ietfOffset", 0).put("ietfLength", 0));
        // The last track would require resizing its existing field.
        tracks.put(new JSONObject().put("offset", 33).put("length", 2).put("ietfOffset", 0).put("ietfLength", 0));
        Header.current.put("before", Base64.encodeToString(file.data, Base64.NO_WRAP));
        SubtitleLanguageResult result = SubtitleLanguageResult.fromCsv("fr,en,de", "99,89,98");
        SubtitleLanguageStore.save(context, uri.toString(), result);
        try (MockedStatic<NetworkInitialization> network = mockStatic(NetworkInitialization.class);
             MockedStatic<RandomAccessMediaFile> files = mockStatic(RandomAccessMediaFile.class)) {
            files.when(() -> RandomAccessMediaFile.open(context, uri)).thenReturn(file);
            MatroskaLanguageWriter.save(context, uri, result);
            SubtitleLanguageResult remaining = SubtitleLanguageStore.load(context, uri.toString());
            assertEquals("und,en,de", remaining.languagesCsv());
            assertEquals("0,89,98", remaining.confidencesCsv());
            assertEquals("fre", new String(file.data, 20, 3, "US-ASCII"));
            assertEquals("undund", new String(file.data, 30, 6, "US-ASCII"));
            MatroskaLanguageWriter.restore(context, uri);
            assertEquals(result.languagesCsv(), SubtitleLanguageStore.load(context, uri.toString()).languagesCsv());
        }
    }

}
