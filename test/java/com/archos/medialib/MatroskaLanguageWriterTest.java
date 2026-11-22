// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.medialib;
import static org.junit.Assert.*;

import android.util.Base64;
import com.archos.filecorelibrary.RandomAccessMediaFile;
import java.io.IOException;
import java.util.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23)
public class MatroskaLanguageWriterTest {
    private JSONObject header(String original, int ietfLength) throws Exception {
        byte[] prefix = new byte[80];
        Arrays.fill(prefix, (byte) 42);
        System.arraycopy(original.getBytes("US-ASCII"), 0, prefix, 20, 3);
        if (ietfLength > 0)
            System.arraycopy("und".getBytes("US-ASCII"), 0, prefix, 30, 3);
        JSONObject field = new JSONObject()
                               .put("uid", "18446744073709551615")
                               .put("offset", 20)
                               .put("length", 3)
                               .put("ietfOffset", ietfLength > 0 ? 30 : 0)
                               .put("ietfLength", ietfLength);
        return new JSONObject()
                .put("before", Base64.encodeToString(prefix, Base64.NO_WRAP))
                .put("tracks", new JSONArray().put(field))
                .put("checksums", new JSONArray());
    }
    private SubtitleLanguageResult result(int score) {
        return SubtitleLanguageResult.fromCsv("fr", Integer.toString(score));
    }
    @Test
    public void onlyLanguageBytesChangeAndFrenchUsesBibliographicCode() throws Exception {
        JSONObject doc = header("und", 0);
        byte[] old = Base64.decode(doc.getString("before"), Base64.DEFAULT);
        byte[] edited = MatroskaLanguageWriter.prepare(doc, result(94));
        assertEquals("fre", new String(edited, 20, 3, "US-ASCII"));
        for (int i = 0; i < old.length; i++)
            if (i < 20 || i >= 23)
                assertEquals(old[i], edited[i]);
        assertEquals(old.length, edited.length);
    }
    @Test
    public void uncertainKnownAndResizingChangesAreRefused() throws Exception {
        JSONObject[] headers = {header("und", 0), header("eng", 0), header("und", 3)};
        int[] scores = {89, 99, 99};
        for (int i = 0; i < headers.length; i++) {
            try {
                MatroskaLanguageWriter.prepare(headers[i], result(scores[i]));
                fail("Must refuse unsafe edit");
            } catch (IOException expected) {
                assertEquals(SubtitleLanguageException.Code.EDIT_UNSUPPORTED, SubtitleLanguageException.codeOf(expected));
            }
        }
        assertFalse(MatroskaLanguageWriter.canSave(result(89)));
        assertTrue(MatroskaLanguageWriter.canSave(result(90)));
    }
    @Test
    public void trackCountMismatchIsRefusedWithoutPersistedTrackUids() throws Exception {
        JSONObject doc = header("und", 0);
        try {
            MatroskaLanguageWriter.prepare(doc, SubtitleLanguageResult.fromCsv("fr,en", "99,99"));
            fail();
        } catch (IOException expected) {
            assertEquals(SubtitleLanguageException.Code.FILE_CHANGED, SubtitleLanguageException.codeOf(expected));
        }
        doc.getJSONArray("tracks").getJSONObject(0).put("uid", "new-current-file-uid");
        assertEquals("fre", new String(MatroskaLanguageWriter.prepare(doc, result(99)), 20, 3, "US-ASCII"));
    }
    @Test
    public void partialWriteFailureDoesNotTouchUnrelatedRanges() throws Exception {
        byte[] before = new byte[] {1, 2, 3, 4, 5, 6, 7}, after = new byte[] {1, 9, 9, 4, 8, 6, 7};
        List<Long> offsets = new ArrayList<>();
        RandomAccessMediaFile file = new RandomAccessMediaFile() {
            public long length() {
                return 7;
            }
            public int read(long at, byte[] b, int off, int len) {
                return -1;
            }
            public void write(long at, byte[] b, int off, int len) throws IOException {
                offsets.add(at);
                if (at == 4)
                    throw new IOException("Disconnected");
            }
            public void flush() {
                fail("Cannot report success after failed write");
            }
            public void close() {}
        };
        try {
            MatroskaLanguageWriter.patches(file, before, after);
            fail();
        } catch (IOException expected) {
        }
        assertEquals(Arrays.asList(1L, 4L), offsets);
    }

    @Test
    public void checksumsAreVerifiedAndRecomputed() throws Exception {
        JSONObject mkv = header("und", 0);
        byte[] before = Base64.decode(mkv.getString("before"), Base64.DEFAULT);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(before, 16, before.length - 16);
        for (int n = 0; n < 4; n++) before[12 + n] = (byte) (crc.getValue() >>> (8 * n));
        mkv.put("before", Base64.encodeToString(before, Base64.NO_WRAP));
        mkv.getJSONArray("checksums")
            .put(new JSONObject().put("start", 10).put("end", 80).put("element", 10).put("offset", 12));
        byte[] after = MatroskaLanguageWriter.prepare(mkv, result(95));
        crc.reset();
        crc.update(after, 16, after.length - 16);
        long actual = 0;
        for (int n = 0; n < 4; n++) actual |= (long) (after[12 + n] & 255) << (8 * n);
        assertEquals(crc.getValue(), actual);
        before[79] ^= 1;
        mkv.put("before", Base64.encodeToString(before, Base64.NO_WRAP));
        try {
            MatroskaLanguageWriter.prepare(mkv, result(95));
            fail("Corrupt checksum must refuse writing");
        } catch (IOException expected) {
        }
    }
    @Test
    public void realMovieCopyCanBePatchedAndRestored() throws Exception {
        String fixture = System.getenv("NOVA_SUBTITLE_FIXTURE");
        org.junit.Assume.assumeNotNull(fixture);
        JSONObject plan =
            new JSONObject(new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(fixture)), "UTF-8"));
        byte[] before = Base64.decode(plan.getString("before"), Base64.DEFAULT);
        String[] languages = new String[plan.getJSONArray("tracks").length()];
        int[] scores = new int[languages.length];
        Arrays.fill(languages, "fr");
        Arrays.fill(scores, 99);
        byte[] after = MatroskaLanguageWriter.prepare(plan, new SubtitleLanguageResult(languages, scores));
        // Disposable copy produced by the caller, never the source movie.
        try (java.io.RandomAccessFile raw = new java.io.RandomAccessFile(plan.getString("copy"), "rw")) {
            RandomAccessMediaFile file = new RandomAccessMediaFile() {
                public long length() throws Exception {
                    return raw.length();
                }
                public int read(long at, byte[] b, int off, int len) throws Exception {
                    raw.seek(at);
                    return raw.read(b, off, len);
                }
                public void write(long at, byte[] b, int off, int len) throws Exception {
                    raw.seek(at);
                    raw.write(b, off, len);
                }
                public void flush() throws Exception {
                    raw.getFD().sync();
                }
                public void close() {}
            };
            assertArrayEquals(before, file.readFully(0, before.length));
            patchesAndCheck(file, before, after);
            // Leave edited copy for an independent ffprobe check; retain original prefix for recovery.
        }
    }
    private void patchesAndCheck(RandomAccessMediaFile file, byte[] before, byte[] after) throws Exception {
        MatroskaLanguageWriter.patches(file, before, after);
        assertArrayEquals(after, file.readFully(0, after.length));
        MatroskaLanguageWriter.patches(file, after, before);
        assertArrayEquals(before, file.readFully(0, before.length));
        MatroskaLanguageWriter.patches(file, before, after);
    }
}
