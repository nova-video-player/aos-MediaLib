// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.medialib;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Installs the APK model off the UI thread; native inference loads it only on demand. */
final class SubtitleLanguageModel {
    private static final String TAG = "SubtitleLanguageModel";
    static final String SHA256 = "8f3472cfe8738a7b6099e8e999c3cbfae0dcd15696aac7d7738a8039db603e83";
    private static boolean started;

    interface Listener { void onReady(String path); }

    static synchronized void prepare(Context context, Listener listener) {
        if (started) return;
        started = true;
        Context app = context.getApplicationContext();
        Thread worker = new Thread(() -> {
            try {
                File directory = new File(app.getNoBackupFilesDir(), "fasttext");
                if (!directory.isDirectory() && !directory.mkdirs()) {
                    throw new IOException("Cannot create model directory");
                }
                File file = new File(directory, "lid.176-" + SHA256 + ".ftz");
                if (!matches(file, SHA256)) {
                    try (InputStream input = app.getAssets().open("fasttext/lid.176.ftz")) {
                        install(input, file, SHA256);
                    }
                }
                listener.onReady(file.getAbsolutePath());
            } catch (IOException | RuntimeException error) {
                Log.w(TAG, "Subtitle language model unavailable", error);
            }
        }, "subtitle-language-model");
        worker.start();
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 15, 16));
            result.append(Character.forDigit(value & 15, 16));
        }
        return result.toString();
    }

    static boolean matches(File file, String expected) throws IOException {
        if (!file.isFile()) return false;
        MessageDigest hash = digest();
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int size;
            while ((size = input.read(buffer)) != -1) hash.update(buffer, 0, size);
        }
        return expected.equals(hex(hash.digest()));
    }

    static void install(InputStream input, File file, String expected) throws IOException {
        AtomicFile atomic = new AtomicFile(file);
        FileOutputStream output = null;
        try {
            output = atomic.startWrite();
            MessageDigest hash = digest();
            byte[] buffer = new byte[8192];
            int size;
            while ((size = input.read(buffer)) != -1) {
                hash.update(buffer, 0, size);
                output.write(buffer, 0, size);
            }
            if (!expected.equals(hex(hash.digest()))) throw new IOException("Model checksum mismatch");
            atomic.finishWrite(output);
        } catch (IOException | RuntimeException error) {
            if (output != null) atomic.failWrite(output);
            throw error;
        }
    }
}
