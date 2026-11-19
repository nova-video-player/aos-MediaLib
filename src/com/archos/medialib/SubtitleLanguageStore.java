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
        db.execSQL("CREATE TABLE IF NOT EXISTS subtitle_language_analysis (uri TEXT PRIMARY KEY, fingerprint TEXT NOT "
                   + "NULL, result TEXT NOT NULL, updated INTEGER NOT NULL)");
    }
    public static JSONObject load(Context context, String uri, String fingerprint) {
        if (fingerprint == null || fingerprint.isEmpty())
            return null;
        try (Cursor cursor = db(context).query("subtitle_language_analysis", new String[] {"result"},
                 "uri=? AND fingerprint=?", new String[] {uri, fingerprint}, null, null, null)) {
            return cursor.moveToFirst() ? new JSONObject(cursor.getString(0)) : null;
        } catch (Exception e) {
            android.util.Log.w("SubtitleLanguages", "Cannot read analysis", e);
            return null;
        }
    }
    public static void save(Context context, String uri, JSONObject result) throws Exception {
        String fingerprint = result.getString("fingerprint");
        if (!fingerprint.matches("[a-f0-9]{64}"))
            throw new java.io.IOException("Missing file fingerprint");
        ContentValues values = new ContentValues();
        values.put("uri", uri);
        values.put("fingerprint", fingerprint);
        values.put("result", result.toString());
        values.put("updated", System.currentTimeMillis());
        if (db(context).insertWithOnConflict(
                "subtitle_language_analysis", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            < 0)
            throw new java.io.IOException("Cannot persist analysis");
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
}
