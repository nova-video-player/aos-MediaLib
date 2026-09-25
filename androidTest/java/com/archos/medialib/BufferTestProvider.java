// Copyright 2026 Courville Software
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.archos.medialib;

import android.content.*;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Real Android descriptors, including a pipe-backed cloud-provider stand-in. */
public final class BufferTestProvider extends ContentProvider {
    static volatile File backing;
    static volatile byte[] payload;
    static final int PREFIX = 512;
    static Uri uri(String kind) { return Uri.parse("content://nova.medialib.buffer.test/" + kind + "/fixture.mp4"); }
    @Override public boolean onCreate() { return true; }
    @Override public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
        try {
            if (uri.getPathSegments().get(0).equals("pipe")) {
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                byte[] bytes = payload;
                Thread writer = new Thread(() -> {
                    try (OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                        out.write(bytes);
                    } catch (IOException expectedWhenReaderCancels) { }
                }, "Buffer fixture pipe");
                writer.setDaemon(true);
                writer.start();
                return new AssetFileDescriptor(pipe[0], 0, AssetFileDescriptor.UNKNOWN_LENGTH);
            }
            boolean unknown = uri.getPathSegments().get(0).equals("unknown");
            return new AssetFileDescriptor(ParcelFileDescriptor.open(backing, ParcelFileDescriptor.MODE_READ_ONLY),
                    unknown ? 0 : PREFIX, unknown ? AssetFileDescriptor.UNKNOWN_LENGTH : payload.length);
        } catch (IOException e) { throw new FileNotFoundException("Unable to open buffer fixture"); }
    }
    @Override public Cursor query(Uri uri, String[] projection, String s, String[] a, String sort) {
        String[] columns = projection == null ? new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) row.add(OpenableColumns.SIZE.equals(column) ? payload.length
                : OpenableColumns.DISPLAY_NAME.equals(column) ? "fixture.mp4" : null);
        return cursor;
    }
    @Override public String getType(Uri uri) { return "video/mp4"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String s, String[] a) { throw new UnsupportedOperationException(); }
}
