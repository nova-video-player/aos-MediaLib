// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.medialib;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import com.archos.filecorelibrary.RandomAccessMediaFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.CRC32;
import org.json.JSONArray;
import org.json.JSONObject;

/** Fixed-size language patches with a durable before-image. Never remuxes or grows a file. */
public final class MatroskaLanguageWriter {
    private static final Object GUARD = new Object();
    private static boolean playing, editing;
    public static boolean setPlaybackActive(boolean active) {
        synchronized (GUARD) {
            if (active && editing)
                return false;
            playing = active;
            return true;
        }
    }
    public static boolean isPlaybackActive() {
        synchronized (GUARD) {
            return playing;
        }
    }
    private static void beginEdit() throws IOException {
        synchronized (GUARD) {
            if (playing || editing)
                throw new SubtitleLanguageException(SubtitleLanguageException.Code.BUSY, "Stop playback or wait for the current metadata edit");
            editing = true;
        }
    }
    private static void endEdit() {
        synchronized (GUARD) {
            editing = false;
        }
    }

    private static String legacy(String language) {
        String code = language.length() == 3 ? language : new Locale(language).getISO3Language();
        String[] t = {"fra", "fre", "deu", "ger", "ces", "cze", "nld", "dut", "ell", "gre", "zho", "chi", "eus", "baq",
            "fas", "per", "isl", "ice", "hye", "arm", "kat", "geo", "mkd", "mac", "msa", "may", "mya", "bur", "ron",
            "rum", "slk", "slo", "sqi", "alb", "bod", "tib", "cym", "wel"};
        for (int i = 0; i < t.length; i += 2)
            if (t[i].equals(code))
                return t[i + 1];
        return code;
    }
    private static byte[] bytes(JSONObject doc, String key) throws Exception {
        return Base64.decode(doc.getString(key), Base64.DEFAULT);
    }
    private static boolean unknown(byte[] b, int at, int n) {
        if (at < 0 || n < 1 || at > b.length - n)
            return false;
        String value = new String(b, at, n, StandardCharsets.US_ASCII).replace("\0", "").trim();
        return value.isEmpty() || value.equals("und");
    }
    private static void put(byte[] b, int at, int n, String language) throws Exception {
        byte[] value = language.getBytes(StandardCharsets.US_ASCII);
        if (at <= 0 || value.length != n || at > b.length - n || !language.matches("[a-z]{2,3}"))
            throw new SubtitleLanguageException(SubtitleLanguageException.Code.EDIT_UNSUPPORTED, "Language field requires resizing");
        System.arraycopy(value, 0, b, at, n);
    }
    public static boolean canSave(JSONObject analysis) {
        try {
            prepare(analysis);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    static byte[] prepare(JSONObject analysis) throws Exception {
        JSONObject mkv = analysis.getJSONObject("mkv");
        byte[] before = bytes(mkv, "before"), after = before.clone();
        JSONArray entries = mkv.getJSONArray("tracks"), tracks = analysis.getJSONArray("tracks");
        if (entries.length() != tracks.length())
            throw new SubtitleLanguageException(SubtitleLanguageException.Code.FILE_CHANGED, "Track identities changed");
        int changed = 0;
        for (int i = 0; i < tracks.length(); i++) {
            JSONObject t = tracks.getJSONObject(i), e = entries.getJSONObject(i);
            if (!t.getString("uid").equals(e.getString("uid")))
                throw new SubtitleLanguageException(SubtitleLanguageException.Code.FILE_CHANGED, "Track identities changed");
            if (t.optInt("source") != SubtitleLanguage.FASTTEXT || t.optInt("score") < 90)
                continue;
            String lang = t.getString("language"), oldCode;
            try {
                oldCode = legacy(lang);
            } catch (MissingResourceException ex) {
                continue;
            }
            int at = e.getInt("offset"), len = e.getInt("length"), ietf = e.getInt("ietfOffset"),
                ietfLen = e.getInt("ietfLength");
            if (len != 3 || !unknown(before, at, len) || oldCode.length() != 3)
                continue;
            if (ietf > 0 && (ietfLen != lang.length() || !unknown(before, ietf, ietfLen)))
                continue;
            put(after, at, len, oldCode);
            if (ietf > 0)
                put(after, ietf, ietfLen, lang);
            changed++;
        }
        if (changed == 0)
            throw new SubtitleLanguageException(SubtitleLanguageException.Code.EDIT_UNSUPPORTED, "No confident languages fit the existing metadata fields");
        JSONArray crcs = mkv.getJSONArray("checksums");
        List<JSONObject> ordered = new ArrayList<>();
        Set<Integer> parents = new HashSet<>();
        for (int i = 0; i < crcs.length(); i++) {
            JSONObject c = crcs.getJSONObject(i);
            if (!parents.add(c.getInt("start")))
                throw new SubtitleLanguageException(SubtitleLanguageException.Code.METADATA, "Duplicate metadata checksum");
            if (checksum(before, c) != stored(before, c.getInt("offset")))
                throw new SubtitleLanguageException(SubtitleLanguageException.Code.METADATA, "Existing metadata checksum is invalid");
            ordered.add(c);
        }
        ordered.sort(Comparator.comparingInt(c -> c.optInt("end") - c.optInt("start")));
        for (JSONObject c : ordered) {
            long crc = checksum(after, c);
            int at = c.getInt("offset");
            for (int i = 0; i < 4; i++) after[at + i] = (byte) (crc >>> (8 * i));
        }
        return after;
    }
    private static long stored(byte[] b, int at) throws IOException {
        if (at < 0 || at > b.length - 4)
            throw new SubtitleLanguageException(SubtitleLanguageException.Code.METADATA, "Invalid checksum bounds");
        long value = 0;
        for (int i = 0; i < 4; i++) value |= (long) (b[at + i] & 255) << (8 * i);
        return value;
    }
    private static long checksum(byte[] b, JSONObject c) throws Exception {
        int start = c.getInt("start"), end = c.getInt("end"), element = c.getInt("element"), at = c.getInt("offset");
        if (start < 0 || start > element || element > at || at > end - 4 || end > b.length)
            throw new SubtitleLanguageException(SubtitleLanguageException.Code.METADATA, "Invalid checksum coverage");
        CRC32 crc = new CRC32();
        crc.update(b, start, element - start);
        crc.update(b, at + 4, end - at - 4);
        return crc.getValue();
    }
    public static void save(Context context, Uri uri, JSONObject analysis) throws Exception {
        beginEdit();
        try {
            JSONObject previous = SubtitleLanguageStore.journal(context, uri.toString());
            if (previous != null && "PENDING".equals(previous.getString("state")))
                throw new SubtitleLanguageException(SubtitleLanguageException.Code.PENDING_EDIT, "Restore the interrupted edit first");
            byte[] before = bytes(analysis.getJSONObject("mkv"), "before"), after = prepare(analysis);
            JSONObject journal = new JSONObject()
                                     .put("before", Base64.encodeToString(before, Base64.NO_WRAP))
                                     .put("after", Base64.encodeToString(after, Base64.NO_WRAP))
                                     .put("size", analysis.getJSONObject("mkv").getLong("size"))
                                     .put("analysis", analysis);
            try (RandomAccessMediaFile file = RandomAccessMediaFile.open(context, uri)) {
                if (file.length() != journal.getLong("size")
                    || !Arrays.equals(before, file.readFully(0, before.length)))
                    throw new SubtitleLanguageException(SubtitleLanguageException.Code.FILE_CHANGED, "The file changed since analysis; analyze it again");
                SubtitleLanguageStore.journal(context, uri.toString(), journal, "PENDING");
                patches(file, before, after);
                if (file.length() != journal.getLong("size") || !Arrays.equals(after, file.readFully(0, after.length)))
                    throw new SubtitleLanguageException(SubtitleLanguageException.Code.VERIFY, "Metadata verification failed");
                MatroskaHeader.inspect(after, file.length());
            }
            // A fresh metadata fingerprint includes our own edit. Preserve unresolved results and provenance.
            JSONObject updated = new JSONObject(analysis.toString());
            updated.getJSONObject("mkv").put("before", Base64.encodeToString(after, Base64.NO_WRAP));
            updated.put("fingerprint", SubtitleLanguageAnalysis.fingerprint(context, uri))
                .put("writtenAt", System.currentTimeMillis());
            SubtitleLanguageStore.save(context, uri.toString(), updated);
            SubtitleLanguageStore.journal(context, uri.toString(), journal, "APPLIED");

        } finally {
            endEdit();
        }
    }
    public static void restore(Context context, Uri uri) throws Exception {
        beginEdit();
        try {
            JSONObject journal = SubtitleLanguageStore.journal(context, uri.toString());
            if (journal == null || "UNDONE".equals(journal.optString("state")))
                throw new SubtitleLanguageException(SubtitleLanguageException.Code.NO_EDIT, "No edit to restore");
            byte[] before = bytes(journal, "before"), after = bytes(journal, "after");
            try (RandomAccessMediaFile file = RandomAccessMediaFile.open(context, uri)) {
                if (file.length() != journal.getLong("size"))
                    throw new SubtitleLanguageException(SubtitleLanguageException.Code.RESTORE_CHANGED, "File size changed");
                byte[] current = file.readFully(0, before.length);
                for (int i = 0; i < current.length; i++)
                    if (current[i] != before[i] && current[i] != after[i])
                        throw new SubtitleLanguageException(SubtitleLanguageException.Code.RESTORE_CHANGED, "File changed outside the recorded edit");
                SubtitleLanguageStore.journal(context, uri.toString(), journal, "PENDING");
                patches(file, current, before);
                if (!Arrays.equals(before, file.readFully(0, before.length)))
                    throw new SubtitleLanguageException(SubtitleLanguageException.Code.VERIFY, "Restore verification failed");
            }
            SubtitleLanguageStore.save(context, uri.toString(), journal.getJSONObject("analysis"));
            SubtitleLanguageStore.journal(context, uri.toString(), journal, "UNDONE");

        } finally {
            endEdit();
        }
    }
    static void patches(RandomAccessMediaFile file, byte[] before, byte[] after) throws Exception {
        if (before.length != after.length)
            throw new SubtitleLanguageException(SubtitleLanguageException.Code.EDIT_UNSUPPORTED, "Resizing is forbidden");
        for (int at = 0; at < after.length;) {
            if (before[at] == after[at]) {
                at++;
                continue;
            }
            int start = at;
            while (at < after.length && before[at] != after[at]) at++;
            file.write(start, after, start, at - start);
        }
        file.flush();
    }
}
