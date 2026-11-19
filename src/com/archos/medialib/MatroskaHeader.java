// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.medialib;

import android.content.Context;
import android.net.Uri;
import android.os.CancellationSignal;
import android.util.Base64;
import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.FileEditorFactory;
import com.archos.filecorelibrary.ReadOptions;
import com.archos.filecorelibrary.MetaFile2Factory;
import java.io.IOException;
import java.io.InputStream;
import org.json.JSONObject;

/** Bounded, read-only input to libebml/libmatroska. No file handles cross JNI. */
public final class MatroskaHeader {
    static {
        System.loadLibrary("nova_matroska");
    }
    public static native String inspect(byte[] prefix, long length) throws IOException;
    public static JSONObject read(Context context, Uri uri, CancellationSignal cancel) throws Exception {
        FileEditor editor = FileEditorFactory.getFileEditorForUrl(uri, context);
        // FileEditor.length() is optional (SFTP/SMB/local implementations return -1).
        long length = MetaFile2Factory.getMetaFileForUrl(uri).length();
        if (length < 16)
            throw new IOException("Source has no usable file size");
        IOException last = null;
        for (int cap = 65536; cap <= 1024 * 1024; cap *= 2) {
            cancel.throwIfCanceled();
            byte[] prefix = new byte[(int) Math.min(length, cap)];
            try (InputStream in = editor.getInputStream(0, ReadOptions.METADATA)) {
                for (int pos = 0; pos < prefix.length;) {
                    cancel.throwIfCanceled();
                    int n = in.read(prefix, pos, prefix.length - pos);
                    if (n <= 0)
                        throw new IOException("Incomplete header read");
                    pos += n;
                }
            }
            try {
                return new JSONObject(inspect(prefix, length))
                    .put("size", length)
                    .put("before", Base64.encodeToString(prefix, Base64.NO_WRAP));
            } catch (IOException e) {
                last = e;
            }
            if (length <= cap)
                break;
        }
        throw last == null ? new IOException("Cannot inspect MKV") : last;
    }
}
