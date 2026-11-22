// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.medialib;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.archos.mediaprovider.video.VideoOpenHelper;
import org.json.JSONObject;

/** User-requested analysis is durable, including unresolved tracks. No TTL or automatic retry. */
public final class SubtitleLanguageStore {
    private static VideoOpenHelper helper;
    private static synchronized SQLiteDatabase db(Context context) {
        if (helper == null)
            helper = new VideoOpenHelper(context.getApplicationContext());
        return helper.getWritableDatabase();
    }
    public static void createTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS subtitle_language_analysis (uri TEXT PRIMARY KEY, "
                   + "languages TEXT NOT NULL, confidences TEXT NOT NULL)");
    }
    public static SubtitleLanguageResult load(Context context, String uri) {
        try (Cursor cursor = db(context).query("subtitle_language_analysis", new String[] {"languages", "confidences"},
                 "uri=?", new String[] {uri}, null, null, null)) {
            return cursor.moveToFirst() ? SubtitleLanguageResult.fromCsv(cursor.getString(0), cursor.getString(1)) : null;
        } catch (Exception e) {
            android.util.Log.w("SubtitleLanguages", "Cannot read analysis", e);
            return null;
        }
    }
    public static void save(Context context, String uri, SubtitleLanguageResult result) throws Exception {
        ContentValues values = new ContentValues();
        values.put("uri", uri);
        values.put("languages", result.languagesCsv());
        values.put("confidences", result.confidencesCsv());
        if (db(context).insertWithOnConflict(
                "subtitle_language_analysis", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            < 0)
            throw new SubtitleLanguageException(SubtitleLanguageException.Code.STORAGE, "Cannot persist analysis");
    }
    private static android.util.AtomicFile journalFile(Context context, String uri) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(
            uri.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder name = new StringBuilder();
        for (byte b : digest) name.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        java.io.File directory = new java.io.File(context.getNoBackupFilesDir(), "subtitle-language-edits");
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory())
            throw new java.io.IOException("Cannot create journal directory");
        return new android.util.AtomicFile(new java.io.File(directory, name.toString() + ".json"));
    }
    public static JSONObject journal(Context context, String uri) throws Exception {
        android.util.AtomicFile file = journalFile(context, uri);
        try {
            return new JSONObject(new String(file.readFully(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.FileNotFoundException absent) {
            return null;
        }
    }
    public static void journal(Context context, String uri, JSONObject journal, String state) throws Exception {
        android.util.AtomicFile file = journalFile(context, uri);
        java.io.FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(new JSONObject(journal.toString())
                    .put("state", state)
                    .put("uri", uri)
                    .toString()
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.getFD().sync();
            file.finishWrite(output);
        } catch (Exception failure) {
            if (output != null)
                file.failWrite(output);
            throw failure;
        }
    }
    public static void invalidate(Context context, String uri) {
        db(context).delete("subtitle_language_analysis", "uri=?", new String[] {uri});
    }

    /** Called only after physical deletion succeeds, never on scanner/library removal. */
    public static void removeDeleted(Context context, android.net.Uri uri, boolean folder) throws Exception {
        if (uri.getScheme() == null || "file".equals(uri.getScheme()))
            uri = android.net.Uri.fromFile(new java.io.File(uri.getPath()));
        String key = uri.toString();
        if (!folder) {
            invalidate(context, key);
            journalFile(context, key).delete();
            return;
        }
        String prefix = key.endsWith("/") ? key : key + "/";
        java.util.List<String> removed = new java.util.ArrayList<>();
        // substr avoids interpreting percent/underscore characters in URIs as SQL wildcards.
        try (Cursor cursor = db(context).query("subtitle_language_analysis", new String[]{"uri"},
                "uri=? OR substr(uri,1,?)=?", new String[]{key, Integer.toString(prefix.length()), prefix},
                null, null, null)) {
            while (cursor.moveToNext()) removed.add(cursor.getString(0));
        }
        for (String value : removed) {
            invalidate(context, value);
            journalFile(context, value).delete();
        }
        // A fully saved file may have a recovery journal but no remaining cached guesses.
        java.io.File directory = new java.io.File(context.getNoBackupFilesDir(), "subtitle-language-edits");
        java.io.File[] journals = directory.listFiles((dir, name) -> name.endsWith(".json"));
        if (journals == null) return;
        for (java.io.File path : journals) {
            android.util.AtomicFile file = new android.util.AtomicFile(path);
            try {
                JSONObject doc = new JSONObject(new String(file.readFully(), java.nio.charset.StandardCharsets.UTF_8));
                String value = doc.optString("uri");
                if (value.equals(key) || value.startsWith(prefix)) file.delete();
            } catch (Exception e) {
                android.util.Log.w("SubtitleLanguages", "Cannot inspect recovery journal for deleted folder", e);
            }
        }
    }
}
